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

import com.google.android.apps.forscience.javalib.MaybeConsumer;
import com.google.android.apps.forscience.javalib.Success;
import com.google.android.apps.forscience.whistlepunk.api.scalarinput.InputDeviceSpec;
import com.google.android.apps.forscience.whistlepunk.data.GoosciSensorSpec;
import com.google.android.apps.forscience.whistlepunk.filemetadata.Experiment;
import com.google.android.apps.forscience.whistlepunk.filemetadata.Trial;
import com.google.android.apps.forscience.whistlepunk.metadata.ExperimentSensors;
import com.google.android.apps.forscience.whistlepunk.metadata.ExternalSensorSpec;
import com.google.android.apps.forscience.whistlepunk.metadata.GoosciUserMetadata;
import com.google.android.apps.forscience.whistlepunk.metadata.MetaDataManager;
import com.google.android.apps.forscience.whistlepunk.sensordb.ScalarReading;
import com.google.android.apps.forscience.whistlepunk.sensordb.ScalarReadingList;
import com.google.android.apps.forscience.whistlepunk.sensordb.TimeRange;

import java.util.List;
import java.util.Map;

import io.reactivex.Observable;

/**
 * Provides access to any data outside of the UI.  All methods should be called from the UI thread;
 * callers can expect that methods will return quickly, and the provided callback will be called
 * later on the UI thread.
 */
public interface DataController {
    void getScalarReadings(String databaseTag, final int resolutionTier, TimeRange timeRange,
            int maxRecords, MaybeConsumer<ScalarReadingList> onSuccess);

    Observable<ScalarReading> createScalarObservable(String[] sensorIds,
            TimeRange timeRange, final int resolutionTier);

    void deleteTrialData(Trial trial, MaybeConsumer<Success> onSuccess);

    void createExperiment(MaybeConsumer<Experiment> onSuccess);

    void deleteExperiment(Experiment experiment, MaybeConsumer<Success> onSuccess);

    void getExperimentById(String experimentId, MaybeConsumer<Experiment> onSuccess);

    void updateExperiment(String experimentId, MaybeConsumer<Success> onSuccess);

    void updateExperiment(Experiment experiment, MaybeConsumer<Success> onSuccess);

    void saveImmediately(MaybeConsumer<Success> onSuccess);

    String generateNewLabelId();

    /**
     * Gets all experiment overviews.
     */
    void getExperimentOverviews(boolean includeArchived,
            MaybeConsumer<List<GoosciUserMetadata.ExperimentOverview>> onSuccess);

    /**
     * Gets all experiment overviews on the same thread as the caller.
     */
    List<GoosciUserMetadata.ExperimentOverview> blockingGetExperimentOverviews(
            boolean includeArchived);

    /**
     * Gets the most recently used, unarchived experiment.
     */
    void getLastUsedUnarchivedExperiment(MaybeConsumer<Experiment> onSuccess);

    void getExternalSensors(MaybeConsumer<Map<String, ExternalSensorSpec>> onSuccess);

    // TODO: fix docs, rename?
    /**
     * Passes to onSuccess a map from sensor ids to external sensor specs
     */
    void getExternalSensorsByExperiment(final String experimentId,
            MaybeConsumer<ExperimentSensors> onSuccess);

    /**
     * Passes to onSuccess a map from sensor ids to external sensor specs
     */
    void getExternalSensorById(String id, MaybeConsumer<ExternalSensorSpec> onSuccess);

    /**
     * Adds the external sensor to the experiment.
     *
     * @return {@code true} to the consumer if the sensor was added, {@code false} if the sensor
     * was already present.
     */
    void addSensorToExperiment(final String experimentId, String sensorId,
            MaybeConsumer<Success> onSuccess);

    /**
     * Removes the external sensor from the experiment.
     *
     * @return {@code true} to the consumer if the sensor needed to be removed, {@code false} if
     * the sensor was not already present.
     */
    void removeSensorFromExperiment(final String experimentId, final String sensorId,
            MaybeConsumer<Success> onSuccess);

    /**
     * Makes sure there is an external sensor already registered in the database with the given
     * spec, and returns its id to {@code onSensorId}
     */
    void addOrGetExternalSensor(ExternalSensorSpec sensor, MaybeConsumer<String> onSensorId);

    // TODO: layout and external sensor storage is redundant
    /**
     * Replace oldSensorId with newSensorId in experiment.  All cards that were showing oldSensorId
     * will now show newSensorId
     */
    void replaceSensorInExperiment(String experimentId, String oldSensorId, String newSensorId,
            final MaybeConsumer<Success> onSuccess);

    void getMyDevices(MaybeConsumer<List<InputDeviceSpec>> onSuccess);

    void forgetMyDevice(InputDeviceSpec spec, MaybeConsumer<Success> onSuccess);

    void addMyDevice(InputDeviceSpec spec, MaybeConsumer<Success> onSuccess);
}
