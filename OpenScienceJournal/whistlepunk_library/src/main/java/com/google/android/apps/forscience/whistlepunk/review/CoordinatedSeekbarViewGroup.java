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

import android.annotation.TargetApi;
import android.content.Context;
import android.content.res.Configuration;
import android.os.Build;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.widget.RelativeLayout;

/**
 * Coordinate touches between two seekbars so that they do not pass each other.
 */
public class CoordinatedSeekbarViewGroup extends RelativeLayout {
    private CropSeekBar mStartSeekBar;
    private CropSeekBar mEndSeekBar;
    private CropSeekBar mSelectedSeekbar = null;

    public CoordinatedSeekbarViewGroup(Context context) {
        super(context);
    }

    public CoordinatedSeekbarViewGroup(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public CoordinatedSeekbarViewGroup(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    public CoordinatedSeekbarViewGroup(Context context, AttributeSet attrs, int defStyleAttr,
            int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    public void setSeekbarPair(CropSeekBar startSeekBar, CropSeekBar endSeekBar) {
        mStartSeekBar = startSeekBar;
        mEndSeekBar = endSeekBar;

        mStartSeekBar.setType(CropSeekBar.TYPE_START);
        mEndSeekBar.setType(CropSeekBar.TYPE_END);
        mStartSeekBar.setOtherSeekbar(mEndSeekBar);
        mEndSeekBar.setOtherSeekbar(mStartSeekBar);

        addView(mStartSeekBar);
        addView(mEndSeekBar);
        mStartSeekBar.setProgress(0);
        mEndSeekBar.setProgress((int) GraphExploringSeekBar.SEEKBAR_MAX);
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        // Always intercept touch events!
        return true;
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        final int action = ev.getActionMasked();
        if (action == MotionEvent.ACTION_CANCEL || action == MotionEvent.ACTION_UP) {
            if (mSelectedSeekbar != null) {
                mSelectedSeekbar.onTouchEvent(ev);
                mSelectedSeekbar = null;
            }
            return true;
        }
        if (mSelectedSeekbar == null) {
            // And send them to the children as appropriate -- based on which is closer!
            // Need to make sure to pay attention to which one is active.
            int startProgress = mStartSeekBar.getProgress();
            int endProgress = mEndSeekBar.getProgress();
            int touchProgress = (int) (ev.getX() / getWidth() * GraphExploringSeekBar.SEEKBAR_MAX);
            if (Math.abs(touchProgress - startProgress) < Math.abs(touchProgress - endProgress)) {
                mSelectedSeekbar = mStartSeekBar;
            } else {
                mSelectedSeekbar = mEndSeekBar;
            }
            // Make sure the accessibility focus is updated, so that announcements are generated
            // for the correct view.
            mSelectedSeekbar.requestFocus();
        }
        mSelectedSeekbar.onTouchEvent(ev);

        return true;
    }

    public CropSeekBar getStartSeekBar() {
        return mStartSeekBar;
    }

    public CropSeekBar getEndSeekBar() {
        return mEndSeekBar;
    }

    public void setMillisecondsInRange(long millisecondsInRange) {
        mStartSeekBar.setMillisecondsInRange(millisecondsInRange);
        mEndSeekBar.setMillisecondsInRange(millisecondsInRange);
    }
}
