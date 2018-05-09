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

package com.google.android.apps.forscience.whistlepunk.project.experiment;

import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import androidx.annotation.Nullable;
import android.support.design.widget.TextInputEditText;
import android.support.design.widget.TextInputLayout;
import androidx.fragment.app.DialogFragment;
import androidx.appcompat.app.AlertDialog;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import android.widget.Button;
import com.google.android.apps.forscience.whistlepunk.AppSingleton;
import com.google.android.apps.forscience.whistlepunk.DataController;
import com.google.android.apps.forscience.whistlepunk.R;
import com.google.android.apps.forscience.whistlepunk.RxDataController;
import com.jakewharton.rxbinding2.widget.RxTextView;

/** Dialog prompting a user to name an experiment. */
public class NameExperimentDialog extends DialogFragment {
  public static final String TAG = "NameExperimentDialog";
  private static final String KEY_EXPERIMENT_ID = "experiment_id";
  private static final String KEY_SAVED_TITLE = "saved_title";

  interface OnExperimentTitleChangeListener {
    void onTitleChangedFromDialog();
  }

  private String experimentId;
  private TextInputEditText input;
  private TextInputLayout inputLayout;

  public static NameExperimentDialog newInstance(String experimentId) {
    NameExperimentDialog dialog = new NameExperimentDialog();
    Bundle args = new Bundle();
    args.putString(KEY_EXPERIMENT_ID, experimentId);
    dialog.setArguments(args);
    return dialog;
  }

  public NameExperimentDialog() {}

  @Override
  public void onCreate(@Nullable Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
  }

  @Override
  public Dialog onCreateDialog(Bundle savedInstanceState) {
    String previousTitle = getResources().getString(R.string.default_experiment_name);
    if (savedInstanceState != null) {
      previousTitle = savedInstanceState.getString(KEY_SAVED_TITLE);
    }
    experimentId = getArguments().getString(KEY_EXPERIMENT_ID);

    final AlertDialog.Builder alertDialog = new AlertDialog.Builder(getActivity());
    ViewGroup rootView =
        (ViewGroup)
            LayoutInflater.from(getActivity()).inflate(R.layout.name_experiment_dialog, null);
    input = (TextInputEditText) rootView.findViewById(R.id.title);
    inputLayout = (TextInputLayout) rootView.findViewById(R.id.title_input_layout);
    input.setText(previousTitle);
    if (savedInstanceState == null) {
      input.selectAll();
    }
    // TODO: Max char count?
    // TODO: Pop up the keyboard immediately.

    alertDialog.setView(rootView);

    alertDialog.setTitle(R.string.name_experiment_dialog_title);
    alertDialog.setCancelable(true);
    alertDialog.setPositiveButton(
        android.R.string.ok, (dialogInterface, i) -> saveNewTitle(alertDialog.getContext()));
    AlertDialog result = alertDialog.create();
    RxTextView.afterTextChangeEvents(input)
        .subscribe(
            event -> {
              Button button = result.getButton(DialogInterface.BUTTON_POSITIVE);
              if (input.getText().toString().length() == 0) {
                if (getActivity() != null) {
                  inputLayout.setError(
                      getActivity()
                          .getResources()
                          .getString(R.string.empty_experiment_title_error));
                }
                if (button != null) {
                  button.setEnabled(false);
                }
              } else if (button != null) {
                inputLayout.setErrorEnabled(false);
                button.setEnabled(true);
              }
            });

    return result;
  }

  @Override
  public void onSaveInstanceState(Bundle outState) {
    outState.putString(KEY_SAVED_TITLE, input.getText().toString());
    super.onSaveInstanceState(outState);
  }

  @Override
  public void onCancel(DialogInterface dialog) {
    if (getActivity() == null) {
      return;
    }
    DataController dc = AppSingleton.getInstance(getActivity()).getDataController();
    RxDataController.getExperimentById(dc, experimentId)
        .subscribe(
            experiment -> {
              // Set the title to something so that we don't try to save it again later.
              String title = getResources().getString(R.string.default_experiment_name);
              experiment.setTitle(title);
              RxDataController.updateExperiment(dc, experiment)
                  .subscribe(
                      () -> {
                        super.onCancel(dialog);
                      });
            });
  }

  private void saveNewTitle(Context context) {
    DataController dc = AppSingleton.getInstance(context).getDataController();
    RxDataController.getExperimentById(dc, experimentId)
        .subscribe(
            experiment -> {
              String title = input.getText().toString().trim();
              if (TextUtils.isEmpty(title)) {
                title = getResources().getString(R.string.default_experiment_name);
              }
              experiment.setTitle(title);
              RxDataController.updateExperiment(dc, experiment)
                  .subscribe(
                      () ->
                          ((OnExperimentTitleChangeListener) getParentFragment())
                              .onTitleChangedFromDialog());
            });
  }
}
