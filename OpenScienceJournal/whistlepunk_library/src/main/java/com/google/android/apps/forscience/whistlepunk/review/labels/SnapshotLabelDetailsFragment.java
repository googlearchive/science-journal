/*
 *  Copyright 2017 Google Inc. All Rights Reserved.
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

package com.google.android.apps.forscience.whistlepunk.review.labels;

import android.os.Bundle;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.View;
import android.view.ViewGroup;
import com.google.android.apps.forscience.whistlepunk.NoteViewHolder;
import com.google.android.apps.forscience.whistlepunk.R;
import com.google.android.apps.forscience.whistlepunk.accounts.AppAccount;
import com.google.android.apps.forscience.whistlepunk.filemetadata.Label;

/** Details view controller for a Snapshot label. */
public class SnapshotLabelDetailsFragment extends LabelDetailsFragment {

  public static SnapshotLabelDetailsFragment newInstance(
      AppAccount appAccount, String experimentId, String trialId, Label originalLabel) {
    SnapshotLabelDetailsFragment result = new SnapshotLabelDetailsFragment();
    Bundle args = new Bundle();
    args.putString(LabelDetailsActivity.ARG_ACCOUNT_KEY, appAccount.getAccountKey());
    args.putString(LabelDetailsActivity.ARG_EXPERIMENT_ID, experimentId);
    args.putString(LabelDetailsActivity.ARG_TRIAL_ID, trialId);
    args.putParcelable(LabelDetailsActivity.ARG_LABEL, originalLabel);
    result.setArguments(args);
    return result;
  }

  public SnapshotLabelDetailsFragment() {
    // Required empty public constructor
  }

  @Override
  public View onCreateView(
      LayoutInflater inflater, ViewGroup container, final Bundle savedInstanceState) {
    setHasOptionsMenu(true);
    View rootView =
        inflater.inflate(R.layout.sensor_readings_label_details_fragment, container, false);

    // TODO: Consider using a ListView instead of inflating each view?
    NoteViewHolder.loadSnapshotsIntoList(
        (ViewGroup) rootView.findViewById(R.id.snapshot_values_list), originalLabel, appAccount);
    setupCaption(rootView);
    setupDetails(rootView);

    return rootView;
  }

  @Override
  public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
    inflater.inflate(R.menu.menu_sensor_item_label_details, menu);

    ActionBar actionBar = ((AppCompatActivity) getActivity()).getSupportActionBar();
    actionBar.setTitle(
        getActivity().getResources().getString(R.string.snapshot_label_details_title));

    super.onCreateOptionsMenu(menu, inflater);
  }
}
