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

import android.app.Dialog;
import android.app.Fragment;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.design.widget.BottomSheetDialogFragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;

import com.google.android.apps.forscience.whistlepunk.R;

/**
 * Shows options for exporting.
 */
public class ExportOptionsDialogFragment extends BottomSheetDialogFragment {

    private static final String KEY_EXPERIMENT_TITLE = "experiment_title";
    private static final String KEY_TRIAL_TITLE = "trial_title";
    private CheckBox mRelativeTime;

    public interface Exporter {
        void onExport(boolean startAtZero);
    }

    public static ExportOptionsDialogFragment createOptionsDialog(String experimentTitle,
            String trialTitle) {
        Bundle args = new Bundle();
        args.putString(KEY_EXPERIMENT_TITLE, experimentTitle);
        args.putString(KEY_TRIAL_TITLE, trialTitle);
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
        view.findViewById(R.id.action_export).setOnClickListener(v -> {
            // TODO: remove this when moving to the service: just kick off intentservice instead.
            Fragment fragment = getActivity().getFragmentManager().findFragmentById(R.id.container);
            if (fragment instanceof Exporter) {
                ((Exporter) fragment).onExport(mRelativeTime.isChecked());
            }
            dismiss();
        });
        return view;
    }
}
