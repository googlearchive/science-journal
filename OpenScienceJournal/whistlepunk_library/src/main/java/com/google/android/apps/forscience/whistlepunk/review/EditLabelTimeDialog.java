/*
 *  Copyright 2016 Google Inc. All Rights Reserved.
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

package com.google.android.apps.forscience.whistlepunk.review;

import android.app.DialogFragment;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import com.google.android.apps.forscience.whistlepunk.AccessibilityUtils;
import com.google.android.apps.forscience.whistlepunk.ProtoUtils;
import com.google.android.apps.forscience.whistlepunk.R;
import com.google.android.apps.forscience.whistlepunk.RunReviewOverlay;
import com.google.android.apps.forscience.whistlepunk.filemetadata.Label;
import com.google.android.apps.forscience.whistlepunk.metadata.GoosciLabelValue;
import com.google.protobuf.nano.InvalidProtocolBufferNanoException;

/**
 * A dialogfragment for editing a note's timestamp.
 */
public class EditLabelTimeDialog extends DialogFragment implements RunReviewOverlay.OnTimestampChangeListener {
    public static final String TAG = "edit_time_dialog";
    public static final String KEY_LABEL = "savedLabel";
    public static final String KEY_EDITED_LABEL = "editedLabel";
    public static final String KEY_INITIAL_TIMESTAMP = "savedFirstTimestamp";
    public static final String KEY_RUN_START_TIMESTAMP = "savedRunStartTimestamp";

    public static final long NO_TIMESTAMP_SELECTED = -1;

    public interface EditTimeDialogListener {
        void onEditTimeDialogDismissedEdit(Label originalLabel, long selectedTimestamp);
        void onEditTimeDialogDismissedAdd(Label label, long selectedTimestamp);
    }

    // The timestamp to initially show to the user.
    private long mInitialTimestamp;

    // The currently selected timestamp. This will be mInitialTimestamp after onCreateView,
    // but we initialize it to NO_TIMESTAMP_SELECTED in case onCreateView is not yet called.
    private long mCurrentTimestamp = NO_TIMESTAMP_SELECTED;

    // The first timestamp in this run, used for formatting the timestamp string.
    private long mRunStartTimestamp;

    private TextView mTimeView;
    private boolean timestampSelected = false;
    private boolean mDismissed = false;

    // Edits an existing label.
    public static EditLabelTimeDialog newInstance(Label label,
            long runStartTimestamp) {
        EditLabelTimeDialog dialog = newInstanceHelper(label, label.getTimeStamp(),
                runStartTimestamp);
        dialog.getArguments().putParcelable(KEY_LABEL, label);
        return dialog;
    }

    // For adding a new label.
    public static EditLabelTimeDialog newInstance(Label editedLabel,
            long initialTimestamp, long runStartTimestamp) {
        EditLabelTimeDialog dialog = newInstanceHelper(editedLabel, initialTimestamp,
                runStartTimestamp);
        return dialog;
    }

    private static EditLabelTimeDialog newInstanceHelper(Label editedLabel,
            long initialTimestamp, long runStartTimestamp) {
        EditLabelTimeDialog dialog = new EditLabelTimeDialog();
        Bundle args = new Bundle();
        args.putLong(KEY_INITIAL_TIMESTAMP, initialTimestamp);
        args.putLong(KEY_RUN_START_TIMESTAMP, runStartTimestamp);
        args.putParcelable(KEY_EDITED_LABEL, editedLabel);
        dialog.setArguments(args);
        return dialog;
    }

    public EditLabelTimeDialog() {
        setHasOptionsMenu(true);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        mInitialTimestamp = getArguments().getLong(KEY_INITIAL_TIMESTAMP);
        mRunStartTimestamp = getArguments().getLong(KEY_RUN_START_TIMESTAMP);
        View rootView = inflater.inflate(R.layout.edit_time_dialog, container, false);
        mTimeView = (TextView) rootView.findViewById(R.id.edit_note_time);
        timestampSelected = false;

        onTimestampChanged(mInitialTimestamp);
        ImageButton button = (ImageButton) rootView.findViewById(R.id.submit_button);
        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                timestampSelected = true;
                dismiss();
            }
        });
        AccessibilityUtils.setTouchDelegateToMinAccessibleSize(button);
        return rootView;
    }

    @Override
    public void dismiss() {
        // Avoid double-callbacks by checking if this was already dismissed.
        if (!mDismissed) {
            mDismissed = true;
            if (getArguments().containsKey(KEY_LABEL)) {
                // Then this came from editing an existing label.
                ((EditTimeDialogListener) getParentFragment()).onEditTimeDialogDismissedEdit(
                        getArguments().getParcelable(KEY_LABEL),
                        timestampSelected ? mCurrentTimestamp : mInitialTimestamp);
            } else {
                // Then this came from adding a new label.
                Label label = getArguments().getParcelable(KEY_EDITED_LABEL);
                ((EditTimeDialogListener) getParentFragment()).onEditTimeDialogDismissedAdd(
                        label, timestampSelected ? mCurrentTimestamp : mInitialTimestamp);
            }
        }
        super.dismiss();
    }

    public long getCurrentTimestamp() {
        // If we haven't created the view yet, get the original timestamp from the args.
        if (mCurrentTimestamp == NO_TIMESTAMP_SELECTED) {
            return getArguments().getLong(KEY_INITIAL_TIMESTAMP);
        }
        return mCurrentTimestamp;
    }

    public Label getLabel() {
        return getArguments().getParcelable(KEY_LABEL);
    }

    @Override
    public void onTimestampChanged(long timestamp) {
        mCurrentTimestamp = timestamp;
        if (mTimeView != null) {
            mTimeView.setText(
                    PinnedNoteAdapter.getNoteTimeText(mCurrentTimestamp, mRunStartTimestamp));
            mTimeView.setContentDescription(PinnedNoteAdapter.getNoteTimeContentDescription(
                    mCurrentTimestamp, mRunStartTimestamp, getActivity()));
        }
    }

}
