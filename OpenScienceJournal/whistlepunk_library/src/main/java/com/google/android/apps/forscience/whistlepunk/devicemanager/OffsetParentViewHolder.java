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
import com.bignerdranch.expandablerecyclerview.ViewHolder.ParentViewHolder;
import com.google.common.base.Supplier;

public class OffsetParentViewHolder extends ParentViewHolder {
  private final Supplier<Integer> globalPositionOffset;

  /**
   * Default constructor.
   *
   * @param itemView The {@link View} being hosted in this ViewHolder
   */
  public OffsetParentViewHolder(View itemView, Supplier<Integer> globalPositionOffset) {
    super(itemView);
    this.globalPositionOffset = globalPositionOffset;
  }

  @Override
  public void setParentListItemExpandCollapseListener(
      final ParentListItemExpandCollapseListener superListener) {
    super.setParentListItemExpandCollapseListener(
        new ParentListItemExpandCollapseListener() {
          @Override
          public void onParentListItemExpanded(int position) {
            superListener.onParentListItemExpanded(position - globalPositionOffset.get());
          }

          @Override
          public void onParentListItemCollapsed(int position) {
            superListener.onParentListItemCollapsed(position - globalPositionOffset.get());
          }
        });
  }
}
