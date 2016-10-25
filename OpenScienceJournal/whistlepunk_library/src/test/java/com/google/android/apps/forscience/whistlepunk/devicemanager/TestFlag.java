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

import static org.junit.Assert.assertTrue;

/**
 * Little utility class for when you want to be sure a callback was called.
 */
public class TestFlag {
    private boolean mSet = false;
    private String mTitle;

    public TestFlag(String title) {
        mTitle = title;
    }

    public void set() {
        mSet = true;
    }

    public void assertSet() {
        assertTrue(mTitle, mSet);
    }
}
