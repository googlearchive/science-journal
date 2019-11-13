/*
 *  Copyright 2017 Google Inc. All Rights Reserved.
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

package com.google.android.apps.forscience.whistlepunk.filemetadata;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import androidx.annotation.VisibleForTesting;
import android.text.TextUtils;
import android.util.Log;
import com.google.android.apps.forscience.whistlepunk.AppSingleton;
import com.google.android.apps.forscience.whistlepunk.WhistlePunkApplication;
import com.google.android.apps.forscience.whistlepunk.accounts.AppAccount;
import com.google.android.apps.forscience.whistlepunk.analytics.UsageTracker;
import com.google.android.apps.forscience.whistlepunk.data.GoosciGadgetInfo;
import com.google.android.apps.forscience.whistlepunk.metadata.GoosciExperiment;
import com.google.android.apps.forscience.whistlepunk.metadata.Version;
import java.io.File;
import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * This reads and writes experiments to disk. It caches the last used experiment to avoid extra file
 * operations. This class should be constructed and used from a background thread because it does
 * file operations.
 */
class ExperimentCache {
  private static final String TAG = "ExperimentCache";

  // The current version number we expect from experiments.
  // See upgradeExperimentVersionIfNeeded for the meaning of version numbers.
  protected static final int VERSION = 1;

  // The current minor version number we expect from experiments.
  // See upgradeExperimentVersionIfNeeded for the meaning of version numbers.
  protected static final int MINOR_VERSION = 2;

  // The current platform version number for experiments we write.
  // This is implementation-specific; it _shouldn't_ affect future readers of the data, but it
  // will allow us to detect files written by buggy versions if needed.
  //
  // Increment this each time the file-writing logic changes.
  protected static final int PLATFORM_VERSION = WhistlePunkApplication.getVersionCode();

  // Write the experiment file no more than once per every WRITE_DELAY_MS.
  private static final long WRITE_DELAY_MS = 1000;

  public interface FailureListener {
    // TODO: What's helpful to pass back here? Maybe info about the type of error?
    // When writing an experiment failed
    void onWriteFailed(Experiment experimentToWrite);

    // When reading an experiment failed
    void onReadFailed(ExperimentOverviewPojo localExperimentOverview);

    // When a newer version is found and we cannot parse it
    void onNewerVersionDetected(ExperimentOverviewPojo experimentOverview);
  }

  private final FailureListener failureListener;
  private final Context context;
  private final AppAccount appAccount;
  private final LiteProtoFileHelper<GoosciExperiment.Experiment> experimentProtoFileHelper;
  private final LocalSyncManager localSyncManager;
  private final ExperimentLibraryManager experimentLibraryManager;
  private final boolean enableAutoWrite;

  private final Handler handler;
  private final ExecutorService backgroundWriteThread;
  private final Runnable writeRunnable;

  private final Object activeExperimentLock = new Object();
  private Experiment activeExperiment;
  private boolean activeExperimentNeedsWrite;

  public ExperimentCache(Context context, AppAccount appAccount, FailureListener failureListener) {
    this(context, appAccount, failureListener, true);
  }

  @VisibleForTesting
  ExperimentCache(
      Context context,
      AppAccount appAccount,
      FailureListener failureListener,
      boolean enableAutoWrite) {
    this(
        context,
        appAccount,
        failureListener,
        enableAutoWrite,
        AppSingleton.getInstance(context).getExperimentLibraryManager(appAccount),
        AppSingleton.getInstance(context).getLocalSyncManager(appAccount));
  }

  @VisibleForTesting
  ExperimentCache(
      Context context,
      AppAccount appAccount,
      FailureListener failureListener,
      boolean enableAutoWrite,
      ExperimentLibraryManager elm,
      LocalSyncManager lsm) {
    this.context = context;
    this.appAccount = appAccount;
    this.failureListener = failureListener;
    experimentProtoFileHelper = new LiteProtoFileHelper<>();
    if (Looper.myLooper() == null) {
      Looper.prepare();
    }
    handler = new Handler();
    backgroundWriteThread = Executors.newSingleThreadExecutor();
    writeRunnable =
        () -> {
          synchronized (activeExperimentLock) {
            if (activeExperimentNeedsWrite) {
              final Experiment experimentToWrite = activeExperiment;
              if (experimentToWrite != null) {
                backgroundWriteThread.execute(
                    () -> {
                      writeExperimentFile(experimentToWrite);
                    });
              }
            }
          }
        };
    this.enableAutoWrite = enableAutoWrite;

    localSyncManager = lsm;
    experimentLibraryManager = elm;
  }

