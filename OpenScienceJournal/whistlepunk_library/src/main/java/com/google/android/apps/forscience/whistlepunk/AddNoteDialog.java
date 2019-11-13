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
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;
import androidx.appcompat.app.AlertDialog;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.google.android.apps.forscience.javalib.MaybeConsumer;
import com.google.android.apps.forscience.javalib.MaybeConsumers;
import com.google.android.apps.forscience.whistlepunk.accounts.AppAccount;
import com.google.android.apps.forscience.whistlepunk.filemetadata.Experiment;
import com.google.android.apps.forscience.whistlepunk.filemetadata.Label;
import com.google.android.apps.forscience.whistlepunk.filemetadata.PictureLabelValue;
import com.google.android.apps.forscience.whistlepunk.metadata.GoosciCaption;
import com.google.android.apps.forscience.whistlepunk.metadata.GoosciCaption.Caption;
import com.google.android.apps.forscience.whistlepunk.metadata.GoosciLabel;
import com.google.android.apps.forscience.whistlepunk.metadata.GoosciPictureLabelValue;
import com.google.android.apps.forscience.whistlepunk.metadata.GoosciTextLabelValue.TextLabelValue;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.jakewharton.rxbinding2.widget.RxTextView;
import io.reactivex.Single;
import io.reactivex.disposables.CompositeDisposable;
import java.io.File;
import java.util.UUID;

/** Dialog for adding new notes. */
public class AddNoteDialog extends DialogFragment {
  public static final String TAG = "add_note_dialog";

  private static final String KEY_SAVED_INPUT_TEXT = "savedInputText";
  private static final String KEY_SAVED_PICTURE_PATH = "savedPicturePath";
  private static final String KEY_SAVED_ACCOUNT_KEY = "savedAccountKey";
  private static final String KEY_SAVED_TIMESTAMP = "savedTimestamp";
  private static final String KEY_SAVED_RUN_ID = "savedRunId";
  private static final String KEY_SAVED_EXPERIMENT_ID = "savedExperimentId";
  private static final String KEY_HINT_TEXT_ID = "savedHintTextId";
  private static final String KEY_LABEL_TIME_TEXT = "savedLabelTimeText";
  private static final String KEY_SAVED_VALUE = "savedLabelValue";
  private static final String KEY_SAVED_TIME_TEXT_DESCRIPTION = "keySavedTimeTextDescription";
  private static final String KEY_SAVED_LABEL_UUID = "keySavedLabelUuid";

  private String uuid;

  public abstract static class AddNoteDialogListener {
    /**
     * Called with a label that's about to be added. Listener can adjust the label by, for example,
     * changing the timestamp
     */
    public void adjustLabelBeforeAdd(Label label) {
      // do nothing;
    }

    /**
     * Called when a label is being added to the database. Return value is passed to data controller
     * during the label add.
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
    public void onAddNoteTimestampClicked(Label editedLabel, long selectedTimestamp) {}

    /**
     * @return If the experiment id is not known at creation time, subscribe to this to get it when
     *     set.
     */
    public abstract Single<String> whenExperimentId();
  }

  public interface ListenerProvider {
    AddNoteDialogListener getAddNoteDialogListener();
  }

  private AddNoteDialogListener listener = null;
  private long timestamp;
  private String labelTimeText;
  private String labelTimeTextDescription = "";
  private String trialId;
  private int hintTextId = R.string.add_run_note_placeholder_text;
  private String pictureLabelPath;
  private TextInputEditText input;
  private TextInputLayout inputLayout;
  private AppAccount appAccount;
  private String experimentId;

  CompositeDisposable untilDestroyed = new CompositeDisposable();

