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

import android.content.Context;
import android.os.Bundle;
import androidx.fragment.app.Fragment;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import android.text.InputType;
import android.text.TextUtils;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.TextView;
import com.google.android.apps.forscience.whistlepunk.AppSingleton;
import com.google.android.apps.forscience.whistlepunk.Clock;
import com.google.android.apps.forscience.whistlepunk.DataController;
import com.google.android.apps.forscience.whistlepunk.DeletedLabel;
import com.google.android.apps.forscience.whistlepunk.LoggingConsumer;
import com.google.android.apps.forscience.whistlepunk.R;
import com.google.android.apps.forscience.whistlepunk.RxDataController;
import com.google.android.apps.forscience.whistlepunk.RxEvent;
import com.google.android.apps.forscience.whistlepunk.WhistlePunkApplication;
import com.google.android.apps.forscience.whistlepunk.accounts.AppAccount;
import com.google.android.apps.forscience.whistlepunk.filemetadata.Change;
import com.google.android.apps.forscience.whistlepunk.filemetadata.Experiment;
import com.google.android.apps.forscience.whistlepunk.filemetadata.Label;
import com.google.android.apps.forscience.whistlepunk.filemetadata.Trial;
import com.google.android.apps.forscience.whistlepunk.metadata.GoosciCaption;
import com.google.android.apps.forscience.whistlepunk.metadata.GoosciCaption.Caption;
import com.google.android.apps.forscience.whistlepunk.metadata.GoosciExperiment.ChangedElement.ElementType;
import io.reactivex.Maybe;
import io.reactivex.functions.Consumer;
import io.reactivex.subjects.BehaviorSubject;
import java.text.SimpleDateFormat;
import java.util.Locale;

/** General fragment for label details views */
abstract class LabelDetailsFragment extends Fragment {
  private static final String KEY_SAVED_LABEL = "saved_label";
  private static final String TAG = "LabelDetails";
  protected AppAccount appAccount;
  protected String experimentId;
  private String trialId = null;
  protected BehaviorSubject<Experiment> experiment = BehaviorSubject.create();
  protected Label originalLabel;

  private EditText caption;
  private Clock clock;
  private RxEvent saved = new RxEvent();

  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    appAccount =
        WhistlePunkApplication.getAccount(
            getContext(), getArguments(), LabelDetailsActivity.ARG_ACCOUNT_KEY);
    experimentId = getArguments().getString(LabelDetailsActivity.ARG_EXPERIMENT_ID);
    trialId = getArguments().getString(LabelDetailsActivity.ARG_TRIAL_ID);
    if (savedInstanceState == null) {
      originalLabel = getArguments().getParcelable(LabelDetailsActivity.ARG_LABEL);
    } else {
      // Load the updated label
      originalLabel = savedInstanceState.getParcelable(KEY_SAVED_LABEL);
    }

    RxDataController.getExperimentById(getDataController(), experimentId)
        .subscribe(
            this::attachExperiment,
            error -> {
              if (Log.isLoggable(TAG, Log.ERROR)) {
                Log.e(TAG, "LabelDetailsFragment attachExperiment failed", error);
              }
              throw new IllegalStateException(
                  "LabelDetailsFragment attachExperiment failed", error);
            });
    experiment.firstElement().subscribe(experiment -> getActivity().invalidateOptionsMenu());

