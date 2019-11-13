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

package com.google.android.apps.forscience.whistlepunk.analytics;

import androidx.annotation.VisibleForTesting;
import com.google.android.apps.forscience.whistlepunk.filemetadata.Label;
import com.google.android.apps.forscience.whistlepunk.metadata.GoosciLabel;
import com.google.common.base.Throwables;

/** Constants for usage tracking. */
public final class TrackerConstants {

  // Screen names.
  // public static final String SCREEN_INTRO = "intro";
  // public static final String SCREEN_INTRO_REPLAY = "intro_replay";
  // public static final String SCREEN_OBSERVE_RECORD = "observe_record";
  public static final String SCREEN_EXPERIMENT_LIST = "experiment_list";
  // public static final String SCREEN_NEW_EXPERIMENT = "experiment_new";
  public static final String SCREEN_UPDATE_EXPERIMENT = "experiment_update";
  public static final String SCREEN_EXPERIMENT_DETAIL = "experiment_detail";
  public static final String SCREEN_RUN_REVIEW = "run_review";
  public static final String SCREEN_SETTINGS = "settings";
  public static final String SCREEN_ABOUT = "about";
  public static final String SCREEN_DEVICE_MANAGER = "device_manager";
  public static final String SCREEN_DEVICE_OPTIONS = "device_options";
  public static final String SCREEN_UPDATE_RUN = "run_update";
  public static final String SCREEN_TRIGGER_LIST = "trigger_list";
  public static final String SCREEN_TRIGGER_EDIT = "trigger_edit";
  public static final String SCREEN_SENSOR_INFO = "sensor_info";
  public static final String SCREEN_PANES = "panes";
  public static final String SCREEN_EXPERIMENT = "experiment";

  // Custom dimension indices.
  public static final int DIMENSION_MODE = 1;
  public static final int RELEASE_TYPE = 2;
  public static final int PANES_TOOL_NAME = 3;
  public static final int PANES_DRAWER_STATE = 4;

  // Categories
  public static final String CATEGORY_EXPERIMENTS = "Experiments";
  public static final String CATEGORY_RUNS = "Runs";
  public static final String CATEGORY_NOTES = "Notes";
  public static final String CATEGORY_APP = "App";
  public static final String CATEGORY_TRIGGERS = "Triggers";
  public static final String CATEGORY_API = "API";
  public static final String CATEGORY_INFO = "Info";
  public static final String CATEGORY_SENSOR_MANAGEMENT = "ManageSensors";
  public static final String CATEGORY_STORAGE = "Storage";
  public static final String CATEGORY_PANES = "Panes";
  public static final String CATEGORY_EXPERIMENT = "Experiment";
  public static final String CATEGORY_SIGN_IN = "SignIn";
  public static final String CATEGORY_CLAIMING_DATA = "ClaimingData";
  public static final String CATEGORY_SYNC = "Sync";
  public static final String CATEGORY_FAILURE = "Failure";

  // Event actions
  public static final String ACTION_CREATE = "Create";
  public static final String ACTION_RECORDED = "Recorded";
  public static final String ACTION_EDITED = "EditedValue";
  public static final String ACTION_ARCHIVE = "Archived";
  public static final String ACTION_UNARCHIVE = "Unarchived";
  public static final String ACTION_DELETED = "Deleted";
  public static final String ACTION_DELETE_UNDO = "UndoDelete";
  public static final String ACTION_SET_MODE = "SetMode";
  public static final String ACTION_START_AUDIO_PLAYBACK = "StartAudioPlayback";
  public static final String ACTION_TRY_RECORDING_FROM_TRIGGER = "TryRecordingFromTrigger";
  public static final String ACTION_TRY_STOP_RECORDING_FROM_TRIGGER = "TryStopRecordingFromTrigger";
  public static final String ACTION_API_SCAN_TIMEOUT = "ApiScanTimeout";
  public static final String ACTION_INFO = "Info";
  public static final String ACTION_SCAN = "Scan";
  public static final String ACTION_CROP_COMPLETED = "CropCompleted";
  public static final String ACTION_CROP_STARTED = "CropStarted";
  public static final String ACTION_CROP_FAILED = "CropFailed";
  public static final String ACTION_WRITE_FAILED = "WriteFailed";
  public static final String ACTION_READ_FAILED = "ReadFailed";
  public static final String ACTION_PAUSED = "Paused";
  public static final String ACTION_LABEL_ADDED = "LabelAdded";
  public static final String ACTION_SHARED = "Shared";
  public static final String ACTION_IMPORTED = "Imported";
  public static final String ACTION_DOWNLOAD_REQUESTED = "DownloadRequested";
  public static final String ACTION_CLAIM_FAILED = "ClaimFailed";
  public static final String ACTION_RECOVERY_FAILED = "RecoveryFailed";
  public static final String ACTION_RECOVER_EXPERIMENT_ATTEMPTED = "RecoverExperimentAttempted";
  public static final String ACTION_RECOVER_EXPERIMENT_SUCCEEDED = "RecoverExperimentSucceeded";
  public static final String ACTION_RECOVER_EXPERIMENT_FAILED = "RecoverExperimentFailed";

