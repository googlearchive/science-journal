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

package com.google.android.apps.forscience.whistlepunk;

import android.content.res.Resources;
import android.support.annotation.NonNull;

import com.google.android.apps.forscience.whistlepunk.data.GoosciSensorLayout;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class CommandLineSpecs {
    private static final String PARAM_METER = "METER";
    private static final String PARAM_RED = "RED";
    protected static final String PARAM_YELLOW = "YELLOW";
    protected static final String PARAM_GREEN = "GREEN";
    protected static final String PARAM_BLUE = "BLUE";

    @NonNull
    public static List<GoosciSensorLayout.SensorLayout> buildLayouts(String sensorIds,
            Resources resources) {
        List<GoosciSensorLayout.SensorLayout> layouts = new ArrayList<>();
        for (String spec : sensorIds.split(",")) {
            String[] parts = spec.split(":");

            // Spec is sensorId:color:side, but can be just sensorId:color or just sensorId
            String id = parts[0];
            String color = parts.length > 1 ? parts[1] : null;
            String side = parts.length > 2 ? parts[2] : null;
            GoosciSensorLayout.SensorLayout layout =
                    new GoosciSensorLayout.SensorLayout();
            layout.sensorId = id;
            if (color != null) {
                layout.colorIndex = findKioskColor(color);
            }
            layout.cardView = findCardView(side);
            layouts.add(layout);
        }
        return layouts;
    }

    private static int findCardView(String side) {
        if (Objects.equals(PARAM_METER, side)) {
            return GoosciSensorLayout.SensorLayout.METER;
        } else {
            return GoosciSensorLayout.SensorLayout.GRAPH;
        }
    }

    // Returns an index into the graph_colors.xml array.
    private static int findKioskColor(String color) {
        switch (color) {
            case PARAM_RED:
                return 3;
            case PARAM_YELLOW:
                return 2;
            case PARAM_GREEN:
                return 1;
            case PARAM_BLUE:
            default:
                return 0;
        }
    }
}
