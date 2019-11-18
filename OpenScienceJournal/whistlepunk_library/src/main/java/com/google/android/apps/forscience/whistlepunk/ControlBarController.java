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

import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.net.Uri;
import androidx.fragment.app.FragmentManager;
import androidx.appcompat.widget.TooltipCompat;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.cardview.widget.CardView;
import com.google.android.apps.forscience.whistlepunk.accounts.AppAccount;
import com.google.android.apps.forscience.whistlepunk.actionarea.ActionAreaItem;
import com.google.android.apps.forscience.whistlepunk.actionarea.ActionAreaView;
import com.google.android.apps.forscience.whistlepunk.actionarea.ActionAreaView.ActionAreaListener;
import com.google.android.apps.forscience.whistlepunk.filemetadata.Label;
import com.google.android.material.snackbar.Snackbar;
import com.google.common.base.Throwables;
import com.jakewharton.rxbinding2.view.RxView;
import io.reactivex.Observable;

/**
 * The control bar is at the bottom of the observe fragment and holds buttons for taking snapshots,
 * starting recording, etc
 */
public class ControlBarController {
  private static final String TAG = "ControlBarController";
  private final AppAccount appAccount;
  private final String experimentId;
  private SnackbarManager snackbarManager;

  public ControlBarController(
      AppAccount appAccount, String experimentId, SnackbarManager snackbarManager) {
    this.appAccount = appAccount;
    this.experimentId = experimentId;
    this.snackbarManager = snackbarManager;
  }

  public void attachRecordButton(ImageButton recordButton, FragmentManager fragmentManager) {
    Observable<RecordingStatus> recordingStatus =
        AppSingleton.getInstance(recordButton.getContext())
            .getRecorderController(appAccount)
            .watchRecordingStatus();

    recordButton.setVisibility(View.VISIBLE);
    RxView.clicks(recordButton)
        .flatMapMaybe(click -> recordingStatus.firstElement())
        .subscribe(
            status -> {
              if (status.isRecording()) {
                tryStopRecording(recordButton, fragmentManager);
              } else {
                tryStartRecording(recordButton);
              }
            });

    recordingStatus
        .takeUntil(RxView.detaches(recordButton))
        .subscribe(
            status -> {
              recordButton.setEnabled(status.state.shouldEnableRecordButton());
              Resources resources = recordButton.getResources();

              if (status.state.shouldShowStopButton()) {
                recordButton.setContentDescription(
                    resources.getString(R.string.btn_stop_description));
                recordButton.setImageDrawable(
                    resources.getDrawable(R.drawable.ic_recording_stop_42dp));
              } else {
                recordButton.setContentDescription(
                    resources.getString(R.string.btn_record_description));
                recordButton.setImageDrawable(
                    resources.getDrawable(R.drawable.ic_recording_red_42dp));
              }
            });
  }

  public void attachStopButton(CardView recordButton, FragmentManager fragmentManager) {
    Observable<RecordingStatus> recordingStatus =
        AppSingleton.getInstance(recordButton.getContext())
            .getRecorderController(appAccount)
            .watchRecordingStatus();

    Resources resources = recordButton.getResources();
    ((TextView) recordButton.findViewById(R.id.record_button_text))
        .setText(R.string.btn_stop_label);
    recordButton.setContentDescription(resources.getString(R.string.btn_stop_description));
    ((ImageView) recordButton.findViewById(R.id.record_button_icon))
        .setImageDrawable(resources.getDrawable(R.drawable.ic_recording_stop_42dp));

    RxView.clicks(recordButton)
        .flatMapMaybe(click -> recordingStatus.firstElement())
        .subscribe(
            status -> {
              if (status.isRecording()) {
                tryStopRecording(recordButton, fragmentManager);
              }
            });

    recordingStatus
        .takeUntil(RxView.detaches(recordButton))
        .subscribe(
            status -> {
              if (status.state.shouldShowStopButton()) {
                recordButton.setVisibility(View.VISIBLE);
              } else {
                recordButton.setVisibility(View.INVISIBLE);
              }
            });
  }

