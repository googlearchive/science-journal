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

import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import androidx.annotation.VisibleForTesting;
import androidx.core.content.FileProvider;
import android.util.Log;
import com.google.android.apps.forscience.javalib.MaybeConsumer;
import com.google.android.apps.forscience.javalib.Success;
import com.google.android.apps.forscience.whistlepunk.AppSingleton;
import com.google.android.apps.forscience.whistlepunk.Clock;
import com.google.android.apps.forscience.whistlepunk.ColorAllocator;
import com.google.android.apps.forscience.whistlepunk.DataController;
import com.google.android.apps.forscience.whistlepunk.ExportService;
import com.google.android.apps.forscience.whistlepunk.PermissionUtils;
import com.google.android.apps.forscience.whistlepunk.ProtoUtils;
import com.google.android.apps.forscience.whistlepunk.R;
import com.google.android.apps.forscience.whistlepunk.WhistlePunkApplication;
import com.google.android.apps.forscience.whistlepunk.accounts.AppAccount;
import com.google.android.apps.forscience.whistlepunk.data.nano.GoosciDeviceSpec;
import com.google.android.apps.forscience.whistlepunk.data.nano.GoosciExperimentLibrary;
import com.google.android.apps.forscience.whistlepunk.data.nano.GoosciGadgetInfo;
import com.google.android.apps.forscience.whistlepunk.data.nano.GoosciLocalSyncStatus;
import com.google.android.apps.forscience.whistlepunk.metadata.nano.GoosciExperiment;
import com.google.android.apps.forscience.whistlepunk.metadata.nano.GoosciScalarSensorData;
import com.google.android.apps.forscience.whistlepunk.metadata.nano.GoosciUserMetadata;
import com.google.android.apps.forscience.whistlepunk.metadata.nano.Version;
import com.google.android.apps.forscience.whistlepunk.sensorapi.ScalarSensorDumpReader;
import com.google.protobuf.nano.MessageNano;
import io.reactivex.Single;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/** MetadataManager backed by a file-based system using internal storage. */
// TODO: Extend MetadataManager
public class FileMetadataManager {
  public static final String COVER_IMAGE_FILE = "assets/ExperimentCoverImage.jpg";
  static final String ASSETS_DIRECTORY = "assets";
  public static final String EXPERIMENTS_DIRECTORY = "experiments";
  public static final String EXPERIMENT_FILE = "experiment.proto";
  public static final String EXPERIMENT_LIBRARY_FILE = "experiment_library.proto";
  public static final String SYNC_STATUS_FILE = "sync_status.proto";
  private static final String TAG = "FileMetadataManager";
  private static final String USER_METADATA_FILE = "user_metadata.proto";
  public static final String DOT_PROTO = ".proto";

  private AppAccount appAccount;
  private Clock clock;

  private ExperimentCache activeExperimentCache;
  private UserMetadataManager userMetadataManager;
  private final LocalSyncManager localSyncManager;
  private final ExperimentLibraryManager experimentLibraryManager;
  private ColorAllocator colorAllocator;

  public FileMetadataManager(Context applicationContext, AppAccount appAccount, Clock clock) {
    this.appAccount = appAccount;
    this.clock = clock;
    // TODO: Probably pass failure listeners from a higher level in order to propagate them
    // up to the user. b/62373187.
    ExperimentCache.FailureListener failureListener =
        new ExperimentCache.FailureListener() {
          @Override
          public void onWriteFailed(Experiment experimentToWrite) {
            // TODO: Propagate this up to the user somehow.
            Log.d(TAG, "write failed");
          }

          @Override
          public void onReadFailed(GoosciUserMetadata.ExperimentOverview experimentOverview) {
            // TODO: Propagate this up to the user somehow.
            Log.d(TAG, "read failed");
          }

          @Override
          public void onNewerVersionDetected(
              GoosciUserMetadata.ExperimentOverview experimentOverview) {
            // TODO: Propagate this up to the user somehow.
            Log.d(TAG, "newer proto version detected than we can handle");
          }
        };
    UserMetadataManager.FailureListener userMetadataListener =
        new UserMetadataManager.FailureListener() {

          @Override
          public void onWriteFailed() {
            // TODO: Propagate this up to the user somehow.
            Log.d(TAG, "write failed");
          }

          @Override
          public void onReadFailed() {
            // TODO: Propagate this up to the user somehow.
            Log.d(TAG, "read failed");
          }

          @Override
          public void onNewerVersionDetected() {
            // TODO: Propagate this up to the user somehow.
            Log.d(TAG, "newer proto version detected than we can handle");
          }
        };
    activeExperimentCache = new ExperimentCache(applicationContext, appAccount, failureListener);
    userMetadataManager =
        new UserMetadataManager(applicationContext, appAccount, userMetadataListener);
    localSyncManager = AppSingleton.getInstance(applicationContext).getLocalSyncManager(appAccount);
    experimentLibraryManager =
        AppSingleton.getInstance(applicationContext).getExperimentLibraryManager(appAccount);
    colorAllocator =
        new ColorAllocator(
            applicationContext.getResources().getIntArray(R.array.experiment_colors_array).length);
  }

