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

package com.google.android.apps.forscience.whistlepunk.project;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.net.ConnectivityManager;
import android.os.Bundle;
import android.os.Handler;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.appcompat.widget.PopupMenu;
import androidx.recyclerview.widget.RecyclerView;
import android.text.TextUtils;
import android.text.format.DateFormat;
import android.util.Log;
import android.util.TimingLogger;
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
import android.widget.TextView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout.OnRefreshListener;
import com.google.android.apps.forscience.javalib.Success;
import com.google.android.apps.forscience.whistlepunk.AccessibilityUtils;
import com.google.android.apps.forscience.whistlepunk.AppSingleton;
import com.google.android.apps.forscience.whistlepunk.Clock;
import com.google.android.apps.forscience.whistlepunk.ColorUtils;
import com.google.android.apps.forscience.whistlepunk.DataController;
import com.google.android.apps.forscience.whistlepunk.ExportService;
import com.google.android.apps.forscience.whistlepunk.LoggingConsumer;
import com.google.android.apps.forscience.whistlepunk.PictureUtils;
import com.google.android.apps.forscience.whistlepunk.R;
import com.google.android.apps.forscience.whistlepunk.RecorderController;
import com.google.android.apps.forscience.whistlepunk.RxDataController;
import com.google.android.apps.forscience.whistlepunk.RxEvent;
import com.google.android.apps.forscience.whistlepunk.SnackbarManager;
import com.google.android.apps.forscience.whistlepunk.WhistlePunkApplication;
import com.google.android.apps.forscience.whistlepunk.accounts.AccountsUtils;
import com.google.android.apps.forscience.whistlepunk.accounts.AppAccount;
import com.google.android.apps.forscience.whistlepunk.accounts.NonSignedInAccount;
import com.google.android.apps.forscience.whistlepunk.analytics.TrackerConstants;
import com.google.android.apps.forscience.whistlepunk.analytics.UsageTracker;
import com.google.android.apps.forscience.whistlepunk.cloudsync.CloudSyncManager;
import com.google.android.apps.forscience.whistlepunk.cloudsync.CloudSyncProvider;
import com.google.android.apps.forscience.whistlepunk.featurediscovery.FeatureDiscoveryProvider;
import com.google.android.apps.forscience.whistlepunk.filemetadata.Experiment;
import com.google.android.apps.forscience.whistlepunk.filemetadata.ExperimentLibraryManager;
import com.google.android.apps.forscience.whistlepunk.filemetadata.ExperimentOverviewPojo;
import com.google.android.apps.forscience.whistlepunk.filemetadata.FileMetadataUtil;
import com.google.android.apps.forscience.whistlepunk.filemetadata.Label;
import com.google.android.apps.forscience.whistlepunk.metadata.GoosciCaption;
import com.google.android.apps.forscience.whistlepunk.metadata.GoosciLabel;
import com.google.android.apps.forscience.whistlepunk.metadata.GoosciPictureLabelValue.PictureLabelValue;
import com.google.android.apps.forscience.whistlepunk.metadata.GoosciTextLabelValue.TextLabelValue;
import com.google.android.apps.forscience.whistlepunk.performance.PerfTrackerProvider;
import com.google.android.apps.forscience.whistlepunk.review.DeleteMetadataItemDialog;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.snackbar.Snackbar;
import com.google.common.base.Preconditions;
import io.reactivex.disposables.Disposable;
import java.io.File;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Experiment List Fragment lists all experiments that belong to an account. This fragment is used
 * in MainActivity and in ClaimExperimentsAcitivty. The claimExperimentsMode field can be used to
 * determine whether it is in ClaimExperimentsAcitivty.
 *
 * <p>When used in MainActivity, the appAccount field is the "current" account, which may be the
 * NonSignedInAccount.
 *
 * <p>When used in ClaimExperimentsAcitivty, the appAccount field is the NonSignedInAccount and the
 * claimingAccount field is the "current" signed-in account.
 *
 * <p>Note that the options menu is different based on whether claimExperimentsMode is true or
 * false. In claimExperimentsMode, the menu items for action_sync and action_network_disconnected do
 * not exist. Care should be taken to check for null before dereferencing the result of
 * Menu.findItem for these ids.
 */
