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

package com.google.android.apps.forscience.whistlepunk;

import android.os.Bundle;
import java.util.HashSet;
import java.util.Set;
import junit.framework.Assert;

public class BundleAssert {
  static void assertBundlesEqual(Bundle a, Bundle b) {
    final Set<String> akeys = a.keySet();
    final Set<String> bkeys = b.keySet();
    Assert.assertEquals(new HashSet(akeys), new HashSet(bkeys));

    for (String key : akeys) {
      Assert.assertEquals(a.get(key), b.get(key));
    }
  }
}
