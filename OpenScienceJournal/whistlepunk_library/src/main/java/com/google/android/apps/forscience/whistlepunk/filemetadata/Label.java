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
import com.google.android.apps.forscience.whistlepunk.metadata.GoosciCaption;
import com.google.android.apps.forscience.whistlepunk.metadata.GoosciLabel;
import com.google.android.apps.forscience.whistlepunk.metadata.GoosciPictureLabelValue;
import com.google.android.apps.forscience.whistlepunk.metadata.GoosciSensorTriggerLabelValue;
import com.google.android.apps.forscience.whistlepunk.metadata.GoosciSnapshotValue;
import com.google.android.apps.forscience.whistlepunk.metadata.GoosciTextLabelValue;
import com.google.protobuf.nano.InvalidProtocolBufferNanoException;
import com.google.protobuf.nano.MessageNano;

import java.io.File;
import java.util.Comparator;

/**
 * A label, which is user- or app-generated metadata tagged with a particular timestamp.
 * All changes should be made using the getters and setters provided, rather than by getting the
 * underlying protocol buffer and making changes to that directly.
 */
public class Label implements Parcelable {
    public static final Comparator<Label> COMPARATOR_BY_TIMESTAMP =
            (first, second) -> Long.compare(first.getTimeStamp(), second.getTimeStamp());

    private static final String TAG = "label";
    private GoosciLabel.Label mLabel;

    /**
     * Loads an existing label from a proto.
     */
    public static Label fromLabel(GoosciLabel.Label goosciLabel) {
        return new Label(goosciLabel);
    }

    /**
     * Creates a new label with no content.
     */
    public static Label newLabel(long creationTimeMs, int valueType) {
        return new Label(creationTimeMs, java.util.UUID.randomUUID().toString(), valueType);
    }

    /**
     * Creates a new label with the specified label value.
     */
    public static Label newLabelWithValue(long creationTimeMs, int type, MessageNano data,
            GoosciCaption.Caption caption) {
        Label result = new Label(creationTimeMs, java.util.UUID.randomUUID().toString(), type);
        result.setLabelProtoData(data);
        result.setCaption(caption);
        return result;
    }

    public static Label fromUuidAndValue(long creationTimeMs, String uuid,  int type,
            MessageNano data) {
        Label result = new Label(creationTimeMs, uuid, type);
        result.setLabelProtoData(data);
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
        if (result.getLabelProto().caption != null) {
            result.getLabelProto().caption.lastEditedTimestamp = System.currentTimeMillis();
        }
        return result;
    }

    private Label(GoosciLabel.Label goosciLabel) {
        mLabel = goosciLabel;
    }

    private Label(long creationTimeMs, String labelId, int valueType) {
        mLabel = new GoosciLabel.Label();
        mLabel.timestampMs = creationTimeMs;
        mLabel.creationTimeMs = creationTimeMs;
        mLabel.labelId = labelId;
        mLabel.type = valueType;

    }

