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
import androidx.annotation.IntDef;
import androidx.annotation.Nullable;
import android.view.Surface;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.RelativeLayout.LayoutParams;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/** Implements sensor animation behavior for sensor cards using an ImageView. */
public class ImageViewSensorAnimationBehavior implements SensorAnimationBehavior {

  // The different types of behavior for the sensor animation.
  @IntDef({
    TYPE_STATIC_ICON,
    TYPE_ACCELEROMETER_SCALE,
    TYPE_RELATIVE_SCALE,
    TYPE_POSITIVE_RELATIVE_SCALE,
    TYPE_ROTATION,
    TYPE_ACCELEROMETER_SCALE_ROTATES
  })
  @Retention(RetentionPolicy.SOURCE)
  public @interface BehaviorType {}

  public static final int TYPE_STATIC_ICON = 1;
  public static final int TYPE_ACCELEROMETER_SCALE = 2;
  public static final int TYPE_RELATIVE_SCALE = 3;
  public static final int TYPE_POSITIVE_RELATIVE_SCALE = 4;
  public static final int TYPE_ROTATION = 5;
  public static final int TYPE_ACCELEROMETER_SCALE_ROTATES = 6;

  private final int behaviorType;
  private int levelDrawableId;

  public static ImageViewSensorAnimationBehavior createDefault() {
    // TODO: Replace bluetooth_level_drawable with a default sensor icon from UX.
    return new ImageViewSensorAnimationBehavior(
        R.drawable.generic_sensor_level_drawable,
        ImageViewSensorAnimationBehavior.TYPE_STATIC_ICON);
  }

  // For now, assume that drawableIds is size 1 for static, size 5 for accelerometer, size 4
  // for relative scale, or size 1 for rotation.
  public ImageViewSensorAnimationBehavior(int levelDrawableId, @BehaviorType int behaviorType) {
    this.behaviorType = behaviorType;
    this.levelDrawableId = levelDrawableId;
  }

  private ImageView getImageView(RelativeLayout layout) {
    return (ImageView) layout.getChildAt(0);
  }

  @Override
  public void resetIcon(RelativeLayout layout) {
    ImageView view = getImageView(layout);
    view.setImageLevel(0);
  }

  @Override
  public boolean updateIconAndTextTogether() {
    return false;
  }

  @Override
  public void updateIcon(
      RelativeLayout layout, double newValue, double yMin, double yMax, int screenOrientation) {
    ImageView view = getImageView(layout);
    if (behaviorType == TYPE_ROTATION) {
      float delta = getScreenRotationDelta(screenOrientation);
      view.setRotation((float) (-1.0 * (newValue + delta)));
      view.setImageLevel(0);
    } else {
      if (behaviorType == TYPE_ACCELEROMETER_SCALE_ROTATES) {
        float delta = getScreenRotationDelta(screenOrientation);
        view.setRotation(-1 * delta);
      } else {
        view.setRotation(0.0f);
      }
      view.setImageLevel(getUpdatedLevel(newValue, yMin, yMax));
    }
  }

  private int getUpdatedLevel(double newValue, double yMin, double yMax) {
    int index;
    if (behaviorType == TYPE_STATIC_ICON) {
      index = 0;
    } else if (behaviorType == TYPE_ACCELEROMETER_SCALE
        || behaviorType == TYPE_ACCELEROMETER_SCALE_ROTATES) {
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
      if (behaviorType == TYPE_POSITIVE_RELATIVE_SCALE) {
        double minVal = Math.max(yMin, 0);
        scaled = (newValue - minVal) / (yMax - minVal);
      } else {
        // Assume behaviorType == TYPE_RELATIVE_SCALE
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
        (LevelListDrawable) context.getResources().getDrawable(levelDrawableId);
    drawable.setLevel(0);
    return drawable;
  }

  @Override
  public void initializeLargeIcon(RelativeLayout layout, @Nullable Double value) {
    // Remove previous views.
    if (layout.getChildCount() > 0) {
      layout.removeAllViews();
    }
    ImageView largeIcon = new ImageView(layout.getContext());
    layout.addView(
        largeIcon, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
    largeIcon.setImageDrawable(getLevelDrawable(largeIcon.getContext()));
    largeIcon.setRotation(0.0f);
    // Icon level depends on type -- we want to pick something in the middle to look reasonable.
    if (behaviorType == TYPE_ACCELEROMETER_SCALE
        || behaviorType == TYPE_ACCELEROMETER_SCALE_ROTATES) {
      // Pick the middle icon
      largeIcon.setImageLevel(2);
    } else if (behaviorType == TYPE_POSITIVE_RELATIVE_SCALE
        || behaviorType == TYPE_RELATIVE_SCALE) {
      // Pick the most exciting icon (the biggest value represented)
      largeIcon.setImageLevel(3);
    }
  }
}
