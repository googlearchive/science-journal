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

package com.google.android.apps.forscience.whistlepunk.review;

import android.content.Intent;
import android.os.Bundle;
import androidx.fragment.app.Fragment;
import androidx.core.app.NavUtils;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import com.google.android.apps.forscience.javalib.Success;
import com.google.android.apps.forscience.whistlepunk.AppSingleton;
import com.google.android.apps.forscience.whistlepunk.DataController;
import com.google.android.apps.forscience.whistlepunk.LoggingConsumer;
import com.google.android.apps.forscience.whistlepunk.R;
import com.google.android.apps.forscience.whistlepunk.WhistlePunkApplication;
import com.google.android.apps.forscience.whistlepunk.accounts.AppAccount;
import com.google.android.apps.forscience.whistlepunk.analytics.TrackerConstants;
import com.google.android.apps.forscience.whistlepunk.filemetadata.Experiment;

/** Fragment for updating a run's metadata. */
public class UpdateRunFragment extends Fragment {

  private static final String TAG = "UpdateRunFragment";

  /** Account key for the AppAccount */
  public static final String ARG_ACCOUNT_KEY = "account_key";
  /** Run ID (start label ID) for the run to update. */
  public static final String ARG_RUN_ID = "run_id";

  public static final String ARG_EXP_ID = "exp_id";

  private AppAccount appAccount;
  private String runId;
  private String experimentId;
  private Experiment experiment;
  private EditText runTitle;

  public UpdateRunFragment() {}

  public static UpdateRunFragment newInstance(
      AppAccount appAccount, String runId, String experimentId) {
    UpdateRunFragment fragment = new UpdateRunFragment();
    Bundle args = new Bundle();
    args.putString(ARG_ACCOUNT_KEY, appAccount.getAccountKey());
    args.putString(ARG_RUN_ID, runId);
    args.putString(ARG_EXP_ID, experimentId);
    fragment.setArguments(args);
    return fragment;
  }

  @Override
  public void onStart() {
    super.onStart();
    appAccount = WhistlePunkApplication.getAccount(getContext(), getArguments(), ARG_ACCOUNT_KEY);
    runId = getArguments().getString(ARG_RUN_ID);
    experimentId = getArguments().getString(ARG_EXP_ID);

    getDataController()
        .getExperimentById(
            experimentId,
            new LoggingConsumer<Experiment>(TAG, "retrieve run") {
              @Override
              public void success(Experiment experiment) {
                UpdateRunFragment.this.experiment = experiment;
                runTitle.setText(experiment.getTrial(runId).getTitle(getActivity()));
                runTitle.selectAll();
              }
            });
    WhistlePunkApplication.getUsageTracker(getActivity())
        .trackScreenView(TrackerConstants.SCREEN_UPDATE_RUN);
  }

  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setHasOptionsMenu(true);
  }

  @Override
  public View onCreateView(
      LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
    View view = inflater.inflate(R.layout.fragment_update_run, container, false);
    runTitle = (EditText) view.findViewById(R.id.run_title);
    return view;
  }

  private DataController getDataController() {
    return AppSingleton.getInstance(getActivity()).getDataController(appAccount);
  }

  @Override
  public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
    inflater.inflate(R.menu.menu_update_run, menu);

    ActionBar actionBar = ((AppCompatActivity) getActivity()).getSupportActionBar();
    actionBar.setHomeAsUpIndicator(R.drawable.ic_close_white_24dp);
    actionBar.setHomeActionContentDescription(android.R.string.cancel);

    super.onCreateOptionsMenu(menu, inflater);
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    int id = item.getItemId();

    //noinspection SimplifiableIfStatement
    if (id == android.R.id.home) {
      returnToRunReview();
      return true;
    } else if (id == R.id.action_save) {
      saveAndReturn();
      return true;
    }

    return super.onOptionsItemSelected(item);
  }

  private void saveAndReturn() {
    experiment.getTrial(runId).setTitle(runTitle.getText().toString().trim());
    experiment.updateTrial(experiment.getTrial(runId));
    getDataController()
        .updateExperiment(
            experimentId,
            new LoggingConsumer<Success>(TAG, "updating run") {
              @Override
              public void success(Success value) {
                returnToRunReview();
              }
            });
  }

  private void returnToRunReview() {
    Intent upIntent = NavUtils.getParentActivityIntent(getActivity());
    upIntent.putExtra(RunReviewActivity.EXTRA_FROM_RECORD, false);
    upIntent.putExtra(RunReviewFragment.ARG_ACCOUNT_KEY, appAccount.getAccountKey());
    upIntent.putExtra(RunReviewFragment.ARG_START_LABEL_ID, runId);
    upIntent.putExtra(RunReviewFragment.ARG_EXPERIMENT_ID, experimentId);
    upIntent.putExtra(RunReviewFragment.ARG_CLAIM_EXPERIMENTS_MODE, false);
    NavUtils.navigateUpTo(getActivity(), upIntent);
  }
}
