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

import android.graphics.Color;
import android.graphics.PorterDuff;
import android.os.Bundle;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import com.google.android.apps.forscience.whistlepunk.PictureUtils;
import com.google.android.apps.forscience.whistlepunk.R;
import com.google.android.apps.forscience.whistlepunk.filemetadata.Label;

/**
 * Details view controller for PictureLabel
 */
public class PictureLabelDetailsFragment extends LabelDetailsFragment {

    private ImageView mImageView;

    public static PictureLabelDetailsFragment newInstance(String experimentId,
            String trialId, Label originalLabel) {
        PictureLabelDetailsFragment result = new PictureLabelDetailsFragment();
        Bundle args = new Bundle();
        args.putString(LabelDetailsActivity.ARG_EXPERIMENT_ID, experimentId);
        args.putString(LabelDetailsActivity.ARG_TRIAL_ID, trialId);
        args.putParcelable(LabelDetailsActivity.ARG_LABEL, originalLabel);
        result.setArguments(args);
        return result;
    }

    public PictureLabelDetailsFragment() {
        // Required empty public constructor
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            final Bundle savedInstanceState) {
        setHasOptionsMenu(true);
        View rootView = inflater.inflate(R.layout.picture_label_details_fragment, container, false);

        setupCaption(rootView);

        mImageView = (ImageView) rootView.findViewById(R.id.image);

        // TODO: Transition

        return rootView;
    }

    @Override
    public void onResume() {
        super.onResume();
        // Load on resume in case edits have been made.
        PictureUtils.loadExperimentImage(getActivity(), mImageView, mExperimentId,
                mOriginalLabel.getPictureLabelValue().filePath);
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.menu_picture_label_details, menu);

        ActionBar actionBar = ((AppCompatActivity) getActivity()).getSupportActionBar();
        actionBar.setTitle("");

        super.onCreateOptionsMenu(menu, inflater);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.action_edit) {
            PictureUtils.launchExternalEditor(getActivity(), mExperimentId,
                    mOriginalLabel.getPictureLabelValue().filePath);
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onPrepareOptionsMenu(Menu menu) {
        MenuItem item = menu.findItem(R.id.action_edit);
        item.getIcon().mutate().setColorFilter(Color.BLACK, PorterDuff.Mode.SRC_ATOP);
        super.onPrepareOptionsMenu(menu);
    }

}
