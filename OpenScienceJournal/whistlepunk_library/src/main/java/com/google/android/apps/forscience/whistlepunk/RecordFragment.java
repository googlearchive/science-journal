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

import android.app.Activity;
import android.app.Fragment;
import android.content.Context;
import android.content.Intent;
import android.graphics.Rect;
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

import com.google.android.apps.forscience.ble.DeviceDiscoverer;
import com.google.android.apps.forscience.javalib.Consumer;
import com.google.android.apps.forscience.javalib.Success;
import com.google.android.apps.forscience.whistlepunk.analytics.TrackerConstants;
import com.google.android.apps.forscience.whistlepunk.data.GoosciSensorLayout;
import com.google.android.apps.forscience.whistlepunk.devicemanager.ConnectableSensor;
import com.google.android.apps.forscience.whistlepunk.featurediscovery.FeatureDiscoveryProvider;
import com.google.android.apps.forscience.whistlepunk.filemetadata.Experiment;
import com.google.android.apps.forscience.whistlepunk.filemetadata.Label;
import com.google.android.apps.forscience.whistlepunk.filemetadata.SensorTrigger;
import com.google.android.apps.forscience.whistlepunk.filemetadata.Trial;
import com.google.android.apps.forscience.whistlepunk.metadata.ExperimentSensors;
import com.google.android.apps.forscience.whistlepunk.metadata.GoosciLabel;
import com.google.android.apps.forscience.whistlepunk.metadata.GoosciSensorTriggerInformation
        .TriggerInformation;
import com.google.android.apps.forscience.whistlepunk.performance.PerfTrackerProvider;
import com.google.android.apps.forscience.whistlepunk.scalarchart.GraphOptionsController;
import com.google.android.apps.forscience.whistlepunk.scalarchart.ScalarDisplayOptions;
import com.google.android.apps.forscience.whistlepunk.sensorapi.DataViewOptions;
import com.google.android.apps.forscience.whistlepunk.sensorapi.NewOptionsStorage;
import com.google.android.apps.forscience.whistlepunk.sensorapi.ReadableSensorOptions;
import com.google.android.apps.forscience.whistlepunk.sensorapi.SensorChoice;
import com.google.android.apps.forscience.whistlepunk.sensorapi.SensorPresenter;
import com.google.android.apps.forscience.whistlepunk.sensorapi.SensorStatusListener;
import com.google.android.apps.forscience.whistlepunk.sensorapi.WriteableSensorOptions;
import com.google.android.apps.forscience.whistlepunk.sensors.DecibelSensor;
import com.google.android.apps.forscience.whistlepunk.wireapi.RecordingMetadata;
import com.google.common.base.Suppliers;
import com.google.common.collect.Lists;
import com.jakewharton.rxbinding2.view.RxView;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import io.reactivex.subjects.BehaviorSubject;

