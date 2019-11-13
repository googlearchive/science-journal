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

package com.google.android.apps.forscience.whistlepunk.scalarchart;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.PointF;
import androidx.annotation.VisibleForTesting;
import android.view.View;
import android.view.ViewTreeObserver;
import android.widget.ProgressBar;
import com.google.android.apps.forscience.javalib.FailureListener;
import com.google.android.apps.forscience.whistlepunk.Clock;
import com.google.android.apps.forscience.whistlepunk.CurrentTimeClock;
import com.google.android.apps.forscience.whistlepunk.DataController;
import com.google.android.apps.forscience.whistlepunk.ExternalAxisController;
import com.google.android.apps.forscience.whistlepunk.GraphPopulator;
import com.google.android.apps.forscience.whistlepunk.LoggingConsumer;
import com.google.android.apps.forscience.whistlepunk.R;
import com.google.android.apps.forscience.whistlepunk.filemetadata.Label;
import com.google.android.apps.forscience.whistlepunk.filemetadata.SensorLayoutPojo;
import com.google.android.apps.forscience.whistlepunk.filemetadata.SensorTrigger;
import com.google.android.apps.forscience.whistlepunk.filemetadata.Trial;
import com.google.android.apps.forscience.whistlepunk.filemetadata.TrialStats;
import com.google.android.apps.forscience.whistlepunk.review.ZoomPresenter;
import com.google.android.apps.forscience.whistlepunk.sensorapi.StreamStat;
import com.google.android.apps.forscience.whistlepunk.sensordb.ScalarReadingList;
import com.google.android.apps.forscience.whistlepunk.wireapi.RecordingMetadata;
import com.google.common.base.Preconditions;
import com.google.common.collect.Range;
import java.util.ArrayList;
import java.util.List;

public class ChartController {
  /**
   * Interface for chart loading status object. Things that want to load chart data must pass in an
   * object that implements this interface, which is used to track the status of the load and also
   * whether the data loaded matches the requested data to load. Mis-matches may occur if many load
   * requests are issued to the same ChartController very quickly, which may happen when a user
   * quickly scrolls through sensors in a run or scrolls through a list of runs in an experiment.
   */
  public interface ChartLoadingStatus {
    int GRAPH_LOAD_STATUS_IDLE = 0;
    int GRAPH_LOAD_STATUS_LOADING = 1;

    int getGraphLoadStatus();

    void setGraphLoadStatus(int graphLoadStatus);

    String getRunId();

    String getSensorId();
  }

  public interface ChartDataLoadedCallback {
    void onChartDataLoaded(long firstTimestamp, long lastTimestamp);

    /**
     * Called when the chart starts trying to load more data.
     *
     * @param chartHiddenForLoad True if the graph is hidden for the load, like when a full refresh
     *     is done when loading at a new zoom level. False if the graph is not hidden, like when
     *     loading more data at the edges.
     */
    void onLoadAttemptStarted(boolean chartHiddenForLoad);
  }

  private static final String TAG = "ChartController";

  /**
   * How many (minimum) screenfuls of data should we keep in memory? Note: If this constant is
   * changed, the tests in ScalarSensorTest will need to be updated!
   */
  private static final int KEEP_THIS_MANY_SCREENS = 3;

  /** How long can the screen be off before we forget old data? */
  private static final long MAX_BLACKOUT_MILLIS_BEFORE_CLEARING = 5000;

  private static final long DEFAULT_DATA_LOAD_BUFFER_MILLIS =
      ExternalAxisController.DEFAULT_GRAPH_RANGE_IN_MILLIS / 4;

  private final ChartData chartData;
  private List<Label> displayableLabels = new ArrayList<>();
  private ChartOptions chartOptions;
  private ChartView chartView;
  private ExternalAxisController.InteractionListener interactionListener;
  private ProgressBar progressView;

