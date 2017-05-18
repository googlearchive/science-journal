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

import com.google.android.apps.forscience.whistlepunk.sensordb.IncrementableMonotonicClock;

import org.junit.AfterClass;
import org.junit.BeforeClass;

import java.io.File;

/**
 * Tests for the FileMetadataManager class.
 */
public class FileMetadataManagerTest extends InstrumentationTestCase {

    public void setUp() {
        cleanUp();
    }

    public void tearDown() {
        cleanUp();
    }

    private void cleanUp() {
        File sharedMetadataFile = FileMetadataManager.getSharedMetadataFile(
                getInstrumentation().getContext());
        sharedMetadataFile.delete();
    }

    public void testSingleExperiment() {
        IncrementableMonotonicClock clock = new IncrementableMonotonicClock();
        FileMetadataManager fmm = new FileMetadataManager(getInstrumentation().getContext(), clock);
        Experiment experiment = fmm.newExperiment();
        assertEquals(experiment.getCreationTimeMs(), clock.getNow());
        assertEquals(fmm.getExperimentById(experiment.getExperimentId()).getLastUsedTime(),
                clock.getNow());

        clock.increment();
        fmm.setLastUsedExperiment(experiment);
        assertEquals(fmm.getExperimentById(experiment.getExperimentId()).getLastUsedTime(),
                clock.getNow());

        clock.increment();
        experiment.setTitle("Title");
        experiment.addLabel(Label.newLabelWithValue(clock.getNow(),
                TextLabelValue.fromText("text note")));
        fmm.updateExperiment(experiment);

        Experiment saved = fmm.getLastUsedUnarchivedExperiment();
        assertEquals("Title", saved.getTitle());
        assertEquals(1, saved.getLabelCount());

        // Clean up
        fmm.deleteExperiment(experiment);
        assertNull(fmm.getExperimentById(experiment.getExperimentId()));
    }

    public void testMultipleExperiments() {
        IncrementableMonotonicClock clock = new IncrementableMonotonicClock();
        FileMetadataManager fmm = new FileMetadataManager(getInstrumentation().getContext(), clock);
        Experiment first = fmm.newExperiment();
        clock.increment();
        Experiment second = fmm.newExperiment();
        clock.increment();
        Experiment third = fmm.newExperiment();
        clock.increment();

        assertEquals(third.getExperimentId(),
                fmm.getLastUsedUnarchivedExperiment().getExperimentId());
        assertEquals(3, fmm.getExperimentOverviews(false).size());

        third.setArchived(true);
        fmm.updateExperiment(third);
        assertEquals(second.getExperimentId(),
                fmm.getLastUsedUnarchivedExperiment().getExperimentId());
        assertEquals(2, fmm.getExperimentOverviews(false).size());

        // Clean up
        fmm.deleteExperiment(first);
        fmm.deleteExperiment(second);
        fmm.deleteExperiment(third);
    }
}
