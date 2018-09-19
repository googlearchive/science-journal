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
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.net.ConnectivityManager;
import android.os.Bundle;
import android.os.Handler;
import androidx.annotation.Nullable;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import androidx.fragment.app.Fragment;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v4.widget.SwipeRefreshLayout.OnRefreshListener;
import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.appcompat.widget.PopupMenu;
import androidx.recyclerview.widget.RecyclerView;
import android.text.TextUtils;
import android.text.format.DateFormat;
import android.util.Log;
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
import com.google.android.apps.forscience.javalib.Success;
import com.google.android.apps.forscience.whistlepunk.AccessibilityUtils;
import com.google.android.apps.forscience.whistlepunk.AndroidVersionUtils;
import com.google.android.apps.forscience.whistlepunk.AppSingleton;
import com.google.android.apps.forscience.whistlepunk.Clock;
import com.google.android.apps.forscience.whistlepunk.ColorUtils;
import com.google.android.apps.forscience.whistlepunk.DataController;
import com.google.android.apps.forscience.whistlepunk.ExportService;
import com.google.android.apps.forscience.whistlepunk.LoggingConsumer;
import com.google.android.apps.forscience.whistlepunk.PictureUtils;
import com.google.android.apps.forscience.whistlepunk.R;
import com.google.android.apps.forscience.whistlepunk.RxDataController;
import com.google.android.apps.forscience.whistlepunk.RxEvent;
import com.google.android.apps.forscience.whistlepunk.SnackbarManager;
import com.google.android.apps.forscience.whistlepunk.WhistlePunkApplication;
import com.google.android.apps.forscience.whistlepunk.accounts.AccountsProvider;
import com.google.android.apps.forscience.whistlepunk.accounts.AccountsUtils;
import com.google.android.apps.forscience.whistlepunk.accounts.AppAccount;
import com.google.android.apps.forscience.whistlepunk.accounts.NonSignedInAccount;
import com.google.android.apps.forscience.whistlepunk.analytics.TrackerConstants;
import com.google.android.apps.forscience.whistlepunk.cloudsync.CloudSyncManager;
import com.google.android.apps.forscience.whistlepunk.cloudsync.CloudSyncProvider;
import com.google.android.apps.forscience.whistlepunk.filemetadata.Experiment;
import com.google.android.apps.forscience.whistlepunk.filemetadata.ExperimentLibraryManager;
import com.google.android.apps.forscience.whistlepunk.filemetadata.FileMetadataManager;
import com.google.android.apps.forscience.whistlepunk.filemetadata.Label;
import com.google.android.apps.forscience.whistlepunk.metadata.nano.GoosciCaption;
import com.google.android.apps.forscience.whistlepunk.metadata.nano.GoosciLabel;
import com.google.android.apps.forscience.whistlepunk.metadata.nano.GoosciPictureLabelValue;
import com.google.android.apps.forscience.whistlepunk.metadata.nano.GoosciTextLabelValue;
import com.google.android.apps.forscience.whistlepunk.metadata.nano.GoosciUserMetadata;
import com.google.android.apps.forscience.whistlepunk.performance.PerfTrackerProvider;
import com.google.android.apps.forscience.whistlepunk.review.DeleteMetadataItemDialog;
import java.io.File;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.List;

