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

import android.app.Dialog;
import android.content.DialogInterface;
import android.os.Bundle;
import androidx.fragment.app.DialogFragment;
import androidx.appcompat.app.AlertDialog;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.TextView;
import com.google.android.apps.forscience.whistlepunk.R;
import java.util.Locale;

/** A dialog for editing RunReviewOverlay timestamps directly. */
public class EditTimestampDialog extends DialogFragment {
  public static String TAG = "EditTimestampDialog";
  private static final String KEY_EDITING_START = "keyEditingStart";
  private static final String KEY_MIN_MS = "keyMinMs";
  private static final String KEY_MAX_MS = "keyMaxMs";
  private static final String KEY_ZERO_MS = "keyZeroMs";
  private static final String KEY_CURRENT_MS = "keyCurrentMs";

  // TODO do we need to save these for rotation? Or can they be local vars?
  private boolean isEditingStart;
  private TimestampPickerController picker;

  private EditText inputText;
  private TimestampPickerController.TimestampPickerListener listener;

  public static EditTimestampDialog newInstance(
      boolean isEditingStart, long minMs, long maxMs, long zeroMs, long currentMs) {
    EditTimestampDialog dialog = new EditTimestampDialog();
    Bundle args = new Bundle();
    args.putBoolean(KEY_EDITING_START, isEditingStart);
    args.putLong(KEY_MIN_MS, minMs);
    args.putLong(KEY_MAX_MS, maxMs);
    args.putLong(KEY_ZERO_MS, zeroMs);
    args.putLong(KEY_CURRENT_MS, currentMs);
    dialog.setArguments(args);
    return dialog;
  }

  public EditTimestampDialog() {
    setHasOptionsMenu(true);
  }

  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    isEditingStart = getArguments().getBoolean(KEY_EDITING_START);
    picker =
        new TimestampPickerController(
            Locale.getDefault(),
            getActivity(),
            isEditingStart,
            new TimestampPickerController.OnTimestampErrorListener() {
              @Override
              public void onTimestampError(int errorId) {
                inputText.setError(inputText.getContext().getResources().getString(errorId));
              }
            });
    if (listener != null) {
      setupListener();
    }
  }

  @Override
  public AlertDialog onCreateDialog(Bundle savedInstanceState) {
    long minMs = getArguments().getLong(KEY_MIN_MS);
    long maxMs = getArguments().getLong(KEY_MAX_MS);
    long zeroMs = getArguments().getLong(KEY_ZERO_MS);
    long currentMs = getArguments().getLong(KEY_CURRENT_MS);
    picker.setTimestampRange(minMs, maxMs, zeroMs, currentMs);

    final AlertDialog.Builder alertDialog = new AlertDialog.Builder(getActivity());
    View rootView =
        LayoutInflater.from(getActivity()).inflate(R.layout.edit_timestamp_dialog, null);

    inputText = (EditText) rootView.findViewById(R.id.timestamp_text);
    inputText.setText(picker.getTimeString());

    inputText.setOnEditorActionListener(
        new TextView.OnEditorActionListener() {
          @Override
          public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
              if (picker.trySavingTimestamp(inputText.getText().toString())) {
                getDialog().dismiss();
              }
              return true;
            }
            return false;
          }
        });

    alertDialog.setView(rootView);
    alertDialog.setTitle(
        isEditingStart ? R.string.edit_crop_start_time_title : R.string.edit_crop_end_time_title);

    alertDialog.setPositiveButton(
        android.R.string.ok,
        new DialogInterface.OnClickListener() {
          @Override
          public void onClick(DialogInterface dialog, int which) {
            // Initialize in onStart so that we can keep the dialog from closing after the OK
            // button is pushed, if the input was invalid.
          }
        });
    alertDialog.setNegativeButton(
        android.R.string.cancel,
        new DialogInterface.OnClickListener() {
          @Override
          public void onClick(DialogInterface dialog, int which) {
            dialog.cancel();
          }
        });

    return alertDialog.create();
  }

  @Override
  public void onStart() {
    super.onStart();
    final AlertDialog dialog = (AlertDialog) getDialog();
    dialog
        .getButton(Dialog.BUTTON_POSITIVE)
        .setOnClickListener(
            new View.OnClickListener() {
              @Override
              public void onClick(View v) {
                if (picker.trySavingTimestamp(inputText.getText().toString())) {
                  dialog.dismiss();
                }
              }
            });
  }

  public void setOnPickerTimestampChangedListener(
      final TimestampPickerController.TimestampPickerListener listener) {
    this.listener = listener;
    if (picker != null) {
      setupListener();
    }
  }

  private void setupListener() {
    picker.setOnPickerTimestampChangedListener(
        new TimestampPickerController.TimestampPickerListener() {
          @Override
          public int isValidTimestamp(long timestamp, boolean isStartCrop) {
            return listener.isValidTimestamp(timestamp, isStartCrop);
          }

          @Override
          public void onPickerTimestampChanged(long timestamp, boolean isStartCrop) {
            listener.onPickerTimestampChanged(timestamp, isStartCrop);
            getDialog().dismiss();
          }
        });
  }
}
