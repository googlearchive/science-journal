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
import androidx.annotation.NonNull;
import androidx.fragment.app.FragmentManager;
import android.view.View;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import com.google.android.apps.forscience.whistlepunk.ColorUtils;
import com.google.android.apps.forscience.whistlepunk.R;
import com.google.android.apps.forscience.whistlepunk.ToggleArrow;
import com.google.android.apps.forscience.whistlepunk.metadata.BleSensorSpec;
import com.google.android.apps.forscience.whistlepunk.metadata.MkrSciBleDeviceSpec;
import com.google.common.base.Supplier;

public class ServiceParentViewHolder extends OffsetParentViewHolder {
  private final TextView nameView;
  private final ImageView icon;
  private final ToggleArrow collapsedIcon;
  private final ImageView errorIcon;
  private final ImageButton refreshIcon;
  private Animation rotation;
  private ServiceParentListItem item;

  public ServiceParentViewHolder(View itemView, Supplier<Integer> offsetSupplier) {
    super(itemView, offsetSupplier);
    nameView = (TextView) itemView.findViewById(R.id.service_name);
    icon = (ImageView) itemView.findViewById(R.id.service_icon);
    collapsedIcon = (ToggleArrow) itemView.findViewById(R.id.collapsed_icon);
    errorIcon = (ImageView) itemView.findViewById(R.id.btn_service_connection_error);
    refreshIcon = (ImageButton) itemView.findViewById(R.id.btn_service_refresh);
  }

  public void bind(
      final ServiceParentListItem item, FragmentManager fragmentManager, final Runnable onRefresh) {
    this.item = item;
    String name = item.getServiceName();
    Context context = this.icon.getContext();
    nameView.setText(name);

    Drawable icon = item.getDeviceIcon(context);
    if (item.getProviderId().equals(BleSensorSpec.TYPE)
        || item.getProviderId().equals(MkrSciBleDeviceSpec.TYPE)) {
      icon = ColorUtils.colorDrawable(this.icon.getContext(), icon, R.color.color_accent);
    }
    this.icon.setImageDrawable(icon);

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
    final SensorDiscoverer.ServiceConnectionError error = item.getConnectionErrorIfAny();
    if (error == null) {
      errorIcon.setVisibility(View.GONE);
      errorIcon.setOnClickListener(null);
    } else {
      errorIcon.setVisibility(View.VISIBLE);
      errorIcon.setContentDescription(
          context
              .getResources()
              .getString(R.string.snackbar_source_error, error.getErrorMessage()));
      errorIcon.setOnClickListener(getOnClickListener(error, fragmentManager));
    }

    boolean loading = item.isLoading();
    if (loading) {
      startLoadingAnimation();
    } else {
      refreshIcon.setEnabled(true);
      stopRotation();
      refreshIcon.setOnClickListener(
          new View.OnClickListener() {
            @Override
            public void onClick(View v) {
              itemView.sendAccessibilityEvent(AccessibilityEvent.TYPE_VIEW_FOCUSED);
              item.setIsLoading(true);
              startLoadingAnimation();
              onRefresh.run();
            }
          });
    }

    refreshIcon.setContentDescription(
        context.getResources().getString(R.string.refresh_device, name));
  }

  private void startLoadingAnimation() {
    refreshIcon.setEnabled(false);
    stopRotation();
    rotation = AnimationUtils.loadAnimation(refreshIcon.getContext(), R.anim.reload_rotate);
    refreshIcon.startAnimation(rotation);
    refreshIcon.setOnClickListener(null);
  }

  private void stopRotation() {
    if (rotation != null) {
      rotation.cancel();
      rotation = null;
      refreshIcon.setRotation(0);
    }
  }

  @NonNull
  private View.OnClickListener getOnClickListener(
      final SensorDiscoverer.ServiceConnectionError error, final FragmentManager fragmentManager) {
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
    collapsedIcon.setActive(isNowExpanded, true);
    item.setIsCurrentlyExpanded(isNowExpanded);
    updateActionStrings(isNowExpanded);
  }

  // Updates the action strings on the itemView row based on whether it is currently expanded.
  private void updateActionStrings(boolean isExpanded) {
    final String description =
        itemView
            .getContext()
            .getString(isExpanded ? R.string.btn_contract_service : R.string.btn_expand_service);
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
