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
import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;
import androidx.collection.ArraySet;
import android.util.Pair;
import com.google.android.apps.forscience.javalib.Consumer;
import com.google.android.apps.forscience.whistlepunk.data.GoosciGadgetInfo;
import com.google.android.apps.forscience.whistlepunk.data.GoosciSensorAppearance;
import com.google.android.apps.forscience.whistlepunk.data.GoosciSensorSpec;
import com.google.android.apps.forscience.whistlepunk.devicemanager.ConnectableSensor;
import com.google.android.apps.forscience.whistlepunk.metadata.ExternalSensorSpec;
import com.google.android.apps.forscience.whistlepunk.sensorapi.AvailableSensors;
import com.google.android.apps.forscience.whistlepunk.sensorapi.ScalarSensor;
import com.google.android.apps.forscience.whistlepunk.sensorapi.SensorChoice;
import com.google.android.apps.forscience.whistlepunk.sensors.AccelerometerSensor;
import com.google.android.apps.forscience.whistlepunk.sensors.AmbientLightSensor;
import com.google.android.apps.forscience.whistlepunk.sensors.AmbientTemperatureSensor;
import com.google.android.apps.forscience.whistlepunk.sensors.BarometerSensor;
import com.google.android.apps.forscience.whistlepunk.sensors.CompassSensor;
import com.google.android.apps.forscience.whistlepunk.sensors.DecibelSensor;
import com.google.android.apps.forscience.whistlepunk.sensors.LinearAccelerometerSensor;
import com.google.android.apps.forscience.whistlepunk.sensors.MagneticStrengthSensor;
import com.google.android.apps.forscience.whistlepunk.sensors.PitchSensor;
import com.google.android.apps.forscience.whistlepunk.sensors.SineWavePseudoSensor;
import com.google.android.apps.forscience.whistlepunk.sensors.VelocitySensor;
import com.google.common.base.Preconditions;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.Multimap;
import com.google.protobuf.ByteString;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * All UI elements that want to observe what's being sensed or change the state of the sensors
 * should go through this class.
 *
 * <p>Note that when first written, this class assumed that there was only one registry for the
 * entire platform, and therefore sensorIds were global. As we move forward, sensorIds will be
 * associated directly with experiments.
 */
// TODO: Move more functionality into SensorCardPresenter.
public class SensorRegistry {
  @VisibleForTesting
  public static final String WP_HARDWARE_PROVIDER_ID =
      "com.google.android.apps.forscience.whistlepunk.hardware";

  private static final String TAG = "SensorRegistry";

  private static class SensorRegistryItem {
    public String providerId;
    public String loggingId;
    public ExternalSensorSpec externalSpec;
    public SensorChoice choice;

    public SensorRegistryItem(
        String providerId, ExternalSensorSpec externalSpec, SensorChoice choice, String loggingId) {
      this.providerId = providerId;
      this.externalSpec = externalSpec;
      this.choice = Preconditions.checkNotNull(choice);
      this.loggingId = loggingId;
    }
  }

  /** Remember insertion order in order to display sensors in order added. */
  private Map<String, SensorRegistryItem> sensorRegistry = new LinkedHashMap<>();

  /** Which ids that would otherwise be available has the user explicitly excluded? */
  private Set<String> excludedIds = new ArraySet<>();

  // sensorId -> (tag, op)
  private Multimap<String, Pair<String, Consumer<SensorChoice>>> waitingSensorChoiceOperations =
      HashMultimap.create();

  public static SensorRegistry createWithBuiltinSensors(final Context context) {
    final SensorRegistry sc = new SensorRegistry();
    sc.addAvailableBuiltinSensors(context);
    return sc;
  }

  public void refreshBuiltinSensors(Context context) {
    removeBuiltInSensors();
    addAvailableBuiltinSensors(context);
  }

  private void removeBuiltInSensors() {
    Iterator<SensorRegistryItem> iter = sensorRegistry.values().iterator();
    while (iter.hasNext()) {
      SensorRegistryItem item = iter.next();
      if (Objects.equals(WP_HARDWARE_PROVIDER_ID, item.providerId)) {
        iter.remove();
      }
    }
  }

