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


import android.support.annotation.VisibleForTesting;
import android.text.TextUtils;
import java.util.Objects;

/**
 * A wrapper class for a SensorTriggerInformation.
 */
public class SensorTrigger {

    private TriggerInformation mTriggerInfo;

    // The trigger ID is a unique ID for this trigger only. It tends to be the time that this
    // trigger was initially created.
    private final String mTriggerId;

    // The sensor ID is not mutatable.
    private final String mSensorId;

    // The time at which this trigger was last used. This is updated with any setters to the
    // trigger, but it is the responsibility of the client to update the database.
    private long mLastUsed;

    // Used to determine when the trigger is triggered.
    private Double mOldValue;
    private boolean mIsInitialized = false;

    // Short cut to create an alert type SensorTrigger.
    public static SensorTrigger newAlertTypeTrigger(String triggerId, String sensorId,
            int triggerWhen, int[] alertTypes, double triggerValue) {
        SensorTrigger result = new SensorTrigger(triggerId, sensorId, triggerWhen,
                TriggerInformation.TRIGGER_ACTION_ALERT,
                triggerValue);
        result.setAlertTypes(alertTypes);
        return result;
    }

    // Short cut to create a Note type SensorTrigger.
    public static SensorTrigger newNoteTypeTrigger(String triggerId, String sensorId,
            int triggerWhen, String noteText, double triggerValue) {
        SensorTrigger result = new SensorTrigger(triggerId, sensorId, triggerWhen,
                TriggerInformation.TRIGGER_ACTION_NOTE,
                triggerValue);
        result.setNoteText(noteText);
        return result;
    }

    // Creates a SensorTrigger from scratch, assuming the time it was last used is just now,
    // when it is created.
    public SensorTrigger(String triggerId, String sensorId, int triggerWhen, int actionType,
            double triggerValue) {
        mTriggerInfo = new TriggerInformation();
        mTriggerInfo.triggerWhen = triggerWhen;
        mTriggerInfo.triggerActionType = actionType;
        mTriggerInfo.valueToTrigger = triggerValue;
        mSensorId = sensorId;
        mTriggerId = triggerId;
        updateLastUsed();
    }

    public SensorTrigger(String triggerId, String sensorId, long lastUsed,
            TriggerInformation triggerInformation) {
        mTriggerInfo = triggerInformation;
        mSensorId = sensorId;
        mTriggerId = triggerId;
        setLastUsed(lastUsed);
    }

    public TriggerInformation getTriggerInformation() {
        return mTriggerInfo;
    }

    public String getTriggerId() {
        return mTriggerId;
    }

    public String getSensorId() {
        return mSensorId;
    }

    public Double getValueToTrigger() {
        return mTriggerInfo.valueToTrigger;
    }

    public void setValueToTrigger(double valueToTrigger) {
        mTriggerInfo.valueToTrigger = valueToTrigger;
        updateLastUsed();
    }

    public int getTriggerWhen() {
        return mTriggerInfo.triggerWhen;
    }

    public void setTriggerWhen(int triggerWhen) {
        mTriggerInfo.triggerWhen = triggerWhen;
        updateLastUsed();
    }

    public int getActionType() {
        return mTriggerInfo.triggerActionType;
    }

    // TODO: Write tests for this function to make sure it clears and sets the correct data.
    public void setTriggerActionType(int actionType) {
        if (mTriggerInfo.triggerActionType == actionType) {
            return;
        }
        // Clear old metadata to defaults when the new trigger is set.
        if (mTriggerInfo.triggerActionType == TriggerInformation.TRIGGER_ACTION_NOTE) {
            mTriggerInfo.noteText = "";
        } else if (mTriggerInfo.triggerActionType == TriggerInformation.TRIGGER_ACTION_ALERT) {
            mTriggerInfo.triggerAlertTypes = null;
        }
        mTriggerInfo.triggerActionType = actionType;
        updateLastUsed();
    }

    public long getLastUsed() {
        return mLastUsed;
    }

    // Unless re-creating a SensorTrigger from the DB, nothing should call setLastUsed with a
    // timestamp except the updateLastUsed function.
    @VisibleForTesting
    void setLastUsed(long lastUsed) {
        mLastUsed = lastUsed;
    }

    // This can be called any time a trigger is "used", i.e. when the trigger is used in a card, or
    // when information about a trigger is edited.
    private void updateLastUsed() {
        setLastUsed(System.currentTimeMillis());
    }

    public boolean isTriggered(double newValue) {
        boolean result = false;
        if (!mIsInitialized) {
            mIsInitialized = true;
            result = false;
        } else {
            if (mTriggerInfo.triggerWhen == TriggerInformation.TRIGGER_WHEN_AT) {
                // Not just an equality check: also test to see if the threshold was crossed in
                // either direction.
                result = newValue == mTriggerInfo.valueToTrigger ||
                        crossedThreshold(newValue, mOldValue);
            } else if (mTriggerInfo.triggerWhen == TriggerInformation.TRIGGER_WHEN_DROPS_BELOW) {
                result = droppedBelow(newValue, mOldValue);
            } else if (mTriggerInfo.triggerWhen == TriggerInformation.TRIGGER_WHEN_RISES_ABOVE) {
                result = roseAbove(newValue, mOldValue);
            }
        }
        mOldValue = newValue;
        // The last used time may be the last time it was used in a card.
        updateLastUsed();
        return result;
    }

    private boolean droppedBelow(double newValue, double oldValue) {
        return newValue < mTriggerInfo.valueToTrigger &&
                oldValue >= mTriggerInfo.valueToTrigger;
    }

    private boolean roseAbove(double newValue, double oldValue) {
        return newValue > mTriggerInfo.valueToTrigger && oldValue <= mTriggerInfo.valueToTrigger;
    }

    private boolean crossedThreshold(double newValue, double oldValue) {
        return (newValue < mTriggerInfo.valueToTrigger && oldValue > mTriggerInfo.valueToTrigger) ||
                (newValue > mTriggerInfo.valueToTrigger && oldValue < mTriggerInfo.valueToTrigger);
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
        return mTriggerInfo.triggerAlertTypes;
    }

    // For TRIGGER_ACTION_ALERT only.
    public void setAlertTypes(int[] alertTypes) {
        mTriggerInfo.triggerAlertTypes = alertTypes;
        updateLastUsed();
    }

    // For TRIGGER_ACTION_NOTE only.
    public String getNoteText() {
        return mTriggerInfo.noteText;
    }

    // For TRIGGER_ACTION_NOTE only.
    public void setNoteText(String newText) {
        mTriggerInfo.noteText = newText;
        updateLastUsed();
    }
}
