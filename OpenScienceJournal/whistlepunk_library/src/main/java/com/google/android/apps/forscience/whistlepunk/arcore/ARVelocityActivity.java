/*
 *  Copyright 2019 Google Inc. All Rights Reserved.
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

package com.google.android.apps.forscience.whistlepunk.arcore;

import static com.google.android.apps.forscience.whistlepunk.ExperimentActivity.EXTRA_ACCOUNT_KEY;
import static com.google.android.apps.forscience.whistlepunk.ExperimentActivity.EXTRA_EXPERIMENT_ID;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.TooltipCompat;
import android.util.Log;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import com.google.android.apps.forscience.whistlepunk.AppSingleton;
import com.google.android.apps.forscience.whistlepunk.R;
import com.google.android.apps.forscience.whistlepunk.RecorderController;
import com.google.android.apps.forscience.whistlepunk.Snapshotter;
import com.google.android.apps.forscience.whistlepunk.WhistlePunkApplication;
import com.google.android.apps.forscience.whistlepunk.accounts.AppAccount;
import com.google.android.apps.forscience.whistlepunk.filemetadata.Label;
import com.google.android.apps.forscience.whistlepunk.sensors.VelocitySensor;
import com.google.ar.core.AugmentedImage;
import com.google.ar.core.AugmentedImage.TrackingMethod;
import com.google.ar.core.Frame;
import com.google.ar.core.Pose;
import com.google.ar.core.TrackingState;
import com.google.ar.sceneform.FrameTime;
import com.google.ar.sceneform.math.Vector3;
import com.google.ar.sceneform.ux.ArFragment;
import com.google.common.base.Throwables;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Activity that allows user to measure velocity of a ARCore tracked object. */
public class ARVelocityActivity extends AppCompatActivity {
  private static final String TAG = "ARVelocity";
  private ArFragment arFragment;
  private ImageView fitToScanView;
  private TextView velocityText;
  private static final float INTERVAL_TIME_SECONDS = 1f;
  private float delTime;
  private Vector3 lastPos;
  private Vector3 currPos;
  private AppAccount appAccount;
  private AppSingleton singleton;
  private ImageButton snapshotButton;
  private RecorderController rc;
  private String observerId;
  private VelocitySensor velocitySensor;

  // Augmented images that are currently being tracked.
  private final Set<AugmentedImage> augmentedImageSet = new HashSet<>();

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    setContentView(R.layout.activity_ar);

    arFragment = (ArFragment) getSupportFragmentManager().findFragmentById(R.id.ux_fragment);
    fitToScanView = findViewById(R.id.image_view_fit_to_scan);
    velocityText = findViewById(R.id.velocity_tracker_velocity_text);
    arFragment.getArSceneView().getScene().addOnUpdateListener(this::onUpdateFrame);
    snapshotButton = findViewById(R.id.snapshot_button);
    appAccount = WhistlePunkApplication.getAccount(this, getIntent(), EXTRA_ACCOUNT_KEY);
    singleton = AppSingleton.getInstance(this);
    velocitySensor = singleton.getVelocitySensor();

    setUpRecording();

