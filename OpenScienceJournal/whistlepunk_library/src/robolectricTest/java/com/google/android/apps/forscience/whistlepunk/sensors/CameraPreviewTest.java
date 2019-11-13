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

import static org.junit.Assert.assertEquals;

import androidx.annotation.NonNull;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

@RunWith(RobolectricTestRunner.class)
public class CameraPreviewTest {
  @Test
  public void highResolutionAboveGoodRatio() {
    CameraPreview.PreviewSize bestSize =
        CameraPreview.getBestSize(0.5, false, size(2, 1), size(200, 110));
    assertEquals("200,110", bestSize.toString());
  }

  @Test
  public void sameResolutionTiebreaksWithRatioMatch() {
    CameraPreview.PreviewSize bestSize =
        CameraPreview.getBestSize(0.5, false, size(2, 1), size(200, 110), size(110, 200));
    assertEquals("200,110", bestSize.toString());
  }

  @Test
  public void flipped() {
    CameraPreview.PreviewSize bestSize =
        CameraPreview.getBestSize(0.5, true, size(1, 2), size(200, 110), size(110, 200));
    assertEquals("110,200", bestSize.toString());
  }

  @NonNull
  public CameraPreview.PreviewSize size(int width, int height) {
    return new CameraPreview.PreviewSize(width, height);
  }
}
