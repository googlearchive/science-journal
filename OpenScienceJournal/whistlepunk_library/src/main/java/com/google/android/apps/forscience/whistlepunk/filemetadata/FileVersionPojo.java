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

import com.google.android.apps.forscience.whistlepunk.data.GoosciGadgetInfo.GadgetInfo.Platform;
import com.google.android.apps.forscience.whistlepunk.metadata.Version.FileVersion;

/** A file version, with major and minor versions, and a device platform. */
public class FileVersionPojo {
  private int version;
  private int minorVersion;
  private int platformVersion;
  private Platform platform = Platform.ANDROID;

  public int getVersion() {
    return version;
  }

  public void setVersion(int version) {
    this.version = version;
  }

  public int getMinorVersion() {
    return minorVersion;
  }

  public void setMinorVersion(int minorVersion) {
    this.minorVersion = minorVersion;
  }

  public int getPlatformVersion() {
    return platformVersion;
  }

  public void setPlatformVersion(int platformVersion) {
    this.platformVersion = platformVersion;
  }

  public Platform getPlatform() {
    return platform;
  }

  public void setPlatform(Platform platform) {
    this.platform = platform;
  }

  public FileVersion toProto() {
    FileVersion.Builder proto =
        FileVersion.newBuilder()
            .setVersion(version)
            .setMinorVersion(minorVersion)
            .setPlatformVersion(platformVersion);
    if (platform != null) {
      proto.setPlatform(platform);
    }
    return proto.build();
  }

  public static FileVersionPojo fromProto(FileVersion proto) {
    if (proto == null) {
      return null;
    }
    FileVersionPojo pojo = new FileVersionPojo();
    pojo.setVersion(proto.getVersion());
    pojo.setMinorVersion(proto.getMinorVersion());
    pojo.setPlatform(proto.getPlatform());
    pojo.setPlatformVersion(proto.getPlatformVersion());

    return pojo;
  }
}
