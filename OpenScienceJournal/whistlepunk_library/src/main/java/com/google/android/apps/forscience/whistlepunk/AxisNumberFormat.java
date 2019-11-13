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

import java.text.DecimalFormat;
import java.text.FieldPosition;
import java.text.NumberFormat;
import java.text.ParsePosition;

/** The format for the Y axis. */
public class AxisNumberFormat extends NumberFormat {

  // Format numbers less than .01 as 0.
  private static final String FORMAT_TINY = "0";

  // Format numbers less than 10 with one decimal place.
  private static final double MIN_SMALL = 0.1;
  private static final String FORMAT_SMALL = "%1$.1f";

  // Format numbers equal or greater than 10 and less than 100 without any decimal places.
  private static final double MIN_DECI = 10;
  private static final String FORMAT_DECI = "%1$.0f";

  // Format numbers equal or greater than 1000 with a "k", like "10k".
  private static final double MIN_KILO = 1000;
  private static final String FORMAT_KILO = "%1$.0f%2$s";
  private static final String KILO_SUFFIX = "K";

  // Format numbers equal or greater than 1,000,000 with a "m", like "2M".
  private static final double MIN_MIL = 1000000;
  private static final String FORMAT_MIL = "%1$.0f%2$s";
  private static final String MIL_SUFFIX = "M";

  // Format numbers larger than 1 billion with scientific notation, like "2e10".
  private static final double MIN_SCIENTIFIC = 1e9;
  private final DecimalFormat scientificFormatter;
  private final ReusableFormatter formatter;

  public AxisNumberFormat() {
    scientificFormatter = new DecimalFormat("0.#E0");
    formatter = new ReusableFormatter();
  }

  @Override
  public StringBuffer format(double value, StringBuffer buffer, FieldPosition field) {
    if (Math.abs(value) < MIN_SMALL) {
      return buffer.append(FORMAT_TINY);
    } else if (Math.abs(value) < MIN_DECI) {
      return buffer.append(formatter.format(FORMAT_SMALL, value));
    } else if (Math.abs(value) < MIN_KILO) {
      return buffer.append(formatter.format(FORMAT_DECI, value));
    } else if (Math.abs(value) < MIN_MIL) {
      return buffer.append(formatter.format(FORMAT_KILO, value / MIN_KILO, KILO_SUFFIX));
    } else if (Math.abs(value) < MIN_SCIENTIFIC) {
      return buffer.append(formatter.format(FORMAT_MIL, value / MIN_MIL, MIL_SUFFIX));
    } else {
      return buffer.append(scientificFormatter.format(value));
    }
  }

  @Override
  public StringBuffer format(long value, StringBuffer buffer, FieldPosition field) {
    return format((double) value, buffer, field);
  }

  @Override
  public Number parse(String string, ParsePosition position) {
    return null;
  }
}
