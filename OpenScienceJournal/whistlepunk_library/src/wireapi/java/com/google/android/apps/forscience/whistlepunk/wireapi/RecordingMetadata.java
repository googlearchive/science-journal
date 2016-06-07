package com.google.android.apps.forscience.whistlepunk.wireapi;

import android.os.Parcel;
import android.os.Parcelable;

public class RecordingMetadata implements Parcelable {
    /**
     * "Timestamp" that indicates that recording has not started.
     */
    public static final int NOT_RECORDING = -1;

    protected RecordingMetadata(Parcel in) {
        mStartTime = in.readLong();
        mRunId = in.readString();
        mExperimentName = in.readString();
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeLong(mStartTime);
        dest.writeString(mRunId);
        dest.writeString(mExperimentName);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public static final Creator<RecordingMetadata> CREATOR = new Creator<RecordingMetadata>() {
        @Override
        public RecordingMetadata createFromParcel(Parcel in) {
            return new RecordingMetadata(in);
        }

        @Override
        public RecordingMetadata[] newArray(int size) {
            return new RecordingMetadata[size];
        }
    };

    public static long getStartTime(RecordingMetadata recording) {
        return recording != null ? recording.getStartTime() : NOT_RECORDING;
    }

    private long mStartTime;
    private String mRunId;
    private String mExperimentName;

    public RecordingMetadata(long startTime, String runId, String experimentName) {
        mStartTime = startTime;
        mRunId = runId;
        mExperimentName = experimentName;
    }

    public long getStartTime() {
        return mStartTime;
    }

    public String getRunId() {
        return mRunId;
    }

    public String getExperimentName() {
        return mExperimentName;
    }
}
