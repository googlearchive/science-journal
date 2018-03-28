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

package com.google.android.apps.forscience.whistlepunk.devicemanager;

import android.app.Activity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;
import android.bluetooth.BluetoothAdapter;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import android.view.Menu;

import com.google.android.apps.forscience.javalib.Success;
import com.google.android.apps.forscience.whistlepunk.AppSingleton;
import com.google.android.apps.forscience.whistlepunk.DataController;
import com.google.android.apps.forscience.whistlepunk.LoggingConsumer;
import com.google.android.apps.forscience.whistlepunk.R;
import com.google.android.apps.forscience.whistlepunk.WhistlePunkApplication;
import com.google.android.apps.forscience.whistlepunk.analytics.TrackerConstants;
import com.google.android.apps.forscience.whistlepunk.filemetadata.Experiment;


public class ManageDevicesActivity extends AppCompatActivity implements
        DeviceOptionsDialog.DeviceOptionsListener {

    private static final String TAG = "ManageDevices";

    /**
     * String extra which stores the experiment ID that launched this activity.
     */
    public static final String EXTRA_EXPERIMENT_ID = "experiment_id";

    private BroadcastReceiver mBtReceiver;
    private DataController mDataController;
    private ManageDevicesRecyclerFragment mManageFragment;
    private Experiment mCurrentExperiment;

    public static DeviceOptionsDialog.DeviceOptionsListener getOptionsListener(Activity activity) {
        if (activity instanceof DeviceOptionsDialog.DeviceOptionsListener) {
            return (DeviceOptionsDialog.DeviceOptionsListener) activity;
        } else {
            return DeviceOptionsDialog.NULL_LISTENER;
        }
    }

    public static void launch(Context context, String experimentId) {
        context.startActivity(launchIntent(context, experimentId));
    }

    @NonNull
    public static Intent launchIntent(Context context, String experimentId) {
        Intent intent = new Intent(context, ManageDevicesActivity.class);
        if (experimentId != null) {
            intent.putExtra(EXTRA_EXPERIMENT_ID, experimentId);
        }
        return intent;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_manage_devices);
        mDataController = AppSingleton.getInstance(this).getDataController();
    }

    @Override
    protected void onStart() {
        super.onStart();
        WhistlePunkApplication.getUsageTracker(this).trackScreenView(
                TrackerConstants.SCREEN_DEVICE_MANAGER);
    }

    @Override
    protected void onResume() {
        super.onResume();
        setupFragment();
        // Set up a broadcast receiver in case the adapter is disabled from the notification shade.
        registerBtReceiverIfNecessary();
        String experimentId = getIntent().getStringExtra(EXTRA_EXPERIMENT_ID);
        mDataController.getExperimentById(experimentId,
                new LoggingConsumer<Experiment>(TAG, "load experiment with ID = " + experimentId) {
                    @Override
                    public void success(Experiment value) {
                        mCurrentExperiment = value;
                    }
                });
    }

    @Override
    protected void onPause() {
        unregisterBtReceiverIfNecessary();
        super.onPause();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        return true;
    }

    private void setupFragment() {
        FragmentManager fragmentManager = getSupportFragmentManager();
        Fragment fragmentById = fragmentManager.findFragmentById(R.id.fragment);
        if (fragmentById != null) {
            mManageFragment = (ManageDevicesRecyclerFragment) fragmentById;
        } else {
            mManageFragment = new ManageDevicesRecyclerFragment();
            Bundle args = new Bundle();
            args.putString(EXTRA_EXPERIMENT_ID, getIntent().getStringExtra(EXTRA_EXPERIMENT_ID));
            mManageFragment.setArguments(args);
            FragmentTransaction ft = fragmentManager.beginTransaction();
            ft.replace(R.id.fragment, mManageFragment);
            ft.commitAllowingStateLoss();
        }
    }

    private void registerBtReceiverIfNecessary() {
        if (mBtReceiver == null) {
            mBtReceiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    setupFragment();
                }
            };
            IntentFilter filter = new IntentFilter();
            filter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);
            registerReceiver(mBtReceiver, filter);
        }
    }

    private void unregisterBtReceiverIfNecessary() {
        if (mBtReceiver != null) {
            unregisterReceiver(mBtReceiver);
            mBtReceiver = null;
        }
    }

    @Override
    public void onExperimentSensorReplaced(String oldSensorId, String newSensorId) {
        refreshAfterLoad();
    }

    @Override
    public void onRemoveSensorFromExperiment(String experimentId, final String sensorId) {
        if (mCurrentExperiment != null && mCurrentExperiment.getExperimentId().equals(
                experimentId)) {
            removeSensorFromExperiment(sensorId);
        }
    }

    private void removeSensorFromExperiment(String sensorId) {
        mDataController.removeSensorFromExperiment(mCurrentExperiment.getExperimentId(), sensorId,
                new LoggingConsumer<Success>(TAG, "remove sensor from experiment") {
                    @Override
                    public void success(Success value) {
                    }
                });
    }

    private void refreshAfterLoad() {
        if (mManageFragment != null) {
            mManageFragment.refreshAfterLoad();
        }
    }
}
