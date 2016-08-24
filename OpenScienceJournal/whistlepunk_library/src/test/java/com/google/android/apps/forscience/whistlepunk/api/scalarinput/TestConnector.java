package com.google.android.apps.forscience.whistlepunk.api.scalarinput;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import android.os.RemoteException;

import com.google.android.apps.forscience.whistlepunk.scalarchart.ChartData;

import java.util.List;

class TestConnector extends ISensorConnector.Stub {
    private final List<ChartData.DataPoint> mDataToSend;
    private final String mSensorId;
    private ISensorObserver mObserver = null;
    private ISensorStatusListener mListener;

    public TestConnector(List<ChartData.DataPoint> dataToSend, String sensorId) {
        mDataToSend = dataToSend;
        mSensorId = sensorId;
    }

    @Override
    public void startObserving(String sensorId, ISensorObserver observer,
            ISensorStatusListener listener, String settingsKey) throws RemoteException {
        assertEquals(mSensorId, sensorId);
        assertNull(mObserver);
        mObserver = observer;
        mListener = listener;
        listener.onSensorConnected();
    }

    @Override
    public void stopObserving(String sensorId) throws RemoteException {
        assertEquals(mSensorId, sensorId);
        assertNotNull(mObserver);
        for (ChartData.DataPoint point : mDataToSend) {
            mObserver.onNewData(point.getX(), point.getY());
        }
        mListener.onSensorDisconnected();
        mListener = null;
        mObserver = null;
    }
}
