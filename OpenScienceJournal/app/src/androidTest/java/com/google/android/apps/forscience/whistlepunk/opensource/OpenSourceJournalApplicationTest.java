package com.google.android.apps.forscience.whistlepunk.opensource;

import android.test.AndroidTestCase;

import com.google.android.apps.forscience.whistlepunk.api.scalarinput.ScalarInputSpec;
import com.google.android.apps.forscience.whistlepunk.devicemanager.ExternalSensorDiscoverer;
import com.google.android.apps.forscience.whistlepunk.metadata.BleSensorSpec;
import com.google.android.apps.forscience.whistlepunk.opensource.OpenScienceJournalApplication;

import java.util.Map;

public class OpenSourceJournalApplicationTest extends AndroidTestCase {
    public void testNativeBleDiscovererIsThere() {
        Map<String, ExternalSensorDiscoverer> discoverers = getDiscoverers();
        assertTrue(discoverers.containsKey(BleSensorSpec.TYPE));
    }

    public void testThirdPartyDiscoverer() {
        Map<String, ExternalSensorDiscoverer> discoverers = getDiscoverers();
        assertTrue(discoverers.containsKey(ScalarInputSpec.TYPE));
    }

    private Map<String, ExternalSensorDiscoverer> getDiscoverers() {
        OpenScienceJournalApplication app =
                (OpenScienceJournalApplication) getContext().getApplicationContext();
        app.onCreateInjector();
        return OpenScienceJournalApplication.getExternalSensorDiscoverers(getContext());
    }
}
