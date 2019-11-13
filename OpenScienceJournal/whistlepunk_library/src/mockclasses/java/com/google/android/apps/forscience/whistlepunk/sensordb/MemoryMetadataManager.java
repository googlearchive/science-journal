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

package com.google.android.apps.forscience.whistlepunk.sensordb;

import android.content.ContentResolver;
import android.net.Uri;
import androidx.annotation.NonNull;
import android.text.TextUtils;
import com.google.android.apps.forscience.whistlepunk.SensorProvider;
import com.google.android.apps.forscience.whistlepunk.accounts.AppAccount;
import com.google.android.apps.forscience.whistlepunk.api.scalarinput.InputDeviceSpec;
import com.google.android.apps.forscience.whistlepunk.devicemanager.ConnectableSensor;
import com.google.android.apps.forscience.whistlepunk.filemetadata.Experiment;
import com.google.android.apps.forscience.whistlepunk.filemetadata.ExperimentOverviewPojo;
import com.google.android.apps.forscience.whistlepunk.filemetadata.SensorLayoutPojo;
import com.google.android.apps.forscience.whistlepunk.metadata.ExperimentSensors;
import com.google.android.apps.forscience.whistlepunk.metadata.ExternalSensorSpec;
import com.google.android.apps.forscience.whistlepunk.metadata.MetaDataManager;
import com.google.common.base.Preconditions;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.Multimap;
import com.google.common.collect.Sets;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MemoryMetadataManager implements MetaDataManager {
  private List<Experiment> experiments = new ArrayList<>();
  private Multimap<String, String> experimentIncluded = HashMultimap.create();
  private Multimap<String, String> experimentExcluded = HashMultimap.create();
  private final Map<String, List<SensorLayoutPojo>> layouts = new HashMap<>();
  private Map<String, ExternalSensorSpec> externalSensors = new HashMap<>();

  @Override
  public Experiment getExperimentById(String experimentId) {
    for (Experiment experiment : experiments) {
      if (TextUtils.equals(experiment.getExperimentId(), experimentId)) {
        return experiment;
      }
    }
    return null;
  }

  @Override
  public Experiment newExperiment() {
    long timestamp = System.currentTimeMillis();
    String experimentId = String.valueOf(timestamp);
    return newExperiment(timestamp, experimentId);
  }

  @Override
  public void addExperiment(Experiment experiment) {
    experiments.add(0, experiment);
  }

  @NonNull
  public Experiment newExperiment(long timestamp, String experimentId) {
    Experiment experiment = Experiment.newExperiment(timestamp, experimentId, 0);
    experiments.add(0, experiment);
    return experiment;
  }

  @Override
  public void deleteExperiment(Experiment experiment) {
    // TODO: test directly
    experiments.remove(experiment);
  }

  @Override
  public void deleteExperiment(String experimentId) {
    // TODO: test directly
    for (Experiment e : experiments) {
      if (e.getExperimentId().equals(experimentId)) {
        experiments.remove(e);
        return;
      }
    }
  }

  @Override
  public void updateExperiment(Experiment experiment, boolean setDirty) {
    layouts.put(experiment.getExperimentId(), experiment.getSensorLayouts());
  }

  @Override
  public List<ExperimentOverviewPojo> getExperimentOverviews(boolean includeArchived) {
    List<ExperimentOverviewPojo> result = new ArrayList<>();
    for (Experiment experiment : experiments) {
      result.add(experiment.getExperimentOverview());
    }
    return result;
  }

  @Override
  public Experiment importExperimentFromZip(Uri zipUri, ContentResolver resolver) {
    return null;
  }

  @Override
  public Map<String, ExternalSensorSpec> getExternalSensors(
      Map<String, SensorProvider> providerMap) {
    return externalSensors;
  }

  @Override
  public ExternalSensorSpec getExternalSensorById(
      String id, Map<String, SensorProvider> providerMap) {
    return externalSensors.get(id);
  }

  @Override
  public void removeExternalSensor(String databaseTag) {
    externalSensors.remove(databaseTag);
  }

  @Override
  public String addOrGetExternalSensor(
      ExternalSensorSpec sensor, Map<String, SensorProvider> providerMap) {
    for (Map.Entry<String, ExternalSensorSpec> entry : externalSensors.entrySet()) {
      if (sensor.isSameSensorAndSpec(entry.getValue())) {
        return entry.getKey();
      }
    }
    int suffix = 0;
    while (externalSensors.containsKey(ExternalSensorSpec.getSensorId(sensor, suffix))) {
      suffix++;
    }
    String newId = ExternalSensorSpec.getSensorId(sensor, suffix);
    externalSensors.put(newId, cloneSensor(sensor, providerMap));
    return newId;
  }

  private ExternalSensorSpec cloneSensor(
      ExternalSensorSpec sensor, Map<String, SensorProvider> providerMap) {
    Preconditions.checkNotNull(sensor);
    Preconditions.checkNotNull(providerMap);
    String sensorType = sensor.getType();
    SensorProvider provider = providerMap.get(sensorType);
    if (provider == null) {
      throw new IllegalArgumentException("No provider for sensor type " + sensorType);
    }
    String sensorName = sensor.getName();
    byte[] sensorConfig = sensor.getConfig();
    return provider.buildSensorSpec(sensorName, sensorConfig);
  }

  @Override
  public void addSensorToExperiment(String databaseTag, String experimentId) {
    experimentExcluded.remove(experimentId, databaseTag);
    experimentIncluded.put(experimentId, databaseTag);
  }

  @Override
  public void removeSensorFromExperiment(String databaseTag, String experimentId) {
    experimentIncluded.remove(experimentId, databaseTag);
    experimentExcluded.put(experimentId, databaseTag);
  }

  @Override
  public void eraseSensorFromExperiment(String databaseTag, String experimentId) {
    experimentIncluded.remove(experimentId, databaseTag);
    experimentExcluded.put(experimentId, databaseTag);
  }

  @Override
  public ExperimentSensors getExperimentSensors(
      String experimentId,
      Map<String, SensorProvider> providerMap,
      ConnectableSensor.Connector connector) {
    Preconditions.checkNotNull(connector);
    // TODO: doesn't deal with exclusions
    List<ConnectableSensor> specs = new ArrayList<>();
    for (String id : experimentIncluded.get(experimentId)) {
      specs.add(connector.connected(ExternalSensorSpec.toGoosciSpec(externalSensors.get(id)), id));
    }
    return new ExperimentSensors(specs, Sets.newHashSet(experimentExcluded.get(experimentId)));
  }

  @Override
  public void addMyDevice(InputDeviceSpec deviceSpec) {}

  @Override
  public void removeMyDevice(InputDeviceSpec deviceSpec) {}

  @Override
  public List<InputDeviceSpec> getMyDevices() {
    return Lists.newArrayList();
  }

  @Override
  public Experiment getLastUsedUnarchivedExperiment() {
    for (Experiment experiment : experiments) {
      if (!experiment.isArchived()) {
        return experiment;
      }
    }
    ;
    return null;
  }

  @Override
  public void setLastUsedExperiment(Experiment experiment) {
    experiments.remove(experiment);
    experiments.add(0, experiment);
  }

  @Override
  public void close() {}

  @Override
  public void saveImmediately() {}

  @Override
  public boolean canMoveAllExperimentsToAnotherAccount(AppAccount targetAccount) {
    return false;
  }

  @Override
  public void moveAllExperimentsToAnotherAccount(AppAccount targetAccount) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void beforeMovingExperimentToAnotherAccount(Experiment experiment) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void moveExperimentToAnotherAccount(Experiment experiment, AppAccount targetAccount)
      throws IOException {
    throw new UnsupportedOperationException();
  }

  @Override
  public void afterMovingExperimentFromAnotherAccount(Experiment experiment) {
    throw new UnsupportedOperationException();
  }
}