  /**
   * Returns the files directory for the given account. This method should be used instead of
   * context.getFilesDir().
   */
  public static File getFilesDir(AppAccount appAccount) {
    return appAccount.getFilesDir();
  }

  public static File getUserMetadataFile(AppAccount appAccount) {
    return new File(getFilesDir(appAccount), USER_METADATA_FILE);
  }

  public static File getExperimentLibraryFile(AppAccount appAccount) {
    return new File(getFilesDir(appAccount), EXPERIMENT_LIBRARY_FILE);
  }

  public static File getLocalSyncStatusFile(AppAccount appAccount) {
    return new File(getFilesDir(appAccount), SYNC_STATUS_FILE);
  }

  public static File getAssetsDirectory(AppAccount appAccount, String experimentId) {
    return new File(getExperimentDirectory(appAccount, experimentId), ASSETS_DIRECTORY);
  }

  public static File getExperimentDirectory(AppAccount appAccount, String experimentId) {
    return new File(getExperimentsRootDirectory(appAccount), experimentId);
  }

  public static File getExperimentsRootDirectory(AppAccount appAccount) {
    return new File(getFilesDir(appAccount), EXPERIMENTS_DIRECTORY);
  }

  public static File getExternalExperimentsDirectory(Context context) {
    return context.getExternalFilesDir(null);
  }

  /**
   * Gets the relative path to a file within an experiment. For example, if the file is a picture
   * pic.png in the assets/ directory of experiment xyz, this will return just "assets/pic.png". If
   * the file is not in xyz but the experimentId passed in is xyz, this will return an empty string.
   */
  public static String getRelativePathInExperiment(String experimentId, File file) {
    String absolutePath = file.getAbsolutePath();
    int start = absolutePath.indexOf(experimentId);
    if (start < 0) {
      // This file is not part of this experiment.
      return "";
    } else {
      return absolutePath.substring(start + experimentId.length() + 1);
    }
  }

  public static String getExperimentExportDirectory(AppAccount appAccount) throws IOException {
    File dir = new File(getFilesDir(appAccount), "exported_experiments");
    if (!dir.exists() && !dir.mkdir()) {
      throw new IOException("Can't create experiments directory");
    }
    return dir.toString();
  }

  /** Gets a file in an experiment from a relative path to that file within the experiment. */
  public static File getExperimentFile(
      AppAccount appAccount, String experimentId, String relativePath) {
    return new File(getExperimentDirectory(appAccount, experimentId), relativePath);
  }

  /**
   * Gets the relative path to the file from the accounts's files directory. This can be used to
   * create the imagePath in UserMetadata.ExperimentOverview.
   */
  public static File getRelativePathInFilesDir(String experimentId, String relativePath) {
    return new File(new File(EXPERIMENTS_DIRECTORY, experimentId), relativePath);
  }

