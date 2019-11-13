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
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import android.util.AttributeSet;
import android.view.View;
import androidx.cardview.widget.CardView;
import com.google.android.apps.forscience.whistlepunk.R;
import java.util.Objects;

/**
 * Reusable custom UI component for the action area in Science Journal.
 *
 * <p>ActionAreaView provides an area at the bottom of the view to allow users to add notes, take
 * snapshots, add sensors, and take other actions depending on the remainder of the view.
 *
 * <p>The action area will always be visible on screen even if the remaining content on screen is
 * scrollable.
 *
 * <p>The actions in the action area can be populated using {@code ActionAreaView#addItems}. All
 * actions should be declared in ActionAreaItem to maximize code readability and reuse throughout
 * the many views that contain an action area.
 *
 * <p>To include an action area, simply add the following to your layout resource file.
 *
 * <pre>
 * &lt;com.google.android.apps.forscience.whistlepunk.actionarea.ActionAreaView
 *     android:id="@+id/action_area"
 *     style="@style/DefaultActionArea" /&gt;
 * </pre>
 */
public class ActionAreaView extends CardView {
  private final ActionAreaItemView[] actionAreaItemViews = new ActionAreaItemView[4];

  public ActionAreaView(@NonNull Context context, @Nullable AttributeSet attrs) {
    super(context, attrs);
    initView(context);
  }

  private void initView(Context context) {
    inflate(context, R.layout.action_area, this);
    actionAreaItemViews[0] = findViewById(R.id.action_area_item_0);
    actionAreaItemViews[1] = findViewById(R.id.action_area_item_1);
    actionAreaItemViews[2] = findViewById(R.id.action_area_item_2);
    actionAreaItemViews[3] = findViewById(R.id.action_area_item_3);
  }

  /**
   * Sets up the action area with the passed in items and listener.
   *
   * <p>If addItems has already been called, the action area and listener will be cleared and reset
   * with the new list and listener.
   *
   * @param context The current context
   * @param actionAreaItems The items to add to the action area; must have 4 or less items
   * @param listener The callback for when an item is clicked
   */
  public void addItems(
      Context context, ActionAreaItem[] actionAreaItems, ActionAreaListener listener) {
    if (actionAreaItems.length > 4) {
      throw new AssertionError("Action area can only hold 4 items");
    }

    for (int i = 0; i < 4; i++) {
      ActionAreaItem item = null;
      if (actionAreaItems.length > i) {
        item = actionAreaItems[i];
      }
      actionAreaItemViews[i].setActionAreaItem(context, item, listener);
    }
  }

  /**
   * Returns the item view for the passed in item or null if the item isn't in the Action Area
   *
   * @param item The item to look for
   * @return The ActionAreaItemView for that item
   */
  private ActionAreaItemView getItemView(ActionAreaItem item) {
    for (ActionAreaItemView view : actionAreaItemViews) {
      if (Objects.equals(item, view.getItem())) {
        return view;
      }
    }
    return null;
  }

  /**
   * Disables or re-enables the Add Sensor ActionAreaItem
   *
   * @param context The current context
   * @param disabled If the button should be disabled
   */
  public void disableAddSensorItem(Context context, boolean disabled) {
    ActionAreaItemView itemView = getItemView(ActionAreaItem.ADD_SENSOR);
    if (itemView != null && context != null) {
      int style = disabled ? R.style.DisabledActionAreaIcon : R.style.DefaultActionAreaIcon;
      itemView.updateView(context, style);
      itemView.setClickable(!disabled);
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

  public void setUpScrollListener(View scrollingView) {
    scrollingView
        .getViewTreeObserver()
        .addOnScrollChangedListener(
            () ->
                this.setSelected(
                    scrollingView.canScrollVertically(1 /*positive to check scrolling down*/)));
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
