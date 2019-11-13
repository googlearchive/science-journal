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

import com.google.android.apps.forscience.whistlepunk.data.GoosciGadgetInfo.GadgetInfo;
import com.google.android.apps.forscience.whistlepunk.data.GoosciGadgetInfo.GadgetInfo.Platform;
import java.util.Objects;

/** Information about a given device.. */
public class GadgetInfoPojo {

  private Platform platform = Platform.ANDROID;
  private String providerId = "";
  private String address = "";
  private String hostId = "";
  private String hostDescription = "";

  public Platform getPlatform() {
    return platform;
  }

  public void setPlatform(Platform platform) {
    this.platform = platform;
  }

  public String getProviderId() {
    return providerId;
  }

  public void setProviderId(String providerId) {
    this.providerId = providerId;
  }

  public String getAddress() {
    return address;
  }

  public void setAddress(String address) {
    this.address = address;
  }

  public String getHostId() {
    return hostId;
  }

  public void setHostId(String hostId) {
    this.hostId = hostId;
  }

  public String getHostDescription() {
    return hostDescription;
  }

  public void setHostDescription(String hostDescription) {
    this.hostDescription = hostDescription;
  }

  public static GadgetInfoPojo fromProto(GadgetInfo proto) {
    if (proto == null) {
      return null;
    }
    GadgetInfoPojo pojo = new GadgetInfoPojo();
    pojo.setAddress(proto.getAddress());
    pojo.setHostDescription(proto.getHostDescription());
    pojo.setHostId(proto.getHostId());
    pojo.setPlatform(proto.getPlatform());
    pojo.setProviderId(proto.getProviderId());
    return pojo;
  }

  public GadgetInfo toProto() {
    return GadgetInfo.newBuilder()
        .setAddress(address)
        .setHostDescription(hostDescription)
        .setPlatform(platform)
        .setHostId(hostId)
        .setProviderId(providerId)
        .build();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    GadgetInfoPojo that = (GadgetInfoPojo) o;
    return platform == that.platform
        && Objects.equals(providerId, that.providerId)
        && Objects.equals(address, that.address)
        && Objects.equals(hostId, that.hostId)
        && Objects.equals(hostDescription, that.hostDescription);
  }

  @Override
  public int hashCode() {
    return Objects.hash(platform, providerId, address, hostId, hostDescription);
  }
}
