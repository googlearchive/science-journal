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

import android.app.FragmentManager;
import android.content.Intent;
import android.content.res.Resources;
import android.net.Uri;
import android.support.design.widget.Snackbar;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import com.jakewharton.rxbinding2.view.RxView;

import io.reactivex.Observable;

/**
 * The control bar is at the bottom of the observe fragment and holds buttons for taking
 * snapshots, starting recording, etc
 */
class ControlBarController {
    private final String mExperimentId;
    private SnackbarManager mSnackbarManager;

    public ControlBarController(String experimentId,
            SnackbarManager snackbarManager) {
        mExperimentId = experimentId;
        mSnackbarManager = snackbarManager;
    }

    public void attachSnapshotButton(View snapshotButton) {
        AppSingleton singleton = AppSingleton.getInstance(snapshotButton.getContext());
        snapshotButton.setOnClickListener(v -> {
            Snapshotter snapshotter = new Snapshotter(singleton.getRecorderController(),
                    singleton.getDataController(), singleton.getSensorRegistry());
            singleton.getRecorderController().watchRecordingStatus()
                     .firstElement()
                     .flatMapSingle(status -> snapshotter.addSnapshotLabel(mExperimentId, status))
                     .subscribe(label -> singleton.onLabelsAdded().onNext(label));
        });
    }

    private void attachAddButton(Observable<RecordingStatus> recordingState,
            ImageButton addButton) {
        recordingState.takeUntil(RxView.detaches(addButton)).subscribe(status -> {
            addButton.setEnabled(status.state.shouldEnableRecordButton());

            Resources resources = addButton.getResources();
            if (status.state.shouldShowStopButton()) {
                addButton.setContentDescription(
                        resources.getString(R.string.btn_add_run_note_description));
            } else {
                addButton.setContentDescription(
                        resources.getString(R.string.btn_add_experiment_note_description));
            }
        });
    }

    public void attachRecordButton(ImageButton recordButton, FragmentManager fragmentManager) {
        Observable<RecordingStatus> recordingStatus =
                AppSingleton.getInstance(recordButton.getContext())
                            .getRecorderController()
                            .watchRecordingStatus();

        recordButton.setVisibility(View.VISIBLE);
        RxView.clicks(recordButton)
              .flatMapMaybe(click -> recordingStatus.firstElement())
              .subscribe(status -> {
                  if (status.isRecording()) {
                      tryStopRecording(recordButton, fragmentManager);
                  } else {
                      tryStartRecording(recordButton);
                  }
              });

        recordingStatus.takeUntil(RxView.detaches(recordButton)).subscribe(status -> {
            recordButton.setEnabled(status.state.shouldEnableRecordButton());
            Resources resources = recordButton.getResources();

            if (status.state.shouldShowStopButton()) {
                recordButton.setContentDescription(resources.getString(
                        R.string.btn_stop_description));
                recordButton.setImageDrawable(resources.getDrawable(
                        R.drawable.ic_recording_stop_42dp));
            } else {
                recordButton.setContentDescription(resources.getString(
                        R.string.btn_record_description));
                recordButton.setImageDrawable(resources.getDrawable(
                        R.drawable.ic_recording_red_42dp));
            }
        });
    }

    private void tryStopRecording(View anchorView, FragmentManager fragmentManager) {
        AppSingleton singleton = AppSingleton.getInstance(anchorView.getContext());
        RecorderController rc = singleton.getRecorderController();
        SensorRegistry sensorRegistry = singleton.getSensorRegistry();

        rc.stopRecording(sensorRegistry).subscribe(() -> {
        }, error -> {
            if (error instanceof RecorderController.RecordingStopFailedException) {
                RecorderController.RecordingStopFailedException e =
                        (RecorderController.RecordingStopFailedException) error;
                onRecordingStopFailed(anchorView, e.errorType, fragmentManager);
            }
        });
    }

