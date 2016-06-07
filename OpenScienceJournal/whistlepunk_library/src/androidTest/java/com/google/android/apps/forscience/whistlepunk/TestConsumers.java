package com.google.android.apps.forscience.whistlepunk;

import com.google.android.apps.forscience.javalib.Consumer;
import com.google.android.apps.forscience.javalib.MaybeConsumer;

import junit.framework.Assert;

import java.util.Arrays;
import java.util.List;

public class TestConsumers {
    public static final MaybeConsumer<Object> EXPECT_NOTHING = expecting(null);

    public static <T> MaybeConsumer<T> expectingSuccess(final Consumer<T> c) {
        return new MaybeConsumer<T>() {
            @Override
            public void success(T actual) {
                c.take(actual);
            }

            @Override
            public void fail(Exception e) {
                throw new RuntimeException(e);
            }
        };

    }

    /**
     * Returns a consumer that will throw an exception if
     * <p/>
     * <ol> <li>{@link MaybeConsumer#fail(Exception)} is
     * called</li> <li>{@link MaybeConsumer#success
     * (Object)} is called with any value other than {@code expected}</li> </ol>
     */
    public static <T> MaybeConsumer<T> expecting(final T expected) {
        return expectingSuccess(new Consumer<T>() {
            @Override
            public void take(T t) {
                Assert.assertEquals(expected, t);
            }
        });
    }

    public static <T> MaybeConsumer<List<T>> expectingList(T... expected) {
        List<T> ls = Arrays.asList(expected);
        return expecting(ls);
    }

    public static <T> Consumer<T> guaranteedSuccess(final MaybeConsumer<T> m) {
        return new Consumer<T>() {
            @Override
            public void take(T t) {
                m.success(t);
            }
        };
    }

    public static <T> MaybeConsumer<T> expectingSuccess() {
        return expectingSuccess(new Consumer<T>() {
            @Override
            public void take(T t) {

            }
        });
    }
}
