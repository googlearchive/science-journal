/*
 *  Copyright 2018 Google Inc. All Rights Reserved.
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
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Environment;
import android.os.StatFs;
import androidx.annotation.VisibleForTesting;
import androidx.core.content.FileProvider;
import android.util.Log;
import com.google.android.apps.forscience.javalib.MaybeConsumer;
import com.google.android.apps.forscience.javalib.Success;
import com.google.android.apps.forscience.whistlepunk.DataController;
import com.google.android.apps.forscience.whistlepunk.ExportService;
import com.google.android.apps.forscience.whistlepunk.R;
import com.google.android.apps.forscience.whistlepunk.accounts.AppAccount;
import com.google.android.apps.forscience.whistlepunk.data.GoosciExperimentLibrary.ExperimentLibrary;
import com.google.android.apps.forscience.whistlepunk.data.GoosciLocalSyncStatus;
import com.google.android.apps.forscience.whistlepunk.metadata.GoosciScalarSensorData;
import com.google.android.apps.forscience.whistlepunk.metadata.Version;
import io.reactivex.Single;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipOutputStream;

/** Utility class for interacting with the file system * */
public class FileMetadataUtil {
  public static final String COVER_IMAGE_FILE = "assets/ExperimentCoverImage.jpg";
  static final String ASSETS_DIRECTORY = "assets";
  public static final String EXPERIMENTS_DIRECTORY = "experiments";
  public static final String EXPERIMENT_FILE = "experiment.proto";
  public static final String EXPERIMENT_LIBRARY_FILE = "experiment_library.proto";
  public static final String SYNC_STATUS_FILE = "sync_status.proto";
  private static final String TAG = "FileMetadataManager";
  private static final String USER_METADATA_FILE = "user_metadata.proto";
  public static final String DOT_PROTO = ".proto";
  private static final String RECORDING = "recording_";

  public static final FileMetadataUtil instance = new FileMetadataUtil();

  public static FileMetadataUtil getInstance() {
    return instance;
  }

  public FileMetadataUtil() {}

  /**
   * Returns the files directory for the given account. This method should be used instead of
   * context.getFilesDir().
   */
  public File getFilesDir(AppAccount appAccount) {
    return appAccount.getFilesDir();
  }

  public File getUserMetadataFile(AppAccount appAccount) {
    return new File(getFilesDir(appAccount), USER_METADATA_FILE);
  }

  public File getExperimentLibraryFile(AppAccount appAccount) {
    return new File(getFilesDir(appAccount), EXPERIMENT_LIBRARY_FILE);
  }

  public File getLocalSyncStatusFile(AppAccount appAccount) {
    return new File(getFilesDir(appAccount), SYNC_STATUS_FILE);
  }

  public File getAssetsDirectory(AppAccount appAccount, String experimentId) {
    return new File(getExperimentDirectory(appAccount, experimentId), ASSETS_DIRECTORY);
  }

  public File getExperimentDirectory(AppAccount appAccount, String experimentId) {
    return new File(getExperimentsRootDirectory(appAccount), experimentId);
  }

  public File getExperimentsRootDirectory(AppAccount appAccount) {
    return new File(getFilesDir(appAccount), EXPERIMENTS_DIRECTORY);
  }

  public File getExternalExperimentsDirectory(Context context) {
    return context.getExternalFilesDir(null);
  }

  /**
   * Gets the relative path to a file within an experiment. For example, if the file is a picture
   * pic.png in the assets/ directory of experiment xyz, this will return just "assets/pic.png". If
   * the file is not in xyz but the experimentId passed in is xyz, this will return an empty string.
   */
  public String getRelativePathInExperiment(String experimentId, File file) {
    String absolutePath = file.getAbsolutePath();
    int start = absolutePath.indexOf(experimentId);
    if (start < 0) {
      // This file is not part of this experiment.
      return "";
    } else {
      return absolutePath.substring(start + experimentId.length() + 1);
    }
  }

  public String getExperimentExportDirectory(AppAccount appAccount) throws IOException {
    File dir = new File(getFilesDir(appAccount), "exported_experiments");
    if (!dir.exists() && !dir.mkdir()) {
      throw new IOException("Can't create experiments directory");
    }
    return dir.toString();
  }

  /** Gets a file in an experiment from a relative path to that file within the experiment. */
  public File getExperimentFile(AppAccount appAccount, String experimentId, String relativePath) {
    return new File(getExperimentDirectory(appAccount, experimentId), relativePath);
  }

  /**
   * Gets the relative path to the file from the accounts's files directory. This can be used to
   * create the imagePath in UserMetadata.ExperimentOverview.
   */
  public File getRelativePathInFilesDir(String experimentId, String relativePath) {
    return new File(new File(EXPERIMENTS_DIRECTORY, experimentId), relativePath);
  }

  /**
   * Immediately saves the file to be sure the in-storage protos are consistent with memory, and
   * starts the Export Service to produce an SJ file.
   */
  public Single<File> getFileForExport(
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
                            sensorData.writeTo(sensorStream);
                          } catch (IOException ioException) {
                            s.onError(ioException);
                            return;
                          }

