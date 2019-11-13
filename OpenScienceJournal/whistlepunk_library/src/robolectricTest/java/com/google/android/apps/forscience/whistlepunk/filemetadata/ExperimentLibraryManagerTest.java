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

import com.google.android.apps.forscience.whistlepunk.accounts.NonSignedInAccount;
import com.google.android.apps.forscience.whistlepunk.data.GoosciExperimentLibrary;
import com.google.android.apps.forscience.whistlepunk.data.GoosciExperimentLibrary.ExperimentLibrary;
import com.google.android.apps.forscience.whistlepunk.data.GoosciExperimentLibrary.SyncExperiment;
import com.google.android.apps.forscience.whistlepunk.data.GoosciLocalSyncStatus.LocalSyncStatus;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

/** Tests for the ExperimentLibraryManager class. */
@RunWith(RobolectricTestRunner.class)
public class ExperimentLibraryManagerTest {

  private ExperimentLibraryManager getTestManager(
      GoosciExperimentLibrary.ExperimentLibrary library) {
    return new ExperimentLibraryManager(
        library,
        NonSignedInAccount.getInstance(RuntimeEnvironment.application.getApplicationContext()));
  }

  private ExperimentLibraryManager getTestManager() {
    return new ExperimentLibraryManager(
        ExperimentLibrary.getDefaultInstance(),
        NonSignedInAccount.getInstance(RuntimeEnvironment.application.getApplicationContext()));
  }

  private LocalSyncManager getTestLocalSyncManager() {
    return new LocalSyncManager(
        LocalSyncStatus.getDefaultInstance(),
        NonSignedInAccount.getInstance(RuntimeEnvironment.application.getApplicationContext()));
  }

  @Test
  public void testSetArchived() {
    GoosciExperimentLibrary.ExperimentLibrary.Builder library = ExperimentLibrary.newBuilder();
    SyncExperiment experiment =
        GoosciExperimentLibrary.SyncExperiment.newBuilder().setExperimentId("id").build();

    library.addSyncExperiment(experiment);

    ExperimentLibraryManager manager = getTestManager(library.build());
    manager.setArchived("id", false);
    assertFalse(manager.isArchived("id"));
    manager.setArchived("id", true);
    assertTrue(manager.isArchived("id"));
  }

  @Test
  public void testSetDeleted() {
    GoosciExperimentLibrary.ExperimentLibrary.Builder library = ExperimentLibrary.newBuilder();
    SyncExperiment experiment =
        GoosciExperimentLibrary.SyncExperiment.newBuilder().setExperimentId("id").build();

    library.addSyncExperiment(experiment);

    ExperimentLibraryManager manager = getTestManager(library.build());
    manager.setDeleted("id", false);
    assertFalse(manager.isDeleted("id"));
    manager.setDeleted("id", true);
    assertTrue(manager.isDeleted("id"));
  }

  @Test
  public void testSetOpened() {
    GoosciExperimentLibrary.ExperimentLibrary.Builder library = ExperimentLibrary.newBuilder();
    SyncExperiment experiment =
        GoosciExperimentLibrary.SyncExperiment.newBuilder().setExperimentId("id").build();

    library.addSyncExperiment(experiment);

    ExperimentLibraryManager manager = getTestManager(library.build());
    manager.setOpened("id", 10);
    assertEquals(manager.getOpened("id"), 10);
    manager.setOpened("id", 20);
    assertEquals(manager.getOpened("id"), 20);
  }

  @Test
  public void testSetModified() {
    GoosciExperimentLibrary.ExperimentLibrary.Builder library = ExperimentLibrary.newBuilder();
    SyncExperiment experiment =
        GoosciExperimentLibrary.SyncExperiment.newBuilder().setExperimentId("id").build();

    library.addSyncExperiment(experiment);

    ExperimentLibraryManager manager = getTestManager(library.build());
    manager.setModified("id", 10);
    assertEquals(manager.getModified("id"), 10);
    manager.setModified("id", 20);
    assertEquals(manager.getModified("id"), 20);
  }

  @Test
  public void testUpdateWithNewExperimentAndFolderId() {
    ExperimentLibraryManager manager = getTestManager();
    manager.addExperiment("id");
    manager.setFolderId("foo");

    assertEquals("foo", manager.getFolderId());
    assertNull(manager.getExperiment("id2"));

    GoosciExperimentLibrary.ExperimentLibrary.Builder library = ExperimentLibrary.newBuilder();
    SyncExperiment experiment =
        GoosciExperimentLibrary.SyncExperiment.newBuilder()
            .setExperimentId("id2")
            .setArchived(true)
            .build();

    library.addSyncExperiment(experiment).setFolderId("bar");

    manager.merge(library.build(), getTestLocalSyncManager());

    assertNotNull(manager.getExperiment("id"));
    assertNotNull(manager.getExperiment("id2"));
    assertEquals("bar", manager.getFolderId());
    assertFalse(manager.isArchived("id"));
    assertTrue(manager.isArchived("id2"));
  }

