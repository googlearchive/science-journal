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
import com.google.android.apps.forscience.javalib.MaybeConsumer;
import com.google.android.apps.forscience.javalib.Success;
import com.google.android.apps.forscience.whistlepunk.data.GoosciSensorAppearance;
import com.google.android.apps.forscience.whistlepunk.metadata.ExternalSensorSpec;
import com.google.android.apps.forscience.whistlepunk.sensors.AccelerometerSensor;
import com.google.android.apps.forscience.whistlepunk.sensors.AmbientLightSensor;
import com.google.android.apps.forscience.whistlepunk.sensors.AmbientTemperatureSensor;
import com.google.android.apps.forscience.whistlepunk.sensors.BarometerSensor;
import com.google.android.apps.forscience.whistlepunk.sensors.CompassSensor;
import com.google.android.apps.forscience.whistlepunk.sensors.DecibelSensor;
import com.google.android.apps.forscience.whistlepunk.sensors.LinearAccelerometerSensor;
import com.google.android.apps.forscience.whistlepunk.sensors.MagneticStrengthSensor;
import com.google.android.apps.forscience.whistlepunk.sensors.PitchSensor;
import com.google.android.apps.forscience.whistlepunk.sensors.SineWavePseudoSensor;
import com.google.android.apps.forscience.whistlepunk.sensors.VelocitySensor;
import com.google.common.base.Preconditions;
import java.text.FieldPosition;
import java.text.NumberFormat;
import java.text.ParsePosition;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class SensorAppearanceProviderImpl implements SensorAppearanceProvider {
  private static final String TAG = "SensorAppearance";

  private static final SensorAppearance UNKNOWN_SENSOR_APPEARANCE =
      new BuiltInSensorAppearance(R.string.unknown_sensor, R.drawable.generic_sensor_white, null);

  public static final int DEFAULT_POINTS_AFTER_DECIMAL = -1;

  // Don't allow more than 10 places after the decimal to be displayed. The UX can't
  // handle this very well.
  // TODO: Revisit this constant -- should it be even smaller, like 5?
  public static final int MAX_POINTS_AFTER_DECIMAL = 10;

  private Map<String, SensorAppearance> appearances = new HashMap<>();

  private DataController dataController;

  public static GoosciSensorAppearance.BasicSensorAppearance toProto(
      SensorAppearance appearance, Context context) {
    return GoosciSensorAppearance.BasicSensorAppearance.newBuilder()
        .setName(appearance.getName(context))
        .setLocale(getLanguageTag(context))
        .setIconPath(appearance.getSmallIconPath())
        .setLargeIconPath(appearance.getLargeIconPath())
        .setUnits(appearance.getUnits(context))
        .setShortDescription(appearance.getShortDescription(context))
        .setPointsAfterDecimal(appearance.getPointsAfterDecimal())
        .build();
  }

  private static String getLanguageTag(Context context) {
    return getCurrentLocale(context).toLanguageTag();
  }

  private static Locale getCurrentLocale(Context context) {
    if (AndroidVersionUtils.isApiLevelAtLeastNougat()) {
      return context.getResources().getConfiguration().getLocales().get(0);
    } else {
      //noinspection deprecation
      return context.getResources().getConfiguration().locale;
    }
  }

  @Override
  public void loadAppearances(final MaybeConsumer<Success> onSuccess) {
    dataController.getExternalSensors(
        new LoggingConsumer<Map<String, ExternalSensorSpec>>(
            TAG, "load external sensors from database") {
          @Override
          public void success(Map<String, ExternalSensorSpec> value) {
            for (Map.Entry<String, ExternalSensorSpec> entry : value.entrySet()) {
              putExternalSensorAppearance(entry.getKey(), entry.getValue());
            }
            Success.succeeded(onSuccess);
          }
        });
  }

  public SensorAppearanceProviderImpl(DataController dataController) {
    this.dataController = Preconditions.checkNotNull(dataController);
    // If we add new on-device sensors/icons, they need to be added here.

    // TODO: add these when the sensors are added to the adapter?
    // TODO(b/138994689): Update the Velocity Sensor icon and strings once they are finalized
    putAppearance(
        VelocitySensor.ID,
        BuiltInSensorAppearance.create(
            R.string.vel,
            R.drawable.ic_sensor_acc_y_white_24dp,
            R.string.vel_units,
            R.string.sensor_desc_short_acc_x,
            R.string.sensor_desc_first_paragraph_acc,
            R.string.sensor_desc_second_paragraph_acc,
            R.drawable.learnmore_acc,
            new ImageViewSensorAnimationBehavior(
                R.drawable.accy_level_drawable,
                ImageViewSensorAnimationBehavior.TYPE_ACCELEROMETER_SCALE_ROTATES),
            2 /* 2 decimal places */,
            VelocitySensor.ID));

    putAppearance(
        AccelerometerSensor.Axis.X.getSensorId(),
        BuiltInSensorAppearance.create(
            R.string.acc_x,
            R.drawable.ic_sensor_acc_x_white_24dp,
            R.string.acc_units,
            R.string.sensor_desc_short_acc_x,
            R.string.sensor_desc_first_paragraph_acc,
            R.string.sensor_desc_second_paragraph_acc,
            R.drawable.learnmore_acc,
            new ImageViewSensorAnimationBehavior(
                R.drawable.accx_level_drawable,
                ImageViewSensorAnimationBehavior.TYPE_ACCELEROMETER_SCALE_ROTATES),
            BuiltInSensorAppearance.DEFAULT_POINTS_AFTER_DECIMAL,
            AccelerometerSensor.Axis.X.getSensorId()));

    putAppearance(
        AccelerometerSensor.Axis.Y.getSensorId(),
        BuiltInSensorAppearance.create(
            R.string.acc_y,
            R.drawable.ic_sensor_acc_y_white_24dp,
            R.string.acc_units,
            R.string.sensor_desc_short_acc_y,
            R.string.sensor_desc_first_paragraph_acc,
            R.string.sensor_desc_second_paragraph_acc,
            R.drawable.learnmore_acc,
            new ImageViewSensorAnimationBehavior(
                R.drawable.accy_level_drawable,
                ImageViewSensorAnimationBehavior.TYPE_ACCELEROMETER_SCALE_ROTATES),
            BuiltInSensorAppearance.DEFAULT_POINTS_AFTER_DECIMAL,
            AccelerometerSensor.Axis.Y.getSensorId()));

    putAppearance(
        AccelerometerSensor.Axis.Z.getSensorId(),
        BuiltInSensorAppearance.create(
            R.string.acc_z,
            R.drawable.ic_sensor_acc_z_white_24dp,
            R.string.acc_units,
            R.string.sensor_desc_short_acc_z,
            R.string.sensor_desc_first_paragraph_acc,
            R.string.sensor_desc_second_paragraph_acc,
            R.drawable.learnmore_acc,
            new ImageViewSensorAnimationBehavior(
                R.drawable.accz_level_drawable,
                ImageViewSensorAnimationBehavior.TYPE_ACCELEROMETER_SCALE),
            BuiltInSensorAppearance.DEFAULT_POINTS_AFTER_DECIMAL,
            AccelerometerSensor.Axis.Z.getSensorId()));

    putAppearance(
        LinearAccelerometerSensor.ID,
        BuiltInSensorAppearance.create(
            R.string.linear_accelerometer,
            R.drawable.ic_sensor_linacc_white_24dp,
            R.string.acc_units,
            R.string.sensor_desc_short_linear_acc,
            R.string.sensor_desc_first_paragraph_linear_acc,
            R.string.sensor_desc_second_paragraph_linear_acc,
            R.drawable.learnmore_acc,
            new ImageViewSensorAnimationBehavior(
                R.drawable.linacc_level_drawable,
                ImageViewSensorAnimationBehavior.TYPE_POSITIVE_RELATIVE_SCALE),
            BuiltInSensorAppearance.DEFAULT_POINTS_AFTER_DECIMAL,
            LinearAccelerometerSensor.ID));

    putAppearance(
        AmbientLightSensor.ID,
        BuiltInSensorAppearance.create(
            R.string.ambient_light,
            R.drawable.ic_sensor_light_white_24dp,
            R.string.ambient_light_units,
            R.string.sensor_desc_short_light,
            R.string.sensor_desc_first_paragraph_light,
            R.string.sensor_desc_second_paragraph_light,
            R.drawable.learnmore_light,
            new ImageViewSensorAnimationBehavior(
                R.drawable.ambient_level_drawable,
                ImageViewSensorAnimationBehavior.TYPE_RELATIVE_SCALE),
            BuiltInSensorAppearance.DEFAULT_POINTS_AFTER_DECIMAL,
            AmbientLightSensor.ID));

    putAppearance(
        MagneticStrengthSensor.ID,
        BuiltInSensorAppearance.create(
            R.string.magnetic_field_strength,
            R.drawable.ic_sensor_magnet_white_24dp,
            R.string.magnetic_strength_units,
            R.string.sensor_desc_short_magnetic_strength,
            R.string.sensor_desc_first_paragraph_magnetic_strength,
            R.string.sensor_desc_second_paragraph_magnetic_strength,
            R.drawable.learnmore_magnet,
            new ImageViewSensorAnimationBehavior(
                R.drawable.magnetometer_level_drawable,
                ImageViewSensorAnimationBehavior.TYPE_POSITIVE_RELATIVE_SCALE),
            BuiltInSensorAppearance.DEFAULT_POINTS_AFTER_DECIMAL,
            MagneticStrengthSensor.ID));

    putAppearance(
        CompassSensor.ID,
        BuiltInSensorAppearance.create(
            R.string.compass,
            R.drawable.ic_sensor_compass_white_24dp,
            R.string.compass_units,
            R.string.sensor_desc_short_compass,
            R.string.sensor_desc_first_paragraph_compass,
            R.string.sensor_desc_second_paragraph_compass,
            R.drawable.learnmore_compass,
            new ImageViewSensorAnimationBehavior(
                R.drawable.compass_level_drawable, ImageViewSensorAnimationBehavior.TYPE_ROTATION),
            BuiltInSensorAppearance.DEFAULT_POINTS_AFTER_DECIMAL,
            CompassSensor.ID));

    putAppearance(
        DecibelSensor.ID,
        BuiltInSensorAppearance.create(
            R.string.decibel,
            R.drawable.ic_sensor_decibels_white_24dp,
            R.string.decibel_units,
            R.string.sensor_desc_short_decibel,
            R.string.sensor_desc_first_paragraph_decibel,
            R.string.sensor_desc_second_paragraph_decibel,
            R.drawable.learnmore_sound,
            new ImageViewSensorAnimationBehavior(
                R.drawable.decibel_level_drawable,
                ImageViewSensorAnimationBehavior.TYPE_RELATIVE_SCALE),
            BuiltInSensorAppearance.DEFAULT_POINTS_AFTER_DECIMAL,
            DecibelSensor.ID));

    putAppearance(
        PitchSensor.ID,
        BuiltInSensorAppearance.create(
            R.string.pitch,
            R.drawable.ic_music_note_white_24dp,
            R.string.hertz_units,
            R.string.sensor_desc_short_pitch,
            R.string.sensor_desc_first_paragraph_pitch,
            R.string.sensor_desc_second_paragraph_pitch,
            R.drawable.learnmore_sound,
            new PitchSensorAnimationBehavior(),
            1 /* 1 decimal place */,
            PitchSensor.ID));

    putAppearance(
        BarometerSensor.ID,
        BuiltInSensorAppearance.create(
            R.string.barometer,
            R.drawable.ic_sensor_barometer_white_24dp,
            R.string.barometer_units,
            R.string.sensor_desc_short_barometer,
            R.string.sensor_desc_first_paragraph_barometer,
            R.string.sensor_desc_second_paragraph_barometer,
            R.drawable.learnmore_barometer,
            new ImageViewSensorAnimationBehavior(
                R.drawable.barometer_level_drawable,
                ImageViewSensorAnimationBehavior.TYPE_RELATIVE_SCALE),
            2 /* 2 decimal places */,
            BarometerSensor.ID));

    putAppearance(
        AmbientTemperatureSensor.ID,
        new BuiltInSensorAppearance(
            R.string.ambient_temperature,
            R.drawable.generic_sensor_white,
            R.string.temperature_units,
            new ImageViewSensorAnimationBehavior(
                R.drawable.bluetooth_level_drawable,
                ImageViewSensorAnimationBehavior.TYPE_STATIC_ICON),
            AmbientTemperatureSensor.ID));

    putAppearance(
        SineWavePseudoSensor.ID,
        new BuiltInSensorAppearance(
            R.string.sine_wave, R.drawable.generic_sensor_white, SineWavePseudoSensor.ID));
  }

  private void putExternalSensorAppearance(String sensorId, ExternalSensorSpec sensor) {
    putAppearance(sensorId, sensor.getSensorAppearance());
  }

  private void putAppearance(String sensorId, SensorAppearance appearance) {
    appearances.put(sensorId, Preconditions.checkNotNull(appearance));
  }

  @Override
  public SensorAppearance getAppearance(String sensorId) {
    if (appearances.containsKey(sensorId)) {
      return appearances.get(sensorId);
    }

    // Completely unknown sensors get a default appearance here.
    return UNKNOWN_SENSOR_APPEARANCE;
  }

  public static GoosciSensorAppearance.BasicSensorAppearance appearanceToProto(
      SensorAppearance appearance, Context context) {
    return GoosciSensorAppearance.BasicSensorAppearance.newBuilder()
        .setName(appearance.getName(context))
        .setUnits(appearance.getUnits(context))
        .setShortDescription(appearance.getShortDescription(context))
        .setIconPath(appearance.getSmallIconPath())
        .setLargeIconPath(appearance.getLargeIconPath())
        .setPointsAfterDecimal(appearance.getPointsAfterDecimal())
        .build();
  }

  public static NumberFormat createNumberFormat(int pointsAfterDecimalInNumberFormat) {
    if (pointsAfterDecimalInNumberFormat <= DEFAULT_POINTS_AFTER_DECIMAL) {
      return new AxisNumberFormat();
    } else {
      pointsAfterDecimalInNumberFormat =
          Math.min(pointsAfterDecimalInNumberFormat, MAX_POINTS_AFTER_DECIMAL);
      final String format = "%." + pointsAfterDecimalInNumberFormat + "f";
      return new NumberFormat() {
        @Override
        public StringBuffer format(double value, StringBuffer buffer, FieldPosition field) {
          return buffer.append(String.format(format, value));
        }

        @Override
        public StringBuffer format(long value, StringBuffer buffer, FieldPosition field) {
          return format((double) value, buffer, field);
        }

        @Override
        public Number parse(String string, ParsePosition position) {
          return null;
        }
      };
    }
  }
}
