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

package com.google.android.apps.forscience.whistlepunk.sensordb;

import com.google.android.apps.forscience.javalib.Consumer;
import com.google.android.apps.forscience.javalib.MaybeConsumer;

public class StoringConsumer<T> implements MaybeConsumer<T> {
  public static <T> T retrieve(Consumer<MaybeConsumer<T>> c) {
    final StoringConsumer<T> t = new StoringConsumer<>();
    c.take(t);
    return t.getValue();
  }

  private T value;

  @Override
  public void success(T value) {
    this.value = value;
  }

  public T getValue() {
    return value;
  }

  @Override
  public void fail(Exception e) {
    throw new RuntimeException(e);
  }
}
