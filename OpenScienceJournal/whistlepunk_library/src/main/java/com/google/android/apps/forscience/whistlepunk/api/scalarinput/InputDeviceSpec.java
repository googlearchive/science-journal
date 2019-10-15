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
import android.graphics.drawable.Drawable;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import android.util.Log;
import com.google.android.apps.forscience.javalib.FailureListener;
import com.google.android.apps.forscience.whistlepunk.R;
import com.google.android.apps.forscience.whistlepunk.SensorAppearance;
import com.google.android.apps.forscience.whistlepunk.SensorProvider;
import com.google.android.apps.forscience.whistlepunk.data.GoosciDeviceSpec;
import com.google.android.apps.forscience.whistlepunk.data.InputDevice;
import com.google.android.apps.forscience.whistlepunk.devicemanager.SensorDiscoverer;
import com.google.android.apps.forscience.whistlepunk.metadata.ExternalSensorSpec;
import com.google.android.apps.forscience.whistlepunk.metadata.MkrSciBleDeviceSpec;
import com.google.android.apps.forscience.whistlepunk.sensorapi.SensorChoice;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;
import com.google.common.html.HtmlEscapers;
import com.google.protobuf.InvalidProtocolBufferException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * An "ExternalSensorSpec" that identifies a device, which is, for our purposes, an abstract
 * grouping of connectable sensors that live on the same physical piece of hardware, in order to
 * group things helpfully for users that live in the physical world.
 *
 * <p>An input device, like an external sensor, has a name, address, and appearance, but it does not
 * allow one to actually create SensorChoices directly; SensorChoices are created from actual sensor
 * specs.
 */
public class InputDeviceSpec extends ExternalSensorSpec {
  public static SensorProvider PROVIDER =
      new SensorProvider() {
        @Override
        public SensorChoice buildSensor(String sensorId, ExternalSensorSpec spec) {
          // device entries are never actual streaming sensors
          return null;
        }

        @Override
        public ExternalSensorSpec buildSensorSpec(String name, byte[] config) {
          return new InputDeviceSpec(name, config);
        }
      };

  public static final SensorDiscoverer DISCOVERER =
      new SensorDiscoverer() {
        @Override
        public boolean startScanning(ScanListener listener, FailureListener onScanError) {
          // These are only remembered from the database, never discovered by scanning
          return false;
        }

        @Override
        public void stopScanning() {}

        @Override
        public SensorProvider getProvider() {
          return PROVIDER;
        }
      };

  public static final String TYPE = "InputDevice";
  private static final String TAG = "InputDeviceSpec";
  public static Map<String, SensorProvider> PROVIDER_MAP = ImmutableMap.of(TYPE, PROVIDER);

  /** The "address" given to the device on which the app is running. */
  public static final String BUILT_IN_DEVICE_ADDRESS = "BUILT_IN_DEVICE";

  private String name;
  private InputDevice.InputDeviceConfig config;

  public static InputDeviceSpec fromProto(GoosciDeviceSpec.DeviceSpec proto) {
    return new InputDeviceSpec(
        proto.getInfo().getProviderId(), proto.getInfo().getAddress(), proto.getName());
  }

  public InputDeviceSpec(String providerType, String deviceAddress, String deviceName) {
    config =
        InputDevice.InputDeviceConfig.newBuilder()
            .setProviderId(providerType)
            .setDeviceAddress(Preconditions.checkNotNull(deviceAddress))
            .build();
    name = deviceName;
  }

  public InputDeviceSpec(String name, byte[] config) {
    this.name = name;
    this.config = parse(config);
  }

  @Nullable
  private InputDevice.InputDeviceConfig parse(byte[] config) {
    try {
      return InputDevice.InputDeviceConfig.parseFrom(config);
    } catch (InvalidProtocolBufferException e) {
      if (Log.isLoggable(TAG, Log.ERROR)) {
        Log.e(TAG, "error parsing config", e);
      }
    }
    return null;
  }

  /**
   * Given two arbitrary strings representing parts of an address, join them in a guaranteed-unique
   * way, by HTML-escaping both parts and joining them with '&'
   */
  @NonNull
  public static String joinAddresses(String firstPart, String secondPart) {
    return escape(firstPart) + "&" + escape(secondPart);
  }

  @Override
  public SensorAppearance getSensorAppearance() {
    return new EmptySensorAppearance() {
      @Override
      public Drawable getIconDrawable(Context context) {
        if (getDeviceAddress().equals(BUILT_IN_DEVICE_ADDRESS)) {
          return context.getResources().getDrawable(R.drawable.ic_phone_android_black_48dp);
        }
        if (Objects.equals(getProviderType(), MkrSciBleDeviceSpec.TYPE)) {
          return context.getResources().getDrawable(R.drawable.ic_arduino_device_white_24dp);
        }
        return null;
      }

      @Override
      public String getName(Context context) {
        return InputDeviceSpec.this.getName();
      }
    };
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
    return joinAddresses("DEVICE", getDeviceAddress());
  }

  public static String escape(String string) {
    return HtmlEscapers.htmlEscaper().escape(string);
  }

  @Override
  public byte[] getConfig() {
    return config.toByteArray();
  }

  @Override
  public boolean shouldShowOptionsOnConnect() {
    return false;
  }

  public String getDeviceAddress() {
    return config.getDeviceAddress();
  }

  public static InputDeviceSpec builtInDevice(Context context) {
    return new InputDeviceSpec(
        TYPE, BUILT_IN_DEVICE_ADDRESS, context.getString(R.string.phone_sensors));
  }

  public String getProviderType() {
    return config.getProviderId();
  }

  @Override
  public String getGlobalDeviceAddress() {
    return InputDeviceSpec.joinAddresses(getProviderType(), getDeviceAddress());
  }

  public GoosciDeviceSpec.DeviceSpec asDeviceSpec() {
    return GoosciDeviceSpec.DeviceSpec.newBuilder()
        .setInfo(getGadgetInfo(getDeviceAddress()))
        .setName(getName())
        .build();
  }

  public static List<InputDeviceSpec> fromProtos(List<GoosciDeviceSpec.DeviceSpec> protos) {
    List<InputDeviceSpec> specs = new ArrayList<>(protos.size());
    for (GoosciDeviceSpec.DeviceSpec proto : protos) {
      specs.add(InputDeviceSpec.fromProto(proto));
    }
    return specs;
  }
}
