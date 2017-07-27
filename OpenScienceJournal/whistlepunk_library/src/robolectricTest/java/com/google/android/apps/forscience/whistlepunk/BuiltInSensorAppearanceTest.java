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

import static org.junit.Assert.assertEquals;

import android.content.Context;
import android.content.res.Resources;
import android.support.annotation.NonNull;

import com.google.android.apps.forscience.whistlepunk.data.GoosciIcon;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(constants = BuildConfig.class)
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
        String sensorId = Arbitrary.string();

        SensorAnimationBehavior behavior = SensorAnimationBehavior.createDefault();

        int pointsAfterDecimalInNumberFormat = BuiltInSensorAppearance.DEFAULT_POINTS_AFTER_DECIMAL;
        BuiltInSensorAppearance appearance =
                BuiltInSensorAppearance.create(nameStringId, drawableId, unitsStringId,
                        shortDescriptionId,
                        firstParagraphStringId, secondParagraphStringId, infoDrawableId, behavior,
                        pointsAfterDecimalInNumberFormat, sensorId);

        Context context = new StubContext() {
            @Override
            public Resources getResources() {
                Resources appResources =
                        RuntimeEnvironment.application.getApplicationContext().getResources();
                return new Resources(appResources.getAssets(), appResources.getDisplayMetrics(),
                        appResources.getConfiguration()) {
                    @NonNull
                    @Override
                    public String getString(int id) throws NotFoundException {
                        return String.valueOf(id);
                    }
                };
            }
        };

        assertEquals(nameStringId, (int) Integer.valueOf(appearance.getName(context)));
        assertEquals(firstParagraphStringId, (int) Integer.valueOf(
                appearance.loadLearnMore(context).test().values().get(0).getFirstParagraph()));
        assertEquals(GoosciIcon.IconPath.BUILTIN, appearance.getSmallIconPath().type);
        assertEquals(sensorId, appearance.getSmallIconPath().pathString);
        assertEquals(GoosciIcon.IconPath.BUILTIN, appearance.getLargeIconPath().type);
        assertEquals(sensorId, appearance.getLargeIconPath().pathString);
        // TODO(saff): test all the other fields as well
    }

}