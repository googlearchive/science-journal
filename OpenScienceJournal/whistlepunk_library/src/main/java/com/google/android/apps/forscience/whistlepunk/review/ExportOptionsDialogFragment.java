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

import android.content.Intent;
import android.os.Bundle;
import androidx.annotation.Nullable;
import android.support.design.widget.BottomSheetDialogFragment;
import android.support.design.widget.Snackbar;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.ProgressBar;
import com.google.android.apps.forscience.whistlepunk.AccessibilityUtils;
import com.google.android.apps.forscience.whistlepunk.AppSingleton;
import com.google.android.apps.forscience.whistlepunk.DataService;
import com.google.android.apps.forscience.whistlepunk.ExportService;
import com.google.android.apps.forscience.whistlepunk.ExportService.ExportProgress;
import com.google.android.apps.forscience.whistlepunk.R;
import com.google.android.apps.forscience.whistlepunk.RxDataController;
import com.google.android.apps.forscience.whistlepunk.filemetadata.Trial;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;
import java.util.List;
import java.util.Objects;

/** Shows options for exporting. */
public class ExportOptionsDialogFragment extends BottomSheetDialogFragment {

  private static final String KEY_EXPERIMENT_ID = "experiment_id";
  private static final String KEY_TRIAL_ID = "trial_id";
  private String trialId;
  private CheckBox relativeTime;
  private List<String> sensorIds;
  private ProgressBar progressBar;
  private Button exportButton;
  private Disposable untilStop;

  public static ExportOptionsDialogFragment createOptionsDialog(
      String experimentId, String trialId) {
    Bundle args = new Bundle();
    args.putString(KEY_EXPERIMENT_ID, experimentId);
    args.putString(KEY_TRIAL_ID, trialId);
    ExportOptionsDialogFragment fragment = new ExportOptionsDialogFragment();
    fragment.setArguments(args);
    return fragment;
  }

  @Override
  public void onStart() {
    super.onStart();
    trialId = getArguments().getString(KEY_TRIAL_ID);
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
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("application/octet-stream");
        intent.putExtra(Intent.EXTRA_STREAM, progress.getFileUri());
        if (getActivity().getPackageManager().queryIntentActivities(intent, 0).size() > 0) {
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
    } else if (progress.getState() == ExportProgress.ERROR) {
      if (getActivity() != null) {
        Snackbar bar =
            AccessibilityUtils.makeSnackbar(
                getView(), getString(R.string.export_error), Snackbar.LENGTH_LONG);
        bar.show();
      }
    }
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
    final String experimentId = getArguments().getString(KEY_EXPERIMENT_ID);
    final String trialId = getArguments().getString(KEY_TRIAL_ID);
    DataService.bind(getActivity())
        .map(AppSingleton::getDataController)
        .flatMap(dc -> RxDataController.getExperimentById(dc, experimentId))
        .subscribe(
            experiment -> {
              Trial trial = experiment.getTrial(trialId);
              sensorIds = trial.getSensorIds();
              // TODO: fill in UI with these sensors.
            });
    exportButton = (Button) view.findViewById(R.id.action_export);
    exportButton.setOnClickListener(
        v -> {
          ExportService.exportTrial(
              getActivity(),
              experimentId,
              trialId,
              relativeTime.isChecked(),
              sensorIds.toArray(new String[] {}));
        });
    return view;
  }
}
