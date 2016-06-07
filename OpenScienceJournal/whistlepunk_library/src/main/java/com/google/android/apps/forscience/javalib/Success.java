package com.google.android.apps.forscience.javalib;

public class Success {
    public static void succeeded(MaybeConsumer<Success> c) {
        c.success(SUCCESS);
    }

    public static Success SUCCESS = new Success();

    private Success() {
        // don't allow instantiation
    }
}
