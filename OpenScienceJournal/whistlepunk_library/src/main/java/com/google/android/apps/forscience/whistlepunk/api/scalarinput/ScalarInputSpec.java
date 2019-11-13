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

import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import android.util.Log;
import com.google.android.apps.forscience.whistlepunk.ImageViewSensorAnimationBehavior;
import com.google.android.apps.forscience.whistlepunk.R;
import com.google.android.apps.forscience.whistlepunk.SensorAnimationBehavior;
import com.google.android.apps.forscience.whistlepunk.SensorAppearance;
import com.google.android.apps.forscience.whistlepunk.data.GoosciScalarInput;
import com.google.android.apps.forscience.whistlepunk.data.GoosciScalarInput.ScalarInputConfig;
import com.google.android.apps.forscience.whistlepunk.metadata.ExternalSensorSpec;
import com.google.common.base.Preconditions;
import com.google.protobuf.InvalidProtocolBufferException;

public class ScalarInputSpec extends ExternalSensorSpec {
  public static final String TYPE = "ScalarInput";
  private static final String TAG = "ScalarInputSpec";

  private String name;
  private GoosciScalarInput.ScalarInputConfig config;

  public ScalarInputSpec(
      String sensorName,
      String serviceId,
      String address,
      SensorBehavior behavior,
      SensorAppearanceResources ids,
      String deviceId,
      int orderInExperimentApiSensors) {
    name = sensorName;
    ScalarInputConfig.Builder scalarInputConfig =
        GoosciScalarInput.ScalarInputConfig.newBuilder()
            .setServiceId(Preconditions.checkNotNull(serviceId))
            .setAddress(address)
            .setOrderInExperimentApiSensors(orderInExperimentApiSensors)
            .setDeviceId(deviceId);

    if (behavior != null) {
      if (behavior.loggingId != null) {
        scalarInputConfig.setLoggingId(behavior.loggingId);
      }
      scalarInputConfig
          .setShouldShowOptionsOnConnect(behavior.shouldShowSettingsOnConnect)
          .setExpectedSamplesPerSecond(behavior.expectedSamplesPerSecond);
    }

    writeResourceIds(scalarInputConfig, ids);
    config = scalarInputConfig.build();
  }

  public ScalarInputSpec(
      String sensorName,
      String serviceId,
      String address,
      SensorBehavior behavior,
      SensorAppearanceResources ids,
      String deviceId) {
    // TODO: inline?
    this(sensorName, serviceId, address, behavior, ids, deviceId, 0);
  }

  private void writeResourceIds(ScalarInputConfig.Builder config, SensorAppearanceResources ids) {
    if (ids != null) {
      config
          .setIconId(ids.iconId)
          .setUnits(emptyIfNull(ids.units))
          .setShortDescription(emptyIfNull(ids.shortDescription));
    }
  }

  private String emptyIfNull(String s) {
    return s == null ? "" : s;
  }

  public ScalarInputSpec(String sensorName, byte[] config) {
    name = sensorName;
    this.config = parse(config);
  }

  @Nullable
  private GoosciScalarInput.ScalarInputConfig parse(byte[] config) {
    try {
      return GoosciScalarInput.ScalarInputConfig.parseFrom(config);
    } catch (InvalidProtocolBufferException e) {
      if (Log.isLoggable(TAG, Log.ERROR)) {
        Log.e(TAG, "error parsing config", e);
      }
    }
    return null;
  }

