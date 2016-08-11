/*
 * Copyright (C) 2008 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.android.apps.forscience.samplegyroprovider;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.RemoteException;
import android.support.annotation.Nullable;
import android.util.Log;

import com.google.android.apps.forscience.whistlepunk.api.scalarinput.IDeviceConsumer;
import com.google.android.apps.forscience.whistlepunk.api.scalarinput.ISensorConnector;
import com.google.android.apps.forscience.whistlepunk.api.scalarinput.ISensorConsumer;
import com.google.android.apps.forscience.whistlepunk.api.scalarinput.ISensorDiscoverer;
import com.google.android.apps.forscience.whistlepunk.api.scalarinput.ISensorObserver;
import com.google.android.apps.forscience.whistlepunk.api.scalarinput.ISensorStatusListener;

public class GyroAddedSensor extends Service {
    public static final String DEVICE_ID = "onlyDevice";
    public static final String XID = "gyro.x";
    public static final String YID = "gyro.y";
    public static final String ZID = "gyro.z";
    private static final String TAG = "GyroAddedSensor";

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
        return new ISensorDiscoverer.Stub() {
            @Override
            public String getName() throws RemoteException {
                return "GYRO";
            }

            @Override
            public void scanDevices(IDeviceConsumer c) throws RemoteException {
                c.onDeviceFound(DEVICE_ID, "Phone gyros", null);
            }

            @Override
            public void scanSensors(String deviceId, ISensorConsumer c) throws RemoteException {
                if (!DEVICE_ID.equals(deviceId)) {
                    return;
                }
                c.onSensorFound("GYRO_X", XID, null);
                c.onSensorFound("GYRO_Y", YID, null);
                c.onSensorFound("GYRO_Z", ZID, null);
            }

            @Override
            public ISensorConnector getConnector() throws RemoteException {
                return new ISensorConnector.Stub() {
                    private ISensorStatusListener mListener;
                    public SensorEventListener mSensorEventListener;

                    @Override
                    public void startObserving(final String sensorId,
                            final ISensorObserver observer, final ISensorStatusListener listener,
                            String settingsKey) throws RemoteException {
                        mListener = listener;
                        listener.onSensorConnected(sensorId);
                        unregister();
                        Sensor sensor = getSensorManager().getDefaultSensor(Sensor.TYPE_GYROSCOPE);
                        final int eventIndex = getEventIndexForSensor(sensorId);
                        mSensorEventListener = new SensorEventListener() {
                            @Override
                            public void onSensorChanged(SensorEvent event) {
                                try {
                                    observer.onNewData(System.currentTimeMillis(),
                                            event.values[eventIndex]);
                                } catch (RemoteException e) {
                                    try {
                                        reportError(e);
                                        listener.onSensorError(sensorId, e.getMessage());
                                    } catch (RemoteException e1) {
                                        reportError(e1);
                                    }
                                }
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
                        getSensorManager().registerListener(mSensorEventListener, sensor,
                                SensorManager.SENSOR_DELAY_UI);
                    }

                    private int getEventIndexForSensor(String sensorId) {
                        if (XID.equals(sensorId)) {
                            return 0;
                        }
                        if (YID.equals(sensorId)) {
                            return 1;
                        }
                        if (ZID.equals(sensorId)) {
                            return 2;
                        }
                        if (Log.isLoggable(TAG, Log.ERROR)) {
                            Log.e(TAG, "Don't recognize sensor id: " + sensorId);
                        }
                        return 0;
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
                            mListener.onSensorDisconnected(sensorId);
                            mListener = null;
                        }
                    }
                };
            }
        };
    }


    private SensorManager getSensorManager() {
        return (SensorManager) getSystemService(Context.SENSOR_SERVICE);
    }
}
