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
import android.content.res.ColorStateList;
import android.content.res.Resources;
import android.content.res.Resources.Theme;
import android.net.Uri;
import android.util.TypedValue;
import android.view.ContextThemeWrapper;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View.OnClickListener;
import android.widget.ProgressBar;
import androidx.core.content.res.ResourcesCompat;
import androidx.fragment.app.FragmentManager;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.cardview.widget.CardView;
import com.google.android.apps.forscience.whistlepunk.accounts.AppAccount;
import com.google.android.apps.forscience.whistlepunk.actionarea.ActionAreaItem;
import com.google.android.apps.forscience.whistlepunk.actionarea.ActionAreaView;
import com.google.android.apps.forscience.whistlepunk.actionarea.ActionAreaView.ActionAreaListener;
import com.google.android.apps.forscience.whistlepunk.actionarea.SensorFragment;
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.snackbar.Snackbar;
import com.jakewharton.rxbinding2.view.RxView;
import io.reactivex.Observable;
import java.util.ArrayList;
import java.util.List;

/**
 * Controls starting and stopping recordings and updating UI elements based on recording state
 */
public class ActionController {
  private final AppAccount appAccount;
  private final String experimentId;
  private SnackbarManager snackbarManager;
  private Observable<RecordingStatus> recordingStatus;

  public ActionController(
      AppAccount appAccount, String experimentId,
      SnackbarManager snackbarManager, Context context) {
    this.appAccount = appAccount;
    this.experimentId = experimentId;
    this.snackbarManager = snackbarManager;

    recordingStatus =
        AppSingleton.getInstance(context)
            .getRecorderController(appAccount)
            .watchRecordingStatus();
  }

