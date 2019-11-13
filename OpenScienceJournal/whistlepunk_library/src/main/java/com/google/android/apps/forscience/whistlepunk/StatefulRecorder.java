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

import androidx.annotation.VisibleForTesting;
import com.google.android.apps.forscience.javalib.Delay;
import com.google.android.apps.forscience.javalib.Scheduler;
import com.google.android.apps.forscience.whistlepunk.filemetadata.Trial;
import com.google.android.apps.forscience.whistlepunk.sensorapi.ReadableSensorOptions;
import com.google.android.apps.forscience.whistlepunk.sensorapi.SensorRecorder;

public class StatefulRecorder {
  private boolean observing = false;
  private boolean recording = false;
  private final SensorRecorder recorder;
  private final Scheduler scheduler;
  private Delay stopDelay;
  private boolean forceHasDataForTesting = false;
  private Runnable stopRunnable;

  StatefulRecorder(SensorRecorder recorder, Scheduler scheduler, Delay stopDelay) {
    this.recorder = recorder;
    this.scheduler = scheduler;
    this.stopDelay = stopDelay;
  }

  public void startObserving() {
    cancelCurrentStopRunnable();
    if (!isStillRunning()) {
      recorder.startObserving();
    }
    observing = true;
  }

  public void reboot() {
    recorder.stopObserving();
    recorder.startObserving();
  }

  private void cancelCurrentStopRunnable() {
    if (stopRunnable != null) {
      scheduler.unschedule(stopRunnable);
      stopRunnable = null;
    }
  }

  public void stopObserving() {
    if (this.stopRunnable != null) {
      // Already stopping.
      return;
    }
    Runnable stopRunnable =
        new Runnable() {
          @Override
          public void run() {
            observing = false;
            maybeStopObserving();
          }
        };
    if (scheduler != null && !stopDelay.isZero()) {
      this.stopRunnable = stopRunnable;
      scheduler.schedule(stopDelay, this.stopRunnable);
    } else {
      stopRunnable.run();
    }
  }

  /** @param runId id for the run that is starting, to allow out-of-band data saving */
  public void startRecording(String runId) {
    recorder.startRecording(runId);
    recording = true;
  }

  void stopRecording(Trial trialToUpdate) {
    recorder.stopRecording(trialToUpdate);
    recording = false;
    maybeStopObserving();
  }

  // The spec for SensorRecorder says that once you stopObserving, the recorder should
  // shut down.  Unless we decide to change that (and retrofit all current sensors to that
  // new design), we should only _actually_ call stopObserving once we're no longer
  // observing OR recording.
  private void maybeStopObserving() {
    if (!isStillRunning()) {
      recorder.stopObserving();
    }
  }

  public boolean isStillRunning() {
    return observing || recording;
  }

  public void applyOptions(ReadableSensorOptions settings) {
    recorder.applyOptions(settings);
  }

  public boolean hasRecordedData() {
    return recorder.hasRecordedData() || forceHasDataForTesting;
  }

  // TODO: Find a better way to fake force add data than this method.
  @VisibleForTesting
  void forceHasDataForTesting(boolean hasDataForTesting) {
    forceHasDataForTesting = hasDataForTesting;
  }

  public boolean isRecording() {
    return recording;
  }
}