    attachSnapshotButton(snapshotButton);
  }

  @Override
  protected void onResume() {
    super.onResume();

    if (augmentedImageSet.isEmpty()) {
      fitToScanView.setVisibility(View.VISIBLE);
      velocityText.setVisibility(View.INVISIBLE);
    }
  }

  @Override
  protected void onStop() {
    if (rc != null) {
      rc.stopObserving(VelocitySensor.ID, observerId);
    }
    super.onStop();
  }

  public static Intent getIntent(Context context, AppAccount appAccount, String experimentId) {
    Intent intent = new Intent(context, ARVelocityActivity.class);
    intent.putExtra(EXTRA_ACCOUNT_KEY, appAccount.getAccountKey());
    intent.putExtra(EXTRA_EXPERIMENT_ID, experimentId);
    return intent;
  }

  private void onUpdateFrame(FrameTime frameTime) {
    Frame frame = arFragment.getArSceneView().getArFrame();

    // If there is no frame or ARCore is not tracking yet, just return.
    if (frame == null || frame.getCamera().getTrackingState() != TrackingState.TRACKING) {
      return;
    }

    Collection<AugmentedImage> updatedAugmentedImages =
        frame.getUpdatedTrackables(AugmentedImage.class);
    for (AugmentedImage augmentedImage : updatedAugmentedImages) {
      switch (augmentedImage.getTrackingState()) {
        case PAUSED:
          // When an image is in the PAUSED state, it has been detected, but not yet tracked.
          velocityText.setVisibility(View.VISIBLE);
          velocityText.setText(getResources().getString(R.string.ar_detecting_image));
          break;

        case TRACKING:
          if (augmentedImage.getTrackingMethod() == TrackingMethod.FULL_TRACKING) {
            fitToScanView.setVisibility(View.GONE);

            // Create a new anchor for newly found images.
            if (!augmentedImageSet.contains(augmentedImage)) {
              arFragment.getArSceneView().getSession().createAnchor(augmentedImage.getCenterPose());
              augmentedImageSet.add(augmentedImage);
            }

            calculateVelocity(augmentedImage.getCenterPose(), frameTime.getDeltaSeconds());
          } else {
            lastPos = null;
            velocityText.setText(getResources().getString(R.string.ar_not_tracking));
            snapshotButton.setVisibility(View.GONE);
          }
          break;

        case STOPPED:
          velocityText.setVisibility(View.INVISIBLE);
          augmentedImageSet.remove(augmentedImage);
          break;
      }
    }
  }

  private void calculateVelocity(Pose centerPose, float deltaSeconds){

    Pose anchorPose = arFragment.getArSceneView().getSession().getAllAnchors().iterator().next()
        .getPose();

    currPos = Vector3.subtract(new Vector3(centerPose.tx(), centerPose.ty(), centerPose.tz()),
        new Vector3(anchorPose.tx(), anchorPose.ty(), anchorPose.tz()));

    delTime += deltaSeconds;

    if (lastPos == null) {
      lastPos = currPos;
    } else if (delTime >= INTERVAL_TIME_SECONDS) {

      // Calculate velocity in meters per second.
      Vector3 displacement = Vector3.subtract(currPos, lastPos);
      Vector3 avgVelocity = displacement.scaled(1 / delTime);
      float velocityValue = avgVelocity.length();

      // TODO(b/135678092): Add a string resource for the following
      velocityText.setText(String.format(Locale.getDefault(), "%.2f m/s", velocityValue));
      snapshotButton.setVisibility(View.VISIBLE);

      velocitySensor.setNextVelocity(velocityValue);

      delTime = 0;
      lastPos = currPos;
    }
  }

  private void setUpRecording(){

    rc = singleton.getRecorderController(appAccount);
    observerId = rc.startObserving(
        VelocitySensor.ID,
        new ArrayList<>(),
        (timestamp, value) -> {},
        null,
        null,
        singleton.getSensorRegistry());
  }

  public void attachSnapshotButton(View snapshotButton) {
    String experimentId = getIntent().getStringExtra(EXTRA_EXPERIMENT_ID);

    // Pass in the velocity ID to take a snapshot of just the velocity sensor.
    List<String> ids = new ArrayList<>();
    ids.add(VelocitySensor.ID);

    snapshotButton.setOnClickListener(
        v -> {
          Snapshotter snapshotter =
              new Snapshotter(
                  singleton.getRecorderController(appAccount),
                  singleton.getDataController(appAccount),
                  singleton.getSensorRegistry());
          singleton
              .getRecorderController(appAccount)
              .watchRecordingStatus()
              .firstElement()
              .flatMapSingle(status -> snapshotter.addSnapshotLabel(experimentId, status, ids))
              .subscribe(
                  (Label label) -> singleton.onLabelsAdded().onNext(label),
                  (Throwable e) -> {
                    if (Log.isLoggable(TAG, Log.DEBUG)) {
                      Log.d(TAG, Throwables.getStackTraceAsString(e));
                    }
                  });
        });
    TooltipCompat.setTooltipText(
        snapshotButton, getResources().getString(R.string.snapshot_button_description));
  }

}
