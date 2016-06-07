package com.google.android.apps.forscience.whistlepunk;

import android.content.Context;

/**
 * Subclass of {@link SensorAppearance} which includes the external device name.
 */
public class ExternalSensorAppearance extends SensorAppearance {

    private String mDeviceName;


    public ExternalSensorAppearance(int nameStringId, int drawableId, int unitsStringId,
            int shortDescriptionId, int firstParagraphStringId, int secondParagraphStringId,
            int infoDrawableId, SensorAnimationBehavior sensorAnimationBehavior,
            String deviceName) {
        super(nameStringId, drawableId, unitsStringId, shortDescriptionId, firstParagraphStringId,
                secondParagraphStringId, infoDrawableId, sensorAnimationBehavior);
        mDeviceName = deviceName;
    }

    @Override
    public String getName(Context context) {
        return context.getResources().getString(R.string.external_sensor_appearance_name,
                super.getName(context), mDeviceName);
    }
}
