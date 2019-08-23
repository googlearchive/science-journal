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

import com.google.android.apps.forscience.whistlepunk.accounts.NonSignedInAccount;
import com.google.android.apps.forscience.whistlepunk.data.GoosciLocalSyncStatus.LocalSyncStatus;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

/** Tests for the LocalSyncManager class. */
@RunWith(RobolectricTestRunner.class)
public class LocalSyncManagerTest {

  @Test
  public void testSetDirty() {
    LocalSyncManager manager =
        new LocalSyncManager(
            LocalSyncStatus.getDefaultInstance(),
            NonSignedInAccount.getInstance(RuntimeEnvironment.application.getApplicationContext()));
    manager.addExperiment("id");
    manager.setLastSyncedLibraryVersion(10L);

    manager.setDirty("id", false);
    assertFalse(manager.getDirty("id"));
    assertEquals(10, manager.getLastSyncedLibraryVersion());
    manager.setDirty("id", true);
    assertTrue(manager.getDirty("id"));
    assertEquals(0, manager.getLastSyncedLibraryVersion());
  }

  @Test
  public void testSetLastSyncedVersion() {
    LocalSyncManager manager =
        new LocalSyncManager(
            LocalSyncStatus.getDefaultInstance(),
            NonSignedInAccount.getInstance(RuntimeEnvironment.application.getApplicationContext()));
    manager.addExperiment("id");

    manager.setLastSyncedVersion("id", 2);
    assertEquals(2, manager.getLastSyncedVersion("id"));
    manager.setLastSyncedVersion("id", 7);
    assertEquals(7, manager.getLastSyncedVersion("id"));
  }

  @Test
  public void testSetServerArchived() {
    LocalSyncManager manager =
        new LocalSyncManager(
            LocalSyncStatus.getDefaultInstance(),
            NonSignedInAccount.getInstance(RuntimeEnvironment.application.getApplicationContext()));
    manager.addExperiment("id");

    manager.setServerArchived("id", false);
    assertFalse(manager.getServerArchived("id"));
    manager.setServerArchived("id", true);
    assertTrue(manager.getServerArchived("id"));
  }

  @Test
  public void testSetDownloaded() {
    LocalSyncManager manager =
        new LocalSyncManager(
            LocalSyncStatus.getDefaultInstance(),
            NonSignedInAccount.getInstance(RuntimeEnvironment.application.getApplicationContext()));
    manager.addExperiment("id");

    manager.setDownloaded("id", false);
    assertFalse(manager.getDownloaded("id"));
    manager.setDownloaded("id", true);
    assertTrue(manager.getDownloaded("id"));
  }
}