  @VisibleForTesting
  Experiment getActiveExperimentForTests() {
    synchronized (activeExperimentLock) {
      return activeExperiment;
    }
  }

  /**
   * Creates file space for a new experiment, and gets it ready for a save.
   *
   * @return whether space was created successfully.
   */
  boolean createNewExperiment(Experiment experiment) {
    if (!prepareForNewExperiment(experiment.getExperimentOverview().getExperimentId())) {
      failureListener.onWriteFailed(experiment);
      return false;
    }
    setExistingActiveExperiment(experiment);
    return true;
  }

  /** Updates the given experiment. */
  void updateExperiment(Experiment experiment, boolean setDirty) {
    synchronized (activeExperimentLock) {
      if (isDifferentFromActive(experiment.getExperimentOverview())) {
        immediateWriteIfActiveChanging(experiment.getExperimentOverview());
      }
      experimentLibraryManager.setModified(
          experiment.getExperimentId(), experiment.getLastUsedTime());
      localSyncManager.setDirty(experiment.getExperimentId(), setDirty);
      activeExperiment = experiment;
      startWriteTimer();
    }
  }

  /**
   * Updates the experiment overview of the active experiment if the active experiment has the same
   * ID. This allows us to keep the experimentOverview fresh without doing extra writes to disk. If
   * the active experiment is not the same ID as the experiment overview to update, no action needs
   * to be taken.
   *
   * @param experimentOverview the updated experimentOverview to set on the cached experiment if
   *     they have the same ID.
   */
  void onExperimentOverviewUpdated(ExperimentOverviewPojo experimentOverview) {
    synchronized (activeExperimentLock) {
      if (!isDifferentFromActive(experimentOverview)) {
        activeExperiment.setLastUsedTime(experimentOverview.getLastUsedTimeMs());
        activeExperiment.setArchived(context, appAccount, experimentOverview.isArchived());
        activeExperiment.getExperimentOverview().setImagePath(experimentOverview.getImagePath());
      }
    }
  }

  /**
   * Loads an active experiment from the disk if it is different from the currently cached active
   * experiment, otherwise just returns the current cached experiment.
   *
   * @param localExperimentOverview The local ExperimentOverview of the experiment to load. This is
   *     used for lookup.
   */
  Experiment getExperiment(ExperimentOverviewPojo localExperimentOverview) {
    synchronized (activeExperimentLock) {
      // Write only if the experiment ID is changing. If it's not changing, we just want to
      // reload even if it was dirty.
      if (isDifferentFromActive(localExperimentOverview)) {
        immediateWriteIfActiveChanging(localExperimentOverview);
        loadActiveExperimentFromFile(localExperimentOverview);
      }
      return activeExperiment;
    }
  }

  /**
   * Deletes an experiment from disk. Doesn't need to be the active one to be deleted. Sets the
   * active experiment to null if it is the same one
   */
  void deleteExperiment(String localExperimentId) {
    File expDirectory = getExperimentDirectory(localExperimentId);
    if (!deleteRecursive(expDirectory)) {
      // TODO show an error to the user, something has gone wrong
      // We are also in a weird partially deleted state at this point, need to fix.
      // TODO: Does any other work need to be done deleting assets, i.e. unregistering them
      // so that the user can't see pictures any more?
      return;
    }
    synchronized (activeExperimentLock) {
      if (activeExperiment != null
          && TextUtils.equals(
              activeExperiment.getExperimentOverview().getExperimentId(), localExperimentId)) {
        activeExperiment = null;
        cancelWriteTimer();
        activeExperimentNeedsWrite = false;
        if (experimentLibraryManager.getExperiment(localExperimentId) != null) {
          experimentLibraryManager.setDeleted(localExperimentId, true);
          localSyncManager.setDirty(localExperimentId, true);
        } else {
          if (Log.isLoggable(TAG, Log.WARN)) {
            Log.w(TAG, "Experiment Library didn't contain experiment: " + localExperimentId);
          }
        }
      }
    }
  }

  /**
   * Prepares an experiment for deletion by deleting all of its assets and contents, including trial
   * data. This is not reversable.
   */
  public void prepareExperimentForDeletion(Experiment experiment) {
    experiment.deleteContents(context, appAccount);
  }

