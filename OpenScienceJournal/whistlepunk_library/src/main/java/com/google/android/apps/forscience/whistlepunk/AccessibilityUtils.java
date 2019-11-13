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
import androidx.annotation.VisibleForTesting;
import android.view.MotionEvent;
import android.view.TouchDelegate;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.accessibility.AccessibilityManager;
import com.google.android.material.snackbar.Snackbar;
import java.util.List;

/** Class for general-use accessibility utils. */
public final class AccessibilityUtils {

  // For accessibility, snackbars with actions can time out only after 90 seconds.
  public static final int SNACKBAR_TIMEOUT_EXTRA_LONG = 90 * 1000;

  private AccessibilityUtils() {}

  /**
   * General-purpose function to increase the TouchDelegate size up to the minimum size needed for
   * accessibility, and centered around the existing center of the view.
   *
   * @param viewToDelegate The view whose touchable area needs to be increased by setting a
   *     TouchDelegate on its parent with a larger rect.
   */
  public static void setTouchDelegateToMinAccessibleSize(final View viewToDelegate) {
    viewToDelegate.post(
        new Runnable() {
          @Override
          public void run() {
            if (viewToDelegate == null) {
              return;
            }
            int a11ySize =
                viewToDelegate
                    .getContext()
                    .getResources()
                    .getDimensionPixelSize(R.dimen.accessibility_touch_target_min_size);
            Rect rect = new Rect();
            viewToDelegate.getHitRect(rect);
            resizeRect(a11ySize, rect);
            ((View) viewToDelegate.getParent())
                .setTouchDelegate(new TouchDelegate(rect, viewToDelegate));
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
   * Returns the toast duration unless accessibility touch mode is enabled, in which case it returns
   * a longer duration (90 seconds).
   */
  public static @Snackbar.Duration int getLongerToastDurationIfAccessibilityEnabled(
      Context context, Snackbar bar) {
    if (isAccessibilityManagerEnabled(context)) {
      return SNACKBAR_TIMEOUT_EXTRA_LONG;
    }
    return bar.getDuration();
  }

  /** Returns true if the accessibility manager is enabled. */
  public static boolean isAccessibilityManagerEnabled(Context context) {
    AccessibilityManager accessibilityManager =
        ((AccessibilityManager) context.getSystemService(Context.ACCESSIBILITY_SERVICE));
    return accessibilityManager != null && accessibilityManager.isEnabled();
  }

  private static class TouchDelegateGroup extends TouchDelegate {

    private static final Rect rect = new Rect();
    private List<TouchDelegate> delegateList;

    /**
     * Constructor
     *
     * @param delegateView The view that should receive motion events
     */
    public TouchDelegateGroup(View delegateView, List<TouchDelegate> touchDelegates) {
      super(rect, delegateView);
      delegateList = touchDelegates;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
      // Go through the list and see if any of the delegates could claim this event.
      // Note: Assumes non-overlapping touchDelegates.
      boolean result = false;
      // Check against all the touchDelegates in the list -- this could be an
      // ACTION_MOVE or ACTION_UP that impacts a view that isn't at the (x,y) of an event.
      for (TouchDelegate touchDelegate : delegateList) {
        result = touchDelegate.onTouchEvent(event) || result;
      }
      return result;
    }
  }

  public static Snackbar makeSnackbar(View view, String message, int length) {
    Context context = view.getContext();
    Snackbar bar = Snackbar.make(view, message, length);
    bar.getView().setContentDescription(message);
    bar.setDuration(getLongerToastDurationIfAccessibilityEnabled(context, bar));
    bar.setActionTextColor(context.getResources().getColor(R.color.snackbar_action_color));
    return bar;
  }

  public static Snackbar makeSnackbar(
      View view, String message, int length, String action, OnClickListener onClickAction) {
    Context context = view.getContext();
    Snackbar bar = Snackbar.make(view, message, length);
    bar.getView().setContentDescription(message);
    bar.setDuration(getLongerToastDurationIfAccessibilityEnabled(context, bar));
    bar.setActionTextColor(context.getResources().getColor(R.color.snackbar_action_color));
    bar.setAction(action, onClickAction);
    return bar;
  }
}
