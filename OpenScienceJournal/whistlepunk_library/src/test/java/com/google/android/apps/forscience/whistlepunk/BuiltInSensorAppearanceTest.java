/*
 *  Copyright 2017 Google Inc. All Rights Reserved.
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
import android.content.res.Resources;
import android.support.annotation.NonNull;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class BuiltInSensorAppearanceTest {
    @Test
    public void testCreate() {
        int nameStringId = Arbitrary.integer();
        int drawableId = Arbitrary.integer();
        int unitsStringId = Arbitrary.integer();
        int shortDescriptionId = Arbitrary.integer();
        int firstParagraphStringId = Arbitrary.integer();
        int secondParagraphStringId = Arbitrary.integer();
        int infoDrawableId = Arbitrary.integer();

        SensorAnimationBehavior behavior = SensorAnimationBehavior.createDefault();

        int pointsAfterDecimalInNumberFormat = BuiltInSensorAppearance.DEFAULT_POINTS_AFTER_DECIMAL;
        BuiltInSensorAppearance appearance =
                BuiltInSensorAppearance.create(nameStringId, drawableId, unitsStringId,
                        shortDescriptionId,
                        firstParagraphStringId, secondParagraphStringId, infoDrawableId, behavior,
                        pointsAfterDecimalInNumberFormat);

        Context context = new StubContext() {
            @Override
            public Resources getResources() {
                return new Resources(null, null, null) {
                    @NonNull
                    @Override
                    public String getString(int id) throws NotFoundException {
                        return String.valueOf(id);
                    }
                };
            }
        };

        assertEquals(nameStringId, (int) Integer.valueOf(appearance.getName(context)));
        // TODO(saff): test all the other fields as well
    }

}