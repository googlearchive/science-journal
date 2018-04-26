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

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.google.android.apps.forscience.javalib.Consumer;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

@RunWith(RobolectricTestRunner.class)
public class EnablementControllerTest {
  @Test
  public void trackEnabled() {
    EnablementController ec = new EnablementController();
    RememberingConsumer rc1 = new RememberingConsumer();
    RememberingConsumer rc2 = new RememberingConsumer();

    ec.addEnablementListener("key1", rc1);
    assertTrue(rc1.lastValue);

    ec.addEnablementListener("key2", rc2);
    assertTrue(rc2.lastValue);

    ec.setChecked("key1", true);
    assertFalse(rc1.lastValue);
    assertTrue(rc2.lastValue);

    ec.setChecked("key2", true);
    assertTrue(rc1.lastValue);
    assertTrue(rc2.lastValue);

    ec.setChecked("key1", false);
    assertTrue(rc1.lastValue);
    assertFalse(rc2.lastValue);

    // Simulate somehow unchecking a disabled box (like, for example, forgetting the last
    // selected device).
    ec.setChecked("key2", false);
    assertTrue(rc1.lastValue);
    assertTrue(rc2.lastValue);
  }

  private static class RememberingConsumer extends Consumer<Boolean> {
    public Boolean lastValue = null;

    @Override
    public void take(Boolean aBoolean) {
      lastValue = aBoolean;
    }
  }
}
