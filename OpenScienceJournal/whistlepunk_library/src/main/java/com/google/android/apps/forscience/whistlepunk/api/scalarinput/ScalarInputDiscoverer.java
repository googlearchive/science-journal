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

import android.app.FragmentManager;
import android.app.PendingIntent;
import android.content.Context;
import android.graphics.drawable.Drawable;
import android.os.RemoteException;
import android.support.annotation.NonNull;
import android.support.annotation.VisibleForTesting;
import android.util.Log;

import com.google.android.apps.forscience.javalib.Consumer;
import com.google.android.apps.forscience.javalib.Delay;
import com.google.android.apps.forscience.javalib.FailureListener;
import com.google.android.apps.forscience.javalib.Scheduler;
import com.google.android.apps.forscience.whistlepunk.AppSingleton;
import com.google.android.apps.forscience.whistlepunk.ExternalSensorProvider;
import com.google.android.apps.forscience.whistlepunk.R;
import com.google.android.apps.forscience.whistlepunk.WhistlePunkApplication;
import com.google.android.apps.forscience.whistlepunk.analytics.TrackerConstants;
import com.google.android.apps.forscience.whistlepunk.analytics.UsageTracker;
import com.google.android.apps.forscience.whistlepunk.devicemanager.ExternalSensorDiscoverer;
import com.google.android.apps.forscience.whistlepunk.metadata.ExternalSensorSpec;
import com.google.android.apps.forscience.whistlepunk.sensors.SystemScheduler;

import java.util.concurrent.Executor;

public class ScalarInputDiscoverer implements ExternalSensorDiscoverer {
    private static final long DEFAULT_SCAN_TIMEOUT_MILLIS = 10_000;
    private static String TAG = "SIDiscoverer";
    public static final String SCALAR_INPUT_PROVIDER_ID =
            "com.google.android.apps.forscience.whistlepunk.scalarinput";

    private final Consumer<AppDiscoveryCallbacks> mServiceFinder;
    private final ScalarInputStringSource mStringSource;
    private final Executor mUiThreadExecutor;
    private final Scheduler mScheduler;
    private final long mScanTimeoutMillis;
    private UsageTracker mUsageTracker;

    public ScalarInputDiscoverer(Consumer<AppDiscoveryCallbacks> serviceFinder,
            Context context) {
        this(serviceFinder, defaultStringSource(context),
                AppSingleton.getUiThreadExecutor(),
                new SystemScheduler(), DEFAULT_SCAN_TIMEOUT_MILLIS,
                WhistlePunkApplication.getUsageTracker(context));
    }

    private static ScalarInputStringSource defaultStringSource(final Context context) {
        return new ScalarInputStringSource() {
            @Override
            public String generateCouldNotFindServiceErrorMessage(String serviceId) {
                return context.getResources().getString(R.string.could_not_find_service_error,
                        serviceId);
            }

            @Override
            public String generateConnectionTimeoutMessage() {
                return context.getResources().getString(R.string.api_connection_timeout_error);
            }
        };
    }

    @VisibleForTesting
    public ScalarInputDiscoverer(
            Consumer<AppDiscoveryCallbacks> serviceFinder,
            ScalarInputStringSource stringSource,
            Executor uiThreadExecutor, Scheduler scheduler, long scanTimeoutMillis,
            UsageTracker usageTracker) {
        mServiceFinder = serviceFinder;
        mStringSource = stringSource;
        mUiThreadExecutor = uiThreadExecutor;
        mScheduler = scheduler;
        mScanTimeoutMillis = scanTimeoutMillis;
        mUsageTracker = usageTracker;
    }

    @Override
    public ExternalSensorProvider getProvider() {
        return new ScalarInputProvider(mServiceFinder, mStringSource, mUiThreadExecutor,
                mScheduler);
    }

    @Override
    public boolean startScanning(final ScanListener listener, final FailureListener onScanError) {
        final String discoveryTaskId = "DISCOVERY";
        final TaskPool pool = new TaskPool(new Runnable() {
            @Override
            public void run() {
                mUiThreadExecutor.execute(new Runnable() {
                    @Override
                    public void run() {
                        listener.onScanDone();
                    }
                });
            }
        }, discoveryTaskId);
        mServiceFinder.take(new AppDiscoveryCallbacks() {
            @Override
            public void onServiceFound(final String serviceId, final ISensorDiscoverer service) {
                if (Log.isLoggable(TAG, Log.DEBUG)) {
                    Log.d(TAG, "Found service: " + serviceId);
                }
                try {
                    final String serviceName = service.getName();
                    mUiThreadExecutor.execute(new Runnable() {
                        @Override
                        public void run() {
                            listener.onServiceFound(new DiscoveredService() {
                                @Override
                                public String getServiceId() {
                                    return serviceId;
                                }

                                @Override
                                public String getName() {
                                    return serviceName;
                                }

                                @Override
                                public Drawable getIconDrawable(Context context) {
                                    return ScalarInputSpec.getServiceDrawable(serviceId, context);
                                }

                                @Override
                                public ServiceConnectionError getConnectionErrorIfAny() {
                                    // TODO: implement this?
                                    return null;
                                }
                            });
                        }
                    });

                    service.scanDevices(
                            makeDeviceConsumer(service, serviceId, listener, pool));
                } catch (RemoteException e) {
                    onScanError.fail(e);
                }
            }

            @Override
            public void onDiscoveryDone() {
                pool.taskDone(discoveryTaskId);
            }
        });
        return true;
    }

