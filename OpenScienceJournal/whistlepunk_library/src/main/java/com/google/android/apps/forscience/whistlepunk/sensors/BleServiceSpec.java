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