    private void onRecordingStopFailed(View anchorView,
            @RecorderController.RecordingStopErrorType int errorType,
            FragmentManager fragmentManager) {
        Resources resources = anchorView.getResources();
        if (errorType == RecorderController.ERROR_STOP_FAILED_DISCONNECTED) {
            alertFailedStopRecording(resources, R.string.recording_stop_failed_disconnected,
                    fragmentManager);
        } else if (errorType == RecorderController.ERROR_STOP_FAILED_NO_DATA) {
            alertFailedStopRecording(resources, R.string.recording_stop_failed_no_data,
                    fragmentManager);
        } else if (errorType == RecorderController.ERROR_FAILED_SAVE_RECORDING) {
            AccessibilityUtils.makeSnackbar(anchorView,
                    resources.getString(R.string.recording_stop_failed_save),
                    Snackbar.LENGTH_LONG).show();
        }
    }

    private void alertFailedStopRecording(Resources resources, int stringId,
            FragmentManager fragmentManager) {
        StopRecordingNoDataDialog dialog = StopRecordingNoDataDialog.newInstance(
                resources.getString(stringId));
        dialog.show(fragmentManager, StopRecordingNoDataDialog.TAG);
    }

    private void tryStartRecording(View anchorView) {
        if (mExperimentId == null) {
            return;
        }

        Intent launchIntent =
                PanesActivity.launchIntent(anchorView.getContext(), mExperimentId);

        // This isn't currently used, but does ensure this intent doesn't match any other intent.
        // See b/31616891
        launchIntent.setData(
                Uri.fromParts("observe", "experiment=" + mExperimentId,
                        null));
        RecorderController rc =
                AppSingleton.getInstance(anchorView.getContext()).getRecorderController();

        rc.startRecording(launchIntent, /* user initiated */ true).subscribe(() -> {
        }, error -> {
            if (error instanceof RecorderController.RecordingStartFailedException) {
                RecorderController.RecordingStartFailedException e =
                        (RecorderController.RecordingStartFailedException) error;
                onRecordingStartFailed(anchorView, e.errorType);
            }
        });
    }

    private void onRecordingStartFailed(View anchorView,
            @RecorderController.RecordingStartErrorType int errorType) {
        if (errorType == RecorderController.ERROR_START_FAILED) {
            alertFailedStopRecording(anchorView, R.string.recording_start_failed);
        } else if (errorType == RecorderController.ERROR_START_FAILED_DISCONNECTED) {
            alertFailedStopRecording(anchorView, R.string.recording_start_failed_disconnected);
        }
    }

    private void alertFailedStopRecording(View anchorView, int stringId) {
        Snackbar bar = AccessibilityUtils.makeSnackbar(anchorView,
                anchorView.getResources().getString(stringId), Snackbar.LENGTH_LONG);
        mSnackbarManager.showSnackbar(bar);
    }

    void attachRecordButtons(ViewGroup rootView, FragmentManager fragmentManager) {
        ImageButton recordButton = (ImageButton) rootView.findViewById(R.id.btn_record);
        attachRecordButton(recordButton, fragmentManager);

        View snapshotButton = rootView.findViewById(R.id.snapshot_button);
        attachSnapshotButton(snapshotButton);
    }

    void attachElapsedTime(ViewGroup rootView, RecordFragment fragment) {
        TextView elapsedTime = (TextView) rootView.findViewById(R.id.recorded_time);
        Observable<RecordingStatus> recordingStatus =
                AppSingleton.getInstance(elapsedTime.getContext())
                        .getRecorderController()
                        .watchRecordingStatus();
        recordingStatus.takeUntil(RxView.detaches(elapsedTime)).subscribe(status -> {
            if (status.isRecording()) {
                elapsedTime.setVisibility(View.VISIBLE);
            } else {
                elapsedTime.setVisibility(View.GONE);
            }
        });
        ElapsedTimeAxisFormatter formatter = ElapsedTimeAxisFormatter.getInstance(
                elapsedTime.getContext());
        // This clears any old listener as well as setting a new one, so we don't need to worry
        // about having multiple listeners active.
        fragment.setRecordingTimeUpdateListener(
                recordingTime -> elapsedTime.setText(formatter.format(recordingTime, true)));
    }
}
