/*
 *  Copyright 2017 Google Inc. All Rights Reserved.
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
import android.content.res.Configuration;
import android.content.res.TypedArray;
import android.os.Parcel;
import android.os.Parcelable;
import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.core.os.ParcelableCompat;
import androidx.core.os.ParcelableCompatCreatorCallbacks;
import androidx.customview.view.AbsSavedState;
import androidx.core.view.MotionEventCompat;
import androidx.core.view.VelocityTrackerCompat;
import androidx.core.view.ViewCompat;
import androidx.customview.widget.ViewDragHelper;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.ViewParent;
import androidx.coordinatorlayout.widget.CoordinatorLayout;
import com.google.android.material.R;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.ref.WeakReference;

/**
 * An interaction behavior plugin for a child view of {@link CoordinatorLayout} to make it work as a
 * bottom sheet that has a mid-height stop, STATE_MIDDLE. This bottom sheet behavior also does not
 * include a STATE_HIDDEN, unlike the Android version.
 *
 * <p>Based on
 * https://android.googlesource.com/platform/frameworks/support/+/master/design/src/android/support/design/widget/BottomSheetBehavior.java
 *
 */
public class PanesBottomSheetBehavior<V extends View> extends CoordinatorLayout.Behavior<V> {
  private static final String TAG = "PanesBottomSheetBehavior";
  private View viewToKeepVisibleIfPossible;
  private boolean allowHalfHeightDrawerInLandscape = false;

  /** Callback for monitoring events about bottom sheets. */
  public abstract static class BottomSheetCallback {
    /**
     * Called when the bottom sheet changes its state.
     *
     * @param bottomSheet The bottom sheet view.
     * @param newState The new state. This will be one of {@link #STATE_DRAGGING}, {@link
     *     #STATE_SETTLING}, {@link #STATE_EXPANDED}, {@link #STATE_COLLAPSED}, or {@link
     *     #STATE_MIDDLE}.
     */
    public abstract void onStateChanged(@NonNull View bottomSheet, @State int newState);
    /**
     * Called when the bottom sheet is being dragged.
     *
     * @param bottomSheet The bottom sheet view.
     * @param slideOffset The new offset of this bottom sheet within [-1,1] range. Offset increases
     *     as this bottom sheet is moving upward. From 0 to 1 the sheet is between collapsed and
     *     expanded states and from -1 to 0 it is between hidden and collapsed states.
     */
    public abstract void onSlide(@NonNull View bottomSheet, float slideOffset);
  }
  /** The bottom sheet is dragging. */
  public static final int STATE_DRAGGING = 1;
  /** The bottom sheet is settling. */
  public static final int STATE_SETTLING = 2;
  /** The bottom sheet is expanded. */
  public static final int STATE_EXPANDED = 3;
  /** The bottom sheet is collapsed. */
  public static final int STATE_COLLAPSED = 4;
  /** The bottom sheet is in the middle. Added for PanesBottomSheetBehavior. */
  public static final int STATE_MIDDLE = 5;

  /** @hide */
  @IntDef({STATE_EXPANDED, STATE_COLLAPSED, STATE_DRAGGING, STATE_SETTLING, STATE_MIDDLE})
  @Retention(RetentionPolicy.SOURCE)
  public @interface State {}
  /**
   * Peek at the 16:9 ratio keyline of its parent.
   *
   * <p>This can be used as a parameter for {@link #setPeekHeight(int)}. {@link #getPeekHeight()}
   * will return this when the value is set.
   */
  public static final int PEEK_HEIGHT_AUTO = -1;

