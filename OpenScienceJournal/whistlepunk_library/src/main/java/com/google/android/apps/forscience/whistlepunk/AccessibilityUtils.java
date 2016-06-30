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

import android.content.Context;
import android.graphics.Rect;
import android.support.annotation.VisibleForTesting;
import android.support.design.widget.Snackbar;
import android.view.MotionEvent;
import android.view.TouchDelegate;
import android.view.View;
import android.view.accessibility.AccessibilityManager;

import java.util.ArrayList;
import java.util.List;

/**
 * Class for general-use accessibility utils.
 */
public final class AccessibilityUtils {

    // For accessibility, snackbars with actions can time out only after 90 seconds.
    public static final int SNACKBAR_TIMEOUT_EXTRA_LONG = 90 * 1000;

    private AccessibilityUtils() {

    }

    /**
     * General-purpose function to increase the TouchDelegate size up to the minimum size
     * needed for accessibility, and centered around the existing center of the view.
     * @param viewToDelegate The view whose touchable area needs to be increased by setting a
     *                       TouchDelegate on its parent with a larger rect.
     */

    public static void setTouchDelegateToMinAccessibleSize(final View viewToDelegate) {
        viewToDelegate.post(new Runnable() {
            @Override
            public void run() {
                if (viewToDelegate == null) {
                    return;
                }
                int a11ySize = viewToDelegate.getContext().getResources()
                        .getDimensionPixelSize(R.dimen.accessibility_touch_target_min_size);
                Rect rect = new Rect();
                viewToDelegate.getHitRect(rect);
                resizeRect(a11ySize, rect);
                ((View) viewToDelegate.getParent()).setTouchDelegate(new TouchDelegate(rect,
                        viewToDelegate));
            }
        });
    }

    /**
     * This is specific to SensorTabLayout because it needs to know about the number of ancestors
     * of the sensor tab, and it only increases the height (not the width).
     * This may be fragile if the SensorTabLayout is moved in the view tree.
     * @param viewsToDelegate A list of views who need TouchDelegates to increase their
     *                        clickable area in one ancestor.
     * @param ancestor The ancestor on which to set TouchDelegate for the views. This ancestor
     *                 must contain the full rect for the TouchDelegate for the views.
     */
    public static void setTouchDelegateForSensorTabs(final View viewsToDelegate[],
                                                     final View ancestor) {
        if (viewsToDelegate == null || viewsToDelegate.length == 0 || ancestor == null) {
            return;
        }
        final View parent = (View) viewsToDelegate[0].getParent();
        final View gParent = (View) parent.getParent(); // tablayout
        final View ggParent = (View) gParent.getParent(); // sensor_selection_tab_holder
        final View gggParent = (View) ggParent.getParent(); // sensor_selection_area
        ancestor.post(new Runnable() {
            @Override
            public void run() {
                if (viewsToDelegate == null || viewsToDelegate.length == 0 || ancestor == null) {
                    return;
                }
                int a11ySize = ancestor.getContext().getResources()
                        .getDimensionPixelSize(R.dimen.accessibility_touch_target_min_size);
                // Need to shift the rect into the coordinates of the rect by adding the top offset
                // of all the intermediate ancestors.
                int shift = parent.getTop() + gParent.getTop() + ggParent.getTop() +
                        gggParent.getTop();

                List<TouchDelegate> touchDelegates = new ArrayList<TouchDelegate>();
                for (int i = 0; i < viewsToDelegate.length; i++) {
                    View next = viewsToDelegate[i];
                    if (next != null) {
                        Rect rect = new Rect();
                        next.getHitRect(rect);
                        rect.top += shift;
                        rect.bottom = Math.max(rect.top + a11ySize, rect.bottom);
                        touchDelegates.add(new TouchDelegate(rect, next));
                    }
                }
                ancestor.setTouchDelegate(new TouchDelegateGroup(ancestor, touchDelegates));
            }
        });
    }

    @VisibleForTesting
    public static void resizeRect(int a11ySize, Rect rect) {
        int heightToShift = (int) Math.ceil((a11ySize - rect.height()) / 2.0);
        int widthToShift = (int) Math.ceil((a11ySize - rect.width()) / 2.0);
        if (heightToShift > 0) {
            rect.top -= heightToShift;
            rect.bottom += heightToShift;
        }
        if (widthToShift > 0) {
            rect.left -= widthToShift;
            rect.right += widthToShift;
        }
    }

    /**
     * Returns the toast duration unless accessibility touch mode is enabled, in
     * which case it returns a longer duration (90 seconds).
     */
    public static int getLongerToastDurationIfAccessibilityEnabled(Context context, Snackbar bar) {
        if (isAccessibilityManagerEnabled(context)) {
            return SNACKBAR_TIMEOUT_EXTRA_LONG;
        }
        return bar.getDuration();
    }

    /**
     * Returns true if the accessibility manager is enabled.
     */
    private static boolean isAccessibilityManagerEnabled(Context context) {
        AccessibilityManager accessibilityManager =
                ((AccessibilityManager) context.getSystemService(Context.ACCESSIBILITY_SERVICE));
        return accessibilityManager != null && accessibilityManager.isEnabled();
    }

    private static class TouchDelegateGroup extends TouchDelegate {

        private static final Rect sRect = new Rect();
        private List<TouchDelegate> mDelegateList;

        /**
         * Constructor
         *
         * @param delegateView The view that should receive motion events
         */
        public TouchDelegateGroup(View delegateView, List<TouchDelegate> touchDelegates) {
            super(sRect, delegateView);
            mDelegateList = touchDelegates;
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            // Go through the list and see if any of the delegates could claim this event.
            // Note: Assumes non-overlapping touchDelegates.
            boolean result = false;
            // Check against all the touchDelegates in the list -- this could be an
            // ACTION_MOVE or ACTION_UP that impacts a view that isn't at the (x,y) of an event.
            for (TouchDelegate touchDelegate : mDelegateList) {
                result = touchDelegate.onTouchEvent(event) || result;
            }
            return result;
        }

    }

    public static Snackbar makeSnackbar(View view, String message, int length) {
        Context context = view.getContext();
        Snackbar bar = Snackbar.make(view, message, length);
        bar.setDuration(getLongerToastDurationIfAccessibilityEnabled(context, bar));
        bar.setActionTextColor(context.getResources().getColor(R.color.snackbar_action_color));
        return bar;
    }
}
