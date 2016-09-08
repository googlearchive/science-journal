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

package com.google.android.apps.forscience.whistlepunk.metadata;

import com.google.android.apps.forscience.whistlepunk.DataController;
import com.google.android.apps.forscience.whistlepunk.LoggingConsumer;

/**
 * Utility functions for cropping.
 */
public class CropUtils {
    public static final String TAG = "CropUtils";

    public static class CropLabels {
        public ApplicationLabel cropStartLabel;
        public ApplicationLabel cropEndLabel;

        public CropLabels() {

        }
    }

    public interface CropRunListener {
        void onCropCompleted();
        void onCropFailed();
    }

    public static void cropRun(final DataController dc, ExperimentRun run, long startTimestamp,
            long endTimestamp, final CropRunListener listener) {

        // Are we trying to crop too wide? Are the timestamps valid?
        if (startTimestamp < run.getOriginalFirstTimestamp() ||
                run.getOriginalLastTimestamp() < endTimestamp || endTimestamp < startTimestamp) {
            listener.onCropFailed();
            return;
        }

        final CropLabels cropLabels = run.getCropLabels();
        if (cropLabels.cropStartLabel != null && cropLabels.cropStartLabel != null) {
            // Check if it is already cropped, so we can edit the old crop labels.
            cropLabels.cropStartLabel.setTimestamp(startTimestamp);
            cropLabels.cropEndLabel.setTimestamp(endTimestamp);
            dc.editLabel(cropLabels.cropStartLabel,
                    new LoggingConsumer<Label>(TAG, "edit crop start label") {
                        @Override
                        public void success(Label value) {
                            dc.editLabel(cropLabels.cropEndLabel,
                                    new LoggingConsumer<Label>(TAG,"edit crop end label") {
                                        @Override
                                        public void success(Label value) {
                                            listener.onCropCompleted();
                                        }
                            });
                        }
            });
        } else if (cropLabels.cropStartLabel == null && cropLabels.cropStartLabel == null) {
            // Otherwise we make new crop labels.
            ApplicationLabel cropStartLabel = new ApplicationLabel(
                    ApplicationLabel.TYPE_CROP_START, dc.generateNewLabelId(), run.getRunId(),
                    startTimestamp);
            final ApplicationLabel cropEndLabel = new ApplicationLabel(
                    ApplicationLabel.TYPE_CROP_END, dc.generateNewLabelId(), run.getRunId(),
                    endTimestamp);

            // Update the run.
            cropLabels.cropStartLabel = cropStartLabel;
            cropLabels.cropEndLabel = cropEndLabel;

            // Add new crop labels to the database.
            dc.addLabel(cropStartLabel, new LoggingConsumer<Label>(TAG, "add crop start label") {
                @Override
                public void success(Label value) {
                    dc.addLabel(cropEndLabel,
                            new LoggingConsumer<Label>(TAG, "Add crop end label") {
                                @Override
                                public void success(Label value) {
                                    listener.onCropCompleted();
                                }
                            });
                }
            });
        } else {
            // One crop label is set and the other is not. This is an error!
            listener.onCropFailed();
        }
    }

    public static void throwAwayDataOutsideCroppedRegion(DataController dc, ExperimentRun run) {
        // TODO
    }
}
