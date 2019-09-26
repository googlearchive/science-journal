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

import static junit.framework.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.Intent;
import com.google.android.apps.forscience.javalib.Delay;
import com.google.android.apps.forscience.javalib.MaybeConsumer;
import com.google.android.apps.forscience.javalib.Success;
import com.google.android.apps.forscience.whistlepunk.accounts.AppAccount;
import com.google.android.apps.forscience.whistlepunk.accounts.NonSignedInAccount;
import com.google.android.apps.forscience.whistlepunk.api.scalarinput.EmptySensorAppearance;
import com.google.android.apps.forscience.whistlepunk.data.GoosciSensorAppearance;
import com.google.android.apps.forscience.whistlepunk.data.GoosciSensorLayout.SensorLayout.CardView;
import com.google.android.apps.forscience.whistlepunk.data.GoosciSensorSpec;
import com.google.android.apps.forscience.whistlepunk.devicemanager.FakeUnitAppearanceProvider;
import com.google.android.apps.forscience.whistlepunk.filemetadata.Experiment;
import com.google.android.apps.forscience.whistlepunk.filemetadata.SensorLayoutPojo;
import com.google.android.apps.forscience.whistlepunk.filemetadata.SensorTrigger;
import com.google.android.apps.forscience.whistlepunk.filemetadata.Trial;
import com.google.android.apps.forscience.whistlepunk.metadata.BleSensorSpec;
import com.google.android.apps.forscience.whistlepunk.metadata.GoosciSensorTriggerInformation.TriggerInformation.TriggerActionType;
import com.google.android.apps.forscience.whistlepunk.metadata.GoosciSensorTriggerInformation.TriggerInformation.TriggerWhen;
import com.google.android.apps.forscience.whistlepunk.metadata.GoosciSnapshotValue;
import com.google.android.apps.forscience.whistlepunk.metadata.GoosciSnapshotValue.SnapshotLabelValue.SensorSnapshot;
import com.google.android.apps.forscience.whistlepunk.sensorapi.FakeBleClient;
import com.google.android.apps.forscience.whistlepunk.sensorapi.ManualSensor;
import com.google.android.apps.forscience.whistlepunk.sensorapi.MemorySensorEnvironment;
import com.google.android.apps.forscience.whistlepunk.sensorapi.RecordingSensorObserver;
import com.google.android.apps.forscience.whistlepunk.sensorapi.StubStatusListener;
import com.google.android.apps.forscience.whistlepunk.sensordb.InMemorySensorDatabase;
import com.google.common.base.Supplier;
import com.google.common.collect.Lists;
import io.reactivex.Maybe;
import io.reactivex.Single;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

@RunWith(RobolectricTestRunner.class)
public class RecorderControllerTest {
  private final MockScheduler scheduler = new MockScheduler();
  private String sensorId = "sensorId";
  private String sensorName = "sensor";
  private final ManualSensorRegistry sensorRegistry = new ManualSensorRegistry();
  private final ManualSensor sensor = sensorRegistry.addSensor(sensorId, sensorName);
  private final InMemorySensorDatabase database = new InMemorySensorDatabase();
  private final DataControllerImpl dataController = database.makeSimpleController();
  private final MemorySensorEnvironment environment =
      new MemorySensorEnvironment(
          database.makeSimpleRecordingController(),
          new FakeBleClient(null),
          new MemorySensorHistoryStorage(),
          null);

  @Test
  public void multipleObservers() {
    RecorderControllerImpl rc =
        new RecorderControllerImpl(
            null, // context
            getAppAccount(),
            environment,
            new RecorderListenerRegistry(),
            null, // connectionSupplier
            null, // dataController
            null, // scheduler
            Delay.ZERO,
            new FakeUnitAppearanceProvider());
    RecordingSensorObserver observer1 = new RecordingSensorObserver();
    RecordingSensorObserver observer2 = new RecordingSensorObserver();

    sensor.pushValue(0, 0);
    String id1 =
        rc.startObserving(
            sensorId,
            Collections.<SensorTrigger>emptyList(),
            observer1,
            new StubStatusListener(),
            null,
            sensorRegistry);
    sensor.pushValue(1, 1);
    String id2 =
        rc.startObserving(
            sensorId,
            Collections.<SensorTrigger>emptyList(),
            observer2,
            new StubStatusListener(),
            null,
            sensorRegistry);
    sensor.pushValue(2, 2);
    rc.stopObserving(sensorId, id1);
    sensor.pushValue(3, 3);
    rc.stopObserving(sensorId, id2);
    sensor.pushValue(4, 4);

    TestData.allPointsBetween(1, 2, 1).checkObserver(observer1);
    TestData.allPointsBetween(2, 3, 1).checkObserver(observer2);
  }

