/*
 *  Copyright 2016 Google Inc. All Rights Reserved.
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
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import com.google.android.apps.forscience.whistlepunk.PermissionUtils;
import com.google.android.apps.forscience.whistlepunk.PictureUtils;
import com.google.android.apps.forscience.whistlepunk.R;
import com.google.android.apps.forscience.whistlepunk.RecorderService;
import com.google.android.apps.forscience.whistlepunk.WhistlePunkApplication;
import com.google.android.apps.forscience.whistlepunk.filemetadata.Label;
import com.google.android.apps.forscience.whistlepunk.performance.PerfTrackerProvider;
import com.google.android.apps.forscience.whistlepunk.project.MetadataActivity;
import com.google.android.apps.forscience.whistlepunk.project.experiment.ExperimentDetailsFragment;

public class RunReviewActivity extends MetadataActivity {
    public static final String EXTRA_FROM_RECORD = "from_record_activity";
    public static final String EXTRA_CREATE_TASK = "create_task";
    private static final String FRAGMENT_TAG = "fragment";
    private boolean mFromRecord;

    /**
     * Launches a new run review activity
     * @param context
     * @param startLabelId The ID of the start run label.
     * @param activeSensorIndex The index of the sensor which ought to be displayed first.
     * @param fromRecord Whether we reached the RunReview activity from recording directly or
     *                   from another part of the app.
     * @param options  Options bundle for launch
     */
    public static void launch(Context context, String startLabelId, String experimentId,
            int activeSensorIndex, boolean fromRecord, boolean createTask, Bundle options) {
        // TODO(saff): fancy schmancy material transition here (see specs)
        final Intent intent = new Intent(context, RunReviewActivity.class);
        intent.putExtra(RunReviewFragment.ARG_EXPERIMENT_ID, experimentId);
        intent.putExtra(RunReviewFragment.ARG_START_LABEL_ID, startLabelId);
        intent.putExtra(RunReviewFragment.ARG_SENSOR_INDEX, activeSensorIndex);
        intent.putExtra(EXTRA_FROM_RECORD, fromRecord);
        intent.putExtra(EXTRA_CREATE_TASK, createTask);
        context.startActivity(intent, options);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WhistlePunkApplication.getPerfTrackerProvider(this).onActivityInit();
        setContentView(R.layout.activity_run_review);
        mFromRecord = getIntent().getExtras().getBoolean(EXTRA_FROM_RECORD, false);
        boolean createTask = getIntent().getExtras().getBoolean(EXTRA_CREATE_TASK, true);
        if (getFragmentManager().findFragmentByTag(FRAGMENT_TAG) == null) {
            RunReviewFragment fragment = RunReviewFragment.newInstance(
                    getIntent().getExtras().getString(RunReviewFragment.ARG_EXPERIMENT_ID),
                    getIntent().getExtras().getString(RunReviewFragment.ARG_START_LABEL_ID),
                    getIntent().getExtras().getInt(RunReviewFragment.ARG_SENSOR_INDEX),
                    createTask /* create a task when going up */);
            getFragmentManager().beginTransaction().add(R.id.container, fragment, FRAGMENT_TAG)
                    .commit();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        RecorderService.clearRecordingCompletedNotification(getApplicationContext());
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String permissions[],
                                           int[] grantResults) {
        PermissionUtils.onRequestPermissionsResult(this, requestCode, permissions,
                grantResults);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        Fragment fragment = getFragmentManager().findFragmentById(R.id.container);
        if (fragment != null) {
            fragment.onActivityResult(requestCode, resultCode, data);
        }
    }

    @Override
    public void onBackPressed() {
        Fragment fragment = getFragmentManager().findFragmentById(R.id.container);
        if (fragment != null) {
            // If the edit time dialog is showing, make it hide on back pressed.
            EditLabelTimeDialog editLabelTimeDialog =
                    (EditLabelTimeDialog) fragment.getChildFragmentManager()
                            .findFragmentByTag(EditLabelTimeDialog.TAG);
            if (editLabelTimeDialog != null) {
                editLabelTimeDialog.dismiss();
                return;
            }
        }
        super.onBackPressed();
    }

    boolean isFromRecord() {
        return mFromRecord;
    }
}
