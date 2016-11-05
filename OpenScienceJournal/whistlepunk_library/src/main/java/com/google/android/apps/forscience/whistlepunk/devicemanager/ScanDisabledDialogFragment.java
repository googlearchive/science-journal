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

import android.Manifest;
import android.app.DialogFragment;
import android.bluetooth.BluetoothAdapter;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;

import com.google.android.apps.forscience.whistlepunk.PermissionUtils;
import com.google.android.apps.forscience.whistlepunk.R;

/**
 * Shown to the user when Bluetooth scanning can't be done.
 * Shows a button to the user which leads to the system dialog for turning on Bluetooth.
 */
public class ScanDisabledDialogFragment extends DialogFragment {

    private static final int REQUEST_FROM_BUTTON = 1;

    static ScanDisabledDialogFragment newInstance() {
        return new ScanDisabledDialogFragment();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_scan_disabled_dialog, container, false);
        Button button = (Button) view.findViewById(R.id.btn_enable_scan);

        final boolean hasPermission = hasScanPermission(getActivity());
        if (!hasPermission) {
            // Update the text of the button.
            button.setText(R.string.btn_request_location);
        }

        button.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                if (!hasPermission) {
                    // TODO: is this doing the right thing if user says "never ask again"?
                    PermissionUtils.tryRequestingPermission(getActivity(),
                            Manifest.permission.ACCESS_COARSE_LOCATION, REQUEST_FROM_BUTTON, true);
                } else {
                    Intent intent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                    getActivity().startActivity(intent);
                }
                dismiss();
            }
        });
        return view;
    }

    /*package */ static boolean hasScanPermission(Context context) {
        return ContextCompat.checkSelfPermission(context,
                Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }
}
