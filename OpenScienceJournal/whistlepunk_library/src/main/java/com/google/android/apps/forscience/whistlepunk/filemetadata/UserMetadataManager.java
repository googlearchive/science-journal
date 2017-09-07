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

import com.google.android.apps.forscience.whistlepunk.WhistlePunkApplication;
import com.google.android.apps.forscience.whistlepunk.analytics.UsageTracker;
import com.google.android.apps.forscience.whistlepunk.data.GoosciDeviceSpec;
import com.google.android.apps.forscience.whistlepunk.metadata.GoosciUserMetadata;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Lists;
import com.google.protobuf.nano.MessageNano;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Reads and writes ExperimentOverview lists
 */
// TODO: Should this be a cache too?
public class UserMetadataManager {
    private static final String TAG = "UserMetadataManager";

    // The current version number we expect from UserMetadata.
    // See upgradeUserMetadataVersionIfNeeded for the meaning of version numbers.
    private static final int VERSION = 1;

    // The current minor version number we expect from UserMetadata.
    // See upgradeUserMetadataVersionIfNeeded for the meaning of version numbers.
    private static final int MINOR_VERSION = 1;
    private static final long WRITE_DELAY_MS = 500;

    private final Handler mHandler;
    private final Runnable mWriteRunnable;
    private final long mWriteDelayMs;
    private boolean mNeedsWrite = false;
    private GoosciUserMetadata.UserMetadata mUserMetadata;
    private UsageTracker mUsageTracker;

    interface FailureListener {
        // TODO: What's helpful to pass back here? Maybe info about the type of error?
        void onWriteFailed();
        void onReadFailed();

        // A newer version of the User Metadata proto was found on the device. We cannot parse it
        // with this version of the app.
        void onNewerVersionDetected();
    }

    private FailureListener mFailureListener;
    private ProtoFileHelper<GoosciUserMetadata.UserMetadata> mOverviewProtoFileHelper;
    private File mUserMetadataFile;

    public UserMetadataManager(Context context, FailureListener failureListener) {
        mFailureListener = failureListener;
        mOverviewProtoFileHelper = new ProtoFileHelper<>();
        mUserMetadataFile = FileMetadataManager.getUserMetadataFile(context);
        mHandler = new Handler();
        mWriteRunnable = new Runnable() {
            @Override
            public void run() {
                if (mNeedsWrite) {
                    writeUserMetadata(mUserMetadata);
                }
            }
        };
        mWriteDelayMs = WRITE_DELAY_MS;
        mUsageTracker = WhistlePunkApplication.getUsageTracker(context);
    }

    private void startWriteTimer() {
        if (mNeedsWrite) {
            // The timer is already running.
            return;
        }
        mNeedsWrite = true;
        mHandler.postDelayed(mWriteRunnable, mWriteDelayMs);
    }

    public void saveImmediately() {
        if (mNeedsWrite) {
            writeUserMetadata(mUserMetadata);
            cancelWriteTimer();
        }
    }

    private void cancelWriteTimer() {
        mHandler.removeCallbacks(mWriteRunnable);
    }

    /**
     * Gets an experiment overview by experiment ID from the Shared Metadata.
     */
    GoosciUserMetadata.ExperimentOverview getExperimentOverview(String experimentId) {
        GoosciUserMetadata.UserMetadata userMetadata = getUserMetadata();
        if (userMetadata == null) {
            return null;
        }
        for (GoosciUserMetadata.ExperimentOverview overview : userMetadata.experiments) {
            if (TextUtils.equals(overview.experimentId, experimentId)) {
                return overview;
            }
        }
        return null;
    }

    /**
     * Adds a new experiment overview to the Shared Metadata.
     */
    void addExperimentOverview(GoosciUserMetadata.ExperimentOverview overviewToAdd) {
        GoosciUserMetadata.UserMetadata userMetadata = getUserMetadata();
        if (userMetadata == null) {
            return;
        }
        GoosciUserMetadata.ExperimentOverview[] newList =
                Arrays.copyOf(userMetadata.experiments, userMetadata.experiments.length + 1);
        newList[userMetadata.experiments.length] = overviewToAdd;
        userMetadata.experiments = newList;
        startWriteTimer();
    }

    /**
     * Updates an experiment overview in the Shared Metadata.
     */
    void updateExperimentOverview(GoosciUserMetadata.ExperimentOverview overviewToUpdate) {
        GoosciUserMetadata.UserMetadata userMetadata = getUserMetadata();
        if (userMetadata == null) {
            return;
        }
        boolean updated = false;
        for (int i = 0; i < userMetadata.experiments.length; i++) {
            if (TextUtils.equals(userMetadata.experiments[i].experimentId,
                    overviewToUpdate.experimentId)) {
                userMetadata.experiments[i] = overviewToUpdate;
                updated = true;
                break;
            }
        }
        if (updated) {
            startWriteTimer();
        }
    }

