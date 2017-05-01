/*
 *  Copyright 2017 Google Inc. All Rights Reserved.
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

import android.app.Fragment;
import android.hardware.Camera;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.google.android.apps.forscience.whistlepunk.sensors.CameraPreview;

public class CameraFragment extends Fragment {
    private CameraPreview mPreview = null;

    public static CameraFragment newInstance() {
        return new CameraFragment();
    }

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container,
            Bundle savedInstanceState) {
        View inflated = inflater.inflate(R.layout.fragment_camera_tool, null);
        mPreview = (CameraPreview) inflated.findViewById(R.id.preview);
        return inflated;
    }

    @Override
    public void onStart() {
        super.onStart();
        mPreview.setCamera(Camera.open());
    }

    @Override
    public void onStop() {
        mPreview.removeCamera();
        super.onStop();
    }
}
