package com.google.android.apps.forscience.whistlepunk.devicemanager;

import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.preference.Preference;
import android.preference.PreferenceCategory;
import android.preference.PreferenceFragment;
import android.preference.PreferenceScreen;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;

import com.google.android.apps.forscience.ble.DeviceDiscoverer;
import com.google.android.apps.forscience.ble.DeviceDiscoverer.DeviceRecord;
import com.google.android.apps.forscience.javalib.Success;
import com.google.android.apps.forscience.whistlepunk.AppSingleton;
import com.google.android.apps.forscience.whistlepunk.DataController;
import com.google.android.apps.forscience.whistlepunk.LoggingConsumer;
import com.google.android.apps.forscience.whistlepunk.PreferenceProgressCategory;
import com.google.android.apps.forscience.whistlepunk.R;
import com.google.android.apps.forscience.whistlepunk.WhistlePunkApplication;
import com.google.android.apps.forscience.whistlepunk.metadata.BleSensorSpec;
import com.google.android.apps.forscience.whistlepunk.metadata.ExternalSensorSpec;
import com.squareup.leakcanary.RefWatcher;

import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Searches for Bluetooth LE devices that are supported.
 */
public class ManageDevicesFragment extends PreferenceFragment {

    private static final String TAG = "ManageDevices";

    // STOPSHIP: set this to false.
    private static final boolean LOCAL_LOGD = true;

    private static final int MSG_STOP_SCANNING = 10001;
    private static final long SCAN_TIME_MS = TimeUnit.SECONDS.toMillis(10);

    /**
     * Boolean extra set on the preference when the device in question is paired.
     */
    private static final String EXTRA_KEY_PAIRED = "paired";
    private static final String EXTRA_KEY_ID = "id";
    private static final String PREF_KEY_PAIRED_DEVICES = "paired_devices";
    private static final String PREF_KEY_AVAILABLE_DEVICES = "available_devices";

    private PreferenceCategory mPairedDevices;
    private PreferenceProgressCategory mAvailableDevices;
    private Menu mMainMenu;

    private boolean mScanning;
    private Handler mHandler;
    private DeviceDiscoverer mDeviceDiscoverer;
    private DataController mDataController;

    private DeviceDiscoverer.Callback mCallback;

    private String mExperimentId;

    public ManageDevicesFragment() {
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mHandler = new Handler(new Handler.Callback() {
            @Override
            public boolean handleMessage(Message msg) {
                if (msg.what == MSG_STOP_SCANNING) {
                    stopScanning();
                    return true;
                }
                return false;
            }
        });
        mCallback = new DeviceDiscoverer.Callback() {
            @Override
            public void onDeviceFound(final DeviceRecord record) {
                if (getActivity() != null) {
                    onDeviceRecordFound(record);
                }
            }

            @Override
            public void onError(int error) {
                // TODO: handle errors
            }
        };
        mDeviceDiscoverer = DeviceDiscoverer.getNewInstance(getActivity());
        mDataController = AppSingleton.getInstance(getActivity()).getDataController();
        addPreferencesFromResource(R.xml.external_devices);
        mPairedDevices = new PreferenceProgressCategory(getActivity());
        mPairedDevices.setKey(PREF_KEY_PAIRED_DEVICES);
        mPairedDevices.setTitle(R.string.external_devices_paired);
        mPairedDevices.setOrder(0);
        mPairedDevices.setSelectable(false);
        mAvailableDevices = new PreferenceProgressCategory(getActivity());
        mAvailableDevices.setKey(PREF_KEY_AVAILABLE_DEVICES);
        mAvailableDevices.setTitle(R.string.external_devices_available);
        mAvailableDevices.setOrder(1);
        mAvailableDevices.setSelectable(false);
        getPreferenceScreen().addPreference(mAvailableDevices);
        setHasOptionsMenu(true);


    }

    @Override
    public void onResume() {
        super.onResume();
        refreshAfterLoad();
    }

    @Override
    public void onPause() {
        stopScanning();
        super.onPause();
    }

    @Override
    public void onDestroy() {
        mDeviceDiscoverer.destroy();
        mDeviceDiscoverer = null;
        mCallback = null;
        mMainMenu = null;
        super.onDestroy();

        // Make sure we don't leak this fragment.
        RefWatcher watcher = WhistlePunkApplication.getRefWatcher(getActivity());
        watcher.watch(this);
    }

