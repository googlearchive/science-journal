package com.google.android.apps.forscience.whistlepunk.accounts;

import android.accounts.AccountManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import com.google.android.apps.forscience.whistlepunk.WhistlePunkApplication;

/** Handles LOGIN_ACCOUNTS_CHANGED_ACTION. */
public class AccountsChangedReceiver extends BroadcastReceiver {
  @Override
  public void onReceive(Context context, Intent intent) {
    if (intent != null && AccountManager.LOGIN_ACCOUNTS_CHANGED_ACTION.equals(intent.getAction())) {
      WhistlePunkApplication.getAppServices(context).getAccountsProvider().onLoginAccountsChanged();
    }
  }
}
