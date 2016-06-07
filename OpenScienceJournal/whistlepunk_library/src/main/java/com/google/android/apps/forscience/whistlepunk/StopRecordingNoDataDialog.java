package com.google.android.apps.forscience.whistlepunk;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.app.DialogFragment;

/**
 * DialogFragment for when recording fails to stop.
 */
public class StopRecordingNoDataDialog extends DialogFragment {

    public static final String TAG = "stop_recording_dialog";
    public static final String KEY_BODY_STRING = "key_body_string";

    public interface StopRecordingDialogListener {
        public void requestStopRecording();

        public void continueRecording();
    }

    public static StopRecordingNoDataDialog newInstance(String text) {
        StopRecordingNoDataDialog dialog = new StopRecordingNoDataDialog();

        Bundle args = new Bundle();
        args.putString(KEY_BODY_STRING, text);
        dialog.setArguments(args);

        return dialog;
    }

    public StopRecordingNoDataDialog() {

    }

    public AlertDialog onCreateDialog(Bundle savedInstanceState) {
        String text = "";
        if (getArguments() != null) {
             text = getArguments().getString(KEY_BODY_STRING, "");
        }
        AlertDialog.Builder alertDialog = new AlertDialog.Builder(getActivity());
        alertDialog.setTitle(getResources().getString(R.string.recording_stop_failed_no_data_title));
        alertDialog.setMessage(text);
        alertDialog.setNegativeButton(R.string.recording_stop_failed_continue,
                new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                ((StopRecordingDialogListener) getParentFragment()).continueRecording();
            }
        });
        alertDialog.setPositiveButton(R.string.recording_stop_failed_cancel,
                new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                ((StopRecordingDialogListener) getParentFragment()).requestStopRecording();
            }
        });
        alertDialog.setCancelable(true);
        return alertDialog.create();
    }
}