  @Test
  public void layoutLogging() {
    RecorderControllerImpl rc =
        new RecorderControllerImpl(
            null,
            getAppAccount(),
            environment,
            new RecorderListenerRegistry(),
            null,
            null,
            null,
            Delay.ZERO,
            new FakeUnitAppearanceProvider());

    SensorLayoutPojo layout = new SensorLayoutPojo();
    layout.setSensorId("aa:bb:cc:dd");
    layout.setCardView(CardView.GRAPH);
    layout.setAudioEnabled(false);
    String loggingId = BleSensorSpec.TYPE;

    assertEquals("bluetooth_le|graph|audioOff", rc.getLayoutLoggingString(loggingId, layout));

    layout.setCardView(CardView.METER);
    assertEquals("bluetooth_le|meter|audioOff", rc.getLayoutLoggingString(loggingId, layout));

    layout.setAudioEnabled(true);
    assertEquals("bluetooth_le|meter|audioOn", rc.getLayoutLoggingString(loggingId, layout));

    layout.setSensorId("AmbientLight");
    assertEquals("AmbientLight|meter|audioOn", rc.getLayoutLoggingString("AmbientLight", layout));
  }

  @Test
  public void delayStopObserving() {
    TestTrigger trigger = new TestTrigger(sensorId);
    ArrayList<SensorTrigger> triggerList = Lists.<SensorTrigger>newArrayList(trigger);
    RecorderControllerImpl rc =
        new RecorderControllerImpl(
            null,
            getAppAccount(),
            environment,
            new RecorderListenerRegistry(),
            null,
            null,
            scheduler,
            Delay.seconds(15),
            new FakeUnitAppearanceProvider());
    String observeId1 =
        rc.startObserving(
            sensorId,
            Lists.<SensorTrigger>newArrayList(),
            new RecordingSensorObserver(),
            new RecordingStatusListener(),
            null,
            sensorRegistry);
    rc.stopObserving(sensorId, observeId1);

    // Redundant call should have no effect.
    rc.stopObserving(sensorId, observeId1);
    scheduler.incrementTime(1000);

    // Should still be observing 1s later.
    assertTrue(sensor.isObserving());

    // Start observing again before the delay is hit
    String observeId2 =
        rc.startObserving(
            sensorId,
            triggerList,
            new RecordingSensorObserver(),
            new RecordingStatusListener(),
            null,
            sensorRegistry);

    // Make sure there's no delayed stop commands waiting to strike
    scheduler.incrementTime(30000);
    assertTrue(sensor.isObserving());

    // And we have correctly picked up the new trigger list.
    trigger.clearTestCount();
    sensor.pushValue(0, 0);
    assertEquals(1, trigger.getTestCount());

    // Finally, after appropriate delay, sensor stops.
    rc.stopObserving(sensorId, observeId2);
    assertTrue(sensor.isObserving());
    scheduler.incrementTime(16000);
    assertFalse(sensor.isObserving());
  }

  @Test
  public void dontScheduleIfDelayIs0() {
    RecorderControllerImpl rc =
        new RecorderControllerImpl(
            null,
            getAppAccount(),
            environment,
            new RecorderListenerRegistry(),
            null,
            null,
            scheduler,
            Delay.ZERO,
            new FakeUnitAppearanceProvider());
    String observeId1 =
        rc.startObserving(
            sensorId,
            null,
            new RecordingSensorObserver(),
            new RecordingStatusListener(),
            null,
            sensorRegistry);
    rc.stopObserving(sensorId, observeId1);
    assertEquals(0, scheduler.getScheduleCount());
  }

  @Test
  public void reboot() {
    RecorderControllerImpl rc =
        new RecorderControllerImpl(
            null,
            getAppAccount(),
            environment,
            new RecorderListenerRegistry(),
            null,
            null,
            scheduler,
            Delay.ZERO,
            new FakeUnitAppearanceProvider());
    rc.startObserving(
        sensorId,
        null,
        new RecordingSensorObserver(),
        new RecordingStatusListener(),
        null,
        sensorRegistry);
    sensor.simulateExternalEventPreventingObservation();
    assertFalse(sensor.isObserving());
    rc.reboot(sensorId);
    assertTrue(sensor.isObserving());
  }

