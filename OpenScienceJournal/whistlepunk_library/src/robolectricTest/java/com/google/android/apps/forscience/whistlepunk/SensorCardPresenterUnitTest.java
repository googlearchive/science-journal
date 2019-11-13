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

package com.google.android.apps.forscience.whistlepunk;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.graphics.Color;
import android.view.View;
import com.google.android.apps.forscience.javalib.FailureListener;
import com.google.android.apps.forscience.whistlepunk.filemetadata.Experiment;
import com.google.android.apps.forscience.whistlepunk.filemetadata.Label;
import com.google.android.apps.forscience.whistlepunk.filemetadata.SensorLayoutPojo;
import com.google.android.apps.forscience.whistlepunk.filemetadata.SensorTrigger;
import com.google.android.apps.forscience.whistlepunk.scalarchart.ScalarDisplayOptions;
import com.google.android.apps.forscience.whistlepunk.sensorapi.BlankReadableSensorOptions;
import com.google.android.apps.forscience.whistlepunk.sensorapi.DataViewOptions;
import com.google.android.apps.forscience.whistlepunk.sensorapi.ManualSensor;
import com.google.android.apps.forscience.whistlepunk.sensorapi.NewOptionsStorage;
import com.google.android.apps.forscience.whistlepunk.sensorapi.OptionsListener;
import com.google.android.apps.forscience.whistlepunk.sensorapi.SensorChoice;
import com.google.android.apps.forscience.whistlepunk.sensorapi.SensorPresenter;
import com.google.android.apps.forscience.whistlepunk.sensorapi.SensorStatusListener;
import com.google.android.apps.forscience.whistlepunk.sensorapi.StreamStat;
import com.google.android.apps.forscience.whistlepunk.sensordb.InMemorySensorDatabase;
import com.google.android.apps.forscience.whistlepunk.sensordb.MemoryMetadataManager;
import com.google.android.apps.forscience.whistlepunk.sensors.AmbientLightSensor;
import com.google.common.collect.Lists;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

@RunWith(RobolectricTestRunner.class)
public class SensorCardPresenterUnitTest {
  @Test
  public void rememberIdPassToStop() {
    DataViewOptions dataViewOptions = new DataViewOptions(Color.BLACK, new ScalarDisplayOptions());
    SensorSettingsController ssc =
        new SensorSettingsController() {
          @Override
          public void launchOptionsDialog(
              SensorChoice source,
              SensorPresenter presenter,
              NewOptionsStorage storage,
              OptionsListener commitListener,
              FailureListener failureListener) {}
        };
    MemoryRecorderController rc = new MemoryRecorderController();
    SensorCardPresenter scp =
        new SensorCardPresenter(dataViewOptions, ssc, rc, new SensorLayoutPojo(), "", null, null);
    SensorPresenter presenter = new MemorySensorPresenter();
    ManualSensor ds = new ManualSensor("sensorId", 100, 100);
    InMemorySensorDatabase db = new InMemorySensorDatabase();
    MemoryMetadataManager manager = new MemoryMetadataManager();
    final DataController dc = db.makeSimpleController(manager);
    scp.startObserving(
        ds,
        presenter,
        new BlankReadableSensorOptions(),
        Experiment.newExperiment(10, "localId", 0),
        null);
    scp.setAppearanceProvider(new FakeAppearanceProvider(0));
    scp.setUiForConnectingNewSensor(ds.getId(), "Display Name", "units", false);
    assertEquals(Arrays.asList(ds.getId()), rc.getCurrentObservedIds());
    scp.stopObserving();
    assertEquals(Arrays.asList(), rc.getCurrentObservedIds());
  }

  @Test
  public void disconnectAllowsRetry() {
    SensorCardPresenter.CardStatus cardStatus = new SensorCardPresenter.CardStatus();
    cardStatus.setStatus(SensorStatusListener.STATUS_DISCONNECTED);
    cardStatus.setHasError(false);
    assertTrue(cardStatus.shouldShowRetry());
  }

  @Test
  public void trustIncomingSensorSortOrder() {
    ArrayList<String> ids = Lists.newArrayList("c", "b", "a", AmbientLightSensor.ID);
    List<String> sorted = SensorCardPresenter.customSortSensorIds(ids, ids);
    assertEquals(Lists.newArrayList(AmbientLightSensor.ID, "c", "b", "a"), sorted);
  }

  private static class MemorySensorPresenter implements SensorPresenter {
    @Override
    public void startShowing(
        View contentView, ExternalAxisController.InteractionListener listener) {}

    @Override
    public void onRecordingStateChange(boolean isRecording, long recordingStart) {}

    @Override
    public void onLabelsChanged(List<Label> labels) {}

    @Override
    public void onGlobalXAxisChanged(
        long xMin, long xMax, boolean isPinnedToNow, DataController dataController) {}

    @Override
    public double getMinY() {
      return 0;
    }

    @Override
    public double getMaxY() {
      return 0;
    }

    @Override
    public void onPause() {}

    @Override
    public void onResume(long resetTime) {}

    @Override
    public void onStopObserving() {}

    @Override
    public void onViewRecycled() {}

    @Override
    public OptionsPresenter getOptionsPresenter() {
      return null;
    }

    @Override
    public void updateAudioSettings(boolean audioEnabled, String sonificationType) {}

    @Override
    public void setShowStatsOverlay(boolean showStatsOverlay) {}

    @Override
    public void updateStats(List<StreamStat> stats) {}

    @Override
    public void setYAxisRange(double minimumYAxisValue, double maximumYAxisValue) {}

    @Override
    public void resetView() {}

    @Override
    public void setTriggers(List<SensorTrigger> triggers) {}

    @Override
    public void onNewData(long timestamp, Data data) {}
  }
}
