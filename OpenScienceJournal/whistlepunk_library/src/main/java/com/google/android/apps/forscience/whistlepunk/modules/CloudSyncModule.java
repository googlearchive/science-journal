package com.google.android.apps.forscience.whistlepunk.modules;

import android.content.Context;
import com.google.android.apps.forscience.whistlepunk.cloudsync.CloudSyncProvider;
import com.google.android.apps.forscience.whistlepunk.cloudsync.StubCloudSyncProvider;
import dagger.Module;
import dagger.Provides;

/** Creates a stub cloud sync provider. */
@Module
public class CloudSyncModule {
  @Provides
  CloudSyncProvider provideCloudSyncProvider(Context context) {
    return new StubCloudSyncProvider();
  }
}
