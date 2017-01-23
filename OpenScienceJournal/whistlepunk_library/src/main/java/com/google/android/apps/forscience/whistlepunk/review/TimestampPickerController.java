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

package com.google.android.apps.forscience.whistlepunk.review;

import android.content.Context;
import android.support.annotation.VisibleForTesting;

import com.google.android.apps.forscience.whistlepunk.R;

import org.joda.time.DateTime;
import org.joda.time.Duration;
import org.joda.time.Period;
import org.joda.time.format.PeriodFormatter;
import org.joda.time.format.PeriodFormatterBuilder;

import java.util.Locale;


/**
 * A timestamp picker class.
 */
public class TimestampPickerController {

    private static final String TAG = "TimestampPickerCtrlr";
    public static final int NO_ERROR = -1;

    public interface TimestampPickerListener {
        /**
         * Allows for additional checks of timestamp validity.
         * @param timestamp The timestamp to check for validity
         * @param isStartCrop Whether this is the start or end crop timestamp.
         * @return an error string ID if the timestamp is not valid, or NO_ERROR if it is valid.
         */
        int isValidTimestamp(long timestamp, boolean isStartCrop);

        /**
         * Called when the timestamp is successfully changed in the picker.
         * @param timestamp The updated timestamp
         */
        void onPickerTimestampChanged(long timestamp, boolean isStartCrop);
    }

    public interface OnTimestampErrorListener {
        void onTimestampError(int errorId);
    }

    private DateTime mRunStartTime;
    private DateTime mRunEndTime;
    private DateTime mZeroTime;
    private DateTime mSelectedTime;

    private String mNegativePrefix;

    private PeriodFormatter mPeriodFormatter;
    private Locale mLocale;
    private boolean mIsStartCrop;

    private TimestampPickerListener mListener;
    private OnTimestampErrorListener mErrorListener;

    public TimestampPickerController(Locale locale, Context context, boolean isStartCrop,
            OnTimestampErrorListener errorListener) {
        this(locale, isStartCrop, context.getResources().getString(R.string.negative_prefix),
                context.getResources().getString(R.string.hour_minute_divider),
                context.getResources().getString(R.string.minute_second_divider),
                errorListener);
    }

    @VisibleForTesting
    public TimestampPickerController(Locale locale, boolean isStartCrop, String negativePrefix,
            String hourMinuteDivider, String minuteSecondDivider,
            OnTimestampErrorListener errorListener) {
        mLocale = locale;
        mIsStartCrop = isStartCrop;
        mErrorListener = errorListener;
        mNegativePrefix = negativePrefix;
        // Builds the formatter, which will be used to read and write timestamp strings.
        mPeriodFormatter = new PeriodFormatterBuilder()
                .rejectSignedValues(true)  // Applies to all fields
                .printZeroAlways()  // Applies to all fields
                .appendHours()
                .appendLiteral(hourMinuteDivider)
                .minimumPrintedDigits(2)  // Applies to minutes and seconds
                .appendMinutes()
                .appendLiteral(minuteSecondDivider)
                .appendSecondsWithMillis()
                .toFormatter()
                .withLocale(mLocale);
    }

    /**
     * Sets the range the picker can choose from, the point that is labeled "zero", and the
     * currently selected point.
     * @param minMs The minimum allowable value (i.e. the run original start when cropping).
     * @param maxMs The maximum allowable value (i.e. the run original end when cropping).
     * @param zeroMs The zero value (i.e. the run current start when cropping).
     * @param currentValueMs The currently selected timestamp.
     */
    public void setTimestampRange(long minMs, long maxMs, long zeroMs, long currentValueMs) {
        mRunStartTime = new DateTime(minMs);
        mRunEndTime = new DateTime(maxMs);
        mZeroTime = new DateTime(zeroMs);
        mSelectedTime = new DateTime(currentValueMs);
    }

    public void setOnPickerTimestampChangedListener(
            TimestampPickerListener onPickerTimestampChangedListener) {
        mListener = onPickerTimestampChangedListener;
    }

    public String getTimeString() {
        Duration duration;
        String prefix = "";
        if (mSelectedTime.isAfter(mZeroTime) || mSelectedTime.isEqual(mZeroTime)) {
            duration = new Duration(mZeroTime, mSelectedTime);
        } else {
            duration = new Duration(mSelectedTime, mZeroTime);
            prefix = mNegativePrefix;
        }
        return prefix + mPeriodFormatter.print(duration.toPeriod());
    }

    /**
     * Tries to save the timestamp back to the main activity.
     */
    public boolean trySavingTimestamp(String timeString) {
        int errorId = updateSelectedTime(timeString);
        if (errorId == NO_ERROR) {
            if (mListener != null) {
                mListener.onPickerTimestampChanged(mSelectedTime.getMillis(), mIsStartCrop);
            }
            return true;
        }
        mErrorListener.onTimestampError(errorId);
        return false;
    }

    /**
     * Determines if the current timestamp created from the text fields is a valid one, and
     * if so, updates the selected time to match the input string.
     * @return null if the text fields represent a valid timestamp, otherwise an error message ID
     *         which can be displayed to the user to explain what was wrong.
     */
    @VisibleForTesting
    int updateSelectedTime(String timeString) {
        // See if it is negative separately from parsing.
        boolean isNegative = timeString.startsWith(mNegativePrefix);
        if (isNegative) {
            timeString = timeString.substring(1);
        }
        try {
            Period selectedPeriod = mPeriodFormatter.parsePeriod(timeString);
            if (isNegative) {
                mSelectedTime = mZeroTime.minus(selectedPeriod);
            } else {
                mSelectedTime = mZeroTime.plus(selectedPeriod);
            }
        } catch (IllegalArgumentException ex) {
            return R.string.timestamp_picker_format_error;
        }

        // Make sure it is in the run
        if (mSelectedTime.isBefore(mRunStartTime) || mSelectedTime.isAfter(mRunEndTime)) {
            return R.string.timestamp_picker_range_error;
        }
        if (mListener != null) {
            return mListener.isValidTimestamp(mSelectedTime.getMillis(), mIsStartCrop);
        } else {
            return NO_ERROR;
        }
    }

    @VisibleForTesting
    long getSelectedTime() {
        return mSelectedTime.getMillis();
    }
}
