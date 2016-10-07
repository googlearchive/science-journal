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
package com.google.android.apps.forscience.whistlepunk.api.scalarinput;

public class TrackedEvent {
    private final String mCategory;
    private final String mAction;
    private final String mLabel;
    private final long mValue;

    public TrackedEvent(String category, String action, String label, long value) {
        mCategory = category;
        mAction = action;
        mLabel = label;
        mValue = value;
    }

    public String getCategory() {
        return mCategory;
    }

    public String getAction() {
        return mAction;
    }

    public String getLabel() {
        return mLabel;
    }

    public long getValue() {
        return mValue;
    }
}
