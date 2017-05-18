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

import com.google.android.apps.forscience.whistlepunk.metadata.GoosciSharedMetadata;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Reads and writes ExperimentOverview lists
 */
// TODO: Should this be a cache too?
public class SharedMetadataManager {
    private static final String TAG = "SharedMetadataManager";

    interface FailureListener {
        // TODO: What's helpful to pass back here? Maybe info about the type of error?
        void onWriteFailed();
        void onReadFailed();
    }

    private FailureListener mFailureListener;
    private ProtoFileHelper<GoosciSharedMetadata.SharedMetadata> mOverviewProtoFileHelper;
    private File mSharedMetadataFile;

    public SharedMetadataManager(Context context, FailureListener failureListener) {
        mFailureListener = failureListener;
        mOverviewProtoFileHelper = new ProtoFileHelper<>();
        mSharedMetadataFile = FileMetadataManager.getSharedMetadataFile(context);
    }

    /**
     * Gets an experiment overview by experiment ID from the Shared Metadata.
     */
    GoosciSharedMetadata.ExperimentOverview getExperimentOverview(String experimentId) {
        GoosciSharedMetadata.SharedMetadata sharedMetadata = readSharedMetadata();
        if (sharedMetadata == null) {
            return null;
        }
        for (GoosciSharedMetadata.ExperimentOverview overview : sharedMetadata.experiments) {
            if (TextUtils.equals(overview.experimentId, experimentId)) {
                return overview;
            }
        }
        return null;
    }

    /**
     * Adds a new experiment overview to the Shared Metadata.
     */
    void addExperimentOverview(GoosciSharedMetadata.ExperimentOverview overviewToAdd) {
        GoosciSharedMetadata.SharedMetadata sharedMetadata = readSharedMetadata();
        if (sharedMetadata == null) {
            return;
        }
        GoosciSharedMetadata.ExperimentOverview[] newList =
                Arrays.copyOf(sharedMetadata.experiments, sharedMetadata.experiments.length + 1);
        newList[sharedMetadata.experiments.length] = overviewToAdd;
        sharedMetadata.experiments = newList;
        writeSharedMetadata(sharedMetadata);
    }

    /**
     * Updates an experiment overview in the Shared Metadata.
     */
    void updateExperimentOverview(GoosciSharedMetadata.ExperimentOverview overviewToUpdate) {
        GoosciSharedMetadata.SharedMetadata sharedMetadata = readSharedMetadata();
        if (sharedMetadata == null) {
            return;
        }
        boolean updated = false;
        for (int i = 0; i < sharedMetadata.experiments.length; i++) {
            if (TextUtils.equals(sharedMetadata.experiments[i].experimentId,
                    overviewToUpdate.experimentId)) {
                sharedMetadata.experiments[i] = overviewToUpdate;
                updated = true;
                break;
            }
        }
        if (updated) {
            writeSharedMetadata(sharedMetadata);
        }
    }

    /**
     * Deletes an experiment overview from disk.
     * @param experimentIdToDelete the ID of the overview to be deleted.
     */
    void deleteExperimentOverview(String experimentIdToDelete) {
        GoosciSharedMetadata.SharedMetadata sharedMetadata = readSharedMetadata();
        if (sharedMetadata == null || sharedMetadata.experiments.length == 0) {
            return;
        }
        boolean updated = false;
        GoosciSharedMetadata.ExperimentOverview[] newList =
                new GoosciSharedMetadata.ExperimentOverview[sharedMetadata.experiments.length - 1];
        int newIndex = 0;
        for (int oldIndex = 0; oldIndex < sharedMetadata.experiments.length; oldIndex++) {
            // If it's not the one we want to delete, add it to the new list.
            if (!TextUtils.equals(experimentIdToDelete,
                    sharedMetadata.experiments[oldIndex].experimentId)) {
                newList[newIndex] = sharedMetadata.experiments[oldIndex];
                newIndex++;
            } else {
                updated = true;
            }
        }
        if (updated) {
            sharedMetadata.experiments = newList;
            writeSharedMetadata(sharedMetadata);
        }
    }

    /**
     * Gets all the experiment overviews
     * @param includeArchived Whether to include the archived experiments.
     */
    List<GoosciSharedMetadata.ExperimentOverview> getExperimentOverviews(
            boolean includeArchived) {
        GoosciSharedMetadata.SharedMetadata sharedMetadata = readSharedMetadata();
        if (sharedMetadata == null) {
            return null;
        }
        if (includeArchived) {
            return Arrays.asList(sharedMetadata.experiments);
        } else {
            List<GoosciSharedMetadata.ExperimentOverview> result = new ArrayList<>();
            for (GoosciSharedMetadata.ExperimentOverview overview : sharedMetadata.experiments) {
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
    private GoosciSharedMetadata.SharedMetadata readSharedMetadata() {
        createSharedMetadataFileIfNeeded();
        GoosciSharedMetadata.SharedMetadata sharedMetadata =
                new GoosciSharedMetadata.SharedMetadata();
        boolean success = mOverviewProtoFileHelper.readFromFile(mSharedMetadataFile, sharedMetadata);
        if (!success) {
            mFailureListener.onReadFailed();
            return null;
        }
        return sharedMetadata;
    }

    /**
     * Writes the shared metadata object to the file.
     */
    private void writeSharedMetadata(GoosciSharedMetadata.SharedMetadata sharedMetadata) {
        createSharedMetadataFileIfNeeded();
        if (!mOverviewProtoFileHelper.writeToFile(mSharedMetadataFile, sharedMetadata)) {
            mFailureListener.onWriteFailed();
        };
    }

    /**
     * Lazy creation of the shared metadata file. This is probably only done the first time the
     * app is opened, so we could consider calling this only once to reduce load, but it doesn't
     * seem super expensive.
     */
    private void createSharedMetadataFileIfNeeded() {
        if (!mSharedMetadataFile.exists()) {
            // If the files aren't there yet, create them.
            try {
                boolean created = mSharedMetadataFile.createNewFile();
                if (!created) {
                    mFailureListener.onWriteFailed();
                }
            } catch (IOException e) {
                mFailureListener.onWriteFailed();
            }
        }
    }
}
