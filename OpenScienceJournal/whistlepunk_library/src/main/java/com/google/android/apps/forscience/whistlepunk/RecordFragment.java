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
import android.bluetooth.BluetoothAdapter;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.design.widget.Snackbar;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.DefaultItemAnimator;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.Window;
import android.view.WindowManager;
import android.view.animation.DecelerateInterpolator;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ImageButton;
import android.widget.Spinner;
import android.widget.TextView;

import com.google.android.apps.forscience.javalib.Consumer;
import com.google.android.apps.forscience.javalib.Success;
import com.google.android.apps.forscience.whistlepunk.analytics.TrackerConstants;
import com.google.android.apps.forscience.whistlepunk.data.GoosciSensorLayout;
import com.google.android.apps.forscience.whistlepunk.devicemanager.ConnectableSensor;
import com.google.android.apps.forscience.whistlepunk.devicemanager.ManageDevicesActivity;
import com.google.android.apps.forscience.whistlepunk.featurediscovery.FeatureDiscoveryListener;
import com.google.android.apps.forscience.whistlepunk.featurediscovery.FeatureDiscoveryProvider;
import com.google.android.apps.forscience.whistlepunk.metadata.Experiment;
import com.google.android.apps.forscience.whistlepunk.metadata.ExternalSensorSpec;
import com.google.android.apps.forscience.whistlepunk.metadata.GoosciLabelValue;
import com.google.android.apps.forscience.whistlepunk.metadata.GoosciSensorTriggerInformation.TriggerInformation;
import com.google.android.apps.forscience.whistlepunk.metadata.Label;
import com.google.android.apps.forscience.whistlepunk.metadata.PictureLabel;
import com.google.android.apps.forscience.whistlepunk.metadata.Project;
import com.google.android.apps.forscience.whistlepunk.metadata.SensorTrigger;
import com.google.android.apps.forscience.whistlepunk.metadata.SensorTriggerLabel;
import com.google.android.apps.forscience.whistlepunk.project.experiment.ExperimentDetailsActivity;
import com.google.android.apps.forscience.whistlepunk.project.experiment.UpdateExperimentActivity;
import com.google.android.apps.forscience.whistlepunk.review.RunReviewActivity;
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
import com.google.common.collect.Lists;

import java.io.File;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;

