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

import android.support.annotation.VisibleForTesting;

import com.google.android.apps.forscience.javalib.Delay;
import com.google.android.apps.forscience.javalib.MaybeConsumer;
import com.google.android.apps.forscience.javalib.Scheduler;
import com.google.android.apps.forscience.javalib.Success;
import com.google.android.apps.forscience.whistlepunk.filemetadata.Trial;
import com.google.android.apps.forscience.whistlepunk.sensorapi.ReadableSensorOptions;
import com.google.android.apps.forscience.whistlepunk.sensorapi.SensorRecorder;

public class StatefulRecorder {
    private boolean mObserving = false;
    private boolean mRecording = false;
    private final SensorRecorder mRecorder;
    private final Scheduler mScheduler;
    private Delay mStopDelay;
    private boolean mForceHasDataForTesting = false;
    private Runnable mStopRunnable;

    StatefulRecorder(SensorRecorder recorder, Scheduler scheduler, Delay stopDelay) {
        mRecorder = recorder;
        mScheduler = scheduler;
        mStopDelay = stopDelay;
    }

    public void startObserving() {
        cancelCurrentStopRunnable();
        if (!isStillRunning()) {
            mRecorder.startObserving();
        }
        mObserving = true;
    }

    public void reboot() {
        mRecorder.stopObserving();
        mRecorder.startObserving();
    }

    private void cancelCurrentStopRunnable() {
        if (mStopRunnable != null) {
            mScheduler.unschedule(mStopRunnable);
            mStopRunnable = null;
        }
    }

    public void stopObserving() {
        if (mStopRunnable != null) {
            // Already stopping.
            return;
        }
        Runnable stopRunnable = new Runnable() {
            @Override
            public void run() {
                mObserving = false;
                maybeStopObserving();
            }
        };
        if (mScheduler != null && !mStopDelay.isZero()) {
            mStopRunnable = stopRunnable;
            mScheduler.schedule(mStopDelay, mStopRunnable);
        } else {
            stopRunnable.run();
        }
    }

    /**
     * @param runId id for the run that is starting, to allow out-of-band data saving
     */
    public void startRecording(String runId) {
        mRecorder.startRecording(runId);
        mRecording = true;
    }


    void stopRecording(Trial trialToUpdate) {
        mRecorder.stopRecording(trialToUpdate);
        mRecording = false;
        maybeStopObserving();
    }

    // The spec for SensorRecorder says that once you stopObserving, the recorder should
    // shut down.  Unless we decide to change that (and retrofit all current sensors to that
    // new design), we should only _actually_ call stopObserving once we're no longer
    // observing OR recording.
    private void maybeStopObserving() {
        if (!isStillRunning()) {
            mRecorder.stopObserving();
        }
    }

    public boolean isStillRunning() {
        return mObserving || mRecording;
    }

    public void applyOptions(ReadableSensorOptions settings) {
        mRecorder.applyOptions(settings);
    }

    public boolean hasRecordedData() {
        return mRecorder.hasRecordedData() || mForceHasDataForTesting;
    }

    // TODO: Find a better way to fake force add data than this method.
    @VisibleForTesting
    void forceHasDataForTesting(boolean hasDataForTesting) {
        mForceHasDataForTesting = hasDataForTesting;
    }

    public boolean isRecording() {
        return mRecording;
    }
}
