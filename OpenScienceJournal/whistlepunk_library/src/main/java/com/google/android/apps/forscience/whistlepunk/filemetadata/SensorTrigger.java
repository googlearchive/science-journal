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

package com.google.android.apps.forscience.whistlepunk.filemetadata;

import android.os.Bundle;
import androidx.annotation.VisibleForTesting;
import android.text.TextUtils;
import android.util.Log;
import com.google.android.apps.forscience.whistlepunk.metadata.GoosciSensorTrigger;
import com.google.android.apps.forscience.whistlepunk.metadata.GoosciSensorTriggerInformation.TriggerInformation;
import com.google.android.apps.forscience.whistlepunk.metadata.GoosciSensorTriggerInformation.TriggerInformation.TriggerActionType;
import com.google.android.apps.forscience.whistlepunk.metadata.GoosciSensorTriggerInformation.TriggerInformation.TriggerAlertType;
import com.google.android.apps.forscience.whistlepunk.metadata.GoosciSensorTriggerInformation.TriggerInformation.TriggerWhen;
import com.google.common.collect.ImmutableSet;
import com.google.protobuf.InvalidProtocolBufferException;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** A wrapper class for a SensorTrigger proto. */
public class SensorTrigger {
  private static final String TAG = "SensorTrigger";

  private static final String KEY_TRIGGER = "trigger_info";

  // When comparing double values from sensors, use this epsilon.
  // TODO: This could be passed in per-sensor as part of the API.
  private static final Double EPSILON = .00001;

  private GoosciSensorTrigger.SensorTrigger triggerProto;

  // Used to determine when the trigger is triggered.
  private Double oldValue;
  private boolean isInitialized = false;

  // Short cut to create an alert type SensorTrigger.
  public static SensorTrigger newAlertTypeTrigger(
      String sensorId,
      TriggerWhen triggerWhen,
      Set<TriggerAlertType> alertTypes,
      double triggerValue) {
    SensorTrigger result =
        new SensorTrigger(
            sensorId, triggerWhen, TriggerActionType.TRIGGER_ACTION_ALERT, triggerValue);
    result.setAlertTypes(alertTypes);
    return result;
  }

  // Short cut to create a Note type SensorTrigger.
  public static SensorTrigger newNoteTypeTrigger(
      String sensorId, TriggerWhen triggerWhen, String noteText, double triggerValue) {
    SensorTrigger result =
        new SensorTrigger(
            sensorId, triggerWhen, TriggerActionType.TRIGGER_ACTION_NOTE, triggerValue);
    result.setNoteText(noteText);
    return result;
  }

  // Creates a SensorTrigger from scratch, assuming the time it was last used is just now,
  // when it is created.
  public static SensorTrigger newTrigger(
      String sensorId, TriggerWhen triggerWhen, TriggerActionType actionType, double triggerValue) {
    return new SensorTrigger(sensorId, triggerWhen, actionType, triggerValue);
  }

  public static SensorTrigger fromTrigger(
      String triggerId, String sensorId, long lastUsed, TriggerInformation triggerInformation) {
    return new SensorTrigger(triggerId, sensorId, lastUsed, triggerInformation);
  }

  public static SensorTrigger fromProto(GoosciSensorTrigger.SensorTrigger proto) {
    return new SensorTrigger(proto);
  }

  @VisibleForTesting
  protected SensorTrigger(
      String sensorId, TriggerWhen triggerWhen, TriggerActionType actionType, double triggerValue) {
    GoosciSensorTrigger.SensorTrigger.Builder sensorTrigger =
        GoosciSensorTrigger.SensorTrigger.newBuilder();
    TriggerInformation triggerInformation =
        TriggerInformation.newBuilder()
            .setTriggerWhen(triggerWhen)
            .setTriggerActionType(actionType)
            .setValueToTrigger(triggerValue)
            .build();
    triggerProto =
        sensorTrigger
            .setTriggerInformation(triggerInformation)
            .setSensorId(sensorId)
            .setTriggerId(UUID.randomUUID().toString())
            .build();
    updateLastUsed();
  }

