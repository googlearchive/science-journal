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

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Represents a set of files that have to be uploaded or downloaded to cloud storage following an
 * experiment merge.
 */
public class FileSyncCollection {

  private final Set<String> imageUploads;
  private final Set<String> imageDownloads;

  private final Set<String> trialUploads;
  private final Set<String> trialDownloads;

  public FileSyncCollection() {
    imageUploads = new LinkedHashSet<>();
    imageDownloads = new LinkedHashSet<>();

    trialUploads = new LinkedHashSet<>();
    trialDownloads = new LinkedHashSet<>();
  }

  public Set<String> getImageUploads() {
    return imageUploads;
  }

  public Set<String> getImageDownloads() {
    return imageDownloads;
  }

  public Set<String> getTrialUploads() {
    return trialUploads;
  }

  public Set<String> getTrialDownloads() {
    return trialDownloads;
  }

  public void addImageUpload(String imagePath) {
    imageUploads.add(imagePath);
  }

  public void addImageDownload(String imagePath) {
    imageDownloads.add(imagePath);
  }

  public void addTrialUpload(String trialId) {
    trialUploads.add(trialId);
  }

  public void addTrialDownload(String trialId) {
    trialDownloads.add(trialId);
  }
}
