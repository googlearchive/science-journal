package com.google.android.apps.forscience.whistlepunk.cloudsync;

import android.content.Context;
import java.io.IOException;

/** An interface that defines a service used to sync data to and from a cloud storage provider. */
public interface CloudSyncService {

  /**
   * Syncs the Experiment Library file to cloud storage
   */
  void syncExperimentLibrary(Context context) throws IOException;

  /** Syncs the Experiment Proto file to cloud storage */
  void syncExperimentProto(Context context, String experimentId) throws IOException;
}