  /**
   * Immediately saves the file to be sure the in-storage protos are consistent with memory, and
   * starts the Export Service to produce an SJ file.
   */
  public static Single<File> getFileForExport(
      Context context, AppAccount appAccount, Experiment experiment, DataController dc) {
    return Single.create(
        s -> {
          dc.saveImmediately(
              new MaybeConsumer<Success>() {
                @Override
                public void success(Success result) {
                  String sensorProtoFileName =
                      getExperimentDirectory(appAccount, experiment.getExperimentId())
                          + "/sensorData.proto";

                  File zipFile;
                  String experimentName = experiment.getTitle();
                  if (experimentName.isEmpty()) {
                    experimentName =
                        context.getResources().getString(R.string.default_experiment_name);
                  }
                  try {
                    zipFile =
                        new File(
                            getExperimentExportDirectory(appAccount),
                            ExportService.makeSJExportFilename(experimentName));
                  } catch (IOException ioException) {
                    s.onError(ioException);
                    return;
                  }

                  dc.getScalarReadingProtosInBackground(
                      experiment.getExperimentProto(),
                      new MaybeConsumer<GoosciScalarSensorData.ScalarSensorData>() {
                        @Override
                        public void success(GoosciScalarSensorData.ScalarSensorData sensorData) {
                          try (FileOutputStream sensorStream =
                              new FileOutputStream(sensorProtoFileName)) {
                            byte[] sensorBytes = ProtoUtils.makeBlob(sensorData);
                            sensorStream.write(sensorBytes);

                          } catch (IOException ioException) {
                            s.onError(ioException);
                            return;
                          }

                          try (FileOutputStream fos = new FileOutputStream(zipFile);
                              ZipOutputStream zos = new ZipOutputStream(fos);) {
                            File experimentDirectory =
                                getExperimentDirectory(appAccount, experiment.getExperimentId());
                            zipDirectory(experimentDirectory, zos, "");

                            if (!experiment.getExperimentOverview().imagePath.isEmpty()) {
                              File experimentImage =
                                  new File(
                                      getFilesDir(appAccount),
                                      experiment.getExperimentOverview().imagePath);
                              zipExperimentImage(experimentImage, zos);
                            }
                          } catch (IOException ioException) {
                            s.onError(ioException);
                            return;
                          }
                          s.onSuccess(zipFile);
                        }

                        @Override
                        public void fail(Exception e) {
                          s.onError(e);
                          return;
                        }
                      });
                }

                @Override
                public void fail(Exception e) {
                  s.onError(e);
                }
              });
        });
  }

  public static void zipDirectory(File directory, ZipOutputStream zipOutputStream, String path)
      throws IOException {
    File[] fileList = directory.listFiles();
    for (File f : fileList) {
      if (f.isDirectory()) {
        zipDirectory(f, zipOutputStream, path + f.getName() + "/");
        continue;
      }
      FileInputStream fis = new FileInputStream(f.getAbsolutePath());
      String zipPath = path + f.getName();
      if (!zipPath.equals(COVER_IMAGE_FILE)) {
        ZipEntry zipEntry = new ZipEntry(zipPath);
        zipOutputStream.putNextEntry(zipEntry);

        byte[] bytes = new byte[1024];
        int length;
        while ((length = fis.read(bytes)) >= 0) {
          zipOutputStream.write(bytes, 0, length);
        }
        zipOutputStream.closeEntry();
      }

      fis.close();
    }
  }

  public static void zipExperimentImage(File image, ZipOutputStream zipOutputStream)
      throws IOException {
    FileInputStream fis = new FileInputStream(image.getAbsolutePath());
    ZipEntry zipEntry = new ZipEntry(COVER_IMAGE_FILE);

    try {
      zipOutputStream.putNextEntry(zipEntry);

      byte[] bytes = new byte[1024];
      int length;
      while ((length = fis.read(bytes)) >= 0) {
        zipOutputStream.write(bytes, 0, length);
      }
    } catch (ZipException zipException) {
      // Already zipped the cover image, because the image name was COVER_IMAGE_FILE.
      // This is ok.
      Log.d(TAG, "Trying to zip the cover again.", zipException);
    }

    zipOutputStream.closeEntry();
    fis.close();
  }

  public static boolean validateShareIntent(
      Context context, AppAccount appAccount, String experimentId) {
    // Get a low-cost known-good file to test if anything can handle our intent.
    // This won't be used for the actual intent.
    Uri experimentProto =
        FileProvider.getUriForFile(
            context,
            context.getPackageName(),
            FileMetadataManager.getExperimentFile(appAccount, experimentId, "experiment.proto"));
    return FileMetadataManager.getShareIntent(context, appAccount, experimentProto) != null;
  }

  public static Intent getShareIntent(Context context, AppAccount appAccount, Uri exportFile) {
    if (!ExportService.canShare(context, appAccount)) {
      return null;
    }
    Intent shareIntent = new Intent(Intent.ACTION_SEND);
    shareIntent.setType("application/x-zip");
    shareIntent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
    shareIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    shareIntent.putExtra(Intent.EXTRA_STREAM, exportFile);

    PackageManager packageManager = context.getPackageManager();

    // Don't worry, this is fast.
    List activities =
        packageManager.queryIntentActivities(shareIntent, PackageManager.MATCH_DEFAULT_ONLY);

    if (activities.size() > 0) {
      return shareIntent;
    }
    return null;
  }

