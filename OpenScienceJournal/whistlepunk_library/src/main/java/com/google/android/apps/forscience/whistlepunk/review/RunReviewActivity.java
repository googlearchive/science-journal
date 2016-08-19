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
import android.support.v7.app.AppCompatActivity;
import android.view.Menu;
import android.view.MenuItem;

import com.google.android.apps.forscience.whistlepunk.DevOptionsFragment;
import com.google.android.apps.forscience.whistlepunk.PictureUtils;
import com.google.android.apps.forscience.whistlepunk.R;
import com.google.android.apps.forscience.whistlepunk.intro.AgeVerifier;
import com.google.android.apps.forscience.whistlepunk.project.MetadataActivity;

public class RunReviewActivity extends MetadataActivity {
    public static final String EXTRA_FROM_RECORD = "from_record_activity";
    public static final String EXTRA_CREATE_TASK = "create_task";
    private boolean mFromRecord;

    /**
     * Launches a new run review activity
     * @param context
     * @param startLabelId The ID of the start run label.
     * @param sensorId The ID of the sensor which ought to be displayed first.
     * @param fromRecord Whether we reached the RunReview activity from recording directly or
     *                   from another part of the app.
     * @param options  Options bundle for launch
     */
    public static void launch(Context context, String startLabelId, int activeSensorIndex,
                              boolean fromRecord, boolean createTask, Bundle options) {
        // TODO(saff): fancy schmancy material transition here (see specs)
        final Intent intent = new Intent(context, RunReviewActivity.class);
        intent.putExtra(RunReviewFragment.ARG_START_LABEL_ID, startLabelId);
        intent.putExtra(RunReviewFragment.ARG_SENSOR_INDEX, activeSensorIndex);
        intent.putExtra(EXTRA_FROM_RECORD, fromRecord);
        intent.putExtra(EXTRA_CREATE_TASK, createTask);
        context.startActivity(intent, options);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_run_review);
        mFromRecord = getIntent().getExtras().getBoolean(EXTRA_FROM_RECORD, false);
        boolean createTask = getIntent().getExtras().getBoolean(EXTRA_CREATE_TASK, true);
        supportPostponeEnterTransition();
        if (savedInstanceState == null) {
            RunReviewFragment fragment = RunReviewFragment.newInstance(
                    getIntent().getExtras().getString(RunReviewFragment.ARG_START_LABEL_ID),
                    getIntent().getExtras().getInt(RunReviewFragment.ARG_SENSOR_INDEX),
                    createTask /* create a task when going up */);
            getFragmentManager().beginTransaction().add(R.id.container, fragment)
                    .commit();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_run_review, menu);
        boolean enableDevTools = DevOptionsFragment.isDevToolsEnabled(this);
        menu.findItem(R.id.action_export).setVisible(AgeVerifier.isOver13(
                AgeVerifier.getUserAge(this)));
        menu.findItem(R.id.action_graph_options).setVisible(false);  // b/29771945

        // Delete is only available if we came from record mode directly.
        menu.findItem(R.id.action_run_review_delete).setVisible(mFromRecord);

        // TODO: Re-enable this when ready to implement the functionality.
        menu.findItem(R.id.action_run_review_crop).setVisible(false);

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String permissions[],
                                           int[] grantResults) {
        PictureUtils.onRequestPermissionsResult(requestCode, permissions, grantResults, this);
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
            EditTimeDialog editTimeDialog = (EditTimeDialog) fragment.getChildFragmentManager()
                    .findFragmentByTag(EditTimeDialog.TAG);
            if (editTimeDialog != null) {
                editTimeDialog.dismiss();
                return;
            }
        }
        super.onBackPressed();
    }
}