  void beforeMovingAllExperimentsToAnotherAccount() {
    // This ExperimentCache is losing all experiments.
    synchronized (activeExperimentLock) {
      activeExperiment = null;
    }
  }

  void beforeMovingExperimentToAnotherAccount(String localExperimentId) {
    // This ExperimentCache is losing the experiment.
    synchronized (activeExperimentLock) {
      if (activeExperiment != null
          && TextUtils.equals(
              activeExperiment.getExperimentOverview().getExperimentId(), localExperimentId)) {
        activeExperiment = null;
      }
    }
  }

  /**
   * Used to set the active experiment when the experiment already exists, not when it's being made
   * for the first time. Sets the dirty bit to true, then starts a timer if needed to make sure that
   * the write happens within a reasonable time frame.
   */
  private void setExistingActiveExperiment(Experiment experiment) {
    synchronized (activeExperimentLock) {
      immediateWriteIfActiveChanging(experiment.getExperimentOverview());

      // Then set the new experiment and set the dirty bit to true, resetting the write timer,
      // so that we save it soon.
      activeExperiment = experiment;
      startWriteTimer();
    }
  }

  /** Create a folder with the experiment ID, and create the appropriate folders within it. */
  private boolean prepareForNewExperiment(String localExperimentId) {
    File expDirectory = getExperimentDirectory(localExperimentId);
    if (!expDirectory.exists() && !expDirectory.mkdirs()) {
      return false;
    }
    ;
    File assetsDirectory = getAssetsDirectory(expDirectory);
    if (!assetsDirectory.exists() && !assetsDirectory.mkdir()) {
      return false;
    }
    // Create the experimentFile
    try {
      File expFile = getExperimentFile(localExperimentId);
      if (!expFile.exists()) {
        boolean created = expFile.createNewFile();
        if (!created) {
          return false;
        }
      }
    } catch (IOException e) {
      if (Log.isLoggable(TAG, Log.DEBUG)) {
        Log.d(TAG, Log.getStackTraceString(e));
      }
      return false;
    }
    return true;
  }

  /**
   * Immediately writes the current active experiment to disk if it is different from the given
   * parameter localExperimentId.
   */
  private void immediateWriteIfActiveChanging(ExperimentOverviewPojo localExperimentOverview) {
    synchronized (activeExperimentLock) {
      if (activeExperiment != null
          && activeExperimentNeedsWrite
          && isDifferentFromActive(localExperimentOverview)) {
        // First write the old active experiment if the ID has changed.
        // Then cancel the write timer on the old experiment. We will reset it below.
        cancelWriteTimer();
        writeActiveExperimentFile();
      }
    }
  }

  private void cancelWriteTimer() {
    handler.removeCallbacks(writeRunnable);
  }

  private void startWriteTimer() {
    synchronized (activeExperimentLock) {
      if (activeExperimentNeedsWrite) {
        // The timer is already running.
        return;
      }

      // TODO: I think this can only be null during tests.  Can we rewrite the tests to remove
      //       this possibility?
      if (activeExperiment != null) {
        // We're going to write a new file, so rev the platform version
        activeExperiment.setPlatformVersion(PLATFORM_VERSION);
      }

      activeExperimentNeedsWrite = true;

      if (enableAutoWrite) {
        handler.postDelayed(writeRunnable, WRITE_DELAY_MS);
      }
    }
  }

  @VisibleForTesting
  boolean needsWrite() {
    synchronized (activeExperimentLock) {
      return activeExperimentNeedsWrite;
    }
  }

  /** Writes the active experiment to a file immediately, if needed. */
  void saveImmediately() {
    synchronized (activeExperimentLock) {
      if (activeExperimentNeedsWrite) {
        cancelWriteTimer();
        writeActiveExperimentFile();
      }
    }
  }

  /** Writes the active experiment to a file. */
  @VisibleForTesting
  void writeActiveExperimentFile() {
    writeExperimentFile(activeExperiment);
  }

