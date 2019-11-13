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
import com.google.android.apps.forscience.whistlepunk.accounts.AppAccount;
import com.google.android.apps.forscience.whistlepunk.metadata.GoosciCaption;
import com.google.android.apps.forscience.whistlepunk.metadata.GoosciCaption.Caption;
import com.google.android.apps.forscience.whistlepunk.metadata.GoosciLabel;
import com.google.android.apps.forscience.whistlepunk.metadata.GoosciLabel.Label.ValueType;
import com.google.android.apps.forscience.whistlepunk.metadata.GoosciPictureLabelValue;
import com.google.android.apps.forscience.whistlepunk.metadata.GoosciSensorTriggerLabelValue;
import com.google.android.apps.forscience.whistlepunk.metadata.GoosciSnapshotValue;
import com.google.android.apps.forscience.whistlepunk.metadata.GoosciTextLabelValue;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.MessageLite;
import java.io.File;
import java.util.Comparator;

/**
 * A label, which is user- or app-generated metadata tagged with a particular timestamp. All changes
 * should be made using the getters and setters provided, rather than by getting the underlying
 * protocol buffer and making changes to that directly.
 */
public class Label implements Parcelable {
  public static final Comparator<Label> COMPARATOR_BY_TIMESTAMP =
      (first, second) -> Long.compare(first.getTimeStamp(), second.getTimeStamp());

  private static final String TAG = "label";

  private GoosciLabel.Label.Builder labelBuilder = GoosciLabel.Label.newBuilder();

  /** Loads an existing label from a proto. */
  public static Label fromLabel(GoosciLabel.Label goosciLabel) {
    return new Label(goosciLabel);
  }

  /** Creates a new label with no content. */
  public static Label newLabel(long creationTimeMs, ValueType valueType) {
    return new Label(creationTimeMs, java.util.UUID.randomUUID().toString(), valueType);
  }

  /** Creates a new label with the specified label value. */
  public static Label newLabelWithValue(
      long creationTimeMs, ValueType type, MessageLite data, GoosciCaption.Caption caption) {
    Label result = new Label(creationTimeMs, java.util.UUID.randomUUID().toString(), type);
    result.setLabelProtoData(data);
    result.setCaption(caption);
    return result;
  }

  public static Label fromUuidAndValue(
      long creationTimeMs, String uuid, ValueType type, MessageLite data) {
    Label result = new Label(creationTimeMs, uuid, type);
    result.setLabelProtoData(data);
    return result;
  }

  /** Creates a deep copy of an existing label. The creation time and label ID will be different. */
  public static Label copyOf(Label label) {
    Parcel parcel = Parcel.obtain();
    label.writeToParcel(parcel, 0);
    parcel.setDataPosition(0);
    Label result = Label.CREATOR.createFromParcel(parcel);
    result.labelBuilder.setCreationTimeMs(System.currentTimeMillis());
    result.labelBuilder.setLabelId(java.util.UUID.randomUUID().toString());
    if (result.labelBuilder.hasCaption()) {
      Caption newCaption =
          result.labelBuilder.getCaption().toBuilder()
              .setLastEditedTimestamp(System.currentTimeMillis())
              .build();
      result.labelBuilder.setCaption(newCaption);
    }
    return result;
  }

  private Label(GoosciLabel.Label goosciLabel) {
    initializeFromProto(goosciLabel);
  }

  private Label(long creationTimeMs, String labelId, ValueType valueType) {
    labelBuilder.setLabelId(labelId);
    labelBuilder.setTimestampMs(creationTimeMs);
    labelBuilder.setCreationTimeMs(creationTimeMs);
    labelBuilder.setType(valueType);
  }

  protected Label(Parcel in) {
    int serializedSize = in.readInt();
    // readByteArray(byte[]) appears to be broken in robolectric currently
    // createByteArray() is an alternative
    // byte[] serialized = new byte[serializedSize];
    // in.readByteArray(serialized);
    byte[] serialized = in.createByteArray();
    try {
      initializeFromProto(GoosciLabel.Label.parseFrom(serialized));
    } catch (InvalidProtocolBufferException ex) {
      if (Log.isLoggable(TAG, Log.ERROR)) {
        Log.e(TAG, "Couldn't parse label storage");
      }
    }
  }

  private void initializeFromProto(GoosciLabel.Label goosciLabel) {
    labelBuilder = goosciLabel.toBuilder();
  }

  @Override
  public int describeContents() {
    return 0;
  }

  @Override
  public void writeToParcel(Parcel parcel, int i) {
    GoosciLabel.Label goosciLabel = getLabelProto();
    parcel.writeInt(goosciLabel.getSerializedSize());
    parcel.writeByteArray(goosciLabel.toByteArray());
  }

  public static final Parcelable.Creator<Label> CREATOR =
      new Parcelable.Creator<Label>() {
        public Label createFromParcel(Parcel in) {
          return new Label(in);
        }

        @Override
        public Label[] newArray(int size) {
          return new Label[size];
        }
      };

  /**
   * Gets the proto underlying this Label. The resulting proto should *not* be modified outside of
   * this class because changes to it will not be saved.
   *
   * @return The label's underlying protocolbuffer.
   */
  public GoosciLabel.Label getLabelProto() {
    return labelBuilder.build();
  }

  public String getLabelId() {
    return labelBuilder.getLabelId();
  }

  public long getTimeStamp() {
    return labelBuilder.getTimestampMs();
  }

  public void setTimestamp(long timestampMs) {
    labelBuilder.setTimestampMs(timestampMs);
  }

  public long getCreationTimeMs() {
    return labelBuilder.getCreationTimeMs();
  }