    /**
     * Deletes an experiment overview from disk.
     * @param experimentIdToDelete the ID of the overview to be deleted.
     */
    void deleteExperimentOverview(String experimentIdToDelete) {
        GoosciUserMetadata.UserMetadata userMetadata = getUserMetadata();
        if (userMetadata == null || userMetadata.experiments.length == 0) {
            return;
        }
        boolean updated = false;
        GoosciUserMetadata.ExperimentOverview[] newList =
                new GoosciUserMetadata.ExperimentOverview[userMetadata.experiments.length - 1];
        int newIndex = 0;
        for (int oldIndex = 0; oldIndex < userMetadata.experiments.length; oldIndex++) {
            // If it's not the one we want to delete, add it to the new list.
            if (!TextUtils.equals(experimentIdToDelete,
                    userMetadata.experiments[oldIndex].experimentId)) {
                newList[newIndex] = userMetadata.experiments[oldIndex];
                newIndex++;
            } else {
                updated = true;
            }
        }
        if (updated) {
            userMetadata.experiments = newList;
            startWriteTimer();
        }
    }

    /**
     * Gets all the experiment overviews
     * @param includeArchived Whether to include the archived experiments.
     */
    List<GoosciUserMetadata.ExperimentOverview> getExperimentOverviews(boolean includeArchived) {
        GoosciUserMetadata.UserMetadata userMetadata = getUserMetadata();
        if (userMetadata == null) {
            return null;
        }
        if (includeArchived) {
            return Arrays.asList(userMetadata.experiments);
        } else {
            List<GoosciUserMetadata.ExperimentOverview> result = new ArrayList<>();
            for (GoosciUserMetadata.ExperimentOverview overview : userMetadata.experiments) {
                if (!overview.isArchived) {
                    result.add(overview);
                }
            }
            return result;
        }
    }

    /**
     * Adds a device to the user's list of devices if it is not yet added.
     */
    public void addMyDevice(GoosciDeviceSpec.DeviceSpec device) {
        GoosciUserMetadata.UserMetadata userMetadata = getUserMetadata();

        if (userMetadata == null) {
            return;
        }

        GoosciDeviceSpec.DeviceSpec[] myDevices = userMetadata.myDevices;
        for (GoosciDeviceSpec.DeviceSpec myDevice : myDevices) {
            if (MessageNano.messageNanoEquals(myDevice, device)) {
                return;
            }
        }

        GoosciDeviceSpec.DeviceSpec[] newSpecs = Arrays.copyOf(myDevices, myDevices.length + 1);
        newSpecs[newSpecs.length - 1] = device;
        userMetadata.myDevices = newSpecs;

        // TODO: capture this pattern (read, null check, write) in a helper method?
        startWriteTimer();
    }

    public void removeMyDevice(GoosciDeviceSpec.DeviceSpec device) {
        GoosciUserMetadata.UserMetadata userMetadata = getUserMetadata();

        if (userMetadata == null) {
            return;
        }

        GoosciDeviceSpec.DeviceSpec[] myDevices = userMetadata.myDevices;
        for (int i = 0; i < myDevices.length; i++) {
            if (MessageNano.messageNanoEquals(myDevices[i], device)) {
                removeMyDeviceAtIndex(userMetadata, i);
                return;
            }
        }
    }

    private void removeMyDeviceAtIndex(GoosciUserMetadata.UserMetadata userMetadata, int i) {
        GoosciDeviceSpec.DeviceSpec[] myDevices = userMetadata.myDevices;
        GoosciDeviceSpec.DeviceSpec[] newSpecs = Arrays.copyOf(myDevices, myDevices.length - 1);
        System.arraycopy(myDevices, 0, newSpecs, 0, i);
        System.arraycopy(myDevices, i + 1, newSpecs, i, myDevices.length - 1);
        userMetadata.myDevices = newSpecs;

        // TODO: capture this pattern (read, null check, write) in a helper method?
        startWriteTimer();
    }

    public List<GoosciDeviceSpec.DeviceSpec> getMyDevices() {
        GoosciUserMetadata.UserMetadata userMetadata = getUserMetadata();
        if (userMetadata == null) {
            return Lists.newArrayList();
        }

        return Lists.newArrayList(userMetadata.myDevices);
    }