  public static AddNoteDialog newInstance(
      AppAccount appAccount,
      long timestamp,
      String currentRunId,
      String experimentId,
      int hintTextId,
      String labelTimeText,
      Label editedLabel,
      String labelTimeTextDescription) {
    AddNoteDialog dialog = new AddNoteDialog();

    Bundle args = new Bundle();
    args.putString(KEY_SAVED_ACCOUNT_KEY, appAccount.getAccountKey());
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

  public AddNoteDialog() {}

  @Override
  public void onSaveInstanceState(Bundle outState) {
    super.onSaveInstanceState(outState);
    outState.putString(KEY_SAVED_PICTURE_PATH, pictureLabelPath);
    outState.putString(KEY_SAVED_INPUT_TEXT, input == null ? null : input.getText().toString());
    outState.putString(KEY_SAVED_LABEL_UUID, uuid);
  }

  @Nullable
  @Override
  public View onCreateView(
      LayoutInflater inflater, @Nullable ViewGroup container, Bundle savedInstanceState) {
    if (getShowsDialog()) {
      // dialog builder will build entire view in dialog mode, don't try to replicate here.
      return super.onCreateView(inflater, container, savedInstanceState);
    }

    return createAddNoteViewFromSavedState(savedInstanceState, inflater, true);
  }

  @Override
  public AlertDialog onCreateDialog(Bundle savedInstanceState) {
    final LinearLayout rootView =
        createAddNoteViewFromSavedState(
            savedInstanceState, LayoutInflater.from(getActivity()), false);

    AlertDialog.Builder alertDialog = new AlertDialog.Builder(getActivity());
    alertDialog.setView(rootView);
    alertDialog.setPositiveButton(R.string.action_save, null);
    alertDialog.setNegativeButton(android.R.string.cancel, (dialog, which) -> dialog.cancel());
    alertDialog.setCancelable(true);
    AlertDialog dialog = alertDialog.create();
    if (!hasPicture()) {
      dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
    }

    // Set the click listener for the positive button separately so we can control
    // when the dialog closes. This allows us to not close the dialog in the case of
    // an error.
    dialog.setOnShowListener(
        dialogInterface -> {
          Button button = dialog.getButton(DialogInterface.BUTTON_POSITIVE);
          button.setEnabled(false);
          whenExperiment(getActivity())
              .subscribe(
                  experiment -> {
                    button.setOnClickListener(view -> addLabel(experiment));
                    updatePositiveButtonEnabled((AlertDialog) getDialog(), true);
                  },
                  error -> {
                    if (Log.isLoggable(TAG, Log.ERROR)) {
                      Log.e(TAG, "AddNoteDialog setOnShowListener failed", error);
                    }
                    throw new IllegalStateException(
                        "AddNoteDialog setOnShowListener failed", error);
                  });
        });
    return dialog;
  }

  @Override
  public void onDestroy() {
    untilDestroyed.dispose();
    super.onDestroy();
  }

  @Override
  public void onDetach() {
    listener = null;
    super.onDetach();
  }

  private AddNoteDialogListener getListener(Context context) {
    if (listener == null) {
      if (context instanceof ListenerProvider) {
        listener = ((ListenerProvider) context).getAddNoteDialogListener();
      } else {
        Fragment parentFragment = getParentFragment();
        if (parentFragment instanceof ListenerProvider) {
          listener = ((ListenerProvider) parentFragment).getAddNoteDialogListener();
        }
      }
    }
    return listener;
  }

  @NonNull
  private LinearLayout createAddNoteViewFromSavedState(
      Bundle savedInstanceState, LayoutInflater inflater, boolean nativeSaveButton) {
    String text = "";
    if (getArguments() != null) {
      appAccount =
          WhistlePunkApplication.getAccount(getContext(), getArguments(), KEY_SAVED_ACCOUNT_KEY);
      timestamp = getArguments().getLong(KEY_SAVED_TIMESTAMP, -1);
      hintTextId = getArguments().getInt(KEY_HINT_TEXT_ID, hintTextId);
      labelTimeText = getArguments().getString(KEY_LABEL_TIME_TEXT, "");
      trialId = getArguments().getString(KEY_SAVED_RUN_ID);
      experimentId = getArguments().getString(KEY_SAVED_EXPERIMENT_ID);

      if (getArguments().containsKey(KEY_SAVED_TIME_TEXT_DESCRIPTION)) {
        labelTimeTextDescription = getArguments().getString(KEY_SAVED_TIME_TEXT_DESCRIPTION);
      }
      if (getArguments().containsKey(KEY_SAVED_VALUE)) {
        Label savedLabel = getArguments().getParcelable(KEY_SAVED_VALUE);
        if (savedLabel != null) {
          if (savedLabel.getType() == GoosciLabel.Label.ValueType.TEXT) {
            // Text labels don't use the caption field.
            text = savedLabel.getTextLabelValue().getText();
          } else {
            text = savedLabel.getCaptionText();
          }
          if (savedLabel.getType() == GoosciLabel.Label.ValueType.PICTURE) {
            pictureLabelPath = savedLabel.getPictureLabelValue().getFilePath();
          }
          uuid = savedLabel.getLabelId();
        }
      }
    }
    if (savedInstanceState != null) {
      // SavedInstanceState is more recent than args and may override the values.
      pictureLabelPath = savedInstanceState.getString(KEY_SAVED_PICTURE_PATH, null);
      text = savedInstanceState.getString(KEY_SAVED_INPUT_TEXT, text);
      uuid = savedInstanceState.getString(KEY_SAVED_LABEL_UUID, null);
    }

    LinearLayout addNoteView = createAddNoteView(text, inflater);

    if (nativeSaveButton) {
      Button button = (Button) addNoteView.findViewById(R.id.create_note);
      whenExperiment(button.getContext())
          .subscribe(
              experiment -> {
                button.setVisibility(View.VISIBLE);
                button.setOnClickListener(
                    v -> {
                      addLabel(experiment);
                      hideKeyboard(v);
                    });
              },
              error -> {
                if (Log.isLoggable(TAG, Log.ERROR)) {
                  Log.e(TAG, "AddNoteDialog nativeSaveButton failed", error);
                }
                throw new IllegalStateException("AddNoteDialog nativeSaveButton failed", error);
              });
    }

    return addNoteView;
  }

  @VisibleForTesting
  public static Single<String> whenExperimentId(
      String experimentId, AddNoteDialogListener listener) {
    if (experimentId != null) {
      // If already supplied, return it
      return Single.just(experimentId);
    } else {
      // Otherwise, wait for it.
      return listener.whenExperimentId();
    }
  }

  private void hideKeyboard(View v) {
    InputMethodManager imm =
        (InputMethodManager) getActivity().getSystemService(Context.INPUT_METHOD_SERVICE);
    imm.hideSoftInputFromWindow(v.getWindowToken(), 0);
  }

  @NonNull
  private LinearLayout createAddNoteView(String text, LayoutInflater inflater) {
    final LinearLayout rootView = (LinearLayout) inflater.inflate(R.layout.label_add, null);

    final ImageButton takePictureBtn =
        (ImageButton) rootView.findViewById(R.id.add_label_picture_btn);
    ImageView imageView = (ImageView) rootView.findViewById(R.id.picture_note_preview_image);

    // Note: Any layout used for this must have the text field edit_note_text.
    input = rootView.findViewById(R.id.edit_note_text);
    input.setHint(input.getResources().getText(hintTextId));
    input.setText(text);

    inputLayout = rootView.findViewById(R.id.edit_note_text_input_layout);

    RxTextView.afterTextChangeEvents(input)
        .subscribe(
            events -> {
              if (TextUtils.isEmpty(input.getText().toString())) {
                inputLayout.setError(getResources().getString(R.string.empty_text_note_error));
                inputLayout.setErrorEnabled(true);
              } else {
                inputLayout.setErrorEnabled(false);
              }
            });

    rootView.findViewById(R.id.label_dialog_timestamp_section).setVisibility(View.VISIBLE);
    TextView timestampSection = (TextView) rootView.findViewById(R.id.edit_note_time);
    timestampSection.setText(labelTimeText);
    if (!TextUtils.isEmpty(labelTimeTextDescription)) {
      timestampSection.setContentDescription(labelTimeTextDescription);
    }
    timestampSection.setOnClickListener(
        v -> {
          getListener(v.getContext()).onAddNoteTimestampClicked(getCurrentValue(), timestamp);
        });

    setViewVisibilities(takePictureBtn, imageView, !hasPicture());
    if (pictureLabelPath != null) {
      PictureUtils.loadExperimentImage(
          getActivity(), imageView, appAccount, experimentId, pictureLabelPath, false);
    }

    takePictureBtn.setOnClickListener(
        v -> {
          PermissionUtils.tryRequestingPermission(
              getActivity(),
              PermissionUtils.REQUEST_CAMERA,
              new PermissionUtils.PermissionListener() {
                @Override
                public void onPermissionGranted() {
                  uuid = UUID.randomUUID().toString();
                  pictureLabelPath =
                      PictureUtils.capturePictureLabel(
                          getActivity(), appAccount, experimentId, uuid);
                }

                @Override
                public void onPermissionDenied() {}

                @Override
                public void onPermissionPermanentlyDenied() {}
              });
        });

    return rootView;
  }

  private Label getCurrentValue() {
    long timestamp =
        AppSingleton.getInstance(getActivity()).getSensorEnvironment().getDefaultClock().getNow();
    if (hasPicture()) {
      GoosciPictureLabelValue.PictureLabelValue labelValue =
          GoosciPictureLabelValue.PictureLabelValue.newBuilder()
              .setFilePath(pictureLabelPath)
              .build();
      Caption caption =
          GoosciCaption.Caption.newBuilder()
              .setText(input.getText().toString())
              .setLastEditedTimestamp(timestamp)
              .build();
      return Label.newLabelWithValue(
          timestamp, GoosciLabel.Label.ValueType.PICTURE, labelValue, caption);
    } else {
      TextLabelValue labelValue =
          TextLabelValue.newBuilder().setText(input.getText().toString()).build();
      return Label.newLabelWithValue(timestamp, GoosciLabel.Label.ValueType.TEXT, labelValue, null);
    }
  }

  private void setViewVisibilities(View imageButton, View imageView, boolean textSideVisible) {
    imageView.setVisibility(textSideVisible ? View.GONE : View.VISIBLE);

    // Show the picture note button if the camera is available and the user
    // hasn't already taken a picture.
    imageButton.setVisibility(
        textSideVisible && isCameraAvailable(getActivity().getApplicationContext())
            ? View.VISIBLE
            : View.GONE);
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
    String text = input.getText().toString();
    if (inputLayout.isErrorEnabled()) {
      return false;
    }
    TextLabelValue labelValue = TextLabelValue.newBuilder().setText(text).build();
    input.setText("");
    Label label =
        Label.newLabelWithValue(timestamp, GoosciLabel.Label.ValueType.TEXT, labelValue, null);
    addLabel(label, getDataController(input.getContext()), experiment, input.getContext());
    return true;
  }

  private DataController getDataController(Context context) {
    return AppSingleton.getInstance(context).getDataController(appAccount);
  }

  private boolean addPictureLabel(Experiment experiment) {
    GoosciPictureLabelValue.PictureLabelValue labelValue =
        GoosciPictureLabelValue.PictureLabelValue.newBuilder()
            .setFilePath(pictureLabelPath)
            .build();
    Label label =
        Label.fromUuidAndValue(timestamp, uuid, GoosciLabel.Label.ValueType.PICTURE, labelValue);
    Caption caption =
        GoosciCaption.Caption.newBuilder()
            .setText(input.getText().toString())
            .setLastEditedTimestamp(label.getCreationTimeMs())
            .build();
    label.setCaption(caption);
    addLabel(label, getDataController(input.getContext()), experiment, input.getContext());
    pictureLabelPath = null;
    return true;
  }

  private void addLabel(
      final Label label, DataController dc, Experiment experiment, Context context) {
    AddNoteDialogListener listener = getListener(context);
    listener.adjustLabelBeforeAdd(label);

    RxDataController.addTrialLabel(label, dc, experiment, trialId)
        .subscribe(
            () -> {
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
        PictureUtils.loadExperimentImage(
            getActivity(), imageView, appAccount, experimentId, pictureLabelPath, false);
      } else {
        pictureLabelPath = null;
      }

      setViewVisibilities(
          dialog.findViewById(R.id.add_label_picture_btn), imageView, pictureLabelPath == null);
      dialog.setCancelable(true);
    }
  }

  private boolean hasPicture() {
    return !TextUtils.isEmpty(pictureLabelPath);
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
    if (pictureLabelPath != null) {
      // Delete the picture if we canceled this.
      File picture = new File(PictureLabelValue.getAbsoluteFilePath(pictureLabelPath));
      if (picture.exists()) {
        picture.delete();
      }
      pictureLabelPath = null;
    }
    super.onCancel(dialog);
  }

  private Single<Experiment> whenExperiment(Context context) {
    return RxDataController.getExperimentById(getDataController(context), experimentId)
        .doOnError(LoggingConsumer.complain(TAG, "get experiment"));
  }

  public static boolean isCameraAvailable(Context context) {
    return (context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY));
  }
}