  // Fields used for data loading and clearing during Observe/Record
  private long defaultGraphRange;
  private long dataLoadBuffer;
  private final FailureListener dataFailureListener;
  private long resetTime = -1;
  private String sensorId;
  private String trialId;

  private ZoomPresenter zoomPresenter;
  // Need to keep track of min/max loaded separately from what is in ChartData,
  // because repeated async callbacks adding data can cause data to be added after
  // that region was meant to be cleared, causing bugs. Therefore minLoadedX and maxLoadedX
  // are used to track the min and max we know we've loaded during RunReview, to assist in
  // data loading during zoom and pan.
  private static final long NOTHING_LOADED = -1;
  private long minLoadedX = NOTHING_LOADED;
  private long maxLoadedX;
  private boolean needsForwardLoad = false;
  private List<Long> currentLoadIds = new ArrayList<>();
  private final Clock uptimeClock;
  private final Clock currentTimeClock;
  private List<ChartDataLoadedCallback> chartDataLoadedCallbacks = new ArrayList<>();

  public ChartController(
      ChartOptions.ChartPlacementType type,
      ScalarDisplayOptions lineGraphOptions,
      Clock uptimeClock) {
    this(
        type,
        lineGraphOptions,
        ChartData.DEFAULT_THROWAWAY_THRESHOLD,
        ChartController.DEFAULT_DATA_LOAD_BUFFER_MILLIS,
        uptimeClock);
  }

  public ChartController(
      ChartOptions.ChartPlacementType chartPlacementType,
      ScalarDisplayOptions scalarDisplayOptions) {
    this(
        chartPlacementType,
        scalarDisplayOptions,
        ChartData.DEFAULT_THROWAWAY_THRESHOLD,
        DEFAULT_DATA_LOAD_BUFFER_MILLIS,
        new UptimeClock());
  }

  @VisibleForTesting
  public ChartController(
      ChartOptions.ChartPlacementType chartPlacementType,
      ScalarDisplayOptions scalarDisplayOptions,
      int chartDataThrowawayThreshold,
      long dataLoadBuffer,
      Clock uptimeClock) {
    this(
        chartPlacementType,
        scalarDisplayOptions,
        chartDataThrowawayThreshold,
        dataLoadBuffer,
        uptimeClock,
        LoggingConsumer.expectSuccess(TAG, "loading readings"));
  }

  @VisibleForTesting
  public ChartController(
      ChartOptions.ChartPlacementType chartPlacementType,
      ScalarDisplayOptions scalarDisplayOptions,
      int chartDataThrowawayThreshold,
      long dataLoadBuffer,
      Clock uptimeClock,
      FailureListener dataFailureListener) {
    this.uptimeClock = uptimeClock;
    chartData =
        new ChartData(chartDataThrowawayThreshold, ChartData.DEFAULT_THROWAWAY_TIME_THRESHOLD);
    chartOptions = new ChartOptions(chartPlacementType);
    chartOptions.setScalarDisplayOptions(scalarDisplayOptions);
    this.dataFailureListener = dataFailureListener;
    defaultGraphRange = ExternalAxisController.DEFAULT_GRAPH_RANGE_IN_MILLIS;
    this.dataLoadBuffer = dataLoadBuffer;
    currentTimeClock = new CurrentTimeClock();
  }

  public void setDefaultGraphRange(long defaultGraphRange) {
    this.defaultGraphRange = defaultGraphRange;
  }

  public void setChartView(ChartView view) {
    chartView = view;
    chartView.clearInteractionListeners();
    if (interactionListener != null) {
      chartView.addInteractionListener(interactionListener);
    }
    chartView.initialize(chartOptions, chartData);
  }

  public void setProgressView(ProgressBar progress) {
    progressView = progress;
  }

