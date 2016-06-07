package com.google.android.apps.forscience.whistlepunk.sensorapi;

import android.view.View;

import com.google.android.apps.forscience.javalib.Consumer;
import com.google.common.base.Preconditions;

/**
 * Convenience class: provides access to a bundle's values, while allowing changes to
 * the bundle to be tracked.
 *
 * Note that this contains a reference to a handler that may itself refer back to an activity.
 * To prevent leaks, do not hold references to an ActiveBundle longer than necessary.
 */
public class ActiveBundle {
    private final WriteableSensorOptions mBundle;
    private final Consumer<ReadableSensorOptions> mOnOptionsChanged;
    private final OnErrorListener mOnEntryError;

    public interface OnErrorListener {
        void onError(String error, View relevantView);
    }

    public ActiveBundle(WriteableSensorOptions bundle,
            Consumer<ReadableSensorOptions> onOptionsChanged, OnErrorListener onEntryError) {
        mOnEntryError = onEntryError;
        mBundle = Preconditions.checkNotNull(bundle);
        mOnOptionsChanged = Preconditions.checkNotNull(onOptionsChanged);
    }

    /**
     * @return the underlying Bundle.  Note that while this does not prohibit the caller from
     * calling modifying methods on the returned bundle, any changes made will not be broadcast to
     * the change listener.
     */
    public ReadableSensorOptions getReadOnly() {
        return mBundle.getReadOnly();
    }

    public void changeBoolean(String key, boolean value) {
        changeString(key, String.valueOf(value));
    }

    public void changeLong(String key, long value) {
        changeString(key, String.valueOf(value));
    }

    public void changeFloat(String key, float value) {
        changeString(key, String.valueOf(value));
    }

    public void changeInt(String key, int value) {
        changeString(key, String.valueOf(value));
    }

    public void changeString(String key, String value) {
        mBundle.put(key, value);
        notifyListener();
    }

    public void reportError(String message, View view) {
        mOnEntryError.onError(message, view);
    }

    private void notifyListener() {
        mOnOptionsChanged.take(mBundle.getReadOnly());
    }
}
