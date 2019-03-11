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

package com.google.android.apps.forscience.whistlepunk.devicemanager;

import android.bluetooth.BluetoothAdapter;
import android.content.Intent;
import android.os.Bundle;
import androidx.fragment.app.DialogFragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import com.google.android.apps.forscience.whistlepunk.PermissionUtils;
import com.google.android.apps.forscience.whistlepunk.R;

/**
 * Shown to the user when Bluetooth scanning can't be done. Shows a button to the user which leads
 * to the system dialog for turning on Bluetooth.
 */
public class ScanDisabledDialogFragment extends DialogFragment {

  static ScanDisabledDialogFragment newInstance() {
    return new ScanDisabledDialogFragment();
  }

  @Override
  public View onCreateView(
      LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
    getDialog().setTitle(R.string.bluetooth_not_on_title);
    View view = inflater.inflate(R.layout.fragment_scan_disabled_dialog, container, false);
    Button button = (Button) view.findViewById(R.id.btn_enable_scan);

    final boolean hasPermission =
        PermissionUtils.hasPermission(
            getActivity(), PermissionUtils.REQUEST_ACCESS_COARSE_LOCATION);
    if (!hasPermission) {
      // Update the text of the button.
      button.setText(R.string.btn_request_location);
    }

    button.setOnClickListener(
        v -> {
          PermissionUtils.tryRequestingPermission(
              getActivity(),
              PermissionUtils.REQUEST_ACCESS_COARSE_LOCATION,
              new PermissionUtils.PermissionListener() {
                @Override
                public void onPermissionGranted() {
                  Intent intent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                  getActivity().startActivity(intent);
                }

                @Override
                public void onPermissionDenied() {}

                @Override
                public void onPermissionPermanentlyDenied() {}
              });
          dismiss();
        });
    return view;
  }
}