  // Adds a single point to the end of the path. Assumes points are ordered as they arrive.
  public void addPoint(ChartData.DataPoint point) {
    // TODO: extract as a testable object
    if (resetTime != -1) {
      if (point.getX() < resetTime) {
        // straggling datapoint from before the reset, ignore
        return;
      } else {
        resetTime = -1;
      }
    }
    if (!chartData.isEmpty()) {
      // Get rid of data too old to be interesting for "now", but too new to be likely
      // seen by scrolling from the current view.  If we're recording, we'll swap
      // this data back in when we scroll to it.  If not, then we have no data
      // retention guarantees.
      // TODO: Is it possible to call throwAwayBetween less frequently for performance?
      // no need to do so many binary searches in ChartData...
      // TODO: This throwAwayBetween is causing b/28614204.
      long throwawayBefore = point.getX() - (KEEP_THIS_MANY_SCREENS * defaultGraphRange);
      long throwawayAfter = chartOptions.getRenderedXMax() + defaultGraphRange;
      chartData.throwAwayBetween(throwawayAfter, throwawayBefore);
    }

    chartData.addPoint(point);
    if (chartView != null && chartView.isDrawn()) {
      chartView.addPointToEndOfPath(point);
    }
  }

  // Assume this is an ordered list.
  public void setData(List<ChartData.DataPoint> points) {
    chartData.clear();
    chartData.setPoints(points);
    chartOptions.reset();
    chartOptions.setPinnedToNow(false);
  }

  private void addOrderedGroupOfPoints(List<ChartData.DataPoint> points, long requestId) {
    if (currentLoadIds.contains(requestId)) {
      chartData.addOrderedGroupOfPoints(points);
    }
  }

  // Clears just the line data, but does not reset the options. This is useful if we need
  // to update zoom levels on the same sensor in the same range, for example.
  private void clearLineData() {
    chartData.clear();
    currentLoadIds.clear();
    if (chartView != null) {
      chartView.clear();
    }
  }

  public void clearData() {
    chartData.clear();
    currentLoadIds.clear();
    chartOptions.reset();
    if (chartView != null) {
      chartView.clear();
    }
  }

  public void onDestroy() {
    onViewRecycled();
    chartData.clear();
    currentLoadIds.clear();
    chartDataLoadedCallbacks.clear();
  }

  public void onViewRecycled() {
    if (chartView != null) {
      chartView.clearInteractionListeners();
      interactionListener = null;
      chartView = null;
    }
  }

  private void setPinnedToNow(boolean isPinnedToNow) {
    chartOptions.setPinnedToNow(isPinnedToNow);
  }

  public boolean isPinnedToNow() {
    return chartOptions.isPinnedToNow();
  }

  public void setTriggers(List<SensorTrigger> triggers) {
    List<Double> values = new ArrayList<>();
    for (SensorTrigger trigger : triggers) {
      values.add(trigger.getValueToTrigger());
    }
    chartOptions.setTriggerValues(values);
  }

  public void setLabels(List<Label> labels) {
    displayableLabels.clear();
    for (Label label : labels) {
      if (chartOptions.isDisplayable(label, chartOptions.getRecordingStartTime())) {
        displayableLabels.add(label);
      }
    }
    chartData.setDisplayableLabels(displayableLabels);
    if (chartView != null) {
      chartView.postInvalidateOnAnimation();
    }
  }

  private void refreshLabels() {
    chartData.setDisplayableLabels(displayableLabels);
    if (chartView != null) {
      chartView.postInvalidateOnAnimation();
    }
  }

  public List<ChartData.DataPoint> getData() {
    return chartData.getPoints();
  }

  public void setXAxis(long xMin, long xMax) {
    chartOptions.setRenderedXRange(xMin, xMax);
    if (chartOptions.isPinnedToNow() && !chartData.isEmpty()) {
      chartOptions.adjustYAxisStep(chartData.getPoints().get(chartData.getNumPoints() - 1));
    }
    if (chartView != null) {
      chartView.onAxisLimitsAdjusted();
    }
  }

