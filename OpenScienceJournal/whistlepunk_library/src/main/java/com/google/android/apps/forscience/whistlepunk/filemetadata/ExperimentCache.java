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
import android.os.Handler;
import android.text.TextUtils;
import android.util.Log;

import com.google.android.apps.forscience.whistlepunk.metadata.GoosciExperiment;
import com.google.android.apps.forscience.whistlepunk.metadata.GoosciSharedMetadata;
import com.google.common.annotations.VisibleForTesting;

import java.io.File;
import java.io.IOException;

/**
 * This reads and writes experiments to disk. It caches the last used experiment to avoid extra
 * file operations.
 * This class should be constructed and used from a background thread because it does file
 * operations.
 */
class ExperimentCache {
    private static final String TAG = "ExperimentCache";

    // Write the experiment file no more than once per every WRITE_DELAY_MS.
    private static final long WRITE_DELAY_MS = 1000;

    public interface FailureListener {
        // TODO: What's helpful to pass back here? Maybe info about the type of error?
        void onWriteFailed(Experiment experimentToWrite);
        void onReadFailed(GoosciSharedMetadata.ExperimentOverview localExperimentOverview);
    }

    private FailureListener mFailureListener;
    private Context mContext;
    private Experiment mActiveExperiment;
    private ProtoFileHelper<GoosciExperiment.Experiment> mExperimentProtoFileHelper;
    private boolean mActiveExperimentNeedsWrite = false;
    private final long mWriteDelayMs;

    private Handler mHandler;
    private Runnable mWriteRunnable;

    public ExperimentCache(Context context, FailureListener failureListener) {
        this (context, failureListener, WRITE_DELAY_MS);
    }

    @VisibleForTesting
    ExperimentCache(Context context, FailureListener failureListener, long writeDelayMs) {
        mContext = context;
        mFailureListener = failureListener;
        mExperimentProtoFileHelper = new ProtoFileHelper<>();
        mHandler = new Handler();
        mWriteRunnable = new Runnable() {
            @Override
            public void run() {
                if (mActiveExperimentNeedsWrite) {
                    writeActiveExperimentFile();
                }
            }
        };
        mWriteDelayMs = writeDelayMs;
    }

    @VisibleForTesting
    Experiment getActiveExperimentForTests() {
        return mActiveExperiment;
    }

    /**
     * Creates file space for a new experiment, and gets it ready for a save.
     * @return whether space was created successfully.
     */
    boolean createNewExperiment(Experiment experiment) {
        if (!prepareForNewExperiment(experiment.getExperimentOverview().experimentId)) {
            mFailureListener.onWriteFailed(experiment);
            return false;
        }
        return setExistingActiveExperiment(experiment);
    }

    /**
     * Updates the given experiment.
     */
    void updateExperiment(Experiment experiment) {
        if (isDifferentFromActive(experiment.getExperimentOverview())) {
            immediateWriteIfActiveChanging(experiment.getExperimentOverview());
        }
        mActiveExperiment = experiment;
        startWriteTimer();
    }

    /**
     * Updates the experiment overview of the active experiment if the active experiment has the
     * same ID. This allows us to keep the experimentOverview fresh without doing extra writes
     * to disk.
     * If the active experiment is not the same ID as the experiment overview to update, no action
     * needs to be taken.
     * @param experimentOverview the updated experimentOverview to set on the cached experiment
     *                           if they have the same ID.
     */
    void onExperimentOverviewUpdated(
            GoosciSharedMetadata.ExperimentOverview experimentOverview) {
        if (!isDifferentFromActive(experimentOverview)) {
            mActiveExperiment.setLastUsedTime(experimentOverview.lastUsedTimeMs);
            mActiveExperiment.setArchived(experimentOverview.isArchived);
            mActiveExperiment.getExperimentOverview().imagePath = experimentOverview.imagePath;
        }
    }

    /**
     * Loads an active experiment from the disk if it is different from the currently cached active
     * experiment, otherwise just returns the current cached experiment.
     * @param localExperimentOverview The local ExperimentOverview of the experiment to load. This
     *                                is used for lookup.
     */
    Experiment getExperiment(GoosciSharedMetadata.ExperimentOverview localExperimentOverview) {
        // Write only if the experiment ID is changing. If it's not changing, we just want to
        // reload even if it was dirty.
        if (isDifferentFromActive(localExperimentOverview)) {
            immediateWriteIfActiveChanging(localExperimentOverview);
            loadActiveExperimentFromFile(localExperimentOverview);
        }
        return mActiveExperiment;
    }

    /**
     * Deletes an experiment from disk. Doesn't need to be the active one to be deleted.
     * Sets the active experiment to null if it is the same one
     */
    void deleteExperiment(String localExperimentId) {
        File expDirectory = getExperimentDirectory(localExperimentId);
        if (!deleteRecursive(expDirectory)) {
            // TODO show an error to the user, something has gone wrong
            // We are also in a weird partially deleted state at this point, need to fix.
            // TODO: Does any other work need to be done deleting assets, i.e. unregistering them
            // so that the user can't see pictures any more?
            return;
        }
        if (mActiveExperiment != null && TextUtils.equals(
                mActiveExperiment.getExperimentOverview().experimentId, localExperimentId)) {
            mActiveExperiment = null;
            cancelWriteTimer();
            mActiveExperimentNeedsWrite = false;
        }
    }

