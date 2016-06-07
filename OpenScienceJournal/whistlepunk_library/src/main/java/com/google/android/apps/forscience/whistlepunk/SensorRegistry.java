package com.google.android.apps.forscience.whistlepunk;

import android.content.Context;
import android.support.annotation.NonNull;
import android.support.annotation.VisibleForTesting;
import android.util.ArrayMap;

import com.google.android.apps.forscience.javalib.Consumer;
import com.google.android.apps.forscience.whistlepunk.metadata.BleSensorSpec;
import com.google.android.apps.forscience.whistlepunk.metadata.ExternalSensorSpec;
import com.google.android.apps.forscience.whistlepunk.sensorapi.SensorChoice;
import com.google.android.apps.forscience.whistlepunk.sensors.AccelerometerSensor;
import com.google.android.apps.forscience.whistlepunk.sensors.AmbientLightSensor;
import com.google.android.apps.forscience.whistlepunk.sensors.AmbientTemperatureSensor;
import com.google.android.apps.forscience.whistlepunk.sensors.BarometerSensor;
import com.google.android.apps.forscience.whistlepunk.sensors.BluetoothSensor;
import com.google.android.apps.forscience.whistlepunk.sensors.DecibelSensor;
import com.google.android.apps.forscience.whistlepunk.sensors.MagneticRotationSensor;
import com.google.android.apps.forscience.whistlepunk.sensors.SineWavePseudoSensor;
import com.google.android.apps.forscience.whistlepunk.sensors.VideoSensor;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * All UI elements that want to observe what's being sensed or change the state of the sensors
 * should go through this class.
 */
// TODO: Move more functionality into SensorCardPresenter.
public class SensorRegistry {
    public interface ExternalSensorProvider {
        public SensorChoice buildSensor(String sensorId, ExternalSensorSpec spec);

        String getProviderId();
    }

    private Map<String, ExternalSensorProvider> mExternalProviders = new ArrayMap<>();

    public static final String WP_NATIVE_BLE_PROVIDER_ID =
            "com.google.android.apps.forscience.whistlepunk.ble";

    public static final String WP_HARDWARE_PROVIDER_ID =
            "com.google.android.apps.forscience.whistlepunk.hardware";

    private static final ExternalSensorProvider WINDMILL_PROVIDER = new ExternalSensorProvider() {
        @Override
        public SensorChoice buildSensor(String sensorId, ExternalSensorSpec spec) {
            return new BluetoothSensor(sensorId, (BleSensorSpec) spec,
                    BluetoothSensor.ANNING_SERVICE_SPEC);
        }

        @Override
        public String getProviderId() {
            return WP_NATIVE_BLE_PROVIDER_ID;
        }
    };

    private static class SensorRegistryItem {
        public String providerId;
        public SensorChoice choice;

        public SensorRegistryItem(String providerId, SensorChoice choice) {
            this.providerId = providerId;
            this.choice = choice;
        }
    }

    private Map<String, SensorRegistryItem> mSensorRegistry = new ArrayMap<>();
    private Multimap<String, Consumer<SensorChoice>> mWaitingSensorChoiceOperations =
            HashMultimap.create();
    private ExternalSensorListener mExternalSensorListener;
    private Map<String, ExternalSensorSpec> mMostRecentExternalSensors;

    public static SensorRegistry createWithBuiltinSensors(Context context) {
        SensorRegistry sc = new SensorRegistry();
        sc.addAvailableBuiltinSensors(context);
        return sc;
    }

    @VisibleForTesting
    protected SensorRegistry() {
        // prevent direct construction

        // TODO: more dynamic way to build this?
        mExternalProviders.put(BleSensorSpec.TYPE, WINDMILL_PROVIDER);
    }

    /**
     * When the named sensor is added, do the operation defined by {@code consumer}
     *
     * This can happen one of two ways: if the sensor is already in the registry, {@code consumer}
     * is called immediately.
     *
     * Otherwise, {@code consumer} is remembered, and run when a SensorChoice with the given id gets
     * added.
     *
     * This is needed because especially BluetoothSensors can be added considerably after the rest
     * of the RecordFragment setup.
     */
    public void withSensorChoice(String sensorId, Consumer<SensorChoice> consumer) {
        SensorRegistryItem item = mSensorRegistry.get(sensorId);
        if (item != null) {
            consumer.take(item.choice);
        } else {
            mWaitingSensorChoiceOperations.put(sensorId, consumer);
        }
    }

