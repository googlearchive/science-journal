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

package com.google.android.apps.forscience.whistlepunk.analytics;

import com.google.android.apps.forscience.whistlepunk.SensorRegistry;
import com.google.android.apps.forscience.whistlepunk.filemetadata.Label;
import com.google.android.apps.forscience.whistlepunk.metadata.GoosciLabel;

/**
 * Constants for usage tracking.
 */
public final class TrackerConstants {

    // Screen names.
    // public static final String SCREEN_INTRO = "intro";
    // public static final String SCREEN_INTRO_REPLAY = "intro_replay";
    // public static final String SCREEN_OBSERVE_RECORD = "observe_record";
    public static final String SCREEN_EXPERIMENT_LIST = "experiment_list";
    // public static final String SCREEN_NEW_EXPERIMENT = "experiment_new";
    public static final String SCREEN_UPDATE_EXPERIMENT = "experiment_update";
    public static final String SCREEN_EXPERIMENT_DETAIL = "experiment_detail";
    public static final String SCREEN_RUN_REVIEW = "run_review";
    public static final String SCREEN_SETTINGS = "settings";
    public static final String SCREEN_ABOUT = "about";
    public static final String SCREEN_DEVICE_MANAGER = "device_manager";
    public static final String SCREEN_DEVICE_OPTIONS = "device_options";
    public static final String SCREEN_UPDATE_RUN = "run_update";
    public static final String SCREEN_TRIGGER_LIST = "trigger_list";
    public static final String SCREEN_TRIGGER_EDIT = "trigger_edit";
    public static final String SCREEN_SENSOR_INFO = "sensor_info";
    public static final String SCREEN_PANES = "panes";

    // Custom dimension indices.
    public static final int DIMENSION_MODE = 1;
    public static final int RELEASE_TYPE = 2;
    public static final int PANES_TOOL_NAME = 3;
    public static final int PANES_DRAWER_STATE = 4;

    // Categories
    public static final String CATEGORY_EXPERIMENTS = "Experiments";
    public static final String CATEGORY_RUNS = "Runs";
    public static final String CATEGORY_NOTES = "Notes";
    public static final String CATEGORY_APP = "App";
    public static final String CATEGORY_TRIGGERS = "Triggers";
    public static final String CATEGORY_API = "API";
    public static final String CATEGORY_INFO = "Info";
    public static final String CATEGORY_SENSOR_MANAGEMENT = "ManageSensors";
    public static final String CATEGORY_STORAGE = "Storage";
    public static final String CATEGORY_PANES = "Panes";

    // Event actions
    public static final String ACTION_CREATE = "Create";
    public static final String ACTION_RECORDED = "Recorded";
    public static final String ACTION_EDITED = "EditedValue";
    public static final String ACTION_ARCHIVE = "Archived";
    public static final String ACTION_UNARCHIVE = "Unarchived";
    public static final String ACTION_DELETED = "Deleted";
    public static final String ACTION_DELETE_UNDO = "UndoDelete";
    public static final String ACTION_SET_MODE = "SetMode";
    public static final String ACTION_START_AUDIO_PLAYBACK = "StartAudioPlayback";
    public static final String ACTION_TRY_RECORDING_FROM_TRIGGER = "TryRecordingFromTrigger";
    public static final String ACTION_TRY_STOP_RECORDING_FROM_TRIGGER =
            "TryStopRecordingFromTrigger";
    public static final String ACTION_API_SCAN_TIMEOUT = "ApiScanTimeout";
    public static final String ACTION_INFO = "Info";
    public static final String ACTION_SCAN = "Scan";
    public static final String ACTION_CROP_COMPLETED = "CropCompleted";
    public static final String ACTION_CROP_STARTED = "CropStarted";
    public static final String ACTION_CROP_FAILED = "CropFailed";
    public static final String ACTION_WRITE_FAILED = "WriteFailed";
    public static final String ACTION_READ_FAILED = "ReadFailed";
    public static final String ACTION_PAUSED = "Paused";
    public static final String ACTION_LABEL_ADDED = "LabelAdded";

    // Labels
    public static final String LABEL_RECORD = "record";
    public static final String LABEL_RUN_REVIEW = "run_review";
    public static final String LABEL_OBSERVE = "observe";
    public static final String LABEL_EXPERIMENT_DETAIL = "experiment_detail";
    public static final String LABEL_EXPERIMENT_LIST = "experiment_list";
    public static final String LABEL_UPDATE_EXPERIMENT = "update_experiment";

    public static final String LABEL_MODE_CHILD = "child";
    public static final String LABEL_MODE_NONCHILD = "nonchild";

    // Values
    private static final long VALUE_TYPE_TEXT = 0;
    private static final long VALUE_TYPE_PICTURE = 1;
    private static final long VALUE_TYPE_SENSOR_TRIGGER = 2;
    private static final long VALUE_TYPE_SNAPSHOT = 3;

    // Primes Event Names
    public static final String PRIMES_OBSERVE = "OBSERVE";
    public static final String PRIMES_EXPERIMENT_LOADED = "EXPERIMENT_LOADED";
    public static final String PRIMES_EXPERIMENT_LIST_LOADED = "EXPERIMENT_LIST_LOADED";
    public static final String PRIMES_RUN_LOADED = "RUN_LOADED";
    public static final String PRIMES_DEFAULT_EXPERIMENT_CREATED = "DEFAULT_EXPERIMENT_CREATED";

    private TrackerConstants() {}

    /**
     * Gets the logging type for a label.
     */
    // TODO: Add tracking for snapshot labels.
    public static long getLabelValueType(Label label) {
        if (label.getType() == GoosciLabel.Label.PICTURE) {
            return TrackerConstants.VALUE_TYPE_PICTURE;
        } else if (label.getType() == GoosciLabel.Label.TEXT) {
            return TrackerConstants.VALUE_TYPE_TEXT;
        } else if (label.getType() == GoosciLabel.Label.SENSOR_TRIGGER) {
            return TrackerConstants.VALUE_TYPE_SENSOR_TRIGGER;
        } else if (label.getType() == GoosciLabel.Label.SNAPSHOT) {
            return TrackerConstants.VALUE_TYPE_SNAPSHOT;
        } else {
            throw new IllegalArgumentException("Label type is not supported for logging.");
        }
    }
}
