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
  private final List<ChartData.DataPoint> dataToSend;
  private final String sensorId;
  private ISensorObserver observer = null;
  private ISensorStatusListener listener;

  public TestConnector(List<ChartData.DataPoint> dataToSend, String sensorId) {
    this.dataToSend = dataToSend;
    this.sensorId = sensorId;
  }

  @Override
  public void startObserving(
      String sensorId, ISensorObserver observer, ISensorStatusListener listener, String settingsKey)
      throws RemoteException {
    assertEquals(this.sensorId, sensorId);
    assertNull(this.observer);
    this.observer = observer;
    this.listener = listener;
    listener.onSensorConnected();
  }

  @Override
  public void stopObserving(String sensorId) throws RemoteException {
    assertEquals(this.sensorId, sensorId);
    assertNotNull(observer);
    for (ChartData.DataPoint point : dataToSend) {
      observer.onNewData(point.getX(), point.getY());
    }
    listener.onSensorDisconnected();
    listener = null;
    observer = null;
  }
}