    /**
     * Add the given source.
     *
     * May run callbacks already registered if the user already requested observation on the given
     * sensorId.
     */
    private void addSource(SensorRegistryItem item) {
        // TODO: enable per-sensor defaults (b/28036680)
        String id = item.choice.getId();
        mSensorRegistry.put(id, item);
        for (Consumer<SensorChoice> c : mWaitingSensorChoiceOperations.get(id)) {
            c.take(item.choice);
        }
        mWaitingSensorChoiceOperations.removeAll(id);
    }

    private boolean hasSource(String id) {
        return mSensorRegistry.containsKey(id);
    }

    public Set<String> getAllSources() {
        return mSensorRegistry.keySet();
    }

    private void removeAllBluetoothDevices() {
        for (Iterator<SensorRegistryItem> iterator = mSensorRegistry.values().iterator();
                iterator.hasNext(); ) {
            SensorRegistryItem source = iterator.next();
            if (source.providerId.equals(WP_NATIVE_BLE_PROVIDER_ID)) {
                iterator.remove();
            }
        }
    }

    private void addAvailableBuiltinSensors(Context context) {
        if (AccelerometerSensor.isAccelerometerAvailable(context.getApplicationContext())) {
            addBuiltInSensor(new AccelerometerSensor(AccelerometerSensor.Axis.X));
            addBuiltInSensor(new AccelerometerSensor(AccelerometerSensor.Axis.Y));
            addBuiltInSensor(new AccelerometerSensor(AccelerometerSensor.Axis.Z));
        }
        if (AmbientLightSensor.isAmbientLightAvailable(context.getApplicationContext())) {
            addBuiltInSensor(new AmbientLightSensor());
        }
        addBuiltInSensor(new DecibelSensor());

        if (DevOptionsFragment.isMagnetometerEnabled(context)) {
            if (MagneticRotationSensor.isMagneticRotationSensorAvailable(
                    context.getApplicationContext())) {
                addBuiltInSensor(new MagneticRotationSensor());
            }
        }

        if (DevOptionsFragment.isBarometerEnabled(context)) {
            if (BarometerSensor.isBarometerSensorAvailable(context.getApplicationContext())) {
                addBuiltInSensor(new BarometerSensor());
            }
        }

        if (DevOptionsFragment.isAmbientTemperatureSensorEnabled(context)) {
            if (AmbientTemperatureSensor.isAmbientTemperatureSensorAvailable(context)) {
                addBuiltInSensor(new AmbientTemperatureSensor());
            }
        }

        if (DevOptionsFragment.isSineWaveEnabled(context)) {
            addBuiltInSensor(new SineWavePseudoSensor());
        }

        if (DevOptionsFragment.isVideoSensorEnabled(context)) {
            if (VideoSensor.isCameraAvailable(context.getApplicationContext())) {
                addBuiltInSensor(new VideoSensor(context.getApplicationContext()));
            }
        }
    }

    private void addBuiltInSensor(SensorChoice source) {
        addSource(new SensorRegistryItem(WP_HARDWARE_PROVIDER_ID, source));
    }

    @NonNull
    public List<String> replaceExternalSensors(Map<String, ExternalSensorSpec> sensors) {
        mMostRecentExternalSensors = sensors;
        removeAllBluetoothDevices();

        List<String> sensorsActuallyAdded = new ArrayList<>();

        for (Map.Entry<String, ExternalSensorSpec> entry : sensors.entrySet()) {
            String sensorId = entry.getKey();
            if (!hasSource(sensorId)) {
                ExternalSensorSpec sensor = entry.getValue();
                ExternalSensorProvider provider = mExternalProviders.get(sensor.getType());
                if (provider != null) {
                    addSource(new SensorRegistryItem(provider.getProviderId(),
                            provider.buildSensor(sensorId, sensor)));
                    sensorsActuallyAdded.add(sensorId);
                }
            }
        }

        if (mExternalSensorListener != null) {
            mExternalSensorListener.replaceExternalSensors(sensors);
        }

        return sensorsActuallyAdded;
    }

    public void setExternalSensorListener(ExternalSensorListener listener) {
        mExternalSensorListener = listener;

        if (mMostRecentExternalSensors != null) {
            // TODO: write test for this behavior
            mExternalSensorListener.replaceExternalSensors(mMostRecentExternalSensors);
        }
    }
}
