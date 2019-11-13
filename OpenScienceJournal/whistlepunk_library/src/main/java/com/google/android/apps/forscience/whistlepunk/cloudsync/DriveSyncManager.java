package com.google.android.apps.forscience.whistlepunk.cloudsync;

import android.content.Context;
import android.util.Log;
import com.google.android.apps.forscience.whistlepunk.AppSingleton;
import com.google.android.apps.forscience.whistlepunk.DataController;
import com.google.android.apps.forscience.whistlepunk.PictureUtils;
import com.google.android.apps.forscience.whistlepunk.RecordingDataController;
import com.google.android.apps.forscience.whistlepunk.RxDataController;
import com.google.android.apps.forscience.whistlepunk.WhistlePunkApplication;
import com.google.android.apps.forscience.whistlepunk.accounts.AppAccount;
import com.google.android.apps.forscience.whistlepunk.analytics.TrackerConstants;
import com.google.android.apps.forscience.whistlepunk.data.GoosciExperimentLibrary;
import com.google.android.apps.forscience.whistlepunk.filemetadata.Experiment;
import com.google.android.apps.forscience.whistlepunk.filemetadata.ExperimentLibraryManager;
import com.google.android.apps.forscience.whistlepunk.filemetadata.ExperimentOverviewPojo;
import com.google.android.apps.forscience.whistlepunk.filemetadata.FileMetadataUtil;
import com.google.android.apps.forscience.whistlepunk.filemetadata.FileSyncCollection;
import com.google.android.apps.forscience.whistlepunk.filemetadata.Label;
import com.google.android.apps.forscience.whistlepunk.filemetadata.LocalSyncManager;
import com.google.android.apps.forscience.whistlepunk.filemetadata.Trial;
import com.google.android.apps.forscience.whistlepunk.metadata.GoosciExperiment;
import com.google.android.apps.forscience.whistlepunk.metadata.GoosciLabel;
import com.google.android.apps.forscience.whistlepunk.metadata.GoosciScalarSensorData;
import com.google.android.apps.forscience.whistlepunk.sensorapi.ScalarSensorDumpReader;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.common.base.Strings;
import com.google.common.base.Supplier;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * Manages a connection to a user's Google Drive, and provides a home for the code that interacts
 * with the Google Drive API. There is one instance of this class for each signed in user, and
 * provides a handle to their drive.
 */
public class DriveSyncManager implements CloudSyncManager {

  private static final String TAG = "DriveSyncManager";

  public static final String EXPERIMENT_LIBRARY_PROTO = "experiment_library.proto";
  public static final String EXPERIMENT_PROTO_FILE = "experiment.proto";

  private final DriveApi driveApi;
  private final AppAccount appAccount;
  private final DataController dc;
  private final RecordingDataController rdc;
  private boolean sjFolderExists = false;
  // State-holders for kicking off a second sync if one is requested during an active sync.
  private boolean syncAgain = false;
  private String lastLogMessage = "";

  DriveSyncManager(
      AppAccount appAccount,
      DataController dc,
      HttpTransport transport,
      JsonFactory jsonFactory,
      Context applicationContext,
      Supplier<DriveApi> driveSupplier,
      RecordingDataController rdc) {
    this(
        appAccount,
        dc,
        driveSupplier.get().init(transport, jsonFactory, appAccount, applicationContext),
        rdc);
  }

  public DriveSyncManager(
      AppAccount appAccount,
      DataController dc,
      DriveApi driveWrapper,
      RecordingDataController rdc) {
    this.appAccount = appAccount;
    this.driveApi = driveWrapper;
    this.dc = dc;
    this.rdc = rdc;
  }

  @Override
  public void syncExperimentLibrary(Context context, String logMessage) throws IOException {
    // If we are currently syncing, we should wait on this new sync request, and sync again when the
    // first sync completes.
    if (isCurrentlySyncing(context)) {
      syncAgain = true;
      lastLogMessage = logMessage;
      return;
    }
    if (Log.isLoggable(TAG, Log.INFO)) {
      Log.i(TAG, logMessage);
    }
    if (DriveSyncAndroidService.syncExperimentLibraryFile(context, appAccount)) {
      AppSingleton.getInstance(context).setSyncServiceBusy(true);
    }
  }

