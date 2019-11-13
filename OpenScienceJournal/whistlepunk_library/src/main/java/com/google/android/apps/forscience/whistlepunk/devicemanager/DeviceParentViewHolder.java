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
import android.graphics.drawable.Drawable;
import android.os.Build;
import androidx.appcompat.widget.PopupMenu;
import android.view.Gravity;
import android.view.MenuItem;
import android.view.View;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import com.google.android.apps.forscience.whistlepunk.R;
import com.google.android.apps.forscience.whistlepunk.ToggleArrow;
import com.google.android.apps.forscience.whistlepunk.api.scalarinput.InputDeviceSpec;
import com.google.common.base.Supplier;
import java.util.Map;

/** View holder for device views in the expandable view */
public class DeviceParentViewHolder extends OffsetParentViewHolder {
  private final TextView deviceNameView;
  private final ImageView deviceIcon;
  private final ToggleArrow collapsedIcon;
  private final ImageButton menuButton;
  private final MenuCallbacks menuCallbacks;
  private PopupMenu popupMenu;
  private DeviceParentListItem item;

  /** Defines what to do when menu items are chosen. */
  public interface MenuCallbacks {
    void forgetDevice(InputDeviceSpec spec);
  }

  public DeviceParentViewHolder(
      View itemView, Supplier<Integer> globalPositionOffset, MenuCallbacks menuCallbacks) {
    super(itemView, globalPositionOffset);
    deviceNameView = (TextView) itemView.findViewById(R.id.device_name);
    deviceIcon = (ImageView) itemView.findViewById(R.id.device_icon);
    collapsedIcon = (ToggleArrow) itemView.findViewById(R.id.collapsed_icon);
    menuButton = (ImageButton) itemView.findViewById(R.id.btn_device_overflow_menu);
    this.menuCallbacks = menuCallbacks;
  }

  public void bind(
      final DeviceParentListItem item,
      Map<String, ConnectableSensor> sensorMap,
      DeviceRegistry deviceRegistry) {
    this.item = item;
    String name = item.getDeviceName();
    deviceNameView.setText(name);
    Context context = deviceIcon.getContext();
    Drawable icon = item.getDeviceIcon(context, sensorMap);
    deviceIcon.setImageDrawable(icon);

    collapsedIcon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
    collapsedIcon.setIsFocusable(false);
    updateActionStrings(item.isInitiallyExpanded());
    collapsedIcon.setActive(item.isInitiallyExpanded(), false);
    collapsedIcon.setOnClickListener(
        new View.OnClickListener() {
          @Override
          public void onClick(View v) {
            // The ImageButton steals the clicks from the row, so get the clicks back
            // with this listener!
            itemView.callOnClick();
          }
        });

    if (item.canForget(deviceRegistry)) {
      menuButton.setVisibility(View.VISIBLE);
      menuButton.setOnClickListener(
          new View.OnClickListener() {
            @Override
            public void onClick(View v) {
              openDeviceMenu(item);
            }
          });
      menuButton.setContentDescription(
          context.getResources().getString(R.string.device_settings_for, name));
    } else {
      menuButton.setVisibility(View.GONE);
      menuButton.setOnClickListener(null);
      menuButton.setContentDescription(null);
    }
  }

  private void openDeviceMenu(final DeviceParentListItem listItem) {
    if (popupMenu != null) {
      return;
    }
    final Context context = menuButton.getContext();
    popupMenu =
        new PopupMenu(context, menuButton, Gravity.NO_GRAVITY, R.attr.actionOverflowMenuStyle, 0);
    popupMenu.getMenuInflater().inflate(R.menu.menu_device_recycler_item, popupMenu.getMenu());

    popupMenu.setOnMenuItemClickListener(
        new PopupMenu.OnMenuItemClickListener() {
          public boolean onMenuItemClick(MenuItem item) {
            int itemId = item.getItemId();
            if (itemId == R.id.btn_forget_device) {
              menuCallbacks.forgetDevice(listItem.getSpec());
              return true;
            }
            return false;
          }
        });
    popupMenu.setOnDismissListener(
        new PopupMenu.OnDismissListener() {
          @Override
          public void onDismiss(PopupMenu menu) {
            popupMenu = null;
          }
        });

    popupMenu.show();
  }

  @Override
  public void onExpansionToggled(boolean wasExpandedBefore) {
    super.onExpansionToggled(wasExpandedBefore);
    boolean isNowExpanded = !wasExpandedBefore;
    collapsedIcon.setActive(isNowExpanded, true);
    item.setIsCurrentlyExpanded(isNowExpanded);
    updateActionStrings(isNowExpanded);
  }

  // Updates the action strings on the itemView row based on whether it is currently expanded.
  private void updateActionStrings(boolean isExpanded) {
    final String description =
        itemView
            .getContext()
            .getString(isExpanded ? R.string.btn_contract_device : R.string.btn_expand_device);
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
  }
}
