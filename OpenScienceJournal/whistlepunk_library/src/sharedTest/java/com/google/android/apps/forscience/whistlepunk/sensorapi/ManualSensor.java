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

import static org.junit.Assert.fail;

import android.content.Context;
import androidx.annotation.NonNull;
import com.google.android.apps.forscience.whistlepunk.AxisNumberFormat;
import com.google.android.apps.forscience.whistlepunk.MemorySensorHistoryStorage;
import com.google.android.apps.forscience.whistlepunk.RecordingDataController;
import com.google.android.apps.forscience.whistlepunk.RecordingStatusListener;
import com.google.android.apps.forscience.whistlepunk.StatsListener;
import com.google.android.apps.forscience.whistlepunk.accounts.AppAccount;
import com.google.android.apps.forscience.whistlepunk.accounts.NonSignedInAccount;
import com.google.android.apps.forscience.whistlepunk.accounts.StubAppAccount;
import com.google.android.apps.forscience.whistlepunk.filemetadata.Trial;
import com.google.android.apps.forscience.whistlepunk.scalarchart.ChartController;
import com.google.android.apps.forscience.whistlepunk.scalarchart.ChartData;
import com.google.android.apps.forscience.whistlepunk.scalarchart.ChartOptions;
import com.google.android.apps.forscience.whistlepunk.scalarchart.ScalarDisplayOptions;
import com.google.android.apps.forscience.whistlepunk.scalarchart.UptimeClock;
import com.google.android.apps.forscience.whistlepunk.sensordb.MonotonicClock;
import com.google.common.util.concurrent.MoreExecutors;
import java.util.List;
import java.util.concurrent.Executor;

public class ManualSensor extends ScalarSensor {
  private final boolean automaticallyConnectWhenObserving;
  private StreamConsumer consumer;
  private ChartController chartController;

  private MemorySensorHistoryStorage storage = new MemorySensorHistoryStorage();
  private int throwawayThreshold = 1;

  public ManualSensor(
      String sensorId, long defaultGraphRange, int zoomLevelBetweenResolutionTiers) {
    this(
        sensorId,
        defaultGraphRange,
        zoomLevelBetweenResolutionTiers,
        MoreExecutors.directExecutor());
  }

  public ManualSensor(
      String sensorId,
      long defaultGraphRange,
      int zoomLevelBetweenResolutionTiers,
      Executor uiThreadExecutor) {
    this(sensorId, defaultGraphRange, zoomLevelBetweenResolutionTiers, uiThreadExecutor, true);
  }

  public ManualSensor(
      String sensorId,
      long defaultGraphRange,
      int zoomLevelBetweenResolutionTiers,
      Executor uiThreadExecutor,
      boolean automaticallyConnectWhenObserving) {
    super(
        sensorId,
        defaultGraphRange,
        uiThreadExecutor,
        zoomLevelBetweenResolutionTiers,
        new UptimeClock());
    this.automaticallyConnectWhenObserving = automaticallyConnectWhenObserving;
  }

  public SensorRecorder createRecorder(
      Context context, RecordingDataController rdc, RecordingSensorObserver observer) {
    return createRecorder(
        context,
        getAppAccount(context),
        observer,
        new StubStatusListener(),
        makeSensorEnvironment(context, rdc));
  }

  /**
   * Simulates an external event (like a BLE sensor being unplugged) that prevents further
   * observation, outside of a direct call to stopObserving.
   */
  public void simulateExternalEventPreventingObservation() {
    consumer = null;
  }

  @Override
  protected SensorRecorder makeScalarControl(
      final StreamConsumer c,
      SensorEnvironment environment,
      Context context,
      SensorStatusListener listener) {
    return new SensorRecorder() {
      @Override
      public void startObserving() {
        consumer = c;
        if (automaticallyConnectWhenObserving) {
          listener.onSourceStatus(getId(), SensorStatusListener.STATUS_CONNECTED);
        }
      }

      @Override
      public void startRecording(String runId) {
        startObserving();
      }

      @Override
      public void stopRecording(Trial trialToUpdate) {}

      @Override
      public void stopObserving() {
        consumer = null;
        if (automaticallyConnectWhenObserving) {
          listener.onSourceStatus(getId(), SensorStatusListener.STATUS_DISCONNECTED);
        }
      }

      @Override
      public boolean hasRecordedData() {
        return true;
      }

      @Override
      public void applyOptions(ReadableSensorOptions settings) {}
    };
  }

  @NonNull
  @Override
  protected ChartController createChartController(
      DataViewOptions dataViewOptions, String sensorId, long defaultGraphRange) {
    chartController =
        new ChartController(
            ChartOptions.ChartPlacementType.TYPE_OBSERVE,
            getLineGraphOptions(dataViewOptions),
            throwawayThreshold,
            /* no data loading buffer */ 0,
            new MonotonicClock());
    chartController.setSensorId(sensorId);
    chartController.setDefaultGraphRange(defaultGraphRange);
    return chartController;
  }

  private ScalarDisplayOptions getLineGraphOptions(DataViewOptions dataViewOptions) {
    if (dataViewOptions != null) {
      return dataViewOptions.getLineGraphOptions();
    } else {
      return new ScalarDisplayOptions();
    }
  }

  public List<ChartData.DataPoint> getRawData() {
    return chartController.getData();
  }

  public List<ChartData.DataPoint> getLineData() {
    return chartController.getData();
  }

  public void pushValue(long timestampMillis, double value) {
    if (consumer != null) {
      if (!consumer.addData(timestampMillis, value)) {
        fail("Did not add data: " + timestampMillis + ", " + value);
      }
    }
  }

  private SensorPresenter createPresenter() {
    StatsListener statsListener = new StubStatsListener();
    return createPresenter(
        new DataViewOptions(0, new ScalarDisplayOptions()), new AxisNumberFormat(), statsListener);
  }

  @NonNull
  public SensorPresenter createRecordingPresenter(
      Context context, RecordingDataController rc, String runId) {
    SensorPresenter presenter = createPresenter();
    createRecorder(
            context,
            getAppAccount(context),
            presenter,
            new RecordingStatusListener(),
            makeSensorEnvironment(context, rc))
        .startRecording(runId);
    presenter.onRecordingStateChange(true, 0);
    return presenter;
  }

  @NonNull
  private MemorySensorEnvironment makeSensorEnvironment(
      Context context, RecordingDataController rc) {
    return new MemorySensorEnvironment(rc, new FakeBleClient(context), storage, null);
  }

  @NonNull
  public SensorPresenter createRecordingPresenter(
      Context context,
      RecordingDataController rc,
      String runId,
      int graphDataThrowawaySizeThreshold) {
    throwawayThreshold = graphDataThrowawaySizeThreshold;
    return createRecordingPresenter(context, rc, runId);
  }

  public ChartController getChartController() {
    return chartController;
  }

  public void pushDataPoints(SensorRecorder recorder, int howMany, Trial trialToUpdate) {
    recorder.startRecording("runId");
    createPresenter().onRecordingStateChange(true, 0);

    // tier threshhold 10 and 100 data points means:
    // tier 0: 100 points
    // tier 1: 20 points
    // tier 2: 4 points
    for (int i = 0; i < howMany; i++) {
      pushValue(i, i);
    }

    recorder.stopRecording(trialToUpdate);
  }

  public boolean isObserving() {
    return consumer != null;
  }

  private AppAccount getAppAccount(Context context) {
    return (context != null)
        ? NonSignedInAccount.getInstance(context)
        : StubAppAccount.getInstance();
  }
}
