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
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;

import com.google.android.apps.forscience.whistlepunk.R;
import com.google.android.apps.forscience.whistlepunk.filemetadata.Label;
import com.google.android.apps.forscience.whistlepunk.metadata.GoosciTextLabelValue;

/**
 * Details view controller for TextLabel
 */
public class TextLabelDetailsFragment extends LabelDetailsFragment {
    private static final String TAG = "TextLabelDetailsFragment";

    private EditText mNoteText;
    private TextWatcher mWatcher;

    public static TextLabelDetailsFragment newInstance(String experimentId, Label originalLabel) {
        TextLabelDetailsFragment result = new TextLabelDetailsFragment();
        Bundle args = new Bundle();
        args.putString(LabelDetailsActivity.ARG_EXPERIMENT_ID, experimentId);
        args.putParcelable(LabelDetailsActivity.ARG_LABEL, originalLabel);
        result.setArguments(args);
        return result;
    }

    public TextLabelDetailsFragment() {
        // Required empty public constructor
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            final Bundle savedInstanceState) {
        setHasOptionsMenu(true);
        mNoteText = (EditText) inflater.inflate(R.layout.text_label_details_fragment,
                container, false);
        mNoteText.setText(mOriginalLabel.getTextLabelValue().text);
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
                saveTextChanges(mNoteText.getText().toString());
            }
        };
        mNoteText.addTextChangedListener(mWatcher);
        mNoteText.setEnabled(false);

        mExperiment.firstElement().subscribe(experiment -> {
            mNoteText.setEnabled(true);
            // Move the cursor to the end
            mNoteText.post(() -> mNoteText.setSelection(mNoteText.getText().toString().length()));
        });

        // TODO: Transition

        return mNoteText;
    }

    @Override
    public void onDestroyView() {
        mNoteText.removeTextChangedListener(mWatcher);
        super.onDestroyView();
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.menu_text_label_details, menu);

        ActionBar actionBar = ((AppCompatActivity) getActivity()).getSupportActionBar();
        actionBar.setTitle(getActivity().getResources().getString(
                R.string.text_label_details_title));

        super.onCreateOptionsMenu(menu, inflater);
    }

    @Override
    public void onPrepareOptionsMenu(Menu menu) {
        MenuItem item = menu.findItem(R.id.action_delete);
        item.getIcon().mutate().setColorFilter(Color.WHITE, PorterDuff.Mode.SRC_ATOP);
        super.onPrepareOptionsMenu(menu);
    }

    private void saveTextChanges(String newText) {
        if (!verifyInput(newText)) {
            return;
        }
        GoosciTextLabelValue.TextLabelValue labelValue = new GoosciTextLabelValue.TextLabelValue();
        labelValue.text = newText;
        mOriginalLabel.setLabelProtoData(labelValue);
        saveUpdatedOriginalLabel();
    }

    private boolean verifyInput(String text) {
        if (TextUtils.isEmpty(text)) {
            mNoteText.setError(getActivity().getResources().getString(
                    R.string.empty_text_note_error));
            return false;
        }
        return true;
    }
}
