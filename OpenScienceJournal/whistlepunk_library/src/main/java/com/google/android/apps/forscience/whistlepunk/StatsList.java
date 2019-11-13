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
import android.content.res.TypedArray;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.widget.FrameLayout;
import android.widget.TextView;
import com.google.android.apps.forscience.whistlepunk.sensorapi.StreamStat;
import java.util.ArrayList;
import java.util.List;

/**
 * Displays StreamStat objects from a given source. Takes an XML argument, statsLayout, which is a
 * layout resource file. Defaults to a small stats view, one that might be shown on the top of a
 * line graph. The XML layout resource file mut have three text views with IDs - stats_view_min, -
 * stats_view_max, and - stats_view_avg The StatsList will update those text views with the current
 * values of min, max and average respectively.
 */
public class StatsList extends FrameLayout {

  private static final Typeface MEDIUM_TYPEFACE =
      Typeface.create("sans-serif-medium", Typeface.NORMAL);
  private static final Typeface NORMAL_TYPEFACE = Typeface.create("sans-serif", Typeface.NORMAL);

  private List<StreamStat> stats;
  private SingleLineResizableTextView minTextView;
  private SingleLineResizableTextView maxTextView;
  private SingleLineResizableTextView avgTextView;

  public StatsList(Context context, AttributeSet attrs) {
    super(context, attrs);
    init(context, attrs);
  }

  public StatsList(Context context, AttributeSet attrs, int defStyle) {
    super(context, attrs, defStyle);
    init(context, attrs);
  }

  private void init(Context context, AttributeSet attrs) {
    TypedArray a = context.getTheme().obtainStyledAttributes(attrs, R.styleable.StatsList, 0, 0);
    try {
      int layout =
          a.getResourceId(R.styleable.StatsList_statsLayout, R.layout.stats_view_run_review);
      LayoutInflater.from(context).inflate(layout, this);
    } finally {
      a.recycle();
    }
    minTextView = (SingleLineResizableTextView) this.findViewById(R.id.stats_view_min);
    maxTextView = (SingleLineResizableTextView) this.findViewById(R.id.stats_view_max);
    avgTextView = (SingleLineResizableTextView) this.findViewById(R.id.stats_view_avg);
    stats = new ArrayList<>();
  }

  public void updateStats(List<StreamStat> stats) {
    this.stats.clear();
    this.stats.addAll(stats);

    for (int i = 0; i < stats.size(); i++) {
      StreamStat stat = stats.get(i);
      TextView next = null;
      switch (stat.getType()) {
        case StreamStat.TYPE_MIN:
          next = minTextView;
          break;
        case StreamStat.TYPE_MAX:
          next = maxTextView;
          break;
        case StreamStat.TYPE_AVERAGE:
          next = avgTextView;
          break;
      }
      if (next == null) {
        continue;
      }
      String text = stat.getDisplayValue();
      next.setText(text);
      next.setContentDescription(
          getResources().getString(stat.getDisplayTypeStringId()) + ": " + text);
    }
  }

  public void clearStats() {
    stats.clear();
    String unknownContentDescription = getResources().getString(R.string.stat_unknown);
    minTextView.setContentDescription(
        getResources().getString(R.string.stat_min) + ": " + unknownContentDescription);
    maxTextView.setContentDescription(
        getResources().getString(R.string.stat_max) + ": " + unknownContentDescription);
    avgTextView.setContentDescription(
        getResources().getString(R.string.stat_average) + ": " + unknownContentDescription);

    String unknown = getResources().getString(R.string.indeterminate_value);
    minTextView.setText(unknown);
    maxTextView.setText(unknown);
    avgTextView.setText(unknown);

    minTextView.resetTextSize();
    maxTextView.resetTextSize();
    avgTextView.resetTextSize();
  }

  public void setTextBold(boolean shouldBeBold) {
    Typeface typeface;
    if (shouldBeBold) {
      typeface = MEDIUM_TYPEFACE;
    } else {
      typeface = NORMAL_TYPEFACE;
    }
    minTextView.setTypeface(typeface);
    maxTextView.setTypeface(typeface);
    avgTextView.setTypeface(typeface);
  }

  public void updateColor(int color) {
    minTextView.setTextColor(color);
    maxTextView.setTextColor(color);
  }
}
