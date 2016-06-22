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
import android.support.design.widget.CoordinatorLayout;
import android.util.AttributeSet;
import android.view.MotionEvent;


/**
 * A coordinatorlayout that can be frozen (not allowed to scroll).
 */
public class FreezeableCoordinatorLayout extends CoordinatorLayout {
    public boolean mIsFrozen = false;

    public FreezeableCoordinatorLayout(Context context) {
        super(context);
    }

    public FreezeableCoordinatorLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public FreezeableCoordinatorLayout(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public void setFrozen(boolean frozen) {
        mIsFrozen = frozen;
    }

    public boolean isFrozen() {
        return mIsFrozen;
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        // TODO: Instead of overwriting onTouchEvent here, try using a special behavior or
        // overriding the scroll event in AppBarLayout.
        if (mIsFrozen) {
            return true;
        }
        return super.onTouchEvent(ev);
    }
}
