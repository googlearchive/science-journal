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

package com.google.android.apps.forscience.whistlepunk;

import android.app.DialogFragment;
import android.content.DialogInterface;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.support.v7.app.AlertDialog;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.google.android.apps.forscience.javalib.MaybeConsumer;
import com.google.android.apps.forscience.javalib.Success;
import com.google.android.apps.forscience.whistlepunk.filemetadata.Experiment;
import com.google.android.apps.forscience.whistlepunk.filemetadata.Label;
import com.google.android.apps.forscience.whistlepunk.filemetadata.PictureLabelValue;
import com.google.android.apps.forscience.whistlepunk.filemetadata.SensorTriggerLabelValue;
import com.google.android.apps.forscience.whistlepunk.filemetadata.TextLabelValue;
import com.google.android.apps.forscience.whistlepunk.metadata.GoosciCaption;
import com.google.android.apps.forscience.whistlepunk.metadata.GoosciLabelValue;
import com.google.android.apps.forscience.whistlepunk.metadata.TriggerHelper;
import com.google.protobuf.nano.InvalidProtocolBufferNanoException;

/**
 * For editing existing notes.
 */
public class EditNoteDialog extends DialogFragment {
    public static final String TAG = "edit_note_dialog";
    private static final String KEY_SAVED_LABEL = "keySavedLabel";
    private static final String KEY_SAVED_TIME_TEXT = "keySavedTimeText";
    private static final String KEY_SAVED_TIMESTAMP = "keySavedTimestamp";
    private static final String KEY_SELECTED_VALUE = "keySavedValue";
    private static final String KEY_SAVED_TIME_TEXT_DESCRIPTION = "keySavedTimeTextDescription";
    private static final String KEY_EXPERIMENT_ID = "keyExperimentId";
    private static final String KEY_TRIAL_ID = "keyTrialId";

    private Label mLabel;
    private GoosciLabelValue.LabelValue mSelectedValue;
    private long mTimestamp;
    private String mTrialId;
    private String mExperimentId;
    private Experiment mExperiment;

    public interface EditNoteDialogListener {
        /**
         * Called when a label is being edited in the database. Return value is passed to data
         * controller during the label edit.
         * @return A MaybeConsumer on whether the label was successfully edited..
         */
        public MaybeConsumer<Success> onLabelEdit(Label updatedLabel);

        /**
         * Called when a timestamp was clicked. The responder may use this to allow
         * editing of the label's timestamp, or may ignore it.
         */
        public void onEditNoteTimestampClicked(Label label,
                GoosciLabelValue.LabelValue selectedValue, long selectedTimestamp);
    }

    /**
     * Create an instance of the edit note dialog which uses a relative time that updates every
     * minute.
     */
    public static EditNoteDialog newInstance(Label label,
            GoosciLabelValue.LabelValue selectedValue, long timestamp, String experimentId,
            String trialId) {
        return newInstance(label, selectedValue, null, timestamp, null, experimentId, trialId);
    }

    /**
     * Create an instance of the edit note dialog which uses a set time that doesn't change, for
     * example an elapsed time.
     */
    public static EditNoteDialog newInstance(Label label,
            GoosciLabelValue.LabelValue selectedValue, String timeText, long timestamp,
            String timeTextDescription, String experimentId, String trialId) {
        EditNoteDialog dialog = new EditNoteDialog();
        Bundle args = new Bundle();
        args.putParcelable(KEY_SAVED_LABEL, label);
        args.putByteArray(KEY_SELECTED_VALUE, ProtoUtils.makeBlob(selectedValue));
        args.putString(KEY_SAVED_TIME_TEXT, timeText);
        args.putLong(KEY_SAVED_TIMESTAMP, timestamp);
        args.putString(KEY_SAVED_TIME_TEXT_DESCRIPTION, timeTextDescription);
        args.putString(KEY_EXPERIMENT_ID, experimentId);
        args.putString(KEY_TRIAL_ID, trialId);
        dialog.setArguments(args);
        return dialog;
    }

