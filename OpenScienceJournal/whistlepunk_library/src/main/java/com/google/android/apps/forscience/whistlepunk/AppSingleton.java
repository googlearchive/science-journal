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
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;

import com.google.android.apps.forscience.ble.BleClient;
import com.google.android.apps.forscience.ble.BleClientImpl;
import com.google.android.apps.forscience.whistlepunk.devicemanager.ConnectableSensor;
import com.google.android.apps.forscience.whistlepunk.devicemanager.SensorDiscoverer;
import com.google.android.apps.forscience.whistlepunk.filemetadata.Label;
import com.google.android.apps.forscience.whistlepunk.metadata.SimpleMetaDataManager;
import com.google.android.apps.forscience.whistlepunk.sensorapi.SensorEnvironment;
import com.google.android.apps.forscience.whistlepunk.sensordb.SensorDatabaseImpl;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import io.reactivex.Observable;
import io.reactivex.Observer;
import io.reactivex.Single;
import io.reactivex.subjects.PublishSubject;

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
    private SensorRegistry mSensorRegistry;
    private PrefsSensorHistoryStorage mPrefsSensorHistoryStorage;
    private Map<String, SensorProvider> mExternalSensorProviders;
    private ConnectableSensor.Connector mSensorConnector;
    private PublishSubject<Label> mLabelsAdded = PublishSubject.create();

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
                public Single<BleClient> getConnectedBleClient() {
                    return AppSingleton.this.getConnectedBleClient();
                }
            };
    private DeletedLabel mDeletedLabel;

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

    public Single<BleClient> getConnectedBleClient() {
        if (mBleClient == null) {
            mBleClient = new BleClientImpl(mApplicationContext);
            mBleClient.create();
        }
        return mBleClient.whenConnected();
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

    public RecorderController getRecorderController() {
        if (mRecorderController == null) {
            mRecorderController = new RecorderControllerImpl(mApplicationContext);
        }
        return mRecorderController;
    }

    // TODO: stop depending on this.  Each experiment should have its own registry
    public SensorRegistry getSensorRegistry() {
        if (mSensorRegistry == null) {
            mSensorRegistry = SensorRegistry.createWithBuiltinSensors(mApplicationContext);
            SharedPreferences prefs =
                    PreferenceManager.getDefaultSharedPreferences(mApplicationContext);
            prefs.registerOnSharedPreferenceChangeListener(
                    (sprefs, key) -> mSensorRegistry.refreshBuiltinSensors(mApplicationContext));
        }
        return mSensorRegistry;
    }

    public Map<String,SensorProvider> getExternalSensorProviders() {
        if (mExternalSensorProviders == null) {
            mExternalSensorProviders = buildProviderMap(
                    WhistlePunkApplication.getExternalSensorDiscoverers(mApplicationContext));
        }
        return mExternalSensorProviders;
    }

    @NonNull
    public static Map<String, SensorProvider> buildProviderMap(
            Map<String, SensorDiscoverer> discoverers) {
        Map<String, SensorProvider> providers = new HashMap<>();
        for (Map.Entry<String, SensorDiscoverer> entry : discoverers.entrySet()) {
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

    public Observable<AddedLabelEvent> whenLabelsAdded() {
        return mLabelsAdded.withLatestFrom(getRecorderController().watchRecordingStatus(),
                AddedLabelEvent::new);
    }

    public Observer<Label> onLabelsAdded() {
        return mLabelsAdded;
    }

    public void pushDeletedLabelForUndo(DeletedLabel deletedLabel) {
        mDeletedLabel = deletedLabel;
    }

    public DeletedLabel popDeletedLabelForUndo() {
        if (mDeletedLabel != null) {
            DeletedLabel returnThis = mDeletedLabel;
            mDeletedLabel = null;
            return returnThis;
        }
        return null;
    }
}
