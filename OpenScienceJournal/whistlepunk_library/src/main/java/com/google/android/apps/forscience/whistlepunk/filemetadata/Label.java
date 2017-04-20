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

package com.google.android.apps.forscience.whistlepunk.filemetadata;

import android.content.Context;
import android.os.Parcel;
import android.os.Parcelable;
import android.util.Log;

import com.google.android.apps.forscience.whistlepunk.PictureUtils;
import com.google.android.apps.forscience.whistlepunk.ProtoUtils;
import com.google.android.apps.forscience.whistlepunk.metadata.GoosciLabel;
import com.google.android.apps.forscience.whistlepunk.metadata.GoosciLabelValue;
import com.google.protobuf.nano.InvalidProtocolBufferNanoException;

import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A label, which is user- or app-generated metadata tagged with a particular timestamp.
 * All changes should be made using the getters and setters provided, rather than by getting the
 * underlying protocol buffer and making changes to that directly.
 */
public class Label implements Parcelable {
    public static final Comparator<Label> COMPARATOR_BY_TIMESTAMP = new Comparator<Label>() {
        @Override
        public int compare(Label first, Label second) {
            return Long.compare(first.getTimeStamp(), second.getTimeStamp());
        }
    };

    private static final String TAG = "label";
    private GoosciLabel.Label mLabel;
    private Map<Integer, LabelValue> mLabelValues;

    /**
     * Loads an existing label from a proto.
     */
    public static Label fromLabel(GoosciLabel.Label goosciLabel) {
        return new Label(goosciLabel);
    }

    /**
     * Creates a new label with no content.
     */
    public static Label newLabel(long creationTimeMs) {
        return new Label(creationTimeMs, java.util.UUID.randomUUID().toString());
    }

    /**
     * Creates a new label with the specified label value.
     */
    public static Label newLabelWithValue(long creationTimeMs, LabelValue labelValue) {
        Label result = new Label(creationTimeMs, java.util.UUID.randomUUID().toString());
        result.setLabelValue(labelValue);
        return result;
    }

    /**
     * Creates a deep copy of an existing label. The creation time and label ID will be different.
     */
    public static Label copyOf(Label label) {
        Parcel parcel = Parcel.obtain();
        label.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);
        Label result = Label.CREATOR.createFromParcel(parcel);
        result.getLabelProto().creationTimeMs = System.currentTimeMillis();
        result.getLabelProto().labelId = java.util.UUID.randomUUID().toString();
        return result;
    }

    private Label(GoosciLabel.Label goosciLabel) {
        mLabel = goosciLabel;
        mLabelValues = LabelValue.getLabelValues(mLabel.values);
    }

    private Label(long creationTimeMs, String labelId) {
        mLabel = new GoosciLabel.Label();
        mLabel.timestampMs = creationTimeMs;
        mLabel.creationTimeMs = creationTimeMs;
        mLabel.labelId = labelId;
        mLabelValues = new HashMap<>();
    }

    protected Label(Parcel in) {
        int serializedSize = in.readInt();
        byte[] serialized = new byte[serializedSize];
        in.readByteArray(serialized);
        try {
            mLabel = GoosciLabel.Label.parseFrom(serialized);
            mLabelValues = LabelValue.getLabelValues(mLabel.values);
        } catch (InvalidProtocolBufferNanoException ex) {
            if (Log.isLoggable(TAG, Log.ERROR)) {
                Log.e(TAG, "Couldn't parse label storage");
            }
        }
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel parcel, int i) {
        updateLabelProtoWithValues();
        parcel.writeInt(mLabel.getSerializedSize());
        parcel.writeByteArray(ProtoUtils.makeBlob(mLabel));
    }

    public static final Parcelable.Creator<Label> CREATOR = new Parcelable.Creator<Label>() {
        public Label createFromParcel(Parcel in) {
            return new Label(in);
        }

        @Override
        public Label[] newArray(int size) {
            return new Label[size];
        }
    };

    public GoosciLabel.Label getLabelProto() {
        updateLabelProtoWithValues();
        return mLabel;
    }

    public String getLabelId() {
        return mLabel.labelId;
    }

    public long getTimeStamp() {
        return mLabel.timestampMs;
    }

    public void setTimestamp(long timestampMs) {
        mLabel.timestampMs = timestampMs;
    }

    public long getCreationTimeMs() {
        return mLabel.creationTimeMs;
    }

    public boolean canEditTimestamp() {
        boolean result = false;
        for (int type : mLabelValues.keySet()) {
            result |= mLabelValues.get(type).canEditTimestamp();
        }
        return result;
    }

    /**
     * Labels can have multiple value types, which allows us flexibility to have
     * users add pictures, text, data, etc to one label instance.
     * Labels may only have one of each type value, however -- i.e. one picture, one
     * trigger, etc.
     * @return a set of GoosciLabelValue.Type enum
     */
    public Set<Integer> getValueTypes() {
        return mLabelValues.keySet();
    }

    /**
     * Checks whether this label contains a particular value type.
     */
    public boolean hasValueType(int valueType) {
        return mLabelValues.containsKey(valueType);
    }

    /**
     * Gets the LabelValue for a particular value type.
     */
    public LabelValue getLabelValue(int valueType) {
        return mLabelValues.get(valueType);
    }

    /**
     * Sets the LabelValue for a particular label type.
     * This will replace any existing value for that type.
     */
    public void setLabelValue(LabelValue value) {
        mLabelValues.put(value.getValue().type, value);
    }

    /**
     * Gets all the values of this label.
     */
    public List<LabelValue> getLabelValues() {
        return new ArrayList<>(mLabelValues.values());
    }

    /**
     * Deletes any assets associated with this label
     */
    public void deleteAssets(Context context) {
        // TODO: Delete any assets associated with this label, including pictures, etc.
        // May need a reference to the FileMetadataManager to do this.
        if (hasValueType(GoosciLabelValue.LabelValue.PICTURE)) {
            File file = new File(((PictureLabelValue) getLabelValue(
                    GoosciLabelValue.LabelValue.PICTURE)).getAbsoluteFilePath());
            boolean deleted = file.delete();
            if (!deleted) {
                Log.w(TAG, "Could not delete " + file.toString());
            }
            PictureUtils.scanFile(file.getAbsolutePath(), context);
        }
    }

    private void updateLabelProtoWithValues() {
        GoosciLabelValue.LabelValue[] result = new GoosciLabelValue.LabelValue[mLabelValues.size()];
        int index = 0;
        for (LabelValue value : mLabelValues.values()) {
            result[index++] = value.getValue();
        }
        mLabel.values = result;
    }
}
