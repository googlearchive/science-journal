package com.google.android.apps.forscience.whistlepunk;

import android.util.Log;

import com.google.android.apps.forscience.javalib.MaybeConsumer;

/**
 * MaybeConsumer that logs an stacktrace on failure, which is sufficient for many situations
 * in which failure is not expected.
 *
 * @param <T>
 */
public abstract class LoggingConsumer<T> implements MaybeConsumer<T> {
    public static <T> LoggingConsumer<T> expectSuccess(String tag, String operation) {
        return new LoggingConsumer<T>(tag, operation) {
            @Override
            public void success(T value) {
                // do nothing
            };
        };
    }

    private final String mTag;
    private final String mOperation;

    public LoggingConsumer(String tag, String operation) {
        mTag = tag;
        mOperation = operation;
    }

    @Override
    public void fail(Exception e) {
        // TODO: allow non-ERROR log levels
        if (Log.isLoggable(mTag, Log.ERROR)) {
            Log.e(mTag, "Failed: " + mOperation, e);
        }
    }
}
