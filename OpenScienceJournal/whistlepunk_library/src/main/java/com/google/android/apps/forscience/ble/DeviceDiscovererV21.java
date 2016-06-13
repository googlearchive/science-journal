package com.google.android.apps.forscience.ble;

import android.annotation.TargetApi;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanRecord;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.os.ParcelUuid;

import com.google.android.apps.forscience.whistlepunk.sensors.BluetoothSensor;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Discovers LE devices using API level 21+ methods.
 */
@TargetApi(21)
/* package */ class DeviceDiscovererV21 extends DeviceDiscoverer {

    private BluetoothLeScanner mScanner;

    private ScanCallback mCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            ScanRecord scanRecord = result.getScanRecord();
            addOrUpdateDevice(result.getDevice(), result.getRssi(),
                    extractLongName(scanRecord.getBytes()));
        }

        @Override
        public void onBatchScanResults(List<ScanResult> results) {
            for (ScanResult result : results) {
                ScanRecord scanRecord = result.getScanRecord();
                addOrUpdateDevice(result.getDevice(), result.getRssi(),
                        extractLongName(scanRecord.getBytes()));
            }
        }

        @Override
        public void onScanFailed(int errorCode) {
            // TODO: surface errors.
        }
    };

    DeviceDiscovererV21(Context context) {
        super(context);
    }

    @Override
    public void onStartScanning() {
        mScanner = getBluetoothAdapter().getBluetoothLeScanner();
        List<ScanFilter> filters = new ArrayList<>();
        for (BluetoothSensor.BleServiceSpec spec : BluetoothSensor.SUPPORTED_SERVICES) {
            filters.add(new ScanFilter.Builder()
                    .setServiceUuid(ParcelUuid.fromString(spec.getServiceId().toString()))
                    .build());
        }
        ScanSettings settings = new ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_BALANCED)
                .build();
        mScanner.startScan(filters, settings, mCallback);
    }

    @Override
    public void onStopScanning() {
        if (mScanner != null) {
            if (BluetoothAdapter.getDefaultAdapter().isEnabled()) {
                mScanner.stopScan(mCallback);
            }
        }
    }

    @Override
    public void destroy() {
        super.destroy();
        mCallback = null;
    }
}
