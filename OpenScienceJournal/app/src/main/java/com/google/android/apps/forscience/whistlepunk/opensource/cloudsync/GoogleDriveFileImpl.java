package com.google.android.apps.forscience.whistlepunk.opensource.cloudsync;

import com.google.android.apps.forscience.whistlepunk.cloudsync.DriveFile;
import com.google.api.services.drive.model.File;

/**
 * A wrapper around com.google.api.services.drive.model.File, made necessary by the fact that it is
 * a final class, and thus unmockable.
 */
public class GoogleDriveFileImpl implements DriveFile {

  private final File file;

  public GoogleDriveFileImpl(File file) {
    this.file = file;
  }

  @Override
  public long getVersion() {
    return file.getVersion();
  }

  @Override
  public String getId() {
    return file.getId();
  }

  @Override
  public String getTitle() {
    return file.getTitle();
  }
}
