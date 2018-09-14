package com.google.android.apps.forscience.whistlepunk.cloudsync;

import android.content.Context;
import java.io.IOException;

/** An interface that defines a service used to sync data to and from a cloud storage provider. */
public interface CloudSyncManager {

  /** Syncs the Experiment Library file to cloud storage */
  void syncExperimentLibrary(Context context, String logMessage) throws IOException;

  /** Syncs the Experiment Proto file to cloud storage */
  void syncExperimentProto(Context context, String experimentId) throws IOException;

  /** Deletes an Experiment Package from Drive */
  void deleteExperimentPackage(Context context, String experimentId);
}
