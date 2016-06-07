package com.google.android.apps.forscience.whistlepunk;

import android.Manifest;
import android.app.Activity;
import android.app.Dialog;
import android.app.DialogFragment;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.AlertDialog;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.bumptech.glide.Glide;
import com.google.android.apps.forscience.javalib.MaybeConsumer;
import com.google.android.apps.forscience.whistlepunk.metadata.GoosciLabelValue;
import com.google.android.apps.forscience.whistlepunk.metadata.Label;
import com.google.android.apps.forscience.whistlepunk.metadata.PictureLabel;
import com.google.android.apps.forscience.whistlepunk.metadata.TextLabel;
import com.google.android.apps.forscience.whistlepunk.review.RunReviewFragment;
import com.google.android.apps.forscience.whistlepunk.sensors.VideoSensor;
import com.google.protobuf.nano.InvalidProtocolBufferNanoException;

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
    private static final String KEY_SHOW_TIMESTAMP_SECTION = "savedShowTimestampSection";
    private static final String KEY_LABEL_TIME_TEXT = "savedLabelTimeText";
    private static final String KEY_SAVED_VALUE = "savedLabelValue";

    public interface AddNoteDialogListener {
        /**
         * Called when a label is being added to the database. Return value is passed to data
         * controller during the label add.
         * @return A MaybeConsumer of labels.
         */
        public MaybeConsumer<Label> onLabelAdd();

        /**
         * Called when the timestamp section is clicked. Note that this is only used if
         * showTimestampSection is true when creating a newInstance of AddNoteDialog.
         */
        public void onAddNoteTimestampClicked(GoosciLabelValue.LabelValue selectedValue,
                int labelType, long selectedTimestamp);
    }

    private long mTimestamp;
    private boolean mShowTimestampSection;
    private String mLabelTimeText;
    private String mRunId;
    private String mExperimentId;
    private int mHintTextId = R.string.add_run_note_placeholder_text;
    private String mPictureLabelPath;
    private EditText mInput;

    public static AddNoteDialog newInstance(long timestamp, String currentRunId,
            String experimentId, int hintTextId, boolean showTimestampSection,
            String labelTimeText) {
        AddNoteDialog dialog = new AddNoteDialog();

        Bundle args = new Bundle();
        args.putLong(KEY_SAVED_TIMESTAMP, timestamp);
        args.putString(KEY_SAVED_RUN_ID, currentRunId);
        args.putString(KEY_SAVED_EXPERIMENT_ID, experimentId);
        args.putInt(KEY_HINT_TEXT_ID, hintTextId);
        args.putBoolean(KEY_SHOW_TIMESTAMP_SECTION, showTimestampSection);
        args.putString(KEY_LABEL_TIME_TEXT, labelTimeText);
        dialog.setArguments(args);

        return dialog;
    }

    public static AddNoteDialog newInstance(long timestamp, String currentRunId,
            String experimentId, int hintTextId, boolean showTimestampSection,
            String labelTimeText, GoosciLabelValue.LabelValue currentValue, int labelType) {
        AddNoteDialog dialog = AddNoteDialog.newInstance(timestamp, currentRunId, experimentId,
                hintTextId, showTimestampSection, labelTimeText);

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

    @Override
    public void onSaveInstanceState(Bundle outState) {
        outState.putString(KEY_SAVED_PICTURE_PATH, mPictureLabelPath);
        outState.putString(KEY_SAVED_INPUT_TEXT,
                mInput == null ? null : mInput.getText().toString());
    }

    @Override
    public AlertDialog onCreateDialog(Bundle savedInstanceState) {
        String text = "";
        GoosciLabelValue.LabelValue value;
        if (getArguments() != null) {
            mTimestamp = getArguments().getLong(KEY_SAVED_TIMESTAMP, -1);
            mShowTimestampSection = getArguments().getBoolean(KEY_SHOW_TIMESTAMP_SECTION, false);
            mRunId = getArguments().getString(KEY_SAVED_RUN_ID);
            mExperimentId = getArguments().getString(KEY_SAVED_EXPERIMENT_ID);
            mHintTextId = getArguments().getInt(KEY_HINT_TEXT_ID, mHintTextId);
            mLabelTimeText = getArguments().getString(KEY_LABEL_TIME_TEXT, "");
            mPictureLabelPath = getArguments().getString(KEY_SAVED_PICTURE_PATH, null);
            if (getArguments().containsKey(KEY_SAVED_VALUE)) {
                try {
                    value = GoosciLabelValue.LabelValue.parseFrom(
                            getArguments().getByteArray(KEY_SAVED_VALUE));
                    mPictureLabelPath = PictureLabel.getFilePath(value);
                    text = TextLabel.getText(value);
                    if (TextUtils.isEmpty(text)) {
                        text = PictureLabel.getCaption(value);
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

        final AlertDialog.Builder alertDialog = inflateAddNoteView(text);
        alertDialog.setCancelable(true);
        final AlertDialog dialog = alertDialog.create();
        if (!hasPicture()) {
            dialog.getWindow().setSoftInputMode(
                    WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
        }
        return dialog;
    }

    private AlertDialog.Builder inflateAddNoteView(String text) {
        AlertDialog.Builder alertDialog = new AlertDialog.Builder(getActivity());
        final LinearLayout rootView =
                (LinearLayout) LayoutInflater.from(getActivity()).inflate(R.layout.label_add, null);
        alertDialog.setView(rootView);

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
            timestampSection.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    ((AddNoteDialogListener) getParentFragment()).onAddNoteTimestampClicked(
                            getCurrentValue(), hasPicture() ? RunReviewFragment.LABEL_TYPE_PICTURE :
                                    RunReviewFragment.LABEL_TYPE_TEXT,
                            mTimestamp);
                }
            });
        } else {
            rootView.findViewById(R.id.label_dialog_timestamp_section).setVisibility(View.GONE);
        }

        setViewVisibilities(takePictureBtn, imageView, !hasPicture());
        if (mPictureLabelPath != null) {
            Glide.clear(imageView);
            Glide.with(getParentFragment())
                    .load(mPictureLabelPath)
                    .into(imageView);
        }
        alertDialog.setPositiveButton(R.string.action_save, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                addLabel();
            }
        });
        alertDialog.setNegativeButton(android.R.string.cancel,
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        mPictureLabelPath = null;
                        dialog.cancel();
                    }
                });

            takePictureBtn.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    boolean grantedCamera = PictureUtils.tryRequestingPermission(getActivity(),
                            Manifest.permission.CAMERA,
                            PictureUtils.PERMISSIONS_CAMERA, /* force retry */ true);
                    boolean grantedStorage = PictureUtils.tryRequestingPermission(getActivity(),
                            Manifest.permission.WRITE_EXTERNAL_STORAGE,
                            PictureUtils.PERMISSIONS_WRITE_EXTERNAL_STORAGE,
                            /* force retry */ true);
                    if (grantedStorage && grantedCamera) {
                        // TODO: Error states if these are not granted (b/24303452)
                        mPictureLabelPath = PictureUtils.capturePictureLabel(getActivity());
                    }
                }
            });

        return alertDialog;
    }

    private GoosciLabelValue.LabelValue getCurrentValue() {
        GoosciLabelValue.LabelValue result = new GoosciLabelValue.LabelValue();
        if (hasPicture()) {
            PictureLabel.populateStorageValue(result, mPictureLabelPath,
                    mInput.getText().toString());
        } else {
            TextLabel.populateStorageValue(result, mInput.getText().toString());
        }
        return result;
    }

    private void setViewVisibilities(View imageButton, View imageView, boolean textSideVisible) {
        imageView.setVisibility(textSideVisible ? View.GONE : View.VISIBLE);

        // Show the picture note button if the camera is available and the user
        // hasn't already taken a picture.
        imageButton.setVisibility(textSideVisible &&
                VideoSensor.isCameraAvailable(getActivity().getApplicationContext()) ?
                View.VISIBLE : View.GONE);
    }

    private void addLabel() {
        if (hasPicture()) {
            addPictureLabel();
        } else {
            addTextLabel();
        }
    }

    private void addTextLabel() {
        TextLabel label = new TextLabel(mInput.getText().toString(),
                getDataController().generateNewLabelId(), mRunId, mTimestamp);
        label.setExperimentId(mExperimentId);
        getDataController().addLabel(label,
                ((AddNoteDialogListener) getParentFragment()).onLabelAdd());
    }

    private void addPictureLabel() {
        PictureLabel label = new PictureLabel(mPictureLabelPath, mInput.getText().toString(),
                getDataController().generateNewLabelId(), mRunId,
                mTimestamp);
        label.setExperimentId(mExperimentId);
        getDataController().addLabel(label,
                ((AddNoteDialogListener) getParentFragment()).onLabelAdd());
        mPictureLabelPath = null;
    }

    private DataController getDataController() {
        return AppSingleton.getInstance(getActivity()).getDataController();
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == PictureUtils.REQUEST_TAKE_PHOTO) {
            Dialog dialog = getDialog();
            ImageView imageView = (ImageView) dialog.findViewById(R.id.picture_note_preview_image);
            if (resultCode == Activity.RESULT_OK) {
                Glide.clear(imageView);
                Glide.with(getParentFragment())
                        .load(mPictureLabelPath)
                        .into(imageView);
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
}