                          try (FileOutputStream fos = new FileOutputStream(zipFile);
                              ZipOutputStream zos = new ZipOutputStream(fos); ) {
                            File experimentDirectory =
                                getExperimentDirectory(appAccount, experiment.getExperimentId());
                            zipDirectory(experimentDirectory, zos, "");

                            if (!experiment.getExperimentOverview().getImagePath().isEmpty()) {
                              File experimentImage =
                                  new File(
                                      getFilesDir(appAccount),
                                      experiment.getExperimentOverview().getImagePath());
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

  public void zipDirectory(File directory, ZipOutputStream zipOutputStream, String path)
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

  public void zipExperimentImage(File image, ZipOutputStream zipOutputStream) throws IOException {
    try {
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
    } catch (FileNotFoundException fileException) {
      Log.d(TAG, "Image not found when exporting.", fileException);
    }
  }

  public boolean validateShareIntent(Context context, AppAccount appAccount, String experimentId) {
    try {
      // Get a low-cost known-good file to test if anything can handle our intent.
      // This won't be used for the actual intent.
      Uri experimentProto =
          FileProvider.getUriForFile(
              context,
              context.getPackageName(),
              getExperimentFile(appAccount, experimentId, EXPERIMENT_FILE));
      return getShareIntent(context, appAccount, experimentProto) != null;
    } catch (Exception e) {
      Log.e(TAG, "Error validating share intent", e);
      return false;
    }
  }

  public Intent getShareIntent(Context context, AppAccount appAccount, Uri exportFile) {
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

  public Intent createPhotoShareIntent(
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
    File imageFile = new File(getExperimentDirectory(appAccount, experimentId), imageName);
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

  @VisibleForTesting
  public boolean canImportFromVersion(Version.FileVersion fileVersion) {
    switch (fileVersion.getPlatform()) {
      case ANDROID:
        return fileVersion.getVersion() == 1 && fileVersion.getMinorVersion() <= 2;
      case IOS:
        if (fileVersion.getVersion() != 1) {
          return false;
        }
        if (fileVersion.getMinorVersion() == 1) {
          return fileVersion.getPlatformVersion() >= 3;
        }
        return fileVersion.getMinorVersion() == 2;
    }

    // Not IOS or Android?  Did we finally release on Commodore 64?  Well, as long as it's
    // using a released file version.
    return fileVersion.getVersion() == 1 && fileVersion.getMinorVersion() == 2;
  }

  private void writeProtoToFile(byte[] protoBytes, File file) throws IOException {
    try (FileOutputStream fos = new FileOutputStream(file)) {
      fos.write(protoBytes);
    }
  }

  public void writeExperimentLibraryFile(ExperimentLibrary library, AppAccount appAccount)
      throws IOException {
    writeProtoToFile(library.toByteArray(), getExperimentLibraryFile(appAccount));
  }

  public ExperimentLibrary readExperimentLibraryFile(AppAccount appAccount) {
    ExperimentLibrary library = null;
    File libraryFile = getExperimentLibraryFile(appAccount);
    if (libraryFile.canRead()) {
      byte[] libraryBytes = new byte[(int) libraryFile.length()];
      try (FileInputStream fis = new FileInputStream(libraryFile);
          DataInputStream dis = new DataInputStream(fis);) {
        dis.readFully(libraryBytes);
        library = ExperimentLibrary.parseFrom(libraryBytes);
      } catch (Exception e) {
        Log.e(TAG, "Exception reading Experiment Library file", e);
      }
    }
    return library;
  }

  public void writeLocalSyncStatusFile(
      GoosciLocalSyncStatus.LocalSyncStatus status, AppAccount appAccount) throws IOException {
    writeProtoToFile(status.toByteArray(), getLocalSyncStatusFile(appAccount));
  }

  public GoosciLocalSyncStatus.LocalSyncStatus readLocalSyncStatusFile(AppAccount appAccount) {
    File statusFile = getLocalSyncStatusFile(appAccount);
    if (statusFile.canRead()) {
      try (FileInputStream fis = new FileInputStream(statusFile)) {
        return GoosciLocalSyncStatus.LocalSyncStatus.parseFrom(fis);
      } catch (Exception e) {
        if (Log.isLoggable(TAG, Log.ERROR)) {
          Log.e(TAG, "Exception reading Local Sync Status file", e);
        }
      }
    }
    return GoosciLocalSyncStatus.LocalSyncStatus.getDefaultInstance();
  }

  public String getTrialProtoFileName(String protoId) {
    return RECORDING + protoId + DOT_PROTO;
  }

  public long getFreeSpaceInMb() {
    File path = Environment.getDataDirectory();
    StatFs stat = new StatFs(path.getPath());
    long blockSize;
    long availableBlocks;
    blockSize = stat.getBlockSizeLong();
    availableBlocks = stat.getAvailableBlocksLong();
    long bytesFree = availableBlocks * blockSize;
    long mbFree = bytesFree / (1024 * 1024);
    if (Log.isLoggable(TAG, Log.INFO)) {
      Log.i(TAG, "Available storage: " + mbFree);
    }
    return mbFree;
  }
}
