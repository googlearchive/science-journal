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
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import com.google.android.apps.forscience.whistlepunk.R;
import com.google.android.apps.forscience.whistlepunk.accounts.AppAccount;
import com.google.android.apps.forscience.whistlepunk.filemetadata.Change;
import com.google.android.apps.forscience.whistlepunk.filemetadata.Experiment;
import com.google.android.apps.forscience.whistlepunk.filemetadata.Label;
import com.google.android.apps.forscience.whistlepunk.metadata.GoosciExperiment.ChangedElement.ElementType;
import com.google.android.apps.forscience.whistlepunk.metadata.GoosciTextLabelValue;
import com.google.android.apps.forscience.whistlepunk.metadata.GoosciTextLabelValue.TextLabelValue;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.jakewharton.rxbinding2.widget.RxTextView;

/** Details view controller for TextLabel */
public class TextLabelDetailsFragment extends LabelDetailsFragment {
  private TextInputLayout noteTextLayout;
  private TextInputEditText noteText;

  public static TextLabelDetailsFragment newInstance(
      AppAccount appAccount, String experimentId, String trialId, Label originalLabel) {
    TextLabelDetailsFragment result = new TextLabelDetailsFragment();
    Bundle args = new Bundle();
    args.putString(LabelDetailsActivity.ARG_ACCOUNT_KEY, appAccount.getAccountKey());
    args.putString(LabelDetailsActivity.ARG_EXPERIMENT_ID, experimentId);
    args.putString(LabelDetailsActivity.ARG_TRIAL_ID, trialId);
    args.putParcelable(LabelDetailsActivity.ARG_LABEL, originalLabel);
    result.setArguments(args);
    return result;
  }

  public TextLabelDetailsFragment() {
    // Required empty public constructor
  }

  @Override
  public View onCreateView(
      LayoutInflater inflater, ViewGroup container, final Bundle savedInstanceState) {
    setHasOptionsMenu(true);
    noteTextLayout =
        (TextInputLayout) inflater.inflate(R.layout.text_label_details_fragment, container, false);
    noteText = noteTextLayout.findViewById(R.id.note_text);
    noteText.setText(originalLabel.getTextLabelValue().getText());
    noteText.post(() -> noteText.setSelection(noteText.getText().toString().length()));

    RxTextView.afterTextChangeEvents(noteText)
        .subscribe(
            events -> {
              verifyInput(noteText.getText().toString());
            });

    // TODO: Transition

    return noteTextLayout;
  }

  @Override
  public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
    inflater.inflate(R.menu.menu_text_label_details, menu);

    ActionBar actionBar = ((AppCompatActivity) getActivity()).getSupportActionBar();
    actionBar.setTitle(getActivity().getResources().getString(R.string.text_label_details_title));
    actionBar.setHomeAsUpIndicator(R.drawable.ic_close_white_24dp);
    actionBar.setHomeActionContentDescription(android.R.string.cancel);
    super.onCreateOptionsMenu(menu, inflater);
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    if (item.getItemId() == R.id.action_save) {
      experiment
          .firstElement()
          .subscribe(
              experiment -> {
                if (noteTextLayout.isErrorEnabled()) {
                  // No-op. Shows an error, doesn't exit.
                  return;
                }
                saveTextChanges(noteText.getText().toString(), experiment);
                returnToParent(false, null);
              });
      return true;
    }
    return super.onOptionsItemSelected(item);
  }

  private void saveTextChanges(String newText, Experiment experiment) {
    TextLabelValue labelValue =
        GoosciTextLabelValue.TextLabelValue.newBuilder().setText(newText).build();
    originalLabel.setLabelProtoData(labelValue);
    saveUpdatedOriginalLabel(
        experiment, Change.newModifyTypeChange(ElementType.NOTE, originalLabel.getLabelId()));
  }

  private boolean verifyInput(String text) {
    if (TextUtils.isEmpty(text)) {
      noteTextLayout.setError(
          getActivity().getResources().getString(R.string.empty_text_note_error));
      noteTextLayout.setErrorEnabled(true);
      return false;
    }
    noteTextLayout.setErrorEnabled(false);
    return true;
  }
}