    clock = AppSingleton.getInstance(getActivity()).getSensorEnvironment().getDefaultClock();
  }

  @Override
  public void onSaveInstanceState(Bundle outState) {
    outState.putParcelable(KEY_SAVED_LABEL, originalLabel);
    super.onSaveInstanceState(outState);
  }

  @Override
  public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
    ActionBar actionBar = ((AppCompatActivity) getActivity()).getSupportActionBar();

    actionBar.setDisplayHomeAsUpEnabled(true);
    actionBar.setHomeAsUpIndicator(R.drawable.ic_close_white_24dp);
    actionBar.setHomeActionContentDescription(android.R.string.cancel);

    super.onCreateOptionsMenu(menu, inflater);
  }

  @Override
  public void onPrepareOptionsMenu(Menu menu) {
    MenuItem item = menu.findItem(R.id.action_delete);
    // Disable delete until the experiment is loaded.
    item.setEnabled(experiment.hasValue());
    super.onPrepareOptionsMenu(menu);
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    int id = item.getItemId();
    if (id == android.R.id.home) {
      saved.onDoneHappening();
      boolean labelDeleted = false;
      returnToParent(labelDeleted, null);
      return true;
    } else if (id == R.id.action_save) {
      saved.onHappened();
      returnToParent(false, null);
    } else if (id == R.id.action_delete) {
      deleteAndReturnToParent();
      return true;
    }
    return super.onOptionsItemSelected(item);
  }

  protected void saveUpdatedOriginalLabel(Experiment experiment, Change change) {
    // TODO: Log analytics here? That would send an event per keystroke.
    if (TextUtils.isEmpty(trialId)) {
      RxDataController.updateLabel(
              getDataController(), experiment, originalLabel, experiment, change)
          .subscribe(LoggingConsumer.observe(TAG, "update"));
    } else {
      Trial trial = experiment.getTrial(trialId);
      trial.updateLabel(experiment, originalLabel, change);
      experiment.updateTrial(trial);
      RxDataController.updateExperiment(getDataController(), experiment, true)
          .subscribe(LoggingConsumer.observe(TAG, "update"));
    }
  }

  private void attachExperiment(Experiment experiment) {
    this.experiment.onNext(experiment);
  }

  protected DataController getDataController() {
    return AppSingleton.getInstance(getActivity()).getDataController(appAccount);
  }

  protected void returnToParent(boolean labelDeleted, Consumer<Context> assetDeleter) {
    if (getActivity() == null) {
      return;
    }
    if (!experiment.hasValue()) {
      // We didn't load yet, just go back.
      getActivity().onBackPressed();
    }
    // Need to either fake a back button or send the right args
    ((LabelDetailsActivity) getActivity())
        .returnToParent(labelDeleted, new DeletedLabel(originalLabel, assetDeleter));
  }

  protected void deleteAndReturnToParent() {
    experiment
        .firstElement()
        .flatMap(
            experiment -> {
              Consumer<Context> assetDeleter = deleteLabelFromExperiment(experiment);
              return RxDataController.updateExperiment(getDataController(), experiment, true)
                  .andThen(Maybe.just(assetDeleter));
            })
        .subscribe(
            assetDeleter -> returnToParent(/* label deleted */ true, assetDeleter),
            LoggingConsumer.complain(TAG, "delete label"));
  }

  private Consumer<Context> deleteLabelFromExperiment(Experiment experiment) {
    if (TextUtils.isEmpty(trialId)) {
      return experiment.deleteLabelAndReturnAssetDeleter(experiment, originalLabel, appAccount);
    } else {
      return experiment
          .getTrial(trialId)
          .deleteLabelAndReturnAssetDeleter(experiment, originalLabel, appAccount);
    }
  }

  // Most types of labels have a caption. This sets up the text watcher / autosave for that.
  protected void setupCaption(View rootView) {
    caption = (EditText) rootView.findViewById(R.id.caption);
    caption.setText(originalLabel.getCaptionText());
    caption.setImeOptions(EditorInfo.IME_ACTION_DONE);
    caption.setRawInputType(InputType.TYPE_CLASS_TEXT);
    caption.setOnEditorActionListener(
        (textView, i, keyEvent) -> {
          if (i == EditorInfo.IME_ACTION_DONE) {
            caption.clearFocus();
            caption.setFocusable(false);
          }
          return false;
        });
    caption.setOnTouchListener(
        (v, motionEvent) -> {
          caption.setFocusableInTouchMode(true);
          caption.requestFocus();
          return false;
        });

    caption.setEnabled(false);
    experiment
        .firstElement()
        .subscribe(
            experiment -> {
              caption.setEnabled(true);
              // Move the cursor to the end
              caption.post(() -> caption.setSelection(caption.getText().toString().length()));

              saved
                  .happens()
                  .subscribe(o -> saveCaptionChanges(experiment, caption.getText().toString()));
            });
  }

  private void saveCaptionChanges(Experiment experiment, String newText) {
    Caption caption =
        GoosciCaption.Caption.newBuilder()
            .setText(newText)
            .setLastEditedTimestamp(clock.getNow())
            .build();
    originalLabel.setCaption(caption);
    saveUpdatedOriginalLabel(
        experiment, Change.newModifyTypeChange(ElementType.CAPTION, originalLabel.getLabelId()));
  }

  // This should only be called by subclasses that have date and time views in their XML.
  protected void setupDetails(View rootView) {
    Locale locale = getActivity().getResources().getConfiguration().locale;
    TextView date = (TextView) rootView.findViewById(R.id.date);
    TextView time = (TextView) rootView.findViewById(R.id.time);
    SimpleDateFormat dateFormat = new SimpleDateFormat("MMMM dd, yyyy", locale);
    SimpleDateFormat timeFormat = new SimpleDateFormat("EEEE h:mm a", locale);
    date.setText(dateFormat.format(originalLabel.getTimeStamp()));
    time.setText(timeFormat.format(originalLabel.getTimeStamp()));
  }
}
