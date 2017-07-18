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
import com.google.android.apps.forscience.whistlepunk.SensorProvider;
import com.google.android.apps.forscience.whistlepunk.R;
import com.google.android.apps.forscience.whistlepunk.analytics.TrackerConstants;
import com.google.android.apps.forscience.whistlepunk.analytics.UsageTracker;
import com.google.android.apps.forscience.whistlepunk.data.GoosciSensorSpec;
import com.google.android.apps.forscience.whistlepunk.devicemanager.ConnectableSensor;
import com.google.android.apps.forscience.whistlepunk.devicemanager.SensorDiscoverer;
import com.google.android.apps.forscience.whistlepunk.sensors.SystemScheduler;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;

public class ScalarInputDiscoverer implements SensorDiscoverer {
    private static final long DEFAULT_SCAN_TIMEOUT_MILLIS = 10_000;
    private static String TAG = "SIDiscoverer";

    private final Consumer<AppDiscoveryCallbacks> mServiceFinder;
    private final ScalarInputStringSource mStringSource;
    private final Executor mUiThreadExecutor;
    private final Scheduler mScheduler;
    private final long mScanTimeoutMillis;
    private UsageTracker mUsageTracker;
    private ScanListener mScanListener;
    private List<String> mActiveServices = new ArrayList<>();

    public ScalarInputDiscoverer(Consumer<AppDiscoveryCallbacks> serviceFinder,
            Context context, UsageTracker usageTracker) {
        this(serviceFinder, defaultStringSource(context), AppSingleton.getUiThreadExecutor(),
                new SystemScheduler(), DEFAULT_SCAN_TIMEOUT_MILLIS, usageTracker);
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
        if (usageTracker == null) {
            if (Log.isLoggable(TAG, Log.WARN)) {
                Log.w(TAG, "Configuration error: No usage tracking in ScalarInputDiscoverer");
            }
        }
    }

    @Override
    public SensorProvider getProvider() {
        return new ScalarInputProvider(mServiceFinder, mStringSource, mUiThreadExecutor,
                mScheduler);
    }

    @Override
    public boolean startScanning(final ScanListener listener, final FailureListener onScanError) {
        mScanListener = listener;
        final String discoveryTaskId = "DISCOVERY";
        final TaskPool pool = new TaskPool(new Runnable() {
            @Override
            public void run() {
                mUiThreadExecutor.execute(new Runnable() {
                    @Override
                    public void run() {
                        markAllScansDone();
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
                            if (mScanListener == null) {
                                return;
                            }
                            mActiveServices.add(serviceId);
                            mScanListener.onServiceFound(new DiscoveredService() {
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

                    service.scanDevices(makeDeviceConsumer(service, serviceId, pool));
                } catch (RemoteException e) {
                    onScanError.fail(e);
                } catch (RuntimeException e) {
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
            final String serviceId,
            final TaskPool pool) {
        final String serviceTaskId = "SERVICE:" + serviceId;
        pool.addTask(serviceTaskId);
        mScheduler.schedule(Delay.millis(mScanTimeoutMillis), new Runnable() {
            @Override
            public void run() {
                markTaskTimeout(pool, serviceTaskId);
                mActiveServices.remove(serviceId);
                if (mScanListener != null) {
                    mScanListener.onServiceScanComplete(serviceId);
                }
            }
        });
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
                        if (mScanListener == null) {
                            return;
                        }
                        mScanListener.onDeviceFound(new DiscoveredDevice() {
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
                        makeSensorConsumer(serviceId, deviceId, new Runnable() {
                            @Override
                            public void run() {
                                pool.taskDone(deviceTaskId);
                            }
                        }));
            }

            @Override
            public void onScanDone() throws RemoteException {
                mUiThreadExecutor.execute(new Runnable() {
                    @Override
                    public void run() {
                        if (mScanListener != null) {
                            mScanListener.onServiceScanComplete(serviceId);
                        }
                        pool.taskDone(serviceTaskId);
                    }
                });
            }
        };
    }

    private void scheduleTaskTimeout(final TaskPool pool, final String taskId) {
        mScheduler.schedule(Delay.millis(mScanTimeoutMillis), new Runnable() {
            @Override
            public void run() {
                markTaskTimeout(pool, taskId);
            }
        });
    }

    private void markTaskTimeout(TaskPool pool, String taskId) {
        if (pool.taskDone(taskId) && mUsageTracker != null) {
            mUsageTracker.trackEvent(TrackerConstants.CATEGORY_API,
                    TrackerConstants.ACTION_API_SCAN_TIMEOUT, taskId, mScanTimeoutMillis);
        }
    }

    @NonNull
    private ISensorConsumer.Stub makeSensorConsumer(final String serviceId,
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
                        if (mScanListener == null) {
                            return;
                        }
                        mScanListener.onSensorFound(new DiscoveredSensor() {

                            @Override
                            public GoosciSensorSpec.SensorSpec getSensorSpec() {
                                return spec.asGoosciSpec();
                            }

                            @Override
                            public SettingsInterface getSettingsInterface() {
                                if (behavior == null || behavior.settingsIntent == null) {
                                    return null;
                                }
                                return new SettingsInterface() {
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

                            @Override
                            public boolean shouldReplaceStoredSensor(ConnectableSensor oldSensor) {
                                // The scalar API only has one set of settings per external sensor
                                // in its current form, so any old settings are now invalid
                                return true;
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
        markAllScansDone();
        mScanListener = null;
    }

    private void markAllScansDone() {
        if (mScanListener != null) {
            for (String serviceId : mActiveServices) {
                mScanListener.onServiceScanComplete(serviceId);
            }
            mScanListener.onScanDone();
        }
        mActiveServices.clear();
    }
}
