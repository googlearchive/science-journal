package com.google.android.apps.forscience.whistlepunk;

import static org.junit.Assert.assertEquals;

import com.google.android.apps.forscience.whistlepunk.sensorapi.BlankReadableSensorOptions;
import com.google.android.apps.forscience.whistlepunk.sensorapi.RecordingSensorObserver;
import com.google.android.apps.forscience.whistlepunk.sensorapi.StubStatusListener;
import com.google.common.collect.Lists;

import org.junit.Test;

public class MemoryRecorderControllerTest {
    @Test public void basicMemoryRecorderControllerTest() {
        MemoryRecorderController rc = new MemoryRecorderController();
        String observerId = rc.startObserving("sensorId", new RecordingSensorObserver(),
                new StubStatusListener(),
                AbstractReadableSensorOptions.makeTransportable(new BlankReadableSensorOptions()));
        assertEquals(Lists.newArrayList("sensorId"), rc.getCurrentObservedIds());
        rc.stopObserving("sensorId", observerId);
        assertEquals(Lists.newArrayList(), rc.getCurrentObservedIds());
    }
}