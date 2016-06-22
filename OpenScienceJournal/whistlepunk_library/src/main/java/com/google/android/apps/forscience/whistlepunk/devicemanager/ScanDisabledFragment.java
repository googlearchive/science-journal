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
import android.app.Fragment;
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

import com.google.android.apps.forscience.whistlepunk.R;

/**
 * Shown to the user when Bluetooth scanning can't be done.
 * Shows a button to the user which leads to the system dialog for turning on Bluetooth.
 */
public class ScanDisabledFragment extends Fragment {

    private static final int REQUEST_FROM_BUTTON = 1;

    public ScanDisabledFragment() {}

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_scan_disabled, container, false);
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
                    // Note: this can fail if the user says "never ask again." but there is no
                    // API access to know if this is the case.
                    ActivityCompat.requestPermissions(getActivity(),
                            new String[]{Manifest.permission.ACCESS_COARSE_LOCATION},
                            REQUEST_FROM_BUTTON);
                } else {
                    Intent intent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                    getActivity().startActivity(intent);
                }
            }
        });
        return view;
    }

    /*package */ static boolean hasScanPermission(Context context) {
        return ContextCompat.checkSelfPermission(context,
                Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }
}
