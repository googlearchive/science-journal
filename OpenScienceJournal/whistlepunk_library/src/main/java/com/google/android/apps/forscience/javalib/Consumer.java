package com.google.android.apps.forscience.javalib;

import java.util.ArrayList;
import java.util.List;

public abstract class Consumer<T> {
    public abstract void take(T t);

    public Consumer<T> and(Consumer<T> consumer) {
        if (consumer == null) {
            return this;
        }
        return new CompoundConsumer(this, consumer);
    }

    private class CompoundConsumer extends Consumer<T> {
        private final List<Consumer<T>> mConsumers = new ArrayList<>();

        CompoundConsumer(Consumer<T> first, Consumer<T> second) {
            mConsumers.add(first);
            mConsumers.add(second);
        }

        @Override
        public void take(T t) {
            for (Consumer<T> consumer : mConsumers) {
                consumer.take(t);
            }
        }

        @Override
        public Consumer<T> and(Consumer<T> consumer) {
            if (consumer != null) {
                mConsumers.add(consumer);
            }
            return this;
        }
    }
}
