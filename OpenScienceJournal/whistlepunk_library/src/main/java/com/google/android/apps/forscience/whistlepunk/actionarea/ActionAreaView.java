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
import android.content.res.TypedArray;
import android.graphics.drawable.Drawable;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.res.ResourcesCompat;
import android.util.AttributeSet;
import android.view.ContextThemeWrapper;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.google.android.apps.forscience.whistlepunk.R;
import java.util.ArrayList;
import java.util.List;

/**
 * Reusable custom UI component for the action area in Science Journal.
 *
 * <p>ActionAreaView provides an area at either the bottom or the right side of the view to allow
 * users to add notes, take snapshots, add sensors, and take other actions depending on the
 * remainder of the view.
 *
 * <p>The action area will always be visible on screen even if the remaining content on screen is
 * scrollable.
 *
 * <p>The actions in the action area can be populated using {@code ActionAreaView#addItems}. All
 * actions should be declared in ActionAreaItem to maximize code readability and reuse throughout
 * the many views that contain an action area.
 *
 * <p>If more than 4 actions are added to an action area, the fourth action will be replaced with a
 * "More" action that opens a separate view with the remaining actions. TODO(b/132651474): Implement
 * "More" action
 *
 * <p>To include an action area at the bottom, simply add the following to your layout resource file
 * replacing {@code app:isBottomLayout="true"} with {@code app:isBottomLayout="false"} and swapping
 * the {@code layout_width} and {@code layout_height} if you want the app bar on the right side of
 * the screen instead of at the bottom.
 *
 * <pre>
 * &lt;com.google.android.apps.forscience.whistlepunk.actionarea.ActionAreaView
 *     android:id="@+id/action_area"
 *     android:layout_width="match_parent"
 *     android:layout_height="@dimen/action_area_height"
 *     app:isBottomLayout="true" /&gt;
 * </pre>
 */
public class ActionAreaView extends LinearLayout {
  private boolean isBottomLayout = true;
  private final List<ActionAreaItemView> actionAreaItemViews = new ArrayList<>();
  private ActionAreaListener listener;

  public ActionAreaView(@NonNull Context context, @Nullable AttributeSet attrs) {
    super(context, attrs);
    initView(context, attrs);
  }

  private void initView(@NonNull Context context, @Nullable AttributeSet attrs) {
    if (attrs != null) {
      TypedArray a =
          context.getTheme().obtainStyledAttributes(attrs, R.styleable.ActionAreaView, 0, 0);
      isBottomLayout = a.getBoolean(R.styleable.ActionAreaView_isBottomLayout, true);
    }
  }

  /**
   * Sets up the action area with the passed in items and listener.
   *
   * <p>If addItems has already been called, the action area and listener will be cleared and reset
   * with the new list and listener.
   *
   * @param context The current context
   * @param actionAreaItems The items to add to the action area
   * @param listener The callback for when an item is clicked
   */
  public void addItems(
      Context context, ActionAreaItem[] actionAreaItems, ActionAreaListener listener) {
    // Removing all previously added action area items
    actionAreaItemViews.clear();
    removeAllViews();
    this.listener = listener;
    if (isBottomLayout) {
      setOrientation(HORIZONTAL);
      setBackground(getResources().getDrawable(R.drawable.action_area_view_bottom_bg));
    } else {
      setOrientation(VERTICAL);
      setBackground(getResources().getDrawable(R.drawable.action_area_view_right_bg));
    }
    for (ActionAreaItem item : actionAreaItems) {
      ActionAreaItemView view = new ActionAreaItemView(context, item);
      actionAreaItemViews.add(view);
      addView(view);
    }
  }

  /**
   * Changes the color of all the items in the action area.
   *
   * @param context The current context
   * @param style The action area style declared in @see styles.xml
   */
  public void updateColor(Context context, int style) {
    for (ActionAreaItemView item : actionAreaItemViews) {
      item.updateView(context, style);
    }
  }

  private class ActionAreaItemView extends LinearLayout {
    private final ActionAreaItem actionAreaItem;

    private ActionAreaItemView(Context context, ActionAreaItem actionAreaItem) {
      super(context);
      this.actionAreaItem = actionAreaItem;
      initView(context);
    }

    private void initView(Context context) {
      inflate(context, R.layout.action_area_item, this);
      if (isBottomLayout) {
        setLayoutParams(new LayoutParams(0, LayoutParams.MATCH_PARENT, 1));
      } else {
        setLayoutParams(new LayoutParams(LayoutParams.MATCH_PARENT, 0, 1));
      }
      setGravity(Gravity.CENTER);
      TextView textView = findViewById(R.id.text_view);
      textView.setText(actionAreaItem.getContentDescriptionId());
      textView.setContentDescription(
          getResources().getString(actionAreaItem.getContentDescriptionId()));
      ContextThemeWrapper wrapper = new ContextThemeWrapper(context, R.style.DefaultActionAreaIcon);
      Drawable drawable =
          ResourcesCompat.getDrawable(
              getResources(), actionAreaItem.getIconId(), wrapper.getTheme());
      textView.setCompoundDrawablesWithIntrinsicBounds(null, drawable, null, null);
      setOnClickListener((View view) -> listener.onClick(actionAreaItem));
    }

    private void updateView(Context context, int style) {
      ContextThemeWrapper wrapper = new ContextThemeWrapper(context, style);
      Drawable drawable =
          ResourcesCompat.getDrawable(
              getResources(), actionAreaItem.getIconId(), wrapper.getTheme());
      ((TextView) findViewById(R.id.text_view))
          .setCompoundDrawablesRelativeWithIntrinsicBounds(null, drawable, null, null);
    }
  }

  /**
   * Listener for clicks in the action area.
   *
   * <p>Users must define what happens when an item in the action area is clicked.
   */
  public interface ActionAreaListener {
    /**
     * Called when an item in the action area is clicked on.
     *
     * @param item The item that has been clicked
     */
    void onClick(ActionAreaItem item);
  }
}
