/*
 *  Copyright 2019 Google Inc. All Rights Reserved.
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
package com.google.android.apps.forscience.whistlepunk.actionarea;

import android.content.Context;
import android.graphics.drawable.Drawable;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.res.ResourcesCompat;
import android.util.AttributeSet;
import android.view.ContextThemeWrapper;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.google.android.apps.forscience.whistlepunk.R;
import com.google.android.apps.forscience.whistlepunk.actionarea.ActionAreaView.ActionAreaListener;

/**
 * Reusable custom UI component for the action area items in the action area in Science Journal.
 *
 * <p>ActionAreaItemView provides a clickable view with a description and icon in the action area to
 * allow users to add a note, take a snapshot, add a sensor, or take other actions depending on the
 * remainder of the view.
 */
public class ActionAreaItemView extends LinearLayout {
  private ActionAreaItem actionAreaItem;

  public ActionAreaItemView(@NonNull Context context, @Nullable AttributeSet attrs) {
    super(context, attrs);
    inflate(context, R.layout.action_area_item, this);
  }

  protected void setActionAreaItem(
      Context context, ActionAreaItem actionAreaItem, ActionAreaListener listener) {
    this.actionAreaItem = actionAreaItem;
    TextView textView = findViewById(R.id.text_view);
    if (actionAreaItem != null && listener != null) {
      textView.setText(actionAreaItem.getContentDescriptionId());
      textView.setContentDescription(
          getResources().getString(actionAreaItem.getContentDescriptionId()));
      updateView(context, R.style.DefaultActionAreaIcon);
      setOnClickListener((View view) -> listener.onClick(actionAreaItem));
    } else {
      textView.setText("");
      textView.setContentDescription("");
      textView.setCompoundDrawablesRelativeWithIntrinsicBounds(null, null, null, null);
      setOnClickListener(null);
    }
  }

  protected void updateView(Context context, int style) {
    if (actionAreaItem != null) {
      ContextThemeWrapper wrapper = new ContextThemeWrapper(context, style);
      // Use the correctly colored icon and on touch ripple based on the passed in style
      Drawable drawable =
          ResourcesCompat.getDrawable(
              getResources(), actionAreaItem.getIconId(), wrapper.getTheme());
      ((TextView) findViewById(R.id.text_view))
          .setCompoundDrawablesRelativeWithIntrinsicBounds(null, drawable, null, null);
      setBackground(ResourcesCompat.getDrawable(
          getResources(), R.drawable.action_area_ripple, wrapper.getTheme()));
    }
  }

  protected ActionAreaItem getItem() {
    return actionAreaItem;
  }
}
