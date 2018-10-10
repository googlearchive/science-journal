/*
 *  Copyright 2018 Google Inc. All Rights Reserved.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package com.google.android.apps.forscience.whistlepunk.accounts;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import com.google.android.apps.forscience.whistlepunk.MainActivity;
import com.google.android.apps.forscience.whistlepunk.R;
import com.google.android.apps.forscience.whistlepunk.WhistlePunkApplication;

/**
 * Fragment that tells the user about saving experiments in Google Drive and prompts the user to *
 * Sign In.
 */
public class SignInFragment extends Fragment {
  private static final int REQUEST_CODE_ACCOUNT_SWITCHER = 217;

  private AccountsProvider accountsProvider;

  @Override
  public View onCreateView(
      @NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
    super.onCreateView(inflater, container, savedInstanceState);

    accountsProvider = WhistlePunkApplication.getAppServices(getActivity()).getAccountsProvider();

    View view = inflater.inflate(R.layout.fragment_sign_in, container, false);

    Button signInButton = view.findViewById(R.id.btn_sign_in);
    signInButton.setOnClickListener(v -> signInClicked());

    Button continueWithoutSigningInButton = view.findViewById(R.id.btn_continue_without_signing_in);
    continueWithoutSigningInButton.setOnClickListener(v -> continueWithoutSigningInClicked());

    Button learnMoreButton = view.findViewById(R.id.btn_learn_more);
    learnMoreButton.setOnClickListener(v -> learnMoreClicked());

    return view;
  }

  @Override
  public void onActivityResult(int requestCode, int resultCode, Intent data) {
    if (requestCode == REQUEST_CODE_ACCOUNT_SWITCHER) {
      if (accountsProvider.isSignedIn()) {
        afterSignIn();
      }
    }
  }

  private void signInClicked() {
    if (accountsProvider.isSignedIn()) {
      // This shouldn't happen, but if it does, just make this screen go away.
      afterSignIn();
      return;
    }

    if (accountsProvider.getAccountCount() > 0) {
      // If there is one or more accounts on the device, show the account switcher dialog.
      accountsProvider.showAccountSwitcherDialog(this, REQUEST_CODE_ACCOUNT_SWITCHER);
    } else {
      // If there are no accounts on the device, show the add account dialog.
      accountsProvider.showAddAccountDialog(getActivity());
    }
  }

  private void afterSignIn() {
    FragmentActivity activity = getActivity();
    activity.startActivity(new Intent(activity, MainActivity.class));
    activity.finish();
  }

  private void learnMoreClicked() {
    startActivity(
        new Intent(Intent.ACTION_VIEW, Uri.parse(getString(R.string.sign_in_learn_more_url))));
  }

  private void continueWithoutSigningInClicked() {
    // Go to MainActivity.
    FragmentActivity activity = getActivity();
    activity.startActivity(new Intent(activity, MainActivity.class));
    activity.finish();
  }
}
