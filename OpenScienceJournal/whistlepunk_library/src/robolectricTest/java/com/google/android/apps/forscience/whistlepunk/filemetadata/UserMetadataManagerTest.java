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
import static org.junit.Assert.assertNull;

import android.content.Context;

import com.google.android.apps.forscience.whistlepunk.BuildConfig;
import com.google.android.apps.forscience.whistlepunk.data.GoosciDeviceSpec;
import com.google.android.apps.forscience.whistlepunk.data.GoosciGadgetInfo;
import com.google.android.apps.forscience.whistlepunk.metadata.GoosciUserMetadata;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.io.File;

/**
 * Tests for SharedMetadatamanager
 */
@RunWith(RobolectricTestRunner.class)
@Config(constants = BuildConfig.class)
public class UserMetadataManagerTest {
    private int mFailureCount = 0;

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
                mFailureCount++;
            }

            @Override
            public void onReadFailed() {
                mFailureCount++;
            }

            @Override
            public void onNewerVersionDetected() {
                mFailureCount++;
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
        File sharedMetadataFile = FileMetadataManager.getUserMetadataFile(
                getContext());
        sharedMetadataFile.delete();
        mFailureCount = 0;
    }


    @Test
    public void testEmpty() {
        UserMetadataManager smm = new UserMetadataManager(getContext(),
                getFailureFailsListener());
        assertNull(smm.getExperimentOverview("doesNotExist"));
        assertEquals(0, smm.getExperimentOverviews(true).size());
    }

    @Test
    public void testReadAndWriteSingle() {
        UserMetadataManager smm = new UserMetadataManager(getContext(),
                getFailureFailsListener());
        GoosciUserMetadata.ExperimentOverview overview =
                new GoosciUserMetadata.ExperimentOverview();
        overview.experimentId = "expId";
        overview.lastUsedTimeMs = 42;
        smm.addExperimentOverview(overview);
        assertEquals(smm.getExperimentOverview("expId").lastUsedTimeMs, 42);
        overview.lastUsedTimeMs = 84;
        smm.updateExperimentOverview(overview);
        assertEquals(smm.getExperimentOverview("expId").lastUsedTimeMs, 84);
    }

    @Test
    public void testReadAndWriteMultiple() {
        UserMetadataManager smm = new UserMetadataManager(getContext(),
                getFailureFailsListener());
        GoosciUserMetadata.ExperimentOverview first =
                new GoosciUserMetadata.ExperimentOverview();
        first.experimentId = "exp1";
        first.lastUsedTimeMs = 1;
        smm.addExperimentOverview(first);
        GoosciUserMetadata.ExperimentOverview second =
                new GoosciUserMetadata.ExperimentOverview();
        second.experimentId = "exp2";
        second.lastUsedTimeMs = 2;
        smm.addExperimentOverview(second);
        GoosciUserMetadata.ExperimentOverview third =
                new GoosciUserMetadata.ExperimentOverview();
        third.experimentId = "exp3";
        third.lastUsedTimeMs = 3;
        smm.addExperimentOverview(third);

        // All are unarchived
        assertEquals(smm.getExperimentOverviews(false).size(), 3);

        // Archive one
        second.isArchived = true;
        smm.updateExperimentOverview(second);

        assertEquals(smm.getExperimentOverviews(false).size(), 2);

        // Check delete works properly
        smm.deleteExperimentOverview(second.experimentId);
        assertEquals(smm.getExperimentOverviews(true).size(), 2);
    }

    @Test
    public void testUpgrade() {
        // This test is not very interesting but more can be added as upgrades get more complex.
        UserMetadataManager smm = new UserMetadataManager(getContext(),
                getFailureFailsListener());
        GoosciUserMetadata.UserMetadata proto = new GoosciUserMetadata.UserMetadata();
        proto.version = 0;
        proto.minorVersion = 0;
        smm.upgradeUserMetadataVersionIfNeeded(proto, 1, 1);
        assertEquals(proto.version, 1);
        assertEquals(proto.minorVersion, 1);
    }

    @Test
    public void testNoUpgrade() {
        UserMetadataManager smm = new UserMetadataManager(getContext(),
                getFailureFailsListener());
        GoosciUserMetadata.UserMetadata proto = new GoosciUserMetadata.UserMetadata();
        proto.version = 1;
        proto.minorVersion = 1;
        smm.upgradeUserMetadataVersionIfNeeded(proto, 1, 1);
        assertEquals(proto.version, 1);
        assertEquals(proto.minorVersion, 1);
    }

    @Test
    public void testVersionTooNewThrowsError() {
        UserMetadataManager smm = new UserMetadataManager(getContext(),
                getFailureExpectedListener());

        GoosciUserMetadata.UserMetadata proto = new GoosciUserMetadata.UserMetadata();
        proto.version = 2;
        proto.minorVersion = 0;
        smm.upgradeUserMetadataVersionIfNeeded(proto, 1, 1);
        assertEquals(1, mFailureCount);
    }

    @Test
    public void testOnlyUpgradesMinorVersion() {
        UserMetadataManager smm = new UserMetadataManager(getContext(),
                getFailureFailsListener());
        GoosciUserMetadata.UserMetadata proto = new GoosciUserMetadata.UserMetadata();
        proto.version = 1;
        proto.minorVersion = 0;
        smm.upgradeUserMetadataVersionIfNeeded(proto, 1, 1);
        assertEquals(proto.version, 1);
        assertEquals(proto.minorVersion, 1);
    }

    @Test
    public void testCantWriteNewerVersion() {
        UserMetadataManager smm = new UserMetadataManager(getContext(),
                getFailureExpectedListener());
        GoosciUserMetadata.UserMetadata proto = new GoosciUserMetadata.UserMetadata();
        smm.upgradeUserMetadataVersionIfNeeded(proto, 100, 0);
        assertEquals(1, mFailureCount);
    }

    @Test
    public void testAddAndRemoveMyDevice() {
        UserMetadataManager smm = new UserMetadataManager(getContext(),
                getFailureExpectedListener());
        GoosciDeviceSpec.DeviceSpec device = new GoosciDeviceSpec.DeviceSpec();
        device.info = new GoosciGadgetInfo.GadgetInfo();
        device.name = "Name";
        assertEquals(0, smm.getMyDevices().size());
        smm.addMyDevice(device);
        assertEquals(1, smm.getMyDevices().size());
        smm.addMyDevice(device);
        assertEquals(1, smm.getMyDevices().size());
        smm.removeMyDevice(device);
        assertEquals(0, smm.getMyDevices().size());
        smm.removeMyDevice(device);
        assertEquals(0, smm.getMyDevices().size());
    }

    private Context getContext() {
        return RuntimeEnvironment.application.getApplicationContext();
    }
}
