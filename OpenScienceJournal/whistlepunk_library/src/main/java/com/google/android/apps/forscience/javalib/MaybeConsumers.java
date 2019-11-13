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

import io.reactivex.Completable;
import io.reactivex.CompletableEmitter;
import io.reactivex.CompletableObserver;
import io.reactivex.CompletableOnSubscribe;
import io.reactivex.Maybe;
import io.reactivex.Observer;
import io.reactivex.Single;
import io.reactivex.SingleEmitter;
import io.reactivex.SingleObserver;
import io.reactivex.SingleOnSubscribe;
import io.reactivex.annotations.NonNull;
import io.reactivex.disposables.Disposable;

/** Utilities for interfaces that use {@link MaybeConsumer} */
public class MaybeConsumers {
  /** A MaybeConsumer that does nothing */
  private static final MaybeConsumer<Object> NOOP =
      new MaybeConsumer<Object>() {
        @Override
        public void success(Object value) {}

        @Override
        public void fail(Exception e) {}
      };

  /** A MaybeConsumer that does nothing, with a particular generic type */
  public static <T> MaybeConsumer<T> noop() {
    return (MaybeConsumer<T>) NOOP;
  }

  /** Combine a failure listener and success listener into a single MaybeConsumer */
  public static <T> MaybeConsumer<T> chainFailure(
      final FailureListener failure, final Consumer<T> success) {
    return new MaybeConsumer<T>() {
      @Override
      public void success(T value) {
        success.take(value);
      }

      @Override
      public void fail(Exception e) {
        failure.fail(e);
      }
    };
  }

  /** Combine a failure listener and fallible success listener into a single MaybeConsumer */
  public static <T> MaybeConsumer<T> chainFailure(
      final FailureListener failure, final FallibleConsumer<T> success) {
    return new MaybeConsumer<T>() {
      @Override
      public void success(T value) {
        try {
          success.take(value);
        } catch (Exception e) {
          fail(e);
        }
      }

      @Override
      public void fail(Exception e) {
        failure.fail(e);
      }
    };
  }

  /** A MaybeConsumer that doesn't care about successful values, and passes along failures. */
  public static MaybeConsumer<Success> expectSuccess(FailureListener failureListener) {
    return chainFailure(
        failureListener,
        new Consumer<Success>() {
          @Override
          public void take(Success success) {
            // do nothing, expected
          }
        });
  }

  /**
   * Given an operation that takes a {@link MaybeConsumer<Success>}, create a JavaRX {@link
   * Completable} that succeeds iff the operation does.
   *
   * <p>Example:
   *
   * <pre>
   *     // update the experiment, and then log that it was successful
   *     DataController dc = getDataController();
   *     MaybeConsumers.buildCompleteable(mc -> dc.updateExperiment(e.getExperimentId(), mc))
   *                   .subscribe(() -> log("successfully updated!"));
   * </pre>
   */
  public static Completable buildCompleteable(
      io.reactivex.functions.Consumer<MaybeConsumer<Success>> c) {
    return Completable.create(
        new CompletableOnSubscribe() {
          @Override
          public void subscribe(CompletableEmitter emitter) throws Exception {
            c.accept(
                new MaybeConsumer<Success>() {
                  @Override
                  public void success(Success value) {
                    emitter.onComplete();
                  }

                  @Override
                  public void fail(Exception e) {
                    emitter.onError(e);
                  }
                });
          }
        });
  }

  /**
   * Given an operation that takes a {@link MaybeConsumer<T>}, create a JavaRX {@link Single<T>}
   * that produces the value passed to the MaybeConsumer.
   *
   * <p>Example:
   *
   * <pre>
   *     // log the name of the experiment with a given id
   *     DataController dc = getDataController();
   *     MaybeConsumers.buildSingle(mc -> dc.getExperimentById(id, mc))
   *                   .subscribe(experiment -> log("Name: " + experiment.getName()));
   * </pre>
   */
  public static <T> Single<T> buildSingle(io.reactivex.functions.Consumer<MaybeConsumer<T>> c) {
    return Single.create(
        new SingleOnSubscribe<T>() {
          @Override
          public void subscribe(SingleEmitter<T> emitter) throws Exception {
            c.accept(
                new MaybeConsumer<T>() {
                  @Override
                  public void success(T value) {
                    emitter.onSuccess(value);
                  }

                  @Override
                  public void fail(Exception e) {
                    emitter.onError(e);
                  }
                });
          }
        });
  }

