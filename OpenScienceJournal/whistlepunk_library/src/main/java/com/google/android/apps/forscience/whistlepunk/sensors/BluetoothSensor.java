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

package com.google.android.apps.forscience.whistlepunk.sensors;

import android.content.Context;
import android.support.annotation.Nullable;
import android.util.Log;

import com.google.android.apps.forscience.ble.BleFlow;
import com.google.android.apps.forscience.ble.BleFlowListener;
import com.google.android.apps.forscience.whistlepunk.Clock;
import com.google.android.apps.forscience.whistlepunk.PacketAssembler;
import com.google.android.apps.forscience.whistlepunk.data.GoosciSensor;
import com.google.android.apps.forscience.whistlepunk.data.GoosciSensorConfig;
import com.google.android.apps.forscience.whistlepunk.devicemanager.PinTypeProvider;
import com.google.android.apps.forscience.whistlepunk.metadata.BleSensorSpec;
import com.google.android.apps.forscience.whistlepunk.sensorapi.AbstractSensorRecorder;
import com.google.android.apps.forscience.whistlepunk.sensorapi.ScalarSensor;
import com.google.android.apps.forscience.whistlepunk.sensorapi.SensorEnvironment;
import com.google.android.apps.forscience.whistlepunk.sensorapi.SensorRecorder;
import com.google.android.apps.forscience.whistlepunk.sensorapi.SensorStatusListener;
import com.google.android.apps.forscience.whistlepunk.sensorapi.StreamConsumer;
import com.google.android.apps.forscience.whistlepunk.sensorapi.ValueFilter;
import com.google.common.annotations.VisibleForTesting;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.UUID;

public class BluetoothSensor extends ScalarSensor {
    private static final String TAG = "BluetoothSensor";

    public static class BleServiceSpec {
        private final UUID mServiceId;
        private final UUID mValueId;
        private final UUID mSettingId;
        private final UUID mVersionId;

        public BleServiceSpec(UUID serviceId, UUID valueId, UUID settingId, UUID versionId) {
            mServiceId = serviceId;
            mValueId = valueId;
            mSettingId = settingId;
            mVersionId = versionId;
        }

        public UUID getServiceId() {
            return mServiceId;
        }

        public UUID getValueId() {
            return mValueId;
        }

        public UUID getSettingId() {
            return mSettingId;
        }

        public UUID getVersionId() {
            return mVersionId;
        }
    }

    public static class BleProtocolVersion {
        private final int mMajorVersion;
        private final int mMinorVersion;
        private final int mPatchVersion;

        private static final int MAJOR_BITS = 5;
        private static final int MINOR_BITS = 5;
        private static final int PATCH_BITS = 6;

        private static final int MAJOR_MAX = (1 << MAJOR_BITS) - 1;
        private static final int MINOR_MAX = (1 << MINOR_BITS) - 1;
        private static final int PATCH_MAX = (1 << PATCH_BITS) - 1;

        private static final int MAJOR_SHIFT = PATCH_BITS + MINOR_BITS;
        private static final int MINOR_SHIFT = PATCH_BITS;

        private static final int MAJOR_MASK = MAJOR_MAX << MAJOR_SHIFT;
        private static final int MINOR_MASK = MINOR_MAX << MINOR_SHIFT;
        private static final int PATCH_MASK = PATCH_MAX;

        public BleProtocolVersion(byte[] rawVersion) {
            int version = (rawVersion[0] & 0xFF) | ((rawVersion[1]<<8) & 0xFF00);

            mMajorVersion = (version & MAJOR_MASK) >> MAJOR_SHIFT;
            mMinorVersion = (version & MINOR_MASK) >> MINOR_SHIFT;
            mPatchVersion = (version & PATCH_MASK);

        }

        public int getMajorVersion() {
            return mMajorVersion;
        }

        public int getMinorVersion() {
            return mMinorVersion;
        }

        public int getPatchVersion() {
            return mPatchVersion;
        }

        @VisibleForTesting
        public int getMaxMajorVersion() {
            return MAJOR_MAX;
        }

        @VisibleForTesting
        public int getMaxMinorVersion() {
            return MINOR_MAX;
        }

        @VisibleForTesting
        public int getMaxPatchVersion() {
            return PATCH_MAX;
        }
    }

    // There may be someday additional BLE services that we natively read in Science Journal
    // However, in the anning release, there was just one, a single stream of timestamped
    // scalar values.  In the code, we'll refer to that as the "Anning service".

