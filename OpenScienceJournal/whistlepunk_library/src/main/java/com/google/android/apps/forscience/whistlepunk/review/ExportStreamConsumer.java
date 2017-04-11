package com.google.android.apps.forscience.whistlepunk.review;

import com.google.android.apps.forscience.javalib.FailureListener;
import com.google.android.apps.forscience.whistlepunk.sensorapi.StreamConsumer;

import java.io.IOException;
import java.io.OutputStreamWriter;

public class ExportStreamConsumer implements StreamConsumer {
    private FailureListener mFailureListener;
    private OutputStreamWriter mOutputStreamWriter;
    private boolean mStartAtZero;
    private long mFirstTimeStampWritten = -1;
    private long mLastTimeStampWritten = -1;

    /**
     * @param startAtZero if true, adjusts all timestamps so that the first timestamp is reported
     *                    as 0.
     */
    public ExportStreamConsumer(OutputStreamWriter outputStreamWriter,
            boolean startAtZero, FailureListener failureListener) {
        this.mFailureListener = failureListener;
        this.mOutputStreamWriter = outputStreamWriter;
        mStartAtZero = startAtZero;
    }

    @Override
    public void addData(final long timestampMillis, final double value) {
        if (mFirstTimeStampWritten < 0) {
            mFirstTimeStampWritten = timestampMillis;
        }
        try {
            if (mOutputStreamWriter == null) {
                mFailureListener.fail(new IllegalStateException("Output stream closed."));
                return;
            }
            mOutputStreamWriter.write(getTimestampString(timestampMillis));
            mOutputStreamWriter.write(",");
            mOutputStreamWriter.write(Double.toString(value));
            mOutputStreamWriter.write("\n");
            mLastTimeStampWritten = timestampMillis;

        } catch (IOException e) {
            mFailureListener.fail(e);
            return;
        }
    }

    private String getTimestampString(long time) {
        return Long.toString(mStartAtZero ? time - mFirstTimeStampWritten : time);
    }

    public long getLastTimeStampWritten() {
        return mLastTimeStampWritten;
    }
}
