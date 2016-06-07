package com.google.android.apps.forscience.whistlepunk.sensorapi;

import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;

/**
 * TextWatcher that updates an ActiveBundle with a long value when the text is changed.
 */
public abstract class OptionsUpdatingWatcher implements TextWatcher {
    protected ActiveBundle mActiveBundle;
    protected View mView;

    public OptionsUpdatingWatcher(ActiveBundle activeBundle, View view) {
        mActiveBundle = activeBundle;
        mView = view;
    }

    protected abstract void applyUpdate(String string, ActiveBundle activeBundle)
            throws NumberFormatException;

    @Override
    public void beforeTextChanged(CharSequence s, int start, int count, int after) {

    }

    @Override
    public void onTextChanged(CharSequence s, int start, int before, int count) {

    }

    @Override
    public void afterTextChanged(Editable s) {
        try {
            String string = s.toString();
            applyUpdate(string, mActiveBundle);
        } catch (NumberFormatException e) {
            mActiveBundle.reportError(e.getMessage(), mView);
        }
    }
}
