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

import static com.google.common.truth.Truth.assertThat;

import android.content.Context;
import com.google.android.apps.forscience.whistlepunk.accounts.AppAccount;
import com.google.android.apps.forscience.whistlepunk.accounts.NonSignedInAccount;
import com.google.android.apps.forscience.whistlepunk.data.GoosciDeviceSpec;
import com.google.android.apps.forscience.whistlepunk.data.GoosciDeviceSpec.DeviceSpec;
import com.google.android.apps.forscience.whistlepunk.data.GoosciGadgetInfo;
import com.google.android.apps.forscience.whistlepunk.metadata.GoosciUserMetadata;
import com.google.android.apps.forscience.whistlepunk.metadata.GoosciUserMetadata.ExperimentOverview;
import com.google.android.apps.forscience.whistlepunk.metadata.GoosciUserMetadata.UserMetadata;
import java.io.File;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

/** Tests for SharedMetadatamanager */
@RunWith(RobolectricTestRunner.class)
public class UserMetadataManagerTest {
  private int failureCount = 0;

  private UserMetadataManager.FailureListener getFailureFailsListener() {
    return new UserMetadataManager.FailureListener() {
      @Override
      public void onWriteFailed() {
        throw new RuntimeException("Expected success");
      }

      @Override
      public void onReadFailed() {
        throw new RuntimeException("Expected success");
      }

      @Override
      public void onNewerVersionDetected() {
        throw new RuntimeException("Expected success");
      }
    };
  }

  private UserMetadataManager.FailureListener getFailureExpectedListener() {
    return new UserMetadataManager.FailureListener() {
      @Override
      public void onWriteFailed() {
        failureCount++;
      }

      @Override
      public void onReadFailed() {
        failureCount++;
      }

      @Override
      public void onNewerVersionDetected() {
        failureCount++;
      }
    };
  }

  @Before
  public void setUp() {
    cleanUp();
  }

  @After
  public void tearDown() {
    cleanUp();
  }

  private void cleanUp() {
    File sharedMetadataFile = FileMetadataUtil.getInstance().getUserMetadataFile(getAppAccount());
    sharedMetadataFile.delete();
    failureCount = 0;
  }

  @Test
  public void testEmpty() {
    UserMetadataManager smm =
        new UserMetadataManager(getContext(), getAppAccount(), getFailureFailsListener());
    assertThat(smm.getExperimentOverview("doesNotExist")).isNull();
    assertThat(smm.getExperimentOverviews(true)).isEmpty();
  }

  @Test
  public void testReadAndWriteSingle() {
    UserMetadataManager smm =
        new UserMetadataManager(getContext(), getAppAccount(), getFailureFailsListener());
    com.google.android.apps.forscience.whistlepunk.metadata.GoosciUserMetadata.ExperimentOverview
            .Builder
        overview = ExperimentOverview.newBuilder().setExperimentId("expId").setLastUsedTimeMs(42);
    smm.addExperimentOverview(ExperimentOverviewPojo.fromProto(overview.build()));
    assertThat(smm.getExperimentOverview("expId").getLastUsedTimeMs()).isEqualTo(42);

    ExperimentOverview overviewUpdated = overview.clone().setLastUsedTimeMs(84).build();
    smm.updateExperimentOverview(ExperimentOverviewPojo.fromProto(overviewUpdated));
    assertThat(smm.getExperimentOverview("expId").getLastUsedTimeMs()).isEqualTo(84);
  }

  @Test
  public void testReadAndWriteMultiple() {
    UserMetadataManager smm =
        new UserMetadataManager(getContext(), getAppAccount(), getFailureFailsListener());
    ExperimentOverview first =
        ExperimentOverview.newBuilder().setExperimentId("exp1").setLastUsedTimeMs(1).build();
    smm.addExperimentOverview(ExperimentOverviewPojo.fromProto(first));
    com.google.android.apps.forscience.whistlepunk.metadata.GoosciUserMetadata.ExperimentOverview
            .Builder
        second = ExperimentOverview.newBuilder().setExperimentId("exp2").setLastUsedTimeMs(2);
    smm.addExperimentOverview(ExperimentOverviewPojo.fromProto(second.build()));
    ExperimentOverview third =
        ExperimentOverview.newBuilder().setExperimentId("exp3").setLastUsedTimeMs(3).build();
    smm.addExperimentOverview(ExperimentOverviewPojo.fromProto(third));

    // All are unarchived
    assertThat(smm.getExperimentOverviews(false)).hasSize(3);

    // Archive one
    ExperimentOverview secondUpdated = second.clone().setIsArchived(true).build();
    smm.updateExperimentOverview(ExperimentOverviewPojo.fromProto(secondUpdated));

    assertThat(smm.getExperimentOverviews(false)).hasSize(2);

    // Check delete works properly
    smm.deleteExperimentOverview(second.getExperimentId());
    assertThat(smm.getExperimentOverviews(true)).hasSize(2);
  }

