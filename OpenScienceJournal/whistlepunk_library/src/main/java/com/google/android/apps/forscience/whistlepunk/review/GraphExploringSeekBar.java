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
import android.support.v4.view.AccessibilityDelegateCompat;
import android.support.v4.view.ViewCompat;
import android.support.v4.view.accessibility.AccessibilityNodeInfoCompat;
import android.support.v7.widget.AppCompatSeekBar;
import android.util.AttributeSet;
import android.view.View;
import android.view.accessibility.AccessibilityEvent;

import com.google.android.apps.forscience.whistlepunk.R;

/**
 * A SeekBar which is used to explore a graph of data, and can announce the current
 * (time, value) pairs for accessibility.
 */
public class GraphExploringSeekBar extends AppCompatSeekBar {
    private static final String TAG = "GraphExploringSeekBar";

    // The maximum range of the progress bar for a graph seekbar. Smaller values will have less
    // resolution, but very large ranges may have worse performance due to more calculations being
    // needed. This is applied to the seekbar using the setMax function.
    public static final double SEEKBAR_MAX = 500.0;

    private String mTimeString = "";
    private String mValueString = "";
    private String mUnits = "";
    private String mFormat;

    // This is like mProgress, but can be less than 0 or greater than the max.
    // This makes it easier to handle times when we want to represent data that is
    // outside the edge of the graph -- and therefore outside of the seekbar range.
    private int mFullProgress;

    public GraphExploringSeekBar(Context context) {
        super(context);
        init();
    }

    public GraphExploringSeekBar(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public GraphExploringSeekBar(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        // Use an AccessibilityDelegate to add custom text to Accessibility events.
        ViewCompat.setAccessibilityDelegate(this, new AccessibilityDelegateCompat() {
            @Override
            public void onPopulateAccessibilityEvent(View host, AccessibilityEvent event) {
                super.onPopulateAccessibilityEvent(host, event);
                event.getText().clear();
                event.getText().add(generateEventText());
            }

            @Override
            public void onInitializeAccessibilityNodeInfo(View host,
                    AccessibilityNodeInfoCompat info) {
                super.onInitializeAccessibilityNodeInfo(host, info);
                info.setText(generateEventText());
            }
        });

        Resources res = getContext().getResources();
        mFormat = res.getString(R.string.graph_exploring_seekbar_content_description);

        // Always use LTR layout, since graphs are always LTR.
        setLayoutDirection(LAYOUT_DIRECTION_LTR);

        setMax((int) SEEKBAR_MAX);
    }

    public void updateValuesForAccessibility(String time, String value) {
        mTimeString = time;
        mValueString = value;
    }

    public void setUnits(String units) {
        mUnits = units;
    }

    private String generateEventText() {
        return String.format(mFormat, mTimeString, mValueString, mUnits);
    }

    protected void setFormat(String format) {
        mFormat = format;
    }

    // Gets the full progress, which may be more than the max and less than 0.
    public int getFullProgress() {
        return mFullProgress;
    }

    // This should be used instead of setProgress whenever anything wants to change this
    // seekbar's progress.
    public void setFullProgress(int progress) {
        mFullProgress = progress;
        setProgress(mFullProgress);
    }

    // Updates the full progress without calling the setProgress function unless it is necessary.
    public void updateFullProgress(int progress) {
        mFullProgress = progress;
        if (getProgress() == 0 && progress != 0) {
            setProgress(progress);
        }
    }
}
