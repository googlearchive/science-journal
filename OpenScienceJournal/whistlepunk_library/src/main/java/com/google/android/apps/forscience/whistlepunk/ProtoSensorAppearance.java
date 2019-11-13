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
import com.google.android.apps.forscience.whistlepunk.data.GoosciIcon;
import com.google.android.apps.forscience.whistlepunk.data.GoosciSensorAppearance.BasicSensorAppearance;
import io.reactivex.Single;
import java.text.NumberFormat;

public class ProtoSensorAppearance implements SensorAppearance {
  public static final int DEFAULT_POINTS_AFTER_DECIMAL = -1;

  // Don't allow more than 10 places after the decimal to be displayed. The UX can't
  // handle this very well.
  // TODO: Revisit this constant -- should it be even smaller, like 5?
  public static final int MAX_POINTS_AFTER_DECIMAL = 10;

  private BasicSensorAppearance proto;
  private NumberFormat numberFormat;

  public static SensorAppearance getAppearanceFromProtoOrProvider(
      BasicSensorAppearance proto, String sensorId, SensorAppearanceProvider appearanceProvider) {

    SensorAppearance providerAppearance = appearanceProvider.getAppearance(sensorId);
    if (providerAppearance != null && providerAppearance != appearanceProvider.getAppearance("0")) {
      return providerAppearance;
    } else {
      return new ProtoSensorAppearance(proto);
    }
  }

  public ProtoSensorAppearance(BasicSensorAppearance proto) {
    BasicSensorAppearance basicSensorAppearance = proto;
    numberFormat =
        SensorAppearanceProviderImpl.createNumberFormat(
            basicSensorAppearance.getPointsAfterDecimal());
    this.proto = basicSensorAppearance;
  }

  @Override
  public String getName(Context context) {
    return proto.getName();
  }

  @Override
  public String getUnits(Context context) {
    return proto.getUnits();
  }

  @Override
  public Drawable getIconDrawable(Context context) {
    return context.getResources().getDrawable(R.drawable.generic_sensor_white);
  }

  @Override
  public String getShortDescription(Context context) {
    return proto.getShortDescription();
  }

  @Override
  public boolean hasLearnMore() {
    return false;
  }

  @Override
  public Single<LearnMoreContents> loadLearnMore(final Context context) {
    return Single.error(new IllegalStateException("No learn more"));
  }

  // These images aren't rendered now, so for now, this is safe.
  // TODO: Export images to proto
  @Override
  public GoosciIcon.IconPath getSmallIconPath() {
    return GoosciIcon.IconPath.getDefaultInstance();
  }

  @Override
  public GoosciIcon.IconPath getLargeIconPath() {
    return GoosciIcon.IconPath.getDefaultInstance();
  }

  @Override
  public SensorAnimationBehavior getSensorAnimationBehavior() {
    return ImageViewSensorAnimationBehavior.createDefault();
  }

  @Override
  public NumberFormat getNumberFormat() {
    return numberFormat;
  }

  @Override
  public int getPointsAfterDecimal() {
    return proto.getPointsAfterDecimal();
  }
}
