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

package com.google.android.apps.forscience.whistlepunk.filemetadata;

import com.google.android.apps.forscience.whistlepunk.data.GoosciDeviceSpec.DeviceSpec;
import java.util.Objects;

/** Represents a device, Android or iOS. Used in File Versions and User Metadata. */
public class DeviceSpecPojo {

  private GadgetInfoPojo gadgetInfo;
  private String name = "";

  public GadgetInfoPojo getGadgetInfo() {
    return gadgetInfo;
  }

  public void setGadgetInfo(GadgetInfoPojo gadgetInfo) {
    this.gadgetInfo = gadgetInfo;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    DeviceSpecPojo that = (DeviceSpecPojo) o;
    return Objects.equals(gadgetInfo, that.gadgetInfo) && Objects.equals(name, that.name);
  }

  @Override
  public int hashCode() {
    return Objects.hash(gadgetInfo, name);
  }

  public static DeviceSpecPojo fromProto(DeviceSpec proto) {
    if (proto == null) {
      return null;
    }
    DeviceSpecPojo pojo = new DeviceSpecPojo();
    pojo.setName(proto.getName());
    pojo.setGadgetInfo(GadgetInfoPojo.fromProto(proto.getInfo()));
    return pojo;
  }

  public DeviceSpec toProto() {
    DeviceSpec.Builder proto = DeviceSpec.newBuilder().setName(name);
    if (gadgetInfo != null) {
      proto.setInfo(gadgetInfo.toProto());
    }
    return proto.build();
  }
}
