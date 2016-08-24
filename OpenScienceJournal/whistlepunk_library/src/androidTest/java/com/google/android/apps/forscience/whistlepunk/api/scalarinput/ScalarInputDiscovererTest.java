package com.google.android.apps.forscience.whistlepunk.api.scalarinput;

import android.os.RemoteException;
import android.preference.Preference;
import android.test.AndroidTestCase;

import com.google.android.apps.forscience.javalib.Consumer;
import com.google.android.apps.forscience.whistlepunk.Arbitrary;
import com.google.android.apps.forscience.whistlepunk.devicemanager.ExternalSensorDiscoverer;
import com.google.android.apps.forscience.whistlepunk.devicemanager.ManageDevicesFragment;

import java.util.ArrayList;
import java.util.List;

public class ScalarInputDiscovererTest extends AndroidTestCase {
    public void testExtractFromPreference() {
        ScalarInputDiscoverer discoverer = new ScalarInputDiscoverer(null, null);
        String name = Arbitrary.string();
        String deviceId = Arbitrary.string();
        String serviceId = Arbitrary.string();
        Preference pref = ManageDevicesFragment.makePreference(name, deviceId, ScalarInputSpec.TYPE,
                false, getContext());
        ScalarInputSpec.addServiceId(pref, serviceId);
        ScalarInputSpec spec = (ScalarInputSpec) discoverer.extractSensorSpec(pref);
        assertEquals(ScalarInputSpec.TYPE, spec.getType());
        assertEquals(serviceId, spec.getServiceId());
    }

    public void testStartScanning() {
        final String serviceName = Arbitrary.string();
        final String deviceId = Arbitrary.string();
        final String deviceName = Arbitrary.string();
        final String sensorId = Arbitrary.string();
        final String sensorName = Arbitrary.string();
        final String serviceId = Arbitrary.string();

        ScalarInputDiscoverer sid = new ScalarInputDiscoverer(
                new Consumer<AppDiscoveryCallbacks>() {
                    @Override
                    public void take(AppDiscoveryCallbacks adc) {
                        adc.onServiceFound(serviceId, new TestSensorDiscoverer(serviceName) {
                            @Override
                            public void scanDevices(IDeviceConsumer c) throws RemoteException {
                                c.onDeviceFound(deviceId, deviceName, null);
                            }

                            @Override
                            public void scanSensors(String actualDeviceId, ISensorConsumer c)
                                    throws RemoteException {
                                assertEquals(deviceId, actualDeviceId);
                                c.onSensorFound(sensorId, sensorName, null);
                            }

                            @Override
                            public ISensorConnector getConnector() throws RemoteException {
                                return null;
                            }
                        });
                        adc.onDiscoveryDone();
                    }
                }, null);

        TestCallbacks callbacks = new TestCallbacks();
        assertEquals(true, sid.startScanning(callbacks, getContext()));
        assertEquals(1, callbacks.prefsSeen.size());
        Preference preference = callbacks.prefsSeen.get(0);
        assertEquals(false, ManageDevicesFragment.getIsPairedFromPreference(preference));
        assertEquals(ScalarInputSpec.TYPE, ManageDevicesFragment.getTypeFromPreference(preference));
        ScalarInputSpec spec = (ScalarInputSpec) sid.extractSensorSpec(preference);
        assertEquals(sensorName, spec.getName());
        assertEquals(sensorId, spec.getAddress());
        assertEquals(serviceId, spec.getServiceId());
    }

    public void testScanError() {
        final String serviceName = Arbitrary.string();
        final String errorText = Arbitrary.string();

        ScalarInputDiscoverer sid = new ScalarInputDiscoverer(
                new Consumer<AppDiscoveryCallbacks>() {
                    @Override
                    public void take(AppDiscoveryCallbacks adc) {
                        adc.onServiceFound(null, new TestSensorDiscoverer(serviceName) {
                            @Override
                            public void scanDevices(IDeviceConsumer c) throws RemoteException {
                                throw new RemoteException(errorText);
                            }

                            @Override
                            public void scanSensors(String actualDeviceId, ISensorConsumer c)
                                    throws RemoteException {
                                throw new RemoteException("Should never be thrown");
                            }

                            @Override
                            public ISensorConnector getConnector() throws RemoteException {
                                return null;
                            }
                        });
                    }
                }, null);

        TestCallbacks callbacks = new TestCallbacks();
        sid.startScanning(callbacks, getContext());
        assertEquals(0, callbacks.prefsSeen.size());
        assertEquals(1, callbacks.errorsSeen.size());
        assertTrue(callbacks.errorsSeen.get(0).getMessage().contains(errorText));
    }

    private static abstract class TestSensorDiscoverer extends ISensorDiscoverer.Stub {
        private String mServiceName;

        public TestSensorDiscoverer(String serviceName) {
            mServiceName = serviceName;
        }

        @Override
        public String getName() throws RemoteException {
            return mServiceName;
        }
    }

    private static class TestCallbacks implements ExternalSensorDiscoverer.SensorPrefCallbacks {
        public List<Preference> prefsSeen = new ArrayList<>();
        public List<Exception> errorsSeen = new ArrayList<>();

        @Override
        public boolean isSensorAlreadyKnown(String key) {
            return false;
        }

        @Override
        public void addAvailableSensorPreference(Preference newPref) {
            prefsSeen.add(newPref);
        }

        @Override
        public void onScanError(Exception e) {
            errorsSeen.add(e);
        }
    }
}