    protected Label(Parcel in) {
        int serializedSize = in.readInt();
        byte[] serialized = new byte[serializedSize];
        in.readByteArray(serialized);
        try {
            mLabel = GoosciLabel.Label.parseFrom(serialized);
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

    // You cannot edit the timestamp of some labels, like Snapshot and Trigger labels.
    public boolean canEditTimestamp() {
        return (mLabel.type != GoosciLabel.Label.SNAPSHOT && mLabel.type !=
                GoosciLabel.Label.SENSOR_TRIGGER);
    }

    public String getCaptionText() {
        if (mLabel.caption == null) {
            return "";
        }
        return mLabel.caption.text;
    }

    public void setCaption(GoosciCaption.Caption caption) {
        mLabel.caption = caption;
    }

    public int getType() {
        return mLabel.type;
    }

    /**
     * Gets the GoosciTextLabelValue.TextLabelValue for this label.
     * If changes are made, this needs to be re-set on the Label for them to be saved.
     */
    public GoosciTextLabelValue.TextLabelValue getTextLabelValue() {
        if (mLabel.type == GoosciLabel.Label.TEXT) {
            try {
                return GoosciTextLabelValue.TextLabelValue.parseFrom(mLabel.protoData);
            } catch (InvalidProtocolBufferNanoException e) {
                if (Log.isLoggable(TAG, Log.ERROR)) {
                    Log.e(TAG, e.getMessage());
                }
            }
        } else {
            throwLabelValueException("TextLabelValue", mLabel.type);
        }
        return null;
    }

    /**
     * Gets the PictureLabelValue for this label.
     * If changes are made, this needs to be re-set on the Label for them to be saved.
     */
    public GoosciPictureLabelValue.PictureLabelValue getPictureLabelValue() {
        if (mLabel.type == GoosciLabel.Label.PICTURE) {
            try {
                return GoosciPictureLabelValue.PictureLabelValue.parseFrom(mLabel.protoData);
            } catch (InvalidProtocolBufferNanoException e) {
                if (Log.isLoggable(TAG, Log.ERROR)) {
                    Log.e(TAG, e.getMessage());
                }
            }
        } else {
            throwLabelValueException("PictureLabelValue", mLabel.type);
        }
        return null;
    }

    /**
     * Gets the SensorTriggerLabelValue for this label.
     * If changes are made, this needs to be re-set on the Label for them to be saved.
     */
    public GoosciSensorTriggerLabelValue.SensorTriggerLabelValue getSensorTriggerLabelValue() {
        if (mLabel.type == GoosciLabel.Label.SENSOR_TRIGGER) {
            try {
                return GoosciSensorTriggerLabelValue.SensorTriggerLabelValue.parseFrom(
                        mLabel.protoData);
            } catch (InvalidProtocolBufferNanoException e) {
                if (Log.isLoggable(TAG, Log.ERROR)) {
                    Log.e(TAG, e.getMessage());
                }
            }
        } else {
            throwLabelValueException("SensorTriggerLabelValue", mLabel.type);
        }
        return null;
    }

    /**
     * Gets the SnapshotLabelValue for this label.
     * If changes are made, this needs to be re-set on the Label for them to be saved.
     */
    public GoosciSnapshotValue.SnapshotLabelValue getSnapshotLabelValue() {
        if (mLabel.type == GoosciLabel.Label.SNAPSHOT) {
            try {
                return GoosciSnapshotValue.SnapshotLabelValue.parseFrom(mLabel.protoData);
            } catch (InvalidProtocolBufferNanoException e) {
                if (Log.isLoggable(TAG, Log.ERROR)) {
                    Log.e(TAG, e.getMessage());
                }
            }
        } else {
            throwLabelValueException("SnapshotLabelValue", mLabel.type);
        }
        return null;
    }

    /**
     * Sets the proto data and type on this label. This must be done in order to save changes
     * back to the label that occur on the protoData field.
     */
    public void setLabelProtoData(MessageNano data) {
        mLabel.protoData = MessageNano.toByteArray(data);
    }

    /**
     * Deletes any assets associated with this label
     */
    public void deleteAssets(Context context, String experimentId) {
        if (mLabel.type == GoosciLabel.Label.PICTURE) {
            File file = new File(PictureUtils.getExperimentImagePath(context, experimentId,
                    getPictureLabelValue().filePath));
            boolean deleted = file.delete();
            if (!deleted && Log.isLoggable(TAG, Log.WARN)) {
                Log.w(TAG, "Could not delete " + file.toString());
            }
        }
    }

    @Override
    public String toString() {
        return mLabel.labelId + ": time: " + mLabel.timestampMs + ", type:" + mLabel.type;
    }

    private static void throwLabelValueException(String protoToCreate, int actualType) {
        throw new IllegalStateException(String.format("Cannot get %s from label of type %s",
                protoToCreate, actualType));
    }
}
