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
    public boolean addData(final long timestampMillis, final double value) {
        if (mFirstTimeStampWritten < 0) {
            mFirstTimeStampWritten = timestampMillis;
        }
        try {
            if (mOutputStreamWriter == null) {
                mFailureListener.fail(new IllegalStateException("Output stream closed."));
                return false;
            }
            mOutputStreamWriter.write(getTimestampString(timestampMillis));
            mOutputStreamWriter.write(",");
            mOutputStreamWriter.write(Double.toString(value));
            mOutputStreamWriter.write("\n");
            mLastTimeStampWritten = timestampMillis;

        } catch (IOException e) {
            mFailureListener.fail(e);
            return false;
        }
        return true;
    }

    private String getTimestampString(long time) {
        return Long.toString(mStartAtZero ? time - mFirstTimeStampWritten : time);
    }

    public long getLastTimeStampWritten() {
        return mLastTimeStampWritten;
    }
}
