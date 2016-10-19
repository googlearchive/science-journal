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
package com.google.android.apps.forscience.samplegyroprovider;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Build;
import android.os.DeadObjectException;
import android.os.RemoteException;
import android.support.annotation.Nullable;
import android.util.Log;

import com.google.android.apps.forscience.whistlepunk.api.scalarinput.IDeviceConsumer;
import com.google.android.apps.forscience.whistlepunk.api.scalarinput.ISensorConnector;
import com.google.android.apps.forscience.whistlepunk.api.scalarinput.ISensorConsumer;
import com.google.android.apps.forscience.whistlepunk.api.scalarinput.ISensorDiscoverer;
import com.google.android.apps.forscience.whistlepunk.api.scalarinput.ISensorObserver;
import com.google.android.apps.forscience.whistlepunk.api.scalarinput.ISensorStatusListener;
import com.google.android.apps.forscience.whistlepunk.api.scalarinput.SensorAppearanceResources;
import com.google.android.apps.forscience.whistlepunk.api.scalarinput.SensorBehavior;

import java.util.List;

public class AllNativeSensorProvider extends Service {
    public static final String DEVICE_ID = "onlyDevice";
    private static final String TAG = "AllNativeSensorProvider";

    @Override
    public void onCreate() {
        super.onCreate();
    }

    private ISensorDiscoverer.Stub mDiscoverer = null;

    @Nullable
    @Override
    public ISensorDiscoverer.Stub onBind(Intent intent) {
        return getDiscoverer();
    }

    public ISensorDiscoverer.Stub getDiscoverer() {
        if (mDiscoverer == null) {
            mDiscoverer = createDiscoverer();
        }
        return mDiscoverer;
    }

    private ISensorDiscoverer.Stub createDiscoverer() {
        final List<Sensor> sensors = getSensorManager().getSensorList(Sensor.TYPE_ALL);
        return new ISensorDiscoverer.Stub() {
            @Override
            public String getName() throws RemoteException {
                return "AllNative";
            }

            @Override
            public void scanDevices(IDeviceConsumer c) throws RemoteException {
                c.onDeviceFound(DEVICE_ID, "Phone native sensors", null);
            }

            @Override
            public void scanSensors(String deviceId, ISensorConsumer c) throws RemoteException {
                if (!DEVICE_ID.equals(deviceId)) {
                    return;
                }
                for (Sensor sensor : sensors) {
                    SensorAppearanceResources appearance = new SensorAppearanceResources();
                    String name = sensor.getName();

                    SensorBehavior behavior = new SensorBehavior();
                    behavior.loggingId = name;
                    behavior.settingsIntent = DeviceSettingsPopupActivity.getPendingIntent(
                            AllNativeSensorProvider.this, sensor);

                    int sensorType = sensor.getType();
                    if (sensorType == Sensor.TYPE_ACCELEROMETER) {
                        appearance.iconId = android.R.drawable.ic_media_ff;
                        appearance.units = "ms/2";
                        appearance.shortDescription = "Not really a 3-axis accelerometer";
                        behavior.shouldShowSettingsOnConnect = true;
                    }

                    if (isTemperature(sensorType)) {
                        appearance.iconId = android.R.drawable.star_on;
                        String unitString = TemperatureSettingsPopupActivity.getUnitString(
                                AllNativeSensorProvider.this);
                        appearance.units = unitString;
                        appearance.shortDescription =
                                "Ambient temperature (settings to change units!)";
                        behavior.settingsIntent = TemperatureSettingsPopupActivity.getPendingIntent(
                                AllNativeSensorProvider.this, sensor);
                    }

                    String sensorAddress = "" + sensorType;
                    c.onSensorFound(sensorAddress, name, behavior, appearance);
                }
            }

            @Override
            public ISensorConnector getConnector() throws RemoteException {
                return new ISensorConnector.Stub() {
                    private ISensorStatusListener mListener;
                    private SensorEventListener mSensorEventListener;

                    @Override
                    public void startObserving(final String sensorId,
                            final ISensorObserver observer, final ISensorStatusListener listener,
                            String settingsKey) throws RemoteException {
                        mListener = listener;
                        listener.onSensorConnected();
                        unregister();
                        final int sensorType = getSensorType(sensorId, listener);
                        if (sensorType < 0) {
                            return;
                        }
                        Sensor sensor = getSensorManager().getDefaultSensor(sensorType);
                        // TODO: figure out which sensors have vector values
                        final int index = DeviceSettingsPopupActivity.getIndexForSensorType(
                                sensorType, AllNativeSensorProvider.this);
                        mSensorEventListener = new SensorEventListener() {
                            @Override
                            public void onSensorChanged(SensorEvent event) {
                                try {
                                    long timestamp = System.currentTimeMillis();
                                    float value = event.values[index];
                                    observer.onNewData(timestamp, maybeModify(value));
                                } catch (DeadObjectException e) {
                                    reportError(e);
                                    unregister();
                                } catch (RemoteException e) {
                                    reportError(e);
                                }
                            }

                            private float maybeModify(float value) {
                                if (isTemperature(sensorType)
                                        && !TemperatureSettingsPopupActivity.isCelsius(
                                        AllNativeSensorProvider.this)) {
                                    return value * 1.8f + 32f;
                                }
                                return value;
                            }

                            private void reportError(RemoteException e) {
                                if (Log.isLoggable(TAG, Log.ERROR)) {
                                    Log.e(TAG, "error sending data", e);
                                }
                            }

                            @Override
                            public void onAccuracyChanged(Sensor sensor, int accuracy) {
                                // do nothing
                            }
                        };
                        boolean b = getSensorManager().registerListener(mSensorEventListener,
                                sensor, SensorManager.SENSOR_DELAY_UI);
                    }

                    private int getSensorType(String sensorId, ISensorStatusListener listener)
                            throws RemoteException {
                        try {
                            return Integer.valueOf(sensorId);
                        } catch (IllegalArgumentException e) {
                            listener.onSensorError("Not an int: " + sensorId);
                            return -1;
                        }
                    }

                    private void unregister() {
                        if (mSensorEventListener != null) {
                            getSensorManager().unregisterListener(mSensorEventListener);
                            mSensorEventListener = null;
                        }
                    }

                    @Override
                    public void stopObserving(String sensorId) throws RemoteException {
                        unregister();
                        if (mListener != null) {
                            mListener.onSensorDisconnected();
                            mListener = null;
                        }
                    }
                };
            }
        };
    }

    private boolean isTemperature(int sensorType) {
        return sensorType == Sensor.TYPE_AMBIENT_TEMPERATURE || isNexus6pThermometer(sensorType);
    }

    private boolean isNexus6pThermometer(int sensorType) {
        return Build.MODEL == "angler" && sensorType == 65536;
    }


    private SensorManager getSensorManager() {
        return (SensorManager) getSystemService(Context.SENSOR_SERVICE);
    }
}
