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

package com.google.android.apps.forscience.whistlepunk.devicemanager;

import android.content.Context;
import android.graphics.drawable.Drawable;
import androidx.fragment.app.FragmentManager;
import com.google.android.apps.forscience.javalib.FailureListener;
import com.google.android.apps.forscience.whistlepunk.SensorProvider;
import com.google.android.apps.forscience.whistlepunk.accounts.AppAccount;
import com.google.android.apps.forscience.whistlepunk.api.scalarinput.InputDeviceSpec;
import com.google.android.apps.forscience.whistlepunk.data.GoosciSensorSpec;

/** One way of discovering additional sensors that can be added to an experiment */
public interface SensorDiscoverer {
  public interface SettingsInterface {
    public void show(
        AppAccount appAccount,
        String experimentId,
        String sensorId,
        FragmentManager fragmentManager,
        boolean showForgetButton);
  }

  public interface DiscoveredSensor {
    GoosciSensorSpec.SensorSpec getSensorSpec();

    SettingsInterface getSettingsInterface();

    /**
     * @return true if this newly-discovered sensor from a scan should replace oldSensor in any
     *     experiments. (For example, in current implementations of the Scalar API). false if
     *     oldSensor is a valid, but different, setting of this same sensor (For example, in current
     *     implementations of native BLE sensors)
     */
    boolean shouldReplaceStoredSensor(ConnectableSensor oldSensor);
  }

  public interface ServiceConnectionError {
    String getErrorMessage();

    boolean canBeResolved();

    /**
     * Open a UI for resolving the error (adding a permission? Turning on a setting?)
     *
     * <p>Should eventually resume back to the opening activity and fragment.
     */
    void tryToResolve(FragmentManager fragmentManager);
  }

  public interface DiscoveredService {
    /** Should be unique for each discoverer */
    String getServiceId();

    String getName();

    Drawable getIconDrawable(Context context);

    /**
     * @return if there was an error connecting to the service, information about the error.
     *     Otherwise, null.
     */
    ServiceConnectionError getConnectionErrorIfAny();
  }

  public interface DiscoveredDevice {
    String getServiceId();

    InputDeviceSpec getSpec();
  }

  public interface ScanListener {
    /**
     * Called when a scan finds a new service (each provider has its own definition of what
     * consitutes a "service". For BLE, it's the entire native BLE mechanism. For scalar API, it's
     * each API-implementing service.
     */
    void onServiceFound(DiscoveredService service);

    /** Called when a scan finds a new device (on the UI thread) */
    void onDeviceFound(DiscoveredDevice device);

    /** Called when a scan finds a new sensor on a device (on the UI thread). */
    void onSensorFound(DiscoveredSensor sensor);

    /**
     * Called when all devices have been discovered on the service with the given id, or the scan
     * has timed out. No more devices are forthcoming.
     */
    void onServiceScanComplete(String serviceId);

    /** Called when all devices and sensors are found (on the UI thread). */
    void onScanDone();
  }

  /** @return true if starting scanning was successful */
  boolean startScanning(ScanListener listener, FailureListener onScanError);

  /** Stops scanning, and discards any state or references acquired during scanning */
  void stopScanning();

  /** @return the provider that can be used to generate a SensorChoice from the stored spec. */
  SensorProvider getProvider();
}
