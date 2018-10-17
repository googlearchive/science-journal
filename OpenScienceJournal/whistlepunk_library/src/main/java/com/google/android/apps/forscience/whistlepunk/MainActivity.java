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

import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import android.support.design.navigation.NavigationView;
import android.support.design.widget.Snackbar;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;
import androidx.core.view.GravityCompat;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.widget.Toolbar;
import android.util.Pair;
import android.view.Menu;
import android.view.MenuItem;
import com.google.android.apps.forscience.whistlepunk.accounts.AccountsProvider;
import com.google.android.apps.forscience.whistlepunk.accounts.AccountsUtils;
import com.google.android.apps.forscience.whistlepunk.accounts.AppAccount;
import com.google.android.apps.forscience.whistlepunk.accounts.GetStartedActivity;
import com.google.android.apps.forscience.whistlepunk.accounts.OldUserOptionPromptActivity;
import com.google.android.apps.forscience.whistlepunk.accounts.SignInActivity;
import com.google.android.apps.forscience.whistlepunk.analytics.TrackerConstants;
import com.google.android.apps.forscience.whistlepunk.feedback.FeedbackProvider;
import com.google.android.apps.forscience.whistlepunk.filemetadata.Experiment;
import com.google.android.apps.forscience.whistlepunk.intro.AgeVerifier;
import com.google.android.apps.forscience.whistlepunk.project.ExperimentListFragment;
import io.reactivex.Observable;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.CompositeDisposable;

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
  private boolean requireSignedInAccount;

  private FeedbackProvider feedbackProvider;
  private NavigationView navigationView;
  private MultiTouchDrawerLayout drawerLayout;
  private int selectedItemId = NO_SELECTED_ITEM;
  private boolean isRecording = false;

  /** Receives an event every time the activity pauses */
  private final RxEvent pause = new RxEvent();
  /** Receives an event every time the current account changes */
  private final RxEvent currentAccountChanging = new RxEvent();

  private final CompositeDisposable disposeWhenPaused = new CompositeDisposable();
  private final CompositeDisposable disposeWhenDestroyed = new CompositeDisposable();

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    AgeVerifier.forgetAge(this);

    savedItemId =
        (savedInstanceState == null)
            ? NO_SELECTED_ITEM
            : savedInstanceState.getInt(ARG_SELECTED_NAV_ITEM_ID, NO_SELECTED_ITEM);

    WhistlePunkApplication.getPerfTrackerProvider(this).onActivityInit();

    accountsProvider = WhistlePunkApplication.getAppServices(this).getAccountsProvider();
    accountsProvider.registerAccountBasedPreferenceKey(
        ExperimentListFragment.KEY_DEFAULT_EXPERIMENT_CREATED, false);
    accountsProvider.registerAccountBasedPreferenceKey(
        AccountsProvider.KEY_OLD_PREFERENCES_COPIED, false);

    setContentView(R.layout.activity_main);

    disposeWhenDestroyed.add(
        accountsProvider
            .installAccountSwitcher(this)
            .subscribe(
                () -> {
                  navigationView = (NavigationView) findViewById(R.id.navigation);
                  navigationView.setNavigationItemSelectedListener(this);

                  // Only show dev testing options when requested.
                  if (!DevOptionsFragment.shouldShowTestingOptions()) {
                    navigationView.getMenu().removeItem(R.id.dev_testing_options);
                  }
                }));

    Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
    setSupportActionBar(toolbar);

    final ActionBar actionBar = getSupportActionBar();
    if (actionBar != null) {
      actionBar.setDisplayHomeAsUpEnabled(true);
      actionBar.setHomeAsUpIndicator(R.drawable.ic_menu_white_24dp);
      actionBar.setDisplayShowTitleEnabled(true);
    }

    drawerLayout = (MultiTouchDrawerLayout) findViewById(R.id.drawer_layout);
    drawerLayout.setStatusBarBackgroundColor(getResources().getColor(R.color.color_primary_dark));

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

    if (isAttemptingImport()) {
      attemptImport();
    }

    showRequiredScreensIfNeeded(
        () -> {
          // Navigate to the desired fragment, based on saved state or intent extras, or (by
          // default) the experiments list.
          Intent intent = getIntent();
          Bundle extras = null;
          if (intent != null) {
            extras = intent.getExtras();
          }
          int selectedNavItemId;
          if (savedItemId != NO_SELECTED_ITEM) {
            selectedNavItemId = savedItemId;
          } else if (extras != null) {
            selectedNavItemId =
                extras.getInt(ARG_SELECTED_NAV_ITEM_ID, R.id.navigation_item_experiments);
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
          // If we get to here, it's safe to log the mode we are in: user has signed in
          // and/or completed age verification.
          trackMode();
        });
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
        && getIntent().getAction().equals(Intent.ACTION_VIEW);
  }

  private void trackMode() {
    String labelMode =
        accountsProvider.isSignedIn()
            ? TrackerConstants.LABEL_MODE_SIGNED_IN
            : (AgeVerifier.isUserOver13(this)
                ? TrackerConstants.LABEL_MODE_SIGNED_OUT_NONCHILD
                : TrackerConstants.LABEL_MODE_SIGNED_OUT_CHILD);
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
    disposeWhenPaused.dispose();
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
    disposeWhenDestroyed.dispose();
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
                            if (appAccount.equals(currentAccount)) {
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

  private void showRequiredScreensIfNeeded(Runnable runIfNoRequiredScreens) {
    disposeWhenPaused.add(
        Observable.combineLatest(
                accountsProvider.supportSignedInAccount().toObservable(),
                accountsProvider.requireSignedInAccount().toObservable(),
                Pair::create)
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(
                pair -> {
                  boolean supportSignedInAccount = pair.first;
                  boolean requireSignedInAccount = pair.second;
                  this.requireSignedInAccount = requireSignedInAccount;
                  if (showRequiredScreensIfNeeded(supportSignedInAccount, requireSignedInAccount)) {
                    return;
                  }

                  runIfNoRequiredScreens.run();
                }));
  }

  /**
   * If we haven't seen all the required screens, opens the next required activity, and finishes
   * this activity
   *
   * @return true iff the activity has been finished
   */
  private boolean showRequiredScreensIfNeeded(
      boolean supportSignedInAccount, boolean requireSignedInAccount) {
    if (supportSignedInAccount) {
      if (GetStartedActivity.maybeLaunch(this)) {
        finish();
        return true;
      }
      if (accountsProvider.getShowSignInActivityIfNotSignedIn() || requireSignedInAccount) {
        accountsProvider.setShowSignInActivityIfNotSignedIn(false);
        if (!accountsProvider.isSignedIn()) {
          SignInActivity.launch(this);
          finish();
          return true;
        }
      }
      if (accountsProvider.isSignedIn() && AccountsUtils.getUnclaimedExperimentCount(this) >= 1) {
        if (OldUserOptionPromptActivity.maybeLaunch(this)) {
          finish();
          return true;
        }
      }
      // Once we get here (whether the user signed in or chose to continue without signing in), we
      // will never show the OldUserOptionPromptActivity on this device.
      OldUserOptionPromptActivity.setShouldLaunch(this, false);
    }

    if (AgeVerifier.shouldShowUserAge(this)) {
      rememberAttemptingImport();
      Intent intent = new Intent(this, AgeVerifier.class);
      startActivity(intent);
      finish();
      return true;
    }
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
        intent = new Intent(Intent.ACTION_VIEW, Uri.parse(getString(R.string.activities_url)));
      } else if (itemId == R.id.navigation_item_settings) {
        intent =
            SettingsActivity.getLaunchIntent(
                this, currentAccount, menuItem.getTitle(), SettingsActivity.TYPE_SETTINGS);
      } else if (itemId == R.id.navigation_item_about) {
        intent =
            SettingsActivity.getLaunchIntent(
                this, currentAccount, menuItem.getTitle(), SettingsActivity.TYPE_ABOUT);
      } else if (itemId == R.id.dev_testing_options) {
        intent =
            SettingsActivity.getLaunchIntent(
                this, currentAccount, menuItem.getTitle(), SettingsActivity.TYPE_DEV_OPTIONS);
      } else if (itemId == R.id.navigation_item_feedback) {
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
        startActivity(intent);
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
    AccessibilityUtils.makeSnackbar(
            findViewById(R.id.drawer_layout),
            getResources().getString(R.string.feedback_error_message),
            Snackbar.LENGTH_SHORT)
        .show();
  }

  @Override
  public void onRequestPermissionsResult(
      int requestCode, String permissions[], int[] grantResults) {
    // TODO: is this ever used?
    PermissionUtils.onRequestPermissionsResult(this, requestCode, permissions, grantResults);
    return;
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
    // TODO: Do this for all possible IDs in case others have activity results.
    Fragment fragment =
        getSupportFragmentManager()
            .findFragmentByTag(String.valueOf(R.id.navigation_item_experiments));
    if (fragment != null) {
      fragment.onActivityResult(requestCode, resultCode, data);
    }
  }

  private void onAccountSwitched(AppAccount appAccount) {
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

    showRequiredScreensIfNeeded(
        () -> {
          // Navigate to experiments list.
          int selectedNavItemId = R.id.navigation_item_experiments;
          MenuItem item = navigationView.getMenu().findItem(selectedNavItemId);
          navigationView.setCheckedItem(selectedNavItemId);
          onNavigationItemSelected(item);

          // Log the mode we are in: user has signed in or signed out.
          trackMode();
        });
  }
}
