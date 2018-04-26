/*
 *  Copyright 2016 Google Inc. All Rights Reserved.
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

package com.google.android.apps.forscience.whistlepunk.sensordb;

import static org.junit.Assert.assertEquals;

import com.google.android.apps.forscience.whistlepunk.filemetadata.Experiment;
import com.google.common.collect.Lists;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

@RunWith(RobolectricTestRunner.class)
public class MemoryMetadataManagerTest {
  @Test
  public void testExperimentOrdering() {
    MemoryMetadataManager mmm = new MemoryMetadataManager();
    Experiment e1 = mmm.newExperiment(1, "e1");
    Experiment e2 = mmm.newExperiment(2, "e2");
    Experiment e3 = mmm.newExperiment(3, "e3");
    assertEquals(
        Lists.newArrayList(
            e3.getExperimentOverview(), e2.getExperimentOverview(), e1.getExperimentOverview()),
        mmm.getExperimentOverviews(false));
    mmm.setLastUsedExperiment(e2);
    assertEquals(
        Lists.newArrayList(
            e2.getExperimentOverview(), e3.getExperimentOverview(), e1.getExperimentOverview()),
        mmm.getExperimentOverviews(false));
  }
}
