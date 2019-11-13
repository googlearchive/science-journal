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
package com.google.android.apps.forscience.whistlepunk.opensource;

import android.test.AndroidTestCase;
import com.google.android.apps.forscience.whistlepunk.api.scalarinput.ScalarInputSpec;
import com.google.android.apps.forscience.whistlepunk.devicemanager.SensorDiscoverer;
import com.google.android.apps.forscience.whistlepunk.metadata.BleSensorSpec;
import java.util.Map;

public class OpenSourceJournalApplicationTest extends AndroidTestCase {
  public void testNativeBleDiscovererIsThere() {
    Map<String, SensorDiscoverer> discoverers = getDiscoverers();
    assertTrue(discoverers.containsKey(BleSensorSpec.TYPE));
  }

  public void testThirdPartyDiscoverer() {
    Map<String, SensorDiscoverer> discoverers = getDiscoverers();
    assertTrue(discoverers.containsKey(ScalarInputSpec.TYPE));
  }

  private Map<String, SensorDiscoverer> getDiscoverers() {
    OpenScienceJournalApplication app =
        (OpenScienceJournalApplication) getContext().getApplicationContext();
    app.onCreateInjector();
    return OpenScienceJournalApplication.getExternalSensorDiscoverers(getContext());
  }
}
