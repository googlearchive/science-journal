/*
 *  Copyright 2017 Google Inc. All Rights Reserved.
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

package com.google.android.apps.forscience.whistlepunk.sensors;

import java.util.UUID;

public class BleServiceSpec {
  private final UUID serviceId;
  private final UUID valueId;
  private final UUID settingId;
  private final UUID versionId;

  public BleServiceSpec(UUID serviceId, UUID valueId, UUID settingId, UUID versionId) {
    this.serviceId = serviceId;
    this.valueId = valueId;
    this.settingId = settingId;
    this.versionId = versionId;
  }

  public UUID getServiceId() {
    return serviceId;
  }

  public UUID getValueId() {
    return valueId;
  }

  public UUID getSettingId() {
    return settingId;
  }

  public UUID getVersionId() {
    return versionId;
  }
}