  @Test
  public void testUpdateTimesWithExistingExperiment() {
    ExperimentLibraryManager manager = getTestManager();
    manager.addExperiment("id");
    manager.setModified("id", 100);
    manager.setOpened("id", 100);

    GoosciExperimentLibrary.ExperimentLibrary.Builder library = ExperimentLibrary.newBuilder();
    SyncExperiment experiment =
        GoosciExperimentLibrary.SyncExperiment.newBuilder()
            .setExperimentId("id")
            .setLastModified(200)
            .setLastOpened(300)
            .build();

    library.addSyncExperiment(experiment);

    manager.merge(library.build(), getTestLocalSyncManager());

    assertEquals(200, manager.getModified("id"));
    assertEquals(300, manager.getOpened("id"));
  }

  @Test
  public void testUpdateSomeTimesWithExistingExperiment() {
    ExperimentLibraryManager manager = getTestManager();
    manager.addExperiment("id");
    manager.setModified("id", 100);
    manager.setOpened("id", 100);

    GoosciExperimentLibrary.ExperimentLibrary.Builder library = ExperimentLibrary.newBuilder();
    SyncExperiment experiment =
        GoosciExperimentLibrary.SyncExperiment.newBuilder()
            .setExperimentId("id")
            .setLastModified(200)
            .setLastOpened(50)
            .build();

    library.addSyncExperiment(experiment);

    manager.merge(library.build(), getTestLocalSyncManager());

    assertEquals(200, manager.getModified("id"));
    assertEquals(100, manager.getOpened("id"));
  }

  @Test
  public void testUpdateDeleteWithExistingExperiment() {
    ExperimentLibraryManager manager = getTestManager();
    manager.addExperiment("id");
    manager.setDeleted("id", false);

    GoosciExperimentLibrary.ExperimentLibrary.Builder library = ExperimentLibrary.newBuilder();
    SyncExperiment experiment =
        GoosciExperimentLibrary.SyncExperiment.newBuilder()
            .setExperimentId("id")
            .setDeleted(true)
            .build();

    library.addSyncExperiment(experiment);

    manager.merge(library.build(), getTestLocalSyncManager());

    assertTrue(manager.isDeleted("id"));
  }

  @Test
  public void testDontUpdateDeleteIfExistingAlreadyDeleted() {
    ExperimentLibraryManager manager = getTestManager();
    manager.addExperiment("id");
    manager.setDeleted("id", true);

    GoosciExperimentLibrary.ExperimentLibrary.Builder library = ExperimentLibrary.newBuilder();
    SyncExperiment experiment =
        GoosciExperimentLibrary.SyncExperiment.newBuilder()
            .setExperimentId("id")
            .setDeleted(false)
            .build();

    library.addSyncExperiment(experiment);

    manager.merge(library.build(), getTestLocalSyncManager());

    assertTrue(manager.isDeleted("id"));
  }

  @Test
  public void testUpdateArchivedIfMergeSourceChanged() {
    ExperimentLibraryManager manager = getTestManager();
    manager.addExperiment("id");
    manager.setArchived("id", false);

    GoosciExperimentLibrary.ExperimentLibrary.Builder library = ExperimentLibrary.newBuilder();
    SyncExperiment experiment =
        GoosciExperimentLibrary.SyncExperiment.newBuilder()
            .setExperimentId("id")
            .setArchived(true)
            .build();

    library.addSyncExperiment(experiment);

    LocalSyncManager localSync = getTestLocalSyncManager();
    localSync.addExperiment("id");
    localSync.setServerArchived("id", false);
    manager.merge(library.build(), localSync);

    assertTrue(manager.isArchived("id"));
  }

  @Test
  public void testDontUpdateArchivedIfMergeSourceUnchanged() {
    ExperimentLibraryManager manager = getTestManager();
    manager.addExperiment("id");
    manager.setArchived("id", true);

    GoosciExperimentLibrary.ExperimentLibrary.Builder library = ExperimentLibrary.newBuilder();
    SyncExperiment experiment =
        GoosciExperimentLibrary.SyncExperiment.newBuilder()
            .setExperimentId("id")
            .setArchived(false)
            .build();

    library.addSyncExperiment(experiment);

    LocalSyncManager localSync = getTestLocalSyncManager();
    localSync.addExperiment("id");
    localSync.setServerArchived("id", false);
    manager.merge(library.build(), localSync);

    assertTrue(manager.isArchived("id"));
  }

  @Test
  public void testSetGetFolderId() {
    ExperimentLibraryManager manager = getTestManager();
    manager.setFolderId("FolderId");
    assertEquals("FolderId", manager.getFolderId());
  }
}
