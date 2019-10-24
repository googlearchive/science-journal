/*
 *  Copyright 2017 Google Inc. All Rights Reserved.
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

package com.google.android.apps.forscience.whistlepunk.review;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import androidx.annotation.Nullable;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.ProgressBar;
import android.widget.TextView;
import com.google.android.apps.forscience.whistlepunk.AccessibilityUtils;
import com.google.android.apps.forscience.whistlepunk.DataService;
import com.google.android.apps.forscience.whistlepunk.ExportService;
import com.google.android.apps.forscience.whistlepunk.ExportService.ExportProgress;
import com.google.android.apps.forscience.whistlepunk.R;
import com.google.android.apps.forscience.whistlepunk.RxDataController;
import com.google.android.apps.forscience.whistlepunk.WhistlePunkApplication;
import com.google.android.apps.forscience.whistlepunk.accounts.AppAccount;
import com.google.android.apps.forscience.whistlepunk.analytics.TrackerConstants;
import com.google.android.apps.forscience.whistlepunk.filemetadata.Trial;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.snackbar.Snackbar;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;
import java.util.List;
import java.util.Objects;

/** Shows options for exporting. */
public class ExportOptionsDialogFragment extends BottomSheetDialogFragment {

  private static final String KEY_ACCOUNT_KEY = "account_key";
  private static final String KEY_EXPERIMENT_ID = "experiment_id";
  private static final String KEY_TRIAL_ID = "trial_id";
  private static final String KEY_SAVE_LOCALLY = "save_locally";
  private static final String TAG = "ExportOptionsDialog";
  private String trialId;
  private boolean saveLocally;
  private CheckBox relativeTime;
  private List<String> sensorIds;
  private ProgressBar progressBar;
  private Button exportButton;
  private Disposable untilStop;

  public static ExportOptionsDialogFragment createOptionsDialog(
      AppAccount appAccount, String experimentId, String trialId, boolean saveLocally) {
    Bundle args = new Bundle();
    args.putString(KEY_ACCOUNT_KEY, appAccount.getAccountKey());
    args.putString(KEY_EXPERIMENT_ID, experimentId);
    args.putString(KEY_TRIAL_ID, trialId);
    args.putBoolean(KEY_SAVE_LOCALLY, saveLocally);
    ExportOptionsDialogFragment fragment = new ExportOptionsDialogFragment();
    fragment.setArguments(args);
    return fragment;
  }

  @Override
  public void onStart() {
    super.onStart();
    trialId = getArguments().getString(KEY_TRIAL_ID);
    saveLocally = getArguments().getBoolean(KEY_SAVE_LOCALLY);
    untilStop =
        ExportService.bind(getActivity())
            // Only look at events for this trial or the default value
            .filter(
                progress ->
                    Objects.equals(progress.getId(), trialId) || progress.getId().equals(""))
            .observeOn(AndroidSchedulers.mainThread())
            .doOnNext(
                progress -> {
                  if (progress.getState() == ExportProgress.EXPORT_COMPLETE) {
                    // Reset the progress only after the UI has consumed this.
                    ExportService.resetProgress(trialId);
                  }
                })
            .subscribe(this::updateProgress);
  }

  private void updateProgress(ExportProgress progress) {
    progressBar.setVisibility(
        progress.getState() == ExportProgress.EXPORTING ? View.VISIBLE : View.INVISIBLE);
    exportButton.setEnabled(progress.getState() != ExportProgress.EXPORTING);
    if (progress.getState() == ExportProgress.EXPORTING) {
      progressBar.setProgress(progress.getProgress());
    } else if (progress.getState() == ExportProgress.EXPORT_COMPLETE) {
      // Finish dialog and send the filename.
      if (getActivity() != null) {
        if (saveLocally) {
          requestDownload(progress);
        } else {
          requestExport(progress);
        }
      }
    } else if (progress.getState() == ExportProgress.ERROR) {
      if (getActivity() != null) {
        Snackbar bar =
            AccessibilityUtils.makeSnackbar(
                getView(), getString(R.string.export_error), Snackbar.LENGTH_LONG);
        bar.show();
      }
    }
  }

  private void requestExport(ExportProgress progress) {
    Intent intent = new Intent(Intent.ACTION_SEND);
    intent.setType("application/octet-stream");
    intent.putExtra(Intent.EXTRA_STREAM, progress.getFileUri());
    if (!getActivity().getPackageManager().queryIntentActivities(intent, 0).isEmpty()) {
      getActivity()
          .startActivity(
              Intent.createChooser(intent, getString(R.string.export_run_chooser_title)));
      dismiss();
    } else {
      Snackbar bar =
          AccessibilityUtils.makeSnackbar(
              getView(), getString(R.string.no_app_found_for_csv), Snackbar.LENGTH_LONG);
      bar.show();
    }
  }

  private void requestDownload(ExportProgress progress) {
    Activity activity = getActivity();
    ExportService.requestDownloadPermissions(
        () -> {
          Uri sourceUri = progress.getFileUri();
          ExportService.saveToDownloads(activity, sourceUri);
          dismiss();
        },
        activity,
        android.R.id.content,
        TrackerConstants.CATEGORY_RUNS,
        TrackerConstants.LABEL_RUN_REVIEW);
  }

  @Override
  public void onStop() {
    super.onStop();
    if (untilStop != null) {
      untilStop.dispose();
    }
  }

  @Nullable
  @Override
  public View onCreateView(
      LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
    View view = inflater.inflate(R.layout.dialog_export_options, container, false);
    relativeTime = (CheckBox) view.findViewById(R.id.export_relative_time);
    progressBar = (ProgressBar) view.findViewById(R.id.progress);
    progressBar.setMax(100);
    view.findViewById(R.id.action_cancel)
        .setOnClickListener(
            v -> {
              // TODO: could cancel the export action here: requires saving references in
              // ExportService.
              dismiss();
            });
    AppAccount appAccount =
        WhistlePunkApplication.getAccount(getContext(), getArguments(), KEY_ACCOUNT_KEY);
    final String experimentId = getArguments().getString(KEY_EXPERIMENT_ID);
    // onCreateView is called before onStart so we need to grab these values for onCreateView
    final String trialId = getArguments().getString(KEY_TRIAL_ID);
    final boolean saveLocally = getArguments().getBoolean(KEY_SAVE_LOCALLY);
    DataService.bind(getActivity())
        .map(
            appSingleton -> {
              return appSingleton.getDataController(appAccount);
            })
        .flatMap(dc -> RxDataController.getExperimentById(dc, experimentId))
        .subscribe(
            experiment -> {
              Trial trial = experiment.getTrial(trialId);
              sensorIds = trial.getSensorIds();
              // TODO: fill in UI with these sensors.
            },
            error -> {
              if (Log.isLoggable(TAG, Log.ERROR)) {
                Log.e(TAG, "Unable to bind DataService in ExportOptionsDialogFragment", error);
              }
              throw new IllegalStateException(
                  "Unable to bind DataService in ExportOptionsDialogFragment", error);
            });
    exportButton = (Button) view.findViewById(R.id.action_export);
    if (saveLocally) {
      TextView title = (TextView) view.findViewById(R.id.export_title);
      title.setText(R.string.download_options_title);
      exportButton.setText(R.string.download_copy_action);
    }
    exportButton.setOnClickListener(
        v -> {
          ExportService.exportTrial(
              getActivity(),
              appAccount,
              experimentId,
              trialId,
              relativeTime.isChecked(),
              saveLocally,
              sensorIds.toArray(new String[] {}));
        });

    return view;
  }
}
