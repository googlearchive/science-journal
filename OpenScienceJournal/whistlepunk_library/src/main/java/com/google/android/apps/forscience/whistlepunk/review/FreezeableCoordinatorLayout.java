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
