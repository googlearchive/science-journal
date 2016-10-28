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

import android.content.Context;
import android.os.RemoteException;

import com.google.android.apps.forscience.javalib.Consumer;
import com.google.android.apps.forscience.javalib.Delay;
import com.google.android.apps.forscience.javalib.Scheduler;
import com.google.android.apps.forscience.whistlepunk.Clock;
import com.google.android.apps.forscience.whistlepunk.sensorapi.AbstractSensorRecorder;
import com.google.android.apps.forscience.whistlepunk.sensorapi.ScalarSensor;
import com.google.android.apps.forscience.whistlepunk.sensorapi.SensorEnvironment;
import com.google.android.apps.forscience.whistlepunk.sensorapi.SensorRecorder;
import com.google.android.apps.forscience.whistlepunk.sensorapi.SensorStatusListener;
import com.google.android.apps.forscience.whistlepunk.sensorapi.StreamConsumer;

import java.util.Objects;
import java.util.concurrent.Executor;

/**
 * Sensor that receives data through the scalar input API.
 */
class ScalarInputSensor extends ScalarSensor {
    public static final Delay CONNECTION_TIME_OUT = Delay.seconds(20);
    private static final int MINIMUM_REFRESH_RATE_MILLIS = 1000;
    private final String mAddress;
    private final String mServiceId;
    private final Scheduler mScheduler;
    private Consumer<AppDiscoveryCallbacks> mServiceFinder;
    private ScalarInputStringSource mStringSource;
    private int mMostRecentStatus = -1;

    // TODO: find a way to reduce parameters?
    public ScalarInputSensor(
            String sensorId,
            Executor uiThreadExecutor,
            final Consumer<AppDiscoveryCallbacks> serviceFinder,
            final ScalarInputStringSource stringSource,
            ScalarInputSpec spec,
            Scheduler scheduler) {
        super(sensorId, uiThreadExecutor);
        mAddress = spec.getSensorAddressInService();
        mServiceId = spec.getServiceId();
        mServiceFinder = serviceFinder;
        mStringSource = stringSource;
        mScheduler = scheduler;
    }

