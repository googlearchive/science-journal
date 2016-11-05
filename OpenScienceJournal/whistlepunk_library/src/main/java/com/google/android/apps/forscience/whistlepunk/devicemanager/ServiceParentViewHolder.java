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

import android.support.annotation.NonNull;
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
    private final ImageView mErrorIcon;

    private static final String TAG = "SPVHolder";

    public ServiceParentViewHolder(View itemView, Supplier<Integer> offsetSupplier) {
        super(itemView, offsetSupplier);
        mNameView = (TextView) itemView.findViewById(R.id.service_name);
        mIcon = (ImageView) itemView.findViewById(R.id.service_icon);
        mCollapsedIcon = (ToggleArrow) itemView.findViewById(R.id.collapsed_icon);
        mErrorIcon = (ImageView) itemView.findViewById(R.id.btn_service_connection_error);
    }

    public void bind(ServiceParentListItem item) {
        mNameView.setText(item.getServiceName());
        mIcon.setImageDrawable(item.getDeviceIcon(mIcon.getContext()));
        mCollapsedIcon.setActionStrings(R.string.btn_expand_device,
                R.string.btn_contract_device);
        mCollapsedIcon.setActive(item.isInitiallyExpanded(), false);
        final ExternalSensorDiscoverer.ServiceConnectionError error =
                item.getConnectionErrorIfAny();
        if (error == null) {
            mErrorIcon.setVisibility(View.GONE);
        } else {
            mErrorIcon.setVisibility(View.VISIBLE);
            mErrorIcon.setContentDescription(
                    mErrorIcon.getContext().getString(R.string.snackbar_source_error,
                            error.getErrorMessage()));
            mErrorIcon.setOnClickListener(getOnClickListener(error));
        }
    }

    @NonNull
    private View.OnClickListener getOnClickListener(
            final ExternalSensorDiscoverer.ServiceConnectionError error) {
        if (error.canBeResolved()) {
            return new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    error.tryToResolve();
                }
            };
        } else {
            return null;
        }
    }

    // TODO: Duplication with DeviceParentViewHolder?
    @Override
    public void onExpansionToggled(boolean wasExpandedBefore) {
        super.onExpansionToggled(wasExpandedBefore);
        boolean isNowExpanded = !wasExpandedBefore;
        mCollapsedIcon.setActive(isNowExpanded, true);
    }
}
