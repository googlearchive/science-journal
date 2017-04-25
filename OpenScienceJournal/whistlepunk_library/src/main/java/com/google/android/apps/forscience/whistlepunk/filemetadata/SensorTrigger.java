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


import com.google.android.apps.forscience.whistlepunk.ProtoUtils;
import com.google.android.apps.forscience.whistlepunk.metadata.GoosciSensorTrigger;
import com.google.android.apps.forscience.whistlepunk.metadata.GoosciSensorTriggerInformation.TriggerInformation;

import com.google.common.primitives.Ints;
import com.google.protobuf.nano.InvalidProtocolBufferNanoException;

import android.os.Bundle;
import android.support.annotation.VisibleForTesting;
import android.text.TextUtils;
import android.util.Log;

import java.util.Objects;

/**
 * A wrapper class for a SensorTrigger proto.
 */
public class SensorTrigger {
    private static final String TAG = "SensorTrigger";

    private static final String KEY_TRIGGER = "trigger_info";

    // When comparing double values from sensors, use this epsilon.
    // TODO: This could be passed in per-sensor as part of the API.
    private static final Double EPSILON = .00001;

    private GoosciSensorTrigger.SensorTrigger mTriggerProto;

    // Used to determine when the trigger is triggered.
    private Double mOldValue;
    private boolean mIsInitialized = false;

    // Short cut to create an alert type SensorTrigger.
    public static SensorTrigger newAlertTypeTrigger(String sensorId,
            int triggerWhen, int[] alertTypes, double triggerValue) {
        SensorTrigger result = new SensorTrigger(sensorId, triggerWhen,
                TriggerInformation.TRIGGER_ACTION_ALERT, triggerValue);
        result.setAlertTypes(alertTypes);
        return result;
    }

    // Short cut to create a Note type SensorTrigger.
    public static SensorTrigger newNoteTypeTrigger(String sensorId,
            int triggerWhen, String noteText, double triggerValue) {
        SensorTrigger result = new SensorTrigger(sensorId, triggerWhen,
                TriggerInformation.TRIGGER_ACTION_NOTE, triggerValue);
        result.setNoteText(noteText);
        return result;
    }

    // Creates a SensorTrigger from scratch, assuming the time it was last used is just now,
    // when it is created.
    public static SensorTrigger newTrigger(String sensorId, int triggerWhen, int actionType,
            double triggerValue) {
        return new SensorTrigger(sensorId, triggerWhen, actionType, triggerValue);
    }

    public static SensorTrigger fromTrigger(String triggerId, String sensorId, long lastUsed,
            TriggerInformation triggerInformation) {
        return new SensorTrigger(triggerId, sensorId, lastUsed, triggerInformation);
    }

    public static SensorTrigger fromProto(GoosciSensorTrigger.SensorTrigger proto) {
        return new SensorTrigger(proto);
    }

    @VisibleForTesting
    protected SensorTrigger(String sensorId, int triggerWhen, int actionType,
            double triggerValue) {
        mTriggerProto = new GoosciSensorTrigger.SensorTrigger();
        mTriggerProto.triggerInformation = new TriggerInformation();
        mTriggerProto.triggerInformation.triggerWhen = triggerWhen;
        mTriggerProto.triggerInformation.triggerActionType = actionType;
        mTriggerProto.triggerInformation.valueToTrigger = triggerValue;
        mTriggerProto.sensorId = sensorId;
        mTriggerProto.triggerId = java.util.UUID.randomUUID().toString();
        updateLastUsed();
    }

    private SensorTrigger(String triggerId, String sensorId, long lastUsed,
            TriggerInformation triggerInformation) {
        mTriggerProto = new GoosciSensorTrigger.SensorTrigger();
        mTriggerProto.triggerInformation = triggerInformation;
        mTriggerProto.sensorId = sensorId;
        mTriggerProto.triggerId = triggerId;
        setLastUsed(lastUsed);
    }

    private SensorTrigger(GoosciSensorTrigger.SensorTrigger triggerProto) {
        mTriggerProto = triggerProto;
    }

    public String getTriggerId() {
        return mTriggerProto.triggerId;
    }

    public GoosciSensorTrigger.SensorTrigger getTriggerProto() {
        return mTriggerProto;
    }

    public String getSensorId() {
        return mTriggerProto.sensorId;
    }

    public Double getValueToTrigger() {
        return mTriggerProto.triggerInformation.valueToTrigger;
    }

    public void setValueToTrigger(double valueToTrigger) {
        mTriggerProto.triggerInformation.valueToTrigger = valueToTrigger;
    }

    public int getTriggerWhen() {
        return mTriggerProto.triggerInformation.triggerWhen;
    }

    public void setTriggerWhen(int triggerWhen) {
        mTriggerProto.triggerInformation.triggerWhen = triggerWhen;
    }

    public int getActionType() {
        return mTriggerProto.triggerInformation.triggerActionType;
    }