  @Test
  public void takeSnapshotText() {
    final RecorderControllerImpl rc =
        new RecorderControllerImpl(
            getContext(),
            getAppAccount(),
            environment,
            new RecorderListenerRegistry(),
            null,
            dataController,
            scheduler,
            Delay.ZERO,
            new FakeUnitAppearanceProvider());

    rc.startObserving(
        sensorId,
        new ArrayList<SensorTrigger>(),
        new RecordingSensorObserver(),
        new RecordingStatusListener(),
        null,
        sensorRegistry);

    Maybe<String> snapshot =
        rc.generateSnapshotText(Lists.<String>newArrayList(sensorId), sensorRegistry);
    sensor.pushValue(10, 50);
    assertEquals("[sensor has value 50.0]", snapshot.test().assertNoErrors().values().toString());
  }

  @Test
  public void takeSnapshot() {
    // TODO: produce framework to cut down on test duplication here?
    final RecorderControllerImpl rc =
        new RecorderControllerImpl(
            getContext(),
            getAppAccount(),
            environment,
            new RecorderListenerRegistry(),
            null,
            dataController,
            scheduler,
            Delay.ZERO,
            new FakeUnitAppearanceProvider());

    rc.startObserving(
        sensorId,
        new ArrayList<SensorTrigger>(),
        new RecordingSensorObserver(),
        new RecordingStatusListener(),
        null,
        sensorRegistry);

    Single<GoosciSnapshotValue.SnapshotLabelValue> snapshot =
        rc.generateSnapshotLabelValue(Lists.<String>newArrayList(sensorId), sensorRegistry);
    sensor.pushValue(10, 50);

    GoosciSnapshotValue.SnapshotLabelValue value =
        snapshot.test().assertNoErrors().assertValueCount(1).values().get(0);

    SensorSnapshot shot = value.getSnapshots(0);
    assertEquals(sensorName, shot.getSensor().getRememberedAppearance().getName());
    assertEquals(50.0, shot.getValue(), 0.01);
    assertEquals(10, shot.getTimestampMs());

    // TODO: test other values (other appearance values and timestamp)
  }

  @Test
  public void snapshotWithNoSensors() {
    final RecorderControllerImpl rc =
        new RecorderControllerImpl(
            getContext(),
            getAppAccount(),
            environment,
            new RecorderListenerRegistry(),
            null,
            dataController,
            scheduler,
            Delay.ZERO,
            new FakeUnitAppearanceProvider());

    Maybe<String> snapshot =
        rc.generateSnapshotText(Lists.<String>newArrayList(sensorId), sensorRegistry);
    assertEquals("[No sensors observed]", snapshot.test().values().toString());
  }

  @Test
  public void snapshotWithThreeSensors() {
    ManualSensor s2 = sensorRegistry.addSensor("s2", "B2");
    ManualSensor s3 = sensorRegistry.addSensor("s3", "C3");

    final RecorderControllerImpl rc =
        new RecorderControllerImpl(
            getContext(),
            getAppAccount(),
            environment,
            new RecorderListenerRegistry(),
            null,
            dataController,
            scheduler,
            Delay.ZERO,
            new FakeUnitAppearanceProvider());

    ArrayList<SensorTrigger> triggers = new ArrayList<>();
    rc.startObserving(
        sensorId,
        triggers,
        new RecordingSensorObserver(),
        new RecordingStatusListener(),
        null,
        sensorRegistry);
    rc.startObserving(
        s2.getId(),
        triggers,
        new RecordingSensorObserver(),
        new RecordingStatusListener(),
        null,
        sensorRegistry);
    rc.startObserving(
        s3.getId(),
        triggers,
        new RecordingSensorObserver(),
        new RecordingStatusListener(),
        null,
        sensorRegistry);

    // Some sensors may publish earlier
    s2.pushValue(12, 52);
    s3.pushValue(13, 53);
    Maybe<String> snapshot =
        rc.generateSnapshotText(
            Lists.newArrayList(sensorId, s2.getId(), s3.getId()), sensorRegistry);
    // Some may publish later
    sensor.pushValue(11, 51);
    assertEquals(
        "[" + sensorName + " has value 51.0, B2 has value 52.0, C3 has value 53.0]",
        snapshot.test().assertNoErrors().values().toString());
  }

