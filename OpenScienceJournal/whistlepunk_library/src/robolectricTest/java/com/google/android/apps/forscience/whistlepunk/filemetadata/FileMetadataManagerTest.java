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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import com.google.android.apps.forscience.whistlepunk.AppSingleton;
import com.google.android.apps.forscience.whistlepunk.accounts.AppAccount;
import com.google.android.apps.forscience.whistlepunk.accounts.NonSignedInAccount;
import com.google.android.apps.forscience.whistlepunk.data.GoosciGadgetInfo;
import com.google.android.apps.forscience.whistlepunk.data.nano.GoosciExperimentLibrary.ExperimentLibrary;
import com.google.android.apps.forscience.whistlepunk.data.nano.GoosciLocalSyncStatus.LocalSyncStatus;
import com.google.android.apps.forscience.whistlepunk.metadata.GoosciLabel;
import com.google.android.apps.forscience.whistlepunk.metadata.nano.GoosciTextLabelValue;
import com.google.android.apps.forscience.whistlepunk.metadata.nano.Version;
import com.google.android.apps.forscience.whistlepunk.sensordb.IncrementableMonotonicClock;
import com.google.protobuf.migration.nano2lite.runtime.MigrateAs;
import com.google.protobuf.migration.nano2lite.runtime.MigrateAs.Destination;
import java.io.File;
import java.io.IOException;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

/** Tests for the FileMetadataManager class. */
@RunWith(RobolectricTestRunner.class)
public class FileMetadataManagerTest {

  private final FileMetadataUtil fileMetadataUtil = FileMetadataUtil.getInstance();

  @Before
  public void setUp() {
    cleanUp();
  }

  @After
  public void tearDown() {
    cleanUp();
  }

  private void cleanUp() {
    File sharedMetadataFile = fileMetadataUtil.getUserMetadataFile(getAppAccount());
    sharedMetadataFile.delete();
  }

  @Test
  public void testSingleExperiment() {
    IncrementableMonotonicClock clock = new IncrementableMonotonicClock();
    FileMetadataManager fmm = new FileMetadataManager(getContext(), getAppAccount(), clock);
    LocalSyncManager lsm =
        AppSingleton.getInstance(getContext()).getLocalSyncManager(getAppAccount());
    lsm.setLocalSyncStatus(new LocalSyncStatus());
    ExperimentLibraryManager elm =
        AppSingleton.getInstance(getContext()).getExperimentLibraryManager(getAppAccount());
    elm.setLibrary(new ExperimentLibrary());
    Experiment experiment = fmm.newExperiment();
    assertEquals(experiment.getCreationTimeMs(), clock.getNow());
    assertEquals(
        fmm.getExperimentById(experiment.getExperimentId()).getLastUsedTime(), clock.getNow());

    clock.increment();
    fmm.setLastUsedExperiment(experiment);
    assertEquals(
        fmm.getExperimentById(experiment.getExperimentId()).getLastUsedTime(), clock.getNow());

    clock.increment();
    experiment.setTitle("Title");
    experiment.addLabel(
        Label.newLabelWithValue(
            clock.getNow(),
            GoosciLabel.Label.ValueType.TEXT,
            new GoosciTextLabelValue.TextLabelValue(),
            null));
    fmm.updateExperiment(experiment, true);

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
    FileMetadataManager fmm = new FileMetadataManager(getContext(), getAppAccount(), clock);
    LocalSyncManager lsm =
        AppSingleton.getInstance(getContext()).getLocalSyncManager(getAppAccount());
    lsm.setLocalSyncStatus(new LocalSyncStatus());
    ExperimentLibraryManager elm =
        AppSingleton.getInstance(getContext()).getExperimentLibraryManager(getAppAccount());
    elm.setLibrary(new ExperimentLibrary());
    Experiment first = fmm.newExperiment();
    clock.increment();
    Experiment second = fmm.newExperiment();
    clock.increment();
    Experiment third = fmm.newExperiment();
    clock.increment();

    assertEquals(third.getExperimentId(), fmm.getLastUsedUnarchivedExperiment().getExperimentId());
    assertEquals(3, fmm.getExperimentOverviews(false).size());

    third.setArchived(getContext(), getAppAccount(), true);
    elm.setArchived(third.getExperimentId(), true);
    assertTrue(elm.isArchived(third.getExperimentId()));
    fmm.updateExperiment(third, true);
    assertEquals(second.getExperimentId(), fmm.getLastUsedUnarchivedExperiment().getExperimentId());
    assertEquals(2, fmm.getExperimentOverviews(false).size());
    assertTrue(elm.isArchived(third.getExperimentId()));

    // Doesn't re-use colors
    assertTrue(
        first.getExperimentOverview().colorIndex != second.getExperimentOverview().colorIndex);
    assertTrue(
        second.getExperimentOverview().colorIndex != third.getExperimentOverview().colorIndex);
    assertTrue(
        first.getExperimentOverview().colorIndex != third.getExperimentOverview().colorIndex);

    // Clean up
    fmm.deleteExperiment(first);
    fmm.deleteExperiment(second);
    fmm.deleteExperiment(third);
  }

