package com.google.android.apps.forscience.whistlepunk;

import com.google.android.apps.forscience.javalib.FailureListener;

/**
 * FailureListenerFactory that returns listeners that throw uncaught exceptions on failures,
 * for more easily tracked test feedback.
 */
public class ExplodingFactory implements MetadataController.FailureListenerFactory {
    @Override
    public FailureListener makeListenerForOperation(String operation) {
        return new FailureListener() {
            @Override
            public void fail(Exception e) {
                throw new RuntimeException(e);
            }
        };
    }
}
