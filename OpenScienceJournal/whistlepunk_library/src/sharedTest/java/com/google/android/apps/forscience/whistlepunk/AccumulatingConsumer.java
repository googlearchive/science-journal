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

import static org.junit.Assert.assertEquals;

import com.google.android.apps.forscience.javalib.Consumer;
import java.util.ArrayList;
import java.util.List;

/**
 * For test support: this consumer may receive one or more calls to {@link #take(Object)}. All
 * consumed items are stored in {@link #seen} to be examined by tests.
 */
public class AccumulatingConsumer<T> extends Consumer<T> {
  public List<T> seen = new ArrayList<>();

  @Override
  public void take(T t) {
    seen.add(t);
  }

  public T getOnlySeen() {
    assertEquals(1, seen.size());
    return seen.get(0);
  }
}