  public static final String ACTION_START_SIGN_IN = "StartSignIn";
  public static final String ACTION_START_SWITCH_ACCOUNT = "StartSwitchAccount";
  public static final String ACTION_SIGN_IN_FROM_WELCOME = "SignInFromWelcome";
  public static final String ACTION_SIGN_IN_FROM_SIDEBAR = "SignInFromSidebar";
  public static final String ACTION_LEARN_MORE = "LearnMore";
  public static final String ACTION_CONTINUE_WITHOUT_ACCOUNT_FROM_WELCOME =
      "ContinueWithoutAccount";
  public static final String ACTION_CONTINUE_WITHOUT_ACCOUNT_FROM_SIDEBAR =
      "ContinueWithoutAccountSidebar";
  public static final String ACTION_ACCOUNT_CHANGED = "AccountChanged";
  public static final String ACTION_ACCOUNT_SIGNED_IN = "AccountSignedIn";
  public static final String ACTION_FAILED = "Failed"; // Android only.
  public static final String ACTION_SWITCH_FAILED = "SwitchFailed"; // iOS only.
  public static final String ACTION_NO_CHANGE = "NoChange"; // iOS only.
  public static final String ACTION_REMOVED_ACCOUNT = "RemovedAccount";
  public static final String ACTION_ERROR = "Error";
  public static final String ACTION_ACCOUNT_TYPE = "AccountType";
  public static final String ACTION_PERMISSION_DENIED = "PermissionDenied";
  public static final String ACTION_SYNC_EXISTING_ACCOUNT = "SyncExistingAccount";
  public static final String ACTION_SYNC_FAILED = "SyncFailed";
  public static final String ACTION_SYNC_FAILED_USER_RATE_LIMIT_EXCEEDED =
      "SyncFailedUserRateLimitExceeded";
  public static final String ACTION_IS_ACCOUNT_PERMITTED = "IsAccountPermitted"; // Android only.
  public static final String ACTION_CLAIM_ALL = "ClaimAll";
  public static final String ACTION_DELETE_ALL = "DeleteAll";
  public static final String ACTION_SELECT_LATER = "SelectLater";
  public static final String ACTION_CLAIM_SINGLE = "ClaimSingle";
  public static final String ACTION_DELETE_SINGLE = "DeleteSingle";
  public static final String ACTION_SHARE = "Share";
  public static final String ACTION_VIEW_EXPERIMENT = "ViewExperiment";
  public static final String ACTION_VIEW_TRIAL = "ViewTrial";
  public static final String ACTION_VIEW_TRIAL_NOTE = "ViewTrialNote"; // iOS only.
  public static final String ACTION_VIEW_NOTE = "ViewNote"; // iOS only.
  public static final String ACTION_REMOVE_COVER_IMAGE_FOR_EXPERIMENT =
      "RemoveCoverImageForExperiment";
  public static final String ACTION_DELETE_TRIAL = "DeleteTrial";
  public static final String ACTION_DELETE_TRIAL_NOTE = "DeleteTrialNote";
  public static final String ACTION_DELETE_NOTE = "DeleteNote";
  public static final String ACTION_SYNC_EXPERIMENT_FROM_DRIVE = "SyncExperimentFromDrive";
  public static final String ACTION_MANUAL_SYNC_STARTED = "ManualSyncStarted";
  public static final String ACTION_PERMISSION_REQUEST_FAILED = "PermissionRequestFailed";
  public static final String ACTION_MISSING_REMOTE_FILE_ID = "MissingRemoteFileId";
  public static final String ACTION_EXPORT_TRIAL = "ExportTrial";
  public static final String ACTION_EXPORT_EXPERIMENT = "ExportExperiment";
  public static final String ACTION_IMPORT_EXPERIMENT = "ImportExperiment";
  public static final String ACTION_CLEAN_OLD_EXPORT_FILES = "CleanOldExportFiles";
  public static final String ACTION_SYNC_EXPERIMENT_LIBRARY_FILE = "SyncExperimentLibraryFile";
  public static final String ACTION_SYNC_EXPERIMENT_PROTO_FILE = "SyncExperimentProtoFile";

