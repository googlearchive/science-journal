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

import com.google.android.apps.forscience.whistlepunk.metadata.GoosciSharedMetadata;
import com.google.common.util.concurrent.MoreExecutors;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.BeforeClass;

import java.io.File;

/**
 * Tests for SharedMetadatamanager
 */
public class SharedMetadataManagerTest extends InstrumentationTestCase {

    private SharedMetadataManager.FailureListener getFailureFailsListener() {
        return new SharedMetadataManager.FailureListener() {
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
        File sharedMetadataFile = new File(getInstrumentation().getContext().getFilesDir(),
                FileMetadataManager.SHARED_METADATA_FILE);
        sharedMetadataFile.delete();
    }


    public void testEmpty() {
        SharedMetadataManager smm = new SharedMetadataManager(getInstrumentation().getContext(),
                getFailureFailsListener());
        assertNull(smm.getExperimentOverview("doesNotExist"));
        assertEquals(0, smm.getExperimentOverviews(true).size());
    }

    public void testReadAndWriteSingle() {
        SharedMetadataManager smm = new SharedMetadataManager(getInstrumentation().getContext(),
                getFailureFailsListener());
        GoosciSharedMetadata.ExperimentOverview overview =
                new GoosciSharedMetadata.ExperimentOverview();
        overview.experimentId = "expId";
        overview.lastUsedTimeMs = 42;
        smm.addExperimentOverview(overview);
        assertEquals(smm.getExperimentOverview("expId").lastUsedTimeMs, 42);
        overview.lastUsedTimeMs = 84;
        smm.updateExperimentOverview(overview);
        assertEquals(smm.getExperimentOverview("expId").lastUsedTimeMs, 84);
    }

    public void testReadAndWriteMultiple() {
        SharedMetadataManager smm = new SharedMetadataManager(getInstrumentation().getContext(),
                getFailureFailsListener());
        GoosciSharedMetadata.ExperimentOverview first =
                new GoosciSharedMetadata.ExperimentOverview();
        first.experimentId = "exp1";
        first.lastUsedTimeMs = 1;
        smm.addExperimentOverview(first);
        GoosciSharedMetadata.ExperimentOverview second =
                new GoosciSharedMetadata.ExperimentOverview();
        second.experimentId = "exp2";
        second.lastUsedTimeMs = 2;
        smm.addExperimentOverview(second);
        GoosciSharedMetadata.ExperimentOverview third =
                new GoosciSharedMetadata.ExperimentOverview();
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
        smm.deleteExperimentOverview(second);
        assertEquals(smm.getExperimentOverviews(true).size(), 2);

    }
}