  /** Writes the given experiment to a file. */
  @VisibleForTesting
  void writeExperimentFile(Experiment experimentToWrite) {
    boolean writingActiveExperiment = (activeExperiment == experimentToWrite);
    // If we are writing the active experiment, hold the activeExperimentLock until after we've set
    // activeExperimentNeedsWrite to false. Otherwise, if startWriteTimer is called on another
    // thread after we've got the proto from the experimentToWrite and before we set
    // activeExperimentNeedsWrite to false, it will see that activeExperimentNeedsWrite is true and
    // incorrectly decide that it doesn't need to start the timer.
    synchronized (writingActiveExperiment ? activeExperimentLock : new Object()) {
      GoosciExperiment.Experiment proto = experimentToWrite.getExperimentProto();
      if ((proto.getVersion() > VERSION)
          || (proto.getVersion() == VERSION && proto.getMinorVersion() > MINOR_VERSION)) {
        // If the major version is too new, or the minor version is too new, we can't save this.
        // TODO: Or should this throw onWriteFailed?
        failureListener.onNewerVersionDetected(experimentToWrite.getExperimentOverview());
        return;
      }

      File experimentFile = getExperimentFile(experimentToWrite.getExperimentOverview());
      boolean success;
      synchronized (appAccount.getLockForExperimentProtoFile()) {
        success =
            experimentProtoFileHelper.writeToFile(
                experimentFile, experimentToWrite.getExperimentProto(), getUsageTracker());
      }
      if (success) {
        if (writingActiveExperiment) {
          activeExperimentNeedsWrite = false;
        }
      } else {
        failureListener.onWriteFailed(experimentToWrite);
      }
    }
  }

  private UsageTracker getUsageTracker() {
    return WhistlePunkApplication.getUsageTracker(context);
  }

  @VisibleForTesting
  void loadActiveExperimentFromFile(ExperimentOverviewPojo experimentOverview) {
    File experimentFile = getExperimentFile(experimentOverview);
    GoosciExperiment.Experiment proto;
    synchronized (appAccount.getLockForExperimentProtoFile()) {
      proto =
          experimentProtoFileHelper.readFromFile(
              experimentFile, GoosciExperiment.Experiment::parseFrom, getUsageTracker());
    }
    synchronized (activeExperimentLock) {
      if (proto != null) {
        Experiment toLoad = Experiment.fromExperiment(proto, experimentOverview);
        upgradeExperimentVersionIfNeeded(toLoad);
        activeExperiment = toLoad;
        localSyncManager.addExperiment(activeExperiment.getExperimentId());
        experimentLibraryManager.addExperiment(activeExperiment.getExperimentId());
      } else {
        // Or maybe pass a FailureListener into the load instead of failing here.
        failureListener.onReadFailed(experimentOverview);
        activeExperiment = null;
      }
    }
  }

  private void upgradeExperimentVersionIfNeeded(Experiment experiment) {
    upgradeExperimentVersionIfNeeded(experiment, VERSION, MINOR_VERSION, PLATFORM_VERSION);
  }

