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
import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.NavUtils;
import android.support.v7.app.AppCompatActivity;

import com.google.android.apps.forscience.whistlepunk.AppSingleton;
import com.google.android.apps.forscience.whistlepunk.DeletedLabel;
import com.google.android.apps.forscience.whistlepunk.PanesActivity;
import com.google.android.apps.forscience.whistlepunk.R;
import com.google.android.apps.forscience.whistlepunk.filemetadata.Label;
import com.google.android.apps.forscience.whistlepunk.metadata.GoosciLabel;
import com.google.android.apps.forscience.whistlepunk.project.experiment.ExperimentDetailsFragment;
import com.google.android.apps.forscience.whistlepunk.review.RunReviewActivity;
import com.google.android.apps.forscience.whistlepunk.review.RunReviewFragment;

/**
 * Activity managing a LabelDetails page
 */
public class LabelDetailsActivity extends AppCompatActivity {

    static final String ARG_EXPERIMENT_ID = "experiment_id";
    static final String ARG_TRIAL_ID = "trial_id";
    static final String ARG_LABEL = "label";
    static final String ARG_PARENT_EXP_DETAILS = "from_exp_details";
    static final String ARG_PARENT_RUN_REVIEW = "from_run_review";
    private static final String FRAGMENT_TAG = "fragment";

    public static void launchFromExpDetails(Context context, String experimentId, Label label) {
        final Intent intent = new Intent(context, LabelDetailsActivity.class);
        intent.putExtra(ARG_EXPERIMENT_ID, experimentId);
        intent.putExtra(ARG_LABEL, label);
        intent.putExtra(ARG_PARENT_EXP_DETAILS, true);
        context.startActivity(intent);
    }

    public static void launchFromRunReview(Context context, String experimentId, String trialId,
            int selectedSensor, Label label, boolean createTask, boolean fromRecord) {
        final Intent intent = new Intent(context, LabelDetailsActivity.class);
        intent.putExtra(ARG_EXPERIMENT_ID, experimentId);
        intent.putExtra(ARG_LABEL, label);
        intent.putExtra(ARG_PARENT_RUN_REVIEW, true);
        intent.putExtra(RunReviewActivity.EXTRA_CREATE_TASK, createTask);
        intent.putExtra(RunReviewActivity.EXTRA_FROM_RECORD, fromRecord);
        intent.putExtra(RunReviewFragment.ARG_SENSOR_INDEX, selectedSensor);
        intent.putExtra(RunReviewFragment.ARG_START_LABEL_ID, trialId);
        context.startActivity(intent);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Label originalLabel = getIntent().getExtras().getParcelable(ARG_LABEL);
        if (originalLabel == null) {
            finish();
            return;
        }
        int labelType = originalLabel.getType();

        // Update the theme if this is a text note before setting the view.
        if (labelType == GoosciLabel.Label.TEXT) {
            setTheme(R.style.text_label_details);
        } else if (labelType == GoosciLabel.Label.PICTURE) {
            setTheme(R.style.picture_label_details);
        }

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_label_details);

        // TODO: Enable transitions between note views in the experiment or trial note list
        // and these activities, similar to RunReview transition. This may involve
        // supportPostponeEnterTransition();?

        if (getFragmentManager().findFragmentByTag(FRAGMENT_TAG) == null) {
            LabelDetailsFragment fragment;
            if (labelType == GoosciLabel.Label.TEXT) {
                fragment = TextLabelDetailsFragment.newInstance(getExperimentId(), getTrialId(),
                        originalLabel);
            } else if (labelType == GoosciLabel.Label.PICTURE) {
                fragment = PictureLabelDetailsFragment.newInstance(getExperimentId(), getTrialId(),
                        originalLabel);
            } else if (labelType == GoosciLabel.Label.SENSOR_TRIGGER) {
                fragment = TriggerLabelDetailsFragment.newInstance(getExperimentId(), getTrialId(),
                        originalLabel);
            } else if (labelType == GoosciLabel.Label.SNAPSHOT) {
                fragment = SnapshotLabelDetailsFragment.newInstance(getExperimentId(), getTrialId(),
                        originalLabel);
            } else {
                // Unknown type
                finish();
                return;
            }
            getFragmentManager().beginTransaction().add(R.id.container, fragment, FRAGMENT_TAG)
                    .commit();
        }
    }

    public void returnToParent(boolean labelDeleted, DeletedLabel originalLabel) {
        // Must return to the parent using all the appropriate args if the label was deleted.
        // We would use NavUtils.getParentActivityIntent() instead of making a new intent, but it
        // seems that each activity may only have one parent specified in the manifest, and two
        // distinct activities (trial review and experiment details) may be parents of LabelDetails.
        // Therefore we do "up" by programmatically remembering the parent activity intent and
        // re-creating it.
        Intent upIntent;
        if (getIntent().getExtras().getBoolean(LabelDetailsActivity.ARG_PARENT_EXP_DETAILS)) {
            upIntent = new Intent(this, PanesActivity.class);
            upIntent.putExtra(ExperimentDetailsFragment.ARG_EXPERIMENT_ID, getExperimentId());
        } else if (getIntent().getExtras().getBoolean(
                LabelDetailsActivity.ARG_PARENT_RUN_REVIEW)) {
            upIntent = new Intent(this, RunReviewActivity.class);
            upIntent.putExtra(RunReviewFragment.ARG_EXPERIMENT_ID, getExperimentId());
            upIntent.putExtra(RunReviewActivity.EXTRA_FROM_RECORD,
                    getIntent().getExtras().getBoolean(RunReviewActivity.EXTRA_FROM_RECORD));
            upIntent.putExtra(RunReviewActivity.EXTRA_CREATE_TASK,
                    getIntent().getExtras().getBoolean(RunReviewActivity.EXTRA_CREATE_TASK));
            upIntent.putExtra(RunReviewFragment.ARG_SENSOR_INDEX, getIntent().getExtras().getInt(
                    RunReviewFragment.ARG_SENSOR_INDEX));
            upIntent.putExtra(RunReviewFragment.ARG_START_LABEL_ID, getIntent().getExtras()
                    .getString(RunReviewFragment.ARG_START_LABEL_ID));
        } else {
            // unknown parent.
            finish();
            return;
        }

        if (labelDeleted) {
            AppSingleton.getInstance(this).pushDeletedLabelForUndo(originalLabel);
        }
        // TODO: In the Panes world, do we still need to pass any other args?

        // TODO: nice transition!

        NavUtils.navigateUpTo(this, upIntent);
    }

    private String getExperimentId() {
        return getIntent().getExtras().getString(ARG_EXPERIMENT_ID);
    }

    private String getTrialId() {
        return getIntent().getExtras().getString(RunReviewFragment.ARG_START_LABEL_ID);
    }
}
