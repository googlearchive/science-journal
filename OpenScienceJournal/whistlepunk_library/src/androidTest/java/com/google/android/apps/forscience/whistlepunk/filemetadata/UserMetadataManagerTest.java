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

import android.test.InstrumentationTestCase;

import com.google.android.apps.forscience.whistlepunk.metadata.GoosciUserMetadata;

import java.io.File;

/**
 * Tests for SharedMetadatamanager
 */
public class UserMetadataManagerTest extends InstrumentationTestCase {

    private UserMetadataManager.FailureListener getFailureFailsListener() {
        return new UserMetadataManager.FailureListener() {
            @Override
            public void onWriteFailed() {
                throw new RuntimeException("Expected success");
            }

            @Override
            public void onReadFailed() {
                throw new RuntimeException("Expected success");
            }
        };
    }

    public void setUp() {
        cleanUp();
    }

    public void tearDown() {
        cleanUp();
    }

    private void cleanUp() {
        File sharedMetadataFile = FileMetadataManager.getUserMetadataFile(
                getInstrumentation().getContext());
        sharedMetadataFile.delete();
    }


    public void testEmpty() {
        UserMetadataManager smm = new UserMetadataManager(getInstrumentation().getContext(),
                getFailureFailsListener());
        assertNull(smm.getExperimentOverview("doesNotExist"));
        assertEquals(0, smm.getExperimentOverviews(true).size());
    }

    public void testReadAndWriteSingle() {
        UserMetadataManager smm = new UserMetadataManager(getInstrumentation().getContext(),
                getFailureFailsListener());
        GoosciUserMetadata.ExperimentOverview overview =
                new GoosciUserMetadata.ExperimentOverview();
        overview.experimentId = "expId";
        overview.lastUsedTimeMs = 42;
        smm.addExperimentOverview(overview);
        assertEquals(smm.getExperimentOverview("expId").lastUsedTimeMs, 42);
        overview.lastUsedTimeMs = 84;
        smm.updateExperimentOverview(overview);
        assertEquals(smm.getExperimentOverview("expId").lastUsedTimeMs, 84);
    }

    public void testReadAndWriteMultiple() {
        UserMetadataManager smm = new UserMetadataManager(getInstrumentation().getContext(),
                getFailureFailsListener());
        GoosciUserMetadata.ExperimentOverview first =
                new GoosciUserMetadata.ExperimentOverview();
        first.experimentId = "exp1";
        first.lastUsedTimeMs = 1;
        smm.addExperimentOverview(first);
        GoosciUserMetadata.ExperimentOverview second =
                new GoosciUserMetadata.ExperimentOverview();
        second.experimentId = "exp2";
        second.lastUsedTimeMs = 2;
        smm.addExperimentOverview(second);
        GoosciUserMetadata.ExperimentOverview third =
                new GoosciUserMetadata.ExperimentOverview();
        third.experimentId = "exp3";
        third.lastUsedTimeMs = 3;
        smm.addExperimentOverview(third);

        // All are unarchived
        assertEquals(smm.getExperimentOverviews(false).size(), 3);

        // Archive one
        second.isArchived = true;
        smm.updateExperimentOverview(second);

        assertEquals(smm.getExperimentOverviews(false).size(), 2);

        // Check delete works properly
        smm.deleteExperimentOverview(second.experimentId);
        assertEquals(smm.getExperimentOverviews(true).size(), 2);

    }
}
