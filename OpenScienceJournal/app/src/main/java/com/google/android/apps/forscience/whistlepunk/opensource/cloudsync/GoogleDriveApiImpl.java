package com.google.android.apps.forscience.whistlepunk.opensource.cloudsync;

import android.content.Context;
import android.util.Log;
import com.google.android.apps.forscience.whistlepunk.R;
import com.google.android.apps.forscience.whistlepunk.accounts.AppAccount;
import com.google.android.apps.forscience.whistlepunk.cloudsync.DriveApi;
import com.google.android.apps.forscience.whistlepunk.cloudsync.DriveFile;
import com.google.android.apps.forscience.whistlepunk.data.GoosciExperimentLibrary.ExperimentLibrary;
import com.google.android.apps.forscience.whistlepunk.metadata.GoosciExperiment.Experiment;
import com.google.android.apps.forscience.whistlepunk.metadata.Version.FileVersion;
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential;
import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.client.http.ByteArrayContent;
import com.google.api.client.http.FileContent;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.util.ExponentialBackOff;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.DriveScopes;
import com.google.api.services.drive.model.File;
import com.google.api.services.drive.model.FileList;
import com.google.api.services.drive.model.ParentReference;
import java.io.ByteArrayOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Implements a wrapper around an actual Google Drive API object. */
public class GoogleDriveApiImpl implements DriveApi {

  private static final String[] SCOPES = {
      DriveScopes.DRIVE, DriveScopes.DRIVE_APPDATA,
  };

  private static final String TAG = "DriveApiImpl";
  private static final String APP_DATA_FOLDER = "appDataFolder";
  private static final String EXPERIMENT_LIBRARY_QUERY = "title = 'experiment_library.proto'";
  private static final String EXPERIMENT_LIBRARY_PROTO = "experiment_library.proto";
  private static final String MIME_TYPE = "application/sj";
  private static final String EXPERIMENT_PROTO_FILE = "experiment.proto";
  private static final String FOLDER_MIME_TYPE = "application/vnd.google-apps.folder";
  private static final String FOLDER_NAME = "Open Science Journal";
  private static final String VERSION_PROTO_FILE = "version.proto";
  private static final int MINOR_VERSION = 0;
  private static final int VERSION = 1;

  private Drive driveApi;

  public GoogleDriveApiImpl() {}

  @Override
  public DriveApi init(
      HttpTransport transport,
      JsonFactory jsonFactory,
      AppAccount appAccount,
      Context applicationContext) {

    List<String> scopeList = Arrays.asList(SCOPES);
    GoogleAccountCredential credential =
        GoogleAccountCredential.usingOAuth2(applicationContext, scopeList)
            .setBackOff(new ExponentialBackOff())
            .setSelectedAccount(appAccount.getAccount());

    this.driveApi =
        new Drive.Builder(transport, jsonFactory, credential)
            .setApplicationName(applicationContext.getPackageName())
            .build();

    return this;
  }

  private void downloadFileIdToOutputStream(String fileId, ByteArrayOutputStream outputStream)
      throws IOException {
    driveApi
        .files()
        .get(fileId)
        .executeMediaAndDownloadTo(outputStream);
  }

  @Override
  public ExperimentLibrary downloadExperimentLibraryFile(String fileId) throws IOException {
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    downloadFileIdToOutputStream(fileId, outputStream);
    return ExperimentLibrary.parseFrom(
        outputStream.toByteArray());
  }

  @Override
  public Experiment downloadExperimentProtoFile(String fileId) throws IOException {
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    downloadFileIdToOutputStream(fileId, outputStream);
    return Experiment.parseFrom(
        outputStream.toByteArray());
  }

  @Override
  public String getRemoteExperimentLibraryFileId() throws IOException {
    FileList fileList =
        driveApi
            .files()
            .list()
            .setSpaces(APP_DATA_FOLDER)
            .setQ(EXPERIMENT_LIBRARY_QUERY)
            .execute();
    if (!fileList.getItems().isEmpty()) {
      File firstFile = fileList.getItems().get(0);
      if (firstFile != null) {
        return firstFile.getId();
      }
    }
    return null;
  }

  @Override
  public void insertExperimentLibraryFile(java.io.File libraryFile) throws IOException {
    File file = new File();
    file.setTitle(EXPERIMENT_LIBRARY_PROTO);
    file.setParents(Collections.singletonList(new ParentReference().setId(APP_DATA_FOLDER)));
    FileContent content = new FileContent(MIME_TYPE, libraryFile);
    driveApi
        .files()
        .insert(file, content)
        .execute();
  }

