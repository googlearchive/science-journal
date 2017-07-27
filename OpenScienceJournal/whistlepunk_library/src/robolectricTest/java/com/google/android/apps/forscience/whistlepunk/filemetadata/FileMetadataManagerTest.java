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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import com.google.android.apps.forscience.whistlepunk.BuildConfig;
import com.google.android.apps.forscience.whistlepunk.metadata.GoosciLabel;
import com.google.android.apps.forscience.whistlepunk.metadata.GoosciTextLabelValue;
import com.google.android.apps.forscience.whistlepunk.sensordb.IncrementableMonotonicClock;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.io.File;

/**
 * Tests for the FileMetadataManager class.
 */
@RunWith(RobolectricTestRunner.class)
@Config(constants = BuildConfig.class)
public class FileMetadataManagerTest {

    @Before
    public void setUp() {
        cleanUp();
    }

    @After
    public void tearDown() {
        cleanUp();
    }

    private void cleanUp() {
        File sharedMetadataFile = FileMetadataManager.getUserMetadataFile(
                getContext());
        sharedMetadataFile.delete();
    }

    @Test
    public void testSingleExperiment() {
        IncrementableMonotonicClock clock = new IncrementableMonotonicClock();
        FileMetadataManager fmm = new FileMetadataManager(getContext(), clock);
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
        experiment.addLabel(Label.newLabelWithValue(clock.getNow(), GoosciLabel.Label.TEXT,
                new GoosciTextLabelValue.TextLabelValue(), null));
        fmm.updateExperiment(experiment);

        Experiment saved = fmm.getLastUsedUnarchivedExperiment();
        assertEquals("Title", saved.getTitle());
        assertEquals(1, saved.getLabelCount());

        // Clean up
        fmm.deleteExperiment(experiment);
        assertNull(fmm.getExperimentById(experiment.getExperimentId()));
    }

    @Test
    public void testMultipleExperiments() {
        IncrementableMonotonicClock clock = new IncrementableMonotonicClock();
        FileMetadataManager fmm = new FileMetadataManager(getContext(), clock);
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

        // Doesn't re-use colors
        assertTrue(first.getExperimentOverview().colorIndex !=
                second.getExperimentOverview().colorIndex);
        assertTrue(second.getExperimentOverview().colorIndex !=
                third.getExperimentOverview().colorIndex);
        assertTrue(first.getExperimentOverview().colorIndex !=
                third.getExperimentOverview().colorIndex);

        // Clean up
        fmm.deleteExperiment(first);
        fmm.deleteExperiment(second);
        fmm.deleteExperiment(third);
    }

    @Test
    public void testRelativePathFunctions() {
        File file = new File(getContext().getFilesDir() +
                "/experiments/experiment182/assets/cats.png");
        assertEquals("assets/cats.png",
                FileMetadataManager.getRelativePathInExperiment("experiment182", file));

        // No match should return an empty string.
        assertEquals("", FileMetadataManager.getRelativePathInExperiment("experiment42", file));

        File result = FileMetadataManager.getExperimentFile(getContext(),
                "experiment182", "assets/cats.png");
        assertEquals(file.getAbsolutePath(), result.getAbsolutePath());

        assertEquals("experiments/experiment182/assets/cats.png",
                FileMetadataManager.getRelativePathInFilesDir("experiment182", "assets/cats.png"));
    }

    private Context getContext() {
        return RuntimeEnvironment.application.getApplicationContext();
    }
}
