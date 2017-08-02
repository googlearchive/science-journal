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

import android.app.FragmentManager;
import android.content.Context;
import android.graphics.drawable.Drawable;
import android.support.annotation.VisibleForTesting;

import com.google.android.apps.forscience.ble.DeviceDiscoverer;
import com.google.android.apps.forscience.javalib.FailureListener;
import com.google.android.apps.forscience.whistlepunk.PermissionUtils;
import com.google.android.apps.forscience.whistlepunk.SensorProvider;
import com.google.android.apps.forscience.whistlepunk.R;
import com.google.android.apps.forscience.whistlepunk.api.scalarinput.InputDeviceSpec;
import com.google.android.apps.forscience.whistlepunk.data.GoosciSensorSpec;
import com.google.android.apps.forscience.whistlepunk.metadata.BleSensorSpec;
import com.google.android.apps.forscience.whistlepunk.metadata.ExternalSensorSpec;
import com.google.android.apps.forscience.whistlepunk.sensorapi.SensorChoice;
import com.google.android.apps.forscience.whistlepunk.sensors.BluetoothSensor;

/**
 * Discovers BLE sensors that speak our "native" Science Journal protocol.
 */
public class NativeBleDiscoverer implements SensorDiscoverer {

    private static final SensorProvider PROVIDER = new SensorProvider() {
        @Override
        public SensorChoice buildSensor(String sensorId, ExternalSensorSpec spec) {
            return new BluetoothSensor(sensorId, (BleSensorSpec) spec,
                    BluetoothSensor.ANNING_SERVICE_SPEC);
        }

        @Override
        public ExternalSensorSpec buildSensorSpec(String name, byte[] config) {
            return new BleSensorSpec(name, config);
        }
    };

    private static final String SERVICE_ID = "com.google.android.apps.forscience.whistlepunk.ble";

    private DeviceDiscoverer mDeviceDiscoverer;
    private Runnable mOnScanDone;
    private Context mContext;

    public NativeBleDiscoverer(Context context) {
        mContext = context;
    }

    @Override
    public SensorProvider getProvider() {
        return PROVIDER;
    }

    @Override
    public boolean startScanning(final ScanListener listener, FailureListener onScanError) {
        stopScanning();

        // BLE scan is only done when it times out (which is imposed from fragment)
        // TODO: consider making that timeout internal (like it is for API sensor services)
        mOnScanDone = new Runnable() {
            @Override
            public void run() {
                listener.onServiceScanComplete(SERVICE_ID);
                listener.onScanDone();
            }
        };

        mDeviceDiscoverer = createDiscoverer(mContext);
        final boolean canScan = mDeviceDiscoverer.canScan() && hasScanPermission();


        listener.onServiceFound(new DiscoveredService() {
            @Override
            public String getServiceId() {
                return SERVICE_ID;
            }

            @Override
            public String getName() {
                // TODO: agree on a string here
                return mContext.getString(R.string.native_ble_service_name);
            }

            @Override
            public Drawable getIconDrawable(Context context) {
                return context.getResources().getDrawable(R.drawable.ic_bluetooth_white_24dp);
            }

            @Override
            public ServiceConnectionError getConnectionErrorIfAny() {
                if (canScan) {
                    return null;
                } else {
                    return new ServiceConnectionError() {
                        @Override
                        public String getErrorMessage() {
                            return mContext.getString(R.string.btn_enable_bluetooth);
                        }

                        @Override
                        public boolean canBeResolved() {
                            return true;
                        }

                        @Override
                        public void tryToResolve(FragmentManager fragmentManager) {
                            ScanDisabledDialogFragment.newInstance().show(fragmentManager,
                                    "scanDisabledDialog");
                        }
                    };
                }
            }
        });

        if (!canScan) {
            stopScanning();
            return false;
        }

        mDeviceDiscoverer.startScanning(new DeviceDiscoverer.Callback() {
            @Override
            public void onDeviceFound(final DeviceDiscoverer.DeviceRecord record) {
                onDeviceRecordFound(record, listener);
            }

            @Override
            public void onError(int error) {
                // TODO: handle errors
            }
        });
        return true;
    }

    @VisibleForTesting
    protected boolean hasScanPermission() {
        return PermissionUtils.hasPermission(mContext,
                PermissionUtils.REQUEST_ACCESS_COARSE_LOCATION);
    }

    protected DeviceDiscoverer createDiscoverer(Context context) {
        return DeviceDiscoverer.getNewInstance(context);
    }

    @Override
    public void stopScanning() {
        if (mDeviceDiscoverer != null) {
            mDeviceDiscoverer.stopScanning();
            mDeviceDiscoverer = null;
        }
        if (mOnScanDone != null) {
            mOnScanDone.run();
            mOnScanDone = null;
        }
    }

    private void onDeviceRecordFound(DeviceDiscoverer.DeviceRecord record,
            ScanListener scanListener) {
        WhistlepunkBleDevice device = record.device;
        String address = device.getAddress();

        // sensorScanCallbacks will handle duplicates
        final BleSensorSpec spec = new BleSensorSpec(address, device.getName());

        scanListener.onDeviceFound(new DiscoveredDevice() {
            @Override
            public String getServiceId() {
                return SERVICE_ID;
            }

            @Override
            public InputDeviceSpec getSpec() {
                return DeviceRegistry.createHoldingDevice(spec);
            }
        });

        scanListener.onSensorFound(new DiscoveredSensor() {

            @Override
            public GoosciSensorSpec.SensorSpec getSensorSpec() {
                return spec.asGoosciSpec();
            }

            @Override
            public SettingsInterface getSettingsInterface() {
                return new SettingsInterface() {
                    @Override
                    public void show(String experimentId, String sensorId,
                            FragmentManager fragmentManager, boolean showForgetButton) {
                        DeviceOptionsDialog dialog = DeviceOptionsDialog.newInstance(experimentId,
                                sensorId, null, showForgetButton);
                        dialog.show(fragmentManager, "edit_device");
                    }
                };
            }

            @Override
            public boolean shouldReplaceStoredSensor(ConnectableSensor oldSensor) {
                // The current implementation of this discoverer does not notice when it detects
                // a sensor that has already been customized in the database, so we should not
                // overwrite local customizations
                return false;
            }
        });
    }
}
