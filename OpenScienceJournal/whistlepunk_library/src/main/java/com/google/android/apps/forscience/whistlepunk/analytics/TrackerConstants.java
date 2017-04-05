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

import android.content.Context;

import com.google.android.apps.forscience.whistlepunk.AppSingleton;
import com.google.android.apps.forscience.whistlepunk.filemetadata.Label;
import com.google.android.apps.forscience.whistlepunk.metadata.GoosciLabelValue;

/**
 * Constants for usage tracking.
 */
public final class TrackerConstants {

    // Screen names.
    public static final String SCREEN_INTRO = "intro";
    public static final String SCREEN_INTRO_REPLAY = "intro_replay";
    public static final String SCREEN_OBSERVE_RECORD = "observe_record";
    public static final String SCREEN_PROJECTS = "projects";
    public static final String SCREEN_PROJECT_DETAIL = "project_detail";
    public static final String SCREEN_NEW_PROJECT = "project_new";
    public static final String SCREEN_UPDATE_PROJECT = "project_update";
    public static final String SCREEN_NEW_EXPERIMENT = "experiment_new";
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

    // Custom dimension indices.
    public static final int DIMENSION_MODE = 1;
    public static final int RELEASE_TYPE = 2;

    // Categories
    public static final String CATEGORY_PROJECTS = "Projects";
    public static final String CATEGORY_EXPERIMENTS = "Experiments";
    public static final String CATEGORY_RUNS = "Runs";
    public static final String CATEGORY_NOTES = "Notes";
    public static final String CATEGORY_APP = "App";
    public static final String CATEGORY_TRIGGERS = "Triggers";
    public static final String CATEGORY_API = "API";
    public static final String CATEGORY_INFO = "Info";
    public static final String CATEGORY_SENSOR_MANAGEMENT = "ManageSensors";

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

    // Labels
    public static final String LABEL_RECORD = "record";
    public static final String LABEL_RUN_REVIEW = "run_review";
    public static final String LABEL_OBSERVE = "observe";
    public static final String LABEL_EXPERIMENT_DETAIL = "experiment_detail";

    public static final String LABEL_MODE_CHILD = "child";
    public static final String LABEL_MODE_NONCHILD = "nonchild";

    // Values
    public static final long VALUE_TYPE_TEXT = 0;
    public static final long VALUE_TYPE_PICTURE = 1;
    public static final long VALUE_TYPE_SENSOR_TRIGGER = 2;

    private TrackerConstants() {}

    /**
     * Gets the logging type for a label.
     */
    // TODO: Update this for labels that have multiple types
    public static long getLabelValueType(Label label) {
        if (label.hasValueType(GoosciLabelValue.LabelValue.PICTURE)) {
            return TrackerConstants.VALUE_TYPE_PICTURE;
        } else if (label.hasValueType(GoosciLabelValue.LabelValue.TEXT)) {
            return TrackerConstants.VALUE_TYPE_TEXT;
        } else if (label.hasValueType(GoosciLabelValue.LabelValue.SENSOR_TRIGGER)) {
            return TrackerConstants.VALUE_TYPE_SENSOR_TRIGGER;
        } else {
            throw new IllegalArgumentException("Label type is not supported for logging.");
        }
    }

    public static String getLoggingId(String sensorId, Context context) {
        return AppSingleton.getInstance(context).getSensorRegistry().getLoggingId(sensorId);
    }
}
