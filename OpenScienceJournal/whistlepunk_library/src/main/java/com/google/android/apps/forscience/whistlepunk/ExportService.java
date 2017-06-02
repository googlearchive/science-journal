/*
 *  Copyright 2017 Google Inc. All Rights Reserved.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package com.google.android.apps.forscience.whistlepunk;

import android.app.IntentService;
import android.content.Intent;
import android.content.Context;
import android.support.annotation.NonNull;
import android.support.annotation.VisibleForTesting;
import android.support.v4.util.ArrayMap;
import android.util.Log;

import com.google.android.apps.forscience.whistlepunk.filemetadata.Experiment;
import com.google.android.apps.forscience.whistlepunk.filemetadata.Trial;
import com.google.android.apps.forscience.whistlepunk.review.RunReviewExporter;
import com.google.android.apps.forscience.whistlepunk.sensordb.ScalarReading;
import com.google.android.apps.forscience.whistlepunk.sensordb.TimeRange;
import com.google.common.collect.Range;

import org.reactivestreams.Subscription;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;

import io.reactivex.Observer;
import io.reactivex.Single;
import io.reactivex.SingleObserver;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.disposables.Disposable;
import io.reactivex.functions.Consumer;
import io.reactivex.schedulers.Schedulers;

/**
 * An {@link IntentService} subclass for handling asynchronous task requests in
 * a service on a separate handler thread for exporting various data sets in various
 * formats.
 */
public class ExportService extends IntentService {
    private static final String TAG = "ExportService";

    private static final String ACTION_EXPORT_TRIAL =
            "com.google.android.apps.forscience.whistlepunk.action.EXPORT_TRIAL";

    private static final String EXTRA_EXPERIMENT_ID =
            "com.google.android.apps.forscience.whistlepunk.extra.EXPERIMENT_ID";
    private static final String EXTRA_TRIAL_ID =
            "com.google.android.apps.forscience.whistlepunk.extra.TRIAL_ID";
    private static final String EXTRA_RELATIVE_TIME =
            "com.google.android.apps.forscience.whistlepunk.extra.RELATIVE_TIME";
    private static final String EXTRA_SENSOR_IDS =
            "com.google.android.apps.forscience.whistlepunk.extra.SENSOR_IDS";

    public ExportService() {
        super("ExportService");
    }

    /**
     * Starts this service to perform action export trial with the given parameters. If
     * the service is already performing a task this action will be queued.
     *
     * @see IntentService
     */
    public static void exportTrial(Context context, String experimentId, String trialId,
            boolean relativeTime, String[] sensorIds) {
        Intent intent = new Intent(context, ExportService.class);
        intent.setAction(ACTION_EXPORT_TRIAL);
        intent.putExtra(EXTRA_EXPERIMENT_ID, experimentId);
        intent.putExtra(EXTRA_TRIAL_ID, trialId);
        intent.putExtra(EXTRA_RELATIVE_TIME, relativeTime);
        intent.putExtra(EXTRA_SENSOR_IDS, sensorIds);
        context.startService(intent);
    }


    @Override
    protected void onHandleIntent(Intent intent) {
        if (intent != null) {
            final String action = intent.getAction();
            if (ACTION_EXPORT_TRIAL.equals(action)) {
                final String experimentId = intent.getStringExtra(EXTRA_EXPERIMENT_ID);
                final String trialId = intent.getStringExtra(EXTRA_TRIAL_ID);
                final boolean relativeTime = intent.getBooleanExtra(EXTRA_RELATIVE_TIME, false);
                final String[] sensorIds = intent.getStringArrayExtra(EXTRA_SENSOR_IDS);
                handleActionExportTrial(experimentId, trialId, relativeTime, sensorIds);
            }
        }
    }

    /**
     * Handle action export trial in the provided background thread with the provided
     * parameters.
     */
    private void handleActionExportTrial(String experimentId, String trialId, boolean relativeTime,
            String[] sensorIds) {
        // Blocking gets OK: this is already background threaded.
        DataController dc = getDataController().blockingGet();
        Experiment experiment = RxDataController.getExperimentById(dc, experimentId).blockingGet();
        Trial trial = experiment.getTrial(trialId);

        String fileName = makeExportFilename(experiment.getDisplayTitle(this),
                trial.getTitle(this));
        // Start observing sensor data from here, while grouping them into timestamp equal rows.
        // Then write the rows out.
        Range<Long> range = Range.closed(trial.getFirstTimestamp(), trial.getLastTimestamp());
        dc.createScalarObservable(sensorIds, TimeRange.oldest(range), 0 /* resolution tier */)
                .subscribe(new TrialDataWriter(fileName, relativeTime, sensorIds));
    }

    private Single<DataController> getDataController() {
        return DataService.bind(this).map(AppSingleton::getDataController);
    }

