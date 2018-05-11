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
 * <p>Example:
 *
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
  private MaybeConsumer<Success> whenDone = null;
  private final SparseBooleanArray stepsDone = new SparseBooleanArray();
  private final List<Exception> failures = new ArrayList<>();

  public MaybeConsumer<Success> addStep() {
    final int index = stepsDone.size();
    stepsDone.put(index, false);
    return new MaybeConsumer<Success>() {
      @Override
      public void success(Success value) {
        done();
      }

      @Override
      public void fail(Exception e) {
        failures.add(e);
        done();
      }

      protected void done() {
        stepsDone.put(index, true);
        checkForAllDone();
      }
    };
  }

  public void whenAllDone(MaybeConsumer<Success> whenDone) {
    this.whenDone = whenDone;
    checkForAllDone();
  }

  private void checkForAllDone() {
    if (whenDone == null) {
      return;
    }
    int numSteps = stepsDone.size();
    for (int i = 0; i < numSteps; i++) {
      if (!stepsDone.get(i)) {
        return;
      }
    }
    if (failures.isEmpty()) {
      whenDone.success(Success.SUCCESS);
    } else {
      for (Exception failure : failures) {
        whenDone.fail(failure);
      }
    }
    whenDone = null;
  }
}
