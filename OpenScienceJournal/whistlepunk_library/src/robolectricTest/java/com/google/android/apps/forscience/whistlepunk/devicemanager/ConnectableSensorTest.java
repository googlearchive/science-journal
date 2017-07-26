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
package com.google.android.apps.forscience.whistlepunk.devicemanager;

import static org.junit.Assert.assertEquals;

import android.content.Context;

import com.google.android.apps.forscience.whistlepunk.Arbitrary;
import com.google.android.apps.forscience.whistlepunk.BuildConfig;
import com.google.android.apps.forscience.whistlepunk.MemoryAppearanceProvider;
import com.google.android.apps.forscience.whistlepunk.SensorAppearance;
import com.google.android.apps.forscience.whistlepunk.api.scalarinput.EmptySensorAppearance;
import com.google.android.apps.forscience.whistlepunk.api.scalarinput.InputDeviceSpec;
import com.google.android.apps.forscience.whistlepunk.api.scalarinput.ScalarInputSpec;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.HashMap;

@RunWith(RobolectricTestRunner.class)
@Config(constants = BuildConfig.class)
public class ConnectableSensorTest {
    private ConnectableSensor.Connector mConnector =
            new ConnectableSensor.Connector(new HashMap<>());

    @Test
    public void getNameExternal() {
        InputDeviceSpec spec = new InputDeviceSpec(ScalarInputSpec.TYPE, "address", "name");
        SensorAppearance appearance = new ConnectableSensor.Connector(
                EnumeratedDiscoverer.buildProviderMap(spec)).disconnected(spec.asGoosciSpec())
                                                            .getAppearance(null);
        assertEquals("name", appearance.getName(null));
    }

    @Test
    public void getNameInternal() {
        final String sensorName = Arbitrary.string();
        MemoryAppearanceProvider map = new MemoryAppearanceProvider();
        map.putAppearance("sid", new EmptySensorAppearance() {
            @Override
            public String getName(Context context) {
                return sensorName;
            }
        });

        assertEquals(sensorName, mConnector.builtIn("sid", false).getAppearance(map).getName(null));
    }
}