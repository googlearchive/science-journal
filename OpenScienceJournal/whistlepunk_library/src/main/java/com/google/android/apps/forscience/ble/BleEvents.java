package com.google.android.apps.forscience.ble;

import android.content.IntentFilter;

/**
 * BLE related events.
 */
public class BleEvents {
    static final String BLE_UNSUPPORTED = "BLE_UNSUPPORTED";
    static final String BLE_DISABLED = "BLE_DISABLED";
    static final String BLE_ENABLED = "BLE_ENABLED";
    static final String BLE_SCAN_END = "BLE_SCAN_END";
    static final String DEVICE_FOUND = "DEVICE_FOUND";

    static final String GATT_CONNECT = "GATT_CONNECT";
    static final String GATT_CONNECT_FAIL = "GATT_CONNECT_FAIL";
    static final String GATT_DISCONNECT = "GATT_DISCONNECT";
    static final String GATT_DISCONNECT_FAIL = "GATT_DISCONNECT_FAIL";
    static final String SERVICES_OK = "SERVICES_OK";
    static final String SERVICES_FAIL = "SERVICES_FAIL";

    static final String READ_CHAR_OK = "READ_CHAR_OK";
    static final String READ_CHAR_FAIL = "READ_CHAR_FAIL";

    static final String WRITE_CHAR_OK = "WRITE_CHAR_OK";
    static final String WRITE_CHAR_FAIL = "WRITE_CHAR_FAIL";

    static final String COMMIT_OK = "COMMIT_OK";
    static final String COMMIT_FAIL = "COMMIT_FAIL";

    static final String READ_DESC_OK = "READ_DESC_OK";
    static final String READ_DESC_FAIL = "READ_DESC_FAIL";
    static final String WRITE_DESC_OK = "WRITE_DESC_OK";
    static final String WRITE_DESC_FAIL = "WRITE_DESC_FAIL";
    static final String CHAR_CHANGED = "CHAR_CHANGED";

    static final String MTU_CHANGE_OK = "MTU_CHANGE_OK";
    static final String MTU_CHANGE_FAIL = "MTU_CHANGE_FAIL";
    public static final String START_TX_OK = "START_TX_OK";
    public static final String START_TX_FAIL = "START_TX_FAIL";

    static IntentFilter createIntent() {
        IntentFilter intent = new IntentFilter();
        intent.addAction(BLE_ENABLED);
        intent.addAction(BLE_UNSUPPORTED);
        intent.addAction(BLE_DISABLED);
        intent.addAction(BLE_SCAN_END);
        intent.addAction(DEVICE_FOUND);

        intent.addAction(GATT_CONNECT);
        intent.addAction(GATT_CONNECT_FAIL);
        intent.addAction(GATT_DISCONNECT);
        intent.addAction(GATT_DISCONNECT_FAIL);
        intent.addAction(SERVICES_OK);
        intent.addAction(SERVICES_FAIL);

        intent.addAction(READ_CHAR_OK);
        intent.addAction(READ_CHAR_FAIL);
        intent.addAction(WRITE_CHAR_OK);
        intent.addAction(WRITE_CHAR_FAIL);

        intent.addAction(COMMIT_OK);
        intent.addAction(COMMIT_FAIL);

        intent.addAction(CHAR_CHANGED);

        intent.addAction(READ_DESC_OK);
        intent.addAction(READ_DESC_FAIL);
        intent.addAction(WRITE_DESC_OK);
        intent.addAction(WRITE_DESC_FAIL);

        intent.addAction(MTU_CHANGE_OK);
        intent.addAction(MTU_CHANGE_FAIL);

        intent.addAction(START_TX_FAIL);
        intent.addAction(START_TX_OK);

        return intent;
    }
}