  @Test
  public void testUpgrade() {
    // This test is not very interesting but more can be added as upgrades get more complex.
    UserMetadataManager smm =
        new UserMetadataManager(getContext(), getAppAccount(), getFailureFailsListener());
    UserMetadata proto =
        GoosciUserMetadata.UserMetadata.newBuilder().setVersion(0).setMinorVersion(0).build();
    UserMetadataPojo pojo = UserMetadataPojo.fromProto(proto);
    smm.upgradeUserMetadataVersionIfNeeded(pojo, 1, 1);
    assertThat(pojo.getVersion()).isEqualTo(1);
    assertThat(pojo.getMinorVersion()).isEqualTo(1);
  }

  @Test
  public void testNoUpgrade() {
    UserMetadataManager smm =
        new UserMetadataManager(getContext(), getAppAccount(), getFailureFailsListener());
    UserMetadata proto =
        GoosciUserMetadata.UserMetadata.newBuilder().setVersion(1).setMinorVersion(1).build();
    UserMetadataPojo pojo = UserMetadataPojo.fromProto(proto);
    smm.upgradeUserMetadataVersionIfNeeded(pojo, 1, 1);
    assertThat(pojo.getVersion()).isEqualTo(1);
    assertThat(pojo.getMinorVersion()).isEqualTo(1);
  }

  @Test
  public void testVersionTooNewThrowsError() {
    UserMetadataManager smm =
        new UserMetadataManager(getContext(), getAppAccount(), getFailureExpectedListener());

    UserMetadata proto =
        GoosciUserMetadata.UserMetadata.newBuilder().setVersion(2).setMinorVersion(0).build();
    UserMetadataPojo pojo = UserMetadataPojo.fromProto(proto);
    smm.upgradeUserMetadataVersionIfNeeded(pojo, 1, 1);
    assertThat(failureCount).isEqualTo(1);
  }

  @Test
  public void testOnlyUpgradesMinorVersion() {
    UserMetadataManager smm =
        new UserMetadataManager(getContext(), getAppAccount(), getFailureFailsListener());
    UserMetadata proto =
        GoosciUserMetadata.UserMetadata.newBuilder().setVersion(1).setMinorVersion(0).build();
    UserMetadataPojo pojo = UserMetadataPojo.fromProto(proto);
    smm.upgradeUserMetadataVersionIfNeeded(pojo, 1, 1);
    assertThat(pojo.getVersion()).isEqualTo(1);
    assertThat(pojo.getMinorVersion()).isEqualTo(1);
  }

  @Test
  public void testCantWriteNewerVersion() {
    UserMetadataManager smm =
        new UserMetadataManager(getContext(), getAppAccount(), getFailureExpectedListener());
    GoosciUserMetadata.UserMetadata proto = GoosciUserMetadata.UserMetadata.getDefaultInstance();
    UserMetadataPojo pojo = UserMetadataPojo.fromProto(proto);
    smm.upgradeUserMetadataVersionIfNeeded(pojo, 100, 0);
    assertThat(failureCount).isEqualTo(1);
  }

  @Test
  public void testAddAndRemoveMyDevice() {
    UserMetadataManager smm =
        new UserMetadataManager(getContext(), getAppAccount(), getFailureExpectedListener());
    DeviceSpec deviceProto =
        GoosciDeviceSpec.DeviceSpec.newBuilder()
            .setInfo(GoosciGadgetInfo.GadgetInfo.getDefaultInstance())
            .setName("Name")
            .build();
    DeviceSpecPojo device = DeviceSpecPojo.fromProto(deviceProto);
    assertThat(smm.getMyDevices()).isEmpty();
    smm.addMyDevice(device);
    assertThat(smm.getMyDevices()).hasSize(1);
    smm.addMyDevice(device);
    assertThat(smm.getMyDevices()).hasSize(1);
    smm.removeMyDevice(device);
    assertThat(smm.getMyDevices()).isEmpty();
    smm.removeMyDevice(device);
    assertThat(smm.getMyDevices()).isEmpty();
  }

  private Context getContext() {
    return RuntimeEnvironment.application.getApplicationContext();
  }

  private AppAccount getAppAccount() {
    return NonSignedInAccount.getInstance(getContext());
  }
}
