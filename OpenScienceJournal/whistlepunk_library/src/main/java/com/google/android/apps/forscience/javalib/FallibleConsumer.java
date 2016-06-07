package com.google.android.apps.forscience.javalib;

/**
 * A consumer that does something with a provided value, but may fail in some way, throwing an
 * exception.
 */
public interface FallibleConsumer<T> {
    void take(T t) throws Exception;
}
