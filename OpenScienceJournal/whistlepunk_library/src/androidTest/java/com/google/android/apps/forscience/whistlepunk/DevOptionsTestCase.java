package com.google.android.apps.forscience.whistlepunk;

import android.content.SharedPreferences;
import android.support.annotation.NonNull;
import android.test.AndroidTestCase;

public class DevOptionsTestCase extends AndroidTestCase {
    private boolean mRememberedPref;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mRememberedPref = getPrefs().getBoolean(getRememberedPrefKey(), false);
    }

    @NonNull
    protected String getRememberedPrefKey() {
        return DevOptionsFragment.KEY_THIRD_PARTY_SENSORS;
    }

    @Override
    protected void tearDown() throws Exception {
        setPrefValue(mRememberedPref);
        super.tearDown();
    }

    protected void setPrefValue(boolean value) {
        getPrefs().edit().putBoolean(getRememberedPrefKey(), value).apply();
    }

    private SharedPreferences getPrefs() {
        return DevOptionsFragment.getPrefs(getContext());
    }
}
