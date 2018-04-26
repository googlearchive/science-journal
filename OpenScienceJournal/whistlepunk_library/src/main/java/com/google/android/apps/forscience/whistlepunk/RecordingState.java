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

public enum RecordingState {
  // We are not yet connected to the RecorderController, and don't yet know the current state
  UNCONNECTED,

  // No current recording
  INACTIVE {
    @Override
    public boolean shouldEnableRecordButton() {
      return true;
    }
  },

  // User has requested recording to start, but it hasn't yet
  STARTING,

  // Currently recording
  ACTIVE {
    @Override
    public boolean shouldEnableRecordButton() {
      return true;
    }

    @Override
    public boolean shouldShowStopButton() {
      return true;
    }
  },

  // User has requested recording to stop, but it hasn't yet.
  STOPPING {
    @Override
    public boolean shouldShowStopButton() {
      return true;
    }
  };

  public boolean shouldEnableRecordButton() {
    return false;
  }

  public boolean shouldShowStopButton() {
    return false;
  }
}
