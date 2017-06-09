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
import com.google.android.apps.forscience.whistlepunk.metadata.GoosciUserMetadata;
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

    // The current version number we expect from experiments.
    // See upgradeExperimentVersionIfNeeded for the meaning of version numbers.
    protected static final int VERSION = 1;

    // The current minor version number we expect from experiments.
    // See upgradeExperimentVersionIfNeeded for the meaning of version numbers.
    protected static final int MINOR_VERSION = 1;

    // Write the experiment file no more than once per every WRITE_DELAY_MS.
    private static final long WRITE_DELAY_MS = 1000;

    public interface FailureListener {
        // TODO: What's helpful to pass back here? Maybe info about the type of error?
        // When writing an experiment failed
        void onWriteFailed(Experiment experimentToWrite);

        // When reading an experiment failed
        void onReadFailed(GoosciUserMetadata.ExperimentOverview localExperimentOverview);

        // When a newer version is found and we cannot parse it
        void onNewerVersionDetected(GoosciUserMetadata.ExperimentOverview experimentOverview);
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
            GoosciUserMetadata.ExperimentOverview experimentOverview) {
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
    Experiment getExperiment(GoosciUserMetadata.ExperimentOverview localExperimentOverview) {
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
    private void immediateWriteIfActiveChanging(GoosciUserMetadata.ExperimentOverview
            localExperimentOverview) {
        if (mActiveExperiment != null && mActiveExperimentNeedsWrite &&
                isDifferentFromActive(localExperimentOverview)) {
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
     * Writes the active experiment to a file immediately, if needed.
     */
    void saveImmediately() {
        if (mActiveExperimentNeedsWrite) {
            cancelWriteTimer();
            writeActiveExperimentFile();
        }
    }

    /**
     * Writes the active experiment to a file.
     */
    @VisibleForTesting
    void writeActiveExperimentFile() {
        if (mActiveExperiment.getVersion() > VERSION ||
                mActiveExperiment.getVersion() == VERSION &&
                        mActiveExperiment.getMinorVersion() > MINOR_VERSION) {
            // If the major version is too new, or the minor version is too new, we can't save this.
            // TODO: Or should this throw onWriteFailed?
            mFailureListener.onNewerVersionDetected(mActiveExperiment.getExperimentOverview());
        }
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
    void loadActiveExperimentFromFile(GoosciUserMetadata.ExperimentOverview experimentOverview) {
        File experimentFile = getExperimentFile(experimentOverview);
        GoosciExperiment.Experiment proto = mExperimentProtoFileHelper.readFromFile(experimentFile,
                GoosciExperiment.Experiment::parseFrom);
        if (proto != null) {
            upgradeExperimentVersionIfNeeded(proto, experimentOverview);
            mActiveExperiment = Experiment.fromExperiment(proto, experimentOverview);
        } else {
            // Or maybe pass a FailureListener into the load instead of failing here.
            mFailureListener.onReadFailed(experimentOverview);
            mActiveExperiment = null;
        }
    }

    private void upgradeExperimentVersionIfNeeded(GoosciExperiment.Experiment proto,
            GoosciUserMetadata.ExperimentOverview experimentOverview) {
        upgradeExperimentVersionIfNeeded(proto, experimentOverview, VERSION, MINOR_VERSION);
    }

    /**
     * Upgrades an experiment proto if necessary. Requests a save if the upgrade happened.
     * @param proto The experiment to upgrade if necessary
     * @param newMajorVersion The major version to upgrade to, available for testing
     * @param newMinorVersion The minor version to upgrade to, available for testing
     */
    @VisibleForTesting
    void upgradeExperimentVersionIfNeeded(GoosciExperiment.Experiment proto,
            GoosciUserMetadata.ExperimentOverview experimentOverview, int newMajorVersion,
            int newMinorVersion) {
        if (proto.version == newMajorVersion && proto.minorVersion == newMinorVersion) {
            // No upgrade needed, this is running the same version as us.
            return;
        }
        if (proto.version > newMajorVersion) {
            // It is too new for us to read -- the major version is later than ours.
            mFailureListener.onNewerVersionDetected(experimentOverview);
            return;
        }
        // Try to upgrade the major version
        if (proto.version == 0) {
            // Do any work to increment the minor version.

            if (proto.version < newMajorVersion) {
                // Upgrade from 0 to 1, for example: Increment the major version and reset the minor
                // version.
                // Other work could be done here first like populating protos.
                revMajorVersionTo(proto, 1);
            }
        }
        if (proto.version == 1) {
            // Minor version upgrades are done within the if statement
            // for their major version counterparts.
            if (proto.minorVersion == 0 && proto.minorVersion < newMinorVersion) {
                // Upgrade minor version from 0 to 1, within in major version 1, for example.
                proto.minorVersion = 1;
            }
            // More minor version upgrades for major version 1 could be done here.

            // When we are ready for version 2.0, we would do work in the following if statement
            // and then call incrementMajorVersion.
            if (proto.version < newMajorVersion) {
                // Do any work to upgrade version, then increment the version when we are
                // ready to go to 2.0 or above.
                revMajorVersionTo(proto, 2);

            }
        }
        // We've made changes we need to save.
        startWriteTimer();
    }

    private void revMajorVersionTo(GoosciExperiment.Experiment proto, int majorVersion) {
        proto.version = majorVersion;
        proto.minorVersion = 0;
    }

    private File getExperimentFile(GoosciUserMetadata.ExperimentOverview experimentOverview) {
        return getExperimentFile(experimentOverview.experimentId);
    }

    private File getExperimentFile(String localExperimentId) {
        return new File(mContext.getFilesDir() + "/experiments/" + localExperimentId,
                FileMetadataManager.EXPERIMENT_FILE);
    }

    private File getExperimentDirectory(String localExperimentId) {
        return new File(mContext.getFilesDir() + "/experiments/" + localExperimentId);
    }

    private File getAssetsDirectory(File experimentDirectory) {
        return new File(experimentDirectory, FileMetadataManager.ASSETS_DIRECTORY);
    }

    private boolean isDifferentFromActive(GoosciUserMetadata.ExperimentOverview other) {
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
