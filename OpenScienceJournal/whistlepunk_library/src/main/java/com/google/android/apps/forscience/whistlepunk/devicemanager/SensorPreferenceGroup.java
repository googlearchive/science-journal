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
package com.google.android.apps.forscience.whistlepunk.devicemanager;

import static android.R.attr.key;

import android.preference.Preference;
import android.preference.PreferenceCategory;
import android.preference.PreferenceScreen;
import android.support.annotation.NonNull;

import com.google.android.apps.forscience.whistlepunk.R;

class SensorPreferenceGroup implements SensorGroup {
    private PreferenceScreen mScreen;
    private final PreferenceCategory mCategory;
    private boolean mRemoveWhenEmpty;

    public SensorPreferenceGroup(PreferenceScreen screen, PreferenceCategory category,
            boolean removeWhenEmpty) {
        mScreen = screen;
        mCategory = category;
        mRemoveWhenEmpty = removeWhenEmpty;
    }

    @Override
    public boolean hasSensorKey(String sensorKey) {
        return mCategory.findPreference(sensorKey) != null;
    }

    @Override
    public void addAvailableSensor(String sensorKey, ConnectableSensor sensor) {
        Preference preference = buildAvailablePreference(sensorKey, sensor);
        addPreference(preference);
    }

    private void addPreference(Preference preference) {
        if (mRemoveWhenEmpty && !isOnScreen()) {
            // TODO: adjust tests so screen is never null
            if (mScreen != null) {
                mScreen.addPreference(mCategory);
            }
        }
        mCategory.addPreference(preference);
    }

    private boolean isOnScreen() {
        // TODO: adjust tests so screen is never null
        if (mScreen == null) {
            return false;
        }
        return mScreen.findPreference(mCategory.getKey()) != null;
    }

    @Override
    public void addPairedSensor(String key, ConnectableSensor newSensor) {
        Preference pref = buildAvailablePreference(key, newSensor);
        pref.setWidgetLayoutResource(R.layout.preference_external_device);
        pref.setSummary(newSensor.getSpec().getSensorAppearance().getName(pref.getContext()));
        addPreference(pref);
    }

    @NonNull
    private Preference buildAvailablePreference(String key, ConnectableSensor sensor) {
        Preference pref = new Preference(mCategory.getContext());
        pref.setTitle(sensor.getName());
        pref.setKey(key);
        return pref;
    }

    @Override
    public void removeSensor(String key) {
        Preference preference = mCategory.findPreference(key);
        if (preference != null) {
            mCategory.removePreference(preference);
        }
        if (mRemoveWhenEmpty && mCategory.getPreferenceCount() == 0 && isOnScreen()) {
            mCategory.removePreference(mCategory);
        }
    }
}
