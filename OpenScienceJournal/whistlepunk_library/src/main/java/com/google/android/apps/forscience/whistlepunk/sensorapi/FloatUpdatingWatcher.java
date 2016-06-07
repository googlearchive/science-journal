package com.google.android.apps.forscience.whistlepunk.sensorapi;

import android.view.View;

/**
 * TextWatcher that updates an ActiveBundle with a float value when the text is changed.
 */
public class FloatUpdatingWatcher extends OptionsUpdatingWatcher {
    private final String mBundleKey;

    public FloatUpdatingWatcher(ActiveBundle activeBundle, String bundleKey, View view) {
        super(activeBundle, view);
        mBundleKey = bundleKey;
    }

    @Override
    protected void applyUpdate(String string, ActiveBundle activeBundle) {
        Float value = Float.valueOf(string);
        activeBundle.changeFloat(mBundleKey, value);
    }
}
