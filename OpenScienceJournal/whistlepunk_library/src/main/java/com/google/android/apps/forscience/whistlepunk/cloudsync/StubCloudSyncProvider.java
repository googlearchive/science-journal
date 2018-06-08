package com.google.android.apps.forscience.whistlepunk.cloudsync;

import com.google.android.apps.forscience.whistlepunk.accounts.AppAccount;

/** A stub cloud sync provider. */
@SuppressWarnings("RedundantIfStatement")
public class StubCloudSyncProvider implements CloudSyncProvider {
  private static final String TAG = "StubCloudSyncProvider";
  private static final CloudSyncService service = new StubCloudSyncService();

  public StubCloudSyncProvider() {}

  @Override
  public CloudSyncService getServiceForAccount(AppAccount appAccount) {
    return service;
  }
}
