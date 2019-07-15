/*
 *  Copyright 2019 Google Inc. All Rights Reserved.
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

import static com.google.android.apps.forscience.whistlepunk.sensors.MkrSciBleSensor.HANDLER_LIGHT;
import static com.google.android.apps.forscience.whistlepunk.sensors.MkrSciBleSensor.HANDLER_TEMPERATURE_CELSIUS;
import static com.google.android.apps.forscience.whistlepunk.sensors.MkrSciBleSensor.HANDLER_TEMPERATURE_FAHRENHEIT;
import static com.google.android.apps.forscience.whistlepunk.sensors.MkrSciBleSensor.SENSOR_ACCELEROMETER_X;
import static com.google.android.apps.forscience.whistlepunk.sensors.MkrSciBleSensor.SENSOR_ACCELEROMETER_Y;
import static com.google.android.apps.forscience.whistlepunk.sensors.MkrSciBleSensor.SENSOR_ACCELEROMETER_Z;
import static com.google.android.apps.forscience.whistlepunk.sensors.MkrSciBleSensor.SENSOR_CURRENT;
import static com.google.android.apps.forscience.whistlepunk.sensors.MkrSciBleSensor.SENSOR_GYROSCOPE_X;
import static com.google.android.apps.forscience.whistlepunk.sensors.MkrSciBleSensor.SENSOR_GYROSCOPE_Y;
import static com.google.android.apps.forscience.whistlepunk.sensors.MkrSciBleSensor.SENSOR_GYROSCOPE_Z;
import static com.google.android.apps.forscience.whistlepunk.sensors.MkrSciBleSensor.SENSOR_INPUT_1;
import static com.google.android.apps.forscience.whistlepunk.sensors.MkrSciBleSensor.SENSOR_INPUT_2;
import static com.google.android.apps.forscience.whistlepunk.sensors.MkrSciBleSensor.SENSOR_INPUT_3;
import static com.google.android.apps.forscience.whistlepunk.sensors.MkrSciBleSensor.SENSOR_LINEAR_ACCELEROMETER;
import static com.google.android.apps.forscience.whistlepunk.sensors.MkrSciBleSensor.SENSOR_MAGNETOMETER;
import static com.google.android.apps.forscience.whistlepunk.sensors.MkrSciBleSensor.SENSOR_RESISTANCE;
import static com.google.android.apps.forscience.whistlepunk.sensors.MkrSciBleSensor.SENSOR_VOLTAGE;

import com.google.android.apps.forscience.whistlepunk.data.GoosciIcon;
import com.google.android.apps.forscience.whistlepunk.data.GoosciIcon.IconPath.PathType;
import com.google.common.base.Strings;
import java.util.Objects;

/** Subclass of {@link BuiltInSensorAppearance} for MkrSciBle sensors. */
public class MkrSciBleSensorAppearance extends BuiltInSensorAppearance {

