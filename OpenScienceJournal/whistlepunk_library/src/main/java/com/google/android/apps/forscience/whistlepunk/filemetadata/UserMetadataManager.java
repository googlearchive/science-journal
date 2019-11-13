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
import com.google.android.apps.forscience.whistlepunk.WhistlePunkApplication;
import com.google.android.apps.forscience.whistlepunk.accounts.AppAccount;
import com.google.android.apps.forscience.whistlepunk.analytics.UsageTracker;
import com.google.android.apps.forscience.whistlepunk.metadata.GoosciUserMetadata;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Reads and writes ExperimentOverview lists */
// TODO: Should this be a cache too?
public class UserMetadataManager {
  private static final String TAG = "UserMetadataManager";

  // The current version number we expect from UserMetadata.
  // See upgradeUserMetadataVersionIfNeeded for the meaning of version numbers.
  private static final int VERSION = 1;

  // The current minor version number we expect from UserMetadata.
  // See upgradeUserMetadataVersionIfNeeded for the meaning of version numbers.
  private static final int MINOR_VERSION = 1;
  private static final long WRITE_DELAY_MS = 500;

  private final Runnable writeRunnable;
  private final Handler handler;
  private final ExecutorService backgroundWriteThread;
  private final long writeDelayMs;
  private boolean needsWrite = false;
  private UserMetadataPojo userMetadata;
  private UsageTracker usageTracker;

  interface FailureListener {
    // TODO: What's helpful to pass back here? Maybe info about the type of error?
    void onWriteFailed();

    void onReadFailed();

    // A newer version of the User Metadata proto was found on the device. We cannot parse it
    // with this version of the app.
    void onNewerVersionDetected();
  }

  private FailureListener failureListener;
  private LiteProtoFileHelper<GoosciUserMetadata.UserMetadata> overviewProtoFileHelper;
  private File userMetadataFile;

  public UserMetadataManager(
      Context context, AppAccount appAccount, FailureListener failureListener) {
    this.failureListener = failureListener;
    overviewProtoFileHelper = new LiteProtoFileHelper<>();
    userMetadataFile = FileMetadataUtil.getInstance().getUserMetadataFile(appAccount);
    backgroundWriteThread = Executors.newSingleThreadExecutor();
    handler = new Handler();
    writeRunnable =
        () -> {
          if (needsWrite) {
            backgroundWriteThread.execute(() -> writeUserMetadata(userMetadata));
          }
        };
    writeDelayMs = WRITE_DELAY_MS;
    usageTracker = WhistlePunkApplication.getUsageTracker(context);
  }

  private void startWriteTimer() {
    if (needsWrite) {
      // The timer is already running.
      return;
    }
    needsWrite = true;
    handler.postDelayed(writeRunnable, writeDelayMs);
  }

  public void saveImmediately() {
    if (needsWrite) {
      writeUserMetadata(userMetadata);
      cancelWriteTimer();
    }
  }

  private void cancelWriteTimer() {
    handler.removeCallbacks(writeRunnable);
  }

  /** Gets an experiment overview by experiment ID from the Shared Metadata. */
  ExperimentOverviewPojo getExperimentOverview(String experimentId) {
    userMetadata = getUserMetadata();
    if (userMetadata == null) {
      return null;
    }
    return userMetadata.getOverview(experimentId);
  }

  /** Adds a new experiment overview to the Shared Metadata. */
  void addExperimentOverview(ExperimentOverviewPojo overviewToAdd) {
    updateExperimentOverview(overviewToAdd);
  }

  /** Updates an experiment overview in the Shared Metadata. */
  void updateExperimentOverview(ExperimentOverviewPojo overviewToUpdate) {
    userMetadata = getUserMetadata();
    if (userMetadata == null) {
      return;
    }
    userMetadata.insertOverview(overviewToUpdate);
    startWriteTimer();
  }

  /**
   * Deletes an experiment overview from disk.
   *
   * @param experimentIdToDelete the ID of the overview to be deleted.
   */
  void deleteExperimentOverview(String experimentIdToDelete) {
    userMetadata = getUserMetadata();
    userMetadata.deleteOverview(experimentIdToDelete);
    startWriteTimer();
  }

  void deleteAllExperimentOverviews() {
    userMetadata = getUserMetadata();
    if (userMetadata == null) {
      return;
    }
    userMetadata.clearOverviews();
    startWriteTimer();
  }

  /**
   * Gets all the experiment overviews
   *
   * @param includeArchived Whether to include the archived experiments.
   */
  List<ExperimentOverviewPojo> getExperimentOverviews(boolean includeArchived) {
    userMetadata = getUserMetadata();
    if (userMetadata == null) {
      return null;
    }
    return userMetadata.getOverviews(includeArchived);
  }

  /** Adds a device to the user's list of devices if it is not yet added. */
  public void addMyDevice(DeviceSpecPojo device) {
    UserMetadataPojo userMetadata = getUserMetadata();

    if (userMetadata == null) {
      return;
    }

    userMetadata.addDevice(device);

    // TODO: capture this pattern (read, null check, write) in a helper method?
    startWriteTimer();
  }

