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
import android.widget.ImageView;
import android.widget.TextView;

import com.google.android.apps.forscience.whistlepunk.R;
import com.google.android.apps.forscience.whistlepunk.ToggleArrow;
import com.google.common.base.Supplier;

public class ServiceParentViewHolder extends OffsetParentViewHolder {
    private final TextView mNameView;
    private final ImageView mIcon;
    private final ToggleArrow mCollapsedIcon;

    public ServiceParentViewHolder(View itemView, Supplier<Integer> offsetSupplier) {
        super(itemView, offsetSupplier);
        mNameView = (TextView) itemView.findViewById(R.id.device_name);
        mIcon = (ImageView) itemView.findViewById(R.id.device_icon);
        mCollapsedIcon = (ToggleArrow) itemView.findViewById(R.id.collapsed_icon);
    }

    public void bind(ServiceParentListItem item) {
        mNameView.setText(item.getServiceName());
        mIcon.setImageDrawable(item.getDeviceIcon(mIcon.getContext()));
        mCollapsedIcon.setActionStrings(R.string.btn_expand_device,
                R.string.btn_contract_device);
        mCollapsedIcon.setActive(item.isInitiallyExpanded(), false);
    }

    // TODO: Duplication with DeviceParentViewHolder?
    @Override
    public void onExpansionToggled(boolean wasExpandedBefore) {
        super.onExpansionToggled(wasExpandedBefore);
        boolean isNowExpanded = !wasExpandedBefore;
        mCollapsedIcon.setActive(isNowExpanded, true);
    }
}