  public static SensorAppearance get(String sensorId, String handlerId) {
    if (Objects.equals(sensorId, SENSOR_INPUT_1)) {
      if (Objects.equals(handlerId, HANDLER_TEMPERATURE_CELSIUS)) {
        return new MkrSciBleSensorAppearance(
            sensorId,
            handlerId,
            R.string.input_1, // name
            R.drawable.ic_sensor_mkrsci_temperature_white_24dp, // icon
            R.string.temperature_c_units, // units
            0, // desc short
            0, // desc extended 1st part
            0, // desc extended 2nd part
            0, // desc extended image
            new ImageViewSensorAnimationBehavior(
                R.drawable.mkrsci_temperature_level_drawable,
                ImageViewSensorAnimationBehavior.TYPE_RELATIVE_SCALE), // animation
            2, // points after decimal
            null // sensor id
            );
      }
      if (Objects.equals(handlerId, HANDLER_TEMPERATURE_FAHRENHEIT)) {
        return new MkrSciBleSensorAppearance(
            sensorId,
            handlerId,
            R.string.input_1, // name
            R.drawable.ic_sensor_mkrsci_temperature_white_24dp, // icon
            R.string.temperature_f_units, // units
            0, // desc short
            0, // desc extended 1st part
            0, // desc extended 2nd part
            0, // desc extended image
            new ImageViewSensorAnimationBehavior(
                R.drawable.mkrsci_temperature_level_drawable,
                ImageViewSensorAnimationBehavior.TYPE_RELATIVE_SCALE), // animation
            2, // points after decimal
            null // sensor id
            );
      }
      if (Objects.equals(handlerId, HANDLER_LIGHT)) {
        return new MkrSciBleSensorAppearance(
            sensorId,
            handlerId,
            R.string.input_1, // name
            R.drawable.ic_sensor_mkrsci_light_white_24dp, // icon
            R.string.ambient_light_units, // units
            0, // desc short
            0, // desc extended 1st part
            0, // desc extended 2nd part
            0, // desc extended image
            new ImageViewSensorAnimationBehavior(
                R.drawable.mkrsci_light_level_drawable,
                ImageViewSensorAnimationBehavior.TYPE_RELATIVE_SCALE),
            0, // points after decimal
            null // sensor id
            );
      }
      return new MkrSciBleSensorAppearance(
          sensorId,
          handlerId,
          R.string.input_1, // name
          R.drawable.ic_sensor_mkrsci_input_1_white_24dp, // icon
          0, // units
          0, // desc short
          0, // desc extended 1st part
          0, // desc extended 2nd part
          0, // desc extended image
          new ImageViewSensorAnimationBehavior(
              R.drawable.mkrsci_level_drawable,
              ImageViewSensorAnimationBehavior.TYPE_RELATIVE_SCALE), // animation
          0, // points after decimal
          null // sensor id
          );
    }
    if (Objects.equals(sensorId, SENSOR_INPUT_2)) {
      if (Objects.equals(handlerId, HANDLER_LIGHT)) {
        return new MkrSciBleSensorAppearance(
            sensorId,
            handlerId,
            R.string.input_2, // name
            R.drawable.ic_sensor_mkrsci_light_white_24dp, // icon
            R.string.ambient_light_units, // units
            0, // desc short
            0, // desc extended 1st part
            0, // desc extended 2nd part
            0, // desc extended image
            new ImageViewSensorAnimationBehavior(
                R.drawable.mkrsci_light_level_drawable,
                ImageViewSensorAnimationBehavior.TYPE_RELATIVE_SCALE),
            0, // points after decimal
            null // sensor id
            );
      }
      return new MkrSciBleSensorAppearance(
          sensorId,
          handlerId,
          R.string.input_2, // name
          R.drawable.ic_sensor_mkrsci_input_2_white_24dp, // icon
          0, // units
          0, // desc short
          0, // desc extended 1st part
          0, // desc extended 2nd part
          0, // desc extended image
          new ImageViewSensorAnimationBehavior(
              R.drawable.mkrsci_level_drawable,
              ImageViewSensorAnimationBehavior.TYPE_RELATIVE_SCALE), // animation
          0, // points after decimal
          null // sensor id
          );
    }
    if (Objects.equals(sensorId, SENSOR_INPUT_3)) {
      return new MkrSciBleSensorAppearance(
          sensorId,
          handlerId,
          R.string.input_3, // name
          R.drawable.ic_sensor_mkrsci_input_3_white_24dp, // icon
          0, // units
          0, // desc short
          0, // desc extended 1st part
          0, // desc extended 2nd part
          0, // desc extended image
          new ImageViewSensorAnimationBehavior(
              R.drawable.mkrsci_level_drawable,
              ImageViewSensorAnimationBehavior.TYPE_RELATIVE_SCALE), // animation
          0, // points after decimal
          null // sensor id
          );
    }
    if (Objects.equals(sensorId, SENSOR_VOLTAGE)) {
      return new MkrSciBleSensorAppearance(
          sensorId,
          handlerId,
          R.string.voltage, // name
          R.drawable.ic_sensor_mkrsci_voltage_white_24dp, // icon
          R.string.voltage_units, // units
          R.string.sensor_desc_short_mkrsci_voltage, // desc short
          0, // desc extended 1st part
          0, // desc extended 2nd part
          0, // desc extended image
          new ImageViewSensorAnimationBehavior(
              R.drawable.mkrsci_voltage_level_drawable,
              ImageViewSensorAnimationBehavior.TYPE_RELATIVE_SCALE), // animation
          2, // points after decimal
          null // sensor id
          );
    }
    if (Objects.equals(sensorId, SENSOR_CURRENT)) {
      return new MkrSciBleSensorAppearance(
          sensorId,
          handlerId,
          R.string.current, // name
          R.drawable.ic_sensor_mkrsci_current_white_24dp, // icon
          R.string.current_units, // units
          R.string.sensor_desc_short_mkrsci_current, // desc short
          0, // desc extended 1st part
          0, // desc extended 2nd part
          0, // desc extended image
          new ImageViewSensorAnimationBehavior(
              R.drawable.mkrsci_current_level_drawable,
              ImageViewSensorAnimationBehavior.TYPE_RELATIVE_SCALE), // animation
          2, // points after decimal
          null // sensor id
          );
    }
    if (Objects.equals(sensorId, SENSOR_RESISTANCE)) {
      return new MkrSciBleSensorAppearance(
          sensorId,
          handlerId,
          R.string.resistance, // name
          R.drawable.ic_sensor_mkrsci_resistance_white_24dp, // icon
          R.string.resistance_units, // units
          R.string.sensor_desc_short_mkrsci_resistance, // desc short
          0, // desc extended 1st part
          0, // desc extended 2nd part
          0, // desc extended image
          new ImageViewSensorAnimationBehavior(
              R.drawable.mkrsci_resistance_level_drawable,
              ImageViewSensorAnimationBehavior.TYPE_POSITIVE_RELATIVE_SCALE), // animation
          2, // BuiltInSensorAppearance.MAX_POINTS_AFTER_DECIMAL, // points after decimal
          null // sensor id
          );
    }
    if (Objects.equals(sensorId, SENSOR_ACCELEROMETER_X)) {
      return new MkrSciBleSensorAppearance(
          sensorId,
          handlerId,
          R.string.acc_x,
          R.drawable.ic_sensor_mkrsci_acc_x_white_24dp,
          R.string.acc_units,
          R.string.sensor_desc_short_mkrsci_acc,
          0,
          0,
          0,
          new ImageViewSensorAnimationBehavior(
              R.drawable.mkrsci_accx_level_drawable,
              ImageViewSensorAnimationBehavior.TYPE_ACCELEROMETER_SCALE_ROTATES),
          2,
          null);
    }
    if (Objects.equals(sensorId, SENSOR_ACCELEROMETER_Y)) {
      return new MkrSciBleSensorAppearance(
          sensorId,
          handlerId,
          R.string.acc_y,
          R.drawable.ic_sensor_mkrsci_acc_y_white_24dp,
          R.string.acc_units,
          R.string.sensor_desc_short_mkrsci_acc,
          0,
          0,
          0,
          new ImageViewSensorAnimationBehavior(
              R.drawable.mkrsci_accy_level_drawable,
              ImageViewSensorAnimationBehavior.TYPE_ACCELEROMETER_SCALE_ROTATES),
          2,
          null);
    }
    if (Objects.equals(sensorId, SENSOR_ACCELEROMETER_Z)) {
      return new MkrSciBleSensorAppearance(
          sensorId,
          handlerId,
          R.string.acc_z,
          R.drawable.ic_sensor_mkrsci_acc_z_white_24dp,
          R.string.acc_units,
          R.string.sensor_desc_short_mkrsci_acc,
          0,
          0,
          0,
          new ImageViewSensorAnimationBehavior(
              R.drawable.mkrsci_accz_level_drawable,
              ImageViewSensorAnimationBehavior.TYPE_ACCELEROMETER_SCALE_ROTATES),
          2,
          null);
    }
    if (Objects.equals(sensorId, SENSOR_LINEAR_ACCELEROMETER)) {
      return new MkrSciBleSensorAppearance(
          sensorId,
          handlerId,
          R.string.linear_accelerometer,
          R.drawable.ic_sensor_mkrsci_acc_linear_white_24dp,
          R.string.acc_units,
          R.string.sensor_desc_short_mkrsci_acc,
          0,
          0,
          0,
          new ImageViewSensorAnimationBehavior(
              R.drawable.mkrsci_acclin_level_drawable,
              ImageViewSensorAnimationBehavior.TYPE_POSITIVE_RELATIVE_SCALE),
          2,
          null);
    }
    if (Objects.equals(sensorId, SENSOR_GYROSCOPE_X)) {
      return new MkrSciBleSensorAppearance(
          sensorId,
          handlerId,
          R.string.gyr_x, // name
          R.drawable.ic_sensor_mkrsci_gyr_x_white_24dp, // icon
          R.string.gyr_units, // units
          R.string.sensor_desc_short_mkrsci_gyr, // desc short
          0, // desc extended 1st part
          0, // desc extended 2nd part
          0, // desc extended image
          new ImageViewSensorAnimationBehavior(
              R.drawable.mkrsci_gyrx_level_drawable,
              ImageViewSensorAnimationBehavior.TYPE_RELATIVE_SCALE), // animation
          2, // points after decimal
          null // sensor id
          );
    }
    if (Objects.equals(sensorId, SENSOR_GYROSCOPE_Y)) {
      return new MkrSciBleSensorAppearance(
          sensorId,
          handlerId,
          R.string.gyr_y, // name
          R.drawable.ic_sensor_mkrsci_gyr_y_white_24dp, // icon
          R.string.gyr_units, // units
          R.string.sensor_desc_short_mkrsci_gyr, // desc short
          0, // desc extended 1st part
          0, // desc extended 2nd part
          0, // desc extended image
          new ImageViewSensorAnimationBehavior(
              R.drawable.mkrsci_gyry_level_drawable,
              ImageViewSensorAnimationBehavior.TYPE_RELATIVE_SCALE), // animation
          2, // points after decimal
          null // sensor id
          );
    }
    if (Objects.equals(sensorId, SENSOR_GYROSCOPE_Z)) {
      return new MkrSciBleSensorAppearance(
          sensorId,
          handlerId,
          R.string.gyr_z, // name
          R.drawable.ic_sensor_mkrsci_gyr_z_white_24dp, // icon
          R.string.gyr_units, // units
          R.string.sensor_desc_short_mkrsci_gyr, // desc short
          0, // desc extended 1st part
          0, // desc extended 2nd part
          0, // desc extended image
          new ImageViewSensorAnimationBehavior(
              R.drawable.mkrsci_gyrz_level_drawable,
              ImageViewSensorAnimationBehavior.TYPE_RELATIVE_SCALE), // animation
          2, // points after decimal
          null // sensor id
          );
    }
    if (Objects.equals(sensorId, SENSOR_MAGNETOMETER)) {
      return new MkrSciBleSensorAppearance(
          sensorId,
          handlerId,
          R.string.magnetic_field_strength,
          R.drawable.ic_sensor_mkrsci_magnetometer_white_24dp,
          R.string.magnetic_strength_units,
          R.string.sensor_desc_short_mkrsci_magnetometer, // desc short
          0,
          0,
          0,
          new ImageViewSensorAnimationBehavior(
              R.drawable.mkrsci_magnetometer_level_drawable,
              ImageViewSensorAnimationBehavior.TYPE_POSITIVE_RELATIVE_SCALE),
          2,
          null);
    }
    return null;
  }

