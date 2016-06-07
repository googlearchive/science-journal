package com.google.android.apps.forscience.whistlepunk.review;

import android.content.Context;
import android.net.Uri;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.support.annotation.NonNull;
import android.util.Log;

import com.google.android.apps.forscience.javalib.MaybeConsumer;
import com.google.android.apps.forscience.whistlepunk.DataController;
import com.google.android.apps.forscience.whistlepunk.metadata.ExperimentRun;
import com.google.android.apps.forscience.whistlepunk.sensorapi.StreamConsumer;
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
    private StreamConsumer mStreamConsumer;
    private String mFileName;
    private ExperimentRun mRun;
    private long mLastTimeStampWritten = -1;
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

    public void startExport(Context context, final String experimentName, final ExperimentRun run,
            String sensorTag) {
        mStop = false;
        mRun = run;
        mSensorTag = sensorTag;
        mContext = context.getApplicationContext();
        mHandlerThread = new HandlerThread("export", Thread.MIN_PRIORITY);
        mHandlerThread.start();
        mHandler = new Handler(mHandlerThread.getLooper(), this);
        mStreamConsumer = new StreamConsumer() {
            @Override
            public void addData(final long timestampMillis, final double value) {
                try {
                    mOutputStreamWriter.write(Long.toString(timestampMillis));
                    mOutputStreamWriter.write(",");
                    mOutputStreamWriter.write(Double.toString(value));
                    mOutputStreamWriter.write("\n");
                    mLastTimeStampWritten = timestampMillis;

                } catch (IOException e) {
                    mListener.onExportError(e);
                    return;
                }
            }
        };
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

                File file = new File(storageDir.getPath(), sanitizeFilename(experimentName + " "
                        + run.getRunTitle(mContext)) + ".csv");
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
                    mOutputStreamWriter.write("timestamp");
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

        Range<Long> times = Range.closed(mRun.getFirstTimestamp(), mRun.getLastTimestamp());
        getReadings(TimeRange.oldest(times));
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
    public void addReadings(ScalarReadingList readings) {
        // Check if we are not stopped.
        if (mHandler != null) {
            mHandler.sendMessage(Message.obtain(mHandler, MSG_WRITE, readings));
        }
    }

    public void endExport() {
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
                    "content://com.google.android.apps.forscience.whistlepunk/exported_runs/"
                            + mFileName);
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
            if (mStop) {
                return false;
            }
            final long start = mRun.getFirstTimestamp();
            final long end = mRun.getLastTimestamp();
            int progress = (int) (((mLastTimeStampWritten - start) / (double) (end - start)) * 100);
            mListener.onExportProgress(progress);
            if (list.size() == 0 || list.size() < MAX_RECORDS || mLastTimeStampWritten >= end) {
                endExport();
            } else {
                Range<Long> times = Range.openClosed(mLastTimeStampWritten, end);
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
