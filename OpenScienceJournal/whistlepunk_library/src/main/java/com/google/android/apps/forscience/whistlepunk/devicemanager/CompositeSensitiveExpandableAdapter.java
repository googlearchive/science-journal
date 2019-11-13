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

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import android.view.ViewGroup;
import com.bignerdranch.expandablerecyclerview.Adapter.ExpandableRecyclerAdapter;
import com.bignerdranch.expandablerecyclerview.Model.ParentListItem;
import com.bignerdranch.expandablerecyclerview.ViewHolder.ChildViewHolder;
import com.google.common.base.Supplier;
import java.util.List;

public abstract class CompositeSensitiveExpandableAdapter<
        PVH extends OffsetParentViewHolder, CVH extends ChildViewHolder>
    extends ExpandableRecyclerAdapter<PVH, CVH>
    implements CompositeRecyclerAdapter.CompositeSensitiveAdapter {
  private final int typeOffset;
  private int globalAdapterStartPosition = 0;

  /**
   * @param uniqueId Each instance of this class that is in the same composite needs a distinct
   *     uniqueId (this is used to make sure that each instance gets a distinct range of view
   *     types).
   */
  public CompositeSensitiveExpandableAdapter(
      @NonNull List<? extends ParentListItem> parentItemList, int uniqueId) {
    super(parentItemList);
    typeOffset = uniqueId * 2;
  }

  @Override
  public int getItemViewType(int position) {
    return super.getItemViewType(position) + typeOffset;
  }

  @Override
  public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup viewGroup, int viewType) {
    return super.onCreateViewHolder(viewGroup, viewType - typeOffset);
  }

  protected Supplier<Integer> offsetSupplier() {
    return new Supplier<Integer>() {
      @Override
      public Integer get() {
        return globalAdapterStartPosition;
      }
    };
  }

  @Override
  public void informGlobalAdapterStartPosition(int startPosition) {
    globalAdapterStartPosition = startPosition;
  }
}
