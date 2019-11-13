package com.google.android.apps.forscience.whistlepunk.accounts;

import android.content.Context;
import java.util.concurrent.atomic.AtomicInteger;

/** An abstract base class for an account. */
abstract class AbstractAccount implements AppAccount {
  protected final Context applicationContext;
  private final Object lockForExperimentLibraryFile = new Object();
  private final Object lockForExperimentProtoFile = new Object();
  private final AtomicInteger syncCompleteCounter = new AtomicInteger();

  /** For testing only */
  protected AbstractAccount() {
    applicationContext = null;
  }

  protected AbstractAccount(Context context) {
    applicationContext = context.getApplicationContext();
  }

  @Override
  public Object getLockForExperimentLibraryFile() {
    return lockForExperimentLibraryFile;
  }

  @Override
  public Object getLockForExperimentProtoFile() {
    return lockForExperimentProtoFile;
  }

  @Override
  public void incrementSyncCompleteCount() {
    syncCompleteCounter.incrementAndGet();
  }

  @Override
  public int getSyncCompleteCount() {
    return syncCompleteCounter.get();
  }
}
