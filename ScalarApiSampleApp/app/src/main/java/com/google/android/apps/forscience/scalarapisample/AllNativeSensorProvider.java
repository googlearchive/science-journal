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
package com.google.android.apps.forscience.scalarapisample;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Build;
import androidx.annotation.NonNull;
import com.google.android.apps.forscience.whistlepunk.api.scalarinput.AdvertisedDevice;
import com.google.android.apps.forscience.whistlepunk.api.scalarinput.AdvertisedSensor;
import com.google.android.apps.forscience.whistlepunk.api.scalarinput.ScalarSensorService;
import com.google.android.apps.forscience.whistlepunk.api.scalarinput.SensorAppearanceResources;
import com.google.android.apps.forscience.whistlepunk.api.scalarinput.SensorBehavior;
import com.google.common.collect.Lists;
import java.util.ArrayList;
import java.util.List;

public class AllNativeSensorProvider extends ScalarSensorService {
  public static final String DEVICE_ID = "onlyDevice";

  /**
   * If true, only allow Google-built versions of Science Journal to access these sensors. Edit to
   * false in your local version if you want to allow custom apps or open-source Science Journal to
   * connect.
   *
   * <p>But beware, if this is left as false in a publicly-released service, a malicious third-party
   * client app could use this service to get access to device sensors without permission.
   */
  private static final boolean ONLY_ALLOW_SCIENCE_JOURNAL = true;

  @Override
  protected boolean shouldCheckBinderSignature() {
    return ONLY_ALLOW_SCIENCE_JOURNAL;
  }

  @Override
  protected List<? extends AdvertisedDevice> getDevices() {
    return Lists.newArrayList(
        new AdvertisedDevice(DEVICE_ID, "Phone native sensors") {
          @Override
          public List<AdvertisedSensor> getSensors() {
            return buildSensors();
          }
        });
  }

  private List<AdvertisedSensor> buildSensors() {
    List<AdvertisedSensor> sensors = new ArrayList<>();
    final List<Sensor> deviceSensors = getSensorManager().getSensorList(Sensor.TYPE_ALL);
    for (final Sensor sensor : deviceSensors) {
      final SensorAppearanceResources appearance = new SensorAppearanceResources();
      String name = sensor.getName();

      final SensorBehavior behavior = new SensorBehavior();
      behavior.loggingId = name;
      behavior.settingsIntent =
          DeviceSettingsPopupActivity.getPendingIntent(AllNativeSensorProvider.this, sensor);

      final int sensorType = sensor.getType();
      if (sensorType == Sensor.TYPE_ACCELEROMETER) {
        appearance.iconId = android.R.drawable.ic_media_ff;
        appearance.units = "ms/2";
        appearance.shortDescription = "Not really a 3-axis accelerometer";
        behavior.shouldShowSettingsOnConnect = true;
      }

      if (isTemperature(sensorType)) {
        appearance.iconId = android.R.drawable.star_on;
        String unitString =
            TemperatureSettingsPopupActivity.getUnitString(AllNativeSensorProvider.this);
        appearance.units = unitString;
        appearance.shortDescription = "Ambient temperature (settings to change units!)";
        behavior.settingsIntent =
            TemperatureSettingsPopupActivity.getPendingIntent(AllNativeSensorProvider.this, sensor);
      }

      String sensorAddress = "" + sensorType;
      sensors.add(
          new AdvertisedSensor(sensorAddress, name) {
            private SensorEventListener mSensorEventListener;

            @Override
            protected SensorAppearanceResources getAppearance() {
              return appearance;
            }

            @Override
            protected SensorBehavior getBehavior() {
              return behavior;
            }

            @Override
            protected boolean connect() throws Exception {
              unregister();
              return true;
            }

            @Override
            protected void streamData(final DataConsumer c) {
              final int index =
                  DeviceSettingsPopupActivity.getIndexForSensorType(
                      sensorType, AllNativeSensorProvider.this);
              mSensorEventListener = new HardwareEventListener(sensorType, index, c);
              getSensorManager()
                  .registerListener(mSensorEventListener, sensor, SensorManager.SENSOR_DELAY_UI);
            }

            @Override
            protected void disconnect() {
              unregister();
            }

            private void unregister() {
              if (mSensorEventListener != null) {
                getSensorManager().unregisterListener(mSensorEventListener);
                mSensorEventListener = null;
              }
            }
          });
    }

    return sensors;
  }

  @Override
  @NonNull
  protected String getDiscovererName() {
    return "AllNative";
  }

  private boolean isTemperature(int sensorType) {
    return sensorType == Sensor.TYPE_AMBIENT_TEMPERATURE || isNexus6pThermometer(sensorType);
  }

  private boolean isNexus6pThermometer(int sensorType) {
    return isNexus6p() && sensorType == 65536;
  }

  private boolean isNexus6p() {
    return Build.MODEL.equals("angler") || Build.MODEL.equals("Nexus 6P");
  }

  private SensorManager getSensorManager() {
    return (SensorManager) getSystemService(Context.SENSOR_SERVICE);
  }

  private class HardwareEventListener implements SensorEventListener {
    private final int mSensorType;
    private final int mSensorValueIndex;
    private final AdvertisedSensor.DataConsumer mDataConsumer;

    HardwareEventListener(
        int sensorType, int sensorValueIndex, AdvertisedSensor.DataConsumer dataConsumer) {
      mSensorType = sensorType;
      mSensorValueIndex = sensorValueIndex;
      mDataConsumer = dataConsumer;
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
      if (mDataConsumer.isReceiving()) {
        long timestamp = System.currentTimeMillis();
        float value = event.values[mSensorValueIndex];
        mDataConsumer.onNewData(timestamp, maybeModify(value));
      }
    }

    private float maybeModify(float value) {
      if (isTemperature(mSensorType)
          && !TemperatureSettingsPopupActivity.isCelsius(AllNativeSensorProvider.this)) {
        return value * 1.8f + 32f;
      }
      return value;
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
      // do nothing
    }
  }
}
