package com.google.android.apps.forscience.whistlepunk.cloudsync;

import java.io.File;
import java.io.IOException;

/** An interface that defines a service used to sync data to and from a cloud storage provider. */
public interface CloudSyncService {

  /**
   * Writes the specified file to cloud storage
   *
   * @param file the file to store.
   */
  void uploadExperimentLibraryFile(File file) throws IOException;

  /**
   * Gets the experiment library file from cloud storage.
   *
   * @return a byte array containing a serialized proto, or null if the file doesn't exist.
   */
  public byte[] downloadExperimentLibraryFile() throws IOException;
}
