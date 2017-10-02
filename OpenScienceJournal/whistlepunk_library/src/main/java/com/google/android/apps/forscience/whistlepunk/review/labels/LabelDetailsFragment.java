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

import android.app.Fragment;
import android.content.Context;
import android.os.Bundle;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.text.InputType;
import android.text.TextUtils;
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
import com.google.android.apps.forscience.whistlepunk.filemetadata.Experiment;
import com.google.android.apps.forscience.whistlepunk.filemetadata.Label;
import com.google.android.apps.forscience.whistlepunk.filemetadata.Trial;
import com.google.android.apps.forscience.whistlepunk.metadata.GoosciCaption;

import java.text.SimpleDateFormat;
import java.util.Locale;

import io.reactivex.Maybe;
import io.reactivex.functions.Consumer;
import io.reactivex.subjects.BehaviorSubject;

/**
 * General fragment for label details views
 */
abstract class LabelDetailsFragment extends Fragment {
    private static final String KEY_SAVED_LABEL = "saved_label";
    private static final String TAG = "LabelDetails";
    protected String mExperimentId;
    private String mTrialId = null;
    protected BehaviorSubject<Experiment> mExperiment = BehaviorSubject.create();
    protected Label mOriginalLabel;

    private EditText mCaption;
    private Clock mClock;
    private RxEvent mSaved = new RxEvent();

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mExperimentId = getArguments().getString(LabelDetailsActivity.ARG_EXPERIMENT_ID);
        mTrialId = getArguments().getString(LabelDetailsActivity.ARG_TRIAL_ID);
        if (savedInstanceState == null) {
            mOriginalLabel = getArguments().getParcelable(LabelDetailsActivity.ARG_LABEL);
        } else {
            // Load the updated label
            mOriginalLabel = savedInstanceState.getParcelable(KEY_SAVED_LABEL);
        }

        RxDataController.getExperimentById(getDataController(), mExperimentId)
                        .subscribe(this::attachExperiment);
        mExperiment.firstElement().subscribe(experiment -> getActivity().invalidateOptionsMenu());

        mClock = AppSingleton.getInstance(getActivity()).getSensorEnvironment().getDefaultClock();
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        outState.putParcelable(KEY_SAVED_LABEL, mOriginalLabel);
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
        item.setEnabled(mExperiment.hasValue());
        super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == android.R.id.home) {
            mSaved.onDoneHappening();
            boolean labelDeleted = false;
            returnToParent(labelDeleted, null);
            return true;
        } else if (id == R.id.action_save) {
            mSaved.onHappened();
            returnToParent(false, null);
        } else if (id == R.id.action_delete) {
            deleteAndReturnToParent();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    protected void saveUpdatedOriginalLabel(Experiment experiment) {
        // TODO: Log analytics here? That would send an event per keystroke.
        if (TextUtils.isEmpty(mTrialId)) {
            RxDataController.updateLabel(getDataController(), experiment, mOriginalLabel,
                    experiment)
                    .subscribe(LoggingConsumer.observe(TAG, "update"));
        } else {
            Trial trial = experiment.getTrial(mTrialId);
            trial.updateLabel(mOriginalLabel);
            experiment.updateTrial(trial);
            RxDataController.updateExperiment(getDataController(), experiment)
                    .subscribe(LoggingConsumer.observe(TAG, "update"));
        }
    }

    private void attachExperiment(Experiment experiment) {
        mExperiment.onNext(experiment);
    }

    protected DataController getDataController() {
        return AppSingleton.getInstance(getActivity()).getDataController();
    }

    protected void returnToParent(boolean labelDeleted, Consumer<Context> assetDeleter) {
        if (getActivity() == null) {
            return;
        }
        if (!mExperiment.hasValue()) {
            // We didn't load yet, just go back.
            getActivity().onBackPressed();
        }
        // Need to either fake a back button or send the right args
        ((LabelDetailsActivity) getActivity()).returnToParent(labelDeleted,
                new DeletedLabel(mOriginalLabel, assetDeleter));
    }

    protected void deleteAndReturnToParent() {
        mExperiment.firstElement()
                   .flatMap(experiment -> {
                       Consumer<Context> assetDeleter = deleteLabelFromExperiment(experiment);
                       return RxDataController.updateExperiment(getDataController(), experiment)
                                              .andThen(Maybe.just(assetDeleter));
                   })
                   .subscribe(
                           assetDeleter -> returnToParent(/* label deleted */ true, assetDeleter),
                           LoggingConsumer.complain(TAG, "delete label"));
    }

    private Consumer<Context> deleteLabelFromExperiment(Experiment experiment) {
        if (TextUtils.isEmpty(mTrialId)) {
            return experiment.deleteLabelAndReturnAssetDeleter(mOriginalLabel);
        } else {
            return experiment.getTrial(mTrialId)
                             .deleteLabelAndReturnAssetDeleter(mOriginalLabel, mExperimentId);
        }
    }

    // Most types of labels have a caption. This sets up the text watcher / autosave for that.
    protected void setupCaption(View rootView) {
        mCaption = (EditText) rootView.findViewById(R.id.caption);
        mCaption.setText(mOriginalLabel.getCaptionText());
        mCaption.setImeOptions(EditorInfo.IME_ACTION_DONE);
        mCaption.setRawInputType(InputType.TYPE_CLASS_TEXT);
        mCaption.setOnEditorActionListener((textView, i, keyEvent) -> {
            if (i == EditorInfo.IME_ACTION_DONE) {
                mCaption.clearFocus();
                mCaption.setFocusable(false);
            }
            return false;
        });
        mCaption.setOnTouchListener((v, motionEvent) -> {
            mCaption.setFocusableInTouchMode(true);
            mCaption.requestFocus();
            return false;
        });

        mCaption.setEnabled(false);
        mExperiment.firstElement().subscribe(experiment -> {
            mCaption.setEnabled(true);
            // Move the cursor to the end
            mCaption.post(() -> mCaption.setSelection(mCaption.getText().toString().length()));

            mSaved.happens()
                  .subscribe(o -> saveCaptionChanges(experiment, mCaption.getText().toString()));
        });
    }

    private void saveCaptionChanges(Experiment experiment, String newText) {
        GoosciCaption.Caption caption = new GoosciCaption.Caption();
        caption.text = newText;
        caption.lastEditedTimestamp = mClock.getNow();
        mOriginalLabel.setCaption(caption);
        saveUpdatedOriginalLabel(experiment);
    }

    // This should only be called by subclasses that have date and time views in their XML.
    protected void setupDetails(View rootView) {
        Locale locale = getActivity().getResources().getConfiguration().locale;
        TextView date = (TextView) rootView.findViewById(R.id.date);
        TextView time = (TextView) rootView.findViewById(R.id.time);
        SimpleDateFormat dateFormat = new SimpleDateFormat("MMMM dd, yyyy", locale);
        SimpleDateFormat timeFormat = new SimpleDateFormat("EEEE h:mm a", locale);
        date.setText(dateFormat.format(mOriginalLabel.getTimeStamp()));
        time.setText(timeFormat.format(mOriginalLabel.getTimeStamp()));
    }
}
