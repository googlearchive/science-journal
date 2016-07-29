package com.google.android.apps.forscience.whistlepunk.metadata;

import android.app.Activity;
import android.content.res.Resources;
import android.text.TextUtils;

import com.google.android.apps.forscience.whistlepunk.AppSingleton;
import com.google.android.apps.forscience.whistlepunk.R;
import com.google.android.apps.forscience.whistlepunk.data.GoosciSensorLayout;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Utils for triggers and layouts.
 */
public class SensorLayoutTriggerUtils {

    /**
     * Adds the trigger ID to the layout's active triggers if it is not already in the list.
     */
    public static void addTriggerToLayoutActiveTriggers(GoosciSensorLayout.SensorLayout layout,
            String triggerId) {
        int oldSize = layout.activeSensorTriggerIds.length;
        for (int i = 0; i < oldSize; i++) {
            if (TextUtils.equals(layout.activeSensorTriggerIds[i], triggerId)) {
                // Then is it already in the list, no need to add it again.
                return;
            }
        }
        String[] newTriggerIds = new String[oldSize + 1];
        System.arraycopy(layout.activeSensorTriggerIds, 0, newTriggerIds, 0,
                layout.activeSensorTriggerIds.length);
        newTriggerIds[oldSize] = triggerId;
        layout.activeSensorTriggerIds = newTriggerIds;
    }

    /**
     * Removes the trigger ID from the layout's active triggers.
     */
    public static void removeTriggerFromLayoutActiveTriggers(GoosciSensorLayout.SensorLayout layout,
            String triggerId) {
        // Use an ArrayList intermediate for simplicity.
        List<String> triggersList = new ArrayList<>();
        triggersList.addAll(Arrays.asList(layout.activeSensorTriggerIds));
        if (triggersList.contains(triggerId)) {
            triggersList.remove(triggerId);
            layout.activeSensorTriggerIds = triggersList.toArray(new String[triggersList.size()]);
        }
    }

    public static String buildDescription(SensorTrigger trigger, Activity activity) {
        Resources res = activity.getResources();
        int actionType = trigger.getActionType();
        String action = "";
        if (actionType == GoosciSensorTriggerInformation.TriggerInformation.TRIGGER_ACTION_START_RECORDING) {
            action = res.getString(R.string.trigger_type_start_recording);
        } else if (actionType == GoosciSensorTriggerInformation.TriggerInformation.TRIGGER_ACTION_STOP_RECORDING) {
            action = res.getString(R.string.trigger_type_stop_recording);
        } else if (actionType == GoosciSensorTriggerInformation.TriggerInformation.TRIGGER_ACTION_NOTE) {
            action = res.getString(R.string.trigger_type_note);
        } else if (actionType == GoosciSensorTriggerInformation.TriggerInformation.TRIGGER_ACTION_ALERT) {
            action = res.getString(R.string.trigger_type_alert);
        }
        String units = AppSingleton.getInstance(activity)
                .getSensorAppearanceProvider().getAppearance(trigger.getSensorId())
                .getUnits(activity);
        Double value = trigger.getValueToTrigger();
        String result = "";
        int when = trigger.getTriggerWhen();
        if (when == GoosciSensorTriggerInformation.TriggerInformation.TRIGGER_WHEN_AT) {
            result = res.getString(R.string.trigger_when_at_description, action, value, units);
        } else if (when == GoosciSensorTriggerInformation.TriggerInformation.TRIGGER_WHEN_RISES_ABOVE) {
            result = res.getString(R.string.trigger_when_rises_above_description, action, value, units);
        } else if (when == GoosciSensorTriggerInformation.TriggerInformation.TRIGGER_WHEN_DROPS_BELOW) {
            result = res.getString(R.string.trigger_when_drops_below_description, action, value, units);
        }
        return result;
    }
}