  /**
   * Given an operation that takes a {@link MaybeConsumer<T>}, create a JavaRX {@link Maybe<T>} that
   * produces the value passed to the MaybeConsumer, or onComplete if the value is null
   *
   * <p>Example:
   *
   * <pre>
   *     // log the name of the experiment with a given id
   *     DataController dc = getDataController();
   *     MaybeConsumers.MaybeConsumers.buildMaybe(mc -> dc.getLastUsedUnarchivedExperiment(mc))
   *                   .subscribe(experiment -> log("Name: " + experiment.getName()));
   * </pre>
   */
  public static <T> Maybe<T> buildMaybe(io.reactivex.functions.Consumer<MaybeConsumer<T>> c) {
    return Maybe.create(
        emitter ->
            c.accept(
                new MaybeConsumer<T>() {
                  @Override
                  public void success(T value) {
                    if (value == null) {
                      emitter.onComplete();
                    } else {
                      emitter.onSuccess(value);
                    }
                  }

                  @Override
                  public void fail(Exception e) {
                    emitter.onError(e);
                  }
                }));
  }

  /**
   * MaybeConsumer<Success> is very similar to {@link CompletableObserver} in JavaRX. Both are
   * looking for either a signal that a process has succeeded, or that it has failed with some
   * exception. For the time period where we are still using both interfaces, we will find it useful
   * to be able to switch between them.
   *
   * @return a {@link MaybeConsumer<Success>} that pipes {@link MaybeConsumer#success(Object)} to
   *     {@link CompletableObserver#onComplete()}, and {@link MaybeConsumer#fail(Exception)} to
   *     {@link CompletableObserver#onError(Throwable)}
   */
  public static MaybeConsumer<Success> fromCompletableObserver(final CompletableObserver o) {
    return new MaybeConsumer<Success>() {
      @Override
      public void success(Success value) {
        o.onComplete();
      }

      @Override
      public void fail(Exception e) {
        o.onError(e);
      }
    };
  }

  /**
   * Performs the opposite translation from {@link
   * MaybeConsumers#fromCompletableObserver(CompletableObserver)}
   */
  public static CompletableObserver toCompletableObserver(final MaybeConsumer<Success> c) {
    return new CompletableObserver() {
      @Override
      public void onSubscribe(@NonNull Disposable d) {
        // do nothing
      }

      @Override
      public void onComplete() {
        c.success(Success.SUCCESS);
      }

      @Override
      public void onError(@NonNull Throwable e) {
        c.fail(throwableToException(e));
      }
    };
  }

  private static Exception throwableToException(Throwable e) {
    if (e instanceof Exception) {
      return (Exception) e;
    } else {
      return new Exception(e);
    }
  }

  /**
   * MaybeConsumer<T> is very similar to {@link SingleObserver<T>} in JavaRX. Both are looking for
   * either a signal that a computation has succeeded and returned a value of type T, or that it has
   * failed with some exception. For the time period where we are still using both interfaces, we
   * will find it useful to be able to switch between them.
   *
   * @return a {@link MaybeConsumer<T>} that pipes {@link MaybeConsumer#success(Object)} to {@link
   *     SingleObserver#onSuccess(Object)}, and {@link MaybeConsumer#fail(Exception)} to {@link
   *     SingleObserver#onError(Throwable)}
   */
  public static <T> MaybeConsumer<T> fromSingleObserver(final SingleObserver<T> o) {
    return new MaybeConsumer<T>() {
      @Override
      public void success(T value) {
        o.onSuccess(value);
      }

      @Override
      public void fail(Exception e) {
        o.onError(e);
      }
    };
  }

  /**
   * Performs the opposite translation from {@link
   * MaybeConsumers#fromSingleObserver(SingleObserver)}
   */
  public static <T> SingleObserver<T> toSingleObserver(final MaybeConsumer<T> c) {
    return new SingleObserver<T>() {
      @Override
      public void onSubscribe(@NonNull Disposable d) {
        // do nothing
      }

      @Override
      public void onSuccess(@NonNull T t) {
        c.success(t);
      }

      @Override
      public void onError(@NonNull Throwable e) {
        c.fail(new RuntimeException(e));
      }
    };
  }

  /**
   * Allows a function that takes a MaybeConsumer to pipe a single success value to the given
   * Observer (which may also be accepting values from other places)
   *
   * @return a {@link MaybeConsumer<T>} that pipes {@link MaybeConsumer#success(Object)} to {@link
   *     Observer#onNext(Object)}, and {@link MaybeConsumer#fail(Exception)} to {@link
   *     Observer#onError(Throwable)}
   */
  public static <T> MaybeConsumer<T> fromObserver(Observer<T> o) {
    return new MaybeConsumer<T>() {
      @Override
      public void success(T value) {
        // if value is null, just report empty
        if (value != null) {
          o.onNext(value);
        }
        o.onComplete();
      }

      @Override
      public void fail(Exception e) {
        o.onError(e);
      }
    };
  }
}
