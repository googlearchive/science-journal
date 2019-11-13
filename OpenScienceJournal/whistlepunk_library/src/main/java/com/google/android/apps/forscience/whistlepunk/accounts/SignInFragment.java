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

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.net.Uri;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.appcompat.app.AlertDialog;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import com.google.android.apps.forscience.whistlepunk.ActivityRequestCodes;
import com.google.android.apps.forscience.whistlepunk.R;
import com.google.android.apps.forscience.whistlepunk.WhistlePunkApplication;
import com.google.android.apps.forscience.whistlepunk.analytics.TrackerConstants;

/**
 * Fragment that tells the user about saving experiments in Google Drive and prompts the user to *
 * Sign In.
 */
public class SignInFragment extends Fragment {
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
  public void onResume() {
    super.onResume();

    // Handle the situation where the user got to the OldUserOptionPromptActivity and then pressed
    // the home button. In that case, when they re-launch Science Journal, they will get here but
    // they are already signed in.
    if (accountsProvider.isSignedIn()) {
      afterSignIn();
      return;
    }

    if (accountsProvider.getAndSetShowScienceJournalIsDisabledAlert(false)) {
      showScienceJournalIsDisabledAlert();
    }
  }

  private void showScienceJournalIsDisabledAlert() {
    WhistlePunkApplication.getUsageTracker(getActivity())
        .trackEvent(
            TrackerConstants.CATEGORY_SIGN_IN, TrackerConstants.ACTION_PERMISSION_DENIED, null, 0);
    AlertDialog.Builder alertDialog = new AlertDialog.Builder(getActivity());
    Resources resources = getResources();
    alertDialog.setTitle(resources.getString(R.string.science_journal_disabled_title));
    alertDialog.setMessage(resources.getString(R.string.science_journal_disabled_message));
    alertDialog.setNegativeButton(
        android.R.string.cancel,
        (dialog, which) -> {
          dialog.cancel();
        });
    alertDialog.setPositiveButton(
        R.string.science_journal_disabled_yes,
        (dialog, which) -> {
          dialog.dismiss();
          showAccountSwitcherDialog();
        });
    alertDialog.create().show();
  }

  @Override
  public void onActivityResult(int requestCode, int resultCode, Intent data) {
    switch (requestCode) {
      case ActivityRequestCodes.REQUEST_ACCOUNT_SWITCHER:
        if (resultCode == Activity.RESULT_OK) {
          if (accountsProvider.isSignedIn()) {
            afterSignIn();
          }
        } else if (resultCode == AccountsProvider.RESULT_ACCOUNT_NOT_PERMITTED) {
          showScienceJournalIsDisabledAlert();
        }
        break;
      case ActivityRequestCodes.REQUEST_OLD_USER_OPTION_PROMPT_ACTIVITY:
        if (resultCode == Activity.RESULT_OK) {
          OldUserOptionPromptActivity.setShouldLaunch(getContext(), false);
          finish();
        } else {
          // User hit the back button in OldUserOptionPromptActivity.
          accountsProvider.undoSignIn();
        }
        break;
      default:
        break;
    }
  }

  private void signInClicked() {
    WhistlePunkApplication.getUsageTracker(getActivity())
        .trackEvent(
            TrackerConstants.CATEGORY_SIGN_IN,
            TrackerConstants.ACTION_SIGN_IN_FROM_WELCOME,
            null,
            0);
    showAccountSwitcherDialog();
  }

  private void showAccountSwitcherDialog() {
    if (accountsProvider.isSignedIn()) {
      // This shouldn't happen, but if it does, just make this screen go away.
      afterSignIn();
      return;
    }

    if (accountsProvider.getAccountCount() > 0) {
      // If there is one or more accounts on the device, show the account switcher dialog.
      accountsProvider.showAccountSwitcherDialog(
          this, ActivityRequestCodes.REQUEST_ACCOUNT_SWITCHER);
    } else {
      // If there are no accounts on the device, show the add account dialog.
      accountsProvider.showAddAccountDialog(getActivity());
    }
  }

  private void afterSignIn() {
    Context context = getContext();
    if (OldUserOptionPromptActivity.shouldLaunch(context)) {
      Intent intent = new Intent(context, OldUserOptionPromptActivity.class);
      startActivityForResult(intent, ActivityRequestCodes.REQUEST_OLD_USER_OPTION_PROMPT_ACTIVITY);
    } else {
      finish();
    }
  }

  private void learnMoreClicked() {
    WhistlePunkApplication.getUsageTracker(getActivity())
        .trackEvent(TrackerConstants.CATEGORY_SIGN_IN, TrackerConstants.ACTION_LEARN_MORE, null, 0);
    startActivity(
        new Intent(Intent.ACTION_VIEW, Uri.parse(getString(R.string.sign_in_learn_more_url))));
  }

  private void continueWithoutSigningInClicked() {
    WhistlePunkApplication.getUsageTracker(getActivity())
        .trackEvent(
            TrackerConstants.CATEGORY_SIGN_IN,
            TrackerConstants.ACTION_CONTINUE_WITHOUT_ACCOUNT_FROM_WELCOME,
            null,
            0);
    finish();
  }

  private void finish() {
    Activity activity = getActivity();
    accountsProvider.setShowSignInActivityIfNotSignedIn(false);
    activity.setResult(Activity.RESULT_OK);
    activity.finish();
  }
}