    @NonNull
    @VisibleForTesting
    public static String makeExportFilename(String experimentName, String trialName) {
        // 40 chars of experimentname + 35 chars of run title + " " + ".csv" = 80 chars
        return sanitizeFilename(truncate(experimentName, 40)
                + " "
                + truncate(trialName, 35)
                + ".csv");
    }

    private static String truncate(String string, int maxLength) {
        int hexLength = 8;
        if (string.length() < maxLength) {
            return string;
        }

        // Use a hash of the overflow characters to minimize odds of collision.
        int cutoff = maxLength - hexLength;
        String hexString = Integer.toHexString(string.substring(cutoff).hashCode());
        return string.substring(0, cutoff) + hexString;
    }

    static String sanitizeFilename(String inputName) {
        return inputName.replaceAll("[^ a-zA-Z0-9-_\\.]", "_");
    }

    @NonNull
    private File getStorageDir() {
        return new File(getFilesDir().getPath(), "exported_run_files");
    }

    private class TrialDataWriter implements Observer<ScalarReading> {

        private long mCurrentTimestamp = -1;
        private long mFirstTimeStampWritten = -1;

        private ArrayMap<String, Double> mCurrentRow = new ArrayMap<>();
        private OutputStreamWriter mOutputStreamWriter;
        private final String mFileName;
        private final boolean mRelativeTime;
        private final String[] mSensorIds;

        public TrialDataWriter(String fileName, boolean relativeTime, String[] sensorIds) {
            mFileName = fileName;
            mRelativeTime = relativeTime;
            mSensorIds = sensorIds;
        }

        @Override
        public void onSubscribe(Disposable disposable) {
            // Start writing stream.
            File storageDir = getStorageDir();

            // Create the storage directory if it does not exist
            if (!storageDir.exists()) {
                if (!storageDir.mkdirs()) {
                    Log.e(TAG, "failed to create directory");
                    onError(new IOException("Could not create dir " +
                            storageDir.getAbsolutePath()));
                    return;
                }
            }

            File file = new File(storageDir.getPath(), mFileName);
            FileOutputStream fs;
            try {
                fs = new FileOutputStream(file);
            } catch (FileNotFoundException e) {
                onError(e);
                return;
            }

            mOutputStreamWriter = new OutputStreamWriter(fs);
            try {
                mOutputStreamWriter.write(mRelativeTime ? "relative_time" : "timestamp");
                // Loop through sensor IDs and output them here as column names.
                for (int index = 0, length = mSensorIds.length; index < length; ++index) {
                    mOutputStreamWriter.write(",");
                    mOutputStreamWriter.write(mSensorIds[index].replace(",", "_"));
                }
                mOutputStreamWriter.write("\n");
            } catch (IOException e) {
                onError(e);
                return;
            }
        }

        @Override
        public void onNext(ScalarReading scalarReading) {
            // Check if we have a different timestamp than the current row.
            if (scalarReading.getCollectedTimeMillis() != mCurrentTimestamp) {
                if (mCurrentRow != null && mCurrentTimestamp != -1) {
                    // If so, if current row, then write it out (the old one)!
                    try {
                        if (mOutputStreamWriter == null) {
                            onError(new IllegalStateException("Output stream closed."));
                        }
                        mOutputStreamWriter.write(getTimestampString(mCurrentTimestamp));
                        for (int index = 0, length = mSensorIds.length; index < length; ++index) {
                            String value = "";
                            if (mCurrentRow.containsKey(mSensorIds[index])) {
                                value = Double.toString(mCurrentRow.get(mSensorIds[index]));
                            }
                            mOutputStreamWriter.write(",");
                            mOutputStreamWriter.write(value);
                        }
                        mOutputStreamWriter.write("\n");
                    } catch (IOException e) {
                        onError(e);
                    }
                }

                mCurrentRow.clear();
            }
            // If not, just add to current row.
            mCurrentRow.put(scalarReading.getSensorTag(), scalarReading.getValue());
            if (mCurrentTimestamp == -1) {
                mFirstTimeStampWritten = scalarReading.getCollectedTimeMillis();
            }
            mCurrentTimestamp = scalarReading.getCollectedTimeMillis();
        }

        @Override
        public void onError(Throwable throwable) {
            // End writing stream.
            closeStreamIfNecessary();
        }

        @Override
        public void onComplete() {
            // End writing stream.
            closeStreamIfNecessary();
        }

        private String getTimestampString(long time) {
            return Long.toString(mRelativeTime ? time - mFirstTimeStampWritten : time);
        }

        private void closeStreamIfNecessary() {
            if (mOutputStreamWriter != null) {
                try {
                    mOutputStreamWriter.close();
                } catch (IOException e) {
                    Log.e(TAG, "File close failed: " + e.toString());
                    onError(e);
                    return;
                } finally {
                    mOutputStreamWriter = null;
                }
            }
        }
    }
}
