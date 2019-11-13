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

import android.content.DialogInterface;
import android.os.Bundle;
import androidx.fragment.app.DialogFragment;
import androidx.appcompat.app.AlertDialog;

/** DialogFragment for when recording fails to stop. */
public class StopRecordingNoDataDialog extends DialogFragment {

  public static final String TAG = "stop_recording_dialog";
  public static final String KEY_BODY_STRING = "key_body_string";

  public interface StopRecordingDialogListener {
    public void requestCancelRecording();
  }

  public static StopRecordingNoDataDialog newInstance(String text) {
    StopRecordingNoDataDialog dialog = new StopRecordingNoDataDialog();

    Bundle args = new Bundle();
    args.putString(KEY_BODY_STRING, text);
    dialog.setArguments(args);

    return dialog;
  }

  public StopRecordingNoDataDialog() {}

  public AlertDialog onCreateDialog(Bundle savedInstanceState) {
    String text = "";
    if (getArguments() != null) {
      text = getArguments().getString(KEY_BODY_STRING, "");
    }
    AlertDialog.Builder alertDialog = new AlertDialog.Builder(getActivity());
    alertDialog.setTitle(getResources().getString(R.string.recording_stop_failed_no_data_title));
    alertDialog.setMessage(text);
    alertDialog.setNegativeButton(
        R.string.recording_stop_failed_continue,
        (dialog, which) -> {
          // do nothing, we'll continue recording
        });
    alertDialog.setPositiveButton(
        R.string.recording_stop_failed_cancel,
        new DialogInterface.OnClickListener() {
          @Override
          public void onClick(DialogInterface dialog, int which) {
            if (getParentFragment() != null) {
              ((StopRecordingDialogListener) getParentFragment()).requestCancelRecording();
            }
          }
        });

    alertDialog.setCancelable(true);
    return alertDialog.create();
  }
}
