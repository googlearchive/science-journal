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
package com.google.android.apps.forscience.whistlepunk.devicemanager;

import androidx.collection.ArraySet;
import android.util.ArrayMap;
import com.google.android.apps.forscience.javalib.Consumer;
import java.util.Map;
import java.util.Set;

public class EnablementController {
  private Set<String> disabledCheckboxes = new ArraySet<>();
  private Set<String> checkedBoxes = new ArraySet<>();
  private Map<String, Consumer<Boolean>> enablementListeners = new ArrayMap<>();

  public void addEnablementListener(String sensorKey, Consumer<Boolean> listener) {
    enablementListeners.put(sensorKey, listener);
    listener.take(isEnabled(sensorKey));
  }

  public void clearEnablementListener(String sensorKey) {
    enablementListeners.remove(sensorKey);
  }

  public void setChecked(String sensorKey, boolean isChecked) {
    changeInclusion(checkedBoxes, sensorKey, isChecked);
    if (checkedBoxes.size() == 1) {
      setEnabled(checkedBoxes.iterator().next(), false);
    } else {
      for (String key : checkedBoxes) {
        setEnabled(key, true);
      }
    }

    // Forgetting a device can cause the last checked checkbox to become unchecked, even
    // though it is disabled.  Unchecked boxes should always be enabled.
    if (!isChecked) {
      setEnabled(sensorKey, true);
    }
  }

  private void setEnabled(String sensorKey, boolean isEnabled) {
    changeInclusion(disabledCheckboxes, sensorKey, !isEnabled);
    Consumer<Boolean> listener = enablementListeners.get(sensorKey);
    if (listener != null) {
      listener.take(isEnabled);
    }
  }

  private void changeInclusion(Set<String> set, String key, boolean isIncluded) {
    if (isIncluded) {
      set.add(key);
    } else {
      set.remove(key);
    }
  }

  private boolean isEnabled(String sensorKey) {
    return !disabledCheckboxes.contains(sensorKey);
  }

  public void onDestroy() {
    enablementListeners.clear();
  }
}