  @Override
  public SensorAppearance getSensorAppearance() {
    // TODO: better icon?
    return new EmptySensorAppearance() {
      @Override
      public String getName(Context context) {
        return name;
      }

      @Override
      public Drawable getIconDrawable(Context context) {
        if (config.getIconId() <= 0) {
          return getDefaultIcon(context);
        }
        try {
          // TODO: test this?
          return getApiAppResources(context).getDrawable(config.getIconId());
        } catch (PackageManager.NameNotFoundException e) {
          if (Log.isLoggable(TAG, Log.ERROR)) {
            Log.e(TAG, "Package has gone missing: " + getPackageId());
          }
          return getDefaultIcon(context);
        } catch (Resources.NotFoundException e) {
          if (Log.isLoggable(TAG, Log.ERROR)) {
            Log.e(TAG, "Didn't find icon", e);
          }
          return getDefaultIcon(context);
        }
      }

      private Drawable getDefaultIcon(Context context) {
        return context.getResources().getDrawable(getDefaultIconId());
      }

      @Override
      public String getUnits(Context context) {
        return config.getUnits();
      }

      @Override
      public String getShortDescription(Context context) {
        return config.getShortDescription();
      }

      private Resources getApiAppResources(Context context)
          throws PackageManager.NameNotFoundException {
        return context.getPackageManager().getResourcesForApplication(getPackageId());
      }

      @Override
      public SensorAnimationBehavior getSensorAnimationBehavior() {
        return new ImageViewSensorAnimationBehavior(
            R.drawable.generic_sensor_level_drawable,
            ImageViewSensorAnimationBehavior.TYPE_RELATIVE_SCALE);
      }
    };
  }

  private String getPackageId() {
    return getPackageId(ScalarInputSpec.this.getServiceId());
  }

  public static Drawable getServiceDrawable(String serviceId, Context context) {
    try {
      return context.getPackageManager().getApplicationIcon(getPackageId(serviceId));
    } catch (PackageManager.NameNotFoundException e) {
      return context.getResources().getDrawable(R.drawable.generic_sensor_white_1);
    }
  }

  public int getDefaultIconId() {
    switch (config.getOrderInExperimentApiSensors() % 4) {
      case 0:
        return R.drawable.generic_sensor_white_1;
      case 1:
        return R.drawable.generic_sensor_white_2;
      case 2:
        return R.drawable.generic_sensor_white_3;
      case 3:
        return R.drawable.generic_sensor_white_4;
      default:
        // Should never happen, if math works.
        return R.drawable.generic_sensor_white_1;
    }
  }

  @Override
  public String getName() {
    return name;
  }

  @Override
  public String getType() {
    return TYPE;
  }

  @Override
  public String getAddress() {
    return InputDeviceSpec.joinAddresses(getServiceId(), getSensorAddressInService());
  }

  public String getSensorAddressInService() {
    return config.getAddress();
  }

  @Override
  public byte[] getConfig() {
    return config.toByteArray();
  }

  @Override
  public boolean shouldShowOptionsOnConnect() {
    return config.getShouldShowOptionsOnConnect();
  }

  @Override
  public String getDeviceAddress() {
    return makeApiDeviceAddress(getServiceId(), getDeviceId());
  }

  @NonNull
  public static String makeApiDeviceAddress(String serviceId, String deviceId) {
    return InputDeviceSpec.joinAddresses(serviceId, deviceId);
  }

  public String getDeviceId() {
    return config.getDeviceId();
  }

  public String getServiceId() {
    return config.getServiceId();
  }

  private static String getPackageId(String serviceId) {
    // TODO: write test!
    return serviceId.split("/")[0];
  }

  @Override
  public String getLoggingId() {
    return InputDeviceSpec.joinAddresses(getServiceId(), config.getLoggingId());
  }

  public float getExpectedSamplesPerSecond() {
    return config.getExpectedSamplesPerSecond();
  }

  @Override
  public ExternalSensorSpec maybeAdjustBeforePairing(int numPairedBeforeAdded) {
    if (numPairedBeforeAdded == config.getOrderInExperimentApiSensors()) {
      return this;
    }
    ScalarInputConfig copyConfig =
        parse(config.toByteArray()).toBuilder()
            .setOrderInExperimentApiSensors(numPairedBeforeAdded)
            .build();
    return new ScalarInputSpec(name, copyConfig.toByteArray());
  }
}
