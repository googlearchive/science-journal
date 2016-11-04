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
import android.graphics.drawable.Drawable;
import android.support.v4.graphics.drawable.DrawableCompat;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.ToggleButton;

import com.bignerdranch.expandablerecyclerview.ViewHolder.ChildViewHolder;
import com.google.android.apps.forscience.whistlepunk.R;
import com.google.android.apps.forscience.whistlepunk.SensorAppearance;
import com.google.android.apps.forscience.whistlepunk.SensorAppearanceProvider;
import com.google.android.apps.forscience.whistlepunk.SensorRegistry;

import java.util.Map;

/**
 * View holder for child views in expandable tree view of sensors.
 */
public class SensorChildViewHolder extends ChildViewHolder {
    private final TextView mNameView;
    private final ToggleButton mPairedCheckbox;
    private final SensorAppearanceProvider mAppearanceProvider;
    private final ImageButton mSettingsGear;
    private final ImageView mIcon;

    public SensorChildViewHolder(View itemView, SensorAppearanceProvider appearanceProvider) {
        super(itemView);
        mNameView = (TextView) itemView.findViewById(R.id.sensor_name);
        mPairedCheckbox = (ToggleButton) itemView.findViewById(R.id.paired_checkbox);
        mSettingsGear = (ImageButton) itemView.findViewById(R.id.settings_gear);
        mIcon = (ImageView) itemView.findViewById(R.id.sensor_icon);
        mAppearanceProvider = appearanceProvider;
    }

    public void bind(final String sensorKey, Map<String, ConnectableSensor> sensorMap,
            final ConnectableSensorRegistry registry, final SensorRegistry sensorRegistry) {
        ConnectableSensor sensor = sensorMap.get(sensorKey);
        SensorAppearance appearance = sensor.getAppearance(mAppearanceProvider);
        Context context = itemView.getContext();
        mNameView.setText(appearance.getName(context));

        Drawable icon = appearance.getIconDrawable(context);
        DrawableCompat.setTint(icon,
                context.getResources().getColor(R.color.text_color_light_grey));
        mIcon.setImageDrawable(icon);

        boolean paired = sensor.isPaired();
        mPairedCheckbox.setChecked(paired);
        updateCheckboxContentDescription(paired);
        DrawableCompat.setTint(mPairedCheckbox.getBackground(),
                context.getResources().getColor(R.color.color_accent));
        mPairedCheckbox.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (isChecked) {
                    registry.pair(sensorKey, mAppearanceProvider, sensorRegistry);
                } else {
                    registry.unpair(sensorKey);
                }
                updateCheckboxContentDescription(isChecked);
            }
        });

        // Clicking anywhere on the row can change the checked state of the checkbox.
        itemView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mPairedCheckbox.setChecked(!mPairedCheckbox.isChecked());
            }
        });

        DrawableCompat.setTint(mSettingsGear.getDrawable(),
                context.getResources().getColor(R.color.color_accent));
        mSettingsGear.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // TODO: this should really be called showSensorOptions.
                registry.showDeviceOptions(sensorKey);
            }
        });
    }

    private void updateCheckboxContentDescription(boolean isChecked) {
        mPairedCheckbox.setContentDescription(mPairedCheckbox.getResources().getString(
                isChecked ? R.string.remove_device_from_experiment_checkbox
                        : R.string.add_device_to_experiment_checkbox));
    }
}
