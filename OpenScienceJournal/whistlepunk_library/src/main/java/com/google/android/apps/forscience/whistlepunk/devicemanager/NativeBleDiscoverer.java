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

package com.google.android.apps.forscience.whistlepunk.devicemanager;

import static com.google.android.apps.forscience.whistlepunk.sensors.MkrSciBleSensor.HANDLER_LIGHT;
import static com.google.android.apps.forscience.whistlepunk.sensors.MkrSciBleSensor.HANDLER_RAW;
import static com.google.android.apps.forscience.whistlepunk.sensors.MkrSciBleSensor.HANDLER_TEMPERATURE_CELSIUS;
import static com.google.android.apps.forscience.whistlepunk.sensors.MkrSciBleSensor.HANDLER_TEMPERATURE_FAHRENHEIT;
import static com.google.android.apps.forscience.whistlepunk.sensors.MkrSciBleSensor.SENSOR_ACCELEROMETER_X;
import static com.google.android.apps.forscience.whistlepunk.sensors.MkrSciBleSensor.SENSOR_ACCELEROMETER_Y;
import static com.google.android.apps.forscience.whistlepunk.sensors.MkrSciBleSensor.SENSOR_ACCELEROMETER_Z;
import static com.google.android.apps.forscience.whistlepunk.sensors.MkrSciBleSensor.SENSOR_CURRENT;
import static com.google.android.apps.forscience.whistlepunk.sensors.MkrSciBleSensor.SENSOR_GYROSCOPE_X;
import static com.google.android.apps.forscience.whistlepunk.sensors.MkrSciBleSensor.SENSOR_GYROSCOPE_Y;
import static com.google.android.apps.forscience.whistlepunk.sensors.MkrSciBleSensor.SENSOR_GYROSCOPE_Z;
import static com.google.android.apps.forscience.whistlepunk.sensors.MkrSciBleSensor.SENSOR_INPUT_1;
import static com.google.android.apps.forscience.whistlepunk.sensors.MkrSciBleSensor.SENSOR_INPUT_2;
import static com.google.android.apps.forscience.whistlepunk.sensors.MkrSciBleSensor.SENSOR_INPUT_3;
import static com.google.android.apps.forscience.whistlepunk.sensors.MkrSciBleSensor.SENSOR_LINEAR_ACCELEROMETER;
import static com.google.android.apps.forscience.whistlepunk.sensors.MkrSciBleSensor.SENSOR_MAGNETOMETER;
import static com.google.android.apps.forscience.whistlepunk.sensors.MkrSciBleSensor.SENSOR_RESISTANCE;
import static com.google.android.apps.forscience.whistlepunk.sensors.MkrSciBleSensor.SENSOR_VOLTAGE;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.os.ParcelUuid;
import androidx.annotation.VisibleForTesting;
import androidx.fragment.app.FragmentManager;
import com.google.android.apps.forscience.ble.DeviceDiscoverer;
import com.google.android.apps.forscience.ble.MkrSciBleManager;
import com.google.android.apps.forscience.javalib.FailureListener;
import com.google.android.apps.forscience.whistlepunk.PermissionUtils;
import com.google.android.apps.forscience.whistlepunk.R;
import com.google.android.apps.forscience.whistlepunk.SensorProvider;
import com.google.android.apps.forscience.whistlepunk.accounts.AppAccount;
import com.google.android.apps.forscience.whistlepunk.api.scalarinput.InputDeviceSpec;
import com.google.android.apps.forscience.whistlepunk.data.GoosciSensorSpec;
import com.google.android.apps.forscience.whistlepunk.metadata.BleSensorSpec;
import com.google.android.apps.forscience.whistlepunk.metadata.ExternalSensorSpec;
import com.google.android.apps.forscience.whistlepunk.metadata.MkrSciBleDeviceSpec;
import com.google.android.apps.forscience.whistlepunk.metadata.MkrSciBleSensorSpec;
import com.google.android.apps.forscience.whistlepunk.sensorapi.SensorChoice;
import com.google.android.apps.forscience.whistlepunk.sensors.BleServiceSpec;
import com.google.android.apps.forscience.whistlepunk.sensors.BluetoothSensor;
import com.google.android.apps.forscience.whistlepunk.sensors.MkrSciBleSensor;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Discovers BLE sensors that speak our "native" Science Journal protocol. */
public class NativeBleDiscoverer implements SensorDiscoverer {

