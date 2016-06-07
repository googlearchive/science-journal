package com.google.android.apps.forscience.whistlepunk;

import android.widget.SeekBar;

import com.google.android.apps.forscience.whistlepunk.sensorapi.ActiveBundle;

/**
 * Seek bar listeners that update an ActiveBundle based on computed values from progress.
 */
public class ActiveSeekBarListeners {
    private static abstract class StubSeekBarListener implements SeekBar.OnSeekBarChangeListener {
        protected final ActiveBundle mActiveBundle;
        protected final String mKey;

        public StubSeekBarListener(ActiveBundle activeBundle, String key) {
            mActiveBundle = activeBundle;
            mKey = key;
        }

        @Override
        public void onStartTrackingTouch(SeekBar seekBar) {

        }

        @Override
        public void onStopTrackingTouch(SeekBar seekBar) {

        }
    }

    public static abstract class FloatSeekBarListener extends
            StubSeekBarListener {
        public FloatSeekBarListener(ActiveBundle activeBundle, String key) {
            super(activeBundle, key);
        }

        @Override
        public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
            mActiveBundle.changeFloat(mKey, computeValueFromProgress(progress, seekBar.getMax()));
        }

        protected abstract float computeValueFromProgress(int progress, int max);
    }

    public static abstract class IntSeekBarListener extends
            StubSeekBarListener {
        public IntSeekBarListener(ActiveBundle activeBundle, String key) {
            super(activeBundle, key);
        }

        @Override
        public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
            mActiveBundle.changeInt(mKey, computeValueFromProgress(progress, seekBar.getMax()));
        }

        protected abstract int computeValueFromProgress(int progress, int max);
    }
}
