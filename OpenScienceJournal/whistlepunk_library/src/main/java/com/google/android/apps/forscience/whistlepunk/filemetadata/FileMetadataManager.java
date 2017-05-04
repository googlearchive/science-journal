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

package com.google.android.apps.forscience.whistlepunk.filemetadata;

import android.content.Context;
import android.util.Log;

import com.google.android.apps.forscience.whistlepunk.Clock;
import com.google.android.apps.forscience.whistlepunk.ExternalSensorProvider;
import com.google.android.apps.forscience.whistlepunk.api.scalarinput.InputDeviceSpec;
import com.google.android.apps.forscience.whistlepunk.metadata.ExperimentSensors;
import com.google.android.apps.forscience.whistlepunk.metadata.ExternalSensorSpec;
import com.google.android.apps.forscience.whistlepunk.metadata.GoosciSharedMetadata;

import java.util.List;
import java.util.Map;

/**
 * MetadataManager backed by a file-based system using internal storage.
 */
// TODO: Extend MetadataManager
public class FileMetadataManager {
    private static final String TAG = "FileMetadataManager";
    static final String ASSETS_DIRECTORY = "assets";
    static final String EXPERIMENT_FILE = "experiment.proto";
    static final String SHARED_METADATA_FILE = "shared_metadata.proto";

    private Clock mClock;

    private ExperimentCache mActiveExperimentCache;
    private SharedMetadataManager mSharedMetadataManager;

    public FileMetadataManager(Context applicationContext, Clock clock) {
        mClock = clock;
        // TODO: Probably pass failure listeners from a higher level in order to propgate them
        // up to the user.
        ExperimentCache.FailureListener failureListener = new ExperimentCache.FailureListener() {
            @Override
            public void onWriteFailed(Experiment experimentToWrite) {
                // TODO: Propagate this up to the user somehow.
                Log.d(TAG, "write failed");
            }

            @Override
            public void onReadFailed(
                    GoosciSharedMetadata.ExperimentOverview experimentOverview) {
                // TODO: Propagate this up to the user somehow.
                Log.d(TAG, "read failed");
            }
        };
        SharedMetadataManager.FailureListener sharedMetadataListener =
                new SharedMetadataManager.FailureListener() {

                    @Override
                    public void onWriteFailed() {
                        // TODO: Propagate this up to the user somehow.
                        Log.d(TAG, "write failed");
                    }

                    @Override
                    public void onReadFailed() {
                        // TODO: Propagate this up to the user somehow.
                        Log.d(TAG, "read failed");
                    }
                };
        mActiveExperimentCache = new ExperimentCache(applicationContext, failureListener);
        mSharedMetadataManager = new SharedMetadataManager(applicationContext,
                sharedMetadataListener);
    }

    public Experiment getExperimentById(String experimentId) {
        GoosciSharedMetadata.ExperimentOverview overview =
                mSharedMetadataManager.getExperimentOverview(experimentId);
        if (overview == null) {
            return null;
        }
        return mActiveExperimentCache.getExperiment(overview);
    }

    public Experiment newExperiment() {
        long timestamp = mClock.getNow();
        String localExperimentId = "experiment_" + timestamp;
        Experiment experiment = Experiment.newExperiment(timestamp, localExperimentId);

        // Write the experiment to a file
        mActiveExperimentCache.createNewExperiment(experiment);
        mSharedMetadataManager.addExperimentOverview(experiment.getExperimentOverview());
        return experiment;
    }

    public void deleteExperiment(Experiment experiment) {
        mActiveExperimentCache.deleteExperiment(experiment.getExperimentId());
        mSharedMetadataManager.deleteExperimentOverview(experiment.getExperimentOverview());
    }

    public void updateExperiment(Experiment experiment) {
        mActiveExperimentCache.updateExperiment(experiment);

        // TODO: Only do this if strictly necessary, instead of every time?
        // Or does updateExperiment mean the last updated time should change, and we need a clock?
        mSharedMetadataManager.updateExperimentOverview(experiment.getExperimentOverview());
    }

    public List<GoosciSharedMetadata.ExperimentOverview> getExperimentOverviews(
            boolean includeArchived) {
        return mSharedMetadataManager.getExperimentOverviews(includeArchived);
    }

    public Experiment getLastUsedUnarchivedExperiment() {
        List<GoosciSharedMetadata.ExperimentOverview> overviews = getExperimentOverviews(false);
        long mostRecent = Long.MIN_VALUE;
        GoosciSharedMetadata.ExperimentOverview overviewToGet = null;
        for (GoosciSharedMetadata.ExperimentOverview overview : overviews) {
            if (overview.lastUsedTimeMs > mostRecent) {
                mostRecent = overview.lastUsedTimeMs;
                overviewToGet = overview;
            }
        }
        if (overviewToGet != null) {
            return mActiveExperimentCache.getExperiment(overviewToGet);
        }
        return null;
    }

    public void setLastUsedExperiment(Experiment experiment) {
        long timestamp = mClock.getNow();
        experiment.setLastUsedTime(timestamp);
        mActiveExperimentCache.onExperimentOverviewUpdated(experiment.getExperimentOverview());
        mSharedMetadataManager.updateExperimentOverview(experiment.getExperimentOverview());
    }

    public Map<String, ExternalSensorSpec> getExternalSensors(
            Map<String, ExternalSensorProvider> providerMap) {
        // TODO
        return null;
    }

    public ExternalSensorSpec getExternalSensorById(String id,
            Map<String, ExternalSensorProvider> providerMap) {
        // TODO
        return null;
    }

    public String addOrGetExternalSensor(ExternalSensorSpec sensor,
            Map<String, ExternalSensorProvider> providerMap) {
        // TODO
        return null;
    }

    public void removeExternalSensor(String databaseTag) {
        // TODO
    }

    public ExperimentSensors getExperimentExternalSensors(String experimentId,
            Map<String, ExternalSensorProvider> providerMap) {
        // TODO
        return null;
    }

    public void addMyDevice(InputDeviceSpec deviceSpec) {
        // TODO
    }

    public void removeMyDevice(InputDeviceSpec deviceSpec) {
        // TODO
    }

    public List<InputDeviceSpec> getMyDevices() {
        // TODO
        return null;
    }
}
