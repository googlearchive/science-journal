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
import static junit.framework.Assert.assertTrue;
import static junit.framework.Assert.fail;

import android.content.Context;

import com.google.android.apps.forscience.whistlepunk.BuildConfig;
import com.google.android.apps.forscience.whistlepunk.analytics.UsageTracker;
import com.google.android.apps.forscience.whistlepunk.metadata.GoosciUserMetadata;

import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.io.File;
import java.io.IOException;

/**
 * Tests for the ProtoFileHelper class.
 */
@RunWith(RobolectricTestRunner.class)
@Config(constants = BuildConfig.class)
public class ProtoFileHelperTest {
    private Context getContext() {
        return RuntimeEnvironment.application.getApplicationContext();
    }

    private File getFile() {
        return new File(getContext().getFilesDir() + "/file");
    }

    @After
    public void cleanUp() {
        getFile().delete();
    }

    @Test
    public void testWriteAndRead() {
        File file = getFile();
        try {
            file.createNewFile();
        } catch (IOException ex) {
            fail();
        }

        GoosciUserMetadata.UserMetadata metadata = new GoosciUserMetadata.UserMetadata();
        metadata.version = 42;
        ProtoFileHelper<GoosciUserMetadata.UserMetadata> helper = new ProtoFileHelper<>();
        boolean success = helper.writeToFile(file, metadata, null);
        assertTrue(success);

        GoosciUserMetadata.UserMetadata result =
                helper.readFromFile(file, GoosciUserMetadata.UserMetadata::parseFrom, null);
        assertEquals(42, result.version);
    }

    @Test
    public void testWriteFailsRevertsToPrevious() {
        File file = getFile();
        try {
            file.createNewFile();
        } catch (IOException ex) {
            fail();
        }

        GoosciUserMetadata.UserMetadata metadata = new GoosciUserMetadata.UserMetadata();
        metadata.version = 42;
        ProtoFileHelper<GoosciUserMetadata.UserMetadata> helper = new ProtoFileHelper<>();
        boolean success = helper.writeToFile(file, metadata, null);
        assertTrue(success);

        metadata.version = 64;
        // Fails to write a proto and the old version is put back
        success = helper.writeToFile(file, metadata, true, UsageTracker.STUB);
        assertFalse(success);

        // The old version should still be available.
        GoosciUserMetadata.UserMetadata result =
                helper.readFromFile(file, GoosciUserMetadata.UserMetadata::parseFrom, null);
        assertEquals(42, result.version);
    }
}