public class ExperimentListFragment extends Fragment
    implements DeleteMetadataItemDialog.DeleteDialogListener, OnRefreshListener {
  private static final String TAG = "ExperimentListFragment";

  /** Boolean extra for savedInstanceState with the state of includeArchived experiments. */
  private static final String EXTRA_INCLUDE_ARCHIVED = "includeArchived";

  private static final String ARG_ACCOUNT_KEY = "accountKey";
  private static final String ARG_CLAIM_EXPERIMENTS_MODE = "claimExperimentsMode";
  private static final String ARG_CLAIMING_ACCOUNT_KEY = "claimingAccountKey";
  private static final String ARG_USE_PANES = "usePanes";
  public static final String KEY_DEFAULT_EXPERIMENT_CREATED = "key_default_experiment_created";
  private static final String TAG_NEW_EXPERIMENT_BUTTON = "new_experiment_button";

  /** Duration of snackbar length long. 3.5 seconds */
  private static final int LONG_DELAY_MILLIS = 3500;

  private Context applicationContext;
  private ExperimentListAdapter experimentListAdapter;
  private boolean includeArchived;
  private boolean syncProgressBarVisible = false;
  private boolean exportProgressBarVisible = false;
  private boolean claimProgressBarVisible = false;
  private final RxEvent destroyed = new RxEvent();
  private final RxEvent paused = new RxEvent();
  private final IntentFilter networkIntentFilter = new IntentFilter();
  private AppAccount appAccount;
  private boolean claimExperimentsMode;
  private AppAccount claimingAccount;
  private ConnectivityBroadcastReceiver connectivityBroadcastReceiver;
  private Menu optionsMenu = null;
  private FeatureDiscoveryProvider featureDiscoveryProvider;
  private SwipeRefreshLayout swipeLayout;
  private final AtomicBoolean syncing = new AtomicBoolean(false);

  public static ExperimentListFragment newInstance(AppAccount appAccount, boolean usePanes) {
    return newInstance(createArguments(appAccount, usePanes));
  }

  private static ExperimentListFragment newInstance(Bundle arguments) {
    ExperimentListFragment fragment = new ExperimentListFragment();
    fragment.setArguments(arguments);
    return fragment;
  }

  private static Bundle createArguments(AppAccount appAccount, boolean usePanes) {
    Bundle args = new Bundle();
    args.putString(ARG_ACCOUNT_KEY, appAccount.getAccountKey());
    args.putBoolean(ARG_USE_PANES, usePanes);
    return args;
  }

  public static ExperimentListFragment reuseOrCreateInstance(
      @Nullable Fragment fragment, AppAccount appAccount, boolean usePanes) {
    Bundle newArguments = createArguments(appAccount, usePanes);
    if (fragment instanceof ExperimentListFragment
        && newArguments.equals(fragment.getArguments())) {
      return (ExperimentListFragment) fragment;
    }
    return newInstance(newArguments);
  }

  public static ExperimentListFragment newInstanceForClaimExperimentsMode(
      Context context, AppAccount claimingAccount, boolean usePanes) {
    NonSignedInAccount nonSignedInAccount = NonSignedInAccount.getInstance(context);
    ExperimentListFragment fragment = new ExperimentListFragment();
    Bundle args = new Bundle();
    args.putString(ARG_ACCOUNT_KEY, nonSignedInAccount.getAccountKey());
    args.putBoolean(ARG_CLAIM_EXPERIMENTS_MODE, true);
    args.putString(ARG_CLAIMING_ACCOUNT_KEY, claimingAccount.getAccountKey());
    args.putBoolean(ARG_USE_PANES, usePanes);
    fragment.setArguments(args);
    return fragment;
  }

  public ExperimentListFragment() {
    networkIntentFilter.addAction(ConnectivityManager.CONNECTIVITY_ACTION);
  }

  private boolean isFragmentGone() {
    try {
      return getActivity() == null
          || getContext() == null
          || getResources() == null
          || getView() == null;
    } catch (Exception e) {
      return true;
    }
  }

  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    applicationContext = getContext().getApplicationContext();
    AppSingleton.getInstance(applicationContext)
        .whenExportBusyChanges()
        .takeUntil(destroyed.happens())
        .subscribe(
            busy -> {
              Handler uiHandler = new Handler(applicationContext.getMainLooper());
              uiHandler.post(
                  () -> {
                    // This fragment may be gone by the time this code executes.
                    if (isFragmentGone()) {
                      return;
                    }
                    setExportProgressBarVisible(busy);
                  });
            });
    AppSingleton.getInstance(applicationContext)
        .whenSyncBusyChanges()
        .takeUntil(destroyed.happens())
        .subscribe(
            busy -> {
              if (!busy) {
                if (syncing.compareAndSet(true, false)) {
                  appAccount.incrementSyncCompleteCount();
                }
              }
              Handler uiHandler = new Handler(applicationContext.getMainLooper());
              uiHandler.post(
                  () -> {
                    // This fragment may be gone by the time this code executes.
                    if (isFragmentGone()) {
                      return;
                    }
                    setSyncProgressBarVisible(busy);
                  });
            });

    appAccount =
        WhistlePunkApplication.getAccount(applicationContext, getArguments(), ARG_ACCOUNT_KEY);

    claimExperimentsMode = getArguments().getBoolean(ARG_CLAIM_EXPERIMENTS_MODE);
    if (claimExperimentsMode) {
      claimingAccount =
          WhistlePunkApplication.getAccount(
              applicationContext, getArguments(), ARG_CLAIMING_ACCOUNT_KEY);

      // In claim experiments mode, we always start with showing archived experiments, even if the
      // user hid them previously.
      includeArchived = true;
      getActivity().invalidateOptionsMenu();

    } else {
      if (savedInstanceState != null) {
        includeArchived = savedInstanceState.getBoolean(EXTRA_INCLUDE_ARCHIVED, false);
        getActivity().invalidateOptionsMenu();
      }
    }
    featureDiscoveryProvider =
        WhistlePunkApplication.getAppServices(applicationContext).getFeatureDiscoveryProvider();
    setHasOptionsMenu(true);
  }

  @Override
  public void onStart() {
    super.onStart();
    WhistlePunkApplication.getUsageTracker(applicationContext)
        .trackScreenView(TrackerConstants.SCREEN_EXPERIMENT_LIST);
  }

  @Override
  public void onResume() {
    super.onResume();
    setExportProgressBarVisible(exportProgressBarVisible);
    setSyncProgressBarVisible(syncProgressBarVisible);
    setClaimProgressBarVisible(claimProgressBarVisible);

    connectivityBroadcastReceiver = new ConnectivityBroadcastReceiver();
    getContext().registerReceiver(connectivityBroadcastReceiver, networkIntentFilter);

    TimingLogger timing = new TimingLogger(TAG, "Sync on Resume");
    AppSingleton.getInstance(applicationContext)
        .whenNewExperimentSynced()
        .takeUntil(paused.happens())
        .subscribe(
            count -> {
              Handler uiHandler = new Handler(applicationContext.getMainLooper());
              uiHandler.post(
                  () -> {
                    // This fragment may be gone by the time this code executes.
                    if (isFragmentGone()) {
                      return;
                    }
                    loadExperiments();
                    timing.addSplit("Syncing complete");
                    timing.dumpToLog();
                  });
            });
    loadExperiments();
    syncNow("Sync On Resume");
  }

  @Override
  public void onSaveInstanceState(Bundle outState) {
    super.onSaveInstanceState(outState);
    outState.putBoolean(EXTRA_INCLUDE_ARCHIVED, includeArchived);
  }

  @Override
  public void onDestroy() {
    // TODO: Use RxEvent here
    experimentListAdapter.onDestroy();
    destroyed.onHappened();
    super.onDestroy();
  }

  @Override
  public void onPause() {
    getContext().unregisterReceiver(connectivityBroadcastReceiver);
    paused.onHappened();
    super.onPause();
  }


  @Override public void onRefresh() {
    swipeLayout.setRefreshing(false);
    WhistlePunkApplication.getUsageTracker(applicationContext)
        .trackEvent(
            TrackerConstants.CATEGORY_SYNC, TrackerConstants.ACTION_MANUAL_SYNC_STARTED, null, 0);
    syncNow("Sync on Pulldown");
  }


  @Override
  public View onCreateView(
      LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
    View view = inflater.inflate(R.layout.fragment_experiment_list, container, false);
    final RecyclerView detailList = (RecyclerView) view.findViewById(R.id.details);

    experimentListAdapter = new ExperimentListAdapter(this);

    swipeLayout = (SwipeRefreshLayout) view.findViewById(R.id.swipe_container);
    swipeLayout.setOnRefreshListener(this);

    // TODO: Adjust the column count based on breakpoint specs when available.
    int column_count = 2;
    GridLayoutManager manager = new GridLayoutManager(getActivity(), column_count);
    manager.setSpanSizeLookup(
        new GridLayoutManager.SpanSizeLookup() {
          @Override
          public int getSpanSize(int position) {
            return experimentListAdapter.getItemViewType(position)
                    == ExperimentListAdapter.VIEW_TYPE_EXPERIMENT
                ? 1
                : column_count;
          }
        });
    detailList.setLayoutManager(manager);
    detailList.setAdapter(experimentListAdapter);

    FloatingActionButton newExperimentButton =
        (FloatingActionButton) view.findViewById(R.id.new_experiment);
    if (claimExperimentsMode) {
      newExperimentButton.setVisibility(View.GONE);
    } else {
      newExperimentButton.setOnClickListener(
          v -> {
            if (getRecorderController().watchRecordingStatus().blockingFirst().isRecording()) {
              // This should never happen, but apparently it does on some Xperia devices?
              // b/117484248
              return;
            }
            getDataController()
                .createExperiment(
                    new LoggingConsumer<Experiment>(TAG, "Create a new experiment") {
                      @Override
                      public void success(final Experiment experiment) {
                        WhistlePunkApplication.getUsageTracker(applicationContext)
                            .trackEvent(
                                TrackerConstants.CATEGORY_EXPERIMENTS,
                                TrackerConstants.ACTION_CREATE,
                                TrackerConstants.LABEL_EXPERIMENT_LIST,
                                0);
                        launchPanesActivity(
                            v.getContext(),
                            appAccount,
                            experiment.getExperimentId(),
                            false /* claimExperimentsMode */);
                      }
                    });
          });
    }

    return view;
  }

  public static void launchPanesActivity(
      Context context, AppAccount appAccount, String experimentId, boolean claimExperimentsMode) {
    context.startActivity(
        WhistlePunkApplication.getLaunchIntentForPanesActivity(
            context, appAccount, experimentId, claimExperimentsMode));
  }

  private boolean shouldShowClaimExperimentsCard() {
    // We should prompt to claim experiments if:
    // we are not already in claim experiments mode
    // and there is one or more experiments in unclaimed storage.
    return !claimExperimentsMode
        && appAccount.isSignedIn()
        && AccountsUtils.getUnclaimedExperimentCount(applicationContext) >= 1;
  }

  private boolean shouldShowAddExperimentsToDriveCard() {
    return claimExperimentsMode;
  }

  private void loadExperiments() {
    // This fragment may be gone by the time this code executes.
    if (isFragmentGone()) {
      return;
    }

    PerfTrackerProvider perfTracker =
        WhistlePunkApplication.getPerfTrackerProvider(applicationContext);
    PerfTrackerProvider.TimerToken loadExperimentTimer = perfTracker.startTimer();
    getDataController()
        .getExperimentOverviews(
            includeArchived,
            new LoggingConsumer<List<ExperimentOverviewPojo>>(TAG, "Retrieve experiments") {
              @Override
              public void success(List<ExperimentOverviewPojo> experiments) {
                // This fragment may be gone by the time this code executes.
                if (isFragmentGone()) {
                  return;
                }
                if (experiments.isEmpty() && claimExperimentsMode) {
                  getActivity().finish();
                  return;
                }
                if (shouldCreateDefaultExperiment(experiments)) {
                  // If there are no experiments and we've never made a default one,
                  // create the default experiment and set the boolean to true.
                  // Note that we don't create the default experiment if the user is
                  // prompted to claim unclaimed experiments.
                  createDefaultExperiment();
                  boolean discoveryEnabled =
                      featureDiscoveryProvider.isEnabled(
                          applicationContext,
                          appAccount,
                          FeatureDiscoveryProvider.FEATURE_NEW_EXPERIMENT);
                  if (discoveryEnabled) {
                    scheduleFeatureDiscovery();
                  }
                  perfTracker.stopTimer(
                      loadExperimentTimer, TrackerConstants.PRIMES_DEFAULT_EXPERIMENT_CREATED);
                } else {
                  attachToExperiments(experiments);
                  perfTracker.stopTimer(
                      loadExperimentTimer, TrackerConstants.PRIMES_EXPERIMENT_LIST_LOADED);
                }
                perfTracker.onAppInteractive();
              }
            });
  }

  private void scheduleFeatureDiscovery() {
    Handler handler = new Handler(applicationContext.getMainLooper());
    handler.postDelayed(
        this::showFeatureDiscovery, FeatureDiscoveryProvider.FEATURE_DISCOVERY_SHOW_DELAY_MS);
  }

  private void showFeatureDiscovery() {
    // This fragment may be gone by the time this code executes.
    if (isFragmentGone()) {
      return;
    }

    // Confirm that a view with the tag exists, so featureDiscoveryProvider can find it.
    final View view = this.getView().findViewWithTag(TAG_NEW_EXPERIMENT_BUTTON);
    if (view != null) {
      featureDiscoveryProvider.show(
          getActivity(),
          appAccount,
          FeatureDiscoveryProvider.FEATURE_NEW_EXPERIMENT,
          TAG_NEW_EXPERIMENT_BUTTON);
    }
  }

  private SharedPreferences getSharedPreferences() {
    return AccountsUtils.getSharedPreferences(applicationContext, appAccount);
  }

  private boolean shouldCreateDefaultExperiment(List<ExperimentOverviewPojo> experiments) {
    return experiments.isEmpty()
        && !wasDefaultExperimentCreated()
        && !shouldShowClaimExperimentsCard()
        && (!appAccount.isSignedIn() || appAccount.getSyncCompleteCount() > 0)
        && AppSingleton.getInstance(applicationContext)
            .getExperimentLibraryManager(appAccount)
            .getKnownExperiments()
            .isEmpty();
  }

  private boolean wasDefaultExperimentCreated() {
    return getSharedPreferences().getBoolean(KEY_DEFAULT_EXPERIMENT_CREATED, false);
  }

  private void setDefaultExperimentCreated() {
    getSharedPreferences().edit().putBoolean(KEY_DEFAULT_EXPERIMENT_CREATED, true).apply();
  }

  private void createDefaultExperiment() {
    setDefaultExperimentCreated();
    DataController dataController = getDataController();
    RxDataController.createExperiment(dataController)
        .subscribe(
            e -> {
              // This fragment may be gone by the time this code executes.
              // However, we can still use applicationContext, appAccount, and dataController to
              // add labels to the default experiment.
              initializeDefaultExperiment(applicationContext, appAccount, e);

              RxDataController.updateExperiment(dataController, e, true)
                  .subscribe(
                      () -> {
                        // This fragment may be gone by the time this code executes.
                        if (isFragmentGone()) {
                          return;
                        }
                        loadExperiments();
                      });
            });
  }

  private static void initializeDefaultExperiment(
      Context applicationContext, AppAccount appAccount, Experiment e) {
    Resources res = applicationContext.getResources();
    e.setTitle(res.getString(R.string.first_experiment_title));
    Clock clock =
        AppSingleton.getInstance(applicationContext).getSensorEnvironment().getDefaultClock();

    // Create a text label 1 second ago with default text.
    TextLabelValue goosciTextLabel1 =
        TextLabelValue.newBuilder()
            .setText(res.getString(R.string.first_experiment_second_text_note))
            .build();
    Label textLabel1 =
        Label.newLabelWithValue(
            clock.getNow() - 1000, GoosciLabel.Label.ValueType.TEXT, goosciTextLabel1, null);
    e.addLabel(e, textLabel1);

    // Create a text label 2 seconds ago with default text.
    TextLabelValue goosciTextLabel2 =
        TextLabelValue.newBuilder()
            .setText(res.getString(R.string.first_experiment_text_note))
            .build();
    Label textLabel2 =
        Label.newLabelWithValue(
            clock.getNow() - 2000, GoosciLabel.Label.ValueType.TEXT, goosciTextLabel2, null);
    e.addLabel(e, textLabel2);

    // Create a picture label 4 second ago with a default drawable and caption.
    GoosciCaption.Caption.Builder caption =
        GoosciCaption.Caption.newBuilder()
            .setText(res.getString(R.string.first_experiment_picture_note_caption))
            .setLastEditedTimestamp(clock.getNow() - 4000);
    Label pictureLabel =
        Label.newLabel(caption.getLastEditedTimestamp(), GoosciLabel.Label.ValueType.PICTURE);
    File pictureFile =
        PictureUtils.createImageFile(
            applicationContext, appAccount, e.getExperimentId(), pictureLabel.getLabelId());
    PictureUtils.writeDrawableToFile(applicationContext, pictureFile, R.drawable.first_note);
    PictureLabelValue goosciPictureLabel =
        PictureLabelValue.newBuilder()
            .setFilePath(
                FileMetadataUtil.getInstance()
                    .getRelativePathInExperiment(e.getExperimentId(), pictureFile))
            .build();
    pictureLabel.setLabelProtoData(goosciPictureLabel);
    pictureLabel.setCaption(caption.build());
    e.addLabel(e, pictureLabel);

    // TODO: Add a recording item if required by b/64844798.
  }

  private void attachToExperiments(List<ExperimentOverviewPojo> experiments) {
    final View rootView = getView();
    if (rootView == null) {
      return;
    }
    experimentListAdapter.setData(experiments, includeArchived);
  }

  private DataController getDataController() {
    return AppSingleton.getInstance(applicationContext).getDataController(appAccount);
  }

  private RecorderController getRecorderController() {
    return AppSingleton.getInstance(applicationContext).getRecorderController(appAccount);
  }

  private ExperimentLibraryManager getExperimentLibraryManager() {
    return AppSingleton.getInstance(applicationContext).getExperimentLibraryManager(appAccount);
  }

  public void setSyncProgressBarVisible(boolean visible) {
    syncProgressBarVisible = visible;
    // This fragment may be gone by the time this code executes.
    if (isFragmentGone()) {
      return;
    }
    getView().findViewById(R.id.syncIndeterminateBar)
        .setVisibility(visible ? View.VISIBLE : View.GONE);
  }

  public void setExportProgressBarVisible(boolean visible) {
    exportProgressBarVisible = visible;
    // This fragment may be gone by the time this code executes.
    if (isFragmentGone()) {
      return;
    }
    getView().findViewById(R.id.exportIndeterminateBar)
        .setVisibility(visible ? View.VISIBLE : View.GONE);
  }

  private void setClaimProgressBarVisible(boolean visible) {
    claimProgressBarVisible = visible;
    // This fragment may be gone by the time this code executes.
    if (isFragmentGone()) {
      return;
    }
    getView()
        .findViewById(R.id.claimIndeterminateBar)
        .setVisibility(visible ? View.VISIBLE : View.GONE);
  }

  boolean handleOnBackPressed() {
    // If we are currently claiming an experiment, don't go back.
    if (claimProgressBarVisible) {
      return true;
    }

    // The activity can handle it normally.
    return false;
  }

  @Override
  public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
    super.onCreateOptionsMenu(menu, inflater);
    if (claimExperimentsMode) {
      inflater.inflate(R.menu.menu_claim_experiments, menu);
      ColorUtils.colorDrawable(
          applicationContext,
          menu.findItem(R.id.run_review_overflow_menu).getIcon(),
          R.color.claim_experiments_action_bar_text);

    } else {
      inflater.inflate(R.menu.menu_experiment_list, menu);
    }
    optionsMenu = menu;
    updateNetworkStatusIcon();
  }

  @Override
  public void onPrepareOptionsMenu(Menu menu) {
    menu.findItem(R.id.action_include_archived).setVisible(!includeArchived);
    menu.findItem(R.id.action_exclude_archived).setVisible(includeArchived);
    optionsMenu = menu;
    updateNetworkStatusIcon();
  }

  private void updateNetworkStatusIcon() {
    if (isFragmentGone()) {
      return;
    }

    if (optionsMenu == null) {
      return;
    }

    MenuItem menuItemActionNetworkDisconnected =
        optionsMenu.findItem(R.id.action_network_disconnected);

    // In claim experiments mode, the menus are different. If menuItemActionNetworkDisconnected is
    // null, the menu item doesn't exist.
    if (menuItemActionNetworkDisconnected == null) {
      return;
    }

    if (!appAccount.isSignedIn()) {
      menuItemActionNetworkDisconnected.setVisible(false);
      return;
    }

    ConnectivityManager cm =
        (ConnectivityManager) applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE);
    boolean shouldShowIcon =
        cm.getActiveNetworkInfo() == null || !cm.getActiveNetworkInfo().isConnectedOrConnecting();
    menuItemActionNetworkDisconnected.setVisible(shouldShowIcon);
    menuItemActionNetworkDisconnected.setEnabled(shouldShowIcon);
    if (shouldShowIcon) {
      getView()
          .announceForAccessibility(
              getResources().getString(R.string.drive_sync_cannot_reach_google_drive));
    }
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    int id = item.getItemId();
    if (exportProgressBarVisible || claimProgressBarVisible) {
      return true;
    }
    if (id == R.id.action_include_archived) {
      includeArchived = true;
      loadExperiments();
      getActivity().invalidateOptionsMenu();
      return true;
    } else if (id == R.id.action_exclude_archived) {
      includeArchived = false;
      loadExperiments();
      getActivity().invalidateOptionsMenu();
      return true;
    } else if (id == R.id.action_claim_unclaimed_experiments) {
      confirmClaimUnclaimedExperiments();
      return true;
    } else if (id == R.id.action_delete_unclaimed_experiments) {
      confirmDeleteUnclaimedExperiments();
      return true;
    } else if (id == R.id.action_network_disconnected) {
      Resources res = applicationContext.getResources();
      experimentListAdapter.showSnackbar(
          res.getString(R.string.drive_sync_cannot_reach_google_drive), null);
    }
    return super.onOptionsItemSelected(item);
  }

  private void syncNow(String logMessage) {
    // This fragment may be gone by the time this code executes.
    if (isFragmentGone()) {
      return;
    }
    if (appAccount.isSignedIn()) {
      // Check if the account hasn't been loaded yet.
      if (appAccount.getAccount() == null) {
        syncLater(logMessage);
        return;
      }
      CloudSyncProvider syncProvider =
          WhistlePunkApplication.getCloudSyncProvider(applicationContext);
      CloudSyncManager syncService = syncProvider.getServiceForAccount(appAccount);
      try {
        syncing.set(true);
        getView()
            .announceForAccessibility(
                getResources().getString(R.string.action_sync_start));

        syncService.syncExperimentLibrary(applicationContext, logMessage);
      } catch (IOException ioe) {
        if (Log.isLoggable(TAG, Log.ERROR)) {
          Log.e(TAG, "IOE", ioe);
        }
      }
    } else {
      loadExperiments();
    }
  }

  private void syncLater(String logMessage) {
    new Handler(applicationContext.getMainLooper())
        .post(
            () -> {
              // This fragment may be gone by the time this code executes.
              if (isFragmentGone()) {
                return;
              }
              syncNow(logMessage);
            });
  }

  private void confirmClaimUnclaimedExperiments() {
    Context context = getContext();
    int unclaimedExperimentCount = AccountsUtils.getUnclaimedExperimentCount(applicationContext);
    AlertDialog.Builder builder = new AlertDialog.Builder(context);
    builder.setTitle(
        context
            .getResources()
            .getQuantityString(
                R.plurals.claim_all_confirmation_text,
                unclaimedExperimentCount,
                unclaimedExperimentCount));
    builder.setNegativeButton(android.R.string.cancel, (dialog, which) -> dialog.cancel());
    builder.setPositiveButton(
        R.string.claim_all_confirmation_yes,
        (dialog, which) -> {
          claimUnclaimedExperiments();
          dialog.dismiss();
        });
    builder.create().show();
  }

  private void claimUnclaimedExperiments() {
    setClaimProgressBarVisible(true);
    getDataController()
        .moveAllExperimentsToAnotherAccount(
            claimingAccount,
            new LoggingConsumer<Success>(TAG, "claimUnclaimedExperiments") {
              @Override
              public void success(Success value) {
                // This fragment may be gone by the time this code executes.
                if (isFragmentGone()) {
                  return;
                }
                setClaimProgressBarVisible(false);
                getActivity().finish();
              }

              @Override
              public void fail(Exception e) {
                String labelFromStackTrace = TrackerConstants.createLabelFromStackTrace(e);
                UsageTracker usageTracker =
                    WhistlePunkApplication.getUsageTracker(applicationContext);
                usageTracker.trackEvent(
                    TrackerConstants.CATEGORY_CLAIMING_DATA,
                    TrackerConstants.ACTION_FAILED,
                    labelFromStackTrace,
                    0);
                usageTracker.trackEvent(
                    TrackerConstants.CATEGORY_FAILURE,
                    TrackerConstants.ACTION_CLAIM_FAILED,
                    labelFromStackTrace,
                    0);
                experimentListAdapter.showSnackbar(
                    applicationContext.getResources().getString(R.string.claim_failed), null);
                super.fail(e);
              }
            });
  }

  private void confirmDeleteUnclaimedExperiments() {
    AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
    builder.setTitle(R.string.delete_all_prompt_headline);
    builder.setMessage(R.string.delete_all_prompt_text);
    builder.setNegativeButton(android.R.string.cancel, (dialog, which) -> dialog.cancel());
    builder.setPositiveButton(
        R.string.delete_all_prompt_yes,
        (dialog, which) -> {
          deleteUnclaimedExperiments();
          dialog.dismiss();
        });
    builder.create().show();
  }

  private void deleteUnclaimedExperiments() {
    getDataController()
        .deleteAllExperiments(
            new LoggingConsumer<Success>(TAG, "deleteUnclaimedExperiments") {
              @Override
              public void success(Success value) {
                // This fragment may be gone by the time this code executes.
                if (isFragmentGone()) {
                  return;
                }
                getActivity().finish();
              }
            });
  }

  private void confirmDelete(String experimentId) {
    DeleteMetadataItemDialog dialog =
        DeleteMetadataItemDialog.newInstance(
            R.string.delete_experiment_dialog_title,
            R.string.delete_experiment_dialog_message,
            experimentId);
    dialog.show(getChildFragmentManager(), DeleteMetadataItemDialog.TAG);
  }

  @Override
  public void requestDelete(Bundle extras) {
    String experimentId = extras.getString(DeleteMetadataItemDialog.KEY_ITEM_ID);
    DataController dataController = getDataController();
    RxDataController.getExperimentById(dataController, experimentId)
        .subscribe(
            fullExperiment -> {
              dataController.deleteExperiment(
                  fullExperiment,
                  new LoggingConsumer<Success>(TAG, "delete experiment") {
                    @Override
                    public void success(Success value) {
                      // This fragment may be gone by the time this code executes.
                      if (isFragmentGone()) {
                        return;
                      }
                      experimentListAdapter.onExperimentDeleted(experimentId);
                      WhistlePunkApplication.getUsageTracker(applicationContext)
                          .trackEvent(
                              TrackerConstants.CATEGORY_EXPERIMENTS,
                              TrackerConstants.ACTION_DELETED,
                              TrackerConstants.LABEL_EXPERIMENT_LIST,
                              0);
                      if (claimExperimentsMode) {
                        WhistlePunkApplication.getUsageTracker(applicationContext)
                            .trackEvent(
                                TrackerConstants.CATEGORY_CLAIMING_DATA,
                                TrackerConstants.ACTION_DELETE_SINGLE,
                                null,
                                0);
                      }
                      syncLater("Sync on Delete");
                      maybeFinishClaimExperimentsMode();
                    }
                  });
            },
            error -> {
              if (Log.isLoggable(TAG, Log.ERROR)) {
                Log.e(TAG, "Delete current experiment in ExperimentListFragment failed", error);
              }
              throw new IllegalStateException(
                  "Delete current experiment in ExperimentListFragment failed", error);
            });
  }

  private void maybeFinishClaimExperimentsMode() {
    // This fragment may be gone by the time this code executes.
    if (isFragmentGone()) {
      return;
    }
    // If the item count is now 1, then the only item is the
    // add_experiments_to_drive_card. There are no unclaimed experiments left.
    if (claimExperimentsMode && experimentListAdapter.getItemCount() == 1) {
      getActivity().finish();
    }
  }

  static class ExperimentListItem {
    public final int viewType;
    public final ExperimentOverviewPojo experimentOverview;
    public final String dateString;

    ExperimentListItem(ExperimentOverviewPojo experimentOverview) {
      viewType = ExperimentListAdapter.VIEW_TYPE_EXPERIMENT;
      this.experimentOverview = experimentOverview;
      dateString = null;
    }

    ExperimentListItem(String date) {
      viewType = ExperimentListAdapter.VIEW_TYPE_DATE;
      dateString = date;
      experimentOverview = null;
    }

    ExperimentListItem(int viewType) {
      this.viewType = viewType;
      dateString = null;
      experimentOverview = null;
    }
  }

  public static class ExperimentListAdapter extends RecyclerView.Adapter<ViewHolder> {
    static final int VIEW_TYPE_EXPERIMENT = 0;
    static final int VIEW_TYPE_EMPTY = 1;
    static final int VIEW_TYPE_DATE = 2;
    static final int VIEW_TYPE_CLAIM_EXPERIMENTS = 3;
    static final int VIEW_TYPE_ADD_EXPERIMENTS_TO_DRIVE = 4;
    private final Drawable placeHolderImage;

    private final Context applicationContext;
    private final List<ExperimentListItem> items;
    private boolean includeArchived;
    private final Calendar calendar;
    private final int currentYear;
    private final String monthYearFormat;

    private final WeakReference<ExperimentListFragment> parentReference;
    private final SnackbarManager snackbarManager = new SnackbarManager();
    private PopupMenu popupMenu = null;

    public ExperimentListAdapter(ExperimentListFragment parent) {
      items = new ArrayList<>();
      applicationContext = parent.applicationContext;
      placeHolderImage =
          applicationContext.getResources().getDrawable(R.drawable.experiment_card_placeholder);
      calendar = Calendar.getInstance(applicationContext.getResources().getConfiguration().locale);
      currentYear = calendar.get(Calendar.YEAR);
      monthYearFormat = applicationContext.getResources().getString(R.string.month_year_format);
      parentReference = new WeakReference<>(parent);
    }

    private boolean isParentGone() {
      return parentReference.get() == null;
    }

    void setData(List<ExperimentOverviewPojo> experimentOverviews, boolean includeArchived) {
      if (isParentGone()) {
        return;
      }
      this.includeArchived = includeArchived;
      items.clear();
      if (parentReference.get().shouldShowClaimExperimentsCard()) {
        items.add(new ExperimentListItem(VIEW_TYPE_CLAIM_EXPERIMENTS));
      }
      if (parentReference.get().shouldShowAddExperimentsToDriveCard()) {
        items.add(new ExperimentListItem(VIEW_TYPE_ADD_EXPERIMENTS_TO_DRIVE));
      }
      if (experimentOverviews.isEmpty()) {
        items.add(new ExperimentListItem(VIEW_TYPE_EMPTY));
      } else {
        // Sort most recent first
        Collections.sort(
            experimentOverviews,
            (eo1, eo2) -> Long.compare(eo2.getLastUsedTimeMs(), eo1.getLastUsedTimeMs()));
        String date = "";
        for (ExperimentOverviewPojo overview : experimentOverviews) {
          // Only show the year if it is not this year.
          calendar.setTime(new Date(overview.getLastUsedTimeMs()));
          String nextDate =
              DateFormat.format(
                      calendar.get(Calendar.YEAR) == currentYear ? "MMMM" : monthYearFormat,
                      calendar)
                  .toString();
          if (!TextUtils.equals(date, nextDate)) {
            date = nextDate;
            items.add(new ExperimentListItem(date));
          }
          items.add(new ExperimentListItem(overview));
        }
      }
      notifyDataSetChanged();
    }

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
      Preconditions.checkState(!isParentGone());
      LayoutInflater inflater = LayoutInflater.from(parent.getContext());
      View view;
      if (viewType == VIEW_TYPE_EMPTY) {
        view = inflater.inflate(R.layout.empty_list, parent, false);
      } else if (viewType == VIEW_TYPE_DATE) {
        view = inflater.inflate(R.layout.experiment_date, parent, false);
      } else if (viewType == VIEW_TYPE_CLAIM_EXPERIMENTS) {
        view = inflater.inflate(R.layout.claim_experiments_card, parent, false);
      } else if (viewType == VIEW_TYPE_ADD_EXPERIMENTS_TO_DRIVE) {
        view = inflater.inflate(R.layout.add_experiments_to_drive_card, parent, false);
      } else { // VIEW_TYPE_EXPERIMENT
        view =
            inflater.inflate(
                parentReference.get().claimExperimentsMode
                    ? R.layout.claim_experiment_overview
                    : R.layout.project_experiment_overview,
                parent,
                false);
      }
      return new ViewHolder(view, viewType, parentReference.get().claimExperimentsMode);
    }

    @Override
    public void onBindViewHolder(ViewHolder holder, int position) {
      if (items.get(position).viewType == VIEW_TYPE_EXPERIMENT) {
        bindExperiment(holder, items.get(position));
      } else if (items.get(position).viewType == VIEW_TYPE_DATE) {
        ((TextView) holder.itemView).setText(items.get(position).dateString);
      } else if (items.get(position).viewType == VIEW_TYPE_CLAIM_EXPERIMENTS) {
        int unclaimedExperimentCount =
            AccountsUtils.getUnclaimedExperimentCount(applicationContext);
        TextView textView = holder.itemView.findViewById(R.id.text_claim_experiments);
        textView.setText(
            applicationContext
                .getResources()
                .getQuantityString(
                    R.plurals.claim_experiments_card_text,
                    unclaimedExperimentCount,
                    unclaimedExperimentCount));
        holder.claimButton.setOnClickListener(
            v -> {
              long mbFree = FileMetadataUtil.getInstance().getFreeSpaceInMb();
              if (mbFree < 100) {
                showSnackbar(
                    applicationContext.getResources().getString(R.string.claim_failed_disk_space),
                    null);
                return;
              }
              if (isParentGone()) {
                return;
              }
              ClaimExperimentsActivity.launch(
                  v.getContext(),
                  parentReference.get().appAccount,
                  parentReference.get().getArguments().getBoolean(ARG_USE_PANES));
            });
      }
    }

    @Override
    public int getItemCount() {
      return items.size();
    }

    @Override
    public int getItemViewType(int position) {
      return items.get(position).viewType;
    }

    private void bindExperiment(final ViewHolder holder, final ExperimentListItem item) {
      if (isParentGone()) {
        return;
      }
      Resources res = applicationContext.getResources();
      // First on the UI thread, set what experiment we're trying to load.
      ExperimentOverviewPojo overview = item.experimentOverview;
      holder.experimentId = overview.getExperimentId();

      // Set the data we know about.
      String experimentText = Experiment.getDisplayTitle(applicationContext, overview.getTitle());
      holder.experimentTitle.setText(experimentText);
      holder.archivedIndicator.setVisibility(overview.isArchived() ? View.VISIBLE : View.GONE);

      if (overview.isArchived()) {
        holder.experimentTitle.setContentDescription(
            res.getString(R.string.archived_content_description, experimentText));
        holder
            .itemView
            .findViewById(R.id.content)
            .setAlpha(res.getFraction(R.fraction.metadata_card_archived_alpha, 1, 1));
      } else {
        // Use default.
        holder.experimentTitle.setContentDescription("");
        holder
            .itemView
            .findViewById(R.id.content)
            .setAlpha(res.getFraction(R.fraction.metadata_card_alpha, 1, 1));
      }

      holder.itemView.setTag(R.id.experiment_title, overview.getExperimentId());

      holder.cardView.setOnClickListener(
          v -> {
            if (isParentGone()) {
              return;
            }
            // If we are currently claiming an experiment, don't launch PanesActivity.
            if (parentReference.get().claimProgressBarVisible) {
              return;
            }
            launchPanesActivity(
                v.getContext(),
                parentReference.get().appAccount,
                overview.getExperimentId(),
                parentReference.get().claimExperimentsMode);
          });

      Context context = holder.menuButton.getContext();
      boolean isShareIntentValid =
          FileMetadataUtil.getInstance()
              .validateShareIntent(
                  applicationContext, parentReference.get().appAccount, overview.getExperimentId());
      if (parentReference.get().claimExperimentsMode) {
        // In claim experiments mode, we don't show the overflow menu, but we do show buttons for
        // saving to drive and sharing/downloading.
        holder.menuButton.setVisibility(View.GONE);
        holder.driveButton.setOnClickListener(
            v -> promptBeforeClaimExperiment(overview.getExperimentId()));
        holder.downloadButton.setOnClickListener(
            v -> requestDownload(parentReference.get().getActivity(), overview.getExperimentId()));
        holder.shareButton.setVisibility(View.GONE);
        holder.deleteButton.setOnClickListener(v -> deleteExperiment(overview.getExperimentId()));
      } else if (parentReference
          .get()
          .getRecorderController()
          .watchRecordingStatus()
          .blockingFirst()
          .isRecording()) {
        // This should never occur. But apparently it does on some Lenovo K5 devices: b/119263772
        // BlockingFirst above is ok because there will always be a RecordingStatus. This won't
        // ever actually block.
        holder.menuButton.setVisibility(View.GONE);
      } else {
        holder.menuButton.setOnClickListener(
            v -> {
              int position = items.indexOf(item);

              popupMenu =
                  new PopupMenu(
                      context,
                      holder.menuButton,
                      Gravity.NO_GRAVITY,
                      R.attr.actionOverflowMenuStyle,
                      0);
              popupMenu
                  .getMenuInflater()
                  .inflate(R.menu.menu_experiment_overview, popupMenu.getMenu());
              popupMenu
                  .getMenu()
                  .findItem(R.id.menu_item_archive)
                  .setVisible(!overview.isArchived());
              popupMenu
                  .getMenu()
                  .findItem(R.id.menu_item_unarchive)
                  .setVisible(overview.isArchived());
              popupMenu
                  .getMenu()
                  .findItem(R.id.menu_item_export_experiment)
                  .setVisible(isShareIntentValid);

              popupMenu.setOnMenuItemClickListener(
                  menuItem -> {
                    if (isParentGone()) {
                      return true;
                    }
                    if (parentReference.get().exportProgressBarVisible
                        || parentReference.get().claimProgressBarVisible) {
                      return true;
                    }
                    if (menuItem.getItemId() == R.id.menu_item_archive) {
                      setExperimentArchived(overview, position, true);
                      return true;
                    } else if (menuItem.getItemId() == R.id.menu_item_unarchive) {
                      setExperimentArchived(overview, position, false);
                      return true;
                    } else if (menuItem.getItemId() == R.id.menu_item_delete) {
                      deleteExperiment(overview.getExperimentId());
                      return true;
                    } else if (menuItem.getItemId() == R.id.menu_item_export_experiment) {
                      exportOrSaveExperiment(overview.getExperimentId(), false);
                      return true;
                    } else if (menuItem.getItemId() == R.id.menu_item_download_experiment) {
                      requestDownload(
                          parentReference.get().getActivity(), overview.getExperimentId());
                      return true;
                    }
                    return false;
                  });
              popupMenu.setOnDismissListener(menu -> popupMenu = null);
              popupMenu.show();
            });
      }

      if (!TextUtils.isEmpty(overview.getImagePath())) {
        PictureUtils.loadExperimentOverviewImage(
            parentReference.get().appAccount, holder.experimentImage, overview.getImagePath());
      } else {
        // Make sure the scale type is correct for the placeholder
        holder.experimentImage.setScaleType(ImageView.ScaleType.FIT_CENTER);
        holder.experimentImage.setImageDrawable(placeHolderImage);
        int[] intArray =
            applicationContext.getResources().getIntArray(R.array.experiment_colors_array);
        holder.experimentImage.setBackgroundColor(intArray[overview.getColorIndex()]);
      }
    }

    private void requestDownload(Activity activity, String experimentId) {
      ExportService.requestDownloadPermissions(
          () -> exportOrSaveExperiment(experimentId, true),
          activity,
          android.R.id.content,
          null,
          null);
    }

    private void setExperimentArchived(
        ExperimentOverviewPojo overview, final int position, boolean archived) {
      if (isParentGone()) {
        return;
      }
      overview.setArchived(archived);
      DataController dataController = parentReference.get().getDataController();
      ExperimentLibraryManager elm = parentReference.get().getExperimentLibraryManager();
      // This Disposable was added to avoid a lint failure. It is not used.
      Disposable experimentSingle =
          RxDataController.getExperimentById(dataController, overview.getExperimentId())
              .subscribe(
                  fullExperiment -> {
                    fullExperiment.setArchived(
                        applicationContext, dataController.getAppAccount(), archived);
                    elm.setArchived(fullExperiment.getExperimentId(), archived);
                    dataController.updateExperiment(
                        overview.getExperimentId(),
                        new LoggingConsumer<Success>(TAG, "set archived bit") {
                          @Override
                          public void success(Success value) {
                            updateArchivedState(position, archived);
                            WhistlePunkApplication.getUsageTracker(applicationContext)
                                .trackEvent(
                                    TrackerConstants.CATEGORY_EXPERIMENTS,
                                    archived
                                        ? TrackerConstants.ACTION_ARCHIVE
                                        : TrackerConstants.ACTION_UNARCHIVE,
                                    TrackerConstants.LABEL_EXPERIMENT_LIST,
                                    0);
                            showArchivedSnackbar(overview, position, archived);
                          }
                        });
                  },
                  error -> {
                    if (Log.isLoggable(TAG, Log.ERROR)) {
                      Log.e(TAG, "Archive experiment in ExperimentListFragment failed", error);
                    }
                    throw new IllegalStateException(
                        "Archive experiment in ExperimentListFragment failed", error);
                  });
    }

    private void updateArchivedState(int position, boolean archived) {
      if (includeArchived) {
        notifyItemChanged(position);
      } else if (archived) {
        // Remove archived experiment immediately.
        int i = position;
        removeExperiment(i);
      } else {
        // It could be added back anywhere.
        if (isParentGone()) {
          return;
        }
        parentReference.get().loadExperiments();
      }
    }

    private void showClaimedSnackbar() {
      if (isParentGone()) {
        return;
      }
      String accountName = parentReference.get().claimingAccount.getAccountName();
      String message =
          applicationContext.getResources().getString(R.string.experiment_added_text, accountName);
      showSnackbar(message, null /* undoOnClickListener */);
    }

    private void showArchivedSnackbar(
        ExperimentOverviewPojo overview, int position, boolean archived) {
      if (isParentGone()) {
        return;
      }
      String message =
          applicationContext
              .getResources()
              .getString(
                  archived
                      ? R.string.archived_experiment_message
                      : R.string.unarchived_experiment_message);
      // We only seem to show "undo" for archiving items, not unarchiving them.
      View.OnClickListener undoOnClickListener =
          archived ? view -> setExperimentArchived(overview, position, !archived) : null;
      showSnackbar(message, undoOnClickListener);
    }

    public void showSnackbar(String message, @Nullable View.OnClickListener undoOnClickListener) {
      if (isParentGone()) {
        return;
      }
      Snackbar bar =
          AccessibilityUtils.makeSnackbar(
              parentReference.get().getView(), message, Snackbar.LENGTH_LONG);
      if (undoOnClickListener != null) {
        bar.setAction(R.string.action_undo, undoOnClickListener);
      }
      snackbarManager.showSnackbar(bar);
    }

    public void onExperimentDeleted(String experimentId) {
      int index = -1;
      for (int i = 0; i < items.size(); i++) {
        ExperimentListItem item = items.get(i);
        if (item.viewType == VIEW_TYPE_EXPERIMENT
            && TextUtils.equals(item.experimentOverview.getExperimentId(), experimentId)) {
          index = i;
          break;
        }
      }
      if (index > 0) {
        removeExperiment(index);
      }
    }

    private void removeExperiment(int index) {
      items.remove(index);
      notifyItemRemoved(index);

      // Remove the previous item if it is a date with no children.
      // We don't need to index check that index is zero because there must be a date card
      // somewhere above the experiment we just removed. So, an experiment is never at index zero.
      if (items.get(index - 1).viewType == VIEW_TYPE_DATE) {
        // The previous item is a date.
        // If there are no items after that date, or the item after that date is also a date
        if (index == items.size() || items.get(index).viewType == VIEW_TYPE_DATE) {
          items.remove(index - 1);
          if (items.isEmpty()) {
            notifyDataSetChanged();
          } else {
            notifyItemRemoved(index - 1);
          }
        }
      }
    }

    private void promptBeforeClaimExperiment(String experimentId) {
      if (isParentGone()) {
        return;
      }
      // If we are currently claiming an experiment, don't claim another one.
      if (parentReference.get().claimProgressBarVisible) {
        return;
      }
      AlertDialog.Builder builder = new AlertDialog.Builder(parentReference.get().getContext());
      builder.setTitle(R.string.drive_confirmation_text);
      builder.setNegativeButton(android.R.string.cancel, (dialog, which) -> dialog.cancel());
      builder.setPositiveButton(
          R.string.drive_confirmation_yes,
          (dialog, which) -> {
            claimExperiment(experimentId);
            dialog.dismiss();
          });
      AlertDialog dialog = builder.create();
      dialog.show();
      // Need to reset the content description so the button will be read correctly b/116869645
      dialog
          .getButton(DialogInterface.BUTTON_POSITIVE)
          .setContentDescription(
              applicationContext.getResources().getString(R.string.drive_confirmation_yes));
    }

    private void claimExperiment(String experimentId) {
      if (isParentGone()) {
        return;
      }
      parentReference.get().setClaimProgressBarVisible(true);
      parentReference
          .get()
          .getDataController()
          .moveExperimentToAnotherAccount(
              experimentId,
              parentReference.get().claimingAccount,
              new LoggingConsumer<Success>(TAG, "claimExperiments") {
                @Override
                public void success(Success value) {
                  if (isParentGone()) {
                    return;
                  }
                  parentReference.get().setClaimProgressBarVisible(false);
                  onExperimentDeleted(experimentId);
                  WhistlePunkApplication.getUsageTracker(applicationContext)
                      .trackEvent(
                          TrackerConstants.CATEGORY_CLAIMING_DATA,
                          TrackerConstants.ACTION_CLAIM_SINGLE,
                          null,
                          0);
                  showClaimedSnackbar();
                  // When the snackbar disappears, finish claim experiments mode if there are no
                  // experiments left.
                  new Handler()
                      .postDelayed(
                          () -> {
                            if (isParentGone()) {
                              return;
                            }
                            parentReference.get().maybeFinishClaimExperimentsMode();
                          },
                          LONG_DELAY_MILLIS);
                }
              });
    }

    private void deleteExperiment(String experimentId) {
      if (isParentGone()) {
        return;
      }
      // If we are currently claiming an experiment, don't delete an experiment.
      if (parentReference.get().claimProgressBarVisible) {
        return;
      }
      snackbarManager.hideVisibleSnackbar();
      parentReference.get().confirmDelete(experimentId);
    }

    private void exportOrSaveExperiment(String experimentId, boolean saveLocally) {
      if (isParentGone()) {
        return;
      }
      // If we are currently claiming an experiment, don't export an experiment.
      if (parentReference.get().claimProgressBarVisible) {
        return;
      }
      String trackerAction =
          saveLocally ? TrackerConstants.ACTION_DOWNLOAD_REQUESTED : TrackerConstants.ACTION_SHARED;
      WhistlePunkApplication.getUsageTracker(applicationContext)
          .trackEvent(
              TrackerConstants.CATEGORY_EXPERIMENTS,
              trackerAction,
              TrackerConstants.LABEL_EXPERIMENT_LIST,
              0);
      if (parentReference.get().claimExperimentsMode) {
        // For claim experiments mode, we use ACTION_SHARE instead of ACTION_SHARED
        String claimTrackerAction =
            saveLocally
                ? TrackerConstants.ACTION_DOWNLOAD_REQUESTED
                : TrackerConstants.ACTION_SHARE;
        WhistlePunkApplication.getUsageTracker(applicationContext)
            .trackEvent(TrackerConstants.CATEGORY_CLAIMING_DATA, claimTrackerAction, null, 0);
      }
      parentReference.get().setExportProgressBarVisible(true);
      Context context = parentReference.get().getContext();
      ExportService.handleExperimentExportClick(
          context, parentReference.get().appAccount, experimentId, saveLocally);
    }

    public void onDestroy() {
      snackbarManager.onDestroy();
      if (popupMenu != null) {
        popupMenu.dismiss();
      }
    }
  }

  public static class ViewHolder extends RecyclerView.ViewHolder {

    // Accessing via fields for faster access.

    /** Experiment ID that is being loaded or has been loaded. */
    public String experimentId;

    public TextView experimentTitle;
    public ImageView experimentImage;
    public View archivedIndicator;
    public View cardView;
    public ImageButton menuButton;
    public ImageButton driveButton;
    public ImageButton shareButton;
    public ImageButton downloadButton;
    public ImageButton deleteButton;
    public Button claimButton;

    int viewType;

    public ViewHolder(View itemView, int viewType, boolean claimExperimentsMode) {
      super(itemView);
      this.viewType = viewType;
      if (viewType == ExperimentListAdapter.VIEW_TYPE_EXPERIMENT) {
        cardView = itemView.findViewById(R.id.card_view);
        experimentImage = (ImageView) itemView.findViewById(R.id.experiment_image);
        experimentTitle = (TextView) itemView.findViewById(R.id.experiment_title);
        archivedIndicator = itemView.findViewById(R.id.archived_indicator);
        menuButton = (ImageButton) itemView.findViewById(R.id.menu_button);
        if (claimExperimentsMode) {
          driveButton = (ImageButton) itemView.findViewById(R.id.drive_button);
          shareButton = (ImageButton) itemView.findViewById(R.id.share_button);
          downloadButton = (ImageButton) itemView.findViewById(R.id.download_button);
          deleteButton = (ImageButton) itemView.findViewById(R.id.delete_button);
        }
      } else if (viewType == ExperimentListAdapter.VIEW_TYPE_CLAIM_EXPERIMENTS) {
        claimButton = (Button) itemView.findViewById(R.id.btn_claim_experiments);
      }
    }
  }

  private class ConnectivityBroadcastReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
      updateNetworkStatusIcon();
    }
  }
}
