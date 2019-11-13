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

import com.google.android.apps.forscience.javalib.Consumer;
import com.google.android.apps.forscience.javalib.FailureListener;
import com.google.android.apps.forscience.javalib.MaybeConsumer;
import java.util.Arrays;
import java.util.List;
import junit.framework.Assert;

public class TestConsumers {
  public static final MaybeConsumer<Object> EXPECT_NOTHING = expecting(null);

  public static <T> MaybeConsumer<T> expectingSuccess(final Consumer<T> c) {
    return new MaybeConsumer<T>() {
      @Override
      public void success(T actual) {
        c.take(actual);
      }

      @Override
      public void fail(Exception e) {
        throw new RuntimeException(e);
      }
    };
  }

  /**
   * Returns a consumer that will throw an exception if
   *
   * <p>
   *
   * <ol>
   *   <li>{@link MaybeConsumer#fail(Exception)} is called
   *   <li>{@link MaybeConsumer#success (Object)} is called with any value other than {@code
   *       expected}
   * </ol>
   */
  public static <T> MaybeConsumer<T> expecting(final T expected) {
    return expectingSuccess(
        new Consumer<T>() {
          @Override
          public void take(T t) {
            Assert.assertEquals(expected, t);
          }
        });
  }

  public static <T> MaybeConsumer<List<T>> expectingList(T... expected) {
    List<T> ls = Arrays.asList(expected);
    return expecting(ls);
  }

  public static <T> Consumer<T> guaranteedSuccess(final MaybeConsumer<T> m) {
    return new Consumer<T>() {
      @Override
      public void take(T t) {
        m.success(t);
      }
    };
  }

  public static <T> MaybeConsumer<T> expectingSuccess() {
    return expectingSuccess(
        new Consumer<T>() {
          @Override
          public void take(T t) {}
        });
  }

  public static <T> MaybeConsumer expectingFailure(final FailureListener listener) {
    return new MaybeConsumer<T>() {
      @Override
      public void success(T actual) {
        throw new RuntimeException("Expected failure");
      }

      @Override
      public void fail(Exception e) {
        listener.fail(e);
      }
    };
  }

  public static <T> MaybeConsumer expectingFailureType(final Class<? extends Exception> type) {
    return new MaybeConsumer<T>() {
      @Override
      public void success(T actual) {
        throw new RuntimeException("Expected failure");
      }

      @Override
      public void fail(Exception e) {
        assert type.isInstance(e);
      }
    };
  }
}
