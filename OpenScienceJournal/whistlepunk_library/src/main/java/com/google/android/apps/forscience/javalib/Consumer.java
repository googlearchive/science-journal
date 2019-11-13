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

package com.google.android.apps.forscience.javalib;

import java.util.ArrayList;
import java.util.List;

public abstract class Consumer<T> {
  public abstract void take(T t);

  public Consumer<T> and(Consumer<T> consumer) {
    if (consumer == null) {
      return this;
    }
    return new CompoundConsumer(this, consumer);
  }

  private class CompoundConsumer extends Consumer<T> {
    private final List<Consumer<T>> consumers = new ArrayList<>();

    CompoundConsumer(Consumer<T> first, Consumer<T> second) {
      consumers.add(first);
      consumers.add(second);
    }

    @Override
    public void take(T t) {
      for (Consumer<T> consumer : consumers) {
        consumer.take(t);
      }
    }

    @Override
    public Consumer<T> and(Consumer<T> consumer) {
      if (consumer != null) {
        consumers.add(consumer);
      }
      return this;
    }
  }
}
