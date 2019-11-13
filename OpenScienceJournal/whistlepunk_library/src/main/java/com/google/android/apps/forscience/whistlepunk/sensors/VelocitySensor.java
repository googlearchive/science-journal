/*
 *  Copyright 2019 Google Inc. All Rights Reserved.
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

package com.google.android.apps.forscience.whistlepunk.sensors;

import android.app.ActivityManager;
import android.content.Context;
import android.os.Build;
import android.util.Log;
import com.google.android.apps.forscience.whistlepunk.Flags;
import com.google.android.apps.forscience.whistlepunk.filemetadata.SensorLayoutPojo;
import com.google.android.apps.forscience.whistlepunk.sensorapi.AbstractSensorRecorder;
import com.google.android.apps.forscience.whistlepunk.sensorapi.ScalarSensor;
import com.google.android.apps.forscience.whistlepunk.sensorapi.SensorEnvironment;
import com.google.android.apps.forscience.whistlepunk.sensorapi.SensorRecorder;
import com.google.android.apps.forscience.whistlepunk.sensorapi.SensorStatusListener;
import com.google.android.apps.forscience.whistlepunk.sensorapi.StreamConsumer;
import com.google.ar.core.ArCoreApk;

/** Class to mock a velocity sensor. */
public class VelocitySensor extends ScalarSensor {

  public static final String ID = "VelocitySensor";
  private static final String TAG = "VelocitySensor";
  private static final double MIN_OPENGL_VERSION = 3.0;
  private StreamConsumer consumer;
  private SensorEnvironment sensorEnvironment;

  public VelocitySensor() {
    super(ID);
  }

  @Override
  protected SensorRecorder makeScalarControl(
      final StreamConsumer c,
      final SensorEnvironment environment,
      final Context context,
      final SensorStatusListener listener) {
    return new AbstractSensorRecorder() {
      @Override
      public void startObserving() {
        listener.onSourceStatus(getId(), SensorStatusListener.STATUS_CONNECTED);
        consumer = c;
        sensorEnvironment = environment;
      }

      @Override
      public void stopObserving() {
        listener.onSourceStatus(getId(), SensorStatusListener.STATUS_DISCONNECTED);
        consumer = null;
        sensorEnvironment = null;
      }
    };
  }

  public static boolean isVelocitySensorAvailable(Context appContext) {
    if (Flags.showVelocityTrackerOption()) {
      ArCoreApk.Availability availability = null;
      try {
        // Calling checkAvailability() will determine if ARCore is supported on this device.
        availability = ArCoreApk.getInstance().checkAvailability(appContext);
      } catch (NullPointerException e) {
        // Unclear why NPE is happening in ArCoreApkImpl.checkInstallActivity
        // TODO(b/141910242): Investigate why this NPE is happening
        if (Log.isLoggable(TAG, Log.ERROR)) {
          Log.e(TAG, "NPE initiating ARCore check", e);
        }
      }
      if (availability != null) {
        // Transient means this state is temporary and we should re-check availability soon.
        if (availability.isTransient()) {
          // TODO (b/139126555): update this method to re-query if availability is transient
          return false;
        }

        // Check that the device is compatible with Sceneform.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
          Log.e(TAG, "Sceneform requires Android N or later");
          return false;
        }

        // Do a runtime check for the OpenGL level available at runtime.
        String openGlVersionString =
            ((ActivityManager) appContext.getSystemService(Context.ACTIVITY_SERVICE))
                .getDeviceConfigurationInfo()
                .getGlEsVersion();
        if (Double.parseDouble(openGlVersionString) < MIN_OPENGL_VERSION) {
          Log.e(TAG, "Sceneform requires OpenGL ES 3.0 or later");
          return false;
        }

        return availability.isSupported();
      }
    }
    return false;
  }

  public void setNextVelocity(float velocityValue) {
    if (consumer != null && sensorEnvironment != null) {
      consumer.addData(sensorEnvironment.getDefaultClock().getNow(), velocityValue);
    }
  }

  public SensorLayoutPojo buildLayout() {
    SensorLayoutPojo layout = new SensorLayoutPojo();
    layout.setSensorId(ID);

    return layout;
  }
}
