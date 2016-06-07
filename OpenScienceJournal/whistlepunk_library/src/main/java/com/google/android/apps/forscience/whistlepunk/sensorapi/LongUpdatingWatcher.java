package com.google.android.apps.forscience.whistlepunk.sensorapi;

import android.view.View;

/**
 * TextWatcher that updates an ActiveBundle with a long value when the text is changed.
 */
public class LongUpdatingWatcher extends OptionsUpdatingWatcher {
    private final String mBundleKey;

    public LongUpdatingWatcher(ActiveBundle activeBundle, String bundleKey, View view) {
        super(activeBundle, view);
        mBundleKey = bundleKey;
    }

    @Override
    protected void applyUpdate(String string, ActiveBundle activeBundle) {
        Long value = Long.valueOf(string);
        activeBundle.changeLong(mBundleKey, value);
    }
}
