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

package com.google.android.apps.forscience.whistlepunk.api.scalarinput;

import static org.junit.Assert.assertEquals;

import android.content.ComponentName;
import android.content.ServiceConnection;
import android.content.pm.ResolveInfo;
import android.os.Bundle;
import java.util.HashMap;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

@RunWith(RobolectricTestRunner.class)
public class ScalarSensorServiceFinderTest {
  @Test
  public void testUseFlattenedComponentName() {
    RecordingCallbacks callbacks = new RecordingCallbacks();
    ComponentName name = new ComponentName("packageName", "packageName.MyClass");
    ServiceConnection connection =
        ScalarSensorServiceFinder.makeServiceConnection(
            new HashMap<String, ServiceConnection>(), name, callbacks, null);
    connection.onServiceConnected(name, new TestSensorDiscoverer("serviceName"));
    assertEquals("packageName/.MyClass", callbacks.serviceId);
  }

  @Test
  public void testMetadataOverride() {
    RecordingCallbacks callbacks = new RecordingCallbacks();
    Bundle metaData = new Bundle();
    metaData.putString(ScalarSensorServiceFinder.METADATA_KEY_CLASS_NAME_OVERRIDE, "YourClass");
    ComponentName name = new ComponentName("packageName", "packageName.MyClass");
    ServiceConnection connection =
        ScalarSensorServiceFinder.makeServiceConnection(
            new HashMap<String, ServiceConnection>(), name, callbacks, metaData);
    connection.onServiceConnected(name, new TestSensorDiscoverer("serviceName"));
    assertEquals("packageName/YourClass", callbacks.serviceId);
  }

  @Test
  public void testDontGarbageCollectSecondServiceInSamePackage() {
    RecordingCallbacks callbacks = new RecordingCallbacks();
    HashMap<String, ServiceConnection> connections = new HashMap<>();

    ComponentName name1 = new ComponentName("packageName", "packageName.Class1");
    ServiceConnection connection1 =
        ScalarSensorServiceFinder.makeServiceConnection(connections, name1, callbacks, null);
    assertEquals(1, connections.size());

    ComponentName name2 = new ComponentName("packageName", "packageName.Class2");
    ServiceConnection connection2 =
        ScalarSensorServiceFinder.makeServiceConnection(connections, name2, callbacks, null);
    assertEquals(2, connections.size());

    connection1.onServiceDisconnected(name1);
    assertEquals(1, connections.size());

    connection2.onServiceDisconnected(name2);
    assertEquals(0, connections.size());
  }

  @Test
  public void testNullCheck() {
    ScalarSensorServiceFinder finder =
        new ScalarSensorServiceFinder(RuntimeEnvironment.application.getApplicationContext()) {
          @Override
          protected List<ResolveInfo> getResolveInfos() {
            return null;
          }
        };
    // Just don't crash
    finder.take(new RecordingCallbacks());
  }

  private static class RecordingCallbacks implements AppDiscoveryCallbacks {
    public String serviceId;

    @Override
    public void onServiceFound(String serviceId, ISensorDiscoverer service) {
      this.serviceId = serviceId;
    }

    @Override
    public void onDiscoveryDone() {}
  }
}