  public void setXAxisWithBuffer(long xMin, long xMax) {
    long buffer = (long) (ExternalAxisController.EDGE_POINTS_BUFFER_FRACTION * (xMax - xMin));
    setXAxis(xMin - buffer, xMax + buffer);
  }

  public void setYAxis(double yMin, double yMax) {
    chartOptions.setRenderedYRange(yMin, yMax);
    if (chartView != null) {
      chartView.onAxisLimitsAdjusted();
    }
  }

  private void updateYRangeFromValueRange(Range<Double> valueRange) {
    chartOptions.updateYMinAndMax(
        Math.min(valueRange.lowerEndpoint(), chartOptions.getYMinLimit()),
        Math.max(valueRange.upperEndpoint(), chartOptions.getYMaxLimit()));
  }

  public void clearReviewYAxis() {
    chartOptions.resetYAxisLimits();
  }

  public void setReviewYAxis(double min, double max, boolean hasBuffer) {
    min = Math.min(chartOptions.getYMinLimit(), min);
    max = Math.max(chartOptions.getYMaxLimit(), max);
    double buffer = 0;
    if (hasBuffer) {
      buffer = ChartOptions.getYBuffer(min, max);
    }
    chartOptions.updateYMinAndMax(min - buffer, max + buffer);
    setYAxis(min - buffer, max + buffer);
    refreshChartView();
  }

  public boolean hasData() {
    return !chartData.isEmpty();
  }

  public long getXMin() {
    return chartData.getXMin();
  }

  public long getXMax() {
    return chartData.getXMax();
  }

  public long getRenderedXMin() {
    return chartOptions.getRenderedXMin();
  }

  public long getRenderedXMax() {
    return chartOptions.getRenderedXMax();
  }

  public double getRenderedYMin() {
    return chartOptions.getRenderedYMin();
  }

  public double getRenderedYMax() {
    return chartOptions.getRenderedYMax();
  }

  public void refreshChartView() {
    if (chartView != null) {
      chartView.redraw();
    }
  }

  /**
   * Gets the closest data point to a given time stamp.
   *
   * @param timestamp The timestamp to search for
   * @return A data point that is closest to this timestamp
   */
  public ChartData.DataPoint getClosestDataPointToTimestamp(long timestamp) {
    return chartData.getClosestDataPointToTimestamp(timestamp);
  }

  /**
   * Gets the closest data point to the given timestamp, so long as it is above or equal to the
   * aboveTimestamp. The aboveTimestamp cannot be more than one index away from the closest data
   * point's index.
   *
   * @param timestamp The timestamp to search for.
   * @param aboveTimestamp This may not be more than one index away in ChartData.
   * @return A data point, or null if none is available.
   */
  public ChartData.DataPoint getClosestDataPointToTimestampAbove(
      long timestamp, long aboveTimestamp) {
    if (chartData.isEmpty()) {
      return null;
    }
    int closestIndex = chartData.getClosestIndexToTimestamp(timestamp);
    ChartData.DataPoint closestPoint = chartData.getPoints().get(closestIndex);
    // Check if we are above the aboveTimestamp.
    if (closestPoint.getX() >= aboveTimestamp) {
      return closestPoint;
    }
    if (closestIndex + 1 < chartData.getNumPoints() - 1) {
      return chartData.getPoints().get(closestIndex + 1);
    }
    return null;
  }

  /**
   * Gets the closest data point to the given timestamp, so long as it is below or equal to the
   * belowTimestamp. The belowTimestamp cannot be more than one index away from the closest data
   * point's index.
   *
   * @param timestamp The timestamp to search for.
   * @param belowTimestamp This may not be more than one index away in ChartData.
   * @return A data point, or null if none is available.
   */
  public ChartData.DataPoint getClosestDataPointToTimestampBelow(
      long timestamp, long belowTimestamp) {
    if (chartData.isEmpty()) {
      return null;
    }
    int closestIndex = chartData.getClosestIndexToTimestamp(timestamp);
    ChartData.DataPoint closestPoint = chartData.getPoints().get(closestIndex);
    // Check if we are above the aboveTimestamp.
    if (closestPoint.getX() <= belowTimestamp) {
      return closestPoint;
    }
    if (closestIndex - 1 >= 0) {
      return chartData.getPoints().get(closestIndex - 1);
    }
    return null;
  }

