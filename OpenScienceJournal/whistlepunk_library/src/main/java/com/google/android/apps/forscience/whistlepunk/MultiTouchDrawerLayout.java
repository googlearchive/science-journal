package com.google.android.apps.forscience.whistlepunk;

import android.content.Context;
import android.support.v4.widget.DrawerLayout;
import android.util.AttributeSet;
import android.view.MotionEvent;

/**
 * This is a workaround to a multi-touch bug in DrawerLayout as described in
 * https://code.google.com/p/android/issues/detail?id=60464.
 * This is taken nearly verbatum from a solution in that thread,
 * https://code.google.com/p/android/issues/detail?id=60464#c5.
 */
public class MultiTouchDrawerLayout extends DrawerLayout {
    public MultiTouchDrawerLayout(Context context) {
        super(context);
    }

    public MultiTouchDrawerLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public MultiTouchDrawerLayout(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    private boolean mIsDisallowIntercept = false;

    @Override
    public void requestDisallowInterceptTouchEvent(boolean disallowIntercept) {
        // keep the info about if the innerViews do requestDisallowInterceptTouchEvent
        mIsDisallowIntercept = disallowIntercept;
        super.requestDisallowInterceptTouchEvent(disallowIntercept);
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        // the incorrect array size will only happen in the multi-touch scenario.
        if (ev.getPointerCount() > 1 && mIsDisallowIntercept) {
            requestDisallowInterceptTouchEvent(false);
            boolean handled = super.dispatchTouchEvent(ev);
            requestDisallowInterceptTouchEvent(true);
            return handled;
        } else {
            return super.dispatchTouchEvent(ev);
        }
    }
}
