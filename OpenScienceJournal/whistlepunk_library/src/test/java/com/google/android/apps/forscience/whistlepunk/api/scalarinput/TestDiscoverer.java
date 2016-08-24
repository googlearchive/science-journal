package com.google.android.apps.forscience.whistlepunk.api.scalarinput;

import android.os.RemoteException;

class TestDiscoverer extends ISensorDiscoverer.Stub {
    private final ISensorConnector mTestConnector;

    public TestDiscoverer(ISensorConnector testConnector) {
        mTestConnector = testConnector;
    }

    @Override
    public String getName() throws RemoteException {
        return null;
    }

    @Override
    public void scanDevices(IDeviceConsumer c) throws RemoteException {

    }

    @Override
    public void scanSensors(String deviceId, ISensorConsumer c) throws RemoteException {

    }

    @Override
    public ISensorConnector getConnector() throws RemoteException {
        return mTestConnector;
    }
}
