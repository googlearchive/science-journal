package com.google.android.apps.forscience.whistlepunk.sensordb;

import com.google.android.apps.forscience.javalib.Consumer;
import com.google.android.apps.forscience.javalib.MaybeConsumer;

public class StoringConsumer<T> implements MaybeConsumer<T> {
    public static <T> T retrieve(Consumer<MaybeConsumer<T>> c) {
        final StoringConsumer<T> t = new StoringConsumer<>();
        c.take(t);
        return t.getValue();
    }

    private T mValue;

    @Override
    public void success(T value) {
        mValue = value;
    }

    public T getValue() {
        return mValue;
    }

    @Override
    public void fail(Exception e) {
        throw new RuntimeException(e);
    }
}
