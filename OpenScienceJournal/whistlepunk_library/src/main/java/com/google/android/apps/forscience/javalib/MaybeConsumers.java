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
