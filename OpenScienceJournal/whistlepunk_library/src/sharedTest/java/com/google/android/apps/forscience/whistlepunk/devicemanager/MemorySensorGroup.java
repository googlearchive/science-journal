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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

class MemorySensorGroup implements SensorGroup {
    private LinkedHashMap<String, ConnectableSensor> mSensors = new LinkedHashMap<>();

    @Override
    public boolean hasSensorKey(String sensorKey) {
        return mSensors.containsKey(sensorKey);
    }

    @Override
    public void addSensor(String sensorKey, ConnectableSensor sensor) {
        mSensors.put(sensorKey, sensor);
    }

    @Override
    public boolean removeSensor(String prefKey) {
        return mSensors.remove(prefKey) != null;
    }

    @Override
    public void replaceSensor(String sensorKey, ConnectableSensor sensor) {
        mSensors.put(sensorKey, sensor);
    }

    @Override
    public int getSensorCount() {
        return mSensors.size();
    }

    @Override
    public List<String> getAllKeys() {
        return new ArrayList<>(mSensors.keySet());
    }

    public String getKey(int location) {
        return new ArrayList<>(mSensors.keySet()).get(location);
    }

    public int size() {
        return mSensors.size();
    }

    public void removeAll() {
        mSensors.clear();
    }

    public String getTitle(int i) {
        return new ArrayList<>(mSensors.values()).get(i).getName();
    }
}
