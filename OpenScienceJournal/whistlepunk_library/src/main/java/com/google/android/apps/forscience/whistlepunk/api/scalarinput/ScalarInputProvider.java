package com.google.android.apps.forscience.whistlepunk.api.scalarinput;

import com.google.android.apps.forscience.javalib.Consumer;
import com.google.android.apps.forscience.whistlepunk.ExternalSensorProvider;
import com.google.android.apps.forscience.whistlepunk.metadata.ExternalSensorSpec;
import com.google.android.apps.forscience.whistlepunk.sensorapi.SensorChoice;

import java.util.concurrent.Executor;

/**
 * Generates ScalarInputSensors from ScalarInputSpecs
 */
class ScalarInputProvider implements ExternalSensorProvider {
    private Consumer<AppDiscoveryCallbacks> mServiceFinder;
    private ScalarInputStringSource mStringSource;
    private Executor mUiThreadExecutor;

    public ScalarInputProvider(Consumer<AppDiscoveryCallbacks> serviceFinder,
            ScalarInputStringSource stringSource, Executor uiThreadExecutor) {
        this.mServiceFinder = serviceFinder;
        this.mStringSource = stringSource;
        this.mUiThreadExecutor = uiThreadExecutor;
    }

    @Override
    public SensorChoice buildSensor(String sensorId, ExternalSensorSpec spec) {
        ScalarInputSpec sis = (ScalarInputSpec) spec;
        return new ScalarInputSensor(sensorId, mUiThreadExecutor, mServiceFinder, mStringSource,
                sis.getServiceId(), spec.getAddress()
        );
    }

    @Override
    public String getProviderId() {
        return ScalarInputDiscoverer.SCALAR_INPUT_PROVIDER_ID;
    }

    @Override
    public ExternalSensorSpec buildSensorSpec(String name, byte[] config) {
        return new ScalarInputSpec(name, config);
    }
}
