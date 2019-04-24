package com.google.android.apps.forscience.whistlepunk.devicemanager;

/** Interface for listening for options changing. */
public interface DeviceOptionsListener {

  /**
   * Called when one sensor in the experiment is being replaced by another (which should be
   * displayed in the same place)
   *
   * @param oldSensorId
   * @param newSensorId
   */
  void onExperimentSensorReplaced(String oldSensorId, String newSensorId);

  /** Called when the user requests to remove the device from the experiment. */
  void onRemoveSensorFromExperiment(String experimentId, String sensorId);
}
