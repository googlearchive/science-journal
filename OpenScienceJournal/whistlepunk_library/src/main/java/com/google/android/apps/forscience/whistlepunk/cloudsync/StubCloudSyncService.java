package com.google.android.apps.forscience.whistlepunk.cloudsync;

import android.util.Log;
import java.io.File;
import java.io.IOException;

/** */
public class StubCloudSyncService implements CloudSyncService {
  private static final String TAG = "StubCloudSyncService";

  public StubCloudSyncService() {}

  @Override
  public void uploadExperimentLibraryFile(File libraryFile) throws IOException {
    Log.i(TAG, "Stubbed File Upload!");
  }

  @Override
  public byte[] downloadExperimentLibraryFile() throws IOException {
    Log.i(TAG, "Stubbed Experiment Library File Download!");
    return null;
  }
}
