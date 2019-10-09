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

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertFalse;
import static junit.framework.Assert.assertNull;
import static junit.framework.Assert.assertTrue;

import android.content.Context;
import androidx.annotation.NonNull;
import com.google.android.apps.forscience.whistlepunk.accounts.AppAccount;
import com.google.android.apps.forscience.whistlepunk.accounts.NonSignedInAccount;
import com.google.android.apps.forscience.whistlepunk.data.GoosciExperimentLibrary.ExperimentLibrary;
import com.google.android.apps.forscience.whistlepunk.data.GoosciGadgetInfo;
import com.google.android.apps.forscience.whistlepunk.data.GoosciLocalSyncStatus;
import com.google.android.apps.forscience.whistlepunk.metadata.GoosciExperiment;
import com.google.android.apps.forscience.whistlepunk.metadata.GoosciTrial;
import com.google.android.apps.forscience.whistlepunk.metadata.Version;
import java.io.File;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

/**
 * Tests for the ExperimentCache class. Note: All experiments created should start with prefix
 * "exp_" so that they can be cleaned up automatically.
 */
@RunWith(RobolectricTestRunner.class)
public class ExperimentCacheTest {
  private int mFailureCount = 0;

  private AppAccount appAccount;
  private ExperimentLibraryManager elm;
  private ExperimentCache cache;
  private LocalSyncManager lsm;

  private Context getContext() {
    return RuntimeEnvironment.application.getApplicationContext();
  }

  private ExperimentCache.FailureListener getFailureFailsListener() {
    return new ExperimentCache.FailureListener() {
      @Override
      public void onWriteFailed(Experiment experimentToWrite) {
        throw new RuntimeException("Expected success");
      }

      @Override
      public void onReadFailed(ExperimentOverviewPojo experimentOverview) {
        throw new RuntimeException("Expected success");
      }

      @Override
      public void onNewerVersionDetected(ExperimentOverviewPojo experimentOverview) {
        throw new RuntimeException("Expected success");
      }
    };
  }

  private ExperimentCache.FailureListener getFailureExpectedListener() {
    return new ExperimentCache.FailureListener() {
      @Override
      public void onWriteFailed(Experiment experimentToWrite) {
        mFailureCount++;
      }

      @Override
      public void onReadFailed(ExperimentOverviewPojo localExperimentOverview) {
        mFailureCount++;
      }

      @Override
      public void onNewerVersionDetected(ExperimentOverviewPojo experimentOverview) {
        mFailureCount++;
      }
    };
  }

  @Before
  public void setUp() {
    cleanUp();
    appAccount = NonSignedInAccount.getInstance(getContext());
    elm = new ExperimentLibraryManager(ExperimentLibrary.getDefaultInstance(), appAccount);
    lsm =
        new LocalSyncManager(
            GoosciLocalSyncStatus.LocalSyncStatus.getDefaultInstance(), appAccount);
    cache =
        new ExperimentCache(
            getContext(),
            appAccount,
            getFailureFailsListener(),
            false /* enableAutoWrite */,
            elm,
            lsm);
  }

  @After
  public void tearDown() {
    cleanUp();
  }

  private void cleanUp() {
    File rootDirectory = getContext().getFilesDir();
    for (File file : rootDirectory.listFiles()) {
      if (file.getName().startsWith("exp_")) {
        ExperimentCache.deleteRecursive(file);
      }
    }
    mFailureCount = 0;
  }

  @Test
  public void testExperimentWriteRead() {
    Experiment experiment = Experiment.newExperiment(10, "exp_localId", 0);
    elm.addExperiment(experiment.getExperimentId());
    lsm.addExperiment(experiment.getExperimentId());
    cache.createNewExperiment(experiment);
    cache.writeActiveExperimentFile();

    // Was it set correctly in the ExperimentCache?
    assertEquals(
        experiment.getExperimentProto(), cache.getActiveExperimentForTests().getExperimentProto());

    // Force a load, make sure that's equal too.
    cache.loadActiveExperimentFromFile(experiment.getExperimentOverview());
    assertEquals(
        experiment.getExperimentProto(), cache.getActiveExperimentForTests().getExperimentProto());

    // Clean up.
    cache.deleteExperiment("exp_localId");
    assertTrue(lsm.getDirty(experiment.getExperimentId()));
    assertNull(cache.getActiveExperimentForTests());
  }

