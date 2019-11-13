package com.google.android.apps.forscience.whistlepunk.metadata;

import com.google.android.apps.forscience.whistlepunk.SensorAppearance;
import com.google.android.apps.forscience.whistlepunk.devicemanager.SensorTypeProvider;

/** Represents a specification of a MkrSciBle device. */
public class MkrSciBleDeviceSpec extends ExternalSensorSpec {

  public static final String TYPE = "bluetooth_le";

  private String address;

  private String name;

  public MkrSciBleDeviceSpec(String address, String name) {
    this.address = address;
    this.name = name;
  }

  @Override
  public String getType() {
    return TYPE;
  }

  @Override
  public String getAddress() {
    return address;
  }

  @Override
  public String getName() {
    return name;
  }

  @Override
  public SensorAppearance getSensorAppearance() {
    return SensorTypeProvider.getSensorAppearance(SensorTypeProvider.TYPE_CUSTOM, name);
  }

  @Override
  public byte[] getConfig() {
    return null;
  }

  @Override
  public boolean shouldShowOptionsOnConnect() {
    return false;
  }
}
