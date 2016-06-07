package com.google.android.apps.forscience.ble;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Context;

import com.google.android.apps.forscience.whistlepunk.sensors.BluetoothSensor;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Discovers devices using pre API level 21 methods.
 */
/* package */ class DeviceDiscovererLegacy extends DeviceDiscoverer {

    private BluetoothAdapter.LeScanCallback mCallback = new BluetoothAdapter.LeScanCallback() {
        @Override
        public void onLeScan(BluetoothDevice device, int rssi, byte[] scanRecord) {
            if (isScienceSensor(parseUuids(scanRecord))) {
                addOrUpdateDevice(device, rssi, extractLongName(scanRecord));
            }
        }
    };

    DeviceDiscovererLegacy(Context context, Callback callback) {
        super(context, callback);
    }

    @Override
    public void onStartScanning() {
        // KitKat can't handle 128bit UUIDs, so ask for all devices.
        getBluetoothAdapter().startLeScan(mCallback);
    }

    @Override
    public void onStopScanning() {
        getBluetoothAdapter().stopLeScan(mCallback);
    }

    private List<UUID> parseUuids(byte[] advertisedData) {
        List<UUID> uuids = new ArrayList<UUID>();

        ByteBuffer buffer = ByteBuffer.wrap(advertisedData).order(ByteOrder.LITTLE_ENDIAN);
        while (buffer.remaining() > 2) {
            byte length = buffer.get();
            if (length == 0) break;

            byte type = buffer.get();
            switch (type) {
                case 0x02: // Partial list of 16-bit UUIDs
                case 0x03: // Complete list of 16-bit UUIDs
                    while (length >= 2) {
                        uuids.add(UUID.fromString(String.format(
                                "%08x-0000-1000-8000-00805f9b34fb", buffer.getShort())));
                        length -= 2;
                    }
                    break;

                case 0x06: // Partial list of 128-bit UUIDs
                case 0x07: // Complete list of 128-bit UUIDs
                    while (length >= 16) {
                        long lsb = buffer.getLong();
                        long msb = buffer.getLong();
                        uuids.add(new UUID(msb, lsb));
                        length -= 16;
                    }
                    break;

                default:
                    buffer.position(buffer.position() + length - 1);
                    break;
            }
        }

        return uuids;
    }

    private boolean isScienceSensor(List<UUID> ids) {
        for (BluetoothSensor.BleServiceSpec spec : BluetoothSensor.SUPPORTED_SERVICES) {
            for (UUID loopId : ids) {
                if (loopId.compareTo(spec.getServiceId()) == 0) {
                    return true;
                }
            }
        }
        return false;
    }
}
