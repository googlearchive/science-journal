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
import android.app.FragmentManager;
import android.content.Context;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.support.annotation.NonNull;
import android.view.View;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import com.google.android.apps.forscience.whistlepunk.AccessibilityUtils;
import com.google.android.apps.forscience.whistlepunk.ColorUtils;
import com.google.android.apps.forscience.whistlepunk.R;
import com.google.android.apps.forscience.whistlepunk.ToggleArrow;
import com.google.android.apps.forscience.whistlepunk.metadata.BleSensorSpec;
import com.google.common.base.Supplier;

public class ServiceParentViewHolder extends OffsetParentViewHolder {
    private final TextView mNameView;
    private final ImageView mIcon;
    private final ToggleArrow mCollapsedIcon;
    private final ImageView mErrorIcon;
    private final ImageButton mRefreshIcon;
    private Animation mRotation;
    private ServiceParentListItem mItem;

    public ServiceParentViewHolder(View itemView, Supplier<Integer> offsetSupplier) {
        super(itemView, offsetSupplier);
        mNameView = (TextView) itemView.findViewById(R.id.service_name);
        mIcon = (ImageView) itemView.findViewById(R.id.service_icon);
        mCollapsedIcon = (ToggleArrow) itemView.findViewById(R.id.collapsed_icon);
        mErrorIcon = (ImageView) itemView.findViewById(R.id.btn_service_connection_error);
        mRefreshIcon = (ImageButton) itemView.findViewById(R.id.btn_service_refresh);
    }

    public void bind(final ServiceParentListItem item, FragmentManager fragmentManager,
            final Runnable onRefresh) {
        mItem = item;
        String name = item.getServiceName();
        Context context = mIcon.getContext();
        mNameView.setText(name);

        Drawable icon = item.getDeviceIcon(context);
        if (item.getProviderId().equals(BleSensorSpec.TYPE)) {
            icon = ColorUtils.colorDrawable(mIcon.getContext(), icon, R.color.color_accent);
        }
        mIcon.setImageDrawable(icon);

        if (AccessibilityUtils.canSetAccessibilityDelegateAction()) {
            mCollapsedIcon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            mCollapsedIcon.setIsFocusable(false);
            updateActionStrings(item.isInitiallyExpanded());
        } else {
            mCollapsedIcon.setActionStrings(R.string.btn_expand_service_for,
                    R.string.btn_contract_service_for, name);
        }

        mCollapsedIcon.setActive(item.isInitiallyExpanded(), false);
        mCollapsedIcon.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // The ImageButton steals the clicks from the row, so get the clicks back
                // with this listener!
                itemView.callOnClick();
            }
        });
        final SensorDiscoverer.ServiceConnectionError error =
                item.getConnectionErrorIfAny();
        if (error == null) {
            mErrorIcon.setVisibility(View.GONE);
            mErrorIcon.setOnClickListener(null);
        } else {
            mErrorIcon.setVisibility(View.VISIBLE);
            mErrorIcon.setContentDescription(
                    context.getResources().getString(R.string.snackbar_source_error,
                            error.getErrorMessage()));
            mErrorIcon.setOnClickListener(getOnClickListener(error, fragmentManager));
        }

        boolean loading = item.isLoading();
        if (loading) {
            startLoadingAnimation();
        } else {
            mRefreshIcon.setEnabled(true);
            stopRotation();
            mRefreshIcon.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    itemView.sendAccessibilityEvent(AccessibilityEvent.TYPE_VIEW_FOCUSED);
                    item.setIsLoading(true);
                    startLoadingAnimation();
                    onRefresh.run();
                }
            });
        }

        mRefreshIcon.setContentDescription(context.getResources().getString(R.string.refresh_device, name));
    }

    private void startLoadingAnimation() {
        mRefreshIcon.setEnabled(false);
        stopRotation();
        mRotation = AnimationUtils.loadAnimation(mRefreshIcon.getContext(),
                R.anim.reload_rotate);
        mRefreshIcon.startAnimation(mRotation);
        mRefreshIcon.setOnClickListener(null);
    }

    private void stopRotation() {
        if (mRotation != null) {
            mRotation.cancel();
            mRotation = null;
            mRefreshIcon.setRotation(0);
        }
    }

    @NonNull
    private View.OnClickListener getOnClickListener(
            final SensorDiscoverer.ServiceConnectionError error,
            final FragmentManager fragmentManager) {
        if (error.canBeResolved()) {
            return new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    error.tryToResolve(fragmentManager);
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
        mItem.setIsCurrentlyExpanded(isNowExpanded);
        if (AccessibilityUtils.canSetAccessibilityDelegateAction()) {
            // For newer phones, we can update the content description on the row, and the arrow
            // does not need to be focusable for a11y.
            updateActionStrings(isNowExpanded);
        }
    }

    // Updates the action strings on the itemView row based on whether it is currently expanded.
    private void updateActionStrings(boolean isExpanded) {
        final String description = itemView.getContext().getString(isExpanded ?
                R.string.btn_contract_service : R.string.btn_expand_service);
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
    }
}
