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

package com.google.android.apps.forscience.whistlepunk.review;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.widget.SeekBar;
import com.google.android.apps.forscience.whistlepunk.R;
import com.google.android.apps.forscience.whistlepunk.metadata.CropHelper;
import java.util.ArrayList;
import java.util.List;

public class CropSeekBar extends GraphExploringSeekBar {

  public static final int TYPE_START = 1;
  public static final int TYPE_END = 2;

  private double millisPerTick;
  private int type;
  private CropSeekBar otherSeekbar;
  private List<OnSeekBarChangeListener> seekBarChangeListeners = new ArrayList<>();
  private Drawable thumb;

  public CropSeekBar(Context context) {
    super(context);
    init();
  }

  public CropSeekBar(Context context, AttributeSet attrs) {
    super(context, attrs);
    init();
  }

  public CropSeekBar(Context context, AttributeSet attrs, int defStyleAttr) {
    super(context, attrs, defStyleAttr);
    init();
  }

  private void init() {
    setOnSeekBarChangeListener(
        new OnSeekBarChangeListener() {
          @Override
          public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
            for (OnSeekBarChangeListener listener : seekBarChangeListeners) {
              listener.onProgressChanged(seekBar, progress, fromUser);
            }
          }

          @Override
          public void onStartTrackingTouch(SeekBar seekBar) {
            for (OnSeekBarChangeListener listener : seekBarChangeListeners) {
              listener.onStartTrackingTouch(seekBar);
            }
          }

          @Override
          public void onStopTrackingTouch(SeekBar seekBar) {
            for (OnSeekBarChangeListener listener : seekBarChangeListeners) {
              listener.onStartTrackingTouch(seekBar);
            }
          }
        });
  }

  public int getType() {
    return type;
  }

  public void setType(int type) {
    this.type = type;
    Resources res = getContext().getResources();
    setThumbOffset(res.getDimensionPixelSize(R.dimen.crop_thumb_offset));
    if (this.type == TYPE_START) {
      thumb = res.getDrawable(R.drawable.crop_thumb_start);
      setFormat(res.getString(R.string.crop_start_seekbar_content_description));
    } else {
      thumb = res.getDrawable(R.drawable.crop_thumb_end);
      setFormat(res.getString(R.string.crop_end_seekbar_content_description));
    }
    int color = res.getColor(R.color.color_accent);
    thumb.setColorFilter(color, PorterDuff.Mode.SRC_IN);
    setThumb(thumb);
  }

  public void setOtherSeekbar(CropSeekBar other) {
    otherSeekbar = other;
    addOnSeekBarChangeListener(
        new OnSeekBarChangeListener() {
          @Override
          public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
            int bufferTicks = (int) Math.ceil(CropHelper.MINIMUM_CROP_MILLIS / millisPerTick);
            if (type == TYPE_START) {
              if (progress > otherSeekbar.getFullProgress() - bufferTicks) {
                ((CropSeekBar) seekBar)
                    .setFullProgress(otherSeekbar.getFullProgress() - bufferTicks);
                return;
              }
            } else {
              if (progress < otherSeekbar.getFullProgress() + bufferTicks) {
                ((CropSeekBar) seekBar)
                    .setFullProgress(otherSeekbar.getFullProgress() + bufferTicks);
                return;
              }
            }
            ((CropSeekBar) seekBar).updateFullProgress(progress);
          }

          @Override
          public void onStartTrackingTouch(SeekBar seekBar) {}

          @Override
          public void onStopTrackingTouch(SeekBar seekBar) {}
        });
  }

  // Allows us to have multiple change listeners.
  public void addOnSeekBarChangeListener(OnSeekBarChangeListener onSeekBarChangeListener) {
    seekBarChangeListeners.add(onSeekBarChangeListener);
  }

  public void setMillisecondsInRange(long millisecondsInRange) {
    millisPerTick = millisecondsInRange / GraphExploringSeekBar.SEEKBAR_MAX;
  }

  public void hideThumb() {
    setThumb(null);
  }

  public void showThumb() {
    setThumb(thumb);
  }
}
