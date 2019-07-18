/*
 *  Copyright 2017 Google Inc. All Rights Reserved.
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

import static org.junit.Assert.assertEquals;

import android.content.Context;
import com.google.android.apps.forscience.whistlepunk.data.GoosciIcon;
import com.google.android.apps.forscience.whistlepunk.data.GoosciSensorAppearance;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

@RunWith(RobolectricTestRunner.class)
public class SensorAppearanceProviderImplTest {
  @Test
  public void includeIcons() {
    Context context = RuntimeEnvironment.application.getApplicationContext();
    int placeholderStringId = android.R.string.ok;
    BuiltInSensorAppearance appearance =
        new BuiltInSensorAppearance(placeholderStringId, placeholderStringId, "sensorId");

    GoosciSensorAppearance.BasicSensorAppearance proto =
        SensorAppearanceProviderImpl.toProto(appearance, context);
    assertEquals(GoosciIcon.IconPath.PathType.BUILTIN, proto.getIconPath().getType());
    assertEquals(GoosciIcon.IconPath.PathType.BUILTIN, proto.getLargeIconPath().getType());
    assertEquals("sensorId", proto.getIconPath().getPathString());
    assertEquals("sensorId", proto.getLargeIconPath().getPathString());

    // TODO: re-enable once we have this implemented for all SDKs (b/63933068)
    // This should be a BCP 47 language tag, like "en-US"
    // String expected = context.getResources().getConfiguration().locale.toLanguageTag();
    // assertEquals(expected, proto.locale);
  }
}
