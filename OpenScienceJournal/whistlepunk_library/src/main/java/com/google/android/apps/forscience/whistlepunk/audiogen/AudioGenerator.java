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

package com.google.android.apps.forscience.whistlepunk.audiogen;

/** An interface to generate audio from raw data. */
public interface AudioGenerator {

  /** Start playing audio as long as data is available. */
  void startPlaying();

  /** Stop playing audio. */
  void stopPlaying();

  /** Reset the state of the AudioGenerator. Useful if a new set of data will be coming soon. */
  void reset();

  /**
   * Add data at a particular timesetamp.
   *
   * @param timestamp
   * @param data
   * @param min The minimum value shown on the graph.
   * @param max The maximum value shown on the graph.
   */
  void addData(long timestamp, double data, double min, double max);

  /** Called when the AudioGenerator should be removed from memory. */
  void destroy();

  void setSonificationType(String sonificationType);
}
