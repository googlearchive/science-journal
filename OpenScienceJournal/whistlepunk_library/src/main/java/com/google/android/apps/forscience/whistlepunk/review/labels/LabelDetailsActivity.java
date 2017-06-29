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
import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;

import com.google.android.apps.forscience.whistlepunk.R;
import com.google.android.apps.forscience.whistlepunk.filemetadata.Label;
import com.google.android.apps.forscience.whistlepunk.metadata.GoosciLabel;

/**
 * Activity managing a LabelDetails page
 */
public class LabelDetailsActivity extends AppCompatActivity {

    static final String ARG_EXPERIMENT_ID = "experiment_id";
    static final String ARG_LABEL = "label";
    private static final String FRAGMENT_TAG = "fragment";

    public static void launch(Context context, String experimentId, Label label) {
        final Intent intent = new Intent(context, LabelDetailsActivity.class);
        intent.putExtra(ARG_EXPERIMENT_ID, experimentId);
        intent.putExtra(ARG_LABEL, label);
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
                fragment = TextLabelDetailsFragment.newInstance(
                        getIntent().getExtras().getString(ARG_EXPERIMENT_ID), originalLabel);
            } else if (labelType == GoosciLabel.Label.PICTURE) {
                fragment = PictureLabelDetailsFragment.newInstance(
                        getIntent().getExtras().getString(ARG_EXPERIMENT_ID), originalLabel);
            } else if (labelType == GoosciLabel.Label.SENSOR_TRIGGER) {
                fragment = TriggerLabelDetailsFragment.newInstance(
                        getIntent().getExtras().getString(ARG_EXPERIMENT_ID), originalLabel);
            } else if (labelType == GoosciLabel.Label.SNAPSHOT) {
                fragment = SnapshotLabelDetailsFragment.newInstance(
                        getIntent().getExtras().getString(ARG_EXPERIMENT_ID), originalLabel);
            } else {
                // Unknown type
                finish();
                return;
            }
            getFragmentManager().beginTransaction().add(R.id.container, fragment, FRAGMENT_TAG)
                    .commit();
        }
    }
}