  public static Intent createPhotoShareIntent(
      Context context,
      AppAccount appAccount,
      String experimentId,
      String imageName,
      String imageCaption) {

    if (!ExportService.canShare(context, appAccount)) {
      return null;
    }

    Intent shareIntent = new Intent(Intent.ACTION_SEND);
    shareIntent.setType("image/*");
    File imageFile =
        new File(FileMetadataManager.getExperimentDirectory(appAccount, experimentId), imageName);
    Uri uri = FileProvider.getUriForFile(context, context.getPackageName(), imageFile);

    shareIntent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
    shareIntent.putExtra(Intent.EXTRA_STREAM, uri);
    shareIntent.putExtra(Intent.EXTRA_TEXT, imageCaption);

    PackageManager packageManager = context.getPackageManager();
    List activities =
        packageManager.queryIntentActivities(shareIntent, PackageManager.MATCH_DEFAULT_ONLY);

    if (activities.size() > 0) {
      return shareIntent;
    }
    return null;
  }

  /**
   * Deletes all experiments in the list of experiment IDs. This really deletes everything and
   * should be used very sparingly!
   */
  public void deleteAll(List<String> experimentIds) {
    for (String experimentId : experimentIds) {
      deleteExperiment(experimentId);
    }
  }

  public Experiment getExperimentById(String experimentId) {
    GoosciUserMetadata.ExperimentOverview overview =
        userMetadataManager.getExperimentOverview(experimentId);
    if (overview == null) {
      return null;
    }
    return activeExperimentCache.getExperiment(overview);
  }

  public Experiment newExperiment() {
    long timestamp = clock.getNow();
    String localExperimentId = UUID.randomUUID().toString();
    List<GoosciUserMetadata.ExperimentOverview> overviews =
        userMetadataManager.getExperimentOverviews(true);
    int[] usedColors = new int[overviews.size()];
    for (int i = 0; i < overviews.size(); i++) {
      usedColors[i] = overviews.get(i).colorIndex;
    }
    int colorIndex = colorAllocator.getNextColor(usedColors);
    Experiment experiment = Experiment.newExperiment(timestamp, localExperimentId, colorIndex);
    localSyncManager.addExperiment(experiment.getExperimentId());
    experimentLibraryManager.addExperiment(experiment.getExperimentId());
    addExperiment(experiment);
    return experiment;
  }

  // Adds an existing experiment to the file system (rather than creating a new one).
  // This should just be used for data migration and testing.
  public void addExperiment(Experiment experiment) {
    // Get ready to write the experiment to a file. Will write when the timer expires.
    activeExperimentCache.createNewExperiment(experiment);
    userMetadataManager.addExperimentOverview(experiment.getExperimentOverview());
    localSyncManager.addExperiment(experiment.getExperimentId());
    experimentLibraryManager.addExperiment(experiment.getExperimentId());
  }

  public void saveImmediately() {
    activeExperimentCache.saveImmediately();
    userMetadataManager.saveImmediately();
  }

  public void deleteExperiment(Experiment experiment) {
    activeExperimentCache.prepareExperimentForDeletion(experiment);
    deleteExperiment(experiment.getExperimentId());
  }

  private void deleteExperiment(String experimentId) {
    activeExperimentCache.deleteExperiment(experimentId);
    userMetadataManager.deleteExperimentOverview(experimentId);
    experimentLibraryManager.setDeleted(experimentId, true);
  }

  public void beforeMovingExperimentToAnotherAccount(Experiment experiment) {
    // This FileMetadataManager is losing the experiment.
    activeExperimentCache.beforeMovingExperimentToAnotherAccount(experiment.getExperimentId());
    userMetadataManager.deleteExperimentOverview(experiment.getExperimentId());
    experimentLibraryManager.setDeleted(experiment.getExperimentId(), true);
  }

  public void afterMovingExperimentFromAnotherAccount(Experiment experiment) {
    // This FileMetadataManager is gaining the experiment.
    userMetadataManager.addExperimentOverview(experiment.getExperimentOverview());
    localSyncManager.addExperiment(experiment.getExperimentId());
    experimentLibraryManager.addExperiment(experiment.getExperimentId());
    experimentLibraryManager.setModified(
        experiment.getExperimentId(), experiment.getLastUsedTime());
    experimentLibraryManager.setOpened(experiment.getExperimentId(), experiment.getLastUsedTime());
  }