  private static final SensorProvider PROVIDER =
      new SensorProvider() {
        @Override
        public SensorChoice buildSensor(String sensorId, ExternalSensorSpec spec) {
          if (spec instanceof MkrSciBleSensorSpec) {
            return new MkrSciBleSensor(sensorId, (MkrSciBleSensorSpec) spec);
          } else {
            return new BluetoothSensor(
                sensorId, (BleSensorSpec) spec, BluetoothSensor.ANNING_SERVICE_SPEC);
          }
        }

        @Override
        public ExternalSensorSpec buildSensorSpec(String name, byte[] config) {
          try {
            return MkrSciBleSensor.validateSpec(new MkrSciBleSensorSpec(name, config));
          } catch (Exception e) {
            return new BleSensorSpec(name, config);
          }
        }
      };

  private static final String SERVICE_ID = "com.google.android.apps.forscience.whistlepunk.ble";

  private DeviceDiscoverer deviceDiscoverer;
  private Runnable onScanDone;
  private Context context;

  public NativeBleDiscoverer(Context context) {
    this.context = context;
  }

  @Override
  public SensorProvider getProvider() {
    return PROVIDER;
  }

  @Override
  public boolean startScanning(final ScanListener listener, FailureListener onScanError) {
    stopScanning();

    // BLE scan is only done when it times out (which is imposed from fragment)
    // TODO: consider making that timeout internal (like it is for API sensor services)
    onScanDone =
        new Runnable() {
          @Override
          public void run() {
            listener.onServiceScanComplete(SERVICE_ID);
            listener.onScanDone();
          }
        };

    deviceDiscoverer = createDiscoverer(context);
    final boolean canScan = deviceDiscoverer.canScan() && hasScanPermission();

    listener.onServiceFound(
        new DiscoveredService() {
          @Override
          public String getServiceId() {
            return SERVICE_ID;
          }

          @Override
          public String getName() {
            return context.getString(R.string.arduino_boards);
          }

          @Override
          public Drawable getIconDrawable(Context context) {
            return context.getResources().getDrawable(R.drawable.ic_bluetooth_white_24dp);
          }

          @Override
          public ServiceConnectionError getConnectionErrorIfAny() {
            if (canScan) {
              return null;
            } else {
              return new ServiceConnectionError() {
                @Override
                public String getErrorMessage() {
                  return context.getString(R.string.btn_enable_bluetooth);
                }

                @Override
                public boolean canBeResolved() {
                  return true;
                }

                @Override
                public void tryToResolve(FragmentManager fragmentManager) {
                  ScanDisabledDialogFragment.newInstance()
                      .show(fragmentManager, "scanDisabledDialog");
                }
              };
            }
          }
        });

    if (!canScan) {
      stopScanning();
      return false;
    }
    List<ParcelUuid> uuids = new ArrayList<>();
    uuids.add(ParcelUuid.fromString(MkrSciBleManager.SERVICE_UUID));
    for (BleServiceSpec spec : BluetoothSensor.SUPPORTED_SERVICES) {
      uuids.add(ParcelUuid.fromString(spec.getServiceId().toString()));
    }
    deviceDiscoverer.startScanning(
        uuids.toArray(new ParcelUuid[0]),
        new DeviceDiscoverer.Callback() {
          @Override
          public void onDeviceFound(final DeviceDiscoverer.DeviceRecord record) {
            if (record.device.getServiceUuids().contains(MkrSciBleManager.SERVICE_UUID)) {
              onMkrSciDeviceRecordFound(record, listener);
            } else {
              onDeviceRecordFound(record, listener);
            }
          }

          @Override
          public void onError(int error) {}
        });

    return true;
  }

  @VisibleForTesting
  protected boolean hasScanPermission() {
    return PermissionUtils.hasPermission(context, PermissionUtils.REQUEST_ACCESS_COARSE_LOCATION);
  }

  protected DeviceDiscoverer createDiscoverer(Context context) {
    return DeviceDiscoverer.getNewInstance(context);
  }

  @Override
  public void stopScanning() {
    if (deviceDiscoverer != null) {
      deviceDiscoverer.stopScanning();
      deviceDiscoverer = null;
    }
    if (onScanDone != null) {
      onScanDone.run();
      onScanDone = null;
    }
  }

  private void onDeviceRecordFound(
      DeviceDiscoverer.DeviceRecord record, ScanListener scanListener) {
    WhistlepunkBleDevice device = record.device;
    String address = device.getAddress();

    // sensorScanCallbacks will handle duplicates
    final BleSensorSpec spec = new BleSensorSpec(address, device.getName());

    scanListener.onDeviceFound(
        new DiscoveredDevice() {
          @Override
          public String getServiceId() {
            return SERVICE_ID;
          }

          @Override
          public InputDeviceSpec getSpec() {
            return DeviceRegistry.createHoldingDevice(spec);
          }
        });

    scanListener.onSensorFound(
        new DiscoveredSensor() {

          @Override
          public GoosciSensorSpec.SensorSpec getSensorSpec() {
            return spec.asGoosciSpec();
          }

          @Override
          public SettingsInterface getSettingsInterface() {
            return new SettingsInterface() {
              @Override
              public void show(
                  AppAccount appAccount,
                  String experimentId,
                  String sensorId,
                  FragmentManager fragmentManager,
                  boolean showForgetButton) {
                DeviceOptionsDialog dialog =
                    DeviceOptionsDialog.newInstance(
                        appAccount, experimentId, sensorId, null, showForgetButton);
                dialog.show(fragmentManager, "edit_device");
              }
            };
          }

          @Override
          public boolean shouldReplaceStoredSensor(ConnectableSensor oldSensor) {
            // The current implementation of this discoverer does not notice when it detects
            // a sensor that has already been customized in the database, so we should not
            // overwrite local customizations
            return false;
          }
        });
  }

