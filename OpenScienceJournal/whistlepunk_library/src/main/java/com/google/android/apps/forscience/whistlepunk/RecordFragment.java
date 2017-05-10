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

package com.google.android.apps.forscience.whistlepunk;

import android.Manifest;
import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.app.Activity;
import android.app.Fragment;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.annotation.NonNull;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.DefaultItemAnimator;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.animation.DecelerateInterpolator;
import android.widget.ImageButton;

import com.google.android.apps.forscience.ble.DeviceDiscoverer;
import com.google.android.apps.forscience.javalib.Consumer;
import com.google.android.apps.forscience.javalib.MaybeConsumer;
import com.google.android.apps.forscience.javalib.Success;
import com.google.android.apps.forscience.whistlepunk.analytics.TrackerConstants;
import com.google.android.apps.forscience.whistlepunk.data.GoosciSensorLayout;
import com.google.android.apps.forscience.whistlepunk.devicemanager.ConnectableSensor;
import com.google.android.apps.forscience.whistlepunk.devicemanager.ManageDevicesActivity;
import com.google.android.apps.forscience.whistlepunk.featurediscovery.FeatureDiscoveryProvider;
import com.google.android.apps.forscience.whistlepunk.filemetadata.Experiment;
import com.google.android.apps.forscience.whistlepunk.filemetadata.Label;
import com.google.android.apps.forscience.whistlepunk.filemetadata.PictureLabelValue;
import com.google.android.apps.forscience.whistlepunk.filemetadata.SensorTrigger;
import com.google.android.apps.forscience.whistlepunk.filemetadata.Trial;
import com.google.android.apps.forscience.whistlepunk.metadata.ExperimentRun;
import com.google.android.apps.forscience.whistlepunk.metadata.ExperimentSensors;
import com.google.android.apps.forscience.whistlepunk.metadata.GoosciLabelValue;
import com.google.android.apps.forscience.whistlepunk.metadata.GoosciSensorTriggerInformation
        .TriggerInformation;
import com.google.android.apps.forscience.whistlepunk.project.experiment.ExperimentDetailsActivity;
import com.google.android.apps.forscience.whistlepunk.scalarchart.GraphOptionsController;
import com.google.android.apps.forscience.whistlepunk.scalarchart.ScalarDisplayOptions;
import com.google.android.apps.forscience.whistlepunk.sensorapi.DataViewOptions;
import com.google.android.apps.forscience.whistlepunk.sensorapi.NewOptionsStorage;
import com.google.android.apps.forscience.whistlepunk.sensorapi.ReadableSensorOptions;
import com.google.android.apps.forscience.whistlepunk.sensorapi.SensorChoice;
import com.google.android.apps.forscience.whistlepunk.sensorapi.SensorEnvironment;
import com.google.android.apps.forscience.whistlepunk.sensorapi.SensorPresenter;
import com.google.android.apps.forscience.whistlepunk.sensorapi.SensorStatusListener;
import com.google.android.apps.forscience.whistlepunk.sensorapi.StreamStat;
import com.google.android.apps.forscience.whistlepunk.sensorapi.WriteableSensorOptions;
import com.google.android.apps.forscience.whistlepunk.sensors.DecibelSensor;
import com.google.android.apps.forscience.whistlepunk.wireapi.RecordingMetadata;
import com.google.common.base.Preconditions;
import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;

import java.io.File;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import io.reactivex.Maybe;
import io.reactivex.functions.Function;
import io.reactivex.subjects.BehaviorSubject;
import io.reactivex.subjects.MaybeSubject;

