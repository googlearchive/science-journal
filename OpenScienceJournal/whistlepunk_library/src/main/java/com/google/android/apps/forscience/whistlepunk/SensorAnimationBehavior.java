/*
 *  Copyright 2016 Google Inc. All Rights Reserved.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package com.google.android.apps.forscience.whistlepunk;

import static com.google.android.apps.forscience.whistlepunk.audio.SoundUtils.HALF_STEP_FREQUENCY_RATIO;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LevelListDrawable;
import androidx.annotation.IntDef;
import android.view.Surface;
import android.widget.ImageView;

import com.google.android.apps.forscience.whistlepunk.audio.SoundUtils;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Defines sensor animation behavior for sensor cards.
 */
public class SensorAnimationBehavior {

    // The different types of behavior for the sensor animation.
    @IntDef({TYPE_STATIC_ICON, TYPE_ACCELEROMETER_SCALE, TYPE_RELATIVE_SCALE,
            TYPE_POSITIVE_RELATIVE_SCALE, TYPE_ROTATION, TYPE_ACCELEROMETER_SCALE_ROTATES,
            TYPE_PITCH})
    @Retention(RetentionPolicy.SOURCE)
    public @interface BehaviorType {
    }

    public static final int TYPE_STATIC_ICON = 1;
    public static final int TYPE_ACCELEROMETER_SCALE = 2;
    public static final int TYPE_RELATIVE_SCALE = 3;
    public static final int TYPE_POSITIVE_RELATIVE_SCALE = 4;
    public static final int TYPE_ROTATION = 5;
    public static final int TYPE_ACCELEROMETER_SCALE_ROTATES = 6;
    public static final int TYPE_PITCH = 7;

    /**
     * noteFrequencies contains the frequencies of notes of a piano at indices 1-88.
     * The value at index 0 is a half step less than the value at index 1.
     * The value at index 89 is a half step more than the value at index 88.
     */
    private static final List<Double> noteFrequencies = new ArrayList<Double>();

    private final int mBehaviorType;
    private int mLevelDrawableId;

    public static SensorAnimationBehavior createDefault() {
        // TODO: Replace bluetooth_level_drawable with a default sensor icon from UX.
        return new SensorAnimationBehavior(R.drawable.bluetooth_level_drawable,
                SensorAnimationBehavior.TYPE_STATIC_ICON);
    }

    // For now, assume that drawableIds is size 1 for static, size 5 for accelerometer, size 4
    // for relative scale, or size 1 for rotation.
    public SensorAnimationBehavior(int levelDrawableId, @BehaviorType int behaviorType) {
        mBehaviorType = behaviorType;
        mLevelDrawableId = levelDrawableId;
    }

    public void updateImageView(ImageView view, double newValue, double yMin, double yMax,
            int screenOrientation) {
        if (mBehaviorType == TYPE_ROTATION) {
            float delta = getScreenRotationDelta(screenOrientation);
            view.setRotation((float) (-1.0 * (newValue + delta)));
            view.setImageLevel(0);
        } else {
            if (mBehaviorType == TYPE_ACCELEROMETER_SCALE_ROTATES) {
                float delta = getScreenRotationDelta(screenOrientation);
                view.setRotation(-1 * delta);
            } else {
                view.setRotation(0.0f);
            }
            view.setImageLevel(getUpdatedLevel(newValue, yMin, yMax));
        }
    }

    private void resetImageView(ImageView view) {
        view.setImageLevel(0);
        view.setRotation(0.0f);
    }

