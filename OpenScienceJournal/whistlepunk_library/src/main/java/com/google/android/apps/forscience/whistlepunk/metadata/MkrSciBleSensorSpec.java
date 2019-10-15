package com.google.android.apps.forscience.whistlepunk.metadata;

import androidx.annotation.VisibleForTesting;
import android.util.Log;
import com.google.android.apps.forscience.whistlepunk.MkrSciBleSensorAppearance;
import com.google.android.apps.forscience.whistlepunk.SensorAppearance;
import com.google.android.apps.forscience.whistlepunk.data.GoosciMkrSciSensorConfig.MkrSciBleSensorConfig;
import java.util.Objects;

/** Represents a specification of a MkrSciBle sensor. */
public class MkrSciBleSensorSpec extends ExternalSensorSpec {
  private static final String TAG = "MkrSciBleSensorSpec";
  public static final String TYPE = MkrSciBleDeviceSpec.TYPE;

  private String name;

  private MkrSciBleSensorConfig config;

  public MkrSciBleSensorSpec(String address, String sensor, String name) {
    this.name = name;
    config =
        MkrSciBleSensorConfig.newBuilder()
            .setAddress(address)
            .setSensor(sensor)
            .setHandler("")
            .build();
  }

  public MkrSciBleSensorSpec(String name, byte[] config) {
    this.name = name;
    loadFromConfig(config);
  }

  @Override
  public String getType() {
    return TYPE;
  }

  @Override
  public String getAddress() {
    return config.getAddress();
  }

  @Override
  public String getName() {
    return name;
  }

  public String getSensor() {
    return config.getSensor();
  }

  public void setHandler(String handler) {
    config = config.toBuilder().setHandler(handler).build();
  }

  public String getHandler() {
    return config.getHandler();
  }

  @Override
  public SensorAppearance getSensorAppearance() {
    return MkrSciBleSensorAppearance.get(config.getSensor(), config.getHandler());
  }

  @Override
  public byte[] getConfig() {
    return config.toByteArray();
  }

  @Override
  public boolean shouldShowOptionsOnConnect() {
    return true;
  }

  @VisibleForTesting
  public void loadFromConfig(byte[] data) {
    try {
      config = MkrSciBleSensorConfig.parseFrom(data);
    } catch (Exception e) {
      Log.e(TAG, "Could not deserialize config", e);
      throw new IllegalStateException(e);
    }
  }

  @Override
  public boolean isSameSensor(ExternalSensorSpec spec) {
    if (spec instanceof MkrSciBleSensorSpec) {
      MkrSciBleSensorSpec aux = (MkrSciBleSensorSpec) spec;
      return Objects.equals(aux.config.getAddress(), config.getAddress())
          && Objects.equals(aux.config.getSensor(), config.getSensor());
    }
    return false;
  }
}
