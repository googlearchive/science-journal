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

import android.app.Activity;
import android.app.Fragment;
import android.content.Context;
import android.hardware.Camera;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.google.android.apps.forscience.whistlepunk.filemetadata.Label;
import com.google.android.apps.forscience.whistlepunk.metadata.GoosciLabel;
import com.google.android.apps.forscience.whistlepunk.metadata.GoosciPictureLabelValue;
import com.google.android.apps.forscience.whistlepunk.sensors.CameraPreview;

import java.util.UUID;

import io.reactivex.Observable;

import static android.content.ContentValues.TAG;

public class CameraFragment extends Fragment {
    public static abstract class CameraFragmentListener {
        static CameraFragmentListener NULL = new CameraFragmentListener() {
            @Override
            public Observable<String> getActiveExperimentId() {
                return Observable.empty();
            }

            @Override
            public String toString() {
                return "CameraFragmentListener.NULL";
            }
        };

        public void onPictureLabelTaken(Label label) {
        }

        public abstract Observable<String> getActiveExperimentId();
    }

    public interface ListenerProvider {
        CameraFragmentListener getCameraFragmentListener();
    }

    private CameraFragmentListener mListener = CameraFragmentListener.NULL;

    private CameraPreview mPreview = null;

    public static CameraFragment newInstance() {
        return new CameraFragment();
    }

    // TODO: extract this pattern of fragment listeners
    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        internalOnAttach(context);
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        internalOnAttach(activity);
    }

    @Override
    public void onDetach() {
        mListener = CameraFragmentListener.NULL;
        super.onDetach();
    }

    private void internalOnAttach(Context context) {
        if (context instanceof ListenerProvider) {
            mListener = ((ListenerProvider) context).getCameraFragmentListener();
        }
        Fragment parentFragment = getParentFragment();
        if (parentFragment instanceof ListenerProvider) {
            mListener = ((ListenerProvider) parentFragment).getCameraFragmentListener();
        }
    }

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container,
            Bundle savedInstanceState) {
        View inflated = inflater.inflate(R.layout.fragment_camera_tool, null);
        mPreview = (CameraPreview) inflated.findViewById(R.id.preview);
        mPreview.setOnClickListener(v -> {
            final long timestamp = getTimestamp(v.getContext());
            final String uuid = UUID.randomUUID().toString();
            mPreview.takePicture(mListener.getActiveExperimentId().firstElement(), uuid,
                    new LoggingConsumer<String>(TAG, "taking picture") {
                        @Override
                        public void success(String relativePicturePath) {
                            GoosciPictureLabelValue.PictureLabelValue labelValue =
                                    new GoosciPictureLabelValue.PictureLabelValue();
                            labelValue.filePath = relativePicturePath;
                            Label label = Label.fromUuidAndValue(timestamp, uuid,
                                    GoosciLabel.Label.PICTURE, labelValue);
                            mListener.onPictureLabelTaken(label);
                        }
                    });
        });
        return inflated;
    }

    private long getTimestamp(Context context) {
        return getClock(context).getNow();
    }

    private Clock getClock(Context context) {
        // TODO: Don't depend here on AppSingleton
        return AppSingleton.getInstance(context)
                           .getSensorEnvironment()
                           .getDefaultClock();
    }

    @Override
    public void onStart() {
        super.onStart();
        PermissionUtils.tryRequestingPermission(getActivity(),
                PermissionUtils.REQUEST_CAMERA, new PermissionUtils.PermissionListener() {
                    @Override
                    public void onPermissionGranted() {
                        Camera camera = Camera.open(0);
                        if(camera != null) {
                            mPreview.setCamera(camera);
                        }
                    }

                    @Override
                    public void onPermissionDenied() {

                    }

                    @Override
                    public void onPermissionPermanentlyDenied() {

                    }
                });
    }

    @Override
    public void onStop() {
        mPreview.removeCamera();
        super.onStop();
    }
}
