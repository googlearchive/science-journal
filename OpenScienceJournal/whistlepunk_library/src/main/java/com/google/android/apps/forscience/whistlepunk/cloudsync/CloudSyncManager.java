package com.google.android.apps.forscience.whistlepunk.cloudsync;

import android.content.Context;
import java.io.IOException;

/** An interface that defines a service used to sync data to and from a cloud storage provider. */
public interface CloudSyncManager {

  /** Syncs the Experiment Library file to cloud storage */
  void syncExperimentLibrary(Context context, String logMessage) throws IOException;

  /** Logs diagnostic info to the console * */
  void logCloudInfo(String tag);
}
