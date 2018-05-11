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
import androidx.annotation.VisibleForTesting;
import android.text.TextUtils;
import android.util.Log;
import com.google.android.apps.forscience.whistlepunk.AppSingleton;
import com.google.android.apps.forscience.whistlepunk.WhistlePunkApplication;
import com.google.android.apps.forscience.whistlepunk.accounts.AppAccount;
import com.google.android.apps.forscience.whistlepunk.analytics.UsageTracker;
import com.google.android.apps.forscience.whistlepunk.data.nano.GoosciGadgetInfo;
import com.google.android.apps.forscience.whistlepunk.metadata.nano.GoosciExperiment;
import com.google.android.apps.forscience.whistlepunk.metadata.nano.GoosciTrial;
import com.google.android.apps.forscience.whistlepunk.metadata.nano.GoosciUserMetadata;
import com.google.android.apps.forscience.whistlepunk.metadata.nano.Version;
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
    void onReadFailed(GoosciUserMetadata.ExperimentOverview localExperimentOverview);

    // When a newer version is found and we cannot parse it
    void onNewerVersionDetected(GoosciUserMetadata.ExperimentOverview experimentOverview);
  }

  private FailureListener failureListener;
  private Context context;
  private AppAccount appAccount;
  private Experiment activeExperiment;
  private ProtoFileHelper<GoosciExperiment.Experiment> experimentProtoFileHelper;
  private boolean activeExperimentNeedsWrite = false;
  private LocalSyncManager localSyncManager;
  private ExperimentLibraryManager experimentLibraryManager;
  private final long writeDelayMs;

  private final Handler handler;
  private final ExecutorService backgroundWriteThread;
  private final Runnable writeRunnable;

  public ExperimentCache(Context context, AppAccount appAccount, FailureListener failureListener) {
    this(context, appAccount, failureListener, WRITE_DELAY_MS);
  }

  @VisibleForTesting
  ExperimentCache(
      Context context, AppAccount appAccount, FailureListener failureListener, long writeDelayMs) {
    this.context = context;
    this.appAccount = appAccount;
    this.failureListener = failureListener;
    experimentProtoFileHelper = new ProtoFileHelper<>();
    handler = new Handler();
    backgroundWriteThread = Executors.newSingleThreadExecutor();
    writeRunnable =
        () -> {
          if (activeExperimentNeedsWrite) {
            backgroundWriteThread.execute(this::writeActiveExperimentFile);
          }
        };
    this.writeDelayMs = writeDelayMs;

    localSyncManager = AppSingleton.getInstance(context).getLocalSyncManager();
    experimentLibraryManager = AppSingleton.getInstance(context).getExperimentLibraryManager();
  }

  @VisibleForTesting
  Experiment getActiveExperimentForTests() {
    return activeExperiment;
  }

  /**
   * Creates file space for a new experiment, and gets it ready for a save.
   *
   * @return whether space was created successfully.
   */
  boolean createNewExperiment(Experiment experiment) {
    if (!prepareForNewExperiment(experiment.getExperimentOverview().experimentId)) {
      failureListener.onWriteFailed(experiment);
      return false;
    }
    return setExistingActiveExperiment(experiment);
  }

  /** Updates the given experiment. */
  void updateExperiment(Experiment experiment) {
    if (isDifferentFromActive(experiment.getExperimentOverview())) {
      immediateWriteIfActiveChanging(experiment.getExperimentOverview());
    }
    experimentLibraryManager.setOpened(experiment.getExperimentId(), System.currentTimeMillis());
    localSyncManager.setDirty(experiment.getExperimentId(), true);
    activeExperiment = experiment;
    startWriteTimer();
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
  void onExperimentOverviewUpdated(GoosciUserMetadata.ExperimentOverview experimentOverview) {
    if (!isDifferentFromActive(experimentOverview)) {
      activeExperiment.setLastUsedTime(experimentOverview.lastUsedTimeMs);
      activeExperiment.setArchived(context, experimentOverview.isArchived);
      activeExperiment.getExperimentOverview().imagePath = experimentOverview.imagePath;
    }
  }

  /**
   * Loads an active experiment from the disk if it is different from the currently cached active
   * experiment, otherwise just returns the current cached experiment.
   *
   * @param localExperimentOverview The local ExperimentOverview of the experiment to load. This is
   *     used for lookup.
   */
  Experiment getExperiment(GoosciUserMetadata.ExperimentOverview localExperimentOverview) {
    // Write only if the experiment ID is changing. If it's not changing, we just want to
    // reload even if it was dirty.
    if (isDifferentFromActive(localExperimentOverview)) {
      immediateWriteIfActiveChanging(localExperimentOverview);
      loadActiveExperimentFromFile(localExperimentOverview);
    }
    return activeExperiment;
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
    if (activeExperiment != null
        && TextUtils.equals(
            activeExperiment.getExperimentOverview().experimentId, localExperimentId)) {
      activeExperiment = null;
      cancelWriteTimer();
      activeExperimentNeedsWrite = false;
      experimentLibraryManager.setDeleted(localExperimentId, true);
    }
  }

  /**
   * Prepares an experiment for deletion by deleting all of its assets and contents, including trial
   * data. This is not reversable.
   */
  public void prepareExperimentForDeletion(Experiment experiment) {
    experiment.deleteContents(context);
  }

  /**
   * Used to set the active experiment when the experiment already exists, not when it's being made
   * for the first time. Sets the dirty bit to true, then starts a timer if needed to make sure that
   * the write happens within a reasonable time frame.
   */
  private boolean setExistingActiveExperiment(Experiment experiment) {
    immediateWriteIfActiveChanging(experiment.getExperimentOverview());

    // Then set the new experiment and set the dirty bit to true, resetting the write timer,
    // so that we save it soon.
    activeExperiment = experiment;
    startWriteTimer();
    return true;
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
  private void immediateWriteIfActiveChanging(
      GoosciUserMetadata.ExperimentOverview localExperimentOverview) {
    if (activeExperiment != null
        && activeExperimentNeedsWrite
        && isDifferentFromActive(localExperimentOverview)) {
      // First write the old active experiment if the ID has changed.
      // Then cancel the write timer on the old experiment. We will reset it below.
      cancelWriteTimer();
      writeActiveExperimentFile();
    }
  }

  private void cancelWriteTimer() {
    handler.removeCallbacks(writeRunnable);
  }

  private void startWriteTimer() {
    if (activeExperimentNeedsWrite) {
      // The timer is already running.
      return;
    }

    // TODO: I think this can only be null during tests.  Can we rewrite the tests to remove
    //       this possibility?
    if (activeExperiment != null) {
      // We're going to write a new file, so rev the platform version
      setPlatformVersion(activeExperiment.getExperimentProto(), PLATFORM_VERSION);
    }

    activeExperimentNeedsWrite = true;
    handler.postDelayed(writeRunnable, writeDelayMs);
  }

  @VisibleForTesting
  boolean needsWrite() {
    return activeExperimentNeedsWrite;
  }

  /** Writes the active experiment to a file immediately, if needed. */
  void saveImmediately() {
    if (activeExperimentNeedsWrite) {
      cancelWriteTimer();
      writeActiveExperimentFile();
    }
  }

  /** Writes the active experiment to a file. */
  @VisibleForTesting
  void writeActiveExperimentFile() {
    if (activeExperiment.getVersion() > VERSION
        || activeExperiment.getVersion() == VERSION
            && activeExperiment.getMinorVersion() > MINOR_VERSION) {
      // If the major version is too new, or the minor version is too new, we can't save this.
      // TODO: Or should this throw onWriteFailed?
      failureListener.onNewerVersionDetected(activeExperiment.getExperimentOverview());
    }

    File experimentFile = getExperimentFile(activeExperiment.getExperimentOverview());
    boolean success =
        experimentProtoFileHelper.writeToFile(
            experimentFile, activeExperiment.getExperimentProto(), getUsageTracker());
    if (success) {
      activeExperimentNeedsWrite = false;
    } else {
      failureListener.onWriteFailed(activeExperiment);
    }
  }

  private UsageTracker getUsageTracker() {
    return WhistlePunkApplication.getUsageTracker(context);
  }

  @VisibleForTesting
  void loadActiveExperimentFromFile(GoosciUserMetadata.ExperimentOverview experimentOverview) {
    File experimentFile = getExperimentFile(experimentOverview);
    GoosciExperiment.Experiment proto =
        experimentProtoFileHelper.readFromFile(
            experimentFile, GoosciExperiment.Experiment::parseFrom, getUsageTracker());
    if (proto != null) {
      upgradeExperimentVersionIfNeeded(proto, experimentOverview);
      activeExperiment = Experiment.fromExperiment(proto, experimentOverview);
      localSyncManager.addExperiment(activeExperiment.getExperimentId());
      experimentLibraryManager.addExperiment(activeExperiment.getExperimentId());
    } else {
      // Or maybe pass a FailureListener into the load instead of failing here.
      failureListener.onReadFailed(experimentOverview);
      activeExperiment = null;
    }
  }

  private void upgradeExperimentVersionIfNeeded(
      GoosciExperiment.Experiment proto, GoosciUserMetadata.ExperimentOverview experimentOverview) {
    upgradeExperimentVersionIfNeeded(
        proto, experimentOverview, VERSION, MINOR_VERSION, PLATFORM_VERSION);
  }

  /**
   * Upgrades an experiment proto if necessary. Requests a save if the upgrade happened.
   *
   * @param proto The experiment to upgrade if necessary
   * @param newMajorVersion The major version to upgrade to, available for testing
   * @param newMinorVersion The minor version to upgrade to, available for testing
   * @param newPlatformVersion The platform version to upgrade to, available for testing
   */
  @VisibleForTesting
  void upgradeExperimentVersionIfNeeded(
      GoosciExperiment.Experiment proto,
      GoosciUserMetadata.ExperimentOverview experimentOverview,
      int newMajorVersion,
      int newMinorVersion,
      int newPlatformVersion) {

    if (proto.fileVersion == null) {
      proto.fileVersion = new Version.FileVersion();
      proto.fileVersion.version = 0;
      proto.fileVersion.minorVersion = 0;
      proto.fileVersion.platformVersion = 0;
    }

    Version.FileVersion fileVersion = proto.fileVersion;

    if (fileVersion.version == newMajorVersion
        && fileVersion.minorVersion == newMinorVersion
        && fileVersion.platformVersion == newPlatformVersion) {
      // No upgrade needed, this is running the same version as us.
      return;
    }
    if (fileVersion.version > newMajorVersion) {
      // It is too new for us to read -- the major version is later than ours.
      failureListener.onNewerVersionDetected(experimentOverview);
      return;
    }
    // Try to upgrade the major version
    if (fileVersion.version == 0) {
      // Do any work to increment the minor version.

      if (fileVersion.version < newMajorVersion) {
        // Upgrade from 0 to 1, for example: Increment the major version and reset the minor
        // version.
        // Other work could be done here first like populating protos.
        revMajorVersionTo(proto, 1);
      }
    }
    if (fileVersion.version == 1) {
      // Minor version upgrades are done within the if statement
      // for their major version counterparts.
      if (fileVersion.minorVersion == 0 && fileVersion.minorVersion < newMinorVersion) {
        // Upgrade minor version from 0 to 1, within in major version 1, for example.
        fileVersion.minorVersion = 1;
      }

      if (fileVersion.minorVersion == 1 && fileVersion.minorVersion < newMinorVersion) {
        // Upgrade minor version from 1 to 2, within in major version 1, for example.
        fileVersion.minorVersion = 2;
      }

      // More minor version upgrades for major version 1 could be done here.

      // Also, update any data from incomplete or buggy platformVersions here.
      // See go/platform-version
      // TODO: create open-sourceable version of this doc
      if (fileVersion.platformVersion == 0 && fileVersion.platformVersion < newPlatformVersion) {
        // Any further upgrades may do work in logic below
        setPlatformVersion(proto, 1);
      }

      if (fileVersion.platformVersion == 1 && fileVersion.platformVersion < newPlatformVersion) {
        // Update trial indexes for each trial in the experiment
        // Since this has never been set, we don't know about deleted trials, so we will
        // just do our best and index over again.
        proto.totalTrials = proto.trials.length;
        int count = 0;
        for (GoosciTrial.Trial trial : proto.trials) {
          trial.trialNumberInExperiment = ++count;
        }
        setPlatformVersion(proto, 2);
      }

      if (fileVersion.platform != GoosciGadgetInfo.GadgetInfo.Platform.ANDROID) {
        // Update platform version to reflect Android, if this is coming from iOS.
        // Also put any iOS version specific fixes in here, if we find any issues.
        setPlatformVersion(proto, newPlatformVersion);
      }

      if (fileVersion.platformVersion < newPlatformVersion) {
        // We will already be at platformVersion 2 or greater. Do any necessary platform
        // bug fixes here before increasing to newPlatformVersion. None are necessary
        // at this time.
        setPlatformVersion(proto, newPlatformVersion);
      }

      // When we are ready for version 2.0, we would do work in the following if statement
      // and then call incrementMajorVersion.
      if (fileVersion.version < newMajorVersion) {
        // Do any work to upgrade version, then increment the version when we are
        // ready to go to 2.0 or above.
        revMajorVersionTo(proto, 2);
      }
    }

    // We've made changes we need to save.
    startWriteTimer();
  }

  private void revMajorVersionTo(GoosciExperiment.Experiment proto, int majorVersion) {
    proto.fileVersion.version = majorVersion;
    proto.fileVersion.minorVersion = 0;
  }

  private void setPlatformVersion(GoosciExperiment.Experiment proto, int platformVersion) {
    proto.fileVersion.platform = GoosciGadgetInfo.GadgetInfo.Platform.ANDROID;
    proto.fileVersion.platformVersion = platformVersion;
  }

  private File getExperimentFile(GoosciUserMetadata.ExperimentOverview experimentOverview) {
    return getExperimentFile(experimentOverview.experimentId);
  }

  private File getExperimentFile(String localExperimentId) {
    return new File(getExperimentDirectory(localExperimentId), FileMetadataManager.EXPERIMENT_FILE);
  }

  private File getExperimentDirectory(String localExperimentId) {
    return FileMetadataManager.getExperimentDirectory(appAccount, localExperimentId);
  }

  private File getAssetsDirectory(File experimentDirectory) {
    return new File(experimentDirectory, FileMetadataManager.ASSETS_DIRECTORY);
  }

  private boolean isDifferentFromActive(GoosciUserMetadata.ExperimentOverview other) {
    if (activeExperiment == null) {
      return true;
    }
    return !TextUtils.equals(
        other.experimentId, activeExperiment.getExperimentOverview().experimentId);
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
