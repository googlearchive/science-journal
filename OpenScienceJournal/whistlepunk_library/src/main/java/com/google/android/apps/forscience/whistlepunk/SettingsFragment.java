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

package com.google.android.apps.forscience.whistlepunk;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import android.preference.PreferenceScreen;
import android.util.Log;

import com.google.android.apps.forscience.whistlepunk.analytics.TrackerConstants;

/**
 * Holder for Settings, About, etc.
 */
public class SettingsFragment extends PreferenceFragment {

    private static final String TAG = "SettingsFragment";
    private static final String KEY_VERSION = "version";
    private static final String KEY_OPEN_SOURCE = "open_source";

    public static SettingsFragment newInstance() {
        return new SettingsFragment();
    }

    public SettingsFragment() {}

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        addPreferencesFromResource(R.xml.settings);

        loadVersion(getActivity());
    }

    @Override
    public void onResume() {
        super.onResume();
        WhistlePunkApplication.getUsageTracker(getActivity()).trackScreenView(
                TrackerConstants.SCREEN_SETTINGS);
    }

    @Override
    public boolean onPreferenceTreeClick(PreferenceScreen preferenceScreen, Preference preference) {
        if (KEY_OPEN_SOURCE.equals(preference.getKey())) {
            new LicenseFragment().show(getFragmentManager(), "license");
            return true;
        }
        return super.onPreferenceTreeClick(preferenceScreen, preference);
    }

    private void loadVersion(Context context) {
        PackageManager pm = context.getPackageManager();

        PackageInfo info;
        String versionName;
        try {
            info = pm.getPackageInfo(getActivity().getPackageName(), 0);
            versionName = info.versionName;
        } catch (PackageManager.NameNotFoundException e) {
            Log.e(TAG, "Could not load package info.", e);
            versionName = "";
        }

        Preference version = findPreference(KEY_VERSION);
        version.setSummary(versionName);
    }
}
