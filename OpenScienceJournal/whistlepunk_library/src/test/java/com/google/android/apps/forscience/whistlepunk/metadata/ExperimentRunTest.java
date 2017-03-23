/*
 *  Copyright 2017 Google Inc. All Rights Reserved.
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

import com.google.android.apps.forscience.whistlepunk.data.GoosciSensorLayout;
import com.google.common.collect.Lists;

import org.junit.Test;

import java.util.ArrayList;

import static org.junit.Assert.assertEquals;

public class ExperimentRunTest {
    @Test
    public void elapsedSeconds() {
        Run run = new Run("runId", 0, new ArrayList<GoosciSensorLayout.SensorLayout>(), false);
        ApplicationLabel startLabel =
                new ApplicationLabel(ApplicationLabel.TYPE_RECORDING_START, "startLabelId",
                        "startLabelId", 7);
        ApplicationLabel stopLabel =
                new ApplicationLabel(ApplicationLabel.TYPE_RECORDING_STOP, "stopLabelId",
                        "startLabelId", 5007);

        ExperimentRun invalidRun =
                ExperimentRun.fromLabels(run, Lists.<Label>newArrayList(startLabel));
        assertEquals(0, invalidRun.elapsedSeconds());

        ExperimentRun validRun =
                ExperimentRun.fromLabels(run, Lists.<Label>newArrayList(startLabel, stopLabel));
        assertEquals(5, validRun.elapsedSeconds());
    }
}