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

import android.util.SparseBooleanArray;

import java.util.ArrayList;
import java.util.List;

/**
 * Easy way to wait for multiple asynchronous processes to complete before moving on.
 *
 * Example:
 * <pre>
 *   ParallelTask task = new ParallelTask();
 *   doSomeTask(task.addStep());
 *   doADifferentTask(task.addStep());
 *   yetAnotherTask(task.addStep());
 *
 *   task.whenAllDone(new LoggingConsumer<Success>(TAG, "three-step process") {
 *      void onSuccess(Success success) {
 *          cleanUpAfterAllThreeTasksDone();
 *      }
 *   });
 * </pre>
 *
 * All calls to {@see addStep} should come before the single call to {@see whenAllDone}.
 */
public class ParallelTask {
    private MaybeConsumer<Success> mWhenDone = null;
    private final SparseBooleanArray mStepsDone = new SparseBooleanArray();
    private final List<Exception> mFailures = new ArrayList<>();

    public MaybeConsumer<Success> addStep() {
        final int index = mStepsDone.size();
        mStepsDone.put(index, false);
        return new MaybeConsumer<Success>() {
            @Override
            public void success(Success value) {
                done();
            }

            @Override
            public void fail(Exception e) {
                mFailures.add(e);
                done();
            }

            protected void done() {
                mStepsDone.put(index, true);
                checkForAllDone();
            }
        };
    }

    public void whenAllDone(MaybeConsumer<Success> whenDone) {
        mWhenDone = whenDone;
        checkForAllDone();
    }

    private void checkForAllDone() {
        if (mWhenDone == null) {
            return;
        }
        int numSteps = mStepsDone.size();
        for (int i = 0; i < numSteps; i++) {
            if (! mStepsDone.get(i)) {
                return;
            }
        }
        if (mFailures.isEmpty()) {
            mWhenDone.success(Success.SUCCESS);
        } else {
            for (Exception failure : mFailures) {
                mWhenDone.fail(failure);
            }
        }
        mWhenDone = null;
    }
}
