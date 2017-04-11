package com.google.android.apps.forscience.whistlepunk.sensors;

import com.google.common.annotations.VisibleForTesting;

public class BleProtocolVersion {
    private final int mMajorVersion;
    private final int mMinorVersion;
    private final int mPatchVersion;

    private static final int MAJOR_BITS = 5;
    private static final int MINOR_BITS = 5;
    private static final int PATCH_BITS = 6;

    private static final int MAJOR_MAX = (1 << MAJOR_BITS) - 1;
    private static final int MINOR_MAX = (1 << MINOR_BITS) - 1;
    private static final int PATCH_MAX = (1 << PATCH_BITS) - 1;

    private static final int MAJOR_SHIFT = PATCH_BITS + MINOR_BITS;
    private static final int MINOR_SHIFT = PATCH_BITS;

    private static final int MAJOR_MASK = MAJOR_MAX << MAJOR_SHIFT;
    private static final int MINOR_MASK = MINOR_MAX << MINOR_SHIFT;
    private static final int PATCH_MASK = PATCH_MAX;

    public BleProtocolVersion(byte[] rawVersion) {
        int version = (rawVersion[0] & 0xFF) | ((rawVersion[1]<<8) & 0xFF00);

        mMajorVersion = (version & MAJOR_MASK) >> MAJOR_SHIFT;
        mMinorVersion = (version & MINOR_MASK) >> MINOR_SHIFT;
        mPatchVersion = (version & PATCH_MASK);

    }

    public int getMajorVersion() {
        return mMajorVersion;
    }

    public int getMinorVersion() {
        return mMinorVersion;
    }

    public int getPatchVersion() {
        return mPatchVersion;
    }

    @VisibleForTesting
    public int getMaxMajorVersion() {
        return MAJOR_MAX;
    }

    @VisibleForTesting
    public int getMaxMinorVersion() {
        return MINOR_MAX;
    }

    @VisibleForTesting
    public int getMaxPatchVersion() {
        return PATCH_MAX;
    }
}