  private void onMkrSciDeviceRecordFound(
      DeviceDiscoverer.DeviceRecord record, ScanListener scanListener) {
    WhistlepunkBleDevice device = record.device;

    final String address = device.getAddress();

    // sensorScanCallbacks will handle duplicates

    final MkrSciBleDeviceSpec spec = new MkrSciBleDeviceSpec(address, device.getName());

    scanListener.onDeviceFound(
        new DiscoveredDevice() {
          @Override
          public String getServiceId() {
            return SERVICE_ID;
          }

          @Override
          public InputDeviceSpec getSpec() {
            return DeviceRegistry.createHoldingDevice(spec);
          }
        });

    addMkrSciSensor(scanListener, address, SENSOR_INPUT_1, context.getString(R.string.input_1));
    addMkrSciSensor(scanListener, address, SENSOR_INPUT_2, context.getString(R.string.input_2));
    addMkrSciSensor(scanListener, address, SENSOR_INPUT_3, context.getString(R.string.input_3));
    addMkrSciSensor(scanListener, address, SENSOR_VOLTAGE, context.getString(R.string.voltage));
    addMkrSciSensor(scanListener, address, SENSOR_CURRENT, context.getString(R.string.current));
    addMkrSciSensor(
        scanListener, address, SENSOR_RESISTANCE, context.getString(R.string.resistance));
    addMkrSciSensor(
        scanListener, address, SENSOR_ACCELEROMETER_X, context.getString(R.string.acc_x));
    addMkrSciSensor(
        scanListener, address, SENSOR_ACCELEROMETER_Y, context.getString(R.string.acc_y));
    addMkrSciSensor(
        scanListener, address, SENSOR_ACCELEROMETER_Z, context.getString(R.string.acc_z));
    addMkrSciSensor(
        scanListener,
        address,
        SENSOR_LINEAR_ACCELEROMETER,
        context.getString(R.string.linear_accelerometer));
    addMkrSciSensor(scanListener, address, SENSOR_GYROSCOPE_X, context.getString(R.string.gyr_x));
    addMkrSciSensor(scanListener, address, SENSOR_GYROSCOPE_Y, context.getString(R.string.gyr_y));
    addMkrSciSensor(scanListener, address, SENSOR_GYROSCOPE_Z, context.getString(R.string.gyr_z));
    addMkrSciSensor(
        scanListener,
        address,
        SENSOR_MAGNETOMETER,
        context.getString(R.string.magnetic_field_strength));
  }

  private void addMkrSciSensor(
      ScanListener scanListener, String address, String sensor, String name) {
    final MkrSciBleSensorSpec spec = new MkrSciBleSensorSpec(address, sensor, name);
    scanListener.onSensorFound(
        new DiscoveredSensor() {
          @Override
          public GoosciSensorSpec.SensorSpec getSensorSpec() {
            return spec.asGoosciSpec();
          }

          @Override
          public SettingsInterface getSettingsInterface() {
            if (Objects.equals(sensor, SENSOR_INPUT_1)) {
              return (appAccount, experimentId, sensorId, fragmentManager, showForgetButton) ->
                  MkrSciSensorOptionsDialog.newInstance(
                          appAccount,
                          experimentId,
                          sensorId,
                          new String[] {
                            HANDLER_TEMPERATURE_CELSIUS,
                            HANDLER_TEMPERATURE_FAHRENHEIT,
                            HANDLER_LIGHT,
                            HANDLER_RAW
                          },
                          3)
                      .show(fragmentManager, "edit_sensor_input1");
            }
            if (Objects.equals(sensor, SENSOR_INPUT_2)) {
              return (appAccount, experimentId, sensorId, fragmentManager, showForgetButton) ->
                  MkrSciSensorOptionsDialog.newInstance(
                          appAccount,
                          experimentId,
                          sensorId,
                          new String[] {HANDLER_LIGHT, HANDLER_RAW},
                          1)
                      .show(fragmentManager, "edit_sensor_input2");
            }
            return null;
          }

          @Override
          public boolean shouldReplaceStoredSensor(ConnectableSensor oldSensor) {
            return false;
          }
        });
  }
}
