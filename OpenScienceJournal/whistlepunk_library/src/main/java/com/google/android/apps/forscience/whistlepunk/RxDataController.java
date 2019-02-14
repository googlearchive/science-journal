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

import android.util.Log;
import com.google.android.apps.forscience.javalib.MaybeConsumers;
import com.google.android.apps.forscience.whistlepunk.filemetadata.Change;
import com.google.android.apps.forscience.whistlepunk.filemetadata.Experiment;
import com.google.android.apps.forscience.whistlepunk.filemetadata.FileSyncCollection;
import com.google.android.apps.forscience.whistlepunk.filemetadata.Label;
import com.google.android.apps.forscience.whistlepunk.filemetadata.LabelListHolder;
import com.google.android.apps.forscience.whistlepunk.filemetadata.Trial;
import io.reactivex.Completable;
import io.reactivex.Maybe;
import io.reactivex.Single;
import java.io.File;

/** Utility methods for bridging DataController calls with code that uses Rx */
public class RxDataController {
  private static final String TAG = "RxDataController";

  public static Completable updateExperiment(
      DataController dc, Experiment e, long lastUsedTime, boolean shouldMarkDirty) {
    return MaybeConsumers.buildCompleteable(
        mc -> dc.updateExperiment(e.getExperimentId(), lastUsedTime, shouldMarkDirty, mc));
  }

  public static Completable updateExperimentEvenIfNotActive(
      DataController dc, Experiment e, long lastUsedTime, boolean shouldMarkDirty) {
    return MaybeConsumers.buildCompleteable(
        mc -> dc.updateExperimentEvenIfNotActive(e, lastUsedTime, shouldMarkDirty, mc));
  }

  public static Completable updateExperiment(
      DataController dc, Experiment e, boolean shouldMarkDirty) {
    return MaybeConsumers.buildCompleteable(
        mc -> dc.updateExperiment(e.getExperimentId(), shouldMarkDirty, mc));
  }

  public static Completable deleteExperiment(DataController dc, Experiment e) {
    return MaybeConsumers.buildCompleteable(mc -> dc.deleteExperiment(e, mc));
  }

  public static Completable deleteExperiment(DataController dc, String experimentId) {
    return MaybeConsumers.buildCompleteable(mc -> dc.deleteExperiment(experimentId, mc));
  }

  public static Single<FileSyncCollection> mergeExperiment(
      DataController dc, String id, Experiment toMerge, boolean overwrite) {
    return MaybeConsumers.buildSingle(mc -> dc.mergeExperiment(id, toMerge, overwrite, mc));
  }

  public static Completable addExperiment(DataController dc, Experiment e) {
    return MaybeConsumers.buildCompleteable(mc -> dc.addExperiment(e, mc));
  }

  public static Completable updateLabel(
      DataController dc, LabelListHolder h, Label l, Experiment e, Change c) {
    h.updateLabel(e, l, c);
    return updateExperiment(dc, e, true);
  }

  public static Single<Experiment> getExperimentById(DataController dc, String experimentId) {
    Exception justInCase = new Exception("getExperimentById failed");
    return MaybeConsumers.<Experiment>buildSingle(mc -> dc.getExperimentById(experimentId, mc))
        .doOnError(
            throwable -> {
              if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "getExperimentById failed", justInCase);
              }
            });
  }

  public static Single<Boolean> experimentExists(DataController dc, String experimentId) {
    Exception justInCase = new Exception("getExperimentById failed");
    return MaybeConsumers.<Boolean>buildSingle(mc -> dc.experimentExists(experimentId, mc))
        .doOnError(
            throwable -> {
              if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "getExperimentById failed", justInCase);
              }
            });
  }

  public static Single<Experiment> createExperiment(DataController dc) {
    return MaybeConsumers.buildSingle(mc -> dc.createExperiment(mc));
  }

  public static Single<Trial> getTrial(DataController dc, String experimentId, String trialId) {
    return getExperimentById(dc, experimentId).map(experiment -> experiment.getTrial(trialId));
  }

  public static Maybe<Trial> getTrialMaybe(DataController dc, String experimentId, String trialId) {
    return getExperimentById(dc, experimentId)
        .flatMapMaybe(
            experiment -> {
              Trial trial = experiment.getTrial(trialId);
              if (trial != null) {
                return Maybe.just(trial);
              } else {
                return Maybe.empty();
              }
            });
  }

  public static Completable addTrialLabel(
      Label label, DataController dc, Experiment experiment, String trialId) {
    experiment.getTrial(trialId).addLabel(experiment, label);
    return updateExperiment(dc, experiment, true);
  }

  public static Single<File> writeTrialProtoToFile(
      DataController dc, String experimentId, String trialId) {
    return MaybeConsumers.buildSingle(mc -> dc.writeTrialProtoToFile(experimentId, trialId, mc));
  }
}
