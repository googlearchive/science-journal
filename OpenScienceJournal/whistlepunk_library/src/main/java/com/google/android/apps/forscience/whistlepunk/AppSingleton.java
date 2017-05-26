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

package com.google.android.apps.forscience.whistlepunk;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.support.annotation.NonNull;

import com.google.android.apps.forscience.ble.BleClient;
import com.google.android.apps.forscience.ble.BleClientImpl;
import com.google.android.apps.forscience.javalib.Consumer;
import com.google.android.apps.forscience.javalib.FailureListener;
import com.google.android.apps.forscience.whistlepunk.devicemanager.ConnectableSensor;
import com.google.android.apps.forscience.whistlepunk.devicemanager.ExternalSensorDiscoverer;
import com.google.android.apps.forscience.whistlepunk.metadata.SimpleMetaDataManager;
import com.google.android.apps.forscience.whistlepunk.sensorapi.SensorEnvironment;
import com.google.android.apps.forscience.whistlepunk.sensordb.SensorDatabaseImpl;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class AppSingleton {
    private static final String SENSOR_DATABASE_NAME = "sensors.db";
    private static final String TAG = "AppSingleton";
    private static AppSingleton sInstance;
    private final Context mApplicationContext;
    private DataControllerImpl mDataController;

    private static Executor sUiThreadExecutor = null;
    private SensorAppearanceProviderImpl mSensorAppearanceProvider;
    private final Clock mCurrentTimeClock = new CurrentTimeClock();
    private BleClientImpl mBleClient;
    private RecorderController mRecorderController;
    private MetadataController mMetadataController;
    private SensorRegistry mSensorRegistry;
    private PrefsSensorHistoryStorage mPrefsSensorHistoryStorage;
    private Map<String, ExternalSensorProvider> mExternalSensorProviders;
    private ConnectableSensor.Connector mSensorConnector;

    private SensorEnvironment mSensorEnvironment = new SensorEnvironment() {
                @Override
                public RecordingDataController getDataController() {
                    return AppSingleton.this.getRecordingDataController();
                }

                @Override
                public Clock getDefaultClock() {
                    return AppSingleton.this.getDefaultClock();
                }

                @Override
                public SensorHistoryStorage getSensorHistoryStorage() {
                    return AppSingleton.this.getPrefsSensorHistoryStorage();
                }

                @Override
                public BleClient getBleClient() {
                    return AppSingleton.this.getBleClient();
                }
            };

    @NonNull
    public PrefsSensorHistoryStorage getPrefsSensorHistoryStorage() {
        if (mPrefsSensorHistoryStorage == null) {
            mPrefsSensorHistoryStorage = new PrefsSensorHistoryStorage(mApplicationContext);
        }
        return mPrefsSensorHistoryStorage;
    }

    public static Executor getUiThreadExecutor() {
        if (sUiThreadExecutor == null) {
            final Handler handler = new Handler(Looper.getMainLooper());
            sUiThreadExecutor = new Executor() {
                @Override
                public void execute(Runnable command) {
                    handler.post(command);
                }
            };
        }
        return sUiThreadExecutor;
    }

    public static AppSingleton getInstance(Context context) {
        if (sInstance == null) {
            sInstance = new AppSingleton(context);
        }
        return sInstance;
    }

    private AppSingleton(Context context) {
        mApplicationContext = context.getApplicationContext();
    }

    public DataController getDataController() {
        return internalGetDataController();
    }

    @NonNull
    private DataControllerImpl internalGetDataController() {
        if (mDataController == null) {
            mDataController = new DataControllerImpl(
                    new SensorDatabaseImpl(mApplicationContext, SENSOR_DATABASE_NAME),
                    getUiThreadExecutor(), Executors.newSingleThreadExecutor(),
                    Executors.newSingleThreadExecutor(),
                    new SimpleMetaDataManager(mApplicationContext), getDefaultClock(),
                    getExternalSensorProviders(), getSensorConnector());
        }
        return mDataController;
    }

    public SensorAppearanceProvider getSensorAppearanceProvider() {
        if (mSensorAppearanceProvider == null) {
            mSensorAppearanceProvider = new SensorAppearanceProviderImpl(getDataController());
        }
        return mSensorAppearanceProvider;
    }

    public SensorEnvironment getSensorEnvironment() {
        return mSensorEnvironment;
    }

    private RecordingDataController getRecordingDataController() {
        return internalGetDataController();
    }

    public BleClientImpl getBleClient() {
        if (mBleClient == null) {
            mBleClient = new BleClientImpl(mApplicationContext);
            mBleClient.create();
        }
        return mBleClient;
    }

    private Clock getDefaultClock() {
        return mCurrentTimeClock;
    }

    public void destroyBleClient() {
        if (mBleClient != null) {
            mBleClient.destroy();
            mBleClient = null;
        }
    }

    // TODO: proxy through service instead
    // Right now, the main app thread owns the recorder controller, which is exposed to other apps
    // through the RecorderService.  We want to eventually swap this, so that the service owns the
    // controller, at which point the app thread will need to only get to the controller
    // asynchronously through withRecorderController below.
    public RecorderController getRecorderController() {
        if (mRecorderController == null) {
            mRecorderController = new RecorderControllerImpl(mApplicationContext);
        }
        return mRecorderController;
    }

    /**
     * Request action to be taken when the RecorderController is connected
     *
     * @param tag can be used to cancel the request using {@link #removeListeners(String)} below.
     * @param c consumer that will have the RecorderController delivered, either immediately,
     *          or on the main thread when it becomes available.
     */
    public void withRecorderController(String tag, Consumer<RecorderController> c) {
        c.take(getRecorderController());
    }

    /**
     * Cancel all operations that were submitted with the given tag
     *
     * @param tag
     */
    public void removeListeners(String tag) {
        // TODO: once we have asynchronous connection to the remote RecorderController, we may
        // have fragments that come and go without ever seeing the RecorderController, which will
        // need to be dropped here.
    }

    public SensorRegistry getSensorRegistry() {
        if (mSensorRegistry == null) {
            mSensorRegistry = SensorRegistry.createWithBuiltinSensors(mApplicationContext);
            withRecorderController(TAG, new Consumer<RecorderController>() {
                @Override
                public void take(RecorderController rc) {
                    mSensorRegistry.setSensorRegistryListener(rc);
                }
            });
        }
        return mSensorRegistry;
    }

    @NonNull
    public MetadataController getMetadataController() {
        if (mMetadataController == null) {
            mMetadataController = new MetadataController(
                    getDataController(), new MetadataController.FailureListenerFactory() {
                @Override
                public FailureListener makeListenerForOperation(String operation) {
                    return LoggingConsumer.expectSuccess(TAG, operation);
                }
            });
        }
        return mMetadataController;
    }

    public Map<String,ExternalSensorProvider> getExternalSensorProviders() {
        if (mExternalSensorProviders == null) {
            mExternalSensorProviders = buildProviderMap(
                    WhistlePunkApplication.getExternalSensorDiscoverers(mApplicationContext));
        }
        return mExternalSensorProviders;
    }

    @NonNull
    public static Map<String, ExternalSensorProvider> buildProviderMap(
            Map<String, ExternalSensorDiscoverer> discoverers) {
        Map<String, ExternalSensorProvider> providers = new HashMap<>();
        for (Map.Entry<String, ExternalSensorDiscoverer> entry : discoverers.entrySet()) {
            providers.put(entry.getKey(), entry.getValue().getProvider());
        }
        return providers;
    }

    public ConnectableSensor.Connector getSensorConnector() {
        if (mSensorConnector == null) {
            mSensorConnector = new ConnectableSensor.Connector(getExternalSensorProviders());
        }
        return mSensorConnector;
    }
}
