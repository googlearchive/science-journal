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

import static android.content.Intent.ACTION_VIEW;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;
import androidx.core.view.GravityCompat;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.widget.Toolbar;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import com.google.android.apps.forscience.whistlepunk.accounts.AccountsProvider;
import com.google.android.apps.forscience.whistlepunk.accounts.AppAccount;
import com.google.android.apps.forscience.whistlepunk.accounts.GetStartedActivity;
import com.google.android.apps.forscience.whistlepunk.accounts.OldUserOptionPromptActivity;
import com.google.android.apps.forscience.whistlepunk.accounts.SignInActivity;
import com.google.android.apps.forscience.whistlepunk.analytics.TrackerConstants;
import com.google.android.apps.forscience.whistlepunk.cloudsync.CloudSyncManager;
import com.google.android.apps.forscience.whistlepunk.cloudsync.CloudSyncProvider;
import com.google.android.apps.forscience.whistlepunk.feedback.FeedbackProvider;
import com.google.android.apps.forscience.whistlepunk.filemetadata.Experiment;
import com.google.android.apps.forscience.whistlepunk.filemetadata.ExperimentLibraryManager;
import com.google.android.apps.forscience.whistlepunk.filemetadata.LocalSyncManager;
import com.google.android.apps.forscience.whistlepunk.project.ExperimentListFragment;
import com.google.android.material.navigation.NavigationView;
import com.google.android.material.snackbar.Snackbar;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/** The main activity. */
public class MainActivity extends ActivityWithNavigationView {
  private static final String TAG = "MainActivity";
  public static final String ARG_SELECTED_NAV_ITEM_ID = "selected_nav_item_id";
  // TODO(lizlooney): It looks like the ARG_USE_PANES value is always true. Investigate whether the
  // ARG_USE_PANES value is ever false and if not, remove the argument in MainActivity,
  // ExperimentListFragment, and ClaimExperimentsActivity.
  public static final String ARG_USE_PANES = "use_panes";
  protected static final int NO_SELECTED_ITEM = -1;

  /**
   * The ARG_SELECTED_NAV_ITEM_ID value from onCreate's savedInstanceState if there is one, or
   * NO_SELECTED_ITEM.
   */
  private int savedItemId;

  /** Used to store the last screen title. For use in {@link #restoreActionBar()}. */
  private CharSequence titleToRestore;

  private AccountsProvider accountsProvider;
  @Nullable private AppAccount currentAccount;

  private FeedbackProvider feedbackProvider;
  private NavigationView navigationView;
  private MultiTouchDrawerLayout drawerLayout;
  private int selectedItemId = NO_SELECTED_ITEM;
  private boolean isRecording = false;

  /** Receives an event every time the activity pauses */
  private final RxEvent pause = new RxEvent();
  /** Receives an event every time the current account changes */
  private final RxEvent currentAccountChanging = new RxEvent();

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    savedItemId =
        (savedInstanceState == null)
            ? NO_SELECTED_ITEM
            : savedInstanceState.getInt(ARG_SELECTED_NAV_ITEM_ID, NO_SELECTED_ITEM);

    WhistlePunkApplication.getPerfTrackerProvider(this).onActivityInit();

    accountsProvider = WhistlePunkApplication.getAppServices(this).getAccountsProvider();

    setContentView(R.layout.activity_main);
    accountsProvider.installAccountSwitcher(this);

    Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
    setSupportActionBar(toolbar);

    boolean isTablet = getResources().getBoolean(R.bool.is_tablet);
    if (!isTablet) {
      setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
    }

    final ActionBar actionBar = getSupportActionBar();
    if (actionBar != null) {
      actionBar.setDisplayHomeAsUpEnabled(true);
      actionBar.setHomeAsUpIndicator(R.drawable.ic_menu_white_24dp);
      actionBar.setHomeActionContentDescription(R.string.navigation_drawer_content_description);
      actionBar.setDisplayShowTitleEnabled(true);
    }

    drawerLayout = (MultiTouchDrawerLayout) findViewById(R.id.drawer_layout);
    drawerLayout.setStatusBarBackgroundColor(getResources().getColor(R.color.color_primary_dark));
    navigationView = (NavigationView) findViewById(R.id.navigation);
    navigationView.setNavigationItemSelectedListener(this);

    // Only show dev testing options when requested.
    if (!Flags.showTestingOptions()) {
      navigationView.getMenu().removeItem(R.id.dev_testing_options);
    }

