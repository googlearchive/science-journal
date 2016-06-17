package com.google.android.apps.forscience.ble;

import android.test.AndroidTestCase;

/**
 * Tests for {@link DeviceDiscoverer}
 */
public class DeviceDiscovererTest extends AndroidTestCase {

    private DeviceDiscoverer mDeviceDiscoverer;

    public void setUp() {
        DeviceDiscoverer.Callback callback = new DeviceDiscoverer.Callback();
        mDeviceDiscoverer = DeviceDiscoverer.getNewInstance(mContext);
    }

    public void tearDown() {
    }

    // TODO(goosci): fix this test.
    /*
    public void testExtractLongNameGoodCase() {
        byte[] scanRecord = new byte[62];
        String shortName = "Sci1234";
        String longName = "Rotation";

        // don't care what's in the first 31 bytes, start looking at byte 32.
        System.arraycopy(shortName.getBytes(), 0, scanRecord, 31, shortName.length());
        scanRecord[38] = (byte) longName.length();
        scanRecord[39] = 22; // AD Type
        scanRecord[40] = 10; // LSB of UUID
        scanRecord[41] = 24; // MSB of UUID
        System.arraycopy(longName.getBytes(), 0, scanRecord, 42, longName.length());

        String longname = mDeviceDiscoverer.extractLongName(scanRecord);
        assertEquals("Rotation (Sci1234)", longname);
    }
    */

    public void testExtractLongNameTruncatedScanRecord() {
        byte[] scanRecord = new byte[10];
        for (int i = 0; i < 10; ++i)
            scanRecord[i] = (byte) i;

        String longname = mDeviceDiscoverer.extractLongName(scanRecord);
        assertEquals("", longname);
    }

    public void testExtractLongNameEmptyScanRecord() {
        byte[] scanRecord = new byte[62];
        for (int i = 0; i < 62; ++i)
            scanRecord[i] = 0;

        String longname = mDeviceDiscoverer.extractLongName(scanRecord);
        assertEquals("", longname);
    }

    // TODO(goosci): fix this test.
    /*
    public void testExtractLongNameErroneousScanRecord() {
        byte[] scanRecord = new byte[62];
        for (int i = 0; i < 62; ++i)
            scanRecord[i] = (byte) i;

        String longname = mDeviceDiscoverer.extractLongName(scanRecord);
        assertEquals("", longname);
    }
    */
}
