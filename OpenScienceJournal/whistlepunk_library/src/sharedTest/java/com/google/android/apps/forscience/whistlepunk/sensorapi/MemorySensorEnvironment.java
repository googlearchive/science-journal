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

package com.google.android.apps.forscience.whistlepunk.sensorapi;

import com.google.android.apps.forscience.ble.BleClient;
import com.google.android.apps.forscience.whistlepunk.Clock;
import com.google.android.apps.forscience.whistlepunk.RecordingDataController;
import com.google.android.apps.forscience.whistlepunk.SensorHistoryStorage;

public class MemorySensorEnvironment implements SensorEnvironment {
    private final RecordingDataController mDataController;
    private final Clock mClock;
    private FakeBleClient mBleClient;
    private SensorHistoryStorage mHistoryStorage;

    public MemorySensorEnvironment(RecordingDataController dataController, FakeBleClient bleClient,
            SensorHistoryStorage shs, Clock clock) {
        mDataController = dataController;
        mBleClient = bleClient;
        mHistoryStorage = shs;
        mClock = clock;
    }

    @Override
    public RecordingDataController getDataController() {
        return mDataController;
    }

    @Override
    public BleClient getBleClient() {
        return mBleClient;
    }

    @Override
    public Clock getDefaultClock() {
        return mClock;
    }

    @Override
    public SensorHistoryStorage getSensorHistoryStorage() {
        return mHistoryStorage;
    }

}
