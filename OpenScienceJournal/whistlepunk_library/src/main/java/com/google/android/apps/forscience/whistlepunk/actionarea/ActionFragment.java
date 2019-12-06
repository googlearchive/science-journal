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

import android.os.Bundle;
import android.transition.Slide;
import android.view.Gravity;
import android.view.Menu;
import android.view.View;
import android.widget.TextView;
import androidx.fragment.app.Fragment;
import com.google.android.apps.forscience.whistlepunk.ActionController;
import com.google.android.apps.forscience.whistlepunk.NoteTakingActivity;
import com.google.android.apps.forscience.whistlepunk.R;
import com.google.android.apps.forscience.whistlepunk.SnackbarManager;
import com.google.android.apps.forscience.whistlepunk.WhistlePunkApplication;
import com.google.android.apps.forscience.whistlepunk.accounts.AppAccount;

public abstract class ActionFragment extends Fragment {
  static final String KEY_ACCOUNT_KEY = "accountKey";
  static final String KEY_EXPERIMENT_ID = "experimentId";
  ActionController actionController;
  String currentTime;

  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setEnterTransition(new Slide(Gravity.BOTTOM));
    setExitTransition(new Slide(Gravity.BOTTOM));
    setHasOptionsMenu(true);

    actionController =
        new ActionController(getAppAccount(),
            getExperimentId(), new SnackbarManager(), getContext());
  }

  @Override
  public void onPrepareOptionsMenu(Menu menu) {
    if (isVisible()) {
      updateTitle();
    }
  }

  void updateTitle() {
    NoteTakingActivity activity = (NoteTakingActivity) getActivity();
    if (activity != null) {
      String title = getTitle();
      if (activity.isTwoPane()) {
        if (getView().findViewById(R.id.title_bar_text) != null) {
          ((TextView) getView().findViewById(R.id.title_bar_text)).setText(title);
        }
      } else {
        activity.updateTitleByToolFragment(title);
      }
    }
  }

  AppAccount getAppAccount() {
    return WhistlePunkApplication.getAccount(getContext(), getArguments(), KEY_ACCOUNT_KEY);
  }

  String getExperimentId() {
    return getArguments().getString(KEY_EXPERIMENT_ID);
  }

  private boolean isTwoPane() {
    NoteTakingActivity activity = (NoteTakingActivity) getActivity();
    return activity != null && activity.isTwoPane();
  }

  void setUpTitleBar(View rootView, boolean hideDuringRecording,
      int titleResource, int iconResource) {
    NoteTakingActivity activity = (NoteTakingActivity) getActivity();
    actionController.attachTitleBar(rootView.findViewById(R.id.tool_pane_title_bar),
        isTwoPane(), v -> {if(activity != null) activity.closeToolFragment();},
        hideDuringRecording, titleResource, iconResource);
  }

  public void updateTime(String timestamp) {
    currentTime = timestamp;
    updateTitle();
  }

  protected abstract String getTitle();
}