  public void updateExperiment(Experiment experiment) {
    activeExperimentCache.updateExperiment(experiment);

    // TODO: Only do this if strictly necessary, instead of every time?
    // Or does updateExperiment mean the last updated time should change, and we need a clock?
    userMetadataManager.updateExperimentOverview(experiment.getExperimentOverview());
  }

  public List<GoosciUserMetadata.ExperimentOverview> getExperimentOverviews(
      boolean includeArchived) {
    return userMetadataManager.getExperimentOverviews(includeArchived);
  }

  public Experiment getLastUsedUnarchivedExperiment() {
    List<GoosciUserMetadata.ExperimentOverview> overviews = getExperimentOverviews(false);
    long mostRecent = Long.MIN_VALUE;
    GoosciUserMetadata.ExperimentOverview overviewToGet = null;
    for (GoosciUserMetadata.ExperimentOverview overview : overviews) {
      if (overview.lastUsedTimeMs > mostRecent) {
        mostRecent = overview.lastUsedTimeMs;
        overviewToGet = overview;
      }
    }
    if (overviewToGet != null) {
      return activeExperimentCache.getExperiment(overviewToGet);
    }
    return null;
  }

  /**
   * Sets the most recently used experiment. This should only be used in testing -- the experiment
   * object should not actually be modified by FileMetadataManager.
   */
  @Deprecated
  public void setLastUsedExperiment(Experiment experiment) {
    long timestamp = clock.getNow();
    experiment.setLastUsedTime(timestamp);
    activeExperimentCache.onExperimentOverviewUpdated(experiment.getExperimentOverview());
    userMetadataManager.updateExperimentOverview(experiment.getExperimentOverview());
  }

  public void close() {
    saveImmediately();
  }

  /**
   * Imports an experiment from a ZIP file at the given URI, with the permissions of the Activity.
   */
  public Experiment importExperiment(Context context, Uri data, ContentResolver resolver)
      throws IOException {
    String experimentId = null;
    Context appContext = context.getApplicationContext();
    Experiment newExperiment = null;
    File externalPath = null;
    boolean containsExperimentImage;
    try {
      newExperiment = newExperiment();
      experimentId = newExperiment.getExperimentId();
      File externalFilesDir = getExternalExperimentsDirectory(context);
      externalPath = new File(externalFilesDir, experimentId);
      File internalPath = getExperimentDirectory(appAccount, experimentId);
      // Blocking get is ok as this is already on a background thread.
      containsExperimentImage =
          unzipExperimentFile(appContext, data, resolver, externalPath, internalPath).blockingGet();
    } catch (Exception e) {
      deleteExperiment(experimentId);
      throw e;
    }

    GoosciExperiment.Experiment proto = populateExperimentProto(context, externalPath);
    if (proto == null) {
      deleteExperiment(experimentId);
      throw new ZipException("Corrupt or Missing Experiment Proto");
    }
    if (!canImportFromVersion(proto.fileVersion)) {
      deleteExperiment(experimentId);
      // TODO: better error message
      throw new ZipException("Cannot import from file version: " + versionToString(proto));
    }

    GoosciUserMetadata.ExperimentOverview overview = populateOverview(proto, experimentId);
    HashMap<String, String> trialIdMap = updateTrials(proto, newExperiment);

    updateLabels(proto, newExperiment);
    newExperiment.setTitle(proto.title);
    newExperiment.setLastUsedTime(clock.getNow());
    if (containsExperimentImage) {
      overview.imagePath = EXPERIMENTS_DIRECTORY + "/" + experimentId + "/" + COVER_IMAGE_FILE;
    }
    updateExperiment(Experiment.fromExperiment(proto, overview));
    File dataFile = new File(externalPath, "sensorData.proto");

    if (dataFile.exists()) {
      ProtoFileHelper<GoosciScalarSensorData.ScalarSensorData> dataProtoFileHelper =
          new ProtoFileHelper<>();
      GoosciScalarSensorData.ScalarSensorData dataProto =
          dataProtoFileHelper.readFromFile(
              dataFile,
              GoosciScalarSensorData.ScalarSensorData::parseFrom,
              WhistlePunkApplication.getUsageTracker(context));

      if (dataProto != null) {
        ScalarSensorDumpReader dumpReader =
            new ScalarSensorDumpReader(
                AppSingleton.getInstance(context)
                    .getSensorEnvironment()
                    .getDataController(appAccount));
        dumpReader.readData(dataProto, trialIdMap);
      }
    }

    return newExperiment;
  }

