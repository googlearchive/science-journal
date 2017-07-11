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
import com.google.android.apps.forscience.whistlepunk.sensors.VideoSensor;

import java.io.File;
import java.util.UUID;

import io.reactivex.Single;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.subjects.BehaviorSubject;

/**
 * Dialog for adding new notes.
 */
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
    private boolean mUseSavedTimestamp;
    private boolean mShowTimestampSection;
    private String mLabelTimeText;
    private String mLabelTimeTextDescription = "";
    private String mTrialId;
    private int mHintTextId = R.string.add_run_note_placeholder_text;
    private String mPictureLabelPath;
    private EditText mInput;
    private BehaviorSubject<String> mWhenExperimentId = BehaviorSubject.create();

    CompositeDisposable mUntilDestroyed = new CompositeDisposable();

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
     * Create an AddNoteDialog that will wait to be told what the current experiment is
     */
    public static AddNoteDialog createWithNoExperimentYet(int hintTextId) {
        return createWithDynamicTimestamp(RecorderController.NOT_RECORDING_RUN_ID, null,
                hintTextId);
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
            String labelTimeText, Label editedLabel,
            String labelTimeTextDescription, boolean shouldUseSavedTimestamp) {
        AddNoteDialog dialog = AddNoteDialog.newInstance(timestamp, currentRunId, experimentId,
                hintTextId, showTimestampSection, labelTimeText, labelTimeTextDescription,
                shouldUseSavedTimestamp);

        if (editedLabel == null) {
            // no user added value yet, so no need to store anything else.
            return dialog;
        }

        Bundle args = dialog.getArguments();
        args.putParcelable(KEY_SAVED_VALUE, editedLabel);

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


        Button button = dialog.getButton(DialogInterface.BUTTON_POSITIVE);
        button.setEnabled(false);

        whenExperiment(button.getContext()).subscribe(experiment ->
                // Set the click listener for the postive button separately so we can control
                // when the dialog closes. This allows us to not close the dialog in the case of
                // an error.
                dialog.setOnShowListener(
                        dialogInterface -> button.setOnClickListener(
                                view -> addLabel(getActivity(), experiment))));
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
            mUseSavedTimestamp = getArguments().getBoolean(KEY_SHOULD_USE_SAVED_TIMESTAMP, true);
            mShowTimestampSection = getArguments().getBoolean(KEY_SHOW_TIMESTAMP_SECTION, false);
            mHintTextId = getArguments().getInt(KEY_HINT_TEXT_ID, mHintTextId);
            mLabelTimeText = getArguments().getString(KEY_LABEL_TIME_TEXT, "");
            mTrialId = getArguments().getString(KEY_SAVED_RUN_ID);
            String experimentId = getArguments().getString(KEY_SAVED_EXPERIMENT_ID);

            if (experimentId != null) {
                // If already supplied, set it
                mWhenExperimentId.onNext(experimentId);
            } else {
                // Otherwise, wait for it.
                // TODO: be sure to unsubscribe once FragmentBinder CL is in.
                getListener(inflater.getContext()).whenExperimentId()
                                                  .toObservable()
                                                  .subscribe(mWhenExperimentId);
            }

            mUntilDestroyed.add(whenExperiment(inflater.getContext()).subscribe(
                    exp -> updatePositiveButtonEnabled((AlertDialog) getDialog(), true)));

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
            whenExperiment(button.getContext()).subscribe(experiment -> {
                        button.setVisibility(View.VISIBLE);
                        button.setOnClickListener(v -> {
                            addLabel(button.getContext(), experiment);
                            hideKeyboard(v);
                        });
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
                getListener(v.getContext()).onAddNoteTimestampClicked(getCurrentValue(),
                        getTimestamp(timestampSection.getContext()));
            });
        } else {
            rootView.findViewById(R.id.label_dialog_timestamp_section).setVisibility(View.GONE);
        }

        setViewVisibilities(takePictureBtn, imageView, !hasPicture());
        if (mPictureLabelPath != null) {
            mWhenExperimentId.firstElement()
                             .subscribe(id -> PictureUtils.loadExperimentImage(getActivity(),
                                     imageView, id, mPictureLabelPath));
        }

        takePictureBtn.setOnClickListener(v -> {
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

                    mWhenExperimentId.firstElement().subscribe(id -> {
                        mPictureLabelPath =
                                PictureUtils.capturePictureLabel(getActivity(), id, mUuid);
                    });
                }
            }
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
                                  VideoSensor.isCameraAvailable(
                                          getActivity().getApplicationContext()) ?
                View.VISIBLE : View.GONE);
    }

    private void addLabel(Context context, Experiment experiment) {
        // Save this as a picture label if it has a picture, otherwise just save it as a text
        // label.
        long t = getTimestamp(context);

        boolean success = false;
        if (hasPicture()) {
            success = addPictureLabel(t, experiment);
        } else {
            success = addTextLabel(t, experiment);
        }
        if (success) {
            Dialog dialog = getDialog();

            if (dialog != null) {
                dialog.dismiss();
            }
        }
    }

    private boolean addTextLabel(long timestamp, Experiment experiment) {
        String text = mInput.getText().toString();
        if (TextUtils.isEmpty(text)) {
            mInput.setError(getResources().getString(R.string.empty_text_note_error));
            return false;
        }
        GoosciTextLabelValue.TextLabelValue labelValue = new GoosciTextLabelValue.TextLabelValue();
        labelValue.text = text;
        mInput.setText("");
        Label label = Label.newLabelWithValue(timestamp, GoosciLabel.Label.TEXT,
                labelValue, null);
        addLabel(label, getDataController(mInput.getContext()), experiment, mInput.getContext());
        return true;
    }

    private DataController getDataController(Context context) {
        return AppSingleton.getInstance(context).getDataController();
    }


    private long getTimestamp(Context context) {
        if (mUseSavedTimestamp) {
            return mTimestamp;
        } else {
            return AppSingleton.getInstance(context)
                               .getSensorEnvironment()
                               .getDefaultClock()
                               .getNow();
        }
    }

    private boolean addPictureLabel(long timestamp, Experiment experiment) {
        GoosciPictureLabelValue.PictureLabelValue labelValue = new GoosciPictureLabelValue
                .PictureLabelValue();
        labelValue.filePath = mPictureLabelPath;
        Label label = Label.fromUuidAndValue(timestamp, mUuid, GoosciLabel.Label.PICTURE,
                labelValue);
        GoosciCaption.Caption caption = new GoosciCaption.Caption();
        caption.text = mInput.getText().toString();
        caption.lastEditedTimestamp = label.getCreationTimeMs();
        label.setCaption(caption);
        addLabel(label, getDataController(mInput.getContext()), experiment, mInput.getContext());
        PictureUtils.scanFile(mPictureLabelPath, getActivity());
        mPictureLabelPath = null;
        return true;
    }

    private void addLabel(final Label label, DataController dc, Experiment experiment,
            Context context) {
        AddNoteDialogListener listener = getListener(context);
        listener.adjustLabelBeforeAdd(label);

        // The listener may be cleared by onDetach() before the experiment/trial is written,
        // so save the MaybeConsumer here as a final var.
        if (TextUtils.equals(mTrialId, RecorderController.NOT_RECORDING_RUN_ID)) {
            experiment.addLabel(label);
        } else {
            experiment.getTrial(mTrialId).addLabel(label);
        }
        saveExperiment(dc, experiment, label).subscribe(
                MaybeConsumers.toSingleObserver(listener.onLabelAdd()));
    }

    /**
     * @return a single that only calls onSuccess if the Label is successfully added.
     */
    public static Single<Label> saveExperiment(DataController dataController, Experiment experiment,
            final Label label) {
        return RxDataController.updateExperiment(dataController, experiment)
                               .andThen(Single.just(label));
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == PictureUtils.REQUEST_TAKE_PHOTO) {
            Dialog dialog = getDialog();
            ImageView imageView = (ImageView) dialog.findViewById(R.id.picture_note_preview_image);
            if (resultCode == Activity.RESULT_OK) {
                mWhenExperimentId.firstElement().subscribe(
                        id -> PictureUtils.loadExperimentImage(getActivity(), imageView, id,
                                mPictureLabelPath));
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
        return mWhenExperimentId.firstElement()
                                .flatMapSingle(id -> RxDataController.getExperimentById(
                                        getDataController(context), id))
                                .doOnError(LoggingConsumer.complain(TAG, "get experiment"));
    }
}
