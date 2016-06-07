package com.google.android.apps.forscience.whistlepunk.sensordb;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;

class ExplicitExecutor implements Executor {
    List<Runnable> mQueue = new ArrayList<>();

    @Override
    public void execute(Runnable command) {
        mQueue.add(command);
    }

    public boolean drain() {
        if (mQueue.isEmpty()) {
            return false;
        }

        while (!mQueue.isEmpty()) {
            Runnable next = mQueue.remove(0);
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