  @VisibleForTesting
  protected SensorRegistry() {
    // prevent direct construction
  }

  /**
   * When the named sensor is added, do the operation defined by {@code consumer}
   *
   * <p>This can happen one of two ways: if the sensor is already in the registry, {@code consumer}
   * is called immediately.
   *
   * <p>Otherwise, {@code consumer} is remembered, and run when a SensorChoice with the given id
   * gets added.
   *
   * <p>This is needed because especially BluetoothSensors can be added considerably after the rest
   * of the RecordFragment setup.
   */
  public void withSensorChoice(String tag, String sensorId, Consumer<SensorChoice> consumer) {
    SensorRegistryItem item = sensorRegistry.get(sensorId);
    if (item != null) {
      consumer.take(item.choice);
    } else {
      waitingSensorChoiceOperations.put(sensorId, Pair.create(tag, consumer));
    }
  }

  /**
   * Add the given source.
   *
   * <p>May run callbacks already registered if the user already requested observation on the given
   * sensorId.
   */
  @VisibleForTesting
  private void addSource(SensorRegistryItem item) {
    // TODO: enable per-sensor defaults (b/28036680)
    String id = item.choice.getId();
    sensorRegistry.put(id, item);
    for (Pair<String, Consumer<SensorChoice>> c : waitingSensorChoiceOperations.get(id)) {
      c.second.take(item.choice);
    }
    waitingSensorChoiceOperations.removeAll(id);
  }

  public List<String> getAllSources() {
    return Lists.newArrayList(sensorRegistry.keySet());
  }

  private Set<String> getAllExternalSources() {
    Set<String> externalSourceIds = new HashSet<String>();
    for (Map.Entry<String, SensorRegistryItem> entry : sensorRegistry.entrySet()) {
      if (!Objects.equals(entry.getValue().providerId, WP_HARDWARE_PROVIDER_ID)) {
        externalSourceIds.add(entry.getKey());
      }
    }
    return externalSourceIds;
  }

  public List<String> getBuiltInSources() {
    // TODO: is this going to be returned in the right order?
    List<String> ids = new ArrayList<>();
    for (Map.Entry<String, SensorRegistryItem> entry : sensorRegistry.entrySet()) {
      if (Objects.equals(entry.getValue().providerId, WP_HARDWARE_PROVIDER_ID)) {
        ids.add(entry.getKey());
      }
    }
    return ids;
  }

  private void addAvailableBuiltinSensors(Context context) {
    final Context appContext = context.getApplicationContext();
    AppSingleton singleton = AppSingleton.getInstance(appContext);
    AvailableSensors available =
        new AvailableSensors() {
          @Override
          public boolean isSensorAvailable(int sensorType) {
            return ScalarSensor.getSensorManager(appContext).getDefaultSensor(sensorType) != null;
          }
        };

    // Add the sensors in the order of SensorCardPresenter.SENSOR_ID_ORDER. If new sensors are
    // added to this list of built-ins, make sure to update that list if they are not added
    // to the end of this list.

    if (AmbientLightSensor.isAmbientLightAvailable(available)) {
      addBuiltInSensor(new AmbientLightSensor());
    }
    addBuiltInSensor(new DecibelSensor());
    addBuiltInSensor(new PitchSensor());

    if (AccelerometerSensor.isAccelerometerAvailable(available)) {
      addBuiltInSensor(new AccelerometerSensor(AccelerometerSensor.Axis.X));
      addBuiltInSensor(new AccelerometerSensor(AccelerometerSensor.Axis.Y));
      addBuiltInSensor(new AccelerometerSensor(AccelerometerSensor.Axis.Z));
    }

    if (LinearAccelerometerSensor.isLinearAccelerometerAvailable(available)) {
      addBuiltInSensor(new LinearAccelerometerSensor());
    }

    if (BarometerSensor.isBarometerSensorAvailable(available)) {
      addBuiltInSensor(new BarometerSensor());
    }

    if (MagneticStrengthSensor.isMagneticRotationSensorAvailable(available)) {
      addBuiltInSensor(new MagneticStrengthSensor());
    }

    if (CompassSensor.isCompassSensorAvailable(available)) {
      addBuiltInSensor(new CompassSensor());
    }

    if (VelocitySensor.isVelocitySensorAvailable(appContext)) {
      addBuiltInSensor(singleton.getVelocitySensor());
    }

    if (DevOptionsFragment.isAmbientTemperatureSensorEnabled(context)) {
      if (AmbientTemperatureSensor.isAmbientTemperatureSensorAvailable(available)) {
        addBuiltInSensor(new AmbientTemperatureSensor());
      }
    }

    if (DevOptionsFragment.isSineWaveEnabled(context)) {
      addBuiltInSensor(new SineWavePseudoSensor());
    }
  }

