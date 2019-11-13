package com.google.android.apps.forscience.whistlepunk.accounts;

import android.accounts.Account;
import android.content.Context;
import androidx.annotation.Nullable;
import java.io.File;

/**
 * An implementation of {@link AppAccount} representing a user with no signed-in account.
 *
 * <p>File data, database data, and user preferences for are stored in the same places as they were
 * before accounts were supported.
 */
public final class NonSignedInAccount extends AbstractAccount {
  private static NonSignedInAccount instance;

  public static NonSignedInAccount getInstance(Context context) {
    if (instance == null) {
      instance = new NonSignedInAccount(context);
    }
    return instance;
  }

  private NonSignedInAccount(Context context) {
    super(context);
  }

  @Nullable
  @Override
  public Account getAccount() {
    return null;
  }

  @Override
  public String getAccountName() {
    return "";
  }

  @Override
  public String getAccountKey() {
    return "com.google.nsi:none";
  }

  @Override
  public boolean isSignedIn() {
    return false;
  }

  @Override
  public File getFilesDir() {
    return applicationContext.getFilesDir();
  }

  @Override
  public String getDatabaseFileName(String name) {
    return name;
  }

  @Override
  public String getSharedPreferencesName() {
    // Return the name of the default SharedPreferences.
    return applicationContext.getPackageName() + "_preferences";
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    // All NonSignedInAccount instances are equal.
    return true;
  }

  @Override
  public int hashCode() {
    return 42;
  }
}