    public void setTriggerActionType(int actionType) {
        if (mTriggerProto.triggerInformation.triggerActionType == actionType) {
            return;
        }
        // Clear old metadata to defaults when the new trigger is set.
        if (mTriggerProto.triggerInformation.triggerActionType ==
                TriggerInformation.TRIGGER_ACTION_NOTE) {
            mTriggerProto.triggerInformation.noteText = "";
        } else if (mTriggerProto.triggerInformation.triggerActionType ==
                TriggerInformation.TRIGGER_ACTION_ALERT) {
            mTriggerProto.triggerInformation.triggerAlertTypes = new int[]{};
        }
        mTriggerProto.triggerInformation.triggerActionType = actionType;
    }

    public long getLastUsed() {
        return mTriggerProto.lastUsedMs;
    }

    // Unless re-creating a SensorTrigger from the DB, nothing should call setLastUsed with a
    // timestamp except the updateLastUsed function.
    @VisibleForTesting
    public void setLastUsed(long lastUsed) {
        mTriggerProto.lastUsedMs = lastUsed;
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
            if (mTriggerProto.triggerInformation.triggerWhen ==
                    TriggerInformation.TRIGGER_WHEN_AT) {
                // Not just an equality check: also test to see if the threshold was crossed in
                // either direction.
                result = doubleEquals(newValue, mTriggerProto.triggerInformation.valueToTrigger) ||
                        crossedThreshold(newValue, mOldValue);
            } else if (mTriggerProto.triggerInformation.triggerWhen ==
                    TriggerInformation.TRIGGER_WHEN_DROPS_BELOW) {
                result = droppedBelow(newValue, mOldValue);
            } else if (mTriggerProto.triggerInformation.triggerWhen ==
                    TriggerInformation.TRIGGER_WHEN_RISES_ABOVE) {
                result = roseAbove(newValue, mOldValue);
            } else if (mTriggerProto.triggerInformation.triggerWhen ==
                    TriggerInformation.TRIGGER_WHEN_BELOW) {
                return newValue < mTriggerProto.triggerInformation.valueToTrigger;
            } else if (mTriggerProto.triggerInformation.triggerWhen ==
                    TriggerInformation.TRIGGER_WHEN_ABOVE) {
                return newValue > mTriggerProto.triggerInformation.valueToTrigger;
            }
        }
        mOldValue = newValue;
        // The last used time may be the last time it was used in a card.
        updateLastUsed();
        return result;
    }

    private boolean doubleEquals(double first, double second) {
        return Math.abs(first - second) < EPSILON;
    }

    private boolean droppedBelow(double newValue, double oldValue) {
        return newValue < mTriggerProto.triggerInformation.valueToTrigger &&
                oldValue >= mTriggerProto.triggerInformation.valueToTrigger;
    }

    private boolean roseAbove(double newValue, double oldValue) {
        return newValue > mTriggerProto.triggerInformation.valueToTrigger &&
                oldValue <= mTriggerProto.triggerInformation.valueToTrigger;
    }

    private boolean crossedThreshold(double newValue, double oldValue) {
        return (newValue < mTriggerProto.triggerInformation.valueToTrigger &&
                oldValue > mTriggerProto.triggerInformation.valueToTrigger) ||
                (newValue > mTriggerProto.triggerInformation.valueToTrigger &&
                        oldValue < mTriggerProto.triggerInformation.valueToTrigger);
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
        return mTriggerProto.triggerInformation.triggerAlertTypes;
    }

    public boolean hasAlertType(int alertType) {
        int[] alertTypes = mTriggerProto.triggerInformation.triggerAlertTypes;
        return Ints.indexOf(alertTypes, alertType) != -1;
    }

    // For TRIGGER_ACTION_ALERT only.
    public void setAlertTypes(int[] alertTypes) {
        mTriggerProto.triggerInformation.triggerAlertTypes = alertTypes;
    }

    // For TRIGGER_ACTION_NOTE only.
    public String getNoteText() {
        return mTriggerProto.triggerInformation.noteText;
    }

    // For TRIGGER_ACTION_NOTE only.
    public void setNoteText(String newText) {
        mTriggerProto.triggerInformation.noteText = newText;
    }

    public Bundle toBundle() {
        Bundle bundle = new Bundle();
        bundle.putByteArray(KEY_TRIGGER, ProtoUtils.makeBlob(mTriggerProto));
        return bundle;
    }

    public static SensorTrigger fromBundle(Bundle bundle) {
        try {
            return new SensorTrigger(GoosciSensorTrigger.SensorTrigger.parseFrom(
                    bundle.getByteArray(KEY_TRIGGER)));
        } catch (InvalidProtocolBufferNanoException e) {
            if (Log.isLoggable(TAG, Log.ERROR)) {
                Log.e(TAG, "Error parsing SensorTrigger");
            }
            return null;
        }
    }

    public boolean shouldTriggerOnlyWhenRecording() {
        return mTriggerProto.triggerInformation.triggerOnlyWhenRecording;
    }

    public void setTriggerOnlyWhenRecording(boolean triggerOnlyWhenRecording) {
        mTriggerProto.triggerInformation.triggerOnlyWhenRecording = triggerOnlyWhenRecording;
    }
}
