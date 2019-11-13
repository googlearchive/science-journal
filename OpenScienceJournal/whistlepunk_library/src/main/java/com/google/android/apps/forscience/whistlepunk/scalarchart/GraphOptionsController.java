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

package com.google.android.apps.forscience.whistlepunk.scalarchart;

import android.content.Context;
import android.content.res.Resources;
import android.view.View;
import com.google.android.apps.forscience.javalib.FailureListener;
import com.google.android.apps.forscience.whistlepunk.ActiveSettingsController;
import com.google.android.apps.forscience.whistlepunk.PrefsNewOptionsStorage;
import com.google.android.apps.forscience.whistlepunk.R;
import com.google.android.apps.forscience.whistlepunk.sensorapi.NewOptionsStorage;
import com.google.android.apps.forscience.whistlepunk.sensorapi.WriteableSensorOptions;

public class GraphOptionsController extends ActiveSettingsController {

  /** Prefix for sensor tags to generate a preferences filename. */
  private static final String GRAPH_OPTIONS_PREF_FILE = "graph_options";

  private final Context context;

  public GraphOptionsController(Context context) {
    super(context);
    this.context = context;
  }

  public WriteableSensorOptions getOptions(FailureListener failureListener) {
    return new PrefsNewOptionsStorage(GRAPH_OPTIONS_PREF_FILE, context).load(failureListener);
  }

  public void launchOptionsDialog(
      ScalarDisplayOptions scalarDisplayOptions, FailureListener failureListener) {
    Resources resources = context.getResources();
    String optionsTitle = resources.getString(R.string.graph_options_title);
    super.launchOptionsDialog(
        new GraphOptionsManager(scalarDisplayOptions).makeCallbacks(context),
        resources.getString(R.string.graph_name),
        optionsTitle,
        getOptions(failureListener));
  }

  public void loadIntoScalarDisplayOptions(ScalarDisplayOptions scalarDisplayOptions, View view) {
    final GraphOptionsManager manager = new GraphOptionsManager(scalarDisplayOptions);
    manager.loadOptions(
        getOptions(new NewOptionsStorage.SnackbarFailureListener(view)).getReadOnly());
  }
}
