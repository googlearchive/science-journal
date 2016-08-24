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