  protected void addBuiltInSensor(SensorChoice source) {
    String id = source.getId();
    addSource(new SensorRegistryItem(WP_HARDWARE_PROVIDER_ID, null, source, id));
  }

  @NonNull
  public List<String> updateExternalSensors(
      List<ConnectableSensor> sensors, Map<String, SensorProvider> externalProviders) {
    List<String> sensorsActuallyAdded = new ArrayList<>();

    Set<String> previousExternalSources = getAllExternalSources();

    // Add previously unknown sensors
    for (ConnectableSensor newSensor : sensors) {
      if (newSensor.getSpec() != null && newSensor.isPaired()) {
        String externalSensorId = newSensor.getConnectedSensorId();
        if (!previousExternalSources.remove(externalSensorId)) {
          // sensor is new
          ExternalSensorSpec sensor = newSensor.getSpec();
          String type = sensor.getType();
          SensorProvider provider = externalProviders.get(type);
          if (provider != null) {
            addSource(
                new SensorRegistryItem(
                    type,
                    sensor,
                    provider.buildSensor(externalSensorId, sensor),
                    sensor.getLoggingId()));
            sensorsActuallyAdded.add(externalSensorId);
          }
        }
      }
    }

    // Remove known sensors that no longer exist
    Set<String> removedExternalSensors = previousExternalSources;
    for (String externalSensorId : removedExternalSensors) {
      sensorRegistry.remove(externalSensorId);
    }

    return sensorsActuallyAdded;
  }

  public String getLoggingId(String sensorId) {
    if (sensorRegistry.containsKey(sensorId)) {
      return sensorRegistry.get(sensorId).loggingId;
    } else {
      return null;
    }
  }

  public void removePendingOperations(String tag) {
    Iterator<Pair<String, Consumer<SensorChoice>>> i =
        waitingSensorChoiceOperations.values().iterator();
    while (i.hasNext()) {
      if (i.next().first.equals(tag)) {
        i.remove();
      }
    }
  }

  // TODO: test this?
  public List<String> getIncludedSources() {
    List<String> allSensorIds = getAllSources();
    allSensorIds.removeAll(excludedIds);
    return allSensorIds;
  }

  public void setExcludedIds(Set<String> excludedSensorIds) {
    excludedIds.clear();
    excludedIds.addAll(excludedSensorIds);
  }

  public GoosciSensorSpec.SensorSpec getSpecForId(
      String sensorId, SensorAppearanceProvider appearanceProvider, Context context) {
    GoosciSensorAppearance.BasicSensorAppearance appearance =
        SensorAppearanceProviderImpl.toProto(appearanceProvider.getAppearance(sensorId), context);
    SensorRegistryItem item = sensorRegistry.get(sensorId);
    GoosciSensorSpec.SensorSpec.Builder spec =
        GoosciSensorSpec.SensorSpec.newBuilder()
            .setRememberedAppearance(appearance)
            .setInfo(
                GoosciGadgetInfo.GadgetInfo.newBuilder()
                    .setProviderId(item.providerId)
                    .setAddress(getAddress(item)));

    if (item.externalSpec != null) {
      spec.setConfig(ByteString.copyFrom(item.externalSpec.getConfig()));
    }

    return spec.build();
  }

  private String getAddress(SensorRegistryItem item) {
    if (item.externalSpec != null) {
      return item.externalSpec.getAddress();
    } else {
      // For internal sensors, the id is the only address we need.
      return item.choice.getId();
    }
  }
}
