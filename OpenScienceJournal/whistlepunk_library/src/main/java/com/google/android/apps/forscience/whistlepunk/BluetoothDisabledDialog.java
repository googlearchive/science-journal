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

import android.bluetooth.BluetoothAdapter;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import androidx.fragment.app.DialogFragment;
import androidx.appcompat.app.AlertDialog;

public class BluetoothDisabledDialog extends DialogFragment {
  public static final String TAG = "bluetooth_disabled_dialog";

  public BluetoothDisabledDialog() {}

  @Override
  public AlertDialog onCreateDialog(Bundle savedInstanceState) {
    final AlertDialog.Builder alertDialog = new AlertDialog.Builder(getActivity());
    alertDialog.setMessage(R.string.bluetooth_not_on_body);
    alertDialog.setTitle(R.string.bluetooth_not_on_title);
    alertDialog.setPositiveButton(
        R.string.bluetooth_not_on_ok,
        new DialogInterface.OnClickListener() {
          @Override
          public void onClick(DialogInterface dialog, int which) {
            Intent intent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            getActivity().startActivity(intent);
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
    alertDialog.setCancelable(true);

    return alertDialog.create();
  }
}