  public boolean hasDrawnChart() {
    return chartView != null && chartView.isDrawn();
  }

  public boolean hasScreenPoints() {
    return chartView != null && chartView.isDrawn() && !chartData.isEmpty();
  }

  public PointF getScreenPoint(long timestamp, double value) {
    return new PointF(chartView.getScreenX(timestamp), chartView.getScreenY(value));
  }

  public ViewTreeObserver getChartViewTreeObserver() {
    if (chartView == null) {
      return null;
    }
    return chartView.getViewTreeObserver();
  }

  private void updateColor(int colorIndex, Context context) {
    if (context == null) {
      return;
    }
    int color = context.getResources().getIntArray(R.array.graph_colors_array)[colorIndex];
    updateColor(color);
  }

  public void updateColor(int color) {
    chartOptions.setLineColor(color);
    if (chartView != null) {
      chartView.updateColorOptions();
    }
  }

  public void setShowProgress(boolean showProgress) {
    if (chartView != null) {
      chartView.setVisibility(showProgress ? View.GONE : View.VISIBLE);
    }
    if (progressView != null) {
      progressView.setIndeterminateTintList(ColorStateList.valueOf(chartOptions.getLineColor()));
      progressView.setVisibility(showProgress ? View.VISIBLE : View.GONE);
    }
  }

  public void setInteractionListener(
      ExternalAxisController.InteractionListener interactionListener) {
    this.interactionListener = interactionListener;
    if (chartView != null) {
      chartView.addInteractionListener(interactionListener);
    }
  }

  public void updateOptions(
      int graphColor, ScalarDisplayOptions scalarDisplayOptions, String sensorId) {
    chartOptions.setLineColor(graphColor);
    chartOptions.setScalarDisplayOptions(scalarDisplayOptions);
    this.sensorId = sensorId;
    if (chartView != null) {
      chartView.redraw(); // Full redraw in case the options caused computational changes.
    }
  }

  public void setSensorId(String sensorId) {
    this.sensorId = sensorId;
  }

  public void setTrialId(String trialId) {
    this.trialId = trialId;
  }

  public void setRecordingStartTime(long recordingStartTime) {
    chartOptions.setRecordingStartTime(recordingStartTime);
    if (chartView != null) {
      chartView.postInvalidateOnAnimation();
    }
  }

  public void updateStats(List<StreamStat> stats) {
    chartData.updateStats(stats);
    if (chartView != null) {
      chartView.postInvalidateOnAnimation();
    }
  }

  public void setShowStatsOverlay(boolean showStatsOverlay) {
    chartOptions.setShowStatsOverlay(showStatsOverlay);
    if (chartView != null) {
      chartView.postInvalidateOnAnimation();
    }
  }

  /**
   * Tries to load data into the chart with the given parameters.
   *
   * @param fullChartLoadDataCallback A callback which is used just during this load, but is not
   *     saved for future data loads.
   */
  // TODO: Seems like this loads the whole run data even if we are really zoomed in, so
  // this should be revisited to get a range for the original load.
  public void loadRunData(
      Trial trial,
      SensorLayoutPojo sensorLayout,
      DataController dc,
      ChartLoadingStatus status,
      TrialStats stats,
      ChartDataLoadedCallback fullChartLoadDataCallback,
      Context context) {
    updateColor(sensorLayout.getColorIndex(), context);
    setShowProgress(true);
    clearData();
    final long firstTimestamp = trial.getFirstTimestamp();
    final long lastTimestamp = trial.getLastTimestamp();
    chartOptions.setRecordingTimes(
        firstTimestamp,
        lastTimestamp,
        trial.getOriginalFirstTimestamp(),
        trial.getOriginalLastTimestamp());
    sensorId = sensorLayout.getSensorId();
    trialId = trial.getTrialId();
    tryLoadingChartData(
        trial.getTrialId(),
        sensorLayout,
        dc,
        chartOptions.getRecordingStartTime(),
        chartOptions.getRecordingEndTime(),
        status,
        stats,
        fullChartLoadDataCallback,
        context);
  }

