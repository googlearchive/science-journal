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

package com.google.android.apps.forscience.whistlepunk.sensorapi;

import android.view.View;
import com.google.android.apps.forscience.javalib.Consumer;
import com.google.common.base.Preconditions;

/**
 * Convenience class: provides access to a bundle's values, while allowing changes to the bundle to
 * be tracked.
 *
 * <p>Note that this contains a reference to a handler that may itself refer back to an activity. To
 * prevent leaks, do not hold references to an ActiveBundle longer than necessary.
 */
public class ActiveBundle {
  private final WriteableSensorOptions bundle;
  private final Consumer<ReadableSensorOptions> onOptionsChanged;
  private final OnErrorListener onEntryError;

  public interface OnErrorListener {
    void onError(String error, View relevantView);
  }

  public ActiveBundle(
      WriteableSensorOptions bundle,
      Consumer<ReadableSensorOptions> onOptionsChanged,
      OnErrorListener onEntryError) {
    this.onEntryError = onEntryError;
    this.bundle = Preconditions.checkNotNull(bundle);
    this.onOptionsChanged = Preconditions.checkNotNull(onOptionsChanged);
  }

  /**
   * @return the underlying Bundle. Note that while this does not prohibit the caller from calling
   *     modifying methods on the returned bundle, any changes made will not be broadcast to the
   *     change listener.
   */
  public ReadableSensorOptions getReadOnly() {
    return bundle.getReadOnly();
  }

  public void changeBoolean(String key, boolean value) {
    changeString(key, String.valueOf(value));
  }

  public void changeLong(String key, long value) {
    changeString(key, String.valueOf(value));
  }

  public void changeFloat(String key, float value) {
    changeString(key, String.valueOf(value));
  }

  public void changeInt(String key, int value) {
    changeString(key, String.valueOf(value));
  }

  public void changeString(String key, String value) {
    bundle.put(key, value);
    notifyListener();
  }

  public void reportError(String message, View view) {
    onEntryError.onError(message, view);
  }

  private void notifyListener() {
    onOptionsChanged.take(bundle.getReadOnly());
  }
}