    // Details in https://goto.google.com/goosci-ble
    private static UUID ANNING_SERV = UUID.fromString("555a0001-0aaa-467a-9538-01f0652c74e8");
    private static UUID ANNING_VALUE = UUID.fromString("555a0003-0aaa-467a-9538-01f0652c74e8");
    private static UUID ANNING_SETTING = UUID.fromString("555a0010-0aaa-467a-9538-01f0652c74e8");
    private static UUID ANNING_VERSION = UUID.fromString("555a0011-0aaa-467a-9538-01f0652c74e8");

    public static final BleServiceSpec ANNING_SERVICE_SPEC = new BleServiceSpec(ANNING_SERV,
            ANNING_VALUE, ANNING_SETTING, ANNING_VERSION);
    public static final BleServiceSpec[] SUPPORTED_SERVICES =
            new BleServiceSpec[]{ANNING_SERVICE_SPEC};

    private static final long DEFAULT_FREQUENCY_WINDOW = 2000;
    private static final float DEFAULT_FREQUENCY_FILTER = 0;
    private final BleSensorSpec mSensor;
    private final BleServiceSpec mServiceSpec;
    private boolean mDeviceFrequencyEnabled;
    private boolean mNotificationSubscribed = false;

    private long mTimeSkew = -1;
    private String mAddress;
    private GoosciSensorConfig.BleSensorConfig.ScaleTransform mDeviceScaleTransform;

    public long getTimeSkew() { return mTimeSkew; }
    public void setTimeSkew(long skew) { mTimeSkew = skew; }
    BleFlow mFlow;
    BleFlowListener mBleFlowListener;

    public BluetoothSensor(String sensorId, BleSensorSpec sensor, BleServiceSpec serviceSpec) {
        super(sensorId);
        mSensor = sensor;
        mServiceSpec = serviceSpec;
        mAddress = sensor.getAddress();
        readConfigurationFrom(sensor);
    }

    private BleFlowListener createBleFlowListener(final StreamConsumer c, final Clock defaultClock,
            final SensorStatusListener listener) {
        return new BleFlowListener() {
            final PacketAssembler mPa = new PacketAssembler(c, defaultClock, BluetoothSensor.this);

            @Override
            public void onSuccess() {
            }

            @Override
            public void onFailure(Exception error) {
                Log.d(TAG, "Failure " + error.getMessage());
                listener.onSourceError(getId(), SensorStatusListener.ERROR_UNKNOWN,
                        error.getLocalizedMessage());
                mFlow.resetAndAddListener(mBleFlowListener)
                        .disconnect();
                BleFlow.run(mFlow);
            }

            @Override
            public void onCharacteristicRead(UUID characteristic, int flags, byte[] value) {
                // This is where we catch the characteristic static value
                // For Value (UUID:555a0003), it's a protobuf
                // For Description (UUID:555a0002), it's a string desc (eg. "Goosci Windmill")
                if (characteristic.compareTo(mServiceSpec.getVersionId()) == 0) {
                    BleProtocolVersion protocolVersion = new BleProtocolVersion(value);
                    switch (protocolVersion.getMajorVersion()) {
                        // Currently no version requires a special connection sequence
                        default:
                            writeConfigAndSetNotification();
                    }
                }
            }

            @Override
            public void onNotification(UUID characteristic, int flags, byte[] value) {
                mPa.append(value);
            }
            @Override
            public void onDisconnect() {
                listener.onSourceStatus(getId(), SensorStatusListener.STATUS_DISCONNECTED);
            }

            @Override
            public void onConnect() {
                listener.onSourceStatus(getId(), SensorStatusListener.STATUS_CONNECTED);
            }

            @Override
            public void onNotificationSubscribed(){
                mNotificationSubscribed = true;
            }

            @Override
            public void onNotificationUnsubscribed() {
                mNotificationSubscribed = false;
                mFlow.resetAndAddListener(mBleFlowListener)
                        .disconnect();
                BleFlow.run(mFlow);
            }

            @Override
            public void onServicesDiscovered() {
                if (mFlow.isCharacteristicValid(mServiceSpec.getVersionId())) {
                    mFlow.lookupCharacteristic(mServiceSpec.getVersionId())
                            .read();
                    BleFlow.run(mFlow);
                } else {
                    writeConfigAndSetNotification();
                }
            }
        };
    }

    @Override
    protected boolean getDefaultFrequencyChecked() {
        return mDeviceFrequencyEnabled;
    }

    @Nullable
    protected GoosciSensorConfig.BleSensorConfig.ScaleTransform getDefaultScaleTransform() {
        return mDeviceScaleTransform;
    }