  // TODO: remove duplication with loadReadings?
  private void tryLoadingChartData(
      final String runId,
      final SensorLayoutPojo sensorLayout,
      final DataController dc,
      final long firstTimestamp,
      final long lastTimestamp,
      final ChartLoadingStatus status,
      final TrialStats stats,
      final ChartDataLoadedCallback fullChartLoadDataCallback,
      Context context) {
    Preconditions.checkNotNull(runId);

    // If we are currently trying to load something, don't try and load something else.
    // Instead, when loading is completed a callback will check that the correct data
    // was loaded and re-call this function.
    // The status is always set loading before loadSensorReadings, and always set to idle
    // when loading is completed (regardless of whether the correct data was loaded).
    if (status.getGraphLoadStatus() != ChartLoadingStatus.GRAPH_LOAD_STATUS_IDLE) {
      return;
    }
    updateColor(sensorLayout.getColorIndex(), context);
    status.setGraphLoadStatus(ChartLoadingStatus.GRAPH_LOAD_STATUS_LOADING);
    addChartDataLoadedCallback(fullChartLoadDataCallback);
    callChartDataStartLoadingCallbacks(true);
    final ZoomPresenter zp = getZoomPresenter(stats);
    minLoadedX = firstTimestamp;
    maxLoadedX = lastTimestamp;
    int currentTier = zp.updateTier(lastTimestamp - firstTimestamp);

    // Populate the initial graph
    GraphPopulator graphPopulator =
        new GraphPopulator(
            new GraphPopulator.ObservationDisplay() {
              @Override
              public void addRange(
                  ScalarReadingList observations, Range<Double> valueRange, long requestId) {
                updateYRangeFromValueRange(valueRange);
                addOrderedGroupOfPoints(observations.asDataPoints(), requestId);
              }

              @Override
              public void onFinish(long requestId) {
                status.setGraphLoadStatus(ChartLoadingStatus.GRAPH_LOAD_STATUS_IDLE);

                if (!runId.equals(status.getRunId())
                    || !sensorLayout.getSensorId().equals(status.getSensorId())
                    || !currentLoadIds.contains(requestId)) {
                  // The wrong run or the wrong sensor ID was loaded into this
                  // chartController, or this is the wrong request ID.
                  // Clear and try again with the updated run and sensor values from the holder.
                  clearData();
                  tryLoadingChartData(
                      status.getRunId(),
                      sensorLayout,
                      dc,
                      firstTimestamp,
                      lastTimestamp,
                      status,
                      stats,
                      fullChartLoadDataCallback,
                      context);
                } else {
                  currentLoadIds.remove(requestId);
                  callChartDataLoadedCallbacks(firstTimestamp, lastTimestamp);
                  if (fullChartLoadDataCallback != null) {
                    removeChartDataLoadedCallback(fullChartLoadDataCallback);
                  }
                  setShowProgress(false);
                }
              }
            },
            uptimeClock);

    currentLoadIds.add(graphPopulator.getRequestId());
    graphPopulator.requestObservations(
        GraphPopulator.constantGraphStatus(firstTimestamp, lastTimestamp),
        dc,
        dataFailureListener,
        currentTier,
        runId,
        sensorId);
  }

  private ZoomPresenter getZoomPresenter(TrialStats stats) {
    if (zoomPresenter == null) {
      zoomPresenter = new ZoomPresenter();
    }
    zoomPresenter.setRunStats(stats);
    return zoomPresenter;
  }