  // Labels
  public static final String LABEL_RECORD = "record";
  public static final String LABEL_RUN_REVIEW = "run_review";
  public static final String LABEL_OBSERVE = "observe";
  public static final String LABEL_EXPERIMENT_DETAIL = "experiment_detail";
  public static final String LABEL_EXPERIMENT_LIST = "experiment_list";
  public static final String LABEL_UPDATE_EXPERIMENT = "update_experiment";
  public static final String LABEL_MODE_SIGNED_IN = "signedin";
  public static final String LABEL_MODE_SIGNED_OUT = "signedout";
  public static final String LABEL_PICTURE_DETAIL = "picture_detail";
  public static final String LABEL_USER_RECOVERABLE_AUTH_EXCEPTION = "UserRecoverableAuthException";
  public static final String LABEL_ACCOUNT_TYPE_OTHER = "Other";
  public static final String LABEL_ACCOUNT_TYPE_GMAIL = "Gmail";
  public static final String LABEL_ACCOUNT_TYPE_GSUITE = "GSuite";
  public static final String LABEL_ACCOUNT_TYPE_GOOGLE_CORP = "GoogleCorp";
  public static final String LABEL_ACCOUNT_TYPE_UNKNOWN = "Unknown";
  public static final String LABEL_PERMISSION_RESPONSE_RECEIVED_PERMITTED =
      "PermissionResponseReceived_Permitted";
  public static final String LABEL_PERMISSION_RESPONSE_RECEIVED_NOT_PERMITTED =
      "PermissionResponseReceived_NotPermitted";
  public static final String LABEL_PERMISSION_REQUEST_FAILED = "PermissionRequestFailed";
  public static final String LABEL_CACHED_PERMISSION_PERMITTED = "CachedPermission_Permitted";
  public static final String LABEL_CACHED_PERMISSION_NOT_PERMITTED =
      "CachedPermission_NotPermitted";

  // Values
  private static final long VALUE_TYPE_TEXT = 0;
  private static final long VALUE_TYPE_PICTURE = 1;
  private static final long VALUE_TYPE_SENSOR_TRIGGER = 2;
  private static final long VALUE_TYPE_SNAPSHOT = 3;

  public static final long VALUE_ACCOUNT_TYPE_OTHER = 0;
  public static final long VALUE_ACCOUNT_TYPE_GMAIL = 1;
  public static final long VALUE_ACCOUNT_TYPE_GSUITE = 2;
  public static final long VALUE_ACCOUNT_TYPE_GOOGLE_CORP = 3;
  public static final long VALUE_ACCOUNT_TYPE_UNKNOWN = 4;

  // Primes Event Names
  public static final String PRIMES_OBSERVE = "OBSERVE";
  public static final String PRIMES_EXPERIMENT_LOADED = "EXPERIMENT_LOADED";
  public static final String PRIMES_EXPERIMENT_LIST_LOADED = "EXPERIMENT_LIST_LOADED";
  public static final String PRIMES_RUN_LOADED = "RUN_LOADED";
  public static final String PRIMES_DEFAULT_EXPERIMENT_CREATED = "DEFAULT_EXPERIMENT_CREATED";

  // For Google analytics, the maximum length for a field value is 8192. However, the maximum
  // length for the whole payload is also 8192 and hits with larger payloads are discarded!
  // Here we limit a label created from an exception stack trace to 512, with the hope that the
  // whole payload will not exceed the payload limit.
  @VisibleForTesting public static final int MAX_LABEL_LENGTH = 512;

  private TrackerConstants() {}

  /** Gets the logging type for a label. */
  // TODO: Add tracking for snapshot labels.
  public static long getLabelValueType(Label label) {
    if (label.getType() == GoosciLabel.Label.ValueType.PICTURE) {
      return TrackerConstants.VALUE_TYPE_PICTURE;
    } else if (label.getType() == GoosciLabel.Label.ValueType.TEXT) {
      return TrackerConstants.VALUE_TYPE_TEXT;
    } else if (label.getType() == GoosciLabel.Label.ValueType.SENSOR_TRIGGER) {
      return TrackerConstants.VALUE_TYPE_SENSOR_TRIGGER;
    } else if (label.getType() == GoosciLabel.Label.ValueType.SNAPSHOT) {
      return TrackerConstants.VALUE_TYPE_SNAPSHOT;
    } else {
      throw new IllegalArgumentException("Label type is not supported for logging.");
    }
  }

  public static String createLabelFromStackTrace(Throwable t) {
    String s = Throwables.getStackTraceAsString(t);
    s = s.replace("\n\t", " ");
    if (s.length() > MAX_LABEL_LENGTH) {
      s = s.substring(0, MAX_LABEL_LENGTH);
    }
    return s;
  }
}
