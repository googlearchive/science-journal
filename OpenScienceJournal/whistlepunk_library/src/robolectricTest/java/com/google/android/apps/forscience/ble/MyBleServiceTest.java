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

import static org.junit.Assert.assertEquals;

import com.google.common.collect.Lists;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

@RunWith(RobolectricTestRunner.class)
public class MyBleServiceTest {
  @Test
  public void testDontAskForSameServiceTwice() {
    TestBleService mbs = new TestBleService();

    mbs.discoverServices("address");
    // make sure this doesn't actually deliver
    mbs.discoverServices("address");

    // Should be only one discovery call
    assertEquals(Lists.newArrayList("address"), mbs.addressesDiscovered);
  }

  private static class TestBleService extends MyBleService {
    public List<String> addressesDiscovered = new ArrayList<>();

    @Override
    protected boolean internalDiscoverServices(String address) {
      addressesDiscovered.add(address);
      return true;
    }
  }
}
