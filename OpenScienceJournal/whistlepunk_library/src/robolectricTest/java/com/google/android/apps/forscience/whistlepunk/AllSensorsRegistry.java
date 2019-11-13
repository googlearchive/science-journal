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

import com.google.android.apps.forscience.javalib.Consumer;
import com.google.android.apps.forscience.whistlepunk.sensorapi.ManualSensor;
import com.google.android.apps.forscience.whistlepunk.sensorapi.SensorChoice;
import com.google.common.util.concurrent.MoreExecutors;

class AllSensorsRegistry extends SensorRegistry {
  @Override
  public void withSensorChoice(String tag, String id, Consumer<SensorChoice> consumer) {
    consumer.take(
        new ManualSensor(
            id,
            100,
            100,
            MoreExecutors.directExecutor(),
            shouldAutomaticallyConnectWhenObserving()));
  }

  protected boolean shouldAutomaticallyConnectWhenObserving() {
    return true;
  }
}