  /**
   * Updates the recording button, action area, title bar, and sensor card view for {@link
   * SensorFragment} when a recording starts/stops.
   */
  protected void attachSensorFragmentView(
      CardView recordButton,
      FragmentManager fragmentManager,
      ActionAreaView actionAreaView,
      ActionAreaListener listener,
      View sensorCardRecyclerView,
      View titleBarView,
      boolean isTwoPane) {
    Observable<RecordingStatus> recordingStatus =
        AppSingleton.getInstance(recordButton.getContext())
            .getRecorderController(appAccount)
            .watchRecordingStatus();

    recordButton.setVisibility(View.VISIBLE);
    RxView.clicks(recordButton)
        .flatMapMaybe(click -> recordingStatus.firstElement())
        .subscribe(
            status -> {
              if (status.isRecording()) {
                tryStopRecording(recordButton, fragmentManager);
              } else {
                tryStartRecording(recordButton);
              }
            });

    recordingStatus
        .takeUntil(RxView.detaches(recordButton))
        .subscribe(
            status -> {
              recordButton.setEnabled(status.state.shouldEnableRecordButton());
              Resources resources = recordButton.getResources();
              int titleId = 0;
              int contentDescriptionId = 0;
              int imageResourceId = 0;

              int paddingBottom =
                  resources.getDimensionPixelOffset(R.dimen.list_bottom_padding_with_action_area);

              if (status.state.shouldShowStopButton()) {
                titleId = R.string.btn_stop_label;
                contentDescriptionId = R.string.btn_stop_description;
                imageResourceId = R.drawable.ic_recording_stop_42dp;

                ActionAreaItem[] actionAreaItems = {
                  ActionAreaItem.NOTE, ActionAreaItem.SNAPSHOT, ActionAreaItem.CAMERA
                };
                actionAreaView.addItems(actionAreaView.getContext(), actionAreaItems, listener);
                actionAreaView.updateColor(actionAreaView.getContext(), R.style.RedActionAreaIcon);

                int paddingTop = resources.getDimensionPixelSize(R.dimen.external_axis_height);
                sensorCardRecyclerView.setPadding(0, paddingTop, 0, paddingBottom);

                titleBarView.setVisibility(View.GONE);
              } else {
                titleId = R.string.btn_record_label;
                contentDescriptionId = R.string.btn_record_description;
                imageResourceId = R.drawable.ic_recording_red_42dp;

                ActionAreaItem[] actionAreaItems = {
                  ActionAreaItem.ADD_SENSOR, ActionAreaItem.SNAPSHOT
                };
                actionAreaView.addItems(actionAreaView.getContext(), actionAreaItems, listener);
                actionAreaView.updateColor(
                    actionAreaView.getContext(), R.style.DefaultActionAreaIcon);

                sensorCardRecyclerView.setPadding(0, 0, 0, paddingBottom);

                if (isTwoPane) {
                  titleBarView.setVisibility(View.VISIBLE);
                }
              }
              ((TextView) recordButton.findViewById(R.id.record_button_text)).setText(titleId);
              ((ImageView) recordButton.findViewById(R.id.record_button_icon))
                  .setImageResource(imageResourceId);
              recordButton.setContentDescription(resources.getString(contentDescriptionId));
              recordButton.invalidate();
            });
  }

  private void tryStopRecording(View anchorView, FragmentManager fragmentManager) {
    AppSingleton singleton = AppSingleton.getInstance(anchorView.getContext());
    RecorderController rc = singleton.getRecorderController(appAccount);
    SensorRegistry sensorRegistry = singleton.getSensorRegistry();

    rc.stopRecording(sensorRegistry)
        .subscribe(
            () -> {},
            error -> {
              if (error instanceof RecorderController.RecordingStopFailedException) {
                RecorderController.RecordingStopFailedException e =
                    (RecorderController.RecordingStopFailedException) error;
                onRecordingStopFailed(anchorView, e.errorType, fragmentManager);
              }
            });
  }

  private void onRecordingStopFailed(
      View anchorView,
      @RecorderController.RecordingStopErrorType int errorType,
      FragmentManager fragmentManager) {
    Resources resources = anchorView.getResources();
    if (errorType == RecorderController.ERROR_STOP_FAILED_DISCONNECTED) {
      alertFailedStopRecording(
          resources, R.string.recording_stop_failed_disconnected, fragmentManager);
    } else if (errorType == RecorderController.ERROR_STOP_FAILED_NO_DATA) {
      alertFailedStopRecording(resources, R.string.recording_stop_failed_no_data, fragmentManager);
    } else if (errorType == RecorderController.ERROR_FAILED_SAVE_RECORDING) {
      AccessibilityUtils.makeSnackbar(
              anchorView,
              resources.getString(R.string.recording_stop_failed_save),
              Snackbar.LENGTH_LONG)
          .show();
    }
  }

  private void alertFailedStopRecording(
      Resources resources, int stringId, FragmentManager fragmentManager) {
    StopRecordingNoDataDialog dialog =
        StopRecordingNoDataDialog.newInstance(resources.getString(stringId));
    dialog.show(fragmentManager, StopRecordingNoDataDialog.TAG);
  }

  private void tryStartRecording(View anchorView) {
    if (experimentId == null) {
      return;
    }

    Intent launchIntent =
        WhistlePunkApplication.getLaunchIntentForExperimentActivity(
            anchorView.getContext(), appAccount, experimentId, false /* claimExperimentsMode */);

    // This isn't currently used, but does ensure this intent doesn't match any other intent.
    // See b/31616891
    launchIntent.setData(Uri.fromParts("observe", "experiment=" + experimentId, null));
    RecorderController rc =
        AppSingleton.getInstance(anchorView.getContext()).getRecorderController(appAccount);

    rc.startRecording(launchIntent, /* user initiated */ true)
        .subscribe(
            () -> {},
            error -> {
              if (error instanceof RecorderController.RecordingStartFailedException) {
                RecorderController.RecordingStartFailedException e =
                    (RecorderController.RecordingStartFailedException) error;
                onRecordingStartFailed(anchorView, e.errorType);
              }
            });
  }

  private void onRecordingStartFailed(
      View anchorView, @RecorderController.RecordingStartErrorType int errorType) {
    if (errorType == RecorderController.ERROR_START_FAILED) {
      alertFailedStopRecording(anchorView, R.string.recording_start_failed);
    } else if (errorType == RecorderController.ERROR_START_FAILED_DISCONNECTED) {
      alertFailedStopRecording(anchorView, R.string.recording_start_failed_disconnected);
    }
  }

  private void alertFailedStopRecording(View anchorView, int stringId) {
    Snackbar bar =
        AccessibilityUtils.makeSnackbar(
            anchorView, anchorView.getResources().getString(stringId), Snackbar.LENGTH_LONG);
    snackbarManager.showSnackbar(bar);
  }
}
