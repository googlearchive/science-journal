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

import androidx.annotation.VisibleForTesting;

public class BleProtocolVersion {
  private final int majorVersion;
  private final int minorVersion;
  private final int patchVersion;

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
    int version = (rawVersion[0] & 0xFF) | ((rawVersion[1] << 8) & 0xFF00);

    majorVersion = (version & MAJOR_MASK) >> MAJOR_SHIFT;
    minorVersion = (version & MINOR_MASK) >> MINOR_SHIFT;
    patchVersion = (version & PATCH_MASK);
  }

  public int getMajorVersion() {
    return majorVersion;
  }

  public int getMinorVersion() {
    return minorVersion;
  }

  public int getPatchVersion() {
    return patchVersion;
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
