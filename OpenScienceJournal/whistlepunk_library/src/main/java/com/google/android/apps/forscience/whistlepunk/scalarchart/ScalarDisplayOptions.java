/*
 *  Copyright 2016 Google Inc. All Rights Reserved.
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

package com.google.android.apps.forscience.whistlepunk.scalarchart;

import android.os.Bundle;
import androidx.annotation.IntDef;
import androidx.annotation.VisibleForTesting;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

/** Tracks the options needed for a Line Graph Presenter. */
public class ScalarDisplayOptions {
  // TODO: rename to ScalarDisplayOptions (in separate CL)
  public static final String PREFS_KEY_SMOOTHNESS = "prefs_smoothness";
  public static final String PREFS_KEY_WINDOW = "prefs_window";
  public static final String PREFS_KEY_BLUR_TYPE = "prefs_blurType";
  public static final String PREFS_KEY_GAUSSIAN_SIGMA = "prefs_sigma";
  public static final String PREFS_KEY_SONIFICATION_TYPE = "prefs_sonification_type";

  public Bundle asBundle() {
    final Bundle bundle = new Bundle();
    bundle.putFloat(PREFS_KEY_GAUSSIAN_SIGMA, gaussianSigma);
    bundle.putFloat(PREFS_KEY_SMOOTHNESS, smoothness);
    bundle.putInt(PREFS_KEY_BLUR_TYPE, blurType);
    bundle.putInt(PREFS_KEY_WINDOW, window);
    return bundle;
  }

  public interface ScalarDisplayOptionsListener {
    void onLineOptionsChanged(float smoothness, int window, @BlurType int blurType, float sigma);

    void onAudioPreferencesChanged(String newValue);
  }

  // The smoothness of the cubic curve. Values from 0.0 to 0.5 are acceptable.
  @VisibleForTesting public static final float SMOOTHNESS_MIN = 0.0f;
  @VisibleForTesting public static final float SMOOTHNESS_MAX = 0.5f;
  static final float DEFAULT_SMOOTHNESS = SMOOTHNESS_MAX;

  // The number of points to use when bluring the data for the chart.
  static final int WINDOW_MIN = 1;
  static final int WINDOW_MAX = 20;
  static final int DEFAULT_WINDOW = WINDOW_MIN;

  @VisibleForTesting public static final float GAUSSIAN_SIGMA_MIN = 1.0f;
  @VisibleForTesting public static final float GAUSSIAN_SIGMA_MAX = 10.0f;
  static final float DEFAULT_GAUSSIAN_SIGMA = 5.0f;

  // The type of blur to do for the chart. Currently can be one of a gaussian blur or
  // a simple average across the whole window.
  @IntDef({BLUR_TYPE_GAUSSIAN, BLUR_TYPE_AVERAGE})
  @Retention(RetentionPolicy.SOURCE)
  public @interface BlurType {}

  static final int BLUR_TYPE_GAUSSIAN = 0;
  static final int BLUR_TYPE_AVERAGE = 1;

  static final int DEFAULT_BLUR_TYPE = BLUR_TYPE_GAUSSIAN;

  private float smoothness = DEFAULT_SMOOTHNESS;
  private int window = DEFAULT_WINDOW;
  private @BlurType int blurType = DEFAULT_BLUR_TYPE;
  private float gaussianSigma = DEFAULT_GAUSSIAN_SIGMA;

  Set<ScalarDisplayOptionsListener> listeners = Collections.newSetFromMap(new WeakHashMap());

  public void updateLineSettings(
      float smoothness, int window, @BlurType int blurType, float sigma) {
    this.smoothness = smoothness;
    this.window = window;
    this.blurType = blurType;
    gaussianSigma = sigma;
    notifyLineGraphPresenters();
  }

  /**
   * Retains a weak reference to the provided listener. Caller must make sure that it holds a strong
   * reference to the listener, or else messages may not be delivered.
   */
  public void weaklyRegisterListener(ScalarDisplayOptionsListener listener) {
    listeners.add(listener);
  }

  private void notifyLineGraphPresenters() {
    for (ScalarDisplayOptionsListener listener : listeners) {
      listener.onLineOptionsChanged(smoothness, window, blurType, gaussianSigma);
    }
  }

  public float getSmoothness() {
    return smoothness;
  }

  public int getWindow() {
    return window;
  }

  public @BlurType int getBlurType() {
    return blurType;
  }

  public float getGaussianSigma() {
    return gaussianSigma;
  }
}