  @Test
  public void testRelativePathFunctions() throws IOException {
    File file =
        new File(getAppAccount().getFilesDir() + "/experiments/experiment182/assets/cats.png");
    assertEquals(
        "assets/cats.png", fileMetadataUtil.getRelativePathInExperiment("experiment182", file));

    // No match should return an empty string.
    assertEquals("", fileMetadataUtil.getRelativePathInExperiment("experiment42", file));

    File result =
        fileMetadataUtil.getExperimentFile(getAppAccount(), "experiment182", "assets/cats.png");
    assertEquals(file.getAbsolutePath(), result.getAbsolutePath());

    assertEquals(
        "experiments/experiment182/assets/cats.png",
        fileMetadataUtil.getRelativePathInFilesDir("experiment182", "assets/cats.png").toString());

    File exportDir = new File(getAppAccount().getFilesDir().toString(), "/exported_experiments");
    assertEquals(
        exportDir.getAbsolutePath().toString(),
        fileMetadataUtil.getExperimentExportDirectory(getAppAccount()));
  }

  @Test
  public void versionChecks() {
    @MigrateAs(Destination.BUILDER)
    Version.FileVersion fileVersion = new Version.FileVersion();
    fileVersion.version = 1;
    fileVersion.minorVersion = 1;
    fileVersion.platform = GoosciGadgetInfo.GadgetInfo.Platform.ANDROID;
    fileVersion.platformVersion = 1;
    assertTrue(fileMetadataUtil.canImportFromVersion(fileVersion));

    fileVersion.minorVersion = 2;
    assertTrue(fileMetadataUtil.canImportFromVersion(fileVersion));

    fileVersion.minorVersion = 3;
    assertFalse(fileMetadataUtil.canImportFromVersion(fileVersion));

    fileVersion.version = 2;
    fileVersion.minorVersion = 1;
    assertFalse(fileMetadataUtil.canImportFromVersion(fileVersion));

    fileVersion.platform = GoosciGadgetInfo.GadgetInfo.Platform.IOS;
    fileVersion.version = 1;
    fileVersion.minorVersion = 1;
    fileVersion.platformVersion = 1;
    assertFalse(fileMetadataUtil.canImportFromVersion(fileVersion));

    fileVersion.platformVersion = 2;
    assertFalse(fileMetadataUtil.canImportFromVersion(fileVersion));

    fileVersion.platformVersion = 3;
    assertTrue(fileMetadataUtil.canImportFromVersion(fileVersion));

    fileVersion.minorVersion = 2;
    assertTrue(fileMetadataUtil.canImportFromVersion(fileVersion));

    fileVersion.minorVersion = 3;
    assertFalse(fileMetadataUtil.canImportFromVersion(fileVersion));
  }

  private static Context getContext() {
    return RuntimeEnvironment.application.getApplicationContext();
  }

  private static AppAccount getAppAccount() {
    return NonSignedInAccount.getInstance(getContext());
  }
}
