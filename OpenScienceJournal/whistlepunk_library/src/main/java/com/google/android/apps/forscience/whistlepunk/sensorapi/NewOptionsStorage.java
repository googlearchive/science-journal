package com.google.android.apps.forscience.whistlepunk.sensorapi;

import android.content.Context;
import android.os.Bundle;
import android.support.design.widget.Snackbar;
import android.util.Log;
import android.view.View;

import com.google.android.apps.forscience.javalib.FailureListener;
import com.google.android.apps.forscience.whistlepunk.AccessibilityUtils;
import com.google.android.apps.forscience.whistlepunk.R;

/**
 * Interface for loading and storing a sensor's options from persistent storage.
 * <p/>
 * Implementations may use SharedPreferences, a SQLite database, or another strategy.
 */
// TODO: remove uses of OptionsStorage, rename to remove "New"
public interface NewOptionsStorage {
    WriteableSensorOptions load(FailureListener onFailures);

    class SnackbarFailureListener implements FailureListener {
        private static final String TAG = "SnackbarFListener";
        private final View mView;

        public SnackbarFailureListener(View view) {
            mView = view;
        }

        @Override
        public void fail(Exception e) {
            if (Log.isLoggable(TAG, Log.ERROR)) {
                Log.e(TAG, "Error loading options", e);
            }
            if (mView == null) {
                return;
            }
            String message = mView.getResources().getString(R.string.options_load_error);
            AccessibilityUtils.makeSnackbar(mView, message, Snackbar.LENGTH_LONG).show();
        }
    }
}
