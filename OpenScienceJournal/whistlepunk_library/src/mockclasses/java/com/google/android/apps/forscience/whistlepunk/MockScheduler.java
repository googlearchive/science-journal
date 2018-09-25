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

import com.google.android.apps.forscience.javalib.Delay;
import com.google.android.apps.forscience.javalib.Scheduler;
import java.util.Iterator;
import java.util.TreeSet;

// A mock scheduler that executes runnables after a delay.
// Note that this does may not run the runnables in order of when they were
// scheduled to be run: After time is incremented, any applicable runnable is run
// immediately.
public class MockScheduler implements Scheduler {

  class QueuedRunnable implements Comparable<QueuedRunnable> {
    public long executeAfter;
    public Runnable runnable;

    @Override
    public int compareTo(QueuedRunnable another) {
      int timingDiff = Long.compare(executeAfter, another.executeAfter);
      if (timingDiff != 0) {
        return timingDiff;
      }
      // Don't let two operations compare identical just because they're scheduled at the
      // same time.
      return Integer.compare(System.identityHashCode(this), System.identityHashCode(another));
    }
  }

  private long currentTime = 0;
  private TreeSet<QueuedRunnable> runnables = new TreeSet<>();
  private int scheduleCount = 0;

  @Override
  public void schedule(Delay delay, Runnable doThis) {
    scheduleCount++;
    if (delay.asMillis() == 0) {
      doThis.run();
    } else {
      // Add to list of runnables
      QueuedRunnable qr = new QueuedRunnable();
      qr.executeAfter = delay.asMillis() + currentTime;
      qr.runnable = doThis;
      runnables.add(qr);
    }
  }

  @Override
  public void unschedule(Runnable removeThis) {
    Iterator<QueuedRunnable> iter = runnables.iterator();
    while (iter.hasNext()) {
      if (iter.next().runnable == removeThis) {
        iter.remove();
      }
    }
  }

  public int getScheduleCount() {
    return scheduleCount;
  }

  public Clock getClock() {
    return new Clock() {
      @Override
      public long getNow() {
        return currentTime;
      }
    };
  }

  public void incrementTime(long ms) {
    long targetTime = currentTime + ms;

    while (!runnables.isEmpty() && runnables.first().executeAfter <= targetTime) {
      QueuedRunnable first = runnables.first();
      runnables.remove(first);
      currentTime = first.executeAfter;
      first.runnable.run();
    }
    currentTime = targetTime;
  }
}
