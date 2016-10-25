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

import com.bignerdranch.expandablerecyclerview.ViewHolder.ParentViewHolder;
import com.google.android.apps.forscience.whistlepunk.R;

/**
 * View holder for device views in the expandable view
 */
public class DeviceParentViewHolder extends ParentViewHolder {
    private final TextView mDeviceNameView;

    public DeviceParentViewHolder(View itemView) {
        super(itemView);
        mDeviceNameView = (TextView) itemView.findViewById(R.id.device_name);
    }

    public void bind(DeviceParentListItem item) {
        mDeviceNameView.setText(item.getDeviceName());
    }
}