  @Override
  public void updateExperimentLibraryFile(java.io.File libraryFile, String fileId)
      throws IOException {
    File file = new File();
    file.setId(fileId);
    FileContent content = new FileContent(MIME_TYPE, libraryFile);
    driveApi
        .files()
        .update(fileId, file, content)
        .execute();
  }

  private FileList getFileFromPackage(String packageId, String fileName) throws IOException {
    return driveApi
        .files()
        .list()
        .setQ("title = '" + fileName + "' and '" + packageId + "' in parents")
        .execute();
  }

  @Override
  public java.io.File downloadExperimentAsset(
      String packageId, java.io.File experimentDirectory, String fileName) throws IOException {
    java.io.File outputFile = new java.io.File(experimentDirectory, fileName);
    outputFile.getParentFile().mkdirs();
    FileList files = getFileFromPackage(packageId, outputFile.getName());
    if (!files.getItems().isEmpty()) {
      if (files.getItems().size() > 1 && Log.isLoggable(TAG, Log.ERROR)) {
        Log.e(TAG, "More than one file found " + outputFile.getName());
      }
      try (FileOutputStream fos = new FileOutputStream(outputFile)) {
        driveApi
            .files()
            .get(files.getItems().get(0).getId())
            .executeMediaAndDownloadTo(fos);
        fos.flush();
      }
    }
    return outputFile;
  }

  private void updateFile(File serverFile, java.io.File localFile) throws IOException {
    FileContent content = new FileContent(MIME_TYPE, localFile);
    File file = new File();
    file.setTitle(serverFile.getTitle());
    file.setId(serverFile.getId());
    driveApi
        .files()
        .update(serverFile.getId(), file, content)
        .execute();
  }

  private void insertFile(java.io.File localFile, String packageId) throws IOException {
    FileContent content = new FileContent(MIME_TYPE, localFile);
    File file = new File();
    file.setTitle(localFile.getName());
    file.setParents(Collections.singletonList(new ParentReference().setId(packageId)));
    driveApi
        .files()
        .insert(file, content)
        .execute();
  }

  // Get the Package ID for the experiment on Drive
  @Override
  public String getExperimentPackageId(Context context, String directoryId) throws IOException {
    // If we don't know it locally, but have the experiment locally, it must not exist yet. So,
    // create it remotely.
    File folder = new File();
    folder.setTitle(context.getResources().getString(R.string.default_experiment_name));
    folder.setMimeType(FOLDER_MIME_TYPE);
    folder.setParents(Collections.singletonList(new ParentReference().setId(directoryId)));
    File packageId =
        driveApi
            .files()
            .insert(folder)
            .setFields("id")
            .execute();
    return packageId.getId();
  }

  // Returns true if a file exists and hasn't been trashed.
  @Override
  public boolean getFileExists(String fileId) throws IOException {
    try {
      File file =
          driveApi
              .files()
              .get(fileId)
              .execute();
      if (file == null || file.getLabels() == null) {
        return false;
      }
      return !file.getLabels().getTrashed();
    } catch (GoogleJsonResponseException e) {
      // Drive will return a GoogleJsonResponseException if the file is not found.
      return false;
    }
  }

  // Get the file metadata for the specific experiment proto
  @Override
  public Map<String, Long> getAllDriveExperimentVersions() throws IOException {
    FileList files =
        driveApi
            .files()
            .list()
            .setQ("title = '" + EXPERIMENT_PROTO_FILE + "'")
            .setFields("items(version,parents)")
            .execute();
    HashMap<String, Long> versionMap = new HashMap<>();
    if (!files.getItems().isEmpty()) {
      for (File f : files.getItems()) {
        versionMap.put(f.getParents().get(0).getId(), f.getVersion());
      }
    }
    return versionMap;
  }

  // Get the file metadata for the specific experiment proto
  @Override
  public DriveFile getExperimentProtoMetadata(String packageId) throws IOException {
    FileList files =
        driveApi
            .files()
            .list()
            .setQ("title = '" + EXPERIMENT_PROTO_FILE + "' and '" + packageId + "' in parents")
            .execute();
    if (!files.getItems().isEmpty()) {
      return new GoogleDriveFileImpl(files.getItems().get(0));
    }
    return null;
  }