  @Test
  public void testExperimentWithChanges() {
    Experiment experiment = Experiment.newExperiment(10, "exp_localId", 0);
    elm.addExperiment(experiment.getExperimentId());
    lsm.addExperiment(experiment.getExperimentId());
    cache.createNewExperiment(experiment);
    assertTrue(cache.needsWrite());

    cache.writeActiveExperimentFile();
    assertFalse(cache.needsWrite());

    experiment.setTitle("Title");
    cache.updateExperiment(experiment, true);
    assertTrue(lsm.getDirty(experiment.getExperimentId()));
    assertEquals(elm.getModified(experiment.getExperimentId()), experiment.getLastUsedTime());
    assertTrue(cache.needsWrite());
    cache.writeActiveExperimentFile();

    // Force a load, make sure that's got the new title.
    cache.loadActiveExperimentFromFile(experiment.getExperimentOverview());
    assertEquals("Title", cache.getActiveExperimentForTests().getTitle());

    // Clean up.
    cache.deleteExperiment("exp_localId");
    assertNull(cache.getActiveExperimentForTests());
  }

  @Test
  public void testChangingExperimentWritesOldOne() {
    Experiment experiment = Experiment.newExperiment(10, "exp_localId", 0);
    elm.addExperiment(experiment.getExperimentId());
    lsm.addExperiment(experiment.getExperimentId());
    cache.createNewExperiment(experiment);
    assertEquals(10, cache.getActiveExperimentForTests().getCreationTimeMs());
    experiment.setTitle("Title");
    cache.updateExperiment(experiment, false);

    Experiment second = Experiment.newExperiment(20, "exp_secondId", 0);
    elm.addExperiment(second.getExperimentId());
    lsm.addExperiment(second.getExperimentId());
    cache.createNewExperiment(second);
    assertEquals(20, cache.getActiveExperimentForTests().getCreationTimeMs());

    cache.getExperiment(experiment.getExperimentOverview());
    assertEquals(10, cache.getActiveExperimentForTests().getCreationTimeMs());
    assertEquals("Title", cache.getActiveExperimentForTests().getTitle());
  }

  @Test
  public void testUpgradeStartsWriteTimer() {
    GoosciExperiment.Experiment.Builder proto = createExperimentProto().toBuilder();
    proto.setFileVersion(
        proto.getFileVersion().toBuilder().setVersion(0).setMinorVersion(0).setPlatformVersion(0));
    Experiment pojo = Experiment.fromExperiment(proto.build(), new ExperimentOverviewPojo());
    cache.upgradeExperimentVersionIfNeeded(pojo, 1, 1, 1);
    proto = pojo.toProto().toBuilder();
    assertEquals(1, proto.getFileVersion().getVersion());
    assertEquals(1, proto.getFileVersion().getMinorVersion());
    assertEquals(1, proto.getFileVersion().getPlatformVersion());
    assertEquals(
        GoosciGadgetInfo.GadgetInfo.Platform.ANDROID, proto.getFileVersion().getPlatform());
    assertTrue(cache.needsWrite());
  }

  @Test
  public void testUpgradeWhenVersionMissing() {
    GoosciExperiment.Experiment.Builder proto =
        createExperimentProto().toBuilder().clearFileVersion();
    Experiment pojo = Experiment.fromExperiment(proto.build(), new ExperimentOverviewPojo());
    cache.upgradeExperimentVersionIfNeeded(pojo, 1, 1, 1);
    proto = pojo.toProto().toBuilder();
    assertEquals(1, proto.getFileVersion().getVersion());
    assertEquals(1, proto.getFileVersion().getMinorVersion());
    assertEquals(1, proto.getFileVersion().getPlatformVersion());
    assertEquals(
        GoosciGadgetInfo.GadgetInfo.Platform.ANDROID, proto.getFileVersion().getPlatform());
  }

  @Test
  public void testNoUpgradeDoesNotStartWriteTimer() {
    GoosciExperiment.Experiment.Builder proto = createExperimentProto().toBuilder();
    proto.setFileVersion(
        proto.getFileVersion().toBuilder().setVersion(1).setMinorVersion(1).setPlatformVersion(1));
    Experiment pojo = Experiment.fromExperiment(proto.build(), new ExperimentOverviewPojo());
    cache.upgradeExperimentVersionIfNeeded(pojo, 1, 1, 1);
    proto = pojo.toProto().toBuilder();
    assertEquals(1, proto.getFileVersion().getVersion());
    assertEquals(1, proto.getFileVersion().getMinorVersion());
    assertEquals(1, proto.getFileVersion().getPlatformVersion());
    assertFalse(cache.needsWrite());
  }

