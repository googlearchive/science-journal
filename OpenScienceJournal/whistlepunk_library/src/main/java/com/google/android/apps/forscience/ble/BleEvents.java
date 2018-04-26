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

package com.google.android.apps.forscience.ble;

import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;

/** BLE related events. */
public class BleEvents {
  static final String BLE_UNSUPPORTED = "BLE_UNSUPPORTED";
  static final String BLE_DISABLED = "BLE_DISABLED";

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

  static final String READ_DESC_OK = "READ_DESC_OK";
  static final String READ_DESC_FAIL = "READ_DESC_FAIL";
  static final String WRITE_DESC_OK = "WRITE_DESC_OK";
  static final String WRITE_DESC_FAIL = "WRITE_DESC_FAIL";
  static final String CHAR_CHANGED = "CHAR_CHANGED";

  private static final String DATA_SCHEME = "sciencejournal";

  static IntentFilter createIntentFilter(String address) {
    IntentFilter intent = new IntentFilter();

    intent.addDataScheme(DATA_SCHEME);
    intent.addDataAuthority(address, null);

    intent.addAction(BLE_UNSUPPORTED);
    intent.addAction(BLE_DISABLED);

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

    intent.addAction(CHAR_CHANGED);

    intent.addAction(READ_DESC_OK);
    intent.addAction(READ_DESC_FAIL);
    intent.addAction(WRITE_DESC_OK);
    intent.addAction(WRITE_DESC_FAIL);

    return intent;
  }

  static Intent createIntent(String action, String address) {
    Uri intentUri = new Uri.Builder().scheme(DATA_SCHEME).authority(address).build();
    return new Intent(action, intentUri);
  }
}
