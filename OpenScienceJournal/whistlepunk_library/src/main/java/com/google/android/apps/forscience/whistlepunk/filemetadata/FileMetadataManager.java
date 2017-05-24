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
import android.support.annotation.VisibleForTesting;
import android.util.Log;

import com.google.android.apps.forscience.whistlepunk.Clock;
import com.google.android.apps.forscience.whistlepunk.ExternalSensorProvider;
import com.google.android.apps.forscience.whistlepunk.api.scalarinput.InputDeviceSpec;
import com.google.android.apps.forscience.whistlepunk.devicemanager.ConnectableSensor;
import com.google.android.apps.forscience.whistlepunk.metadata.ExperimentSensors;
import com.google.android.apps.forscience.whistlepunk.metadata.ExternalSensorSpec;
import com.google.android.apps.forscience.whistlepunk.metadata.GoosciUserMetadata;

import java.io.File;
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
    private static final String USER_METADATA_FILE = "user_metadata.proto";

    private Clock mClock;

    private ExperimentCache mActiveExperimentCache;
    private UserMetadataManager mUserMetadataManager;

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
            public void onReadFailed(GoosciUserMetadata.ExperimentOverview experimentOverview) {
                // TODO: Propagate this up to the user somehow.
                Log.d(TAG, "read failed");
            }

            @Override
            public void onNewerVersionDetected(
                    GoosciUserMetadata.ExperimentOverview experimentOverview) {
                // TODO: Propagate this up to the user somehow.
                Log.d(TAG, "newer proto version detected than we can handle");
            }
        };
        UserMetadataManager.FailureListener userMetadataListener =
                new UserMetadataManager.FailureListener() {

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

                    @Override
                    public void onNewerVersionDetected() {
                        // TODO: Propagate this up to the user somehow.
                        Log.d(TAG, "newer proto version detected than we can handle");
                    }
                };
        mActiveExperimentCache = new ExperimentCache(applicationContext, failureListener);
        mUserMetadataManager = new UserMetadataManager(applicationContext,
                userMetadataListener);
    }

    /**
     * Deletes all experiments in the list of experiment IDs.
     */
    public void deleteAll(List<String> experimentIds) {
        for (String experimentId : experimentIds) {
            deleteExperiment(experimentId);
        }
    }

    public Experiment getExperimentById(String experimentId) {
        GoosciUserMetadata.ExperimentOverview overview =
                mUserMetadataManager.getExperimentOverview(experimentId);
        if (overview == null) {
            return null;
        }
        return mActiveExperimentCache.getExperiment(overview);
    }

    public Experiment newExperiment() {
        long timestamp = mClock.getNow();
        String localExperimentId = "experiment_" + timestamp;
        Experiment experiment = Experiment.newExperiment(timestamp, localExperimentId);

        addExperiment(experiment);
        return experiment;
    }

    // Adds an existing experiment to the file system (rather than creating a new one).
    private void addExperiment(Experiment experiment) {
        // Write the experiment to a file
        mActiveExperimentCache.createNewExperiment(experiment);
        mUserMetadataManager.addExperimentOverview(experiment.getExperimentOverview());
    }

    // Adds an experiment to the file system and saves it immediately.
    public void addExperimentImmediately(Experiment experiment) {
        addExperiment(experiment);
        mActiveExperimentCache.saveImmediately();
    }

    public void deleteExperiment(Experiment experiment) {
        deleteExperiment(experiment.getExperimentId());
    }

    private void deleteExperiment(String experimentId) {
        mActiveExperimentCache.deleteExperiment(experimentId);
        mUserMetadataManager.deleteExperimentOverview(experimentId);
    }

    public void updateExperiment(Experiment experiment) {
        mActiveExperimentCache.updateExperiment(experiment);

        // TODO: Only do this if strictly necessary, instead of every time?
        // Or does updateExperiment mean the last updated time should change, and we need a clock?
        mUserMetadataManager.updateExperimentOverview(experiment.getExperimentOverview());
    }

    public List<GoosciUserMetadata.ExperimentOverview> getExperimentOverviews(
            boolean includeArchived) {
        return mUserMetadataManager.getExperimentOverviews(includeArchived);
    }

    public Experiment getLastUsedUnarchivedExperiment() {
        List<GoosciUserMetadata.ExperimentOverview> overviews = getExperimentOverviews(false);
        long mostRecent = Long.MIN_VALUE;
        GoosciUserMetadata.ExperimentOverview overviewToGet = null;
        for (GoosciUserMetadata.ExperimentOverview overview : overviews) {
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
        mUserMetadataManager.updateExperimentOverview(experiment.getExperimentOverview());
    }

    public void close() {
        mActiveExperimentCache.saveImmediately();
    }

    @VisibleForTesting
    public static File getUserMetadataFile(Context context) {
        return new File(context.getFilesDir(), USER_METADATA_FILE);
    }

    public static File getAssetsDirectory(Context context, String experimentId) {
        return new File(context.getFilesDir() + "/experiments/" + experimentId + "/" +
                ASSETS_DIRECTORY);
    }
}