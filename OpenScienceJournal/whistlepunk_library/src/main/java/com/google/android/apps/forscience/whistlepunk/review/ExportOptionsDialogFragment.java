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

import android.app.Fragment;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.design.widget.BottomSheetDialogFragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;

import com.google.android.apps.forscience.whistlepunk.AppSingleton;
import com.google.android.apps.forscience.whistlepunk.DataService;
import com.google.android.apps.forscience.whistlepunk.ExportService;
import com.google.android.apps.forscience.whistlepunk.R;
import com.google.android.apps.forscience.whistlepunk.RxDataController;
import com.google.android.apps.forscience.whistlepunk.filemetadata.Trial;

import java.util.List;


/**
 * Shows options for exporting.
 */
public class ExportOptionsDialogFragment extends BottomSheetDialogFragment {

    private static final boolean DEBUG_USE_SERVICE = false;

    private static final String KEY_EXPERIMENT_ID = "experiment_id";
    private static final String KEY_TRIAL_ID = "trial_id";
    private CheckBox mRelativeTime;
    private List<String> mSensorIds;

    public interface Exporter {
        void onExport(boolean startAtZero);
    }

    public static ExportOptionsDialogFragment createOptionsDialog(String experimentId,
            String trialId) {
        Bundle args = new Bundle();
        args.putString(KEY_EXPERIMENT_ID, experimentId);
        args.putString(KEY_TRIAL_ID, trialId);
        ExportOptionsDialogFragment fragment = new ExportOptionsDialogFragment();
        fragment.setArguments(args);
        return fragment;
    }

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.dialog_export_options, container, false);
        mRelativeTime = (CheckBox) view.findViewById(R.id.export_relative_time);
        view.findViewById(R.id.action_cancel).setOnClickListener(v -> {
            dismiss();
        });
        final String experimentId = getArguments().getString(KEY_EXPERIMENT_ID);
        final String trialId = getArguments().getString(KEY_TRIAL_ID);
        DataService.bind(getActivity()).map(AppSingleton::getDataController)
                .flatMap(dc -> RxDataController.getExperimentById(dc, experimentId))
                .subscribe(experiment -> {
                    Trial trial = experiment.getTrial(trialId);
                    mSensorIds = trial.getSensorIds();
                });
        view.findViewById(R.id.action_export).setOnClickListener(v -> {

            if (DEBUG_USE_SERVICE) {
                ExportService.exportTrial(getActivity(), experimentId, trialId,
                        mRelativeTime.isChecked(), mSensorIds.toArray(new String[]{}));
            } else {
                // TODO: remove this when moving to the service: just kick off intentservice
                // instead.
                Fragment fragment = getActivity().getFragmentManager().findFragmentById(
                        R.id.container);
                if (fragment instanceof Exporter) {
                    ((Exporter) fragment).onExport(mRelativeTime.isChecked());
                }
            }
            dismiss();
        });
        return view;
    }
}
