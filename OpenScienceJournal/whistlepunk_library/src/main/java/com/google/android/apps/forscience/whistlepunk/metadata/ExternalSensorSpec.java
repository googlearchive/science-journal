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

package com.google.android.apps.forscience.whistlepunk.metadata;

import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;
import com.google.android.apps.forscience.whistlepunk.SensorAppearance;
import com.google.android.apps.forscience.whistlepunk.SensorProvider;
import com.google.android.apps.forscience.whistlepunk.api.scalarinput.InputDeviceSpec;
import com.google.android.apps.forscience.whistlepunk.data.GoosciGadgetInfo;
import com.google.android.apps.forscience.whistlepunk.data.GoosciSensorAppearance;
import com.google.android.apps.forscience.whistlepunk.data.GoosciSensorSpec;
import com.google.android.apps.forscience.whistlepunk.devicemanager.SensorDiscoverer;
import com.google.common.base.Preconditions;
import com.google.protobuf.ByteString;
import java.util.Arrays;
import java.util.Map;
import java.util.Objects;

/**
 * Represents a specification of an external sensor, including its name and address. Subclasses may
 * include additional options.
 */
public abstract class ExternalSensorSpec {
  private static final String TAG = "ExternalSensorSpec";

  protected ExternalSensorSpec() {
    // do nothing
  }

  /**
   * Returns a suggested sensorId for the given spec. Answer depends on spec address, type, and
   * name, but _not_ configuration.
   */
  public static String getSensorId(ExternalSensorSpec spec, int suffix) {
    return spec.getType() + "-" + spec.getAddress() + "-" + spec.getName() + "-" + suffix;
  }

  /** Returns redacted info suitable for logging. */
  public String getLoggingId() {
    return getType();
  }

  public abstract String getName();

  public abstract String getType();

  /**
   * @return the address at which this sensor sits. This is opaque to whistlepunk; each sensor that
   *     an {@link SensorDiscoverer} returns must have a unique address, but that address need not
   *     have any specific semantic relationship to how the sensor is physically addressed.
   */
  public abstract String getAddress();

  /**
   * @return an "address" for the device that hosts this sensor. This is opaque to whistlepunk; each
   *     device that is referred to by a sensor that an {@link SensorDiscoverer} returns must have a
   *     unique address, but that address need not have any specific semantic relationship to how
   *     the device is physically addressed.
   *     <p>Device addresses and sensor addresses are in two different namespaces; it is not a
   *     problem if a device and the sensor on it have the same address.
   *     <p>The default implementation uses the sensor address for the device address, assuming that
   *     each device has exactly one sensor, and vice versa
   */
  public String getDeviceAddress() {
    return getAddress();
  };

  /**
   * @return an "address" for the device that includes this spec's type, guaranteeing the address is
   *     unique even across sensor providers
   */
  public String getGlobalDeviceAddress() {
    return InputDeviceSpec.joinAddresses(getType(), getDeviceAddress());
  }

  /**
   * @return a sensor appearance for this sensor. Note that if this returns an appearance with a
   *     name different than what {@link #getName()} returns, this will lead to odd undefined
   *     behavior.
   */
  public abstract SensorAppearance getSensorAppearance();

  /**
   * Returns a serialized version of internal state of the sensor, suitable for long term storage.
   */
  @VisibleForTesting
  public abstract byte[] getConfig();

  /**
   * @return true iff {@code spec} is the same type, at the same address, and the same
   *     configuration.
   */
  public final boolean isSameSensorAndSpec(ExternalSensorSpec spec) {
    return isSameType(spec) && Arrays.equals(spec.getConfig(), getConfig());
  }

  private boolean isSameType(ExternalSensorSpec spec) {
    return spec != null && Objects.equals(spec.getType(), getType());
  }

  /**
   * @return true iff {@code spec} is the same type, at the same address (but potentially different
   *     configuration.
   */
  public boolean isSameSensor(ExternalSensorSpec spec) {
    return isSameType(spec) && Objects.equals(spec.getAddress(), getAddress());
  }

  @Override
  public String toString() {
    return "ExternalSensorSpec(" + getType() + "," + getAddress() + ": " + getName() + ")";
  }

  public abstract boolean shouldShowOptionsOnConnect();

  /**
   * Specs may choose to produce an edited version based on the order within the experiment
   * (perhaps, as in the case of ScalarInputSpec, to produce a different icon depending on order).
   *
   * <p>This should return either a reference to the same object, or to a different spec object with
   * different values. It should _not_ mutate the underlying object.
   */
  public ExternalSensorSpec maybeAdjustBeforePairing(int numPairedBeforeAdded) {
    return this;
  }

  public GoosciSensorSpec.SensorSpec asGoosciSpec() {
    // TODO: fill in other fields here?  hostDescription?  hostId?
    return GoosciSensorSpec.SensorSpec.newBuilder()
        .setInfo(getGadgetInfo(getAddress()))
        .setRememberedAppearance(
            GoosciSensorAppearance.BasicSensorAppearance.newBuilder().setName(getName()))
        .setConfig(ByteString.copyFrom(getConfig()))
        .build();
  }

  /**
   * @return a gadget info for the connected gadget. To get complete info for the sensor this spec
   *     represents, pass in {@link #getAddress()}. For the device, pass in {@link
   *     #getDeviceAddress()}.
   */
  @NonNull
  public GoosciGadgetInfo.GadgetInfo getGadgetInfo(String address) {
    // TODO: fill in other gadget info fields
    return GoosciGadgetInfo.GadgetInfo.newBuilder()
        .setProviderId(getType())
        .setAddress(address)
        .build();
  }

  public static ExternalSensorSpec fromGoosciSpec(
      GoosciSensorSpec.SensorSpec spec, Map<String, SensorProvider> providerMap) {
    Preconditions.checkNotNull(providerMap);
    if (spec == null) {
      return null;
    }
    GoosciGadgetInfo.GadgetInfo info =
        Preconditions.checkNotNull(spec.hasInfo() ? spec.getInfo() : null);
    SensorProvider sensorProvider = providerMap.get(info.getProviderId());
    if (sensorProvider == null) {
      throw new IllegalArgumentException(
          "No provider for sensor type: "
              + info.getProviderId()
              + ". Options: "
              + providerMap.keySet());
    }

    return sensorProvider.buildSensorSpec(
        spec.getRememberedAppearance().getName(), spec.getConfig().toByteArray());
  }

  public static GoosciSensorSpec.SensorSpec toGoosciSpec(ExternalSensorSpec spec) {
    return spec == null ? null : spec.asGoosciSpec();
  }
}