    public byte[] buildConfigProtoForDevice(BleSensorSpec sensor) {
        GoosciSensor.SensorDataRequest sdr = new GoosciSensor.SensorDataRequest();
        sdr.timestampKey = 42; // arbitrary constant.  TMOLTUAE.
        sdr.interval = new GoosciSensor.Interval();
        sdr.interval.count = 1;
        sdr.interval.frequency = 20;

        sdr.pin = new GoosciSensor.Pin[]{
                new GoosciSensor.Pin(),
        };
        String pinName = sensor.getPin();
        PinTypeProvider pinTypeProvider = new PinTypeProvider();
        PinTypeProvider.PinType pinType = pinTypeProvider.parsePinName(pinName);
        if (pinType == null) {
            Log.e(TAG, "Failed to parse pin: " + pinName);
            return null;
        } else if (pinType.getPinSignalType() == PinTypeProvider.PinSignalType.ANALOG) {
            GoosciSensor.AnalogPin ap = new GoosciSensor.AnalogPin();
            ap.pin = pinType.getPinNumber();
            sdr.pin[0].setAnalogPin(ap);
        } else if (pinType.getPinSignalType() == PinTypeProvider.PinSignalType.DIGITAL) {
            GoosciSensor.DigitalPin dp = new GoosciSensor.DigitalPin();
            dp.pin = pinType.getPinNumber();
            sdr.pin[0].setDigitalPin(dp);
        } else if (pinType.getPinSignalType() == PinTypeProvider.PinSignalType.VIRTUAL) {
            GoosciSensor.VirtualPin vp = new GoosciSensor.VirtualPin();
            vp.pin = pinType.getPinNumber();
            sdr.pin[0].setVirtualPin(vp);
        }

        byte[] value = GoosciSensor.SensorDataRequest.toByteArray(sdr);
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        try {
            byte v = (byte)value.length;
            outputStream.write(v);
            outputStream.write((byte)1);
            outputStream.write(value);

        } catch (IOException e) {
            Log.e(TAG, "Failed to append byte arrays: " + e.getMessage());
        }
        return outputStream.toByteArray();
    }

    private void writeConfigAndSetNotification() {
        byte[] sensorConfig = buildConfigProtoForDevice(mSensor);
        if (sensorConfig != null && mFlow.isCharacteristicValid(mServiceSpec.getSettingId())) {
            mFlow.lookupCharacteristic(mServiceSpec.getSettingId())
                    .write(sensorConfig);
        }
        mFlow.lookupCharacteristic(mServiceSpec.getValueId())
                .enableNotification();
        BleFlow.run(mFlow);
    }

    private void readConfigurationFrom(BleSensorSpec bleSensor) {
        mDeviceFrequencyEnabled = bleSensor.getFrequencyEnabled();
        mDeviceScaleTransform = bleSensor.getScaleTransform();
        setScalarFilter(getDeviceDefaultValueFilter());
    }

    @VisibleForTesting
    public ValueFilter getDeviceDefaultValueFilter() {
        return computeValueFilter(DEFAULT_FREQUENCY_WINDOW, DEFAULT_FREQUENCY_FILTER,
                getDefaultFrequencyChecked(), getDefaultScaleTransform());
    }

    @Override
    protected SensorRecorder makeScalarControl(final StreamConsumer c,
            final SensorEnvironment environment, Context context,
            final SensorStatusListener listener) {
        mFlow = environment.getBleClient().getFlowFor(mAddress);
        return new AbstractSensorRecorder() {
            @Override
            public void startObserving() {
                // make BLE connection
                listener.onSourceStatus(getId(), SensorStatusListener.STATUS_CONNECTING);
                mBleFlowListener =
                        createBleFlowListener(c, environment.getDefaultClock(), listener);
                mTimeSkew = -1;
                mFlow.resetAndAddListener(mBleFlowListener)
                        .connect()
                        .lookupService(mServiceSpec.getServiceId());
                BleFlow.run(mFlow);
            }

            @Override
            public void stopObserving() {
                mTimeSkew = -1;
                mFlow.resetAndAddListener(mBleFlowListener);
                if(mNotificationSubscribed) {
                    mFlow.lookupService(mServiceSpec.getServiceId())
                            .lookupCharacteristic(mServiceSpec.getValueId())
                            .disableNotification();
                    BleFlow.run(mFlow);
                } else {
                    mFlow.disconnect();
                    BleFlow.run(mFlow);
                }
            }
        };
    }
}
