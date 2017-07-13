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
package com.google.android.apps.forscience.whistlepunk;

import com.google.android.apps.forscience.whistlepunk.filemetadata.Experiment;
import com.google.android.apps.forscience.whistlepunk.sensordb.InMemorySensorDatabase;
import com.google.android.apps.forscience.whistlepunk.sensordb.MemoryMetadataManager;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;

public class PanesActivityTest {
    @Test
    public void selectedExperimentGiven() {
        MemoryMetadataManager mmm = new MemoryMetadataManager();
        DataController dc =
                new InMemorySensorDatabase().makeSimpleController(mmm);
        Experiment expected = mmm.newExperiment();
        PanesActivity.whenSelectedExperiment(expected.getExperimentId(), dc)
                     .test()
                     .assertValue(
                             actual -> actual.getExperimentId().equals(expected.getExperimentId()));
    }

    @Test
    public void selectedExperimentCreate() {
        MemoryMetadataManager mmm = new MemoryMetadataManager();
        DataController dc =
                new InMemorySensorDatabase().makeSimpleController(mmm);
        List<Experiment> exps = PanesActivity.whenSelectedExperiment(null, dc).test().values();
        assertEquals(1, exps.size());
        Experiment returned = exps.get(0);
        Experiment added = mmm.getExperimentById(returned.getExperimentId());
        assertEquals(returned.getExperimentId(), added.getExperimentId());
    }
}