  @Override
  public void logCloudInfo(String tag) {
    Executor thread = Executors.newSingleThreadExecutor();
    thread.execute(
        new Runnable() {
          @Override
          public void run() {
            if (Log.isLoggable(tag, Log.WARN)) {
              boolean remoteExists = false;
              try {
                String remoteLibraryId = driveApi.getRemoteExperimentLibraryFileId();
                if (remoteLibraryId != null && driveApi.getFileExists(remoteLibraryId)) {
                  remoteExists = true;
                }
              } catch (IOException ioe) {
                Log.w(tag, "IO Exception checking for remote library");
              }

              boolean sjFolderExists = false;
              try {
                sjFolderExists = driveApi.sjFolderExists();
              } catch (IOException ioe) {
                Log.w(tag, "IO Exception checking for SJ Folder");
              }

              int experimentCount = 0;
              try {
                experimentCount = driveApi.countSJExperiments();
              } catch (IOException ioe) {
                Log.w(tag, "IO Exception counting experiments");
              }

              Log.w(tag, "Remote Library Exists: " + remoteExists);
              Log.w(tag, "SJ Folder Exists: " + sjFolderExists);
              Log.w(tag, "Experiment Count: " + experimentCount);
            }
          }
        });
  }

  private Boolean isCurrentlySyncing(Context context) {
    // With a default value, "blockingMostRecent" doesn't actually block. It returns the default.
    return AppSingleton.getInstance(context)
        .whenSyncBusyChanges()
        .blockingMostRecent(false)
        .iterator()
        .next();
  }

