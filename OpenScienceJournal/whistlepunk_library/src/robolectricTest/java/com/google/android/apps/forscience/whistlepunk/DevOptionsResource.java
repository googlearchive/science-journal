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
package com.google.android.apps.forscience.whistlepunk;

import android.content.SharedPreferences;
import androidx.annotation.NonNull;
import org.junit.rules.ExternalResource;
import org.robolectric.RuntimeEnvironment;

public class DevOptionsResource extends ExternalResource {
  private boolean rememberedPref;

  @Override
  public void before() throws Exception {
    rememberedPref = getPrefs().getBoolean(getRememberedPrefKey(), false);
  }

  @NonNull
  protected String getRememberedPrefKey() {
    return DevOptionsFragment.KEY_SINE_WAVE_SENSOR;
  }

  @Override
  public void after() {
    setPrefValue(rememberedPref);
  }

  protected void setPrefValue(boolean value) {
    getPrefs().edit().putBoolean(getRememberedPrefKey(), value).apply();
  }

  private SharedPreferences getPrefs() {
    return DevOptionsFragment.getPrefs(RuntimeEnvironment.application.getApplicationContext());
  }
}
