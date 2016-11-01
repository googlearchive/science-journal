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
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import com.bignerdranch.expandablerecyclerview.ViewHolder.ParentViewHolder;
import com.google.android.apps.forscience.whistlepunk.R;
import com.google.android.apps.forscience.whistlepunk.ToggleArrow;
import com.google.common.base.Supplier;

import java.util.Map;

/**
 * View holder for device views in the expandable view
 */
public class DeviceParentViewHolder extends ParentViewHolder {
    private final TextView mDeviceNameView;
    private final ImageView mDeviceIcon;
    private final ToggleArrow mCollapsedIcon;
    private final Supplier<Integer> mGlobalPositionOffset;

    public DeviceParentViewHolder(View itemView, Supplier<Integer> globalPositionOffset) {
        super(itemView);
        mDeviceNameView = (TextView) itemView.findViewById(R.id.device_name);
        mDeviceIcon = (ImageView) itemView.findViewById(R.id.device_icon);
        mCollapsedIcon = (ToggleArrow) itemView.findViewById(R.id.collapsed_icon);
        mGlobalPositionOffset = globalPositionOffset;
    }

    public void bind(DeviceParentListItem item, Map<String, ConnectableSensor> sensorMap) {
        mDeviceNameView.setText(item.getDeviceName());
        Context context = mDeviceIcon.getContext();
        mDeviceIcon.setImageDrawable(item.getDeviceIcon(context, sensorMap));
        mCollapsedIcon.setActionStrings(R.string.btn_expand_device,
                R.string.btn_contract_device);
        mCollapsedIcon.setActive(item.isInitiallyExpanded(), false);
    }

    @Override
    public void onExpansionToggled(boolean wasExpandedBefore) {
        super.onExpansionToggled(wasExpandedBefore);
        boolean isNowExpanded = !wasExpandedBefore;
        mCollapsedIcon.setActive(isNowExpanded, true);
    }

    @Override
    public void setParentListItemExpandCollapseListener(
            final ParentListItemExpandCollapseListener superListener) {
        super.setParentListItemExpandCollapseListener(new ParentListItemExpandCollapseListener() {
            @Override
            public void onParentListItemExpanded(int position) {
                superListener.onParentListItemExpanded(position - mGlobalPositionOffset.get());
            }

            @Override
            public void onParentListItemCollapsed(int position) {
                superListener.onParentListItemCollapsed(position - mGlobalPositionOffset.get());
            }
        });
    }
}