  /**
   * Called by DriveSyncAndroidService to handle Drive syncing of library in the background thread.
   * This method will likely return while background processes are still occurring.
   */
  void syncExperimentLibraryInBackgroundThread(
      Context context,
      ExperimentLibraryManager experimentLibraryManager,
      LocalSyncManager localSyncManager)
      throws IOException {
    Map<String, FileSyncCollection> fileSyncCollectionMap = new HashMap<>();
    if (!appAccount.isSignedIn()) {
      AppSingleton.getInstance(context).setSyncServiceBusy(false);
      return;
    }
    long remoteLibraryVersion = getRemoteLibraryVersion();
    long localLibraryVersion = localSyncManager.getLastSyncedLibraryVersion();
    if (remoteLibraryVersion == localLibraryVersion) {
      checkForDeletions(experimentLibraryManager);
      AppSingleton.getInstance(context).setSyncServiceBusy(false);
      syncAgain = false;
      return;
    }
    // Is the same user using multiple devices?
    if (localLibraryVersion == 0) {
      WhistlePunkApplication.getUsageTracker(context)
          .trackEvent(
              TrackerConstants.CATEGORY_SIGN_IN,
              TrackerConstants.ACTION_SYNC_EXISTING_ACCOUNT,
              null,
              0);
    }
    // Try to get the remote experiment library
    GoosciExperimentLibrary.ExperimentLibrary remoteLibrary = downloadExperimentLibraryFile();
    Map<String, Long> driveExperimentVersions = driveApi.getAllDriveExperimentVersions();
    if (remoteLibrary != null) {
      // If the remote experiment library already exists, merge it into our local library
      experimentLibraryManager.merge(remoteLibrary, localSyncManager);
    }
    String sjDirectoryId = getSJDirectoryId(experimentLibraryManager);
    experimentLibraryManager.setFolderId(sjDirectoryId);
    List<ExperimentOverviewPojo> overviews = dc.blockingGetExperimentOverviews(true);
    for (String id : experimentLibraryManager.getKnownExperiments()) {
      // For each experiment that we know about (past or current)
      ExperimentOverviewPojo matching = null;
      for (ExperimentOverviewPojo overview : overviews) {
        // For each experiment that is currently not-deleted
        if (id.equals(overview.getExperimentId())) {
          // Find if the known experiment currently exists locally
          matching = overview;
        }
      }
      if (experimentLibraryManager.isDeleted(id)) {
        // If the known experiment has been marked deleted
        if (matching != null) {
          // If it exists locally, delete it.
          if (Log.isLoggable(TAG, Log.INFO)) {
            Log.i(TAG, "Deleting locally: Marked deleted in library");
          }
          deleteExperiment(id);
          deleteExperimentRemotely(context, experimentLibraryManager, id, sjDirectoryId);
        }
        if (localSyncManager.getDirty(id)) {
          if (Log.isLoggable(TAG, Log.INFO)) {
            Log.i(TAG, "Deleting locally: Marked deleted and dirty in library");
          }
          deleteExperimentRemotely(context, experimentLibraryManager, id, sjDirectoryId);
          localSyncManager.setDirty(id, false);
        }
      } else {
        String remoteFileId = experimentLibraryManager.getFileId(id);
        long remoteDriveExperimentVersion = -1;
        if (remoteFileId != null && driveExperimentVersions.get(remoteFileId) != null) {
          remoteDriveExperimentVersion = driveExperimentVersions.get(remoteFileId);
        }
        // Else the experiment hasn't been deleted
        if (matching == null) {
          // And it doesn't exist locally, so add it, if it exists remotely!
          if (Strings.isNullOrEmpty(remoteFileId)) {
            // This happens if the ExperimentLibrary file doesn't have a file ID yet. For example
            // see bug 123845261, where an iOS device and an Android device have both been offline
            // and both come online and attempt to sync at the same time. One device could update
            // the experiment library file and before it finished uploading all the individual
            // experiments, the other device downloads the experiment library file.
            // TODO(b/135479937): Reconcile experiments in drive.
            // For now, track this to find out how often this happens in the wild.
            WhistlePunkApplication.getUsageTracker(context)
                .trackEvent(
                    TrackerConstants.CATEGORY_SYNC,
                    TrackerConstants.ACTION_MISSING_REMOTE_FILE_ID,
                    null,
                    0);
            continue;
          } else if (!driveApi.getFileExists(remoteFileId)) {
            if (Log.isLoggable(TAG, Log.INFO)) {
              Log.i(TAG, "Marking deleted: package not found and local not found");
            }
            experimentLibraryManager.setDeleted(id, true);
          } else {
            Experiment newExperiment =
                Experiment.newExperiment(
                    context,
                    appAccount,
                    experimentLibraryManager,
                    experimentLibraryManager.getModified(id),
                    id,
                    0,
                    experimentLibraryManager.getModified(id));
            fileSyncCollectionMap.put(
                id,
                syncNewRemoteExperimentProtoFileInBackgroundThread(
                    context, id, experimentLibraryManager, localSyncManager, dc, newExperiment));
          }
        } else {
          // It does exist locally. Let's sync it!
          try {
            fileSyncCollectionMap.put(id, syncExperimentProtoFileInBackgroundThread(
                context,
                id,
                remoteDriveExperimentVersion,
                experimentLibraryManager,
                localSyncManager));
          } catch (IOException ioe) {
            Log.e(TAG, "IOException", ioe);
          }
        }
      }
      AppSingleton.getInstance(context).notifyNewExperimentSynced();
    }

    transferFileSyncCollections(context, experimentLibraryManager, fileSyncCollectionMap);
    AppSingleton.getInstance(context).notifyNewExperimentSynced();
    // Now upload the library back to Drive
    remoteLibraryVersion = uploadExperimentLibraryToDrive();
    localSyncManager.setLastSyncedLibraryVersion(remoteLibraryVersion);
    AppSingleton.getInstance(context).setSyncServiceBusy(false);
    sjFolderExists = false;
    // If a sync was requested while we were busy, start another sync.
    if (syncAgain) {
      syncAgain = false;
      syncExperimentLibrary(context, lastLogMessage);
    }
    cleanUpDrive(context, experimentLibraryManager, localSyncManager, sjDirectoryId);
  }

  private long uploadExperimentLibraryToDrive() throws IOException {
    synchronized (appAccount.getLockForExperimentLibraryFile()) {
      java.io.File libraryFile = getLocalLibraryFile();
      String fileId = driveApi.getRemoteExperimentLibraryFileId();
      if (fileId == null) {
        // Either by inserting, if it didn't already exist on Drive
        driveApi.insertExperimentLibraryFile(libraryFile);
        fileId = driveApi.getRemoteExperimentLibraryFileId();
      } else {
        // Or by updating, if it did. Drive doesn't have an upsert action, which makes this clunky.
        driveApi.updateExperimentLibraryFile(libraryFile, fileId);
      }
      return driveApi.getFileVersion(fileId);
    }
  }

