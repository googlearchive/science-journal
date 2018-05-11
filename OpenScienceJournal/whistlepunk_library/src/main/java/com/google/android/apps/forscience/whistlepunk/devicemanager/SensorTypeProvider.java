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

package com.google.android.apps.forscience.whistlepunk.devicemanager;

import android.content.Context;
import androidx.annotation.IntDef;
import com.google.android.apps.forscience.whistlepunk.ExternalSensorAppearance;
import com.google.android.apps.forscience.whistlepunk.ImageViewSensorAnimationBehavior;
import com.google.android.apps.forscience.whistlepunk.R;
import com.google.android.apps.forscience.whistlepunk.SensorAppearance;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

public class SensorTypeProvider {

  // The different types of sensor.
  @IntDef({TYPE_ROTATION, TYPE_CUSTOM, TYPE_RAW})
  @Retention(RetentionPolicy.SOURCE)
  public @interface SensorKind {}

  public static final int TYPE_ROTATION = 1;
  public static final int TYPE_CUSTOM = 2;
  public static final int TYPE_RAW = 3;

  public static SensorAppearance getSensorAppearance(@SensorKind int kind, final String name) {
    if (kind == SensorTypeProvider.TYPE_ROTATION) {
      return new ExternalSensorAppearance(
          R.string.sensor_rotation_rpm,
          R.drawable.ic_rotation_white_24dp,
          R.string.rpm_units,
          R.string.sensor_desc_short_rotation,
          R.string.sensor_desc_first_paragraph_rotation,
          R.string.sensor_desc_second_paragraph_rotation,
          R.drawable.learnmore_rotation,
          new ImageViewSensorAnimationBehavior(
              R.drawable.rotation_level_drawable,
              ImageViewSensorAnimationBehavior.TYPE_POSITIVE_RELATIVE_SCALE),
          name,
          kind);
    } else if (kind == SensorTypeProvider.TYPE_RAW) {
      // TODO(dek): switch icon to voltage when ready.
      // b/27226547
      return new ExternalSensorAppearance(
          R.string.sensor_raw,
          R.drawable.ic_sensor_raw_white_24dp,
          R.string.raw_units,
          R.string.sensor_desc_short_raw,
          R.string.sensor_desc_first_paragraph_raw,
          R.string.sensor_desc_second_paragraph_raw,
          R.drawable.artboard,
          new ImageViewSensorAnimationBehavior(
              R.drawable.percent_level_drawable,
              ImageViewSensorAnimationBehavior.TYPE_RELATIVE_SCALE),
          name,
          kind);
    } else {
      // Handle appearance for custom sensors.
      return new ExternalSensorAppearance(
          R.string.sensor_custom,
          R.drawable.ic_bluetooth_white_24dp, /* units */
          0,
          R.string.sensor_desc_short_bluetooth,
          R.string.sensor_desc_first_paragraph_unknown_bluetooth,
          R.string.sensor_desc_second_paragraph_unknown_bluetooth,
          R.drawable.learnmore_bluetooth,
          new ImageViewSensorAnimationBehavior(
              R.drawable.bluetooth_level_drawable,
              ImageViewSensorAnimationBehavior.TYPE_RELATIVE_SCALE),
          name,
          kind);
    }
  }

  SensorType[] sensors;

  public SensorTypeProvider(Context context) {
    sensors =
        new SensorType[] {
          new SensorType(
              context,
              TYPE_ROTATION,
              R.string.sensor_rotation_rpm,
              R.drawable.ic_rotation_white_24dp,
              false),
          new SensorType(
              context, TYPE_RAW, R.string.sensor_raw, R.drawable.ic_sensor_raw_white_24dp, false),
          new SensorType(
              context,
              TYPE_CUSTOM,
              R.string.sensor_custom,
              R.drawable.ic_bluetooth_white_24dp,
              true)
        };
  }

  public class SensorType {
    private String label;
    private boolean custom;
    private int drawableId;
    private @SensorKind int kind;

    SensorType(
        Context context, @SensorKind int sensorKind, int nameId, int drawableId, boolean custom) {
      kind = sensorKind;
      label = context.getResources().getString(nameId);
      this.custom = custom;
      this.drawableId = drawableId;
    }

    public boolean isCustom() {
      return custom;
    }

    @Override
    public String toString() {
      return label;
    }

    public int getDrawableId() {
      return drawableId;
    }

    public @SensorKind int getSensorKind() {
      return kind;
    }
  }

  public SensorType[] getSensors() {
    return sensors;
  }
}
