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

import android.preference.Preference;
import android.preference.PreferenceCategory;
import android.support.annotation.NonNull;

import com.google.android.apps.forscience.whistlepunk.R;

class SensorPreferenceGroup implements SensorGroup {
    private final PreferenceCategory mCategory;

    public SensorPreferenceGroup(PreferenceCategory category) {
        mCategory = category;
    }

    @Override
    public boolean hasSensorKey(String sensorKey) {
        return mCategory.findPreference(sensorKey) != null;
    }

    @Override
    public boolean addAvailableSensor(String sensorKey, ConnectableSensor sensor) {
        return mCategory.addPreference(buildAvailablePreference(sensorKey, sensor));
    }

    @Override
    public void addPairedSensor(String key, ConnectableSensor newSensor) {
        Preference pref = buildAvailablePreference(key, newSensor);
        pref.setWidgetLayoutResource(R.layout.preference_external_device);
        pref.setSummary(newSensor.getSpec().getSensorAppearance().getName(pref.getContext()));
        mCategory.addPreference(pref);
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
    }
}
