/*
 *  Copyright 2017 Google Inc. All Rights Reserved.
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

import static org.junit.Assert.assertEquals;

import com.google.android.apps.forscience.whistlepunk.TestConsumers;
import com.google.android.apps.forscience.whistlepunk.sensordb.StoringConsumer;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

@RunWith(RobolectricTestRunner.class)
public class ParallelTaskTest {
  @Test
  public void succeedLate() {
    ParallelTask task = new ParallelTask();
    MaybeConsumer<Success> a = task.addStep();
    MaybeConsumer<Success> b = task.addStep();
    MaybeConsumer<Success> c = task.addStep();

    StoringConsumer<Success> whenDone = new StoringConsumer<>();

    task.whenAllDone(whenDone);
    assertEquals(null, whenDone.getValue());

    b.success(Success.SUCCESS);
    assertEquals(null, whenDone.getValue());

    c.success(Success.SUCCESS);
    assertEquals(null, whenDone.getValue());

    // don't be fooled by repeat successes
    c.success(Success.SUCCESS);
    assertEquals(null, whenDone.getValue());

    a.success(Success.SUCCESS);
    assertEquals(Success.SUCCESS, whenDone.getValue());
  }

  @Test
  public void succeedEarly() {
    ParallelTask task = new ParallelTask();
    MaybeConsumer<Success> a = task.addStep();
    a.success(Success.SUCCESS);

    MaybeConsumer<Success> b = task.addStep();
    b.success(Success.SUCCESS);

    MaybeConsumer<Success> c = task.addStep();
    c.success(Success.SUCCESS);

    StoringConsumer<Success> whenDone = new StoringConsumer<>();

    task.whenAllDone(whenDone);
    assertEquals(Success.SUCCESS, whenDone.getValue());
  }

  @Test
  public void failure() {
    ParallelTask task = new ParallelTask();
    task.addStep().fail(new RuntimeException("oh no!"));
    task.whenAllDone(TestConsumers.expectingFailureType(RuntimeException.class));
  }
}
