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
import android.support.design.navigation.NavigationView;
import android.support.design.widget.Snackbar;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;
import androidx.core.view.GravityCompat;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.widget.Toolbar;
import android.view.Menu;
import android.view.MenuItem;
import android.view.Window;
import android.view.WindowManager;
import com.google.android.apps.forscience.whistlepunk.accounts.AccountsProvider;
import com.google.android.apps.forscience.whistlepunk.accounts.AppAccount;
import com.google.android.apps.forscience.whistlepunk.accounts.NonSignedInAccount;
import com.google.android.apps.forscience.whistlepunk.accounts.NotSignedInYetActivity;
import com.google.android.apps.forscience.whistlepunk.analytics.TrackerConstants;
import com.google.android.apps.forscience.whistlepunk.feedback.FeedbackProvider;
import com.google.android.apps.forscience.whistlepunk.filemetadata.Experiment;
import com.google.android.apps.forscience.whistlepunk.intro.AgeVerifier;
import com.google.android.apps.forscience.whistlepunk.project.ExperimentListFragment;
import com.google.android.apps.forscience.whistlepunk.review.RunReviewActivity;

/** The main activity. */
public class MainActivity extends ActivityWithNavigationView {
  private static final String TAG = "MainActivity";
  public static final String ARG_SELECTED_NAV_ITEM_ID = "selected_nav_item_id";
  // TODO(lizlooney): It looks like the ARG_USE_PANES value is always true. Investigate whether the
  // ARG_USE_PANES value is ever false and if not, remove the argument in MainActivity,
  // ExperimentListFragment, and ClaimExperimentsActivity.
  public static final String ARG_USE_PANES = "use_panes";
  protected static final int NO_SELECTED_ITEM = -1;

  /** Used to store the last screen title. For use in {@link #restoreActionBar()}. */
  private CharSequence titleToRestore;

  private AccountsProvider accountsProvider;
  private AppAccount currentAccount;
  private final RxEvent paused = new RxEvent();
  private FeedbackProvider feedbackProvider;
  private NavigationView navigationView;
  private MultiTouchDrawerLayout drawerLayout;
  private int selectedItemId = NO_SELECTED_ITEM;
  private boolean isRecording = false;

  /** Receives an event every time the activity pauses */
  RxEvent pause = new RxEvent();

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    WhistlePunkApplication.getPerfTrackerProvider(this).onActivityInit();
    accountsProvider = WhistlePunkApplication.getAppServices(this).getAccountsProvider();
    currentAccount = accountsProvider.getCurrentAccount();
    if (showRequiredScreensIfNeeded()) {
      return;
    }
    setContentView(R.layout.activity_main);
    accountsProvider.connectAccountSwitcher(this);

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
    navigationView = (NavigationView) findViewById(R.id.navigation);
    navigationView.setNavigationItemSelectedListener(this);

    // Only show dev testing options for (1) user-debug devices (2) debug APK builds
    if (DevOptionsFragment.shouldHideTestingOptions(this)) {
      navigationView.getMenu().removeItem(R.id.dev_testing_options);
    }

    feedbackProvider = WhistlePunkApplication.getAppServices(this).getFeedbackProvider();

    Bundle extras = getIntent().getExtras();
    int selectedNavItemId = R.id.navigation_item_experiments;

    int savedItemId = getSavedItemId(savedInstanceState);
    if (savedItemId != NO_SELECTED_ITEM) {
      selectedNavItemId = savedItemId;
    } else if (extras != null) {
      selectedNavItemId = extras.getInt(ARG_SELECTED_NAV_ITEM_ID, R.id.navigation_item_experiments);
    }
    MenuItem item = navigationView.getMenu().findItem(selectedNavItemId);
    if (item == null) {
      selectedNavItemId = R.id.navigation_item_experiments;
      item = navigationView.getMenu().findItem(selectedNavItemId);
    }
    navigationView.setCheckedItem(selectedNavItemId);
    onNavigationItemSelected(item);

    setVolumeControlStream(AudioManager.STREAM_MUSIC);

