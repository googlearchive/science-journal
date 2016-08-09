package com.google.android.apps.forscience.whistlepunk.api.scalarinput;

import android.preference.Preference;
import android.test.AndroidTestCase;

import com.google.android.apps.forscience.whistlepunk.Arbitrary;
import com.google.android.apps.forscience.whistlepunk.devicemanager.ManageDevicesFragment;
import com.google.android.apps.forscience.whistlepunk.metadata.ExternalSensorSpec;

public class ScalarInputDiscovererTest extends AndroidTestCase {
    public void testExtractFromPreference() {
        ScalarInputDiscoverer discoverer = new ScalarInputDiscoverer(null);
        String name = Arbitrary.string();
        String deviceId = Arbitrary.string();
        Preference pref = ManageDevicesFragment.makePreference(name, deviceId, ScalarInputSpec.TYPE,
                false, getContext());
        ExternalSensorSpec spec = discoverer.extractSensorSpec(pref);
        assertEquals(ScalarInputSpec.TYPE, spec.getType());
    }
}