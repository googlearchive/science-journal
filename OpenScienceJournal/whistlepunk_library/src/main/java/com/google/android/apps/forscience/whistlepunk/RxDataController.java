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

import com.google.android.apps.forscience.javalib.MaybeConsumers;
import com.google.android.apps.forscience.whistlepunk.filemetadata.Experiment;
import com.google.android.apps.forscience.whistlepunk.filemetadata.Label;
import com.google.android.apps.forscience.whistlepunk.filemetadata.LabelListHolder;
import com.google.android.apps.forscience.whistlepunk.filemetadata.Trial;

import io.reactivex.Completable;
import io.reactivex.Maybe;
import io.reactivex.Single;

/**
 * Utility methods for bridging DataController calls with code that uses Rx
 */
public class RxDataController {
    /**
     * @return a {@link Completable} that completes when updateExperiment is successful.  Caller
     *         <em>must</em> subscribe to this completeable to deal with errors and force execution.
     */
    public static Completable updateExperiment(DataController dc, Experiment e) {
        return MaybeConsumers.buildCompleteable(mc -> dc.updateExperiment(e.getExperimentId(), mc));
    }

    public static Completable updateLabel(DataController dc, LabelListHolder h, Label l,
            Experiment e) {
        h.updateLabel(l);
        return updateExperiment(dc, e);
    }

    public static Single<Experiment> getExperimentById(DataController dc, String experimentId) {
        return MaybeConsumers.buildSingle(mc -> dc.getExperimentById(experimentId, mc));
    }

    public static Single<Experiment> createExperiment(DataController dc) {
        return MaybeConsumers.buildSingle(mc -> dc.createExperiment(mc));
    }

    public static Single<Trial> getTrial(DataController dc, String experimentId, String trialId) {
        return getExperimentById(dc, experimentId).map(experiment -> experiment.getTrial(trialId));
    }

    public static Maybe<Trial> getTrialMaybe(DataController dc, String experimentId,
            String trialId) {
        return getExperimentById(dc, experimentId).flatMapMaybe(experiment -> {
            Trial trial = experiment.getTrial(trialId);
            if (trial != null) {
                return Maybe.just(trial);
            } else {
                return Maybe.empty();
            }
        });
    }

    public static Completable addTrialLabel(Label label, DataController dc, Experiment experiment,
            String trialId) {
        experiment.getTrial(trialId).addLabel(label);
        return updateExperiment(dc, experiment);
    }
}