  @Override
  public long insertExperimentProto(
      java.io.File localFile, String packageId, String experimentTitle) throws IOException {
    FileContent content = new FileContent(MIME_TYPE, localFile);

    File file = new File();
    file.setTitle(EXPERIMENT_PROTO_FILE);
    file.setParents(Collections.singletonList(new ParentReference().setId(packageId)));
    driveApi
        .files()
        .insert(file, content)
        .execute();

    File drivePackage = new File();
    drivePackage.setId(packageId);
    drivePackage.setTitle(experimentTitle);
    driveApi
        .files()
        .patch(packageId, drivePackage)
        .execute();

    FileVersion versionProto =
        FileVersion.newBuilder().setMinorVersion(MINOR_VERSION).setVersion(VERSION).build();

    ByteArrayContent versionBytes = new ByteArrayContent(MIME_TYPE, versionProto.toByteArray());

    File version = new File();
    version.setParents(Collections.singletonList(new ParentReference().setId(packageId)));
    version.setTitle(VERSION_PROTO_FILE);
    driveApi.files().insert(version, versionBytes).execute();

    return getExperimentProtoMetadata(packageId).getVersion();
  }

  // Overwrite an existing proto on Drive
  @Override
  public long updateExperimentProto(
      java.io.File localFile,
      DriveFile serverExperimentProtoMetadata,
      String packageId,
      String experimentTitle)
      throws IOException {
    FileContent content = new FileContent(MIME_TYPE, localFile);

    // We can't re-use the file metadata that we already have, for some opaque reason about fields
    // that are already defined in the metadata we have, and can't be defined for an update.
    File file = new File();
    file.setTitle(serverExperimentProtoMetadata.getTitle());
    file.setId(serverExperimentProtoMetadata.getId());
    driveApi
        .files()
        .update(serverExperimentProtoMetadata.getId(), file, content)
        .execute();

    File drivePackage = new File();
    drivePackage.setId(packageId);
    drivePackage.setTitle(experimentTitle);
    driveApi
        .files()
        .patch(packageId, drivePackage)
        .execute();

    return getExperimentProtoMetadata(packageId).getVersion();
  }

  @Override
  public String createNewSJFolder() throws IOException {
    File folder = new File();
    folder.setTitle(FOLDER_NAME);
    folder.setMimeType(FOLDER_MIME_TYPE);
    folder.setParents(Collections.singletonList(new ParentReference().setId("root")));
    File withId =
        driveApi
            .files()
            .insert(folder)
            .setFields("id")
            .execute();
    return withId.getId();
  }

  @Override
  public void trashFileById(String fileId) throws IOException {
    driveApi
        .files()
        .trash(fileId)
        .execute();
  }

  @Override
  public void uploadFile(java.io.File localFile, String packageId) throws IOException {
    FileList files = getFileFromPackage(packageId, localFile.getName());
    if (!files.getItems().isEmpty()) {
      updateFile(files.getItems().get(0), localFile);
    } else {
      insertFile(localFile, packageId);
    }
  }

  @Override
  public int getPackageVersion(String packageId) throws IOException {
    FileList files =
        driveApi
            .files()
            .list()
            .setQ("title = '" + VERSION_PROTO_FILE + "' and '" + packageId + "' in parents")
            .execute();
    if (!files.getItems().isEmpty()) {
      String fileId = files.getItems().get(0).getId();
      try {
        FileVersion version = downloadVersionProtoFile(fileId);
        return version.getVersion();
      } catch (IOException ioe) {
        // IO Exception. Don't sync this right now. We can try again in the future with no
        // consequences.
        return Integer.MAX_VALUE;
      }
    } else {
      // If there's no version info at all, it must have been uploaded before this CL. So it's
      // Version 1.
      return 1;
    }
  }

  private FileVersion downloadVersionProtoFile(String fileId) throws IOException {
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    downloadFileIdToOutputStream(fileId, outputStream);
    return FileVersion.parseFrom(outputStream.toByteArray());
  }

  // Get the file metadata for the specific experiment proto
  @Override
  public long getFileVersion(String fileId) throws IOException {
    File file = driveApi.files().get(fileId).setFields("version").execute();
    return file.getVersion();
  }

  @Override
  public boolean sjFolderExists() throws IOException {
    FileList files = driveApi.files().list().setQ("title = '" + FOLDER_NAME + "'").execute();
    return !files.getItems().isEmpty();
  }

  @Override
  public int countSJExperiments() throws IOException {
    FileList files =
        driveApi
            .files()
            .list()
            .setQ("title = '" + EXPERIMENT_PROTO_FILE + "'")
            .execute();
    return files.getItems().size();
  }
}