    /**
     * Used to set the active experiment when the experiment already exists, not when it's being
     * made for the first time.
     * Sets the dirty bit to true, then starts a timer if needed to make sure that the write happens
     * within a reasonable time frame.
     */
    private boolean setExistingActiveExperiment(Experiment experiment) {
        immediateWriteIfActiveChanging(experiment.getExperimentOverview());

        // Then set the new experiment and set the dirty bit to true, resetting the write timer,
        // so that we save it soon.
        mActiveExperiment = experiment;
        startWriteTimer();
        return true;
    }

    /**
     * Create a folder with the experiment ID, and create the appropriate folders within it.
     */
    private boolean prepareForNewExperiment(String localExperimentId) {
        File expDirectory = getExperimentDirectory(localExperimentId);
        if (!expDirectory.exists() && !expDirectory.mkdirs()) {
            return false;
        };
        // TODO: Store run data in assets or somewhere else?
        // If somewhere else, need to make the directory for that.
        File assetsDirectory = getAssetsDirectory(expDirectory);
        if (!assetsDirectory.exists() && !assetsDirectory.mkdir()) {
            return false;

        }
        // Create the experimentFile
        try {
            File expFile = getExperimentFile(localExperimentId);
            if (!expFile.exists()) {
                boolean created = expFile.createNewFile();
                if (!created) {
                    return false;
                }
            }
        } catch (IOException e) {
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, Log.getStackTraceString(e));
            }
            return false;
        }
        return true;
    }

    /**
     * Immediately writes the current active experiment to disk if it is different from the given
     * parameter localExperimentId.
     */
    private void immediateWriteIfActiveChanging(GoosciSharedMetadata.ExperimentOverview
            localExperimentOverview) {
        if (mActiveExperimentNeedsWrite && isDifferentFromActive(localExperimentOverview)) {
            // First write the old active experiment if the ID has changed.
            // Then cancel the write timer on the old experiment. We will reset it below.
            cancelWriteTimer();
            writeActiveExperimentFile();
        }
    }

    private void cancelWriteTimer() {
        mHandler.removeCallbacks(mWriteRunnable);
    }

    private void startWriteTimer() {
        if (mActiveExperimentNeedsWrite) {
            // The timer is already running.
            return;
        }
        mActiveExperimentNeedsWrite = true;
        mHandler.postDelayed(mWriteRunnable, mWriteDelayMs);
    }

    @VisibleForTesting
    boolean needsWrite() {
        return mActiveExperimentNeedsWrite;
    }

    /**
     * Writes the active experiment to a file.
     */
    @VisibleForTesting
    void writeActiveExperimentFile() {
        File experimentFile = getExperimentFile(mActiveExperiment.getExperimentOverview());
        boolean success = mExperimentProtoFileHelper.writeToFile(experimentFile,
                mActiveExperiment.getExperimentProto());
        if (success) {
            mActiveExperimentNeedsWrite = false;
        } else {
            mFailureListener.onWriteFailed(mActiveExperiment);
        }
    }

    @VisibleForTesting
    void loadActiveExperimentFromFile(GoosciSharedMetadata.ExperimentOverview experimentOverview) {
        File experimentFile = getExperimentFile(experimentOverview);
        GoosciExperiment.Experiment proto = new GoosciExperiment.Experiment();
        boolean success = mExperimentProtoFileHelper.readFromFile(experimentFile, proto);
        if (success) {
            mActiveExperiment = Experiment.fromExperiment(proto, experimentOverview);
        } else {
            // Or maybe pass a FailureListener into the load instead of failing here.
            mFailureListener.onReadFailed(experimentOverview);
            mActiveExperiment = null;
        }
    }

    private File getExperimentFile(GoosciSharedMetadata.ExperimentOverview experimentOverview) {
        return getExperimentFile(experimentOverview.experimentId);
    }

    private File getExperimentFile(String localExperimentId) {
        return new File(mContext.getFilesDir() + "/" + localExperimentId,
                FileMetadataManager.EXPERIMENT_FILE);
    }

    private File getExperimentDirectory(String localExperimentId) {
        return new File(mContext.getFilesDir() + "/" + localExperimentId);
    }

    private File getAssetsDirectory(File experimentDirectory) {
        return new File(experimentDirectory, FileMetadataManager.ASSETS_DIRECTORY);
    }

    private boolean isDifferentFromActive(GoosciSharedMetadata.ExperimentOverview other) {
        if (mActiveExperiment == null) {
            return true;
        }
        return !TextUtils.equals(other.experimentId,
                mActiveExperiment.getExperimentOverview().experimentId);
    }

    @VisibleForTesting
    static boolean deleteRecursive(File file) {
        if (file.isDirectory()) {
            for (File child : file.listFiles()) {
                deleteRecursive(child);
            }
        }
        return file.delete();
    }
}
