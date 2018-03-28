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

import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.net.Uri;
import android.os.Binder;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import android.support.design.widget.Snackbar;
import androidx.core.content.FileProvider;
import androidx.collection.ArrayMap;
import android.util.Log;

import com.google.android.apps.forscience.javalib.MaybeConsumer;
import com.google.android.apps.forscience.whistlepunk.filemetadata.Experiment;
import com.google.android.apps.forscience.whistlepunk.filemetadata.FileMetadataManager;
import com.google.android.apps.forscience.whistlepunk.filemetadata.Trial;
import com.google.android.apps.forscience.whistlepunk.sensordb.ScalarReading;
import com.google.android.apps.forscience.whistlepunk.sensordb.TimeRange;
import com.google.common.collect.Range;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.Objects;
import java.util.zip.ZipException;

import io.reactivex.Observable;
import io.reactivex.Observer;
import io.reactivex.Single;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;
import io.reactivex.subjects.BehaviorSubject;
import io.reactivex.subjects.PublishSubject;

/**
 * Service for importing and exporting trial and experiment data with different options.
 * Can be bound for status updates using {@link #bind(Context)}.
 * Export trial data using {@link #exportTrial(Context, String, String, boolean, String[])}
 * Export experiment data using {@link #exportExperiment(Context, String)}
 * Import experiment data using {@link #importExperiment(Context, Uri)}
 * TODO: Rename to ImportExportService
 */
public class ExportService extends Service {
    private static final String TAG = "ExportService";

    private static final String ACTION_EXPORT_TRIAL =
            "com.google.android.apps.forscience.whistlepunk.action.EXPORT_TRIAL";

    private static final String ACTION_EXPORT_EXPERIMENT =
            "com.google.android.apps.forscience.whistlepunk.action.EXPORT_EXPERIMENT";

    private static final String ACTION_IMPORT_EXPERIMENT =
            "com.google.android.apps.forscience.whistlepunk.action.IMPORT_EXPERIMENT";

    private static final String EXTRA_EXPERIMENT_ID =
            "com.google.android.apps.forscience.whistlepunk.extra.EXPERIMENT_ID";
    private static final String EXTRA_TRIAL_ID =
            "com.google.android.apps.forscience.whistlepunk.extra.TRIAL_ID";
    private static final String EXTRA_RELATIVE_TIME =
            "com.google.android.apps.forscience.whistlepunk.extra.RELATIVE_TIME";
    private static final String EXTRA_SENSOR_IDS =
            "com.google.android.apps.forscience.whistlepunk.extra.SENSOR_IDS";
    private static final String EXTRA_IMPORT_URI =
            "com.google.android.apps.forscience.whistlepunk.extra.IMPORT_URI";

    private static final String ACTION_CLEAN_OLD_FILES =
            "com.google.android.apps.forscience.whistlepunk.action.CLEAN_OLD_FILES";

    private final IBinder mBinder = new ExportServiceBinder();

    // Make static so that all instances of this service can reach it.
    private final static BehaviorSubject<ExportProgress> sProgressSubject =
            BehaviorSubject.createDefault(new ExportProgress("", ExportProgress.NOT_EXPORTING, 0));

    // Copied from IntentService: basically we do everything the same except wait to call stopSelf
    // until subscriptions finish.
    private volatile Looper mServiceLooper;
    private volatile ServiceHandler mServiceHandler;

    private final class ServiceHandler extends Handler {
        public ServiceHandler(Looper looper) {
            super(looper);
        }

        @Override
        public void handleMessage(Message msg) {
            onHandleIntent((Intent) msg.obj, msg.arg1);
        }
    }

    public class ExportServiceBinder extends Binder {
        ExportService getService() {
            return ExportService.this;
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        HandlerThread thread = new HandlerThread("ExportService");
        thread.start();
        mServiceLooper = thread.getLooper();
        mServiceHandler = new ServiceHandler(mServiceLooper);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Message msg = mServiceHandler.obtainMessage();
        msg.arg1 = startId;
        msg.obj = intent;
        mServiceHandler.sendMessage(msg);

        return START_NOT_STICKY;
    }