    @Override
    protected SensorRecorder makeScalarControl(final StreamConsumer c,
            SensorEnvironment environment,
            Context context, final SensorStatusListener listener) {
        final Clock clock = environment.getDefaultClock();
        return new AbstractSensorRecorder() {
            private Runnable mTimeOutRunnable;
            private ISensorConnector mConnector = null;
            private double mLatestData;
            private ApiStatusListener mSensorStatusListener = new ApiStatusListener(listener) {
                @Override
                protected void onNoLongerStreaming() {
                    removeOldRefresh();
                }
            };
            public Runnable mRefreshRunnable;

            class RefreshableObserver extends ISensorObserver.Stub {
                private final StreamConsumer mConsumer;

                public RefreshableObserver(StreamConsumer consumer) {
                    mConsumer = consumer;
                }

                @Override
                public void onNewData(long timestamp, double data) {
                    if (mConnector == null) {
                        // We're disconnected, nothing to do here.
                        return;
                    }
                    mLatestData = data;
                    mScheduler.unschedule(mRefreshRunnable);
                    mScheduler.schedule(Delay.millis(MINIMUM_REFRESH_RATE_MILLIS),
                            mRefreshRunnable);
                    mConsumer.addData(timestamp, data);

                    // Some sensors may forget to set to connected, but if we're getting data,
                    //   we're probably connected.  (This actually happened in a version of the
                    //   Vernier implementation.)
                    if (mMostRecentStatus != SensorStatusListener.STATUS_CONNECTED) {
                        mSensorStatusListener.onSensorConnected();
                    }
                }
            }

            @Override
            public void startObserving() {
                if (mSensorStatusListener != null) {
                    mSensorStatusListener.connect();
                }
                mServiceFinder.take(new AppDiscoveryCallbacks() {
                    @Override
                    public void onServiceFound(String serviceId, ISensorDiscoverer service) {
                        if (!Objects.equals(serviceId, mServiceId)) {
                            // For beta compatibility, check if the sensor was stored with a
                            // beta-style serviceId (just package name) and finder is reporting
                            // correct style ("$package/$class").  In this case, the first found
                            // service in the package will be used (which matches beta behavior)
                            String[] idParts = serviceId.split("/");
                            if (idParts.length != 2 || !Objects.equals(idParts[0], mServiceId)) {
                                return;
                            }
                        }

                        try {
                            // TODO: generate correct value of settingsKey
                            String settingsKey = null;
                            mConnector = service.getConnector();
                            mConnector.startObserving(mAddress, makeObserver(c),
                                    mSensorStatusListener, settingsKey);
                            cancelTimeoutRunnable();
                            mTimeOutRunnable = new Runnable() {
                                @Override
                                public void run() {
                                    if (mMostRecentStatus
                                            != SensorStatusListener.STATUS_CONNECTED) {
                                        mSensorStatusListener.onSensorError(
                                                mStringSource.generateConnectionTimeoutMessage());
                                    }
                                }
                            };
                            mScheduler.schedule(CONNECTION_TIME_OUT, mTimeOutRunnable);
                        } catch (RemoteException e) {
                            complain(e);
                        } catch (RuntimeException e) {
                            complain(e);
                        }
                    }

                    private ISensorObserver makeObserver(final StreamConsumer c) {
                        final RefreshableObserver observer = new RefreshableObserver(c);

                        // TODO: only refresh if expected sample rate is low
                        removeOldRefresh();
                        mRefreshRunnable = new Runnable() {
                            @Override
                            public void run() {
                                observer.onNewData(clock.getNow(), mLatestData);
                            }
                        };

                        return observer;
                    }

                    @Override
                    public void onDiscoveryDone() {
                        // mConnector is null if no matching services were reported to
                        // onServiceFound.
                        if (mConnector == null) {
                            listener.onSourceError(getId(),
                                    SensorStatusListener.ERROR_FAILED_TO_CONNECT,
                                    mStringSource.generateCouldNotFindServiceErrorMessage(
                                            ScalarInputSensor.this.mServiceId));
                        }
                    }
                });
            }

            private void cancelTimeoutRunnable() {
                if (mTimeOutRunnable != null) {
                    mScheduler.unschedule(mTimeOutRunnable);
                    mTimeOutRunnable = null;
                }
            }

            @Override
            public void stopObserving() {
                cancelTimeoutRunnable();
                if (mConnector != null) {
                    try {
                        mConnector.stopObserving(mAddress);
                    } catch (RemoteException e) {
                        complain(e);
                    }
                    mConnector = null;
                    if (mSensorStatusListener != null) {
                        mSensorStatusListener.disconnect();
                    }
                    removeOldRefresh();
                }
            }

            private void removeOldRefresh() {
                if (mRefreshRunnable != null) {
                    mScheduler.unschedule(mRefreshRunnable);
                    mRefreshRunnable = null;
                }
            }

            private void complain(Throwable e) {
                listener.onSourceError(getId(), SensorStatusListener.ERROR_UNKNOWN,
                        e.getMessage());
            }
        };
    }

    private abstract class ApiStatusListener extends ISensorStatusListener.Stub {
        private boolean mConnected = true;
        private final SensorStatusListener mListener;

        public ApiStatusListener(SensorStatusListener listener) {
            mListener = listener;
        }

        public void connect() {
            mConnected = true;
        }

        public void disconnect() {
            mConnected = false;
            onNoLongerStreaming();
        }

        @Override
        public void onSensorConnecting() throws RemoteException {
            runOnMainThread(new Runnable() {
                @Override
                public void run() {
                    setStatus(SensorStatusListener.STATUS_CONNECTING);
                }
            });
        }

        @Override
        public void onSensorConnected() {
            runOnMainThread(new Runnable() {
                @Override
                public void run() {
                    setStatus(SensorStatusListener.STATUS_CONNECTED);
                }
            });
        }

        @Override
        public void onSensorDisconnected() throws RemoteException {
            runOnMainThread(new Runnable() {
                @Override
                public void run() {
                    setStatus(SensorStatusListener.STATUS_DISCONNECTED);
                    onNoLongerStreaming();
                }
            });
        }

        @Override
        public void onSensorError(final String errorMessage) {
            runOnMainThread(new Runnable() {
                @Override
                public void run() {
                    if (!mConnected) {
                        return;
                    }
                    mListener.onSourceError(getId(),
                            SensorStatusListener.ERROR_UNKNOWN, errorMessage);
                    onNoLongerStreaming();
                }
            });
        }

        protected abstract void onNoLongerStreaming();

        private void setStatus(int statusCode) {
            if (!mConnected) {
                return;
            }
            mListener.onSourceStatus(getId(), statusCode);
            mMostRecentStatus = statusCode;
        }
    }
}
