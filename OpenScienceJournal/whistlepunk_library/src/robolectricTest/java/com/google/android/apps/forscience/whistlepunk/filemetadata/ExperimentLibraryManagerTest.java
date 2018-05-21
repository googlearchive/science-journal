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
import static junit.framework.Assert.assertNotNull;
import static junit.framework.Assert.assertNull;
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

  @Test
  public void testUpdateWithNewExperiment() {
    ExperimentLibraryManager manager = new ExperimentLibraryManager();
    manager.addExperiment("id");

    assertNull(manager.getExperiment("id2"));

    GoosciExperimentLibrary.ExperimentLibrary library =
        new GoosciExperimentLibrary.ExperimentLibrary();
    GoosciExperimentLibrary.SyncExperiment experiment =
        new GoosciExperimentLibrary.SyncExperiment();
    experiment.experimentId = "id2";

    library.syncExperiment = new GoosciExperimentLibrary.SyncExperiment[] {experiment};

    manager.merge(library, new LocalSyncManager());

    assertNotNull(manager.getExperiment("id"));
    assertNotNull(manager.getExperiment("id2"));
  }

  @Test
  public void testUpdateTimesWithExistingExperiment() {
    ExperimentLibraryManager manager = new ExperimentLibraryManager();
    manager.addExperiment("id");
    manager.setModified("id", 100);
    manager.setOpened("id", 100);

    GoosciExperimentLibrary.ExperimentLibrary library =
        new GoosciExperimentLibrary.ExperimentLibrary();
    GoosciExperimentLibrary.SyncExperiment experiment =
        new GoosciExperimentLibrary.SyncExperiment();
    experiment.experimentId = "id";
    experiment.lastModified = 200;
    experiment.lastOpened = 300;

    library.syncExperiment = new GoosciExperimentLibrary.SyncExperiment[] {experiment};

    manager.merge(library, new LocalSyncManager());

    assertEquals(200, manager.getModified("id"));
    assertEquals(300, manager.getOpened("id"));
  }

  @Test
  public void testUpdateSomeTimesWithExistingExperiment() {
    ExperimentLibraryManager manager = new ExperimentLibraryManager();
    manager.addExperiment("id");
    manager.setModified("id", 100);
    manager.setOpened("id", 100);

    GoosciExperimentLibrary.ExperimentLibrary library =
        new GoosciExperimentLibrary.ExperimentLibrary();
    GoosciExperimentLibrary.SyncExperiment experiment =
        new GoosciExperimentLibrary.SyncExperiment();
    experiment.experimentId = "id";
    experiment.lastModified = 200;
    experiment.lastOpened = 50;

    library.syncExperiment = new GoosciExperimentLibrary.SyncExperiment[] {experiment};

    manager.merge(library, new LocalSyncManager());

    assertEquals(200, manager.getModified("id"));
    assertEquals(100, manager.getOpened("id"));
  }

  @Test
  public void testUpdateDeleteWithExistingExperiment() {
    ExperimentLibraryManager manager = new ExperimentLibraryManager();
    manager.addExperiment("id");
    manager.setDeleted("id", false);

    GoosciExperimentLibrary.ExperimentLibrary library =
        new GoosciExperimentLibrary.ExperimentLibrary();
    GoosciExperimentLibrary.SyncExperiment experiment =
        new GoosciExperimentLibrary.SyncExperiment();
    experiment.experimentId = "id";
    experiment.deleted = true;

    library.syncExperiment = new GoosciExperimentLibrary.SyncExperiment[] {experiment};

    manager.merge(library, new LocalSyncManager());

    assertTrue(manager.isDeleted("id"));
  }

  @Test
  public void testDontUpdateDeleteIfExistingAlreadyDeleted() {
    ExperimentLibraryManager manager = new ExperimentLibraryManager();
    manager.addExperiment("id");
    manager.setDeleted("id", true);

    GoosciExperimentLibrary.ExperimentLibrary library =
        new GoosciExperimentLibrary.ExperimentLibrary();
    GoosciExperimentLibrary.SyncExperiment experiment =
        new GoosciExperimentLibrary.SyncExperiment();
    experiment.experimentId = "id";
    experiment.deleted = false;

    library.syncExperiment = new GoosciExperimentLibrary.SyncExperiment[] {experiment};

    manager.merge(library, new LocalSyncManager());

    assertTrue(manager.isDeleted("id"));
  }

  @Test
  public void testUpdateArchivedIfMergeSourceChanged() {
    ExperimentLibraryManager manager = new ExperimentLibraryManager();
    manager.addExperiment("id");
    manager.setArchived("id", false);

    GoosciExperimentLibrary.ExperimentLibrary library =
        new GoosciExperimentLibrary.ExperimentLibrary();
    GoosciExperimentLibrary.SyncExperiment experiment =
        new GoosciExperimentLibrary.SyncExperiment();
    experiment.experimentId = "id";
    experiment.archived = true;

    library.syncExperiment = new GoosciExperimentLibrary.SyncExperiment[] {experiment};

    LocalSyncManager localSync = new LocalSyncManager();
    localSync.addExperiment("id");
    localSync.setServerArchived("id", false);
    manager.merge(library, localSync);

    assertTrue(manager.isArchived("id"));
  }

  @Test
  public void testDontUpdateArchivedIfMergeSourceUnchanged() {
    ExperimentLibraryManager manager = new ExperimentLibraryManager();
    manager.addExperiment("id");
    manager.setArchived("id", true);

    GoosciExperimentLibrary.ExperimentLibrary library =
        new GoosciExperimentLibrary.ExperimentLibrary();
    GoosciExperimentLibrary.SyncExperiment experiment =
        new GoosciExperimentLibrary.SyncExperiment();
    experiment.experimentId = "id";
    experiment.archived = false;

    library.syncExperiment = new GoosciExperimentLibrary.SyncExperiment[] {experiment};

    LocalSyncManager localSync = new LocalSyncManager();
    localSync.addExperiment("id");
    localSync.setServerArchived("id", false);
    manager.merge(library, localSync);

    assertTrue(manager.isArchived("id"));
  }
}