    private int getUpdatedLevel(double newValue, double yMin, double yMax) {
        int index;
        if (mBehaviorType == TYPE_STATIC_ICON) {
            index = 0;
        } else if (mBehaviorType == TYPE_ACCELEROMETER_SCALE ||
                   mBehaviorType == TYPE_ACCELEROMETER_SCALE_ROTATES) {
            if (newValue > 3.0) {
                index = 4;
            } else if (newValue > 0.5) {
                index = 3;
            } else if (newValue > -0.5) {
                index = 2;
            } else if (newValue > -3) {
                index = 1;
            } else {
                index = 0;
            }
        } else if (mBehaviorType == TYPE_PITCH) {
            index = pitchToLevel(newValue);
        } else {
            double scaled;
            if (mBehaviorType == TYPE_POSITIVE_RELATIVE_SCALE) {
                double minVal = Math.max(yMin, 0);
                scaled = (newValue - minVal) / (yMax - minVal);
            } else {
                // Assume mBehaviorType == TYPE_RELATIVE_SCALE
                scaled = (newValue - yMin) / (yMax - yMin);
            }
            if (scaled > .75) {
                index = 3;
            } else if (scaled > .5) {
                index = 2;
            } else if (scaled > .25) {
                index = 1;
            } else {
                index = 0;
            }
        }
        return index;
    }

    private float getScreenRotationDelta(int screenOrientation) {
        float delta = 0;
        // Use screen orientation to make sure we point the icon the right way even if the
        // screen is rotated.
        // The data itself is not changed when the screen is rotated: We always report angle
        // along the long axis of the phone. However, if we do not rotate the image here, it
        // will appear off by 90/180/270 deg.
        if (screenOrientation == Surface.ROTATION_90) {
            delta = 90;
        } else if (screenOrientation == Surface.ROTATION_180) {
            delta = 180;
        } else if (screenOrientation == Surface.ROTATION_270) {
            delta = 270;
        }
        return delta;
    }

    private Drawable getLevelDrawable(Context context) {
        LevelListDrawable drawable =
                (LevelListDrawable) context.getResources().getDrawable(mLevelDrawableId);
        drawable.setLevel(0);
        return drawable;
    }

    void initializeLargeIcon(ImageView largeIcon) {
        largeIcon.setImageDrawable(getLevelDrawable(largeIcon.getContext()));
        largeIcon.setRotation(0.0f);
        // Icon level depends on type -- we want to pick something in the middle to look reasonable.
        if (mBehaviorType == TYPE_ACCELEROMETER_SCALE ||
                mBehaviorType == TYPE_ACCELEROMETER_SCALE_ROTATES) {
            // Pick the middle icon
            largeIcon.setImageLevel(2);
        } else if (mBehaviorType == TYPE_POSITIVE_RELATIVE_SCALE ||
                mBehaviorType == TYPE_RELATIVE_SCALE) {
            // Pick the most exciting icon (the biggest value represented)
            largeIcon.setImageLevel(3);
        }
    }

    private static void fillNoteFrequencies() {
        noteFrequencies.addAll(SoundUtils.getPianoNoteFrequencies());
        // Add first and last items to make lookup easier. Use the approximate half-step ratio to
        // determine the first and last items.
        noteFrequencies.add(0, noteFrequencies.get(0) / HALF_STEP_FREQUENCY_RATIO);
        noteFrequencies.add(
                noteFrequencies.get(noteFrequencies.size() - 1) * HALF_STEP_FREQUENCY_RATIO);
    }

    /**
     * Returns the index corresponding to the given sound frequency, where indices 1-88 represent
     * the notes of keys on a piano.
     */
    private static int pitchToLevel(double frequency) {
        if (noteFrequencies.isEmpty()) {
            fillNoteFrequencies();
        }
        int i = Collections.binarySearch(noteFrequencies, frequency);
        // If there is an exact match, i will be a non-negative number, which is the index of the
        // matching value.
        if (i >= 0) {
            return i;
        }
        // If there is no exact match, i will provide the insertion point, where the observed
        // frequency would belong in the list, as (-(insertion point) - 1).
        // This is the usual case, since in most cases the observed frequency will not match a
        // specific note exactly.
         // Calculate the insertion point
        i = -i - 1;
        if (i == 0) {
            // frequency is significantly lower than the lowest note.
            return 0;
        }
        if (i == noteFrequencies.size()) {
            // frequency is significantly higher than the highest note.
            return noteFrequencies.size() - 1;
        }
        // frequency is between two notes.
        double midpoint = (noteFrequencies.get(i - 1) + noteFrequencies.get(i)) / 2;
        return (frequency < midpoint) ? (i - 1) : i;
    }
}
