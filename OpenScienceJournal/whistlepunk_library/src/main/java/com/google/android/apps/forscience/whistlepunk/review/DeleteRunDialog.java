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

import android.app.AlertDialog;
import android.app.DialogFragment;
import android.content.DialogInterface;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;

import com.google.android.apps.forscience.whistlepunk.R;

/**
 * DialogFragment for deleting a run.
 */
public class DeleteRunDialog extends DialogFragment {
    public static final String TAG = "stop_recording_dialog";

    public interface DeleteRunDialogListener {
        public void requestDeleteRun();
    }

    public static DeleteRunDialog newInstance() {
        DeleteRunDialog dialog = new DeleteRunDialog();
        return dialog;
    }

    public DeleteRunDialog() {
    }

    public AlertDialog onCreateDialog(Bundle savedInstanceState) {
        AlertDialog.Builder alertDialog = new AlertDialog.Builder(getActivity());
        final View rootView = LayoutInflater.from(getActivity()).inflate(
                R.layout.delete_run_dialog, null);
        alertDialog.setView(rootView);
        alertDialog.setTitle(R.string.delete_run_dialog_title);

        alertDialog.setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                ((DeleteRunDialogListener) getParentFragment()).requestDeleteRun();
            }
        });
        alertDialog.setNegativeButton(android.R.string.cancel,
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.cancel();
                    }
                });
        alertDialog.setCancelable(true);
        return alertDialog.create();
    }
}