public class RecordFragment extends Fragment implements AddNoteDialog.ListenerProvider,
        Handler.Callback, StopRecordingNoDataDialog.StopRecordingDialogListener,
        AudioSettingsDialog.AudioSettingsDialogListener {
    private static final String TAG = "RecordFragment";

    private static final String KEY_SAVED_ACTIVE_SENSOR_CARD = "savedActiveCardIndex";
    private static final String KEY_SAVED_RECYCLER_LAYOUT = "savedRecyclerLayout";
    private static final String KEY_SHOW_SNAPSHOT = "showSnapshot";

    private static final int DEFAULT_CARD_VIEW = GoosciSensorLayout.SensorLayout.METER;
    private static final boolean DEFAULT_AUDIO_ENABLED = false;
    private static final boolean DEFAULT_SHOW_STATS_OVERLAY = false;

    // TODO: this is never written.  Remove this logic
    private static final String EXTRA_SENSOR_IDS = "sensorIds";

    private static final int MSG_SHOW_FEATURE_DISCOVERY = 111;



    public static abstract class UICallbacks {
        public static UICallbacks NULL = new UICallbacks() {};

        /**
         * Called when an experiment is selected
         *
         * @param selectedExperiment the experiment that has been selected
         */
        void onSelectedExperimentChanged(Experiment selectedExperiment) {

        }

        /**
         * Called when recording starts
         *
         * @param experimentName the name of the experiment we're recording in.
         */
        void onRecordingStart(String experimentName) {

        }

        /**
         * Called when we first know that we're supposed to stop recording.  This allows us, for
         * example, to know not to allow additional recordings to start if we plan to display
         * a full-screen review once the recording is saved.
         */
        void onRecordStopRequested() {

        }

        /**
         * Called when recording actually stops.  Updates the UI to remove "recording" markers
         */
        void onRecordingStopped() {

        }

        /**
         * Called when a trial is fully saved and assigned a runId, so that we can update the UI
         * accordingly
         */
        void onRecordingSaved(String runId, Experiment experiment) {

        }

        /**
         * Called when a label is added from the RecordFragment (for example, a snapshot)
         * @param label
         */
        public void onLabelAdded(Label label) {

        }
    }

    public interface CallbacksProvider {
        UICallbacks getRecordFragmentCallbacks();
    }

    private UICallbacks mUICallbacks = UICallbacks.NULL;
    private SensorRegistry mSensorRegistry;
    private Snackbar mVisibleSnackbar;

    private ImageButton mAddButton;
    private ImageButton mRecordButton;

    private SensorSettingsController mSensorSettingsController;
    private GraphOptionsController mGraphOptionsController;
    private ColorAllocator mColorAllocator;
    private ScalarDisplayOptions mScalarDisplayOptions;
    private int mInitialActiveCardIndex = -1;

    private LinearLayoutManager mSensorCardLayoutManager;
    private RecyclerView mSensorCardRecyclerView;
    private SensorCardAdapter mSensorCardAdapter;
    private ExternalAxisController mExternalAxis;
    private ViewTreeObserver.OnGlobalLayoutListener mRecyclerViewGlobalLayoutListener;
    private View mBottomPanel;
    // Stores the rect of the panel.
    private Rect mPanelRect = new Rect();

    private Experiment mSelectedExperiment;
    private BehaviorSubject<Experiment> mSelectedExperimentSubject = BehaviorSubject.create();

    // A temporary variable to store a sensor card presenter that wants to use
    // the decibel sensor before the permission to use microphone is granted
    // in Android M.
    private SensorCardPresenter mDecibelSensorCardPresenter;

    private Handler mHandler;
    private FeatureDiscoveryProvider mFeatureDiscoveryProvider;
    private RecordingMetadata mCurrentRecording;

    /**
     * Most recent result from {@link RecorderController#pauseObservingAll()}, which must be passed
     * when we resume
     */
    private String mRecorderPauseId;
    private boolean mRecordingWasCanceled;
    private Set<String> mExcludedSensorIds = Sets.newHashSet();
    private int mRecorderStateListenerId = RecorderController.NO_LISTENER_ID;
    private int mTriggerFiredListenerId = RecorderController.NO_LISTENER_ID;

    public static RecordFragment newInstance(boolean showSnapshot) {
        RecordFragment fragment = new RecordFragment();
        Bundle args = new Bundle();
        args.putBoolean(KEY_SHOW_SNAPSHOT, showSnapshot);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onDetach() {
        mUICallbacks = UICallbacks.NULL;
        super.onDetach();
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (savedInstanceState != null) {
            mInitialActiveCardIndex = savedInstanceState.getInt(KEY_SAVED_ACTIVE_SENSOR_CARD, -1);
        }

        mColorAllocator = new ColorAllocator(getResources().getIntArray(
                R.array.graph_colors_array));
        mSensorRegistry = AppSingleton.getInstance(getActivity()).getSensorRegistry();
        mScalarDisplayOptions = new ScalarDisplayOptions();

        mHandler = new Handler(this);
        mFeatureDiscoveryProvider = WhistlePunkApplication.getFeatureDiscoveryProvider(
                getActivity());

        setHasOptionsMenu(true);

        // TODO: this is necessary because of a race condition: the BluetoothSensor assumes
        // that the BleService has already been connected before it tries to start observing.
        AppSingleton.getInstance(getActivity()).getBleClient();
    }

    public void audioPermissionGranted(boolean granted) {
        if (mDecibelSensorCardPresenter == null) {
            return;
        }
        if (granted) {
            startObserving(DecibelSensor.ID, mDecibelSensorCardPresenter);
        } else {
            // If the sensor can't be loaded, still show it as selected on the card
            // so the user understands that they wanted this sensor but can't use it.
            mDecibelSensorCardPresenter.setConnectingUI(DecibelSensor.ID, true,
                    getActivity().getApplicationContext(), true);
        }
        // in either case, we have our answer.  Stop waiting for it.
        mDecibelSensorCardPresenter = null;
        updateAvailableSensors();
    }

    private SensorAppearanceProvider getSensorAppearanceProvider() {
        return AppSingleton.getInstance(getActivity()).getSensorAppearanceProvider();
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        if (mSensorCardAdapter == null) {
            return;
        }
        List<SensorCardPresenter> presenters = mSensorCardAdapter.getSensorCardPresenters();

        int size = presenters.size();
        for (int i = 0; i < size; i++) {
            SensorCardPresenter presenter = presenters.get(i);
            if (presenter.isActive()) {
                outState.putInt(KEY_SAVED_ACTIVE_SENSOR_CARD, i);
            }
        }

        outState.putParcelable(KEY_SAVED_RECYCLER_LAYOUT,
                mSensorCardLayoutManager.onSaveInstanceState());
    }

    @Override
    public void onPause() {
        saveCurrentExperiment();
        // TODO: can we safely use onStop to shut down observing on pre-Nougat?
        //       See discussion at b/34368790
        if (!isMultiWindowEnabled()) {
            stopUI();
        }
        super.onPause();
    }

    @Override
    public void onStop() {
        if (isMultiWindowEnabled()) {
            stopUI();
        }
        super.onStop();
    }

    private void stopUI() {
        mExternalAxis.onPauseLiveAxis();
        cancelFeatureDiscovery();
        if (mSensorCardAdapter != null) {
            mSensorCardAdapter.onPause();
        }
        getMetadataController().removeExperimentChangeListener(TAG);
        withRecorderController(new Consumer<RecorderController>() {
            @Override
            public void take(RecorderController rc) {
                mRecorderPauseId = rc.pauseObservingAll();
                if (mRecorderStateListenerId != RecorderController.NO_LISTENER_ID) {
                    rc.removeRecordingStateListener(mRecorderStateListenerId);
                    mRecorderStateListenerId = RecorderController.NO_LISTENER_ID;
                }
                if (mTriggerFiredListenerId != RecorderController.NO_LISTENER_ID) {
                    rc.removeTriggerFiredListener(mTriggerFiredListenerId);
                    mTriggerFiredListenerId = RecorderController.NO_LISTENER_ID;
                }
            }
        });
        AppSingleton.getInstance(getActivity()).removeListeners(TAG);
        freezeLayouts();
    }

    private boolean isRecording() {
        return mCurrentRecording != null;
    }

    private String getCurrentRunId() {
        return isRecording() ? mCurrentRecording.getRunId() :
                RecorderController.NOT_RECORDING_RUN_ID;
    }

    @Override
    public void onResume() {
        super.onResume();
        if (!isMultiWindowEnabled()) {
            startUI();
        }

        // Reload sensor appearances in case they have changed while away from this fragment,
        getSensorAppearanceProvider().loadAppearances(
                LoggingConsumer.<Success>expectSuccess(TAG, "Load appearances"));
    }

    @Override
    public void onStart() {
        super.onStart();

        if (isMultiWindowEnabled()) {
            startUI();
        }
    }

    private boolean isMultiWindowEnabled() {
        return MultiWindowUtils.isMultiWindowEnabled(getActivity());
    }

    private void startUI() {
        setControlButtonsEnabled(false);
        mExternalAxis.onResumeLiveAxis();

        withRecorderController(new Consumer<RecorderController>() {
            @Override
            public void take(final RecorderController rc) {
                RecorderController.RecordingStateListener listener =
                        new RecorderController.RecordingStateListener() {
                            @Override
                            public void onRecordingStateChanged(
                                    RecordingMetadata currentRecording) {
                                RecordingMetadata prevRecording = mCurrentRecording;

                                mCurrentRecording = currentRecording;
                                onRecordingMetadataUpdated();
                                // If we have switched from a recording state to a not-recording
                                // state, update the UI.
                                if (prevRecording != null && !isRecording()) {
                                    mExternalAxis.onStopRecording();
                                    AddNoteDialog dialog = (AddNoteDialog) getChildFragmentManager()
                                            .findFragmentByTag(AddNoteDialog.TAG);
                                    if (dialog != null) {
                                        dialog.dismiss();
                                    }
                                    if (!mRecordingWasCanceled) {
                                        mUICallbacks.onRecordingSaved(prevRecording.getRunId(),
                                                mSelectedExperiment);
                                    }
                                }
                                mRecordingWasCanceled = false;
                            }

                            @Override
                            public void onRecordingStartFailed(
                                    @RecorderController.RecordingStartErrorType int errorType,
                                    Exception e) {
                                if (errorType == RecorderController.ERROR_START_FAILED) {
                                    failedStartRecording(R.string.recording_start_failed);
                                } else if (errorType ==
                                           RecorderController.ERROR_START_FAILED_DISCONNECTED) {
                                    failedStartRecording(
                                            R.string.recording_start_failed_disconnected);
                                }
                                updateRecordingUIState();
                            }

                            @Override
                            public void onRecordingStopFailed(
                                    @RecorderController.RecordingStopErrorType int errorType) {
                                if (errorType
                                    == RecorderController.ERROR_STOP_FAILED_DISCONNECTED) {
                                    failedStopRecording(
                                            R.string.recording_stop_failed_disconnected);
                                } else if (errorType
                                           == RecorderController.ERROR_STOP_FAILED_NO_DATA) {
                                    failedStopRecording(R.string.recording_stop_failed_no_data);
                                } else if (errorType
                                           == RecorderController.ERROR_FAILED_SAVE_RECORDING) {
                                    AccessibilityUtils.makeSnackbar(getView(),
                                            getActivity().getResources().getString(
                                                    R.string.recording_stop_failed_save),
                                            Snackbar.LENGTH_LONG).show();
                                }
                                updateRecordingUIState();
                            }
                        };
                mRecorderStateListenerId = rc.addRecordingStateListener(listener);

                RecorderController.TriggerFiredListener tlistener =
                        new RecorderController.TriggerFiredListener() {
                            @Override
                            public void onTriggerFired(SensorTrigger trigger) {
                                doVisualAlert(trigger);
                            }

                            @Override
                            public void onRequestStartRecording() {
                                lockUiForRecording();
                            }

                            @Override
                            public void onLabelAdded(Label label) {
                                processAddedLabel(label);
                            }

                            @Override
                            public void onRequestStopRecording(RecorderController rc) {
                            }
                        };
                mTriggerFiredListenerId = rc.addTriggerFiredListener(tlistener);

                if (!rc.resumeObservingAll(mRecorderPauseId)) {
                    // Force a reload of the current experiment's ob
                    mSelectedExperiment = null;
                    if (mSelectedExperimentSubject.hasValue()) {
                        mSelectedExperimentSubject.onComplete();
                        mSelectedExperimentSubject = BehaviorSubject.create();
                    }
                }

                getMetadataController().addExperimentChangeListener(TAG,
                        new MetadataController.MetadataChangeListener() {
                            @Override
                            public void onMetadataChanged(Experiment selectedExperiment) {
                                if (!readSensorsFromExtras(rc)) {
                                    // By spec, newExperiments should always be non-zero
                                    onSelectedExperimentChanged(selectedExperiment, rc);
                                }
                                mUICallbacks.onSelectedExperimentChanged(mSelectedExperiment);

                                // The recording UI shows the current experiment in the toolbar,
                                // so it cannot be set up until experiments are loaded.
                                onRecordingMetadataUpdated();
                            }
                        });
            }

            public boolean readSensorsFromExtras(RecorderController rc) {
                Bundle extras = getActivity().getIntent().getExtras();
                if (extras == null) {
                    return false;
                }
                String sensorIds = extras.getString(EXTRA_SENSOR_IDS, "");
                if (TextUtils.isEmpty(sensorIds)) {
                    return false;
                }

                setSensorPresenters(CommandLineSpecs.buildLayouts(sensorIds, RecordFragment.this.getResources()), rc);
                return true;
            }
        });
        WhistlePunkApplication.getUsageTracker(getActivity()).trackScreenView(
                TrackerConstants.SCREEN_OBSERVE_RECORD);
    }

    @Override
    public void onDestroyView() {
        // TODO: extract presenter with lifespan identical to the views.
        mRecordButton = null;
        if (mSensorCardAdapter != null) {
            mSensorCardAdapter.onDestroy();
            mSensorCardAdapter = null;
        }

        if (mExternalAxis != null) {
            mExternalAxis.destroy();
        }
        super.onDestroyView();
    }

    private void freezeLayouts() {
        if (mSelectedExperiment == null) {
            return;
        }
        final List<GoosciSensorLayout.SensorLayout> layouts = saveCurrentExperiment();
        Preconditions.checkNotNull(saveCurrentExperiment());
        // Freeze layouts to be saved if recording finishes
        withRecorderController(new Consumer<RecorderController>() {
            @Override
            public void take(RecorderController recorderController) {
                recorderController.setLayoutSupplier(Suppliers.ofInstance(layouts));
            }
        });
    }

    @Override
    public void onDestroy() {
        if (!isRecording()) {
            AppSingleton.getInstance(getActivity()).destroyBleClient();
        }
        stopObservingCurrentSensors();
        if (mVisibleSnackbar != null) {
            mVisibleSnackbar.dismiss();
        }
        mHandler = null;
        mSensorSettingsController = null;
        mGraphOptionsController = null;
        if (mSensorRegistry != null) {
            mSensorRegistry.removePendingOperations(TAG);
        }
        super.onDestroy();
    }

    public void stopObservingCurrentSensors() {
        if (mSensorCardAdapter != null) {
            for (SensorCardPresenter presenter : mSensorCardAdapter.getSensorCardPresenters()) {
                presenter.stopObserving();
            }
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        final ViewGroup rootView = (ViewGroup) inflater.inflate(R.layout.fragment_record, container,
                false);

        View resetButton = rootView.findViewById(R.id.btn_reset);
        ExternalAxisView axisView = (ExternalAxisView) rootView.findViewById(R.id.external_x_axis);
        mExternalAxis = new ExternalAxisController(axisView,
                new ExternalAxisController.AxisUpdateListener() {
                    @Override
                    public void onAxisUpdated(long xMin, long xMax, boolean isPinnedToNow) {
                        if (mSensorCardAdapter == null) {
                            return;
                        }
                        List<SensorCardPresenter> sensorCardPresenters =
                                mSensorCardAdapter.getSensorCardPresenters();
                        for (SensorCardPresenter sensorCardPresenter : sensorCardPresenters) {
                            SensorPresenter presenter = sensorCardPresenter.getSensorPresenter();
                            if (presenter != null) {
                                presenter.onGlobalXAxisChanged(xMin, xMax, isPinnedToNow,
                                        getDataController());
                            }
                        }
                    }
                }, /* IsLive */ true, new CurrentTimeClock(), resetButton);

        mAddButton = (ImageButton) rootView.findViewById(R.id.btn_add);
        mAddButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Save the timestamp for the note, but show the user a UI to create it:
                // Can't create the note yet as we don't know ahead of time if this is a picture
                // or text note.
                launchLabelAdd(getNow());
            }
        });

        mRecordButton = (ImageButton) rootView.findViewById(R.id.btn_record);

        // Hide the record button until we have a RecorderController instance it can use.
        mRecordButton.setVisibility(View.INVISIBLE);
        withRecorderController(new Consumer<RecorderController>() {
            @Override
            public void take(final RecorderController rc) {
                mRecordButton.setVisibility(View.VISIBLE);
                mRecordButton.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        // Disable the record button to stop double-clicks.
                        mRecordButton.setEnabled(false);
                        if (isRecording()) {
                            tryStopRecording(rc);
                        } else {
                            lockUiForRecording();
                            tryStartRecording(rc);
                        }
                    }
                });
            }
        });

        View snapshotButton = rootView.findViewById(R.id.snapshot_button);
        snapshotButton.setVisibility(getShowSnapshot() ? View.VISIBLE : View.GONE);
        snapshotButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                takeSnapshot();
            }
        });

        mBottomPanel = rootView.findViewById(R.id.bottom_panel);

        mGraphOptionsController.loadIntoScalarDisplayOptions(mScalarDisplayOptions, getView());
        mSensorCardLayoutManager = new LinearLayoutManager(getActivity());

        if (savedInstanceState != null) {
            mSensorCardLayoutManager.onRestoreInstanceState(
                    savedInstanceState.getParcelable(KEY_SAVED_RECYCLER_LAYOUT));
        }

        return rootView;
    }

    private boolean getShowSnapshot() {
        return getArguments().getBoolean(KEY_SHOW_SNAPSHOT, false);
    }

    private void activateSensorCardPresenter(SensorCardPresenter sensorCardPresenter,
            boolean setActive) {
        sensorCardPresenter.setActive(setActive, /* force */ false);
        if (setActive) {
            for (SensorCardPresenter other : mSensorCardAdapter.getSensorCardPresenters()) {
                if (!other.equals(sensorCardPresenter)) {
                    other.setActive(false, /* force */ false);
                }
            }
        }
    }

    private MetadataController getMetadataController() {
        return AppSingleton.getInstance(getActivity()).getMetadataController();
    }

    private void onSelectedExperimentChanged(final Experiment selectedExperiment,
            final RecorderController rc) {
        if (!TextUtils.equals(Experiment.getExperimentId(selectedExperiment),
                Experiment.getExperimentId(mSelectedExperiment))) {
            saveCurrentExperiment();
        }
        stopObservingCurrentSensors();
        mSelectedExperiment = selectedExperiment;
        mSelectedExperimentSubject.onNext(selectedExperiment);
        loadIncludedSensors(mSelectedExperiment.getSensorLayouts(), rc);
        rc.setSelectedExperiment(mSelectedExperiment);
        setControlButtonsEnabled(true);
    }

    private void updateSensorLayout(GoosciSensorLayout.SensorLayout sensorLayout) {
        if (mSelectedExperiment == null) {
            return;
        }
        int position = getPositionOfLayout(sensorLayout);
        if (position < 0) {
            return;
        }
        mSelectedExperiment.updateSensorLayout(position, sensorLayout);
        // TODO: Is there a way to do this write less frequently?
        getDataController().updateExperiment(mSelectedExperiment.getExperimentId(),
                LoggingConsumer.<Success>expectSuccess(TAG, "saving layout"));
    }

    // TODO: Can we optimize this by calling it less frequently?
    private List<GoosciSensorLayout.SensorLayout> saveCurrentExperiment() {
        if (mSelectedExperiment == null) {
            return null;
        }
        final List<GoosciSensorLayout.SensorLayout> layouts = buildCurrentLayouts();
        if (layouts != null) {
            mSelectedExperiment.setSensorLayouts(layouts);
            getDataController().updateExperiment(mSelectedExperiment.getExperimentId(),
                    LoggingConsumer.<Success>expectSuccess(TAG, "saving layouts"));
        }
        return layouts;
    }

    private List<GoosciSensorLayout.SensorLayout> buildCurrentLayouts() {
        if (mSensorCardAdapter == null) {
            return null;
        }

        return mSensorCardAdapter.buildLayouts();
    }

    private void setSensorPresenters(List<GoosciSensorLayout.SensorLayout> layouts,
            RecorderController rc) {
        if (layouts == null || layouts.size() == 0) {
            layouts = Lists.newArrayList(defaultLayout());
        }
        int maxNumSources = getAllIncludedSources().size();

        while (layouts.size() > maxNumSources) {
            // A sensor must have been removed.  The initializeSensorSelection calls below will
            // make sure to distribute the available sensors among the available cards, as long
            // as there's enough cards to go around. Start by searching the layouts list for a
            // layout that has an empty string sensorId, which points to a layout that previously
            // had a bluetooth sensor that was "forgotten".
            int indexToRemove = layouts.size() - 1;
            for (int i = 0; i < layouts.size(); i++) {
                if (TextUtils.isEmpty(layouts.get(i).sensorId)) {
                    indexToRemove = i;
                    break;
                }
            }
            layouts.remove(indexToRemove);
        }

        ViewGroup view = (ViewGroup) getView();
        if (view != null) {
            createSensorCardPresenters(view, layouts, rc);
            updateAvailableSensors();
            for (SensorCardPresenter presenter : mSensorCardAdapter.getSensorCardPresenters()) {
                presenter.initializeSensorSelection();
                // If we resume while recording, assume that the sensors are already connected.
                if (isRecording()) {
                    presenter.onSourceStatusUpdate(presenter.getSelectedSensorId(),
                            SensorStatusListener.STATUS_CONNECTED);
                }
            }
        }
    }

    public static GoosciSensorLayout.SensorLayout defaultLayout() {
        GoosciSensorLayout.SensorLayout layout = new GoosciSensorLayout.SensorLayout();
        layout.sensorId = null;
        layout.cardView = DEFAULT_CARD_VIEW;
        layout.audioEnabled = DEFAULT_AUDIO_ENABLED;
        layout.showStatsOverlay = DEFAULT_SHOW_STATS_OVERLAY;
        return layout;
    }

    // TODO: can all of this logic live in a separate, testable class?
    private void createSensorCardPresenters(ViewGroup rootView,
            List<GoosciSensorLayout.SensorLayout> layouts, final RecorderController rc) {
        List<SensorCardPresenter> sensorCardPresenters = new ArrayList<>();

        if (layouts.isEmpty()) {
            layouts = Lists.newArrayList(defaultLayout());
        }
        // Create a sensorData card for each initial source tag, or at minimum one if no source
        // tags are saved in the bundle.
        for (int i = 0; i < layouts.size(); i++) {
            GoosciSensorLayout.SensorLayout layout = layouts.get(i);
            final SensorCardPresenter sensorCardPresenter = createSensorCardPresenter(layout, rc);
            sensorCardPresenter.setInitialSourceTagToSelect(layout.sensorId);
            sensorCardPresenters.add(sensorCardPresenter);
            tryStartObserving(sensorCardPresenter, layout.sensorId, false);
        }

        int activeCardIndex = 0;
        if (mInitialActiveCardIndex != -1 &&
                mInitialActiveCardIndex < sensorCardPresenters.size()) {
            activeCardIndex = mInitialActiveCardIndex;
        }

        sensorCardPresenters.get(activeCardIndex).setActive(true, /** force UI updates */true);

        if (mSensorCardAdapter != null) {
            mSensorCardAdapter.onDestroy();
        }

        mSensorCardAdapter = new SensorCardAdapter(sensorCardPresenters,
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        int numAvailableSources = getAvailableSources().size();
                        if (numAvailableSources != 0) {
                            SensorCardPresenter sensorCardPresenter = createSensorCardPresenter(
                                    defaultLayout(), rc);
                            sensorCardPresenter.setActive(true, /** force UI updates */true);
                            mSensorCardAdapter.addSensorCardPresenter(sensorCardPresenter);
                            updateAvailableSensors();
                            sensorCardPresenter.initializeSensorSelection();
                            if (!mSensorCardAdapter.canAddMoreCards()) {
                                // If only one source is left, this is the final card. Because the
                                // add button will be hidden, we are actually scrolled to this
                                // positionalready and no scrolling will happen without forcing a
                                // scroll using scrollToPositionWithOffset.
                                mSensorCardLayoutManager.scrollToPositionWithOffset(
                                        mSensorCardAdapter.getItemCount() - 1, 1);
                            } else {
                                mSensorCardLayoutManager.scrollToPosition(
                                        mSensorCardAdapter.getItemCount() - 1);
                            }
                            activateSensorCardPresenter(sensorCardPresenter, true);
                            saveCurrentExperiment();
                        }
                    }
                }, new SensorCardAdapter.CardRemovedListener() {
                    @Override
                    public void onCardRemoved(SensorCardPresenter sensorCardPresenter) {
                        sensorCardPresenter.stopObserving();
                        updateAvailableSensors();
                    }
                }, new SensorCardAdapter.SensorCardHeaderToggleListener() {
                    @Override
                    public void onToggleSensorHeader(SensorCardPresenter sensorCardPresenter) {
                        activateSensorCardPresenter(sensorCardPresenter,
                                !sensorCardPresenter.isActive());
                    }
        });

        rc.setLayoutSupplier(new Supplier<List<GoosciSensorLayout.SensorLayout>>() {
            @Override
            public List<GoosciSensorLayout.SensorLayout> get() {
                return buildCurrentLayouts();
            }
        });

        updateSensorCount();
        mSensorCardAdapter.setRecording(isRecording(), getRecordingStartTime());
        long resetTime = mExternalAxis.resetAxes();
        if (mSensorCardAdapter != null) {
            long ignoreDataBefore = isRecording() ? -1 : resetTime;
            mSensorCardAdapter.onResume(ignoreDataBefore);
        }

        refreshLabels();

        mSensorCardRecyclerView =
                (RecyclerView) rootView.findViewById(R.id.sensor_card_recycler_view);
        mSensorCardRecyclerView.setLayoutManager(mSensorCardLayoutManager);
        mSensorCardRecyclerView.setAdapter(mSensorCardAdapter);
        mSensorCardRecyclerView.setItemAnimator(new DefaultItemAnimator() {

            @Override
            public void onMoveFinished(RecyclerView.ViewHolder item) {
                adjustSensorCardAddAlpha();
            }
        });
        mSensorCardRecyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {

            @Override
            public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
                adjustSensorCardAddAlpha();
            }
        });
        mSensorCardRecyclerView.setVisibility(View.VISIBLE);

        // Figure out the optimal height of a sensor presenter when the recycler view is layed out.
        mRecyclerViewGlobalLayoutListener = new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                if (getActivity() == null) {
                    return;
                }
                // A sensor presenter should be the height of the recycler view, minus
                // the margins, and minus the active card header size. We also subtract the height
                // of the external axis, which is transparent so the cards are shown behind it
                // but must be short enough to fit above it.
                int headerHeight = getResources().getDimensionPixelSize(
                        R.dimen.sensor_card_header_height);
                int marginHeight = getResources().getDimensionPixelSize(
                        R.dimen.cardview_margin);
                int externalAxisHeight = getResources().getDimensionPixelSize(
                        R.dimen.external_axis_height);
                final int optimalHeight = mSensorCardRecyclerView.getHeight() -
                        headerHeight - marginHeight * 3 - externalAxisHeight;
                int minHeight = getResources().getDimensionPixelSize(
                        R.dimen.sensor_card_content_height_min);
                mSensorCardAdapter.setSingleCardPresenterHeight(Math.max(optimalHeight, minHeight));
                mSensorCardRecyclerView.getViewTreeObserver().removeOnGlobalLayoutListener(
                        mRecyclerViewGlobalLayoutListener);
            }
        };
        mSensorCardRecyclerView.getViewTreeObserver().addOnGlobalLayoutListener(
                mRecyclerViewGlobalLayoutListener);
        mSensorCardRecyclerView.getViewTreeObserver().addOnGlobalLayoutListener(
                new ViewTreeObserver.OnGlobalLayoutListener() {

                    @Override
                    public void onGlobalLayout() {
                        adjustSensorCardAddAlpha();
                    }
                });
    }

    private List<String> getAllIncludedSources() {
        return nonEmptySensorList(
                Lists.newArrayList(mSensorRegistry.getAllSourcesExcept(mExcludedSensorIds)));
    }

    private List<String> getAvailableSources() {
        String[] selected = mSensorCardAdapter.getSensorTags();
        List<String> sources = mSensorRegistry.getAllSourcesExcept(mExcludedSensorIds, selected);
        if (!hasGoodSensorId(selected)) {
            // If nothing is selected, at least one must be avaiable (see notes on
            // #nonEmptySensorList)
            List<String> nonEmpty = nonEmptySensorList(sources);
            return nonEmpty;
        }

        return sources;
    }

    private boolean hasGoodSensorId(String[] selected) {
        for (String s : selected) {
            if (s != null && !s.isEmpty()) {
                return true;
            }
        }
        return false;
    }

    @NonNull
    private List<String> nonEmptySensorList(List<String> allSourcesExcept) {
        // TODO: try to prevent ever getting here (b/32695928)
        if (allSourcesExcept.isEmpty()) {
            // We need _some_ kind of hack, but hopefully after b/32695928, this will be
            // impossible/rare.
            return Lists.newArrayList(mSensorRegistry.getAllSources().get(0));
        }
        return allSourcesExcept;
    }

    private void adjustSensorCardAddAlpha() {
        if (mSensorCardAdapter == null || !mSensorCardAdapter.canAddMoreCards()) {
            return;
        }
        mBottomPanel.getHitRect(mPanelRect);
        mSensorCardAdapter.adjustAddViewAlpha(mPanelRect);
    }

    private SensorCardPresenter createSensorCardPresenter(GoosciSensorLayout.SensorLayout layout,
            final RecorderController rc) {
        int nextSensorCardColor;
        if (layout.color == Color.TRANSPARENT) {
            nextSensorCardColor = mColorAllocator.getNextColor(mSensorCardAdapter != null ?
                    mSensorCardAdapter.getColorArray() : null);
        } else {
            nextSensorCardColor = layout.color;
        }
        final SensorCardPresenter sensorCardPresenter = new SensorCardPresenter(
                new DataViewOptions(nextSensorCardColor, mScalarDisplayOptions),
                mSensorSettingsController, rc, layout, mSelectedExperiment.getExperimentId(),
                mExternalAxis.getInteractionListener(), this);

        final SensorStatusListener sensorStatusListener = new SensorStatusListener() {
            @Override
            public void onSourceStatus(String id, int status) {
                sensorCardPresenter.onSourceStatusUpdate(id, status);
            }

            @Override
            public void onSourceError(String id, int error, String errorMessage) {
                if (Objects.equals(id, sensorCardPresenter.getSelectedSensorId())) {
                    // Surface the error to the user if we haven't already.
                    if (!sensorCardPresenter.hasError()) {
                        sensorCardPresenter.onSourceError(true /* has an error */);
                        Snackbar bar = AccessibilityUtils.makeSnackbar(getView(),
                                getString(R.string.snackbar_source_error, errorMessage),
                                Snackbar.LENGTH_LONG);
                        showSnackbar(bar);
                    }
                }
            }
        };
        sensorCardPresenter.setSensorStatusListener(sensorStatusListener);
        // TODO: make this externally testable.
        sensorCardPresenter.setOnRetryClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        // Retry streaming.
                        final String sensorId = sensorCardPresenter.getSelectedSensorId();
                        if (sensorId != null && mSensorCardAdapter.getSensorCardPresenters()
                                .contains(sensorCardPresenter)) {
                            sensorCardPresenter.retryConnection(getActivity());
                        }
                    }
                });

        sensorCardPresenter.setOnSensorSelectedListener(
                new SensorCardPresenter.OnSensorClickListener() {
                    @Override
                    public void onSensorClicked(String sensorId) {
                        sensorCardPresenter.stopObserving();
                        tryStartObserving(sensorCardPresenter, sensorId, true);
                    }
                });
        sensorCardPresenter.setAppearanceProvider(getSensorAppearanceProvider());

        return sensorCardPresenter;
    }

    private void tryStartObserving(SensorCardPresenter sensorCardPresenter, String sensorId,
            boolean retry) {
        if (TextUtils.equals(sensorId, DecibelSensor.ID) && mDecibelSensorCardPresenter == null &&
                !PermissionUtils.tryRequestingPermission(getActivity(),
                        Manifest.permission.RECORD_AUDIO,
                        MainActivity.PERMISSIONS_AUDIO_RECORD_REQUEST, /* force retry */ retry)) {
            // If we did actually try to request the permission, save this sensorCardPresenter.
            // for when the permission is granted.
            if (PermissionUtils.canRequestAgain(getActivity(), Manifest.permission.RECORD_AUDIO)) {
                if (retry) {
                    // In this case, we had tried requesting permissions, so save this presenter.
                    mDecibelSensorCardPresenter = sensorCardPresenter;
                }
                // If the sensor can't be loaded, still show it as selected on the card so the user
                // understands that they wanted this sensor but can't use it.
                sensorCardPresenter.setConnectingUI(DecibelSensor.ID, true,
                        getActivity().getApplicationContext(), true);
            } else {
                // Then the user has selected "never ask again".
                sensorCardPresenter.setConnectingUI(DecibelSensor.ID, true,
                        getActivity().getApplicationContext(),
                        /* don't show the retry button */ false);
            }
        }
        startObserving(sensorId, sensorCardPresenter);
        sensorCardPresenter.setRecording(getRecordingStartTime());
        mExternalAxis.resetAxes();
        updateSensorLayout(sensorCardPresenter.buildLayout());
        updateAvailableSensors();
    }

    // TODO: pull out somewhere testable?
    private void updateAvailableSensors() {
        if (mSensorCardAdapter == null) {
            return;
        }
        List<String> availableSensors = getAvailableSources();
        List<String> allSensors = mSensorRegistry.getAllSources();
        List<SensorCardPresenter> sensorCardPresenters =
                mSensorCardAdapter.getSensorCardPresenters();

        // Available Sensors includes only sensors that are not being observed.
        // Check if a card wants to show the decibel sensor but permission was denied.
        // Remove this from the list of available sensors so the decibel sensor icon doesn't show
        // up in other cards.
        // TODO: Extend this to work for any sensor that doesn't have the permission granted.
        // See b/27439593
        if (availableSensors.contains(DecibelSensor.ID)) {
            for (SensorCardPresenter presenter : sensorCardPresenters) {
                if (TextUtils.equals(presenter.getSelectedSensorId(), DecibelSensor.ID)) {
                    availableSensors.remove(DecibelSensor.ID);
                }
            }
        }
        for (SensorCardPresenter presenter : sensorCardPresenters) {
            presenter.updateAvailableSensors(availableSensors, allSensors);
        }

        updateSensorCount();
    }

    private void launchLabelAdd(long timestamp) {
        String currentRunId = getCurrentRunId();
        final AddNoteDialog dialog = AddNoteDialog.createWithSavedTimestamp(timestamp, currentRunId,
                mSelectedExperiment.getExperimentId(),
                isRecording() ? R.string.add_run_note_placeholder_text : R.string
                        .add_experiment_note_placeholder_text);

        dialog.show(getChildFragmentManager(), AddNoteDialog.TAG);
    }

    @Override
    public AddNoteDialog.AddNoteDialogListener getAddNoteDialogListener() {
        return new AddNoteDialog.AddNoteDialogListener() {
            @Override
            public void adjustLabelBeforeAdd(Label label) {
                adjustLabelTimestamp(label);
            }

            @Override
            public MaybeConsumer<Label> onLabelAdd() {
                return new LoggingConsumer<Label>(TAG, "store label") {
                    @Override
                    public void success(Label value) {
                        processAddedLabel(value);
                    }
                };
            }

            public void onAddNoteTimestampClicked(GoosciLabelValue.LabelValue selectedValue,
                    int labelType,
                    long selectedTimestamp) {
                // Do nothing. Timestamp will not be shown and this is unused.
            }
        };
    }

    private static void adjustLabelTimestamp(Label label) {
        if (label.hasValueType(GoosciLabelValue.LabelValue.PICTURE)) {
            // We want to set the time stamp of this label to the time of the picture
            PictureLabelValue pictureLabel =
                    (PictureLabelValue) label.getLabelValue(
                            GoosciLabelValue.LabelValue.PICTURE);
            File file = new File(pictureLabel.getAbsoluteFilePath());
            // Check to make sure this value is not crazy: should be within 10 minutes of
            // now and not from the future.
            long delta = System.currentTimeMillis() - file.lastModified();
            if (delta < TimeUnit.MINUTES.toMillis(10) && delta > 0) {
                label.setTimestamp(file.lastModified());
            }
        }
    }

    private void processAddedLabel(Label label) {
        refreshLabels();
        ensureUnarchived(mSelectedExperiment, getDataController());
        playAddNoteAnimation();
        // Trigger labels are logged in RecorderControllerImpl.
        if (!(label.hasValueType(GoosciLabelValue.LabelValue.SENSOR_TRIGGER))) {
            String trackerLabel = isRecording() ? TrackerConstants.LABEL_RECORD :
                    TrackerConstants.LABEL_OBSERVE;
            WhistlePunkApplication.getUsageTracker(getActivity())
                    .trackEvent(TrackerConstants.CATEGORY_NOTES,
                            TrackerConstants.ACTION_CREATE,
                            trackerLabel,
                            TrackerConstants.getLabelValueType(label));
        }
    }

    private void playAddNoteAnimation() {
        final View addNoteIndicator = LayoutInflater.from(getActivity()).inflate(
                R.layout.note_indicator, (ViewGroup) getView(), false);
        ((ViewGroup) getView()).addView(addNoteIndicator);
        // NOTE: this is not always guaranteed to find the menu item view.
        View menuItem = getActivity().findViewById(R.id.btn_experiment_details);
        if (menuItem == null) {
            // Nothing to animate to, so just end.
            return;
        }
        int[] location = new int[2];
        menuItem.getLocationOnScreen(location);
        // Center in parent view.
        addNoteIndicator.setX(getView().getWidth() / 2);
        addNoteIndicator.setY(getView().getHeight() / 2);
        addNoteIndicator.setAlpha(1.0f);
        addNoteIndicator.setScaleX(4.0f);
        addNoteIndicator.setScaleY(4.0f);
        addNoteIndicator.setVisibility(View.VISIBLE);
        // Note: these animation values are not shared with any other animation, leaving them as
        // raw values so this custom animation can be easily tweaked.
        addNoteIndicator.animate()
                .setStartDelay(100)
                .x(location[0]) // go to the button
                .y(-addNoteIndicator.getHeight()) // hide into menu bar
                .scaleX(1.0f)
                .scaleY(1.0f)
                .alpha(.2f)
                .setDuration(750)
                .setInterpolator(new DecelerateInterpolator())
                .setListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        if (addNoteIndicator.getParent() != null) {
                            ((ViewGroup) addNoteIndicator.getParent()).removeView(addNoteIndicator);
                        }
                    }
                })
                .start();
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

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);

        mGraphOptionsController = new GraphOptionsController(activity);
        mSensorSettingsController = new SensorSettingsControllerImpl(activity);
        mUICallbacks = getUiCallbacks(activity);
    }

    private UICallbacks getUiCallbacks(Activity activity) {
        if (activity instanceof CallbacksProvider) {
            return ((CallbacksProvider) activity).getRecordFragmentCallbacks();
        } else {
            return UICallbacks.NULL;
        }
    }

    private void onRecordingMetadataUpdated() {
        boolean isRecording = isRecording();
        if (isRecording) {
            mExternalAxis.resetAxes();
            mExternalAxis.onStartRecording(mCurrentRecording.getStartTime());
        } else {
            mExternalAxis.onStopRecording();
        }
        refreshLabels();
        if (mSensorCardAdapter != null) {
            mSensorCardAdapter.setRecording(isRecording, RecordingMetadata.getStartTime(
                    mCurrentRecording));
            if (!isRecording) {
                adjustSensorCardAddAlpha();
            }
        }
        updateRecordingUIState();
    }

    private String getExperimentName() {
        if (mSelectedExperiment != null) {
            return mSelectedExperiment.getDisplayTitle(getActivity());
        } else {
            return "";
        }
    }

    private void lockUiForRecording() {
        mUICallbacks.onRecordingStart(getExperimentName());

        // Lock the sensor cards and add button
        if (mSensorCardAdapter != null) {
            mSensorCardAdapter.setUiLockedForRecording(true);
        }
    }

    private void updateRecordingUIState() {
        if (mRecordButton != null) {
            mRecordButton.setEnabled(true);
        }

        if (mSelectedExperiment == null) {
            return;
        }

        if (isRecording()) {
            lockUiForRecording();
            mAddButton.setContentDescription(
                    getResources().getString(R.string.btn_add_run_note_description));
            mRecordButton.setContentDescription(getResources().getString(
                    R.string.btn_stop_description));
            mRecordButton.setImageDrawable(getResources().getDrawable(
                    R.drawable.ic_recording_stop_36dp));
        } else {
            mUICallbacks.onRecordingStopped();
            mAddButton.setContentDescription(
                    getResources().getString(R.string.btn_add_experiment_note_description));
            mRecordButton.setContentDescription(getResources().getString(
                    R.string.btn_record_description));
            mRecordButton.setImageDrawable(getResources().getDrawable(
                    R.drawable.ic_recording_red_40dp));
            if (mSensorCardAdapter != null) {
                mSensorCardAdapter.setUiLockedForRecording(false);
            }
        }
    }

    private void setControlButtonsEnabled(boolean enabled) {
        mRecordButton.setEnabled(enabled);
        mAddButton.setEnabled(enabled);
    }

    private void refreshLabels() {
        if (mSensorCardAdapter == null || !isRecording() || mExternalAxis == null) {
            return;
        }
        Trial trial = mSelectedExperiment.getTrial(mCurrentRecording.getRunId());
        if (trial == null) {
            return;
        }
        if (mSensorCardAdapter != null) {
            for (SensorCardPresenter p :
                    mSensorCardAdapter.getSensorCardPresenters()) {
                p.refreshLabels(trial.getLabels());
            }
        }
        if (mExternalAxis != null) {
            mExternalAxis.onLabelsChanged(trial.getLabels());
        }
    }

    private void takeSnapshot() {
        // TODO: what to do?
    }

    @NonNull
    private Function<String, String> getIdToName() {
        // Right now, this is unused, but demonstrates that we are correctly compiling Java 8
        // syntax.
        return s -> getSensorAppearanceProvider().getAppearance(s).getName(getActivity());
    }


    private void tryStartRecording(final RecorderController rc) {
        if (mSelectedExperiment == null) {
            return;
        }

        Intent mLaunchIntent;
        mLaunchIntent = MainActivity.launchIntent(getActivity(), R.id.navigation_item_observe);
        // This isn't currently used, but does ensure this intent doesn't match any other intent.
        // See b/31616891
        mLaunchIntent.setData(
                Uri.fromParts("observe", "experiment=" + mSelectedExperiment.getExperimentId(),
                        null));
        rc.startRecording(mLaunchIntent);
    }

    private void failedStartRecording(int stringId) {
        Snackbar bar = AccessibilityUtils.makeSnackbar(getView(),
                getActivity().getResources().getString(stringId), Snackbar.LENGTH_LONG);
        showSnackbar(bar);
    }

    private void tryStopRecording(final RecorderController rc) {
        mUICallbacks.onRecordStopRequested();
        rc.stopRecording();
    }

    private void failedStopRecording(int stringId) {
        StopRecordingNoDataDialog dialog = StopRecordingNoDataDialog.newInstance(
                getResources().getString(stringId));
        dialog.show(getChildFragmentManager(), StopRecordingNoDataDialog.TAG);
    }

    @Override
    public void requestCancelRecording() {
        mRecordingWasCanceled = true;
        withRecorderController(new Consumer<RecorderController>() {
            @Override
            public void take(RecorderController rc) {
                rc.stopRecordingWithoutSaving();
            }
        });
    }

    @Override
    public void continueRecording() {
        if (mRecordButton != null) {
            mRecordButton.setEnabled(true);
        }
    }

    private DataController getDataController() {
        return AppSingleton.getInstance(getActivity()).getDataController();
    }

    public void startObserving(final String sensorId,
            final SensorCardPresenter sensorCardPresenter) {
        final Context context = getActivity().getApplicationContext();
        sensorCardPresenter.setConnectingUI(sensorId, false, context, true);
        mSensorRegistry.withSensorChoice(TAG, sensorId, new Consumer<SensorChoice>() {
            @Override
            public void take(SensorChoice sensorChoice) {
                // TODO: should dataViewOptions go into sensorCardPresenter?
                NumberFormat numberFormat = AppSingleton.getInstance(context)
                        .getSensorAppearanceProvider().getAppearance(sensorId).getNumberFormat();
                final SensorPresenter sensorPresenter = sensorChoice.createPresenter(
                        sensorCardPresenter.getDataViewOptions(), numberFormat,
                        new StatsListener() {
                            @Override
                            public void onStatsUpdated(List<StreamStat> stats) {
                                if (isRecording()) {
                                    sensorCardPresenter.updateStats(stats);
                                }
                            }
                        });

                final WriteableSensorOptions options = sensorCardPresenter.getCardOptions(
                        sensorChoice, context).load(
                        new NewOptionsStorage.SnackbarFailureListener(getView()));
                ReadableSensorOptions readOptions = options.getReadOnly();
                sensorPresenter.getOptionsPresenter().applyOptions(readOptions);

                sensorCardPresenter.startObserving(sensorChoice, sensorPresenter, readOptions,
                        mSelectedExperiment);
                refreshLabels();
            }

        });
    }

    public void disableAllTriggers(final GoosciSensorLayout.SensorLayout layout,
            final SensorCardPresenter presenter) {
        if (mSelectedExperiment == null) {
            return;
        }
        int position = getPositionOfLayout(layout);
        if (position < 0) {
            return;
        }
        mSelectedExperiment.updateSensorLayout(position, layout);
        getDataController().updateExperiment(mSelectedExperiment.getExperimentId(),
                new LoggingConsumer<Success>(TAG, "disable sensor triggers") {
                    @Override
                    public void success(Success value) {
                        withRecorderController(new Consumer<RecorderController>() {
                            @Override
                            public void take(RecorderController recorderController) {
                                recorderController.clearSensorTriggers(layout.sensorId);
                                presenter.onSensorTriggersCleared();
                            }
                        });
                    }
                });
    }

    private void doVisualAlert(SensorTrigger trigger) {
        final SensorCardPresenter presenter = getPresenterById(trigger.getSensorId());
        if (presenter == null) {
            return;
        }

        // In any of the presenter is visible, go ahead and pass the trigger to it.
        presenter.onSensorTriggerFired();

        // Only need to do a snackbar for off-screen visual alerts.
        if (!trigger.hasAlertType(TriggerInformation.TRIGGER_ALERT_VISUAL)) {
            return;
        }
        // If a snackbar is already being shown, don't show a new one.
        if (mVisibleSnackbar != null) {
            return;
        }

        // TODO: Work with UX to tweak this check so that the right amount of the card is shown.
        // Look to see if the card is outside of the visible presenter range. The alert is shown
        // near the top of the card, so we check between the first fully visible card and the last
        // partially visible card.
        if (!presenter.isTriggerBarOnScreen()) {

            SensorAppearance appearance = AppSingleton.getInstance(getActivity())
                    .getSensorAppearanceProvider().getAppearance(trigger.getSensorId());
            String units = appearance.getUnits(getActivity());
            String sensorName = appearance.getName(getActivity());
            String triggerWhenText = getActivity().getResources().getStringArray(
                    R.array.trigger_when_list_note_text)[trigger.getTriggerWhen()];
            String message = getActivity().getResources().getString(
                    R.string.trigger_snackbar_auto_text, sensorName, triggerWhenText,
                    trigger.getValueToTrigger(), units);
            final Snackbar bar = AccessibilityUtils.makeSnackbar(getView(), message,
                    Snackbar.LENGTH_LONG);
            bar.setAction(R.string.scroll_to_card, new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    mSensorCardLayoutManager.scrollToPosition(getPositionOfPresenter(presenter));
                }
            });
            showSnackbar(bar);
        }
    }

    /**
     * Get list of existing devices.
     */
    private void loadIncludedSensors(final List<GoosciSensorLayout.SensorLayout> layouts,
            final RecorderController rc) {
        if (mSelectedExperiment != null) {
            getDataController().getExternalSensorsByExperiment(
                    mSelectedExperiment.getExperimentId(),
                    new LoggingConsumer<ExperimentSensors>(TAG,
                            "add external sensors") {
                        @Override
                        public void success(ExperimentSensors sensors) {
                            mExcludedSensorIds.clear();
                            mExcludedSensorIds.addAll(sensors.getExcludedSensorIds());
                            updateExternalSensors(sensors.getIncludedSensors());
                            setSensorPresenters(layouts, rc);
                        }
                    });
        } else {
            updateExternalSensors(Collections.<ConnectableSensor>emptyList());
            setSensorPresenters(layouts, rc);
        }
    }

    private void updateExternalSensors(List<ConnectableSensor> sensors) {
        // TODO: more graceful handling of this case?
        if (getActivity() == null) {
            return;
        }
        List<String> sensorsActuallyAdded = mSensorRegistry.updateExternalSensors(sensors,
                AppSingleton.getInstance(getActivity()).getExternalSensorProviders());

        if (!sensorsActuallyAdded.isEmpty()) {
            boolean discoveryEnabled = mFeatureDiscoveryProvider.isEnabled(
                    getActivity(),
                    FeatureDiscoveryProvider.FEATURE_NEW_EXTERNAL_SENSOR);
            if (discoveryEnabled) {
                // If (unusually) more than one external sensor was added for
                // the first time on the screen, highlight the left-most one,
                // the one most likely to be on-screen.
                String sensorId = sensorsActuallyAdded.get(0);
                scheduleFeatureDiscovery(sensorId);
            }
            Fragment hasBluetoothDialog =
                    getChildFragmentManager().findFragmentByTag(BluetoothDisabledDialog.TAG);
            if (hasBluetoothDialog == null) {
                boolean bluetoothEnabled = DeviceDiscoverer.isBluetoothEnabled();
                if (!bluetoothEnabled) {
                    // Show an alert to have the user turn on bluetooth.
                    BluetoothDisabledDialog dialog = new BluetoothDisabledDialog();
                    dialog.show(getChildFragmentManager(), BluetoothDisabledDialog.TAG);
                }
            }
        }
    }

    private void updateSensorCount() {
        mSensorCardAdapter.setAvailableSensorCount(getAllIncludedSources().size());
    }

    private void scheduleFeatureDiscovery(String sensorId) {
        mHandler.sendMessageDelayed(Message.obtain(mHandler, MSG_SHOW_FEATURE_DISCOVERY, sensorId),
                FeatureDiscoveryProvider.FEATURE_DISCOVERY_SHOW_DELAY_MS);
    }

    private void cancelFeatureDiscovery() {
        mHandler.removeMessages(MSG_SHOW_FEATURE_DISCOVERY);
    }

    private void showFeatureDiscovery(String sensorId) {
        if (getActivity() == null) {
            return;
        }
        if (mSensorCardRecyclerView.getChildCount() == 0) {
            return;
        }
        // Activate the first sensor card so the feature discovery has a place to attach.
        if (mSensorCardAdapter != null &&
                mSensorCardAdapter.getSensorCardPresenters().size() > 0) {
            mSensorCardAdapter.getSensorCardPresenters().get(0).setActive(true, true);
            mSensorCardAdapter.getSensorCardPresenters().get(0).scrollToSensor(sensorId);
        }
        // Look for view with the tag.
        final View view = mSensorCardRecyclerView.getChildAt(0).findViewWithTag(
                sensorId);
        if (view != null) {
            mFeatureDiscoveryProvider.show(((AppCompatActivity) getActivity()),
                    FeatureDiscoveryProvider.FEATURE_NEW_EXTERNAL_SENSOR, sensorId);
        }
    }

    private SensorEnvironment getSensorEnvironment() {
        return AppSingleton.getInstance(getActivity()).getSensorEnvironment();
    }

    private long getNow() {
        return getSensorEnvironment().getDefaultClock().getNow();
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        if (getActivity() == null) {
            return;
        }
        inflater.inflate(R.menu.menu_record, menu);
        boolean enableDevTools = DevOptionsFragment.isDevToolsEnabled(getActivity());
        menu.findItem(R.id.action_graph_options).setVisible(false);  // b/29771945
        menu.findItem(R.id.action_level).setVisible(enableDevTools);
        menu.findItem(R.id.action_ruler).setVisible(enableDevTools);
        super.onCreateOptionsMenu(menu, inflater);
    }

    @Override
    public void onPrepareOptionsMenu(final Menu menu) {
        MenuItem addSensors = menu.findItem(R.id.btn_add_sensors);
        if (addSensors != null) {
            addSensors.setVisible(!isRecording());
        }
        MenuItem expDetails = menu.findItem(R.id.btn_experiment_details);
        if (expDetails != null) {
            expDetails.setVisible(!isRecording());
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_graph_options) {
            mGraphOptionsController.launchOptionsDialog(mScalarDisplayOptions,
                    new NewOptionsStorage.SnackbarFailureListener(getView()));
        } else if (id == R.id.btn_add_sensors) {
            Intent intent = new Intent(getActivity(), ManageDevicesActivity.class);
            if (mSelectedExperiment != null) {
                intent.putExtra(ManageDevicesActivity.EXTRA_EXPERIMENT_ID,
                        mSelectedExperiment.getExperimentId());
            }
            getActivity().startActivity(intent);
        } else if (id == R.id.btn_experiment_details) {
            if (mSelectedExperiment != null) {
                ExperimentDetailsActivity.launch(getActivity(),
                        mSelectedExperiment.getExperimentId(), true /* need a task stack*/);
            }
        } else if (id == R.id.action_ruler) {
            Intent intent = new Intent(getActivity(), RulerActivity.class);
            startActivity(intent);
            return true;
        } else if (id == R.id.action_level) {
            Intent intent = new Intent(getActivity(), LevelActivity.class);
            startActivity(intent);
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    public long getRecordingStartTime() {
        return RecordingMetadata.getStartTime(mCurrentRecording);
    }

    private Maybe<RecorderController> getRecorderController() {
        final MaybeSubject s = MaybeSubject.create();
        withRecorderController(new Consumer<RecorderController>() {
            @Override
            public void take(RecorderController recorderController) {
                s.onSuccess(recorderController);
            }
        });
        return s;
    }

    private void withRecorderController(Consumer<RecorderController> c) {
        // TODO: use getRecorderController everywhere?
        AppSingleton.getInstance(getActivity()).withRecorderController(TAG, c);
    }

    /**
     * Ensures that the experiment is unarchived, in case we make a new run or label.
     * TODO: Find a different home than RecordFragment.
     */
    public static void ensureUnarchived(Experiment experiment, DataController dc) {
        if (experiment != null) {
            if (experiment.isArchived()) {
                experiment.setArchived(false);
                dc.updateExperiment(experiment.getExperimentId(),
                        LoggingConsumer.<Success>expectSuccess(TAG, "Unarchiving experiment"));
            }
        }
    }

    @Override
    public boolean handleMessage(Message msg) {
        if (msg.what == MSG_SHOW_FEATURE_DISCOVERY) {
            showFeatureDiscovery((String) msg.obj);
        }
        return false;
    }

    @Override
    public void onAudioSettingsPreview(String[] previewSonificationTypes, String[] sensorIds) {
        // During observe, audio settings are only set for one card at a time, so sensorIds is
        // length 1.
        SensorCardPresenter presenter = getPresenterById(sensorIds[0]);
        if (presenter == null) {
            return;
        }
        presenter.onAudioSettingsPreview(previewSonificationTypes[0]);
    }

    @Override
    public void onAudioSettingsApplied(String[] newSonificationTypes, String[] sensorIds) {
        // During record, audio settings are only set for one card at a time, so sensorIds is length 1.
        SensorCardPresenter presenter = getPresenterById(sensorIds[0]);
        if (presenter == null) {
            return;
        }
        presenter.onAudioSettingsApplied(newSonificationTypes[0]);
    }

    @Override
    public void onAudioSettingsCanceled(String[] originalSonificationTypes, String[] sensorIds) {
        // During record, audio settings are only set for one card at a time, so sensorIds is length 1.
        SensorCardPresenter presenter = getPresenterById(sensorIds[0]);
        if (presenter == null) {
            return;
        }
        presenter.onAudioSettingsCanceled(originalSonificationTypes[0]);
    }

    public int getPositionOfLayout(GoosciSensorLayout.SensorLayout layout) {
        if (mSensorCardAdapter == null) {
            return -1;
        }
        List<SensorCardPresenter> presenters = mSensorCardAdapter.getSensorCardPresenters();
        for (int i = 0; i < presenters.size(); i++) {
            if (TextUtils.equals(presenters.get(i).getSelectedSensorId(), layout.sensorId)) {
                return i;
            }
        }
        return -1;
    }

    public int getPositionOfPresenter(SensorCardPresenter presenter) {
        if (mSensorCardAdapter == null) {
            return -1;
        }
        List<SensorCardPresenter> presenters = mSensorCardAdapter.getSensorCardPresenters();
        for (int i = 0; i < presenters.size(); i++) {
            if (Objects.equals(presenter, presenters.get(i))) {
                return i;
            }
        }
        return -1;
    }

    private SensorCardPresenter getPresenterById(String sensorId) {
        if (mSensorCardAdapter == null) {
            return null;
        }
        for (SensorCardPresenter presenter : mSensorCardAdapter.getSensorCardPresenters()) {
            if (presenter.getSelectedSensorId().equals(sensorId)) {
                return presenter;
            }
        }
        return null;
    }

    private void showSnackbar(Snackbar bar) {
        // TODO: UX asks for the Snackbar to be shown above the external axis...
        // may need to do a custom snackbar class.
        bar.setCallback(new Snackbar.Callback() {

            @Override
            public void onDismissed(Snackbar snackbar, int event) {
                mVisibleSnackbar = null;
            }

            @Override
            public void onShown(Snackbar snackbar) {
            }
        });
        bar.show();
        mVisibleSnackbar = bar;
    }

}