public class RecordFragment extends PanesToolFragment implements Handler.Callback,
        StopRecordingNoDataDialog.StopRecordingDialogListener, AudioSettingsDialog
                .AudioSettingsDialogListener {
    private static final String TAG = "RecordFragment";

    private static final String KEY_SAVED_ACTIVE_SENSOR_CARD = "savedActiveCardIndex";
    private static final String KEY_SAVED_RECYCLER_LAYOUT = "savedRecyclerLayout";
    private static final String KEY_EXPERIMENT_ID = "experimentId";
    private static final String KEY_INFLATE_MENU = "inflateMenu";

    private static final int DEFAULT_CARD_VIEW = GoosciSensorLayout.SensorLayout.METER;
    private static final boolean DEFAULT_AUDIO_ENABLED = false;
    private static final boolean DEFAULT_SHOW_STATS_OVERLAY = false;

    // TODO: this is never written.  Remove this logic
    private static final String EXTRA_SENSOR_IDS = "sensorIds";

    private static final int MSG_SHOW_FEATURE_DISCOVERY = 111;
    private final SnackbarManager mSnackbarManager = new SnackbarManager();

    public static abstract class UICallbacks {
        public static UICallbacks NULL = new UICallbacks() {};

        /**
         * Called when recording is about to start
         *
         * @param experimentName the name of the experiment we're recording in.
         * @param userInitiated whether the user requested this recording (vs a trigger)
         */
        void onRecordingRequested(String experimentName, boolean userInitiated) {

        }

        /**
         * Called when recording starts
         * @param recordingStatus the current recording status
         */
        void onRecordingStart(RecordingStatus recordingStatus) {

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
         * Called when the record fragment wants to be maximized.
         */
        void maximizeFragment() {

        }
    }

    public interface CallbacksProvider {
        UICallbacks getRecordFragmentCallbacks();
    }

    private UICallbacks mUICallbacks = UICallbacks.NULL;
    private SensorRegistry mSensorRegistry;

    private SensorSettingsController mSensorSettingsController;
    private GraphOptionsController mGraphOptionsController;
    private ColorAllocator mColorAllocator;
    private ScalarDisplayOptions mScalarDisplayOptions;
    private int mInitialActiveCardIndex = -1;

    private LinearLayoutManager mSensorCardLayoutManager;
    private RecyclerView mSensorCardRecyclerView;
    private SensorCardAdapter mSensorCardAdapter;
    private ExternalAxisController mExternalAxis;
    // Stores the rect of the panel.
    private Rect mPanelRect = new Rect();

    private Experiment mSelectedExperiment;
    private BehaviorSubject<Experiment> mSelectedExperimentSubject = BehaviorSubject.create();

    // TODO: update RecorderController.watchRecordingStatus to replace the fine-grained status
    //       updates currently recorded here.
    // Subscribe to get whether we are currently recording, and future updates.
    private BehaviorSubject<RecordingStatus> mRecordingStatus =
            BehaviorSubject.createDefault(RecordingStatus.UNCONNECTED);

    // A temporary variable to store a sensor card presenter that wants to use
    // the decibel sensor before the permission to use microphone is granted
    // in Android M.
    private SensorCardPresenter mDecibelSensorCardPresenter;

    private Handler mHandler;
    private FeatureDiscoveryProvider mFeatureDiscoveryProvider;

    /**
     * Most recent result from {@link RecorderController#pauseObservingAll()}, which must be passed
     * when we resume
     */
    private String mRecorderPauseId;
    private boolean mRecordingWasCanceled;
    private int mTriggerFiredListenerId = RecorderController.NO_LISTENER_ID;

    RxEvent mUiStop = new RxEvent();
    RxEvent mContextDetach = new RxEvent();

    public static RecordFragment newInstance(String experimentId, boolean inflateMenu) {
        RecordFragment fragment = new RecordFragment();
        Bundle args = new Bundle();
        args.putBoolean(KEY_INFLATE_MENU, inflateMenu);
        args.putString(KEY_EXPERIMENT_ID, experimentId);
        fragment.setArguments(args);
        return fragment;
    }

    public RecordFragment() {
        whenVisibilityGained().subscribe(o -> startUI());
        whenVisibilityLost().subscribe(o -> stopUI());
    }

    @Override
    public void onDetach() {
        mUICallbacks = UICallbacks.NULL;
        mContextDetach.onHappened();
        super.onDetach();
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (savedInstanceState != null) {
            mInitialActiveCardIndex = savedInstanceState.getInt(KEY_SAVED_ACTIVE_SENSOR_CARD, -1);
        }

        mColorAllocator = new ColorAllocator(getResources().getIntArray(
                R.array.graph_colors_array).length);
        mSensorRegistry = AppSingleton.getInstance(getActivity()).getSensorRegistry();
        mScalarDisplayOptions = new ScalarDisplayOptions();

        mHandler = new Handler(this);
        mFeatureDiscoveryProvider = WhistlePunkApplication.getFeatureDiscoveryProvider(
                getActivity());

        setHasOptionsMenu(true);
    }

    private void onAudioPermissionChanged(@PermissionUtils.PermissionState int newState) {
        if (mDecibelSensorCardPresenter == null || getActivity() == null) {
            return;
        }
        if (newState == PermissionUtils.GRANTED) {
            mDecibelSensorCardPresenter.retryConnection(getActivity());
        } else if (newState == PermissionUtils.DENIED) {
            // If the sensor can't be loaded, still show it as selected on the card
            // so the user understands that they wanted this sensor but can't use it.
            mDecibelSensorCardPresenter.setConnectingUI(DecibelSensor.ID, true,
                    getActivity(), true);
        } else {
            mDecibelSensorCardPresenter.setConnectingUI(DecibelSensor.ID, true,
                    getActivity(), false);
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
        super.onPause();
    }

    private void stopUI() {
        mExternalAxis.onPauseLiveAxis();
        cancelFeatureDiscovery();
        if (mSensorCardAdapter != null) {
            mSensorCardAdapter.onPause();
        }
        RecorderController rc = getRecorderController();
        mRecorderPauseId = rc.pauseObservingAll();
        mUiStop.onHappened();
        if (mTriggerFiredListenerId != RecorderController.NO_LISTENER_ID) {
            rc.removeTriggerFiredListener(mTriggerFiredListenerId);
            mTriggerFiredListenerId = RecorderController.NO_LISTENER_ID;
        }
        freezeLayouts();
    }

    @Override
    public void onResume() {
        super.onResume();

        // Reload sensor appearances in case they have changed while away from this fragment,
        getSensorAppearanceProvider().loadAppearances(
                LoggingConsumer.<Success>expectSuccess(TAG, "Load appearances"));
    }

    private void startUI() {
        final PerfTrackerProvider perfTracker =
                WhistlePunkApplication.getPerfTrackerProvider(getActivity());
        perfTracker.startJankRecorder(TrackerConstants.PRIMES_OBSERVE);
        mUiStop.happensNext()
               .subscribe(() -> perfTracker.stopJankRecorder(TrackerConstants.PRIMES_OBSERVE));
        mExternalAxis.onResumeLiveAxis();

        mRecordingStatus.onNext(RecordingStatus.UNCONNECTED);

        RecorderController rc = getRecorderController();
        rc.watchRecordingStatus()
          .takeUntil(mUiStop.happens())
          .subscribe(this::onNewRecordingStatus);

        AppSingleton.getInstance(getActivity())
                    .whenLabelsAdded()
                    .takeUntil(mUiStop.happens())
                    .subscribe(event -> processAddedLabel(event));

        RecorderController.TriggerFiredListener tlistener =
                new RecorderController.TriggerFiredListener() {
                    @Override
                    public void onTriggerFired(SensorTrigger trigger) {
                        doVisualAlert(trigger);
                    }

                    @Override
                    public void onRequestStartRecording() {
                        lockUiForRecording(/* is starting */ true, /* user initiated */ false);
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

        RxDataController.getExperimentById(getDataController(), getExperimentId()).subscribe(
                selectedExperiment -> {
                    mRecordingStatus.firstElement().subscribe(status -> {
                        if (!readSensorsFromExtras(rc)) {
                            // By spec, newExperiments should always be non-zero
                            onSelectedExperimentChanged(selectedExperiment, rc, status);
                        }
                    });
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

        mRecordingStatus.firstElement().subscribe(status -> {
            List<GoosciSensorLayout.SensorLayout> layouts =
                    CommandLineSpecs.buildLayouts(sensorIds, RecordFragment.this.getResources());
            setSensorPresenters(layouts, rc, status);
        });
        return true;
    }

    private void onNewRecordingStatus(RecordingStatus newStatus) {
        RecordingStatus oldStatus = mRecordingStatus.getValue();

        // TODO: clean up the logic here.  Can we insert a new "STOP_SUCCEEDED" status so we
        // don't have to track old values?
        RecordingMetadata prevRecording = oldStatus.currentRecording;
        mRecordingStatus.onNext(newStatus);

        onRecordingMetadataUpdated(newStatus);
        updateRecordingUIState(newStatus);

        // If we have switched from a recording state to a not-recording
        // state, update the UI.
        if (prevRecording != null && !newStatus.isRecording()) {
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
    public void onDestroyPanesView() {
        // TODO: extract presenter with lifespan identical to the views.
        if (mSensorCardAdapter != null) {
            mSensorCardAdapter.onDestroy();
            mSensorCardAdapter = null;
        }

        if (mExternalAxis != null) {
            mExternalAxis.destroy();
        }
    }

    private List<GoosciSensorLayout.SensorLayout> safeSaveCurrentLayouts() {
        if (mSelectedExperiment == null) {
            return Collections.EMPTY_LIST;
        }

        // TODO: re-route data to make this impossible
        List<GoosciSensorLayout.SensorLayout> layouts = saveCurrentExperiment();
        if (layouts == null) {
            return Collections.EMPTY_LIST;
        } else {
            return layouts;
        }
    }

    private void freezeLayouts() {
        List<GoosciSensorLayout.SensorLayout> layouts = safeSaveCurrentLayouts();
        // Freeze layouts to be saved if recording finishes
        getRecorderController().setLayoutSupplier(Suppliers.ofInstance(layouts));
    }

    @Override
    public void onDestroy() {
        mRecordingStatus.firstElement().subscribe(status -> {
            // TODO: tying the lifespan of the bleclient to the lifespan of this fragment makes
            //       little sense
            if (!status.isRecording()) {
                AppSingleton.getInstance(getActivity()).destroyBleClient();
            }
        });
        stopObservingCurrentSensors();
        mSnackbarManager.onDestroy();
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
    public View onCreatePanesView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        final ViewGroup rootView = (ViewGroup) inflater.inflate(R.layout.fragment_record, container,
                false);

        View resetButton = rootView.findViewById(R.id.btn_reset);
        ExternalAxisView axisView = (ExternalAxisView) rootView.findViewById(R.id.external_x_axis);
        mExternalAxis = new ExternalAxisController(axisView,
                (xMin, xMax, isPinnedToNow) -> {
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
                }, /* IsLive */ true, new CurrentTimeClock(), resetButton);

        mGraphOptionsController.loadIntoScalarDisplayOptions(mScalarDisplayOptions, getView());
        mSensorCardLayoutManager = new LinearLayoutManager(getActivity());

        if (savedInstanceState != null) {
            mSensorCardLayoutManager.onRestoreInstanceState(
                    savedInstanceState.getParcelable(KEY_SAVED_RECYCLER_LAYOUT));
        }

        return rootView;
    }

    public void setRecordingTimeUpdateListener(
            ExternalAxisController.RecordingTimeUpdateListener listener) {
        if (mExternalAxis != null) {
            mExternalAxis.setRecordingTimeUpdateListener(listener);
        }
    }

    private boolean shouldInflateMenu() {
        return getArguments().getBoolean(KEY_INFLATE_MENU, true);
    }

    private String getExperimentId() {
        return getArguments().getString(KEY_EXPERIMENT_ID);
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

    private void onSelectedExperimentChanged(final Experiment selectedExperiment,
            final RecorderController rc, RecordingStatus status) {
        if (!TextUtils.equals(Experiment.getExperimentId(selectedExperiment),
                Experiment.getExperimentId(mSelectedExperiment))) {
            saveCurrentExperiment();
        }
        stopObservingCurrentSensors();
        mSelectedExperiment = selectedExperiment;
        mSelectedExperimentSubject.onNext(selectedExperiment);
        loadIncludedSensors(mSelectedExperiment.getSensorLayouts(), rc, status);
        rc.setSelectedExperiment(mSelectedExperiment);
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
            RecorderController rc, RecordingStatus status) {
        if (layouts == null || layouts.size() == 0) {
            layouts = Lists.newArrayList(defaultLayout(mColorAllocator,
                    mSensorCardAdapter == null ? null : mSensorCardAdapter.getUsedColors()));
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
            createSensorCardPresenters(view, layouts, rc, status);
            updateAvailableSensors();
            for (SensorCardPresenter presenter : mSensorCardAdapter.getSensorCardPresenters()) {
                presenter.initializeSensorSelection();
                // If we resume while recording, assume that the sensors are already connected.
                if (status.isRecording()) {
                    presenter.onSourceStatusUpdate(presenter.getSelectedSensorId(),
                            SensorStatusListener.STATUS_CONNECTED);
                }
            }
        }
    }

    static GoosciSensorLayout.SensorLayout defaultLayout(ColorAllocator colorAllocator,
            int[] usedColors) {
        return defaultLayout(colorAllocator.getNextColor(usedColors));
    }

    public static GoosciSensorLayout.SensorLayout defaultLayout(int colorIndex) {
        GoosciSensorLayout.SensorLayout layout = new GoosciSensorLayout.SensorLayout();
        layout.sensorId = null;
        layout.cardView = DEFAULT_CARD_VIEW;
        layout.audioEnabled = DEFAULT_AUDIO_ENABLED;
        layout.showStatsOverlay = DEFAULT_SHOW_STATS_OVERLAY;
        layout.colorIndex = colorIndex;
        return layout;
    }

    // TODO: can all of this logic live in a separate, testable class?
    private void createSensorCardPresenters(ViewGroup rootView,
            List<GoosciSensorLayout.SensorLayout> layouts, final RecorderController rc,
            RecordingStatus status) {
        List<SensorCardPresenter> sensorCardPresenters = new ArrayList<>();

        if (layouts.isEmpty()) {
            layouts = Lists.newArrayList(defaultLayout(mColorAllocator,
                    mSensorCardAdapter.getUsedColors()));
        }
        // Create a sensorData card for each initial source tag, or at minimum one if no source
        // tags are saved in the bundle.
        for (int i = 0; i < layouts.size(); i++) {
            GoosciSensorLayout.SensorLayout layout = layouts.get(i);
            final SensorCardPresenter sensorCardPresenter = createSensorCardPresenter(layout, rc);
            sensorCardPresenter.setInitialSourceTagToSelect(layout.sensorId);
            sensorCardPresenters.add(sensorCardPresenter);
            tryStartObserving(sensorCardPresenter, layout.sensorId, status);
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
                                    defaultLayout(mColorAllocator,
                                            mSensorCardAdapter.getUsedColors()), rc);
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

        rc.setLayoutSupplier(() -> buildCurrentLayouts());

        updateSensorCount();
        mSensorCardAdapter.setRecording(status.isRecording(), status.getRecordingStartTime());
        long resetTime = mExternalAxis.resetAxes();
        if (mSensorCardAdapter != null) {
            long ignoreDataBefore = status.isRecording() ? -1 : resetTime;
            mSensorCardAdapter.onResume(ignoreDataBefore);
        }

        refreshLabels(status);

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

        // Figure out the optimal height of a sensor presenter when the recycler view is laid out;
        // only do this on first layout
        RxView.globalLayouts(mSensorCardRecyclerView).firstElement().subscribe(o -> {
            if (getActivity() == null || mSensorCardAdapter == null) {
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

            // TODO: one time, I saw a crash here.  Can we prevent it more gracefully?
            if (mSensorCardAdapter != null) {
                mSensorCardAdapter.setSingleCardPresenterHeight(
                        Math.max(optimalHeight, minHeight));
            }
        });
        mSensorCardRecyclerView.getViewTreeObserver().addOnGlobalLayoutListener(
                () -> adjustSensorCardAddAlpha());
    }

    private List<String> getAllIncludedSources() {
        return nonEmptySensorList(
                Lists.newArrayList(mSensorRegistry.getIncludedSources()));
    }

    private List<String> getAvailableSources() {
        // TODO: test this?
        String[] selected = mSensorCardAdapter.getSensorTags();
        List<String> sources = mSensorRegistry.getIncludedSources();
        sources.removeAll(Lists.newArrayList(selected));
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
        View bottomPanel = getView().findViewById(R.id.bottom_panel);
        if (bottomPanel != null) {
            bottomPanel.getHitRect(mPanelRect);
        }
        mSensorCardAdapter.adjustAddViewAlpha(mPanelRect);
    }

    private SensorCardPresenter createSensorCardPresenter(GoosciSensorLayout.SensorLayout layout,
            final RecorderController rc) {
        final SensorCardPresenter sensorCardPresenter = new SensorCardPresenter(
                new DataViewOptions(layout.colorIndex, getActivity(), mScalarDisplayOptions),
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
                        mSnackbarManager.showSnackbar(bar);
                    }
                }
            }
        };
        sensorCardPresenter.setSensorStatusListener(sensorStatusListener);
        // TODO: make this externally testable.
        sensorCardPresenter.setOnRetryClickListener(
                v -> {
                    // Retry streaming.
                    final String sensorId = sensorCardPresenter.getSelectedSensorId();
                    if (sensorId != null && mSensorCardAdapter.getSensorCardPresenters()
                            .contains(sensorCardPresenter)) {
                        sensorCardPresenter.retryConnection(getActivity());
                    }
                });

        sensorCardPresenter.setOnSensorSelectedListener(
                sensorId -> {
                    sensorCardPresenter.stopObserving();
                    mRecordingStatus.firstElement().subscribe(status -> {
                        tryStartObserving(sensorCardPresenter, sensorId, status);
                    });
                });
        sensorCardPresenter.setAppearanceProvider(getSensorAppearanceProvider());

        return sensorCardPresenter;
    }

    private void tryStartObserving(SensorCardPresenter sensorCardPresenter, String sensorId,
            RecordingStatus status) {
        if (TextUtils.equals(sensorId, DecibelSensor.ID) && mDecibelSensorCardPresenter == null &&
                !PermissionUtils.hasPermission(getActivity(),
                        PermissionUtils.REQUEST_RECORD_AUDIO)) {
            mDecibelSensorCardPresenter = sensorCardPresenter;
            sensorCardPresenter.setConnectingUI(DecibelSensor.ID, true,
                    getActivity().getApplicationContext(), true);
            PermissionUtils.tryRequestingPermission(getActivity(),
                    PermissionUtils.REQUEST_RECORD_AUDIO,
                    new PermissionUtils.PermissionListener() {
                        @Override
                        public void onPermissionGranted() {
                            onAudioPermissionChanged(PermissionUtils.GRANTED);
                        }

                        @Override
                        public void onPermissionDenied() {
                            onAudioPermissionChanged(PermissionUtils.DENIED);
                        }

                        @Override
                        public void onPermissionPermanentlyDenied() {
                            onAudioPermissionChanged(PermissionUtils.PERMANENTLY_DENIED);
                        }
                    });
        }
        startSensorCardObserving(sensorCardPresenter, sensorId, status);
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

    private void processAddedLabel(AddedLabelEvent event) {
        Label label = event.getLabel();
        RecordingStatus status = event.getStatus();
        refreshLabels(status);
        ensureUnarchived(mSelectedExperiment, getDataController());
        // Trigger labels are logged in RecorderControllerImpl.
        if (!(label.getType() == GoosciLabel.Label.SENSOR_TRIGGER)) {
            String trackerLabel = status.isRecording() ? TrackerConstants.LABEL_RECORD :
                    TrackerConstants.LABEL_OBSERVE;
            WhistlePunkApplication.getUsageTracker(getActivity())
                    .trackEvent(TrackerConstants.CATEGORY_NOTES,
                            TrackerConstants.ACTION_CREATE,
                            trackerLabel,
                            TrackerConstants.getLabelValueType(label));
        }
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

    private void onRecordingMetadataUpdated(RecordingStatus status) {
        if (status.isRecording()) {
            mExternalAxis.resetAxes();
            mExternalAxis.onStartRecording(status.currentRecording.getStartTime());
        } else {
            mExternalAxis.onStopRecording();
        }
        refreshLabels(status);
        if (mSensorCardAdapter != null) {
            mSensorCardAdapter.setRecording(status.isRecording(),
                    RecordingMetadata.getStartTime(status.currentRecording));
            if (!status.isRecording()) {
                adjustSensorCardAddAlpha();
            }
        }
        updateRecordingUIState(status);
    }

    private String getExperimentName() {
        if (mSelectedExperiment != null) {
            return mSelectedExperiment.getDisplayTitle(getActivity());
        } else {
            return "";
        }
    }

    /**
     * Locks the UI to user imput in order to do a recording state change.
     * @param isStarting whether recording is just starting.
     */
    private void lockUiForRecording(boolean isStarting, boolean userInitiated) {
        if (isStarting) {
            mUICallbacks.onRecordingRequested(getExperimentName(), userInitiated);
        }

        // Lock the sensor cards and add button
        if (mSensorCardAdapter != null) {
            mSensorCardAdapter.setUiLockedForRecording(true);
        }
    }

    private void updateRecordingUIState(RecordingStatus status) {
        if (mSelectedExperiment == null) {
            return;
        }

        if (status.state == RecordingState.STARTING) {
            lockUiForRecording(/* state is STARTING */ true, status.userInitiated);
        } else if (status.isRecording())   {
            lockUiForRecording(/* state is not STARTING */ false, status.userInitiated);
            mUICallbacks.onRecordingStart(status);
        } else {
            mUICallbacks.onRecordingStopped();
            if (mSensorCardAdapter != null) {
                mSensorCardAdapter.setUiLockedForRecording(false);
            }
        }
    }

    private void refreshLabels(RecordingStatus status) {
        if (mSensorCardAdapter == null || mExternalAxis == null) {
            return;
        }
        List<Label> labels;
        if (!status.isRecording()) {
            labels = Collections.emptyList();
        } else {
            Trial trial = mSelectedExperiment.getTrial(status.currentRecording.getRunId());
            if (trial == null) {
                return;
            }
            labels = trial.getLabels();
        }
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

    @Override
    public void requestCancelRecording() {
        mRecordingWasCanceled = true;
        getRecorderController().stopRecordingWithoutSaving();
    }

    private DataController getDataController() {
        return AppSingleton.getInstance(getActivity()).getDataController();
    }

    private void startSensorCardObserving(SensorCardPresenter sensorCardPresenter, String sensorId,
            RecordingStatus status) {
        startObserving(sensorId, sensorCardPresenter);
        sensorCardPresenter.setRecording(status.getRecordingStartTime());
        mExternalAxis.resetAxes();
        updateSensorLayout(sensorCardPresenter.buildLayout());
        updateAvailableSensors();
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
                final SensorPresenter sensorPresenter =
                        sensorChoice.createPresenter(sensorCardPresenter.getDataViewOptions(),
                                numberFormat, stats -> sensorCardPresenter.updateStats(stats));

                final WriteableSensorOptions options = sensorCardPresenter.getCardOptions(
                        sensorChoice, context).load(
                        new NewOptionsStorage.SnackbarFailureListener(getView()));
                ReadableSensorOptions readOptions = options.getReadOnly();
                sensorPresenter.getOptionsPresenter().applyOptions(readOptions);

                sensorCardPresenter.startObserving(sensorChoice, sensorPresenter, readOptions,
                        mSelectedExperiment, mSensorRegistry);
                mRecordingStatus.firstElement().subscribe(status -> refreshLabels(status));
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
                        getRecorderController().clearSensorTriggers(layout.sensorId,
                                mSensorRegistry);
                        presenter.onSensorTriggersCleared();
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
        if (mSnackbarManager.snackbarIsVisible()) {
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
            bar.setAction(R.string.scroll_to_card, v -> {
                mUICallbacks.maximizeFragment();
                mSensorCardLayoutManager.scrollToPosition(getPositionOfPresenter(presenter));
            });
            mSnackbarManager.showSnackbar(bar);
        }
    }

    /**
     * Get list of existing devices.
     */
    private void loadIncludedSensors(final List<GoosciSensorLayout.SensorLayout> layouts,
            final RecorderController rc, RecordingStatus status) {
        if (mSelectedExperiment != null) {
            getDataController().getExternalSensorsByExperiment(
                    mSelectedExperiment.getExperimentId(),
                    new LoggingConsumer<ExperimentSensors>(TAG,
                            "add external sensors") {
                        @Override
                        public void success(ExperimentSensors sensors) {
                            mSensorRegistry.setExcludedIds(sensors.getExcludedSensorIds());
                            updateExternalSensors(sensors.getIncludedSensors());
                            setSensorPresenters(layouts, rc, status);
                        }
                    });
        } else {
            updateExternalSensors(Collections.<ConnectableSensor>emptyList());
            setSensorPresenters(layouts, rc, status);
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

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        if (getActivity() == null) {
            return;
        }
        if (shouldInflateMenu()) {
            inflater.inflate(R.menu.menu_record, menu);
            boolean enableDevTools = DevOptionsFragment.isDevToolsEnabled(getActivity());
            menu.findItem(R.id.action_graph_options).setVisible(false);  // b/29771945
            menu.findItem(R.id.action_level).setVisible(enableDevTools);
            menu.findItem(R.id.action_ruler).setVisible(enableDevTools);
        }
        super.onCreateOptionsMenu(menu, inflater);
    }

    @Override
    public void onPrepareOptionsMenu(final Menu menu) {
        if (shouldInflateMenu()) {
            mRecordingStatus.firstElement().subscribe(status -> {
                MenuItem addSensors = menu.findItem(R.id.btn_add_sensors);
                if (addSensors != null) {
                    addSensors.setVisible(!status.isRecording());
                }
                MenuItem expDetails = menu.findItem(R.id.btn_experiment_details);
                if (expDetails != null) {
                    expDetails.setVisible(!status.isRecording());
                }
            });
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // TODO: This whole menu is never shown. Delete this code.
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_graph_options) {
            mGraphOptionsController.launchOptionsDialog(mScalarDisplayOptions,
                    new NewOptionsStorage.SnackbarFailureListener(getView()));
        } else if (id == R.id.btn_experiment_details) {
            if (mSelectedExperiment != null) {
                PanesActivity.launch(getActivity(), mSelectedExperiment.getExperimentId());
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

    private RecorderController getRecorderController() {
        // TODO: don't depend on activity here?
        return AppSingleton.getInstance(getActivity()).getRecorderController();
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
}
