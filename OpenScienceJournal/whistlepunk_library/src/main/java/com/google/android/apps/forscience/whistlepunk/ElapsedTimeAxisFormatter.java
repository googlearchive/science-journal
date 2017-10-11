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
import android.util.Log;
import android.util.LruCache;

import java.util.HashMap;

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
    private LruCache<Long, String> mCacheWithTenths;
    private LruCache<Long, String> mCacheWithoutTenths;

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

        mCacheWithTenths = new LruCache<>(128 /* entries */);
        mCacheWithoutTenths = new LruCache<>(128 /* entries */);
    }

    /**
     * Returns a formatted string for elapsedTimeMs using a cache, if a miss occurs, then compute
     * the formatted string and add it to the cache
     * @param elapsedTimeMs Timestamp to be formatted
     * @param includeTenths Whether to include the tenths place in the formatted string
     * @return The formatted string in HH:MM:SS or HH:MM:SS.T
     */
    public String format(long elapsedTimeMs, boolean includeTenths) {
        long absoluteElapsedTimeMs = Math.abs(elapsedTimeMs);
        // Key into the cache based on timestamp with
        // reduced precision to increase hit likelihood
        long timeIndex;
        String formattedString;
        if (includeTenths) {
            timeIndex = elapsedTimeMs/100;
            formattedString = mCacheWithTenths.get(timeIndex);
        } else {
            timeIndex = elapsedTimeMs/1000;
            formattedString = mCacheWithoutTenths.get(timeIndex);
        }

        // Cache hit
        if (formattedString != null) {
            return formattedString;
        }

        long hours = ElapsedTimeUtils.getHours(absoluteElapsedTimeMs);
        long minutes = ElapsedTimeUtils.getMins(absoluteElapsedTimeMs, hours);
        long seconds = ElapsedTimeUtils.getSecs(absoluteElapsedTimeMs, hours, minutes);
        if (includeTenths) {
            long tenths = ElapsedTimeUtils.getTenthsOfSecs(absoluteElapsedTimeMs, hours, minutes,
                    seconds);
            if (hours > 0) {
                formattedString = mFormatter.format(mLargeFormatTenths, hours, minutes, seconds,
                        tenths).toString();
            } else {
                formattedString = mFormatter.format(mSmallFormatTenths, minutes, seconds, tenths)
                        .toString();
            }
        } else {
            if (hours > 0) {
                formattedString = mFormatter.format(mLargeFormat, hours, minutes, seconds)
                        .toString();
            } else {
                formattedString = mFormatter.format(mSmallFormat, minutes, seconds).toString();
            }
        }

        boolean isNegative = elapsedTimeMs < 0;
        if (isNegative) {
            formattedString = mFormatter.format("-%s", formattedString).toString();
        }

        if (includeTenths) {
            mCacheWithTenths.put(timeIndex, formattedString);
        } else {
            mCacheWithoutTenths.put(timeIndex, formattedString);
        }

        return formattedString;
    }
}
