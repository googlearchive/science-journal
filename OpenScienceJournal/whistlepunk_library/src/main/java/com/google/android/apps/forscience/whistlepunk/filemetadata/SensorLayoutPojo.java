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

import com.google.android.apps.forscience.whistlepunk.data.GoosciSensorLayout.SensorLayout;
import com.google.android.apps.forscience.whistlepunk.data.GoosciSensorLayout.SensorLayout.CardView;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Wraps a SensorLayout proto */
public final class SensorLayoutPojo {

  private CardView cardView = CardView.METER;
  private String sensorId = "";
  private boolean audioEnabled;
  private boolean showStatsOverlay;
  private int color;
  private final Map<String, String> extras = new HashMap<>();
  private double minimumYAxisValue;
  private double maximumYAxisValue;
  private Set<String> activeSensorTriggerIds = new HashSet<>();
  private int colorIndex;

  public SensorLayoutPojo() {}

  public SensorLayoutPojo(SensorLayoutPojo source) {
    setCardView(source.getCardView());
    setSensorId(source.getSensorId());
    setAudioEnabled(source.isAudioEnabled());
    setShowStatsOverlay(source.isShowStatsOverlay());
    setColor(source.getColor());
    setExtras(source.getExtras());
    setMinimumYAxisValue(source.getMinimumYAxisValue());
    setMaximumYAxisValue(source.getMaximumYAxisValue());
    setActiveSensorTriggerIds(source.getActiveSensorTriggerIds());
    setColorIndex(source.getColorIndex());
  }

  public CardView getCardView() {
    return cardView;
  }

  public void setCardView(CardView cardView) {
    this.cardView = cardView;
  }

  public String getSensorId() {
    return sensorId;
  }

  public void setSensorId(String sensorId) {
    this.sensorId = sensorId;
  }

  public boolean isAudioEnabled() {
    return audioEnabled;
  }

  public void setAudioEnabled(boolean audioEnabled) {
    this.audioEnabled = audioEnabled;
  }

  public boolean isShowStatsOverlay() {
    return showStatsOverlay;
  }

  public void setShowStatsOverlay(boolean showStatsOverlay) {
    this.showStatsOverlay = showStatsOverlay;
  }

  public int getColor() {
    return color;
  }

  public void setColor(int color) {
    this.color = color;
  }

  public Map<String, String> getExtras() {
    return extras;
  }

  public void setExtras(Map<String, String> extras) {
    this.extras.clear();
    this.extras.putAll(extras);
  }

  public void putAllExtras(Map<String, String> extras) {
    this.extras.putAll(extras);
  }

  public double getMinimumYAxisValue() {
    return minimumYAxisValue;
  }

  public void setMinimumYAxisValue(double minimumYAxisValue) {
    this.minimumYAxisValue = minimumYAxisValue;
  }

  public double getMaximumYAxisValue() {
    return maximumYAxisValue;
  }

  public void setMaximumYAxisValue(double maximumYAxisValue) {
    this.maximumYAxisValue = maximumYAxisValue;
  }

  public Set<String> getActiveSensorTriggerIds() {
    return activeSensorTriggerIds;
  }

  public void setActiveSensorTriggerIds(Set<String> activeSensorTriggerIds) {
    this.activeSensorTriggerIds = activeSensorTriggerIds;
  }

  public void removeTrigger(String triggerId) {
    activeSensorTriggerIds.remove(triggerId);
  }

  public int getColorIndex() {
    return colorIndex;
  }

  public void setColorIndex(int colorIndex) {
    this.colorIndex = colorIndex;
  }

  public void addActiveTriggerId(String trigger) {
    activeSensorTriggerIds.add(trigger);
  }

  public void clearActiveTriggerIds() {
    activeSensorTriggerIds.clear();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    SensorLayoutPojo that = (SensorLayoutPojo) o;
    return audioEnabled == that.audioEnabled
        && showStatsOverlay == that.showStatsOverlay
        && color == that.color
        && Double.compare(that.minimumYAxisValue, minimumYAxisValue) == 0
        && Double.compare(that.maximumYAxisValue, maximumYAxisValue) == 0
        && colorIndex == that.colorIndex
        && cardView == that.cardView
        && Objects.equals(sensorId, that.sensorId)
        && Objects.equals(extras, that.extras)
        && Objects.equals(activeSensorTriggerIds, that.activeSensorTriggerIds);
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        cardView,
        sensorId,
        audioEnabled,
        showStatsOverlay,
        color,
        extras,
        minimumYAxisValue,
        maximumYAxisValue,
        activeSensorTriggerIds,
        colorIndex);
  }

  public static SensorLayoutPojo fromProto(SensorLayout proto) {
    if (proto == null) {
      return null;
    }
    SensorLayoutPojo pojo = new SensorLayoutPojo();
    pojo.setCardView(proto.getCardView());
    pojo.setSensorId(proto.getSensorId());
    pojo.setAudioEnabled(proto.getAudioEnabled());
    pojo.setShowStatsOverlay(proto.getShowStatsOverlay());
    pojo.setColor(proto.getColor());
    pojo.setMinimumYAxisValue(proto.getMinimumYAxisValue());
    pojo.setMaximumYAxisValue(proto.getMaximumYAxisValue());
    pojo.setColorIndex(proto.getColorIndex());
    pojo.setExtras(proto.getExtrasMap());
    if (proto.getActiveSensorTriggerIdsCount() != 0) {
      pojo.setActiveSensorTriggerIds(new HashSet<String>(proto.getActiveSensorTriggerIdsList()));
    }
    return pojo;
  }

  public SensorLayout toProto() {
    return SensorLayout.newBuilder()
        .setCardView(cardView)
        .setSensorId(sensorId)
        .setAudioEnabled(audioEnabled)
        .setShowStatsOverlay(showStatsOverlay)
        .setColor(color)
        .setMinimumYAxisValue(minimumYAxisValue)
        .setMaximumYAxisValue(maximumYAxisValue)
        .setColorIndex(colorIndex)
        .putAllExtras(extras)
        .addAllActiveSensorTriggerIds(
            Arrays.asList(
                activeSensorTriggerIds.toArray(new String[activeSensorTriggerIds.size()])))
        .build();
  }
}
