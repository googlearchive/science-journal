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

package com.google.android.apps.forscience.whistlepunk.api.scalarinput;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;

public class ExplicitExecutor implements Executor {
  List<Runnable> queue = new ArrayList<>();

  @Override
  public void execute(Runnable command) {
    queue.add(command);
  }

  public boolean drain() {
    if (queue.isEmpty()) {
      return false;
    }

    while (!queue.isEmpty()) {
      Runnable next = queue.remove(0);
      next.run();
    }
    return true;
  }

  public static void drainAll(ExplicitExecutor... executors) {
    while (true) {
      boolean didSomething = false;
      for (final ExplicitExecutor executor : executors) {
        didSomething |= executor.drain();
      }
      if (!didSomething) {
        return;
      }
    }
  }
}
