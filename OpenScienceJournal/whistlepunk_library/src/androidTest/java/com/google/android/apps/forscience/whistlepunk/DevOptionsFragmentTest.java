package com.google.android.apps.forscience.whistlepunk;

import android.content.Context;
import android.test.AndroidTestCase;

public class DevOptionsFragmentTest extends AndroidTestCase {
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

    public void testThirdPartyEnable() {
        setThirdParty(false);
        Context context = getContext();
        assertFalse(DevOptionsFragment.isThirdPartyDiscoveryEnabled(context));
        setThirdParty(true);
        assertTrue(DevOptionsFragment.isThirdPartyDiscoveryEnabled(context));
    }

    private void setThirdParty(boolean value) {
        DevOptionsFragment.getPrefs(getContext()).edit().putBoolean(
                DevOptionsFragment.KEY_THIRD_PARTY_SENSORS, value).apply();
    }
}