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
import android.content.res.Resources;
import android.view.View;
import com.google.android.apps.forscience.javalib.FailureListener;
import com.google.android.apps.forscience.whistlepunk.accounts.AppAccount;
import com.google.android.apps.forscience.whistlepunk.sensorapi.ActiveBundle;
import com.google.android.apps.forscience.whistlepunk.sensorapi.NewOptionsStorage;
import com.google.android.apps.forscience.whistlepunk.sensorapi.OptionsListener;
import com.google.android.apps.forscience.whistlepunk.sensorapi.ReadableSensorOptions;
import com.google.android.apps.forscience.whistlepunk.sensorapi.SensorChoice;
import com.google.android.apps.forscience.whistlepunk.sensorapi.SensorPresenter;

public class SensorSettingsControllerImpl extends ActiveSettingsController
    implements SensorSettingsController {
  private Context context;
  private AppAccount appAccount;

  public SensorSettingsControllerImpl(Context context, AppAccount appAccount) {
    super(context);
    this.context = context;
    this.appAccount = appAccount;
  }

  @Override
  public void launchOptionsDialog(
      SensorChoice source,
      final SensorPresenter presenter,
      NewOptionsStorage storage,
      final OptionsListener commitListener,
      FailureListener failureListener) {
    Resources resources = context.getResources();
    String name = getSourceName(source);
    String optionsTitle = resources.getString(R.string.sensor_options);
    OptionsCallbacks callbacks =
        new OptionsCallbacks() {
          @Override
          public View buildOptionsView(ActiveBundle activeBundle) {
            return presenter.getOptionsPresenter().buildOptionsView(activeBundle, context);
          }

          @Override
          public void previewOptions(ReadableSensorOptions newOptions) {
            presenter.getOptionsPresenter().applyOptions(newOptions);
          }

          @Override
          public void commitOptions(ReadableSensorOptions newOptions) {
            previewOptions(newOptions);
            commitListener.applyOptions(newOptions);
          }
        };
    super.launchOptionsDialog(callbacks, name, optionsTitle, storage.load(failureListener));
  }

  private String getSourceName(SensorChoice source) {
    return AppSingleton.getInstance(context)
        .getSensorAppearanceProvider(appAccount)
        .getAppearance(source.getId())
        .getName(context);
  }
}
