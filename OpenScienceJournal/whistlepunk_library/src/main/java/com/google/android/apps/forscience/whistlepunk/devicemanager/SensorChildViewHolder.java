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

import android.annotation.TargetApi;
import android.content.Context;
import android.os.Build;
import android.view.View;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import com.bignerdranch.expandablerecyclerview.ViewHolder.ChildViewHolder;
import com.google.android.apps.forscience.javalib.Consumer;
import com.google.android.apps.forscience.whistlepunk.R;
import com.google.android.apps.forscience.whistlepunk.SensorAppearance;
import com.google.android.apps.forscience.whistlepunk.SensorAppearanceProvider;
import java.util.Map;

/** View holder for child views in expandable tree view of sensors. */
public class SensorChildViewHolder extends ChildViewHolder {
  private final TextView nameView;
  private final CheckBox pairedCheckbox;
  private final SensorAppearanceProvider appearanceProvider;
  private final ImageButton settingsGear;
  private final ImageView icon;
  private String sensorKey;

  public SensorChildViewHolder(View itemView, SensorAppearanceProvider appearanceProvider) {
    super(itemView);
    nameView = (TextView) itemView.findViewById(R.id.sensor_name);
    pairedCheckbox = (CheckBox) itemView.findViewById(R.id.paired_checkbox);
    settingsGear = (ImageButton) itemView.findViewById(R.id.settings_gear);
    icon = (ImageView) itemView.findViewById(R.id.sensor_icon);
    this.appearanceProvider = appearanceProvider;
  }

  // TODO: can we test this logic?
  public void bind(
      final String sensorKey,
      Map<String, ConnectableSensor> sensorMap,
      final ConnectableSensorRegistry registry,
      final EnablementController econtroller) {
    if (this.sensorKey != null) {
      econtroller.clearEnablementListener(this.sensorKey);
    }
    this.sensorKey = sensorKey;
    ConnectableSensor sensor = sensorMap.get(sensorKey);
    SensorAppearance appearance = sensor.getAppearance(appearanceProvider);
    Context context = itemView.getContext();
    nameView.setText(appearance.getName(context));

    icon.setImageDrawable(appearance.getIconDrawable(context));

    boolean paired = sensor.isPaired();
    pairedCheckbox.setOnCheckedChangeListener(null);
    pairedCheckbox.setChecked(paired);
    updateCheckboxContentDescription(paired);

    pairedCheckbox.setOnCheckedChangeListener(
        new CompoundButton.OnCheckedChangeListener() {
          @Override
          public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
            econtroller.setChecked(sensorKey, isChecked);
            if (isChecked) {
              registry.pair(sensorKey);
            } else {
              registry.unpair(sensorKey);
            }
            updateCheckboxContentDescription(isChecked);
          }
        });

    // Clicking anywhere on the row can change the checked state of the checkbox.
    itemView.setOnClickListener(
        new View.OnClickListener() {
          @Override
          public void onClick(View v) {
            if (pairedCheckbox.isEnabled()) {
              pairedCheckbox.setChecked(!pairedCheckbox.isChecked());
            }
          }
        });

    econtroller.addEnablementListener(
        sensorKey,
        new Consumer<Boolean>() {
          @Override
          public void take(Boolean isEnabled) {
            pairedCheckbox.setEnabled(isEnabled);
          }
        });

    boolean hasOptions = registry.hasOptions(sensorKey);
    if (hasOptions) {
      settingsGear.setVisibility(View.VISIBLE);
      settingsGear.setOnClickListener(
          new View.OnClickListener() {
            @Override
            public void onClick(View v) {
              registry.showSensorOptions(sensorKey);
            }
          });
      settingsGear.setContentDescription(
          context.getResources().getString(R.string.sensor_options_for, nameView.getText()));
    } else {
      settingsGear.setVisibility(View.GONE);
      settingsGear.setOnClickListener(null);
      settingsGear.setContentDescription("");
    }
  }

  private void updateCheckboxContentDescription(boolean isChecked) {
    // We can update the content description on the row, and the checkbox
    // does not need to be focusable for a11y.
    final String description =
        itemView
            .getContext()
            .getString(
                isChecked
                    ? R.string.remove_device_from_experiment_checkbox
                    : R.string.add_device_to_experiment_checkbox);
    itemView.setAccessibilityDelegate(
        new View.AccessibilityDelegate() {
          @TargetApi(Build.VERSION_CODES.LOLLIPOP)
          @Override
          public void onInitializeAccessibilityNodeInfo(View host, AccessibilityNodeInfo info) {
            super.onInitializeAccessibilityNodeInfo(host, info);
            info.addAction(
                new AccessibilityNodeInfo.AccessibilityAction(
                    AccessibilityNodeInfo.ACTION_CLICK, description));
          }
        });
    pairedCheckbox.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
    pairedCheckbox.setContentDescription("");
  }
}
