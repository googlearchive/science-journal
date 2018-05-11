/*
 *  Copyright 2016 Google Inc. All Rights Reserved.
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

package com.google.android.apps.forscience.whistlepunk.sensorapi;

import com.google.android.apps.forscience.whistlepunk.RecordingDataController;

/**
 * Stores data at multiple granularities. For each run of N*2 data points in tier X, there are 2
 * data points in tier X+1, and those are the max and min data points over that run.
 *
 * <p>This seems to allow us to capture the general shape of the graph better than trying to, for
 * example, synthesize an "average" data point for the run.
 */
public class ZoomRecorder {
  /**
   * Statistics key for the number of resolution tiers that have stored data in the Database for the
   * current run.
   */
  public static final String STATS_KEY_TIER_COUNT = "stats_tier_count";

  /**
   * Statistics key for the ratio of data points between resolution tiers. For example, if this is
   * 10, then for every 10 data points in resolution tier N, there's 1 in tier N+1.
   */
  public static final String STATS_KEY_ZOOM_LEVEL_BETWEEN_TIERS = "stats_zoom_level";

  private final String sensorId;
  private final int zoomBufferSize;
  private final int tier;

  private String trialId = null;
  private int seenThisPass = 0;
  private long timestampOfMinSeen;
  private double valueOfMinSeen;
  private long timestampOfMaxSeen;
  private double valueOfMaxSeen;
  private ZoomRecorder nextTierUp = null;

  /**
   * @param zoomBufferSize how many data points we can store before sending summary data points to
   *     the next tier up. Note that since we send 2 summary points per buffer, (max and min), each
   *     tier will hold (2 / zoomBufferSize) as many data points as the next tier down.
   */
  public ZoomRecorder(String id, int zoomBufferSize, int tier) {
    sensorId = id;
    this.tier = tier;
    this.zoomBufferSize = zoomBufferSize;
    resetBuffer();
  }

  public void clear() {
    nextTierUp = null;
    resetBuffer();
  }

  public void clearTrialId() {
    trialId = null;
    if (nextTierUp != null) {
      nextTierUp.clearTrialId();
    }
  }

  public void setTrialId(String trialId) {
    this.trialId = trialId;
    if (nextTierUp != null) {
      nextTierUp.setTrialId(trialId);
    }
  }

  private void resetBuffer() {
    seenThisPass = 0;
    valueOfMinSeen = Double.MAX_VALUE;
    valueOfMaxSeen = -Double.MAX_VALUE;
    timestampOfMaxSeen = timestampOfMinSeen = -1;
  }

  public void addData(long timestampMillis, double value, RecordingDataController dc) {
    seenThisPass++;
    if (value > valueOfMaxSeen) {
      valueOfMaxSeen = value;
      timestampOfMaxSeen = timestampMillis;
    }
    if (value < valueOfMinSeen) {
      valueOfMinSeen = value;
      timestampOfMinSeen = timestampMillis;
    }
    if (seenThisPass == zoomBufferSize) {
      flush(dc);
    }
  }

  private void addReadingAtThisTier(RecordingDataController dc, long timestamp, double value) {
    dc.addScalarReading(trialId, sensorId, tier, timestamp, value);
    getNextTierUp().addData(timestamp, value, dc);
  }

  private ZoomRecorder getNextTierUp() {
    if (nextTierUp == null) {
      nextTierUp = new ZoomRecorder(sensorId, zoomBufferSize, tier + 1);
      nextTierUp.setTrialId(trialId);
    }
    return nextTierUp;
  }

  public int countTiers() {
    if (nextTierUp == null) {
      // If we don't have a parent, then we haven't written anything at our level yet, so
      // the total count is our tier number
      return tier;
    } else {
      return nextTierUp.countTiers();
    }
  }

  public void flushAllTiers(RecordingDataController dc) {
    if (nextTierUp != null) {
      nextTierUp.flushAllTiers(dc);
      nextTierUp = null;
    }
    flush(dc);
  }

  public void flush(RecordingDataController dc) {
    if (seenThisPass > 0) {
      // order of adding data to DB doesn't matter
      addReadingAtThisTier(dc, timestampOfMinSeen, valueOfMinSeen);
      addReadingAtThisTier(dc, timestampOfMaxSeen, valueOfMaxSeen);
      resetBuffer();
    }
  }
}