    feedbackProvider = WhistlePunkApplication.getAppServices(this).getFeedbackProvider();

    setVolumeControlStream(AudioManager.STREAM_MUSIC);
  }

  @Override
  protected void onSaveInstanceState(Bundle outState) {
    super.onSaveInstanceState(outState);
    outState.putInt(ARG_SELECTED_NAV_ITEM_ID, selectedItemId);
  }

  @Override
  public void onResume() {
    super.onResume();
    AppSingleton appSingleton = AppSingleton.getInstance(this);
    appSingleton.setResumedActivity(this);
    pause.happensNext().subscribe(() -> appSingleton.setNoLongerResumedActivity(this));

    rememberAttemptingImport();

    if (showRequiredScreensIfNeeded()) {
      return;
    }

    // Navigate to the desired fragment, based on saved state or intent extras, or (by default)
    // the experiments list.
    Intent intent = getIntent();
    Bundle extras = null;
    if (intent != null) {
      extras = intent.getExtras();
    }
    int selectedNavItemId;
    if (savedItemId != NO_SELECTED_ITEM) {
      selectedNavItemId = savedItemId;
    } else if (extras != null) {
      selectedNavItemId = extras.getInt(ARG_SELECTED_NAV_ITEM_ID, R.id.navigation_item_experiments);
    } else {
      selectedNavItemId = R.id.navigation_item_experiments;
    }
    MenuItem item = navigationView.getMenu().findItem(selectedNavItemId);
    if (item == null) {
      selectedNavItemId = R.id.navigation_item_experiments;
      item = navigationView.getMenu().findItem(selectedNavItemId);
    }
    navigationView.setCheckedItem(selectedNavItemId);
    onNavigationItemSelected(item);

    // Subscribe to account switches.
    accountsProvider
        .getObservableCurrentAccount()
        .takeUntil(pause.happens())
        .subscribe(this::onAccountSwitched);

    if (!isMultiWindowEnabled()) {
      // Subscribe to the recording status.
      watchRecordingStatus();
    }
    // If we get to here, it's safe to log the mode we are in: user has signed in or signed out.
    trackMode();

    if (isAttemptingImport() && currentAccount != null) {
      attemptImport();
    }
  }

  private void attemptImport() {
    WhistlePunkApplication.getUsageTracker(this)
        .trackEvent(
            TrackerConstants.CATEGORY_EXPERIMENTS,
            TrackerConstants.ACTION_IMPORTED,
            TrackerConstants.LABEL_EXPERIMENT_LIST,
            0);

    if (!isRecording) {
      ExportService.handleExperimentImport(this, currentAccount, getIntent().getData());
      AppSingleton.getInstance(this).getAndClearMostRecentOpenWasImport();
    } else {
      AccessibilityUtils.makeSnackbar(
              findViewById(R.id.drawer_layout),
              getResources().getString(R.string.import_failed_recording),
              Snackbar.LENGTH_SHORT)
          .show();
    }

    // Clear the intent so we don't try to import again.
    setIntent(null);
  }

  public boolean isAttemptingImport() {
    return getIntent() != null
        && getIntent().getAction() != null
        && getIntent().getAction().equals(ACTION_VIEW);
  }

  private void trackMode() {
    String labelMode =
        accountsProvider.isSignedIn()
            ? TrackerConstants.LABEL_MODE_SIGNED_IN
            : TrackerConstants.LABEL_MODE_SIGNED_OUT;
    WhistlePunkApplication.getUsageTracker(this)
        .trackEvent(TrackerConstants.CATEGORY_APP, TrackerConstants.ACTION_SET_MODE, labelMode, 0);
  }

  @Override
  protected void onNewIntent(Intent intent) {
    if (intent != null) {
      setIntent(intent);
    }
    super.onNewIntent(intent);

    if (navigationView != null && navigationView.getMenu() != null) {
      int desiredItemId = NO_SELECTED_ITEM;
      if (intent.getExtras() != null) {
        desiredItemId = intent.getExtras().getInt(ARG_SELECTED_NAV_ITEM_ID, NO_SELECTED_ITEM);
      }
      if (desiredItemId != NO_SELECTED_ITEM && selectedItemId != desiredItemId) {
        onNavigationItemSelected(navigationView.getMenu().findItem(desiredItemId));
      }
    }
  }

  @Override
  protected void onPause() {
    if (!isMultiWindowEnabled()) {
      // Dispose of the recording status subscription.
      pause.onHappened();
    }
    super.onPause();
  }

  @Override
  protected void onStart() {
    super.onStart();
    if (isMultiWindowEnabled()) {
      // Subscribe to the recording status.
      watchRecordingStatus();
    }
  }

  @Override
  protected void onStop() {
    accountsProvider.disconnectAccountSwitcher(this);
    if (isMultiWindowEnabled()) {
      // Dispose of the recording status subscription.
      pause.onHappened();
    }
    super.onStop();
  }

  @Override
  protected void onDestroy() {
    super.onDestroy();
  }

  private boolean isMultiWindowEnabled() {
    return MultiWindowUtils.isMultiWindowEnabled(getApplicationContext());
  }

  /**
   * Subscribes to the recording status for the current account, until either RxEvents pause or
   * currentAccountChanging happens. This method is called when the MainActivity is resumed (or
   * started for multi-window) and after the currentAccounts has changed.
   *
   * <p>RxEvent pause is triggered when the MainActivity is paused (or stopped for multi-window).
   *
   * <p>RxEvent currentAccountChanging is triggered when the current account is changing (before the
   * call to watchRecordingStatus).
   */
  private void watchRecordingStatus() {
    if (currentAccount == null) {
      return;
    }
    AppSingleton singleton = AppSingleton.getInstance(this);
    RecorderController rc = singleton.getRecorderController(currentAccount);

    // TODO: extract and test
    rc.watchRecordingStatus()
        .takeUntil(pause.happens())
        .takeUntil(currentAccountChanging.happens())
        .subscribe(
            status -> {
              isRecording = status.isRecording();
              // TODO: Add experimentId to RecordingStatus
              if (isRecording) {
                rememberAttemptingImport();
                final AppAccount appAccount = currentAccount;
                singleton
                    .getDataController(currentAccount)
                    .getLastUsedUnarchivedExperiment(
                        new LoggingConsumer<Experiment>(TAG, "getting last used experiment") {
                          @Override
                          public void success(Experiment experiment) {
                            if (experiment != null && appAccount.equals(currentAccount)) {
                              startActivity(
                                  WhistlePunkApplication.getLaunchIntentForPanesActivity(
                                      MainActivity.this,
                                      currentAccount,
                                      experiment.getExperimentId(),
                                      false /* claimExperimentsMode */));
                            }
                          }
                        });
              }
            });
  }

  private void rememberAttemptingImport() {
    AppSingleton.getInstance(this).setMostRecentOpenWasImport(isAttemptingImport());
  }

  /**
   * If we haven't shown all the required screens, opens the next required activity.
   *
   * @return true if a required screen is now open, false if there are no required screens that need
   *     to be shown.
   */
  private boolean showRequiredScreensIfNeeded() {
    if (!accountsProvider.supportSignedInAccount()) {
      return false;
    }

    if (GetStartedActivity.shouldLaunch(this)) {
      Intent intent = new Intent(this, GetStartedActivity.class);
      startActivityForResult(intent, ActivityRequestCodes.REQUEST_GET_STARTED_ACTIVITY);
      return true;
    }

    if (SignInActivity.shouldLaunch(this) || OldUserOptionPromptActivity.shouldLaunch(this)) {
      Intent intent = new Intent(this, SignInActivity.class);
      startActivityForResult(intent, ActivityRequestCodes.REQUEST_SIGN_IN_ACTIVITY);
      return true;
    }

    accountsProvider.setShowSignInActivityIfNotSignedIn(false);
    return false;
  }

  // TODO: need a more principled way of keeping the action bar current

  public void restoreActionBar() {
    if (titleToRestore != null) {
      getSupportActionBar().setTitle(titleToRestore);
    }
  }

  @Override
  public boolean onNavigationItemSelected(MenuItem menuItem) {
    if (menuItem == null) {
      return false;
    }
    if (menuItem.getItemId() == R.id.navigation_item_experiments) {
      if (currentAccount == null) {
        return false;
      }
      if (accountsProvider.isSignedIn() && !currentAccount.isSignedIn()) {
        // This can happen when the app is starting and the current account hasn't been completely
        // restored yet.
        return false;
      }
      FragmentManager fragmentManager = getSupportFragmentManager();
      FragmentTransaction transaction = fragmentManager.beginTransaction();
      int itemId = menuItem.getItemId();

      final String tag = String.valueOf(itemId);
      Fragment fragment =
          ExperimentListFragment.reuseOrCreateInstance(
              fragmentManager.findFragmentByTag(tag), currentAccount, shouldUsePanes());
      adjustActivityForSelectedItem(itemId);

      titleToRestore = getTitleToRestore(menuItem);
      transaction.replace(R.id.content_container, fragment, tag).commitAllowingStateLoss();
      if (menuItem.isCheckable()) {
        menuItem.setChecked(true);
      }
      drawerLayout.closeDrawers();
      restoreActionBar();
      selectedItemId = itemId;
    } else {
      drawerLayout.closeDrawers();
      // Launch intents
      Intent intent = null;
      int itemId = menuItem.getItemId();

      if (itemId == R.id.navigation_item_activities) {
        intent = new Intent(ACTION_VIEW, Uri.parse(getString(R.string.activities_url)));
      } else if (itemId == R.id.navigation_item_settings) {
        // We need currentAccount for the TYPE_SETTINGS version of the SettingsActivity.
        if (currentAccount == null) {
          return false;
        }
        intent =
            SettingsActivity.getLaunchIntent(
                this, currentAccount, menuItem.getTitle(), SettingsActivity.TYPE_SETTINGS);
      } else if (itemId == R.id.navigation_item_about) {
        // Don't use currentAccount for the TYPE_ABOUT version of the SettingsActivity.
        intent =
            SettingsActivity.getLaunchIntent(
                this, menuItem.getTitle(), SettingsActivity.TYPE_ABOUT);
      } else if (itemId == R.id.dev_testing_options) {
        // Don't use currentAccount for the TYPE_DEV_OPTIONS version of the SettingsActivity.
        intent =
            SettingsActivity.getLaunchIntent(
                this, menuItem.getTitle(), SettingsActivity.TYPE_DEV_OPTIONS);
      } else if (itemId == R.id.navigation_item_feedback) {
        // The two log statements below aren't guaranteed to succeed before the user has submitted
        // their feedback, but this is a significantly less complicated way to implement this, and
        // they are very, very likely to succeed before human input completes. The worst case is we
        // don't get the logging we want, but in testing, it's basically instant.
        logExperimentInfo(currentAccount);
        logDriveInfo(currentAccount);
        feedbackProvider.sendFeedback(
            new LoggingConsumer<Boolean>(TAG, "Send feedback") {
              @Override
              public void success(Boolean value) {
                if (!value) {
                  showFeedbackError();
                }
              }

              @Override
              public void fail(Exception e) {
                super.fail(e);
                showFeedbackError();
              }
            });
      }
      if (intent != null) {
        try {
          startActivity(intent);
        } catch (ActivityNotFoundException e) {
          if (intent.getAction().equals(ACTION_VIEW)) {
            showError(R.string.browser_not_found, Snackbar.LENGTH_LONG);
          } else {
            throw e;
          }
        }
      }
    }

    return false;
  }

  private CharSequence getTitleToRestore(MenuItem menuItem) {
    if (menuItem.getItemId() == R.id.navigation_item_experiments) {
      return getResources().getString(R.string.app_name);
    } else {
      return menuItem.getTitle();
    }
  }

  private boolean shouldUsePanes() {
    Intent intent = getIntent();
    if (intent == null) {
      return true;
    }
    return getIntent().getBooleanExtra(ARG_USE_PANES, true);
  }

  private void adjustActivityForSelectedItem(int itemId) {
    MenuItem menu = navigationView.getMenu().findItem(itemId);
    setTitle(getString(R.string.title_activity_main, menu.getTitle()));
  }

  @Override
  public boolean onCreateOptionsMenu(Menu menu) {
    // Only show items in the action bar relevant to this screen
    // if the drawer is not showing. Otherwise, let the drawer
    // decide what to show in the action bar.
    if (!drawerLayout.isDrawerOpen(GravityCompat.START)) {
      restoreActionBar();
    }
    return super.onCreateOptionsMenu(menu);
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    // Handle action bar item clicks here. The action bar will
    // automatically handle clicks on the Home/Up button, so long
    // as you specify a parent activity in AndroidManifest.xml.
    int id = item.getItemId();

    //noinspection SimplifiableIfStatement
    if (id == android.R.id.home) {
      if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
        drawerLayout.closeDrawer(GravityCompat.START);
      } else {
        drawerLayout.openDrawer(GravityCompat.START);
      }
    }
    return super.onOptionsItemSelected(item);
  }

  private void showFeedbackError() {
    showError(R.string.feedback_error_message, Snackbar.LENGTH_SHORT);
  }

  private void showError(int message, int length) {
    AccessibilityUtils.makeSnackbar(
            findViewById(R.id.drawer_layout),
            getResources().getString(message),
            length)
        .show();
  }

  @Override
  public void onRequestPermissionsResult(
      int requestCode, String[] permissions, int[] grantResults) {
    PermissionUtils.onRequestPermissionsResult(this, requestCode, permissions, grantResults);
  }

  /**
   * Launches the main activity to the selected navigation item.
   *
   * @param id One of the navigation_item constants.
   * @param usePanes should we use panes (the drawer) when observing?
   */
  @NonNull
  public static Intent launchIntent(Context context, int id, boolean usePanes) {
    Intent intent = new Intent(context, MainActivity.class);
    intent.putExtra(ARG_SELECTED_NAV_ITEM_ID, id);
    intent.putExtra(ARG_USE_PANES, usePanes);
    return intent;
  }

  @Override
  public void onActivityResult(int requestCode, int resultCode, Intent data) {
    switch (requestCode) {
      case ActivityRequestCodes.REQUEST_GET_STARTED_ACTIVITY:
      case ActivityRequestCodes.REQUEST_SIGN_IN_ACTIVITY:
        if (resultCode == RESULT_CANCELED) {
          finish();
        }
        return;
      default:
        break;
    }

    // TODO: Do this for all possible IDs in case others have activity results.
    Fragment fragment =
        getSupportFragmentManager()
            .findFragmentByTag(String.valueOf(R.id.navigation_item_experiments));
    if (fragment != null) {
      fragment.onActivityResult(requestCode, resultCode, data);
    }
  }

  private void onAccountSwitched(AppAccount appAccount) {
    AppSingleton.getInstance(this).setSyncServiceBusy(false);
    // Dispose of the recording status subscription for the old current account.
    currentAccountChanging.onHappened();
    currentAccount = appAccount;

    // Clean up old files from previous exports.
    ExportService.cleanOldFiles(this, currentAccount);

    // Subscribe to the recording status for the new current account.
    watchRecordingStatus();

    if (isAttemptingImport()) {
      attemptImport();
    }

    if (showRequiredScreensIfNeeded()) {
      return;
    }

    // Navigate to experiments list.
    int selectedNavItemId = R.id.navigation_item_experiments;
    MenuItem item = navigationView.getMenu().findItem(selectedNavItemId);
    navigationView.setCheckedItem(selectedNavItemId);
    onNavigationItemSelected(item);

    // Log the mode we are in: user has signed in or signed out.
    trackMode();
  }

  // Added for b/129700680
  // Will run in background thread.
  private void logExperimentInfo(AppAccount account) {
    Executor thread = Executors.newSingleThreadExecutor();
    thread.execute(
        new Runnable() {
          @Override
          public void run() {
            if (Log.isLoggable(TAG, Log.WARN)) {
              LocalSyncManager lsm =
                  AppSingleton.getInstance(getApplicationContext()).getLocalSyncManager(account);
              ExperimentLibraryManager elm =
                  AppSingleton.getInstance(getApplicationContext())
                      .getExperimentLibraryManager(account);

              int expCount = 0;
              int delCount = 0;
              int syncCount = 0;
              for (String id : elm.getKnownExperiments()) {
                expCount++;
                if (elm.isDeleted(id)) {
                  delCount++;
                }
                if (lsm.hasExperiment(id)) {
                  syncCount++;
                }
              }
              Log.w(TAG, "Experiments: " + expCount);
              Log.w(TAG, "Deleted: " + delCount);
              Log.w(TAG, "SyncManager: " + syncCount);
            }
          }
        });
  }

  // Will run in background thread.
  private void logDriveInfo(AppAccount account) {
    Executor thread = Executors.newSingleThreadExecutor();
    thread.execute(
        new Runnable() {
          @Override
          public void run() {
            if (Log.isLoggable(TAG, Log.WARN)) {
              if (account.isSignedIn()) {
                CloudSyncProvider syncProvider =
                    WhistlePunkApplication.getCloudSyncProvider(getApplicationContext());
                CloudSyncManager syncService = syncProvider.getServiceForAccount(account);
                syncService.logCloudInfo(TAG);
              } else {
                Log.w(TAG, "No Drive logs for signed out user");
              }
            }
          }
        });
  }
}
