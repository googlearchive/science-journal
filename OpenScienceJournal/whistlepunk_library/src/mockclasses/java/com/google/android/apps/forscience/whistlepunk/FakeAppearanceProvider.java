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
package com.google.android.apps.forscience.whistlepunk;

import com.google.android.apps.forscience.javalib.MaybeConsumer;
import com.google.android.apps.forscience.javalib.Success;

public class FakeAppearanceProvider implements SensorAppearanceProvider {
  private int nameStringId;

  public FakeAppearanceProvider() {
    this(R.string.sensor_custom);
  }

  public FakeAppearanceProvider(int nameStringId) {
    this.nameStringId = nameStringId;
  }

  @Override
  public void loadAppearances(MaybeConsumer<Success> onSuccess) {}

  @Override
  public SensorAppearance getAppearance(String sensorId) {
    return new BuiltInSensorAppearance(nameStringId, 0, "sensorId");
  }
}
