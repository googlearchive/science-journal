package com.google.android.apps.forscience.whistlepunk;

import android.app.DialogFragment;
import android.bluetooth.BluetoothAdapter;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.AlertDialog;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;


public class BluetoothDisabledDialog extends DialogFragment {
    public static final String TAG = "bluetooth_disabled_dialog";

    public BluetoothDisabledDialog() {

    }

    @Override
    public AlertDialog onCreateDialog(Bundle savedInstanceState) {
        final AlertDialog.Builder alertDialog = new AlertDialog.Builder(getActivity());
        alertDialog.setMessage(R.string.bluetooth_not_on_body);
        alertDialog.setTitle(R.string.bluetooth_not_on_title);
        alertDialog.setPositiveButton(R.string.bluetooth_not_on_ok,
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        Intent intent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                        getActivity().startActivity(intent);
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
