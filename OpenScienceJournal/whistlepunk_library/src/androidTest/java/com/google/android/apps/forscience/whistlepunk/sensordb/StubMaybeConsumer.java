package com.google.android.apps.forscience.whistlepunk.sensordb;

import com.google.android.apps.forscience.javalib.MaybeConsumer;
import com.google.android.apps.forscience.javalib.Success;

class StubMaybeConsumer implements MaybeConsumer<Success> {
    @Override
    public void success(Success value) {

    }

    @Override
    public void fail(Exception e) {

    }
}