  private String versionToString(GoosciExperiment.Experiment proto) {
    Version.FileVersion fileVersion = proto.fileVersion;
    return fileVersion.version
        + "."
        + fileVersion.minorVersion
        + "."
        + fileVersion.platform
        + "."
        + fileVersion.platformVersion;
  }

  @VisibleForTesting
  public static boolean canImportFromVersion(Version.FileVersion fileVersion) {
    switch (fileVersion.platform) {
      case GoosciGadgetInfo.GadgetInfo.Platform.ANDROID:
        return fileVersion.version == 1 && fileVersion.minorVersion <= 2;
      case GoosciGadgetInfo.GadgetInfo.Platform.IOS:
        if (fileVersion.version != 1) {
          return false;
        }
        if (fileVersion.minorVersion == 1) {
          return fileVersion.platformVersion >= 3;
        }
        return fileVersion.minorVersion == 2;
    }

    // Not IOS or Android?  Did we finally release on Commodore 64?  Well, as long as it's
    // using a released file version.
    return fileVersion.version == 1 && fileVersion.minorVersion == 2;
  }

  private Single<Boolean> unzipExperimentFile(
      Context context, Uri data, ContentResolver resolver, File externalPath, File internalPath)
      throws IOException {
    boolean containsExperimentImage = false;
    if (!externalPath.exists() && !externalPath.mkdir()) {
      throw new IOException("Couldn't create external experiment directory");
    }
    if (!internalPath.exists() && !internalPath.mkdir()) {
      throw new IOException("Couldn't create internal experiment directory");
    }
    File assetsDirectory = new File(internalPath, "assets");
    if (!assetsDirectory.exists() && !assetsDirectory.mkdir()) {
      throw new IOException("Couldn't create assets directory");
    }

    return Single.create(
        s -> {
          AppSingleton.getInstance(context)
              .onNextActivity()
              .subscribe(
                  activity -> {
                    PermissionUtils.tryRequestingPermission(
                        activity,
                        PermissionUtils.REQUEST_READ_EXTERNAL_STORAGE,
                        new PermissionUtils.PermissionListener() {
                          @Override
                          public void onPermissionGranted() {
                            Boolean containsImage = false;
                            try {
                              ZipInputStream zis =
                                  new ZipInputStream(resolver.openInputStream(data));
                              ZipEntry entry = zis.getNextEntry();
                              byte[] buffer = new byte[1024];

                              while (entry != null) {
                                String fileName = entry.getName();
                                if (fileName.equals("experiment.proto")
                                    || fileName.equals("sensorData.proto")) {
                                  FileOutputStream fos =
                                      new FileOutputStream(new File(externalPath, fileName));
                                  readZipInputStream(zis, buffer, fos);
                                } else if (fileName.matches(".*jpg")) {
                                  if (fileName.matches(COVER_IMAGE_FILE)) {
                                    containsImage = true;
                                  }
                                  FileOutputStream fos =
                                      new FileOutputStream(new File(internalPath, fileName));
                                  readZipInputStream(zis, buffer, fos);
                                }

                                entry = zis.getNextEntry();
                              }
                              zis.close();
                              s.onSuccess(containsImage);
                            } catch (Exception e) {
                              s.onError(e);
                            }
                          }

                          @Override
                          public void onPermissionDenied() {
                            s.onError(new IOException("Permission Denied"));
                          }

                          @Override
                          public void onPermissionPermanentlyDenied() {
                            s.onError(new IOException("Permission Denied"));
                          }
                        });
                  });
        });
  }

  private void readZipInputStream(ZipInputStream zis, byte[] buffer, FileOutputStream fos)
      throws IOException {
    int len;
    while ((len = zis.read(buffer)) > 0) {
      fos.write(buffer, 0, len);
    }
    fos.close();
  }

  private GoosciExperiment.Experiment populateExperimentProto(
      Context context, File experimentPath) {
    File experimentFile = new File(experimentPath, "experiment.proto");
    if (!experimentFile.exists()) {
      return null;
    }

    ProtoFileHelper<GoosciExperiment.Experiment> experimentProtoFileHelper =
        new ProtoFileHelper<>();
    GoosciExperiment.Experiment proto =
        experimentProtoFileHelper.readFromFile(
            experimentFile,
            GoosciExperiment.Experiment::parseFrom,
            WhistlePunkApplication.getUsageTracker(context));

    return proto;
  }

