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
    private final UUID mServiceId;
    private final UUID mValueId;
    private final UUID mSettingId;
    private final UUID mVersionId;

    public BleServiceSpec(UUID serviceId, UUID valueId, UUID settingId, UUID versionId) {
        mServiceId = serviceId;
        mValueId = valueId;
        mSettingId = settingId;
        mVersionId = versionId;
    }

    public UUID getServiceId() {
        return mServiceId;
    }

    public UUID getValueId() {
        return mValueId;
    }

    public UUID getSettingId() {
        return mSettingId;
    }

    public UUID getVersionId() {
        return mVersionId;
    }
}
