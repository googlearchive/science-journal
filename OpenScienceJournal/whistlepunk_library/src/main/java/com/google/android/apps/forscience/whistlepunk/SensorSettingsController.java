package com.google.android.apps.forscience.whistlepunk;

import com.google.android.apps.forscience.javalib.FailureListener;
import com.google.android.apps.forscience.whistlepunk.sensorapi.NewOptionsStorage;
import com.google.android.apps.forscience.whistlepunk.sensorapi.OptionsListener;
import com.google.android.apps.forscience.whistlepunk.sensorapi.SensorChoice;
import com.google.android.apps.forscience.whistlepunk.sensorapi.SensorPresenter;

/**
 * Interface for launching an options dialog for a sensor.
 */
public interface SensorSettingsController {
    void launchOptionsDialog(SensorChoice source, SensorPresenter presenter,
            NewOptionsStorage storage, OptionsListener commitListener,
            FailureListener failureListener);
}
