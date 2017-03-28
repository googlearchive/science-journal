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


import com.google.android.apps.forscience.whistlepunk.metadata.GoosciSensorTriggerInformation.TriggerInformation;

import android.os.Bundle;
import android.support.annotation.VisibleForTesting;
import android.text.TextUtils;

import java.util.Objects;

/**
 * A wrapper class for a SensorTriggerInformation.
 */
public class SensorTrigger {
    private static final String TAG = "SensorTrigger";

    private static final String KEY_TRIGGER_ID = "trigger_id";
    private static final String KEY_SENSOR_TRIGGER = "sensor_trigger";

    private com.google.android.apps.forscience.whistlepunk.filemetadata.SensorTrigger
            mSensorTrigger;

    // The trigger ID is a unique ID for this trigger only. It tends to be the time that this
    // trigger was initially created.
    private final String mTriggerId;

    // Short cut to create an alert type SensorTrigger.
    public static SensorTrigger newAlertTypeTrigger(String triggerId, String sensorId,
            int triggerWhen, int[] alertTypes, double triggerValue) {
        return new SensorTrigger(triggerId,
                com.google.android.apps.forscience.whistlepunk.filemetadata.SensorTrigger
                        .newAlertTypeTrigger(sensorId, triggerWhen, alertTypes, triggerValue));
    }

    // Short cut to create a Note type SensorTrigger.
    public static SensorTrigger newNoteTypeTrigger(String triggerId, String sensorId,
            int triggerWhen, String noteText, double triggerValue) {
        return new SensorTrigger(triggerId,
                com.google.android.apps.forscience.whistlepunk.filemetadata.SensorTrigger
                        .newNoteTypeTrigger(sensorId, triggerWhen, noteText, triggerValue));
    }

    // Creates a SensorTrigger from scratch, assuming the time it was last used is just now,
    // when it is created.
    public SensorTrigger(String triggerId, String sensorId, int triggerWhen, int actionType,
            double triggerValue) {
        mTriggerId = triggerId;
        mSensorTrigger = new com.google.android.apps.forscience.whistlepunk.filemetadata
                .SensorTrigger(sensorId, triggerWhen, actionType, triggerValue);
    }

    public SensorTrigger(String triggerId, String sensorId, long lastUsed,
            TriggerInformation triggerInformation) {
        mSensorTrigger = new com.google.android.apps.forscience.whistlepunk.filemetadata
                .SensorTrigger(sensorId, lastUsed, triggerInformation);
        mTriggerId = triggerId;
    }

    private SensorTrigger(String triggerId,
            com.google.android.apps.forscience.whistlepunk.filemetadata.SensorTrigger trigger) {
        mSensorTrigger = trigger;
        mTriggerId = triggerId;
    }


    public TriggerInformation getTriggerInformation() {
        return mSensorTrigger.getTriggerProto().triggerInformation;
    }

    public String getTriggerId() {
        return mTriggerId;
    }

    public String getSensorId() {
        return mSensorTrigger.getSensorId();
    }

    public Double getValueToTrigger() {
        return mSensorTrigger.getValueToTrigger();
    }

    public void setValueToTrigger(double valueToTrigger) {
        mSensorTrigger.setValueToTrigger(valueToTrigger);
    }

    public int getTriggerWhen() {
        return mSensorTrigger.getTriggerWhen();
    }

    public void setTriggerWhen(int triggerWhen) {
        mSensorTrigger.setTriggerWhen(triggerWhen);
    }

    public int getActionType() {
        return mSensorTrigger.getActionType();
    }

    public void setTriggerActionType(int actionType) {
        mSensorTrigger.setTriggerActionType(actionType);
    }

    public long getLastUsed() {
        return mSensorTrigger.getLastUsed();
    }

    // Unless re-creating a SensorTrigger from the DB, nothing should call setLastUsed with a
    // timestamp except the updateLastUsed function.
    @VisibleForTesting
    void setLastUsed(long lastUsed) {
        mSensorTrigger.setLastUsed(lastUsed);
    }

    // This can be called any time a trigger is "used", i.e. when the trigger is used in a card, or
    // when information about a trigger is edited.
    private void updateLastUsed() {
        setLastUsed(System.currentTimeMillis());
    }

    public boolean isTriggered(double newValue) {
        return mSensorTrigger.isTriggered(newValue);
    }

    // Returns true if the other SensorTrigger is the same as this one, ignoring the trigger ID
    // and the time it was last used. This can be used to keep from storing duplicates in the
    // database.
    public boolean userSettingsEquals(SensorTrigger other) {
        return TextUtils.equals(getSensorId(), other.getSensorId()) &&
                Objects.equals(getValueToTrigger(), other.getValueToTrigger()) &&
                getActionType() == other.getActionType() &&
                getTriggerWhen() == other.getTriggerWhen() &&
                TextUtils.equals(getNoteText(), other.getNoteText()) &&
                hasSameAlertTypes(getAlertTypes(), other.getAlertTypes());
    }

    public static boolean hasSameAlertTypes(int[] first, int[] second) {
        if (first == null || second == null) {
            return first == null && second == null;
        }
        java.util.Arrays.sort(first);
        java.util.Arrays.sort(second);
        return java.util.Arrays.equals(first, second);
    }

    // For TRIGGER_ACTION_ALERT only.
    public int[] getAlertTypes() {
        return mSensorTrigger.getAlertTypes();
    }

    public boolean hasAlertType(int alertType) {
        return mSensorTrigger.hasAlertType(alertType);
    }

    // For TRIGGER_ACTION_ALERT only.
    public void setAlertTypes(int[] alertTypes) {
        mSensorTrigger.setAlertTypes(alertTypes);
    }

    // For TRIGGER_ACTION_NOTE only.
    public String getNoteText() {
        return mSensorTrigger.getNoteText();
    }

    // For TRIGGER_ACTION_NOTE only.
    public void setNoteText(String newText) {
        mSensorTrigger.setNoteText(newText);
    }

    public Bundle toBundle() {
        Bundle bundle = new Bundle();
        bundle.putString(KEY_TRIGGER_ID, mTriggerId);
        bundle.putBundle(KEY_SENSOR_TRIGGER, mSensorTrigger.toBundle());
        return bundle;
    }

    public static SensorTrigger fromBundle(Bundle bundle) {
        return new SensorTrigger(bundle.getString(KEY_TRIGGER_ID),
                com.google.android.apps.forscience.whistlepunk.filemetadata.SensorTrigger
                        .fromBundle(bundle.getBundle(KEY_SENSOR_TRIGGER)));

    }

    public boolean shouldTriggerOnlyWhenRecording() {
        return mSensorTrigger.shouldTriggerOnlyWhenRecording();
    }

    public void setTriggerOnlyWhenRecording(boolean triggerOnlyWhenRecording) {
        mSensorTrigger.setTriggerOnlyWhenRecording(triggerOnlyWhenRecording);
    }
}