    /**
     * Starts this service to perform action export trial with the given parameters. If
     * the service is already performing a task this action will be queued.
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

    /**
     * Starts this service to perform action export experiment with the given parameters. If
     * the service is already performing a task this action will be queued.
     */
    public static void exportExperiment(Context context, String experimentId) {
        Intent intent = new Intent(context, ExportService.class);
        intent.setAction(ACTION_EXPORT_EXPERIMENT);
        intent.putExtra(EXTRA_EXPERIMENT_ID, experimentId);
        context.startService(intent);
    }

    /**
     * Starts this service to perform action import experiment with the given parameters. If
     * the service is already performing a task this action will be queued.
     */
    public static void importExperiment(Context context, Uri file) {
        Intent intent = new Intent(context, ExportService.class);
        intent.setAction(ACTION_IMPORT_EXPERIMENT);
        intent.putExtra(EXTRA_IMPORT_URI, file.toString());
        context.startService(intent);
    }

    /**
     * Starts this service to clean up old files.
     */
    public static void cleanOldFiles(Context context) {
        Intent intent = new Intent(context, ExportService.class);
        intent.setAction(ACTION_CLEAN_OLD_FILES);
        context.startService(intent);
    }


    private void onHandleIntent(Intent intent, int startId) {
        if (intent != null) {
            final String action = intent.getAction();
            if (ACTION_EXPORT_TRIAL.equals(action)) {
                final String experimentId = intent.getStringExtra(EXTRA_EXPERIMENT_ID);
                final String trialId = intent.getStringExtra(EXTRA_TRIAL_ID);
                final boolean relativeTime = intent.getBooleanExtra(EXTRA_RELATIVE_TIME, false);
                final String[] sensorIds = intent.getStringArrayExtra(EXTRA_SENSOR_IDS);
                handleActionExportTrial(experimentId, trialId, relativeTime, sensorIds, startId);
            } else if (ACTION_EXPORT_EXPERIMENT.equals(action)) {
                final String experimentId = intent.getStringExtra(EXTRA_EXPERIMENT_ID);
                handleActionExportExperiment(experimentId, startId);
            } else if (ACTION_CLEAN_OLD_FILES.equals(action)) {
                handleCleanOldFiles(startId);
            } else if (ACTION_IMPORT_EXPERIMENT.equals(action)) {
                final Uri file = Uri.parse(intent.getStringExtra(EXTRA_IMPORT_URI));
                handleActionImportExperiment(file, startId);
            }
        }
    }

