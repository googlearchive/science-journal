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
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.PopupMenu;
import android.widget.TextView;

import com.google.android.apps.forscience.whistlepunk.R;
import com.google.android.apps.forscience.whistlepunk.ToggleArrow;
import com.google.android.apps.forscience.whistlepunk.api.scalarinput.InputDeviceSpec;
import com.google.common.base.Supplier;

import java.util.Map;

/**
 * View holder for device views in the expandable view
 */
public class DeviceParentViewHolder extends OffsetParentViewHolder {
    private final TextView mDeviceNameView;
    private final ImageView mDeviceIcon;
    private final ToggleArrow mCollapsedIcon;
    private final ImageButton mMenuButton;
    private final MenuCallbacks mMenuCallbacks;
    private PopupMenu mPopupMenu;
    private DeviceParentListItem mItem;

    /**
     * Defines what to do when menu items are chosen.
     */
    public interface MenuCallbacks {
        void forgetDevice(InputDeviceSpec spec);
    }

    public DeviceParentViewHolder(View itemView, Supplier<Integer> globalPositionOffset,
            MenuCallbacks menuCallbacks) {
        super(itemView, globalPositionOffset);
        mDeviceNameView = (TextView) itemView.findViewById(R.id.device_name);
        mDeviceIcon = (ImageView) itemView.findViewById(R.id.device_icon);
        mCollapsedIcon = (ToggleArrow) itemView.findViewById(R.id.collapsed_icon);
        mMenuButton = (ImageButton) itemView.findViewById(R.id.btn_device_overflow_menu);
        mMenuCallbacks = menuCallbacks;
    }

    public void bind(final DeviceParentListItem item, Map<String, ConnectableSensor> sensorMap,
            DeviceRegistry deviceRegistry) {
        mItem = item;
        mDeviceNameView.setText(item.getDeviceName());
        Context context = mDeviceIcon.getContext();
        Drawable icon = item.getDeviceIcon(context, sensorMap);
        if (icon != null) {
            DrawableCompat.setTint(icon, context.getResources().getColor(R.color.color_accent));
        }
        mDeviceIcon.setImageDrawable(icon);

        mCollapsedIcon.setActionStrings(R.string.btn_expand_device,
                R.string.btn_contract_device);
        mCollapsedIcon.setActive(item.isInitiallyExpanded(), false);
        mCollapsedIcon.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // The ImageButton steals the clicks from the row, so get the clicks back
                // with this listener!
                itemView.callOnClick();
            }
        });

        if (item.canForget(deviceRegistry)) {
            mMenuButton.setVisibility(View.VISIBLE);
            mMenuButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    openDeviceMenu(item);
                }
            });
        } else {
            mMenuButton.setVisibility(View.GONE);
            mMenuButton.setOnClickListener(null);
        }
    }

    private void openDeviceMenu(final DeviceParentListItem listItem) {
        if (mPopupMenu != null) {
            return;
        }
        final Context context = mMenuButton.getContext();
        mPopupMenu = new PopupMenu(context, mMenuButton);
        mPopupMenu.getMenuInflater().inflate(R.menu.menu_device_recycler_item,
                mPopupMenu.getMenu());

        mPopupMenu.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
            public boolean onMenuItemClick(MenuItem item) {
                int itemId = item.getItemId();
                if (itemId == R.id.btn_forget_device) {
                    mMenuCallbacks.forgetDevice(listItem.getSpec());
                    return true;
                }
                return false;
            }
        });
        mPopupMenu.setOnDismissListener(new PopupMenu.OnDismissListener() {
            @Override
            public void onDismiss(PopupMenu menu) {
                mPopupMenu = null;
            }
        });

        mPopupMenu.show();
    }

    @Override
    public void onExpansionToggled(boolean wasExpandedBefore) {
        super.onExpansionToggled(wasExpandedBefore);
        boolean isNowExpanded = !wasExpandedBefore;
        mCollapsedIcon.setActive(isNowExpanded, true);
        mItem.setIsCurrentlyExpanded(isNowExpanded);
    }
}
