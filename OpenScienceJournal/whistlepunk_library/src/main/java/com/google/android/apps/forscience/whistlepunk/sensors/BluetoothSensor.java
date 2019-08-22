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

package com.google.android.apps.forscience.whistlepunk.sensors;

import android.content.Context;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import android.util.Log;
import com.google.android.apps.forscience.ble.BleFlow;
import com.google.android.apps.forscience.ble.BleFlowListener;
import com.google.android.apps.forscience.whistlepunk.AppSingleton;
import com.google.android.apps.forscience.whistlepunk.Clock;
import com.google.android.apps.forscience.whistlepunk.PacketAssembler;
import com.google.android.apps.forscience.whistlepunk.data.GoosciSensor;
import com.google.android.apps.forscience.whistlepunk.data.GoosciSensor.AnalogPin;
import com.google.android.apps.forscience.whistlepunk.data.GoosciSensor.DigitalPin;
import com.google.android.apps.forscience.whistlepunk.data.GoosciSensor.Interval;
import com.google.android.apps.forscience.whistlepunk.data.GoosciSensor.Pin;
import com.google.android.apps.forscience.whistlepunk.data.GoosciSensor.VirtualPin;
import com.google.android.apps.forscience.whistlepunk.data.GoosciSensorConfig;
import com.google.android.apps.forscience.whistlepunk.devicemanager.PinTypeProvider;
import com.google.android.apps.forscience.whistlepunk.metadata.BleSensorSpec;
import com.google.android.apps.forscience.whistlepunk.sensorapi.AbstractSensorRecorder;
import com.google.android.apps.forscience.whistlepunk.sensorapi.ScalarSensor;
import com.google.android.apps.forscience.whistlepunk.sensorapi.SensorEnvironment;
import com.google.android.apps.forscience.whistlepunk.sensorapi.SensorRecorder;
import com.google.android.apps.forscience.whistlepunk.sensorapi.SensorStatusListener;
import com.google.android.apps.forscience.whistlepunk.sensorapi.StreamConsumer;
import com.google.android.apps.forscience.whistlepunk.sensorapi.ValueFilter;
import io.reactivex.Single;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.Executor;

public class BluetoothSensor extends ScalarSensor {
  private static final String TAG = "BluetoothSensor";

  // There may be someday additional BLE services that we natively read in Science Journal
  // However, in the anning release, there was just one, a single stream of timestamped
  // scalar values.  In the code, we'll refer to that as the "Anning service".

  // Details in https://goto.google.com/goosci-ble
  private static UUID ANNING_SERV = UUID.fromString("555a0001-0aaa-467a-9538-01f0652c74e8");
  private static UUID ANNING_VALUE = UUID.fromString("555a0003-0aaa-467a-9538-01f0652c74e8");
  private static UUID ANNING_SETTING = UUID.fromString("555a0010-0aaa-467a-9538-01f0652c74e8");
  private static UUID ANNING_VERSION = UUID.fromString("555a0011-0aaa-467a-9538-01f0652c74e8");

  public static final BleServiceSpec ANNING_SERVICE_SPEC =
      new BleServiceSpec(ANNING_SERV, ANNING_VALUE, ANNING_SETTING, ANNING_VERSION);
  public static final BleServiceSpec[] SUPPORTED_SERVICES =
      new BleServiceSpec[] {ANNING_SERVICE_SPEC};

  private static final long DEFAULT_FREQUENCY_WINDOW = 2000;
  private static final float DEFAULT_FREQUENCY_FILTER = 0;
  private final BleSensorSpec sensor;
  private final BleServiceSpec serviceSpec;
  private boolean deviceFrequencyEnabled;
  private boolean notificationSubscribed = false;

  private String address;
  private GoosciSensorConfig.BleSensorConfig.ScaleTransform deviceScaleTransform;

  private BleFlowListener bleFlowListener;

  public BluetoothSensor(String sensorId, BleSensorSpec sensor, BleServiceSpec serviceSpec) {
    this(sensorId, sensor, serviceSpec, AppSingleton.getUiThreadExecutor());
  }

