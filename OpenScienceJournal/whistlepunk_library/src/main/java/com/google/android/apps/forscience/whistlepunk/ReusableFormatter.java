/*
 *  Copyright 2017 Google Inc. All Rights Reserved.
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

import java.util.Formatter;

/**
 * A re-usable formatter class to use as an alternative to String.format, which creates a new
 * Formatter behind the scenes for each use.
 */
public class ReusableFormatter {
  private Formatter formatter;
  private StringBuilder builder;

  public ReusableFormatter() {
    builder = new StringBuilder();
    formatter = new Formatter(builder);
  }

  public Formatter format(String format, Object... params) {
    builder.setLength(0);
    return formatter.format(format, params);
  }
}
