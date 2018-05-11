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

import androidx.recyclerview.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import android.widget.TextView;

/** Simple RecyclerView.Adapter that always contains a single TextView */
public class HeaderAdapter extends RecyclerView.Adapter<HeaderAdapter.ViewHolder> {
  private final int layoutId;
  private int stringResourceId;

  public HeaderAdapter(int layoutId, int stringResourceId) {
    this.stringResourceId = stringResourceId;
    this.layoutId = layoutId;
  }

  @Override
  public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
    TextView textView =
        (TextView) LayoutInflater.from(parent.getContext()).inflate(layoutId, parent, false);
    return new ViewHolder(textView);
  }

  @Override
  public void onBindViewHolder(ViewHolder holder, int position) {
    holder.textView.setText(stringResourceId);
  }

  @Override
  public int getItemCount() {
    return 1;
  }

  @Override
  public int getItemViewType(int position) {
    return layoutId;
  }

  public static class ViewHolder extends RecyclerView.ViewHolder {
    public TextView textView;

    ViewHolder(TextView textView) {
      super(textView);
      this.textView = textView;
    }
  }
}
