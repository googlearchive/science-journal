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


public class CameraFragment extends PanesToolFragment {
    private static final String KEY_PERMISSION_GRANTED = "key_permission_granted";

    private final BehaviorSubject<Optional<ViewGroup>> mPreviewContainer = BehaviorSubject.create();
    private BehaviorSubject<Boolean> mPermissionGranted = BehaviorSubject.create();
    private PublishSubject<Object> mWhenUserTakesPhoto = PublishSubject.create();

    private RxEvent mVisibilityGained = new RxEvent();
    private RxEvent mVisibilityLost = new RxEvent();
    private BehaviorSubject<Boolean> mFocused = BehaviorSubject.create();
    private BehaviorSubject<Boolean> mResumed = BehaviorSubject.create();

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

    public CameraFragment() {
        // Only treat as visible (and therefore connect the camera) when we are both focused and
        // resumed.
        Observable.combineLatest(mFocused, mResumed, (focused, resumed) -> focused && resumed)
                  .distinctUntilChanged()
                  .subscribe(hasBecomeVisible -> {
                      if (hasBecomeVisible) {
                          mVisibilityGained.onHappened();
                      } else {
                          mVisibilityLost.onHappened();
                      }
                  });

        mVisibilityGained.happens().subscribe(v -> {
            mPreviewContainer.filter(o -> o.isPresent()).firstElement().subscribe(opt -> {
                ViewGroup container = opt.get();
                LayoutInflater inflater = LayoutInflater.from(container.getContext());
                View view = inflater.inflate(R.layout.camera_tool_preview, container, false);
                CameraPreview preview = (CameraPreview) view;
                container.addView(preview);

                mPermissionGranted.firstElement()
                                  .flatMapObservable(granted -> granted ? Observable.just(
                                          openCamera()) : Observable.empty())
                                  .subscribe(preview::setCamera,
                                          LoggingConsumer.complain(TAG, "camera permission"));

                mWhenUserTakesPhoto.takeUntil(mVisibilityLost.happens()).subscribe(o -> {
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

                mVisibilityLost.happensNext().subscribe(() -> {
                    preview.removeCamera();
                    container.removeAllViews();
                });
            });
        });
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
    public View onCreatePanesView(LayoutInflater inflater, @Nullable ViewGroup container,
            Bundle savedInstanceState) {
        View inflated = inflater.inflate(R.layout.fragment_camera_tool, null);
        mPreviewContainer.onNext(
                Optional.of((ViewGroup) inflated.findViewById(R.id.preview_container)));
        return inflated;
    }

    @Override
    public void onDestroyPanesView() {
        mPreviewContainer.onNext(Optional.absent());
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
        mResumed.onNext(false);
        super.onPause();
    }

    @Override
    public void onResume() {
        super.onResume();
        mResumed.onNext(true);
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
        mFocused.onNext(true);
    }

    public Camera openCamera() {
        return Camera.open(0);
    }

    public void onLosingFocus() {
        mFocused.onNext(false);
    }
}
