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
import com.google.android.apps.forscience.whistlepunk.actionarea.ActionAreaView;

/** Fragment that prompts the user to choose a note-adding tool from the action area. */
public class AddMoreObservationNotesFragment extends Fragment {

  public static Fragment newInstance() {
    AddMoreObservationNotesFragment fragment = new AddMoreObservationNotesFragment();
    fragment.setArguments(new Bundle());
    return fragment;
  }

  @Nullable
  @Override
  public View onCreateView(
      LayoutInflater inflater, @Nullable ViewGroup container, Bundle savedInstanceState) {
    View rootView = inflater.inflate(R.layout.add_more_observations_note_fragment, null);
    ActionAreaView actionArea = rootView.findViewById(R.id.action_area);
    NoteTakingActivity noteTakingActivity = (NoteTakingActivity) getActivity();
    actionArea.addItems(getContext(), noteTakingActivity.getActionAreaItems(), noteTakingActivity);
    return rootView;
  }
}