  private SensorTrigger(
      String triggerId, String sensorId, long lastUsed, TriggerInformation triggerInformation) {
    triggerProto =
        GoosciSensorTrigger.SensorTrigger.newBuilder()
            .setTriggerInformation(triggerInformation)
            .setSensorId(sensorId)
            .setTriggerId(triggerId)
            .build();
    setLastUsed(lastUsed);
  }

  private SensorTrigger(GoosciSensorTrigger.SensorTrigger triggerProto) {
    this.triggerProto = triggerProto;
  }

  public String getTriggerId() {
    return triggerProto.getTriggerId();
  }

  public GoosciSensorTrigger.SensorTrigger getTriggerProto() {
    return triggerProto;
  }

  public String getSensorId() {
    return triggerProto.getSensorId();
  }

  public Double getValueToTrigger() {
    return triggerProto.getTriggerInformation().getValueToTrigger();
  }

  public void setValueToTrigger(double valueToTrigger) {
    TriggerInformation triggerInformation =
        triggerProto.getTriggerInformation().toBuilder().setValueToTrigger(valueToTrigger).build();
    triggerProto = triggerProto.toBuilder().setTriggerInformation(triggerInformation).build();
  }

  public TriggerWhen getTriggerWhen() {
    return triggerProto.getTriggerInformation().getTriggerWhen();
  }

  public void setTriggerWhen(TriggerWhen triggerWhen) {
    TriggerInformation triggerInformation =
        triggerProto.getTriggerInformation().toBuilder().setTriggerWhen(triggerWhen).build();
    triggerProto = triggerProto.toBuilder().setTriggerInformation(triggerInformation).build();
  }

  public TriggerActionType getActionType() {
    return triggerProto.getTriggerInformation().getTriggerActionType();
  }

  public void setTriggerActionType(TriggerActionType actionType) {
    if (triggerProto.getTriggerInformation().getTriggerActionType() == actionType) {
      return;
    }
    TriggerInformation.Builder triggerInformation =
        triggerProto.getTriggerInformation().toBuilder();
    // Clear old metadata to defaults when the new trigger is set.
    if (triggerInformation.getTriggerActionType() == TriggerActionType.TRIGGER_ACTION_NOTE) {
      triggerInformation.clearNoteText();
    } else if (triggerInformation.getTriggerActionType()
        == TriggerActionType.TRIGGER_ACTION_ALERT) {
      triggerInformation.clearTriggerAlertTypes();
    }
    triggerInformation.setTriggerActionType(actionType);
    triggerProto = triggerProto.toBuilder().setTriggerInformation(triggerInformation).build();
  }

  public long getLastUsed() {
    return triggerProto.getLastUsedMs();
  }

  // Unless re-creating a SensorTrigger from the DB, nothing should call setLastUsed with a
  // timestamp except the updateLastUsed function.
  @VisibleForTesting
  public void setLastUsed(long lastUsed) {
    triggerProto = triggerProto.toBuilder().setLastUsedMs(lastUsed).build();
  }

  // This can be called any time a trigger is "used", i.e. when the trigger is used in a card, or
  // when information about a trigger is edited.
  private void updateLastUsed() {
    setLastUsed(System.currentTimeMillis());
  }

  public boolean isTriggered(double newValue) {
    boolean result = false;
    if (!isInitialized) {
      isInitialized = true;
      result = false;
    } else {
      switch (triggerProto.getTriggerInformation().getTriggerWhen()) {
        case TRIGGER_WHEN_AT:
          // Not just an equality check: also test to see if the threshold was crossed in
          // either direction.
          result =
              doubleEquals(newValue, triggerProto.getTriggerInformation().getValueToTrigger())
                  || crossedThreshold(newValue, oldValue);
          break;
        case TRIGGER_WHEN_DROPS_BELOW:
          result = droppedBelow(newValue, oldValue);
          break;
        case TRIGGER_WHEN_RISES_ABOVE:
          result = roseAbove(newValue, oldValue);
          break;
        case TRIGGER_WHEN_BELOW:
          return newValue < triggerProto.getTriggerInformation().getValueToTrigger();
        case TRIGGER_WHEN_ABOVE:
          return newValue > triggerProto.getTriggerInformation().getValueToTrigger();
      }
    }
    oldValue = newValue;
    // The last used time may be the last time it was used in a card.
    updateLastUsed();
    return result;
  }

