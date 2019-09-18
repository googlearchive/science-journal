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
import com.google.android.apps.forscience.whistlepunk.data.GoosciExperimentLibrary.ExperimentLibrary;
import com.google.android.apps.forscience.whistlepunk.data.GoosciGadgetInfo;
import com.google.android.apps.forscience.whistlepunk.data.GoosciLocalSyncStatus.LocalSyncStatus;
import com.google.android.apps.forscience.whistlepunk.metadata.GoosciLabel;
import com.google.android.apps.forscience.whistlepunk.metadata.GoosciTextLabelValue;
import com.google.android.apps.forscience.whistlepunk.metadata.Version;
import com.google.android.apps.forscience.whistlepunk.sensordb.IncrementableMonotonicClock;
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
    lsm.setLocalSyncStatus(LocalSyncStatus.getDefaultInstance());
    ExperimentLibraryManager elm =
        AppSingleton.getInstance(getContext()).getExperimentLibraryManager(getAppAccount());
    elm.setLibrary(ExperimentLibrary.getDefaultInstance());
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
            GoosciTextLabelValue.TextLabelValue.getDefaultInstance(),
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
    lsm.setLocalSyncStatus(LocalSyncStatus.getDefaultInstance());
    ExperimentLibraryManager elm =
        AppSingleton.getInstance(getContext()).getExperimentLibraryManager(getAppAccount());
    elm.setLibrary(ExperimentLibrary.getDefaultInstance());
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
        first.getExperimentOverview().getColorIndex()
            != second.getExperimentOverview().getColorIndex());
    assertTrue(
        second.getExperimentOverview().getColorIndex()
            != third.getExperimentOverview().getColorIndex());
    assertTrue(
        first.getExperimentOverview().getColorIndex()
            != third.getExperimentOverview().getColorIndex());

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
    Version.FileVersion.Builder fileVersion =
        Version.FileVersion.newBuilder()
            .setVersion(1)
            .setMinorVersion(1)
            .setPlatform(GoosciGadgetInfo.GadgetInfo.Platform.ANDROID)
            .setPlatformVersion(1);
    assertTrue(fileMetadataUtil.canImportFromVersion(fileVersion.build()));

    fileVersion.setMinorVersion(2);
    assertTrue(fileMetadataUtil.canImportFromVersion(fileVersion.build()));

    fileVersion.setMinorVersion(3);
    assertFalse(fileMetadataUtil.canImportFromVersion(fileVersion.build()));

    fileVersion.setVersion(2).setMinorVersion(1);
    assertFalse(fileMetadataUtil.canImportFromVersion(fileVersion.build()));

    fileVersion
        .setPlatform(GoosciGadgetInfo.GadgetInfo.Platform.IOS)
        .setVersion(1)
        .setMinorVersion(1)
        .setPlatformVersion(1);
    assertFalse(fileMetadataUtil.canImportFromVersion(fileVersion.build()));

    fileVersion.setPlatformVersion(2);
    assertFalse(fileMetadataUtil.canImportFromVersion(fileVersion.build()));

    fileVersion.setPlatformVersion(3);
    assertTrue(fileMetadataUtil.canImportFromVersion(fileVersion.build()));

    fileVersion.setMinorVersion(2);
    assertTrue(fileMetadataUtil.canImportFromVersion(fileVersion.build()));

    fileVersion.setMinorVersion(3);
    assertFalse(fileMetadataUtil.canImportFromVersion(fileVersion.build()));
  }

  private static Context getContext() {
    return RuntimeEnvironment.application.getApplicationContext();
  }

  private static AppAccount getAppAccount() {
    return NonSignedInAccount.getInstance(getContext());
  }
}
