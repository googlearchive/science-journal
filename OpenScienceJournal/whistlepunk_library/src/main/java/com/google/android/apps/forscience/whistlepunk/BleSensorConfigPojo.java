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
package com.google.android.apps.forscience.whistlepunk;

import com.google.android.apps.forscience.whistlepunk.data.GoosciSensorConfig.BleSensorConfig;
import com.google.android.apps.forscience.whistlepunk.data.GoosciSensorConfig.BleSensorConfig.ScaleTransform;
import java.util.Objects;

/** POJO object for BleSensorConfig proto. */
public class BleSensorConfigPojo {
  private String address;
  private String sensorType;
  private String customPin;
  private Boolean customFrequency;
  private ScaleTransform customScaleTransform;

  public BleSensorConfigPojo() {}

  public BleSensorConfigPojo(BleSensorConfig proto) {
    address = proto.hasAddress() ? proto.getAddress() : null;
    sensorType = proto.hasSensorType() ? proto.getSensorType() : null;
    customPin = proto.hasCustomPin() ? proto.getCustomPin() : null;
    customFrequency = proto.hasCustomFrequency() ? proto.getCustomFrequency() : null;
    customScaleTransform = proto.hasCustomScaleTransform() ? proto.getCustomScaleTransform() : null;
  }

  public BleSensorConfig toProto() {
    BleSensorConfig.Builder builder = BleSensorConfig.newBuilder();
    if (address != null) {
      builder.setAddress(address);
    }
    if (sensorType != null) {
      builder.setSensorType(sensorType);
    }
    if (customPin != null) {
      builder.setCustomPin(customPin);
    }
    if (customFrequency != null) {
      builder.setCustomFrequency(customFrequency);
    }
    if (customScaleTransform != null) {
      builder.setCustomScaleTransform(customScaleTransform);
    }
    return builder.build();
  }

  public void setAddress(String address) {
    this.address = address;
  }

  public String getAddress() {
    return address;
  }

  public void setSensorType(String sensorType) {
    this.sensorType = sensorType;
  }

  public String getSensorType() {
    return sensorType;
  }

  public void setCustomPin(String customPin) {
    this.customPin = customPin;
  }

  public String getCustomPin() {
    return customPin;
  }

  public void setCustomFrequency(boolean customFrequency) {
    this.customFrequency = customFrequency;
  }

  public boolean getCustomFrequency() {
    return (customFrequency != null) && customFrequency;
  }

  public void setCustomScaleTransform(ScaleTransform customScaleTransform) {
    this.customScaleTransform = customScaleTransform;
  }

  public ScaleTransform getCustomScaleTransform() {
    return customScaleTransform;
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder().append("{");
    if (address != null) {
      sb.append(" address: \"").append(address).append("\"");
    }
    if (sensorType != null) {
      sb.append(" sensor_type: \"").append(sensorType).append("\"");
    }
    if (customPin != null) {
      sb.append(" custom_pin: \"").append(customPin).append("\"");
    }
    if (customFrequency != null) {
      sb.append(" custom_frequency: ").append(customFrequency);
    }
    if (customScaleTransform != null) {
      sb.append("custom_scale_transform: {");
      if (customScaleTransform.hasDestBottom()) {
        sb.append(" dest_bottom: ").append(customScaleTransform.getDestBottom());
      }
      if (customScaleTransform.hasDestTop()) {
        sb.append(" dest_top: ").append(customScaleTransform.getDestTop());
      }
      if (customScaleTransform.hasDestBottom()) {
        sb.append(" source_bottom: ").append(customScaleTransform.getSourceBottom());
      }
      if (customScaleTransform.hasSourceTop()) {
        sb.append(" source_top: ").append(customScaleTransform.getSourceTop());
      }
      sb.append("}");
    }
    sb.append("}");
    return sb.toString();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof BleSensorConfigPojo)) {
      return false;
    }

    BleSensorConfigPojo bleSensorConfigPojo = (BleSensorConfigPojo) o;

    return Objects.equals(address, bleSensorConfigPojo.address)
        && Objects.equals(sensorType, bleSensorConfigPojo.sensorType)
        && Objects.equals(customPin, bleSensorConfigPojo.customPin)
        && Objects.equals(customFrequency, bleSensorConfigPojo.customFrequency)
        && Objects.equals(customScaleTransform, bleSensorConfigPojo.customScaleTransform);
  }

  @Override
  public int hashCode() {
    return Objects.hash(address, sensorType, customPin, customFrequency, customScaleTransform);
  }
}
