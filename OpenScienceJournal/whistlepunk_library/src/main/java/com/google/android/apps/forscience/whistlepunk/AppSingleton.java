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

import android.app.Activity;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import androidx.annotation.NonNull;
import com.google.android.apps.forscience.ble.BleClient;
import com.google.android.apps.forscience.ble.BleClientImpl;
import com.google.android.apps.forscience.whistlepunk.accounts.AppAccount;
import com.google.android.apps.forscience.whistlepunk.audio.AudioSource;
import com.google.android.apps.forscience.whistlepunk.devicemanager.ConnectableSensor;
import com.google.android.apps.forscience.whistlepunk.devicemanager.SensorDiscoverer;
import com.google.android.apps.forscience.whistlepunk.filemetadata.ExperimentLibraryManager;
import com.google.android.apps.forscience.whistlepunk.filemetadata.Label;
import com.google.android.apps.forscience.whistlepunk.filemetadata.LocalSyncManager;
import com.google.android.apps.forscience.whistlepunk.metadata.SimpleMetaDataManager;
import com.google.android.apps.forscience.whistlepunk.sensorapi.SensorEnvironment;
import com.google.android.apps.forscience.whistlepunk.sensordb.SensorDatabaseImpl;
import com.google.android.apps.forscience.whistlepunk.sensors.VelocitySensor;
import com.google.common.base.Optional;
import io.reactivex.Maybe;
import io.reactivex.Observable;
import io.reactivex.Observer;
import io.reactivex.Single;
import io.reactivex.subjects.BehaviorSubject;
import io.reactivex.subjects.PublishSubject;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class AppSingleton {
  private static final String SENSOR_DATABASE_NAME = "sensors.db";
  private static final String TAG = "AppSingleton";
  private static AppSingleton instance;
  private final Context applicationContext;
  private final Map<AppAccount, DataControllerImpl> dataControllers = new HashMap<>();
  private final Map<AppAccount, LocalSyncManager> localSyncManagers = new HashMap<>();
  private final Map<AppAccount, ExperimentLibraryManager> experimentLibraryManagers =
      new HashMap<>();

  private static Executor uiThreadExecutor = null;
  private final Map<AppAccount, SensorAppearanceProviderImpl> sensorAppearanceProviders =
      new HashMap<>();
  private final Clock currentTimeClock = new CurrentTimeClock();
  private final AudioSource audioSource = new AudioSource();
  private BleClientImpl bleClient;
  private final Map<AppAccount, RecorderControllerImpl> recorderControllers = new HashMap<>();
  private VelocitySensor velocitySensor;
  private SensorRegistry sensorRegistry;
  private PrefsSensorHistoryStorage prefsSensorHistoryStorage;
  private Map<String, SensorProvider> externalSensorProviders;
  private ConnectableSensor.Connector sensorConnector;
  private PublishSubject<Label> labelsAdded = PublishSubject.create();
  private BehaviorSubject<Boolean> exportServiceBusy = BehaviorSubject.create();
  private BehaviorSubject<Boolean> syncServiceBusy = BehaviorSubject.create();
  private final BehaviorSubject<Integer> experimentSynced = BehaviorSubject.create();
  private BehaviorSubject<Optional<Activity>> resumedActivity = BehaviorSubject.create();

  private SensorEnvironment sensorEnvironment =
      new SensorEnvironment() {
        @Override
        public RecordingDataController getDataController(AppAccount appAccount) {
          return AppSingleton.this.getRecordingDataController(appAccount);
        }

        @Override
        public Clock getDefaultClock() {
          return AppSingleton.this.getDefaultClock();
        }

        @Override
        public AudioSource getAudioSource() {
          return AppSingleton.this.getAudioSource();
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
  private DeletedLabel deletedLabel;
  private boolean mostRecentOpenWasImport = false;

  @NonNull
  public PrefsSensorHistoryStorage getPrefsSensorHistoryStorage() {
    if (prefsSensorHistoryStorage == null) {
      prefsSensorHistoryStorage = new PrefsSensorHistoryStorage(applicationContext);
    }
    return prefsSensorHistoryStorage;
  }

  public static Executor getUiThreadExecutor() {
    if (uiThreadExecutor == null) {
      final Handler handler = new Handler(Looper.getMainLooper());
      uiThreadExecutor =
          new Executor() {
            @Override
            public void execute(Runnable command) {
              handler.post(command);
            }
          };
    }
    return uiThreadExecutor;
  }

  public static AppSingleton getInstance(Context context) {
    if (instance == null) {
      instance = new AppSingleton(context);
    }
    return instance;
  }

  private AppSingleton(Context context) {
    applicationContext = context.getApplicationContext();
  }

  public DataController getDataController(AppAccount appAccount) {
    return internalGetDataController(appAccount);
  }

  @NonNull
  private DataControllerImpl internalGetDataController(AppAccount appAccount) {
    DataControllerImpl dataController = dataControllers.get(appAccount);
    if (dataController == null) {
      dataController =
          new DataControllerImpl(
              applicationContext,
              appAccount,
              new SensorDatabaseImpl(applicationContext, appAccount, SENSOR_DATABASE_NAME),
              getUiThreadExecutor(),
              Executors.newSingleThreadExecutor(),
              Executors.newSingleThreadExecutor(),
              new SimpleMetaDataManager(applicationContext, appAccount),
              getDefaultClock(),
              getExternalSensorProviders(),
              getSensorConnector());
      dataControllers.put(appAccount, dataController);
    }
    return dataController;
  }

  public SensorAppearanceProvider getSensorAppearanceProvider(AppAccount appAccount) {
    SensorAppearanceProviderImpl sensorAppearanceProvider =
        sensorAppearanceProviders.get(appAccount);
    if (sensorAppearanceProvider == null) {
      sensorAppearanceProvider =
          new SensorAppearanceProviderImpl(internalGetDataController(appAccount));
      sensorAppearanceProviders.put(appAccount, sensorAppearanceProvider);
    }
    return sensorAppearanceProvider;
  }

  public SensorEnvironment getSensorEnvironment() {
    return sensorEnvironment;
  }

  private RecordingDataController getRecordingDataController(AppAccount appAccount) {
    return internalGetDataController(appAccount);
  }

  public Single<BleClient> getConnectedBleClient() {
    if (bleClient == null) {
      bleClient = new BleClientImpl(applicationContext);
      bleClient.create();
    }
    return bleClient.whenConnected();
  }

  private Clock getDefaultClock() {
    return currentTimeClock;
  }

  public AudioSource getAudioSource() {
    return audioSource;
  }

  public void destroyBleClient() {
    if (bleClient != null) {
      bleClient.destroy();
      bleClient = null;
    }
  }

  public RecorderController getRecorderController(AppAccount appAccount) {
    RecorderControllerImpl recorderController = recorderControllers.get(appAccount);
    if (recorderController == null) {
      recorderController = new RecorderControllerImpl(applicationContext, appAccount);
      recorderControllers.put(appAccount, recorderController);
    }
    return recorderController;
  }

  public VelocitySensor getVelocitySensor() {
    if (velocitySensor == null) {
      velocitySensor = new VelocitySensor();
    }
    return velocitySensor;
  }

  // TODO: stop depending on this.  Each experiment should have its own registry
  public SensorRegistry getSensorRegistry() {
    if (sensorRegistry == null) {
      sensorRegistry = SensorRegistry.createWithBuiltinSensors(applicationContext);
    }
    return sensorRegistry;
  }

  public Map<String, SensorProvider> getExternalSensorProviders() {
    if (externalSensorProviders == null) {
      externalSensorProviders =
          buildProviderMap(WhistlePunkApplication.getExternalSensorDiscoverers(applicationContext));
    }
    return externalSensorProviders;
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
    if (sensorConnector == null) {
      sensorConnector = new ConnectableSensor.Connector(getExternalSensorProviders());
    }
    return sensorConnector;
  }

  public Observable<AddedLabelEvent> whenLabelsAdded(AppAccount appAccount) {
    return labelsAdded.withLatestFrom(
        getRecorderController(appAccount).watchRecordingStatus(), AddedLabelEvent::new);
  }

  public Observer<Label> onLabelsAdded() {
    return labelsAdded;
  }

  public void pushDeletedLabelForUndo(DeletedLabel deletedLabel) {
    this.deletedLabel = deletedLabel;
  }

  public DeletedLabel popDeletedLabelForUndo() {
    if (deletedLabel != null) {
      DeletedLabel returnThis = deletedLabel;
      deletedLabel = null;
      return returnThis;
    }
    return null;
  }

  public void setMostRecentOpenWasImport(boolean mostRecentOpenWasImport) {
    this.mostRecentOpenWasImport = mostRecentOpenWasImport;
  }

  public boolean getAndClearMostRecentOpenWasImport() {
    boolean returnThis = mostRecentOpenWasImport;
    mostRecentOpenWasImport = false;
    return returnThis;
  }

  public void setExportServiceBusy(boolean b) {
    exportServiceBusy.onNext(b);
  }

  public Observable<Boolean> whenExportBusyChanges() {
    return exportServiceBusy;
  }

  public void setSyncServiceBusy(boolean b) {
    syncServiceBusy.onNext(b);
  }

  public Observable<Boolean> whenSyncBusyChanges() {
    return syncServiceBusy;
  }

  public void notifyNewExperimentSynced() {
    int currentValue = 0;
    if (experimentSynced.getValue() != null) {
      currentValue = experimentSynced.getValue();
    }
    experimentSynced.onNext(currentValue + 1);
  }

  public Observable<Integer> whenNewExperimentSynced() {
    return experimentSynced;
  }

  public Maybe<Activity> onNextActivity() {
    return resumedActivity.filter(Optional::isPresent).map(Optional::get).firstElement();
  }

  public void setResumedActivity(Activity activity) {
    resumedActivity.onNext(Optional.of(activity));
  }

  public void setNoLongerResumedActivity(Activity activity) {
    if (activity.equals(resumedActivity.getValue().orNull())) {
      resumedActivity.onNext(Optional.absent());
    }
  }

  public LocalSyncManager getLocalSyncManager(AppAccount appAccount) {
    LocalSyncManager localSyncManager = localSyncManagers.get(appAccount);
    if (localSyncManager == null) {
      localSyncManager = new LocalSyncManager(appAccount);
      localSyncManagers.put(appAccount, localSyncManager);
    }
    return localSyncManager;
  }

  public ExperimentLibraryManager getExperimentLibraryManager(AppAccount appAccount) {
    ExperimentLibraryManager experimentLibraryManager = experimentLibraryManagers.get(appAccount);
    if (experimentLibraryManager == null) {
      experimentLibraryManager =
          new ExperimentLibraryManager(appAccount);
      experimentLibraryManagers.put(appAccount, experimentLibraryManager);
    }
    return experimentLibraryManager;
  }
}
