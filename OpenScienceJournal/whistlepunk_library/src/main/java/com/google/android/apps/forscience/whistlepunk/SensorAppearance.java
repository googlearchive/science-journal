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
import android.graphics.drawable.Drawable;

import com.google.android.apps.forscience.javalib.Consumer;
import com.google.android.apps.forscience.whistlepunk.data.GoosciIcon;

import java.text.NumberFormat;

import io.reactivex.Single;

/**
 * How a sensor appears in the app
 */
public interface SensorAppearance {

    /**
     * The contents of the LEARN MORE screen
     */
    public interface LearnMoreContents {
        /**
         * @return the text that appears above the image
         */
        String getFirstParagraph();

        /**
         * @return an optional image that goes in the middle
         */
        Drawable getDrawable();

        /**
         * @return optionally, text that comes after the image in LEARN MORE
         */
        String getSecondParagraph();
    }

    /**
     * @return the user-meaningful name of the sensor (localized)
     */
    String getName(Context context);

    /**
     * @return the units of the measurement
     */
    String getUnits(Context context);

    /**
     * @return the icon to be displayed in the tab drawer
     */
    Drawable getIconDrawable(Context context);

    /**
     * @return short text shown beneath the sensor in meter mode
     */
    String getShortDescription(Context context);

    /**
     * @return the behavior that the big icon should display in meter mode
     */
    SensorAnimationBehavior getSensorAnimationBehavior();

    /**
     * @return how should numbers be formatted for on-screen display?
     */
    NumberFormat getNumberFormat();

    boolean hasLearnMore();

    Single<LearnMoreContents> loadLearnMore(Context context);

    /**
     * @return the tab-sized icon path proto to store for runs, snapshots, and trigger notes
     * containing this sensor.
     *
     * See icon_path.proto for the semantics of the returned proto.
     */
    GoosciIcon.IconPath getSmallIconPath();

    /**
     * @return the large detail-view icon path proto to store for runs, snapshots, and trigger
     * notes containing this sensor.
     *
     * See icon_path.proto for the semantics of the returned proto.
     */
    GoosciIcon.IconPath getLargeIconPath();
}
