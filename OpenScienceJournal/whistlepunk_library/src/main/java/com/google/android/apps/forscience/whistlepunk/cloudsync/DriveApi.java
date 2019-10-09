package com.google.android.apps.forscience.whistlepunk.cloudsync;

import android.content.Context;
import com.google.android.apps.forscience.whistlepunk.accounts.AppAccount;
import com.google.android.apps.forscience.whistlepunk.data.GoosciExperimentLibrary;
import com.google.android.apps.forscience.whistlepunk.metadata.GoosciExperiment;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.JsonFactory;
import java.io.File;
import java.io.IOException;
import java.util.Map;

/** Provides a wrapper around the Google Drive API object to allow for easier mocking. */
public interface DriveApi {

  /** Initializes the API for an account. */
  public DriveApi init(
      HttpTransport transport,
      JsonFactory jsonFactory,
      AppAccount appAccount,
      Context applicationContext);

  /**
   * Downloads an experiment library file with the given ID
   *
   * @param fileId The id of the file to download.
   * @return a proto containing the experiment library
   * @throws IOException
   */
  GoosciExperimentLibrary.ExperimentLibrary downloadExperimentLibraryFile(String fileId)
      throws IOException;

  /**
   * Downloads an experiment file with the given ID
   *
   * @param fileId The id of the file to download.
   * @return a proto containing the experiment
   * @throws IOException
   */
  GoosciExperiment.Experiment downloadExperimentProtoFile(String fileId) throws IOException;

  /**
   * Gets the file id of the experiment_library.proto file in Drive.
   *
   * @return the file id
   * @throws IOException
   */
  String getRemoteExperimentLibraryFileId() throws IOException;

  /**
   * Uploads an experiment library file for the first time.
   *
   * @param libraryFile the local file to upload.
   * @throws IOException
   */
  void insertExperimentLibraryFile(File libraryFile) throws IOException;

  /**
   * Updates an experiment library file that already exists remotely.
   *
   * @param libraryFile the local file to upload.
   * @throws IOException
   */
  void updateExperimentLibraryFile(File libraryFile, String fileId) throws IOException;

  /**
   * Downloads an experiment asset from Drive to a local file.
   *
   * @param packageId The packageId of the experiment to download from.
   * @param experimentDirectory The local directory to write the file to.
   * @param fileName The file name to use locally.
   * @return the local file.
   * @throws IOException
   */
  File downloadExperimentAsset(String packageId, File experimentDirectory, String fileName)
      throws IOException;

  /**
   * Gets a package ID for a file on drive.
   *
   * @param context The current context.
   * @param directoryId The id of the parent directory on Drive.
   * @return the package id.
   * @throws IOException
   */
  String getExperimentPackageId(Context context, String directoryId) throws IOException;

  /**
   * Checks if a file exists on Drive.
   *
   * @param fileId The file to check existence for.
   * @return Whether or not the file exists on drive, and is not trashed.
   * @throws IOException
   */
  boolean getFileExists(String fileId) throws IOException;

  /**
   * Gets the current file version (Drive revision) of all experiments on Drive.
   *
   * @return A map between the Drive fileIds and the file versions.
   * @throws IOException
   */
  Map<String, Long> getAllDriveExperimentVersions() throws IOException;

  /**
   * Gets the file metadata for the experiment proto inside a specific package.
   *
   * @param packageId The package Id of the experiment to get metadata for.
   * @return the File metadata.
   * @throws IOException
   */
  DriveFile getExperimentProtoMetadata(String packageId) throws IOException;

  /**
   * Uploads an experiment proto that hasn't been uploaded before.
   *
   * @param localFile the local file to upload.
   * @param packageId the packageId to use as the embedding parent.
   * @param experimentTitle the title of the experiment to upload.
   * @return the file version of the containing package.
   * @throws IOException
   */
  long insertExperimentProto(File localFile, String packageId, String experimentTitle)
      throws IOException;

  /**
   * Uploads an experiment proto that has been uploaded before.
   *
   * @param localFile the local file to upload.
   * @param serverExperimentProtoMetadata the metadata of the existing file.
   * @param packageId the packageId to use as the embedding parent.
   * @param experimentTitle the title of the experiment to update.
   * @return the file version of the containing package.
   * @throws IOException
   */
  long updateExperimentProto(
      File localFile,
      DriveFile serverExperimentProtoMetadata,
      String packageId,
      String experimentTitle)
      throws IOException;

  /**
   * Create a new Science Journal folder on Drive.
   *
   * @return The file ID of the new folder.
   * @throws IOException
   */
  String createNewSJFolder() throws IOException;

  /**
   * Trash a file on Drive.
   *
   * @param fileId The id of the file to trash.
   * @throws IOException
   */
  void trashFileById(String fileId) throws IOException;

  /**
   * Uploads a file to drive
   *
   * @param localFile the local file to upload.
   * @param packageId the packageId to use as the embedding parent.
   * @throws IOException
   */
  void uploadFile(File localFile, String packageId) throws IOException;

  /**
   * Checks the version of an SJ package on Drive.
   *
   * @param packageId The package to version check.
   * @return The major version of the package on Drive.
   */
  int getPackageVersion(String packageId) throws IOException;

  // Get the file version for the specific Drive File
  long getFileVersion(String fileId) throws IOException;

  /**
   * Checks if a "Science Journal" folder exists on the user's Drive. This should only be used for
   * logging and diagnostics.
   *
   * @return Whether a folder named "Science Journal" exists in the user's Drive storage.
   */
  boolean sjFolderExists() throws IOException;

  /**
   * Counts the number of files named "experiment.proto" in the user's Drive. This should only be
   * used for logging and diagnostics.
   *
   * @return a count of experiment.proto files in the user's Drive storage.
   */
  int countSJExperiments() throws IOException;
}
