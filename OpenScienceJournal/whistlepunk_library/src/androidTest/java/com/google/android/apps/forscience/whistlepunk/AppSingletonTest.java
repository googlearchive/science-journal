package com.google.android.apps.forscience.whistlepunk;

import android.test.AndroidTestCase;

import com.google.common.collect.Lists;

import junit.framework.TestCase;

import java.util.ArrayList;


public class AppSingletonTest extends AndroidTestCase {
    public void testHistoryStorage() {
        SensorHistoryStorage storage = AppSingleton.getInstance(
                getContext()).getSensorEnvironment().getSensorHistoryStorage();
        ArrayList<String> ids = Lists.newArrayList(Arbitrary.string());
        storage.setMostRecentSensorIds(ids);
        assertEquals(ids, storage.getMostRecentSensorIds());
    }
}