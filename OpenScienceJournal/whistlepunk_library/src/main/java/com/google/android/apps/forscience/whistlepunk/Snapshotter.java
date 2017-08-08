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

import com.google.android.apps.forscience.whistlepunk.filemetadata.Experiment;
import com.google.android.apps.forscience.whistlepunk.filemetadata.Label;
import com.google.android.apps.forscience.whistlepunk.filemetadata.LabelListHolder;
import com.google.android.apps.forscience.whistlepunk.metadata.GoosciLabel;

import io.reactivex.Single;

public class Snapshotter {
    private final RecorderController mRecorderController;
    private final DataController mDataController;
    private final SensorRegistry mSensorRegistry;

    public Snapshotter(RecorderController recorderController, DataController dataController,
            SensorRegistry sensorRegistry) {
        mRecorderController = recorderController;
        mDataController = dataController;
        mSensorRegistry = sensorRegistry;
    }

    public Single<Label> addSnapshotLabel(String experimentId, RecordingStatus status) {
        // When experiment is loaded, add label
        return RxDataController.getExperimentById(mDataController, experimentId).flatMap(e -> {
            LabelListHolder holder =
                    status.isRecording() ? e.getTrial(status.getCurrentRunId()) : e;
            return addSnapshotLabelToHolder(e, holder);
        });
    }

    @VisibleForTesting
    public Single<Label> addSnapshotLabelToHolder(final Experiment selectedExperiment,
            final LabelListHolder labelListHolder) {
        RecorderController rc = mRecorderController;

        // get proto
        return rc.generateSnapshotLabelValue(selectedExperiment.getSensorIds(), mSensorRegistry)

                 // Make it into a label
                 .map(snapshotValue -> Label.newLabelWithValue(rc.getNow(),
                         GoosciLabel.Label.SNAPSHOT, snapshotValue, null))

                 // Make sure it's successfully added
                 .flatMap(label -> {
                     labelListHolder.addLabel(label);
                     return RxDataController.updateExperiment(mDataController, selectedExperiment)
                                            .andThen(Single.just(label));
                 });
    }
}
