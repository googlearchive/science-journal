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

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Intent;
import android.content.IntentFilter;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

/** Tests for {@link BleEvents} */
@RunWith(RobolectricTestRunner.class)
public class BleEventsTest {

  private IntentFilter intentFilter;
  private final String TEST_ACTION = BleEvents.GATT_CONNECT;
  private final String FILTER_ADDRESS = "AA:BB:CC:DD:EE:FF";

  @Before
  public void setUp() {
    intentFilter = BleEvents.createIntentFilter(FILTER_ADDRESS);
  }

  @Test
  public void testIntentFilterMismatchAddress() {
    Intent mismatchAddressIntent = BleEvents.createIntent(TEST_ACTION, "FF:EE:DD:CC:BB:AA");
    assertTrue(intentFilter.hasAction(mismatchAddressIntent.getAction()));
    assertFalse(intentFilter.hasDataAuthority(mismatchAddressIntent.getData()));
  }

  @Test
  public void testIntentFilterMismatchAction() {
    Intent mismatchActionIntent = BleEvents.createIntent("UNKNOWN", FILTER_ADDRESS);
    assertFalse(intentFilter.hasAction(mismatchActionIntent.getAction()));
    assertTrue(intentFilter.hasDataAuthority(mismatchActionIntent.getData()));
  }

  @Test
  public void testIntentFilterMatchAll() {
    Intent matchIntent = BleEvents.createIntent(TEST_ACTION, FILTER_ADDRESS);
    assertTrue(intentFilter.hasAction(matchIntent.getAction()));
    assertTrue(intentFilter.hasDataAuthority(matchIntent.getData()));
  }
}