  public void onPause() {
    if (isRecording()) {
      needsForwardLoad = true;
    }
  }

  public void onResume(long resetTime) {
    this.resetTime = resetTime;
    if (hasData() && this.resetTime > chartData.getXMax() + MAX_BLACKOUT_MILLIS_BEFORE_CLEARING) {
      clearLineData();
      needsForwardLoad = false;
    }
  }

  public void setShowOriginalRun(boolean showOriginalRun) {
    chartOptions.setShowOriginalRun(showOriginalRun);
  }

  public void onGlobalXAxisChanged(
      long xMin, long xMax, boolean isPinnedToNow, DataController dataController) {
    boolean isRunReview =
        chartOptions.getChartPlacementType() == ChartOptions.ChartPlacementType.TYPE_RUN_REVIEW;
    if (isRunReview && zoomPresenter == null) {
      // Then we aren't loaded all the way, so don't try to load anything else.
      return;
    }
    boolean isRecording = isObserving() && isRecording();
    if (isRunReview || isRecording) {
      long range = xMax - xMin;
      long buffer = isRecording ? dataLoadBuffer : range / 8;

      if (isRunReview) {
        int oldTier = zoomPresenter.getCurrentTier();
        int newTier = zoomPresenter.updateTier(range);
        if (oldTier != newTier) {
          reloadAtNewZoomLevel(xMin, xMax, dataController, buffer);
          return;
        }
      }

      // If something is already loaded...
      if (minLoadedX != NOTHING_LOADED && !chartData.isEmpty()) {
        // Load new data and throw away data that is too far off screen.
        // Note that xMin may be less than what is possible to load, because we often
        // load the chart with some buffer.
        long minPossibleToLoad = Math.max(xMin, chartOptions.getRecordingStartTime());
        if (minPossibleToLoad < minLoadedX) {
          long prevMinLoadedX = minLoadedX;
          minLoadedX = Math.max(xMin - buffer, chartOptions.getRecordingStartTime());
          loadReadings(dataController, minLoadedX, prevMinLoadedX, false);
        }
        long maxPossibleToLoad =
            isRecording ? xMax : Math.min(xMax, chartOptions.getRecordingEndTime());
        if (maxPossibleToLoad > maxLoadedX) {
          long prevMaxLoadedX = maxLoadedX;
          maxLoadedX =
              isRecording ? xMax : Math.min(xMax + buffer, chartOptions.getRecordingEndTime());
          // If it's pinned to now, then we don't expect to find data magically
          // appearing in front of old data.
          if (needsForwardLoad || isRunReview || !isPinnedToNow) {
            loadReadings(dataController, prevMaxLoadedX, maxLoadedX, false);
            needsForwardLoad = false;
          }
        }
      } else if (isRecording && currentLoadIds.size() == 0) {
        // If we haven't loaded anything, and it is recorded, try loading data that
        // was already recorded.
        // Don't load anything before the recording start time if we got here
        // from resume.
        minLoadedX = Math.max(xMin, chartOptions.getRecordingStartTime());
        maxLoadedX = xMax;
        loadReadings(dataController, minLoadedX, maxLoadedX, false);
      } else if (chartData.isEmpty()) {
        // If we haven't loaded anything, and we are not recording.
        minLoadedX = Math.max(xMin, chartOptions.getRecordingStartTime());
        maxLoadedX = Math.min(xMax, chartOptions.getRecordingEndTime());
        loadReadings(dataController, minLoadedX, maxLoadedX, false);
      }
    }
    setXAxis(xMin, xMax);

    if (isRunReview) {
      chartData.throwAwayBefore(minLoadedX);
      chartData.throwAwayAfter(maxLoadedX);
    } else {
      setPinnedToNow(isPinnedToNow);
      long throwawayThreshold = xMin - (KEEP_THIS_MANY_SCREENS - 1) * defaultGraphRange;
      if (!isPinnedToNow
          && !isRecording
          && xMax > currentTimeClock.getNow() - ExternalAxisController.LEADING_EDGE_BUFFER_TIME) {
        // Don't throw away data on a big scroll forward
        throwawayThreshold = currentTimeClock.getNow() - KEEP_THIS_MANY_SCREENS * defaultGraphRange;
      }
      if (minLoadedX < throwawayThreshold) {
        minLoadedX = throwawayThreshold;
      }
      // TODO: Should this be a throwAwayBetween or throwAwayafter, depending on which way
      // the x axis changed??
      chartData.throwAwayBefore(throwawayThreshold);
    }
  }

