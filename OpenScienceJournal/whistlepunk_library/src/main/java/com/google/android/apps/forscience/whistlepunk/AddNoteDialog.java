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

import android.Manifest;
import android.app.Activity;
import android.app.Dialog;
import android.app.DialogFragment;
import android.app.Fragment;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v7.app.AlertDialog;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.google.android.apps.forscience.javalib.MaybeConsumer;
import com.google.android.apps.forscience.javalib.MaybeConsumers;
import com.google.android.apps.forscience.whistlepunk.filemetadata.Experiment;
import com.google.android.apps.forscience.whistlepunk.filemetadata.Label;
import com.google.android.apps.forscience.whistlepunk.filemetadata.PictureLabelValue;
import com.google.android.apps.forscience.whistlepunk.filemetadata.TextLabelValue;
import com.google.android.apps.forscience.whistlepunk.metadata.GoosciLabelValue;
import com.google.android.apps.forscience.whistlepunk.review.RunReviewFragment;
import com.google.android.apps.forscience.whistlepunk.sensors.VideoSensor;
import com.google.protobuf.nano.InvalidProtocolBufferNanoException;

import java.io.File;
import java.util.UUID;

import io.reactivex.Maybe;
import io.reactivex.Single;

/**
 * Dialog for adding new notes.
 */
// TODO: Combine similar code with EditNoteDialog.
public class AddNoteDialog extends DialogFragment {
    public static final String TAG = "add_note_dialog";

    private static final String KEY_SAVED_INPUT_TEXT = "savedInputText";
    private static final String KEY_SAVED_PICTURE_PATH = "savedPicturePath";
    private static final String KEY_SHOULD_USE_SAVED_TIMESTAMP = "shouldUseSavedTimestamp";
    private static final String KEY_SAVED_TIMESTAMP = "savedTimestamp";
    private static final String KEY_SAVED_RUN_ID = "savedRunId";
    private static final String KEY_SAVED_EXPERIMENT_ID = "savedExperimentId";
    private static final String KEY_HINT_TEXT_ID = "savedHintTextId";
    private static final String KEY_SHOW_TIMESTAMP_SECTION = "savedShowTimestampSection";
    private static final String KEY_LABEL_TIME_TEXT = "savedLabelTimeText";
    private static final String KEY_SAVED_VALUE = "savedLabelValue";
    private static final java.lang.String KEY_SAVED_TIME_TEXT_DESCRIPTION =
            "keySavedTimeTextDescription";
    private String mUuid;
    private DataService.FragmentBinder mDataBinder = new DataService.FragmentBinder();

    public static abstract class AddNoteDialogListener {
        static AddNoteDialogListener NULL = new AddNoteDialogListener() {
            @Override
            public String toString() {
                return "AddNoteDialogListener.NULL";
            }
        };

        /**
         * Called with a label that's about to be added.  Listener can adjust the label by, for
         * example, changing the timestamp
         */
        public void adjustLabelBeforeAdd(Label label) {
            // do nothing;
        }

        /**
         * Called when a label is being added to the database. Return value is passed to data
         * controller during the label add.
         *
         * @return A MaybeConsumer of labels.
         */
        public MaybeConsumer<Label> onLabelAdd() {
            return MaybeConsumers.noop();
        }

        /**
         * Called when the timestamp section is clicked. Note that this is only used if
         * showTimestampSection is true when creating a newInstance of AddNoteDialog.
         */
        public void onAddNoteTimestampClicked(GoosciLabelValue.LabelValue selectedValue,
                int labelType, long selectedTimestamp) {

        }
    }

    public interface ListenerProvider {
        AddNoteDialogListener getAddNoteDialogListener();
    }

    private AddNoteDialogListener mListener = AddNoteDialogListener.NULL;
    private long mTimestamp;
    private boolean mUseSavedTimestamp;
    private boolean mShowTimestampSection;
    private String mLabelTimeText;
    private String mLabelTimeTextDescription = "";
    private String mTrialId;
    private String mExperimentId;
    private int mHintTextId = R.string.add_run_note_placeholder_text;
    private String mPictureLabelPath;
    private EditText mInput;
    private Experiment mExperiment;

