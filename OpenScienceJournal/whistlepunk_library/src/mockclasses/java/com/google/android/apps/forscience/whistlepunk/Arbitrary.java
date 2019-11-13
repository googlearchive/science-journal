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

import com.google.android.apps.forscience.javalib.Delay;
import com.google.common.collect.Range;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

@SuppressWarnings("WeakerAccess")
public class Arbitrary {
  // TODO(saff): share code with Weather?
  private static Random random = null;

  private static Random getRandom() {
    if (random == null) {
      random = new Random();
    }
    return random;
  }

  public static int integer() {
    return getRandom().nextInt();
  }

  private static int integer(int n) {
    return getRandom().nextInt(n);
  }

  /**
   * Return an arbitrary string using the given prefix, which can help with debugging (calling
   * Arbitrary.string("deviceName") can give you a string that makes it easier to tell when you're
   * erroneously using a device name as a device id.)
   */
  public static String string(String prefix) {
    return prefix + "=" + string();
  }

  public static String string() {
    return memberOf("Ganymede", "Io", "Callisto", "Europa");
  }

  private static <T> T memberOf(T... items) {
    return items[integer(items.length)];
  }

  public static List<String> distinctStrings() {
    final List<String> strings = new ArrayList<>();
    int i = 0;
    do {
      strings.add(string() + (i++));
    } while (getRandom().nextInt(3) > 0);
    return strings;
  }

  public static List<String> distinctStrings(int howMany) {
    final List<String> strings = new ArrayList<>();
    for (int i = 0; i < howMany; i++) {
      strings.add(string() + i);
    }
    return strings;
  }

  public static double doubleFloat() {
    return getRandom().nextDouble();
  }

  public static String stringDifferentThan(String other) {
    return stringSortingAfter(other);
  }

  public static String stringSortingAfter(String other) {
    return other + ":" + string();
  }

  public static Range<Long> longRange() {
    long a = longInteger();
    long b = longInteger();
    return Range.closed(Math.min(a, b), Math.max(a, b));
  }

  public static long longInteger() {
    return getRandom().nextLong();
  }

  public static Delay delay() {
    return Delay.seconds(integer());
  }

  public static float singleFloat() {
    return getRandom().nextFloat();
  }

  public static boolean bool() {
    return getRandom().nextBoolean();
  }
}
