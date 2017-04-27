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
import com.google.protobuf.nano.MessageNano;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.BeforeClass;

import java.io.File;

/**
 * Tests for the ExperimentCache class.
 * Note: All experiments created should start with prefix "exp_" so that they can be cleaned up
 * automatically.
 */
public class ExperimentCacheTest extends InstrumentationTestCase {
    private ExperimentCache.FailureListener getFailureFailsListener() {
        return new ExperimentCache.FailureListener() {
            @Override
            public void onWriteFailed(Experiment experimentToWrite) {
                throw new RuntimeException("Expected success");
            }

            @Override
            public void onReadFailed(GoosciSharedMetadata.ExperimentOverview experimentOverview) {
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
        File rootDirectory = getInstrumentation().getContext().getFilesDir();
        for (File file : rootDirectory.listFiles()) {
            if (file.getName().startsWith("exp_")) {
                ExperimentCache.deleteRecursive(file);
            }
        }
    }

    public void testExperimentWriteRead() {
        Experiment experiment = Experiment.newExperiment(10, "exp_localId");
        ExperimentCache cache = new ExperimentCache(getInstrumentation().getContext(),
                getFailureFailsListener(), 0);
        cache.createNewExperiment(experiment);
        cache.writeActiveExperimentFile();

        // Was it set correctly in the ExperimentCache?
        assertTrue(MessageNano.messageNanoEquals(
                cache.getActiveExperimentForTests().getExperimentProto(),
                experiment.getExperimentProto()));

        // Force a load, make sure that's equal too.
        cache.loadActiveExperimentFromFile(experiment.getExperimentOverview());
        assertTrue(MessageNano.messageNanoEquals(
                cache.getActiveExperimentForTests().getExperimentProto(),
                experiment.getExperimentProto()));

        // Clean up.
        cache.deleteExperiment("exp_localId");
        assertNull(cache.getActiveExperimentForTests());
    }

    public void testExperimentWithChanges() {
        Experiment experiment = Experiment.newExperiment(10, "exp_localId");
        ExperimentCache cache = new ExperimentCache(getInstrumentation().getContext(),
                getFailureFailsListener(), 0);
        cache.createNewExperiment(experiment);
        assertTrue(cache.needsWrite());

        cache.writeActiveExperimentFile();
        assertFalse(cache.needsWrite());

        experiment.setTitle("Title");
        cache.updateExperiment(experiment);
        assertTrue(cache.needsWrite());
        cache.writeActiveExperimentFile();

        // Force a load, make sure that's got the new title.
        cache.loadActiveExperimentFromFile(experiment.getExperimentOverview());
        assertEquals("Title", cache.getActiveExperimentForTests().getTitle());

        // Clean up.
        cache.deleteExperiment("exp_localId");
        assertNull(cache.getActiveExperimentForTests());
    }

    public void testChangingExperimentWritesOldOne() {
        ExperimentCache cache = new ExperimentCache(getInstrumentation().getContext(),
                getFailureFailsListener(), 0);

        Experiment experiment = Experiment.newExperiment(10, "exp_localId");
        cache.createNewExperiment(experiment);
        assertEquals(cache.getActiveExperimentForTests().getCreationTimeMs(), 10);
        experiment.setTitle("Title");
        cache.updateExperiment(experiment);

        Experiment second = Experiment.newExperiment(20, "exp_secondId");
        cache.createNewExperiment(second);
        assertEquals(cache.getActiveExperimentForTests().getCreationTimeMs(), 20);

        cache.getExperiment(experiment.getExperimentOverview());
        assertEquals(cache.getActiveExperimentForTests().getCreationTimeMs(), 10);
        assertEquals(cache.getActiveExperimentForTests().getTitle(), "Title");
    }
}