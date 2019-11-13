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

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import androidx.annotation.NonNull;
import com.google.android.apps.forscience.javalib.Delay;
import com.google.android.apps.forscience.javalib.FailureListener;
import com.google.android.apps.forscience.whistlepunk.accounts.AppAccount;
import com.google.android.apps.forscience.whistlepunk.accounts.NonSignedInAccount;
import com.google.android.apps.forscience.whistlepunk.filemetadata.Experiment;
import com.google.android.apps.forscience.whistlepunk.filemetadata.SensorLayoutPojo;
import com.google.android.apps.forscience.whistlepunk.scalarchart.ScalarDisplayOptions;
import com.google.android.apps.forscience.whistlepunk.sensorapi.DataViewOptions;
import com.google.android.apps.forscience.whistlepunk.sensorapi.ManualSensor;
import com.google.android.apps.forscience.whistlepunk.sensorapi.MemorySensorEnvironment;
import com.google.android.apps.forscience.whistlepunk.sensorapi.NewOptionsStorage;
import com.google.android.apps.forscience.whistlepunk.sensorapi.ReadableSensorOptions;
import com.google.android.apps.forscience.whistlepunk.sensorapi.WriteableSensorOptions;
import com.google.android.apps.forscience.whistlepunk.sensors.DecibelSensor;
import com.google.android.apps.forscience.whistlepunk.sensors.SystemScheduler;
import com.google.common.collect.Lists;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

@RunWith(RobolectricTestRunner.class)
public class SensorCardPresenterTest {
  private RecorderControllerImpl recorderController;
  private SensorRegistry sensorRegistry;
  private List<String> stoppedSensorIds = new ArrayList<>();

  @Test
  public void testExtrasIncludedInLayout() {
    SensorCardPresenter scp = createSCP();
    String key = Arbitrary.string();
    String value = Arbitrary.string();
    scp.getCardOptions(new DecibelSensor(), getContext()).load(explodingListener()).put(key, value);
    Map<String, String> extras = scp.buildLayout().getExtras();
    assertThat(extras).hasSize(1);
    assertThat(extras).containsEntry(key, value);
  }

  private FailureListener explodingListener() {
    return ExplodingFactory.makeListener();
  }

  @Test
  public void testUseSensorDefaults() {
    DecibelSensor sensor = new DecibelSensor();
    String key = Arbitrary.string();
    String value = Arbitrary.string();
    sensor
        .getStorageForSensorDefaultOptions(getContext())
        .load(explodingListener())
        .put(key, value);
    SensorCardPresenter scp = createSCP();
    ReadableSensorOptions read =
        scp.getCardOptions(sensor, getContext()).load(explodingListener()).getReadOnly();
    assertEquals(value, read.getString(key, null));
  }

  @Test
  public void testCanStillUseCardDefaultsIfNoSensor() {
    SensorCardPresenter scp = createSCP();
    NewOptionsStorage storage = scp.getCardOptions(null, getContext());
    WriteableSensorOptions writeable = storage.load(explodingListener());
    writeable.put("key", "value");
    assertEquals("value", writeable.getReadOnly().getString("key", "default"));
  }

  @Test
  public void testExtrasOverrideSensorDefaults() {
    DecibelSensor sensor = new DecibelSensor();
    String key = Arbitrary.string();
    String value = "fromSensorDefault";
    sensor
        .getStorageForSensorDefaultOptions(getContext())
        .load(explodingListener())
        .put(key, value);

    LocalSensorOptionsStorage localStorage = new LocalSensorOptionsStorage();
    localStorage.load().put(key, "fromCardSettings");
    SensorLayoutPojo layout = new SensorLayoutPojo();
    layout.putAllExtras(localStorage.exportAsLayoutExtras());

    SensorCardPresenter scp = createSCP(layout);
    ReadableSensorOptions read =
        scp.getCardOptions(sensor, getContext()).load(explodingListener()).getReadOnly();
    assertEquals("fromCardSettings", read.getString(key, null));
  }

  @Test
  public void testKeepOrder() {
    SensorCardPresenter scp = createSCP();
    setSensorId(scp, "selected", "displayName");
    scp.updateAvailableSensors(
        Lists.newArrayList("available1", "available2", "available3"),
        Lists.<String>newArrayList(
            "available1", "selected", "available2", "selectedElsewhere", "available3"));
    assertEquals(
        Lists.newArrayList("available1", "selected", "available2", "available3"),
        scp.getAvailableSensorIds());
  }

  @Test
  public void testRetry() {
    ManualSensor sensor = new ManualSensor("sensorId", 100, 100);
    getSensorRegistry().addBuiltInSensor(sensor);
    SensorCardPresenter scp = createSCP();
    setSensorId(scp, "sensorId", "Sensor Name");
    scp.startObserving(
        sensor,
        sensor.createPresenter(null, null, null),
        null,
        Experiment.newExperiment(10, "localExperimentId", 0),
        getSensorRegistry());
    sensor.simulateExternalEventPreventingObservation();
    assertFalse(sensor.isObserving());
    scp.retryConnection(getContext());
    assertTrue(sensor.isObserving());
  }

  @Test
  public void testDestroyWithBlankSensorId() {
    SensorCardPresenter scp = createSCP();
    // Don't stop anything if we've never had a sensorId
    scp.destroy();
    assertEquals(0, stoppedSensorIds.size());
  }

  @Test
  public void defensiveCopies() {
    SensorCardPresenter scp = createSCP();
    setSensorId(scp, "rightId", "rightName");
    SensorLayoutPojo firstLayout = scp.buildLayout();
    setSensorId(scp, "wrongId", "rightName");
    SensorLayoutPojo secondLayout = scp.buildLayout();
    assertEquals("rightId", firstLayout.getSensorId());
    assertEquals("wrongId", secondLayout.getSensorId());
  }

  private void setSensorId(SensorCardPresenter scp, String sensorId, String sensorDisplayName) {
    scp.setAppearanceProvider(new FakeAppearanceProvider());
    scp.setUiForConnectingNewSensor(sensorId, sensorDisplayName, "units", false);
  }

  @NonNull
  private SensorCardPresenter createSCP() {
    return createSCP(new SensorLayoutPojo());
  }

  @NonNull
  private SensorCardPresenter createSCP(SensorLayoutPojo layout) {
    return new SensorCardPresenter(
        new DataViewOptions(0, getContext(), new ScalarDisplayOptions()),
        new SensorSettingsControllerImpl(getContext(), getAppAccount()),
        getRecorderController(),
        layout,
        "",
        null,
        null);
  }

  @NonNull
  private RecorderControllerImpl getRecorderController() {
    if (recorderController == null) {
      recorderController =
          new RecorderControllerImpl(
              getContext(),
              getAppAccount(),
              new MemorySensorEnvironment(null, null, null, null),
              new RecorderListenerRegistry(),
              null,
              null,
              new SystemScheduler(),
              Delay.ZERO,
              new FakeAppearanceProvider()) {
            @Override
            public void stopObserving(String sensorId, String observerId) {
              stoppedSensorIds.add(sensorId);
              super.stopObserving(sensorId, observerId);
            }
          };
    }
    return recorderController;
  }

  private SensorRegistry getSensorRegistry() {
    if (sensorRegistry == null) {
      sensorRegistry = SensorRegistry.createWithBuiltinSensors(getContext());
    }
    return sensorRegistry;
  }

  private static Context getContext() {
    return RuntimeEnvironment.application.getApplicationContext();
  }

  private static AppAccount getAppAccount() {
    return NonSignedInAccount.getInstance(getContext());
  }
}
