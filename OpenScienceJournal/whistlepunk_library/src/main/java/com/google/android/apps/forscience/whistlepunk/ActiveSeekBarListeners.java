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

import android.widget.SeekBar;
import com.google.android.apps.forscience.whistlepunk.sensorapi.ActiveBundle;

/** Seek bar listeners that update an ActiveBundle based on computed values from progress. */
public class ActiveSeekBarListeners {
  private abstract static class StubSeekBarListener implements SeekBar.OnSeekBarChangeListener {
    protected final ActiveBundle activeBundle;
    protected final String key;

    public StubSeekBarListener(ActiveBundle activeBundle, String key) {
      this.activeBundle = activeBundle;
      this.key = key;
    }

    @Override
    public void onStartTrackingTouch(SeekBar seekBar) {}

    @Override
    public void onStopTrackingTouch(SeekBar seekBar) {}
  }

  public abstract static class FloatSeekBarListener extends StubSeekBarListener {
    public FloatSeekBarListener(ActiveBundle activeBundle, String key) {
      super(activeBundle, key);
    }

    @Override
    public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
      activeBundle.changeFloat(key, computeValueFromProgress(progress, seekBar.getMax()));
    }

    protected abstract float computeValueFromProgress(int progress, int max);
  }

  public abstract static class IntSeekBarListener extends StubSeekBarListener {
    public IntSeekBarListener(ActiveBundle activeBundle, String key) {
      super(activeBundle, key);
    }

    @Override
    public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
      activeBundle.changeInt(key, computeValueFromProgress(progress, seekBar.getMax()));
    }

    protected abstract int computeValueFromProgress(int progress, int max);
  }
}
