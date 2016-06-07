package com.google.android.apps.forscience.whistlepunk;

import android.annotation.TargetApi;
import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.widget.RelativeLayout;

/**
 * SensorCardHeader gets Touch Events and calls an OnTouchListener function.
 * It does not steal touch events from its children.
 */
public class SensorCardHeader extends RelativeLayout {

    public interface onHeaderTouchListener {
        void onTouch();
    }

    private onHeaderTouchListener mOnTouchListener;

    public SensorCardHeader(Context context) {
        super(context);
    }

    public SensorCardHeader(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public SensorCardHeader(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @TargetApi(21)
    public SensorCardHeader(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    public void setOnHeaderTouchListener(onHeaderTouchListener listener) {
        mOnTouchListener = listener;
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        if (mOnTouchListener != null) {
            mOnTouchListener.onTouch();
        }
        return false;
    }
}