    public EditNoteDialog() {
    }

    @Override
    public AlertDialog onCreateDialog(Bundle savedInstanceState) {
        mLabel = getArguments().getParcelable(KEY_SAVED_LABEL);
        String timeText = getArguments().getString(KEY_SAVED_TIME_TEXT, "");
        String timeTextContentDescription = getArguments().getString(
                KEY_SAVED_TIME_TEXT_DESCRIPTION);
        mTimestamp = getArguments().getLong(KEY_SAVED_TIMESTAMP);
        mTrialId = getArguments().getString(KEY_TRIAL_ID);
        mExperimentId = getArguments().getString(KEY_EXPERIMENT_ID);
        getDataController().getExperimentById(mExperimentId,
                new LoggingConsumer<Experiment>(TAG, "get experiment") {
                    @Override
                    public void success(Experiment value) {
                        mExperiment = value;
                        updatePositiveButtonEnabled((AlertDialog) getDialog());
                    }
                });
        try {
            mSelectedValue = GoosciLabelValue.LabelValue.parseFrom(
                    getArguments().getByteArray(KEY_SELECTED_VALUE));
        } catch (InvalidProtocolBufferNanoException ex) {
            Log.wtf(TAG, "Couldn't parse label value");
        }
        AlertDialog.Builder alertDialog = new AlertDialog.Builder(getActivity());

        LinearLayout rootView = (LinearLayout) LayoutInflater.from(getActivity()).inflate(
                R.layout.run_review_label_edit, null);
        alertDialog.setView(rootView);

        ImageView imageView = (ImageView) rootView.findViewById(R.id.picture_note_preview_image);
        final EditText editText = (EditText) rootView.findViewById(R.id.edit_note_text);
        TextView autoTextView = (TextView) rootView.findViewById(R.id.auto_note_text);

        // Use mSelectedValue to load content, because the user may have changed the value since
        // it was stored in the label. Note that picture labels can't be edited at this time,
        // but in the future this will apply to picture labels as well.
        // TODO: Assuming one value type for now, but extend this to support multiple types later.
        if (mLabel.hasValueType(GoosciLabelValue.LabelValue.PICTURE)) {
            imageView.setVisibility(View.VISIBLE);
            autoTextView.setVisibility(View.GONE);
            editText.setText(mLabel.getCaptionText());
            editText.setHint(R.string.picture_note_caption_hint);
            PictureUtils.loadImage(getActivity(), imageView, mExperimentId,
                    PictureLabelValue.getFilePath(mSelectedValue));
            imageView.setOnClickListener(
                    v -> PictureUtils.launchExternalViewer(getActivity(), mExperimentId,
                            PictureLabelValue.getFilePath(mSelectedValue)));
        } else if (mLabel.hasValueType(GoosciLabelValue.LabelValue.TEXT)) {
            imageView.setVisibility(View.GONE);
            autoTextView.setVisibility(View.GONE);
            editText.setText(TextLabelValue.getText(mSelectedValue));
        } else if (mLabel.hasValueType(GoosciLabelValue.LabelValue.SENSOR_TRIGGER)) {
            imageView.setVisibility(View.GONE);
            autoTextView.setVisibility(View.VISIBLE);
            editText.setText(mLabel.getCaptionText());
            String autoText = SensorTriggerLabelValue.getAutogenText(mSelectedValue);
            TriggerHelper.populateAutoTextViews(autoTextView, autoText,
                    R.drawable.ic_label_black_24dp, getResources());
        }

        alertDialog.setPositiveButton(R.string.action_save,
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        mLabel.setTimestamp(mTimestamp);
                        if (mLabel.hasValueType(GoosciLabelValue.LabelValue.TEXT)) {
                            ((TextLabelValue) mLabel.getLabelValue(
                                    GoosciLabelValue.LabelValue.TEXT)).setText(
                                            editText.getText().toString());
                        } else if (mLabel.hasValueType(GoosciLabelValue.LabelValue.PICTURE) ||
                                mLabel.hasValueType(GoosciLabelValue.LabelValue.SENSOR_TRIGGER)) {
                            GoosciCaption.Caption caption = new GoosciCaption.Caption();
                            caption.text = editText.getText().toString();
                            caption.lastEditedTimestamp = mTimestamp;
                            mLabel.setCaption(caption);
                        }
                        if (TextUtils.equals(mTrialId, RecorderController.NOT_RECORDING_RUN_ID)) {
                            mExperiment.updateLabel(mLabel);
                        } else {
                            mExperiment.getTrial(mTrialId).updateLabel(mLabel);
                        }
                        getDataController().updateExperiment(mExperimentId,
                                ((EditNoteDialogListener) getParentFragment()).onLabelEdit(
                                        mLabel));
                    }
                }
        );
        alertDialog.setNegativeButton(android.R.string.cancel,
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.cancel();
                    }
                });
        alertDialog.setCancelable(true);

        RelativeTimeTextView timeTextView = (RelativeTimeTextView) rootView.findViewById(
                R.id.edit_note_time);
        if (labelBelongsToRun()) {
            timeTextView.setText(timeText);
            timeTextView.setContentDescription(timeTextContentDescription);
        } else {
            timeTextView.setTime(mLabel.getTimeStamp());
        }
        if (labelBelongsToRun() && mLabel.canEditTimestamp()) {
            timeTextView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    GoosciLabelValue.LabelValue value = new GoosciLabelValue.LabelValue();
                    if (mLabel.hasValueType(GoosciLabelValue.LabelValue.PICTURE)) {
                        // Captions can be edited, but the picture path cannot be edited at this
                        // time.
                        PictureLabelValue.populateLabelValue(value,
                                ((PictureLabelValue) mLabel.getLabelValue(
                                        GoosciLabelValue.LabelValue.PICTURE)).getFilePath(),
                                editText.getText().toString());
                        ((EditNoteDialogListener) getParentFragment()).onEditNoteTimestampClicked(
                                mLabel, value, mTimestamp);
                    } else if (mLabel.hasValueType(GoosciLabelValue.LabelValue.TEXT)) {
                        TextLabelValue.populateLabelValue(value, editText.getText().toString());
                        ((EditNoteDialogListener) getParentFragment()).onEditNoteTimestampClicked(
                                mLabel, value, mTimestamp);
                    }
                }
            });
        } else if (labelBelongsToRun()) {
            Drawable lockDrawable = ColorUtils.colorDrawable(timeTextView.getContext(),
                    getResources().getDrawable(R.drawable.ic_lock_black_18dp),
                    R.color.text_color_light_grey);
            // There is already a start drawable. Use it again.
            Drawable[] drawables = timeTextView.getCompoundDrawablesRelative();
            timeTextView.setCompoundDrawablesRelativeWithIntrinsicBounds(drawables[0], null,
                    lockDrawable, null);
        }

        AlertDialog dialog = alertDialog.create();
        dialog.setOnShowListener(new DialogInterface.OnShowListener() {
            @Override
            public void onShow(DialogInterface dialogInterface) {
                updatePositiveButtonEnabled((AlertDialog) dialogInterface);
            }
        });
        if (mLabel.hasValueType(GoosciLabelValue.LabelValue.TEXT) ||
                mLabel.hasValueType(GoosciLabelValue.LabelValue.SENSOR_TRIGGER)) {
            dialog.getWindow().setSoftInputMode(
                    WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
        }
        return dialog;
    }

    private DataController getDataController() {
        return AppSingleton.getInstance(getActivity()).getDataController();
    }

    private boolean labelBelongsToRun() {
        return !TextUtils.equals(mTrialId, RecorderController.NOT_RECORDING_RUN_ID);
    }

    private void updatePositiveButtonEnabled(AlertDialog dialog) {
        if (dialog == null) {
            return;
        }
        Button positiveButton = dialog.getButton(DialogInterface.BUTTON_POSITIVE);
        if (positiveButton != null) {
            positiveButton.setEnabled(mExperiment != null);
        }
    }
}
