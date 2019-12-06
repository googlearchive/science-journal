package com.google.android.apps.forscience.whistlepunk.opensource.modules;

import android.content.Context;
import com.google.android.apps.forscience.whistlepunk.cloudsync.CloudSyncProvider;
import com.google.android.apps.forscience.whistlepunk.cloudsync.DriveSyncProvider;
import com.google.android.apps.forscience.whistlepunk.opensource.cloudsync.GoogleDriveApiImpl;
import dagger.Module;
import dagger.Provides;

/** Creates a cloud sync provider which is backed by Google Drive. */
@Module
public class GoogleDriveSyncModule {
  @Provides
  CloudSyncProvider provideCloudSyncProvider(Context context) {
    return new DriveSyncProvider(context, GoogleDriveApiImpl::new);
  }
}
