package com.google.android.apps.forscience.whistlepunk.accounts;

import android.accounts.Account;
import androidx.annotation.Nullable;
import java.io.File;

/** An stub implementation of {@link AppAccount}. */
public final class StubAppAccount implements AppAccount {
  private static StubAppAccount instance;

  public static StubAppAccount getInstance() {
    if (instance == null) {
      instance = new StubAppAccount();
    }
    return instance;
  }

  private final Object lockForExperimentLibraryFile = new Object();
  private final Object lockForExperimentProtoFile = new Object();

  private StubAppAccount() {}

  @Nullable
  @Override
  public Account getAccount() {
    return null;
  }

  @Override
  public String getAccountName() {
    return "stub";
  }

  @Override
  public String getAccountKey() {
    return "stub";
  }

  @Override
  public boolean isSignedIn() {
    return false;
  }

  @Override
  public File getFilesDir() {
    return new File("/stub");
  }

  @Override
  public String getDatabaseFileName(String name) {
    return name;
  }

  @Override
  public String getSharedPreferencesName() {
    return "default";
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
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    // All StubAppAccount instances are equal.
    return true;
  }

  @Override
  public int hashCode() {
    return 42;
  }
}
