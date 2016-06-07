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
                layout.color = resources.getColor(findKioskColor(color));
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

    private static int findKioskColor(String color) {
        switch (color) {
            case PARAM_RED:
                return R.color.graph_line_color_red;
            case PARAM_YELLOW:
                return R.color.graph_line_color_yellow;
            case PARAM_GREEN:
                return R.color.graph_line_color_green;
            case PARAM_BLUE:
            default:
                return R.color.graph_line_color_blue;
        }
    }
}
