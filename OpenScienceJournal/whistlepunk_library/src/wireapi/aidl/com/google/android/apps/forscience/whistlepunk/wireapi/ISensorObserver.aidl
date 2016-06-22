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

// SensorObserver.aidl
package com.google.android.apps.forscience.whistlepunk.wireapi;

/**
 * Observes changes to a sensor, potentially serialized between processes.
 *
 * Assume all calls are on the main thread
 */
interface ISensorObserver {
    /**
     * Called when new data arrives.  Extender must copy or extract any values from {@code data}
     * that it wishes to use after returning; caller can re-use the same reference to reduce
     * allocations.
     */
    void onNewData(long timestamp, in Bundle data) = 0;
}
