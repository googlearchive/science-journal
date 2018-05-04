/*
 *  Copyright 2018 Google Inc. All Rights Reserved.
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

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertFalse;
import static junit.framework.Assert.assertTrue;

import com.google.android.apps.forscience.whistlepunk.data.nano.GoosciExperimentLibrary;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

/** Tests for the ExperimentLibraryManager class. */
@RunWith(RobolectricTestRunner.class)
public class ExperimentLibraryManagerTest {

  @Test
  public void testSetArchived() {
    GoosciExperimentLibrary.ExperimentLibrary library =
        new GoosciExperimentLibrary.ExperimentLibrary();
    GoosciExperimentLibrary.SyncExperiment experiment =
        new GoosciExperimentLibrary.SyncExperiment();
    experiment.experimentId = "id";

    library.syncExperiment = new GoosciExperimentLibrary.SyncExperiment[] {experiment};

    ExperimentLibraryManager manager = new ExperimentLibraryManager(library);
    manager.setArchived("id", false);
    assertFalse(experiment.archived);
    manager.setArchived("id", true);
    assertTrue(experiment.archived);
  }

  @Test
  public void testSetDeleted() {
    GoosciExperimentLibrary.ExperimentLibrary library =
        new GoosciExperimentLibrary.ExperimentLibrary();
    GoosciExperimentLibrary.SyncExperiment experiment =
        new GoosciExperimentLibrary.SyncExperiment();
    experiment.experimentId = "id";

    library.syncExperiment = new GoosciExperimentLibrary.SyncExperiment[] {experiment};

    ExperimentLibraryManager manager = new ExperimentLibraryManager(library);
    manager.setDeleted("id", false);
    assertFalse(experiment.deleted);
    manager.setDeleted("id", true);
    assertTrue(experiment.deleted);
  }

  @Test
  public void testSetOpened() {
    GoosciExperimentLibrary.ExperimentLibrary library =
        new GoosciExperimentLibrary.ExperimentLibrary();
    GoosciExperimentLibrary.SyncExperiment experiment =
        new GoosciExperimentLibrary.SyncExperiment();
    experiment.experimentId = "id";

    library.syncExperiment = new GoosciExperimentLibrary.SyncExperiment[] {experiment};

    ExperimentLibraryManager manager = new ExperimentLibraryManager(library);
    manager.setOpened("id", 10);
    assertEquals(experiment.lastOpened, 10);
    manager.setOpened("id", 20);
    assertEquals(experiment.lastOpened, 20);
  }

  @Test
  public void testSetModified() {
    GoosciExperimentLibrary.ExperimentLibrary library =
        new GoosciExperimentLibrary.ExperimentLibrary();
    GoosciExperimentLibrary.SyncExperiment experiment =
        new GoosciExperimentLibrary.SyncExperiment();
    experiment.experimentId = "id";

    library.syncExperiment = new GoosciExperimentLibrary.SyncExperiment[] {experiment};

    ExperimentLibraryManager manager = new ExperimentLibraryManager(library);
    manager.setModified("id", 10);
    assertEquals(experiment.lastModified, 10);
    manager.setModified("id", 20);
    assertEquals(experiment.lastModified, 20);
  }
}