    @NonNull
    private IDeviceConsumer.Stub makeDeviceConsumer(final ISensorDiscoverer service,
            final String serviceId, final ScanListener scanListener,
            final TaskPool pool) {
        final String serviceTaskId = "SERVICE:" + serviceId;
        pool.addTask(serviceTaskId);
        scheduleTaskTimeout(pool, serviceTaskId);
        return new IDeviceConsumer.Stub() {
            @Override
            public void onDeviceFound(final String deviceId, final String name,
                    PendingIntent settingsIntent) throws RemoteException {
                if (Log.isLoggable(TAG, Log.DEBUG)) {
                    Log.d(TAG, "Found device: " + name);
                }
                final String deviceTaskId = "DEVICE:" + deviceId;
                pool.addTask(deviceTaskId);
                scheduleTaskTimeout(pool, deviceTaskId);

                mUiThreadExecutor.execute(new Runnable() {
                    @Override
                    public void run() {
                        scanListener.onDeviceFound(new DiscoveredDevice() {
                            @Override
                            public String getServiceId() {
                                return serviceId;
                            }

                            @Override
                            public InputDeviceSpec getSpec() {
                                return new InputDeviceSpec(ScalarInputSpec.TYPE,
                                        ScalarInputSpec.makeApiDeviceAddress(serviceId, deviceId),
                                        name);
                            }
                        });
                    }
                });

                // TODO: restructure to create an object-per-service-scan to hold intermediate data.
                service.scanSensors(deviceId,
                        makeSensorConsumer(serviceId, scanListener, deviceId, new Runnable() {
                            @Override
                            public void run() {
                                pool.taskDone(deviceTaskId);
                            }
                        }));
            }

            @Override
            public void onScanDone() throws RemoteException {
                pool.taskDone(serviceTaskId);
            }
        };
    }

    private void scheduleTaskTimeout(final TaskPool pool, final String taskId) {
        mScheduler.schedule(Delay.millis(mScanTimeoutMillis), new Runnable() {
            @Override
            public void run() {
                if (pool.taskDone(taskId)) {
                    mUsageTracker.trackEvent(TrackerConstants.CATEGORY_API,
                            TrackerConstants.ACTION_API_SCAN_TIMEOUT, taskId, mScanTimeoutMillis);
                }
            }
        });
    }

    @NonNull
    private ISensorConsumer.Stub makeSensorConsumer(final String serviceId,
            final ScanListener onEachSensorFound,
            final String deviceId, final Runnable onScanDone) {
        return new ISensorConsumer.Stub() {
            @Override
            public void onSensorFound(String sensorAddress, String name,
                    final SensorBehavior behavior, SensorAppearanceResources ids)
                    throws RemoteException {
                if (Log.isLoggable(TAG, Log.DEBUG)) {
                    Log.d(TAG, "Found sensor: " + name);
                }

                final ScalarInputSpec spec = new ScalarInputSpec(name, serviceId, sensorAddress,
                        behavior, ids, deviceId);
                mUiThreadExecutor.execute(new Runnable() {
                    @Override
                    public void run() {
                        onEachSensorFound.onSensorFound(new DiscoveredSensor() {
                            @Override
                            public ExternalSensorSpec getSpec() {
                                return spec;
                            }

                            @Override
                            public SettingsInterface getSettingsInterface() {
                                return behavior == null ? null : new SettingsInterface() {
                                    @Override
                                    public void show(String experimentId, String sensorId,
                                            FragmentManager fragmentManager,
                                            boolean showForgetButton) {
                                        try {
                                            behavior.settingsIntent.send();
                                        } catch (PendingIntent.CanceledException e) {
                                            if (Log.isLoggable(TAG, Log.ERROR)) {
                                                Log.e(TAG, "Could not open settings", e);
                                            }
                                        }
                                    }
                                };
                            }
                        });
                    }
                });
            }

            @Override
            public void onScanDone() throws RemoteException {
                onScanDone.run();
            }
        };
    }

    @Override
    public void stopScanning() {
        // TODO: implement!
    }

}
