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
package com.google.android.apps.forscience.whistlepunk.api.scalarinput;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import android.content.Context;
import android.os.RemoteException;
import androidx.annotation.NonNull;
import com.google.android.apps.forscience.javalib.Consumer;
import com.google.android.apps.forscience.whistlepunk.Arbitrary;
import com.google.android.apps.forscience.whistlepunk.MockScheduler;
import com.google.android.apps.forscience.whistlepunk.RecordingStatusListener;
import com.google.android.apps.forscience.whistlepunk.SensorProvider;
import com.google.android.apps.forscience.whistlepunk.TestData;
import com.google.android.apps.forscience.whistlepunk.accounts.AppAccount;
import com.google.android.apps.forscience.whistlepunk.accounts.NonSignedInAccount;
import com.google.android.apps.forscience.whistlepunk.scalarchart.ChartData;
import com.google.android.apps.forscience.whistlepunk.sensorapi.MemorySensorEnvironment;
import com.google.android.apps.forscience.whistlepunk.sensorapi.RecordingSensorObserver;
import com.google.android.apps.forscience.whistlepunk.sensorapi.SensorChoice;
import com.google.android.apps.forscience.whistlepunk.sensorapi.SensorRecorder;
import com.google.android.apps.forscience.whistlepunk.sensorapi.SensorStatusListener;
import com.google.android.apps.forscience.whistlepunk.sensordb.InMemorySensorDatabase;
import com.google.common.collect.Lists;
import com.google.common.util.concurrent.MoreExecutors;
import java.util.List;
import java.util.concurrent.Executor;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

@RunWith(RobolectricTestRunner.class)
public class ScalarInputProviderTest {
  private final RecordingSensorObserver observer = new RecordingSensorObserver();
  private final RecordingStatusListener listener = new RecordingStatusListener();
  private Executor executor = MoreExecutors.directExecutor();

  @Test
  public void grabDataFromService() {
    final List<ChartData.DataPoint> dataToSend = makeData();
    final String sensorId = Arbitrary.string();
    final String sensorAddress = Arbitrary.string();
    final String serviceId = Arbitrary.string();

    Consumer<AppDiscoveryCallbacks> finder =
        new Consumer<AppDiscoveryCallbacks>() {
          @Override
          public void take(AppDiscoveryCallbacks adc) {
            adc.onServiceFound(
                serviceId, new TestDiscoverer(new TestConnector(dataToSend, sensorAddress)));
          }
        };
    SensorProvider provider = new ScalarInputProvider(finder, null, executor, new MockScheduler());

    SensorChoice sensor = provider.buildSensor(sensorId, makeSpec(sensorAddress, serviceId));
    SensorRecorder recorder = createRecorder(sensor);
    recorder.startObserving();
    Integer integer = listener.mostRecentStatuses.get(sensorId);
    assertNotNull(sensorId + sensorAddress + listener.mostRecentStatuses.keySet(), integer);
    assertEquals(SensorStatusListener.STATUS_CONNECTED, (int) integer);
    recorder.stopObserving();
    assertEquals(
        SensorStatusListener.STATUS_DISCONNECTED, (int) listener.mostRecentStatuses.get(sensorId));
    listener.assertNoErrors();
    TestData.fromPoints(dataToSend).checkObserver(observer);
  }

  @Test
  public void reportErrors() {
    final String sensorId = Arbitrary.string();
    final String sensorAddress = Arbitrary.string();
    final String serviceId = Arbitrary.string();
    final String errorMessage = Arbitrary.string();

    Consumer<AppDiscoveryCallbacks> finder =
        new Consumer<AppDiscoveryCallbacks>() {
          @Override
          public void take(AppDiscoveryCallbacks adc) {
            adc.onServiceFound(
                serviceId,
                new TestDiscoverer(
                    new ISensorConnector.Stub() {
                      @Override
                      public void startObserving(
                          String sensorId,
                          ISensorObserver observer,
                          ISensorStatusListener listener,
                          String settingsKey)
                          throws RemoteException {
                        listener.onSensorError(errorMessage);
                      }

                      @Override
                      public void stopObserving(String sensorId) throws RemoteException {}
                    }));
          }
        };

    SensorProvider provider = new ScalarInputProvider(finder, null, executor, new MockScheduler());
    SensorChoice sensor = provider.buildSensor(sensorId, makeSpec(sensorAddress, serviceId));
    SensorRecorder recorder = createRecorder(sensor);
    recorder.startObserving();
    this.listener.assertErrors(errorMessage);
  }