  @Test
  public void dontCacheSensorValuesBetweenObservation() {
    final RecorderControllerImpl rc =
        new RecorderControllerImpl(
            getContext(),
            getAppAccount(),
            environment,
            new RecorderListenerRegistry(),
            null,
            dataController,
            scheduler,
            Delay.ZERO,
            new FakeUnitAppearanceProvider());

    String observerId =
        rc.startObserving(
            sensorId,
            new ArrayList<SensorTrigger>(),
            new RecordingSensorObserver(),
            new RecordingStatusListener(),
            null,
            sensorRegistry);
    sensor.pushValue(10, 50);

    // This should clear the latest value
    rc.stopObserving(sensorId, observerId);

    // Restart observing
    rc.startObserving(
        sensorId,
        new ArrayList<SensorTrigger>(),
        new RecordingSensorObserver(),
        new RecordingStatusListener(),
        null,
        sensorRegistry);

    Maybe<String> snapshot =
        rc.generateSnapshotText(Lists.<String>newArrayList(sensorId), sensorRegistry);
    snapshot.test().assertNotComplete();
    sensor.pushValue(20, 60);
    assertEquals("[sensor has value 60.0]", snapshot.test().assertNoErrors().values().toString());
  }

  @Test
  public void storeAppearances() throws InterruptedException {
    final RecorderControllerImpl rc =
        new RecorderControllerImpl(
            getContext(),
            getAppAccount(),
            environment,
            new RecorderListenerRegistry(),
            connectionSupplier(),
            dataController,
            scheduler,
            Delay.ZERO,
            new SensorAppearanceProvider() {
              @Override
              public void loadAppearances(MaybeConsumer<Success> onSuccess) {}

              @Override
              public SensorAppearance getAppearance(String sensorId) {
                assertEquals(RecorderControllerTest.this.sensorId, sensorId);
                return new EmptySensorAppearance() {
                  @Override
                  public String getName(Context context) {
                    return sensorName;
                  }
                };
              }
            });

    rc.setLayoutSupplier(
        () -> {
          SensorLayoutPojo layout = new SensorLayoutPojo();
          layout.setSensorId(this.sensorId);
          return Lists.newArrayList(layout);
        });

    ArrayList<SensorTrigger> triggers = new ArrayList<>();
    rc.startObserving(
        this.sensorId,
        triggers,
        new RecordingSensorObserver(),
        new RecordingStatusListener(),
        null,
        sensorRegistry);

    Experiment experiment =
        RxDataController.createExperiment(dataController).test().values().get(0);
    rc.setSelectedExperiment(experiment);
    rc.startRecording(null, true).test().await().assertComplete();
    // must push at least one value to record
    sensor.pushValue(10, 50);
    rc.stopRecording(sensorRegistry).test().await().assertComplete();

    Experiment savedExperiment =
        RxDataController.getExperimentById(dataController, experiment.getExperimentId())
            .test()
            .values()
            .get(0);

    List<Trial> trials = savedExperiment.getTrials();
    assertEquals(1, trials.size());

    Trial trial = trials.get(0);

    List<String> sensorIds = trial.getSensorIds();
    assertEquals(Lists.newArrayList(this.sensorId), sensorIds);
    GoosciSensorAppearance.BasicSensorAppearance appearance =
        trial.getAppearances().get(sensorIds.get(0));
    assertEquals(sensorName, appearance.getName());
  }

  private Supplier<RecorderServiceConnection> connectionSupplier() {
    return () ->
        (RecorderServiceConnection)
            c -> {
              try {
                c.take(
                    new IRecorderService() {
                      @Override
                      public void beginServiceRecording(
                          String experimentName, Intent launchIntent) {}

                      @Override
                      public void endServiceRecording(
                          AppAccount appAccount,
                          boolean notifyRecordingEnded,
                          String runId,
                          String experimentId,
                          String experimentTitle) {}
                    });
              } catch (Exception e) {
                throw new RuntimeException(e);
              }
            };
  }

  private void throwIfNotNull(Throwable throwable) {
    if (throwable != null) {
      throw new RuntimeException(throwable);
    }
  }

  public static GoosciSensorSpec.SensorSpec makeSensorProto(String name) {
    return GoosciSensorSpec.SensorSpec.newBuilder()
        .setRememberedAppearance(
            GoosciSensorAppearance.BasicSensorAppearance.newBuilder().setName(name))
        .build();
  }

  private class TestTrigger extends SensorTrigger {
    int testCount = 0;

    public TestTrigger(String sensorId) {
      super(
          sensorId,
          TriggerWhen.TRIGGER_WHEN_AT,
          TriggerActionType.TRIGGER_ACTION_START_RECORDING,
          0);
    }

    public void clearTestCount() {
      testCount = 0;
    }

    public int getTestCount() {
      return testCount;
    }

    @Override
    public boolean isTriggered(double newValue) {
      testCount++;
      return false;
    }
  }

  private static Context getContext() {
    return RuntimeEnvironment.application.getApplicationContext();
  }

  private static AppAccount getAppAccount() {
    return NonSignedInAccount.getInstance(getContext());
  }
}
