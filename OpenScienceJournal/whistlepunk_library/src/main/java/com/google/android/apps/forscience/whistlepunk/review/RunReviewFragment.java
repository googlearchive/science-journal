/*
 *  Copyright 2016 Google Inc. All Rights Reserved.
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

package com.google.android.apps.forscience.whistlepunk.review;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.os.Bundle;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;
import androidx.core.app.NavUtils;
import androidx.core.util.Pair;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.view.ActionMode;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import com.google.android.apps.forscience.javalib.MaybeConsumer;
import com.google.android.apps.forscience.javalib.MaybeConsumers;
import com.google.android.apps.forscience.javalib.Success;
import com.google.android.apps.forscience.whistlepunk.AccessibilityUtils;
import com.google.android.apps.forscience.whistlepunk.AddNoteDialog;
import com.google.android.apps.forscience.whistlepunk.AppSingleton;
import com.google.android.apps.forscience.whistlepunk.Appearances;
import com.google.android.apps.forscience.whistlepunk.AudioSettingsDialog;
import com.google.android.apps.forscience.whistlepunk.CurrentTimeClock;
import com.google.android.apps.forscience.whistlepunk.DataController;
import com.google.android.apps.forscience.whistlepunk.DeletedLabel;
import com.google.android.apps.forscience.whistlepunk.DevOptionsFragment;
import com.google.android.apps.forscience.whistlepunk.ElapsedTimeFormatter;
import com.google.android.apps.forscience.whistlepunk.ExportService;
import com.google.android.apps.forscience.whistlepunk.ExternalAxisController;
import com.google.android.apps.forscience.whistlepunk.ExternalAxisView;
import com.google.android.apps.forscience.whistlepunk.Flags;
import com.google.android.apps.forscience.whistlepunk.LocalSensorOptionsStorage;
import com.google.android.apps.forscience.whistlepunk.LoggingConsumer;
import com.google.android.apps.forscience.whistlepunk.MultiWindowUtils;
import com.google.android.apps.forscience.whistlepunk.NoteTakingActivity;
import com.google.android.apps.forscience.whistlepunk.PanesActivity;
import com.google.android.apps.forscience.whistlepunk.PictureUtils;
import com.google.android.apps.forscience.whistlepunk.ProtoSensorAppearance;
import com.google.android.apps.forscience.whistlepunk.R;
import com.google.android.apps.forscience.whistlepunk.RecordFragment;
import com.google.android.apps.forscience.whistlepunk.RelativeTimeTextView;
import com.google.android.apps.forscience.whistlepunk.RunReviewOverlay;
import com.google.android.apps.forscience.whistlepunk.RxDataController;
import com.google.android.apps.forscience.whistlepunk.SensorAppearance;
import com.google.android.apps.forscience.whistlepunk.StatsAccumulator;
import com.google.android.apps.forscience.whistlepunk.StatsList;
import com.google.android.apps.forscience.whistlepunk.WhistlePunkApplication;
import com.google.android.apps.forscience.whistlepunk.accounts.AppAccount;
import com.google.android.apps.forscience.whistlepunk.actionarea.ActionAreaView;
import com.google.android.apps.forscience.whistlepunk.actionarea.TitleProvider;
import com.google.android.apps.forscience.whistlepunk.analytics.TrackerConstants;
import com.google.android.apps.forscience.whistlepunk.audiogen.AudioPlaybackController;
import com.google.android.apps.forscience.whistlepunk.audiogen.SonificationTypeAdapterFactory;
import com.google.android.apps.forscience.whistlepunk.data.GoosciSensorAppearance;
import com.google.android.apps.forscience.whistlepunk.filemetadata.Change;
import com.google.android.apps.forscience.whistlepunk.filemetadata.Experiment;
import com.google.android.apps.forscience.whistlepunk.filemetadata.Label;
import com.google.android.apps.forscience.whistlepunk.filemetadata.SensorLayoutPojo;
import com.google.android.apps.forscience.whistlepunk.filemetadata.Trial;
import com.google.android.apps.forscience.whistlepunk.filemetadata.TrialStats;
import com.google.android.apps.forscience.whistlepunk.metadata.CropHelper;
import com.google.android.apps.forscience.whistlepunk.metadata.GoosciCaption;
import com.google.android.apps.forscience.whistlepunk.metadata.GoosciCaption.Caption;
import com.google.android.apps.forscience.whistlepunk.metadata.GoosciExperiment.ChangedElement.ElementType;
import com.google.android.apps.forscience.whistlepunk.metadata.GoosciTrial;
import com.google.android.apps.forscience.whistlepunk.performance.PerfTrackerProvider;
import com.google.android.apps.forscience.whistlepunk.project.experiment.ExperimentDetailsFragment;
import com.google.android.apps.forscience.whistlepunk.review.EditLabelTimeDialog.EditTimeDialogListener;
import com.google.android.apps.forscience.whistlepunk.review.labels.LabelDetailsActivity;
import com.google.android.apps.forscience.whistlepunk.scalarchart.ChartController;
import com.google.android.apps.forscience.whistlepunk.scalarchart.ChartOptions;
import com.google.android.apps.forscience.whistlepunk.scalarchart.ChartView;
import com.google.android.apps.forscience.whistlepunk.scalarchart.GraphOptionsController;
import com.google.android.apps.forscience.whistlepunk.scalarchart.ScalarDisplayOptions;
import com.google.android.apps.forscience.whistlepunk.sensorapi.NewOptionsStorage;
import com.google.android.apps.forscience.whistlepunk.sensorapi.StreamStat;
import com.google.android.material.appbar.AppBarLayout;
import com.google.android.material.snackbar.Snackbar;
import io.reactivex.Single;
import io.reactivex.functions.Consumer;
import java.text.NumberFormat;
import java.util.Collections;
import java.util.List;

public class RunReviewFragment extends Fragment
    implements AddNoteDialog.ListenerProvider,
        EditTimeDialogListener,
        DeleteMetadataItemDialog.DeleteDialogListener,
        AudioSettingsDialog.AudioSettingsDialogListener,
        ChartController.ChartLoadingStatus,
        TitleProvider {
  public static final String ARG_ACCOUNT_KEY = "accountKey";
  public static final String ARG_EXPERIMENT_ID = "experimentId";
  public static final String ARG_START_LABEL_ID = "start_label_id";
  public static final String ARG_SENSOR_INDEX = "sensor_tag_index";
  public static final String ARG_CREATE_TASK = "create_task";
  public static final String ARG_CLAIM_EXPERIMENTS_MODE = "claim_experiments_mode";
  private static final String TAG = "RunReviewFragment";

  private static final String KEY_SELECTED_SENSOR_INDEX = "selected_sensor_index";
  private static final String KEY_TIMESTAMP_EDIT_UI_VISIBLE = "timestamp_edit_visible";
  private static final String KEY_EXTERNAL_AXIS_X_MINIMUM = "external_axis_min";
  private static final String KEY_EXTERNAL_AXIS_X_MAXIMUM = "external_axis_max";
  private static final String KEY_RUN_REVIEW_OVERLAY_TIMESTAMP = "run_review_overlay_time";
  private static final String KEY_STATS_OVERLAY_VISIBLE = "stats_overlay_visible";
  private static final String KEY_AUDIO_PLAYBACK_ON = "audio_playback_on";
  private static final String KEY_CROP_UI_VISIBLE = "crop_ui_visible";
  private static final String KEY_CROP_START_TIMESTAMP = "crop_ui_start_timestamp";
  private static final String KEY_CROP_END_TIMESTAMP = "crop_ui_end_timestamp";
  private static final String KEY_CHART_AXIS_Y_MAXIMUM = "chart_y_axis_min";
  private static final String KEY_CHART_AXIS_Y_MINIMUM = "chart_y_axis_max";
  private static final String KEY_TIMESTAMP_PICKER_UI_VISIBLE = "timestamp_picker_visible";

  private int loadingStatus = GRAPH_LOAD_STATUS_IDLE;

  public static final double MILLIS_IN_A_SECOND = 1000.0;

  private ImageButton runReviewPlaybackButton;
  private AudioPlaybackController audioPlaybackController;
  private boolean wasPlayingBeforeTouch = false;
  private boolean audioWasPlayingBeforePause = false;

  private AppAccount appAccount;
  private String trialId;
  private String experimentId;
  private int selectedSensorIndex = 0;
  private boolean claimExperimentsMode;
  private GraphOptionsController graphOptionsController;
  private ScalarDisplayOptions scalarDisplayOptions;
  private ChartController chartController;
  private ExternalAxisController externalAxis;
  private RunReviewOverlay runReviewOverlay;
  private PinnedNoteAdapter pinnedNoteAdapter;
  private RecyclerView pinnedNoteList;
  private Experiment experiment;
  private ActionMode actionMode;
  private TrialStats currentSensorStats;
  private boolean showStatsOverlay = false;
  private BroadcastReceiver broadcastReceiver;
  private Pair<Double, Double> previousYPair;
  private PerfTrackerProvider perfTracker;

  // Save the savedInstanceState between onCreateView and loading the run data, in case
  // an onPause happens during that time.
  private Bundle savedInstanceStateForLoad;

  /**
   * Use this factory method to create a new instance of this fragment using the provided
   * parameters.
   *
   * @param appAccount the account that owns the experiment
   * @param experimentId the experiment id
   * @param startLabelId the startLabelId that joins the labels identifying this run
   * @param sensorIndex the initial sensor to select in run review
   * @param createTask if {@code true}, will create tasks when navigating up
   * @param claimExperimentsMode if {@code true}, run cannot be modified
   * @return A new instance of fragment RunReviewFragment.
   */
  public static RunReviewFragment newInstance(
      AppAccount appAccount,
      String experimentId,
      String startLabelId,
      int sensorIndex,
      boolean createTask,
      boolean claimExperimentsMode) {
    RunReviewFragment fragment = new RunReviewFragment();
    Bundle args = new Bundle();
    args.putString(ARG_ACCOUNT_KEY, appAccount.getAccountKey());
    args.putString(ARG_EXPERIMENT_ID, experimentId);
    args.putString(ARG_START_LABEL_ID, startLabelId);
    args.putInt(ARG_SENSOR_INDEX, sensorIndex);
    args.putBoolean(ARG_CREATE_TASK, createTask);
    args.putBoolean(ARG_CLAIM_EXPERIMENTS_MODE, claimExperimentsMode);
    fragment.setArguments(args);
    return fragment;
  }

  public RunReviewFragment() {
    // Required empty public constructor
  }

  @Override
  public void onResume() {
    super.onResume();
    if (!isMultiWindowEnabled()) {
      initializeData();
    }
    DeletedLabel deletedLabel = AppSingleton.getInstance(getActivity()).popDeletedLabelForUndo();
    if (deletedLabel != null) {
      onLabelDelete(deletedLabel);
    }
  }

  @Override
  public void onPause() {
    if (!isMultiWindowEnabled()) {
      stopUi();
    }
    super.onPause();
  }

  @Override
  public void onDestroy() {
    if (audioPlaybackController != null) {
      clearAudioPlaybackController();
      audioPlaybackController = null;
    }
    graphOptionsController = null;
    if (externalAxis != null) {
      externalAxis.destroy();
    }
    if (pinnedNoteAdapter != null) {
      pinnedNoteAdapter.onDestroy();
    }
    if (runReviewOverlay != null) {
      runReviewOverlay.onDestroy();
    }
    if (chartController != null) {
      chartController.onDestroy();
    }
    super.onDestroy();
  }

  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    perfTracker = WhistlePunkApplication.getPerfTrackerProvider(getActivity());
    perfTracker.startGlobalTimer(TrackerConstants.PRIMES_RUN_LOADED);
    if (getArguments() != null) {
      appAccount = WhistlePunkApplication.getAccount(getContext(), getArguments(), ARG_ACCOUNT_KEY);
      trialId = getArguments().getString(ARG_START_LABEL_ID);
      selectedSensorIndex = getArguments().getInt(ARG_SENSOR_INDEX);
      experimentId = getArguments().getString(ARG_EXPERIMENT_ID);
      claimExperimentsMode = getArguments().getBoolean(ARG_CLAIM_EXPERIMENTS_MODE);
    }
    if (savedInstanceState != null) {
      if (savedInstanceState.containsKey(KEY_SELECTED_SENSOR_INDEX)) {
        // saved instance state is more recent than args, so it takes precedence.
        selectedSensorIndex = savedInstanceState.getInt(KEY_SELECTED_SENSOR_INDEX);
      }
      showStatsOverlay = savedInstanceState.getBoolean(KEY_STATS_OVERLAY_VISIBLE, false);
    }
    audioPlaybackController =
        new AudioPlaybackController(
            new AudioPlaybackController.AudioPlaybackListener() {
              @Override
              public void onAudioPlaybackStarted() {
                if (!isAdded()) {
                  return;
                }
                WhistlePunkApplication.getUsageTracker(getActivity())
                    .trackEvent(
                        TrackerConstants.CATEGORY_RUNS,
                        TrackerConstants.ACTION_START_AUDIO_PLAYBACK,
                        TrackerConstants.LABEL_RUN_REVIEW,
                        0);
                runReviewPlaybackButton.setImageDrawable(
                    getResources().getDrawable(R.drawable.ic_pause_black_24dp));
                runReviewPlaybackButton.setContentDescription(
                    getResources().getString(R.string.playback_button_pause));
              }

              @Override
              public void onTimestampUpdated(long activeTimestamp) {
                runReviewOverlay.setActiveTimestamp(activeTimestamp);
              }

              @Override
              public void onAudioPlaybackStopped() {
                audioPlaybackController.stopPlayback();
                if (!isAdded()) {
                  return;
                }
                runReviewPlaybackButton.setImageDrawable(
                    getResources().getDrawable(R.drawable.ic_play_arrow_black_24dp));
                runReviewPlaybackButton.setContentDescription(
                    getResources().getString(R.string.playback_button_play));
              }
            });
    setHasOptionsMenu(true);
    if (claimExperimentsMode) {
      WhistlePunkApplication.getUsageTracker(getActivity())
          .trackEvent(
              TrackerConstants.CATEGORY_CLAIMING_DATA, TrackerConstants.ACTION_VIEW_TRIAL, null, 0);
    }
  }

  @Override
  public void onStart() {
    super.onStart();
    if (isMultiWindowEnabled()) {
      initializeData();
    }
    WhistlePunkApplication.getUsageTracker(getActivity())
        .trackScreenView(TrackerConstants.SCREEN_RUN_REVIEW);
  }

  @Override
  public void onStop() {
    if (isMultiWindowEnabled()) {
      stopUi();
    }
    super.onStop();
  }

  private void stopUi() {
    pausePlaybackForLifecycleEvent();
    unregisterBroadcastReceiver();
  }

  private void unregisterBroadcastReceiver() {
    if (broadcastReceiver != null) {
      CropHelper.unregisterBroadcastReceiver(getActivity(), broadcastReceiver);
      broadcastReceiver = null;
    }
  }

  private boolean isMultiWindowEnabled() {
    return MultiWindowUtils.isMultiWindowEnabled(getActivity());
  }

  @Override
  public View onCreateView(
      LayoutInflater inflater, ViewGroup container, final Bundle savedInstanceState) {
    savedInstanceStateForLoad = savedInstanceState;
    final View rootView = inflater.inflate(R.layout.fragment_run_review, container, false);
    AppBarLayout appBarLayout = (AppBarLayout) rootView.findViewById(R.id.app_bar_layout);
    ExternalAxisView externalAxisView =
        (ExternalAxisView) rootView.findViewById(R.id.external_x_axis);
    externalAxis =
        new ExternalAxisController(
            externalAxisView,
            new ExternalAxisController.AxisUpdateListener() {
              @Override
              public void onAxisUpdated(long xMin, long xMax, boolean isPinnedToNow) {
                chartController.onGlobalXAxisChanged(
                    xMin, xMax, isPinnedToNow, getDataController());
              }
            }, /* IsLive */
            false,
            new CurrentTimeClock());
    runReviewOverlay = (RunReviewOverlay) rootView.findViewById(R.id.run_review_chart_overlay);
    runReviewOverlay.setGraphSeekBar(
        (GraphExploringSeekBar) rootView.findViewById(R.id.external_axis_seekbar));
    runReviewOverlay.setExternalAxisController(externalAxis);
    runReviewOverlay.setOnSeekbarTouchListener(
        new RunReviewOverlay.OnSeekbarTouchListener() {

          @Override
          public void onTouchStart() {
            if (audioPlaybackController.isPlaying()) {
              wasPlayingBeforeTouch = true;
              audioPlaybackController.stopPlayback();
            }
          }

          @Override
          public void onTouchStop() {
            if (wasPlayingBeforeTouch) {
              if (!isResumed()) {
                return;
              }
              wasPlayingBeforeTouch = false;
              audioPlaybackController.startPlayback(
                  getDataController(),
                  getTrial().getFirstTimestamp(),
                  getTrial().getLastTimestamp(),
                  runReviewOverlay.getTimestamp(),
                  getRunId(),
                  getSensorId());
            }
          }
        });
    runReviewOverlay.setOnLabelClickListener(
        new RunReviewOverlay.OnLabelClickListener() {

          @Override
          public void onValueLabelClicked() {
            // TODO: Allow manual timestamp editing on the normal overlay too.
          }

          @Override
          public void onCropStartLabelClicked() {
            openManualCropEditor(/*start crop*/ true);
          }

          @Override
          public void onCropEndLabelClicked() {
            openManualCropEditor(/*end crop*/ false);
          }
        });

    CoordinatedSeekbarViewGroup cropGroup =
        (CoordinatedSeekbarViewGroup) rootView.findViewById(R.id.seekbar_view_group);
    CropSeekBar firstSeekbar =
        (CropSeekBar) inflater.inflate(R.layout.crop_seek_bar, cropGroup, false);
    CropSeekBar secondSeekbar =
        (CropSeekBar) inflater.inflate(R.layout.crop_seek_bar, cropGroup, false);
    cropGroup.setSeekbarPair(firstSeekbar, secondSeekbar);
    runReviewOverlay.setCropSeekBarGroup(cropGroup);

    View statsDrawer = rootView.findViewById(R.id.stats_drawer);
    statsDrawer.setOnClickListener(
        new View.OnClickListener() {
          @Override
          public void onClick(View v) {
            if (chartController != null) {
              showStatsOverlay = !showStatsOverlay;
              chartController.setShowStatsOverlay(showStatsOverlay);
            }
          }
        });

    runReviewPlaybackButton = (ImageButton) rootView.findViewById(R.id.run_review_playback_button);
    runReviewPlaybackButton.setOnClickListener(
        new View.OnClickListener() {
          @Override
          public void onClick(View v) {
            // If playback is loading, don't do anything.
            if (audioPlaybackController.isPlaying()) {
              audioPlaybackController.stopPlayback();
            } else if (audioPlaybackController.isNotPlaying()) {
              audioPlaybackController.startPlayback(
                  getDataController(),
                  getTrial().getFirstTimestamp(),
                  getTrial().getLastTimestamp(),
                  runReviewOverlay.getTimestamp(),
                  getRunId(),
                  getSensorId());
            }
          }
        });
    // Extend the size of the playback button (which is less than the a11y min size)
    // by letting it's parent call click events on the child.
    rootView
        .findViewById(R.id.run_review_playback_button_holder)
        .setOnClickListener(
            new View.OnClickListener() {
              @Override
              public void onClick(View v) {
                runReviewPlaybackButton.callOnClick();
              }
            });

    scalarDisplayOptions = new ScalarDisplayOptions();

    graphOptionsController = new GraphOptionsController(getActivity());
    graphOptionsController.loadIntoScalarDisplayOptions(scalarDisplayOptions, rootView);

    chartController =
        new ChartController(ChartOptions.ChartPlacementType.TYPE_RUN_REVIEW, scalarDisplayOptions);
    chartController.setChartView((ChartView) rootView.findViewById(R.id.chart_view));
    chartController.setProgressView((ProgressBar) rootView.findViewById(R.id.chart_progress));
    chartController.setInteractionListener(externalAxis.getInteractionListener());
    chartController.setShowStatsOverlay(showStatsOverlay);
    runReviewOverlay.setChartController(chartController);

    ActionAreaView actionArea = rootView.findViewById(R.id.action_area);
    Activity activity = getActivity();
    if (activity instanceof RunReviewActivity) {
      RunReviewActivity runReviewActivity = (RunReviewActivity) activity;
      if (runReviewActivity.isTwoPane()) {
        actionArea.setVisibility(View.GONE);
      } else {
        actionArea.addItems(
            getContext(), runReviewActivity.getActionAreaItems(), runReviewActivity);
        if (pinnedNoteList == null) {
          pinnedNoteList = rootView.findViewById(R.id.pinned_note_list);
        }
        pinnedNoteList.setPadding(
            0,
            0,
            0,
            getResources().getDimensionPixelOffset(R.dimen.list_bottom_padding_with_action_area));
        actionArea.updateColor(getContext(), R.style.BlueActionAreaIcon);
        actionArea.setUpScrollListener(pinnedNoteList);
      }
    } else {
      actionArea.setVisibility(View.GONE);
    }
    if (Flags.showActionBar()) {
      if (activity instanceof RunReviewActivity) {
        runReviewOverlay.setOnTimestampChangeListener((RunReviewActivity) activity);
      }
    }
    return rootView;
  }

  protected long getTimestamp() {
    return runReviewOverlay.getTimestamp();
  }

  protected long getStartTimestamp() {
    return getTrial().getFirstTimestamp();
  }

  @Override
  public void onDestroyView() {
    if (chartController != null) {
      chartController.onViewRecycled();
    }
    super.onDestroyView();
  }

  @Override
  public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
    super.onCreateOptionsMenu(menu, inflater);
    inflater.inflate(R.menu.menu_run_review, menu);
  }

  @Override
  public void onPrepareOptionsMenu(Menu menu) {
    if (claimExperimentsMode) {
      // In claim experiments mode, hide all menu items except download and delete.
      menu.findItem(R.id.action_export).setVisible(false);
      menu.findItem(R.id.action_download).setVisible(true);
      menu.findItem(R.id.action_run_review_delete).setVisible(true);
      menu.findItem(R.id.action_run_review_archive).setVisible(false);
      menu.findItem(R.id.action_run_review_unarchive).setVisible(false);
      menu.findItem(R.id.action_run_review_edit).setVisible(false);
      menu.findItem(R.id.action_run_review_crop).setVisible(false);
      menu.findItem(R.id.action_run_review_audio_settings).setVisible(false);
      menu.findItem(R.id.action_enable_auto_zoom).setVisible(false);
      menu.findItem(R.id.action_disable_auto_zoom).setVisible(false);
      menu.findItem(R.id.action_graph_options).setVisible(false);
    } else {
      menu.findItem(R.id.action_graph_options).setVisible(false); // b/29771945

      // Hide some menu buttons if the run isn't loaded yet.
      if (experiment != null) {
        menu.findItem(R.id.action_run_review_archive).setVisible(!getTrial().isArchived());
        menu.findItem(R.id.action_run_review_unarchive).setVisible(getTrial().isArchived());

        menu.findItem(R.id.action_disable_auto_zoom).setVisible(getTrial().getAutoZoomEnabled());
        menu.findItem(R.id.action_enable_auto_zoom).setVisible(!getTrial().getAutoZoomEnabled());

        // You can only do a crop if the run length is long enough.
        menu.findItem(R.id.action_run_review_crop)
            .setEnabled(CropHelper.experimentIsLongEnoughForCrop(getTrial()));

        menu.findItem(R.id.action_export).setVisible(shouldShowExport());
        menu.findItem(R.id.action_download).setVisible(true);
      } else {
        menu.findItem(R.id.action_run_review_archive).setVisible(false);
        menu.findItem(R.id.action_run_review_unarchive).setVisible(false);
        menu.findItem(R.id.action_disable_auto_zoom).setVisible(false);
        menu.findItem(R.id.action_enable_auto_zoom).setVisible(false);
        menu.findItem(R.id.action_run_review_delete).setVisible(false);
        menu.findItem(R.id.action_run_review_crop).setVisible(false);
        menu.findItem(R.id.action_export).setVisible(false);
        menu.findItem(R.id.action_download).setVisible(false);
      }

      if (Flags.showActionBar()) {
        if (((RunReviewActivity) getActivity()).isFromRecord()) {
          // If this is from record, always enable deletion.
          menu.findItem(R.id.action_run_review_delete).setEnabled(true);
        }
      } else {
        if (((RunReviewDeprecatedActivity) getActivity()).isFromRecord()) {
          // If this is from record, always enable deletion.
          menu.findItem(R.id.action_run_review_delete).setEnabled(true);
        }
      }
    }

    super.onPrepareOptionsMenu(menu);
  }

  private boolean shouldShowExport() {
    return ExportService.canShare(getActivity(), appAccount);
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    // Handle action bar item clicks here. The action bar will
    // automatically handle clicks on the Home/Up button, so long
    // as you specify a parent activity in AndroidManifest.xml.
    int id = item.getItemId();

    //noinspection SimplifiableIfStatement
    if (id == android.R.id.home) {
      Intent upIntent = NavUtils.getParentActivityIntent(getActivity());
      if (experiment != null) {
        String accountKey = appAccount.getAccountKey();
        // Ensure that we set the values we need to go up to PanesActivity from RunReview after
        // starting the app from a notification (b/66162829).
        upIntent.putExtra(PanesActivity.EXTRA_ACCOUNT_KEY, accountKey);
        upIntent.putExtra(PanesActivity.EXTRA_EXPERIMENT_ID, experimentId);
        upIntent.putExtra(PanesActivity.EXTRA_CLAIM_EXPERIMENTS_MODE, claimExperimentsMode);

        upIntent.putExtra(ExperimentDetailsFragment.ARG_ACCOUNT_KEY, accountKey);
        upIntent.putExtra(ExperimentDetailsFragment.ARG_EXPERIMENT_ID, experimentId);
        upIntent.putExtra(
            ExperimentDetailsFragment.ARG_CREATE_TASK,
            getArguments().getBoolean(ARG_CREATE_TASK, false));
        upIntent.putExtra(
            ExperimentDetailsFragment.ARG_CLAIM_EXPERIMENTS_MODE, claimExperimentsMode);
        upIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        getActivity().startActivity(upIntent, null);
      } else if (getActivity() != null) {
        // This is a weird error situation: we didn't load the experiment run at all.
        // In this case, just finish.
        getActivity().onBackPressed();
        return true;
      }
      return true;
    } else if (id == R.id.action_graph_options) {
      graphOptionsController.launchOptionsDialog(
          scalarDisplayOptions, new NewOptionsStorage.SnackbarFailureListener(getView()));
    } else if (id == R.id.action_export) {
      RxDataController.getExperimentById(getDataController(), experimentId)
          .subscribe(
              experiment -> exportRun(experiment.getTrial(trialId), false),
              error -> {
                if (Log.isLoggable(TAG, Log.ERROR)) {
                  Log.e(TAG, "Export in RunReviewFragment failed", error);
                }
                throw new IllegalStateException("Export in RunReviewFragment failed", error);
              });
    } else if (id == R.id.action_download) {
      RxDataController.getExperimentById(getDataController(), experimentId)
          .subscribe(
              experiment -> exportRun(experiment.getTrial(trialId), true),
              error -> {
                if (Log.isLoggable(TAG, Log.ERROR)) {
                  Log.e(TAG, "Download in RunReviewFragment failed", error);
                }
                throw new IllegalStateException("Download in RunReviewFragment failed", error);
              });
    } else if (id == R.id.action_run_review_crop) {
      if (experiment != null) {
        launchCrop(getView());
      }
    } else if (id == R.id.action_run_review_delete) {
      if (experiment != null) {
        deleteThisRun();
      }
    } else if (id == R.id.action_run_review_archive) {
      if (experiment != null) {
        setArchived(true);
      }
    } else if (id == R.id.action_run_review_unarchive) {
      if (experiment != null) {
        setArchived(false);
      }
    } else if (id == R.id.action_run_review_edit) {
      UpdateRunActivity.launch(getActivity(), appAccount, trialId, experimentId);
    } else if (id == R.id.action_enable_auto_zoom) {
      if (experiment != null) {
        setAutoZoomEnabled(true);
      }
    } else if (id == R.id.action_disable_auto_zoom) {
      if (experiment != null) {
        setAutoZoomEnabled(false);
      }
    } else if (id == R.id.action_run_review_audio_settings) {
      launchAudioSettings();
    }
    return super.onOptionsItemSelected(item);
  }

  private void initializeData() {
    final DataController dc = getDataController();
    dc.getExperimentById(
        experimentId,
        new LoggingConsumer<Experiment>(TAG, "load experiment") {
          @Override
          public void success(Experiment experiment) {
            if (experiment == null) {
              // This experiment no longer exists, finish.
              getActivity().finish();
            }
            RunReviewFragment.this.experiment = experiment;
            attachToRun(experiment.getTrial(trialId));
            perfTracker.stopGlobalTimer(TrackerConstants.PRIMES_RUN_LOADED);
            perfTracker.onAppInteractive();
          }
        });
  }

  private void pausePlaybackForLifecycleEvent() {
    audioWasPlayingBeforePause = audioPlaybackController.isPlaying();
    audioPlaybackController.stopPlayback();
  }

  private void clearAudioPlaybackController() {
    audioPlaybackController.stopPlayback();
    audioPlaybackController.clearListener();
  }

  private void setAutoZoomEnabled(boolean enableAutoZoom) {
    getTrial().setAutoZoomEnabled(enableAutoZoom);
    getDataController()
        .updateExperiment(
            experimentId,
            new LoggingConsumer<Success>(TAG, "update auto zoom") {
              @Override
              public void success(Success value) {
                if (currentSensorStats == null) {
                  AccessibilityUtils.makeSnackbar(
                      getView(),
                      getResources().getString(R.string.autozoom_failed),
                      Snackbar.LENGTH_SHORT);
                } else {
                  chartController.clearReviewYAxis();
                  adjustYAxis();
                }
                if (getActivity() != null) {
                  getActivity().invalidateOptionsMenu();
                }
              }
            });
  }

  private void adjustYAxis() {
    if (experiment == null || currentSensorStats == null || audioPlaybackController == null) {
      return;
    }
    double yMin = currentSensorStats.getStatValue(GoosciTrial.SensorStat.StatType.MINIMUM, 0);
    double yMax = currentSensorStats.getStatValue(GoosciTrial.SensorStat.StatType.MAXIMUM, 0);
    if (getTrial().getAutoZoomEnabled()) {
      chartController.setReviewYAxis(yMin, yMax, /* has buffer */ true);
    } else {
      SensorLayoutPojo layout = getSensorLayout();
      // Don't zoom in more than the recorded data.
      // The layout's min/max y value may be too small to show the recorded data when
      // recording happened in the background and was stopped by a trigger.
      chartController.setReviewYAxis(
          Math.min(layout.getMinimumYAxisValue(), yMin),
          Math.max(layout.getMaximumYAxisValue(), yMax), /* no buffer */
          false);
    }

    if (previousYPair != null) {
      chartController.setYAxis(previousYPair.first, previousYPair.second);
      previousYPair = null;
    }

    chartController.refreshChartView();
    // TODO: What happens when we zoom the Y axis while audio is playing?
    audioPlaybackController.setYAxisRange(
        chartController.getRenderedYMin(), chartController.getRenderedYMax());
    // Redraw the thumb after the chart is updated.
    runReviewOverlay.post(
        new Runnable() {
          public void run() {
            runReviewOverlay.onYAxisAdjusted();
          }
        });
  }

  @Override
  public void onSaveInstanceState(Bundle outState) {
    super.onSaveInstanceState(outState);
    outState.putInt(KEY_SELECTED_SENSOR_INDEX, selectedSensorIndex);
    outState.putBoolean(
        KEY_TIMESTAMP_EDIT_UI_VISIBLE,
        getChildFragmentManager().findFragmentByTag(EditLabelTimeDialog.TAG) != null);
    outState.putBoolean(
        KEY_TIMESTAMP_PICKER_UI_VISIBLE,
        getChildFragmentManager().findFragmentByTag(EditTimestampDialog.TAG) != null);
    if (savedInstanceStateForLoad != null) {
      // We haven't finished loading the run from the database yet in onCreateView.
      // Go ahead and use the old savedInstanceState since we haven't reconstructed
      // everything yet.
      outState.putLong(
          KEY_EXTERNAL_AXIS_X_MINIMUM,
          savedInstanceStateForLoad.getLong(KEY_EXTERNAL_AXIS_X_MINIMUM));
      outState.putLong(
          KEY_EXTERNAL_AXIS_X_MAXIMUM,
          savedInstanceStateForLoad.getLong(KEY_EXTERNAL_AXIS_X_MAXIMUM));
      outState.putLong(
          KEY_RUN_REVIEW_OVERLAY_TIMESTAMP,
          savedInstanceStateForLoad.getLong(KEY_RUN_REVIEW_OVERLAY_TIMESTAMP));
      outState.putBoolean(
          KEY_STATS_OVERLAY_VISIBLE,
          savedInstanceStateForLoad.getBoolean(KEY_STATS_OVERLAY_VISIBLE));
      outState.putBoolean(
          KEY_AUDIO_PLAYBACK_ON, savedInstanceStateForLoad.getBoolean(KEY_AUDIO_PLAYBACK_ON));
      outState.putBoolean(
          KEY_CROP_UI_VISIBLE, savedInstanceStateForLoad.getBoolean(KEY_CROP_UI_VISIBLE));
      outState.putLong(
          KEY_CROP_START_TIMESTAMP, savedInstanceStateForLoad.getLong(KEY_CROP_START_TIMESTAMP));
      outState.putLong(
          KEY_CROP_END_TIMESTAMP, savedInstanceStateForLoad.getLong(KEY_CROP_END_TIMESTAMP));
      outState.putDouble(
          KEY_CHART_AXIS_Y_MAXIMUM, savedInstanceStateForLoad.getDouble(KEY_CHART_AXIS_Y_MAXIMUM));
      outState.putDouble(
          KEY_CHART_AXIS_Y_MINIMUM, savedInstanceStateForLoad.getDouble(KEY_CHART_AXIS_Y_MINIMUM));
    } else {
      outState.putLong(KEY_EXTERNAL_AXIS_X_MINIMUM, externalAxis.getXMin());
      outState.putLong(KEY_EXTERNAL_AXIS_X_MAXIMUM, externalAxis.getXMax());
      outState.putLong(KEY_RUN_REVIEW_OVERLAY_TIMESTAMP, runReviewOverlay.getTimestamp());
      outState.putBoolean(KEY_STATS_OVERLAY_VISIBLE, showStatsOverlay);
      outState.putBoolean(
          KEY_AUDIO_PLAYBACK_ON, audioPlaybackController.isPlaying() || audioWasPlayingBeforePause);
      outState.putBoolean(KEY_CROP_UI_VISIBLE, runReviewOverlay.getIsCropping());
      outState.putLong(KEY_CROP_START_TIMESTAMP, runReviewOverlay.getCropStartTimestamp());
      outState.putLong(KEY_CROP_END_TIMESTAMP, runReviewOverlay.getCropEndTimestamp());
      double yMax = chartController.getRenderedYMax();
      double yMin = chartController.getRenderedYMin();
      if (yMax > yMin) {
        outState.putDouble(KEY_CHART_AXIS_Y_MAXIMUM, yMax);
        outState.putDouble(KEY_CHART_AXIS_Y_MINIMUM, yMin);
      } else {
        Log.d(TAG, "not loaded");
      }
    }
  }

  private void attachToRun(final Trial trial) {
    Activity activity = getActivity();
    if (activity == null) {
      return;
    }

    // Create a BroadcastReceiver for when the stats get updated.
    broadcastReceiver =
        new BroadcastReceiver() {
          @Override
          public void onReceive(Context context, Intent intent) {
            String statsRunId = intent.getStringExtra(CropHelper.EXTRA_TRIAL_ID);
            if (TextUtils.equals(statsRunId, getTrial().getTrialId())) {
              String statsSensorId = intent.getStringExtra(CropHelper.EXTRA_SENSOR_ID);
              SensorLayoutPojo sensorLayout = getSensorLayout();
              if (TextUtils.equals(statsSensorId, sensorLayout.getSensorId())) {
                onStatsRefreshed(sensorLayout);
              }
            }
          }
        };
    CropHelper.registerStatsBroadcastReceiver(
        activity.getApplicationContext(), broadcastReceiver);

    final View rootView = getView();
    if (rootView == null) {
      ((AppCompatActivity) activity).supportStartPostponedEnterTransition();
      return;
    }
    if (Flags.showActionBar()) {
      setTitle(trial.getTitle(rootView.getContext()));
    }

    LinearLayoutManager layoutManager = new LinearLayoutManager(getActivity());
    layoutManager.setOrientation(LinearLayoutManager.VERTICAL);
    if (pinnedNoteList == null) {
      pinnedNoteList = rootView.findViewById(R.id.pinned_note_list);
    }
    pinnedNoteList.setLayoutManager(layoutManager);

    pinnedNoteAdapter =
        new PinnedNoteAdapter(
            this,
            appAccount,
            trial,
            trial.getFirstTimestamp(),
            trial.getLastTimestamp(),
            experimentId,
            claimExperimentsMode);
    pinnedNoteAdapter.setListItemModifyListener(
        new PinnedNoteAdapter.ListItemEditListener() {
          @Override
          public void onLabelEditTime(final Label item) {
            if (!claimExperimentsMode) {
              onEditNoteTimestamp(item);
            }
          }

          @Override
          public void onLabelDelete(Label item) {
            deleteLabel(item);
          }

          @Override
          public void onCaptionEdit(String updatedCaption) {
            if (!claimExperimentsMode) {
              Caption caption =
                  GoosciCaption.Caption.newBuilder()
                      .setText(updatedCaption)
                      .setLastEditedTimestamp(System.currentTimeMillis())
                      .build();
              getTrial().setCaption(caption);
              experiment.addChange(
                  Change.newModifyTypeChange(ElementType.CAPTION, getTrial().getTrialId()));
              getDataController()
                  .updateExperiment(
                      experimentId, LoggingConsumer.expectSuccess(TAG, "update caption"));
            }
          }
        });

    pinnedNoteAdapter.setListItemClickListener(
        new PinnedNoteAdapter.ListItemClickListener() {
          @Override
          public void onLabelClicked(Label item) {
            if (!claimExperimentsMode) {
              LabelDetailsActivity.launchFromRunReview(
                  getActivity(),
                  appAccount,
                  experimentId,
                  trialId,
                  selectedSensorIndex,
                  item,
                  getArguments().getBoolean(ARG_CREATE_TASK),
                  getArguments().getBoolean(RunReviewActivity.EXTRA_FROM_RECORD));
            }
          }

          @Override
          public void onAddLabelButtonClicked() {
            if (!claimExperimentsMode && experiment != null && !runReviewOverlay.getIsCropping()) {
              launchLabelAdd(
                  null,
                  Math.max(runReviewOverlay.getTimestamp(), getTrial().getFirstTimestamp()));
            }
          }

          @Override
          public void onLabelTimestampClicked(Label item) {
            if (!claimExperimentsMode) {
              // TODO: Animate to the active timestamp.
              runReviewOverlay.setActiveTimestamp(item.getTimeStamp());
            }
          }
        });

    pinnedNoteList.setAdapter(pinnedNoteAdapter);

    // Re-enable appropriate menu options.
    activity.invalidateOptionsMenu();

    hookUpExperimentDetailsArea(trial, rootView);

    // Load the data for the first sensor only.
    if (savedInstanceStateForLoad != null) {
      if (savedInstanceStateForLoad.getBoolean(KEY_AUDIO_PLAYBACK_ON, false)) {
        audioPlaybackController.setYAxisRange(
            savedInstanceStateForLoad.getDouble(KEY_CHART_AXIS_Y_MINIMUM),
            savedInstanceStateForLoad.getDouble(KEY_CHART_AXIS_Y_MAXIMUM));
        audioPlaybackController.startPlayback(
            getDataController(),
            getTrial().getFirstTimestamp(),
            getTrial().getLastTimestamp(),
            savedInstanceStateForLoad.getLong(KEY_RUN_REVIEW_OVERLAY_TIMESTAMP),
            getRunId(),
            getSensorId());
      }
      // If this isn't the first time we've made a view, check if the timepicker UI is up.
      if (savedInstanceStateForLoad.getBoolean(KEY_TIMESTAMP_EDIT_UI_VISIBLE)) {
        rootView.findViewById(R.id.embedded).setVisibility(View.VISIBLE);
        setTimepickerUi(rootView, true);
      }
      // Also check if the timestamp picker UI is up.
      if (savedInstanceStateForLoad.getBoolean(KEY_TIMESTAMP_PICKER_UI_VISIBLE)) {
        setTimestampDialogListener(
            (EditTimestampDialog)
                getChildFragmentManager().findFragmentByTag(EditTimestampDialog.TAG));
      }
      previousYPair =
          new Pair<>(
              savedInstanceStateForLoad.getDouble(KEY_CHART_AXIS_Y_MINIMUM),
              savedInstanceStateForLoad.getDouble(KEY_CHART_AXIS_Y_MAXIMUM));
    }
    setUpAxis(savedInstanceStateForLoad, rootView);
    savedInstanceStateForLoad = null;

    loadRunData(rootView);
    ((AppCompatActivity) activity).supportStartPostponedEnterTransition();
  }

  private void deleteLabel(Label item) {
    // Delete the item immediately.
    Consumer<Context> assetDeleter =
        getTrial().deleteLabelAndReturnAssetDeleter(experiment, item, appAccount);
    RxDataController.updateExperiment(getDataController(), experiment, true)
        .subscribe(() -> onLabelDelete(new DeletedLabel(item, assetDeleter)));
  }

  private void onLabelDelete(DeletedLabel item) {
    item.deleteAndDisplayUndoBar(
        getView(),
        appAccount,
        experiment,
        getTrial(),
        () -> {
          pinnedNoteAdapter.onLabelAdded(item.getLabel());
          chartController.setLabels(getTrial().getLabels());
          WhistlePunkApplication.getUsageTracker(getActivity())
              .trackEvent(
                  TrackerConstants.CATEGORY_NOTES,
                  TrackerConstants.ACTION_DELETE_UNDO,
                  TrackerConstants.LABEL_RUN_REVIEW,
                  TrackerConstants.getLabelValueType(item.getLabel()));
        });

    pinnedNoteAdapter.onLabelUpdated(item.getLabel());
    chartController.setLabels(getTrial().getLabels());

    WhistlePunkApplication.getUsageTracker(getActivity())
        .trackEvent(
            TrackerConstants.CATEGORY_NOTES,
            TrackerConstants.ACTION_DELETED,
            TrackerConstants.LABEL_RUN_REVIEW,
            TrackerConstants.getLabelValueType(item.getLabel()));
    if (claimExperimentsMode) {
      WhistlePunkApplication.getUsageTracker(getActivity())
          .trackEvent(
              TrackerConstants.CATEGORY_CLAIMING_DATA,
              TrackerConstants.ACTION_DELETE_TRIAL_NOTE,
              null,
              0);
    }
  }

  private void setUpAxis(Bundle savedInstanceStateForLoad, View rootView) {
    // The first and last timestamps of the run.
    long runFirstTimestamp = getTrial().getFirstTimestamp();
    long runLastTimestamp = getTrial().getLastTimestamp();

    // Buffer the endpoints a bit so they look nice, creating the edges of where the axis can
    // be panned / zoomed.
    long buffer = ExternalAxisController.getReviewBuffer(runFirstTimestamp, runLastTimestamp);
    long reviewXMin = runFirstTimestamp - buffer;
    long reviewXMax = runLastTimestamp + buffer;

    // These are the visible first/last timestamp. They are equal to the run first/last
    // timestamp if the user has not yet zoomed or panned.
    long firstTimestamp;
    long lastTimestamp;

    long cropStartTimestamp;
    long cropEndTimestamp;
    long overlayTimestamp;
    boolean isCropping = false;

    if (savedInstanceStateForLoad != null) {
      firstTimestamp = savedInstanceStateForLoad.getLong(KEY_EXTERNAL_AXIS_X_MINIMUM);
      lastTimestamp = savedInstanceStateForLoad.getLong(KEY_EXTERNAL_AXIS_X_MAXIMUM);
      long savedOverlayTimestamp =
          savedInstanceStateForLoad.getLong(KEY_RUN_REVIEW_OVERLAY_TIMESTAMP);
      if (savedOverlayTimestamp != RunReviewOverlay.NO_TIMESTAMP_SELECTED) {
        overlayTimestamp = savedOverlayTimestamp;
      } else {
        overlayTimestamp = runFirstTimestamp;
      }
      // Remember the crop timestamps after a rotate, even if the crop UI is not up.
      cropStartTimestamp =
          savedInstanceStateForLoad.getLong(KEY_CROP_START_TIMESTAMP, runFirstTimestamp);
      cropEndTimestamp =
          savedInstanceStateForLoad.getLong(KEY_CROP_END_TIMESTAMP, runLastTimestamp);
      isCropping = savedInstanceStateForLoad.getBoolean(KEY_CROP_UI_VISIBLE, false);
    } else {
      firstTimestamp = runFirstTimestamp;
      lastTimestamp = runLastTimestamp;
      cropStartTimestamp = runFirstTimestamp;
      cropEndTimestamp = runLastTimestamp;
      overlayTimestamp = runFirstTimestamp;
    }

    // See if the crop UI is up
    if (isCropping) {
      // Launching crop also sets the review data.
      launchCrop(rootView);
      runReviewOverlay.setAllTimestamps(overlayTimestamp, cropStartTimestamp, cropEndTimestamp);
      externalAxis.zoomTo(firstTimestamp, lastTimestamp);
    } else {
      externalAxis.setReviewData(runFirstTimestamp, runFirstTimestamp, reviewXMin, reviewXMax);
      runReviewOverlay.setActiveTimestamp(overlayTimestamp);
      if (savedInstanceStateForLoad == null) {
        externalAxis.zoomTo(reviewXMin, reviewXMax);
      } else {
        // If we just cropped the run, the prev min and max will be too wide, so make sure
        // we clip to the current run size.
        long xMin = Math.max(reviewXMin, firstTimestamp);
        long xMax = Math.min(reviewXMax, lastTimestamp);
        externalAxis.zoomTo(xMin, xMax);
      }
    }
  }

  private void deleteThisRun() {
    DeleteMetadataItemDialog dialog =
        DeleteMetadataItemDialog.newInstance(
            R.string.delete_run_dialog_title, R.string.run_review_delete_confirm);
    dialog.show(getChildFragmentManager(), DeleteMetadataItemDialog.TAG);
  }

  private void setArchived(final boolean archived) {
    getTrial().setArchived(archived);
    getDataController()
        .updateExperiment(
            experimentId,
            new LoggingConsumer<Success>(TAG, "Editing run archived state") {
              @Override
              public void success(Success value) {
                if (getActivity() == null) {
                  return;
                }
                Snackbar bar =
                    AccessibilityUtils.makeSnackbar(
                        getView(),
                        getActivity()
                            .getResources()
                            .getString(
                                archived
                                    ? R.string.archived_run_message
                                    : R.string.unarchived_run_message),
                        Snackbar.LENGTH_LONG);

                if (archived) {
                  bar.setAction(
                      R.string.action_undo,
                      new View.OnClickListener() {

                        @Override
                        public void onClick(View v) {
                          setArchived(false);
                        }
                      });
                }
                bar.show();
                getActivity().invalidateOptionsMenu();

                setArchivedUi(getView(), archived);
              }
            });
    WhistlePunkApplication.getUsageTracker(getActivity())
        .trackEvent(
            TrackerConstants.CATEGORY_RUNS,
            archived ? TrackerConstants.ACTION_ARCHIVE : TrackerConstants.ACTION_UNARCHIVE,
            null,
            0);
  }

  private void loadRunData(final View rootView) {
    audioPlaybackController.stopPlayback();
    final SensorLayoutPojo sensorLayout = getSensorLayout();
    populateSensorViews(rootView, sensorLayout);
    updateSwitchSensorArrows(rootView, getTrial().getSensorIds(), sensorLayout.getSensorId());

    String sonificationType = getSonificationType(sensorLayout);
    audioPlaybackController.setSonificationType(sonificationType);
    currentSensorStats = null;

    loadStatsAndChart(sensorLayout, (StatsList) rootView.findViewById(R.id.stats_drawer));
  }

  private void loadStatsAndChart(final SensorLayoutPojo sensorLayout, final StatsList statsList) {
    final DataController dataController = getDataController();
    final ChartController.ChartLoadingStatus fragmentRef = this;
    TrialStats stats = getTrial().getStatsForSensor(sensorLayout.getSensorId());
    populateStats(stats, statsList, sensorLayout);

    chartController.loadRunData(
        getTrial(),
        sensorLayout,
        dataController,
        fragmentRef,
        stats,
        new ChartController.ChartDataLoadedCallback() {

          @Override
          public void onChartDataLoaded(long firstTimestamp, long lastTimestamp) {
            onDataLoaded();
          }

          @Override
          public void onLoadAttemptStarted(boolean chartHiddenForLoad) {
            // Use getSensorLayout instead of the final sensorLayout
            // because the underlying sensor being loaded may have
            // changed since that final var was declared, in the case
            // where the user switches sensors rapidly.
            runReviewOverlay.updateColor(
                getActivity()
                    .getResources()
                    .getIntArray(R.array.graph_colors_array)[getSensorLayout().getColorIndex()]);
            runReviewPlaybackButton.setVisibility(View.INVISIBLE);
            runReviewOverlay.setVisibility(View.INVISIBLE);
          }
        },
        getActivity());
  }

  private void populateStats(TrialStats trialStats, StatsList statsList, SensorLayoutPojo layout) {
    currentSensorStats = trialStats;
    int color =
        getActivity()
            .getResources()
            .getIntArray(R.array.graph_colors_array)[layout.getColorIndex()];
    statsList.updateColor(color);
    if (!currentSensorStats.statsAreValid()) {
      statsList.clearStats();
      chartController.updateStats(Collections.<StreamStat>emptyList());
    } else {
      NumberFormat numberFormat =
          ProtoSensorAppearance.getAppearanceFromProtoOrProvider(
                  getTrial().getAppearances().get(layout.getSensorId()),
                  layout.getSensorId(),
                  AppSingleton.getInstance(getActivity()).getSensorAppearanceProvider(appAccount))
              .getNumberFormat();
      List<StreamStat> streamStats =
          new StatsAccumulator.StatsDisplay(numberFormat).updateStreamStats(trialStats);
      statsList.updateStats(streamStats);
      chartController.updateStats(streamStats);
    }
  }

  private void onStatsRefreshed(final SensorLayoutPojo sensorLayout) {
    if (getView() == null) {
      return;
    }
    final StatsList statsList = (StatsList) getView().findViewById(R.id.stats_drawer);
    if (statsList == null) {
      return;
    }
    // Reload the experiment run since the stats have changed.
    loadStatsAndChart(sensorLayout, statsList);
  }

  private static String getSonificationType(SensorLayoutPojo sensorLayout) {
    return LocalSensorOptionsStorage.loadFromLayoutExtras(sensorLayout)
        .getReadOnly()
        .getString(
            ScalarDisplayOptions.PREFS_KEY_SONIFICATION_TYPE,
            SonificationTypeAdapterFactory.DEFAULT_SONIFICATION_TYPE);
  }

  @Override
  public void requestDelete(Bundle extras) {
    experiment.deleteTrial(getTrial(), getActivity(), appAccount);
    getDataController()
        .updateExperiment(
            experimentId,
            new LoggingConsumer<Success>(TAG, "Deleting new trial") {
              @Override
              public void success(Success value) {
                if (claimExperimentsMode) {
                  WhistlePunkApplication.getUsageTracker(getActivity())
                      .trackEvent(
                          TrackerConstants.CATEGORY_CLAIMING_DATA,
                          TrackerConstants.ACTION_DELETE_TRIAL,
                          null,
                          0);
                }
                // Go back to the observe & record.
                Intent intent = new Intent(getActivity(), RecordFragment.class);
                NavUtils.navigateUpTo(getActivity(), intent);
              }
            });
  }

  private void onDataLoaded() {
    // Add the labels after all the data is loaded
    // so that they are interpolated correctly.
    chartController.setLabels(getTrial().getLabels());
    chartController.setShowProgress(false);

    externalAxis.updateAxis();
    adjustYAxis();

    if (chartController.hasData()) {
      // Show the replay play button
      runReviewPlaybackButton.setVisibility(View.VISIBLE);
      runReviewOverlay.setVisibility(View.VISIBLE);
    }
  }

  // TODO(saff): probably extract TrialPresenter
  private void hookUpExperimentDetailsArea(Trial trial, View rootView) {
    setArchivedUi(rootView, trial.isArchived());

    final RelativeTimeTextView runDetailsText =
        (RelativeTimeTextView) rootView.findViewById(R.id.run_details_text);
    runDetailsText.setTime(trial.getFirstTimestamp());

    final TextView durationText = (TextView) rootView.findViewById(R.id.run_review_duration);
    ElapsedTimeFormatter formatter = ElapsedTimeFormatter.getInstance(durationText.getContext());
    durationText.setText(formatter.format(trial.elapsedSeconds()));
    durationText.setContentDescription(formatter.formatForAccessibility(trial.elapsedSeconds()));
  }

  private void setArchivedUi(View rootView, boolean isArchived) {
    View archivedIndicator = rootView.findViewById(R.id.light_archived_indicator);
    if (isArchived) {
      archivedIndicator.setVisibility(View.VISIBLE);
      archivedIndicator.setAlpha(
          getResources().getFraction(R.fraction.metadata_card_archived_alpha, 1, 1));
    } else {
      archivedIndicator.setVisibility(View.GONE);
    }
  }

  // Shows and hides the switch sensor arrows based on our current position with sensorIds.
  private void updateSwitchSensorArrows(
      final View rootView, final List<String> sensorIds, final String sensorId) {
    ImageButton prevButton =
        (ImageButton) rootView.findViewById(R.id.run_review_switch_sensor_btn_prev);
    ImageButton nextButton =
        (ImageButton) rootView.findViewById(R.id.run_review_switch_sensor_btn_next);

    final int position = sensorIds.indexOf(sensorId);
    boolean hasPrevButton = position > 0;
    boolean hasNextButton = position < sensorIds.size() - 1;

    prevButton.setVisibility(hasPrevButton ? View.VISIBLE : View.INVISIBLE);
    nextButton.setVisibility(hasNextButton ? View.VISIBLE : View.INVISIBLE);

    if (hasPrevButton) {
      prevButton.setOnClickListener(
          new View.OnClickListener() {
            @Override
            public void onClick(View v) {
              selectedSensorIndex = position - 1;
              loadRunData(rootView);
            }
          });
      String prevSensorId = sensorIds.get(position - 1);
      updateContentDescription(
          prevButton,
          R.string.run_review_switch_sensor_btn_prev,
          prevSensorId,
          getActivity(),
          appAccount,
          getTrial());
    }
    if (hasNextButton) {
      nextButton.setOnClickListener(
          new View.OnClickListener() {
            @Override
            public void onClick(View v) {
              selectedSensorIndex = position + 1;
              loadRunData(rootView);
            }
          });
      String nextSensorId = sensorIds.get(position + 1);
      updateContentDescription(
          nextButton,
          R.string.run_review_switch_sensor_btn_next,
          nextSensorId,
          getActivity(),
          appAccount,
          getTrial());
    }
  }

  public static void updateContentDescription(
      ImageButton button,
      int stringId,
      String sensorId,
      Context context,
      AppAccount appAccount,
      Trial trial) {
    String content =
        ProtoSensorAppearance.getAppearanceFromProtoOrProvider(
                trial.getAppearances().get(sensorId),
                sensorId,
                AppSingleton.getInstance(context).getSensorAppearanceProvider(appAccount))
            .getName(context);
    button.setContentDescription(content);
  }

  private void populateSensorViews(View rootView, SensorLayoutPojo sensorLayout) {
    final Context context = rootView.getContext();
    final TextView sensorNameText = (TextView) rootView.findViewById(R.id.run_review_sensor_name);
    // Experiments created with C don't appear to contain sensorLayout information.
    GoosciSensorAppearance.BasicSensorAppearance basicAppearance =
        getTrial().getAppearances().get(sensorLayout.getSensorId());
    if (basicAppearance != null) {
      sensorNameText.setText(basicAppearance.getName());
      final ImageView sensorIconImage = (ImageView) rootView.findViewById(R.id.sensor_icon);
      final SensorAppearance appearance =
          ProtoSensorAppearance.getAppearanceFromProtoOrProvider(
              getTrial().getAppearances().get(sensorLayout.getSensorId()),
              sensorLayout.getSensorId(),
              AppSingleton.getInstance(context).getSensorAppearanceProvider(appAccount));
      Appearances.applyDrawableToImageView(
          appearance.getIconDrawable(context),
          sensorIconImage,
          context.getResources()
              .getIntArray(R.array.graph_colors_array)[sensorLayout.getColorIndex()]);
      runReviewOverlay.setUnits(appearance.getUnits(context));
    }
  }

  private void launchLabelAdd(Label editedLabel, long timestamp) {
    String labelTimeText =
        PinnedNoteAdapter.getNoteTimeText(timestamp, getTrial().getFirstTimestamp());
    AddNoteDialog dialog =
        AddNoteDialog.newInstance(
            appAccount,
            timestamp,
            getTrial().getTrialId(),
            experiment.getExperimentId(),
            R.string.add_note_hint_text,
            labelTimeText,
            editedLabel,
            PinnedNoteAdapter.getNoteTimeContentDescription(
                timestamp, getTrial().getFirstTimestamp(), getActivity()));
    dialog.show(getChildFragmentManager(), AddNoteDialog.TAG);
  }

  @Override
  public AddNoteDialog.AddNoteDialogListener getAddNoteDialogListener() {
    return new AddNoteDialog.AddNoteDialogListener() {
      @Override
      public MaybeConsumer<Label> onLabelAdd() {
        return new LoggingConsumer<Label>(TAG, "add label") {
          @Override
          public void success(Label newLabel) {
            pinnedNoteAdapter.onLabelAdded(newLabel);
            chartController.setLabels(getTrial().getLabels());
            WhistlePunkApplication.getUsageTracker(getActivity())
                .trackEvent(
                    TrackerConstants.CATEGORY_NOTES,
                    TrackerConstants.ACTION_CREATE,
                    TrackerConstants.LABEL_RUN_REVIEW,
                    TrackerConstants.getLabelValueType(newLabel));
          }
        };
      }

      @Override
      public void onAddNoteTimestampClicked(Label editedLabel, long selectedTimestamp) {
        AddNoteDialog addDialog =
            (AddNoteDialog) getChildFragmentManager().findFragmentByTag(AddNoteDialog.TAG);
        if (addDialog != null) {
          addDialog.dismiss();
        }

        // Show the timestamp edit window below the graph / over the notes
        getView().findViewById(R.id.embedded).setVisibility(View.VISIBLE);
        EditLabelTimeDialog timeDialog =
            EditLabelTimeDialog.newInstance(
                editedLabel, selectedTimestamp, getTrial().getFirstTimestamp());
        FragmentTransaction ft = getChildFragmentManager().beginTransaction();
        ft.add(R.id.embedded, timeDialog, EditLabelTimeDialog.TAG);
        ft.commit();
        runReviewOverlay.setActiveTimestamp(selectedTimestamp);
        runReviewOverlay.setOnTimestampChangeListener(timeDialog);
        setTimepickerUi(getView(), true);
      }

      @Override
      public Single<String> whenExperimentId() {
        return Single.just(experimentId);
      }
    };
  }

  private Trial getTrial() {
    if (experiment == null) {
      return null;
    }
    return experiment.getTrial(trialId);
  }

  @Override
  public void onActivityResult(int requestCode, int resultCode, Intent data) {
    if (requestCode == PictureUtils.REQUEST_TAKE_PHOTO) {
      Fragment dialog = getChildFragmentManager().findFragmentByTag(AddNoteDialog.TAG);
      if (dialog != null) {
        dialog.onActivityResult(requestCode, resultCode, data);
      }
    }
  }

  private MaybeConsumer<Success> onLabelEdit(final Label label) {
    return new LoggingConsumer<Success>(TAG, "edit label") {
      @Override
      public void success(Success value) {
        pinnedNoteAdapter.onLabelUpdated(label);
        // The timestamp was edited, so also refresh the line graph presenter.
        chartController.setLabels(getTrial().getLabels());
        WhistlePunkApplication.getUsageTracker(getActivity())
            .trackEvent(
                TrackerConstants.CATEGORY_NOTES,
                TrackerConstants.ACTION_EDITED,
                TrackerConstants.LABEL_RUN_REVIEW,
                TrackerConstants.getLabelValueType(label));
      }
    };
  }

  public void onEditNoteTimestamp(Label label) {
    // Show the timestamp edit window below the graph / over the notes
    getView().findViewById(R.id.embedded).setVisibility(View.VISIBLE);
    EditLabelTimeDialog timeDialog =
        EditLabelTimeDialog.newInstance(label, getTrial().getFirstTimestamp());
    FragmentTransaction ft = getChildFragmentManager().beginTransaction();
    ft.add(R.id.embedded, timeDialog, EditLabelTimeDialog.TAG);
    ft.commit();
    runReviewOverlay.setActiveTimestamp(label.getTimeStamp());
    runReviewOverlay.setOnTimestampChangeListener(timeDialog);
    setTimepickerUi(getView(), true);
  }

  private void setTimepickerUi(View rootView, boolean showTimepicker) {
    if (showTimepicker) {
      actionMode =
          ((AppCompatActivity) getActivity())
              .startSupportActionMode(
                  new ActionMode.Callback() {
                    public boolean onCreateActionMode(ActionMode mode, Menu menu) {
                      mode.setTitle(getResources().getString(R.string.edit_note_time));
                      return true;
                    }

                    @Override
                    public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
                      return false;
                    }

                    @Override
                    public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
                      return false;
                    }

                    @Override
                    public void onDestroyActionMode(ActionMode mode) {
                      if (actionMode != null) {
                        actionMode = null;
                        dismissEditTimeDialog();
                      }
                    }
                  });
      EditLabelTimeDialog dialog =
          (EditLabelTimeDialog)
              getChildFragmentManager().findFragmentByTag(EditLabelTimeDialog.TAG);
      if (dialog != null) {
        runReviewOverlay.setActiveTimestamp(dialog.getCurrentTimestamp());
        runReviewOverlay.setOnTimestampChangeListener(dialog);
      }
    }
    setUiForActionMode(rootView, showTimepicker);
  }

  private void setCropUi(View rootView, boolean showCrop) {
    if (showCrop) {
      actionMode =
          ((AppCompatActivity) getActivity())
              .startSupportActionMode(
                  new ActionMode.Callback() {
                    public boolean onCreateActionMode(ActionMode mode, Menu menu) {
                      mode.setTitle(getResources().getString(R.string.crop_run));
                      MenuInflater inflater = mode.getMenuInflater();
                      inflater.inflate(R.menu.crop_menu, menu);
                      return true;
                    }

                    @Override
                    public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
                      return false;
                    }

                    @Override
                    public boolean onActionItemClicked(final ActionMode mode, MenuItem item) {
                      if (item.getItemId() == R.id.action_save) {
                        saveCrop(mode);
                        return true;
                      }
                      if (item.getItemId() == R.id.action_edit_crop_start_time) {
                        openManualCropEditor(/*start crop*/ true);
                        return true;
                      }
                      if (item.getItemId() == R.id.action_edit_crop_end_time) {
                        openManualCropEditor(/*end crop*/ false);
                        return true;
                      }
                      return false;
                    }

                    @Override
                    public void onDestroyActionMode(ActionMode mode) {
                      if (actionMode != null) {
                        actionMode = null;
                        completeCrop();
                      }
                    }
                  });
    }
    setUiForActionMode(rootView, showCrop);
  }

  private void saveCrop(final ActionMode mode) {
    CropHelper helper = new CropHelper(getDataController());
    helper.cropTrial(
        getActivity().getApplicationContext(),
        experiment,
        trialId,
        runReviewOverlay.getCropStartTimestamp(),
        runReviewOverlay.getCropEndTimestamp(),
        new CropHelper.CropTrialListener() {
          @Override
          public void onCropCompleted() {
            if (getActivity() == null || isDetached()) {
              // TODO: It's too late to save state that crop is completed, so it'll
              // be pulled up after rotation. That's better than a crash, but
              // after rotation there should be a way to check if the crop finished
              // from before if possible.
              return;
            }
            AccessibilityUtils.makeSnackbar(
                    getView(),
                    getResources().getString(R.string.crop_completed_message),
                    Snackbar.LENGTH_SHORT)
                .show();
            hookUpExperimentDetailsArea(getTrial(), getView());
            pinnedNoteAdapter.updateRunTimestamps(
                getTrial().getFirstTimestamp(), getTrial().getLastTimestamp());
            if (mode != null) {
              mode.finish();
            }
          }

          @Override
          public void onCropFailed(int errorId) {
            if (getActivity() == null || isDetached()) {
              return;
            }
            AccessibilityUtils.makeSnackbar(
                    getView(),
                    String.format(
                        getResources().getString(R.string.crop_failed_message),
                        getResources().getString(errorId)),
                    Snackbar.LENGTH_LONG)
                .show();
            if (mode != null) {
              mode.finish();
            }
          }
        });
  }

  private void setUiForActionMode(View rootView, boolean showActionMode) {
    if (showActionMode) {
      // Hide the min/max/avg section for landscape views.
      // We could do this for portrait too, if desired, but it seems unnecessary.
      if (rootView.getResources().getConfiguration().orientation
          == Configuration.ORIENTATION_LANDSCAPE) {
        // TODO: Animate hiding this view?
        rootView.findViewById(R.id.stats_drawer).setVisibility(View.GONE);
      }

      // Collapse app bar layout as much as possible to bring the graph to the top.
      AppBarLayout appBarLayout = (AppBarLayout) rootView.findViewById(R.id.app_bar_layout);
      appBarLayout.setExpanded(false);
      setFrozen(rootView, true);

      rootView
          .findViewById(R.id.pinned_note_list)
          .setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);

      // Show a grey overlay over the notes. Make it so users can cancel the dialog
      // by clicking in the grey overlay to simulate a normal dialog.
      View notesOverlay = rootView.findViewById(R.id.pinned_note_overlay);
      notesOverlay.setOnTouchListener(
          new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
              return true;
            }
          });

      notesOverlay.setVisibility(View.VISIBLE);
    } else {
      if (actionMode != null) {
        actionMode.finish();
      }
      setFrozen(rootView, false);
      rootView.findViewById(R.id.pinned_note_overlay).setVisibility(View.GONE);
      rootView.findViewById(R.id.embedded).setVisibility(View.GONE);
      rootView.findViewById(R.id.stats_drawer).setVisibility(View.VISIBLE);

      rootView
          .findViewById(R.id.pinned_note_list)
          .setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_AUTO);
    }
  }

  private void dismissEditTimeDialog() {
    EditLabelTimeDialog dialog =
        (EditLabelTimeDialog) getChildFragmentManager().findFragmentByTag(EditLabelTimeDialog.TAG);
    if (dialog != null) {
      dialog.dismiss();
    }
  }

  private void setFrozen(View rootView, boolean isFrozen) {
    ((FreezeableCoordinatorLayout) rootView).setFrozen(isFrozen);
  }

  @Override
  public void onEditTimeDialogDismissedEdit(Label label, long selectedTimestamp) {
    setTimepickerUi(getView(), false);
    label.setTimestamp(selectedTimestamp);
    Trial trial = experiment.getTrial(trialId);
    trial.updateLabel(experiment, label);
    experiment.updateTrial(trial);
    RxDataController.updateExperiment(getDataController(), experiment, true)
        .subscribe(MaybeConsumers.toCompletableObserver(onLabelEdit(label)));
  }

  @Override
  public void onEditTimeDialogDismissedAdd(Label editedLabel, long selectedTimestamp) {
    setTimepickerUi(getView(), false);
    launchLabelAdd(editedLabel, selectedTimestamp);
  }

  private void launchAudioSettings() {
    audioPlaybackController.stopPlayback();

    List<SensorLayoutPojo> sensorLayouts = getTrial().getSensorLayouts();
    int size = sensorLayouts.size();
    String[] sensorIds = new String[size];
    String[] sonificationTypes = new String[size];
    for (int i = 0; i < size; i++) {
      SensorLayoutPojo layout = sensorLayouts.get(i);
      sensorIds[i] = layout.getSensorId();
      sonificationTypes[i] = getSonificationType(layout);
    }
    AudioSettingsDialog dialog =
        AudioSettingsDialog.newInstance(
            appAccount, sonificationTypes, sensorIds, selectedSensorIndex);
    dialog.show(getChildFragmentManager(), AudioSettingsDialog.TAG);
  }

  private void launchCrop(View rootView) {
    rootView.findViewById(R.id.run_review_playback_button_holder).setVisibility(View.GONE);
    audioPlaybackController.stopPlayback();

    long originalFirstTimestamp = getTrial().getOriginalFirstTimestamp();
    long lastTimestamp = getTrial().getOriginalLastTimestamp();
    // Load data even if it was previously cropped out of the graph
    chartController.setShowOriginalRun(true);

    runReviewOverlay.resetCropTimestamps();
    runReviewOverlay.setCropModeOn(true);

    long buffer = ExternalAxisController.getReviewBuffer(originalFirstTimestamp, lastTimestamp);
    externalAxis.setReviewData(
        originalFirstTimestamp,
        getTrial().getFirstTimestamp(),
        originalFirstTimestamp - buffer,
        lastTimestamp + buffer);
    externalAxis.updateAxis();

    WhistlePunkApplication.getUsageTracker(getActivity())
        .trackEvent(TrackerConstants.CATEGORY_RUNS, TrackerConstants.ACTION_CROP_STARTED, "", 1);

    setCropUi(rootView, true);
  }

  public void completeCrop() {
    chartController.setShowOriginalRun(false);
    setCropUi(getView(), false);
    getView().findViewById(R.id.run_review_playback_button_holder).setVisibility(View.VISIBLE);
    runReviewOverlay.setCropModeOn(false);

    // When we started cropping we may have changed the external axis review data. Reset that
    // now that the crop is not happening any more.
    long runFirstTimestamp = getTrial().getFirstTimestamp();
    long runLastTimestamp = getTrial().getLastTimestamp();
    long buffer = ExternalAxisController.getReviewBuffer(runFirstTimestamp, runLastTimestamp);
    long idealRenderedXMin = runFirstTimestamp - buffer;
    long idealRenderedXMax = runLastTimestamp + buffer;
    Bundle timestampBundle = new Bundle();

    long currentRenderedXMin = externalAxis.getXMin();
    long currentRenderedXMax = externalAxis.getXMax();

    long nextRenderedXMin;
    long nextRenderedXMax;

    if (currentRenderedXMin > idealRenderedXMax || currentRenderedXMax < idealRenderedXMin) {
      // If we are currently viewing an area too high or too low on the X axis, just go back
      // to the ideal default rendered range.
      nextRenderedXMin = idealRenderedXMin;
      nextRenderedXMax = idealRenderedXMax;
    } else {
      // Otherwise, clip back to the new range.
      nextRenderedXMin = Math.max(currentRenderedXMin, idealRenderedXMin);
      nextRenderedXMax = Math.min(currentRenderedXMax, idealRenderedXMax);
      if (nextRenderedXMax - nextRenderedXMin < CropHelper.MINIMUM_CROP_MILLIS) {
        // Make sure we aren't too zoomed in.
        nextRenderedXMin = idealRenderedXMin;
        nextRenderedXMax = idealRenderedXMax;
      }
    }

    timestampBundle.putLong(KEY_EXTERNAL_AXIS_X_MINIMUM, nextRenderedXMin);
    timestampBundle.putLong(KEY_EXTERNAL_AXIS_X_MAXIMUM, nextRenderedXMax);
    timestampBundle.putLong(KEY_RUN_REVIEW_OVERLAY_TIMESTAMP, runReviewOverlay.getTimestamp());
    setUpAxis(timestampBundle, getView());

    // TODO: Better way to throw out cropped (or out of range) data besides reloading,
    // As most all the data needed is already loaded -- this is really being done to throw
    // away cropped data.
    loadRunData(getView());
  }

  public void openManualCropEditor(boolean isStartCrop) {
    EditTimestampDialog timestampDialog =
        EditTimestampDialog.newInstance(
            isStartCrop,
            getTrial().getOriginalFirstTimestamp(),
            getTrial().getOriginalLastTimestamp(),
            getTrial().getFirstTimestamp(),
            isStartCrop
                ? runReviewOverlay.getCropStartTimestamp()
                : runReviewOverlay.getCropEndTimestamp());
    setTimestampDialogListener(timestampDialog);
    timestampDialog.show(getChildFragmentManager(), EditTimestampDialog.TAG);
  }

  private void setTimestampDialogListener(EditTimestampDialog timestampDialog) {
    timestampDialog.setOnPickerTimestampChangedListener(
        new TimestampPickerController.TimestampPickerListener() {
          @Override
          public int isValidTimestamp(long timestamp, boolean isStartCrop) {
            // Is it too close to the other seekbar?
            if (runReviewOverlay.isValidCropTimestamp(timestamp, isStartCrop)) {
              return TimestampPickerController.NO_ERROR;
            } else {
              return R.string.timestamp_picker_crop_range_error;
            }
          }

          @Override
          public void onPickerTimestampChanged(long timestamp, boolean isStartCrop) {
            // Zoom out to show the new timestamp if needed.
            if (isStartCrop) {
              if (timestamp < externalAxis.getXMin()) {
                long buffer =
                    ExternalAxisController.getReviewBuffer(timestamp, externalAxis.getXMax());
                externalAxis.zoomTo(timestamp - buffer, externalAxis.getXMax());
              }
              runReviewOverlay.setCropTimestamps(timestamp, runReviewOverlay.getCropEndTimestamp());
            } else {
              if (timestamp > externalAxis.getXMax()) {
                long buffer =
                    ExternalAxisController.getReviewBuffer(externalAxis.getXMin(), timestamp);
                externalAxis.zoomTo(externalAxis.getXMin(), timestamp + buffer);
              }
              runReviewOverlay.setCropTimestamps(
                  runReviewOverlay.getCropStartTimestamp(), timestamp);
            }
          }
        });
  }

  @Override
  public void onAudioSettingsPreview(String[] previewSonificationTypes, String[] sensorIds) {
    // RunReview does not have audio preview.
  }

  @Override
  public void onAudioSettingsApplied(String[] newSonificationTypes, String[] sensorIds) {
    // Update the currently selected sonification type.
    audioPlaybackController.setSonificationType(newSonificationTypes[selectedSensorIndex]);

    // Save the new sonification types into their respective sensorLayouts.
    // Note that this uses the knowledge that the sensor ordering has not changed since
    // launchAudioSettings.
    List<SensorLayoutPojo> layouts = getTrial().getSensorLayouts();
    int size = layouts.size();
    for (int i = 0; i < size; i++) {
      // Update the sonification setting in the extras for this layout.
      LocalSensorOptionsStorage storage = new LocalSensorOptionsStorage();
      storage.putAllExtras(layouts.get(i).getExtras());
      storage
          .load(LoggingConsumer.expectSuccess(TAG, "loading sensor layout"))
          .put(ScalarDisplayOptions.PREFS_KEY_SONIFICATION_TYPE, newSonificationTypes[i]);
      layouts.get(i).putAllExtras(storage.exportAsLayoutExtras());
    }

    // Save the updated layouts to the DB.
    getDataController()
        .updateExperiment(
            experimentId, LoggingConsumer.<Success>expectSuccess(TAG, "updating audio settings"));
  }

  @Override
  public void onAudioSettingsCanceled(String[] originalSonificationTypes, String[] sensorIds) {
    // RunReview does not have audio preview.
  }

  private DataController getDataController() {
    return AppSingleton.getInstance(getActivity()).getDataController(appAccount);
  }

  private void exportRun(final Trial trial, boolean saveLocally) {
    ExportOptionsDialogFragment fragment =
        ExportOptionsDialogFragment.createOptionsDialog(
            appAccount, experiment.getExperimentId(), trial.getTrialId(), saveLocally);
    fragment.show(((AppCompatActivity) getActivity()).getSupportFragmentManager(), "export");
  }

  @Override
  public int getGraphLoadStatus() {
    return loadingStatus;
  }

  @Override
  public void setGraphLoadStatus(int graphLoadStatus) {
    loadingStatus = graphLoadStatus;
  }

  @Override
  public String getRunId() {
    return trialId;
  }

  @Override
  public String getSensorId() {
    return getSensorLayout().getSensorId();
  }

  protected SensorLayoutPojo getSensorLayout() {
    return getTrial().getSensorLayouts().get(selectedSensorIndex);
  }

  public void reloadAndScrollToLabel(Label newLabel) {
    int index = pinnedNoteAdapter.onLabelAdded(newLabel);
    if (DevOptionsFragment.isSmoothScrollingToBottomEnabled(getContext())) {
      pinnedNoteList.smoothScrollToPosition(index);
    } else {
      pinnedNoteList.scrollToPosition(index);
    }
  }

  private void setTitle(String trialName) {
    Activity activity = getActivity();
    if (activity instanceof NoteTakingActivity) {
      ((NoteTakingActivity) activity).updateTitleByDefaultFragment(trialName);
    }
  }

  @Override
  public String getTitle() {
    Trial trial = getTrial();
    if (trial == null) {
      return null;
    }
    return trial.getTitle(getContext());
  }
}