  @Test
  public void properServiceNotInstalled() {
    final List<ChartData.DataPoint> dataToSend = makeData();
    final String sensorId = Arbitrary.string();
    final String sensorAddress = Arbitrary.string();
    final String serviceId = Arbitrary.string();

    Consumer<AppDiscoveryCallbacks> finder =
        new Consumer<AppDiscoveryCallbacks>() {
          @Override
          public void take(AppDiscoveryCallbacks adc) {
            adc.onServiceFound(
                serviceId + "wrong!", new TestDiscoverer(new TestConnector(dataToSend, sensorId)));
            adc.onDiscoveryDone();
          }
        };

    SensorProvider provider =
        new ScalarInputProvider(
            finder,
            new ScalarInputStringSource() {
              @Override
              public String generateCouldNotFindServiceErrorMessage(String serviceId) {
                return "Could not find service: " + serviceId;
              }

              @Override
              public String generateConnectionTimeoutMessage() {
                return "Connection timeout";
              }
            },
            executor,
            new MockScheduler());
    SensorChoice sensor = provider.buildSensor(sensorId, makeSpec(sensorAddress, serviceId));
    SensorRecorder recorder = createRecorder(sensor);
    recorder.startObserving();
    listener.assertErrors("Could not find service: " + serviceId);
  }

  @NonNull
  private ScalarInputSpec makeSpec(String sensorAddress, String serviceId) {
    return new ScalarInputSpec("sensorName", serviceId, sensorAddress, null, null, "deviceId");
  }

  @Test
  public void findCorrectServiceOfSeveral() {
    final List<ChartData.DataPoint> dataToSend = makeData();
    final String sensorId = Arbitrary.string();
    final String sensorAddress = Arbitrary.string();
    final String serviceId = Arbitrary.string();

    final List<ChartData.DataPoint> wrongPoints = makeData();

    Consumer<AppDiscoveryCallbacks> finder =
        new Consumer<AppDiscoveryCallbacks>() {
          @Override
          public void take(AppDiscoveryCallbacks adc) {
            adc.onServiceFound(
                serviceId, new TestDiscoverer(new TestConnector(dataToSend, sensorAddress)));
            adc.onServiceFound(
                serviceId + "wrong!",
                new TestDiscoverer(new TestConnector(wrongPoints, sensorAddress)));
          }
        };

    SensorProvider provider = new ScalarInputProvider(finder, null, executor, new MockScheduler());
    SensorChoice sensor =
        provider.buildSensor(
            sensorId,
            new ScalarInputSpec("sensorName", serviceId, sensorAddress, null, null, "devId"));
    SensorRecorder recorder = createRecorder(sensor);
    recorder.startObserving();
    recorder.stopObserving();
    TestData.fromPoints(dataToSend).checkObserver(observer);
  }

  @NonNull
  private List<ChartData.DataPoint> makeData() {
    long value = Math.abs(Arbitrary.longInteger());
    return Lists.newArrayList(new ChartData.DataPoint(value, value));
  }

  private SensorRecorder createRecorder(SensorChoice sensor) {
    return sensor.createRecorder(null, getAppAccount(), observer, listener, createEnvironment());
  }

  @NonNull
  private MemorySensorEnvironment createEnvironment() {
    return new MemorySensorEnvironment(
        new InMemorySensorDatabase().makeSimpleRecordingController(), null, null, null);
  }

  private static AppAccount getAppAccount() {
    Context context = RuntimeEnvironment.application.getApplicationContext();
    return NonSignedInAccount.getInstance(context);
  }
}
