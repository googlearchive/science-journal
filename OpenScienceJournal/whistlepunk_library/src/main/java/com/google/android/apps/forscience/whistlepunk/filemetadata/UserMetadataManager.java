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
import android.text.TextUtils;

import com.google.android.apps.forscience.whistlepunk.metadata.GoosciUserMetadata;

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

    interface FailureListener {
        // TODO: What's helpful to pass back here? Maybe info about the type of error?
        void onWriteFailed();
        void onReadFailed();
    }

    private FailureListener mFailureListener;
    private ProtoFileHelper<GoosciUserMetadata.UserMetadata> mOverviewProtoFileHelper;
    private File mUserMetadataFile;

    public UserMetadataManager(Context context, FailureListener failureListener) {
        mFailureListener = failureListener;
        mOverviewProtoFileHelper = new ProtoFileHelper<>();
        mUserMetadataFile = FileMetadataManager.getUserMetadataFile(context);
    }

    /**
     * Gets an experiment overview by experiment ID from the Shared Metadata.
     */
    GoosciUserMetadata.ExperimentOverview getExperimentOverview(String experimentId) {
        GoosciUserMetadata.UserMetadata userMetadata = readUserMetadata();
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
        GoosciUserMetadata.UserMetadata userMetadata = readUserMetadata();
        if (userMetadata == null) {
            return;
        }
        GoosciUserMetadata.ExperimentOverview[] newList =
                Arrays.copyOf(userMetadata.experiments, userMetadata.experiments.length + 1);
        newList[userMetadata.experiments.length] = overviewToAdd;
        userMetadata.experiments = newList;
        writeUserMetadata(userMetadata);
    }

    /**
     * Updates an experiment overview in the Shared Metadata.
     */
    void updateExperimentOverview(GoosciUserMetadata.ExperimentOverview overviewToUpdate) {
        GoosciUserMetadata.UserMetadata userMetadata = readUserMetadata();
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
            writeUserMetadata(userMetadata);
        }
    }

    /**
     * Deletes an experiment overview from disk.
     * @param experimentIdToDelete the ID of the overview to be deleted.
     */
    void deleteExperimentOverview(String experimentIdToDelete) {
        GoosciUserMetadata.UserMetadata userMetadata = readUserMetadata();
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
            writeUserMetadata(userMetadata);
        }
    }

    /**
     * Gets all the experiment overviews
     * @param includeArchived Whether to include the archived experiments.
     */
    List<GoosciUserMetadata.ExperimentOverview> getExperimentOverviews(
            boolean includeArchived) {
        GoosciUserMetadata.UserMetadata userMetadata = readUserMetadata();
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
     * Reads the shared metadata from the file, and throws an error to the failure listener if
     * needed.
     */
    private GoosciUserMetadata.UserMetadata readUserMetadata() {
        createUserMetadataFileIfNeeded();
        GoosciUserMetadata.UserMetadata userMetadata = new GoosciUserMetadata.UserMetadata();
        boolean success = mOverviewProtoFileHelper.readFromFile(mUserMetadataFile, userMetadata);
        if (!success) {
            mFailureListener.onReadFailed();
            return null;
        }
        return userMetadata;
    }

    /**
     * Writes the shared metadata object to the file.
     */
    private void writeUserMetadata(GoosciUserMetadata.UserMetadata userMetadata) {
        createUserMetadataFileIfNeeded();
        if (!mOverviewProtoFileHelper.writeToFile(mUserMetadataFile, userMetadata)) {
            mFailureListener.onWriteFailed();
        };
    }

    /**
     * Lazy creation of the shared metadata file. This is probably only done the first time the
     * app is opened, so we could consider calling this only once to reduce load, but it doesn't
     * seem super expensive.
     */
    private void createUserMetadataFileIfNeeded() {
        if (!mUserMetadataFile.exists()) {
            // If the files aren't there yet, create them.
            try {
                boolean created = mUserMetadataFile.createNewFile();
                if (!created) {
                    mFailureListener.onWriteFailed();
                }
            } catch (IOException e) {
                mFailureListener.onWriteFailed();
            }
        }
    }
}