  private boolean doubleEquals(double first, double second) {
    return Math.abs(first - second) < EPSILON;
  }

  private boolean droppedBelow(double newValue, double oldValue) {
    return newValue < triggerProto.getTriggerInformation().getValueToTrigger()
        && oldValue >= triggerProto.getTriggerInformation().getValueToTrigger();
  }

  private boolean roseAbove(double newValue, double oldValue) {
    return newValue > triggerProto.getTriggerInformation().getValueToTrigger()
        && oldValue <= triggerProto.getTriggerInformation().getValueToTrigger();
  }

  private boolean crossedThreshold(double newValue, double oldValue) {
    return (newValue < triggerProto.getTriggerInformation().getValueToTrigger()
            && oldValue > triggerProto.getTriggerInformation().getValueToTrigger())
        || (newValue > triggerProto.getTriggerInformation().getValueToTrigger()
            && oldValue < triggerProto.getTriggerInformation().getValueToTrigger());
  }

  // Returns true if the other SensorTrigger is the same as this one, ignoring the trigger ID
  // and the time it was last used. This can be used to keep from storing duplicates in the
  // database.
  public boolean userSettingsEquals(SensorTrigger other) {
    return TextUtils.equals(getSensorId(), other.getSensorId())
        && Objects.equals(getValueToTrigger(), other.getValueToTrigger())
        && getActionType() == other.getActionType()
        && getTriggerWhen() == other.getTriggerWhen()
        && TextUtils.equals(getNoteText(), other.getNoteText())
        && Objects.equals(getAlertTypes(), other.getAlertTypes());
  }

  // For TRIGGER_ACTION_ALERT only.
  public ImmutableSet<TriggerAlertType> getAlertTypes() {
    return ImmutableSet.copyOf(triggerProto.getTriggerInformation().getTriggerAlertTypesList());
  }

  public boolean hasAlertType(TriggerAlertType alertType) {
    for (TriggerAlertType triggerAlertType :
        triggerProto.getTriggerInformation().getTriggerAlertTypesList()) {
      if (triggerAlertType == alertType) {
        return true;
      }
    }
    return false;
  }

  // For TRIGGER_ACTION_ALERT only.
  public void setAlertTypes(Set<TriggerAlertType> alertTypes) {
    TriggerInformation.Builder triggerInformation =
        triggerProto.getTriggerInformation().toBuilder().clearTriggerAlertTypes();
    for (TriggerAlertType alertType : alertTypes) {
      triggerInformation.addTriggerAlertTypes(alertType);
    }
    triggerProto = triggerProto.toBuilder().setTriggerInformation(triggerInformation).build();
  }

  // For TRIGGER_ACTION_NOTE only.
  public String getNoteText() {
    return triggerProto.getTriggerInformation().getNoteText();
  }

  // For TRIGGER_ACTION_NOTE only.
  public void setNoteText(String newText) {
    TriggerInformation triggerInformation =
        triggerProto.getTriggerInformation().toBuilder().setNoteText(newText).build();
    triggerProto = triggerProto.toBuilder().setTriggerInformation(triggerInformation).build();
  }

  public Bundle toBundle() {
    Bundle bundle = new Bundle();
    bundle.putByteArray(KEY_TRIGGER, triggerProto.toByteArray());
    return bundle;
  }

  public static SensorTrigger fromBundle(Bundle bundle) {
    try {
      return new SensorTrigger(
          GoosciSensorTrigger.SensorTrigger.parseFrom(bundle.getByteArray(KEY_TRIGGER)));
    } catch (InvalidProtocolBufferException e) {
      if (Log.isLoggable(TAG, Log.ERROR)) {
        Log.e(TAG, "Error parsing SensorTrigger");
      }
      return null;
    }
  }

  public boolean shouldTriggerOnlyWhenRecording() {
    return triggerProto.getTriggerInformation().getTriggerOnlyWhenRecording();
  }

  public void setTriggerOnlyWhenRecording(boolean triggerOnlyWhenRecording) {
    TriggerInformation triggerInformation =
        triggerProto.getTriggerInformation().toBuilder()
            .setTriggerOnlyWhenRecording(triggerOnlyWhenRecording)
            .build();
    triggerProto = triggerProto.toBuilder().setTriggerInformation(triggerInformation).build();
  }
}
