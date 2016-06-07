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
