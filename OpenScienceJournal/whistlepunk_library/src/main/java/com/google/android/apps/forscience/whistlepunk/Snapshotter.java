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

import android.content.Context;
import androidx.annotation.VisibleForTesting;
import com.google.android.apps.forscience.whistlepunk.accounts.AppAccount;
import com.google.android.apps.forscience.whistlepunk.filemetadata.Experiment;
import com.google.android.apps.forscience.whistlepunk.filemetadata.Label;
import com.google.android.apps.forscience.whistlepunk.filemetadata.LabelListHolder;
import com.google.android.apps.forscience.whistlepunk.metadata.GoosciLabel;
import io.reactivex.Single;
import io.reactivex.SingleSource;
import io.reactivex.functions.Function;
import java.util.List;

public class Snapshotter {
  private final RecorderController recorderController;
  private final DataController dataController;
  private final SensorRegistry sensorRegistry;

  public Snapshotter(
      RecorderController recorderController,
      DataController dataController,
      SensorRegistry sensorRegistry) {
    this.recorderController = recorderController;
    this.dataController = dataController;
    this.sensorRegistry = sensorRegistry;
  }

  public static Snapshotter createFromContext(Context context, AppAccount appAccount) {
    AppSingleton singleton = AppSingleton.getInstance(context);
    return new Snapshotter(
        singleton.getRecorderController(appAccount),
        singleton.getDataController(appAccount),
        singleton.getSensorRegistry());
  }

  public Single<Label> addSnapshotLabel(String experimentId, RecordingStatus status) {
    // When experiment is loaded, add label
    return RxDataController.getExperimentById(dataController, experimentId)
        .flatMap(
            e -> {
              LabelListHolder holder =
                  status.isRecording() ? e.getTrial(status.getCurrentRunId()) : e;
              return addSnapshotLabelToHolder(e, holder, e.getSensorIds());
            });
  }

  public Single<Label> addSnapshotLabel(String experimentId, RecordingStatus status,
      List<String> ids) {
    // When experiment is loaded, add label
    return RxDataController.getExperimentById(dataController, experimentId)
        .<Label>flatMap(
            new Function<Experiment, SingleSource<? extends Label>>() {
              @Override
              public SingleSource<? extends Label> apply(Experiment e) throws Exception {
                LabelListHolder holder =
                    status.isRecording() ? e.getTrial(status.getCurrentRunId()) : e;
                return Snapshotter.this.addSnapshotLabelToHolder(e, holder, ids);
              }
            });
  }

  @VisibleForTesting
  public Single<Label> addSnapshotLabelToHolder(final Experiment selectedExperiment,
      final LabelListHolder labelListHolder, List<String> ids) {
    RecorderController rc = recorderController;

    // get proto
    return rc.generateSnapshotLabelValue(ids, sensorRegistry)

        // Make it into a label
        .map(
            snapshotValue ->
                Label.newLabelWithValue(
                    rc.getNow(), GoosciLabel.Label.ValueType.SNAPSHOT, snapshotValue, null))

        // Make sure it's successfully added
        .flatMap(
            label -> {
              labelListHolder.addLabel(selectedExperiment, label);
              return RxDataController.updateExperiment(dataController, selectedExperiment, true)
                  .andThen(Single.just(label));
            });
  }
}
