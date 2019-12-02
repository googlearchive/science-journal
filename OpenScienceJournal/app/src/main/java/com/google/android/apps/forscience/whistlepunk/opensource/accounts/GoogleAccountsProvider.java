package com.google.android.apps.forscience.whistlepunk.opensource.accounts;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.util.Log;
import androidx.fragment.app.Fragment;
import com.google.android.apps.forscience.whistlepunk.ActivityWithNavigationView;
import com.google.android.apps.forscience.whistlepunk.accounts.AbstractAccountsProvider;
import com.google.android.apps.forscience.whistlepunk.accounts.AppAccount;
import com.google.android.apps.forscience.whistlepunk.accounts.NonSignedInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.tasks.Task;
import io.reactivex.Single;

/** An accounts provider which is backed by Google Accounts. */
@SuppressWarnings("RedundantIfStatement")
public final class GoogleAccountsProvider extends AbstractAccountsProvider {
  private static final String TAG = "GoogleAccountsProvider";

  GoogleSignInClient googleSignInClient;
  Context context;

  public GoogleAccountsProvider(Context context) {
    super(context);
    this.context = context;
    setShowSignInActivityIfNotSignedIn(false);
    GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
        .requestEmail()
        .build();
    googleSignInClient = GoogleSignIn.getClient(context, gso);

    GoogleSignInAccount googleSignInAccount = GoogleSignIn.getLastSignedInAccount(context);
    if (googleSignInAccount != null) {
      signInCurrentAccount(googleSignInAccount);
    }
  }

  @Override
  protected void afterSetCurrentAccount(AppAccount currentAccount) {
    super.afterSetCurrentAccount(currentAccount);
  }

  @Override
  public boolean supportSignedInAccount() {
    return true;
  }

  @Override
  public boolean isSignedIn() {
    GoogleSignInAccount account = GoogleSignIn.getLastSignedInAccount(context);
    return account != null;
  }

  @Override
  public int getAccountCount() {
    GoogleSignInAccount account = GoogleSignIn.getLastSignedInAccount(context);
    return account == null ? 0 : 1;
  }

  @Override
  public void installAccountSwitcher(ActivityWithNavigationView activity) {

  }

  @Override
  public void disconnectAccountSwitcher(ActivityWithNavigationView activity) {
    return;
  }

  @Override
  public void onLoginAccountsChanged(Intent data) {
    try {
      Task<GoogleSignInAccount> task = GoogleSignIn.getSignedInAccountFromIntent(data);
      GoogleSignInAccount account = task.getResult(ApiException.class);
      GoogleSignInAccount googleSignInAccount = GoogleSignIn.getLastSignedInAccount(context);
      signInCurrentAccount(googleSignInAccount);
    } catch (ApiException apiException) {
      Log.e(TAG, "GoogleSignIn api exception");
    }
  }

  private void signInCurrentAccount(GoogleSignInAccount googleSignInAccount) {
    GoogleAccount googleAccount = new GoogleAccount(context, null, googleSignInAccount);
    if (googleAccount == null) {
      return;
    }

    AppAccount currentAccount = getCurrentAccount();
    boolean sameAsCurrentAccount = googleAccount.equals(currentAccount);
    if (sameAsCurrentAccount) {
      return;
    }

    setCurrentAccount(googleAccount);
  }

  @Override
  public void showAddAccountDialog(Activity activity) {
    return;
  }

  @Override
  public void showAccountSwitcherDialog(Fragment fragment, int requestCode) {
    if (getCurrentAccount() instanceof GoogleAccount) {
      googleSignInClient.signOut();
      setCurrentAccount(NonSignedInAccount.getInstance(context));
      return;
    }
    Intent signInIntent = googleSignInClient.getSignInIntent();
    fragment.getActivity().startActivityForResult(signInIntent, requestCode);
  }
}