  public static SensorAppearance get(String path) {
    final String sensorId;
    final String handlerId;
    final int sep = path.indexOf(':');
    if (sep == -1) {
      sensorId = path;
      handlerId = "";
    } else {
      sensorId = path.substring(0, sep);
      handlerId = path.substring(sep + 1);
    }
    return get(sensorId, handlerId);
  }

  private String sensorId;

  private String handlerId;

  private MkrSciBleSensorAppearance(
      String sensorId,
      String handlerId,
      int nameStringId,
      int drawableId,
      int unitsStringId,
      int shortDescriptionId,
      int firstParagraphStringId,
      int secondParagraphStringId,
      int infoDrawableId,
      SensorAnimationBehavior sensorAnimationBehavior,
      int pointsAfterDecimalInNumberFormat,
      String builtInSensorId) {
    super(
        nameStringId,
        drawableId,
        unitsStringId,
        shortDescriptionId,
        firstParagraphStringId,
        secondParagraphStringId,
        infoDrawableId,
        sensorAnimationBehavior,
        pointsAfterDecimalInNumberFormat,
        builtInSensorId);
    this.sensorId = sensorId;
    this.handlerId = handlerId;
  }

  @Override
  public GoosciIcon.IconPath getSmallIconPath() {
    return GoosciIcon.IconPath.newBuilder()
        .setType(PathType.MKRSCI_ANDROID_BLE)
        .setPathString(sensorId + ":" + Strings.nullToEmpty(handlerId))
        .build();
  }

  @Override
  public GoosciIcon.IconPath getLargeIconPath() {
    return GoosciIcon.IconPath.newBuilder()
        .setType(PathType.MKRSCI_ANDROID_BLE)
        .setPathString(sensorId + ":" + Strings.nullToEmpty(handlerId))
        .build();
  }
}