    @Override
    public void onDestroy() {
        if (Log.isLoggable(TAG, Log.DEBUG)) Log.d(TAG, "Destroying service");
        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    public BehaviorSubject<ExportProgress> getProgressSubject() {
        return sProgressSubject;
    }

    public static class ExportProgress {
        public static final int NOT_EXPORTING = 0;
        public static final int ERROR = 1;
        public static final int EXPORTING = 2;
        public static final int EXPORT_COMPLETE = 3;

        private final String mId;
        private final int mState;
        private final int mProgress;

        private Throwable mError;
        private Uri mFileUri;

        // id should be a UUID, like a trialId or an experimentId.
        public ExportProgress(String id, int state, int progress) {
            mId = id;
            mState = state;
            mProgress = progress;
        }

        public String getId() {
            return mId;
        }

        public int getState() {
            return mState;
        }

        public int getProgress() {
            return mProgress;
        }

        public Throwable getError() {
            return mError;
        }

        public Uri getFileUri() {
            return mFileUri;
        }

        @Override
        public String toString() {
            return "State: " + mState + " progress " + mProgress;
        }

        public static ExportProgress getComplete(String id, Uri fileUri) {
            ExportProgress progress = new ExportProgress(id, EXPORT_COMPLETE, 0);
            progress.mFileUri = fileUri;
            return progress;
        }

        public static ExportProgress fromThrowable(String id, Throwable throwable) {
            ExportProgress progress = new ExportProgress(id, ERROR, 0);
            progress.mError = throwable;
            return progress;
        }
    }

    public static void resetProgress(String id) {
        sProgressSubject.onNext(new ExportProgress(id, ExportProgress.NOT_EXPORTING, 0));
    }

    public static Observable<ExportProgress> bind(Context context) {
        final Context appContext = context.getApplicationContext();
        final PublishSubject<ExportProgress> progressPublishSubject = PublishSubject.create();

        final ServiceConnection conn = new ServiceConnection() {

            @Override
            public void onServiceConnected(ComponentName name, IBinder service) {
                ExportServiceBinder exporter = (ExportServiceBinder) service;
                if (Log.isLoggable(TAG, Log.DEBUG)) {
                    Log.d(TAG, "binding service " + exporter);
                }
                exporter.getService().getProgressSubject()
                        .subscribe(progressPublishSubject);
            }

            @Override
            public void onServiceDisconnected(ComponentName name) {
            }
        };
        Intent intent = new Intent(appContext, ExportService.class);
        if (appContext.bindService(intent, conn, BIND_AUTO_CREATE)) {
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "trying to bind service.");
            }
        }

        return progressPublishSubject
                .doOnDispose(() -> {
                    if (Log.isLoggable(TAG, Log.DEBUG)) {
                        Log.d(TAG, "unbinding service ");
                    }
                    appContext.unbindService(conn);
                });
    }

    /**
     * Handle action export trial in the provided background thread with the provided
     * parameters.
     */
    private void handleActionExportTrial(String experimentId, String trialId, boolean relativeTime,
            String[] sensorIds, int startId) {
        // Blocking gets OK: this is already background threaded.
        DataController dc = getDataController().blockingGet();
        Experiment experiment = RxDataController.getExperimentById(dc, experimentId).blockingGet();
        Trial trial = experiment.getTrial(trialId);

        String fileName = makeExportFilename(experiment.getDisplayTitle(this),
                trial.getTitle(this));
        // Start observing sensor data from here, while grouping them into timestamp equal rows.
        // Then write the rows out.
        Range<Long> range = Range.closed(trial.getFirstTimestamp(), trial.getLastTimestamp());
        dc.createScalarObservable(trialId, sensorIds, TimeRange.oldest(range), 0 /* resolution
        tier */)
                .doOnComplete(() -> stopSelf(startId))
                .observeOn(Schedulers.io())
                .subscribe(new TrialDataWriter(trialId, fileName, relativeTime, sensorIds,
                        trial.getFirstTimestamp(), trial.getLastTimestamp()));
    }

    /**
     * Handle action export experiment in the provided background thread with the provided
     * parameters.
     */
    private void handleActionExportExperiment(String experimentId, int startId) {
        // Blocking gets OK: this is already background threaded.
        DataController dc = getDataController().blockingGet();
        Experiment experiment = RxDataController.getExperimentById(dc, experimentId).blockingGet();
        File file = FileMetadataManager.getFileForExport(getApplicationContext(), experiment, dc)
                .blockingGet();

        updateProgress(ExportProgress.getComplete(experimentId, getExperimentFileUri(
                file.getName())));

    }

