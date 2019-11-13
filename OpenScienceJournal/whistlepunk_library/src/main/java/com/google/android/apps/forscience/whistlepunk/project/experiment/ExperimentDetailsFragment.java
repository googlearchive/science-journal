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

package com.google.android.apps.forscience.whistlepunk.project.experiment;

import android.app.Activity;
import android.app.TaskStackBuilder;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.core.app.NavUtils;
import androidx.core.content.ContextCompat;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.appcompat.widget.PopupMenu;
import androidx.recyclerview.widget.RecyclerView;
import androidx.appcompat.widget.Toolbar;
import android.text.TextUtils;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import com.google.android.apps.forscience.javalib.Success;
import com.google.android.apps.forscience.whistlepunk.AccessibilityUtils;
import com.google.android.apps.forscience.whistlepunk.AddNoteDialog;
import com.google.android.apps.forscience.whistlepunk.AppSingleton;
import com.google.android.apps.forscience.whistlepunk.Appearances;
import com.google.android.apps.forscience.whistlepunk.ColorUtils;
import com.google.android.apps.forscience.whistlepunk.DataController;
import com.google.android.apps.forscience.whistlepunk.DeletedLabel;
import com.google.android.apps.forscience.whistlepunk.DevOptionsFragment;
import com.google.android.apps.forscience.whistlepunk.ExportService;
import com.google.android.apps.forscience.whistlepunk.LoggingConsumer;
import com.google.android.apps.forscience.whistlepunk.MainActivity;
import com.google.android.apps.forscience.whistlepunk.NoteViewHolder;
import com.google.android.apps.forscience.whistlepunk.PictureUtils;
import com.google.android.apps.forscience.whistlepunk.ProtoSensorAppearance;
import com.google.android.apps.forscience.whistlepunk.R;
import com.google.android.apps.forscience.whistlepunk.RelativeTimeTextView;
import com.google.android.apps.forscience.whistlepunk.RxDataController;
import com.google.android.apps.forscience.whistlepunk.RxEvent;
import com.google.android.apps.forscience.whistlepunk.SensorAppearance;
import com.google.android.apps.forscience.whistlepunk.StatsAccumulator;
import com.google.android.apps.forscience.whistlepunk.StatsList;
import com.google.android.apps.forscience.whistlepunk.WhistlePunkApplication;
import com.google.android.apps.forscience.whistlepunk.accounts.AppAccount;
import com.google.android.apps.forscience.whistlepunk.analytics.TrackerConstants;
import com.google.android.apps.forscience.whistlepunk.cloudsync.CloudSyncManager;
import com.google.android.apps.forscience.whistlepunk.cloudsync.CloudSyncProvider;
import com.google.android.apps.forscience.whistlepunk.filemetadata.Experiment;
import com.google.android.apps.forscience.whistlepunk.filemetadata.ExperimentLibraryManager;
import com.google.android.apps.forscience.whistlepunk.filemetadata.FileMetadataUtil;
import com.google.android.apps.forscience.whistlepunk.filemetadata.Label;
import com.google.android.apps.forscience.whistlepunk.filemetadata.LocalSyncManager;
import com.google.android.apps.forscience.whistlepunk.filemetadata.SensorLayoutPojo;
import com.google.android.apps.forscience.whistlepunk.filemetadata.Trial;
import com.google.android.apps.forscience.whistlepunk.filemetadata.TrialStats;
import com.google.android.apps.forscience.whistlepunk.metadata.CropHelper;
import com.google.android.apps.forscience.whistlepunk.metadata.GoosciLabel;
import com.google.android.apps.forscience.whistlepunk.metadata.GoosciPictureLabelValue;
import com.google.android.apps.forscience.whistlepunk.metadata.GoosciTrial;
import com.google.android.apps.forscience.whistlepunk.metadata.GoosciTrial.SensorTrialStats.StatStatus;
import com.google.android.apps.forscience.whistlepunk.review.DeleteMetadataItemDialog;
import com.google.android.apps.forscience.whistlepunk.review.ExportOptionsDialogFragment;
import com.google.android.apps.forscience.whistlepunk.review.PinnedNoteAdapter;
import com.google.android.apps.forscience.whistlepunk.review.RunReviewDeprecatedActivity;
import com.google.android.apps.forscience.whistlepunk.review.RunReviewFragment;
import com.google.android.apps.forscience.whistlepunk.review.labels.LabelDetailsActivity;
import com.google.android.apps.forscience.whistlepunk.scalarchart.ChartController;
import com.google.android.apps.forscience.whistlepunk.scalarchart.ChartView;
import com.google.android.apps.forscience.whistlepunk.scalarchart.GraphOptionsController;
import com.google.android.apps.forscience.whistlepunk.scalarchart.ScalarDisplayOptions;
import com.google.android.apps.forscience.whistlepunk.sensorapi.StreamStat;
import com.google.android.material.snackbar.Snackbar;
import com.jakewharton.rxbinding2.view.RxView;
import io.reactivex.Completable;
import io.reactivex.Observable;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.functions.Consumer;
import io.reactivex.subjects.BehaviorSubject;
import java.io.File;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * A fragment to handle displaying Experiment details, runs and labels.
 *
 * @deprecated Moving to {@link ExperimentDetailsWithActionAreaFragment} to use the new action area.
 **/