  /**
   * Upgrades an experiment proto if necessary. Requests a save if the upgrade happened.
   *
   * @param experiment The experiment to upgrade if necessary
   * @param newMajorVersion The major version to upgrade to, available for testing
   * @param newMinorVersion The minor version to upgrade to, available for testing
   * @param newPlatformVersion The platform version to upgrade to, available for testing
   */
  @VisibleForTesting
  void upgradeExperimentVersionIfNeeded(
      Experiment experiment, int newMajorVersion, int newMinorVersion, int newPlatformVersion) {

    Version.FileVersion.Builder fileVersion;
    if (experiment.getFileVersion() == null) {
      fileVersion = Version.FileVersion.newBuilder();
      fileVersion.setVersion(0).setMinorVersion(0).setPlatformVersion(0);
    } else {
      fileVersion = experiment.getFileVersion().toBuilder();
    }

    if (fileVersion.getVersion() == newMajorVersion
        && fileVersion.getMinorVersion() == newMinorVersion
        && fileVersion.getPlatformVersion() == newPlatformVersion) {
      // No upgrade needed, this is running the same version as us.
      return;
    }
    if (fileVersion.getVersion() > newMajorVersion) {
      // It is too new for us to read -- the major version is later than ours.
      failureListener.onNewerVersionDetected(experiment.getExperimentOverview());
      return;
    }
    // Try to upgrade the major version
    if (fileVersion.getVersion() == 0) {
      // Do any work to increment the minor version.

      if (fileVersion.getVersion() < newMajorVersion) {
        // Upgrade from 0 to 1, for example: Increment the major version and reset the minor
        // version.
        // Other work could be done here first like populating protos.
        revMajorVersionTo(fileVersion, 1);
      }
    }
    if (fileVersion.getVersion() == 1) {
      // Minor version upgrades are done within the if statement
      // for their major version counterparts.
      if (fileVersion.getMinorVersion() == 0 && fileVersion.getMinorVersion() < newMinorVersion) {
        // Upgrade minor version from 0 to 1, within in major version 1, for example.
        fileVersion.setMinorVersion(1);
      }

      if (fileVersion.getMinorVersion() == 1 && fileVersion.getMinorVersion() < newMinorVersion) {
        // Upgrade minor version from 1 to 2, within in major version 1, for example.
        fileVersion.setMinorVersion(2);
      }

      // More minor version upgrades for major version 1 could be done here.

      // Also, update any data from incomplete or buggy platformVersions here.
      // See go/platform-version
      // TODO: create open-sourceable version of this doc
      if (fileVersion.getPlatformVersion() == 0
          && fileVersion.getPlatformVersion() < newPlatformVersion) {
        // Any further upgrades may do work in logic below
        setPlatformVersion(fileVersion, 1);
      }

      if (fileVersion.getPlatformVersion() == 1
          && fileVersion.getPlatformVersion() < newPlatformVersion) {
        // Update trial indexes for each trial in the experiment
        // Since this has never been set, we don't know about deleted trials, so we will
        // just do our best and index over again.
        experiment.setTotalTrials(experiment.getTrials().size());
        int count = 0;
        for (Trial trial : experiment.getTrials()) {
          trial.setTrialNumberInExperiment(++count);
        }
        setPlatformVersion(fileVersion, 2);
      }

      if (fileVersion.getPlatform() != GoosciGadgetInfo.GadgetInfo.Platform.ANDROID) {
        // Update platform version to reflect Android, if this is coming from iOS.
        // Also put any iOS version specific fixes in here, if we find any issues.
        setPlatformVersion(fileVersion, newPlatformVersion);
      }

      if (fileVersion.getPlatformVersion() < newPlatformVersion) {
        // We will already be at platformVersion 2 or greater. Do any necessary platform
        // bug fixes here before increasing to newPlatformVersion. None are necessary
        // at this time.
        setPlatformVersion(fileVersion, newPlatformVersion);
      }

      // When we are ready for version 2.0, we would do work in the following if statement
      // and then call incrementMajorVersion.
      if (fileVersion.getVersion() < newMajorVersion) {
        // Do any work to upgrade version, then increment the version when we are
        // ready to go to 2.0 or above.
        revMajorVersionTo(fileVersion, 2);
      }
    }
    experiment.setFileVersion(fileVersion.build());

    // We've made changes we need to save.
    startWriteTimer();
  }

  private static void revMajorVersionTo(Version.FileVersion.Builder fileVersion, int majorVersion) {
    fileVersion.setVersion(majorVersion).setMinorVersion(0);
  }

  private static void setPlatformVersion(
      Version.FileVersion.Builder fileVersion, int platformVersion) {
    fileVersion
        .setPlatform(GoosciGadgetInfo.GadgetInfo.Platform.ANDROID)
        .setPlatformVersion(platformVersion);
  }

  private File getExperimentFile(ExperimentOverviewPojo experimentOverview) {
    return getExperimentFile(experimentOverview.getExperimentId());
  }

  private File getExperimentFile(String localExperimentId) {
    return new File(getExperimentDirectory(localExperimentId), FileMetadataManager.EXPERIMENT_FILE);
  }

  private File getExperimentDirectory(String localExperimentId) {
    return FileMetadataUtil.getInstance().getExperimentDirectory(appAccount, localExperimentId);
  }

  private File getAssetsDirectory(File experimentDirectory) {
    return new File(experimentDirectory, FileMetadataManager.ASSETS_DIRECTORY);
  }

  private boolean isDifferentFromActive(ExperimentOverviewPojo other) {
    synchronized (activeExperimentLock) {
      if (activeExperiment == null) {
        return true;
      }
      return !TextUtils.equals(
          other.getExperimentId(), activeExperiment.getExperimentOverview().getExperimentId());
    }
  }

  @VisibleForTesting
  static boolean deleteRecursive(File file) {
    if (file.isDirectory()) {
      for (File child : file.listFiles()) {
        deleteRecursive(child);
      }
    }
    return file.delete();
  }
}
