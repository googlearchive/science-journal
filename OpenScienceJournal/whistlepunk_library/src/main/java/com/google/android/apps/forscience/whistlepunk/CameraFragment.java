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

import android.Manifest;
import android.app.Activity;
import android.app.Fragment;
import android.content.Context;
import android.hardware.Camera;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageButton;

import com.google.android.apps.forscience.whistlepunk.filemetadata.Label;
import com.google.android.apps.forscience.whistlepunk.metadata.GoosciLabel;
import com.google.android.apps.forscience.whistlepunk.metadata.GoosciPictureLabelValue;
import com.google.android.apps.forscience.whistlepunk.sensors.CameraPreview;
import com.google.common.base.Optional;
import com.jakewharton.rxbinding2.view.RxView;
import com.tbruyelle.rxpermissions2.RxPermissions;

import java.util.UUID;

import io.reactivex.Observable;
import io.reactivex.Observer;
import io.reactivex.subjects.BehaviorSubject;
import io.reactivex.subjects.PublishSubject;

import static android.content.ContentValues.TAG;


public class CameraFragment extends Fragment {
    private static final String KEY_PERMISSION_GRANTED = "key_permission_granted";

    private final BehaviorSubject<Optional<ViewGroup>> mPreviewContainer = BehaviorSubject.create();
    private BehaviorSubject<Boolean> mPermissionGranted = BehaviorSubject.create();
    private RxEvent mFocusLost = new RxEvent();
    private PublishSubject<Object> mWhenUserTakesPhoto = PublishSubject.create();
    private boolean mFocused = false;

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

    public static CameraFragment newInstance(RxPermissions permissions) {
        CameraFragment fragment = new CameraFragment();
        permissions.request(Manifest.permission.CAMERA).subscribe(granted -> {
            fragment.onPermissionGranted().onNext(granted);
        });
        return fragment;
    }

    Observer<Boolean> onPermissionGranted() {
        return mPermissionGranted;
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
        mPreviewContainer.onNext(
                Optional.of((ViewGroup) inflated.findViewById(R.id.preview_container)));
        return inflated;
    }

    @Override
    public void onDestroyView() {
        mPreviewContainer.onNext(Optional.absent());
        super.onDestroyView();
    }

    public void attachButtons(FrameLayout controlBar) {
        ImageButton addButton = (ImageButton) controlBar.findViewById(R.id.btn_add);
        RxView.clicks(addButton).subscribe(click -> mWhenUserTakesPhoto.onNext(click));
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
    public void onPause() {
        onLosingFocus();
        super.onPause();
    }

    @Override
    public void onResume() {
        super.onResume();
        onGainedFocus();
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        if (mPermissionGranted.hasValue()) {
            outState.putBoolean(KEY_PERMISSION_GRANTED, mPermissionGranted.getValue());
        }
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (savedInstanceState != null && savedInstanceState.containsKey(KEY_PERMISSION_GRANTED)) {
            mPermissionGranted.onNext(savedInstanceState.getBoolean(KEY_PERMISSION_GRANTED));
        }
    }

    public void onGainedFocus() {
        if (mFocused) {
            return;
        }

        mFocused = true;
        mPreviewContainer.filter(o -> o.isPresent()).firstElement().subscribe(opt -> {
            ViewGroup container = opt.get();
            LayoutInflater inflater = LayoutInflater.from(container.getContext());
            View view = inflater.inflate(R.layout.camera_tool_preview, container, false);
            CameraPreview preview = (CameraPreview) view;
            container.addView(preview);

            mPermissionGranted.firstElement().subscribe(granted -> {
                if (granted) {
                    preview.setCamera(openCamera());
                }
            }, LoggingConsumer.complain(TAG, "camera permission"));

            preview.setOnClickListener(v -> mWhenUserTakesPhoto.onNext(v));

            mWhenUserTakesPhoto.takeUntil(mFocusLost.happens()).subscribe(o -> {
                final long timestamp = getTimestamp(preview.getContext());
                final String uuid = UUID.randomUUID().toString();
                preview.takePicture(mListener.getActiveExperimentId().firstElement(), uuid,
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

            mFocusLost.happens().firstElement().subscribe(o -> {
                preview.removeCamera();
                container.removeAllViews();
            });
        });
    }

    public Camera openCamera() {
        return Camera.open(0);
    }

    public void onLosingFocus() {
        if (!mFocused) {
            return;
        }
        mFocused = false;
        mFocusLost.onHappened();
    }
}