@Deprecated
public class ExperimentDetailsFragment extends Fragment
    implements DeleteMetadataItemDialog.DeleteDialogListener,
        NameExperimentDialog.OnExperimentTitleChangeListener {

  public interface Listener {
    void onArchivedStateChanged(Experiment changed);
  }

  public interface ListenerProvider {
    Listener getExperimentDetailsFragmentListener();
  }

  public static final String ARG_ACCOUNT_KEY = "account_key";
  public static final String ARG_EXPERIMENT_ID = "experiment_id";
  public static final String ARG_CREATE_TASK = "create_task";
  public static final String ARG_CLAIM_EXPERIMENTS_MODE = "claim_experiments_mode";
  private static final String TAG = "ExperimentDetails";

  /** Boolen extra for savedInstanceState with the state of includeArchived experiments. */
  private static final String EXTRA_INCLUDE_ARCHIVED = "includeArchived";

  private RecyclerView details;
  private DetailsAdapter adapter;

  private AppAccount appAccount;
  private String experimentId;
  private boolean claimExperimentsMode;
  private Experiment experiment;
  private BehaviorSubject<Experiment> loadedExperiment = BehaviorSubject.create();
  private ScalarDisplayOptions scalarDisplayOptions;
  private boolean includeArchived;
  private BroadcastReceiver broadcastReceiver;
  private String activeTrialId;
  private TextView emptyView;
  private ProgressBar progressBar;
  private boolean progressVisible = false;
  private RxEvent destroyed = new RxEvent();
  private LocalSyncManager localSyncManager;
  private ExperimentLibraryManager experimentLibraryManager;

  /**
   * Creates a new instance of this fragment.
   *
   * @param appAccount the account that owns the experiment
   * @param experimentId Experiment ID to display
   * @param createTaskStack If {@code true}, then navigating home requires building a task stack up
   *     to the experiment list. If {@code false}, use the default navigation.
   */
  public static ExperimentDetailsFragment newInstance(
      AppAccount appAccount,
      String experimentId,
      boolean createTaskStack,
      boolean claimExperimentsMode) {
    ExperimentDetailsFragment fragment = new ExperimentDetailsFragment();
    Bundle args = new Bundle();
    args.putString(ARG_ACCOUNT_KEY, appAccount.getAccountKey());
    args.putString(ARG_EXPERIMENT_ID, experimentId);
    args.putBoolean(ARG_CREATE_TASK, createTaskStack);
    args.putBoolean(ARG_CLAIM_EXPERIMENTS_MODE, claimExperimentsMode);
    fragment.setArguments(args);
    return fragment;
  }

  public ExperimentDetailsFragment() {}

  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    AppSingleton.getInstance(getContext())
        .whenExportBusyChanges()
        .takeUntil(destroyed.happens())
        .observeOn(AndroidSchedulers.mainThread())
        .subscribe(
            busy -> {
              // This fragment may be gone by the time this code executes. Check getContext
              // and give up if it is null.
              if (getContext() == null) {
                return;
              }
              setProgressBarVisible(busy);
            });
    appAccount = WhistlePunkApplication.getAccount(getContext(), getArguments(), ARG_ACCOUNT_KEY);
    localSyncManager =
        AppSingleton.getInstance(getContext()).getLocalSyncManager(appAccount);
    experimentLibraryManager =
        AppSingleton.getInstance(getContext()).getExperimentLibraryManager(appAccount);
    experimentId = getArguments().getString(ARG_EXPERIMENT_ID);
    claimExperimentsMode = getArguments().getBoolean(ARG_CLAIM_EXPERIMENTS_MODE);
    setHasOptionsMenu(true);
    if (claimExperimentsMode) {
      WhistlePunkApplication.getUsageTracker(getActivity())
          .trackEvent(
              TrackerConstants.CATEGORY_CLAIMING_DATA,
              TrackerConstants.ACTION_VIEW_EXPERIMENT,
              null,
              0);
    }
  }

  public void setExperimentId(String experimentId) {
    // TODO(lizlooney): Investigate where this is called to see if we also need to set the
    // AppAccount.
    if (!Objects.equals(experimentId, this.experimentId)) {
      this.experimentId = experimentId;
      if (isResumed()) {
        // If not resumed, wait to load until next resume!
        reloadWithoutScroll();
      }
    }
  }

  @Override
  public void onStart() {
    super.onStart();
    WhistlePunkApplication.getUsageTracker(getActivity())
        .trackScreenView(TrackerConstants.SCREEN_EXPERIMENT_DETAIL);
  }

  @Override
  public void onResume() {
    super.onResume();
    reloadWithoutScroll();
    // Create a BroadcastReceiver for when the stats get updated.
    broadcastReceiver =
        new BroadcastReceiver() {
          @Override
          public void onReceive(Context context, Intent intent) {
            String statsRunId = intent.getStringExtra(CropHelper.EXTRA_TRIAL_ID);
            adapter.onStatsBroadcastReceived(statsRunId, getDataController());
          }
        };
    CropHelper.registerStatsBroadcastReceiver(
        getActivity().getApplicationContext(), broadcastReceiver);

    DeletedLabel label = AppSingleton.getInstance(getActivity()).popDeletedLabelForUndo();
    if (label != null) {
      onLabelDelete(label);
    }
    setProgressBarVisible(progressVisible);
  }

  @Override
  public void onDestroy() {
    startLibrarySync();
    adapter.onDestroy();
    destroyed.onHappened();
    super.onDestroy();
  }

  private void startLibrarySync() {
    CloudSyncProvider syncProvider = WhistlePunkApplication.getCloudSyncProvider(getContext());
    CloudSyncManager syncService = syncProvider.getServiceForAccount(appAccount);
    try {
      if (localSyncManager.getDirty(experimentId)) {
        syncService.syncExperimentLibrary(getContext(), "Sync on Experiment destroy");
      }
    } catch (IOException ioe) {
      if (Log.isLoggable(TAG, Log.ERROR)) {
        Log.e(TAG, "IOE", ioe);
      }
    }
  }

  public void reloadWithoutScroll() {
    loadExperimentIfInitialized().subscribe(() -> {}, onReloadError());
  }

  @NonNull
  public Consumer<? super Throwable> onReloadError() {
    return LoggingConsumer.complain(TAG, "reload");
  }

  public Completable loadExperimentIfInitialized() {
    if (experimentId == null) {
      // We haven't initialized yet. Just wait for this to get called later during
      // initialization.
      return Completable.complete();
    }
    return RxDataController.getExperimentById(getDataController(), experimentId)
        .doOnSuccess(
            experiment -> {
              if (experiment == null) {
                // This was deleted on us. Finish and return so we don't
                // try to load.
                getActivity().finish();
                return;
              }
              attachExperimentDetails(experiment);
              loadExperimentData(experiment);
            })
        .toCompletable();
  }

  @Override
  public void onPause() {
    if (broadcastReceiver != null) {
      CropHelper.unregisterBroadcastReceiver(
          getActivity().getApplicationContext(), broadcastReceiver);
      broadcastReceiver = null;
    }
    super.onPause();
  }

  @Override
  public void onSaveInstanceState(Bundle outState) {
    super.onSaveInstanceState(outState);
    outState.putBoolean(EXTRA_INCLUDE_ARCHIVED, includeArchived);
    adapter.onSaveInstanceState(outState);
  }

  @Override
  public View onCreateView(
      LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
    View view = inflater.inflate(R.layout.fragment_panes_experiment_details, container, false);

    AppCompatActivity activity = (AppCompatActivity) getActivity();
    ActionBar actionBar = activity.getSupportActionBar();
    if (actionBar != null) {
      actionBar.setDisplayHomeAsUpEnabled(true);
      actionBar.setHomeButtonEnabled(true);
    }

    emptyView = (TextView) view.findViewById(R.id.empty_list);
    emptyView.setText(R.string.empty_experiment);
    emptyView.setCompoundDrawablesRelativeWithIntrinsicBounds(
        null, null, null, view.getResources().getDrawable(R.drawable.empty_run));

    progressBar = (ProgressBar) view.findViewById(R.id.detailsIndeterminateBar);

    details = (RecyclerView) view.findViewById(R.id.details_list);
    details.setLayoutManager(
        new LinearLayoutManager(
            view.getContext(), LinearLayoutManager.VERTICAL, /* don't reverse layout */ false));
    DetailsAdapter adapter = new DetailsAdapter(this, savedInstanceState);
    loadedExperiment.subscribe(
        experiment -> {
          boolean includeInvalidRuns = false;
          adapter.setScalarDisplayOptions(scalarDisplayOptions);
          adapter.setData(experiment, experiment.getTrials(includeArchived, includeInvalidRuns));
          if (activeTrialId != null) {
            adapter.addActiveRecording(experiment.getTrial(activeTrialId));
          }
        },
        error -> {
          if (Log.isLoggable(TAG, Log.ERROR)) {
            Log.e(TAG, "loadedExperiment next failed", error);
          }
          throw new IllegalStateException("loadedExperiment next failed", error);
        });
    this.adapter = adapter;

    details.setAdapter(adapter);

    // TODO: Because scalarDisplayOptions are static, if the options are changed during the
    // time we are on this page it probably won't have an effect. Since graph options are
    // hidden from non-userdebug users, and not shown in the ExperimentDetails menu even when
    // enabled, this is OK for now.
    scalarDisplayOptions = new ScalarDisplayOptions();
    GraphOptionsController graphOptionsController = new GraphOptionsController(getActivity());
    graphOptionsController.loadIntoScalarDisplayOptions(scalarDisplayOptions, view);

    if (savedInstanceState != null) {
      includeArchived = savedInstanceState.getBoolean(EXTRA_INCLUDE_ARCHIVED, false);
      getActivity().invalidateOptionsMenu();
    }

    return view;
  }

  public void loadExperimentData(final Experiment experiment) {
    loadedExperiment.onNext(experiment);
  }

  public void onStartRecording(String trialId) {
    activeTrialId = trialId;
    // getTrialMaybe as workaround to avoid b/122074761
    if (adapter != null) {
      RxDataController.getTrialMaybe(getDataController(), experimentId, trialId)
          .subscribe(
              t -> adapter.addActiveRecording(t),
              error -> {
                if (Log.isLoggable(TAG, Log.ERROR)) {
                  Log.e(TAG, "onStartRecording failed", error);
                }
                throw new IllegalStateException("onStartRecording failed failed", error);
              });
    }
    if (getActivity() != null) {
      getActivity().invalidateOptionsMenu();
    }

    // TODO: there has to be a cheaper way to make the feed scroll to the bottom
    reloadAndScrollToBottom();
  }

  public void reloadAndScrollToBottom() {
    loadExperimentIfInitialized().subscribe(() -> scrollToBottom(), onReloadError());
  }

  public void scrollToBottom() {
    try {
      if (details != null && adapter != null && adapter.getItemCount() > 0) {
        if (DevOptionsFragment.isSmoothScrollingToBottomEnabled(getContext())) {
          details.smoothScrollToPosition(adapter.getItemCount() - 1);
        } else {
          details.scrollToPosition(adapter.getItemCount() - 1);
        }
      }
    } catch (NullPointerException e) {
      // TODO(b/78091514): Figure out what is actually going on here.
      if (Log.isLoggable(TAG, Log.ERROR)) {
        Log.e(TAG, "ExperimentDetailsFragment failed to scroll " + e);
      }
    }
  }

  public void onRecordingTrialUpdated(String trialId) {
    // getTrialMaybe as workaround to avoid b/67008535; we can't live without a trial forever,
    // but the consequences of not having that trial are small here.
    RxDataController.getTrialMaybe(getDataController(), experimentId, trialId)
        .subscribe(
            t -> {
              adapter.updateActiveRecording(t);
              scrollToBottom();
            },
            error -> {
              if (Log.isLoggable(TAG, Log.ERROR)) {
                Log.e(TAG, "onRecordingTrailUpdated failed", error);
              }
              throw new IllegalStateException("onRecordingTrialUpdated failed", error);
            });
  }

  public void onStopRecording() {
    if (activeTrialId != null) {
      RxDataController.getTrial(getDataController(), experimentId, activeTrialId)
          .subscribe(
                  t -> adapter.onRecordingEnded(t),
                  error -> {
                    if (Log.isLoggable(TAG, Log.ERROR)) {
                      Log.e(TAG, "Experiment may have been deleted", error);
                    }
                  });
      activeTrialId = null;
    }
    getActivity().invalidateOptionsMenu();
    if (details != null) {
      // We want to scroll to the bottom to show the new recording, but first check whether
      // the details list's height is more than zero. Scrolling doesn't work if a View's
      // height is zero.
      if (details.getHeight() > 0) {
        scrollToBottom();
      } else {
        // Delay calling scrollToBottom until the details lists's height is no longer zero,
        // or give up if 1 second elapses.
        RxEvent done = new RxEvent();
        Observable.interval(100, TimeUnit.MILLISECONDS)
            .take(10)
            .takeUntil(done.happens())
            .subscribe(
                n -> {
                  if (details.getHeight() > 0) {
                    scrollToBottom();
                    done.onHappened();
                  }
                });
      }
    }
  }

  // Sets the actionBar home button to opaque to indicate it is disabled.
  // Behavior is intercepted in onOptionsItemSelected when a recording is in progress
  private void setHomeButtonState(boolean disabled) {
    AppCompatActivity activity = (AppCompatActivity) getActivity();
    if (activity != null) {
      ActionBar actionBar = activity.getSupportActionBar();
      if (actionBar != null) {
        if (claimExperimentsMode) {
          actionBar.setHomeAsUpIndicator(
              ContextCompat.getDrawable(activity, R.drawable.ic_close_white_24dp));
        } else {
          final Drawable upArrow =
              ContextCompat.getDrawable(activity, R.drawable.ic_arrow_back_white_24dp);
          if (disabled) {
            upArrow.setAlpha(getResources().getInteger(R.integer.home_disabled_drawable_alpha));
          } else {
            upArrow.setAlpha(getResources().getInteger(R.integer.home_enabled_drawable_alpha));
          }
          actionBar.setHomeAsUpIndicator(upArrow);
        }
      }
    }
  }

  public String getActiveRecordingId() {
    return activeTrialId;
  }

  private void attachExperimentDetails(Experiment experiment) {
    this.experiment = experiment;
    final View rootView = getView();
    if (rootView == null) {
      return;
    }

    setExperimentItemsOrder(experiment);

    getActivity().setTitle(experiment.getDisplayTitle(getActivity()));

    Toolbar toolbar = (Toolbar) rootView.findViewById(R.id.toolbar);
    if (toolbar != null) {
      toolbar.setTitle(experiment.getDisplayTitle(getActivity()));
    }
  }

  private DataController getDataController() {
    return AppSingleton.getInstance(getActivity()).getDataController(appAccount);
  }

  private ExperimentLibraryManager getExperimentLibraryManager() {
    return AppSingleton.getInstance(getActivity()).getExperimentLibraryManager(appAccount);
  }

  @Override
  public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
    super.onCreateOptionsMenu(menu, inflater);
    inflater.inflate(R.menu.menu_experiment_details, menu);
  }

  @Override
  public void onPrepareOptionsMenu(Menu menu) {
    menu.findItem(R.id.action_archive_experiment).setVisible(canArchive());
    menu.findItem(R.id.action_unarchive_experiment).setVisible(canUnarchive());
    // Disable archive option when recording.
    menu.findItem(R.id.action_archive_experiment).setEnabled(!isRecording());
    menu.findItem(R.id.action_delete_experiment).setEnabled(canDelete());
    menu.findItem(R.id.action_remove_cover_image).setVisible(canRemoveCoverImage());
    menu.findItem(R.id.action_include_archived).setVisible(!includeArchived);
    menu.findItem(R.id.action_exclude_archived).setVisible(includeArchived);
    menu.findItem(R.id.action_edit_experiment).setVisible(canEdit());

    boolean isShareIntentValid =
        FileMetadataUtil.getInstance().validateShareIntent(getContext(), appAccount, experimentId);

    menu.findItem(R.id.action_export_experiment)
        .setVisible(experiment != null && isShareIntentValid);
    menu.findItem(R.id.action_export_experiment).setEnabled(!isRecording());
    menu.findItem(R.id.action_download_experiment).setVisible(experiment != null);
    menu.findItem(R.id.action_download_experiment).setEnabled(!isRecording());
    setHomeButtonState(isRecording());
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    int itemId = item.getItemId();
    if (progressVisible) {
      return true;
    }
    if (itemId == android.R.id.home) {
      // Disable the home button functionality when recording is active
      // Set with appearance in setHomeButtonState
      if (isRecording()) {
        return true;
      }
      displayNamePromptOrGoUp();
      return true;
    } else if (itemId == R.id.action_edit_experiment) {
      UpdateExperimentActivity.launch(getActivity(), appAccount, experimentId);
      return true;
    } else if (itemId == R.id.action_archive_experiment
        || itemId == R.id.action_unarchive_experiment) {
      setExperimentArchived(item.getItemId() == R.id.action_archive_experiment);
      return true;
    } else if (itemId == R.id.action_include_archived) {
      includeArchived = true;
      loadExperimentData(experiment);
      getActivity().invalidateOptionsMenu();
      return true;
    } else if (itemId == R.id.action_exclude_archived) {
      includeArchived = false;
      loadExperimentData(experiment);
      getActivity().invalidateOptionsMenu();
      return true;
    } else if (itemId == R.id.action_delete_experiment) {
      confirmDeleteExperiment();
    } else if (itemId == R.id.action_remove_cover_image) {
      confirmRemoveCoverImage();
    } else if (itemId == R.id.action_export_experiment) {
      WhistlePunkApplication.getUsageTracker(getActivity())
          .trackEvent(
              TrackerConstants.CATEGORY_EXPERIMENTS,
              TrackerConstants.ACTION_SHARED,
              TrackerConstants.LABEL_EXPERIMENT_DETAIL,
              0);
      setProgressBarVisible(true);
      ExportService.handleExperimentExportClick(getContext(), appAccount, experimentId, false);
      return true;
    } else if (itemId == R.id.action_download_experiment) {
      requestDownload();
      return true;
    }
    return super.onOptionsItemSelected(item);
  }

  private void requestDownload() {
    ExportService.requestDownloadPermissions(
        () -> {
          setProgressBarVisible(true);
          ExportService.handleExperimentExportClick(getContext(), appAccount, experimentId, true);
        },
        getActivity(),
        android.R.id.content,
        TrackerConstants.CATEGORY_EXPERIMENTS,
        TrackerConstants.LABEL_EXPERIMENT_DETAIL);
  }

  public void setProgressBarVisible(boolean visible) {
    progressVisible = visible;
    if (progressBar != null) {
      if (visible) {
        progressBar.setVisibility(View.VISIBLE);
      } else {
        progressBar.setVisibility(View.GONE);
      }
    }
  }

  // Prompt the user to name the experiment if they haven't yet.
  private void displayNamePromptOrGoUp() {
    if (!claimExperimentsMode) {
      if (experiment.isEmpty()) {
        // Experiment is empty. No reason to keep it.
        experimentLibraryManager.setDeleted(experimentId, true);
        deleteCurrentExperiment();
        return;
      }
      if (TextUtils.isEmpty(experiment.getTitle()) && canEdit()) {
        // Prompt the user to name the experiment if they haven't yet.
        displayNamePrompt();
        return;
      }
    }
    goToExperimentList();
  }

  private void deleteCurrentExperiment() {
    RxDataController.getExperimentById(getDataController(), experimentId)
        .subscribe(
            fullExperiment -> {
              getDataController()
                  .deleteExperiment(
                      fullExperiment,
                      new LoggingConsumer<Success>(TAG, "delete experiment") {
                        @Override
                        public void success(Success value) {
                          WhistlePunkApplication.getUsageTracker(getActivity())
                              .trackEvent(
                                  TrackerConstants.CATEGORY_EXPERIMENTS,
                                  TrackerConstants.ACTION_DELETED,
                                  TrackerConstants.LABEL_EXPERIMENT_DETAIL,
                                  0);
                          if (claimExperimentsMode) {
                            WhistlePunkApplication.getUsageTracker(getActivity())
                                .trackEvent(
                                    TrackerConstants.CATEGORY_CLAIMING_DATA,
                                    TrackerConstants.ACTION_DELETE_SINGLE,
                                    null,
                                    0);
                          }
                          goToExperimentList();
                        }
                      });
            },
            error -> {
              if (Log.isLoggable(TAG, Log.ERROR)) {
                Log.e(TAG, "Delete current experiment in ExperimentDetailsFragment failed", error);
              }
              throw new IllegalStateException(
                  "Delete current experiment in ExperimentDetailsFragment failed", error);
            });
  }

  private void displayNamePrompt() {
    // The experiment needs a title still.
    NameExperimentDialog dialog = NameExperimentDialog.newInstance(appAccount, experimentId);
    dialog.show(getChildFragmentManager(), NameExperimentDialog.TAG);
  }

  @Override
  public void onTitleChangedFromDialog() {
    // If it was saved successfully, we can just go up to the parent.
    WhistlePunkApplication.getUsageTracker(getActivity())
        .trackEvent(
            TrackerConstants.CATEGORY_EXPERIMENTS,
            TrackerConstants.ACTION_EDITED,
            TrackerConstants.LABEL_EXPERIMENT_DETAIL,
            0);
    goToExperimentList();
  }

  public boolean handleOnBackPressed() {
    if (progressBar != null && progressBar.getVisibility() == View.VISIBLE) {
      return true;
    }

    if (isRecording()) {
      Intent intent = new Intent(Intent.ACTION_MAIN);
      intent.addCategory(Intent.CATEGORY_HOME);
      intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
      startActivity(intent);
      return true;
    }

    if (!claimExperimentsMode && experiment != null && experiment.isEmpty()) {
      // Experiment is empty. No reason to keep it.
      deleteCurrentExperiment();
      return true;
    }

    if (!claimExperimentsMode
        && experiment != null
        && TextUtils.isEmpty(experiment.getTitle())
        && canEdit()) {
      displayNamePrompt();
      // We are handling this.
      return true;
    }

    Activity activity = getActivity();
    if (activity != null && activity.isTaskRoot()) {
      goToExperimentList();
      return true;
    }
    // The activity can handle it normally.
    return false;
  }

  private boolean isRecording() {
    return activeTrialId != null;
  }

  private void goToExperimentList() {
    if (getActivity() == null) {
      if (Log.isLoggable(TAG, Log.DEBUG)) {
        Log.d(TAG, "goToExperimentList called on null activity");
      }
      return;
    }

    if (claimExperimentsMode) {
      getActivity().finish();
      return;
    }

    Intent upIntent =
        getActivity() == null ? null : NavUtils.getParentActivityIntent(getActivity());
    if (upIntent == null) {
      // This is cheating a bit.  Currently, upIntent has only been observed to be null
      // when we're using panes mode, so here we just assume usePanes==true.
      Intent intent =
          MainActivity.launchIntent(getActivity(), R.id.navigation_item_experiments, true);
      getActivity().startActivity(intent);
      getActivity().finish();
      return;
    }
    if (NavUtils.shouldUpRecreateTask(getActivity(), upIntent)
        || getArguments().getBoolean(ARG_CREATE_TASK, false)) {
      upIntent.putExtra(MainActivity.ARG_SELECTED_NAV_ITEM_ID, R.id.navigation_item_experiments);
      upIntent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
      // TODO: Transition animation
      TaskStackBuilder.create(getActivity())
          .addNextIntentWithParentStack(upIntent)
          .startActivities();
    } else {
      NavUtils.navigateUpTo(getActivity(), upIntent);
    }
  }

  private void setExperimentArchived(final boolean archived) {
    final Context context = getContext();
    experiment.setArchived(context, appAccount, archived);
    getExperimentLibraryManager().setArchived(experimentId, archived);
    getDataController()
        .updateExperiment(
            experimentId,
            new LoggingConsumer<Success>(TAG, "Editing experiment") {
              @Override
              public void success(Success value) {
                setExperimentItemsOrder(experiment);
                FragmentActivity activity = getActivity();
                if (activity instanceof ListenerProvider) {
                  ((ListenerProvider) activity)
                      .getExperimentDetailsFragmentListener()
                      .onArchivedStateChanged(experiment);
                }
                // Reload the data to refresh experiment item and insert it if necessary.
                loadExperimentData(experiment);
                WhistlePunkApplication.getUsageTracker(activity)
                    .trackEvent(
                        TrackerConstants.CATEGORY_EXPERIMENTS,
                        archived
                            ? TrackerConstants.ACTION_ARCHIVE
                            : TrackerConstants.ACTION_UNARCHIVE,
                        TrackerConstants.LABEL_EXPERIMENT_DETAIL,
                        0);

                Snackbar bar =
                    AccessibilityUtils.makeSnackbar(
                        getView(),
                        activity
                            .getResources()
                            .getString(
                                archived
                                    ? R.string.archived_experiment_message
                                    : R.string.unarchived_experiment_message),
                        Snackbar.LENGTH_LONG);

                if (archived) {
                  bar.setAction(R.string.action_undo, v -> setExperimentArchived(false));
                }
                bar.show();
                activity.invalidateOptionsMenu();
              }
            });
  }

  private void setExperimentItemsOrder(Experiment experiment) {
    adapter.setReverseLayout(!experiment.isArchived());
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

  void deleteLabel(Label label) {
    if (getActivity() != null) {
      Consumer<Context> assetDeleter =
          experiment.deleteLabelAndReturnAssetDeleter(experiment, label, appAccount);
      RxDataController.updateExperiment(getDataController(), experiment, true)
          .subscribe(() -> onLabelDelete(new DeletedLabel(label, assetDeleter)));
    }
  }

  private void onLabelDelete(DeletedLabel deletedLabel) {
    deletedLabel.deleteAndDisplayUndoBar(
        getView(),
        appAccount,
        experiment,
        experiment,
        () -> {
          adapter.insertNote(deletedLabel.getLabel());

          WhistlePunkApplication.getUsageTracker(getActivity())
              .trackEvent(
                  TrackerConstants.CATEGORY_NOTES,
                  TrackerConstants.ACTION_DELETE_UNDO,
                  TrackerConstants.LABEL_EXPERIMENT_DETAIL,
                  TrackerConstants.getLabelValueType(deletedLabel.getLabel()));
        });

    adapter.deleteNote(deletedLabel.getLabel());

    WhistlePunkApplication.getUsageTracker(getActivity())
        .trackEvent(
            TrackerConstants.CATEGORY_NOTES,
            TrackerConstants.ACTION_DELETED,
            TrackerConstants.LABEL_EXPERIMENT_DETAIL,
            TrackerConstants.getLabelValueType(deletedLabel.getLabel()));
    if (claimExperimentsMode) {
      WhistlePunkApplication.getUsageTracker(getActivity())
          .trackEvent(
              TrackerConstants.CATEGORY_CLAIMING_DATA,
              TrackerConstants.ACTION_DELETE_NOTE,
              null,
              0);
    }
  }

  private void setTrialArchived(Trial trial, boolean toArchive) {
    trial.setArchived(toArchive);
    experiment.updateTrial(trial);
    RxDataController.updateExperiment(getDataController(), experiment, true)
        .subscribe(
            () -> {
              adapter.onTrialArchivedStateChanged(trial, includeArchived);
              WhistlePunkApplication.getUsageTracker(getActivity())
                  .trackEvent(
                      TrackerConstants.CATEGORY_RUNS,
                      toArchive
                          ? TrackerConstants.ACTION_ARCHIVE
                          : TrackerConstants.ACTION_UNARCHIVE,
                      TrackerConstants.LABEL_EXPERIMENT_DETAIL,
                      0);
            });
  }

  private void deleteTrial(Trial trial) {
    DeleteMetadataItemDialog dialog =
        DeleteMetadataItemDialog.newInstance(
            R.string.delete_run_dialog_title,
            R.string.run_review_delete_confirm,
            trial.getTrialId());
    dialog.show(getChildFragmentManager(), DeleteMetadataItemDialog.TAG);
  }

  private void confirmDeleteExperiment() {
    DeleteMetadataItemDialog dialog =
        DeleteMetadataItemDialog.newInstance(
            R.string.delete_experiment_dialog_title, R.string.delete_experiment_dialog_message);
    dialog.show(getChildFragmentManager(), DeleteMetadataItemDialog.TAG);
  }

  private void confirmRemoveCoverImage() {
    DeleteMetadataItemDialog dialog =
        DeleteMetadataItemDialog.newInstance(
            R.string.remove_cover_image_dialog_title,
            R.string.remove_cover_image_confirm,
            true /* removeCoverImage */);
    dialog.show(getChildFragmentManager(), DeleteMetadataItemDialog.TAG);
  }

  @Override
  public void requestDelete(Bundle extras) {
    String trialId = extras.getString(DeleteMetadataItemDialog.KEY_ITEM_ID);
    if (!TextUtils.isEmpty(trialId)) {
      // Then we were trying to delete a trial.
      experiment.deleteTrial(experiment.getTrial(trialId), getActivity(), appAccount);
      RxDataController.updateExperiment(getDataController(), experiment, true)
          .subscribe(() -> adapter.onTrialDeleted(trialId));
    } else if (extras.getBoolean(DeleteMetadataItemDialog.KEY_REMOVE_COVER_IMAGE, false)) {
      // Remove the cover image.
      experiment.setImagePath("");
      getDataController()
          .updateExperiment(
              experimentId,
              new LoggingConsumer<Success>(TAG, "Remove cover image") {
                @Override
                public void success(Success value) {
                  if (claimExperimentsMode) {
                    WhistlePunkApplication.getUsageTracker(getActivity())
                        .trackEvent(
                            TrackerConstants.CATEGORY_CLAIMING_DATA,
                            TrackerConstants.ACTION_REMOVE_COVER_IMAGE_FOR_EXPERIMENT,
                            null,
                            0);
                  }
                  // Reload the data to refresh experiment item.
                  loadExperimentData(experiment);
                  getActivity().invalidateOptionsMenu();
                }
              });
    } else {
      getDataController()
          .deleteExperiment(
              experiment,
              new LoggingConsumer<Success>(TAG, "Delete experiment") {
                @Override
                public void success(Success value) {
                  WhistlePunkApplication.getUsageTracker(getActivity())
                      .trackEvent(
                          TrackerConstants.CATEGORY_EXPERIMENTS,
                          TrackerConstants.ACTION_DELETED,
                          TrackerConstants.LABEL_EXPERIMENT_DETAIL,
                          0);
                  if (claimExperimentsMode) {
                    WhistlePunkApplication.getUsageTracker(getActivity())
                        .trackEvent(
                            TrackerConstants.CATEGORY_CLAIMING_DATA,
                            TrackerConstants.ACTION_DELETE_SINGLE,
                            null,
                            0);
                  }
                  getActivity().finish();
                }
              });
    }
  }

  public String getExperimentId() {
    return experimentId;
  }

  public static class DetailsAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final String KEY_SAVED_SENSOR_INDICES = "savedSensorIndices";

    private static final int VIEW_TYPE_EXPERIMENT_ARCHIVED = 0;
    static final int VIEW_TYPE_EXPERIMENT_TEXT_LABEL = 1;
    static final int VIEW_TYPE_EXPERIMENT_PICTURE_LABEL = 2;
    static final int VIEW_TYPE_RUN_CARD = 3;
    static final int VIEW_TYPE_EXPERIMENT_TRIGGER_LABEL = 4;
    static final int VIEW_TYPE_SNAPSHOT_LABEL = 5;
    static final int VIEW_TYPE_UNKNOWN_LABEL = 6;
    static final int VIEW_TYPE_RECORDING = 7;
    static final int VIEW_TYPE_SKETCH = 8;

    private final WeakReference<ExperimentDetailsFragment> parentReference;
    private Experiment experiment;
    private List<ExperimentDetailItem> items;
    private List<Integer> sensorIndices = null;
    private boolean hasRunsOrLabels;
    private ScalarDisplayOptions scalarDisplayOptions;
    private boolean reverseOrder = true;
    private PopupMenu popupMenu = null;

    DetailsAdapter(ExperimentDetailsFragment parent, Bundle savedInstanceState) {
      items = new ArrayList<>();
      parentReference = new WeakReference<ExperimentDetailsFragment>(parent);
      if (savedInstanceState != null && savedInstanceState.containsKey(KEY_SAVED_SENSOR_INDICES)) {
        sensorIndices = savedInstanceState.getIntegerArrayList(KEY_SAVED_SENSOR_INDICES);
      }
    }

    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
      View view;
      LayoutInflater inflater = LayoutInflater.from(parent.getContext());
      if (viewType == VIEW_TYPE_EXPERIMENT_TEXT_LABEL
          || viewType == VIEW_TYPE_EXPERIMENT_PICTURE_LABEL
          || viewType == VIEW_TYPE_EXPERIMENT_TRIGGER_LABEL
          || viewType == VIEW_TYPE_SNAPSHOT_LABEL
          || viewType == VIEW_TYPE_SKETCH) {
        view = inflater.inflate(R.layout.exp_card_pinned_note, parent, false);
        return new NoteViewHolder(view);
      } else if (viewType == VIEW_TYPE_RECORDING) {
        view = inflater.inflate(R.layout.exp_card_recording, parent, false);
        return new RecordingViewHolder(view);
      } else if (viewType == VIEW_TYPE_EXPERIMENT_ARCHIVED) {
        view = inflater.inflate(R.layout.metadata_archived, parent, false);
      } else if (viewType == VIEW_TYPE_RUN_CARD) {
        view = inflater.inflate(R.layout.exp_card_run, parent, false);
      } else {
        // TODO(b/117987511): display a card that explains why we can't show this data
        view = inflater.inflate(R.layout.exp_card_empty, parent, false);
      }
      return new DetailsViewHolder(view, viewType);
    }

    @Override
    public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
      ExperimentDetailItem item = items.get(position);
      int type = item.getViewType();
      if (type == VIEW_TYPE_RUN_CARD) {
        setupTrialHeader((DetailsViewHolder) holder, item);
        setupCaption((DetailsViewHolder) holder, item.getTrial().getCaptionText());
        bindRun((DetailsViewHolder) holder, item);
        return;
      } else if (type == VIEW_TYPE_RECORDING) {
        bindRecording((RecordingViewHolder) holder, item);
        return;
      }
      boolean isPictureLabel = type == VIEW_TYPE_EXPERIMENT_PICTURE_LABEL;
      boolean isTextLabel = type == VIEW_TYPE_EXPERIMENT_TEXT_LABEL;
      boolean isTriggerLabel = type == VIEW_TYPE_EXPERIMENT_TRIGGER_LABEL;
      boolean isSnapshotLabel = type == VIEW_TYPE_SNAPSHOT_LABEL;
      if (isPictureLabel || isTextLabel || isTriggerLabel || isSnapshotLabel) {
        final Label label = items.get(position).getLabel();

        NoteViewHolder noteViewHolder = (NoteViewHolder) holder;
        noteViewHolder.setNote(
            label,
            parentReference.get().appAccount,
            experiment.getExperimentId(),
            parentReference.get().claimExperimentsMode);

        // Work specific to ExperimentDetails
        noteViewHolder.relativeTimeView.setTime(label.getTimeStamp());
        noteViewHolder.durationText.setVisibility(View.GONE);

        noteViewHolder.menuButton.setOnClickListener(
            view -> {
              Context context = noteViewHolder.menuButton.getContext();
              popupMenu =
                  new PopupMenu(
                      context,
                      noteViewHolder.menuButton,
                      Gravity.NO_GRAVITY,
                      R.attr.actionOverflowMenuStyle,
                      0);
              setupNoteMenu(item);
              popupMenu.show();
            });

        // Don't launch LabelDetailsActivity in claim experiments mode.
        if (!parentReference.get().claimExperimentsMode) {
          holder.itemView.setOnClickListener(
              view -> {
                if (!isRecording()) {
                  // Can't click into details pages when recording.
                  LabelDetailsActivity.launchFromExpDetails(
                      holder.itemView.getContext(),
                      parentReference.get().appAccount,
                      experiment.getExperimentId(),
                      label);
                }
              });
        }
      }
      if (type == VIEW_TYPE_EXPERIMENT_ARCHIVED) {
        View archivedIndicator = holder.itemView.findViewById(R.id.archived_indicator);
        archivedIndicator.setVisibility(experiment.isArchived() ? View.VISIBLE : View.GONE);
      }
    }

    private boolean isRecording() {
      return parentReference.get() != null && parentReference.get().getActiveRecordingId() != null;
    }

    private void setupTrialHeader(DetailsViewHolder holder, final ExperimentDetailItem item) {
      holder.timeHeader.setTime(
          item.getViewType() == VIEW_TYPE_RUN_CARD
              ? item.getTrial().getFirstTimestamp()
              : item.getLabel().getTimeStamp());

      ColorUtils.colorDrawable(
          holder.captionIcon.getContext(),
          holder.captionIcon.getDrawable(),
          R.color.text_color_light_grey);
      ColorUtils.colorDrawable(
          holder.menuButton.getContext(),
          holder.menuButton.getDrawable(),
          R.color.text_color_light_grey);

      holder.menuButton.setOnClickListener(
          view -> {
            Context context = holder.menuButton.getContext();
            popupMenu =
                new PopupMenu(
                    context,
                    holder.menuButton,
                    Gravity.NO_GRAVITY,
                    R.attr.actionOverflowMenuStyle,
                    0);
            setupTrialMenu(item);
            popupMenu.show();
          });
    }

    private void setupCaption(DetailsViewHolder holder, String caption) {
      // This is temporarily removed per b/65063919 but planned to be re-enabled later.
      holder.captionView.setVisibility(View.GONE);
      holder.captionIcon.setVisibility(View.GONE);

      /*
      if (!TextUtils.isEmpty(caption)) {
          holder.captionView.setVisibility(View.VISIBLE);
          holder.captionTextView.setText(caption);
          holder.captionIcon.setVisibility(View.GONE);
      } else {
          holder.captionView.setVisibility(View.GONE);
          holder.captionIcon.setVisibility(View.VISIBLE);
      }*/
    }

    private void setupTrialMenu(ExperimentDetailItem item) {
      popupMenu.getMenuInflater().inflate(R.menu.menu_experiment_trial, popupMenu.getMenu());
      if (parentReference.get().claimExperimentsMode) {
        // Remove both archive and unarchive menu items, as these are not allowed in
        // claimExperimentsMode.
        popupMenu.getMenu().findItem(R.id.menu_item_archive).setVisible(false);
        popupMenu.getMenu().findItem(R.id.menu_item_unarchive).setVisible(false);
      } else {
        // Show either archive or unarchive (not both), based on current archived state.
        boolean archived = item.getTrial().isArchived();
        popupMenu.getMenu().findItem(R.id.menu_item_archive).setVisible(!archived);
        popupMenu.getMenu().findItem(R.id.menu_item_unarchive).setVisible(archived);
      }

      boolean isShareIntentValid =
          FileMetadataUtil.getInstance()
              .validateShareIntent(
                  parentReference.get().getContext(),
                  parentReference.get().appAccount,
                  parentReference.get().experimentId);
      popupMenu
          .getMenu()
          .findItem(R.id.menu_item_export)
          .setVisible(experiment != null && isShareIntentValid);
      popupMenu.getMenu().findItem(R.id.menu_item_export).setEnabled(!isRecording());
      popupMenu.setOnMenuItemClickListener(
          menuItem -> {
            if (parentReference.get() != null && parentReference.get().isVisible()) {
              if (menuItem.getItemId() == R.id.menu_item_archive) {
                parentReference.get().setTrialArchived(item.getTrial(), true);
                return true;
              } else if (menuItem.getItemId() == R.id.menu_item_unarchive) {
                parentReference.get().setTrialArchived(item.getTrial(), false);
                return true;
              } else if (menuItem.getItemId() == R.id.menu_item_download) {
                requestTrialDownloadOrShare(item.getTrial(), true);
              } else if (menuItem.getItemId() == R.id.menu_item_export) {
                requestTrialDownloadOrShare(item.getTrial(), false);
              } else if (menuItem.getItemId() == R.id.menu_item_delete) {
                parentReference.get().deleteTrial(item.getTrial());
                return true;
              }
            }
            return false;
          });
      popupMenu.setOnDismissListener(menu -> popupMenu = null);
    }

    private void requestTrialDownloadOrShare(Trial trial, boolean download) {
      ExportOptionsDialogFragment fragment =
          ExportOptionsDialogFragment.createOptionsDialog(
              parentReference.get().appAccount,
              experiment.getExperimentId(),
              trial.getTrialId(),
              download);
      fragment.show(
          ((AppCompatActivity) parentReference.get().getActivity()).getSupportFragmentManager(),
          "export");
    }

    private void setupNoteMenu(ExperimentDetailItem item) {
      popupMenu.getMenuInflater().inflate(R.menu.menu_experiment_note, popupMenu.getMenu());
      final Context context;
      final Intent shareIntent;
      if (item.getViewType() == VIEW_TYPE_EXPERIMENT_PICTURE_LABEL) {

        if (parentReference.get() != null) {
          context = parentReference.get().getContext();
          shareIntent =
              FileMetadataUtil.getInstance()
                  .createPhotoShareIntent(
                      context,
                      parentReference.get().appAccount,
                      experiment.getExperimentId(),
                      item.getLabel().getPictureLabelValue().getFilePath(),
                      item.getLabel().getCaptionText());
          popupMenu
              .getMenu()
              .findItem(R.id.btn_download_photo)
              .setVisible(true);
          if (shareIntent != null) {
            popupMenu.getMenu().findItem(R.id.btn_share_photo).setVisible(true);
          }
        } else {
          context = null;
          shareIntent = null;
        }
      } else {
        context = null;
        shareIntent = null;
      }
      popupMenu.setOnMenuItemClickListener(
          menuItem -> {
            if (menuItem.getItemId() == R.id.btn_delete_note) {
              if (parentReference.get() != null) {
                parentReference.get().deleteLabel(item.getLabel());
              }
              return true;
            }
            if (menuItem.getItemId() == R.id.btn_share_photo) {
              if (context != null && shareIntent != null) {
                context.startActivity(
                    Intent.createChooser(
                        shareIntent,
                        context.getResources().getString(R.string.export_photo_chooser_title)));
              }
              return true;
            }
            if (menuItem.getItemId() == R.id.btn_download_photo) {
              requestPhotoDownload(context, item);
              return true;
            }
            return false;
          });
      popupMenu.setOnDismissListener(menu -> popupMenu = null);
    }

    private void requestPhotoDownload(Context context, ExperimentDetailItem item) {
      Activity activity = parentReference.get().getActivity();
      ExportService.requestDownloadPermissions(
          () -> {
            String sourcePath = item.getLabel().getPictureLabelValue().getFilePath();
            File sourceFile =
                new File(
                    PictureUtils.getExperimentImagePath(
                        context,
                        parentReference.get().appAccount,
                        experiment.getExperimentId(),
                        sourcePath));
            Uri sourceUri = Uri.fromFile(sourceFile);
            ExportService.saveToDownloads(activity, sourceUri);
          },
          activity,
          android.R.id.content,
          TrackerConstants.CATEGORY_EXPERIMENTS,
          TrackerConstants.LABEL_PICTURE_DETAIL);
    }

    public void deleteNote(Label label) {
      int position = findLabelIndex(label);
      if (position == -1) {
        return;
      }
      items.remove(position);
      updateEmptyView();
      notifyItemRemoved(position);
    }

    private int findLabelIndex(Label label) {
      int expectedViewType =
          label.getType() == GoosciLabel.Label.ValueType.TEXT
              ? VIEW_TYPE_EXPERIMENT_TEXT_LABEL
              : label.getType() == GoosciLabel.Label.ValueType.PICTURE
                  ? VIEW_TYPE_EXPERIMENT_PICTURE_LABEL
                  : label.getType() == GoosciLabel.Label.ValueType.SNAPSHOT
                      ? VIEW_TYPE_SNAPSHOT_LABEL
                      : VIEW_TYPE_EXPERIMENT_TRIGGER_LABEL;
      int position = -1;
      int size = items.size();
      for (int i = 0; i < size; i++) {
        ExperimentDetailItem item = items.get(i);
        if (item.getViewType() == expectedViewType) {
          if (TextUtils.equals(label.getLabelId(), item.getLabel().getLabelId())) {
            position = i;
            break;
          }
        }
      }
      return position;
    }

    public void onTrialArchivedStateChanged(Trial trial, boolean includeArchived) {
      int position = findTrialIndex(trial.getTrialId());
      if (position != -1) {
        if (includeArchived) {
          notifyItemChanged(position);
        } else {
          // It shouldn't be in the list any more.
          items.remove(position);
          notifyItemRemoved(position);
          updateEmptyView();
        }
      }
    }

    public void onTrialDeleted(String trialId) {
      if (parentReference.get().claimExperimentsMode) {
        WhistlePunkApplication.getUsageTracker(parentReference.get().getActivity())
            .trackEvent(
                TrackerConstants.CATEGORY_CLAIMING_DATA,
                TrackerConstants.ACTION_DELETE_TRIAL,
                null,
                0);
      }
      int position = findTrialIndex(trialId);
      if (position != -1) {
        items.remove(position);
        notifyItemRemoved(position);
      }
    }

    private int findTrialIndex(String trialId) {
      for (int i = 0; i < items.size(); i++) {
        ExperimentDetailItem item = items.get(i);
        if (item.getViewType() == VIEW_TYPE_RUN_CARD || item.getViewType() == VIEW_TYPE_RECORDING) {
          if (TextUtils.equals(item.getTrial().getTrialId(), trialId)) {
            return i;
          }
        }
      }
      return -1;
    }

    public void insertNote(Label label) {
      int size = items.size();
      long timestamp = label.getTimeStamp();
      boolean inserted = false;
      hasRunsOrLabels = true;
      for (int i = 0; i < size; i++) {
        ExperimentDetailItem item = items.get(i);
        if (item.getViewType() == VIEW_TYPE_EXPERIMENT_ARCHIVED) {
          continue;
        }
        if (timestamp < item.getTimestamp()) {
          items.add(i, new ExperimentDetailItem(label));
          notifyItemInserted(i);
          inserted = true;
          break;
        }
      }
      if (!inserted) {
        items.add(size, new ExperimentDetailItem(label));
        notifyItemInserted(size);
      }
      parentReference.get().emptyView.setVisibility(View.GONE);
    }

    @Override
    public int getItemCount() {
      return items.size();
    }

    @Override
    public int getItemViewType(int position) {
      return items.get(position).getViewType();
    }

    public void onDestroy() {
      if (popupMenu != null) {
        popupMenu.dismiss();
      }
    }

    public void setScalarDisplayOptions(ScalarDisplayOptions scalarDisplayOptions) {
      this.scalarDisplayOptions = scalarDisplayOptions;
    }

    public void setData(Experiment experiment, List<Trial> trials) {
      hasRunsOrLabels = false;
      this.experiment = experiment;
      // TODO: compare data and see if anything has changed. If so, don't reload at all.
      items.clear();
      // As a safety check, if sensorIndices is not the same size as the run list,
      // just ignore it.
      if (sensorIndices != null && sensorIndices.size() != trials.size()) {
        sensorIndices = null;
      }
      int i = 0;
      String activeTrialId = parentReference.get().getActiveRecordingId();
      for (Trial trial : trials) {
        ExperimentDetailItem item =
            new ExperimentDetailItem(
                trial, scalarDisplayOptions, TextUtils.equals(activeTrialId, trial.getTrialId()));
        item.setSensorTagIndex(sensorIndices != null ? sensorIndices.get(i++) : 0);
        items.add(item);
        hasRunsOrLabels = true;
      }
      for (Label label : experiment.getLabels()) {
        items.add(new ExperimentDetailItem(label));
        hasRunsOrLabels = true;
      }
      sortItems();

      if (experiment.isArchived()) {
        items.add(0, new ExperimentDetailItem(VIEW_TYPE_EXPERIMENT_ARCHIVED));
      }

      parentReference.get().emptyView.setVisibility(hasRunsOrLabels ? View.GONE : View.VISIBLE);

      notifyDataSetChanged();
    }

    /**
     * Checks to see if we have any labels or runs. If so, hides the empty view. Otherwise, add the
     * empty view at the right location.
     */
    private void updateEmptyView() {
      boolean hasRunsOrLabels = false;

      final int count = items.size();
      for (int index = 0; index < count; ++index) {
        int viewType = items.get(index).getViewType();
        switch (viewType) {
            // Most view types count as runs or labels.
            // However, the archived view type does not.
          case VIEW_TYPE_RUN_CARD:
          case VIEW_TYPE_EXPERIMENT_PICTURE_LABEL:
          case VIEW_TYPE_EXPERIMENT_TEXT_LABEL:
          case VIEW_TYPE_SNAPSHOT_LABEL:
          case VIEW_TYPE_RECORDING:
          case VIEW_TYPE_EXPERIMENT_TRIGGER_LABEL:
            hasRunsOrLabels = true;
            break;
        }

        if (hasRunsOrLabels) {
          // Don't need to look anymore.
          break;
        }
      }

      this.hasRunsOrLabels = hasRunsOrLabels;
      parentReference.get().emptyView.setVisibility(hasRunsOrLabels ? View.GONE : View.VISIBLE);
    }

    void bindRun(final DetailsViewHolder holder, final ExperimentDetailItem item) {
      final Trial trial = item.getTrial();
      final Context applicationContext = holder.itemView.getContext().getApplicationContext();
      holder.setRunId(trial.getTrialId());
      String title = trial.getTitleWithDuration(applicationContext);
      holder.runTitle.setText(title);
      holder.cardView.setTag(R.id.run_title_text, trial.getTrialId());

      holder
          .itemView
          .findViewById(R.id.content)
          .setAlpha(
              applicationContext
                  .getResources()
                  .getFraction(
                      trial.isArchived()
                          ? R.fraction.metadata_card_archived_alpha
                          : R.fraction.metadata_card_alpha,
                      1,
                      1));
      View archivedIndicator = holder.itemView.findViewById(R.id.archived_indicator);
      archivedIndicator.setVisibility(trial.isArchived() ? View.VISIBLE : View.GONE);
      if (trial.isArchived()) {
        holder.runTitle.setContentDescription(
            applicationContext
                .getResources()
                .getString(R.string.archived_content_description, title));
      }

      holder.noteHolder.removeAllViews();
      if (trial.getLabelCount() > 0) {
        // Load the first two labels
        holder.noteHolder.setVisibility(View.VISIBLE);
        loadLabelIntoHolder(trial.getLabels().get(0), trial.getFirstTimestamp(), holder.noteHolder);
        if (trial.getLabelCount() > 1) {
          loadLabelIntoHolder(
              trial.getLabels().get(1), trial.getFirstTimestamp(), holder.noteHolder);
        }
        if (trial.getLabelCount() > 2) {
          // Show the "load more" link
          loadLearnMoreIntoHolder(holder.noteHolder, item.getTrial().getTrialId());
        }
      }
      if (!trial.isValid()) {
        removeSensorData(holder);
      } else if (trial.getSensorIds().size() > 0) {
        holder.cardView.setOnClickListener(createRunClickListener(item.getSensorTagIndex()));
        loadSensorData(applicationContext, holder, item);
        holder.sensorNext.setOnClickListener(
            v -> {
              // Sometimes we tap the button before it can disable so return if the button
              // should be disabled.
              if (item.getSensorTagIndex() >= item.getTrial().getSensorIds().size() - 1) {
                return;
              }
              item.setSensorTagIndex(item.getSensorTagIndex() + 1);
              loadSensorData(applicationContext, holder, item);
              SensorLayoutPojo layout = item.getSelectedSensorLayout();
              holder.cardView.setOnClickListener(createRunClickListener(item.getSensorTagIndex()));
              holder.setSensorId(layout.getSensorId());
            });
        holder.sensorPrev.setOnClickListener(
            v -> {
              // TODO: reduce duplication with next listener above?
              // Sometimes we tap the button before it can disable so return if the button
              // should be disabled.
              if (item.getSensorTagIndex() == 0) {
                return;
              }
              item.setSensorTagIndex(item.getSensorTagIndex() - 1);
              loadSensorData(applicationContext, holder, item);
              SensorLayoutPojo layout = item.getSelectedSensorLayout();
              holder.cardView.setOnClickListener(createRunClickListener(item.getSensorTagIndex()));
              holder.setSensorId(layout.getSensorId());
            });
      } else {
        removeSensorData(holder);
      }
    }

    private void bindRecording(RecordingViewHolder holder, ExperimentDetailItem item) {
      Context context = holder.cardView.getContext();
      holder.title.setText(
          context
              .getResources()
              .getString(R.string.card_recording_title, item.getTrial().getTitle(context)));
      holder.noteHolder.removeAllViews();
      if (item.getTrial().getLabelCount() > 0) {
        for (int i = 0; i < item.getTrial().getLabelCount(); i++) {
          loadLabelIntoHolder(
              item.getTrial().getLabels().get(i),
              item.getTrial().getFirstTimestamp(),
              holder.noteHolder);
        }
      }
    }

    private void loadLabelIntoHolder(Label label, long trialStartTime, ViewGroup noteHolder) {
      // Add labels
      // TODO: Combine with code in NoteViewHolder?
      // most of this is not duplicated since NoteViewHolder also deals with menu / caption
      // as well as click listeners.
      ViewGroup noteView =
          (ViewGroup)
              LayoutInflater.from(noteHolder.getContext())
                  .inflate(R.layout.exp_card_pinned_note_content, null, false);

      // TODO: Is it more efficient for Android to have a second XML file so I don't have to
      // show & hide all this stuff?
      noteView.findViewById(R.id.relative_run_time_text).setVisibility(View.VISIBLE);
      noteView.findViewById(R.id.top_divider).setVisibility(View.VISIBLE);
      noteView.findViewById(R.id.heading_section).setVisibility(View.GONE);
      ((TextView) noteView.findViewById(R.id.relative_run_time_text))
          .setText(PinnedNoteAdapter.getNoteTimeText(label.getTimeStamp(), trialStartTime));
      String captionText = label.getCaptionText();
      if (!TextUtils.isEmpty(captionText)) {
        noteView.findViewById(R.id.caption_section).setVisibility(View.VISIBLE);
        ((TextView) noteView.findViewById(R.id.caption)).setText(captionText);
      } else {
        noteView.findViewById(R.id.caption_section).setVisibility(View.GONE);
      }

      if (label.getType() == GoosciLabel.Label.ValueType.TEXT) {
        ((TextView) noteView.findViewById(R.id.note_text))
            .setText(label.getTextLabelValue().getText());
      } else {
        noteView.findViewById(R.id.note_text).setVisibility(View.GONE);
      }

      if (label.getType() == GoosciLabel.Label.ValueType.PICTURE) {
        GoosciPictureLabelValue.PictureLabelValue labelValue = label.getPictureLabelValue();
        noteView.findViewById(R.id.note_image).setVisibility(View.VISIBLE);
        ImageView imageView = noteView.findViewById(R.id.note_image);
        // Note images should be smaller if they're a part of a trial
        imageView.getLayoutParams().height =
            (int)
                TypedValue.applyDimension(
                    TypedValue.COMPLEX_UNIT_DIP, 100, noteView.getResources().getDisplayMetrics());
        PictureUtils.loadExperimentImage(
            noteView.getContext(),
            imageView,
            parentReference.get().appAccount,
            experiment.getExperimentId(),
            labelValue.getFilePath(),
            true);
      }

      if (label.getType() != GoosciLabel.Label.ValueType.SENSOR_TRIGGER
          && label.getType() != GoosciLabel.Label.ValueType.SNAPSHOT) {
        noteView.findViewById(R.id.snapshot_values_list).setVisibility(View.GONE);
      } else {
        if (label.getType() == GoosciLabel.Label.ValueType.SENSOR_TRIGGER) {
          NoteViewHolder.loadTriggerIntoList(
              (ViewGroup) noteView.findViewById(R.id.snapshot_values_list),
              label,
              parentReference.get().appAccount);
        }

        if (label.getType() == GoosciLabel.Label.ValueType.SNAPSHOT) {
          NoteViewHolder.loadSnapshotsIntoList(
              (ViewGroup) noteView.findViewById(R.id.snapshot_values_list),
              label,
              parentReference.get().appAccount);
        }
      }

      noteHolder.addView(noteView);
    }

    private void loadLearnMoreIntoHolder(ViewGroup noteHolder, String runId) {
      LayoutInflater.from(noteHolder.getContext())
          .inflate(R.layout.load_more_notes_button, noteHolder);
      // TODO: Jump straight to the notes section.
      Button button = (Button) noteHolder.findViewById(R.id.load_more_btn);
      Context context = button.getContext();
      int activeTextColor = button.getCurrentTextColor();
      int inactiveColor = context.getResources().getColor(R.color.archived_background_color);

      AppSingleton.getInstance(context)
          .getRecorderController(parentReference.get().appAccount)
          .watchRecordingStatus()
          .takeUntil(RxView.detaches(button))
          .subscribe(
              status -> {
                if (status.isRecording()) {
                  button.setTextColor(inactiveColor);
                } else {
                  button.setTextColor(activeTextColor);
                }
              });

      button.setOnClickListener(
          view ->
              RunReviewDeprecatedActivity.launch(
                  noteHolder.getContext(),
                  parentReference.get().appAccount,
                  runId,
                  experiment.getExperimentId(),
                  0 /* sensor index deprecated */,
                  false /* from record */,
                  false /* create task */,
                  parentReference.get().claimExperimentsMode,
                  null));
    }

    private void removeSensorData(DetailsViewHolder holder) {
      holder.sensorName.setText("");
      holder.setSensorId(null);
      setIndeterminateSensorData(holder);
    }

    private View.OnClickListener createRunClickListener(final int selectedSensorIndex) {
      return v -> {
        if (!isRecording()) {
          // Can't click into details pages when recording.
          String runId = (String) v.getTag(R.id.run_title_text);
          RunReviewDeprecatedActivity.launch(
              v.getContext(),
              parentReference.get().appAccount,
              runId,
              experiment.getExperimentId(),
              selectedSensorIndex,
              false /* from record */,
              false /* create task */,
              parentReference.get().claimExperimentsMode,
              null);
        }
      };
    }

    private void setIndeterminateSensorData(DetailsViewHolder holder) {
      holder.statsLoadStatus = DetailsViewHolder.STATS_LOAD_STATUS_LOADING;
      holder.statsList.clearStats();
    }

    private void loadSensorData(
        Context appContext, final DetailsViewHolder holder, final ExperimentDetailItem item) {
      final Trial trial = item.getTrial();
      final String sensorId = trial.getSensorIds().get(item.getSensorTagIndex());

      final SensorAppearance appearance =
          ProtoSensorAppearance.getAppearanceFromProtoOrProvider(
              trial.getAppearances().get(sensorId),
              sensorId,
              AppSingleton.getInstance(appContext)
                  .getSensorAppearanceProvider(parentReference.get().appAccount));
      final NumberFormat numberFormat = appearance.getNumberFormat();
      holder.sensorName.setText(Appearances.getSensorDisplayName(appearance, appContext));
      final SensorLayoutPojo sensorLayout = item.getSelectedSensorLayout();
      int color =
          appContext.getResources()
              .getIntArray(R.array.graph_colors_array)[sensorLayout.getColorIndex()];
      Appearances.applyDrawableToImageView(
          appearance.getIconDrawable(appContext), holder.sensorImage, color);

      boolean hasNextButton = item.getSensorTagIndex() < trial.getSensorIds().size() - 1;
      boolean hasPrevButton = item.getSensorTagIndex() > 0;
      holder.sensorPrev.setVisibility(hasPrevButton ? View.VISIBLE : View.INVISIBLE);
      holder.sensorNext.setVisibility(hasNextButton ? View.VISIBLE : View.INVISIBLE);
      if (hasNextButton) {
        RunReviewFragment.updateContentDescription(
            holder.sensorNext,
            R.string.run_review_switch_sensor_btn_next,
            item.getNextSensorId(),
            appContext,
            parentReference.get().appAccount,
            trial);
      }
      if (hasPrevButton) {
        RunReviewFragment.updateContentDescription(
            holder.sensorPrev,
            R.string.run_review_switch_sensor_btn_prev,
            item.getPrevSensorId(),
            appContext,
            parentReference.get().appAccount,
            trial);
      }

      TrialStats stats = getStatsOrDefault(trial, sensorId);
      holder.statsList.updateColor(color);
      if (!stats.statsAreValid()) {
        holder.statsList.clearStats();
      } else {
        List<StreamStat> streamStats =
            new StatsAccumulator.StatsDisplay(numberFormat).updateStreamStats(stats);
        holder.statsList.updateStats(streamStats);
      }

      // Load sensor readings into a chart.
      final ChartController chartController = item.getChartController();
      chartController.setChartView(holder.chartView);
      chartController.setProgressView(holder.progressView);
      holder.setSensorId(sensorLayout.getSensorId());
      DataController dc =
          AppSingleton.getInstance(appContext).getDataController(parentReference.get().appAccount);
      chartController.loadRunData(
          trial,
          sensorLayout,
          dc,
          holder,
          stats,
          new ChartController.ChartDataLoadedCallback() {
            @Override
            public void onChartDataLoaded(long firstTimestamp, long lastTimestamp) {
              // Display the graph.
              chartController.setLabels(trial.getLabels());
              chartController.setXAxis(firstTimestamp, lastTimestamp);
              chartController.setReviewYAxis(
                  stats.getStatValue(GoosciTrial.SensorStat.StatType.MINIMUM, 0),
                  stats.getStatValue(GoosciTrial.SensorStat.StatType.MAXIMUM, 0),
                  true);
            }

            @Override
            public void onLoadAttemptStarted(boolean unused) {}
          },
          holder.itemView.getContext());
    }

    @NonNull
    private TrialStats getStatsOrDefault(Trial trial, String sensorId) {
      TrialStats stats = trial.getStatsForSensor(sensorId);
      if (stats == null) {
        if (Log.isLoggable(TAG, Log.ERROR)) {
          Log.e(TAG, "No stats for sensor " + sensorId + " in trial " + trial);
        }
        // At least allow generating default values
        stats = new TrialStats(sensorId);
        stats.setStatStatus(StatStatus.NEEDS_UPDATE);
      }
      return stats;
    }

    void sortItems() {
      if (reverseOrder) {
        Collections.sort(items, (lhs, rhs) -> Long.compare(lhs.getTimestamp(), rhs.getTimestamp()));
      } else {
        Collections.sort(items, (lhs, rhs) -> Long.compare(rhs.getTimestamp(), lhs.getTimestamp()));
      }
    }

    public void onSaveInstanceState(Bundle outState) {
      ArrayList<Integer> selectedIndices = new ArrayList<>();
      int size = getItemCount();
      for (int i = 0; i < size; i++) {
        if (getItemViewType(i) == VIEW_TYPE_RUN_CARD) {
          selectedIndices.add(items.get(i).getSensorTagIndex());
        }
      }
      outState.putIntegerArrayList(KEY_SAVED_SENSOR_INDICES, selectedIndices);
    }

    public void onStatsBroadcastReceived(String statsRunId, DataController dc) {
      // Update the stats when this is received.
      // TODO: Optimize: only update the full view if the sensor ID that changed was visible?
      for (int i = 0; i < items.size(); i++) {
        Trial trial = items.get(i).getTrial();
        if (trial == null) {
          continue;
        }
        final String trialId = trial.getTrialId();
        if (TextUtils.equals(statsRunId, trialId)) {
          // Reload the experiment run since the stats have changed.
          // TODO: Do we need a full experiment reload if the same objects are being used
          // everywhere?
          final int trialIndex = i;
          dc.getExperimentById(
              experiment.getExperimentId(),
              new LoggingConsumer<Experiment>(TAG, "load experiment") {
                @Override
                public void success(final Experiment experiment) {
                  // Rebind the View Holder to reload the stats and graphs.
                  DetailsAdapter.this.experiment = experiment;
                  items.get(trialIndex).setTrial(experiment.getTrial(trialId));
                }
              });
          return;
        }
      }
    }

    public void addActiveRecording(Trial trial) {
      // If trial is somehow null, there's nothing much we can do. This was causing NPE crashes,
      // probably due to asynchronous modifications of the protos. This case will hopefully occur
      // less often with Lite protos, but checking to be sure will at least prevent a crash.
      if (trial == null) {
        return;
      }
      if (findTrialIndex(trial.getTrialId()) == -1) {
        // active recording goes to end of list
        items.add(new ExperimentDetailItem(trial, scalarDisplayOptions, true));
        notifyItemInserted(items.size() - 1);
        updateEmptyView();
      }
    }

    public void updateActiveRecording(Trial trial) {
      int position = findTrialIndex(trial.getTrialId());
      if (position == -1) {
        addActiveRecording(trial);
      } else {
        items.get(position).setTrial(trial);
        notifyItemChanged(position);
      }
    }

    public void onRecordingEnded(Trial trial) {
      int position = findTrialIndex(trial.getTrialId());
      if (position == -1) {
        return;
      }
      if (!trial.isValid()) {
        // Remove it if it is invalid
        // TODO: Ask the parent fragment if we are including invalid runs.
        items.remove(position);
        notifyItemRemoved(position);
      } else {
        items.set(position, new ExperimentDetailItem(trial, scalarDisplayOptions, false));
        notifyItemChanged(position);
      }
    }

    public void setReverseLayout(boolean reverseLayout) {
      if (reverseOrder != reverseLayout) {
        reverseOrder = reverseLayout;
        sortItems();
        notifyDataSetChanged();
      }
    }

    public static class DetailsViewHolder extends RecyclerView.ViewHolder
        implements ChartController.ChartLoadingStatus {

      static final int STATS_LOAD_STATUS_IDLE = 0;
      static final int STATS_LOAD_STATUS_LOADING = 1;

      private int graphLoadStatus;

      // Keep track of the loading state and what should currently be displayed:
      // Loads are done on a background thread, so as cards are scrolled or sensors are
      // updated we need to track what needs to be reloaded.
      private String runId;
      private String sensorId;

      final int viewType;

      int statsLoadStatus = STATS_LOAD_STATUS_IDLE;

      View cardView;

      // Run members.
      TextView runTitle;
      ChartView chartView;

      // Stats members.
      StatsList statsList;

      TextView sensorName;
      ImageView sensorImage;
      ImageButton sensorPrev;
      ImageButton sensorNext;
      ProgressBar progressView;

      ImageView captionIcon;
      View captionView;
      RelativeTimeTextView timeHeader;
      ImageButton menuButton;
      TextView captionTextView;
      ViewGroup noteHolder;

      public DetailsViewHolder(View itemView, int viewType) {
        super(itemView);
        this.viewType = viewType;
        if (viewType == VIEW_TYPE_RUN_CARD) {
          cardView = itemView.findViewById(R.id.card_view);
          runTitle = (TextView) itemView.findViewById(R.id.run_title_text);
          statsList = (StatsList) itemView.findViewById(R.id.stats_view);
          sensorName = (TextView) itemView.findViewById(R.id.run_review_sensor_name);
          sensorPrev = (ImageButton) itemView.findViewById(R.id.run_review_switch_sensor_btn_prev);
          sensorNext = (ImageButton) itemView.findViewById(R.id.run_review_switch_sensor_btn_next);
          chartView = (ChartView) itemView.findViewById(R.id.chart_view);
          sensorImage = (ImageView) itemView.findViewById(R.id.sensor_icon);
          progressView = (ProgressBar) itemView.findViewById(R.id.chart_progress);
          captionIcon = (ImageView) itemView.findViewById(R.id.edit_icon);
          captionView = itemView.findViewById(R.id.caption_section);
          timeHeader = (RelativeTimeTextView) itemView.findViewById(R.id.relative_time_text);
          menuButton = (ImageButton) itemView.findViewById(R.id.note_menu_button);
          captionTextView = (TextView) itemView.findViewById(R.id.caption);
          noteHolder = (ViewGroup) itemView.findViewById(R.id.notes_holder);

          // Only used in RunReview
          itemView.findViewById(R.id.time_text).setVisibility(View.GONE);
        }
      }

      public void setRunId(String runId) {
        this.runId = runId;
      }

      public void setSensorId(String sensorId) {
        this.sensorId = sensorId;
      }

      @Override
      public int getGraphLoadStatus() {
        return graphLoadStatus;
      }

      @Override
      public void setGraphLoadStatus(int graphLoadStatus) {
        this.graphLoadStatus = graphLoadStatus;
      }

      @Override
      public String getRunId() {
        return runId;
      }

      @Override
      public String getSensorId() {
        return sensorId;
      }
    }

    public static class RecordingViewHolder extends RecyclerView.ViewHolder {
      View cardView;
      ViewGroup noteHolder;
      TextView title;

      public RecordingViewHolder(View itemView) {
        super(itemView);
        cardView = itemView.findViewById(R.id.card_view);
        noteHolder = (ViewGroup) itemView.findViewById(R.id.notes_holder);
        title = (TextView) itemView.findViewById(R.id.title);
      }
    }
  }

  private boolean canArchive() {
    return canEdit();
  }

  private boolean canUnarchive() {
    return experiment != null && experiment.isArchived() && !claimExperimentsMode;
  }

  private boolean canDelete() {
    return experiment != null && !isRecording();
  }

  private boolean canEdit() {
    return experiment != null && !experiment.isArchived() && !claimExperimentsMode;
  }

  private boolean canRemoveCoverImage() {
    return experiment != null
        && !TextUtils.isEmpty(experiment.getImagePath())
        && claimExperimentsMode;
  }
}
