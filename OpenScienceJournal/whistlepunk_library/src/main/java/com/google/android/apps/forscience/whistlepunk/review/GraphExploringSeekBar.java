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
    private String mTimeString = "";
    private String mValueString = "";
    private String mUnits = "";
    private String mFormat;

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
}