    /**
     * Handle action import experiment in the provided background thread with the provided
     * parameters.
     */
    private void handleActionImportExperiment(Uri fileUri, int startId) {
        // Blocking gets OK: this is already background threaded.
        AppSingleton.getInstance(
                getApplicationContext()).getDataController().importExperimentFromZip(
                fileUri,
                getApplicationContext().getContentResolver(),
                new MaybeConsumer<String>() {
                    @Override
                    public void success(String experimentId) {
                        updateProgress(ExportProgress.getComplete(experimentId, fileUri));
                    }

                    @Override
                    public void fail(Exception e) {
                        AppSingleton.getInstance(getApplicationContext()).setExportServiceBusy(
                                false);
                        if (e instanceof ZipException) {
                            showSnackbar(R.string.import_failed_file);
                            Log.e(TAG, "SJ file format exception", e);
                        } else {
                            showSnackbar(R.string.import_failed);
                            Log.e(TAG, "Unknown import error", e);
                        }
                    }
                });
    }

    private void showSnackbar(int stringResource) {
        AppSingleton.getInstance(
                getApplicationContext()).onNextActivity().subscribe(
                activity -> {
                    AccessibilityUtils.makeSnackbar(
                            activity.findViewById(R.id.drawer_layout),
                            getResources().getString(stringResource),
                            Snackbar.LENGTH_SHORT).show();
                });
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

    public static String truncate(String string, int maxLength) {
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

    private void updateProgress(ExportProgress exportProgress) {
        if (Log.isLoggable(TAG, Log.DEBUG) &&
                (exportProgress.getState() != ExportProgress.EXPORTING ||
                        exportProgress.getProgress() % 20 == 0)) {
            Log.d(TAG, "Updating progress " + exportProgress + " from " + this);
        }
        sProgressSubject.onNext(exportProgress);
    }

    @NonNull
    private File getStorageDir() {
        return new File(getFilesDir().getPath(), "exported_run_files");
    }

    @NonNull
    private Uri getFileUri(String fileName) {
        return Uri.parse(
                "content://" + getPackageName() + "/exported_runs/" + Uri.encode(fileName));
    }

    private Uri getExperimentFileUri(String fileName) {
        try {
            return FileProvider.getUriForFile(getApplicationContext(), getPackageName(), new File(
                    FileMetadataManager.getExperimentExportDirectory(getApplicationContext()),
                    fileName));
        } catch (IOException ioe) {
            Log.e(TAG, "Error getting export file", ioe);
            return null;
        }
    }

    /**
     * Removes old exported run files on the IO thread and then stops when done.
     */
    private void handleCleanOldFiles(int startId) {
        Observable.just(startId)
                .observeOn(Schedulers.io())
                .doOnComplete(() -> stopSelf(startId))
                .subscribe(id -> {
                    File storageDir = getStorageDir();
                    deleteAllFiles(storageDir);

                    File exportExperimentDir = new File(
                            FileMetadataManager.getExperimentExportDirectory(
                                    getApplicationContext()));
                    deleteAllFiles(exportExperimentDir);
                });
    }

    private void deleteAllFiles(File exportExperimentDir) {
        if (exportExperimentDir.exists()) {
            for (File file : exportExperimentDir.listFiles()) {
                file.delete();
            }
        }
    }

    public static void handleExperimentExportClick(Context context, String experimentId) {
        AppSingleton appSingleton = AppSingleton.getInstance(context);
        appSingleton.setExportServiceBusy(true);
        ExportService.bind(context)
                // Only look at events for this trial or the default value
                .filter(progress -> Objects.equals(progress.getId(), experimentId)
                        || progress.getId().equals(""))
                .observeOn(AndroidSchedulers.mainThread())
                .filter(progress -> progress.getState()
                        == ExportService.ExportProgress.EXPORT_COMPLETE)
                // Get just the next success
                .firstElement()
                .doOnSuccess(progress -> {
                    // Reset the progress only after the UI has consumed this.
                    ExportService.resetProgress(experimentId);
                })
                .subscribe(progress -> {
                    Uri fileUri = progress.getFileUri();
                    appSingleton.onNextActivity()
                            .subscribe(activity -> launchExportChooser(activity, fileUri));
                });

        ExportService.exportExperiment(context, experimentId);
    }


    public static void launchExportChooser(Context context, Uri fileUri) {
        Intent shareIntent = FileMetadataManager.getShareIntent(context, fileUri);
        AppSingleton.getInstance(context).setExportServiceBusy(false);
        context.startActivity(Intent.createChooser(shareIntent,
                context.getResources().getString(R.string.export_experiment_chooser_title)));
    }

    public static void handleExperimentImport(Context context, Uri experimentFile) {
        AppSingleton.getInstance(context).setExportServiceBusy(true);
        ExportService.bind(context)
                // Only look at events for this uri or the default value
                .filter(progress -> Objects.equals(progress.getFileUri(), experimentFile)
                        || progress.getId().equals(""))
                .observeOn(AndroidSchedulers.mainThread())
                .filter(progress -> progress.getState()
                        == ExportService.ExportProgress.EXPORT_COMPLETE)
                // Get just the next success
                .firstElement()
                .doOnSuccess(progress -> {
                    // Reset the progress only after the UI has consumed this.
                    ExportService.resetProgress(experimentFile.toString());
                })
                .subscribe(progress -> {
                    AppSingleton.getInstance(context).setExportServiceBusy(false);
                    AppSingleton.getInstance(context).onNextActivity().subscribe(activity -> {
                        activity.startActivity(
                                WhistlePunkApplication.getLaunchIntentForPanesActivity(
                                        context, progress.getId()));
                    });
                });

        ExportService.importExperiment(context, experimentFile);
    }

    private class TrialDataWriter implements Observer<ScalarReading> {

        private final long mFirstTimeStamp;
        private final long mLastTimeStamp;
        private long mCurrentTimestamp = -1;
        private long mFirstTimeStampWritten = -1;

        private ArrayMap<String, Double> mCurrentRow = new ArrayMap<>();
        private OutputStreamWriter mOutputStreamWriter;
        private final String mFileName;
        private final boolean mRelativeTime;
        private final String[] mSensorIds;
        private final String mTrialId;

        public TrialDataWriter(String trialId, String fileName, boolean relativeTime,
                String[] sensorIds, long firstTimeStamp, long lastTimeStamp) {
            mTrialId = trialId;
            mFileName = fileName;
            mRelativeTime = relativeTime;
            mSensorIds = sensorIds;
            mFirstTimeStamp = firstTimeStamp;
            mLastTimeStamp = lastTimeStamp;
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
            updateProgress(new ExportProgress(mTrialId, ExportProgress.EXPORTING, 0));
        }

        @Override
        public void onNext(ScalarReading scalarReading) {
            // Check if we have a different timestamp than the current row.
            if (scalarReading.getCollectedTimeMillis() != mCurrentTimestamp) {
                if (mCurrentRow != null && mCurrentTimestamp != -1) {
                    writeRow();
                }
                mCurrentRow.clear();
            }
            // If not, just add to current row.
            mCurrentRow.put(scalarReading.getSensorTag(), scalarReading.getValue());
            if (mCurrentTimestamp == -1) {
                mFirstTimeStampWritten = scalarReading.getCollectedTimeMillis();
            }
            mCurrentTimestamp = scalarReading.getCollectedTimeMillis();
            int progress = (int) (((mCurrentTimestamp - mFirstTimeStamp) /
                    (double) (mLastTimeStamp - mFirstTimeStamp)) * 100);
            updateProgress(new ExportProgress(mTrialId, ExportProgress.EXPORTING, progress));
        }

        @Override
        public void onError(Throwable throwable) {
            // End writing stream.
            closeStreamIfNecessary();
            updateProgress(ExportProgress.fromThrowable(mTrialId, throwable));
        }

        @Override
        public void onComplete() {
            // Write the last row if necessary.
            if (!mCurrentRow.isEmpty()) {
                writeRow();
            }

            // End writing stream.
            closeStreamIfNecessary();
            updateProgress(ExportProgress.getComplete(mTrialId, getFileUri(mFileName)));
        }

        private void writeRow() {
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