    /**
     * Create an AddNoteDialog that does not show the timestamp section, and where the note
     * has no previous value, with a timestamp that will be used as the current time when
     * the note is created
     */
    public static AddNoteDialog createWithSavedTimestamp(long timestamp, String currentRunId,
            String experimentId, int hintTextId) {
        return AddNoteDialog.newInstance(timestamp, currentRunId, experimentId, hintTextId, true);
    }

    /**
     * Create an AddNoteDialog that does not show the timestamp section, and where the note
     * has no previous value, which will create notes that reference the time whenever the form is
     * submitted
     */
    public static AddNoteDialog createWithDynamicTimestamp(String currentRunId, String experimentId,
            int hintTextId) {
        return AddNoteDialog.newInstance(-1, currentRunId, experimentId, hintTextId, false);
    }

    /**
     * Create an AddNoteDialog that does not show the timestamp section, and where the note
     * has no previous value.
     *
     * @param shouldUseSavedTimestamp if true, use the timestamp provided when creating a note.
     *                                otherwise, use the current time when the form is submitted.
     */
    private static AddNoteDialog newInstance(long timestamp, String currentRunId,
            String experimentId, int hintTextId, boolean shouldUseSavedTimestamp) {
        return AddNoteDialog.newInstance(timestamp, currentRunId, experimentId,
                hintTextId, false, "", "",
                shouldUseSavedTimestamp);
    }

    public static AddNoteDialog newInstance(long timestamp, String currentRunId,
            String experimentId, int hintTextId, boolean showTimestampSection, String labelTimeText,
            String labelTimeTextDescription, boolean shouldUseSavedTimestamp) {
        AddNoteDialog dialog = new AddNoteDialog();

        Bundle args = new Bundle();
        args.putLong(KEY_SAVED_TIMESTAMP, timestamp);
        args.putBoolean(KEY_SHOULD_USE_SAVED_TIMESTAMP, shouldUseSavedTimestamp);
        args.putString(KEY_SAVED_RUN_ID, currentRunId);
        args.putString(KEY_SAVED_EXPERIMENT_ID, experimentId);
        args.putInt(KEY_HINT_TEXT_ID, hintTextId);
        args.putBoolean(KEY_SHOW_TIMESTAMP_SECTION, showTimestampSection);
        args.putString(KEY_LABEL_TIME_TEXT, labelTimeText);
        args.putString(KEY_SAVED_TIME_TEXT_DESCRIPTION, labelTimeTextDescription);
        dialog.setArguments(args);

        return dialog;
    }

    public static AddNoteDialog newInstance(long timestamp, String currentRunId,
            String experimentId, int hintTextId, boolean showTimestampSection,
            String labelTimeText, GoosciLabelValue.LabelValue currentValue, int labelType,
            String labelTimeTextDescription, boolean shouldUseSavedTimestamp) {
        AddNoteDialog dialog = AddNoteDialog.newInstance(timestamp, currentRunId, experimentId,
                hintTextId, showTimestampSection, labelTimeText, labelTimeTextDescription,
                shouldUseSavedTimestamp);

        if (labelType == RunReviewFragment.LABEL_TYPE_UNDECIDED) {
            // no user added value yet, so no need to store anything else.
            return dialog;
        }

        Bundle args = dialog.getArguments();
        args.putByteArray(KEY_SAVED_VALUE, ProtoUtils.makeBlob(currentValue));

        dialog.setArguments(args);
        return dialog;
    }

    public AddNoteDialog() {
    }