  private java.io.File downloadFileInBackgroundThread(
      String experimentId, String fileName, ExperimentLibraryManager elm)
      throws IOException {
    if (!appAccount.isSignedIn()) {
      return null;
    }

    if (Strings.isNullOrEmpty(fileName)) {
      return null;
    }
    String packageId = elm.getFileId(experimentId);
    java.io.File localExperimentDirectory =
        FileMetadataUtil.getInstance().getExperimentDirectory(appAccount, experimentId);
    return driveApi.downloadExperimentAsset(packageId, localExperimentDirectory, fileName);
  }

  private void uploadFileInBackgroundThread(
      String experimentId, String fileName, ExperimentLibraryManager elm)
      throws IOException {
    if (!appAccount.isSignedIn()) {
      return;
    }
    String packageId = elm.getFileId(experimentId);
    java.io.File localExperimentDirectory =
        FileMetadataUtil.getInstance().getExperimentDirectory(appAccount, experimentId);
    java.io.File localFile = new java.io.File(localExperimentDirectory, fileName);
    driveApi.uploadFile(localFile, packageId);
  }

  private void downloadTrialInBackgroundThread(
      String experimentId, String trialId, ExperimentLibraryManager elm)
      throws IOException {
    if (!appAccount.isSignedIn()) {
      return;
    }
    java.io.File localFile =
        downloadFileInBackgroundThread(
            experimentId, FileMetadataUtil.getInstance().getTrialProtoFileName(trialId), elm);
    ScalarSensorDumpReader dumpReader = new ScalarSensorDumpReader(rdc);
    HashMap<String, String> trialIdMap = new HashMap<>();
    trialIdMap.put(trialId, trialId);

    GoosciScalarSensorData.ScalarSensorData dataProto = null;
    if (localFile.canRead()) {
      try (FileInputStream fis = new FileInputStream(localFile)) {
        dataProto = GoosciScalarSensorData.ScalarSensorData.parseFrom(fis);
      } catch (Exception e) {
        Log.e(TAG, "Exception reading trial data file", e);
      }
    }

    if (dataProto == null) {
      dataProto = GoosciScalarSensorData.ScalarSensorData.getDefaultInstance();
    }

    dumpReader.readData(dataProto, trialIdMap);
  }

  private void uploadTrialInBackgroundThread(
      Context context, String experimentId, String trialId, ExperimentLibraryManager elm)
      throws IOException {
    if (!appAccount.isSignedIn()) {
      return;
    }
    java.io.File localFile =
        RxDataController.writeTrialProtoToFile(dc, experimentId, trialId).blockingGet();
    try {
      if (localFile == null) {
        throw new IOException("Trial not found");
      } else {
        // Get the remote Drive "Science Journal" folder.
        String sjDirectoryId = getSJDirectoryId(elm);
        // Get the Drive embedded package ID and metadata for the Experiment
        String packageId = getExperimentPackageId(context, elm, experimentId, sjDirectoryId);
        driveApi.uploadFile(localFile, packageId);
      }
    } catch (IOException ioe) {
      if (Log.isLoggable(TAG, Log.ERROR)) {
        Log.e(TAG, "File write failed", ioe);
      }
    }
  }

  private GoosciExperimentLibrary.ExperimentLibrary downloadExperimentLibraryFile()
      throws IOException {
    String fileId = driveApi.getRemoteExperimentLibraryFileId();
    if (fileId == null) {
      return null;
    }
    return driveApi.downloadExperimentLibraryFile(fileId);
  }

  private long getRemoteLibraryVersion() throws IOException {
    String fileId = driveApi.getRemoteExperimentLibraryFileId();
    if (fileId == null) {
      return -1;
    }
    return driveApi.getFileVersion(fileId);
  }

  private java.io.File getLocalLibraryFile() {
    return new java.io.File(appAccount.getFilesDir(), EXPERIMENT_LIBRARY_PROTO);
  }

  /**
   * Called by DriveSyncAndroidService to handle Drive syncing of experiments in the background
   * thread.
   */
  FileSyncCollection syncNewRemoteExperimentProtoFileInBackgroundThread(
      Context context,
      String experimentId,
      ExperimentLibraryManager elm,
      LocalSyncManager lsm,
      DataController dc,
      Experiment newExperiment)
      throws IOException {
    if (!appAccount.isSignedIn()) {
      return new FileSyncCollection();
    }

    getExperimentProtoFileFromRemoteInBackgroundThread(
        context, experimentId, elm, lsm, dc, newExperiment);
    WhistlePunkApplication.getUsageTracker(context)
        .trackEvent(
            TrackerConstants.CATEGORY_SYNC,
            TrackerConstants.ACTION_SYNC_EXPERIMENT_FROM_DRIVE,
            null,
            0);
    return new FileSyncCollection();
  }

