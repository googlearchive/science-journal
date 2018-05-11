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
import androidx.appcompat.widget.AppCompatTextView;
import android.text.format.DateUtils;
import android.util.AttributeSet;
import android.view.View;

/**
 * TextView which takes a relative time parameter and updates itself every minute.
 *
 * <p>To use, call {@link #setTime(long)}. If no time is set or the time is set to {@link #NOT_SET},
 * then this functions like a regular TextView.
 */
public class RelativeTimeTextView extends AppCompatTextView
    implements View.OnAttachStateChangeListener {

  public static final long NOT_SET = -1;

  private long timeMs;

  private boolean receiverAttached = false;
  private BroadcastReceiver timeReceiver =
      new BroadcastReceiver() {
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
    this.timeMs = timeMs;
    updateText();
  }

  public long getTime() {
    return timeMs;
  }

  public void updateText() {
    if (timeMs > 0) {
      setText(
          DateUtils.getRelativeDateTimeString(
              getContext(),
              timeMs,
              DateUtils.MINUTE_IN_MILLIS,
              DateUtils.MINUTE_IN_MILLIS * 30,
              DateUtils.FORMAT_ABBREV_RELATIVE));
    }
  }

  private void init() {
    timeMs = NOT_SET;
    addOnAttachStateChangeListener(this);
  }

  @Override
  public void onViewAttachedToWindow(View v) {
    if (!receiverAttached) {
      IntentFilter filter = new IntentFilter();
      filter.addAction(Intent.ACTION_TIME_TICK);
      filter.addAction(Intent.ACTION_TIME_CHANGED);
      filter.addAction(Intent.ACTION_TIMEZONE_CHANGED);
      getContext().registerReceiver(timeReceiver, filter);
      receiverAttached = true;
    }
  }

  @Override
  public void onViewDetachedFromWindow(View v) {
    if (receiverAttached) {
      getContext().unregisterReceiver(timeReceiver);
      receiverAttached = false;
    }
  }
}
