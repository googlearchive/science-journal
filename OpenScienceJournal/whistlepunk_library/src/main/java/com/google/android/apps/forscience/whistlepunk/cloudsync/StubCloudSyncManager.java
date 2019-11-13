package com.google.android.apps.forscience.whistlepunk.cloudsync;

import android.content.Context;
import android.util.Log;
import java.io.IOException;

/** A stub CloudSyncManager implentation for use in open source builds. Doesn't sync anywhere. */
public class StubCloudSyncManager implements CloudSyncManager {
  private static final String TAG = "StubCloudSyncManager";

  public StubCloudSyncManager() {}

  @Override
  public void syncExperimentLibrary(Context context, String logMessage) throws IOException {
    if (Log.isLoggable(TAG, Log.INFO)) {
      Log.i(TAG, "Stubbed Experiment Library Sync! " + logMessage);
    }
  }

  @Override
  public void logCloudInfo(String tag) {
    if (Log.isLoggable(tag, Log.WARN)) {
      Log.w(tag, "Stubbed Cloud Sync Manager");
    }
  }
}
