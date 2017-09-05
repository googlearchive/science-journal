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
import com.google.android.apps.forscience.whistlepunk.ColorAllocator;
import com.google.android.apps.forscience.whistlepunk.R;
import com.google.android.apps.forscience.whistlepunk.data.GoosciDeviceSpec;
import com.google.android.apps.forscience.whistlepunk.metadata.GoosciUserMetadata;

import java.io.File;
import java.util.List;
import java.util.UUID;

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
    private ColorAllocator mColorAllocator;

    public FileMetadataManager(Context applicationContext, Clock clock) {
        mClock = clock;
        // TODO: Probably pass failure listeners from a higher level in order to propagate them
        // up to the user. b/62373187.
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
        mColorAllocator = new ColorAllocator(applicationContext.getResources()
                .getIntArray(R.array.experiment_colors_array).length);
    }

    /**
     * Deletes all experiments in the list of experiment IDs.
     * This really deletes everything and should be used very sparingly!
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
        String localExperimentId = UUID.randomUUID().toString();
        List<GoosciUserMetadata.ExperimentOverview> overviews =
                mUserMetadataManager.getExperimentOverviews(true);
        int[] usedColors = new int[overviews.size()];
        for (int i = 0; i < overviews.size(); i++) {
            usedColors[i] = overviews.get(i).colorIndex;
        }
        int colorIndex = mColorAllocator.getNextColor(usedColors);
        Experiment experiment = Experiment.newExperiment(timestamp, localExperimentId, colorIndex);

        addExperiment(experiment);
        return experiment;
    }

    // Adds an existing experiment to the file system (rather than creating a new one).
    // This should just be used for data migration and testing.
    public void addExperiment(Experiment experiment) {
        // Get ready to write the experiment to a file. Will write when the timer expires.
        mActiveExperimentCache.createNewExperiment(experiment);
        mUserMetadataManager.addExperimentOverview(experiment.getExperimentOverview());
    }

    public void saveImmediately() {
        mActiveExperimentCache.saveImmediately();
        mUserMetadataManager.saveImmediately();
    }

    public void deleteExperiment(Experiment experiment) {
        mActiveExperimentCache.prepareExperimentForDeletion(experiment);
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

    /**
     * Sets the most recently used experiment. This should only be used in testing -- the experiment
     * object should not actually be modified by FileMetadataManager.
     */
    @Deprecated
    public void setLastUsedExperiment(Experiment experiment) {
        long timestamp = mClock.getNow();
        experiment.setLastUsedTime(timestamp);
        mActiveExperimentCache.onExperimentOverviewUpdated(experiment.getExperimentOverview());
        mUserMetadataManager.updateExperimentOverview(experiment.getExperimentOverview());
    }

    public void close() {
        saveImmediately();
    }

    @VisibleForTesting
    public static File getUserMetadataFile(Context context) {
        return new File(context.getFilesDir(), USER_METADATA_FILE);
    }

    public static File getAssetsDirectory(Context context, String experimentId) {
        return new File(getExperimentDirectory(context, experimentId) + ASSETS_DIRECTORY);
    }

    private static String getExperimentDirectory(Context context, String experimentId) {
        return getExperimentsRootDirectory(context) + experimentId + "/";
    }

    public static String getExperimentsRootDirectory(Context context) {
        return context.getFilesDir() + "/experiments/";
    }

    /**
     * Gets the relative path to a file within an experiment. For example, if the file is
     * a picture pic.png in the assets/ directory of experiment xyz, this will return just
     * "assets/pic.png". If the file is not in xyz but the experimentId passed in is xyz, this will
     * return an empty string.
     */
    public static String getRelativePathInExperiment(String experimentId, File file) {
        String absolutePath = file.getAbsolutePath();
        int start = absolutePath.indexOf(experimentId);
        if (start < 0) {
            // This file is not part of this experiment.
            return "";
        } else {
            return absolutePath.substring(start + experimentId.length() + 1);
        }
    }

    /**
     * Gets a file in an experiment from a relative path to that file within the experiment.
     */
    public static File getExperimentFile(Context context, String experimentId,
            String relativePath) {
        return new File(getExperimentDirectory(context, experimentId) + "/" + relativePath);
    }

    /**
     * Gets the relative path to the file from the user's files directory.
     * This can be used to create the imagePath in UserMetadata.ExperimentOverview.
     */
    public static String getRelativePathInFilesDir(String experimentId, String relativePath) {
        return "experiments/" + experimentId + "/" + relativePath;
    }

    public void addMyDevice(GoosciDeviceSpec.DeviceSpec device) {
        mUserMetadataManager.addMyDevice(device);
    }

    public void removeMyDevice(GoosciDeviceSpec.DeviceSpec device) {
        mUserMetadataManager.removeMyDevice(device);
    }

    public List<GoosciDeviceSpec.DeviceSpec> getMyDevices() {
        return mUserMetadataManager.getMyDevices();
    }

}