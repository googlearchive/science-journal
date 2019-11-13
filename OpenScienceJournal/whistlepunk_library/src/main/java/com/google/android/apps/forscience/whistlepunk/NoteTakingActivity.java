/*
 *  Copyright 2019 Google Inc. All Rights Reserved.
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

import static com.google.android.apps.forscience.whistlepunk.PictureUtils.REQUEST_TAKE_PHOTO;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.content.res.Resources.Theme;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;
import androidx.appcompat.app.AppCompatActivity;
import android.text.TextUtils;
import android.util.Log;
import android.view.MenuItem;
import com.google.android.apps.forscience.whistlepunk.accounts.AppAccount;
import com.google.android.apps.forscience.whistlepunk.actionarea.ActionAreaItem;
import com.google.android.apps.forscience.whistlepunk.actionarea.ActionAreaView.ActionAreaListener;
import com.google.android.apps.forscience.whistlepunk.actionarea.TitleProvider;
import com.google.android.apps.forscience.whistlepunk.filemetadata.Experiment;
import com.google.android.apps.forscience.whistlepunk.filemetadata.Label;
import com.google.android.apps.forscience.whistlepunk.metadata.GoosciLabel;
import com.google.android.apps.forscience.whistlepunk.metadata.GoosciPictureLabelValue.PictureLabelValue;
import com.google.android.apps.forscience.whistlepunk.project.experiment.ExperimentDetailsWithActionAreaFragment;
import com.google.android.material.snackbar.Snackbar;
import com.tbruyelle.rxpermissions2.RxPermissions;
import io.reactivex.Single;
import java.util.UUID;

/** Abstract activity that handles taking notes and displaying a primary fragment. */
public abstract class NoteTakingActivity extends AppCompatActivity
    implements TextNoteFragment.ListenerProvider,
        GalleryNoteFragment.ListenerProvider,
        ExperimentDetailsWithActionAreaFragment.ListenerProvider,
        ActionAreaListener {
  private static final String TAG = "NoteTakingActivity";
  public static final String EXTRA_ACCOUNT_KEY = "accountKey";
  public static final String EXTRA_EXPERIMENT_ID = "experimentId";
  public static final String EXTRA_CLAIM_EXPERIMENTS_MODE = "claimExperimentsMode";

  protected static final String MORE_OBSERVATIONS_TAG = "moreObservations";
  protected static final String NOTE_TAG = "note";
  protected static final String SENSOR_TAG = "sensor";
  protected static final String GALLERY_TAG = "gallery";
  protected static final String DEFAULT_FRAGMENT_TAG = "defaultFragment";
  protected static final String DEFAULT_ADD_MORE_OBSERVATIONS_TAG = "defaultAddMoreObservations";

  private static final String EXTRA_PICTURE_UUID = "pictureUUID";
  private static final String EXTRA_PICTURE_PATH = "picturePath";

  private static final String EXTRA_ACTIVE_TOOL_FRAGMENT_TAG = "activeToolFragmentTag";

  private RxPermissions permissions;
  private RxEvent paused = new RxEvent();
  protected AppAccount appAccount;
  private String pictureUUID;
  private String pictureRelativePath;
  protected String activeToolFragmentTag;
  private String experimentId;
  private boolean isTwoPane;
  protected Single<Experiment> exp;

  @NonNull
  public static Intent launchIntent(
      Context context, AppAccount appAccount, String experimentId, boolean claimExperimentsMode) {
    Intent intent = new Intent(context, NoteTakingActivity.class);
    intent.putExtra(EXTRA_ACCOUNT_KEY, appAccount.getAccountKey());
    intent.putExtra(EXTRA_EXPERIMENT_ID, experimentId);
    intent.putExtra(EXTRA_CLAIM_EXPERIMENTS_MODE, claimExperimentsMode);
    return intent;
  }

  private Fragment defaultFragment;

  private RxEvent destroyed = new RxEvent();

  @Override
  protected void onCreate(@Nullable Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    permissions = new RxPermissions(this);
    setContentView(R.layout.activity_experiment_layout);

    Resources resources = getResources();
    boolean isTablet = resources.getBoolean(R.bool.is_tablet);
    if (!isTablet) {
      setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
    }
    boolean isLandscape =
        resources.getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;
    isTwoPane = isTablet && isLandscape;

    appAccount = WhistlePunkApplication.getAccount(this, getIntent(), EXTRA_ACCOUNT_KEY);
    experimentId = getIntent().getStringExtra(EXTRA_EXPERIMENT_ID);
    exp = whenSelectedExperiment(experimentId, getDataController());

    AppSingleton.getInstance(this)
        .whenLabelsAdded(appAccount)
        .takeUntil(destroyed.happens())
        .subscribe(event -> onLabelAdded(event.getTrialId(), event.getLabel()));
  }

  @VisibleForTesting
  public static Single<Experiment> whenSelectedExperiment(
      String experimentId, DataController dataController) {
    if (Log.isLoggable(TAG, Log.INFO)) {
      Log.i(TAG, "Launching specified experiment id: " + experimentId);
    }
    return RxDataController.getExperimentById(dataController, experimentId);
  }

  public void onArchivedStateChanged() {
    findViewById(R.id.experiment_pane).requestLayout();
  }

  public void setUpFragments() {
    showDefaultFragments();
    if (activeToolFragmentTag != null) {
      showFragmentByTagInToolPane(activeToolFragmentTag);
    }
    updateTitle();
  }

  protected void showDefaultFragments() {
    FragmentManager fragmentManager = getSupportFragmentManager();
    if (isTwoPane) {
      showFragmentByTagInToolPane(getDefaultToolFragmentTag());
    } else {
      hideAllFragmentsInToolPane();
    }
    if (defaultFragment == null) {
      defaultFragment = getDefaultFragment();
    }
    if (defaultFragment != null) {
      if (fragmentManager.findFragmentByTag(DEFAULT_FRAGMENT_TAG) != null) {
        fragmentManager.beginTransaction().show(defaultFragment).commit();
      } else {
        fragmentManager
            .beginTransaction()
            .add(R.id.experiment_pane, defaultFragment, DEFAULT_FRAGMENT_TAG)
            .commit();
      }
    }
    updateTitle();
  }

  protected abstract Fragment getDefaultFragment();

  protected abstract String getDefaultToolFragmentTag();

  public void closeToolFragment() {
    activeToolFragmentTag = null;
    showDefaultFragments();
  }

  public void openSensorFragment() {
    openToolFragment(SENSOR_TAG);
  }

  protected void openToolFragment(String tag) {
    showFragmentByTagInToolPane(tag);
    activeToolFragmentTag = tag;
    updateTitle();
  }

  public boolean isTwoPane() {
    return isTwoPane;
  }

  private String getDefaultFragmentTitle() {
    if (defaultFragment != null) {
      return ((TitleProvider) defaultFragment).getTitle();
    }
    return null;
  }

  public void updateTitle() {
    setTitle(getDefaultFragmentTitle());
  }

  public void updateTitleByToolFragment(String title) {
    if (activeToolFragmentTag != null && !isTwoPane) {
      setTitle(title);
    }
  }

  public void updateTitleByDefaultFragment(String title) {
    setTitle(title);
  }

  @Override
  protected void onDestroy() {
    destroyed.onHappened();
    super.onDestroy();
  }

  @Override
  protected void onResume() {
    super.onResume();
    AppSingleton appSingleton = AppSingleton.getInstance(this);
    appSingleton.setResumedActivity(this);
    paused.happensNext().subscribe(() -> appSingleton.setNoLongerResumedActivity(this));
    setUpFragments();
  }

  @Override
  protected void onPause() {
    paused.happens();
    super.onPause();
  }

  @Override
  public void onBackPressed() {
    if (activeToolFragmentTag == null
        || activeToolFragmentTag.equals(DEFAULT_ADD_MORE_OBSERVATIONS_TAG)) {
      if (handleDefaultFragmentOnBackPressed()) {
        return;
      }
    } else {
      closeToolFragment();
      return;
    }

    super.onBackPressed();
  }

  protected abstract boolean handleDefaultFragmentOnBackPressed();

  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    int itemId = item.getItemId();
    if (itemId == android.R.id.home) {
      onBackPressed();
      return true;
    }
    return false;
  }

  @Override
  public GalleryNoteFragment.Listener getGalleryListener() {
    return new GalleryNoteFragment.Listener() {
      @Override
      public String getExperimentId() {
        return experimentId;
      }

      @Override
      public void onPictureLabelTaken(Label label) {
        addNewLabel(label);
        closeToolFragment();
      }

      @Override
      public RxPermissions getPermissions() {
        return permissions;
      }
    };
  }

  @Override
  public TextNoteFragment.TextLabelFragmentListener getTextLabelFragmentListener() {
    return result -> {
      addNewLabel(result);
      closeToolFragment();
    };
  }

  private Clock getClock(Context context) {
    return AppSingleton.getInstance(context).getSensorEnvironment().getDefaultClock();
  }

  protected long getTimestamp(Context context) {
    return getClock(context).getNow();
  }

  // Lint doesn't like "subscribe" without doing anything with the return value. But that's not how
  // that works.
  @SuppressLint("CheckResult")
  protected void addNewLabel(Label label) {
    // Reload the current experiment in case the ActiveExperiment Object has changed beneath us.
    RxDataController.getExperimentById(getDataController(), experimentId)
        .subscribe(
            e -> {
              // if it is recording, add it to the recorded trial instead!
              String trialId = getTrialIdForLabel();
              if (TextUtils.isEmpty(trialId)) {
                e.addLabel(e, label);
              } else {
                e.getTrial(trialId).addLabel(e, label);
              }
              RxDataController.updateExperiment(getDataController(), e, true)
                  .subscribe(() -> onLabelAdded(trialId, label), error -> onAddNewLabelFailed());
            },
            error -> {
              if (Log.isLoggable(TAG, Log.ERROR)) {
                Log.e(TAG, "addNewLabel failed", error);
              }
              onAddNewLabelFailed();
            });
  }

  protected void onAddNewLabelFailed() {
    AccessibilityUtils.makeSnackbar(
            findViewById(R.id.tool_pane),
            getResources().getString(R.string.label_failed_save),
            Snackbar.LENGTH_LONG)
        .show();
  }

  protected abstract String getTrialIdForLabel();

  protected abstract void onLabelAdded(String trialId, Label label);

  protected DataController getDataController() {
    return AppSingleton.getInstance(this).getDataController(appAccount);
  }

  @Override
  public void onRequestPermissionsResult(
      int requestCode, String[] permissions, int[] grantResults) {
    PermissionUtils.onRequestPermissionsResult(this, requestCode, permissions, grantResults);
  }

  @Override
  public ExperimentDetailsWithActionAreaFragment.Listener getExperimentDetailsFragmentListener() {
    return changed -> onArchivedStateChanged();
  }

  @Override
  public void onClick(ActionAreaItem item) {
    if (item.equals(ActionAreaItem.NOTE)) {
      openToolFragment(NOTE_TAG);
    } else if (item.equals(ActionAreaItem.SENSOR)) {
      openToolFragment(SENSOR_TAG);
    } else if (item.equals(ActionAreaItem.CAMERA)) {
      takePicture();
    } else if (item.equals(ActionAreaItem.GALLERY)) {
      openToolFragment(GALLERY_TAG);
    } else if (item.equals(ActionAreaItem.MORE)) {
      openToolFragment(MORE_OBSERVATIONS_TAG);
    }
  }

  private void hideAllFragmentsInToolPane() {
    FragmentManager fragmentManager = getSupportFragmentManager();
    FragmentTransaction ft = fragmentManager.beginTransaction();
    for (Fragment fragment : fragmentManager.getFragments()) {
      if (!fragment.equals(defaultFragment)) {
        ft.hide(fragment);
      }
    }
    ft.commit();
  }

  private void showFragmentByTagInToolPane(String tag) {
    hideAllFragmentsInToolPane();
    FragmentManager fragmentManager = getSupportFragmentManager();
    Fragment fragment = fragmentManager.findFragmentByTag(tag);
    if (fragment != null) {
      fragmentManager.beginTransaction().show(fragment).commit();
    } else {
      fragmentManager
          .beginTransaction()
          .add(R.id.tool_pane, createFragmentByTag(tag), tag)
          .commit();
    }
  }

  private Fragment createFragmentByTag(String tag) {
    switch (tag) {
      case NOTE_TAG:
        return new TextNoteFragment();
      case SENSOR_TAG:
        return SensorFragment.newInstance(appAccount, experimentId);
      case GALLERY_TAG:
        return GalleryNoteFragment.newInstance(appAccount);
      case MORE_OBSERVATIONS_TAG:
        return MoreObservationsFragment.newInstance();
      case DEFAULT_ADD_MORE_OBSERVATIONS_TAG:
        return newInstanceAddMoreObservationNotes();
      default:
        throw new IllegalArgumentException("Invalid fragment tag: " + tag);
    }
  }

  protected abstract Fragment newInstanceAddMoreObservationNotes();

  @Override
  protected void onSaveInstanceState(Bundle outState) {
    super.onSaveInstanceState(outState);
    outState.putString(EXTRA_PICTURE_UUID, pictureUUID);
    outState.putString(EXTRA_PICTURE_PATH, pictureRelativePath);

    outState.putString(EXTRA_ACTIVE_TOOL_FRAGMENT_TAG, activeToolFragmentTag);
  }

  @Override
  protected void onRestoreInstanceState(Bundle savedInstanceState) {
    super.onRestoreInstanceState(savedInstanceState);
    pictureUUID = savedInstanceState.getString(EXTRA_PICTURE_UUID);
    pictureRelativePath = savedInstanceState.getString(EXTRA_PICTURE_PATH);

    activeToolFragmentTag = savedInstanceState.getString(EXTRA_ACTIVE_TOOL_FRAGMENT_TAG);
  }

  @SuppressLint("CheckResult")
  private void takePicture() {
    if (permissions != null) {
      permissions
          .request(Manifest.permission.CAMERA)
          .subscribe(
              granted -> {
                if (granted) {
                  pictureUUID = UUID.randomUUID().toString();
                  // After a user takes a picture, onActivityResult will be called.
                  pictureRelativePath =
                      PictureUtils.capturePictureLabel(this, appAccount, experimentId, pictureUUID);
                } else {
                  AccessibilityUtils.makeSnackbar(
                          findViewById(R.id.tool_pane),
                          getResources().getString(R.string.input_camera_permission_denied),
                          Snackbar.LENGTH_LONG)
                      .show();
                }
              });
    }
  }

  @Override
  protected void onActivityResult(int requestCode, int resultCode, Intent data) {
    super.onActivityResult(requestCode, resultCode, data);
    if (requestCode == REQUEST_TAKE_PHOTO && resultCode == RESULT_OK) {
      if (pictureUUID != null && pictureRelativePath != null) {
        PictureLabelValue labelValue =
            PictureLabelValue.newBuilder().setFilePath(pictureRelativePath).build();
        Label label =
            Label.fromUuidAndValue(
                getTimestamp(this), pictureUUID, GoosciLabel.Label.ValueType.PICTURE, labelValue);
        addNewLabel(label);
      }
    }
  }

  public abstract Theme getActivityTheme();

  public abstract ActionAreaItem[] getActionAreaItems();
}
