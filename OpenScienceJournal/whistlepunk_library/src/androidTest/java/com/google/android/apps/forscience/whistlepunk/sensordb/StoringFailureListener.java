package com.google.android.apps.forscience.whistlepunk.sensordb;

import com.google.android.apps.forscience.javalib.FailureListener;

class StoringFailureListener implements FailureListener {
    public Exception exception;

    @Override
    public void fail(Exception e) {
        exception = e;
    }
}