  /**
   * Updates the recording button for {@link ARVelocityActivity} when a recording starts/stops.
   */
  public void attachRecordButton(ImageButton recordButton, FragmentManager fragmentManager) {
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
                    resources.getDrawable(R.drawable.ic_stop_icon));
              } else {
                recordButton.setContentDescription(
                    resources.getString(R.string.btn_record_description));
                recordButton.setImageDrawable(
                    resources.getDrawable(R.drawable.ic_record_icon));
              }
            });
  }

  /**
   * Updates the stop recording button for {@link ExperimentDetailsFragment} when a recording
   * starts/stops.
   */
  public void attachStopButton(
      ExtendedFloatingActionButton recordButton, FragmentManager fragmentManager) {
    recordButton.setAllCaps(false);
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
   * Updates the add note button for {@link TextNoteFragment} and {@link GalleryNoteFragment} when
   * a recording starts/stops.
   */
  public void attachAddButton(FloatingActionButton button) {
    recordingStatus
        .takeUntil(RxView.detaches(button))
        .subscribe(
            status -> {
              Theme theme = button.getContext().getTheme();
              if (status.state.shouldShowStopButton()) {
                theme = new ContextThemeWrapper(
                    button.getContext(), R.style.RecordingProgressBarColor).getTheme();
              }
              button.setImageDrawable(
                  ResourcesCompat.getDrawable(
                      button.getResources(), R.drawable.ic_send_24dp, theme));

              TypedValue iconColor = new TypedValue();
              theme.resolveAttribute(R.attr.icon_color, iconColor, true);

              TypedValue iconBackground = new TypedValue();
              theme.resolveAttribute(R.attr.icon_background, iconBackground, true);

              button.setBackgroundTintList(ColorStateList.valueOf(
                  button.getResources().getColor(iconBackground.resourceId)));
              button.setRippleColor(button.getResources().getColor(iconColor.resourceId));
            });
  }

  /**
   * Updates the recording progress bar for {@link TextNoteFragment} and {@link GalleryNoteFragment}
   * when a recording starts/stops.
   */
  public void attachProgressBar(ProgressBar bar) {
    recordingStatus
        .takeUntil(RxView.detaches(bar))
        .subscribe(
            status -> {
              if (status.state.shouldShowStopButton()) {
                bar.setVisibility(View.VISIBLE);
              } else {
                bar.setVisibility(View.GONE);
              }
            });
  }

  /**
   * Updates the action area and sensor card view for {@link SensorFragment} when a recording
   * starts/stops.
   */
  public void attachActionAreaAndSensorCardViews(ActionAreaView actionAreaView,
      ActionAreaListener listener,
      View sensorCardRecyclerView) {
    recordingStatus
        .takeUntil(RxView.detaches(actionAreaView))
        .subscribe(
            status -> {
              int paddingBottom =
                  sensorCardRecyclerView.getResources()
                      .getDimensionPixelOffset(R.dimen.list_bottom_padding_with_action_area);
              if (status.state.shouldShowStopButton()) {
                ActionAreaItem[] actionAreaItems = {
                    ActionAreaItem.NOTE, ActionAreaItem.SNAPSHOT, ActionAreaItem.CAMERA, ActionAreaItem.GALLERY
                };
                actionAreaView.addItems(actionAreaView.getContext(), actionAreaItems, listener);
                actionAreaView.updateColor(
                    actionAreaView.getContext(), R.style.RecordingProgressBarColor);

                int paddingTop = sensorCardRecyclerView.getResources()
                    .getDimensionPixelSize(R.dimen.external_axis_height);
                sensorCardRecyclerView.setPadding(0, paddingTop, 0, paddingBottom);
              } else {
                ActionAreaItem[] actionAreaItems = {
                    ActionAreaItem.ADD_SENSOR, ActionAreaItem.SNAPSHOT
                };
                actionAreaView.addItems(actionAreaView.getContext(), actionAreaItems, listener);
                actionAreaView.updateColor(
                    actionAreaView.getContext(), -1);

                sensorCardRecyclerView.setPadding(0, 0, 0, paddingBottom);
              }
            });
  }

  /**
   * Updates the action area {@link ExperimentDetailsFragment} when a recording
   * starts/stops.
   */
  public void attachActionArea(ActionAreaView actionAreaView) {
    recordingStatus
        .takeUntil(RxView.detaches(actionAreaView))
        .subscribe(
            status -> {
              if (status.state.shouldShowStopButton()) {
                actionAreaView.updateColor(
                    actionAreaView.getContext(), R.style.RecordingProgressBarColor);
              } else {
                actionAreaView.updateColor(
                    actionAreaView.getContext(), -1);
              }
            });
  }

  /**
   * Updates the recording button for {@link SensorFragment} when a recording starts/stops.
   */
  public void attachSensorFragmentView(
      ExtendedFloatingActionButton recordButton,
      FragmentManager fragmentManager) {
    recordButton.setAllCaps(false);
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

              if (status.state.shouldShowStopButton()) {
                titleId = R.string.btn_stop_label;
                contentDescriptionId = R.string.btn_stop_description;
                imageResourceId = R.drawable.ic_stop_icon;
              } else {
                titleId = R.string.btn_record_label;
                contentDescriptionId = R.string.btn_record_description;
                imageResourceId = R.drawable.ic_record_icon;
              }
              recordButton.setText(titleId);
              recordButton.setIcon(resources.getDrawable(imageResourceId));
              recordButton.setContentDescription(resources.getString(contentDescriptionId));
              recordButton.invalidate();
            });
  }

  public void attachElapsedTime(MenuItem timingChip, SensorFragment fragment) {
    TextView elapsedTime = timingChip.getActionView().findViewById(R.id.timing_chip_text);
    recordingStatus
        .takeUntil(RxView.detaches(elapsedTime))
        .subscribe(
            status -> {
              if (status.isRecording()) {
                timingChip.setVisible(true);
              } else {
                timingChip.setVisible(false);
              }
            });
    ElapsedTimeAxisFormatter formatter =
        ElapsedTimeAxisFormatter.getInstance(elapsedTime.getContext());
    // This clears any old listener as well as setting a new one, so we don't need to worry
    // about having multiple listeners active.
    fragment.setRecordingTimeUpdateListener(
        recordingTime -> elapsedTime.setText(formatter.format(recordingTime, true)));
  }

  public void attachMenu(Menu menu, View view) {
    List<MenuItem> items = new ArrayList<>();
    for (int i = 0; i < menu.size()-1; i++) {
      items.add(menu.getItem(i));
    }
    if (menu.size() > 0) {
      recordingStatus
          .takeUntil(RxView.detaches(view))
          .subscribe(
              status -> {
                if (status.isRecording()) {
                  for (MenuItem item : items) {
                    item.setVisible(false);
                  }
                } else {
                  for (MenuItem item : items) {
                    item.setVisible(true);
                  }
                }
              });
    }
  }


  /**
   * Updates the title bar for {@link ActionFragment} when a recording starts/stops.
   */
  public void attachTitleBar(View titleBarView, boolean isTwoPane, OnClickListener listener,
      boolean hideDuringRecording, int titleResource, int iconResource) {
    titleBarView
        .findViewById(R.id.title_bar_close)
        .setOnClickListener(listener);
    ((TextView) titleBarView.findViewById(R.id.title_bar_text))
        .setText(titleResource);
    ImageView icon = titleBarView.findViewById(R.id.title_bar_icon);
      if (isTwoPane) {
        recordingStatus
            .takeUntil(RxView.detaches(titleBarView))
            .subscribe(
                status -> {
                  Theme theme = titleBarView.getContext().getTheme();
                  if (status.state.shouldShowStopButton()) {
                    theme = new ContextThemeWrapper(
                        titleBarView.getContext(), R.style.RecordingProgressBarColor).getTheme();
                    if (hideDuringRecording) {
                      titleBarView.setVisibility(View.GONE);
                    }
                  } else {
                    titleBarView.setVisibility(View.VISIBLE);
                  }
                  icon.setImageDrawable(
                      ResourcesCompat.getDrawable(
                          titleBarView.getResources(), iconResource, theme));
                });
      } else {
        titleBarView.setVisibility(View.GONE);
      }
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
