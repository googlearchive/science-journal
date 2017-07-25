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
import android.content.res.Resources;

/**
 * Formats elapsed time (in ms) to a short m:ss or h:mm:ss format.
 * Can also format to tenths of a second using formatToTenths.
 */
// TODO: Switch to using JodaTime library or DateUtils.formatElapsedTime.
public class ElapsedTimeAxisFormatter {

    private static ElapsedTimeAxisFormatter sInstance;
    private final String mSmallFormat;
    private final String mLargeFormat;
    private final String mSmallFormatTenths;
    private final String mLargeFormatTenths;
    private long mTempHours;
    private long mTempMins;
    private long mTempSecs;
    private long mTempTenthsOfSecs;

    private final ReusableFormatter mFormatter;

    public static ElapsedTimeAxisFormatter getInstance(Context context) {
        if (sInstance == null) {
            sInstance = new ElapsedTimeAxisFormatter(context);
        }
        return sInstance;
    }

    private ElapsedTimeAxisFormatter(Context context) {
        Resources res = context.getResources();
        mSmallFormat = res.getString(R.string.elapsed_time_axis_format_small);
        mLargeFormat = res.getString(R.string.elapsed_time_axis_format_large);
        mSmallFormatTenths = res.getString(R.string.elapsed_time_axis_format_small_tenths);
        mLargeFormatTenths = res.getString(R.string.elapsed_time_axis_format_large_tenths);

        mFormatter = new ReusableFormatter();
    }

    public String format(long elapsedTimeMs) {
        updateElapsedTimeValues(elapsedTimeMs);
        String result;
        if (mTempHours > 0) {
            result = mFormatter.format(mLargeFormat, mTempHours, mTempMins, mTempSecs).toString();
        } else {
            result = mFormatter.format(mSmallFormat, mTempMins, mTempSecs).toString();
        }
        boolean isNegative = elapsedTimeMs < 0;
        if (!isNegative) {
            return result;
        }
        return mFormatter.format("-%s", result).toString();
    }

    public String formatToTenths(long elapsedTimeMs) {
        updateElapsedTimeValues(elapsedTimeMs);
        String result;
        if (mTempHours > 0) {
            result = mFormatter.format(mLargeFormatTenths, mTempHours, mTempMins, mTempSecs,
                    mTempTenthsOfSecs).toString();
        } else {
            result = mFormatter.format(mSmallFormatTenths, mTempMins, mTempSecs, mTempTenthsOfSecs)
                    .toString();
        }
        boolean isNegative = elapsedTimeMs < 0;
        return (isNegative ? "-" : "") + result;
    }

    /**
     * Gets the values of the elapsed time.
     * @param elapsedTimeMs
     * @return An array of {hours, minutes, seconds, tenths of seconds}
     */
    private void updateElapsedTimeValues(long elapsedTimeMs) {
        elapsedTimeMs = Math.abs(elapsedTimeMs);
        mTempHours = ElapsedTimeUtils.getHours(elapsedTimeMs);
        mTempMins = ElapsedTimeUtils.getMins(elapsedTimeMs, mTempHours);
        mTempSecs = ElapsedTimeUtils.getSecs(elapsedTimeMs, mTempHours, mTempMins);
        mTempTenthsOfSecs = ElapsedTimeUtils.getTenthsOfSecs(elapsedTimeMs, mTempHours, mTempMins,
                mTempSecs);
    }
}
