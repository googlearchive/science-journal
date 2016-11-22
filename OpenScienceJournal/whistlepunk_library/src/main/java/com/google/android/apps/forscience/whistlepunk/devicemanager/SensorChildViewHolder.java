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
import com.google.android.apps.forscience.whistlepunk.AccessibilityUtils;
import com.google.android.apps.forscience.whistlepunk.R;
import com.google.android.apps.forscience.whistlepunk.SensorAppearance;
import com.google.android.apps.forscience.whistlepunk.SensorAppearanceProvider;

import java.util.Map;

/**
 * View holder for child views in expandable tree view of sensors.
 */
public class SensorChildViewHolder extends ChildViewHolder {
    private final TextView mNameView;
    private final CheckBox mPairedCheckbox;
    private final SensorAppearanceProvider mAppearanceProvider;
    private final ImageButton mSettingsGear;
    private final ImageView mIcon;
    private String mSensorKey;

    public SensorChildViewHolder(View itemView, SensorAppearanceProvider appearanceProvider) {
        super(itemView);
        mNameView = (TextView) itemView.findViewById(R.id.sensor_name);
        mPairedCheckbox = (CheckBox) itemView.findViewById(R.id.paired_checkbox);
        mSettingsGear = (ImageButton) itemView.findViewById(R.id.settings_gear);
        mIcon = (ImageView) itemView.findViewById(R.id.sensor_icon);
        mAppearanceProvider = appearanceProvider;
    }

    // TODO: can we test this logic?
    public void bind(final String sensorKey, Map<String, ConnectableSensor> sensorMap,
            final ConnectableSensorRegistry registry, final EnablementController econtroller) {
        if (mSensorKey != null) {
            econtroller.clearEnablementListener(mSensorKey);
        }
        mSensorKey = sensorKey;
        ConnectableSensor sensor = sensorMap.get(sensorKey);
        SensorAppearance appearance = sensor.getAppearance(mAppearanceProvider);
        Context context = itemView.getContext();
        mNameView.setText(appearance.getName(context));

        mIcon.setImageDrawable(appearance.getIconDrawable(context));

        boolean paired = sensor.isPaired();
        mPairedCheckbox.setOnCheckedChangeListener(null);
        mPairedCheckbox.setChecked(paired);
        updateCheckboxContentDescription(paired);

        mPairedCheckbox.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
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
        itemView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mPairedCheckbox.isEnabled()) {
                    mPairedCheckbox.setChecked(!mPairedCheckbox.isChecked());
                }
            }
        });

        econtroller.addEnablementListener(sensorKey, new Consumer<Boolean>() {
            @Override
            public void take(Boolean isEnabled) {
                mPairedCheckbox.setEnabled(isEnabled);
            }
        });

        boolean hasOptions = registry.hasOptions(sensorKey);
        if (hasOptions) {
            mSettingsGear.setVisibility(View.VISIBLE);
            mSettingsGear.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    registry.showSensorOptions(sensorKey);
                }
            });
            mSettingsGear.setContentDescription(context.getResources().getString(
                    R.string.sensor_options_for, mNameView.getText()));
        } else {
            mSettingsGear.setVisibility(View.GONE);
            mSettingsGear.setOnClickListener(null);
            mSettingsGear.setContentDescription("");
        }
    }

    private void updateCheckboxContentDescription(boolean isChecked) {
        if (AccessibilityUtils.canSetAccessibilityDelegateAction()) {
            // For newer phones, we can update the content description on the row, and the checkbox
            // does not need to be focusable for a11y.
            final String description = itemView.getContext().getString(isChecked ?
                    R.string.remove_device_from_experiment_checkbox :
                    R.string.add_device_to_experiment_checkbox);
            itemView.setAccessibilityDelegate(new View.AccessibilityDelegate() {
                @TargetApi(Build.VERSION_CODES.LOLLIPOP)
                @Override
                public void onInitializeAccessibilityNodeInfo(View host,
                        AccessibilityNodeInfo info) {
                    super.onInitializeAccessibilityNodeInfo(host, info);
                    info.addAction(new AccessibilityNodeInfo.AccessibilityAction(
                            AccessibilityNodeInfo.ACTION_CLICK, description));
                }
            });
            mPairedCheckbox.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            mPairedCheckbox.setContentDescription("");
        } else {
            // For older devices, make sure the content description is specific to this row.
            mPairedCheckbox.setContentDescription(mPairedCheckbox.getResources().getString(
                    R.string.include_device_in_experiment, mNameView.getText()));
        }
    }
}
