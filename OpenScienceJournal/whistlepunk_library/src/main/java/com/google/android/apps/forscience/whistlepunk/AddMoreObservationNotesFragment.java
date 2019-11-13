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
package com.google.android.apps.forscience.whistlepunk;

import android.os.Bundle;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import com.google.android.apps.forscience.whistlepunk.actionarea.ActionAreaView;
import com.google.android.apps.forscience.whistlepunk.review.PinnedNoteAdapter;

/** Fragment that prompts the user to choose a note-adding tool from the action area. */
public class AddMoreObservationNotesFragment extends Fragment {
  private static final String EXTRA_IS_RUN_REVIEW = "isRunReview";
  private boolean isRunReview;

  public static AddMoreObservationNotesFragment newInstance(boolean isRunReview) {
    AddMoreObservationNotesFragment fragment = new AddMoreObservationNotesFragment();
    Bundle bundle = new Bundle();
    bundle.putBoolean(EXTRA_IS_RUN_REVIEW, isRunReview);
    fragment.setArguments(bundle);
    return fragment;
  }

  @Nullable
  @Override
  public View onCreateView(
      LayoutInflater inflater, @Nullable ViewGroup container, Bundle savedInstanceState) {
    Bundle args = getArguments();
    if (args != null) {
      isRunReview = args.getBoolean(EXTRA_IS_RUN_REVIEW, false);
    }
    int layout =
        isRunReview
            ? R.layout.add_more_observations_note_with_time_fragment
            : R.layout.add_more_observations_note_fragment;
    View rootView = inflater.inflate(layout, null);
    ActionAreaView actionArea = rootView.findViewById(R.id.action_area);
    NoteTakingActivity noteTakingActivity = (NoteTakingActivity) getActivity();
    actionArea.addItems(getContext(), noteTakingActivity.getActionAreaItems(), noteTakingActivity);
    if (isRunReview) {
      actionArea.updateColor(getContext(), R.style.BlueActionAreaIcon);
    }
    return rootView;
  }

  public void updateTime(long currentTimestamp, long runStartTimestamp) {
    if (isRunReview) {
      TextView view = getView().findViewById(R.id.title_with_time);
      view.setText(
          String.format(
              getString(R.string.add_note_to_time_text),
              PinnedNoteAdapter.getNoteTimeText(currentTimestamp, runStartTimestamp)));
    }
  }
}
