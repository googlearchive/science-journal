package com.google.android.apps.forscience.javalib;

/**
 * Many times, a queued operation may succeed and deliver a value, or fail with an exception.
 * The methods that enqueue such operations should take a MaybeConsumer to which the success or
 * failure is delivered.
 */
public interface MaybeConsumer<T> extends FailureListener {
    void success(T value);
}
