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
import android.content.Context;
import android.content.Intent;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import android.text.TextUtils;
import android.transition.Slide;
import android.util.Log;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import com.google.android.apps.forscience.ble.DeviceDiscoverer;
import com.google.android.apps.forscience.javalib.Consumer;
import com.google.android.apps.forscience.javalib.Success;
import com.google.android.apps.forscience.whistlepunk.accounts.AppAccount;
import com.google.android.apps.forscience.whistlepunk.actionarea.ActionAreaItem;
import com.google.android.apps.forscience.whistlepunk.actionarea.ActionAreaView;
import com.google.android.apps.forscience.whistlepunk.actionarea.ActionAreaView.ActionAreaListener;
import com.google.android.apps.forscience.whistlepunk.analytics.TrackerConstants;
import com.google.android.apps.forscience.whistlepunk.devicemanager.ConnectableSensor;
import com.google.android.apps.forscience.whistlepunk.featurediscovery.FeatureDiscoveryProvider;
import com.google.android.apps.forscience.whistlepunk.filemetadata.Experiment;
import com.google.android.apps.forscience.whistlepunk.filemetadata.Label;
import com.google.android.apps.forscience.whistlepunk.filemetadata.SensorLayoutPojo;
import com.google.android.apps.forscience.whistlepunk.filemetadata.SensorTrigger;
import com.google.android.apps.forscience.whistlepunk.filemetadata.Trial;
import com.google.android.apps.forscience.whistlepunk.metadata.ExperimentSensors;
import com.google.android.apps.forscience.whistlepunk.metadata.GoosciLabel;
import com.google.android.apps.forscience.whistlepunk.metadata.GoosciSensorTriggerInformation.TriggerInformation;
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
import com.google.android.apps.forscience.whistlepunk.sensors.PitchSensor;
import com.google.android.apps.forscience.whistlepunk.wireapi.RecordingMetadata;
import com.google.android.material.snackbar.Snackbar;
import com.google.common.base.Suppliers;
import com.google.common.base.Throwables;
import com.google.common.collect.Lists;
import com.jakewharton.rxbinding2.view.RxView;
import io.reactivex.subjects.BehaviorSubject;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/** Fragment controlling adding sensor recordings in the ExperimentActivity. */
public class SensorFragment extends Fragment
    implements Handler.Callback,
        StopRecordingNoDataDialog.StopRecordingDialogListener,
        AudioSettingsDialog.AudioSettingsDialogListener,
        ActionAreaListener {
  private static final String TAG = "SensorFragment";

  private static final String KEY_SAVED_ACTIVE_SENSOR_CARD = "savedActiveCardIndex";
  private static final String KEY_SAVED_RECYCLER_LAYOUT = "savedRecyclerLayout";
  private static final String KEY_ACCOUNT_KEY = "accountKey";
  private static final String KEY_EXPERIMENT_ID = "experimentId";

  private static final int MSG_SHOW_FEATURE_DISCOVERY = 111;
  private final SnackbarManager snackbarManager = new SnackbarManager();

  public abstract static class UICallbacks {
    public static UICallbacks NULL = new UICallbacks() {};

    /**
     * Called when recording starts
     *
     * @param recordingStatus the current recording status
     */
    void onRecordingStart(RecordingStatus recordingStatus) {}

    /** Called when recording actually stops. Updates the UI to remove "recording" markers */
    void onRecordingStopped() {}

    /**
     * Called when a trial is fully saved and assigned a runId, so that we can update the UI
     * accordingly
     */
    void onRecordingSaved(String runId, Experiment experiment) {}
  }

  public interface CallbacksProvider {
    UICallbacks getRecordFragmentCallbacks();
  }

  private UICallbacks uICallbacks = UICallbacks.NULL;
  private SensorRegistry sensorRegistry;

  private SensorSettingsController sensorSettingsController;
  private GraphOptionsController graphOptionsController;
  private ColorAllocator colorAllocator;
  private ScalarDisplayOptions scalarDisplayOptions;
  private int initialActiveCardIndex = -1;

  private LinearLayoutManager sensorCardLayoutManager;
  private RecyclerView sensorCardRecyclerView;
  private SensorCardAdapter sensorCardAdapter;
  private ExternalAxisController externalAxis;
  // Stores the rect of the panel.
  private Rect panelRect = new Rect();

  private Experiment selectedExperiment;
  private BehaviorSubject<Experiment> selectedExperimentSubject = BehaviorSubject.create();

  // TODO: update RecorderController.watchRecordingStatus to replace the fine-grained status
  //       updates currently recorded here.
  // Subscribe to get whether we are currently recording, and future updates.
  private BehaviorSubject<RecordingStatus> recordingStatus =
      BehaviorSubject.createDefault(RecordingStatus.UNCONNECTED);

  // A temporary variable to store a sensor card presenter that wants to use the decibel or
  // frequency sensor before the permission to use microphone is granted in Android M.
  private SensorCardPresenter sensorCardPresenterForAudio;
  private String sensorIdForAudio;

  private Handler handler;
  private FeatureDiscoveryProvider featureDiscoveryProvider;

  private ControlBarController controlBarController;
  private ActionAreaView actionAreaView;

  /**
   * Most recent result from {@link RecorderController#pauseObservingAll()}, which must be passed
   * when we resume
   */
  private String recorderPauseId;

  private boolean recordingWasCanceled;
  private int triggerFiredListenerId = RecorderController.NO_LISTENER_ID;

  RxEvent uiStop = new RxEvent();
  RxEvent contextDetach = new RxEvent();

  public static SensorFragment newInstance(AppAccount appAccount, String experimentId) {
    SensorFragment fragment = new SensorFragment();
    Bundle args = new Bundle();
    args.putString(KEY_ACCOUNT_KEY, appAccount.getAccountKey());
    args.putString(KEY_EXPERIMENT_ID, experimentId);
    fragment.setArguments(args);
    return fragment;
  }

  @Override
  public void onDetach() {
    uICallbacks = UICallbacks.NULL;
    contextDetach.onHappened();
    super.onDetach();
  }

  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    if (savedInstanceState != null) {
      initialActiveCardIndex = savedInstanceState.getInt(KEY_SAVED_ACTIVE_SENSOR_CARD, -1);
    }

    colorAllocator =
        new ColorAllocator(getResources().getIntArray(R.array.graph_colors_array).length);
    sensorRegistry = AppSingleton.getInstance(getActivity()).getSensorRegistry();
    scalarDisplayOptions = new ScalarDisplayOptions();

    handler = new Handler(this);
    featureDiscoveryProvider =
        WhistlePunkApplication.getAppServices(getActivity()).getFeatureDiscoveryProvider();

    controlBarController =
        new ControlBarController(getAppAccount(), getExperimentId(), new SnackbarManager());

    setHasOptionsMenu(true);

    setEnterTransition(new Slide());
    setExitTransition(new Slide());
  }

  private void onAudioPermissionChanged(@PermissionUtils.PermissionState int newState) {
    if (sensorCardPresenterForAudio == null || getActivity() == null) {
      return;
    }
    if (newState == PermissionUtils.GRANTED) {
      sensorCardPresenterForAudio.retryConnection(getActivity());
    } else if (newState == PermissionUtils.DENIED) {
      // If the sensor can't be loaded, still show it as selected on the card
      // so the user understands that they wanted this sensor but can't use it.
      sensorCardPresenterForAudio.setConnectingUI(sensorIdForAudio, true, getActivity(), true);
    } else {
      sensorCardPresenterForAudio.setConnectingUI(sensorIdForAudio, true, getActivity(), false);
    }
    // in either case, we have our answer.  Stop waiting for it.
    sensorCardPresenterForAudio = null;
    updateAvailableSensors();
  }

  private SensorAppearanceProvider getSensorAppearanceProvider() {
    return AppSingleton.getInstance(getActivity()).getSensorAppearanceProvider(getAppAccount());
  }

  @Override
  public void onSaveInstanceState(Bundle outState) {
    super.onSaveInstanceState(outState);
    if (sensorCardAdapter == null) {
      return;
    }
    List<SensorCardPresenter> presenters = sensorCardAdapter.getSensorCardPresenters();

    int size = presenters.size();
    for (int i = 0; i < size; i++) {
      SensorCardPresenter presenter = presenters.get(i);
      if (presenter.isActive()) {
        outState.putInt(KEY_SAVED_ACTIVE_SENSOR_CARD, i);
      }
    }

    outState.putParcelable(
        KEY_SAVED_RECYCLER_LAYOUT, sensorCardLayoutManager.onSaveInstanceState());
  }

  @Override
  public void onPause() {
    saveCurrentExperiment();
    stopUI();
    super.onPause();
  }

  private void stopUI() {
    externalAxis.onPauseLiveAxis();
    cancelFeatureDiscovery();
    if (sensorCardAdapter != null) {
      sensorCardAdapter.onPause();
    }
    RecorderController rc = getRecorderController();
    recorderPauseId = rc.pauseObservingAll();
    uiStop.onHappened();
    if (triggerFiredListenerId != RecorderController.NO_LISTENER_ID) {
      rc.removeTriggerFiredListener(triggerFiredListenerId);
      triggerFiredListenerId = RecorderController.NO_LISTENER_ID;
    }
    freezeLayouts();
  }

  @Override
  public void onResume() {
    super.onResume();

    // Reload sensor appearances in case they have changed while away from this fragment,
    getSensorAppearanceProvider()
        .loadAppearances(LoggingConsumer.<Success>expectSuccess(TAG, "Load appearances"));
    startUI();
    if (isVisible()) {
      updateTitle();
    }
  }

  private void startUI() {
    final PerfTrackerProvider perfTracker =
        WhistlePunkApplication.getPerfTrackerProvider(getActivity());
    perfTracker.startJankRecorder(TrackerConstants.PRIMES_OBSERVE);
    uiStop
        .happensNext()
        .subscribe(() -> perfTracker.stopJankRecorder(TrackerConstants.PRIMES_OBSERVE));
    externalAxis.onResumeLiveAxis();

    recordingStatus.onNext(RecordingStatus.UNCONNECTED);

    RecorderController rc = getRecorderController();
    rc.watchRecordingStatus().takeUntil(uiStop.happens()).subscribe(this::onNewRecordingStatus);

    AppSingleton.getInstance(getActivity())
        .whenLabelsAdded(getAppAccount())
        .takeUntil(uiStop.happens())
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
          public void onRequestStopRecording(RecorderController rc) {}
        };
    triggerFiredListenerId = rc.addTriggerFiredListener(tlistener);

    if (!rc.resumeObservingAll(recorderPauseId)) {
      // Force a reload of the current experiment's ob
      this.selectedExperiment = null;
      if (selectedExperimentSubject.hasValue()) {
        selectedExperimentSubject.onComplete();
        selectedExperimentSubject = BehaviorSubject.create();
      }
    }

    RxDataController.getExperimentById(getDataController(), getExperimentId())
        .subscribe(
            selectedExperiment -> {
              recordingStatus
                  .firstElement()
                  .subscribe(status -> onSelectedExperimentChanged(selectedExperiment, rc, status));
            },
            // If this fails to load due to the experiment having been deleted on another
            // device, go ahead and silently catch the error so the entire app doesn't crash.
            // The app will display an empty box where the sensors would be and the user can
            // back out of the view to get back to an expected state. It is extremely unlikely
            // that a user will hit this case b/124102081
            error -> {
              if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "Failed to display sensors.", error);
              }
            });
  }

  private void onNewRecordingStatus(RecordingStatus newStatus) {
    RecordingStatus oldStatus = recordingStatus.getValue();

    // TODO: clean up the logic here.  Can we insert a new "STOP_SUCCEEDED" status so we
    // don't have to track old values?
    RecordingMetadata prevRecording = oldStatus.currentRecording;
    recordingStatus.onNext(newStatus);

    onRecordingMetadataUpdated(newStatus);
    updateRecordingUIState(newStatus);

    // If we have switched from a recording state to a not-recording
    // state, update the UI.
    if (prevRecording != null && !newStatus.isRecording()) {
      externalAxis.onStopRecording();
      AddNoteDialog dialog =
          (AddNoteDialog) getChildFragmentManager().findFragmentByTag(AddNoteDialog.TAG);
      if (dialog != null) {
        dialog.dismiss();
      }
      if (!recordingWasCanceled) {
        uICallbacks.onRecordingSaved(prevRecording.getRunId(), selectedExperiment);
      }
    }
    recordingWasCanceled = false;
  }

  private List<SensorLayoutPojo> safeSaveCurrentLayouts() {
    if (selectedExperiment == null) {
      return Collections.EMPTY_LIST;
    }

    // TODO: re-route data to make this impossible
    List<SensorLayoutPojo> layouts = saveCurrentExperiment();
    if (layouts == null) {
      return Collections.EMPTY_LIST;
    } else {
      return layouts;
    }
  }

  private void freezeLayouts() {
    List<SensorLayoutPojo> layouts = safeSaveCurrentLayouts();
    // Freeze layouts to be saved if recording finishes
    getRecorderController().setLayoutSupplier(Suppliers.ofInstance(layouts));
  }

  @Override
  public void onDestroy() {
    recordingStatus
        .firstElement()
        .subscribe(
            status -> {
              // TODO: tying the lifespan of the bleclient to the lifespan of this fragment makes
              //       little sense
              if (!status.isRecording()) {
                AppSingleton.getInstance(getActivity()).destroyBleClient();
              }
            });
    stopObservingCurrentSensors();
    snackbarManager.onDestroy();
    handler = null;
    sensorSettingsController = null;
    graphOptionsController = null;
    if (sensorRegistry != null) {
      sensorRegistry.removePendingOperations(TAG);
    }

    if (sensorCardAdapter != null) {
      sensorCardAdapter.onDestroy();
      sensorCardAdapter = null;
    }

    if (externalAxis != null) {
      externalAxis.destroy();
    }
    super.onDestroy();
  }

  public void stopObservingCurrentSensors() {
    if (sensorCardAdapter != null) {
      for (SensorCardPresenter presenter : sensorCardAdapter.getSensorCardPresenters()) {
        presenter.stopObserving();
      }
    }
  }

  @Override
  public View onCreateView(
      LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
    final ViewGroup rootView =
        (ViewGroup) inflater.inflate(R.layout.fragment_sensor, container, false);
    actionAreaView = rootView.findViewById(R.id.action_area);
    actionAreaView.setUpScrollListener(rootView.findViewById(R.id.sensor_card_recycler_view));

    View resetButton = rootView.findViewById(R.id.btn_reset);
    ExternalAxisView axisView = (ExternalAxisView) rootView.findViewById(R.id.external_x_axis);
    externalAxis =
        new ExternalAxisController(
            axisView,
            (xMin, xMax, isPinnedToNow) -> {
              if (sensorCardAdapter == null) {
                return;
              }
              List<SensorCardPresenter> sensorCardPresenters =
                  sensorCardAdapter.getSensorCardPresenters();
              for (SensorCardPresenter sensorCardPresenter : sensorCardPresenters) {
                SensorPresenter presenter = sensorCardPresenter.getSensorPresenter();
                if (presenter != null) {
                  presenter.onGlobalXAxisChanged(xMin, xMax, isPinnedToNow, getDataController());
                }
              }
            }, /* IsLive */
            true,
            new CurrentTimeClock(),
            resetButton);

    graphOptionsController.loadIntoScalarDisplayOptions(scalarDisplayOptions, getView());
    sensorCardLayoutManager = new LinearLayoutManager(getActivity());

    setUpTitle(rootView.findViewById(R.id.tool_pane_title_bar));

    androidx.cardview.widget.CardView record = rootView.findViewById(R.id.record);
    sensorCardRecyclerView = (RecyclerView) rootView.findViewById(R.id.sensor_card_recycler_view);
    NoteTakingActivity activity = (NoteTakingActivity) getActivity();
    boolean isTwoPane = activity != null && activity.isTwoPane();
    controlBarController.attachSensorFragmentView(
        record,
        getChildFragmentManager(),
        actionAreaView,
        this,
        sensorCardRecyclerView,
        rootView.findViewById(R.id.tool_pane_title_bar),
        isTwoPane);

    if (savedInstanceState != null) {
      sensorCardLayoutManager.onRestoreInstanceState(
          savedInstanceState.getParcelable(KEY_SAVED_RECYCLER_LAYOUT));
    }

    return rootView;
  }

  private void setUpTitle(View titleBarView) {
    NoteTakingActivity activity = (NoteTakingActivity) getActivity();
    if (activity != null) {
      if (activity.isTwoPane()) {
        ((TextView) titleBarView.findViewById(R.id.title_bar_text))
            .setText(R.string.action_bar_sensor_note);
        ((ImageView) titleBarView.findViewById(R.id.title_bar_icon))
            .setImageDrawable(
                getResources()
                    .getDrawable(
                        R.drawable.ic_sensor,
                        new ContextThemeWrapper(getActivity(), R.style.DefaultActionAreaIcon)
                            .getTheme()));
        titleBarView
            .findViewById(R.id.title_bar_close)
            .setOnClickListener(v -> activity.closeToolFragment());
      } else {
        titleBarView.setVisibility(View.GONE);
      }
    }
  }

  public void setRecordingTimeUpdateListener(
      ExternalAxisController.RecordingTimeUpdateListener listener) {
    if (externalAxis != null) {
      externalAxis.setRecordingTimeUpdateListener(listener);
    }
  }

  private AppAccount getAppAccount() {
    return WhistlePunkApplication.getAccount(getContext(), getArguments(), KEY_ACCOUNT_KEY);
  }

  private String getExperimentId() {
    return getArguments().getString(KEY_EXPERIMENT_ID);
  }

  private void activateSensorCardPresenter(
      SensorCardPresenter sensorCardPresenter, boolean setActive) {
    sensorCardPresenter.setActive(setActive, /* force */ false);
    if (setActive) {
      for (SensorCardPresenter other : sensorCardAdapter.getSensorCardPresenters()) {
        if (!other.equals(sensorCardPresenter)) {
          other.setActive(false, /* force */ false);
        }
      }
    }
  }

  private void onSelectedExperimentChanged(
      final Experiment selectedExperiment, final RecorderController rc, RecordingStatus status) {
    if (!TextUtils.equals(
        Experiment.getExperimentId(selectedExperiment),
        Experiment.getExperimentId(this.selectedExperiment))) {
      saveCurrentExperiment();
    }
    stopObservingCurrentSensors();
    this.selectedExperiment = selectedExperiment;
    selectedExperimentSubject.onNext(selectedExperiment);
    loadIncludedSensors(this.selectedExperiment.getSensorLayouts(), rc, status);
    rc.setSelectedExperiment(this.selectedExperiment);
  }

  private void updateSensorLayout(SensorLayoutPojo sensorLayout) {
    if (selectedExperiment == null) {
      return;
    }
    int position = getPositionOfLayout(sensorLayout);
    if (position < 0) {
      return;
    }
    selectedExperiment.updateSensorLayout(position, sensorLayout);
    // TODO: Is there a way to do this write less frequently?
    getDataController()
        .updateExperiment(
            selectedExperiment.getExperimentId(),
            LoggingConsumer.<Success>expectSuccess(TAG, "saving layout"));
  }

  // TODO: Can we optimize this by calling it less frequently?
  private List<SensorLayoutPojo> saveCurrentExperiment() {
    if (selectedExperiment == null) {
      return null;
    }
    final List<SensorLayoutPojo> layouts = buildCurrentLayouts();

    if (layouts != null) {
      selectedExperiment.setSensorLayouts(layouts);
      getDataController()
          .updateExperiment(
              selectedExperiment.getExperimentId(),
              LoggingConsumer.<Success>expectSuccess(TAG, "saving layouts"));
    }
    return layouts;
  }

  private List<SensorLayoutPojo> buildCurrentLayouts() {
    if (sensorCardAdapter == null) {
      return null;
    }

    return sensorCardAdapter.buildLayouts();
  }

  private void setSensorPresenters(
      List<SensorLayoutPojo> layouts, RecorderController rc, RecordingStatus status) {
    if (layouts == null || layouts.size() == 0) {
      layouts =
          Lists.newArrayList(
              defaultLayout(
                  colorAllocator,
                  sensorCardAdapter == null ? null : sensorCardAdapter.getUsedColors()));
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
        if (TextUtils.isEmpty(layouts.get(i).getSensorId())) {
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
      for (SensorCardPresenter presenter : sensorCardAdapter.getSensorCardPresenters()) {
        presenter.initializeSensorSelection();
        // If we resume while recording, assume that the sensors are already connected.
        if (status.isRecording()) {
          presenter.onSourceStatusUpdate(
              presenter.getSelectedSensorId(), SensorStatusListener.STATUS_CONNECTED);
        }
      }
    }
  }

  static SensorLayoutPojo defaultLayout(ColorAllocator colorAllocator, int[] usedColors) {
    return defaultLayout(colorAllocator.getNextColor(usedColors));
  }

  public static SensorLayoutPojo defaultLayout(int colorIndex) {
    SensorLayoutPojo layout = new SensorLayoutPojo();
    layout.setColorIndex(colorIndex);
    return layout;
  }

  // TODO: can all of this logic live in a separate, testable class?
  private void createSensorCardPresenters(
      ViewGroup rootView,
      List<SensorLayoutPojo> layouts,
      final RecorderController rc,
      RecordingStatus status) {
    List<SensorCardPresenter> sensorCardPresenters = new ArrayList<>();

    if (layouts.isEmpty()) {
      layouts =
          Lists.newArrayList(defaultLayout(colorAllocator, sensorCardAdapter.getUsedColors()));
    }
    // Create a sensorData card for each initial source tag, or at minimum one if no source
    // tags are saved in the bundle.
    for (int i = 0; i < layouts.size(); i++) {
      SensorLayoutPojo layout = layouts.get(i);
      final SensorCardPresenter sensorCardPresenter = createSensorCardPresenter(layout, rc);
      sensorCardPresenter.setInitialSourceTagToSelect(layout.getSensorId());
      sensorCardPresenters.add(sensorCardPresenter);
      tryStartObserving(sensorCardPresenter, layout.getSensorId(), status);
    }

    int activeCardIndex = 0;
    if (initialActiveCardIndex != -1 && initialActiveCardIndex < sensorCardPresenters.size()) {
      activeCardIndex = initialActiveCardIndex;
    }

    sensorCardPresenters
        .get(activeCardIndex)
        .setActive(
            true,
            /** force UI updates */
            true);

    if (sensorCardAdapter != null) {
      sensorCardAdapter.onDestroy();
    }

    sensorCardAdapter =
        new SensorCardAdapter(
            sensorCardPresenters,
            null,
            new SensorCardAdapter.CardRemovedListener() {
              @Override
              public void onCardRemoved(SensorCardPresenter sensorCardPresenter) {
                sensorCardPresenter.stopObserving();
                updateAvailableSensors();
              }
            },
            new SensorCardAdapter.SensorCardHeaderToggleListener() {
              @Override
              public void onToggleSensorHeader(SensorCardPresenter sensorCardPresenter) {
                activateSensorCardPresenter(sensorCardPresenter, !sensorCardPresenter.isActive());
              }
            });

    rc.setLayoutSupplier(() -> buildCurrentLayouts());

    updateSensorCount();
    sensorCardAdapter.setRecording(status.isRecording(), status.getRecordingStartTime());
    long resetTime = externalAxis.resetAxes();
    if (sensorCardAdapter != null) {
      long ignoreDataBefore = status.isRecording() ? -1 : resetTime;
      sensorCardAdapter.onResume(ignoreDataBefore);
    }

    refreshLabels(status);

    sensorCardRecyclerView.setLayoutManager(sensorCardLayoutManager);
    sensorCardRecyclerView.setAdapter(sensorCardAdapter);
    sensorCardRecyclerView.setItemAnimator(
        new DefaultItemAnimator() {

          @Override
          public void onMoveFinished(RecyclerView.ViewHolder item) {
            adjustSensorCardAddAlpha();
          }
        });
    sensorCardRecyclerView.addOnScrollListener(
        new RecyclerView.OnScrollListener() {
          @Override
          public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
            adjustSensorCardAddAlpha();
          }
        });
    sensorCardRecyclerView.setVisibility(View.VISIBLE);

    // Figure out the optimal height of a sensor presenter when the recycler view is laid out;
    // only do this on first layout
    RxView.globalLayouts(sensorCardRecyclerView)
        .firstElement()
        .subscribe(
            o -> {
              if (getActivity() == null || sensorCardAdapter == null) {
                return;
              }
              // A sensor presenter should be the height of the recycler view, minus
              // the margins, and minus the active card header size. We also subtract the height
              // of the external axis, which is transparent so the cards are shown behind it
              // but must be short enough to fit above it.
              int headerHeight =
                  getResources().getDimensionPixelSize(R.dimen.sensor_card_header_height);
              int marginHeight = getResources().getDimensionPixelSize(R.dimen.cardview_margin);
              int externalAxisHeight =
                  getResources().getDimensionPixelSize(R.dimen.external_axis_height);
              final int optimalHeight =
                  sensorCardRecyclerView.getHeight()
                      - headerHeight
                      - marginHeight * 3
                      - externalAxisHeight;
              int minHeight =
                  getResources().getDimensionPixelSize(R.dimen.sensor_card_content_height_min);

              // TODO: one time, I saw a crash here.  Can we prevent it more gracefully?
              if (sensorCardAdapter != null) {
                sensorCardAdapter.setSingleCardPresenterHeight(Math.max(optimalHeight, minHeight));
              }
            });
    sensorCardRecyclerView
        .getViewTreeObserver()
        .addOnGlobalLayoutListener(() -> adjustSensorCardAddAlpha());
  }

  public void addNewSensor() {
    int numAvailableSources = getAvailableSources().size();
    if (numAvailableSources != 0) {
      RecorderController rc = getRecorderController();
      SensorCardPresenter sensorCardPresenter =
          createSensorCardPresenter(
              defaultLayout(colorAllocator, sensorCardAdapter.getUsedColors()), rc);
      sensorCardPresenter.setActive(
          true,
          /** force UI updates */
          true);
      sensorCardAdapter.addSensorCardPresenter(sensorCardPresenter);
      updateAvailableSensors();
      sensorCardPresenter.initializeSensorSelection();
      sensorCardLayoutManager.scrollToPosition(sensorCardAdapter.getItemCount() - 1);
      activateSensorCardPresenter(sensorCardPresenter, true);
      saveCurrentExperiment();
    }
  }

  private List<String> getAllIncludedSources() {
    return nonEmptySensorList(Lists.newArrayList(sensorRegistry.getIncludedSources()));
  }

  private List<String> getAvailableSources() {
    // TODO: test this?
    String[] selected = sensorCardAdapter.getSensorTags();
    List<String> sources = sensorRegistry.getIncludedSources();
    sources.removeAll(Lists.newArrayList(selected));
    if (!hasGoodSensorId(selected)) {
      // If nothing is selected, at least one must be available (see notes on
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
      return Lists.newArrayList(sensorRegistry.getAllSources().get(0));
    }
    return allSourcesExcept;
  }

  private void adjustSensorCardAddAlpha() {
    if (sensorCardAdapter == null || !sensorCardAdapter.canAddMoreCards()) {
      return;
    }
    View topPanel = getView().findViewById(R.id.top_panel);
    if (topPanel != null) {
      topPanel.getHitRect(panelRect);
    }
    sensorCardAdapter.adjustAddViewAlpha(panelRect);
  }

  private SensorCardPresenter createSensorCardPresenter(
      SensorLayoutPojo layout, final RecorderController rc) {
    final SensorCardPresenter sensorCardPresenter =
        new SensorCardPresenter(
            new DataViewOptions(layout.getColorIndex(), getActivity(), scalarDisplayOptions),
            sensorSettingsController,
            rc,
            layout,
            selectedExperiment.getExperimentId(),
            externalAxis.getInteractionListener(),
            this);

    final SensorStatusListener sensorStatusListener =
        new SensorStatusListener() {
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
                Snackbar bar =
                    AccessibilityUtils.makeSnackbar(
                        getView(),
                        getString(R.string.snackbar_source_error, errorMessage),
                        Snackbar.LENGTH_LONG);
                snackbarManager.showSnackbar(bar);
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
          if (sensorId != null
              && sensorCardAdapter.getSensorCardPresenters().contains(sensorCardPresenter)) {
            sensorCardPresenter.retryConnection(getActivity());
          }
        });

    sensorCardPresenter.setOnSensorSelectedListener(
        sensorId -> {
          sensorCardPresenter.stopObserving();
          recordingStatus
              .firstElement()
              .subscribe(
                  status -> {
                    tryStartObserving(sensorCardPresenter, sensorId, status);
                  });
        });
    sensorCardPresenter.setAppearanceProvider(getSensorAppearanceProvider());

    return sensorCardPresenter;
  }

  private void tryStartObserving(
      SensorCardPresenter sensorCardPresenter, String sensorId, RecordingStatus status) {
    if ((TextUtils.equals(sensorId, DecibelSensor.ID) || TextUtils.equals(sensorId, PitchSensor.ID))
        && sensorCardPresenterForAudio == null
        && !PermissionUtils.isPermissionPermanentlyDenied(PermissionUtils.REQUEST_RECORD_AUDIO)
        && !PermissionUtils.hasPermission(getActivity(), PermissionUtils.REQUEST_RECORD_AUDIO)) {
      sensorCardPresenterForAudio = sensorCardPresenter;
      sensorIdForAudio = sensorId;
      sensorCardPresenter.setConnectingUI(
          sensorIdForAudio, true, getActivity().getApplicationContext(), true);
      PermissionUtils.tryRequestingPermission(
          getActivity(),
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
    if (sensorCardAdapter == null) {
      return;
    }
    List<String> availableSensors = getAvailableSources();
    List<String> allSensors = sensorRegistry.getAllSources();
    List<SensorCardPresenter> sensorCardPresenters = sensorCardAdapter.getSensorCardPresenters();

    // Available Sensors includes only sensors that are not being observed.
    // Check if a card wants to show the decibel and frequency sensors but permission was
    // denied.
    // Remove these from the list of available sensors so the decibel and frequency sensor
    // icons don't show up in other cards.
    // TODO: Extend this to work for any sensor that doesn't have the permission granted.
    // See b/27439593
    if (availableSensors.contains(DecibelSensor.ID) || availableSensors.contains(PitchSensor.ID)) {
      for (SensorCardPresenter presenter : sensorCardPresenters) {
        if (TextUtils.equals(presenter.getSelectedSensorId(), DecibelSensor.ID)) {
          availableSensors.remove(DecibelSensor.ID);
        }
        if (TextUtils.equals(presenter.getSelectedSensorId(), PitchSensor.ID)) {
          availableSensors.remove(PitchSensor.ID);
        }
      }
    }
    for (SensorCardPresenter presenter : sensorCardPresenters) {
      presenter.updateAvailableSensors(availableSensors, allSensors);
    }

    actionAreaView.disableAddSensorItem(getContext(), getAvailableSources().isEmpty());
    updateSensorCount();
  }

  private void processAddedLabel(AddedLabelEvent event) {
    Label label = event.getLabel();
    RecordingStatus status = event.getStatus();
    refreshLabels(status);
    ensureUnarchived(getActivity(), selectedExperiment, getDataController());
    // Trigger labels are logged in RecorderControllerImpl.
    if (label.getType() != GoosciLabel.Label.ValueType.SENSOR_TRIGGER) {
      String trackerLabel =
          status.isRecording() ? TrackerConstants.LABEL_RECORD : TrackerConstants.LABEL_OBSERVE;
      WhistlePunkApplication.getUsageTracker(getActivity())
          .trackEvent(
              TrackerConstants.CATEGORY_NOTES,
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

    graphOptionsController = new GraphOptionsController(activity);
    sensorSettingsController = new SensorSettingsControllerImpl(activity, getAppAccount());
    uICallbacks = getUiCallbacks(activity);
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
      externalAxis.resetAxes();
      externalAxis.onStartRecording(status.currentRecording.getStartTime());
    } else {
      externalAxis.onStopRecording();
    }
    refreshLabels(status);
    if (sensorCardAdapter != null) {
      sensorCardAdapter.setRecording(
          status.isRecording(), RecordingMetadata.getStartTime(status.currentRecording));
      if (!status.isRecording()) {
        adjustSensorCardAddAlpha();
      }
    }
    updateRecordingUIState(status);
  }

  /**
   * Locks the UI to user imput in order to do a recording state change.
   *
   * @param isStarting whether recording is just starting.
   */
  private void lockUiForRecording(boolean isStarting, boolean userInitiated) {
    // Lock the sensor cards and add button
    if (sensorCardAdapter != null) {
      sensorCardAdapter.setUiLockedForRecording(true);
    }
  }

  private void updateRecordingUIState(RecordingStatus status) {
    if (selectedExperiment == null) {
      return;
    }

    if (status.state == RecordingState.STARTING) {
      lockUiForRecording(/* state is STARTING */ true, status.userInitiated);
    } else if (status.isRecording()) {
      lockUiForRecording(/* state is not STARTING */ false, status.userInitiated);
      uICallbacks.onRecordingStart(status);
    } else {
      uICallbacks.onRecordingStopped();
      if (sensorCardAdapter != null) {
        sensorCardAdapter.setUiLockedForRecording(false);
      }
    }
  }

  private void refreshLabels(RecordingStatus status) {
    if (sensorCardAdapter == null || externalAxis == null) {
      return;
    }
    List<Label> labels;
    if (!status.isRecording()) {
      labels = Collections.emptyList();
    } else {
      Trial trial = selectedExperiment.getTrial(status.currentRecording.getRunId());
      if (trial == null) {
        return;
      }
      labels = trial.getLabels();
    }
    if (sensorCardAdapter != null) {
      for (SensorCardPresenter p : sensorCardAdapter.getSensorCardPresenters()) {
        p.refreshLabels(labels);
      }
    }
    if (externalAxis != null) {
      externalAxis.onLabelsChanged(labels);
    }
  }

  @Override
  public void requestCancelRecording() {
    recordingWasCanceled = true;
    getRecorderController().stopRecordingWithoutSaving();
  }

  private DataController getDataController() {
    return AppSingleton.getInstance(getActivity()).getDataController(getAppAccount());
  }

  private void startSensorCardObserving(
      SensorCardPresenter sensorCardPresenter, String sensorId, RecordingStatus status) {
    startObserving(sensorId, sensorCardPresenter);
    sensorCardPresenter.setRecording(status.getRecordingStartTime());
    externalAxis.resetAxes();
    updateSensorLayout(sensorCardPresenter.buildLayout());
    updateAvailableSensors();
  }

  public void startObserving(final String sensorId, final SensorCardPresenter sensorCardPresenter) {
    final Context context = getActivity().getApplicationContext();
    sensorCardPresenter.setConnectingUI(sensorId, false, context, true);
    sensorRegistry.withSensorChoice(
        TAG,
        sensorId,
        new Consumer<SensorChoice>() {
          @Override
          public void take(SensorChoice sensorChoice) {
            // TODO: should dataViewOptions go into sensorCardPresenter?
            NumberFormat numberFormat =
                AppSingleton.getInstance(context)
                    .getSensorAppearanceProvider(getAppAccount())
                    .getAppearance(sensorId)
                    .getNumberFormat();
            final SensorPresenter sensorPresenter =
                sensorChoice.createPresenter(
                    sensorCardPresenter.getDataViewOptions(),
                    numberFormat,
                    stats -> sensorCardPresenter.updateStats(stats));

            final WriteableSensorOptions options =
                sensorCardPresenter
                    .getCardOptions(sensorChoice, context)
                    .load(new NewOptionsStorage.SnackbarFailureListener(getView()));
            ReadableSensorOptions readOptions = options.getReadOnly();
            sensorPresenter.getOptionsPresenter().applyOptions(readOptions);

            sensorCardPresenter.startObserving(
                sensorChoice, sensorPresenter, readOptions, selectedExperiment, sensorRegistry);
            recordingStatus.firstElement().subscribe(status -> refreshLabels(status));
          }
        });
  }

  public void disableAllTriggers(
      final SensorLayoutPojo layout, final SensorCardPresenter presenter) {
    if (selectedExperiment == null) {
      return;
    }
    int position = getPositionOfLayout(layout);
    if (position < 0) {
      return;
    }
    selectedExperiment.updateSensorLayout(position, layout);
    getDataController()
        .updateExperiment(
            selectedExperiment.getExperimentId(),
            new LoggingConsumer<Success>(TAG, "disable sensor triggers") {
              @Override
              public void success(Success value) {
                getRecorderController().clearSensorTriggers(layout.getSensorId(), sensorRegistry);
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
    if (!trigger.hasAlertType(TriggerInformation.TriggerAlertType.TRIGGER_ALERT_VISUAL)) {
      return;
    }
    // If a snackbar is already being shown, don't show a new one.
    if (snackbarManager.snackbarIsVisible()) {
      return;
    }

    // TODO: Work with UX to tweak this check so that the right amount of the card is shown.
    // Look to see if the card is outside of the visible presenter range. The alert is shown
    // near the top of the card, so we check between the first fully visible card and the last
    // partially visible card.
    if (!presenter.isTriggerBarOnScreen()) {

      SensorAppearance appearance =
          AppSingleton.getInstance(getActivity())
              .getSensorAppearanceProvider(getAppAccount())
              .getAppearance(trigger.getSensorId());
      String units = appearance.getUnits(getActivity());
      String sensorName = appearance.getName(getActivity());
      String triggerWhenText =
          getActivity()
              .getResources()
              .getStringArray(R.array.trigger_when_list_note_text)[
              trigger.getTriggerWhen().getNumber()];
      String message =
          getActivity()
              .getResources()
              .getString(
                  R.string.trigger_snackbar_auto_text,
                  sensorName,
                  triggerWhenText,
                  trigger.getValueToTrigger(),
                  units);
      final Snackbar bar =
          AccessibilityUtils.makeSnackbar(getView(), message, Snackbar.LENGTH_LONG);
      bar.setAction(
          R.string.scroll_to_card,
          v -> {
            sensorCardLayoutManager.scrollToPosition(getPositionOfPresenter(presenter));
          });
      snackbarManager.showSnackbar(bar);
    }
  }

  /** Get list of existing devices. */
  private void loadIncludedSensors(
      final List<SensorLayoutPojo> layouts, final RecorderController rc, RecordingStatus status) {
    if (selectedExperiment != null) {
      getDataController()
          .getExternalSensorsByExperiment(
              selectedExperiment.getExperimentId(),
              new LoggingConsumer<ExperimentSensors>(TAG, "add external sensors") {
                @Override
                public void success(ExperimentSensors sensors) {
                  sensorRegistry.setExcludedIds(sensors.getExcludedInternalSensorIds());
                  updateExternalSensors(sensors.getExternalSensors());
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
    List<String> sensorsActuallyAdded =
        sensorRegistry.updateExternalSensors(
            sensors, AppSingleton.getInstance(getActivity()).getExternalSensorProviders());

    if (!sensorsActuallyAdded.isEmpty()) {
      boolean discoveryEnabled =
          featureDiscoveryProvider.isEnabled(
              getActivity(), getAppAccount(), FeatureDiscoveryProvider.FEATURE_NEW_EXTERNAL_SENSOR);
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
    sensorCardAdapter.setAvailableSensorCount(getAllIncludedSources().size());
  }

  private void scheduleFeatureDiscovery(String sensorId) {
    handler.sendMessageDelayed(
        Message.obtain(handler, MSG_SHOW_FEATURE_DISCOVERY, sensorId),
        FeatureDiscoveryProvider.FEATURE_DISCOVERY_SHOW_DELAY_MS);
  }

  private void cancelFeatureDiscovery() {
    handler.removeMessages(MSG_SHOW_FEATURE_DISCOVERY);
  }

  private void showFeatureDiscovery(String sensorId) {
    if (getActivity() == null) {
      return;
    }
    if (sensorCardRecyclerView.getChildCount() == 0) {
      return;
    }
    // Activate the first sensor card so the feature discovery has a place to attach.
    if (sensorCardAdapter != null && sensorCardAdapter.getSensorCardPresenters().size() > 0) {
      sensorCardAdapter.getSensorCardPresenters().get(0).setActive(true, true);
      sensorCardAdapter.getSensorCardPresenters().get(0).scrollToSensor(sensorId);
    }
    // Look for view with the tag.
    final View view = sensorCardRecyclerView.getChildAt(0).findViewWithTag(sensorId);
    if (view != null) {
      featureDiscoveryProvider.show(
          ((AppCompatActivity) getActivity()),
          getAppAccount(),
          FeatureDiscoveryProvider.FEATURE_NEW_EXTERNAL_SENSOR,
          sensorId);
    }
  }

  private RecorderController getRecorderController() {
    // TODO: don't depend on activity here?
    return AppSingleton.getInstance(getActivity()).getRecorderController(getAppAccount());
  }

  /**
   * Ensures that the experiment is unarchived, in case we make a new run or label. TODO: Find a
   * different home than RecordFragment.
   */
  public static void ensureUnarchived(Context context, Experiment experiment, DataController dc) {
    if (experiment != null) {
      if (experiment.isArchived()) {
        experiment.setArchived(context, dc.getAppAccount(), false);
        dc.updateExperiment(
            experiment.getExperimentId(),
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

  public int getPositionOfLayout(SensorLayoutPojo layout) {
    if (sensorCardAdapter == null) {
      return -1;
    }
    List<SensorCardPresenter> presenters = sensorCardAdapter.getSensorCardPresenters();
    for (int i = 0; i < presenters.size(); i++) {
      if (TextUtils.equals(presenters.get(i).getSelectedSensorId(), layout.getSensorId())) {
        return i;
      }
    }
    return -1;
  }

  public int getPositionOfPresenter(SensorCardPresenter presenter) {
    if (sensorCardAdapter == null) {
      return -1;
    }
    List<SensorCardPresenter> presenters = sensorCardAdapter.getSensorCardPresenters();
    for (int i = 0; i < presenters.size(); i++) {
      if (Objects.equals(presenter, presenters.get(i))) {
        return i;
      }
    }
    return -1;
  }

  private SensorCardPresenter getPresenterById(String sensorId) {
    if (sensorCardAdapter == null) {
      return null;
    }
    for (SensorCardPresenter presenter : sensorCardAdapter.getSensorCardPresenters()) {
      if (presenter.getSelectedSensorId().equals(sensorId)) {
        return presenter;
      }
    }
    return null;
  }

  @Override
  public void onClick(ActionAreaItem item) {
    if (item.equals(ActionAreaItem.ADD_SENSOR)) {
      addNewSensor();
    } else if (item.equals(ActionAreaItem.SNAPSHOT)) {
      takeSnapshot();
    } else {
      Activity activity = getActivity();
      if (activity != null) {
        ((ExperimentActivity) activity).onClick(item, true /* fromSensorFragment */);
      }
    }
  }

  private void takeSnapshot() {
    AppAccount appAccount = getAppAccount();
    Snapshotter snapshotter = Snapshotter.createFromContext(getContext(), appAccount);
    AppSingleton singleton = AppSingleton.getInstance(getContext());
    singleton
        .getRecorderController(appAccount)
        .watchRecordingStatus()
        .firstElement()
        .flatMapSingle(status -> snapshotter.addSnapshotLabel(getExperimentId(), status))
        .subscribe(
            (Label label) -> singleton.onLabelsAdded().onNext(label),
            (Throwable e) -> {
              if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, Throwables.getStackTraceAsString(e));
              }
            });
  }

  @Override
  public void onHiddenChanged(boolean hidden) {
    super.onHiddenChanged(hidden);
    if (!hidden) {
      updateTitle();
    }
  }

  private void updateTitle() {
    Activity activity = getActivity();
    if (activity != null) {
      ((NoteTakingActivity) activity)
          .updateTitleByToolFragment(getString(R.string.action_bar_sensor_note));
    }
  }
}
