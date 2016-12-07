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

    private double mMillisPerTick;
    private int mType;
    private CropSeekBar mOtherSeekbar;
    private List<OnSeekBarChangeListener> mSeekBarChangeListeners = new ArrayList<>();
    private Drawable mThumb;

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
        setOnSeekBarChangeListener(new OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                for (OnSeekBarChangeListener listener : mSeekBarChangeListeners) {
                    listener.onProgressChanged(seekBar, progress, fromUser);
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                for (OnSeekBarChangeListener listener : mSeekBarChangeListeners) {
                    listener.onStartTrackingTouch(seekBar);
                }
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                for (OnSeekBarChangeListener listener : mSeekBarChangeListeners) {
                    listener.onStartTrackingTouch(seekBar);
                }
            }
        });
    }

    public int getType() {
        return mType;
    }

    public void setType(int type) {
        mType = type;
        Resources res = getContext().getResources();
        setThumbOffset(res.getDimensionPixelSize(R.dimen.crop_thumb_offset));
        if (mType == TYPE_START) {
            mThumb = res.getDrawable(R.drawable.crop_thumb_start);
            setFormat(res.getString(R.string.crop_start_seekbar_content_description));
        } else {
            mThumb = res.getDrawable(R.drawable.crop_thumb_end);
            setFormat(res.getString(R.string.crop_end_seekbar_content_description));
        }
        int color = res.getColor(R.color.color_accent);
        mThumb.setColorFilter(color, PorterDuff.Mode.SRC_IN);
        setThumb(mThumb);
    }

    public void setOtherSeekbar(CropSeekBar other) {
        mOtherSeekbar = other;
        addOnSeekBarChangeListener(new OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                int bufferTicks = (int) Math.ceil(CropHelper.MINIMUM_CROP_MILLIS / mMillisPerTick);
                if (mType == TYPE_START) {
                    if (progress > mOtherSeekbar.getFullProgress() - bufferTicks) {
                        ((CropSeekBar) seekBar).setFullProgress(
                                mOtherSeekbar.getFullProgress() - bufferTicks);
                        return;
                    }
                } else {
                    if (progress < mOtherSeekbar.getFullProgress() + bufferTicks) {
                        ((CropSeekBar) seekBar).setFullProgress(
                                mOtherSeekbar.getFullProgress() + bufferTicks);
                        return;
                    }
                }
                ((CropSeekBar) seekBar).updateFullProgress(progress);
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {

            }
        });
    }

    // Allows us to have multiple change listeners.
    public void addOnSeekBarChangeListener(OnSeekBarChangeListener onSeekBarChangeListener) {
        mSeekBarChangeListeners.add(onSeekBarChangeListener);
    }

    public void setMillisecondsInRange(long millisecondsInRange) {
        mMillisPerTick = millisecondsInRange / GraphExploringSeekBar.SEEKBAR_MAX;
    }

    public void hideThumb() {
        setThumb(null);
    }

    public void showThumb() {
        setThumb(mThumb);
    }
}