  private GoosciUserMetadata.ExperimentOverview populateOverview(
      GoosciExperiment.Experiment proto, String experimentId) {
    GoosciUserMetadata.ExperimentOverview overview = new GoosciUserMetadata.ExperimentOverview();
    overview.title = proto.title;
    overview.trialCount = proto.totalTrials;
    overview.lastUsedTimeMs = clock.getNow();
    overview.experimentId = experimentId;

    return overview;
  }

  private HashMap<String, String> updateTrials(
      GoosciExperiment.Experiment proto, Experiment newExperiment) {
    HashMap<String, String> trialIdMap = new HashMap<>();
    for (int i = 0; i < proto.trials.length; i++) {
      String oldId = proto.trials[i].trialId;
      Trial t = Trial.fromTrialWithNewId(proto.trials[i]);
      newExperiment.addTrial(t);
      proto.trials[i] = t.getTrialProto();
      trialIdMap.put(oldId, proto.trials[i].trialId);
    }
    return trialIdMap;
  }

  private void updateLabels(GoosciExperiment.Experiment proto, Experiment newExperiment) {
    for (int i = 0; i < proto.labels.length; i++) {
      Label label = Label.fromLabel(proto.labels[i]);
      newExperiment.addLabel(newExperiment, label);
    }
  }

  public void addMyDevice(GoosciDeviceSpec.DeviceSpec device) {
    userMetadataManager.addMyDevice(device);
  }

  public void removeMyDevice(GoosciDeviceSpec.DeviceSpec device) {
    userMetadataManager.removeMyDevice(device);
  }

  public List<GoosciDeviceSpec.DeviceSpec> getMyDevices() {
    return userMetadataManager.getMyDevices();
  }

  private static void writeProtoToFile(byte[] protoBytes, File file) throws IOException {
    try (FileOutputStream fos = new FileOutputStream(file)) {
      fos.write(protoBytes);
    }
  }

  public static void writeExperimentLibraryFile(
      GoosciExperimentLibrary.ExperimentLibrary library, AppAccount appAccount) throws IOException {
    writeProtoToFile(ProtoUtils.makeBlob(library), getExperimentLibraryFile(appAccount));
  }

  public static GoosciExperimentLibrary.ExperimentLibrary readExperimentLibraryFile(
      AppAccount appAccount) {
    GoosciExperimentLibrary.ExperimentLibrary library =
        new GoosciExperimentLibrary.ExperimentLibrary();
    File libraryFile = getExperimentLibraryFile(appAccount);
    if (libraryFile.canRead()) {
      byte[] libraryBytes = new byte[(int) libraryFile.length()];
      try (FileInputStream fis = new FileInputStream(libraryFile);
          DataInputStream dis = new DataInputStream(fis);) {
        dis.readFully(libraryBytes);
        library = MessageNano.mergeFrom(library, libraryBytes);
      } catch (Exception e) {
        Log.e(TAG, "Exception reading Experiment Library file", e);
      }
    }
    return library;
  }

  public static void writeLocalSyncStatusFile(
      GoosciLocalSyncStatus.LocalSyncStatus status, AppAccount appAccount) throws IOException {
    writeProtoToFile(ProtoUtils.makeBlob(status), getLocalSyncStatusFile(appAccount));
  }

  public static GoosciLocalSyncStatus.LocalSyncStatus readLocalSyncStatusFile(
      AppAccount appAccount) {
    GoosciLocalSyncStatus.LocalSyncStatus status = new GoosciLocalSyncStatus.LocalSyncStatus();
    File statusFile = getLocalSyncStatusFile(appAccount);
    if (statusFile.canRead()) {
      byte[] statusBytes = new byte[(int) statusFile.length()];
      try (FileInputStream fis = new FileInputStream(statusFile);
          DataInputStream dis = new DataInputStream(fis);) {
        dis.readFully(statusBytes);
        status = MessageNano.mergeFrom(status, statusBytes);
      } catch (Exception e) {
        if (Log.isLoggable(TAG, Log.ERROR)) {
          Log.e(TAG, "Exception reading Local Sync Status file", e);
        }
      }
    }
    return status;
  }

  public static String getProtoFileName(String protoId) {
    return protoId + DOT_PROTO;
  }
}
