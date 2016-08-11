package com.google.android.apps.forscience.whistlepunk.api.scalarinput;

import android.app.PendingIntent;
import android.content.Context;
import android.os.RemoteException;
import android.preference.Preference;
import android.support.annotation.NonNull;

import com.google.android.apps.forscience.javalib.Consumer;
import com.google.android.apps.forscience.whistlepunk.devicemanager.ExternalSensorDiscoverer;
import com.google.android.apps.forscience.whistlepunk.devicemanager.ManageDevicesFragment;
import com.google.android.apps.forscience.whistlepunk.metadata.BleSensorSpec;
import com.google.android.apps.forscience.whistlepunk.metadata.ExternalSensorSpec;

public class ScalarInputDiscoverer implements ExternalSensorDiscoverer {
    public static final String INTENT_ACTION =
            "com.google.android.apps.forscience.whistlepunk.ADDED_SENSOR";
    private static final String TAG = "ThirdPartyDisc";
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
        public void onServiceFound(ISensorDiscoverer.Stub service);

        // Called after all services have been found
        public void onDiscoveryDone();
    }

    public ScalarInputDiscoverer(Consumer<AppDiscoveryCallbacks> serviceFinder) {
        mServiceFinder = serviceFinder;
    }

    // TODO: implement all of these!

    @NonNull
    @Override
    public ExternalSensorSpec extractSensorSpec(Preference preference) {
        return new ScalarInputSpec(ManageDevicesFragment.getNameFromPreference(preference),
                ManageDevicesFragment.getAddressFromPreference(preference));
    }

    @Override
    public boolean startScanning(final SensorPrefCallbacks callbacks, final Context context) {
        final ISensorConsumer.Stub sc = makeSensorConsumer(context, callbacks);
        mServiceFinder.take(new AppDiscoveryCallbacks() {
            @Override
            public void onServiceFound(final ISensorDiscoverer.Stub service) {
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
    private IDeviceConsumer.Stub makeDeviceConsumer(final ISensorDiscoverer.Stub service,
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
    }
}