/** Experiment List Fragment lists all experiments. */
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

  /** Duration of snackbar length long. 3.5 seconds */
  private static final int LONG_DELAY_MILLIS = 3500;

  private ExperimentListAdapter experimentListAdapter;
  private boolean includeArchived;
  private boolean progressBarVisible = false;
  private final RxEvent destroyed = new RxEvent();
  private final RxEvent paused = new RxEvent();
  private final IntentFilter networkIntentFilter = new IntentFilter();
  private AppAccount appAccount;
  private boolean requireSignedInAccount;
  private boolean claimExperimentsMode;
  private AppAccount claimingAccount;
  private SwipeRefreshLayout swipeLayout;
  private ConnectivityBroadcastReceiver connectivityBroadcastReceiver;
  private Menu optionsMenu = null;

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
    networkIntentFilter.addAction(android.net.ConnectivityManager.CONNECTIVITY_ACTION);
  }

  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    AppSingleton.getInstance(getContext())
        .whenExportBusyChanges()
        .takeUntil(destroyed.happens())
        .subscribe(
            busy -> {
              setProgressBarVisible(busy);
            });

    AccountsProvider accountsProvider =
        WhistlePunkApplication.getAppServices(getContext()).getAccountsProvider();
    requireSignedInAccount = accountsProvider.requireSignedInAccount();

    appAccount = WhistlePunkApplication.getAccount(getContext(), getArguments(), ARG_ACCOUNT_KEY);

    claimExperimentsMode = getArguments().getBoolean(ARG_CLAIM_EXPERIMENTS_MODE);
    if (claimExperimentsMode) {
      claimingAccount =
          WhistlePunkApplication.getAccount(getContext(), getArguments(), ARG_CLAIMING_ACCOUNT_KEY);

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
    setHasOptionsMenu(true);
  }

  @Override
  public void onStart() {
    super.onStart();
    WhistlePunkApplication.getUsageTracker(getActivity())
        .trackScreenView(TrackerConstants.SCREEN_EXPERIMENT_LIST);
  }

  @Override
  public void onResume() {
    super.onResume();
    setProgressBarVisible(progressBarVisible);
    if (claimExperimentsMode) {
      loadExperiments();
    }

    connectivityBroadcastReceiver = new ConnectivityBroadcastReceiver();
    getContext().registerReceiver(connectivityBroadcastReceiver, networkIntentFilter);

    AppSingleton.getInstance(getContext())
        .whenSyncBusyChanges()
        .takeUntil(paused.happens())
        .subscribe(
            busy -> {
              if (!busy) {
                Handler uiHandler = new Handler(getContext().getMainLooper());
                uiHandler.post(() -> {
                  loadExperiments();
                  swipeLayout.setRefreshing(false);
                });
              }
            });


    swipeLayout.setRefreshing(true);
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
          v ->
              getDataController()
                  .createExperiment(
                      new LoggingConsumer<Experiment>(TAG, "Create a new experiment") {
                        @Override
                        public void success(final Experiment experiment) {
                          WhistlePunkApplication.getUsageTracker(getActivity())
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
                      }));
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
        && requireSignedInAccount
        && AccountsUtils.getUnclaimedExperimentCount(getContext()) >= 1;
  }

  private boolean shouldShowAddExperimentsToDriveCard() {
    return claimExperimentsMode;
  }

  private void loadExperiments() {
    // Don't show any experiments until the user has signed in.
    if (!claimExperimentsMode && requireSignedInAccount && !appAccount.isSignedIn()) {
      attachToExperiments(new ArrayList<>());
      return;
    }

    PerfTrackerProvider perfTracker = WhistlePunkApplication.getPerfTrackerProvider(getActivity());
    PerfTrackerProvider.TimerToken loadExperimentTimer = perfTracker.startTimer();
    getDataController()
        .getExperimentOverviews(
            includeArchived,
            new LoggingConsumer<List<GoosciUserMetadata.ExperimentOverview>>(
                TAG, "Retrieve experiments") {
              @Override
              public void success(List<GoosciUserMetadata.ExperimentOverview> experiments) {
                // In case the account changes multiple times quickly, ignore the results if
                // the activity is now null.
                if (getActivity() == null) {
                  return;
                }
                if (experiments.isEmpty()
                    && !wasDefaultExperimentCreated()
                    && !shouldShowClaimExperimentsCard()) {
                  // If there are no experiments and we've never made a default one,
                  // create the default experiment and set the boolean to true.
                  // Note that we don't create the default experiment if the user is
                  // prompted to claim unclaimed experiments.
                  createDefaultExperiment();
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

  private SharedPreferences getSharedPreferences() {
    return AccountsUtils.getSharedPreferences(getContext(), appAccount);
  }

  private boolean wasDefaultExperimentCreated() {
    return getSharedPreferences().getBoolean(KEY_DEFAULT_EXPERIMENT_CREATED, false);
  }

  private void setDefaultExperimentCreated() {
    getSharedPreferences().edit().putBoolean(KEY_DEFAULT_EXPERIMENT_CREATED, true).apply();
  }

  private void createDefaultExperiment() {
    DataController dataController = getDataController();
    RxDataController.createExperiment(dataController)
        .subscribe(
            e -> {
              Resources res = getActivity().getResources();
              e.setTitle(res.getString(R.string.first_experiment_title));
              Clock clock =
                  AppSingleton.getInstance(getActivity()).getSensorEnvironment().getDefaultClock();

              // Create a text label 1 second ago with default text.
              GoosciTextLabelValue.TextLabelValue goosciTextLabel1 =
                  new GoosciTextLabelValue.TextLabelValue();
              goosciTextLabel1.text = res.getString(R.string.first_experiment_second_text_note);
              Label textLabel1 =
                  Label.newLabelWithValue(
                      clock.getNow() - 1000,
                      GoosciLabel.Label.ValueType.TEXT,
                      goosciTextLabel1,
                      null);
              e.addLabel(e, textLabel1);

              // Create a text label 2 seconds ago with default text.
              GoosciTextLabelValue.TextLabelValue goosciTextLabel2 =
                  new GoosciTextLabelValue.TextLabelValue();
              goosciTextLabel2.text = res.getString(R.string.first_experiment_text_note);
              Label textLabel2 =
                  Label.newLabelWithValue(
                      clock.getNow() - 2000,
                      GoosciLabel.Label.ValueType.TEXT,
                      goosciTextLabel2,
                      null);
              e.addLabel(e, textLabel2);

              // Create a picture label 4 second ago with a default drawable and caption.
              GoosciCaption.Caption caption = new GoosciCaption.Caption();
              caption.text = res.getString(R.string.first_experiment_picture_note_caption);
              caption.lastEditedTimestamp = clock.getNow() - 4000;
              Label pictureLabel =
                  Label.newLabel(caption.lastEditedTimestamp, GoosciLabel.Label.ValueType.PICTURE);
              File pictureFile =
                  PictureUtils.createImageFile(
                      getActivity(),
                      dataController.getAppAccount(),
                      e.getExperimentId(),
                      pictureLabel.getLabelId());
              PictureUtils.writeDrawableToFile(getActivity(), pictureFile, R.drawable.first_note);
              GoosciPictureLabelValue.PictureLabelValue goosciPictureLabel =
                  new GoosciPictureLabelValue.PictureLabelValue();
              goosciPictureLabel.filePath =
                  FileMetadataManager.getRelativePathInExperiment(e.getExperimentId(), pictureFile);
              pictureLabel.setLabelProtoData(goosciPictureLabel);
              pictureLabel.setCaption(caption);
              e.addLabel(e, pictureLabel);

              // TODO: Add a recording item if required by b/64844798.

              RxDataController.updateExperiment(dataController, e)
                  .subscribe(
                      () -> {
                        setDefaultExperimentCreated();
                        loadExperiments();
                      });
            });
  }

  private void attachToExperiments(List<GoosciUserMetadata.ExperimentOverview> experiments) {
    final View rootView = getView();
    if (rootView == null) {
      return;
    }
    experimentListAdapter.setData(experiments, includeArchived);
  }

  private DataController getDataController() {
    return AppSingleton.getInstance(getActivity()).getDataController(appAccount);
  }

  private ExperimentLibraryManager getExperimentLibraryManager() {
    return AppSingleton.getInstance(getActivity()).getExperimentLibraryManager(appAccount);
  }

  public void setProgressBarVisible(boolean visible) {
    progressBarVisible = visible;
    if (getView() != null) {
      if (visible) {
        getView().findViewById(R.id.indeterminateBar).setVisibility(View.VISIBLE);
      } else {
        getView().findViewById(R.id.indeterminateBar).setVisibility(View.GONE);
      }
    }
  }

  @Override
  public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
    super.onCreateOptionsMenu(menu, inflater);
    if (claimExperimentsMode) {
      inflater.inflate(R.menu.menu_claim_experiments, menu);
      ColorUtils.colorDrawable(
          getContext(),
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
    if (optionsMenu == null) {
      return;
    }
    ConnectivityManager cm =
        (ConnectivityManager) getContext().getSystemService(Context.CONNECTIVITY_SERVICE);
    boolean shouldShowIcon =
        cm.getActiveNetworkInfo() == null || !cm.getActiveNetworkInfo().isConnectedOrConnecting();
    optionsMenu.findItem(R.id.action_network_disconnected).setVisible(shouldShowIcon);
    optionsMenu.findItem(R.id.action_network_disconnected).setEnabled(shouldShowIcon);
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    int id = item.getItemId();
    if (progressBarVisible) {
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
    } else if (id == R.id.action_sync) {
      swipeLayout.setRefreshing(true);
      syncNow("Sync from menu");
      return true;
    } else if (id == R.id.action_network_disconnected) {
      Resources res = getActivity().getResources();
      experimentListAdapter.showSnackbar(
          res.getString(R.string.drive_sync_cannot_reach_google_drive), null);
    }
    return super.onOptionsItemSelected(item);
  }

  private void syncNow(String logMessage) {
    CloudSyncProvider syncProvider = WhistlePunkApplication.getCloudSyncProvider(getActivity());
    CloudSyncManager syncService = syncProvider.getServiceForAccount(appAccount);
    try {
      syncService.syncExperimentLibrary(getContext(), logMessage);
    } catch (IOException ioe) {
      if (Log.isLoggable(TAG, Log.ERROR)) {
        Log.e(TAG, "IOE", ioe);
      }
    }
  }

  private void confirmClaimUnclaimedExperiments() {
    Context context = getContext();
    int unclaimedExperimentCount = AccountsUtils.getUnclaimedExperimentCount(context);
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
    getDataController()
        .moveAllExperimentsToAnotherAccount(
            claimingAccount,
            new LoggingConsumer<Success>(TAG, "claimUnclaimedExperiments") {
              @Override
              public void success(Success value) {
                getActivity().finish();
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
                      experimentListAdapter.onExperimentDeleted(experimentId);
                      WhistlePunkApplication.getUsageTracker(getActivity())
                          .trackEvent(
                              TrackerConstants.CATEGORY_EXPERIMENTS,
                              TrackerConstants.ACTION_DELETED,
                              TrackerConstants.LABEL_EXPERIMENT_LIST,
                              0);
                      maybeFinishClaimExperimentsMode();
                    }
                  });
            });
  }

  private void maybeFinishClaimExperimentsMode() {
    // If the item count is now 1, then the only item is the
    // add_experiments_to_drive_card. There are no unclaimed experiments left.
    if (claimExperimentsMode && experimentListAdapter.getItemCount() == 1) {
      Activity activity = getActivity();
      if (activity != null) {
        activity.finish();
      }
    }
  }

  static class ExperimentListItem {
    public final int viewType;
    public final GoosciUserMetadata.ExperimentOverview experimentOverview;
    public final String dateString;

    ExperimentListItem(GoosciUserMetadata.ExperimentOverview experimentOverview) {
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
      placeHolderImage =
          parent.getActivity().getResources().getDrawable(R.drawable.experiment_card_placeholder);
      calendar =
          Calendar.getInstance(parent.getActivity().getResources().getConfiguration().locale);
      currentYear = calendar.get(Calendar.YEAR);
      monthYearFormat = parent.getActivity().getResources().getString(R.string.month_year_format);
      parentReference = new WeakReference<>(parent);
    }

    void setData(
        List<GoosciUserMetadata.ExperimentOverview> experimentOverviews, boolean includeArchived) {
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
            (eo1, eo2) -> Long.compare(eo2.lastUsedTimeMs, eo1.lastUsedTimeMs));
        String date = "";
        for (GoosciUserMetadata.ExperimentOverview overview : experimentOverviews) {
          // Only show the year if it is not this year.
          calendar.setTime(new Date(overview.lastUsedTimeMs));
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
        Context context = holder.itemView.getContext();
        int unclaimedExperimentCount = AccountsUtils.getUnclaimedExperimentCount(context);
        TextView textView = holder.itemView.findViewById(R.id.text_claim_experiments);
        textView.setText(
            context
                .getResources()
                .getQuantityString(
                    R.plurals.claim_experiments_card_text,
                    unclaimedExperimentCount,
                    unclaimedExperimentCount));
        holder.claimButton.setOnClickListener(
            v ->
                ClaimExperimentsActivity.launch(
                    v.getContext(),
                    parentReference.get().appAccount,
                    parentReference.get().getArguments().getBoolean(ARG_USE_PANES)));
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
      Resources res = holder.itemView.getResources();
      // First on the UI thread, set what experiment we're trying to load.
      GoosciUserMetadata.ExperimentOverview overview = item.experimentOverview;
      holder.experimentId = overview.experimentId;

      // Set the data we know about.
      String experimentText =
          Experiment.getDisplayTitle(holder.itemView.getContext(), overview.title);
      holder.experimentTitle.setText(experimentText);
      holder.archivedIndicator.setVisibility(overview.isArchived ? View.VISIBLE : View.GONE);

      if (overview.isArchived) {
        holder.experimentTitle.setContentDescription(
            res.getString(R.string.archived_content_description, experimentText));
        holder
            .itemView
            .findViewById(R.id.content)
            .setAlpha(res.getFraction(R.fraction.metadata_card_archived_alpha, 1, 1));
        setCardColor(holder, res.getColor(R.color.archived_background_color));
      } else {
        // Use default.
        holder.experimentTitle.setContentDescription("");
        holder
            .itemView
            .findViewById(R.id.content)
            .setAlpha(res.getFraction(R.fraction.metadata_card_alpha, 1, 1));
        setCardColor(holder, res.getColor(R.color.text_color_white));
      }

      holder.itemView.setTag(R.id.experiment_title, overview.experimentId);

      holder.cardView.setOnClickListener(
          v -> {
            if (!parentReference.get().progressBarVisible) {
              launchPanesActivity(
                  v.getContext(),
                  parentReference.get().appAccount,
                  overview.experimentId,
                  parentReference.get().claimExperimentsMode);
            }
          });

      Context context = holder.menuButton.getContext();
      boolean isShareIntentValid =
          FileMetadataManager.validateShareIntent(
              context, parentReference.get().appAccount, overview.experimentId);
      if (parentReference.get().claimExperimentsMode) {
        holder.menuButton.setVisibility(View.GONE);
        holder.driveButton.setOnClickListener(
            v -> promptBeforeClaimExperiment(overview.experimentId));
        if (isShareIntentValid) {
          holder.shareButton.setOnClickListener(v -> exportExperiment(overview.experimentId));
        } else {
          holder.shareButton.setVisibility(View.GONE);
        }
        holder.deleteButton.setOnClickListener(v -> deleteExperiment(overview.experimentId));
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
              popupMenu.getMenu().findItem(R.id.menu_item_archive).setVisible(!overview.isArchived);
              popupMenu
                  .getMenu()
                  .findItem(R.id.menu_item_unarchive)
                  .setVisible(overview.isArchived);
              popupMenu
                  .getMenu()
                  .findItem(R.id.menu_item_export_experiment)
                  .setVisible(isShareIntentValid);

              popupMenu.setOnMenuItemClickListener(
                  menuItem -> {
                    if (parentReference.get().progressBarVisible) {
                      return true;
                    }
                    if (menuItem.getItemId() == R.id.menu_item_archive) {
                      setExperimentArchived(overview, position, true);
                      return true;
                    } else if (menuItem.getItemId() == R.id.menu_item_unarchive) {
                      setExperimentArchived(overview, position, false);
                      return true;
                    } else if (menuItem.getItemId() == R.id.menu_item_delete) {
                      deleteExperiment(overview.experimentId);
                      return true;
                    } else if (menuItem.getItemId() == R.id.menu_item_export_experiment) {
                      exportExperiment(overview.experimentId);
                      return true;
                    }
                    return false;
                  });
              popupMenu.setOnDismissListener(menu -> popupMenu = null);
              popupMenu.show();
            });
      }

      if (!TextUtils.isEmpty(overview.imagePath)) {
        PictureUtils.loadExperimentOverviewImage(
            parentReference.get().appAccount, holder.experimentImage, overview.imagePath);
      } else {
        // Make sure the scale type is correct for the placeholder
        holder.experimentImage.setScaleType(ImageView.ScaleType.FIT_CENTER);
        holder.experimentImage.setImageDrawable(placeHolderImage);
        int[] intArray =
            holder
                .experimentImage
                .getContext()
                .getResources()
                .getIntArray(R.array.experiment_colors_array);
        holder.experimentImage.setBackgroundColor(intArray[overview.colorIndex]);
      }
    }

    private void setExperimentArchived(
        GoosciUserMetadata.ExperimentOverview overview, final int position, boolean archived) {
      if (parentReference.get() == null) {
        return;
      }
      Context context = parentReference.get().getContext();
      overview.isArchived = archived;
      DataController dataController = parentReference.get().getDataController();
      ExperimentLibraryManager elm = parentReference.get().getExperimentLibraryManager();
      RxDataController.getExperimentById(dataController, overview.experimentId)
          .subscribe(
              fullExperiment -> {
                fullExperiment.setArchived(context, dataController.getAppAccount(), archived);
                elm.setArchived(fullExperiment.getExperimentId(), archived);
                dataController.updateExperiment(
                    overview.experimentId,
                    new LoggingConsumer<Success>(TAG, "set archived bit") {
                      @Override
                      public void success(Success value) {
                        updateArchivedState(position, archived);
                        WhistlePunkApplication.getUsageTracker(parentReference.get().getActivity())
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
        if (parentReference.get() != null) {
          parentReference.get().loadExperiments();
        }
      }
    }

    private void showClaimedSnackbar() {
      if (parentReference.get() == null) {
        return;
      }
      String accountName = parentReference.get().claimingAccount.getAccountName();
      String message =
          parentReference
              .get()
              .getResources()
              .getString(R.string.experiment_added_text, accountName);
      showSnackbar(message, null /* undoOnClickListener */);
    }

    private void showArchivedSnackbar(
        GoosciUserMetadata.ExperimentOverview overview, int position, boolean archived) {
      if (parentReference.get() == null) {
        return;
      }
      String message =
          parentReference
              .get()
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
      Snackbar bar =
          AccessibilityUtils.makeSnackbar(
              parentReference.get().getView(), message, Snackbar.LENGTH_LONG);
      if (undoOnClickListener != null) {
        bar.setAction(R.string.action_undo, undoOnClickListener);
      }
      snackbarManager.showSnackbar(bar);
    }

    private void setCardColor(ViewHolder holder, int color) {
      if (AndroidVersionUtils.isApiLevelAtLeastLollipop()) {
        // holder.cardView.setBackgroundColor(color);
      } else {
        // Setting the color of the CardView in KitKat has a side-effect of making the
        // drop shadow disappear around the card. Instead, we set the background color
        // of the content of the card, which looks almost as good. And has UX approval.
        // See b/70328251
        holder.cardView.findViewById(R.id.content).setBackgroundColor(color);
      }
    }

    public void onExperimentDeleted(String experimentId) {
      int index = -1;
      for (int i = 0; i < items.size(); i++) {
        ExperimentListItem item = items.get(i);
        if (item.viewType == VIEW_TYPE_EXPERIMENT
            && TextUtils.equals(item.experimentOverview.experimentId, experimentId)) {
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
      AlertDialog.Builder builder = new AlertDialog.Builder(parentReference.get().getContext());
      builder.setTitle(R.string.drive_confirmation_text);
      builder.setNegativeButton(android.R.string.cancel, (dialog, which) -> dialog.cancel());
      builder.setPositiveButton(
          R.string.drive_confirmation_yes,
          (dialog, which) -> {
            claimExperiment(experimentId);
            dialog.dismiss();
          });
      builder.create().show();
    }

    private void claimExperiment(String experimentId) {
      parentReference
          .get()
          .getDataController()
          .moveExperimentToAnotherAccount(
              experimentId,
              parentReference.get().claimingAccount,
              new LoggingConsumer<Success>(TAG, "claimExperiments") {
                @Override
                public void success(Success value) {
                  onExperimentDeleted(experimentId);
                  showClaimedSnackbar();
                  // When the snackbar disappears, finish claim experiments mode if there are no
                  // experiments left.
                  new Handler()
                      .postDelayed(
                          () -> {
                            if (parentReference.get() != null) {
                              parentReference.get().maybeFinishClaimExperimentsMode();
                            }
                          },
                          LONG_DELAY_MILLIS);
                }
              });
    }

    private void deleteExperiment(String experimentId) {
      snackbarManager.hideVisibleSnackbar();
      parentReference.get().confirmDelete(experimentId);
    }

    private void exportExperiment(String experimentId) {
      Context context = parentReference.get().getContext();
      WhistlePunkApplication.getUsageTracker(context)
          .trackEvent(
              TrackerConstants.CATEGORY_EXPERIMENTS,
              TrackerConstants.ACTION_SHARED,
              TrackerConstants.LABEL_EXPERIMENT_LIST,
              0);
      parentReference.get().setProgressBarVisible(true);
      ExportService.handleExperimentExportClick(
          context, parentReference.get().appAccount, experimentId);
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
