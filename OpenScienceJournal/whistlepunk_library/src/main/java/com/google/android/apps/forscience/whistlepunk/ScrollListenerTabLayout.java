package com.google.android.apps.forscience.whistlepunk;

import android.content.Context;
import android.support.design.widget.TabLayout;
import android.util.AttributeSet;

/**
 * A TabLayout with a scroll listener.
 */
public class ScrollListenerTabLayout extends TabLayout {

    public interface TabLayoutScrollListener {
        public void onScrollChanged(int l, int t, int oldl, int oldt);
    }

    private TabLayoutScrollListener mScrollListener;

    public ScrollListenerTabLayout(Context context) {
        super(context);
    }

    public ScrollListenerTabLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public ScrollListenerTabLayout(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public void setScrollListener(TabLayoutScrollListener listener) {
        mScrollListener = listener;
    }

    @Override
    protected void onScrollChanged(int l, int t, int oldl, int oldt) {
        super.onScrollChanged(l, t, oldl, oldt);
        if (mScrollListener != null) {
            mScrollListener.onScrollChanged(l, t, oldl, oldt);
        }
    }
}