  // You cannot edit the timestamp of some labels, like Snapshot and Trigger labels.
  public boolean canEditTimestamp() {
    return (labelBuilder.getType() != ValueType.SNAPSHOT
        && labelBuilder.getType() != ValueType.SENSOR_TRIGGER);
  }

  public String getCaptionText() {
    return labelBuilder.getCaption().getText();
  }

  public void setCaption(GoosciCaption.Caption caption) {
    if (caption == null) {
      labelBuilder.clearCaption();
    } else {
      labelBuilder.setCaption(caption);
    }
  }

  public ValueType getType() {
    return labelBuilder.getType();
  }

  /**
   * Gets the GoosciTextLabelValue.TextLabelValue for this label. If changes are made, this needs to
   * be re-set on the Label for them to be saved.
   */
  public GoosciTextLabelValue.TextLabelValue getTextLabelValue() {
    if (labelBuilder.getType() == ValueType.TEXT) {
      try {
        return GoosciTextLabelValue.TextLabelValue.parseFrom(labelBuilder.getProtoData());
      } catch (InvalidProtocolBufferException e) {
        if (Log.isLoggable(TAG, Log.ERROR)) {
          Log.e(TAG, e.getMessage());
        }
      }
    } else {
      throwLabelValueException("TextLabelValue", labelBuilder.getType());
    }
    return null;
  }

  /**
   * Gets the PictureLabelValue for this label. If changes are made, this needs to be re-set on the
   * Label for them to be saved.
   */
  public GoosciPictureLabelValue.PictureLabelValue getPictureLabelValue() {
    if (labelBuilder.getType() == ValueType.PICTURE) {
      try {
        return GoosciPictureLabelValue.PictureLabelValue.parseFrom(labelBuilder.getProtoData());
      } catch (InvalidProtocolBufferException e) {
        if (Log.isLoggable(TAG, Log.ERROR)) {
          Log.e(TAG, e.getMessage());
        }
      }
    } else {
      throwLabelValueException("PictureLabelValue", labelBuilder.getType());
    }
    return null;
  }

  /**
   * Gets the SensorTriggerLabelValue for this label. If changes are made, this needs to be re-set
   * on the Label for them to be saved.
   */
  public GoosciSensorTriggerLabelValue.SensorTriggerLabelValue getSensorTriggerLabelValue() {
    if (labelBuilder.getType() == ValueType.SENSOR_TRIGGER) {
      try {
        return GoosciSensorTriggerLabelValue.SensorTriggerLabelValue.parseFrom(
            labelBuilder.getProtoData());
      } catch (InvalidProtocolBufferException e) {
        if (Log.isLoggable(TAG, Log.ERROR)) {
          Log.e(TAG, e.getMessage());
        }
      }
    } else {
      throwLabelValueException("SensorTriggerLabelValue", labelBuilder.getType());
    }
    return null;
  }

  /**
   * Gets the SnapshotLabelValue for this label. If changes are made, this needs to be re-set on the
   * Label for them to be saved.
   */
  public GoosciSnapshotValue.SnapshotLabelValue getSnapshotLabelValue() {
    if (labelBuilder.getType() == ValueType.SNAPSHOT) {
      try {
        return GoosciSnapshotValue.SnapshotLabelValue.parseFrom(labelBuilder.getProtoData());
      } catch (InvalidProtocolBufferException e) {
        if (Log.isLoggable(TAG, Log.ERROR)) {
          Log.e(TAG, e.getMessage());
        }
      }
    } else {
      throwLabelValueException("SnapshotLabelValue", labelBuilder.getType());
    }
    return null;
  }

  /**
   * Sets the proto data and type on this label. This must be done in order to save changes back to
   * the label that occur on the protoData field.
   */
  public void setLabelProtoData(MessageLite data) {
    labelBuilder.setProtoData(data.toByteString());
  }

  /** Deletes any assets associated with this label */
  public void deleteAssets(Context context, AppAccount appAccount, String experimentId) {
    if (labelBuilder.getType() == ValueType.PICTURE) {
      File file =
          new File(
              PictureUtils.getExperimentImagePath(
                  context, appAccount, experimentId, getPictureLabelValue().getFilePath()));
      boolean deleted = file.delete();
      if (!deleted && Log.isLoggable(TAG, Log.WARN)) {
        Log.w(TAG, "Could not delete " + file.toString());
      }
    }
  }

  @Override
  public String toString() {
    return labelBuilder.getLabelId()
        + ": time: "
        + labelBuilder.getTimestampMs()
        + ", type:"
        + getDebugTypeString()
        + ", data: "
        + getDebugLabelValue();
  }

  private String getDebugTypeString() {
    switch (labelBuilder.getType()) {
      case TEXT:
        return "TEXT";
      case PICTURE:
        return "PICTURE";
      case SENSOR_TRIGGER:
        return "TRIGGER";
      case SNAPSHOT:
        return "SNAPSHOT";
      default:
        return "???";
    }
  }

  private Object getDebugLabelValue() {
    switch (labelBuilder.getType()) {
      case TEXT:
        return getTextLabelValue();
      case PICTURE:
        return getPictureLabelValue();
      case SENSOR_TRIGGER:
        return getSensorTriggerLabelValue();
      case SNAPSHOT:
        return getSnapshotLabelValue();
      default:
        return "unknown type";
    }
  }

  private static void throwLabelValueException(String protoToCreate, ValueType actualType) {
    throw new IllegalStateException(
        String.format(
            "Cannot get %s from label of type %s", protoToCreate, actualType.getNumber()));
  }
}
