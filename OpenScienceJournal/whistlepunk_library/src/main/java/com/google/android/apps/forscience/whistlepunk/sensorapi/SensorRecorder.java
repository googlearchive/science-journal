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

import com.google.android.apps.forscience.whistlepunk.filemetadata.Trial;

/**
 * An object for controlling when this sensor starts and stops gathering data.
 *
 * <p>Expects for start and stop to each be called once, in that order.
 */
public interface SensorRecorder extends OptionsListener {
  /**
   * Start observing the data from this sensor. Optionally, the sensor _may_ begin to persist data,
   * if it is needed for real-time capture display.
   */
  void startObserving();

  /**
   * The user has requested that recording begin.
   *
   * <p>Subclasses _must_ record and persist data between calls to onStartRecording and
   * onStopRecording. They also _may_ persist data between startObserving and stopObserving, if it
   * is needed for real-time capture display, but data recorded during observation should be
   * periodically pruned to save storage
   *
   * @param runId runId that will identify this run in the database. (For now, the id of the
   *     startLabel)
   */
  void startRecording(String runId);

  /**
   * The user has requested that recording stop.
   *
   * @param trialToUpdate the trial to update with recorded stats.
   * @see {@link #startRecording} for semantics
   */
  void stopRecording(Trial trialToUpdate);

  // TODO: update spec to allow stopObserving to be called when recording should continue?
  // (context: before sensor-as-a-service, nothing could be recorded if it weren't also being
  // observed.  Now we can stop observing something, but still record it, but the
  // currently-implemented sensors still stop recording when stopObserving is called.  We should
  // probably either change some method names, or change the design).
  /**
   * Stop observing the data from this sensor. No listeners should be updated any longer, and no
   * data should be persisted.
   */
  void stopObserving();

  /**
   * Whether the sensor has gathered data since recording started. If not recording or no data was
   * gathered after recording started, this returns false.
   *
   * @return True if the sensor has gathered data since recording started. False otherwise.
   */
  boolean hasRecordedData();
}
