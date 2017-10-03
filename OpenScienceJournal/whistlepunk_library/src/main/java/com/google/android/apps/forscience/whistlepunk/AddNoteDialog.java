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

import android.app.Activity;
import android.app.Dialog;
import android.app.DialogFragment;
import android.app.Fragment;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.VisibleForTesting;
import android.support.v7.app.AlertDialog;
import android.text.TextUtils;
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
import com.google.android.apps.forscience.whistlepunk.metadata.GoosciCaption;
import com.google.android.apps.forscience.whistlepunk.metadata.GoosciLabel;
import com.google.android.apps.forscience.whistlepunk.metadata.GoosciPictureLabelValue;
import com.google.android.apps.forscience.whistlepunk.metadata.GoosciTextLabelValue;

import java.io.File;
import java.util.UUID;

import io.reactivex.Completable;
import io.reactivex.Single;
import io.reactivex.disposables.CompositeDisposable;

/**
 * Dialog for adding new notes.
 */
public class AddNoteDialog extends DialogFragment {
    public static final String TAG = "add_note_dialog";

    private static final String KEY_SAVED_INPUT_TEXT = "savedInputText";
    private static final String KEY_SAVED_PICTURE_PATH = "savedPicturePath";
    private static final String KEY_SAVED_TIMESTAMP = "savedTimestamp";
    private static final String KEY_SAVED_RUN_ID = "savedRunId";
    private static final String KEY_SAVED_EXPERIMENT_ID = "savedExperimentId";
    private static final String KEY_HINT_TEXT_ID = "savedHintTextId";
    private static final String KEY_LABEL_TIME_TEXT = "savedLabelTimeText";
    private static final String KEY_SAVED_VALUE = "savedLabelValue";
    private static final String KEY_SAVED_TIME_TEXT_DESCRIPTION = "keySavedTimeTextDescription";
    private static final String KEY_SAVED_LABEL_UUID = "keySavedLabelUuid";

    private String mUuid;

    public static abstract class AddNoteDialogListener {
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
        public void onAddNoteTimestampClicked(Label editedLabel, long selectedTimestamp) {

        }

        /**
         * @return If the experiment id is not known at creation time, subscribe to this to
         *         get it when set.
         */
        public abstract Single<String> whenExperimentId();
    }

    public interface ListenerProvider {
        AddNoteDialogListener getAddNoteDialogListener();
    }

    private AddNoteDialogListener mListener = null;
    private long mTimestamp;
    private String mLabelTimeText;
    private String mLabelTimeTextDescription = "";
    private String mTrialId;
    private int mHintTextId = R.string.add_run_note_placeholder_text;
    private String mPictureLabelPath;
    private EditText mInput;
    private String mExperimentId;

    CompositeDisposable mUntilDestroyed = new CompositeDisposable();

    public static AddNoteDialog newInstance(long timestamp, String currentRunId,
            String experimentId, int hintTextId, String labelTimeText, Label editedLabel,
            String labelTimeTextDescription) {
        AddNoteDialog dialog = new AddNoteDialog();

        Bundle args = new Bundle();
        args.putLong(KEY_SAVED_TIMESTAMP, timestamp);
        args.putString(KEY_SAVED_RUN_ID, currentRunId);
        args.putString(KEY_SAVED_EXPERIMENT_ID, experimentId);
        args.putInt(KEY_HINT_TEXT_ID, hintTextId);
        args.putString(KEY_LABEL_TIME_TEXT, labelTimeText);
        args.putString(KEY_SAVED_TIME_TEXT_DESCRIPTION, labelTimeTextDescription);

        if (editedLabel != null) {
            args.putParcelable(KEY_SAVED_VALUE, editedLabel);
        }

        dialog.setArguments(args);
        return dialog;
    }