  public void removeMyDevice(DeviceSpecPojo device) {
    UserMetadataPojo userMetadata = getUserMetadata();

    if (userMetadata == null) {
      return;
    }

    userMetadata.removeDevice(device);

    // TODO: capture this pattern (read, null check, write) in a helper method?
    startWriteTimer();
  }

  public List<DeviceSpecPojo> getMyDevices() {
    userMetadata = getUserMetadata();

    return userMetadata.getMyDevices();
  }

  /**
   * Reads the shared metadata from the file, and throws an error to the failure listener if needed.
   */
  private UserMetadataPojo getUserMetadata() {
    if (userMetadata != null) {
      return userMetadata;
    }
    // Otherwise, read it from the file.
    boolean firstTime = createUserMetadataFileIfNeeded();
    // Create a new user metadata proto to populate
    if (firstTime) {
      // If the file has nothing in it, these version numbers will be the basis of our initial
      // UserMetadata file.
      userMetadata = new UserMetadataPojo();
      userMetadata.setVersion(VERSION);
      userMetadata.setMinorVersion(MINOR_VERSION);
    } else {
      GoosciUserMetadata.UserMetadata userMetadataProto =
          overviewProtoFileHelper.readFromFile(
              userMetadataFile, GoosciUserMetadata.UserMetadata::parseFrom, usageTracker);
      if (userMetadataProto == null) {
        failureListener.onReadFailed();
        return null;
      }
      userMetadata = UserMetadataPojo.fromProto(userMetadataProto);
      upgradeUserMetadataVersionIfNeeded(userMetadata);
    }
    return userMetadata;
  }

  private void upgradeUserMetadataVersionIfNeeded(UserMetadataPojo userMetadata) {
    upgradeUserMetadataVersionIfNeeded(userMetadata, VERSION, MINOR_VERSION);
  }

  /**
   * Upgrades and saves user metadata to match the version given.
   *
   * @param userMetadata The metadata to upgrade if necessary
   * @param newMajorVersion The major version to upgrade to, available for testing
   * @param newMinorVersion The minor version to upgrade to, available for testing
   */
  @VisibleForTesting
  void upgradeUserMetadataVersionIfNeeded(
      UserMetadataPojo userMetadata, int newMajorVersion, int newMinorVersion) {
    if (userMetadata.getVersion() == newMajorVersion
        && userMetadata.getMinorVersion() == newMinorVersion) {
      // No upgrade needed, this is running the same version as us.
      return;
    }
    if (userMetadata.getVersion() > newMajorVersion) {
      // It is too new for us to read -- the major version is later than ours.
      failureListener.onNewerVersionDetected();
      return;
    }
    // Try to upgrade the major version
    if (userMetadata.getVersion() == 0) {
      // Do any work to increment the minor version.

      if (userMetadata.getVersion() < newMajorVersion) {
        // Upgrade from 0 to 1, for example: Increment the major version and reset the minor
        // version.
        // Other work could be done here first like populating protos.
        revMajorVersionTo(userMetadata, 1);
      }
    }
    if (userMetadata.getVersion() == 1) {
      // Minor version upgrades are done within the if statement
      // for their major version counterparts.
      if (userMetadata.getMinorVersion() == 0 && userMetadata.getMinorVersion() < newMinorVersion) {
        // Upgrade minor version from 0 to 1, within in major version 1, for example.
        userMetadata.setMinorVersion(1);
      }
      // More minor version upgrades for major version 1 could be done here.

      // When we are ready for version 2.0, we would do work in the following if statement
      // and then call incrementMajorVersion.
      if (userMetadata.getVersion() < newMajorVersion) {
        // Do any work to upgrade version, then increment the version when we are
        // ready to go to 2.0 or above.
        revMajorVersionTo(userMetadata, 2);
      }
    }
    // We've made changes we need to save.
    writeUserMetadata(userMetadata);
  }

  private void revMajorVersionTo(UserMetadataPojo userMetadata, int majorVersion) {
    userMetadata.setVersion(majorVersion);
    userMetadata.setMinorVersion(0);
  }

  /** Writes the shared metadata object to the file. */
  private void writeUserMetadata(UserMetadataPojo userMetadata) {
    if (userMetadata.getVersion() > VERSION
        || (userMetadata.getVersion() == VERSION
            && userMetadata.getMinorVersion() > MINOR_VERSION)) {
      // If the major version is too new, or the minor version is too new, we can't save this.
      failureListener.onNewerVersionDetected(); // TODO: Or should this throw onWriteFailed?
    }
    createUserMetadataFileIfNeeded();
    if (!overviewProtoFileHelper.writeToFile(
        userMetadataFile, userMetadata.toProto(), usageTracker)) {
      failureListener.onWriteFailed();
    } else {
      needsWrite = false;
    }
  }

  /**
   * Lazy creation of the shared metadata file. This is probably only done the first time the app is
   * opened, so we could consider calling this only once to reduce load, but it doesn't seem super
   * expensive. Returns true if a new file was created.
   */
  private boolean createUserMetadataFileIfNeeded() {
    if (!userMetadataFile.exists()) {
      // If the files aren't there yet, create them.
      try {
        boolean created = userMetadataFile.createNewFile();
        if (!created) {
          failureListener.onWriteFailed();
          return false;
        }
        return true;
      } catch (IOException e) {
        failureListener.onWriteFailed();
      }
    }
    return false;
  }
}