    @Override
    public boolean onPreferenceTreeClick(PreferenceScreen preferenceScreen, Preference preference) {
        if (preference.getKey() != null) {
            if (!preference.getExtras().getBoolean(EXTRA_KEY_PAIRED)) {
                addExternalSensorIfNecessary(preference);
            } else {
                showDeviceOptions(preference);
            }
            return true;
        }
        return false;
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.menu_manage_devices, menu);
        super.onCreateOptionsMenu(menu, inflater);
        mMainMenu = menu;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.action_refresh) {
            refresh();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    public void refreshAfterLoad() {
        mExperimentId = getArguments().getString(ManageDevicesActivity.EXTRA_EXPERIMENT_ID);
        refresh();
    }

    private void refresh() {
        stopScanning();
        mDataController.getExternalSensorsByExperiment(mExperimentId,
                new LoggingConsumer<Map<String, ExternalSensorSpec>>(TAG, "Load external sensors") {
                    @Override
                    public void success(Map<String, ExternalSensorSpec> sensors) {
                        if (getActivity() == null) {
                            return;
                        }

                        mPairedDevices.removeAll();
                        // Need to add paired devices header before adding specific prefs
                        boolean hasPairedDevicePref = getPreferenceScreen().findPreference(
                                PREF_KEY_PAIRED_DEVICES) != null;
                        if (sensors.size() == 0 && hasPairedDevicePref) {
                            getPreferenceScreen().removePreference(mPairedDevices);
                        } else if (sensors.size() > 0 && !hasPairedDevicePref) {
                            getPreferenceScreen().addPreference(mPairedDevices);
                        }
                        for (Map.Entry<String, ExternalSensorSpec> entry : sensors.entrySet()) {
                            ExternalSensorSpec sensor = entry.getValue();
                            Preference device = new Preference(getActivity());
                            device.setTitle(sensor.getName());
                            device.setKey(sensor.getAddress());
                            device.getExtras().putBoolean(EXTRA_KEY_PAIRED, true);
                            device.getExtras().putString(EXTRA_KEY_ID, entry.getKey());
                            device.setWidgetLayoutResource(R.layout.preference_external_device);
                            updateSummary(device, sensor);
                            mPairedDevices.addPreference(device);
                            Preference availablePref = mAvailableDevices.findPreference(
                                    sensor.getAddress());
                            if (availablePref != null) {
                                mAvailableDevices.removePreference(availablePref);
                            }
                        }
                        if (mDeviceDiscoverer.canScan()) {
                            // Now scan, now that we've loaded this.
                            scanForDevices();
                        }


                    }
        });
    }

    private void scanForDevices() {
        if (!mScanning) {
            mScanning = true;
            mHandler.removeMessages(MSG_STOP_SCANNING);
            mDeviceDiscoverer.startScanning(mCallback);
            mHandler.sendEmptyMessageDelayed(MSG_STOP_SCANNING, SCAN_TIME_MS);
            setScanningUi(true);
        }
    }

    private void stopScanning() {
        if (mScanning) {
            mScanning = false;
            if (mDeviceDiscoverer != null) {
                mDeviceDiscoverer.stopScanning();
            }
            mHandler.removeMessages(MSG_STOP_SCANNING);
            setScanningUi(false);
        }
    }

    private void setScanningUi(boolean scanning) {
        mAvailableDevices.setProgress(scanning);
        if (mMainMenu != null) {
            MenuItem refresh = mMainMenu.findItem(R.id.action_refresh);
            refresh.setEnabled(!scanning);
            if (getActivity() != null) {
                refresh.getIcon().setAlpha(getActivity().getResources().getInteger(
                        scanning ? R.integer.icon_inactive_alpha : R.integer.icon_active_alpha));
            }
        }
    }

    private void onDeviceRecordFound(DeviceRecord record) {
        // First check if this is a paired device.
        String address = record.device.getAddress();
        Preference pref = getDevicePreference(mPairedDevices, address);
        if (pref == null) {
            // See if we've already seen this in available.
            pref = getDevicePreference(mAvailableDevices, address);
            if (pref == null) {
                // If not, add to "available"
                Preference device = new Preference(getActivity());
                device.setKey(record.device.getAddress());
                device.setTitle(record.device.getName());
                device.getExtras().putBoolean(EXTRA_KEY_PAIRED, false);
                device.setWidgetLayoutResource(0);
                mAvailableDevices.addPreference(device);
            }
        } else {
            // This is a paired device that we have found.
            // TODO: update the extras w/ scan info?
        }
    }

    private Preference getDevicePreference(PreferenceCategory category, String address) {
        for (int index = 0; index < category.getPreferenceCount(); ++index) {
            Preference device = category.getPreference(index);
            if (device.getKey().equals(address)) {
                return device;
            }
        }
        return null;
    }

    private void addExternalSensorIfNecessary(Preference preference) {
        final String address = preference.getKey();
        final BleSensorSpec sensor = new BleSensorSpec(address, preference.getTitle().toString());
        preference.setEnabled(false);
        preference.setSummary(R.string.external_devices_pairing);
        // TODO: probably shouldn't finish in these cases, instead go into
        // sensor editing.

        mDataController.addOrGetExternalSensor(sensor,
                new LoggingConsumer<String>(TAG, "ensure sensor") {
            @Override
            public void success(final String sensorId) {
                mDataController.addSensorToExperiment(mExperimentId, sensorId,
                        new LoggingConsumer<Success>(TAG, "add sensor to experiment") {
                            @Override
                            public void success(Success value) {
                                if (LOCAL_LOGD) {
                                    Log.d(TAG, "Added sensor to experiment " + mExperimentId);
                                }
                                reloadAppearancesAndShowOptions(sensor, sensorId);
                            }
                        });
            }
        });
    }

    private void reloadAppearancesAndShowOptions(final ExternalSensorSpec sensor,
            final String sensorId) {
        AppSingleton.getInstance(getActivity()).getSensorAppearanceProvider()
                .loadAppearances(new LoggingConsumer<Success>(TAG, "Load appearance") {
                    @Override
                    public void success(Success value) {
                        refresh();
                        showDeviceOptions(mExperimentId, sensor.getAddress(), sensorId);
                    }
                });
    }

    private void updateSummary(Preference preference, ExternalSensorSpec sensor) {
        preference.setSummary(sensor.getSensorAppearance().getNameResource());
    }

    private void showDeviceOptions(Preference preference) {
        // TODO: use a SettingsController subclass once it's fragmentized.
        showDeviceOptions(mExperimentId, preference.getKey(),
                preference.getExtras().getString(EXTRA_KEY_ID));
    }

    private void showDeviceOptions(String experimentId, String address, String sensorId) {
        DeviceOptionsDialog dialog = DeviceOptionsDialog.newInstance(experimentId, address,
                sensorId);
        dialog.show(getFragmentManager(), "edit_device");
    }
}
