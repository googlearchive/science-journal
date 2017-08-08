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

import com.google.android.apps.forscience.whistlepunk.filemetadata.Label;

public class AddedLabelEvent {
    private final Label mLabel;
    private final RecordingStatus mStatus;

    public AddedLabelEvent(Label label, RecordingStatus status) {
        mLabel = label;
        mStatus = status;
    }

    public Label getLabel() {
        return mLabel;
    }

    public RecordingStatus getStatus() {
        return mStatus;
    }

    String getTrialId() {
        return getStatus().getTrialId();
    }
}