public class RecordFragment extends Fragment implements AddNoteDialog.AddNoteDialogListener,
        Handler.Callback, StopRecordingNoDataDialog.StopRecordingDialogListener,
        AudioSettingsDialog.AudioSettingsDialogListener {

    private static final String TAG = "RecordFragment";

    private static final String KEY_SAVED_ACTIVE_SENSOR_CARD = "savedActiveCardIndex";
    private static final String KEY_SAVED_RECYCLER_LAYOUT = "savedRecyclerLayout";

    private static final int DEFAULT_CARD_VIEW = GoosciSensorLayout.SensorLayout.METER;
    private static final boolean DEFAULT_AUDIO_ENABLED = false;
    private static final boolean DEFAULT_SHOW_STATS_OVERLAY = false;

    private static final String EXTRA_SENSOR_IDS = "sensorIds";

    private static final int MSG_SHOW_FEATURE_DISCOVERY = 111;

    public SensorRegistry mSensorRegistry;

    private Snackbar mVisibleSnackbar;

    /**
     * Used as the Run ID for labels that are created when no run is being recorded.
     */
    public static final String NOT_RECORDING_RUN_ID = "NOT_RECORDING";

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
    private Spinner mSpinner;
    private ExternalAxisController mExternalAxis;
    private ViewTreeObserver.OnGlobalLayoutListener mRecyclerViewGlobalLayoutListener;
    private View mBottomPanel;
    // Stores the rect of the panel.
    private Rect mPanelRect = new Rect();

    private Project mSelectedProject;
    private Experiment mSelectedExperiment;
    private boolean mSensorLayoutsLoaded;

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

    public static RecordFragment newInstance() {
        return new RecordFragment();
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
        cancelFeatureDiscovery();
        if (mSensorCardAdapter != null) {
            mSensorCardAdapter.onPause();
        }
        getMetadataController().clearExperimentChangeListener();
        saveCurrentLayouts();
        withRecorderController(new Consumer<RecorderController>() {
            @Override
            public void take(RecorderController rc) {
                mRecorderPauseId = rc.pauseObservingAll();
                rc.removeRecordingStateListener(TAG);
                rc.removeTriggerFiredListener(TAG);
            }
        });
        AppSingleton.getInstance(getActivity()).removeListeners(TAG);
        mSensorLayoutsLoaded = false;
        super.onPause();
    }

    private boolean isRecording() {
        return mCurrentRecording != null;
    }

    private String getCurrentRunId() {
        return isRecording() ? mCurrentRecording.getRunId() : NOT_RECORDING_RUN_ID;
    }

    @Override
    public void onResume() {
        super.onResume();

        // Reload sensor appearances in case they have changed while away from this fragment,
        getSensorAppearanceProvider().loadAppearances(
                LoggingConsumer.<Success>expectSuccess(TAG, "Load appearances"));

        setControlButtonsEnabled(false);

        withRecorderController(new Consumer<RecorderController>() {
            @Override
            public void take(final RecorderController rc) {
                rc.addRecordingStateListener(TAG, new RecorderController.RecordingStateListener() {
                    @Override
                    public void onRecordingStateChanged(RecordingMetadata currentRecording) {
                        RecordingMetadata prevRecording = mCurrentRecording;

                        mCurrentRecording = currentRecording;
                        onRecordingMetadataUpdated();
                        // If we have switched from a recording state to a not-recording state,
                        // update the UI.
                        if (prevRecording != null && !isRecording()) {
                            mExternalAxis.onStopRecording();
                            AddNoteDialog dialog = (AddNoteDialog) getChildFragmentManager()
                                    .findFragmentByTag(AddNoteDialog.TAG);
                            if (dialog != null) {
                                dialog.dismiss();
                            }
                            if (!mRecordingWasCanceled) {
                                onRecordingStopped(prevRecording.getRunId());
                            }
                        }
                        mRecordingWasCanceled = false;
                    }

                    @Override
                    public void onRecordingStartFailed(
                            @RecorderController.RecordingStartErrorType int errorType, Exception e) {
                        if (errorType == RecorderController.ERROR_START_FAILED) {
                            failedStartRecording(R.string.recording_start_failed);
                        } else if (errorType ==
                                RecorderController.ERROR_START_FAILED_DISCONNECTED) {
                            failedStartRecording(R.string.recording_start_failed_disconnected);
                        }
                        updateRecordingUIState();
                    }

                    @Override
                    public void onRecordingStopFailed(
                            @RecorderController.RecordingStopErrorType int errorType) {
                        if (errorType == RecorderController.ERROR_STOP_FAILED_DISCONNECTED) {
                            failedStopRecording(R.string.recording_stop_failed_disconnected);
                        } else if (errorType == RecorderController.ERROR_STOP_FAILED_NO_DATA) {
                            failedStopRecording(R.string.recording_stop_failed_no_data);
                        } else if (errorType == RecorderController.ERROR_FAILED_SAVE_RECORDING) {
                            AccessibilityUtils.makeSnackbar(getView(),
                                    getActivity().getResources().getString(
                                            R.string.recording_stop_failed_save),
                                    Snackbar.LENGTH_LONG).show();
                        }
                        updateRecordingUIState();
                    }
                });
                rc.addTriggerFiredListener(TAG, new RecorderController.TriggerFiredListener() {

                    @Override
                    public void onTriggerFired(SensorTrigger trigger) {
                        doVisualAlert(trigger);
                    }

                    @Override
                    public void onRequestStartRecording() {
                        lockUiForRecording((AppCompatActivity) getActivity());
                    }

                    @Override
                    public void onLabelAdded(Label label) {
                        processAddedLabel(label);
                    }

                    @Override
                    public void onRequestStopRecording(RecorderController rc) {
                        updateRecorderControllerLayouts(rc, buildCurrentLayouts());
                    }
                });

                if (!rc.resumeObservingAll(mRecorderPauseId)) {
                    // Force a reload of the current experiment's ob
                    mSelectedExperiment = null;
                }

                getMetadataController().setExperimentChangeListener(
                        new MetadataController.MetadataChangeListener() {
                            @Override
                            public void onMetadataChanged(Project newProject,
                                    List<Experiment> newExperiments) {
                                mSelectedProject = newProject;
                                if (!readSensorsFromExtras(rc)) {
                                    // By spec, newExperiments should always be non-zero
                                    onSelectedExperimentChanged(newExperiments.get(0), rc);
                                }
                                setupSpinnerAdapter(newProject, newExperiments);

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
        saveCurrentLayouts();
        if (mSensorCardAdapter != null) {
            mSensorCardAdapter.onDestroy();
            mSensorCardAdapter = null;
        }
        super.onDestroyView();
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
        mExternalAxis.destroy();
        mHandler = null;
        mSensorSettingsController = null;
        mGraphOptionsController = null;
        if (mSensorRegistry != null) {
            mSensorRegistry.removePendingOperations(TAG);
        }
        super.onDestroy();
    }

    private void stopObservingCurrentSensors() {
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
                            lockUiForRecording((AppCompatActivity) getActivity());
                            tryStartRecording(rc);
                        }
                    }
                });
            }
        });

        mSpinner = (Spinner) getActivity().findViewById(R.id.spinner_nav);
        mBottomPanel = rootView.findViewById(R.id.bottom_panel);

        mGraphOptionsController.loadIntoScalarDisplayOptions(mScalarDisplayOptions, getView());
        mSensorCardLayoutManager = new LinearLayoutManager(getActivity());

        if (savedInstanceState != null) {
            mSensorCardLayoutManager.onRestoreInstanceState(
                    savedInstanceState.getParcelable(KEY_SAVED_RECYCLER_LAYOUT));
        }

        return rootView;
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

    private void setupSpinnerAdapter(final Project project, List<Experiment> experiments) {
        // If the activity has been killed, ignore this and just return.
        if (this.getActivity() == null) {
            return;
        }

        // Load the experiments into the spinner adapter.
        final ExperimentsSpinnerAdapter adapter = new ExperimentsSpinnerAdapter(
                this.getActivity(), (ArrayList<Experiment>) experiments);
        mSpinner.setAdapter(adapter);

        // Set selection before item selected listener to avoid initial event being fired.
        setSpinnerSelectedExperiment(mSelectedExperiment);

        mSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (adapter.isNewExperimentPlaceholder(position)) {
                    getDataController().createExperiment(project,
                            new LoggingConsumer<Experiment>(TAG, "Create a new experiment") {
                                @Override
                                public void success(final Experiment experiment) {
                                    UpdateExperimentActivity.launch(getActivity(),
                                            experiment.getExperimentId(), true /* is new */,
                                            getActivity().getComponentName());
                                }
                            });
                } else {
                    userChangedSelectedExperiment((Experiment) mSpinner.getItemAtPosition(position));
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                userChangedSelectedExperiment((Experiment) mSpinner.getItemAtPosition(0));
            }
        });

    }

    private void userChangedSelectedExperiment(Experiment experiment) {
        getMetadataController().changeSelectedExperiment(experiment);
    }

    private MetadataController getMetadataController() {
        return AppSingleton.getInstance(getActivity()).getMetadataController();
    }

    private void onSelectedExperimentChanged(final Experiment selectedExperiment,
            final RecorderController rc) {
        if (!TextUtils.equals(Experiment.getExperimentId(selectedExperiment),
                Experiment.getExperimentId(mSelectedExperiment))) {
            saveCurrentLayouts();
        } else if (mSensorLayoutsLoaded) {
            // If it is the same experiment and we've already loaded the sensor layouts, we
            // don't need to do it again.
            setControlButtonsEnabled(true);
            return;
        }
        stopObservingCurrentSensors();
        mSelectedExperiment = selectedExperiment;
        mSensorLayoutsLoaded = false;
        getDataController().getSensorLayouts(selectedExperiment.getExperimentId(),
                new LoggingConsumer<List<GoosciSensorLayout.SensorLayout>>(TAG,
                        "get sensor layout") {
                    @Override
                    public void success(List<GoosciSensorLayout.SensorLayout> layouts) {
                        addExternalSensors(layouts, rc);
                        mSensorLayoutsLoaded = true;
                    }
                });
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
        getDataController().updateSensorLayout(mSelectedExperiment.getExperimentId(), position,
                sensorLayout, LoggingConsumer.<Success>expectSuccess(TAG, "saving layout"));
    }

    private void saveCurrentLayouts() {
        if (mSelectedExperiment == null || !mSensorLayoutsLoaded) {
            return;
        }
        final List<GoosciSensorLayout.SensorLayout> layouts = buildCurrentLayouts();
        if (layouts != null) {
            getDataController().setSensorLayouts(mSelectedExperiment.getExperimentId(),
                    layouts, LoggingConsumer.<Success>expectSuccess(TAG, "saving layouts"));
        }
        withRecorderController(new Consumer<RecorderController>() {
            @Override
            public void take(RecorderController recorderController) {
                updateRecorderControllerLayouts(recorderController, layouts);
            }
        });
    }

    private void updateRecorderControllerLayouts(RecorderController rc,
            List<GoosciSensorLayout.SensorLayout> layouts) {
        rc.setCurrentSensorLayouts(layouts);
    }

    private List<GoosciSensorLayout.SensorLayout> buildCurrentLayouts() {
        if (mSensorCardAdapter == null) {
            return null;
        }
        List<SensorCardPresenter> presenters = mSensorCardAdapter.getSensorCardPresenters();
        int size = presenters.size();
        List<GoosciSensorLayout.SensorLayout> layouts = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            layouts.add(presenters.get(i).buildLayout());
        }

        return layouts;
    }

    private void setSensorPresenters(List<GoosciSensorLayout.SensorLayout> layouts,
            RecorderController rc) {
        if (layouts == null || layouts.size() == 0) {
            layouts = Lists.newArrayList(defaultLayout());
        }
        int maxNumSources = mSensorRegistry.getAllSources().size();

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

    private void setSpinnerSelectedExperiment(Experiment experiment) {
        int selectIndex = 0;
        ExperimentsSpinnerAdapter adapter = (ExperimentsSpinnerAdapter) mSpinner.getAdapter();
        final int count = adapter.getCount();
        for (int i = 0; i < count; i++) {
            if (adapter.getItem(i) != null) {
                String adapterExperimentId = adapter.getItem(i).getExperimentId();
                if (adapterExperimentId.equals(experiment.getExperimentId())) {
                    selectIndex = i;
                }
            }
        }
        mSpinner.setSelection(selectIndex);
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
        updateSensorCount();
        mSensorCardAdapter.setRecording(isRecording(), getRecordingStartTime());
        long resetTime = mExternalAxis.resetAxes();
        if (mSensorCardAdapter != null) {
            long ignoreDataBefore = isRecording() ? -1 : resetTime;
            mSensorCardAdapter.onResume(ignoreDataBefore);
        }

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

    protected Set<String> getAvailableSources() {
        Set<String> allSensorIds = new HashSet<String>(mSensorRegistry.getAllSources());
        allSensorIds.removeAll(Arrays.asList(mSensorCardAdapter.getSensorTags()));
        return allSensorIds;
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
        sensorCardPresenter.setOnRetryClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        // Retry streaming.
                        final String sensorId =
                                sensorCardPresenter.getSelectedSensorId();
                        if (sensorId != null && mSensorCardAdapter.getSensorCardPresenters()
                                .contains(sensorCardPresenter)) {
                            sensorCardPresenter.stopObserving();
                            tryStartObserving(sensorCardPresenter, sensorId, true);
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

    private void updateAvailableSensors() {
        if (mSensorCardAdapter == null) {
            return;
        }
        Set<String> availableSensors = getAvailableSources();
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
            presenter.updateAvailableSensors(availableSensors);
        }

        updateSensorCount();
    }

    private void launchLabelAdd(long timestamp) {
        String currentRunId = getCurrentRunId();
        final AddNoteDialog dialog = AddNoteDialog.newInstance(timestamp, currentRunId,
                mSelectedExperiment.getExperimentId(),
                isRecording() ? R.string.add_run_note_placeholder_text :
                        R.string.add_experiment_note_placeholder_text);

        dialog.show(getChildFragmentManager(), AddNoteDialog.TAG);
    }

    @Override
    public void onAddNoteTimestampClicked(GoosciLabelValue.LabelValue selectedValue, int labelType,
            long selectedTimestamp) {
        // Do nothing. Timestamp will not be shown and this is unused.
    }

    @Override
    public LoggingConsumer<Label> onLabelAdd(Label label) {
        if (label instanceof PictureLabel) {
            // We want to set the time stamp of this label to the time of the picture
            PictureLabel pictureLabel = (PictureLabel) label;
            File file = new File(pictureLabel.getAbsoluteFilePath());
            // Check to make sure this value is not crazy: should be within 10 minutes of now and
            // not from the future.
            long delta = System.currentTimeMillis() - file.lastModified();
            if (delta < TimeUnit.MINUTES.toMillis(10) && delta > 0) {
                label.setTimestamp(file.lastModified());
            }
        }
        return new LoggingConsumer<Label>(TAG, "store label") {
            @Override
            public void success(Label value) {
                processAddedLabel(value);
            }
        };
    }

    private void processAddedLabel(Label label) {
        refreshLabels();
        ensureUnarchived(mSelectedExperiment, mSelectedProject, getDataController());
        playAddNoteAnimation();
        // Trigger labels are logged in RecorderControllerImpl.
        if (!(label instanceof SensorTriggerLabel)) {
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

    private void lockUiForRecording(AppCompatActivity activity) {
        // Lock the toolbar spinner
        ActionBar actionBar = activity.getSupportActionBar();
        Toolbar toolbar = (Toolbar) activity.findViewById(R.id.toolbar);
        actionBar.invalidateOptionsMenu();
        int toolbarColorResource = R.color.recording_toolbar_color;
        int statusBarColorResource = R.color.recording_status_bar_color;
        mSpinner.setVisibility(View.GONE);
        actionBar.setTitle(getExperimentName());
        actionBar.setDisplayShowTitleEnabled(true);
        actionBar.setSubtitle(R.string.recording_title_label);
        updateToolbarRecordingUi(activity, toolbar, toolbarColorResource,
                statusBarColorResource);

        // Lock the sensor cards and add button
        if (mSensorCardAdapter != null) {
            mSensorCardAdapter.setUiLockedForRecording(true);
        }
    }

    private void updateRecordingUIState() {
        if (mRecordButton != null) {
            mRecordButton.setEnabled(true);
        }

        AppCompatActivity activity = ((AppCompatActivity) getActivity());
        if (activity == null || mSelectedExperiment == null) {
            return;
        }

        if (isRecording()) {
            lockUiForRecording(activity);
            mAddButton.setContentDescription(
                    getResources().getString(R.string.btn_add_run_note_description));
            mRecordButton.setContentDescription(getResources().getString(
                    R.string.btn_stop_description));
            mRecordButton.setImageDrawable(getResources().getDrawable(
                    R.drawable.ic_recording_stop_36dp));
        } else {
            ActionBar actionBar = activity.getSupportActionBar();
            Toolbar toolbar = (Toolbar) activity.findViewById(R.id.toolbar);
            actionBar.invalidateOptionsMenu();
            int toolbarColorResource = R.color.color_primary;
            int statusBarColorResource = R.color.color_primary_dark;
            updateToolbarRecordingUi(activity, toolbar, toolbarColorResource,
                    statusBarColorResource);
            mSpinner.setVisibility(View.VISIBLE);
            actionBar.setDisplayShowTitleEnabled(false);
            actionBar.setSubtitle(null);
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

    private void updateToolbarRecordingUi(Activity activity, Toolbar toolbar,
            int toolbarColorResource, int statusBarColorResource) {
        // Update the toolbar and status bar colors.
        toolbar.setBackgroundResource(toolbarColorResource);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            Window window = activity.getWindow();
            if (statusBarColorResource == R.color.color_primary_dark) {
                window.addFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
            } else {
                // For any color that is not the default, need to clear this flag so that we can
                // draw the right color.
                window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
            }
            window.setStatusBarColor(getResources().getColor(statusBarColorResource));
        }
    }

    private void onRecordingStopped(final String startLabelId) {
        RunReviewActivity.launch(RecordFragment.this.getActivity(), startLabelId, 0,
                true /* from record */, true /* create task */, null);
    }

    private void setControlButtonsEnabled(boolean enabled) {
        mRecordButton.setEnabled(enabled);
        mAddButton.setEnabled(enabled);
    }

    private void refreshLabels() {
        if (mSensorCardAdapter == null || mSelectedExperiment == null || mExternalAxis == null) {
            return;
        }
        getDataController().getLabelsForExperiment(mSelectedExperiment,
                new LoggingConsumer<List<Label>>(TAG, "retrieving labels") {
                    @Override
                    public void success(List<Label> labels) {
                        if (mSensorCardAdapter != null) {
                            for (SensorCardPresenter p :
                                    mSensorCardAdapter.getSensorCardPresenters()) {
                                p.refreshLabels(labels);
                            }
                        }
                        if (mExternalAxis != null) {
                            mExternalAxis.onLabelsChanged(labels);
                        }
                    }
                });
    }

    private void tryStartRecording(final RecorderController rc) {
        if (mSelectedExperiment == null || mSelectedProject == null) {
            return;
        }


        Intent mLaunchIntent;
        mLaunchIntent = MainActivity.launchIntent(getActivity(), R.id.navigation_item_observe);
        // This isn't currently used, but does ensure this intent doesn't match any other intent.
        // See b/31616891
        mLaunchIntent.setData(
                Uri.fromParts("observe", "experiment=" + mSelectedExperiment.getExperimentId(),
                        null));
        rc.startRecording(mLaunchIntent, mSelectedProject);
    }

    private void failedStartRecording(int stringId) {
        Snackbar bar = AccessibilityUtils.makeSnackbar(getView(),
                getActivity().getResources().getString(stringId), Snackbar.LENGTH_LONG);
        showSnackbar(bar);
    }

    private void tryStopRecording(final RecorderController rc) {
        rc.setCurrentSensorLayouts(buildCurrentLayouts());
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
                        getDataController());
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
        getDataController().updateSensorLayout(mSelectedExperiment.getExperimentId(), position,
                layout, new LoggingConsumer<Success>(TAG, "disable sensor triggers") {
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
    private void addExternalSensors(final List<GoosciSensorLayout.SensorLayout> layouts,
            final RecorderController rc) {
        if (mSelectedExperiment != null) {
            getDataController().getExternalSensorsByExperiment(
                    mSelectedExperiment.getExperimentId(),
                    new LoggingConsumer<List<ConnectableSensor>>(TAG,
                            "add external sensors") {
                        @Override
                        public void success(List<ConnectableSensor> sensors) {
                            updateExternalSensors(ConnectableSensor.makeMap(sensors));
                            setSensorPresenters(layouts, rc);
                        }
                    });
        } else {
            updateExternalSensors(Collections.<String, ExternalSensorSpec>emptyMap());
            setSensorPresenters(layouts, rc);
        }
    }

    private void updateExternalSensors(Map<String, ExternalSensorSpec> sensors) {
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
                // Activate the first sensor card so the feature discovery has a place to attach.
                if (mSensorCardAdapter != null &&
                        mSensorCardAdapter.getSensorCardPresenters().size() > 0) {
                    mSensorCardAdapter.getSensorCardPresenters().get(0).setActive(true, true);
                }
                scheduleFeatureDiscovery(sensorId);
            }
            Fragment hasBluetoothDialog =
                    getChildFragmentManager().findFragmentByTag(BluetoothDisabledDialog.TAG);
            if (hasBluetoothDialog == null) {
                boolean bluetoothEnabled = BluetoothAdapter.getDefaultAdapter().isEnabled();
                if (!bluetoothEnabled) {
                    // Show an alert to have the user turn on bluetooth.
                    BluetoothDisabledDialog dialog = new BluetoothDisabledDialog();
                    dialog.show(getChildFragmentManager(), BluetoothDisabledDialog.TAG);
                }
            }
        }
    }

    private void updateSensorCount() {
        mSensorCardAdapter.setAvailableSensorCount(
                mSensorRegistry.getAllSources().size());
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
        // Look for view with the tag.
        final View view = mSensorCardRecyclerView.getChildAt(0).findViewWithTag(
                sensorId);
        if (view != null) {
            SensorAppearance appearance = getSensorAppearanceProvider().getAppearance(
                    sensorId);
            mFeatureDiscoveryProvider.show(
                    FeatureDiscoveryProvider.FEATURE_NEW_EXTERNAL_SENSOR,
                    ((AppCompatActivity) getActivity()).getSupportFragmentManager(),
                    view,
                    new FeatureDiscoveryListener() {
                        @Override
                        public void onClick(String feature) {
                            view.performClick();
                        }
                    },
                    appearance.getIconDrawable(getActivity()));
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

    private void withRecorderController(Consumer<RecorderController> c) {
        AppSingleton.getInstance(getActivity()).withRecorderController(TAG, c);
    }

    /**
     * Ensures that the project and experiment are unarchived, in case we make a new run or label
     * on them.
     */
    public static void ensureUnarchived(Experiment experiment, Project project, DataController dc) {
        if (experiment != null) {
            if (experiment.isArchived()) {
                experiment.setArchived(false);
                dc.updateExperiment(experiment,
                        LoggingConsumer.<Success>expectSuccess(TAG, "Unarchiving experiment"));
            }
            if (project != null) {
                if (experiment.getProjectId().equals(project.getProjectId())) {
                    // Make sure we have the right project.
                    if (project.isArchived()) {
                        project.setArchived(false);
                        dc.updateProject(project,
                                LoggingConsumer.<Success>expectSuccess(TAG, "Unarchiving project"));
                    }
                } else {
                    throw new IllegalStateException("Selected project "
                            + project.getProjectId()
                            + " is not the right parent of selected experiment "
                            + experiment.getExperimentId()
                            + " (should be " + experiment.getProjectId() + ")");
                }
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

    private static class ExperimentsSpinnerAdapter extends ArrayAdapter<Experiment> {

        public ExperimentsSpinnerAdapter(Context context, ArrayList<Experiment> experiments) {
            super(context, R.layout.experiment_spinner_item, new ArrayList<>(experiments));
            // Add a "new experiment" placeholder which is null.
            add(null);
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            return getSpinnerView(position, convertView, parent, R.layout.experiment_spinner_item);
        }

        @Override
        public View getDropDownView(int position, View convertView, ViewGroup parent) {
            return getSpinnerView(position, convertView, parent,
                    R.layout.experiment_spinner_dropdown_item);
        }

        private View getSpinnerView(int position, View convertView, ViewGroup parent,
                int resource) {
            Experiment experiment = getItem(position);
            if (convertView == null) {
                convertView = LayoutInflater.from(parent.getContext()).inflate(resource, null);
            }
            TextView textView = (TextView) convertView.findViewById(R.id.experiment_title);
            if (isNewExperimentPlaceholder(position)) {
                textView.setText(R.string.new_experiment_spinner_item);
            } else {
                textView.setText(experiment.getDisplayTitle(parent.getContext()));
            }
            return convertView;
        }

        public boolean isNewExperimentPlaceholder(int position) {
            return getItem(position) == null;
        }

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
