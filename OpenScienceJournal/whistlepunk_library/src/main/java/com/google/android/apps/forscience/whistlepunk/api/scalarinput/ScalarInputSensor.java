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

/** Sensor that receives data through the scalar input API. */
class ScalarInputSensor extends ScalarSensor {
  public static final Delay CONNECTION_TIME_OUT = Delay.seconds(20);
  private static final int MINIMUM_REFRESH_RATE_MILLIS = 1000;
  private final String address;
  private final String serviceId;
  private final Scheduler scheduler;
  private Consumer<AppDiscoveryCallbacks> serviceFinder;
  private ScalarInputStringSource stringSource;
  private int mostRecentStatus = -1;

  // TODO: find a way to reduce parameters?
  public ScalarInputSensor(
      String sensorId,
      Executor uiThreadExecutor,
      final Consumer<AppDiscoveryCallbacks> serviceFinder,
      final ScalarInputStringSource stringSource,
      ScalarInputSpec spec,
      Scheduler scheduler) {
    super(sensorId, uiThreadExecutor);
    address = spec.getSensorAddressInService();
    serviceId = spec.getServiceId();
    this.serviceFinder = serviceFinder;
    this.stringSource = stringSource;
    this.scheduler = scheduler;
  }

  @Override
  protected SensorRecorder makeScalarControl(
      final StreamConsumer c,
      SensorEnvironment environment,
      Context context,
      final SensorStatusListener listener) {
    final Clock clock = environment.getDefaultClock();
    return new AbstractSensorRecorder() {
      private Runnable timeOutRunnable;
      private ISensorConnector connector = null;
      private double latestData;
      private ApiStatusListener sensorStatusListener =
          new ApiStatusListener(listener) {
            @Override
            protected void onNoLongerStreaming() {
              removeOldRefresh();
            }
          };
      public Runnable refreshRunnable;

      class RefreshableObserver extends ISensorObserver.Stub {
        private final StreamConsumer consumer;

        public RefreshableObserver(StreamConsumer consumer) {
          this.consumer = consumer;
        }

        @Override
        public void onNewData(long timestamp, double data) {
          if (connector == null) {
            // We're disconnected, nothing to do here.
            return;
          }
          latestData = data;
          scheduler.unschedule(refreshRunnable);
          scheduler.schedule(Delay.millis(MINIMUM_REFRESH_RATE_MILLIS), refreshRunnable);
          this.consumer.addData(timestamp, data);

          // Some sensors may forget to set to connected, but if we're getting data,
          //   we're probably connected.  (This actually happened in a version of the
          //   Vernier implementation.)
          if (mostRecentStatus != SensorStatusListener.STATUS_CONNECTED) {
            sensorStatusListener.onSensorConnected();
          }
        }
      }

      @Override
      public void startObserving() {
        if (sensorStatusListener != null) {
          sensorStatusListener.connect();
        }
        serviceFinder.take(
            new AppDiscoveryCallbacks() {
              @Override
              public void onServiceFound(String serviceId, ISensorDiscoverer service) {
                if (!Objects.equals(serviceId, ScalarInputSensor.this.serviceId)) {
                  // For beta compatibility, check if the sensor was stored with a
                  // beta-style serviceId (just package name) and finder is reporting
                  // correct style ("$package/$class").  In this case, the first found
                  // service in the package will be used (which matches beta behavior)
                  String[] idParts = serviceId.split("/");
                  if (idParts.length != 2
                      || !Objects.equals(idParts[0], ScalarInputSensor.this.serviceId)) {
                    return;
                  }
                }

                try {
                  // TODO: generate correct value of settingsKey
                  String settingsKey = null;
                  connector = service.getConnector();
                  connector.startObserving(
                      address, makeObserver(c), sensorStatusListener, settingsKey);
                  cancelTimeoutRunnable();
                  timeOutRunnable =
                      new Runnable() {
                        @Override
                        public void run() {
                          if (mostRecentStatus != SensorStatusListener.STATUS_CONNECTED) {
                            sensorStatusListener.onSensorError(
                                stringSource.generateConnectionTimeoutMessage());
                          }
                        }
                      };
                  scheduler.schedule(CONNECTION_TIME_OUT, timeOutRunnable);
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
                refreshRunnable =
                    new Runnable() {
                      @Override
                      public void run() {
                        observer.onNewData(clock.getNow(), latestData);
                      }
                    };

                return observer;
              }

              @Override
              public void onDiscoveryDone() {
                // connector is null if no matching services were reported to
                // onServiceFound.
                if (connector == null) {
                  listener.onSourceError(
                      getId(),
                      SensorStatusListener.ERROR_FAILED_TO_CONNECT,
                      stringSource.generateCouldNotFindServiceErrorMessage(
                          ScalarInputSensor.this.serviceId));
                }
              }
            });
      }

      private void cancelTimeoutRunnable() {
        if (timeOutRunnable != null) {
          scheduler.unschedule(timeOutRunnable);
          timeOutRunnable = null;
        }
      }

      @Override
      public void stopObserving() {
        cancelTimeoutRunnable();
        if (connector != null) {
          try {
            connector.stopObserving(address);
          } catch (RemoteException e) {
            complain(e);
          }
          connector = null;
          if (sensorStatusListener != null) {
            sensorStatusListener.disconnect();
          }
          removeOldRefresh();
        }
      }

      private void removeOldRefresh() {
        if (refreshRunnable != null) {
          scheduler.unschedule(refreshRunnable);
          refreshRunnable = null;
        }
      }

      private void complain(Throwable e) {
        listener.onSourceError(getId(), SensorStatusListener.ERROR_UNKNOWN, e.getMessage());
      }
    };
  }

  private abstract class ApiStatusListener extends ISensorStatusListener.Stub {
    private boolean connected = true;
    private final SensorStatusListener listener;

    public ApiStatusListener(SensorStatusListener listener) {
      this.listener = listener;
    }

    public void connect() {
      connected = true;
    }

    public void disconnect() {
      connected = false;
      onNoLongerStreaming();
    }

    @Override
    public void onSensorConnecting() throws RemoteException {
      runOnMainThread(
          new Runnable() {
            @Override
            public void run() {
              setStatus(SensorStatusListener.STATUS_CONNECTING);
            }
          });
    }

    @Override
    public void onSensorConnected() {
      runOnMainThread(
          new Runnable() {
            @Override
            public void run() {
              setStatus(SensorStatusListener.STATUS_CONNECTED);
            }
          });
    }

    @Override
    public void onSensorDisconnected() throws RemoteException {
      runOnMainThread(
          new Runnable() {
            @Override
            public void run() {
              setStatus(SensorStatusListener.STATUS_DISCONNECTED);
              onNoLongerStreaming();
            }
          });
    }

    @Override
    public void onSensorError(final String errorMessage) {
      runOnMainThread(
          new Runnable() {
            @Override
            public void run() {
              if (!connected) {
                return;
              }
              listener.onSourceError(getId(), SensorStatusListener.ERROR_UNKNOWN, errorMessage);
              onNoLongerStreaming();
            }
          });
    }

    protected abstract void onNoLongerStreaming();

    private void setStatus(int statusCode) {
      if (!connected) {
        return;
      }
      listener.onSourceStatus(getId(), statusCode);
      mostRecentStatus = statusCode;
    }
  }
}
