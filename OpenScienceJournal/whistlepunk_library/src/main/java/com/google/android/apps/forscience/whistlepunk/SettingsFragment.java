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
