package com.google.android.apps.forscience.whistlepunk;

import android.test.AndroidTestCase;

public class DevOptionsTestCase extends AndroidTestCase {
    private boolean mRememberedThirdParty;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mRememberedThirdParty = DevOptionsFragment.isThirdPartyDiscoveryEnabled(getContext());
    }

    @Override
    protected void tearDown() throws Exception {
        setThirdParty(mRememberedThirdParty);
        super.tearDown();
    }

    protected void setThirdParty(boolean value) {
        DevOptionsFragment.getPrefs(getContext()).edit().putBoolean(
                DevOptionsFragment.KEY_THIRD_PARTY_SENSORS, value).apply();
    }
}
