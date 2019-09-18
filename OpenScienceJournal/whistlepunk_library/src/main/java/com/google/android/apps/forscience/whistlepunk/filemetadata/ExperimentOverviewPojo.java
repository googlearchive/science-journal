/*
 *  Copyright 2019 Google Inc. All Rights Reserved.
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

import com.google.android.apps.forscience.whistlepunk.metadata.nano.GoosciUserMetadata;
import com.google.android.apps.forscience.whistlepunk.metadata.nano.GoosciUserMetadata.ExperimentOverview;
import com.google.protobuf.migration.nano2lite.runtime.MigrateAs;
import com.google.protobuf.migration.nano2lite.runtime.MigrateAs.Destination;
import java.util.Objects;

/** Metadata about an experiment, used to render most of the Experiment List view.. */
public class ExperimentOverviewPojo {

  private long lastUsedTimeMs;
  private boolean isArchived;
  private String experimentId = "";
  private int colorIndex;
  private String imagePath = "";
  private int trialCount;
  private String title = "";

  public String getTitle() {
    return title;
  }

  public void setTitle(String title) {
    this.title = title;
  }

  public String getImagePath() {
    return imagePath;
  }

  public void setImagePath(String imagePath) {
    this.imagePath = imagePath;
  }

  public int getTrialCount() {
    return trialCount;
  }

  public void setTrialCount(int trialCount) {
    this.trialCount = trialCount;
  }

  public boolean isArchived() {
    return isArchived;
  }

  public void setArchived(boolean archived) {
    isArchived = archived;
  }

  public String getExperimentId() {
    return experimentId;
  }

  public void setExperimentId(String experimentId) {
    this.experimentId = experimentId;
  }

  public int getColorIndex() {
    return colorIndex;
  }

  public void setColorIndex(int colorIndex) {
    this.colorIndex = colorIndex;
  }

  public long getLastUsedTimeMs() {
    return lastUsedTimeMs;
  }

  public void setLastUsedTimeMs(long lastUsedTimeMs) {
    this.lastUsedTimeMs = lastUsedTimeMs;
  }

  public ExperimentOverview toProto() {
    @MigrateAs(Destination.BUILDER)
    ExperimentOverview proto = new ExperimentOverview();
    proto.colorIndex = colorIndex;
    proto.experimentId = experimentId;
    proto.lastUsedTimeMs = lastUsedTimeMs;
    proto.isArchived = isArchived;
    proto.imagePath = imagePath;
    proto.trialCount = trialCount;
    proto.title = title;
    return proto;
  }

  public static ExperimentOverviewPojo fromProto(GoosciUserMetadata.ExperimentOverview proto) {
    if (proto == null) {
      return null;
    }
    ExperimentOverviewPojo pojo = new ExperimentOverviewPojo();
    pojo.setArchived(proto.isArchived);
    pojo.setColorIndex(proto.colorIndex);
    pojo.setExperimentId(proto.experimentId);
    pojo.setImagePath(proto.imagePath);
    pojo.setTrialCount(proto.trialCount);
    pojo.setLastUsedTimeMs(proto.lastUsedTimeMs);
    pojo.setTitle(proto.title);
    return pojo;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    ExperimentOverviewPojo that = (ExperimentOverviewPojo) o;
    return lastUsedTimeMs == that.lastUsedTimeMs
        && isArchived == that.isArchived
        && colorIndex == that.colorIndex
        && trialCount == that.trialCount
        && Objects.equals(experimentId, that.experimentId)
        && Objects.equals(imagePath, that.imagePath)
        && Objects.equals(title, that.title);
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        lastUsedTimeMs, isArchived, experimentId, colorIndex, imagePath, trialCount, title);
  }
}
