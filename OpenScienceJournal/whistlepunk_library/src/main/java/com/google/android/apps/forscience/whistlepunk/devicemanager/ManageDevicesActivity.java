package com.google.android.apps.forscience.whistlepunk.devicemanager;

import android.Manifest;
import android.app.Fragment;
import android.app.FragmentTransaction;
import android.bluetooth.BluetoothAdapter;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.view.Menu;

import com.google.android.apps.forscience.javalib.Success;
import com.google.android.apps.forscience.whistlepunk.AppSingleton;
import com.google.android.apps.forscience.whistlepunk.DataController;
import com.google.android.apps.forscience.whistlepunk.LoggingConsumer;
import com.google.android.apps.forscience.whistlepunk.R;
import com.google.android.apps.forscience.whistlepunk.WhistlePunkApplication;
import com.google.android.apps.forscience.whistlepunk.analytics.TrackerConstants;
import com.google.android.apps.forscience.whistlepunk.metadata.Experiment;
import com.google.android.apps.forscience.whistlepunk.metadata.ExternalSensorSpec;

import java.util.Map;


public class ManageDevicesActivity extends AppCompatActivity implements
        DeviceOptionsDialog.DeviceOptionsListener {

    private static final String TAG = "ManageDevices";

    /**
     * String extra which stores the experiment ID that launched this activity.
     */
    public static final String EXTRA_EXPERIMENT_ID = "experiment_id";

    private BroadcastReceiver mBtReceiver;
    private DataController mDataController;
    private ManageDevicesFragment mManageFragment;
    private Experiment mCurrentExperiment;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_manage_devices);
        mDataController = AppSingleton.getInstance(this).getDataController();
        if (!ScanDisabledFragment.hasScanPermission(this)
                && !ActivityCompat.shouldShowRequestPermissionRationale(this,
                Manifest.permission.ACCESS_COARSE_LOCATION)) {
            ActivityCompat.requestPermissions(this,
                    new String[] {Manifest.permission.ACCESS_COARSE_LOCATION}, 0);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        setFragment();
        // Set up a broadcast receiver in case the adapter is disabled from the notification shade.
        registerBtReceiverIfNecessary();
        final String experimentId = getIntent().getStringExtra(EXTRA_EXPERIMENT_ID);
        WhistlePunkApplication.getUsageTracker(this).trackScreenView(
                TrackerConstants.SCREEN_DEVICE_MANAGER);
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

    private void setFragment() {
        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        Fragment fragment;
        if (adapter.isEnabled() && ScanDisabledFragment.hasScanPermission(this)) {
            mManageFragment = new ManageDevicesFragment();
            fragment = mManageFragment;
        } else {
            fragment = new ScanDisabledFragment();
            mManageFragment = null;
        }
        Bundle args = new Bundle();
        args.putString(EXTRA_EXPERIMENT_ID, getIntent().getStringExtra(EXTRA_EXPERIMENT_ID));
        fragment.setArguments(args);
        FragmentTransaction ft = getFragmentManager().beginTransaction();
        ft.replace(R.id.fragment, fragment);
        ft.commitAllowingStateLoss();
    }

    private void registerBtReceiverIfNecessary() {
        if (mBtReceiver == null) {
            mBtReceiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    setFragment();
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
        if (mManageFragment != null) {
            mManageFragment.refreshAfterLoad();
        }
    }

    @Override
    public void onRemoveDeviceFromExperiment(String experimentId, final String address) {
        if (mCurrentExperiment != null &&
                mCurrentExperiment.getExperimentId().equals(experimentId)) {
            // Need to get the sensor object which is a little silly since we're only going to use
            // it to get the sensor ID again.
            // TODO: change the sensor adding / removing to just use strings?
            mDataController.getExternalSensorsByExperiment(mCurrentExperiment.getExperimentId(),
                    new LoggingConsumer<Map<String, ExternalSensorSpec>>(TAG,
                            "load external sensors") {
                        @Override
                        public void success(Map<String, ExternalSensorSpec> map) {


                            // Loop through searching, then remove.
                            for (Map.Entry<String, ExternalSensorSpec> entry : map.entrySet()) {
                                if (entry.getValue().getAddress().equals(address)) {
                                    removeSensorFromExperiment(entry.getKey());
                                    break;
                                }
                            }
                        }
                    });
        }
    }

    private void removeSensorFromExperiment(String sensorId) {
        mDataController.removeSensorFromExperiment(mCurrentExperiment.getExperimentId(), sensorId,
                new LoggingConsumer<Success>(TAG, "remove sensor from experiment") {
                    @Override
                    public void success(Success value) {
                        if (mManageFragment != null) {
                            mManageFragment.refreshAfterLoad();
                        }
                    }
                });
    }
}
