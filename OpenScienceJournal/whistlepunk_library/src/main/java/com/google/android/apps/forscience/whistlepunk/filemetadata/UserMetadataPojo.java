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
import com.google.android.apps.forscience.whistlepunk.metadata.GoosciUserMetadata.ExperimentOverview;
import com.google.android.apps.forscience.whistlepunk.metadata.GoosciUserMetadata.UserMetadata;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Information about file versions and devices used to read and write Science Journal experiments.
 */
public class UserMetadataPojo {

  private int majorVersion;
  private int minorVersion;
  private Map<String, ExperimentOverviewPojo> experiments = new HashMap<>();
  private Set<DeviceSpecPojo> myDevices = new HashSet<>();
  private FileVersionPojo fileVersion = new FileVersionPojo();

  public long getVersion() {
    return majorVersion;
  }

  public void setVersion(int majorVersion) {
    this.majorVersion = majorVersion;
  }

  public long getMinorVersion() {
    return minorVersion;
  }

  public void setMinorVersion(int minorVersion) {
    this.minorVersion = minorVersion;
  }

  public FileVersionPojo getFileVersion() {
    return fileVersion;
  }

  public void setFileVersion(FileVersionPojo fileVersion) {
    this.fileVersion = fileVersion;
  }

  public void insertOverview(ExperimentOverviewPojo overview) {
    experiments.put(overview.getExperimentId(), overview);
  }

  public void deleteOverview(String experimentId) {
    experiments.remove(experimentId);
  }

  public ExperimentOverviewPojo getOverview(String experimentId) {
    return experiments.get(experimentId);
  }

  public List<ExperimentOverviewPojo> getOverviews(boolean includeArchived) {
    if (includeArchived) {
      return new ArrayList<ExperimentOverviewPojo>(experiments.values());
    } else {
      List<ExperimentOverviewPojo> result = new ArrayList<>();

      for (ExperimentOverviewPojo overview : experiments.values()) {
        if (!overview.isArchived()) {
          result.add(overview);
        }
      }
      return result;
    }
  }

  public void clearOverviews() {
    experiments.clear();
  }

  public void addDevice(DeviceSpecPojo device) {
    myDevices.add(device);
  }

  public void removeDevice(DeviceSpecPojo device) {
    myDevices.remove(device);
  }

  public List<DeviceSpecPojo> getMyDevices() {
    return new ArrayList<DeviceSpecPojo>(myDevices);
  }

  public UserMetadata toProto() {
    UserMetadata.Builder proto = UserMetadata.newBuilder();
    if (fileVersion != null) {
      proto.setFileVersion(fileVersion.toProto());
    }
    proto.setVersion(majorVersion).setMinorVersion(minorVersion);

    ArrayList<DeviceSpec> deviceProtos = new ArrayList<>();
    for (DeviceSpecPojo pojo : myDevices) {
      deviceProtos.add(pojo.toProto());
    }
    proto.addAllMyDevices(deviceProtos);

    List<ExperimentOverview> overviewProtos = new ArrayList<>();
    for (ExperimentOverviewPojo pojo : experiments.values()) {
      overviewProtos.add(pojo.toProto());
    }
    return proto.addAllExperiments(overviewProtos).build();
  }

  public static UserMetadataPojo fromProto(UserMetadata proto) {
    if (proto == null) {
      return null;
    }
    UserMetadataPojo pojo = new UserMetadataPojo();
    pojo.setVersion(proto.getVersion());
    pojo.setMinorVersion(proto.getMinorVersion());

    for (ExperimentOverview e : proto.getExperimentsList()) {
      ExperimentOverviewPojo experimentPojo = ExperimentOverviewPojo.fromProto(e);
      pojo.insertOverview(experimentPojo);
    }

    for (DeviceSpec d : proto.getMyDevicesList()) {
      DeviceSpecPojo devicePojo = DeviceSpecPojo.fromProto(d);
      pojo.addDevice(devicePojo);
    }

    pojo.setFileVersion(FileVersionPojo.fromProto(proto.getFileVersion()));

    return pojo;
  }
}
