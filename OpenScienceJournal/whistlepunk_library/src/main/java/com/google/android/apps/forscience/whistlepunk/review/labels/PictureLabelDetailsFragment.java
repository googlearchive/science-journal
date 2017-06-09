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
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;

import com.google.android.apps.forscience.whistlepunk.AppSingleton;
import com.google.android.apps.forscience.whistlepunk.Clock;
import com.google.android.apps.forscience.whistlepunk.PictureUtils;
import com.google.android.apps.forscience.whistlepunk.R;
import com.google.android.apps.forscience.whistlepunk.filemetadata.Label;
import com.google.android.apps.forscience.whistlepunk.metadata.GoosciCaption;

/**
 * Details view controller for PictureLabel
 */
public class PictureLabelDetailsFragment extends LabelDetailsFragment {
    private EditText mCaption;
    private TextWatcher mWatcher;
    private Clock mClock;

    public static PictureLabelDetailsFragment newInstance(String experimentId,
            Label originalLabel) {
        PictureLabelDetailsFragment result = new PictureLabelDetailsFragment();
        Bundle args = new Bundle();
        args.putString(LabelDetailsActivity.ARG_EXPERIMENT_ID, experimentId);
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
        mClock = AppSingleton.getInstance(getActivity()).getSensorEnvironment().getDefaultClock();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            final Bundle savedInstanceState) {
        setHasOptionsMenu(true);
        View rootView = inflater.inflate(R.layout.picture_label_details_fragment, container, false);
        mCaption = (EditText) rootView.findViewById(R.id.caption);
        mCaption.setText(mOriginalLabel.getCaptionText());
        mWatcher = new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {

            }

            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {

            }

            @Override
            public void afterTextChanged(Editable editable) {
                // Save the label changes
                saveCaptionChanges(mCaption.getText().toString());
            }
        };
        mCaption.addTextChangedListener(mWatcher);
        mCaption.setEnabled(false);
        mExperiment.firstElement().subscribe(experiment -> {
            mCaption.setEnabled(true);
            // Move the cursor to the end
            mCaption.post(() -> mCaption.setSelection(mCaption.getText().toString().length()));
        });

        ImageView imageView = (ImageView) rootView.findViewById(R.id.image);
        PictureUtils.loadExperimentImage(getActivity(), imageView, mExperimentId,
                mOriginalLabel.getPictureLabelValue().filePath);

        // TODO: Transition

        return rootView;
    }

    @Override
    public void onDestroyView() {
        mCaption.removeTextChangedListener(mWatcher);
        super.onDestroyView();
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.menu_picture_label_details, menu);

        ActionBar actionBar = ((AppCompatActivity) getActivity()).getSupportActionBar();
        actionBar.setTitle("");

        super.onCreateOptionsMenu(menu, inflater);
    }

    private void saveCaptionChanges(String newText) {
        GoosciCaption.Caption caption = new GoosciCaption.Caption();
        caption.text = newText;
        caption.lastEditedTimestamp = mClock.getNow();
        mOriginalLabel.setCaption(caption);
        saveUpdatedOriginalLabel();
    }
}