  public BluetoothSensor(
      String sensorId,
      BleSensorSpec sensor,
      BleServiceSpec serviceSpec,
      Executor uiThreadExecutor) {
    super(sensorId, uiThreadExecutor);
    this.sensor = sensor;
    this.serviceSpec = serviceSpec;
    address = sensor.getAddress();
    readConfigurationFrom(sensor);
  }

  private BleFlowListener createBleFlowListener(
      final StreamConsumer c,
      final Clock defaultClock,
      final SensorStatusListener listener,
      BleFlow flow) {
    return new BleFlowListener() {
      final PacketAssembler pa =
          new PacketAssembler(
              defaultClock,
              new PacketAssembler.Listener() {
                @Override
                public void onError(@SensorStatusListener.Error int error, String message) {
                  listener.onSourceError(getId(), error, message);
                }

                @Override
                public void onDataParsed(long timeStampMs, double data) {
                  c.addData(timeStampMs, data);
                }
              });

      @Override
      public void onSuccess() {}

      @Override
      public void onFailure(Exception error) {
        Log.d(TAG, "Failure " + error.getMessage());
        listener.onSourceError(
            getId(), SensorStatusListener.ERROR_UNKNOWN, error.getLocalizedMessage());
        flow.resetAndAddListener(bleFlowListener, true).disconnect();
        BleFlow.run(flow);
      }

      @Override
      public void onCharacteristicRead(UUID characteristic, int flags, byte[] value) {
        // This is where we catch the characteristic static value
        // For Value (UUID:555a0003), it's a protobuf
        // For Description (UUID:555a0002), it's a string desc (eg. "Goosci Windmill")
        if (characteristic.compareTo(serviceSpec.getVersionId()) == 0) {
          BleProtocolVersion protocolVersion = new BleProtocolVersion(value);
          switch (protocolVersion.getMajorVersion()) {
              // Currently no version requires a special connection sequence
            default:
              writeConfigAndSetNotification(flow);
          }
        }
      }

      @Override
      public void onNotification(UUID characteristic, int flags, byte[] value) {
        pa.append(value);
      }

      @Override
      public void onDisconnect() {
        notificationSubscribed = false;
        listener.onSourceStatus(getId(), SensorStatusListener.STATUS_DISCONNECTED);
      }

      @Override
      public void onConnect() {
        listener.onSourceStatus(getId(), SensorStatusListener.STATUS_CONNECTED);
      }

      @Override
      public void onNotificationSubscribed() {
        notificationSubscribed = true;
      }

      @Override
      public void onNotificationUnsubscribed() {
        notificationSubscribed = false;
        flow.resetAndAddListener(bleFlowListener, true).disconnect();
        BleFlow.run(flow);
      }

      @Override
      public void onServicesDiscovered() {
        if (flow.isCharacteristicValid(serviceSpec.getServiceId(), serviceSpec.getVersionId())) {
          flow.lookupCharacteristic(serviceSpec.getServiceId(), serviceSpec.getVersionId()).read();
          BleFlow.run(flow);
        } else {
          writeConfigAndSetNotification(flow);
        }
      }
    };
  }

  @Override
  protected boolean getDefaultFrequencyChecked() {
    return deviceFrequencyEnabled;
  }

  @Nullable
  protected GoosciSensorConfig.BleSensorConfig.ScaleTransform getDefaultScaleTransform() {
    return deviceScaleTransform;
  }

