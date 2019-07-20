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

import static android.content.ContentValues.TAG;

import android.Manifest;
import android.content.Context;
import android.hardware.Camera;
import android.os.Bundle;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import com.google.android.apps.forscience.whistlepunk.accounts.AppAccount;
import com.google.android.apps.forscience.whistlepunk.filemetadata.Label;
import com.google.android.apps.forscience.whistlepunk.metadata.GoosciLabel;
import com.google.android.apps.forscience.whistlepunk.metadata.GoosciPictureLabelValue;
import com.google.android.apps.forscience.whistlepunk.metadata.GoosciPictureLabelValue.PictureLabelValue;
import com.google.android.apps.forscience.whistlepunk.sensors.CameraPreview;
import com.google.common.base.Optional;
import com.jakewharton.rxbinding2.view.RxView;
import com.tbruyelle.rxpermissions2.RxPermissions;
import io.reactivex.Observable;
import io.reactivex.subjects.BehaviorSubject;
import io.reactivex.subjects.PublishSubject;
import java.util.UUID;

public class CameraFragment extends PanesToolFragment {
  private static final String KEY_ACCOUNT_KEY = "accountKey";

  private final BehaviorSubject<Optional<ViewGroup>> previewContainer = BehaviorSubject.create();
  private BehaviorSubject<Boolean> permissionGranted = BehaviorSubject.create();
  private PublishSubject<Object> whenUserTakesPhoto = PublishSubject.create();

  public abstract static class CameraFragmentListener {
    static CameraFragmentListener NULL =
        new CameraFragmentListener() {
          @Override
          public Observable<String> getActiveExperimentId() {
            return Observable.empty();
          }

          @Override
          public RxPermissions getPermissions() {
            return null;
          }

          @Override
          public String toString() {
            return "CameraFragmentListener.NULL";
          }
        };

    public void onPictureLabelTaken(Label label) {}

    public abstract Observable<String> getActiveExperimentId();

    public abstract RxPermissions getPermissions();
  }

  public interface ListenerProvider {
    CameraFragmentListener getCameraFragmentListener();
  }

  private CameraFragmentListener listener = CameraFragmentListener.NULL;

  public static CameraFragment newInstance(AppAccount appAccount) {
    CameraFragment fragment = new CameraFragment();
    Bundle args = new Bundle();
    args.putString(KEY_ACCOUNT_KEY, appAccount.getAccountKey());
    fragment.setArguments(args);
    return fragment;
  }

  public CameraFragment() {
    whenVisibilityGained()
        .subscribe(
            v -> {
              previewContainer
                  .filter(o -> o.isPresent())
                  .firstElement()
                  .subscribe(
                      opt -> {
                        ViewGroup container = opt.get();

                        permissionGranted
                            .distinctUntilChanged()
                            .takeUntil(RxView.detaches(container))
                            .map(
                                granted -> {
                                  if (granted) {
                                    return Optional.of(openCamera());
                                  } else {
                                    return Optional.<Camera>absent();
                                  }
                                })
                            .subscribe(
                                optCamera -> {
                                  if (optCamera.isPresent()) {
                                    setUpWorkingCamera(container, optCamera.get());
                                  } else {
                                    complainCameraPermissions(container);
                                  }
                                },
                                LoggingConsumer.complain(TAG, "camera permission"));
                      });
            });
  }

  private AppAccount getAppAccount() {
    return WhistlePunkApplication.getAccount(getContext(), getArguments(), KEY_ACCOUNT_KEY);
  }

  private void complainCameraPermissions(ViewGroup container) {
    LayoutInflater inflater = LayoutInflater.from(container.getContext());
    View view = inflater.inflate(R.layout.camera_complaint, container, false);

    container.removeAllViews();
    container.addView(view);
    RxView.clicks(view.findViewById(R.id.open_settings)).subscribe(click -> requestPermission());
  }

  public void setUpWorkingCamera(ViewGroup container, Camera camera) {
    LayoutInflater inflater = LayoutInflater.from(container.getContext());
    View view = inflater.inflate(R.layout.camera_tool_preview, container, false);
    CameraPreview preview = (CameraPreview) view;

    container.removeAllViews();
    container.addView(preview);
    preview.setCamera(camera);

    watchDrawerState()
        .takeUntil(RxView.detaches(preview))
        .subscribe(preview::setCurrentDrawerState);

    whenUserTakesPhoto
        .takeUntil(whenVisibilityLost())
        .doOnComplete(
            () -> {
              preview.removeCamera();
              container.removeAllViews();
            })
        .subscribe(
            o -> {
              final long timestamp = getTimestamp(preview.getContext());
              final String uuid = UUID.randomUUID().toString();
              preview.takePicture(
                  getAppAccount(),
                  listener.getActiveExperimentId().firstElement(),
                  uuid,
                  new LoggingConsumer<String>(TAG, "taking picture") {
                    @Override
                    public void success(String relativePicturePath) {
                      // relativePicturePath cannot be null
                      PictureLabelValue labelValue =
                          GoosciPictureLabelValue.PictureLabelValue.newBuilder()
                              .setFilePath(relativePicturePath)
                              .build();
                      Label label =
                          Label.fromUuidAndValue(
                              timestamp, uuid, GoosciLabel.Label.ValueType.PICTURE, labelValue);
                      listener.onPictureLabelTaken(label);
                    }
                  });
            });
  }

  @Override
  public void onDetach() {
    listener = CameraFragmentListener.NULL;
    super.onDetach();
  }

  protected void panesOnAttach(Context context) {
    if (context instanceof ListenerProvider) {
      listener = ((ListenerProvider) context).getCameraFragmentListener();
    }
    Fragment parentFragment = getParentFragment();
    if (parentFragment instanceof ListenerProvider) {
      listener = ((ListenerProvider) parentFragment).getCameraFragmentListener();
    }
  }

  private void requestPermission() {
    RxPermissions permissions = listener.getPermissions();
    if (permissions == null) {
      return;
    }
    permissions
        .request(Manifest.permission.CAMERA)
        .subscribe(
            granted -> {
              permissionGranted.onNext(granted);
            });
  }

  @Nullable
  @Override
  public View onCreatePanesView(
      LayoutInflater inflater, @Nullable ViewGroup container, Bundle savedInstanceState) {
    View inflated = inflater.inflate(R.layout.fragment_camera_tool, null);
    previewContainer.onNext(Optional.of((ViewGroup) inflated.findViewById(R.id.preview_container)));
    requestPermission();
    return inflated;
  }

  @Override
  public void onDestroyPanesView() {
    previewContainer.onNext(Optional.absent());
  }

  public void attachButtons(FrameLayout controlBar) {
    ImageButton addButton = (ImageButton) controlBar.findViewById(R.id.btn_add);
    RxView.clicks(addButton).subscribe(click -> whenUserTakesPhoto.onNext(click));
  }

  private long getTimestamp(Context context) {
    return getClock(context).getNow();
  }

  private Clock getClock(Context context) {
    // TODO: Don't depend here on AppSingleton
    return AppSingleton.getInstance(context).getSensorEnvironment().getDefaultClock();
  }

  public Camera openCamera() {
    return Camera.open(0);
  }
}
