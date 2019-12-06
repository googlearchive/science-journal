package com.google.android.apps.forscience.whistlepunk.opensource.modules;

import android.content.Context;
import com.google.android.apps.forscience.whistlepunk.accounts.AccountsProvider;
import com.google.android.apps.forscience.whistlepunk.opensource.accounts.GoogleAccountsProvider;
import dagger.Module;
import dagger.Provides;

/** Creates an accounts provider which is backed by Google Accounts. */
@Module
public class GoogleAccountsModule {
  @Provides
  AccountsProvider provideAccountsProvider(Context context) {
    return new GoogleAccountsProvider(context);
  }
}
