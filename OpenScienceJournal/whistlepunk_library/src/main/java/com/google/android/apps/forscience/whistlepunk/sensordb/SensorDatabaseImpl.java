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

package com.google.android.apps.forscience.whistlepunk.sensordb;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import com.google.android.apps.forscience.whistlepunk.sensorapi.StreamConsumer;
import com.google.common.base.Joiner;
import com.google.common.collect.DiscreteDomain;
import com.google.common.collect.Range;

import java.util.ArrayList;
import java.util.List;

public class SensorDatabaseImpl implements SensorDatabase {
    private static class DbVersions {
        public static final int V1_START = 1;
        public static final int V2_INDEX = 2;
        public static final int V3_TIER = 3;
        public static final int CURRENT = V3_TIER;
    }

    private static class ScalarSensorsTable {
        public static final String NAME = "scalar_sensors";

        public static class Column {
            public static final String TAG = "tag";
            public static final String RESOLUTION_TIER = "resolutionTier";
            public static final String TIMESTAMP_MILLIS = "timestampMillis";
            public static final String VALUE = "value";
        }

        public static final String CREATION_SQL = "CREATE TABLE " + NAME + " (" + Column.TAG + " " +
                " TEXT, " + Column.TIMESTAMP_MILLIS + " INTEGER, " + Column.VALUE + " REAL,"
                + Column.RESOLUTION_TIER + " INTEGER DEFAULT 0);";

        public static final String INDEX_SQL =
                "CREATE INDEX timestamp ON " + NAME + "(" + Column.TIMESTAMP_MILLIS + ");";
    }

    private final SQLiteOpenHelper mOpenHelper;

    public SensorDatabaseImpl(Context context, String name) {
        mOpenHelper = new SQLiteOpenHelper(context, name, null, DbVersions.CURRENT) {
            @Override
            public void onCreate(SQLiteDatabase db) {
                db.execSQL(ScalarSensorsTable.CREATION_SQL);
                db.execSQL(ScalarSensorsTable.INDEX_SQL);
            }

            @Override
            public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
                while (oldVersion != newVersion) {
                    if (oldVersion == DbVersions.V1_START) {
                        db.execSQL(ScalarSensorsTable.INDEX_SQL);
                        oldVersion = DbVersions.V2_INDEX;
                    } else if (oldVersion == DbVersions.V2_INDEX) {
                        db.execSQL("ALTER TABLE " + ScalarSensorsTable.NAME + " ADD COLUMN "
                                + ScalarSensorsTable.Column.RESOLUTION_TIER + " INTEGER DEFAULT 0;");
                        oldVersion = DbVersions.V3_TIER;
                    }
                }
            }
        };
    }

    @Override
    public void addScalarReading(String sourceTag, int resolutionTier, long timestampMillis,
            double value) {
        ContentValues values = new ContentValues();
        values.put(ScalarSensorsTable.Column.TAG, sourceTag);
        values.put(ScalarSensorsTable.Column.TIMESTAMP_MILLIS, timestampMillis);
        values.put(ScalarSensorsTable.Column.VALUE, value);
        values.put(ScalarSensorsTable.Column.RESOLUTION_TIER, resolutionTier);
        mOpenHelper.getWritableDatabase().insert(ScalarSensorsTable.NAME, null, values);
    }

    @Override
    public ScalarReadingList getScalarReadings(String sensorTag, TimeRange range,
            int resolutionTier, int maxRecords) {
        List<String> clauses = new ArrayList<>();
        List<String> values = new ArrayList<>();

        clauses.add(ScalarSensorsTable.Column.TAG + " = ?");
        values.add(sensorTag);

        clauses.add(ScalarSensorsTable.Column.RESOLUTION_TIER + " = ?");
        values.add(String.valueOf(resolutionTier));

        Range<Long> times = range.getTimes();
        Range<Long> closedOpen = times.canonical(DiscreteDomain.longs());
        if (closedOpen.hasLowerBound()) {
            clauses.add(ScalarSensorsTable.Column.TIMESTAMP_MILLIS + " >= ?");
            values.add(String.valueOf(closedOpen.lowerEndpoint()));
        }
        if (closedOpen.hasUpperBound()) {
            clauses.add(ScalarSensorsTable.Column.TIMESTAMP_MILLIS + " < ?");
            values.add(String.valueOf(closedOpen.upperEndpoint()));
        }

        String[] columns =
                {ScalarSensorsTable.Column.TIMESTAMP_MILLIS, ScalarSensorsTable.Column.VALUE};
        String selection = Joiner.on(" AND ").join(clauses);
        String[] selectionArgs = values.toArray(new String[values.size()]);
        String orderBy = ScalarSensorsTable.Column.TIMESTAMP_MILLIS + (range.getOrder().equals(
                TimeRange.ObservationOrder.OLDEST_FIRST) ? " ASC" : " DESC");
        String limit = maxRecords <= 0 ? null : String.valueOf(maxRecords);
        Cursor cursor = mOpenHelper.getReadableDatabase().query(ScalarSensorsTable.NAME, columns,
                selection, selectionArgs, null, null, orderBy, limit);
        try {
            final int max = maxRecords <= 0 ? cursor.getCount() : maxRecords;
            final long[] readTimestamps = new long[max];
            final double[] readValues = new double[max];
            int i = 0;
            while (cursor.moveToNext()) {
                readTimestamps[i] = cursor.getLong(0);
                readValues[i] = cursor.getDouble(1);
                i++;
            }
            final int actualCount = i;
            return new ScalarReadingList() {
                @Override
                public void deliver(StreamConsumer c) {
                    for (int i = 0; i < actualCount; i++) {
                        c.addData(readTimestamps[i], readValues[i]);
                    }
                }

                @Override
                public int size() {
                    return actualCount;
                }
            };
        } finally {
            cursor.close();
        }
    }

    // TODO: test
    @Override
    public String getFirstDatabaseTagAfter(long timestamp) {
        final String timestampString = String.valueOf(timestamp);
        final Cursor cursor = mOpenHelper.getReadableDatabase().query(ScalarSensorsTable.NAME,
                new String[]{ScalarSensorsTable.Column.TAG},
                ScalarSensorsTable.Column.TIMESTAMP_MILLIS + ">?", new String[]{timestampString},
                null, null, ScalarSensorsTable.Column.TIMESTAMP_MILLIS + " ASC", "1");
        try {
            if (cursor.moveToNext()) {
                return cursor.getString(0);
            } else {
                return null;
            }
        } finally {
            cursor.close();
        }
    }
}
