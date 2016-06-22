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

/**
 * Utilities for interfaces that use {@link MaybeConsumer}
 */
public class MaybeConsumers {
    /**
     * A MaybeConsumer that does nothing
     */
    public static final MaybeConsumer<Object> NOOP = new MaybeConsumer<Object>() {
        @Override
        public void success(Object value) {
        }

        @Override
        public void fail(Exception e) {
        }
    };

    /**
     * A MaybeConsumer that does nothing, with a particular generic type
     */
    public static <T> MaybeConsumer<T> noop() {
        return (MaybeConsumer<T>) NOOP;
    }

    /**
     * Combine a failure listener and success listener into a single MaybeConsumer
     */
    public static <T> MaybeConsumer<T> chainFailure(final FailureListener failure,
            final Consumer<T> success) {
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

    /**
     * Combine a failure listener and fallible success listener into a single MaybeConsumer
     */
    public static <T> MaybeConsumer<T> chainFailure(final FailureListener failure,
            final FallibleConsumer<T> success) {
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

    /**
     * A MaybeConsumer that doesn't care about successful values, and passes along failures.
     */
    public static MaybeConsumer<Success> expectSuccess(FailureListener failureListener) {
        return chainFailure(failureListener, new Consumer<Success>() {
            @Override
            public void take(Success success) {
                // do nothing, expected
            }
        });
    }
}
