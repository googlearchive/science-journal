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

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.support.v7.widget.AppCompatTextView;
import android.text.format.DateUtils;
import android.util.AttributeSet;
import android.view.View;

/**
 * TextView which takes a relative time parameter and updates itself every minute.
 *
 * To use, call {@link #setTime(long)}. If no time is set or the time is set to {@link #NOT_SET},
 * then this functions like a regular TextView.
 */
public class RelativeTimeTextView extends AppCompatTextView implements View.OnAttachStateChangeListener {

    public static final long NOT_SET = -1;

    private long mTimeMs;

    private boolean mReceiverAttached = false;
    private BroadcastReceiver mTimeReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            updateText();
        }
    };

    public RelativeTimeTextView(Context context) {
        super(context);
        init();
    }

    public RelativeTimeTextView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public RelativeTimeTextView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    public void setTime(long timeMs) {
        mTimeMs = timeMs;
        updateText();
    }

    public long getTime() {
        return mTimeMs;
    }

    public void updateText() {
        if (mTimeMs > 0) {
            setText(DateUtils.getRelativeDateTimeString(getContext(),
                    mTimeMs, DateUtils.MINUTE_IN_MILLIS, DateUtils.MINUTE_IN_MILLIS * 30,
                    DateUtils.FORMAT_ABBREV_RELATIVE));
        }
    }

    private void init() {
        mTimeMs = NOT_SET;
        addOnAttachStateChangeListener(this);
    }

    @Override
    public void onViewAttachedToWindow(View v) {
        if (!mReceiverAttached) {
            IntentFilter filter = new IntentFilter();
            filter.addAction(Intent.ACTION_TIME_TICK);
            filter.addAction(Intent.ACTION_TIME_CHANGED);
            filter.addAction(Intent.ACTION_TIMEZONE_CHANGED);
            getContext().registerReceiver(mTimeReceiver, filter);
            mReceiverAttached = true;
        }
    }

    @Override
    public void onViewDetachedFromWindow(View v) {
        if (mReceiverAttached) {
            getContext().unregisterReceiver(mTimeReceiver);
            mReceiverAttached = false;
        }
    }
}