  private byte[] buildConfigProtoForDevice(BleSensorSpec sensor) {
    GoosciSensor.SensorDataRequest.Builder sdr =
        GoosciSensor.SensorDataRequest.newBuilder()
            .setTimestampKey(42) // arbitrary constant.  TMOLTUAE.
            .setInterval(Interval.newBuilder().setCount(1).setFrequency(20));
    Pin.Builder pin = Pin.newBuilder();
    String pinName = sensor.getPin();
    PinTypeProvider pinTypeProvider = new PinTypeProvider();
    PinTypeProvider.PinType pinType = pinTypeProvider.parsePinName(pinName);
    if (pinType == null) {
      Log.e(TAG, "Failed to parse pin: " + pinName);
      return null;
    } else if (pinType.getPinSignalType() == PinTypeProvider.PinSignalType.ANALOG) {
      AnalogPin ap = AnalogPin.newBuilder().setPin(pinType.getPinNumber()).build();
      pin.setAnalogPin(ap);
    } else if (pinType.getPinSignalType() == PinTypeProvider.PinSignalType.DIGITAL) {
      DigitalPin dp = DigitalPin.newBuilder().setPin(pinType.getPinNumber()).build();
      pin.setDigitalPin(dp);
    } else if (pinType.getPinSignalType() == PinTypeProvider.PinSignalType.VIRTUAL) {
      VirtualPin vp = VirtualPin.newBuilder().setPin(pinType.getPinNumber()).build();
      pin.setVirtualPin(vp);
    }
    // Note the following comment entered during a previous code review:
    // for later, i'd rewrite this as sdr.pin = new Pin[] { pin };
    // it'll save you a conformance violation when migrating sdr.
    sdr.addPin(pin);

    byte[] value = sdr.build().toByteArray();
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    try {
      byte v = (byte) value.length;
      outputStream.write(v);
      outputStream.write((byte) 1);
      outputStream.write(value);

    } catch (IOException e) {
      Log.e(TAG, "Failed to append byte arrays: " + e.getMessage());
    }
    return outputStream.toByteArray();
  }

  private void writeConfigAndSetNotification(BleFlow flow) {
    byte[] sensorConfig = buildConfigProtoForDevice(sensor);
    if (sensorConfig != null
        && flow.isCharacteristicValid(serviceSpec.getServiceId(), serviceSpec.getSettingId())) {
      flow.lookupCharacteristic(serviceSpec.getServiceId(), serviceSpec.getSettingId())
          .write(sensorConfig);
    }
    flow.lookupCharacteristic(serviceSpec.getServiceId(), serviceSpec.getValueId())
        .enableNotification();
    BleFlow.run(flow);
  }

  private void readConfigurationFrom(BleSensorSpec bleSensor) {
    deviceFrequencyEnabled = bleSensor.getFrequencyEnabled();
    deviceScaleTransform = bleSensor.getScaleTransform();
    setScalarFilter(getDeviceDefaultValueFilter());
  }

  @VisibleForTesting
  public ValueFilter getDeviceDefaultValueFilter() {
    return computeValueFilter(
        DEFAULT_FREQUENCY_WINDOW,
        DEFAULT_FREQUENCY_FILTER,
        getDefaultFrequencyChecked(),
        getDefaultScaleTransform());
  }

  @Override
  protected SensorRecorder makeScalarControl(
      final StreamConsumer c,
      final SensorEnvironment environment,
      Context context,
      final SensorStatusListener listener) {
    Single<BleFlow> whenFlow =
        environment.getConnectedBleClient().map(client -> client.getFlowFor(address)).cache();

    return new AbstractSensorRecorder() {
      @Override
      public void startObserving() {
        // make BLE connection
        listener.onSourceStatus(getId(), SensorStatusListener.STATUS_CONNECTING);
        whenFlow.subscribe(
            flow -> {
              bleFlowListener =
                  createBleFlowListener(c, environment.getDefaultClock(), listener, flow);
              flow.resetAndAddListener(bleFlowListener, true)
                  .connect()
                  .lookupService(serviceSpec.getServiceId());
              BleFlow.run(flow);
            });
      }

      @Override
      public void stopObserving() {
        whenFlow.subscribe(
            flow -> {
              // Don't reset service map: should still be valid from above, and it doesn't work
              // on ChromeBooks
              flow.resetAndAddListener(bleFlowListener, false);
              if (notificationSubscribed) {
                flow.lookupService(serviceSpec.getServiceId())
                    .lookupCharacteristic(serviceSpec.getServiceId(), serviceSpec.getValueId())
                    .disableNotification();
                BleFlow.run(flow);
              } else {
                flow.disconnect();
                BleFlow.run(flow);
              }
            });
      }
    };
  }
}
