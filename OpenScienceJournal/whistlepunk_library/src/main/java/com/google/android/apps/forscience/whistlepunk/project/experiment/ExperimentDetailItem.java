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

package com.google.android.apps.forscience.whistlepunk.project.experiment;

import com.google.android.apps.forscience.whistlepunk.filemetadata.Label;
import com.google.android.apps.forscience.whistlepunk.filemetadata.SensorLayoutPojo;
import com.google.android.apps.forscience.whistlepunk.filemetadata.Trial;
import com.google.android.apps.forscience.whistlepunk.metadata.GoosciLabel;
import com.google.android.apps.forscience.whistlepunk.scalarchart.ChartController;
import com.google.android.apps.forscience.whistlepunk.scalarchart.ChartOptions;
import com.google.android.apps.forscience.whistlepunk.scalarchart.ScalarDisplayOptions;

/**
 * Represents a detail item: either a run or an experiment level label or a special card.
 *
 * <p>TODO: might be able to rework this when Run objects exist.
 */
public class ExperimentDetailItem {
  private final int viewType;
  private Trial trial;
  private int sensorTagIndex = -1;
  private Label label;
  private long timestamp;
  private ChartController chartController;

  ExperimentDetailItem(
      Trial trial, ScalarDisplayOptions scalarDisplayOptions, boolean isRecording) {
    this.trial = trial;
    timestamp = this.trial.getFirstTimestamp();
    if (isRecording) {
      viewType = ExperimentDetailsFragment.DetailsAdapter.VIEW_TYPE_RECORDING;
    } else {
      viewType = ExperimentDetailsFragment.DetailsAdapter.VIEW_TYPE_RUN_CARD;
    }
    sensorTagIndex = this.trial.getSensorIds().size() > 0 ? 0 : -1;
    if (trial.isValid()) {
      chartController =
          new ChartController(
              ChartOptions.ChartPlacementType.TYPE_PREVIEW_REVIEW, scalarDisplayOptions);
    }
  }

  ExperimentDetailItem(Label label) {
    this.label = label;
    if (label.getType() == GoosciLabel.Label.ValueType.TEXT) {
      viewType = ExperimentDetailsFragment.DetailsAdapter.VIEW_TYPE_EXPERIMENT_TEXT_LABEL;
    } else if (label.getType() == GoosciLabel.Label.ValueType.PICTURE) {
      viewType = ExperimentDetailsFragment.DetailsAdapter.VIEW_TYPE_EXPERIMENT_PICTURE_LABEL;
    } else if (label.getType() == GoosciLabel.Label.ValueType.SENSOR_TRIGGER) {
      viewType = ExperimentDetailsFragment.DetailsAdapter.VIEW_TYPE_EXPERIMENT_TRIGGER_LABEL;
    } else if (label.getType() == GoosciLabel.Label.ValueType.SNAPSHOT) {
      viewType = ExperimentDetailsFragment.DetailsAdapter.VIEW_TYPE_SNAPSHOT_LABEL;
    } else if (label.getType() == GoosciLabel.Label.ValueType.SKETCH) {
      viewType = ExperimentDetailsFragment.DetailsAdapter.VIEW_TYPE_SKETCH;
    } else {
      viewType = ExperimentDetailsFragment.DetailsAdapter.VIEW_TYPE_UNKNOWN_LABEL;
    }
    timestamp = label.getTimeStamp();
  }

  ExperimentDetailItem(int viewType) {
    this.viewType = viewType;
  }

  int getViewType() {
    return viewType;
  }

  long getTimestamp() {
    return timestamp;
  }

  Trial getTrial() {
    return trial;
  }

  int getSensorTagIndex() {
    return sensorTagIndex;
  }

  SensorLayoutPojo getSelectedSensorLayout() {
    return trial.getSensorLayouts().get(sensorTagIndex);
  }

  String getNextSensorId() {
    return trial.getSensorIds().get(sensorTagIndex + 1);
  }

  String getPrevSensorId() {
    return trial.getSensorIds().get(sensorTagIndex - 1);
  }

  void setSensorTagIndex(int index) {
    sensorTagIndex = index;
  }

  ChartController getChartController() {
    return chartController;
  }

  public Label getLabel() {
    return label;
  }

  public void setLabel(Label label) {
    this.label = label;
  }

  public void setTrial(Trial trial) {
    this.trial = trial;
  }
}
