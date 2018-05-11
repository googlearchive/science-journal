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
import java.text.FieldPosition;
import java.text.NumberFormat;
import java.text.ParsePosition;

/** Formats a timestamp based on how far away it was from a known 0 time. */
public class RelativeTimeFormat extends NumberFormat {
  private final ElapsedTimeAxisFormatter elapsedTimeFormatter;
  private final long zeroTimestamp;

  public RelativeTimeFormat(long zeroTimestamp, Context context) {
    this.zeroTimestamp = zeroTimestamp;
    elapsedTimeFormatter = ElapsedTimeAxisFormatter.getInstance(context);
  }

  @Override
  public StringBuffer format(double value, StringBuffer buffer, FieldPosition field) {
    return format((long) value, buffer, field);
  }

  @Override
  public StringBuffer format(long value, StringBuffer buffer, FieldPosition field) {
    return buffer.append(elapsedTimeFormatter.format(value - zeroTimestamp, false));
  }

  @Override
  public Number parse(String string, ParsePosition position) {
    return null;
  }
}