  /**
   * Called by DriveSyncAndroidService to handle Drive syncing of experiments in the background
   * thread.
   */
  FileSyncCollection syncExperimentProtoFileInBackgroundThread(
      Context context,
      String experimentId,
      long remoteFileVersion,
      ExperimentLibraryManager elm,
      LocalSyncManager localSyncManager)
      throws IOException {
    if (!appAccount.isSignedIn()) {
      return new FileSyncCollection();
    }
    // Get the remote Drive "Science Journal" folder.
    String sjDirectoryId = getSJDirectoryId(elm);
    // Get the Drive embedded package ID and metadata for the Experiment
    String packageId = getExperimentPackageId(context, elm, experimentId, sjDirectoryId);

    if (getPackageFormatVersion(packageId) > 1) {
      // This is a new experiment version. We can't work with it.
      // Versioning design:
      // https://docs.google.com/document/d/1d9sImPmSW4CJHzEiabxl0bxkrJgU5jTtAKq7rjoUEkA/edit
      return new FileSyncCollection();
    }

    // Handles case where user has manually deleted the experiment package.
    if (!driveApi.getFileExists(packageId)) {
      if (Log.isLoggable(TAG, Log.INFO)) {
        Log.i(TAG, "Deleting locally: package not found");
      }
      deleteExperiment(experimentId);
      return new FileSyncCollection();
    }
    DriveFile serverExperimentProtoMetadata = null;
    if (remoteFileVersion < 0) {
      serverExperimentProtoMetadata = driveApi.getExperimentProtoMetadata(packageId);
      if (serverExperimentProtoMetadata != null) {
        remoteFileVersion = serverExperimentProtoMetadata.getVersion();
      }
    }

    // Handles cases where proto doesn't exist in remote package.
    if (remoteFileVersion < 0 && serverExperimentProtoMetadata == null) {
      if (!RxDataController.experimentExists(dc, experimentId).blockingGet()) {
        return new FileSyncCollection();
      }
      Experiment localExperiment =
          RxDataController.getExperimentById(dc, experimentId).blockingGet();
      if (localExperiment.isEmpty()) {
        return new FileSyncCollection();
      }

      if (localExperiment.getLastUsedTime() > elm.getModified(experimentId)) {
        elm.setModified(experimentId, localExperiment.getLastUsedTime());
      }
      if (localExperiment.isArchived() != elm.isArchived(experimentId)) {
        localExperiment.setArchived(context, appAccount, elm.isArchived(experimentId));
      }
      RxDataController.updateExperimentEvenIfNotActive(
              dc, localExperiment, elm.getModified(experimentId), false)
          .blockingAwait();
      localSyncManager.setServerArchived(experimentId, elm.isArchived(experimentId));
      insertExperimentProto(experimentId, packageId, localSyncManager, localExperiment.getTitle());
      for (Trial t : localExperiment.getTrials()) {
        uploadTrialInBackgroundThread(context, experimentId, t.getTrialId(), elm);
        for (Label l : t.getLabels()) {
          uploadLabelIfNecessary(l, experimentId, elm);
        }
      }

      for (Label l : localExperiment.getLabels()) {
        uploadLabelIfNecessary(l, experimentId, elm);
      }

    } else {
      // Get the remote and local versions, as well as if there have been local edits.
      long localExperimentVersion = localSyncManager.getLastSyncedVersion(experimentId);
      boolean isDirty = localSyncManager.getDirty(experimentId);
      if (!RxDataController.experimentExists(dc, experimentId).blockingGet()) {
        return new FileSyncCollection();
      }
      Experiment localExperiment =
          RxDataController.getExperimentById(dc, experimentId).blockingGet();
      localExperiment.cleanTrials(context, appAccount);
      if (localExperiment.getLastUsedTime() > elm.getModified(experimentId)) {
        elm.setModified(experimentId, localExperiment.getLastUsedTime());
      } else {
        localExperiment.setLastUsedTime(elm.getModified(experimentId));
      }
      // if there have been remote changes OR local changes, we have to merge.
      if (remoteFileVersion != localExperimentVersion
          || isDirty
          || elm.isArchived(experimentId) != localSyncManager.getServerArchived(experimentId)) {
        // Download remote file and merge with local.
        if (serverExperimentProtoMetadata == null) {
          serverExperimentProtoMetadata = driveApi.getExperimentProtoMetadata(packageId);
        }
        GoosciExperiment.Experiment remoteExperiment =
            downloadExperimentProto(serverExperimentProtoMetadata.getId());
        FileSyncCollection sync =
            RxDataController.mergeExperiment(
                    dc,
                    experimentId,
                    Experiment.fromExperiment(remoteExperiment, new ExperimentOverviewPojo()),
                    false)
                .blockingGet();
        localSyncManager.setServerArchived(experimentId, elm.isArchived(experimentId));
        localExperiment.setArchived(context, appAccount, elm.isArchived(experimentId));
        updateExperimentProto(
            experimentId,
            serverExperimentProtoMetadata,
            localSyncManager,
            packageId,
            localExperiment.getTitle());

        RxDataController.updateExperimentEvenIfNotActive(
                dc, localExperiment, elm.getModified(experimentId), false)
            .blockingAwait();
        try {
          String imagePath = localExperiment.getImagePath();
          if (Strings.isNullOrEmpty(imagePath)) {
            return sync;
          }
          java.io.File overviewImage =
              new java.io.File(
                  PictureUtils.getExperimentOverviewFullImagePath(
                      appAccount,
                      localExperiment.getPathRelativeToAccountRoot(
                          localExperiment.getImagePath())));
          if (overviewImage.exists()) {
            uploadFileInBackgroundThread(
                experimentId,
                FileMetadataUtil.getInstance()
                    .getRelativePathInExperiment(experimentId, overviewImage),
                elm);
          } else {
            downloadFileInBackgroundThread(
                experimentId,
                FileMetadataUtil.getInstance()
                    .getRelativePathInExperiment(experimentId, overviewImage),
                elm);
          }
          return sync;
        } catch (IOException ioe) {
          Log.e(TAG, "IOException", ioe);
        }
      }
    }
    return new FileSyncCollection();
  }

