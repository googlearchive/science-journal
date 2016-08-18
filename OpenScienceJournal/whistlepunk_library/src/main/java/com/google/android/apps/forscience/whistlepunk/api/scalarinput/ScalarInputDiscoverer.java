package com.google.android.apps.forscience.whistlepunk.api.scalarinput;

import android.app.PendingIntent;
import android.content.Context;
import android.os.Bundle;
import android.os.RemoteException;
import android.preference.Preference;
import android.support.annotation.NonNull;
import android.view.View;

import com.google.android.apps.forscience.javalib.Consumer;
import com.google.android.apps.forscience.whistlepunk.DataController;
import com.google.android.apps.forscience.whistlepunk.ExternalAxisController;
import com.google.android.apps.forscience.whistlepunk.ExternalSensorProvider;
import com.google.android.apps.forscience.whistlepunk.StatsListener;
import com.google.android.apps.forscience.whistlepunk.devicemanager.ExternalSensorDiscoverer;
import com.google.android.apps.forscience.whistlepunk.devicemanager.ManageDevicesFragment;
import com.google.android.apps.forscience.whistlepunk.metadata.ExternalSensorSpec;
import com.google.android.apps.forscience.whistlepunk.metadata.Label;
import com.google.android.apps.forscience.whistlepunk.metadata.SensorTrigger;
import com.google.android.apps.forscience.whistlepunk.sensorapi.DataViewOptions;
import com.google.android.apps.forscience.whistlepunk.sensorapi.ReadableSensorOptions;
import com.google.android.apps.forscience.whistlepunk.sensorapi.ScalarSensor;
import com.google.android.apps.forscience.whistlepunk.sensorapi.SensorChoice;
import com.google.android.apps.forscience.whistlepunk.sensorapi.SensorEnvironment;
import com.google.android.apps.forscience.whistlepunk.sensorapi.SensorObserver;
import com.google.android.apps.forscience.whistlepunk.sensorapi.SensorPresenter;
import com.google.android.apps.forscience.whistlepunk.sensorapi.SensorRecorder;
import com.google.android.apps.forscience.whistlepunk.sensorapi.SensorStatusListener;
import com.google.android.apps.forscience.whistlepunk.sensorapi.StreamConsumer;
import com.google.android.apps.forscience.whistlepunk.sensorapi.StreamStat;

import java.util.List;

public class ScalarInputDiscoverer implements ExternalSensorDiscoverer {
    private Consumer<AppDiscoveryCallbacks> mServiceFinder;

    /**
     * Mostly for testing's sake, there's an onion's worth of layers here.
     *
     * ScalarInputDiscoverer outsources the actual construction of ISensorDiscoverers to this
     * interface, so that we can run automated tests against ScalarInputDiscoverer without having
     * to guarantee that particular apps are actually installed on the test device.
     */
    public interface AppDiscoveryCallbacks {
        // Called with each service found
        public void onServiceFound(ISensorDiscoverer service);

        // Called after all services have been found
        public void onDiscoveryDone();
    }

    public ScalarInputDiscoverer(Consumer<AppDiscoveryCallbacks> serviceFinder) {
        mServiceFinder = serviceFinder;
    }

    @NonNull
    @Override
    public ExternalSensorSpec extractSensorSpec(Preference preference) {
        return new ScalarInputSpec(ManageDevicesFragment.getNameFromPreference(preference),
                ManageDevicesFragment.getAddressFromPreference(preference));
    }

    @Override
    public ExternalSensorProvider getProvider() {
        return new ExternalSensorProvider() {
            @Override
            public SensorChoice buildSensor(String sensorId, ExternalSensorSpec spec) {
                // TODO: implement more fully
                return new ScalarInputSensor(sensorId);
            }

            @Override
            public String getProviderId() {
                // TODO: implement
                return null;
            }

            @Override
            public ExternalSensorSpec buildSensorSpec(String name, byte[] config) {
                return new ScalarInputSpec(name, config);
            }
        };
    }

    @Override
    public boolean startScanning(final SensorPrefCallbacks callbacks, final Context context) {
        final ISensorConsumer.Stub sc = makeSensorConsumer(context, callbacks);
        mServiceFinder.take(new AppDiscoveryCallbacks() {
            @Override
            public void onServiceFound(final ISensorDiscoverer service) {
                try {
                    service.scanDevices(makeDeviceConsumer(service, sc));
                } catch (RemoteException e) {
                    callbacks.onScanError(e);
                }
            }

            @Override
            public void onDiscoveryDone() {
                // TODO: what to do here?  Somehow plumb back that we're done scanning?
            }
        });
        // TODO: should this ever be false?
        return true;
    }

    @NonNull
    private IDeviceConsumer.Stub makeDeviceConsumer(final ISensorDiscoverer service,
            final ISensorConsumer.Stub sc) {
        return new IDeviceConsumer.Stub() {
            @Override
            public void onDeviceFound(String deviceId, String name, PendingIntent settingsIntent)
                    throws RemoteException {
                service.scanSensors(deviceId, sc);
            }
        };
    }

    @NonNull
    private ISensorConsumer.Stub makeSensorConsumer(final Context context,
            final SensorPrefCallbacks callbacks) {
        return new ISensorConsumer.Stub() {
            @Override
            public void onSensorFound(String sensorId, String name, PendingIntent settingsIntent)
                    throws RemoteException {
                boolean isPaired = false;
                Preference newPref = ManageDevicesFragment.makePreference(name, sensorId,
                        ScalarInputSpec.TYPE, isPaired, context);
                callbacks.addAvailableSensorPreference(newPref);
            }
        };
    }

    @Override
    public void stopScanning() {
        // TODO: implement all of these!
    }

    private class ScalarInputSensor extends ScalarSensor {
        public ScalarInputSensor(String id) {
            super(id);
        }

        @Override
        protected SensorRecorder makeScalarControl(StreamConsumer c, SensorEnvironment environment,
                Context context, SensorStatusListener listener) {
            return new SensorRecorder() {
                @Override
                public void startObserving() {
                    // TODO: implement all!

                }

                @Override
                public void startRecording(String runId) {

                }

                @Override
                public void stopRecording() {

                }

                @Override
                public void stopObserving() {

                }

                @Override
                public boolean hasRecordedData() {
                    return false;
                }

                @Override
                public void applyOptions(ReadableSensorOptions settings) {

                }
            };
        }
    }
}
