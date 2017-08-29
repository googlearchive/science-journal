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

import android.support.annotation.NonNull;
import android.text.TextUtils;

import com.google.android.apps.forscience.whistlepunk.SensorProvider;
import com.google.android.apps.forscience.whistlepunk.api.scalarinput.InputDeviceSpec;
import com.google.android.apps.forscience.whistlepunk.data.GoosciSensorLayout;
import com.google.android.apps.forscience.whistlepunk.devicemanager.ConnectableSensor;
import com.google.android.apps.forscience.whistlepunk.filemetadata.Experiment;
import com.google.android.apps.forscience.whistlepunk.metadata.ExperimentSensors;
import com.google.android.apps.forscience.whistlepunk.metadata.ExternalSensorSpec;
import com.google.android.apps.forscience.whistlepunk.metadata.GoosciUserMetadata;
import com.google.android.apps.forscience.whistlepunk.metadata.MetaDataManager;
import com.google.common.base.Preconditions;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.Multimap;
import com.google.common.collect.Sets;
import com.google.protobuf.nano.InvalidProtocolBufferNanoException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MemoryMetadataManager implements MetaDataManager {
    private List<Experiment> mExperiments = new ArrayList<>();
    private Multimap<String, String> mExperimentIncluded = HashMultimap.create();
    private Multimap<String, String> mExperimentExcluded = HashMultimap.create();
    private Map<String, List<GoosciSensorLayout.SensorLayout>> mLayouts = new HashMap<>();
    private Map<String, ExternalSensorSpec> mExternalSensors = new HashMap<>();

    @Override
    public Experiment getExperimentById(String experimentId) {
        for (Experiment experiment : mExperiments) {
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

    @NonNull
    public Experiment newExperiment(long timestamp, String experimentId) {
        Experiment experiment = Experiment.newExperiment(timestamp, experimentId, 0);
        mExperiments.add(0, experiment);
        return experiment;
    }

    @Override
    public void deleteExperiment(Experiment experiment) {
        // TODO: test directly
        mExperiments.remove(experiment);
    }

    @Override
    public void updateExperiment(Experiment experiment) {
        mLayouts.put(experiment.getExperimentId(), experiment.getSensorLayouts());
    }

    @Override
    public List<GoosciUserMetadata.ExperimentOverview> getExperimentOverviews(
            boolean includeArchived) {
        List<GoosciUserMetadata.ExperimentOverview> result = new ArrayList<>();
        for (Experiment experiment : mExperiments) {
            result.add(experiment.getExperimentOverview());
        }
        return result;
    }

    @Override
    public Map<String, ExternalSensorSpec> getExternalSensors(
            Map<String, SensorProvider> providerMap) {
        return mExternalSensors;
    }

    @Override
    public ExternalSensorSpec getExternalSensorById(String id,
            Map<String, SensorProvider> providerMap) {
        return mExternalSensors.get(id);
    }

    @Override
    public void removeExternalSensor(String databaseTag) {
        mExternalSensors.remove(databaseTag);
    }


    @Override
    public String addOrGetExternalSensor(ExternalSensorSpec sensor,
            Map<String, SensorProvider> providerMap) {
        for (Map.Entry<String, ExternalSensorSpec> entry : mExternalSensors.entrySet()) {
            if (sensor.isSameSensorAndSpec(entry.getValue())) {
                return entry.getKey();
            }
        }
        int suffix = 0;
        while (mExternalSensors.containsKey(ExternalSensorSpec.getSensorId(sensor, suffix))) {
            suffix++;
        }
        String newId = ExternalSensorSpec.getSensorId(sensor, suffix);
        mExternalSensors.put(newId, cloneSensor(sensor, providerMap));
        return newId;
    }

    private ExternalSensorSpec cloneSensor(ExternalSensorSpec sensor,
            Map<String, SensorProvider> providerMap) {
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
        mExperimentExcluded.remove(experimentId, databaseTag);
        mExperimentIncluded.put(experimentId, databaseTag);
    }

    @Override
    public void removeSensorFromExperiment(String databaseTag, String experimentId) {
        mExperimentIncluded.remove(experimentId, databaseTag);
        mExperimentExcluded.put(experimentId, databaseTag);
    }

    @Override
    public ExperimentSensors getExperimentSensors(String experimentId,
            Map<String, SensorProvider> providerMap,
            ConnectableSensor.Connector connector) {
        Preconditions.checkNotNull(connector);
        // TODO: doesn't deal with exclusions
        List<ConnectableSensor> specs = new ArrayList<>();
        for (String id : mExperimentIncluded.get(experimentId)) {
            specs.add(connector.connected(ExternalSensorSpec.toGoosciSpec(mExternalSensors.get(id)),
                    id));
        }
        return new ExperimentSensors(specs, Sets.newHashSet(mExperimentExcluded.get(experimentId)));
    }

    @Override
    public void addMyDevice(InputDeviceSpec deviceSpec) {

    }

    @Override
    public void removeMyDevice(InputDeviceSpec deviceSpec) {

    }

    @Override
    public List<InputDeviceSpec> getMyDevices() {
        return Lists.newArrayList();
    }

    @Override
    public Experiment getLastUsedUnarchivedExperiment() {
        for (Experiment experiment : mExperiments) {
            if (!experiment.isArchived()) {
                return experiment;
            }
        };
        return null;
    }

    @Override
    public void setLastUsedExperiment(Experiment experiment) {
        mExperiments.remove(experiment);
        mExperiments.add(0, experiment);
    }

    private GoosciSensorLayout.SensorLayout[] deepCopy(
            List<GoosciSensorLayout.SensorLayout> original) {
        GoosciSensorLayout.SensorLayout[] copy = new GoosciSensorLayout.SensorLayout[
                original.size()];
        for (int i = 0; i < original.size(); i++) {
            GoosciSensorLayout.SensorLayout layout = original.get(i);
            try {
                byte[] bytes = ExternalSensorSpec.getBytes(layout);
                copy[i] = (GoosciSensorLayout.SensorLayout.parseFrom(bytes));
            } catch (InvalidProtocolBufferNanoException e) {
                throw new RuntimeException(e);
            }
        }
        return copy;
    }

    @Override
    public void close() {

    }

    @Override
    public void saveImmediately() {

    }
}