    // Clean up old files from previous exports.
    ExportService.cleanOldFiles(this, currentAccount);
  }

  private int getSavedItemId(Bundle savedInstanceState) {
    if (savedInstanceState == null) {
      return NO_SELECTED_ITEM;
    } else {
      return savedInstanceState.getInt(ARG_SELECTED_NAV_ITEM_ID, NO_SELECTED_ITEM);
    }
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

    if (showRequiredScreensIfNeeded()) {
      return;
    }
    accountsProvider
        .getObservableCurrentAccount()
        .takeUntil(pause.happens())
        .subscribe(this::onAccountSwitched);

    if (!isMultiWindowEnabled()) {
      updateRecorderControllerForResume();
    }
    // If we get to here, it's safe to log the mode we are in: user has signed in and/or
    // completed age verification.
    WhistlePunkApplication.getUsageTracker(this)
        .trackEvent(
            TrackerConstants.CATEGORY_APP,
            TrackerConstants.ACTION_SET_MODE,
            AgeVerifier.isOver13(AgeVerifier.getUserAge(this))
                ? TrackerConstants.LABEL_MODE_NONCHILD
                : TrackerConstants.LABEL_MODE_CHILD,
            0);
    if (isAttemptingImport()) {
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

      setIntent(null);
    }
  }

  public boolean isAttemptingImport() {
    return getIntent() != null
        && getIntent().getAction() != null
        && getIntent().getAction().equals(Intent.ACTION_VIEW);
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
    paused.onHappened();
    if (!isMultiWindowEnabled()) {
      updateRecorderControllerForPause();
    }
    super.onPause();
  }

  @Override
  protected void onStart() {
    super.onStart();
    if (isMultiWindowEnabled()) {
      updateRecorderControllerForResume();
    }
  }

  @Override
  protected void onStop() {
    accountsProvider.disconnectAccountSwitcher(this);
    if (isMultiWindowEnabled()) {
      updateRecorderControllerForPause();
    }
    super.onStop();
  }

  private boolean isMultiWindowEnabled() {
    return MultiWindowUtils.isMultiWindowEnabled(getApplicationContext());
  }

  private void updateRecorderControllerForResume() {
    AppSingleton singleton = AppSingleton.getInstance(this);
    RecorderController rc = singleton.getRecorderController(currentAccount);

    // TODO: extract and test
    rc.watchRecordingStatus()
        .takeUntil(pause.happens())
        .subscribe(
            status -> {
              isRecording = status.isRecording();
              // TODO: Add experimentId to RecordingStatus
              if (isRecording) {
                rememberAttemptingImport();
                singleton
                    .getDataController(currentAccount)
                    .getLastUsedUnarchivedExperiment(
                        new LoggingConsumer<Experiment>(TAG, "getting last used experiment") {
                          @Override
                          public void success(Experiment experiment) {
                            startActivity(
                                WhistlePunkApplication.getLaunchIntentForPanesActivity(
                                    MainActivity.this,
                                    currentAccount,
                                    experiment.getExperimentId(),
                                    false /* claimExperimentsMode */));
                          }
                        });
              }
            });
  }

  private void rememberAttemptingImport() {
    AppSingleton.getInstance(this).setMostRecentOpenWasImport(isAttemptingImport());
  }

  private void updateRecorderControllerForPause() {
    pause.onHappened();
  }

  /**
   * If we haven't seen all the required screens, opens the next required activity, and finishes
   * this activity
   *
   * @return true iff the activity has been finished
   */
  private boolean showRequiredScreensIfNeeded() {
    if (accountsProvider.requireSignedInAccount() && !accountsProvider.isSignedIn()) {
      // Before letting the user sign in, get the DataController for the NonSignedInAccount and
      // call DataController.getLastUsedUnarchivedExperiment, which will upgrade the database, if
      // necessary.
      NonSignedInAccount nonSignedInAccount = NonSignedInAccount.getInstance(this);
      AppSingleton.getInstance(this)
          .getDataController(nonSignedInAccount)
          .getLastUsedUnarchivedExperiment(
              new LoggingConsumer<Experiment>(
                  TAG, "getting last used experiment to force database upgrade") {
                @Override
                public void success(Experiment experiment) {
                  // Let the user sign in.
                  Intent intent = new Intent(MainActivity.this, NotSignedInYetActivity.class);
                  startActivity(intent);
                  finish();
                }
              });
      return true;
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
      FragmentManager fragmentManager = getSupportFragmentManager();
      FragmentTransaction transaction = fragmentManager.beginTransaction();
      int itemId = menuItem.getItemId();

      final String tag = String.valueOf(itemId);
      Fragment fragment =
          ExperimentListFragment.reuseOrCreateInstance(
              fragmentManager.findFragmentByTag(tag), currentAccount, shouldUsePanes());
      adjustActivityForSelectedItem(itemId);

      titleToRestore = getTitleToRestore(menuItem);
      transaction.replace(R.id.content_container, fragment, tag).commit();
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
                this, menuItem.getTitle(), SettingsActivity.TYPE_SETTINGS);
      } else if (itemId == R.id.navigation_item_about) {
        intent =
            SettingsActivity.getLaunchIntent(
                this, menuItem.getTitle(), SettingsActivity.TYPE_ABOUT);
      } else if (itemId == R.id.dev_testing_options) {
        intent =
            SettingsActivity.getLaunchIntent(
                this, menuItem.getTitle(), SettingsActivity.TYPE_DEV_OPTIONS);
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

  // TODO: this is unused now.  Does any of this need to go into PanesActivity?
  public RecordFragment.UICallbacks getRecordFragmentCallbacks() {
    return new RecordFragment.UICallbacks() {
      @Override
      public void onRecordingRequested(String experimentName, boolean userInitiated) {
        ActionBar actionBar = getSupportActionBar();
        supportInvalidateOptionsMenu();
        int toolbarColorResource = R.color.recording_toolbar_color;
        int statusBarColorResource = R.color.recording_status_bar_color;
        actionBar.setSubtitle(R.string.recording_title_label);
        updateToolbarColors(toolbarColorResource, statusBarColorResource);
      }

      @Override
      public void onRecordingStopped() {
        ActionBar actionBar = getSupportActionBar();
        supportInvalidateOptionsMenu();
        int toolbarColorResource = R.color.color_primary;
        int statusBarColorResource = R.color.color_primary_dark;
        updateToolbarColors(toolbarColorResource, statusBarColorResource);
        actionBar.setSubtitle(null);
      }

      @Override
      public void onRecordingSaved(String runId, Experiment experiment) {
        boolean fromRecord = true;
        boolean createTask = true;
        boolean claimExperimentsMode = false;
        RunReviewActivity.launch(
            MainActivity.this,
            currentAccount,
            runId,
            experiment.getExperimentId(),
            0,
            fromRecord,
            createTask,
            claimExperimentsMode,
            null);
      }

      private void updateToolbarColors(int toolbarColorResource, int statusBarColorResource) {
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);

        // Update the toolbar and status bar colors.
        toolbar.setBackgroundResource(toolbarColorResource);
        if (AndroidVersionUtils.isApiLevelAtLeastLollipop()) {
          Window window = getWindow();
          if (statusBarColorResource == R.color.color_primary_dark) {
            window.addFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
          } else {
            // For any color that is not the default, need to clear this flag so that
            // we can draw the right color.
            window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
          }
          window.setStatusBarColor(getResources().getColor(statusBarColorResource));
        }
      }
    };
  }

  private void onAccountSwitched(AppAccount appAccount) {
    if (currentAccount.equals(appAccount)) {
      return;
    }
    currentAccount = appAccount;

    // Clean up old files from previous exports.
    ExportService.cleanOldFiles(this, currentAccount);

    // Navigate to experiments list.
    int selectedNavItemId = R.id.navigation_item_experiments;
    MenuItem item = navigationView.getMenu().findItem(selectedNavItemId);
    navigationView.setCheckedItem(selectedNavItemId);
    onNavigationItemSelected(item);
  }
}