    public void setExperimentId(String experimentId) {
        mExperimentId = experimentId;
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString(KEY_SAVED_PICTURE_PATH, mPictureLabelPath);
        outState.putString(KEY_SAVED_INPUT_TEXT,
                mInput == null ? null : mInput.getText().toString());
    }

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container,
            Bundle savedInstanceState) {
        if (getShowsDialog()) {
            // dialog builder will build entire view in dialog mode, don't try to replicate here.
            return super.onCreateView(inflater, container, savedInstanceState);
        }

        return createAddNoteViewFromSavedState(savedInstanceState, inflater, true);
    }

    @Override
    public AlertDialog onCreateDialog(Bundle savedInstanceState) {
        final LinearLayout rootView = createAddNoteViewFromSavedState(savedInstanceState,
                LayoutInflater.from(getActivity()), false);

        AlertDialog.Builder alertDialog = new AlertDialog.Builder(getActivity());
        alertDialog.setView(rootView);
        alertDialog.setPositiveButton(R.string.action_save, (dialog1, which) -> addLabel());
        alertDialog.setNegativeButton(android.R.string.cancel,
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.cancel();
                    }
                });
        alertDialog.setCancelable(true);
        AlertDialog dialog = alertDialog.create();
        dialog.setOnShowListener(
                dialogInterface -> updatePositiveButtonEnabled((AlertDialog) dialogInterface));
        if (!hasPicture()) {
            dialog.getWindow().setSoftInputMode(
                    WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
        }
        return dialog;
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        internalOnAttach(context);
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        internalOnAttach(activity);
    }

    @Override
    public void onDetach() {
        mListener = AddNoteDialogListener.NULL;
        super.onDetach();
    }

    private void internalOnAttach(Context context) {
        mDataBinder.onAttach(context);
        if (context instanceof ListenerProvider) {
            mListener = ((ListenerProvider) context).getAddNoteDialogListener();
        }
        Fragment parentFragment = getParentFragment();
        if (parentFragment instanceof ListenerProvider) {
            mListener = ((ListenerProvider) parentFragment).getAddNoteDialogListener();
        }
    }

    @NonNull
    protected LinearLayout createAddNoteViewFromSavedState(Bundle savedInstanceState,
            LayoutInflater inflater, boolean nativeSaveButton) {
        String text = "";
        GoosciLabelValue.LabelValue value;
        if (getArguments() != null) {
            mTimestamp = getArguments().getLong(KEY_SAVED_TIMESTAMP, -1);
            mUseSavedTimestamp = getArguments().getBoolean(KEY_SHOULD_USE_SAVED_TIMESTAMP, true);
            mShowTimestampSection = getArguments().getBoolean(KEY_SHOW_TIMESTAMP_SECTION, false);
            mHintTextId = getArguments().getInt(KEY_HINT_TEXT_ID, mHintTextId);
            mLabelTimeText = getArguments().getString(KEY_LABEL_TIME_TEXT, "");
            mPictureLabelPath = getArguments().getString(KEY_SAVED_PICTURE_PATH, null);
            mTrialId = getArguments().getString(KEY_SAVED_RUN_ID);
            mExperimentId = getArguments().getString(KEY_SAVED_EXPERIMENT_ID);

            getDataController()
                    .flatMapSingle(dc -> RxDataController.getExperimentById(dc, mExperimentId))
                    .subscribe(exp -> {
                        mExperiment = exp;
                        updatePositiveButtonEnabled((AlertDialog) getDialog());
                    }, LoggingConsumer.complain(TAG, "get experiment"));
            if (getArguments().containsKey(KEY_SAVED_TIME_TEXT_DESCRIPTION)) {
                mLabelTimeTextDescription =
                        getArguments().getString(KEY_SAVED_TIME_TEXT_DESCRIPTION);
            }
            if (getArguments().containsKey(KEY_SAVED_VALUE)) {
                try {
                    value = GoosciLabelValue.LabelValue.parseFrom(
                            getArguments().getByteArray(KEY_SAVED_VALUE));
                    mPictureLabelPath = PictureLabelValue.getFilePath(value);
                    if (TextUtils.isEmpty(mPictureLabelPath)) {
                        // If there is no picture path, then this was saved as a text label by
                        // default (see addTrialLabel). See if any text is available to populate the
                        // text field.
                        text = TextLabelValue.getText(value);
                    } else {
                        // If there is a picture path, this was saved as a picture label, so get
                        // the caption if it is available to populate the text field.
                        text = PictureLabelValue.getCaption(value);
                    }
                } catch (InvalidProtocolBufferNanoException e) {
                    Log.wtf(TAG, "Unable to parse label value");
                }
            }
        }
        if (savedInstanceState != null) {
            // SavedInstanceState is more recent than args and may override the values.
            mPictureLabelPath = savedInstanceState.getString(KEY_SAVED_PICTURE_PATH, null);
            text = savedInstanceState.getString(KEY_SAVED_INPUT_TEXT, text);
        }

        LinearLayout addNoteView = createAddNoteView(text, inflater);

        if (nativeSaveButton) {
            Button button = (Button) addNoteView.findViewById(R.id.create_note);
            button.setVisibility(View.VISIBLE);
            button.setOnClickListener(v -> {
                addLabel();
                hideKeyboard(v);
            });
        }

        return addNoteView;
    }

    private void hideKeyboard(View v) {
        InputMethodManager imm = (InputMethodManager) getActivity().getSystemService(
                Context.INPUT_METHOD_SERVICE);
        imm.hideSoftInputFromWindow(v.getWindowToken(), 0);
    }

    @NonNull
    private LinearLayout createAddNoteView(String text, LayoutInflater inflater) {
        final LinearLayout rootView =
                (LinearLayout) inflater.inflate(R.layout.label_add, null);

        final ImageButton takePictureBtn =
                (ImageButton) rootView.findViewById(R.id.add_label_picture_btn);
        ImageView imageView = (ImageView) rootView.findViewById(R.id.picture_note_preview_image);

        // Note: Any layout used for this must have the text field edit_note_text.
        mInput = (EditText) rootView.findViewById(R.id.edit_note_text);
        mInput.setHint(mInput.getResources().getText(mHintTextId));
        mInput.setText(text);

        if (mShowTimestampSection) {
            rootView.findViewById(R.id.label_dialog_timestamp_section).setVisibility(View.VISIBLE);
            TextView timestampSection = (TextView) rootView.findViewById(R.id.edit_note_time);
            timestampSection.setText(mLabelTimeText);
            if (!TextUtils.isEmpty(mLabelTimeTextDescription)) {
                timestampSection.setContentDescription(mLabelTimeTextDescription);
            }
            timestampSection.setOnClickListener(v -> {
                int labelType = getLabelType();
                getTimestamp().subscribe(
                        t -> mListener.onAddNoteTimestampClicked(getCurrentValue(), labelType, t));
            });
        } else {
            rootView.findViewById(R.id.label_dialog_timestamp_section).setVisibility(View.GONE);
        }

        setViewVisibilities(takePictureBtn, imageView, !hasPicture());
        if (mPictureLabelPath != null) {
            PictureUtils.loadImage(getActivity(), imageView, mExperimentId, mPictureLabelPath);
        }

        takePictureBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                boolean grantedCamera = PermissionUtils.tryRequestingPermission(getActivity(),
                        Manifest.permission.CAMERA,
                        PictureUtils.PERMISSIONS_CAMERA, /* force retry */ true);
                // If camera permission was not granted, on permission result we try requesting
                // storage. If it was granted, then check storage separately here.
                if (grantedCamera) {
                    boolean grantedStorage = PermissionUtils.tryRequestingPermission(
                            getActivity(),
                            Manifest.permission.WRITE_EXTERNAL_STORAGE,
                            PictureUtils.PERMISSIONS_WRITE_EXTERNAL_STORAGE,
                                /* force retry */ true);
                    if (grantedStorage) {
                        // TODO: Error states if these are not granted (b/24303452)
                        mUuid = UUID.randomUUID().toString();
                        mPictureLabelPath = PictureUtils.capturePictureLabel(getActivity(),
                                mExperimentId, mUuid);
                    }
                }
            }
        });

        return rootView;
    }

    private int getLabelType() {
        if (hasPicture()) {
            return RunReviewFragment.LABEL_TYPE_PICTURE;
        } else {
            return RunReviewFragment.LABEL_TYPE_TEXT;
        }
    }

    private GoosciLabelValue.LabelValue getCurrentValue() {
        GoosciLabelValue.LabelValue result = new GoosciLabelValue.LabelValue();
        if (hasPicture()) {
            PictureLabelValue.populateLabelValue(result, mPictureLabelPath,
                    mInput.getText().toString());
        } else {
            TextLabelValue.populateLabelValue(result, mInput.getText().toString());
        }
        return result;
    }

    private void setViewVisibilities(View imageButton, View imageView, boolean textSideVisible) {
        imageView.setVisibility(textSideVisible ? View.GONE : View.VISIBLE);

        // Show the picture note button if the camera is available and the user
        // hasn't already taken a picture.
        imageButton.setVisibility(textSideVisible &&
                                  VideoSensor.isCameraAvailable(
                                          getActivity().getApplicationContext()) ?
                View.VISIBLE : View.GONE);
    }

    private void addLabel() {
        // Save this as a picture label if it has a picture, otherwise just save it as a text
        // label.
        getTimestamp().subscribe(t -> {
            if (hasPicture()) {
                addPictureLabel(t);
            } else {
                addTextLabel(t);
            }
        });
    }

    private void addTextLabel(long timestamp) {
        TextLabelValue labelValue = TextLabelValue.fromText(mInput.getText().toString());
        mInput.setText("");
        Label label = Label.newLabelWithValue(timestamp, labelValue);
        addLabel(label);
    }

    private Maybe<Long> getTimestamp() {
        if (mUseSavedTimestamp) {
            return Maybe.just(mTimestamp);
        } else {
            Maybe<AppSingleton> data = mDataBinder.bind();
            return data.map(s -> s.getSensorEnvironment().getDefaultClock().getNow());
        }
    }

    private void addPictureLabel(long timestamp) {
        PictureLabelValue labelValue = PictureLabelValue.fromPicture(mPictureLabelPath,
                mInput.getText().toString());
        Label label = Label.fromUuidAndValue(timestamp, mUuid, labelValue);
        addLabel(label);
        PictureUtils.scanFile(mPictureLabelPath, getActivity());
        mPictureLabelPath = null;
    }

    private void addLabel(final Label label) {
        mListener.adjustLabelBeforeAdd(label);

        // The listener may be cleared by onDetach() before the experiment/trial is written,
        // so save the MaybeConsumer here as a final var.
        if (TextUtils.equals(mTrialId, RecorderController.NOT_RECORDING_RUN_ID)) {
            mExperiment.addLabel(label);
        } else {
            mExperiment.getTrial(mTrialId).addLabel(label);
        }
        getDataController().flatMapSingle(dc -> saveExperiment(dc, mExperiment, label))
                           .subscribe(MaybeConsumers.toSingleObserver(mListener.onLabelAdd()));
    }

    /**
     * @return a single that only calls onSuccess if the Label is successfully added.
     */
    public static Single<Label> saveExperiment(DataController dataController, Experiment experiment,
            final Label label) {
        return RxDataController.updateExperiment(dataController, experiment)
                               .andThen(Single.just(label));
    }

    private Maybe<DataController> getDataController() {
        return mDataBinder.bind().map(s -> s.getDataController());
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == PictureUtils.REQUEST_TAKE_PHOTO) {
            Dialog dialog = getDialog();
            ImageView imageView = (ImageView) dialog.findViewById(R.id.picture_note_preview_image);
            if (resultCode == Activity.RESULT_OK) {
                PictureUtils.loadImage(getActivity(), imageView, mExperimentId, mPictureLabelPath);
            } else {
                mPictureLabelPath = null;
            }

            setViewVisibilities(dialog.findViewById(R.id.add_label_picture_btn), imageView,
                    mPictureLabelPath == null);
            dialog.setCancelable(true);
        }
    }

    private boolean hasPicture() {
        return !TextUtils.isEmpty(mPictureLabelPath);
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

    @Override
    public void onCancel(DialogInterface dialog) {
        // Delete the picture we've taken if we are not saving it.
        if (mPictureLabelPath != null) {
            // Delete the picture if we canceled this.
            File picture = new File(PictureLabelValue.getAbsoluteFilePath(mPictureLabelPath));
            if (picture.exists()) {
                picture.delete();
            }
            mPictureLabelPath = null;
        }
        super.onCancel(dialog);
    }
}
