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

import io.reactivex.Completable;
import io.reactivex.Maybe;
import io.reactivex.Single;

/**
 * Utility methods for bridging DataController calls with code that uses Rx
 */
public class RxDataController {
    /**
     * @return a {@link Completable} that completes when updateExperiment is successful.
     */
    public static Completable updateExperiment(DataController dc, Experiment e) {
        return MaybeConsumers.buildCompleteable(mc -> dc.updateExperiment(e.getExperimentId(), mc));
    }

    public static Single<Experiment> getExperimentById(DataController dc, String experimentId) {
        return MaybeConsumers.buildSingle(mc -> dc.getExperimentById(experimentId, mc));
    }

    public static Single<Experiment> loadOrCreateRecentExperiment(DataController dc) {
        // The toMaybe/toSingle is a weird dance required by Rx, which doesn't have a direct
        // switchIfEmpty(Single) (https://github.com/ReactiveX/RxJava/issues/4544)
        return getLastUsedUnarchivedExperiment(dc).switchIfEmpty(createExperiment(dc).toMaybe())
                                                  .toSingle();
    }

    public static Single<Experiment> createExperiment(DataController dc) {
        return MaybeConsumers.buildSingle(mc -> dc.createExperiment(mc));
    }

    public static Maybe<Experiment> getLastUsedUnarchivedExperiment(DataController dc) {
        return MaybeConsumers.buildMaybe(mc -> dc.getLastUsedUnarchivedExperiment(mc));
    }
}