  /**
   * Called by DriveSyncAndroidService to handle Drive syncing of experiments in the background
   * thread.
   */
  private void getExperimentProtoFileFromRemoteInBackgroundThread(
      Context context,
      String experimentId,
      ExperimentLibraryManager elm,
      LocalSyncManager localSyncManager,
      DataController dc,
      Experiment newExperiment)
      throws IOException {
    // Get the remote Drive "Science Journal" folder.
    String sjDirectoryId = getSJDirectoryId(elm);
    // Get the Drive embedded package ID and metadata for the Experiment
    String packageId = getExperimentPackageId(context, elm, experimentId, sjDirectoryId);

    DriveFile serverExperimentProtoMetadata = driveApi.getExperimentProtoMetadata(packageId);
    if (serverExperimentProtoMetadata == null) {
      deleteExperiment(experimentId);
      return;
    }
    GoosciExperiment.Experiment remoteExperiment =
        downloadExperimentProto(serverExperimentProtoMetadata.getId());
    newExperiment.setTitle(remoteExperiment.getTitle());
    try {
      RxDataController.addExperiment(dc, newExperiment).blockingAwait();
    } catch (IllegalArgumentException e) {
      if (Log.isLoggable(TAG, Log.ERROR)) {
        Log.e(TAG, "Experiment already added to data controller", e);
      }
    }
    Experiment localExperiment = RxDataController.getExperimentById(dc, experimentId).blockingGet();
    localSyncManager.setServerArchived(experimentId, elm.isArchived(experimentId));
    localExperiment.setArchived(context, appAccount, elm.isArchived(experimentId));

    ExperimentOverviewPojo overview = new ExperimentOverviewPojo();
    overview.setExperimentId(experimentId);

    RxDataController.mergeExperiment(
            dc,
            experimentId,
            Experiment.fromExperiment(remoteExperiment, new ExperimentOverviewPojo()),
            true)
        .toCompletable()
        .blockingAwait();
    RxDataController.updateExperimentEvenIfNotActive(
            dc,
            Experiment.fromExperiment(remoteExperiment, overview),
            elm.getModified(experimentId),
            false)
        .blockingAwait();

    for (Trial t : localExperiment.getTrials()) {
      downloadTrialInBackgroundThread(experimentId, t.getTrialId(), elm);
      for (Label l : t.getLabels()) {
        downloadLabelIfNecessary(l, experimentId, elm);
      }
    }

    for (Label l : localExperiment.getLabels()) {
      downloadLabelIfNecessary(l, experimentId, elm);
    }

    try {
      String imagePath = localExperiment.getImagePath();
      if (Strings.isNullOrEmpty(imagePath)) {
        return;
      }
      java.io.File overviewImage =
          new java.io.File(
              PictureUtils.getExperimentOverviewFullImagePath(
                  appAccount,
                  PictureUtils.getExperimentOverviewRelativeImagePath(
                      experimentId, localExperiment.getImagePath())));
      if (overviewImage.exists()) {
        uploadFileInBackgroundThread(
            experimentId,
            FileMetadataUtil.getInstance().getRelativePathInExperiment(experimentId, overviewImage),
            elm);
      } else {
        downloadFileInBackgroundThread(
            experimentId,
            FileMetadataUtil.getInstance().getRelativePathInExperiment(experimentId, overviewImage),
            elm);
      }
    } catch (IOException ioe) {
      Log.e(TAG, "IOException", ioe);
    }
  }

