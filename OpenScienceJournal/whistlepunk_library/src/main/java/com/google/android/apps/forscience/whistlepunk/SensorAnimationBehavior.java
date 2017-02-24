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

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LevelListDrawable;
import android.support.annotation.IntDef;
import android.widget.ImageView;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.List;

/**
 * Defines sensor animation behavior for sensor cards.
 */
public class SensorAnimationBehavior {

    // The different types of behavior for the sensor animation.
    @IntDef({TYPE_STATIC_ICON, TYPE_ACCELEROMETER_SCALE, TYPE_RELATIVE_SCALE,
            TYPE_POSITIVE_RELATIVE_SCALE, TYPE_ROTATION})
    @Retention(RetentionPolicy.SOURCE)
    public @interface BehaviorType {}

    public static final int TYPE_STATIC_ICON = 1;
    public static final int TYPE_ACCELEROMETER_SCALE = 2;
    public static final int TYPE_RELATIVE_SCALE = 3;
    public static final int TYPE_POSITIVE_RELATIVE_SCALE = 4;
    public static final int TYPE_ROTATION = 5;

    private final int mBehaviorType;
    private int mLevelDrawableId;
    private LevelListDrawable mLevelDrawable;

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

    public void updateImageView(ImageView view, double newValue, double yMin, double yMax) {
        if (mBehaviorType == TYPE_ROTATION) {
            view.setRotation((float) newValue);
            view.setImageLevel(0);
        } else {
            view.setRotation(0.0f);
            view.setImageLevel(getUpdatedLevel(newValue, yMin, yMax));
        }
    }

    public void resetImageView(ImageView view) {
        view.setImageLevel(0);
        view.setRotation(0.0f);
    }

    private int getUpdatedLevel(double newValue, double yMin, double yMax) {
        int index;
        if (mBehaviorType == TYPE_STATIC_ICON) {
            index = 0;
        } else if (mBehaviorType == TYPE_ACCELEROMETER_SCALE) {
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

    public Drawable getLevelDrawable(Context context) {
        if (mLevelDrawable == null) {
            mLevelDrawable = (LevelListDrawable)
                    context.getResources().getDrawable(mLevelDrawableId);
            mLevelDrawable.setLevel(0);
        }
        return mLevelDrawable;
    }
}