    /**
     * Reads the shared metadata from the file, and throws an error to the failure listener if
     * needed.
     */
    private GoosciUserMetadata.UserMetadata getUserMetadata() {
        if (mUserMetadata != null) {
            return mUserMetadata;
        }
        // Otherwise, read it from the file.
        boolean firstTime = createUserMetadataFileIfNeeded();
        // Create a new user metadata proto to populate
        if (firstTime) {
            // If the file has nothing in it, these version numbers will be the basis of our initial
            // UserMetadata file.
            mUserMetadata = new GoosciUserMetadata.UserMetadata();
            mUserMetadata.version = VERSION;
            mUserMetadata.minorVersion = MINOR_VERSION;
        } else {
            mUserMetadata = mOverviewProtoFileHelper.readFromFile(mUserMetadataFile,
                    GoosciUserMetadata.UserMetadata::parseFrom, mUsageTracker);
            if (mUserMetadata == null) {
                mFailureListener.onReadFailed();
                return null;
            }
            upgradeUserMetadataVersionIfNeeded(mUserMetadata);
        }
        return mUserMetadata;
    }

    private void upgradeUserMetadataVersionIfNeeded(GoosciUserMetadata.UserMetadata userMetadata) {
        upgradeUserMetadataVersionIfNeeded(userMetadata, VERSION, MINOR_VERSION);
    }

    /**
     * Upgrades and saves user metadata to match the version given.
     * @param userMetadata The metadata to upgrade if necessary
     * @param newMajorVersion The major version to upgrade to, available for testing
     * @param newMinorVersion The minor version to upgrade to, available for testing
     */
    @VisibleForTesting
    void upgradeUserMetadataVersionIfNeeded(GoosciUserMetadata.UserMetadata userMetadata,
            int newMajorVersion, int newMinorVersion) {
        if (userMetadata.version == newMajorVersion &&
                userMetadata.minorVersion == newMinorVersion) {
            // No upgrade needed, this is running the same version as us.
            return;
        }
        if (userMetadata.version > newMajorVersion) {
            // It is too new for us to read -- the major version is later than ours.
            mFailureListener.onNewerVersionDetected();
            return;
        }
        // Try to upgrade the major version
        if (userMetadata.version == 0) {
            // Do any work to increment the minor version.

            if (userMetadata.version < newMajorVersion) {
                // Upgrade from 0 to 1, for example: Increment the major version and reset the minor
                // version.
                // Other work could be done here first like populating protos.
                revMajorVersionTo(userMetadata, 1);
            }
        }
        if (userMetadata.version == 1) {
            // Minor version upgrades are done within the if statement
            // for their major version counterparts.
            if (userMetadata.minorVersion == 0 && userMetadata.minorVersion < newMinorVersion) {
                // Upgrade minor version from 0 to 1, within in major version 1, for example.
                userMetadata.minorVersion = 1;
            }
            // More minor version upgrades for major version 1 could be done here.

            // When we are ready for version 2.0, we would do work in the following if statement
            // and then call incrementMajorVersion.
            if (userMetadata.version < newMajorVersion) {
                // Do any work to upgrade version, then increment the version when we are
                // ready to go to 2.0 or above.
                revMajorVersionTo(userMetadata, 2);

            }
        }
        // We've made changes we need to save.
        writeUserMetadata(userMetadata);
    }

    private void revMajorVersionTo(GoosciUserMetadata.UserMetadata userMetadata, int majorVersion) {
        userMetadata.version = majorVersion;
        userMetadata.minorVersion = 0;
    }

    /**
     * Writes the shared metadata object to the file.
     */
    private void writeUserMetadata(GoosciUserMetadata.UserMetadata userMetadata) {
        if (userMetadata.version > VERSION ||
                userMetadata.version == VERSION && userMetadata.minorVersion > MINOR_VERSION) {
            // If the major version is too new, or the minor version is too new, we can't save this.
            mFailureListener.onNewerVersionDetected(); // TODO: Or should this throw onWriteFailed?
        }
        createUserMetadataFileIfNeeded();
        if (!mOverviewProtoFileHelper.writeToFile(mUserMetadataFile, userMetadata, mUsageTracker)) {
            mFailureListener.onWriteFailed();
        } else {
            mNeedsWrite = false;
        }
    }

    /**
     * Lazy creation of the shared metadata file. This is probably only done the first time the
     * app is opened, so we could consider calling this only once to reduce load, but it doesn't
     * seem super expensive.
     * Returns true if a new file was created.
     */
    private boolean createUserMetadataFileIfNeeded() {
        if (!mUserMetadataFile.exists()) {
            // If the files aren't there yet, create them.
            try {
                boolean created = mUserMetadataFile.createNewFile();
                if (!created) {
                    mFailureListener.onWriteFailed();
                    return false;
                }
                return true;
            } catch (IOException e) {
                mFailureListener.onWriteFailed();
            }
        }
        return false;
    }
}
