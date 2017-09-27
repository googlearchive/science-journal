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

import com.google.android.apps.forscience.whistlepunk.data.GoosciIcon;
import com.google.android.apps.forscience.whistlepunk.sensorapi.SensorChoice;
import com.google.common.base.Preconditions;

import java.text.FieldPosition;
import java.text.NumberFormat;
import java.text.ParsePosition;

import io.reactivex.Single;

public class BuiltInSensorAppearance implements SensorAppearance {
    public static final int DEFAULT_POINTS_AFTER_DECIMAL = -1;

    // Don't allow more than 10 places after the decimal to be displayed. The UX can't
    // handle this very well.
    // TODO: Revisit this constant -- should it be even smaller, like 5?
    public static final int MAX_POINTS_AFTER_DECIMAL = 10;

    /**
     * Human readable name for this source.
     */
    private final int mNameStringId;

    /**
     * The ID of the drawable to use as an icon for this sensor source.
     */
    private final int mDrawableId;

    /**
     * The ID of the string that represents the units for this source. Use 0 if no units are
     * needed.
     */
    private final int mUnitsStringId;

    private final int mPointsAfterDecimal;

    /**
     * The number format to use for this sensor everywhere but the graph Y axis.
     */
    private NumberFormat mNumberFormat;
    private final String mBuiltInSensorId;

    /**
     * The ID of the string that has a short description of this sensor.
     */
    private final int mShortDescriptionStringId;

    /**
     * The SensorAnimationBehavior that controls the drawable used in the sensor animation.
     */
    private final SensorAnimationBehavior mSensorAnimationBehavior;

    /**
     * The IDs to the first and second paragraphs of text in the Learn More page.
     * The first paragraph comes before the image, and the second comes after.
     */
    private int mFirstParagraphStringId;
    private int mSecondParagraphStringId;
    private int mLearnMoreDrawableId;

    /**
     * @param builtInSensorId string id of built-in sensor.  This should be the same as the result
     *                        of {@link SensorChoice#getId()}
     */
    public static BuiltInSensorAppearance create(int nameStringId, int drawableId,
            int unitsStringId,
            int shortDescriptionId, int firstParagraphStringId, int secondParagraphStringId,
            int infoDrawableId, SensorAnimationBehavior sensorAnimationBehavior,
            int pointsAfterDecimalInNumberFormat, String builtInSensorId) {
        return new BuiltInSensorAppearance(nameStringId, drawableId, unitsStringId,
                shortDescriptionId, firstParagraphStringId, secondParagraphStringId, infoDrawableId,
                sensorAnimationBehavior, pointsAfterDecimalInNumberFormat, builtInSensorId);
    }

    public BuiltInSensorAppearance(int nameStringId, int drawableId,
            String builtInSensorId) {
        this(nameStringId, drawableId, 0, SensorAnimationBehavior.createDefault(), builtInSensorId);
    }

    public BuiltInSensorAppearance(int nameStringId, int drawableId, int shortDescriptionId,
            SensorAnimationBehavior sensorAnimationBehavior, String builtInSensorId) {
        this(nameStringId, drawableId, 0, shortDescriptionId, 0, 0, 0, sensorAnimationBehavior,
                DEFAULT_POINTS_AFTER_DECIMAL, builtInSensorId);
    }

    BuiltInSensorAppearance(int nameStringId, int drawableId, int unitsStringId,
            int shortDescriptionId, int firstParagraphStringId, int secondParagraphStringId,
            int infoDrawableId, SensorAnimationBehavior sensorAnimationBehavior,
            int pointsAfterDecimalInNumberFormat, String builtInSensorId) {
        mNameStringId = nameStringId;
        mDrawableId = drawableId;
        mUnitsStringId = unitsStringId;
        mShortDescriptionStringId = shortDescriptionId;
        mFirstParagraphStringId = firstParagraphStringId;
        mSecondParagraphStringId = secondParagraphStringId;
        mLearnMoreDrawableId = infoDrawableId;
        mSensorAnimationBehavior = sensorAnimationBehavior;
        mPointsAfterDecimal = pointsAfterDecimalInNumberFormat;
        mNumberFormat = createNumberFormat(pointsAfterDecimalInNumberFormat);
        mBuiltInSensorId = builtInSensorId;
    }

    @Override
    public String getName(Context context) {
        return context.getResources().getString(mNameStringId);
    }

    @Override
    public String getUnits(Context context) {
        return getString(context, mUnitsStringId);
    }

    @Override
    public Drawable getIconDrawable(Context context) {
        return context.getResources().getDrawable(mDrawableId);
    }

    @Override
    public String getShortDescription(Context context) {
        return getString(context, mShortDescriptionStringId);
    }

    @Override
    public boolean hasLearnMore() {
        return mFirstParagraphStringId != 0;
    }

    @Override
    public Single<LearnMoreContents> loadLearnMore(final Context context) {
        return Single.just(new LearnMoreContents() {
            @Override
            public String getFirstParagraph() {
                return getString(context, mFirstParagraphStringId);
            }

            @Override
            public int getDrawableResourceId() {
                return mLearnMoreDrawableId;
            }

            @Override
            public String getSecondParagraph() {
                return getString(context, mSecondParagraphStringId);
            }
        });
    }

    @Override
    public GoosciIcon.IconPath getSmallIconPath() {
        GoosciIcon.IconPath path = new GoosciIcon.IconPath();
        path.type = GoosciIcon.IconPath.BUILTIN;
        path.pathString = Preconditions.checkNotNull(mBuiltInSensorId);
        return path;
    }

    @Override
    public GoosciIcon.IconPath getLargeIconPath() {
        GoosciIcon.IconPath path = new GoosciIcon.IconPath();
        path.type = GoosciIcon.IconPath.BUILTIN;
        path.pathString = Preconditions.checkNotNull(mBuiltInSensorId);
        return path;
    }

    @Override
    public SensorAnimationBehavior getSensorAnimationBehavior() {
        return mSensorAnimationBehavior;
    }

    private String getString(Context context, int id) {
        if (id != 0) {
            return context.getResources().getString(id);
        }
        return "";
    }

    @Override
    public NumberFormat getNumberFormat() {
        return mNumberFormat;
    }

    @Override
    public int getPointsAfterDecimal() {
        return mPointsAfterDecimal;
    }

    private static NumberFormat createNumberFormat(int pointsAfterDecimalInNumberFormat) {
        if (pointsAfterDecimalInNumberFormat <= DEFAULT_POINTS_AFTER_DECIMAL) {
            return new AxisNumberFormat();
        } else {
            pointsAfterDecimalInNumberFormat = Math.min(pointsAfterDecimalInNumberFormat,
                    MAX_POINTS_AFTER_DECIMAL);
            final String format = "%." + pointsAfterDecimalInNumberFormat + "f";
            return new NumberFormat() {
                @Override
                public StringBuffer format(double value, StringBuffer buffer, FieldPosition field) {
                    return buffer.append(String.format(format, value));
                }

                @Override
                public StringBuffer format(long value, StringBuffer buffer, FieldPosition field) {
                    return format((double) value, buffer, field);
                }

                @Override
                public Number parse(String string, ParsePosition position) {
                    return null;
                }
            };
        }
    }

    // TODO: Is there a way to cache this instead of re-constructing it each time it is needed?
    public static String formatValue(double value, int pointsAfterDecimal) {
        NumberFormat format = createNumberFormat(pointsAfterDecimal);
        return format.format(value);
    }
}