  @Test
  public void testVersionTooNewThrowsError() {
    cache =
        new ExperimentCache(
            getContext(),
            appAccount,
            getFailureExpectedListener(),
            false /* enableAutoWrite */,
            elm,
            lsm);
    GoosciExperiment.Experiment.Builder proto = createExperimentProto().toBuilder();
    proto.setFileVersion(
        proto.getFileVersion().toBuilder()
            .setVersion(ExperimentCache.VERSION + 1)
            .setMinorVersion(ExperimentCache.MINOR_VERSION));
    Experiment pojo = Experiment.fromExperiment(proto.build(), new ExperimentOverviewPojo());
    cache.upgradeExperimentVersionIfNeeded(
        pojo,
        ExperimentCache.VERSION,
        ExperimentCache.MINOR_VERSION,
        ExperimentCache.PLATFORM_VERSION);
    assertEquals(1, mFailureCount);
  }

  @Test
  public void testOnlyUpgradesMinorVersion() {
    GoosciExperiment.Experiment.Builder proto = createExperimentProto().toBuilder();
    proto.setFileVersion(proto.getFileVersion().toBuilder().setVersion(1).setMinorVersion(0));
    Experiment pojo = Experiment.fromExperiment(proto.build(), new ExperimentOverviewPojo());
    cache.upgradeExperimentVersionIfNeeded(pojo, 1, 1, 1);
    proto = pojo.toProto().toBuilder();
    assertEquals(1, proto.getFileVersion().getVersion());
    assertEquals(1, proto.getFileVersion().getMinorVersion());
  }

  @Test
  public void testUpgradesToMinor2() {
    GoosciExperiment.Experiment.Builder proto = createExperimentProto().toBuilder();
    proto.setFileVersion(
        proto.getFileVersion().toBuilder()
            .setVersion(1)
            .setMinorVersion(1)
            .setPlatformVersion(2)
            .setPlatform(GoosciGadgetInfo.GadgetInfo.Platform.ANDROID));
    Experiment pojo = Experiment.fromExperiment(proto.build(), new ExperimentOverviewPojo());
    cache.upgradeExperimentVersionIfNeeded(pojo, 1, 2, 500);
    proto = pojo.toProto().toBuilder();
    assertEquals(1, proto.getFileVersion().getVersion());
    assertEquals(2, proto.getFileVersion().getMinorVersion());
    assertEquals(500, proto.getFileVersion().getPlatformVersion());
    assertEquals(
        GoosciGadgetInfo.GadgetInfo.Platform.ANDROID, proto.getFileVersion().getPlatform());
  }

  @Test
  public void testDontDowngradePlatform() {
    GoosciExperiment.Experiment.Builder proto = createExperimentProto().toBuilder();
    proto.setFileVersion(
        proto.getFileVersion().toBuilder()
            .setVersion(1)
            .setMinorVersion(2)
            .setPlatformVersion(1000)
            .setPlatform(GoosciGadgetInfo.GadgetInfo.Platform.ANDROID));
    Experiment pojo = Experiment.fromExperiment(proto.build(), new ExperimentOverviewPojo());
    cache.upgradeExperimentVersionIfNeeded(pojo, 1, 2, 500);
    proto = pojo.toProto().toBuilder();
    assertEquals(1, proto.getFileVersion().getVersion());
    assertEquals(2, proto.getFileVersion().getMinorVersion());
    assertEquals(1000, proto.getFileVersion().getPlatformVersion());
    assertEquals(
        GoosciGadgetInfo.GadgetInfo.Platform.ANDROID, proto.getFileVersion().getPlatform());
  }

  @Test
  public void testChangePlatformToAndroid() {
    GoosciExperiment.Experiment.Builder proto = createExperimentProto().toBuilder();
    proto.setFileVersion(
        proto.getFileVersion().toBuilder()
            .setVersion(1)
            .setMinorVersion(1)
            .setPlatformVersion(1000)
            .setPlatform(GoosciGadgetInfo.GadgetInfo.Platform.IOS));
    Experiment pojo = Experiment.fromExperiment(proto.build(), new ExperimentOverviewPojo());
    cache.upgradeExperimentVersionIfNeeded(pojo, 1, 2, 500);
    proto = pojo.toProto().toBuilder();
    assertEquals(1, proto.getFileVersion().getVersion());
    assertEquals(2, proto.getFileVersion().getMinorVersion());
    assertEquals(500, proto.getFileVersion().getPlatformVersion());
    assertEquals(
        GoosciGadgetInfo.GadgetInfo.Platform.ANDROID, proto.getFileVersion().getPlatform());
  }

