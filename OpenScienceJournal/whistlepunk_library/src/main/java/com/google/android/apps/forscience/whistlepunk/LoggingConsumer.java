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