  private boolean isObserving() {
    return chartOptions.getChartPlacementType() == ChartOptions.ChartPlacementType.TYPE_OBSERVE;
  }

  private void reloadAtNewZoomLevel(
      long xMin, long xMax, DataController dataController, long buffer) {
    setShowProgress(true);
    clearLineData();
    minLoadedX = Math.max(xMin - buffer, chartOptions.getRecordingStartTime());
    maxLoadedX = Math.min(xMax + buffer, chartOptions.getRecordingEndTime());
    currentLoadIds.clear();
    loadReadings(dataController, minLoadedX, maxLoadedX, true);
    setXAxis(xMin, xMax);
  }

  private boolean isRecording() {
    return chartOptions.getRecordingStartTime() != RecordingMetadata.NOT_RECORDING;
  }

  @VisibleForTesting
  public void loadReadings(
      DataController dataController,
      final long minToLoad,
      final long maxToLoad,
      final boolean chartHiddenForLoad) {
    int currentTier = zoomPresenter == null ? 0 : zoomPresenter.getCurrentTier();
    GraphPopulator graphPopulator =
        new GraphPopulator(
            new GraphPopulator.ObservationDisplay() {
              @Override
              public void addRange(
                  ScalarReadingList observations, Range<Double> valueRange, long requestId) {
                updateYRangeFromValueRange(valueRange);
                addOrderedGroupOfPoints(observations.asDataPoints(), requestId);
              }

              @Override
              public void onFinish(long requestId) {
                if (currentLoadIds.contains(requestId)) {
                  currentLoadIds.remove(requestId);
                }
                if (currentLoadIds.size() == 0) {
                  refreshLabels();
                }
                if (chartHiddenForLoad) {
                  setShowProgress(false);
                }
                refreshChartView();
                callChartDataLoadedCallbacks(minToLoad, maxToLoad);
              }
            },
            uptimeClock);
    currentLoadIds.add(graphPopulator.getRequestId());
    graphPopulator.requestObservations(
        GraphPopulator.constantGraphStatus(minToLoad, maxToLoad),
        dataController,
        dataFailureListener,
        currentTier,
        trialId,
        sensorId);

    callChartDataStartLoadingCallbacks(chartHiddenForLoad);
  }

  public void addChartDataLoadedCallback(ChartDataLoadedCallback callback) {
    if (callback != null) {
      chartDataLoadedCallbacks.add(callback);
    }
  }

  public void removeChartDataLoadedCallback(ChartDataLoadedCallback callback) {
    if (chartDataLoadedCallbacks.contains(callback)) {
      chartDataLoadedCallbacks.remove(callback);
    }
  }

  private void callChartDataLoadedCallbacks(long firstTimestamp, long lastTimestamp) {
    if (chartOptions.getRequestResetZoomInY()) {
      setYAxis(chartOptions.getYMinLimit(), chartOptions.getYMaxLimit());
    }
    for (ChartDataLoadedCallback callback : chartDataLoadedCallbacks) {
      callback.onChartDataLoaded(firstTimestamp, lastTimestamp);
    }
  }

  private void callChartDataStartLoadingCallbacks(boolean chartHiddenForLoad) {
    for (ChartDataLoadedCallback callback : chartDataLoadedCallbacks) {
      callback.onLoadAttemptStarted(chartHiddenForLoad);
    }
  }
}
