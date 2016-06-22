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

import static org.junit.Assert.assertEquals;

import com.google.android.apps.forscience.whistlepunk.sensorapi.BlankReadableSensorOptions;
import com.google.android.apps.forscience.whistlepunk.sensorapi.RecordingSensorObserver;
import com.google.android.apps.forscience.whistlepunk.sensorapi.StubStatusListener;
import com.google.common.collect.Lists;

import org.junit.Test;

public class MemoryRecorderControllerTest {
    @Test public void basicMemoryRecorderControllerTest() {
        MemoryRecorderController rc = new MemoryRecorderController();
        String observerId = rc.startObserving("sensorId", new RecordingSensorObserver(),
                new StubStatusListener(),
                AbstractReadableSensorOptions.makeTransportable(new BlankReadableSensorOptions()));
        assertEquals(Lists.newArrayList("sensorId"), rc.getCurrentObservedIds());
        rc.stopObserving("sensorId", observerId);
        assertEquals(Lists.newArrayList(), rc.getCurrentObservedIds());
    }
}