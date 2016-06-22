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
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;

import com.google.android.apps.forscience.whistlepunk.scalarchart.ScalarDisplayOptions;

/**
 * Holder for Developer Testing Options
 */
public class DevOptionsFragment extends PreferenceFragment {
    private static final String TAG = "DevOptionsFragment";

    private static final String KEY_MAGNETOMETER = "enable_magnetometer_sensor";
    private static final String KEY_VIDEO_SENSOR = "enable_video_sensor";
    private static final String KEY_SINE_WAVE_SENSOR = "enable_sine_wave_sensor";
    private static final String KEY_DEV_TOOLS = "dev_tools";
    private static final String KEY_LEAK_CANARY = "leak_canary";
    public static final String KEY_DEV_SONIFICATION_TYPES = "enable_dev_sonification_types";
    public static final String KEY_ENABLE_ZOOM_IN = "live_zoom_type";
    public static final String KEY_BAROMETER_SENSOR = "enable_barometer_sensor";
    public static final String KEY_AMBIENT_TEMPERATURE_SENSOR = "enable_ambient_temp_sensor";

    public static DevOptionsFragment newInstance() {
        return new DevOptionsFragment();
    }

    static boolean shouldHideTestingOptions(Context context) {
        if (!isDebugVersion(context)) {
            return true;
        }
        return Build.TYPE.equals("user") && !BuildConfig.DEBUG && !BuildConfig.BUILD_TYPE.equals(
                "debug");
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        addPreferencesFromResource(R.xml.dev_options);

        CheckBoxPreference leakPref = (CheckBoxPreference) findPreference(KEY_LEAK_CANARY);
        if (isDebugVersion(getActivity())) {
            leakPref.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference preference, Object newValue) {
                    final SharedPreferences prefs = getPrefs(preference.getContext());
                    prefs.edit().putBoolean(KEY_LEAK_CANARY, (Boolean) newValue).apply();
                    return true;
                }
            });
        } else {
            getPreferenceScreen().removePreference(leakPref);
        }
    }

    private static SharedPreferences getPrefs(Context context) {
        return PreferenceManager.getDefaultSharedPreferences(context);
    }

    public static boolean isMagnetometerEnabled(Context context) {
        return getBoolean(KEY_MAGNETOMETER, false, context);
    }

    public static boolean isSineWaveEnabled(Context context) {
        return getBoolean(KEY_SINE_WAVE_SENSOR, false, context);
    }

    public static boolean isVideoSensorEnabled(Context context) {
        return getBoolean(KEY_VIDEO_SENSOR, false, context);
    }

    public static boolean isDevToolsEnabled(Context context) {
        return getBoolean(KEY_DEV_TOOLS, false, context);
    }

    public static boolean isLeakCanaryEnabled(Context context) {
        return getBoolean(KEY_LEAK_CANARY, false, context);
    }

    public static boolean isDebugVersion(Context context) {
        return BuildConfig.DEBUG;
    }

    public static boolean getEnableAdditionalSonificationTypes(Context context) {
        return getBoolean(KEY_DEV_SONIFICATION_TYPES, false, context);
    }

    public static boolean isEnableZoomInOnY(Context context) {
        return getBoolean(KEY_ENABLE_ZOOM_IN, false, context);
    }

    public static boolean isBarometerEnabled(Context context) {
        return getBoolean(KEY_BAROMETER_SENSOR, false, context);
    }

    public static boolean isAmbientTemperatureSensorEnabled(Context context) {
        return getBoolean(KEY_AMBIENT_TEMPERATURE_SENSOR, false, context);
    }

    private static boolean getBoolean(String key, boolean defaultBool, Context context) {
        if (!isDebugVersion(context)) {
            return defaultBool;
        }
        return getPrefs(context).getBoolean(key, defaultBool);
    }
}
