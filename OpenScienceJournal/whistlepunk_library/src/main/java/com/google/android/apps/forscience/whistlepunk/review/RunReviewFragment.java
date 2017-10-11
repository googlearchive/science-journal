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

import android.app.Fragment;
import android.app.FragmentTransaction;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.os.Build;
import android.os.Bundle;
import android.support.design.widget.AppBarLayout;
import android.support.design.widget.CoordinatorLayout;
import android.support.design.widget.Snackbar;
import android.support.v4.app.NavUtils;
import android.support.v4.util.Pair;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.view.ActionMode;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
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
import com.google.android.apps.forscience.whistlepunk.ElapsedTimeFormatter;
import com.google.android.apps.forscience.whistlepunk.ExternalAxisController;
import com.google.android.apps.forscience.whistlepunk.ExternalAxisView;
import com.google.android.apps.forscience.whistlepunk.LocalSensorOptionsStorage;
import com.google.android.apps.forscience.whistlepunk.LoggingConsumer;
import com.google.android.apps.forscience.whistlepunk.MultiWindowUtils;
import com.google.android.apps.forscience.whistlepunk.PanesActivity;
import com.google.android.apps.forscience.whistlepunk.PictureUtils;
import com.google.android.apps.forscience.whistlepunk.R;
import com.google.android.apps.forscience.whistlepunk.RecordFragment;
import com.google.android.apps.forscience.whistlepunk.RelativeTimeTextView;
import com.google.android.apps.forscience.whistlepunk.RunReviewOverlay;
import com.google.android.apps.forscience.whistlepunk.RxDataController;
import com.google.android.apps.forscience.whistlepunk.SensorAppearance;
import com.google.android.apps.forscience.whistlepunk.StatsAccumulator;
import com.google.android.apps.forscience.whistlepunk.StatsList;
import com.google.android.apps.forscience.whistlepunk.WhistlePunkApplication;
import com.google.android.apps.forscience.whistlepunk.analytics.TrackerConstants;
import com.google.android.apps.forscience.whistlepunk.audiogen.AudioPlaybackController;
import com.google.android.apps.forscience.whistlepunk.audiogen.SonificationTypeAdapterFactory;
import com.google.android.apps.forscience.whistlepunk.data.GoosciSensorLayout;
import com.google.android.apps.forscience.whistlepunk.filemetadata.Experiment;
import com.google.android.apps.forscience.whistlepunk.filemetadata.Label;
import com.google.android.apps.forscience.whistlepunk.filemetadata.Trial;
import com.google.android.apps.forscience.whistlepunk.filemetadata.TrialStats;
import com.google.android.apps.forscience.whistlepunk.intro.AgeVerifier;
import com.google.android.apps.forscience.whistlepunk.metadata.CropHelper;
import com.google.android.apps.forscience.whistlepunk.metadata.GoosciCaption;
import com.google.android.apps.forscience.whistlepunk.metadata.GoosciTrial;
import com.google.android.apps.forscience.whistlepunk.performance.PerfTrackerProvider;
import com.google.android.apps.forscience.whistlepunk.project.experiment.ExperimentDetailsFragment;
import com.google.android.apps.forscience.whistlepunk.review.EditLabelTimeDialog
        .EditTimeDialogListener;
import com.google.android.apps.forscience.whistlepunk.review.labels.LabelDetailsActivity;
import com.google.android.apps.forscience.whistlepunk.scalarchart.ChartController;
import com.google.android.apps.forscience.whistlepunk.scalarchart.ChartOptions;
import com.google.android.apps.forscience.whistlepunk.scalarchart.ChartView;
import com.google.android.apps.forscience.whistlepunk.scalarchart.GraphOptionsController;
import com.google.android.apps.forscience.whistlepunk.scalarchart.ScalarDisplayOptions;
import com.google.android.apps.forscience.whistlepunk.sensorapi.NewOptionsStorage;
import com.google.android.apps.forscience.whistlepunk.sensorapi.StreamStat;

import java.text.NumberFormat;
import java.util.Collections;
import java.util.List;

import io.reactivex.Single;
import io.reactivex.functions.Consumer;

