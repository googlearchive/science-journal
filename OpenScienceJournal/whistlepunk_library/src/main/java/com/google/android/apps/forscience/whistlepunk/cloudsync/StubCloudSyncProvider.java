package com.google.android.apps.forscience.whistlepunk.cloudsync;

import com.google.android.apps.forscience.whistlepunk.accounts.AppAccount;

/** A stub cloud sync provider. */
@SuppressWarnings("RedundantIfStatement")
public class StubCloudSyncProvider implements CloudSyncProvider {
  private static final String TAG = "StubCloudSyncProvider";
  private static final CloudSyncManager service = new StubCloudSyncManager();

  public StubCloudSyncProvider() {}

  @Override
  public CloudSyncManager getServiceForAccount(AppAccount appAccount) {
    return service;
  }
}