  private float maximumVelocity;
  private int peekHeight;
  private boolean peekHeightAuto;
  private int peekHeightMin;
  int minOffset;
  int maxOffset;
  private boolean skipCollapsed;
  @State int state = STATE_COLLAPSED;
  ViewDragHelper viewDragHelper;
  private boolean ignoreEvents;
  private int lastNestedScrollDy;
  private boolean nestedScrolled;
  int parentHeight;
  WeakReference<V> viewRef;
  WeakReference<View> nestedScrollingChildRef;
  private BottomSheetCallback callback;
  private VelocityTracker velocityTracker;
  int activePointerId;
  private int initialY;
  boolean touchingScrollingChild;
  /** Default constructor for instantiating BottomSheetBehaviors. */
  public PanesBottomSheetBehavior() {}
  /**
   * Default constructor for inflating BottomSheetBehaviors from layout.
   *
   * @param context The {@link Context}.
   * @param attrs The {@link AttributeSet}.
   */
  public PanesBottomSheetBehavior(Context context, AttributeSet attrs) {
    super(context, attrs);
    TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.BottomSheetBehavior_Layout);
    TypedValue value = a.peekValue(R.styleable.BottomSheetBehavior_Layout_behavior_peekHeight);
    if (value != null && value.data == PEEK_HEIGHT_AUTO) {
      setPeekHeight(value.data);
    } else {
      setPeekHeight(
          a.getDimensionPixelSize(
              R.styleable.BottomSheetBehavior_Layout_behavior_peekHeight, PEEK_HEIGHT_AUTO));
    }
    setSkipCollapsed(
        a.getBoolean(R.styleable.BottomSheetBehavior_Layout_behavior_skipCollapsed, false));
    a.recycle();
    ViewConfiguration configuration = ViewConfiguration.get(context);
    maximumVelocity = configuration.getScaledMaximumFlingVelocity();
  }

  public void setAllowHalfHeightDrawerInLandscape(boolean allowHalfHeightDrawerInLandscape) {
    this.allowHalfHeightDrawerInLandscape = allowHalfHeightDrawerInLandscape;
  }

  @Override
  public Parcelable onSaveInstanceState(CoordinatorLayout parent, V child) {
    return new SavedState(super.onSaveInstanceState(parent, child), state);
  }

  @Override
  public void onRestoreInstanceState(CoordinatorLayout parent, V child, Parcelable state) {
    SavedState ss = (SavedState) state;
    super.onRestoreInstanceState(parent, child, ss.getSuperState());
    // Intermediate states are restored as collapsed state
    if (ss.state == STATE_DRAGGING || ss.state == STATE_SETTLING) {
      this.state = STATE_COLLAPSED;
    } else {
      this.state = ss.state;
    }
  }

  public void setViewToKeepVisibleIfPossible(View view) {
    viewToKeepVisibleIfPossible = view;
  }

  @Override
  public boolean onLayoutChild(CoordinatorLayout parent, V child, int layoutDirection) {
    if (ViewCompat.getFitsSystemWindows(parent) && !ViewCompat.getFitsSystemWindows(child)) {
      ViewCompat.setFitsSystemWindows(child, true);
    }
    int savedTop = child.getTop();
    // First let the parent lay it out
    parent.onLayoutChild(child, layoutDirection);
    // Offset the bottom sheet
    int parentWidth = parent.getWidth();
    parentHeight = parent.getHeight();
    int peekHeight;
    if (peekHeightAuto) {
      if (peekHeightMin == 0) {
        peekHeightMin =
            parent
                .getResources()
                .getDimensionPixelSize(R.dimen.design_bottom_sheet_peek_height_min);
      }
      peekHeight = Math.max(peekHeightMin, parentHeight - parentWidth * 9 / 16);
    } else {
      peekHeight = this.peekHeight;
    }
    minOffset = Math.max(0, parentHeight - child.getHeight());
    maxOffset = Math.max(parentHeight - peekHeight, minOffset);
    if (state == STATE_EXPANDED || (state == STATE_MIDDLE && suppressMiddleDrawer(child))) {
      ViewCompat.offsetTopAndBottom(child, minOffset);
    } else if (state == STATE_COLLAPSED) {
      ViewCompat.offsetTopAndBottom(child, maxOffset);
    } else if (state == STATE_MIDDLE) {
      // Added for PanesBottomSheetBehavior.
      ViewCompat.offsetTopAndBottom(child, computeMiddleOffset(parentWidth > parentHeight));
    } else if (state == STATE_DRAGGING || state == STATE_SETTLING) {
      ViewCompat.offsetTopAndBottom(child, savedTop - child.getTop());
    }
    if (viewDragHelper == null) {
      viewDragHelper = ViewDragHelper.create(parent, dragCallback);
    }
    viewRef = new WeakReference<>(child);

    if (getScrollingChild() == null) {
      setScrollingChild(child);
    }
    return true;
  }

  private int computeMiddleOffset(boolean saveSpace) {
    int midOffset = (maxOffset + minOffset) / 2;
    if (viewToKeepVisibleIfPossible == null || !saveSpace) {
      return midOffset;
    } else {
      // We're at a space premium.  Show just enough drawer to keep the target view entirely
      // visible.  (Assumes target view is just below tool tabs)
      int height = viewToKeepVisibleIfPossible.getHeight();
      return Math.min(midOffset, Math.max(maxOffset - height, minOffset));
    }
  }

  public void setScrollingChild(View child) {
    nestedScrollingChildRef = new WeakReference<>(findScrollingChild(child));
  }

  @Override
  public boolean onInterceptTouchEvent(CoordinatorLayout parent, V child, MotionEvent event) {
    if (!child.isShown()) {
      ignoreEvents = true;
      return false;
    }
    int action = MotionEventCompat.getActionMasked(event);
    // Record the velocity
    if (action == MotionEvent.ACTION_DOWN) {
      reset();
    }
    if (velocityTracker == null) {
      velocityTracker = VelocityTracker.obtain();
    }
    velocityTracker.addMovement(event);
    switch (action) {
      case MotionEvent.ACTION_UP:
      case MotionEvent.ACTION_CANCEL:
        touchingScrollingChild = false;
        activePointerId = MotionEvent.INVALID_POINTER_ID;
        // Reset the ignore flag
        if (ignoreEvents) {
          ignoreEvents = false;
          return false;
        }
        break;
      case MotionEvent.ACTION_DOWN:
        int initialX = (int) event.getX();
        initialY = (int) event.getY();
        View scroll = getScrollingChild();
        if (scroll != null && parent.isPointInChildBounds(scroll, initialX, initialY)) {
          activePointerId = event.getPointerId(event.getActionIndex());
          touchingScrollingChild = true;
        }
        ignoreEvents =
            activePointerId == MotionEvent.INVALID_POINTER_ID
                && !parent.isPointInChildBounds(child, initialX, initialY);
        break;
    }
    if (!ignoreEvents && viewDragHelper.shouldInterceptTouchEvent(event)) {
      return true;
    }
    // We have to handle cases that the ViewDragHelper does not capture the bottom sheet because
    // it is not the top most view of its parent. This is not necessary when the touch event is
    // happening over the scrolling content as nested scrolling logic handles that case.
    View scroll = nestedScrollingChildRef.get();
    return action == MotionEvent.ACTION_MOVE
        && scroll != null
        && !ignoreEvents
        && state != STATE_DRAGGING
        && !parent.isPointInChildBounds(scroll, (int) event.getX(), (int) event.getY())
        && Math.abs(initialY - event.getY()) > viewDragHelper.getTouchSlop();
  }

  @Nullable
  public View getScrollingChild() {
    return nestedScrollingChildRef != null ? nestedScrollingChildRef.get() : null;
  }

  @Override
  public boolean onTouchEvent(CoordinatorLayout parent, V child, MotionEvent event) {
    if (!child.isShown() || viewDragHelper == null) {
      return false;
    }
    int action = MotionEventCompat.getActionMasked(event);
    if (state == STATE_DRAGGING && action == MotionEvent.ACTION_DOWN) {
      return true;
    }
    if (viewDragHelper != null) {
      viewDragHelper.processTouchEvent(event);
    }
    // Record the velocity
    if (action == MotionEvent.ACTION_DOWN) {
      reset();
    }
    if (velocityTracker == null) {
      velocityTracker = VelocityTracker.obtain();
    }
    velocityTracker.addMovement(event);
    // The ViewDragHelper tries to capture only the top-most View. We have to explicitly tell it
    // to capture the bottom sheet in case it is not captured and the touch slop is passed.
    if (action == MotionEvent.ACTION_MOVE && !ignoreEvents) {
      if (Math.abs(initialY - event.getY()) > viewDragHelper.getTouchSlop()) {
        viewDragHelper.captureChildView(child, event.getPointerId(event.getActionIndex()));
      }
    }
    return !ignoreEvents;
  }

  @Override
  public boolean onStartNestedScroll(
      CoordinatorLayout coordinatorLayout,
      V child,
      View directTargetChild,
      View target,
      int nestedScrollAxes) {
    lastNestedScrollDy = 0;
    nestedScrolled = false;
    return (nestedScrollAxes & ViewCompat.SCROLL_AXIS_VERTICAL) != 0;
  }

  @Override
  public void onNestedPreScroll(
      CoordinatorLayout coordinatorLayout, V child, View target, int dx, int dy, int[] consumed) {
    View scrollingChild = nestedScrollingChildRef.get();
    if (target != scrollingChild) {
      return;
    }
    int currentTop = child.getTop();
    int newTop = currentTop - dy;
    if (dy > 0) { // Upward
      if (newTop < minOffset) {
        consumed[1] = currentTop - minOffset;
        ViewCompat.offsetTopAndBottom(child, -consumed[1]);
        setStateInternal(STATE_EXPANDED);
      } else {
        consumed[1] = dy;
        ViewCompat.offsetTopAndBottom(child, -dy);
        setStateInternal(STATE_DRAGGING);
      }
    } else if (dy < 0) { // Downward
      if (!ViewCompat.canScrollVertically(target, -1)) {
        if (newTop <= maxOffset) {
          consumed[1] = dy;
          ViewCompat.offsetTopAndBottom(child, -dy);
          setStateInternal(STATE_DRAGGING);
        } else {
          consumed[1] = currentTop - maxOffset;
          ViewCompat.offsetTopAndBottom(child, -consumed[1]);
          setStateInternal(STATE_COLLAPSED);
        }
      }
    }
    dispatchOnSlide(child.getTop());
    lastNestedScrollDy = dy;
    nestedScrolled = true;
  }

  @Override
  public void onStopNestedScroll(CoordinatorLayout coordinatorLayout, V child, View target) {
    int top = child.getTop();
    if (top == minOffset) {
      setStateInternal(STATE_EXPANDED);
      return;
    }
    if (nestedScrollingChildRef == null
        || target != nestedScrollingChildRef.get()
        || !nestedScrolled) {
      return;
    }
    int targetState;
    if (lastNestedScrollDy > 0) {
      // Going up from a nested scroll: This can only mean we need to expand all the way,
      // since you can't have a nested scroll from the collapsed state.
      top = minOffset;
      targetState = STATE_EXPANDED;
    } else if (lastNestedScrollDy == 0) {
      // TODO: Not sure when this happens -- if I can hit this in a breakpoint then I can
      // see if it should include a middle thing.
      int currentTop = child.getTop();
      if (Math.abs(currentTop - minOffset) < Math.abs(currentTop - maxOffset)) {
        top = minOffset;
        targetState = STATE_EXPANDED;
      } else {
        top = maxOffset;
        targetState = STATE_COLLAPSED;
      }
    } else {
      // Going down from a nested scroll: May need to stop at the middle.
      // Adjusted for PanesBottomSheetBehavior.
      if (top > halfOffset() / 3) {
        top = maxOffset;
        targetState = STATE_COLLAPSED;
      } else {
        top = halfOffset();
        targetState = STATE_MIDDLE;
      }
    }
    if (viewDragHelper.smoothSlideViewTo(child, child.getLeft(), top)) {
      setStateInternal(STATE_SETTLING);
      ViewCompat.postOnAnimation(child, new SettleRunnable(child, targetState));
    } else {
      setStateInternal(targetState);
    }
    nestedScrolled = false;
  }

  @Override
  public boolean onNestedPreFling(
      CoordinatorLayout coordinatorLayout, V child, View target, float velocityX, float velocityY) {
    return target == nestedScrollingChildRef.get()
        && (state != STATE_EXPANDED
            || super.onNestedPreFling(coordinatorLayout, child, target, velocityX, velocityY));
  }
  /**
   * Sets the height of the bottom sheet when it is collapsed.
   *
   * @param peekHeight The height of the collapsed bottom sheet in pixels, or {@link
   *     #PEEK_HEIGHT_AUTO} to configure the sheet to peek automatically at 16:9 ratio keyline.
   * @attr ref com.google.android.material.R.styleable#BottomSheetBehavior_Layout_behavior_peekHeight
   */
  public final void setPeekHeight(int peekHeight) {
    boolean layout = false;
    if (peekHeight == PEEK_HEIGHT_AUTO) {
      if (!peekHeightAuto) {
        peekHeightAuto = true;
        layout = true;
      }
    } else if (peekHeightAuto || this.peekHeight != peekHeight) {
      peekHeightAuto = false;
      this.peekHeight = Math.max(0, peekHeight);
      maxOffset = parentHeight - peekHeight;
      layout = true;
    }
    if (layout && state == STATE_COLLAPSED && viewRef != null) {
      V view = viewRef.get();
      if (view != null) {
        view.requestLayout();
      }
    }
  }
  /**
   * Gets the height of the bottom sheet when it is collapsed.
   *
   * @return The height of the collapsed bottom sheet in pixels, or {@link #PEEK_HEIGHT_AUTO} if the
   *     sheet is configured to peek automatically at 16:9 ratio keyline
   * @attr ref com.google.android.material.R.styleable#BottomSheetBehavior_Layout_behavior_peekHeight
   */
  public final int getPeekHeight() {
    return peekHeightAuto ? PEEK_HEIGHT_AUTO : peekHeight;
  }
  /**
   * Sets whether this bottom sheet should skip the collapsed state when it is being hidden after it
   * is expanded once. Setting this to true has no effect unless the sheet is hideable.
   *
   * @param skipCollapsed True if the bottom sheet should skip the collapsed state.
   * @attr ref com.google.android.material.R.styleable#BottomSheetBehavior_Layout_behavior_skipCollapsed
   */
  public void setSkipCollapsed(boolean skipCollapsed) {
    this.skipCollapsed = skipCollapsed;
  }
  /**
   * Sets whether this bottom sheet should skip the collapsed state when it is being hidden after it
   * is expanded once.
   *
   * @return Whether the bottom sheet should skip the collapsed state.
   * @attr ref com.google.android.material.R.styleable#BottomSheetBehavior_Layout_behavior_skipCollapsed
   */
  public boolean getSkipCollapsed() {
    return skipCollapsed;
  }
  /**
   * Sets a callback to be notified of bottom sheet events.
   *
   * @param callback The callback to notify when bottom sheet events occur.
   */
  public void setBottomSheetCallback(BottomSheetCallback callback) {
    this.callback = callback;
  }
  /**
   * Sets the state of the bottom sheet. The bottom sheet will transition to that state with
   * animation.
   *
   * @param state One of {@link #STATE_COLLAPSED}, {@link #STATE_EXPANDED}, or {@link
   *     #STATE_MIDDLE}.
   */
  public final void setState(final /* @State */ int state) {
    if (state == this.state) {
      return;
    }
    if (viewRef == null) {
      // The view is not laid out yet; modify state and let onLayoutChild handle it later
      if (state == STATE_COLLAPSED || state == STATE_EXPANDED || state == STATE_MIDDLE) {
        this.state = state;
      }
      return;
    }
    final V child = viewRef.get();
    if (child == null) {
      return;
    }
    // Start the animation; wait until a pending layout if there is one.
    ViewParent parent = child.getParent();
    if (parent != null && parent.isLayoutRequested() && ViewCompat.isAttachedToWindow(child)) {
      child.post(
          new Runnable() {
            @Override
            public void run() {
              startSettlingAnimation(child, state);
            }
          });
    } else {
      startSettlingAnimation(child, state);
    }
  }
  /**
   * Gets the current state of the bottom sheet.
   *
   * @return One of {@link #STATE_EXPANDED}, {@link #STATE_COLLAPSED}, {@link #STATE_DRAGGING}, and
   *     {@link #STATE_SETTLING}.
   */
  @State
  public final int getState() {
    return state;
  }

  void setStateInternal(@State int state) {
    if (this.state == state) {
      return;
    }
    this.state = state;
    View bottomSheet = viewRef.get();
    if (bottomSheet != null && callback != null) {
      callback.onStateChanged(bottomSheet, state);
    }
  }

  private void reset() {
    activePointerId = ViewDragHelper.INVALID_POINTER;
    if (velocityTracker != null) {
      velocityTracker.recycle();
      velocityTracker = null;
    }
  }

  @VisibleForTesting
  View findScrollingChild(View view) {
    if (ViewCompat.isNestedScrollingEnabled(view)) {
      return view;
    }
    if (view instanceof ViewGroup) {
      ViewGroup group = (ViewGroup) view;
      for (int i = 0, count = group.getChildCount(); i < count; i++) {
        View scrollingChild = findScrollingChild(group.getChildAt(i));
        if (scrollingChild != null) {
          return scrollingChild;
        }
      }
    }
    return null;
  }

  private float getYVelocity() {
    velocityTracker.computeCurrentVelocity(1000, maximumVelocity);
    return VelocityTrackerCompat.getYVelocity(velocityTracker, activePointerId);
  }

  void startSettlingAnimation(View child, int state) {
    int top;
    if (state == STATE_COLLAPSED) {
      top = maxOffset;
    } else if (state == STATE_EXPANDED || (state == STATE_MIDDLE && suppressMiddleDrawer(child))) {
      top = minOffset;
    } else if (state == STATE_MIDDLE) {
      // Added for PanesBottomSheetBehavior.
      top = halfOffset();
    } else {
      throw new IllegalArgumentException("Illegal state argument: " + state);
    }
    setStateInternal(STATE_SETTLING);
    if (viewDragHelper.smoothSlideViewTo(child, child.getLeft(), top)) {
      ViewCompat.postOnAnimation(child, new SettleRunnable(child, state));
    }
  }

  private final ViewDragHelper.Callback dragCallback =
      new ViewDragHelper.Callback() {
        @Override
        public boolean tryCaptureView(View child, int pointerId) {
          if (state == STATE_DRAGGING) {
            return false;
          }
          if (touchingScrollingChild) {
            return false;
          }
          // TODO: Can you scroll the content while in STATE_MIDDLE? Probably not, leave as-is.
          // Need to verify behavior with Konina.
          if (state == STATE_EXPANDED && activePointerId == pointerId) {
            View scroll = nestedScrollingChildRef.get();
            if (scroll != null && ViewCompat.canScrollVertically(scroll, -1)) {
              // Let the content scroll up
              return false;
            }
          }
          return viewRef != null && viewRef.get() == child;
        }

        @Override
        public void onViewPositionChanged(View changedView, int left, int top, int dx, int dy) {
          dispatchOnSlide(top);
        }

        @Override
        public void onViewDragStateChanged(int state) {
          if (state == ViewDragHelper.STATE_DRAGGING) {
            setStateInternal(STATE_DRAGGING);
          }
        }

        @Override
        public void onViewReleased(View releasedChild, float xvel, float yvel) {
          int top;
          @State int targetState;
          int currentTop = releasedChild.getTop();
          boolean isLandscape = suppressMiddleDrawer(releasedChild);
          if (yvel < 0) { // Moving up
            // Adjusted for PanesBottomSheetBehavior to have 1/3 or less of the distance stop at
            // the middle, and >1/3 of the distance go up to expanded.
            if (isLandscape || (currentTop < halfOffset() * 4 / 3)) {
              top = minOffset;
              targetState = STATE_EXPANDED;
            } else {
              top = halfOffset();
              targetState = STATE_MIDDLE;
            }
          } else if (yvel == 0.f) { // Not moving
            // Adjusted for PanesBottomSheetBehavior to go to middle if we are closest to that,
            // instead of just expanded and collapsed.
            if (!isLandscape
                && Math.abs(currentTop - minOffset) < Math.abs(currentTop - halfOffset())) {
              top = minOffset;
              targetState = STATE_EXPANDED;
            } else if (isLandscape && Math.abs(currentTop - minOffset) < maxOffset) {
              top = minOffset;
              targetState = STATE_EXPANDED;
            } else if (Math.abs(currentTop - halfOffset()) < Math.abs(currentTop - maxOffset)) {
              top = halfOffset();
              targetState = STATE_MIDDLE;
            } else {
              top = maxOffset;
              targetState = STATE_COLLAPSED;
            }
          } else { // Moving down
            // Adjusted for PanesBottomSheetBehavior to have 1/3 or less of the distance stop at
            // the middle, and >1/3 of the distance go up to expanded.
            if (isLandscape || (currentTop > halfOffset() / 2)) {
              top = maxOffset;
              targetState = STATE_COLLAPSED;
            } else {
              top = halfOffset();
              targetState = STATE_MIDDLE;
            }
          }
          if (viewDragHelper.settleCapturedViewAt(releasedChild.getLeft(), top)) {
            setStateInternal(STATE_SETTLING);
            ViewCompat.postOnAnimation(
                releasedChild, new SettleRunnable(releasedChild, targetState));
          } else {
            setStateInternal(targetState);
          }
        }

        @Override
        public int clampViewPositionVertical(View child, int top, int dy) {
          return constrain(top, minOffset, maxOffset);
        }

        @Override
        public int clampViewPositionHorizontal(View child, int left, int dx) {
          return child.getLeft();
        }

        @Override
        public int getViewVerticalDragRange(View child) {
          return maxOffset - minOffset;
        }
        // From android.util.MathUtils
        private int constrain(int amount, int low, int high) {
          return amount < low ? low : (amount > high ? high : amount);
        }
      };

  void dispatchOnSlide(int top) {
    View bottomSheet = viewRef.get();
    if (bottomSheet != null && callback != null) {
      if (top > maxOffset) {
        callback.onSlide(bottomSheet, (float) (maxOffset - top) / (parentHeight - maxOffset));
      } else {
        callback.onSlide(bottomSheet, (float) (maxOffset - top) / ((maxOffset - minOffset)));
      }
    }
  }

  @VisibleForTesting
  int getPeekHeightMin() {
    return peekHeightMin;
  }

  private class SettleRunnable implements Runnable {
    private final View view;
    @State private final int targetState;

    SettleRunnable(View view, @State int targetState) {
      this.view = view;
      this.targetState = targetState;
    }

    @Override
    public void run() {
      if (viewDragHelper != null && viewDragHelper.continueSettling(true)) {
        ViewCompat.postOnAnimation(view, this);
      } else {
        setStateInternal(targetState);
      }
    }
  }

  protected static class SavedState extends AbsSavedState {
    @State final int state;

    public SavedState(Parcel source) {
      this(source, null);
    }

    public SavedState(Parcel source, ClassLoader loader) {
      super(source, loader);
      //noinspection ResourceType
      state = source.readInt();
    }

    public SavedState(Parcelable superState, @State int state) {
      super(superState);
      this.state = state;
    }

    @Override
    public void writeToParcel(Parcel out, int flags) {
      super.writeToParcel(out, flags);
      out.writeInt(state);
    }

    public static final Creator<SavedState> CREATOR =
        ParcelableCompat.newCreator(
            new ParcelableCompatCreatorCallbacks<SavedState>() {
              @Override
              public SavedState createFromParcel(Parcel in, ClassLoader loader) {
                return new SavedState(in, loader);
              }

              @Override
              public SavedState[] newArray(int size) {
                return new SavedState[size];
              }
            });
  }

  /** Calculates the offset for the middle stop in the PanesBottomSheetBehavior. */
  private int halfOffset() {
    return (maxOffset - minOffset) / 2;
  }

  // On some devices, landscape is too short to have a useful half-height drawer
  private boolean suppressMiddleDrawer(View view) {
    return !allowHalfHeightDrawerInLandscape
        && view.getContext().getResources().getConfiguration().orientation
            == Configuration.ORIENTATION_LANDSCAPE;
  }

  /**
   * A utility function to get the {@link PanesBottomSheetBehavior} associated with the {@code
   * view}.
   *
   * @param view The {@link View} with {@link PanesBottomSheetBehavior}.
   * @return The {@link PanesBottomSheetBehavior} associated with the {@code view}.
   */
  @SuppressWarnings("unchecked")
  public static <V extends View> PanesBottomSheetBehavior<V> from(V view) {
    ViewGroup.LayoutParams params = view.getLayoutParams();
    if (!(params instanceof CoordinatorLayout.LayoutParams)) {
      throw new IllegalArgumentException("The view is not a child of CoordinatorLayout");
    }
    CoordinatorLayout.Behavior behavior = ((CoordinatorLayout.LayoutParams) params).getBehavior();
    if (!(behavior instanceof PanesBottomSheetBehavior)) {
      throw new IllegalArgumentException("The view is not associated with BottomSheetBehavior");
    }
    return (PanesBottomSheetBehavior<V>) behavior;
  }

  public String getDrawerStateForLogging() {
    switch (state) {
      case PanesBottomSheetBehavior.STATE_COLLAPSED:
        return "COLLAPSED";
      case PanesBottomSheetBehavior.STATE_MIDDLE:
        return "MIDDLE";
      case PanesBottomSheetBehavior.STATE_EXPANDED:
        return "EXPANDED";
      case PanesBottomSheetBehavior.STATE_DRAGGING:
        return "DRAGGING";
      case PanesBottomSheetBehavior.STATE_SETTLING:
        return "SETTLING";
      default:
        return "UNKNOWN";
    }
  }
}

