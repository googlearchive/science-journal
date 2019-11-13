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

package com.google.android.apps.forscience.whistlepunk.sensorapi;

import androidx.annotation.IntDef;
import com.google.android.apps.forscience.whistlepunk.R;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.text.NumberFormat;

/**
 * A calculated statistic from a stream of data such as the min / max / avg / duration, etc. Saves
 * the value as a double.
 */
public class StreamStat {

  @IntDef({TYPE_MIN, TYPE_MAX, TYPE_AVERAGE, TYPE_DURATION})
  @Retention(RetentionPolicy.SOURCE)
  public @interface StatType {}

  public static final int TYPE_MIN = 0;
  public static final int TYPE_MAX = 1;
  public static final int TYPE_AVERAGE = 2;
  public static final int TYPE_DURATION = 3;

  private @StatType int type;
  private boolean displayValue = false;
  private NumberFormat numberFormat;
  private double value;

  public StreamStat(@StatType int type, NumberFormat numberFormat) {
    this.type = type;
    this.numberFormat = numberFormat;
  }

  public @StatType int getType() {
    return type;
  }

  public int getDisplayTypeStringId() {
    switch (type) {
      case TYPE_MIN:
        return R.string.stat_min;
      case TYPE_MAX:
        return R.string.stat_max;
      case TYPE_AVERAGE:
        return R.string.stat_average;
      case TYPE_DURATION:
        return R.string.stat_duration;
      default:
        return R.string.stat_unknown;
    }
  }

  public String getDisplayValue() {
    if (displayValue) {
      return numberFormat.format(value);
    } else {
      return "";
    }
  }

  public void setValue(double value) {
    this.value = value;
    displayValue = true;
  }

  public void clear() {
    displayValue = false;
  }

  public double getValue() {
    return value;
  }
}
