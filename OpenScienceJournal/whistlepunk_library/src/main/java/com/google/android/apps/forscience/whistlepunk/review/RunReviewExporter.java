/*
 *  Copyright 2016 Google Inc. All Rights Reserved.
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

package com.google.android.apps.forscience.whistlepunk.review;

import android.content.Context;
import android.net.Uri;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.support.annotation.NonNull;
import android.support.annotation.VisibleForTesting;
import android.util.Log;

import com.google.android.apps.forscience.javalib.FailureListener;
import com.google.android.apps.forscience.javalib.MaybeConsumer;
import com.google.android.apps.forscience.whistlepunk.DataController;
import com.google.android.apps.forscience.whistlepunk.filemetadata.Trial;
import com.google.android.apps.forscience.whistlepunk.sensordb.ScalarReadingList;
import com.google.android.apps.forscience.whistlepunk.sensordb.TimeRange;
import com.google.common.collect.Range;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;

public class RunReviewExporter implements Handler.Callback {
    private static final String TAG = "RunReviewExporter";

    private static final int MSG_WRITE = 121;
    private static final int MAX_RECORDS = 500;

    private final DataController mDataController;
    private final Listener mListener;

    private Context mContext;
    private OutputStreamWriter mOutputStreamWriter;
    private ExportStreamConsumer mStreamConsumer;
    private String mFileName;
    private Trial mTrial;
    private boolean mStop;

    private HandlerThread mHandlerThread;
    private Handler mHandler;
    private String mSensorTag;

    public interface Listener {
        /**
         * Called when export has started.
         */
        public void onExportStarted();

        /**
         * Called when export has progress to report.
         *
         * @param progress 0 to 100
         */
        public void onExportProgress(int progress);

        /**
         * Called when export has finished.
         *
         * @param uri URI to the finished file or {@code null} if this was stopped.
         */
        public void onExportEnd(Uri uri);

        /**
         * Called if the export has an error.
         */
        public void onExportError(Exception e);
    }

    public RunReviewExporter(DataController dataController, Listener listener) {
        mDataController = dataController;
        mListener = listener;
    }

    /**
     * @param trial
     * @param startAtZero if true, adjusts all timestamps so that the first timestamp is reported
     */
    public void startExport(Context context, final String experimentName, final Trial trial,
            String sensorTag, final boolean startAtZero) {
        // TODO: can some of these be constructor parameters?
        mStop = false;
        mTrial = trial;
        mSensorTag = sensorTag;
        mContext = context.getApplicationContext();
        mHandlerThread = new HandlerThread("export", Thread.MIN_PRIORITY);
        mHandlerThread.start();
        mHandler = new Handler(mHandlerThread.getLooper(), this);
        mStreamConsumer = new ExportStreamConsumer(mOutputStreamWriter, startAtZero, new FailureListener() {
            @Override
            public void fail(Exception e) {
                mListener.onExportError(e);
            }
        });
        mHandler.post(new Runnable() {

            @Override
            public void run() {
                File storageDir = getStorageDir(mContext);

                // Create the storage directory if it does not exist
                if (!storageDir.exists()) {
                    if (!storageDir.mkdirs()) {
                        Log.e(TAG, "failed to create directory");
                        mListener.onExportError(new IOException("Could not create dir "
                                + storageDir.getAbsolutePath()));
                        return;
                    }
                }

                File file = new File(storageDir.getPath(),
                        makeExportFilename(experimentName, trial, mContext));
                mFileName = file.getName();
                FileOutputStream fs;
                try {
                    fs = new FileOutputStream(file);
                } catch (FileNotFoundException e) {
                    mListener.onExportError(e);
                    return;
                }

                mOutputStreamWriter = new OutputStreamWriter(fs);
                try {
                    mOutputStreamWriter.write(startAtZero ? "relative_time" : "timestamp");
                    mOutputStreamWriter.write(",");
                    mOutputStreamWriter.write("value");
                    mOutputStreamWriter.write("\n");
                } catch (IOException e) {
                    mListener.onExportError(e);
                    return;
                }
            }
        });

        mListener.onExportStarted();

        Range<Long> times = Range.closed(mTrial.getFirstTimestamp(), mTrial.getLastTimestamp());
        getReadings(TimeRange.oldest(times));
    }

    @NonNull
    @VisibleForTesting
    public static String makeExportFilename(String experimentName, Trial trial,
            Context context) {
        // 40 chars of experimentname + 35 chars of run title + " " + ".csv" = 80 chars
        return sanitizeFilename(truncate(experimentName, 40)
                                + " "
                                + truncate(trial.getTitle(context), 35)
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

    @NonNull
    private static File getStorageDir(Context context) {
        return new File(context.getFilesDir().getPath() + "/exported_run_files");
    }

    private void getReadings(TimeRange range) {
        mDataController.getScalarReadings(mSensorTag, 0, range, MAX_RECORDS,
                new MaybeConsumer<ScalarReadingList>() {
                    @Override
                    public void success(ScalarReadingList value) {
                        addReadings(value);
                    }

                    @Override
                    public void fail(Exception e) {
                        mListener.onExportError(e);
                    }
                });
    }

    /**
     * Adds readings to the output stream.
     */
    private void addReadings(ScalarReadingList readings) {
        // Check if we are not stopped.
        if (mHandler != null) {
            mHandler.sendMessage(Message.obtain(mHandler, MSG_WRITE, readings));
        }
    }

    private void endExport() {
        mHandler.post(new Runnable() {

            @Override
            public void run() {
                closeStreamIfNecessary();
                notifyListener();
                destroy();
            }
        });
    }

    private void notifyListener() {
        Uri fileUri = null;
        if (!mStop) {
            fileUri = Uri.parse(
                    "content://" + mContext.getPackageName() + "/exported_runs/" + mFileName);
        }
        mListener.onExportEnd(fileUri);
    }

    private void closeStreamIfNecessary() {
        if (mOutputStreamWriter != null) {
            try {
                mOutputStreamWriter.close();
                mOutputStreamWriter = null;
            } catch (IOException e) {
                Log.e(TAG, "File close failed: " + e.toString());
                mListener.onExportError(e);
                return;
            }
        }
    }

    /**
     * Stops the exporter in case we need to shut it down.
     */
    public void stop() {
        mStop = true;
        mHandler.removeMessages(MSG_WRITE);
        closeStreamIfNecessary();
        destroy();
        mListener.onExportEnd(null);
    }

    public void destroy() {
        mHandlerThread.quitSafely();
        mHandlerThread = null;
        mHandler = null;
        mContext = null;
        mStop = false;
    }

    @Override
    public boolean handleMessage(Message msg) {
        if (msg.what == MSG_WRITE) {
            if (mStop) {
                return false;
            }
            ScalarReadingList list = (ScalarReadingList) msg.obj;
            list.deliver(mStreamConsumer);
            long lastTimeStampWritten = mStreamConsumer.getLastTimeStampWritten();
            if (mStop) {
                return false;
            }
            final long start = mTrial.getFirstTimestamp();
            final long end = mTrial.getLastTimestamp();
            int progress = (int) (((lastTimeStampWritten - start) / (double) (end - start)) * 100);
            mListener.onExportProgress(progress);
            if (list.size() == 0 || list.size() < MAX_RECORDS || lastTimeStampWritten >= end) {
                endExport();
            } else {
                Range<Long> times = Range.openClosed(lastTimeStampWritten, end);
                getReadings(TimeRange.oldest(times));
            }
            return true;
        }
        return false;
    }

    public boolean isExporting() {
        return mOutputStreamWriter != null;
    }

    public static void cleanOldFiles(Context context) {
        final HandlerThread thread = new HandlerThread("delete", Thread.MIN_PRIORITY);
        thread.start();
        Handler handler = new Handler(thread.getLooper());
        final File storageDir = getStorageDir(context);
        handler.post(new Runnable() {

            @Override
            public void run() {
                if (storageDir.exists()) {
                    for (File file : storageDir.listFiles()) {
                        file.delete();
                    }
                }
                thread.quitSafely();
            }
        });
    }

    public static String sanitizeFilename(String inputName) {
        return inputName.replaceAll("[^ a-zA-Z0-9-_\\.]", "_");
    }

};
