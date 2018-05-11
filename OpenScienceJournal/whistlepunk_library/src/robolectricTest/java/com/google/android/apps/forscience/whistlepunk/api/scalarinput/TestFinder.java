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

import android.os.IBinder;
import android.os.RemoteException;
import com.google.android.apps.forscience.javalib.Consumer;

class TestFinder extends Consumer<AppDiscoveryCallbacks> {
  private final String serviceId;
  public ISensorObserver observer;
  public ISensorStatusListener listener;

  public TestFinder(String serviceId) {
    this.serviceId = serviceId;
  }

  @Override
  public void take(AppDiscoveryCallbacks adc) {
    adc.onServiceFound(
        serviceId,
        new ISensorDiscoverer.Stub() {
          @Override
          public String getName() throws RemoteException {
            return null;
          }

          @Override
          public void scanDevices(IDeviceConsumer c) throws RemoteException {}

          @Override
          public void scanSensors(String deviceId, ISensorConsumer c) throws RemoteException {}

          @Override
          public ISensorConnector getConnector() throws RemoteException {
            return new ISensorConnector() {
              @Override
              public void startObserving(
                  String sensorAddress,
                  ISensorObserver observer,
                  ISensorStatusListener listener,
                  String settingsKey)
                  throws RemoteException {
                TestFinder.this.listener = listener;
                TestFinder.this.observer = observer;
                signalConnected(listener);
              }

              @Override
              public void stopObserving(String sensorAddress) throws RemoteException {}

              @Override
              public IBinder asBinder() {
                return null;
              }
            };
          }
        });
  }

  protected void signalConnected(ISensorStatusListener listener) throws RemoteException {
    listener.onSensorConnected();
  }
}
