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

package com.google.android.apps.forscience.whistlepunk.metadata;

import android.os.Parcel;
import android.os.Parcelable;
import android.util.Log;

import com.google.android.apps.forscience.whistlepunk.ProtoUtils;
import com.google.protobuf.nano.InvalidProtocolBufferNanoException;

/**
 * A Label can be attached to an experiment or project, and is associated with a particular
 * run / recording session based on its startLabelId.
 */
public abstract class Label implements Parcelable {

    private static final String TAG = "label";

    /**
     * Experiment ID this label belongs to.
     */
    private String mExperimentId;

    /**
     * Time in ms since the epoch which represents this label.
     */
    private long mTimestamp;

    /**
     * The unique ID of the label.
     */
    private String mLabelId;

    /**
     * Associates a label with a run.
     * When recording starts, a START ApplicationLabel is generated with a particular ID. This ID
     * is used as the startLabelId for all other labels associated with that run.
     */
    private String mRunId;

    /**
     * The value of this label.
     */
    private GoosciLabelValue.LabelValue mValue;

    public Label(String id, String startLabelId, long timestamp,
            GoosciLabelValue.LabelValue value) {
        this(id, startLabelId, timestamp);
        mValue = value;
    }

    protected Label(String id, String startLabelId, long timestamp) {
        mTimestamp = timestamp;
        mLabelId = id;
        mRunId = startLabelId;
    }

    public long getTimeStamp() {
        return mTimestamp;
    }

    /**
     * @return a unique tag for this type of label.
     */
    public abstract String getTag();

    /**
     * @return Bundle value of this label's contents.
     */
    public GoosciLabelValue.LabelValue getValue() {
        return mValue;
    };

    public void setTimestamp(long timestamp) {
        mTimestamp = timestamp;
    }

    public String getExperimentId() {
        return mExperimentId;
    }

    public void setExperimentId(String experimentId) {
        mExperimentId = experimentId;
    }

    public String getLabelId() {
        return mLabelId;
    }

    public String getRunId() {
        return mRunId;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    protected Label() {
        // Creates an empty label.
    }

    protected void setValue(GoosciLabelValue.LabelValue value) {
        mValue = value;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(mLabelId);
        dest.writeString(mRunId);
        dest.writeString(mExperimentId);
        dest.writeLong(this.getTimeStamp());
        dest.writeInt(mValue.getSerializedSize());
        dest.writeByteArray(ProtoUtils.makeBlob(mValue));
    }

    protected void populateFromParcel(Parcel in) {
        String labelId = in.readString();
        String runId = in.readString();
        String experimentId = in.readString();
        long timestamp = in.readLong();
        int serializedSize = in.readInt();
        byte[] serialized = new byte[serializedSize];
        in.readByteArray(serialized);
        try {
            mValue = GoosciLabelValue.LabelValue.parseFrom(serialized);
        } catch (InvalidProtocolBufferNanoException ex) {
            Log.e(TAG, "Couldn't parse label storage");
        }
        mLabelId = labelId;
        mRunId = runId;
        mExperimentId = experimentId;
        mTimestamp = timestamp;
    }
}
