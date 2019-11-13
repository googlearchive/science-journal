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
import static org.junit.Assert.assertNotNull;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

/** Tests for the Experiment class which involve labels, which use Parcelable. */
@RunWith(RobolectricTestRunner.class)
public class FileSyncCollectionTest {

  @Test
  public void testGetters() {
    FileSyncCollection collection = new FileSyncCollection();

    assertNotNull(collection.getImageDownloads());
    assertNotNull(collection.getImageUploads());
    assertNotNull(collection.getTrialDownloads());
    assertNotNull(collection.getTrialUploads());
  }

  @Test
  public void testSetters() {
    FileSyncCollection collection = new FileSyncCollection();

    assertEquals(0, collection.getImageDownloads().size());
    collection.addImageDownload("foo");
    assertEquals(1, collection.getImageDownloads().size());
    collection.addImageDownload("foo");
    assertEquals(1, collection.getImageDownloads().size());
    collection.addImageDownload("bar");
    assertEquals(2, collection.getImageDownloads().size());

    assertEquals(0, collection.getImageUploads().size());
    collection.addImageUpload("foo");
    assertEquals(1, collection.getImageUploads().size());
    collection.addImageUpload("foo");
    assertEquals(1, collection.getImageUploads().size());
    collection.addImageUpload("bar");
    assertEquals(2, collection.getImageUploads().size());

    assertEquals(0, collection.getTrialDownloads().size());
    collection.addTrialDownload("foo");
    assertEquals(1, collection.getTrialDownloads().size());
    collection.addTrialDownload("foo");
    assertEquals(1, collection.getTrialDownloads().size());
    collection.addTrialDownload("bar");
    assertEquals(2, collection.getTrialDownloads().size());

    assertEquals(0, collection.getTrialUploads().size());
    collection.addTrialUpload("foo");
    assertEquals(1, collection.getTrialUploads().size());
    collection.addTrialUpload("foo");
    assertEquals(1, collection.getTrialUploads().size());
    collection.addTrialUpload("bar");
    assertEquals(2, collection.getTrialUploads().size());
  }
}
