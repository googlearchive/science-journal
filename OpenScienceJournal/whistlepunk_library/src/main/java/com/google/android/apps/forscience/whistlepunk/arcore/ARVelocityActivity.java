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

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import com.google.android.apps.forscience.whistlepunk.R;
import com.google.ar.core.AugmentedImage;
import com.google.ar.core.AugmentedImage.TrackingMethod;
import com.google.ar.core.Frame;
import com.google.ar.core.Pose;
import com.google.ar.core.TrackingState;
import com.google.ar.sceneform.FrameTime;
import com.google.ar.sceneform.math.Vector3;
import com.google.ar.sceneform.ux.ArFragment;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/** Activity that allows user to measure velocity of a ARCore tracked object. */
public class ARVelocityActivity extends AppCompatActivity {
  private static final String TAG = "ARVelocity";
  private ArFragment arFragment;
  private ImageView fitToScanView;
  private float delTime;
  private Vector3 lastPos;
  private Vector3 currPos;

  // Augmented images that are currently being tracked.
  private final Set<AugmentedImage> augmentedImageMap = new HashSet<>();

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    setContentView(R.layout.activity_ar);

    arFragment = (ArFragment) getSupportFragmentManager().findFragmentById(R.id.ux_fragment);
    fitToScanView = findViewById(R.id.image_view_fit_to_scan);
    arFragment.getArSceneView().getScene().addOnUpdateListener(this::onUpdateFrame);
  }

  @Override
  protected void onResume() {
    super.onResume();

    if (augmentedImageMap.isEmpty()) {
      fitToScanView.setVisibility(View.VISIBLE);
    }
  }

  public static Intent getLaunchIntent(Context context) {
    return new Intent(context, ARVelocityActivity.class);
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
          if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "Detected Image " + augmentedImage.getIndex());
          }
          break;

        case TRACKING:
          if (augmentedImage.getTrackingMethod() == TrackingMethod.FULL_TRACKING) {
            fitToScanView.setVisibility(View.GONE);

            // Create a new anchor for newly found images.
            if (!augmentedImageMap.contains(augmentedImage)) {
              arFragment.getArSceneView().getSession().createAnchor(augmentedImage.getCenterPose());
              augmentedImageMap.add(augmentedImage);
            }

            calculateVelocity(augmentedImage.getCenterPose(), frameTime.getDeltaSeconds());
          } else {
            if (Log.isLoggable(TAG, Log.DEBUG)) {
              Log.d(TAG, "Not actively tracking");
            }
          }
          break;

        case STOPPED:
          augmentedImageMap.remove(augmentedImage);
          break;
      }
    }
  }

  private void calculateVelocity(Pose centerPose, float deltaSeconds){

    Pose anchorPose = arFragment.getArSceneView().getSession().getAllAnchors().iterator().next()
        .getPose();

    currPos = Vector3.subtract(new Vector3(centerPose.tx(), centerPose.ty(), centerPose.tz()),
        new Vector3(anchorPose.tx(), anchorPose.ty(), anchorPose.tz()));

    if (Log.isLoggable(TAG, Log.DEBUG)) {
      Log.d(TAG, String
          .format("P: %.2f Px: %.2f Py: %.2f Pz: %.2f ", currPos.length(), currPos.x, currPos.y,
              currPos.z));
    }

    delTime += deltaSeconds;

    if (lastPos != null && delTime > 1) {
      // Calculate velocity in meters per second.
      Vector3 displacement = Vector3.subtract(currPos, lastPos);
      Vector3 avgVelocity = displacement.scaled(delTime);
      if (Log.isLoggable(TAG, Log.DEBUG)) {
        Log.d(TAG, String.format("V: %.2f", avgVelocity.length()));
      }
      delTime = 0;
    }

    lastPos = currPos;
  }
}
