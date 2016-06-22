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

import android.content.Context;

/**
 * Subclass of {@link SensorAppearance} which includes the external device name.
 */
public class ExternalSensorAppearance extends SensorAppearance {

    private String mDeviceName;


    public ExternalSensorAppearance(int nameStringId, int drawableId, int unitsStringId,
            int shortDescriptionId, int firstParagraphStringId, int secondParagraphStringId,
            int infoDrawableId, SensorAnimationBehavior sensorAnimationBehavior,
            String deviceName) {
        super(nameStringId, drawableId, unitsStringId, shortDescriptionId, firstParagraphStringId,
                secondParagraphStringId, infoDrawableId, sensorAnimationBehavior);
        mDeviceName = deviceName;
    }

    @Override
    public String getName(Context context) {
        return context.getResources().getString(R.string.external_sensor_appearance_name,
                super.getName(context), mDeviceName);
    }
}