  private int getPackageFormatVersion(String packageId) throws IOException {
    return driveApi.getPackageVersion(packageId);
  }

  private void uploadLabelIfNecessary(
      Label l, String experimentId, ExperimentLibraryManager elm) {
    if (l.getType() == GoosciLabel.Label.ValueType.PICTURE) {
      try {
        uploadFileInBackgroundThread(experimentId, l.getPictureLabelValue().getFilePath(), elm);
      } catch (IOException ioe) {
        if (Log.isLoggable(TAG, Log.ERROR)) {
          Log.e(TAG, "IOException", ioe);
        }
      }
    }
  }

  private void downloadLabelIfNecessary(
      Label l, String experimentId, ExperimentLibraryManager elm) {
    if (l.getType() == GoosciLabel.Label.ValueType.PICTURE) {
      try {
        downloadFileInBackgroundThread(experimentId, l.getPictureLabelValue().getFilePath(), elm);
      } catch (IOException ioe) {
        if (Log.isLoggable(TAG, Log.ERROR)) {
          Log.e(TAG, "IOException", ioe);
        }
      }
    }
  }

  // Gets the Science Journal folder file ID.
  private String getSJDirectoryId(ExperimentLibraryManager experimentLibraryManager)
      throws IOException {
    String folderId = experimentLibraryManager.getFolderId();
    if (Strings.isNullOrEmpty(folderId)) {
      // If the folder isn't stored in the experiment library file, create one and save it.
      String sjFolderId = driveApi.createNewSJFolder();
      experimentLibraryManager.setFolderId(sjFolderId);
    } else {
      if (!sjFolderExists && !driveApi.getFileExists(folderId)) {
        String sjFolderId = driveApi.createNewSJFolder();
        experimentLibraryManager.setFolderId(sjFolderId);
      }
      sjFolderExists = true;
    }
    return experimentLibraryManager.getFolderId();
  }

  // Download the actual experiment proto from Drive.
  private GoosciExperiment.Experiment downloadExperimentProto(String fileId) throws IOException {
    return driveApi.downloadExperimentProtoFile(fileId);
  }

  // Overwrite an existing proto on Drive
  private void updateExperimentProto(
      String experimentId,
      DriveFile serverExperimentProtoMetadata,
      LocalSyncManager localSyncManager,
      String packageId,
      String experimentTitle)
      throws IOException {
    long newVersion;
    synchronized (appAccount.getLockForExperimentProtoFile()) {
      newVersion =
          driveApi.updateExperimentProto(
              FileMetadataUtil.getInstance()
                  .getExperimentFile(appAccount, experimentId, EXPERIMENT_PROTO_FILE),
              serverExperimentProtoMetadata,
              packageId,
              experimentTitle);
    }
    localSyncManager.setDirty(experimentId, false);
    localSyncManager.setLastSyncedVersion(experimentId, newVersion);
  }

