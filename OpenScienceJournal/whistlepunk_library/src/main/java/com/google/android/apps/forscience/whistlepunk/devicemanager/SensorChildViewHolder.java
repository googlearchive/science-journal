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

import android.view.View;
import android.widget.TextView;

import com.bignerdranch.expandablerecyclerview.ViewHolder.ChildViewHolder;
import com.google.android.apps.forscience.whistlepunk.R;
import com.google.android.apps.forscience.whistlepunk.SensorAppearanceProvider;

import java.util.Map;

/**
 * View holder for child views in expandable tree view of sensors.
 */
public class SensorChildViewHolder extends ChildViewHolder {
    private final TextView mNameView;
    private final SensorAppearanceProvider mAppearanceProvider;

    public SensorChildViewHolder(View itemView, SensorAppearanceProvider appearanceProvider) {
        super(itemView);
        mNameView = (TextView) itemView.findViewById(R.id.sensor_name);
        mAppearanceProvider = appearanceProvider;
    }

    public void bind(final String sensorKey, Map<String, ConnectableSensor> sensorMap,
            final ConnectableSensorRegistry registry) {
        mNameView.setText(
                sensorMap.get(sensorKey).getName(mAppearanceProvider, mNameView.getContext()));
        itemView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // TODO: this should really be called showSensorOptions.
                registry.showDeviceOptions(sensorKey);
            }
        });
    }
}
