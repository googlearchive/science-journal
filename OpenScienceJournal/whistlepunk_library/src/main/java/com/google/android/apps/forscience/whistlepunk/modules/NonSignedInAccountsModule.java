package com.google.android.apps.forscience.whistlepunk.modules;

import android.content.Context;
import com.google.android.apps.forscience.whistlepunk.accounts.AccountsProvider;
import com.google.android.apps.forscience.whistlepunk.accounts.NonSignedInAccountsProvider;
import dagger.Module;
import dagger.Provides;

/** Creates an accounts provider which is backed by an artificial non-signed-in account. */
@Module
public class NonSignedInAccountsModule {
  @Provides
  AccountsProvider provideAccountsProvider(Context context) {
    return new NonSignedInAccountsProvider(context);
  }
}