  // Create the proto for the first time. There's no Upsert functionality, so we have to create the
  // file and update the file through different REST endpoints. Shrugging emoji.
  // We'll also add the Version proto.
  private void insertExperimentProto(
      String experimentId,
      String packageId,
      LocalSyncManager localSyncManager,
      String experimentTitle)
      throws IOException {
    long newVersion;
    synchronized (appAccount.getLockForExperimentProtoFile()) {
      newVersion =
          driveApi.insertExperimentProto(
              FileMetadataUtil.getInstance()
                  .getExperimentFile(appAccount, experimentId, EXPERIMENT_PROTO_FILE),
              packageId,
              experimentTitle);
    }
    localSyncManager.setDirty(experimentId, false);
    localSyncManager.setLastSyncedVersion(experimentId, newVersion);
  }

  // Delete an experiment locally.
  private void deleteExperiment(String experimentId) {
    if (RxDataController.experimentExists(dc, experimentId).blockingGet()) {
      RxDataController.deleteExperiment(dc, experimentId).blockingAwait();
    }
  }

  // Delete an experiment remotely.
  private void deleteExperimentRemotely(
      Context context, ExperimentLibraryManager elm, String experimentId, String directoryId)
      throws IOException {
    if (hasLocalPackageId(elm, experimentId)) {
      String remoteFileId = getExperimentPackageId(context, elm, experimentId, directoryId);
      if (remoteFileId != null && driveApi.getFileExists(remoteFileId)) {
        driveApi.trashFileById(remoteFileId);
      }
    }
  }

  private boolean hasLocalPackageId(ExperimentLibraryManager elm, String experimentId) {
    String elmId = elm.getFileId(experimentId);
    return !Strings.isNullOrEmpty(elmId);
  }

  // Get the Package ID for the experiment on Drive
  private String getExperimentPackageId(
      Context context, ExperimentLibraryManager elm, String experimentId, String directoryId)
      throws IOException {

    // If we know it locally, return it.
    String elmId = elm.getFileId(experimentId);
    if (!Strings.isNullOrEmpty(elmId)) {
      return elmId;
    }

    String id = driveApi.getExperimentPackageId(context, directoryId);
    elm.setFileId(experimentId, id);
    return id;
  }

  private void cleanUpDrive(
      Context context, ExperimentLibraryManager elm, LocalSyncManager lsm, String directoryId)
      throws IOException {
    for (String id : elm.getKnownExperiments()) {
      if (elm.isDeleted(id) && lsm.getDirty(id)) {
        if (Log.isLoggable(TAG, Log.INFO)) {
          Log.i(TAG, "Deleting remotely: Marked deleted in library");
        }
        deleteExperimentRemotely(context, elm, id, directoryId);
        lsm.setDirty(id, false);
      }
    }
  }

  private void transferFileSyncCollections(Context context, ExperimentLibraryManager elm,
      Map<String, FileSyncCollection> collectionMap) {
    for (String experimentId : collectionMap.keySet()) {
      FileSyncCollection sync = collectionMap.get(experimentId);
      for (String download : sync.getImageDownloads()) {
        try {
          downloadFileInBackgroundThread(experimentId, download, elm);
        } catch (IOException ioe) {
          if (Log.isLoggable(TAG, Log.ERROR)) {
            Log.e(TAG, "IOException", ioe);
          }
        }
      }

      for (String upload : sync.getImageUploads()) {
        try {
          uploadFileInBackgroundThread(experimentId, upload, elm);
        } catch (IOException ioe) {
          if (Log.isLoggable(TAG, Log.ERROR)) {
            Log.e(TAG, "IOException", ioe);
          }
        }
      }

      for (String download : sync.getTrialDownloads()) {
        try {
          downloadTrialInBackgroundThread(experimentId, download, elm);
        } catch (IOException ioe) {
          if (Log.isLoggable(TAG, Log.ERROR)) {
            Log.e(TAG, "IOException", ioe);
          }
        }
      }

      for (String upload : sync.getTrialUploads()) {
        try {
          uploadTrialInBackgroundThread(context, experimentId, upload, elm);
        } catch (IOException ioe) {
          if (Log.isLoggable(TAG, Log.ERROR)) {
            Log.e(TAG, "IOException", ioe);
          }
        }
      }
    }
  }

  private void checkForDeletions(ExperimentLibraryManager elm) throws IOException {
    List<String> toDelete = new ArrayList<>();
    for (String experiment : elm.getKnownExperiments()) {
      String fileId = elm.getFileId(experiment);
      if (!elm.isDeleted(experiment) && !driveApi.getFileExists(fileId)) {
        toDelete.add(experiment);
      }
    }
    for (String experiment : toDelete) {
      deleteExperiment(experiment);
    }
  }
}
