package com.google.android.apps.forscience.whistlepunk;

import android.content.Context;
import android.content.Intent;
import android.test.mock.MockContext;

/**
 * Stubb of RecorderService for tests.
 */
public class MockRecorderService extends RecorderService {
    MockRecorderService() {
    }

    @Override
    public Context getApplicationContext() {
        return new MockContext();
    }

    @Override
    public void beginServiceRecording(String experimentName, Intent launchIntent) {
        // Do nothing.
    }

    @Override
    public void endServiceRecording() {
        // Do nothing.
    }
}