  @Test
  public void testOnlyUpgradesPlatformVersion() {
    GoosciExperiment.Experiment.Builder proto = createExperimentProto().toBuilder();
    proto.setFileVersion(
        proto.getFileVersion().toBuilder().setVersion(1).setMinorVersion(1).setPlatformVersion(0));
    Experiment pojo = Experiment.fromExperiment(proto.build(), new ExperimentOverviewPojo());
    cache.upgradeExperimentVersionIfNeeded(pojo, 1, 1, 1);
    proto = pojo.toProto().toBuilder();
    assertEquals(1, proto.getFileVersion().getVersion());
    assertEquals(1, proto.getFileVersion().getMinorVersion());
    assertEquals(1, proto.getFileVersion().getPlatformVersion());
  }

  @Test
  public void testCantWriteNewerVersion() {
    cache =
        new ExperimentCache(
            getContext(),
            appAccount,
            getFailureExpectedListener(),
            false /* enableAutoWrite */,
            elm,
            lsm);
    GoosciExperiment.Experiment.Builder proto = createExperimentProto().toBuilder();
    proto.setFileVersion(
        proto.getFileVersion().toBuilder()
            .setVersion(ExperimentCache.VERSION)
            .setMinorVersion(ExperimentCache.MINOR_VERSION + 1));
    ExperimentOverviewPojo overview = new ExperimentOverviewPojo();
    overview.setExperimentId("foo");
    Experiment pojo = Experiment.fromExperiment(proto.build(), new ExperimentOverviewPojo());
    cache.upgradeExperimentVersionIfNeeded(
        pojo,
        ExperimentCache.VERSION,
        ExperimentCache.MINOR_VERSION,
        ExperimentCache.PLATFORM_VERSION);
    // Version should be unchanged -- don't upgrade minor version.
    proto = pojo.toProto().toBuilder();
    assertEquals(ExperimentCache.VERSION, proto.getFileVersion().getVersion());
    assertEquals(ExperimentCache.MINOR_VERSION + 1, proto.getFileVersion().getMinorVersion());

    // But no errors yet -- didn't try to save it.
    assertEquals(0, mFailureCount);

    Experiment experiment = Experiment.fromExperiment(proto.build(), overview);
    elm.addExperiment(experiment.getExperimentId());
    lsm.addExperiment(experiment.getExperimentId());
    cache.updateExperiment(experiment, false); // Set this one to active so we can try to write it.
    assertFalse(lsm.getDirty(experiment.getExperimentId()));
    cache.writeActiveExperimentFile();
    assertEquals(1, mFailureCount);
  }

  @Test
  public void testPlatformVersion1To2() {
    // From 1.1.1 to 1.1.2, we index the trials within the experiment.
    GoosciExperiment.Experiment.Builder proto = createExperimentProto().toBuilder();
    proto.setFileVersion(
        proto.getFileVersion().toBuilder().setPlatformVersion(1).setVersion(1).setMinorVersion(1));
    ExperimentOverviewPojo overview = new ExperimentOverviewPojo();
    GoosciTrial.Trial trial1 = GoosciTrial.Trial.getDefaultInstance();
    GoosciTrial.Trial trial2 = GoosciTrial.Trial.getDefaultInstance();
    proto.clearTrials().addTrials(trial1).addTrials(trial2);
    Experiment pojo = Experiment.fromExperiment(proto.build(), overview);
    cache.upgradeExperimentVersionIfNeeded(pojo, 1, 1, 2);
    proto = pojo.toProto().toBuilder();
    assertEquals(0, mFailureCount);
    assertEquals(2, proto.getFileVersion().getPlatformVersion());
    assertEquals(2, proto.getTotalTrials());
    assertEquals(1, proto.getTrials(0).getTrialNumberInExperiment());
    assertEquals(2, proto.getTrials(1).getTrialNumberInExperiment());
  }

  @NonNull
  private GoosciExperiment.Experiment createExperimentProto() {
    return GoosciExperiment.Experiment.newBuilder()
        .setFileVersion(Version.FileVersion.getDefaultInstance())
        .build();
  }
}
