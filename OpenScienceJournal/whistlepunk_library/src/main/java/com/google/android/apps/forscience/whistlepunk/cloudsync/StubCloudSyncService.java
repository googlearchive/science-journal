package com.google.android.apps.forscience.whistlepunk.cloudsync;

import android.content.Context;
import android.util.Log;
import java.io.IOException;

/** */
public class StubCloudSyncService implements CloudSyncService {
  private static final String TAG = "StubCloudSyncService";

  public StubCloudSyncService() {}

  @Override
  public void syncExperimentLibrary(Context context) throws IOException {
    Log.i(TAG, "Stubbed Experiment Library Sync!");
  }
}
