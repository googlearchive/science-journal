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

import com.jsyn.unitgen.UnitVoice;
import com.softsynth.shared.time.TimeStamp;

/** Interface that adapts between the SimpleJsynAudioGenerator and custom unit voices. */
public interface JsynUnitVoiceAdapterInterface {
  /**
   * Turn on a note by transforming value (in range min-max).
   *
   * <p>Play whatever you consider to be a note on this voice.
   *
   * @param value the value to be transformed to a note
   * @param min the minimum for the value range
   * @param max the maximum for the value range
   * @param timeStamp the timestamp at which to play the note
   */
  public void noteOn(double value, double min, double max, TimeStamp timeStamp);

  /**
   * Return the UnitVoice.
   *
   * @return UnitVoice
   */
  public UnitVoice getVoice();
}
