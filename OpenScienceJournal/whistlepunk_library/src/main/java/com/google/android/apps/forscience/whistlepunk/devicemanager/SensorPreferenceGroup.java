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
import android.preference.PreferenceScreen;
import android.support.annotation.NonNull;

import com.google.android.apps.forscience.whistlepunk.R;

class SensorPreferenceGroup implements SensorGroup {
    private PreferenceScreen mScreen;
    private final PreferenceCategory mCategory;
    private boolean mRemoveWhenEmpty;
    private boolean mIncludeSummary;

    public SensorPreferenceGroup(PreferenceScreen screen, PreferenceCategory category,
            boolean removeWhenEmpty, boolean includeSummary) {
        mScreen = screen;
        mCategory = category;
        mRemoveWhenEmpty = removeWhenEmpty;
        mIncludeSummary = includeSummary;
        mCategory.setOrderingAsAdded(false);
    }

    @Override
    public boolean hasSensorKey(String sensorKey) {
        return mCategory.findPreference(sensorKey) != null;
    }

    @Override
    public void addSensor(String sensorKey, ConnectableSensor sensor) {
        addPreference(buildFullPreference(sensorKey, sensor));
    }

    @NonNull
    private Preference buildFullPreference(String sensorKey, ConnectableSensor sensor) {
        Preference pref = buildAvailablePreference(sensorKey, sensor);
        if (mIncludeSummary) {
            pref.setWidgetLayoutResource(R.layout.preference_external_device);
            pref.setSummary(sensor.getSpec().getSensorAppearance().getName(pref.getContext()));
        }
        return pref;
    }

    private void addPreference(Preference preference) {
        if (mRemoveWhenEmpty && !isOnScreen()) {
            mScreen.addPreference(mCategory);
        }
        mCategory.addPreference(preference);
    }

    private boolean isOnScreen() {
        return mScreen.findPreference(mCategory.getKey()) != null;
    }

    @NonNull
    private Preference buildAvailablePreference(String key, ConnectableSensor sensor) {
        Preference pref = new Preference(mCategory.getContext());
        pref.setTitle(sensor.getName());
        pref.setKey(key);
        return pref;
    }

    @Override
    public boolean removeSensor(String key) {
        Preference preference = mCategory.findPreference(key);
        if (preference != null) {
            mCategory.removePreference(preference);
        }
        if (mRemoveWhenEmpty && mCategory.getPreferenceCount() == 0 && isOnScreen()) {
            mCategory.removePreference(mCategory);
        }
        return preference != null;
    }

    @Override
    public void replaceSensor(String sensorKey, ConnectableSensor sensor) {
        Preference oldPref = mCategory.findPreference(sensorKey);
        if (oldPref == null) {
            addSensor(sensorKey, sensor);
        } else {
            mCategory.removePreference(oldPref);
            Preference newPref = buildFullPreference(sensorKey, sensor);
            newPref.setOrder(oldPref.getOrder());
            // TODO: can I test this directly?
            mCategory.addPreference(newPref);
        }
    }

    @Override
    public int getSensorCount() {
        return mCategory.getPreferenceCount();
    }
}
