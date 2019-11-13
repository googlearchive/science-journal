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

package com.google.android.apps.forscience.whistlepunk.sensors;

import android.os.Handler;
import android.os.Looper;
import com.google.android.apps.forscience.javalib.Delay;
import com.google.android.apps.forscience.javalib.Scheduler;

/** Schedules tasks using built-in Android looper */
public class SystemScheduler implements Scheduler {
  private final Handler handler = new Handler(Looper.getMainLooper());

  @Override
  public void schedule(Delay delay, Runnable doThis) {
    handler.postDelayed(doThis, delay.asMillis());
  }

  @Override
  public void unschedule(Runnable removeThis) {
    handler.removeCallbacks(removeThis);
  }
}