    public AddNoteDialog() {
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString(KEY_SAVED_PICTURE_PATH, mPictureLabelPath);
        outState.putString(KEY_SAVED_INPUT_TEXT,
                mInput == null ? null : mInput.getText().toString());
        outState.putString(KEY_SAVED_LABEL_UUID, mUuid);
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
        alertDialog.setPositiveButton(R.string.action_save, null);
        alertDialog.setNegativeButton(android.R.string.cancel, (dialog, which) -> dialog.cancel());
        alertDialog.setCancelable(true);
        AlertDialog dialog = alertDialog.create();
        if (!hasPicture()) {
            dialog.getWindow().setSoftInputMode(
                    WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
        }

        // Set the click listener for the positive button separately so we can control
        // when the dialog closes. This allows us to not close the dialog in the case of
        // an error.
        dialog.setOnShowListener(dialogInterface -> {
            Button button = dialog.getButton(DialogInterface.BUTTON_POSITIVE);
            button.setEnabled(false);
            whenExperiment(getActivity()).subscribe(experiment -> {
                button.setOnClickListener(view -> addLabel(experiment));
                updatePositiveButtonEnabled((AlertDialog) getDialog(), true);
            });
        });
        return dialog;
    }

    @Override
    public void onDestroy() {
        mUntilDestroyed.dispose();
        super.onDestroy();
    }

    @Override
    public void onDetach() {
        mListener = null;
        super.onDetach();
    }

    private AddNoteDialogListener getListener(Context context) {
        if (mListener == null) {
            if (context instanceof ListenerProvider) {
                mListener = ((ListenerProvider) context).getAddNoteDialogListener();
            } else {
                Fragment parentFragment = getParentFragment();
                if (parentFragment instanceof ListenerProvider) {
                    mListener = ((ListenerProvider) parentFragment).getAddNoteDialogListener();
                }
            }
        }
        return mListener;
    }

    @NonNull
    private LinearLayout createAddNoteViewFromSavedState(Bundle savedInstanceState,
            LayoutInflater inflater, boolean nativeSaveButton) {
        String text = "";
        if (getArguments() != null) {
            mTimestamp = getArguments().getLong(KEY_SAVED_TIMESTAMP, -1);
            mHintTextId = getArguments().getInt(KEY_HINT_TEXT_ID, mHintTextId);
            mLabelTimeText = getArguments().getString(KEY_LABEL_TIME_TEXT, "");
            mTrialId = getArguments().getString(KEY_SAVED_RUN_ID);
            mExperimentId = getArguments().getString(KEY_SAVED_EXPERIMENT_ID);

            if (getArguments().containsKey(KEY_SAVED_TIME_TEXT_DESCRIPTION)) {
                mLabelTimeTextDescription =
                        getArguments().getString(KEY_SAVED_TIME_TEXT_DESCRIPTION);
            }
            if (getArguments().containsKey(KEY_SAVED_VALUE)) {
                Label savedLabel = getArguments().getParcelable(KEY_SAVED_VALUE);
                if (savedLabel != null) {
                    if (savedLabel.getType() == GoosciLabel.Label.TEXT) {
                        // Text labels don't use the caption field.
                        text = savedLabel.getTextLabelValue().text;
                    } else {
                        text = savedLabel.getCaptionText();
                    }
                    if (savedLabel.getType() == GoosciLabel.Label.PICTURE) {
                        mPictureLabelPath = savedLabel.getPictureLabelValue().filePath;
                    }
                    mUuid = savedLabel.getLabelId();
                }
            }
        }
        if (savedInstanceState != null) {
            // SavedInstanceState is more recent than args and may override the values.
            mPictureLabelPath = savedInstanceState.getString(KEY_SAVED_PICTURE_PATH, null);
            text = savedInstanceState.getString(KEY_SAVED_INPUT_TEXT, text);
            mUuid = savedInstanceState.getString(KEY_SAVED_LABEL_UUID, null);
        }

        LinearLayout addNoteView = createAddNoteView(text, inflater);

        if (nativeSaveButton) {
            Button button = (Button) addNoteView.findViewById(R.id.create_note);
            whenExperiment(button.getContext()).subscribe(experiment -> {
                        button.setVisibility(View.VISIBLE);
                        button.setOnClickListener(v -> {
                            addLabel(experiment);
                            hideKeyboard(v);
                        });
                    });
        }

        return addNoteView;
    }

    @VisibleForTesting
    public static Single<String> whenExperimentId(String experimentId,
            AddNoteDialogListener listener) {
        if (experimentId != null) {
            // If already supplied, return it
            return Single.just(experimentId);
        } else {
            // Otherwise, wait for it.
            return listener.whenExperimentId();
        }
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

        rootView.findViewById(R.id.label_dialog_timestamp_section).setVisibility(View.VISIBLE);
        TextView timestampSection = (TextView) rootView.findViewById(R.id.edit_note_time);
        timestampSection.setText(mLabelTimeText);
        if (!TextUtils.isEmpty(mLabelTimeTextDescription)) {
            timestampSection.setContentDescription(mLabelTimeTextDescription);
        }
        timestampSection.setOnClickListener(v -> {
            getListener(v.getContext()).onAddNoteTimestampClicked(getCurrentValue(),
                    mTimestamp);
        });

        setViewVisibilities(takePictureBtn, imageView, !hasPicture());
        if (mPictureLabelPath != null) {
            PictureUtils.loadExperimentImage(getActivity(), imageView, mExperimentId,
                    mPictureLabelPath);
        }

        takePictureBtn.setOnClickListener(v -> {
            PermissionUtils.tryRequestingPermission(getActivity(),
                    PermissionUtils.REQUEST_CAMERA,
                    new PermissionUtils.PermissionListener() {
                        @Override
                        public void onPermissionGranted() {
                            mUuid = UUID.randomUUID().toString();
                            mPictureLabelPath = PictureUtils.capturePictureLabel(getActivity(),
                                    mExperimentId, mUuid);
                        }

                        @Override
                        public void onPermissionDenied() {

                        }

                        @Override
                        public void onPermissionPermanentlyDenied() {

                        }
                    });
        });

        return rootView;
    }

    private Label getCurrentValue() {
        long timestamp = AppSingleton.getInstance(getActivity())
                                     .getSensorEnvironment().getDefaultClock().getNow();
        if (hasPicture()) {
            GoosciPictureLabelValue.PictureLabelValue labelValue =
                    new GoosciPictureLabelValue.PictureLabelValue();
            labelValue.filePath = mPictureLabelPath;
            GoosciCaption.Caption caption = new GoosciCaption.Caption();
            caption.text = mInput.getText().toString();
            caption.lastEditedTimestamp = timestamp;
            return Label.newLabelWithValue(timestamp, GoosciLabel.Label.PICTURE,
                    labelValue, caption);
        } else {
            GoosciTextLabelValue.TextLabelValue labelValue =
                    new GoosciTextLabelValue.TextLabelValue();
            labelValue.text = mInput.getText().toString();
            return Label.newLabelWithValue(timestamp, GoosciLabel.Label.TEXT, labelValue, null);
        }
    }

    private void setViewVisibilities(View imageButton, View imageView, boolean textSideVisible) {
        imageView.setVisibility(textSideVisible ? View.GONE : View.VISIBLE);

        // Show the picture note button if the camera is available and the user
        // hasn't already taken a picture.
        imageButton.setVisibility(textSideVisible &&
                                  isCameraAvailable(
                                          getActivity().getApplicationContext()) ?
                View.VISIBLE : View.GONE);
    }

    private void addLabel(Experiment experiment) {
        // Save this as a picture label if it has a picture, otherwise just save it as a text
        // label.

        boolean success = false;
        if (hasPicture()) {
            success = addPictureLabel(experiment);
        } else {
            success = addTextLabel(experiment);
        }
        if (success) {
            Dialog dialog = getDialog();

            if (dialog != null) {
                dialog.dismiss();
            }
        }
    }

    private boolean addTextLabel(Experiment experiment) {
        String text = mInput.getText().toString();
        if (TextUtils.isEmpty(text)) {
            mInput.setError(getResources().getString(R.string.empty_text_note_error));
            return false;
        }
        GoosciTextLabelValue.TextLabelValue labelValue = new GoosciTextLabelValue.TextLabelValue();
        labelValue.text = text;
        mInput.setText("");
        Label label = Label.newLabelWithValue(mTimestamp, GoosciLabel.Label.TEXT,
                labelValue, null);
        addLabel(label, getDataController(mInput.getContext()), experiment, mInput.getContext());
        return true;
    }

    private DataController getDataController(Context context) {
        return AppSingleton.getInstance(context).getDataController();
    }

    private boolean addPictureLabel(Experiment experiment) {
        GoosciPictureLabelValue.PictureLabelValue labelValue = new GoosciPictureLabelValue
                .PictureLabelValue();
        labelValue.filePath = mPictureLabelPath;
        Label label = Label.fromUuidAndValue(mTimestamp, mUuid, GoosciLabel.Label.PICTURE,
                labelValue);
        GoosciCaption.Caption caption = new GoosciCaption.Caption();
        caption.text = mInput.getText().toString();
        caption.lastEditedTimestamp = label.getCreationTimeMs();
        label.setCaption(caption);
        addLabel(label, getDataController(mInput.getContext()), experiment, mInput.getContext());
        mPictureLabelPath = null;
        return true;
    }

    private void addLabel(final Label label, DataController dc, Experiment experiment,
            Context context) {
        AddNoteDialogListener listener = getListener(context);
        listener.adjustLabelBeforeAdd(label);

        RxDataController.addTrialLabel(label, dc, experiment, mTrialId).subscribe(() -> {
            // in case the fragment creator is hoping for explicit notification
            listener.onLabelAdd().success(label);

            // All other global listeners
            AppSingleton.getInstance(context).onLabelsAdded().onNext(label);
        });
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == PictureUtils.REQUEST_TAKE_PHOTO) {
            Dialog dialog = getDialog();
            ImageView imageView = (ImageView) dialog.findViewById(R.id.picture_note_preview_image);
            if (resultCode == Activity.RESULT_OK) {
                PictureUtils.loadExperimentImage(getActivity(), imageView, mExperimentId,
                        mPictureLabelPath);
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

    private void updatePositiveButtonEnabled(AlertDialog dialog, boolean enabled) {
        if (dialog == null) {
            return;
        }
        Button positiveButton = dialog.getButton(DialogInterface.BUTTON_POSITIVE);
        positiveButton.setEnabled(enabled);
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

    private Single<Experiment> whenExperiment(Context context) {
        return RxDataController.getExperimentById(getDataController(context), mExperimentId)
                               .doOnError(LoggingConsumer.complain(TAG, "get experiment"));
    }

    public static boolean isCameraAvailable(Context context) {
        return (context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY));
    }
}