public class RunReviewFragment extends Fragment implements
        AddNoteDialog.ListenerProvider,
        EditTimeDialogListener,
        DeleteMetadataItemDialog.DeleteDialogListener,
        AudioSettingsDialog.AudioSettingsDialogListener,
        ChartController.ChartLoadingStatus {
    public static final String ARG_EXPERIMENT_ID = "experimentId";
    public static final String ARG_START_LABEL_ID = "start_label_id";
    public static final String ARG_SENSOR_INDEX = "sensor_tag_index";
    public static final String ARG_CREATE_TASK = "create_task";
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

    private int mLoadingStatus = GRAPH_LOAD_STATUS_IDLE;

    public static final double MILLIS_IN_A_SECOND = 1000.0;

    public static final int LABEL_TYPE_TEXT = 0;
    public static final int LABEL_TYPE_PICTURE = 1;

    private ImageButton mRunReviewPlaybackButton;
    private AudioPlaybackController mAudioPlaybackController;
    private boolean mWasPlayingBeforeTouch = false;
    private boolean mAudioWasPlayingBeforePause = false;

    private String mTrialId;
    private String mExperimentId;
    private int mSelectedSensorIndex = 0;
    private GraphOptionsController mGraphOptionsController;
    private ScalarDisplayOptions mScalarDisplayOptions;
    private ChartController mChartController;
    private ExternalAxisController mExternalAxis;
    private RunReviewOverlay mRunReviewOverlay;
    private PinnedNoteAdapter mPinnedNoteAdapter;
    private Experiment mExperiment;
    private ActionMode mActionMode;
    private TrialStats mCurrentSensorStats;
    private boolean mShowStatsOverlay = false;
    private BroadcastReceiver mBroadcastReceiver;
    private Pair<Double, Double> mPreviousYPair;
    private PerfTrackerProvider mPerfTracker;

    // Save the savedInstanceState between onCreateView and loading the run data, in case
    // an onPause happens during that time.
    private Bundle mSavedInstanceStateForLoad;

    /**
     * Use this factory method to create a new instance of
     * this fragment using the provided parameters.
     *
     * @param startLabelId the startLabelId that joins the labels identifying this run
     * @param sensorIndex the initial sensor to select in run review
     * @param createTask if {@code true}, will create tasks when navigating up
     * @return A new instance of fragment RunReviewFragment.
     */
    public static RunReviewFragment newInstance(String experimentId, String startLabelId,
            int sensorIndex, boolean createTask) {
        RunReviewFragment fragment = new RunReviewFragment();
        Bundle args = new Bundle();
        args.putString(ARG_EXPERIMENT_ID, experimentId);
        args.putString(ARG_START_LABEL_ID, startLabelId);
        args.putInt(ARG_SENSOR_INDEX, sensorIndex);
        args.putBoolean(ARG_CREATE_TASK, createTask);
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
        DeletedLabel deletedLabel =
                AppSingleton.getInstance(getActivity()).popDeletedLabelForUndo();
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
        if (mAudioPlaybackController != null) {
            clearAudioPlaybackController();
            mAudioPlaybackController = null;
        }
        mGraphOptionsController = null;
        if (mExternalAxis != null) {
            mExternalAxis.destroy();
        }
        if (mPinnedNoteAdapter != null) {
            mPinnedNoteAdapter.onDestroy();
        }
        if (mRunReviewOverlay != null) {
            mRunReviewOverlay.onDestroy();
        }
        if (mChartController != null) {
            mChartController.onDestroy();
        }
        super.onDestroy();
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mPerfTracker = WhistlePunkApplication.getPerfTrackerProvider(getActivity());
        mPerfTracker.startGlobalTimer(TrackerConstants.PRIMES_RUN_LOADED);
        if (getArguments() != null) {
            mTrialId = getArguments().getString(ARG_START_LABEL_ID);
            mSelectedSensorIndex = getArguments().getInt(ARG_SENSOR_INDEX);
            mExperimentId = getArguments().getString(ARG_EXPERIMENT_ID);
        }
        if (savedInstanceState != null) {
            if (savedInstanceState.containsKey(KEY_SELECTED_SENSOR_INDEX)) {
                // saved instance state is more recent than args, so it takes precedence.
                mSelectedSensorIndex = savedInstanceState.getInt(KEY_SELECTED_SENSOR_INDEX);
            }
            mShowStatsOverlay = savedInstanceState.getBoolean(KEY_STATS_OVERLAY_VISIBLE, false);
        }
        mAudioPlaybackController = new AudioPlaybackController(
                new AudioPlaybackController.AudioPlaybackListener() {
                    @Override
                    public void onAudioPlaybackStarted() {
                        if (!isAdded()) {
                            return;
                        }
                        WhistlePunkApplication.getUsageTracker(getActivity()).trackEvent(
                                TrackerConstants.CATEGORY_RUNS,
                                TrackerConstants.ACTION_START_AUDIO_PLAYBACK,
                                TrackerConstants.LABEL_RUN_REVIEW, 0);
                        mRunReviewPlaybackButton.setImageDrawable(
                                getResources().getDrawable(R.drawable.ic_pause_black_24dp));
                        mRunReviewPlaybackButton.setContentDescription(
                                getResources().getString(R.string.playback_button_pause));
                    }

                    @Override
                    public void onTimestampUpdated(long activeTimestamp) {
                        mRunReviewOverlay.setActiveTimestamp(activeTimestamp);
                    }

                    @Override
                    public void onAudioPlaybackStopped() {
                        mAudioPlaybackController.stopPlayback();
                        if (!isAdded()) {
                            return;
                        }
                        mRunReviewPlaybackButton.setImageDrawable(
                                getResources().getDrawable(R.drawable.ic_play_arrow_black_24dp));
                        mRunReviewPlaybackButton.setContentDescription(
                                getResources().getString(R.string.playback_button_play));
                    }
                });
        setHasOptionsMenu(true);
    }

    @Override
    public void onStart() {
        super.onStart();
        if (isMultiWindowEnabled()) {
            initializeData();
        }
        WhistlePunkApplication.getUsageTracker(getActivity()).trackScreenView(
                TrackerConstants.SCREEN_RUN_REVIEW);
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
        if (mBroadcastReceiver != null) {
            CropHelper.unregisterBroadcastReceiver(getActivity(), mBroadcastReceiver);
            mBroadcastReceiver = null;
        }
    }

    private boolean isMultiWindowEnabled() {
        return MultiWindowUtils.isMultiWindowEnabled(getActivity());
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            final Bundle savedInstanceState) {
        mSavedInstanceStateForLoad = savedInstanceState;
        final View rootView = inflater.inflate(R.layout.fragment_run_review, container, false);
        AppBarLayout appBarLayout = (AppBarLayout) rootView.findViewById(R.id.app_bar_layout);
        ExternalAxisView externalAxisView =
                (ExternalAxisView) rootView.findViewById(R.id.external_x_axis);
        mExternalAxis = new ExternalAxisController(externalAxisView,
                new ExternalAxisController.AxisUpdateListener() {
                    @Override
                    public void onAxisUpdated(long xMin, long xMax, boolean isPinnedToNow) {
                        mChartController.onGlobalXAxisChanged(xMin, xMax, isPinnedToNow,
                                getDataController());
                    }
                }, /* IsLive */ false, new CurrentTimeClock());
        mRunReviewOverlay =
                (RunReviewOverlay) rootView.findViewById(R.id.run_review_chart_overlay);
        mRunReviewOverlay.setGraphSeekBar(
                (GraphExploringSeekBar) rootView.findViewById(R.id.external_axis_seekbar));
        mRunReviewOverlay.setExternalAxisController(mExternalAxis);
        mRunReviewOverlay.setOnSeekbarTouchListener(
                new RunReviewOverlay.OnSeekbarTouchListener() {

                    @Override
                    public void onTouchStart() {
                        if (mAudioPlaybackController.isPlaying()) {
                            mWasPlayingBeforeTouch = true;
                            mAudioPlaybackController.stopPlayback();
                        }
                    }

                    @Override
                    public void onTouchStop() {
                        if (mWasPlayingBeforeTouch) {
                            if (!isResumed()) {
                                return;
                            }
                            mWasPlayingBeforeTouch = false;
                            mAudioPlaybackController.startPlayback(getDataController(),
                                    getTrial().getFirstTimestamp(),
                                    getTrial().getLastTimestamp(),
                                    mRunReviewOverlay.getTimestamp(),
                                    getSensorId());
                        }
                    }
                });
        mRunReviewOverlay.setOnLabelClickListener(new RunReviewOverlay.OnLabelClickListener() {

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
        mRunReviewOverlay.setCropSeekBarGroup(cropGroup);

        View statsDrawer = rootView.findViewById(R.id.stats_drawer);
        statsDrawer.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mChartController != null) {
                    mShowStatsOverlay = !mShowStatsOverlay;
                    mChartController.setShowStatsOverlay(mShowStatsOverlay);
                }
            }
        });

        mRunReviewPlaybackButton =
                (ImageButton) rootView.findViewById(R.id.run_review_playback_button);
        mRunReviewPlaybackButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // If playback is loading, don't do anything.
                if (mAudioPlaybackController.isPlaying()) {
                    mAudioPlaybackController.stopPlayback();
                } else if (mAudioPlaybackController.isNotPlaying()){
                    mAudioPlaybackController.startPlayback(getDataController(),
                            getTrial().getFirstTimestamp(), getTrial().getLastTimestamp(),
                            mRunReviewOverlay.getTimestamp(), getSensorId());
                }
            }
        });
        // Extend the size of the playback button (which is less than the a11y min size)
        // by letting it's parent call click events on the child.
        rootView.findViewById(R.id.run_review_playback_button_holder).setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        mRunReviewPlaybackButton.callOnClick();
                    }
                });

        mScalarDisplayOptions = new ScalarDisplayOptions();

        mGraphOptionsController = new GraphOptionsController(getActivity());
        mGraphOptionsController.loadIntoScalarDisplayOptions(mScalarDisplayOptions, rootView);

        mChartController = new ChartController(ChartOptions.ChartPlacementType.TYPE_RUN_REVIEW,
                mScalarDisplayOptions);
        mChartController.setChartView((ChartView) rootView.findViewById(R.id.chart_view));
        mChartController.setProgressView((ProgressBar) rootView.findViewById(R.id.chart_progress));
        mChartController.setInteractionListener(mExternalAxis.getInteractionListener());
        mChartController.setShowStatsOverlay(mShowStatsOverlay);
        mRunReviewOverlay.setChartController(mChartController);

        return rootView;
    }

    @Override
    public void onDestroyView() {
        if (mChartController != null) {
            mChartController.onViewRecycled();
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
        menu.findItem(R.id.action_export).setVisible(AgeVerifier.isOver13(
                AgeVerifier.getUserAge(getActivity())));
        menu.findItem(R.id.action_graph_options).setVisible(false);  // b/29771945

        // Hide some menu buttons if the run isn't loaded yet.
        if (mExperiment != null) {
            menu.findItem(R.id.action_run_review_archive).setVisible(!getTrial().isArchived());
            menu.findItem(R.id.action_run_review_unarchive).setVisible(getTrial().isArchived());
            menu.findItem(R.id.action_run_review_delete).setEnabled(getTrial().isArchived());

            menu.findItem(R.id.action_disable_auto_zoom).setVisible(
                    getTrial().getAutoZoomEnabled());
            menu.findItem(R.id.action_enable_auto_zoom).setVisible(
                    !getTrial().getAutoZoomEnabled());

            // You can only do a crop if the run length is long enough.
            menu.findItem(R.id.action_run_review_crop).setEnabled(
                    CropHelper.experimentIsLongEnoughForCrop(getTrial()));

            menu.findItem(R.id.action_export).setVisible(true);
        } else {
            menu.findItem(R.id.action_run_review_archive).setVisible(false);
            menu.findItem(R.id.action_run_review_unarchive).setVisible(false);
            menu.findItem(R.id.action_disable_auto_zoom).setVisible(false);
            menu.findItem(R.id.action_enable_auto_zoom).setVisible(false);
            menu.findItem(R.id.action_run_review_delete).setVisible(false);
            menu.findItem(R.id.action_run_review_crop).setVisible(false);
            menu.findItem(R.id.action_export).setVisible(false);
        }

        if (((RunReviewActivity) getActivity()).isFromRecord()) {
            // If this is from record, always enable deletion.
            menu.findItem(R.id.action_run_review_delete).setEnabled(true);
        }

        super.onPrepareOptionsMenu(menu);
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
            if (mExperiment != null) {
                // This should be the only one that matters, I think, but leaving the others
                // for potential legacy cases (b/66162829)
                upIntent.putExtra(PanesActivity.EXTRA_EXPERIMENT_ID, mExperimentId);

                upIntent.putExtra(ExperimentDetailsFragment.ARG_EXPERIMENT_ID, mExperimentId);
                upIntent.putExtra(ExperimentDetailsFragment.ARG_CREATE_TASK,
                        getArguments().getBoolean(ARG_CREATE_TASK, false));
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
            mGraphOptionsController.launchOptionsDialog(mScalarDisplayOptions,
                    new NewOptionsStorage.SnackbarFailureListener(getView()));
        } else if (id == R.id.action_export) {
            RxDataController.getExperimentById(getDataController(), mExperimentId)
                    .subscribe(experiment -> exportRun(experiment.getTrial(mTrialId)));
        } else if (id == R.id.action_run_review_crop) {
            if (mExperiment != null) {
                launchCrop(getView());
            }
        } else if (id == R.id.action_run_review_delete) {
            if (mExperiment != null) {
                deleteThisRun();
            }
        } else if (id == R.id.action_run_review_archive) {
            if (mExperiment != null) {
                setArchived(true);
            }
        } else if (id == R.id.action_run_review_unarchive) {
            if (mExperiment != null) {
                setArchived(false);
            }
        } else if (id == R.id.action_run_review_edit) {
            UpdateRunActivity.launch(getActivity(), mTrialId, mExperimentId);
        } else if (id == R.id.action_enable_auto_zoom) {
            if (mExperiment != null) {
                setAutoZoomEnabled(true);
            }
        } else if (id == R.id.action_disable_auto_zoom) {
            if (mExperiment != null) {
                setAutoZoomEnabled(false);
            }
        } else if (id == R.id.action_run_review_audio_settings) {
            launchAudioSettings();
        }
        return super.onOptionsItemSelected(item);
    }

    private void initializeData() {
        final DataController dc = getDataController();
        dc.getExperimentById(mExperimentId,
                new LoggingConsumer<Experiment>(TAG, "load experiment") {
                    @Override
                    public void success(Experiment experiment) {
                        if (experiment == null) {
                            // This experiment no longer exists, finish.
                            getActivity().finish();
                        }
                        mExperiment = experiment;
                        attachToRun(experiment.getTrial(mTrialId));
                        mPerfTracker.stopGlobalTimer(TrackerConstants.PRIMES_RUN_LOADED);
                        mPerfTracker.onAppInteractive();
                    }
                });
    }

    private void pausePlaybackForLifecycleEvent() {
        mAudioWasPlayingBeforePause = mAudioPlaybackController.isPlaying();
        mAudioPlaybackController.stopPlayback();
    }

    private void clearAudioPlaybackController() {
        mAudioPlaybackController.stopPlayback();
        mAudioPlaybackController.clearListener();
    }

    private void setAutoZoomEnabled(boolean enableAutoZoom) {
        getTrial().setAutoZoomEnabled(enableAutoZoom);
        getDataController().updateExperiment(mExperimentId,
                new LoggingConsumer<Success>(TAG, "update auto zoom") {
                    @Override
                    public void success(Success value) {
                        if (mCurrentSensorStats == null) {
                            AccessibilityUtils.makeSnackbar(getView(),
                                    getResources().getString(R.string.autozoom_failed),
                                    Snackbar.LENGTH_SHORT);
                        } else {
                            mChartController.clearReviewYAxis();
                            adjustYAxis();
                        }
                        if (getActivity() != null) {
                            getActivity().invalidateOptionsMenu();
                        }
                    }
        });
    }

    private void adjustYAxis() {
        if (mExperiment == null || mCurrentSensorStats == null ||
                mAudioPlaybackController == null) {
            return;
        }
        double yMin = mCurrentSensorStats.getStatValue(GoosciTrial.SensorStat.MINIMUM, 0);
        double yMax = mCurrentSensorStats.getStatValue(GoosciTrial.SensorStat.MAXIMUM, 0);
        if (getTrial().getAutoZoomEnabled()) {
            mChartController.setReviewYAxis(yMin, yMax, /* has buffer */ true);
        } else {
            GoosciSensorLayout.SensorLayout layout = getSensorLayout();
            // Don't zoom in more than the recorded data.
            // The layout's min/max y value may be too small to show the recorded data when
            // recording happened in the background and was stopped by a trigger.
            mChartController.setReviewYAxis(Math.min(layout.minimumYAxisValue, yMin),
                    Math.max(layout.maximumYAxisValue, yMax), /* no buffer */ false);
        }

        if (mPreviousYPair != null) {
            mChartController.setYAxis(mPreviousYPair.first, mPreviousYPair.second);
            mPreviousYPair = null;
        }

        mChartController.refreshChartView();
        // TODO: What happens when we zoom the Y axis while audio is playing?
        mAudioPlaybackController.setYAxisRange(mChartController.getRenderedYMin(),
                mChartController.getRenderedYMax());
        // Redraw the thumb after the chart is updated.
        mRunReviewOverlay.post(new Runnable() {
            public void run() {
                mRunReviewOverlay.onYAxisAdjusted();
            }
        });
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(KEY_SELECTED_SENSOR_INDEX, mSelectedSensorIndex);
        outState.putBoolean(KEY_TIMESTAMP_EDIT_UI_VISIBLE, getChildFragmentManager()
                .findFragmentByTag(EditLabelTimeDialog.TAG) != null);
        outState.putBoolean(KEY_TIMESTAMP_PICKER_UI_VISIBLE, getChildFragmentManager()
                .findFragmentByTag(EditTimestampDialog.TAG) != null);
        if (mSavedInstanceStateForLoad != null) {
            // We haven't finished loading the run from the database yet in onCreateView.
            // Go ahead and use the old savedInstanceState since we haven't reconstructed
            // everything yet.
            outState.putLong(KEY_EXTERNAL_AXIS_X_MINIMUM,
                    mSavedInstanceStateForLoad.getLong(KEY_EXTERNAL_AXIS_X_MINIMUM));
            outState.putLong(KEY_EXTERNAL_AXIS_X_MAXIMUM,
                    mSavedInstanceStateForLoad.getLong(KEY_EXTERNAL_AXIS_X_MAXIMUM));
            outState.putLong(KEY_RUN_REVIEW_OVERLAY_TIMESTAMP,
                    mSavedInstanceStateForLoad.getLong(KEY_RUN_REVIEW_OVERLAY_TIMESTAMP));
            outState.putBoolean(KEY_STATS_OVERLAY_VISIBLE,
                    mSavedInstanceStateForLoad.getBoolean(KEY_STATS_OVERLAY_VISIBLE));
            outState.putBoolean(KEY_AUDIO_PLAYBACK_ON,
                    mSavedInstanceStateForLoad.getBoolean(KEY_AUDIO_PLAYBACK_ON));
            outState.putBoolean(KEY_CROP_UI_VISIBLE,
                    mSavedInstanceStateForLoad.getBoolean(KEY_CROP_UI_VISIBLE));
            outState.putLong(KEY_CROP_START_TIMESTAMP,
                    mSavedInstanceStateForLoad.getLong(KEY_CROP_START_TIMESTAMP));
            outState.putLong(KEY_CROP_END_TIMESTAMP,
                    mSavedInstanceStateForLoad.getLong(KEY_CROP_END_TIMESTAMP));
            outState.putDouble(KEY_CHART_AXIS_Y_MAXIMUM,
                    mSavedInstanceStateForLoad.getDouble(KEY_CHART_AXIS_Y_MAXIMUM));
            outState.putDouble(KEY_CHART_AXIS_Y_MINIMUM,
                    mSavedInstanceStateForLoad.getDouble(KEY_CHART_AXIS_Y_MINIMUM));
        } else {
            outState.putLong(KEY_EXTERNAL_AXIS_X_MINIMUM, mExternalAxis.getXMin());
            outState.putLong(KEY_EXTERNAL_AXIS_X_MAXIMUM, mExternalAxis.getXMax());
            outState.putLong(KEY_RUN_REVIEW_OVERLAY_TIMESTAMP, mRunReviewOverlay.getTimestamp());
            outState.putBoolean(KEY_STATS_OVERLAY_VISIBLE, mShowStatsOverlay);
            outState.putBoolean(KEY_AUDIO_PLAYBACK_ON, mAudioPlaybackController.isPlaying() ||
                    mAudioWasPlayingBeforePause);
            outState.putBoolean(KEY_CROP_UI_VISIBLE, mRunReviewOverlay.getIsCropping());
            outState.putLong(KEY_CROP_START_TIMESTAMP, mRunReviewOverlay.getCropStartTimestamp());
            outState.putLong(KEY_CROP_END_TIMESTAMP, mRunReviewOverlay.getCropEndTimestamp());
            double yMax = mChartController.getRenderedYMax();
            double yMin = mChartController.getRenderedYMin();
            if (yMax > yMin) {
                outState.putDouble(KEY_CHART_AXIS_Y_MAXIMUM, yMax);
                outState.putDouble(KEY_CHART_AXIS_Y_MINIMUM, yMin);
            } else {
                Log.d(TAG, "not loaded");
            }
        }
    }

    private void attachToRun(final Trial trial) {
        if (getActivity() == null) {
            return;
        }

        // Create a BroadcastReceiver for when the stats get updated.
        mBroadcastReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String statsRunId = intent.getStringExtra(CropHelper.EXTRA_TRIAL_ID);
                if (TextUtils.equals(statsRunId, getTrial().getTrialId())) {
                    String statsSensorId = intent.getStringExtra(CropHelper.EXTRA_SENSOR_ID);
                    GoosciSensorLayout.SensorLayout sensorLayout = getSensorLayout();
                    if (TextUtils.equals(statsSensorId, sensorLayout.sensorId)) {
                        onStatsRefreshed(sensorLayout);
                    }
                }
            }
        };
        CropHelper.registerStatsBroadcastReceiver(getActivity().getApplicationContext(),
                mBroadcastReceiver);

        final View rootView = getView();
        if (rootView == null) {
            ((AppCompatActivity) getActivity()).supportStartPostponedEnterTransition();
            return;
        }
        final RecyclerView pinnedNoteList = (RecyclerView) rootView.findViewById(
                R.id.pinned_note_list);
        LinearLayoutManager layoutManager = new LinearLayoutManager(getActivity());
        layoutManager.setOrientation(LinearLayoutManager.VERTICAL);
        pinnedNoteList.setLayoutManager(layoutManager);

        mPinnedNoteAdapter = new PinnedNoteAdapter(trial, trial.getFirstTimestamp(),
                trial.getLastTimestamp(), mExperimentId);
        mPinnedNoteAdapter.setListItemModifyListener(new PinnedNoteAdapter.ListItemEditListener() {
            @Override
            public void onLabelEditTime(final Label item) {
                onEditNoteTimestamp(item);
            }

            @Override
            public void onLabelDelete(Label item) {
                deleteLabel(item);
            }

            @Override
            public void onCaptionEdit(String updatedCaption) {
                GoosciCaption.Caption caption = new GoosciCaption.Caption();
                caption.text = updatedCaption;
                caption.lastEditedTimestamp = System.currentTimeMillis();
                getTrial().setCaption(caption);
                getDataController().updateExperiment(mExperimentId, LoggingConsumer.expectSuccess(
                        TAG, "update caption"));
            }
        });

        mPinnedNoteAdapter.setListItemClickListener(new PinnedNoteAdapter.ListItemClickListener() {
            @Override
            public void onLabelClicked(Label item) {
                LabelDetailsActivity.launchFromRunReview(getActivity(), mExperimentId, mTrialId,
                        mSelectedSensorIndex, item, getArguments().getBoolean(ARG_CREATE_TASK),
                        getArguments().getBoolean(RunReviewActivity.EXTRA_FROM_RECORD));
            }

            @Override
            public void onAddLabelButtonClicked() {
                if (mExperiment != null && !mRunReviewOverlay.getIsCropping()) {
                    launchLabelAdd(null, Math.max(mRunReviewOverlay.getTimestamp(),
                            getTrial().getFirstTimestamp()));
                }
            }

            @Override
            public void onLabelTimestampClicked(Label item) {
                // TODO: Animate to the active timestamp.
                mRunReviewOverlay.setActiveTimestamp(item.getTimeStamp());
            }
        });

        pinnedNoteList.setAdapter(mPinnedNoteAdapter);

        // Re-enable appropriate menu options.
        getActivity().invalidateOptionsMenu();

        hookUpExperimentDetailsArea(trial, rootView);

        // Load the data for the first sensor only.
        if (mSavedInstanceStateForLoad != null) {
            if (mSavedInstanceStateForLoad.getBoolean(KEY_AUDIO_PLAYBACK_ON, false)) {
                mAudioPlaybackController.setYAxisRange(
                        mSavedInstanceStateForLoad.getDouble(KEY_CHART_AXIS_Y_MINIMUM),
                        mSavedInstanceStateForLoad.getDouble(KEY_CHART_AXIS_Y_MAXIMUM));
                mAudioPlaybackController.startPlayback(getDataController(),
                        getTrial().getFirstTimestamp(),
                        getTrial().getLastTimestamp(),
                        mSavedInstanceStateForLoad.getLong(KEY_RUN_REVIEW_OVERLAY_TIMESTAMP),
                        getSensorId());
            }
            // If this isn't the first time we've made a view, check if the timepicker UI is up.
            if (mSavedInstanceStateForLoad.getBoolean(KEY_TIMESTAMP_EDIT_UI_VISIBLE)) {
                rootView.findViewById(R.id.embedded).setVisibility(View.VISIBLE);
                setTimepickerUi(rootView, true);
            }
            // Also check if the timestamp picker UI is up.
            if (mSavedInstanceStateForLoad.getBoolean(KEY_TIMESTAMP_PICKER_UI_VISIBLE)) {
                setTimestampDialogListener(
                        (EditTimestampDialog) getChildFragmentManager().findFragmentByTag(
                                EditTimestampDialog.TAG));
            }
            mPreviousYPair = new Pair<>(
                    mSavedInstanceStateForLoad.getDouble(KEY_CHART_AXIS_Y_MINIMUM),
                    mSavedInstanceStateForLoad.getDouble(KEY_CHART_AXIS_Y_MAXIMUM));
        }
        setUpAxis(mSavedInstanceStateForLoad, rootView);
        mSavedInstanceStateForLoad = null;

        loadRunData(rootView);
        if (getActivity() != null) {
            ((AppCompatActivity) getActivity()).supportStartPostponedEnterTransition();
        }
    }

    private void deleteLabel(Label item) {
        // Delete the item immediately.
        Consumer<Context> assetDeleter =
                getTrial().deleteLabelAndReturnAssetDeleter(item, mExperimentId);
        RxDataController.updateExperiment(getDataController(), mExperiment)
                .subscribe(() -> onLabelDelete(new DeletedLabel(item, assetDeleter)));
    }

    private void onLabelDelete(DeletedLabel item) {
        item.deleteAndDisplayUndoBar(getView(), mExperimentId, getTrial(), () -> {
            mPinnedNoteAdapter.onLabelAdded(item.getLabel());
            mChartController.setLabels(getTrial().getLabels());
            WhistlePunkApplication.getUsageTracker(getActivity())
                                  .trackEvent(TrackerConstants.CATEGORY_NOTES,
                                          TrackerConstants.ACTION_DELETE_UNDO,
                                          TrackerConstants.LABEL_RUN_REVIEW,
                                          TrackerConstants.getLabelValueType(item.getLabel()));
        });

        mPinnedNoteAdapter.onLabelUpdated(item.getLabel());
        mChartController.setLabels(getTrial().getLabels());

        WhistlePunkApplication.getUsageTracker(getActivity())
                .trackEvent(TrackerConstants.CATEGORY_NOTES,
                        TrackerConstants.ACTION_DELETED,
                        TrackerConstants.LABEL_RUN_REVIEW,
                        TrackerConstants.getLabelValueType(item.getLabel()));
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
            cropStartTimestamp = savedInstanceStateForLoad.getLong(KEY_CROP_START_TIMESTAMP,
                    runFirstTimestamp);
            cropEndTimestamp = savedInstanceStateForLoad.getLong(KEY_CROP_END_TIMESTAMP,
                    runLastTimestamp);
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
            mRunReviewOverlay.setAllTimestamps(overlayTimestamp, cropStartTimestamp,
                    cropEndTimestamp);
            mExternalAxis.zoomTo(firstTimestamp, lastTimestamp);
        } else {
            mExternalAxis.setReviewData(runFirstTimestamp, runFirstTimestamp, reviewXMin,
                    reviewXMax);
            mRunReviewOverlay.setActiveTimestamp(overlayTimestamp);
            if (savedInstanceStateForLoad == null) {
                mExternalAxis.zoomTo(reviewXMin, reviewXMax);
            } else {
                // If we just cropped the run, the prev min and max will be too wide, so make sure
                // we clip to the current run size.
                long xMin = Math.max(reviewXMin, firstTimestamp);
                long xMax = Math.min(reviewXMax, lastTimestamp);
                mExternalAxis.zoomTo(xMin, xMax);
            }
        }
    }

    private void deleteThisRun() {
        DeleteMetadataItemDialog dialog = DeleteMetadataItemDialog.newInstance(
                R.string.delete_run_dialog_title, R.string.run_review_delete_confirm);
        dialog.show(getChildFragmentManager(), DeleteMetadataItemDialog.TAG);
    }

    private void setArchived(final boolean archived) {
        getTrial().setArchived(archived);
        getDataController().updateExperiment(mExperimentId, new LoggingConsumer<Success>(TAG,
                "Editing run archived state") {
            @Override
            public void success(Success value) {
                if (getActivity() == null) {
                    return;
                }
                Snackbar bar = AccessibilityUtils.makeSnackbar(getView(),
                        getActivity().getResources().getString(archived ?
                                R.string.archived_run_message : R.string.unarchived_run_message),
                        Snackbar.LENGTH_LONG);

                if (archived) {
                    bar.setAction(R.string.action_undo, new View.OnClickListener() {

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
                .trackEvent(TrackerConstants.CATEGORY_RUNS,
                        archived ? TrackerConstants.ACTION_ARCHIVE :
                                TrackerConstants.ACTION_UNARCHIVE,
                        null, 0);
    }

    private void loadRunData(final View rootView) {
        mAudioPlaybackController.stopPlayback();
        final GoosciSensorLayout.SensorLayout sensorLayout = getSensorLayout();
        populateSensorViews(rootView, sensorLayout);
        updateSwitchSensorArrows(rootView, getTrial().getSensorIds(),
                sensorLayout.sensorId);

        String sonificationType = getSonificationType(sensorLayout);
        mAudioPlaybackController.setSonificationType(sonificationType);
        mCurrentSensorStats = null;

        loadStatsAndChart(sensorLayout, (StatsList) rootView.findViewById(R.id.stats_drawer));
    }

    private void loadStatsAndChart(final GoosciSensorLayout.SensorLayout sensorLayout,
            final StatsList statsList) {
        final DataController dataController = getDataController();
        final ChartController.ChartLoadingStatus fragmentRef = this;
        TrialStats stats = getTrial().getStatsForSensor(sensorLayout.sensorId);
        populateStats(stats, statsList, sensorLayout);

        mChartController.loadRunData(getTrial(), sensorLayout, dataController,
                fragmentRef,
                stats, new ChartController.ChartDataLoadedCallback() {

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
                        mRunReviewOverlay.updateColor(getActivity().getResources().getIntArray(
                                R.array.graph_colors_array)[getSensorLayout().colorIndex]);
                        mRunReviewPlaybackButton.setVisibility(View.INVISIBLE);
                        mRunReviewOverlay.setVisibility(View.INVISIBLE);
                    }
                }, getActivity());
    }

    private void populateStats(TrialStats trialStats, StatsList statsList,
            GoosciSensorLayout.SensorLayout layout) {
        mCurrentSensorStats = trialStats;
        int color = getActivity().getResources().getIntArray(R.array.graph_colors_array)[
                layout.colorIndex];
        statsList.updateColor(color);
        if (!mCurrentSensorStats.statsAreValid()) {
            statsList.clearStats();
            mChartController.updateStats(Collections.<StreamStat>emptyList());
        } else {
            NumberFormat numberFormat = AppSingleton.getInstance(getActivity())
                    .getSensorAppearanceProvider().getAppearance(layout.sensorId).getNumberFormat();
            List<StreamStat> streamStats = new StatsAccumulator.StatsDisplay(numberFormat)
                    .updateStreamStats(trialStats);
            statsList.updateStats(streamStats);
            mChartController.updateStats(streamStats);
        }
    }

    private void onStatsRefreshed(final GoosciSensorLayout.SensorLayout sensorLayout) {
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

    private String getSonificationType(GoosciSensorLayout.SensorLayout sensorLayout) {
        return LocalSensorOptionsStorage.loadFromLayoutExtras(sensorLayout).getReadOnly().getString(
                ScalarDisplayOptions.PREFS_KEY_SONIFICATION_TYPE,
                SonificationTypeAdapterFactory.DEFAULT_SONIFICATION_TYPE);
    }

    @Override
    public void requestDelete(Bundle extras) {
        mExperiment.deleteTrial(getTrial(), getActivity());
        getDataController().updateExperiment(mExperimentId,
                new LoggingConsumer<Success>(TAG, "Deleting new trial") {
                    @Override
                    public void success(Success value) {
                        // Go back to the observe & record.
                        Intent intent = new Intent(getActivity(), RecordFragment.class);
                        NavUtils.navigateUpTo(getActivity(), intent);
                    }
                });
    }

    private void onDataLoaded() {
        // Add the labels after all the data is loaded
        // so that they are interpolated correctly.
        mChartController.setLabels(getTrial().getLabels());
        mChartController.setShowProgress(false);

        mExternalAxis.updateAxis();
        adjustYAxis();

        if (mChartController.hasData()) {
            // Show the replay play button
            mRunReviewPlaybackButton.setVisibility(View.VISIBLE);
            mRunReviewOverlay.setVisibility(View.VISIBLE);
        }
    }

    // TODO(saff): probably extract TrialPresenter
    private void hookUpExperimentDetailsArea(Trial trial, View rootView) {
        setArchivedUi(rootView, trial.isArchived());

        final TextView runTitle = (TextView) rootView.findViewById(R.id.run_title_text);
        runTitle.setText(trial.getTitle(rootView.getContext()));

        final RelativeTimeTextView runDetailsText = (RelativeTimeTextView) rootView.findViewById(
                R.id.run_details_text);
        runDetailsText.setTime(trial.getFirstTimestamp());

        final TextView durationText = (TextView) rootView.findViewById(R.id.run_review_duration);
        ElapsedTimeFormatter formatter = ElapsedTimeFormatter.getInstance(
                durationText.getContext());
        durationText.setText(formatter.format(
                trial.elapsedSeconds()));
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
    private void updateSwitchSensorArrows(final View rootView, final List<String> sensorIds,
            final String sensorId) {
        ImageButton prevButton = (ImageButton) rootView.findViewById(
                R.id.run_review_switch_sensor_btn_prev);
        ImageButton nextButton = (ImageButton) rootView.findViewById(
                R.id.run_review_switch_sensor_btn_next);

        final int position = sensorIds.indexOf(sensorId);
        boolean hasPrevButton = position > 0;
        boolean hasNextButton = position < sensorIds.size() - 1;

        prevButton.setVisibility(hasPrevButton ? View.VISIBLE : View.INVISIBLE);
        nextButton.setVisibility(hasNextButton ? View.VISIBLE : View.INVISIBLE);

        if (hasPrevButton) {
            prevButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    mSelectedSensorIndex = position - 1;
                    loadRunData(rootView);
                }
            });
            String prevSensorId = sensorIds.get(position - 1);
            updateContentDescription(prevButton, R.string.run_review_switch_sensor_btn_prev,
                    prevSensorId, getActivity());
        }
        if (hasNextButton) {
            nextButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    mSelectedSensorIndex = position + 1;
                    loadRunData(rootView);
                }
            });
            String nextSensorId = sensorIds.get(position + 1);
            updateContentDescription(nextButton, R.string.run_review_switch_sensor_btn_next,
                    nextSensorId, getActivity());
        }
    }

    public static void updateContentDescription(ImageButton button, int stringId, String sensorId,
                                                Context context) {
        SensorAppearance appearance = AppSingleton.getInstance(context)
                .getSensorAppearanceProvider()
                .getAppearance(sensorId);
        String content = context.getResources().getString(stringId, appearance.getName(context));
        button.setContentDescription(content);
    }

    private void populateSensorViews(View rootView, GoosciSensorLayout.SensorLayout sensorLayout) {
        final Context context = rootView.getContext();
        final SensorAppearance appearance = AppSingleton.getInstance(context)
                .getSensorAppearanceProvider().getAppearance(sensorLayout.sensorId);
        final TextView sensorNameText = (TextView) rootView.findViewById(
                R.id.run_review_sensor_name);
        sensorNameText.setText(Appearances.getSensorDisplayName(appearance, context));
        final ImageView sensorIconImage = (ImageView) rootView.findViewById(
                R.id.sensor_icon);
        Appearances.applyDrawableToImageView(appearance.getIconDrawable(context), sensorIconImage,
                context.getResources().getIntArray(
                        R.array.graph_colors_array)[sensorLayout.colorIndex]);
        mRunReviewOverlay.setUnits(appearance.getUnits(context));
    }

    private void launchLabelAdd(Label editedLabel, long timestamp) {
        String labelTimeText = PinnedNoteAdapter.getNoteTimeText(timestamp,
                getTrial().getFirstTimestamp());
        AddNoteDialog dialog = AddNoteDialog.newInstance(timestamp, getTrial().getTrialId(),
                mExperiment.getExperimentId(), R.string.add_note_hint_text,
                labelTimeText, editedLabel, PinnedNoteAdapter.getNoteTimeContentDescription(
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
                        mPinnedNoteAdapter.onLabelAdded(newLabel);
                        mChartController.setLabels(getTrial().getLabels());
                        WhistlePunkApplication.getUsageTracker(getActivity())
                                              .trackEvent(TrackerConstants.CATEGORY_NOTES,
                                                      TrackerConstants.ACTION_CREATE,
                                                      TrackerConstants.LABEL_RUN_REVIEW,
                                                      TrackerConstants.getLabelValueType(newLabel));
                    }
                };
            }

            @Override
            public void onAddNoteTimestampClicked(Label editedLabel, long selectedTimestamp) {
                AddNoteDialog addDialog = (AddNoteDialog) getChildFragmentManager()
                        .findFragmentByTag(AddNoteDialog.TAG);
                if (addDialog != null) {
                    addDialog.dismiss();
                }

                // Show the timestamp edit window below the graph / over the notes
                getView().findViewById(R.id.embedded).setVisibility(View.VISIBLE);
                EditLabelTimeDialog timeDialog =
                        EditLabelTimeDialog.newInstance(editedLabel, selectedTimestamp,
                                getTrial().getFirstTimestamp());
                FragmentTransaction ft = getChildFragmentManager().beginTransaction();
                ft.add(R.id.embedded, timeDialog, EditLabelTimeDialog.TAG);
                ft.commit();
                mRunReviewOverlay.setActiveTimestamp(selectedTimestamp);
                mRunReviewOverlay.setOnTimestampChangeListener(timeDialog);
                setTimepickerUi(getView(), true);
            }

            @Override
            public Single<String> whenExperimentId() {
                return Single.just(mExperimentId);
            }
        };
    }

    private Trial getTrial() {
        return mExperiment.getTrial(mTrialId);
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
                mPinnedNoteAdapter.onLabelUpdated(label);
                // The timestamp was edited, so also refresh the line graph presenter.
                mChartController.setLabels(getTrial().getLabels());
                WhistlePunkApplication.getUsageTracker(getActivity())
                        .trackEvent(TrackerConstants.CATEGORY_NOTES,
                                TrackerConstants.ACTION_EDITED,
                                TrackerConstants.LABEL_RUN_REVIEW,
                                TrackerConstants.getLabelValueType(label));
            }
        };
    }

    public void onEditNoteTimestamp(Label label) {
        // Show the timestamp edit window below the graph / over the notes
        getView().findViewById(R.id.embedded).setVisibility(View.VISIBLE);
        EditLabelTimeDialog timeDialog = EditLabelTimeDialog.newInstance(label,
                getTrial().getFirstTimestamp());
        FragmentTransaction ft = getChildFragmentManager().beginTransaction();
        ft.add(R.id.embedded, timeDialog, EditLabelTimeDialog.TAG);
        ft.commit();
        mRunReviewOverlay.setActiveTimestamp(label.getTimeStamp());
        mRunReviewOverlay.setOnTimestampChangeListener(timeDialog);
        setTimepickerUi(getView(), true);
    }

    private void setTimepickerUi(View rootView, boolean showTimepicker) {
        if (showTimepicker) {
            mActionMode = ((AppCompatActivity) getActivity()).startSupportActionMode(
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
                            if (mActionMode != null) {
                                mActionMode = null;
                                dismissEditTimeDialog();
                            }
                        }
            });
            EditLabelTimeDialog dialog = (EditLabelTimeDialog) getChildFragmentManager()
                    .findFragmentByTag(EditLabelTimeDialog.TAG);
            if (dialog != null) {
                mRunReviewOverlay.setActiveTimestamp(dialog.getCurrentTimestamp());
                mRunReviewOverlay.setOnTimestampChangeListener(dialog);
            }
        }
        setUiForActionMode(rootView, showTimepicker);
    }

    private void setCropUi(View rootView, boolean showCrop) {
        if (showCrop) {
            mActionMode = ((AppCompatActivity) getActivity()).startSupportActionMode(
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
                            if (mActionMode != null) {
                                mActionMode = null;
                                completeCrop();
                            }
                        }
                    });
        }
        setUiForActionMode(rootView, showCrop);
    }

    private void saveCrop(final ActionMode mode) {
        CropHelper helper = new CropHelper(getDataController());
        helper.cropTrial(getActivity().getApplicationContext(), mExperiment, mTrialId,
                mRunReviewOverlay.getCropStartTimestamp(), mRunReviewOverlay.getCropEndTimestamp(),
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
                        AccessibilityUtils.makeSnackbar(getView(),
                                getResources().getString(R.string.crop_completed_message),
                                Snackbar.LENGTH_SHORT).show();
                        hookUpExperimentDetailsArea(getTrial(), getView());
                        mPinnedNoteAdapter.updateRunTimestamps(getTrial().getFirstTimestamp(),
                                getTrial().getLastTimestamp());
                        if (mode != null) {
                            mode.finish();
                        }
                    }

                    @Override
                    public void onCropFailed(int errorId) {
                        if (getActivity() == null || isDetached()) {
                            return;
                        }
                        AccessibilityUtils.makeSnackbar(getView(), String.format(
                                getResources().getString(R.string.crop_failed_message),
                                getResources().getString(errorId)),
                                Snackbar.LENGTH_LONG).show();
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
            if (rootView.getResources().getConfiguration().orientation ==
                    Configuration.ORIENTATION_LANDSCAPE) {
                // TODO: Animate hiding this view?
                rootView.findViewById(R.id.stats_drawer).setVisibility(View.GONE);
            }

            // Collapse app bar layout as much as possible to bring the graph to the top.
            AppBarLayout appBarLayout = (AppBarLayout) rootView.findViewById(R.id.app_bar_layout);
            appBarLayout.setExpanded(false);
            setFrozen(rootView, true);

            rootView.findViewById(R.id.pinned_note_list).setImportantForAccessibility(
                    View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);

            // Show a grey overlay over the notes. Make it so users can cancel the dialog
            // by clicking in the grey overlay to simulate a normal dialog.
            View notesOverlay = rootView.findViewById(R.id.pinned_note_overlay);

            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
                // On Kitkat devices, the pinned note list is shown over the AppBarLayout
                // instead of under it. We can manually adjust the layout params to show it
                // in the right location and at the right size to cover just the pinned note
                // list.
                ViewGroup.MarginLayoutParams params = (CoordinatorLayout.LayoutParams)
                        rootView.findViewById(R.id.pinned_note_list).getLayoutParams();
                params.setMargins(0, 0, 0, 0);
                notesOverlay.setLayoutParams(params);
                notesOverlay.setPadding(0, 0, 0, 0);
            }
            notesOverlay.setOnTouchListener(new View.OnTouchListener() {
                @Override
                public boolean onTouch(View v, MotionEvent event) {
                    return true;
                }
            });

            notesOverlay.setVisibility(View.VISIBLE);
        } else {
            if (mActionMode != null) {
                mActionMode.finish();
            }
            setFrozen(rootView, false);
            rootView.findViewById(R.id.pinned_note_overlay).setVisibility(View.GONE);
            rootView.findViewById(R.id.embedded).setVisibility(View.GONE);
            rootView.findViewById(R.id.stats_drawer).setVisibility(View.VISIBLE);

            rootView.findViewById(R.id.pinned_note_list).setImportantForAccessibility(
                    View.IMPORTANT_FOR_ACCESSIBILITY_AUTO);
        }
    }

    private void dismissEditTimeDialog() {
        EditLabelTimeDialog dialog = (EditLabelTimeDialog) getChildFragmentManager()
                .findFragmentByTag(EditLabelTimeDialog.TAG);
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
        Trial trial = mExperiment.getTrial(mTrialId);
        trial.updateLabel(label);
        mExperiment.updateTrial(trial);
        RxDataController.updateExperiment(getDataController(), mExperiment)
                .subscribe(MaybeConsumers.toCompletableObserver(onLabelEdit(label)));
    }

    @Override
    public void onEditTimeDialogDismissedAdd(Label editedLabel, long selectedTimestamp) {
        setTimepickerUi(getView(), false);
        launchLabelAdd(editedLabel, selectedTimestamp);
    }

    private void launchAudioSettings() {
        mAudioPlaybackController.stopPlayback();

        List<GoosciSensorLayout.SensorLayout> sensorLayouts = getTrial().getSensorLayouts();
        int size = sensorLayouts.size();
        String[] sensorIds = new String[size];
        String[] sonificationTypes = new String[size];
        for (int i = 0; i < size; i++) {
            GoosciSensorLayout.SensorLayout layout = sensorLayouts.get(i);
            sensorIds[i] = layout.sensorId;
            sonificationTypes[i] = getSonificationType(layout);
        }
        AudioSettingsDialog dialog = AudioSettingsDialog.newInstance(sonificationTypes, sensorIds,
                mSelectedSensorIndex);
        dialog.show(getChildFragmentManager(), AudioSettingsDialog.TAG);
    }

    private void launchCrop(View rootView) {
        rootView.findViewById(R.id.run_review_playback_button_holder).setVisibility(View.GONE);
        mAudioPlaybackController.stopPlayback();

        long originalFirstTimestamp = getTrial().getOriginalFirstTimestamp();
        long lastTimestamp = getTrial().getOriginalLastTimestamp();
        // Load data even if it was previously cropped out of the graph
        mChartController.setShowOriginalRun(true);

        mRunReviewOverlay.resetCropTimestamps();
        mRunReviewOverlay.setCropModeOn(true);

        long buffer = ExternalAxisController.getReviewBuffer(originalFirstTimestamp, lastTimestamp);
        mExternalAxis.setReviewData(originalFirstTimestamp, getTrial().getFirstTimestamp(),
                originalFirstTimestamp - buffer, lastTimestamp + buffer);
        mExternalAxis.updateAxis();

        WhistlePunkApplication.getUsageTracker(getActivity()).trackEvent(
                TrackerConstants.CATEGORY_RUNS, TrackerConstants.ACTION_CROP_STARTED, "", 1);

        setCropUi(rootView, true);
    }

    public void completeCrop() {
        mChartController.setShowOriginalRun(false);
        setCropUi(getView(), false);
        getView().findViewById(R.id.run_review_playback_button_holder).setVisibility(View.VISIBLE);
        mRunReviewOverlay.setCropModeOn(false);

        // When we started cropping we may have changed the external axis review data. Reset that
        // now that the crop is not happening any more.
        long runFirstTimestamp = getTrial().getFirstTimestamp();
        long runLastTimestamp = getTrial().getLastTimestamp();
        long buffer = ExternalAxisController.getReviewBuffer(runFirstTimestamp, runLastTimestamp);
        long idealRenderedXMin = runFirstTimestamp - buffer;
        long idealRenderedXMax = runLastTimestamp + buffer;
        Bundle timestampBundle = new Bundle();

        long currentRenderedXMin = mExternalAxis.getXMin();
        long currentRenderedXMax = mExternalAxis.getXMax();

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
        timestampBundle.putLong(KEY_RUN_REVIEW_OVERLAY_TIMESTAMP, mRunReviewOverlay.getTimestamp());
        setUpAxis(timestampBundle, getView());

        // TODO: Better way to throw out cropped (or out of range) data besides reloading,
        // As most all the data needed is already loaded -- this is really being done to throw
        // away cropped data.
        loadRunData(getView());
    }

    public void openManualCropEditor(boolean isStartCrop) {
        EditTimestampDialog timestampDialog = EditTimestampDialog.newInstance(isStartCrop,
                getTrial().getOriginalFirstTimestamp(), getTrial().getOriginalLastTimestamp(),
                getTrial().getFirstTimestamp(),
                isStartCrop ? mRunReviewOverlay.getCropStartTimestamp() :
                        mRunReviewOverlay.getCropEndTimestamp());
        setTimestampDialogListener(timestampDialog);
        timestampDialog.show(getChildFragmentManager(), EditTimestampDialog.TAG);
    }

    private void setTimestampDialogListener(EditTimestampDialog timestampDialog) {
        timestampDialog.setOnPickerTimestampChangedListener(
                new TimestampPickerController.TimestampPickerListener() {
                    @Override
                    public int isValidTimestamp(long timestamp, boolean isStartCrop) {
                        // Is it too close to the other seekbar?
                        if (mRunReviewOverlay.isValidCropTimestamp(timestamp, isStartCrop)) {
                            return TimestampPickerController.NO_ERROR;
                        } else {
                            return R.string.timestamp_picker_crop_range_error;
                        }
                    }

                    @Override
                    public void onPickerTimestampChanged(long timestamp, boolean isStartCrop) {
                        // Zoom out to show the new timestamp if needed.
                        if (isStartCrop) {
                            if (timestamp < mExternalAxis.getXMin()) {
                                long buffer = ExternalAxisController.getReviewBuffer(timestamp,
                                        mExternalAxis.getXMax());
                                mExternalAxis.zoomTo(timestamp - buffer, mExternalAxis.getXMax());
                            }
                            mRunReviewOverlay.setCropTimestamps(timestamp,
                                    mRunReviewOverlay.getCropEndTimestamp());
                        } else {
                            if (timestamp > mExternalAxis.getXMax()) {
                                long buffer = ExternalAxisController.getReviewBuffer(
                                        mExternalAxis.getXMin(), timestamp);
                                mExternalAxis.zoomTo(mExternalAxis.getXMin(), timestamp + buffer);
                            }
                            mRunReviewOverlay.setCropTimestamps(
                                    mRunReviewOverlay.getCropStartTimestamp(), timestamp);
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
        mAudioPlaybackController.setSonificationType(newSonificationTypes[mSelectedSensorIndex]);

        // Save the new sonification types into their respective sensorLayouts.
        // Note that this uses the knowledge that the sensor ordering has not changed since
        // launchAudioSettings.
        List<GoosciSensorLayout.SensorLayout> layouts = getTrial().getSensorLayouts();
        int size = layouts.size();
        for (int i = 0; i < size; i++) {
            // Update the sonification setting in the extras for this layout.
            LocalSensorOptionsStorage storage = new LocalSensorOptionsStorage();
            storage.putAllExtras(layouts.get(i).extras);
            storage.load(LoggingConsumer.expectSuccess(TAG, "loading sensor layout")).put(
                    ScalarDisplayOptions.PREFS_KEY_SONIFICATION_TYPE, newSonificationTypes[i]);
            layouts.get(i).extras = storage.exportAsLayoutExtras();
        }

        // Save the updated layouts to the DB.
        getDataController().updateExperiment(mExperimentId,
                LoggingConsumer.<Success>expectSuccess(TAG, "updating audio settings"));
    }

    @Override
    public void onAudioSettingsCanceled(String[] originalSonificationTypes, String[] sensorIds) {
        // RunReview does not have audio preview.
    }

    private DataController getDataController() {
        return AppSingleton.getInstance(getActivity()).getDataController();
    }

    private void exportRun(final Trial trial) {
        ExportOptionsDialogFragment fragment = ExportOptionsDialogFragment.createOptionsDialog(
                mExperiment.getExperimentId(), trial.getTrialId());
        fragment.show(((AppCompatActivity) getActivity()).getSupportFragmentManager(), "export");
    }

    @Override
    public int getGraphLoadStatus() {
        return mLoadingStatus;
    }

    @Override
    public void setGraphLoadStatus(int graphLoadStatus) {
        mLoadingStatus = graphLoadStatus;
    }

    @Override
    public String getRunId() {
        return mTrialId;
    }

    @Override
    public String getSensorId() {
        return getSensorLayout().sensorId;
    }

    protected GoosciSensorLayout.SensorLayout getSensorLayout() {
        return getTrial().getSensorLayouts().get(mSelectedSensorIndex);
    }
}
