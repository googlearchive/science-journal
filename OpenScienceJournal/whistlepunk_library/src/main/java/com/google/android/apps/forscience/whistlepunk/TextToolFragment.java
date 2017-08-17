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

package com.google.android.apps.forscience.whistlepunk;

import android.app.Fragment;
import android.content.Context;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import com.google.android.apps.forscience.whistlepunk.analytics.TrackerConstants;
import com.google.android.apps.forscience.whistlepunk.filemetadata.Label;
import com.google.android.apps.forscience.whistlepunk.metadata.GoosciLabel;
import com.google.android.apps.forscience.whistlepunk.metadata.GoosciTextLabelValue;
import com.jakewharton.rxbinding2.widget.RxTextView;

/**
 * Fragment controlling adding text notes in the observe pane.
 */
public class TextToolFragment extends Fragment {
    private static final String KEY_TEXT = "saved_text";
    private TextView mTextView;
    private TextLabelFragmentListener mListener;

    public interface TextLabelFragmentListener {
        void onTextLabelTaken(Label result);
    }

    public interface ListenerProvider {
        TextToolFragment.TextLabelFragmentListener getTextLabelFragmentListener();
    }

    public static Fragment newInstance() {
        return new TextToolFragment();
    }

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container,
            Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.text_label_fragment, null);

        mTextView = (TextView) rootView.findViewById(R.id.text);

        attachButtons(rootView);

        if (savedInstanceState != null) {
            mTextView.setText(savedInstanceState.getString(KEY_TEXT));
        }

        return rootView;
    }

    public void attachButtons(View rootView) {
        ImageButton addButton = (ImageButton) rootView.findViewById(R.id.btn_add);
        addButton.setOnClickListener(view -> {
            final long timestamp = getTimestamp(addButton.getContext());
            GoosciTextLabelValue.TextLabelValue labelValue =
                    new GoosciTextLabelValue.TextLabelValue();
            labelValue.text = mTextView.getText().toString();
            Label result = Label.newLabelWithValue(timestamp, GoosciLabel.Label.TEXT, labelValue,
                    null);
            getListener(addButton.getContext()).onTextLabelTaken(result);

            log(addButton.getContext(), result);
            // Clear the text
            mTextView.setText("");
        });
        // TODO: Need to update the content description if we are recording or not.
        // This will probably happen in the ControlBar rather than here.
        addButton.setEnabled(false);

        RxTextView.afterTextChangeEvents(mTextView)
                  .subscribe(event -> addButton.setEnabled(canAddLabel()));
    }

    private boolean canAddLabel() {
        return !TextUtils.isEmpty(mTextView.getText());
    }

    private void log(Context context, Label result) {
        WhistlePunkApplication.getUsageTracker(context)
                              .trackEvent(TrackerConstants.CATEGORY_NOTES,
                                      TrackerConstants.ACTION_CREATE,
                                      TrackerConstants.LABEL_EXPERIMENT_DETAIL,
                                      TrackerConstants.getLabelValueType(result));
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        if (mTextView != null && mTextView.getText() != null) {
            outState.putString(KEY_TEXT, mTextView.getText().toString());
        }
        super.onSaveInstanceState(outState);
    }

    private long getTimestamp(Context context) {
        return getClock(context).getNow();
    }

    private Clock getClock(Context context) {
        return AppSingleton.getInstance(context)
                .getSensorEnvironment()
                .getDefaultClock();
    }

    private TextLabelFragmentListener getListener(Context context) {
        if (mListener == null) {
            if (context instanceof ListenerProvider) {
                mListener = ((ListenerProvider) context).getTextLabelFragmentListener();
            } else {
                Fragment parentFragment = getParentFragment();
                if (parentFragment instanceof ListenerProvider) {
                    mListener = ((ListenerProvider) parentFragment).getTextLabelFragmentListener();
                }
            }
        }
        return mListener;
    }
}
