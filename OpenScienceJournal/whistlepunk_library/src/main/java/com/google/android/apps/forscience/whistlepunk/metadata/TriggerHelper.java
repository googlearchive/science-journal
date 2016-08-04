package com.google.android.apps.forscience.whistlepunk.metadata;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.res.Resources;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Vibrator;
import android.text.TextUtils;
import android.util.Log;

import com.google.android.apps.forscience.whistlepunk.AppSingleton;
import com.google.android.apps.forscience.whistlepunk.PermissionUtils;
import com.google.android.apps.forscience.whistlepunk.R;
import com.google.android.apps.forscience.whistlepunk.data.GoosciSensorLayout;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Utils for triggers.
 */
public class TriggerHelper {

    private static final String TAG = "TriggerHelper";
    private static final long TRIGGER_VIBRATION_DURATION = 200;

    private static final MediaPlayer.OnCompletionListener MEDIA_PLAYER_COMPLETION_LISTENER =
            new MediaPlayer.OnCompletionListener() {
                @Override
                public void onCompletion(MediaPlayer mp) {
                    mp.reset();
                    mp.release();
                }
            };

    private static final MediaPlayer.OnPreparedListener MEDIA_PLAYER_ON_PREPARED_LISTENER =
            new MediaPlayer.OnPreparedListener() {
                @Override
                public void onPrepared(MediaPlayer mp) {
                    mp.start();
                }
            };

    private final Uri mNotification;
    private Vibrator mVibrator;

    public TriggerHelper(Uri notificationUri) {
        // TODO: Talk to UX about the best sound for this.
        mNotification = notificationUri;
    }

    public void doAudioAlert(Context context) {
        final MediaPlayer mediaPlayer =  new MediaPlayer();
        mediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
        try {
            mediaPlayer.setDataSource(context, mNotification);
        } catch (IOException e) {
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "error getting notification sound");
            }
        }
        mediaPlayer.setOnPreparedListener(MEDIA_PLAYER_ON_PREPARED_LISTENER);
        mediaPlayer.setOnCompletionListener(MEDIA_PLAYER_COMPLETION_LISTENER);
        // Don't prepare the mediaplayer on the UI thread! That's asking for trouble.
        mediaPlayer.prepareAsync();
    }

    public void doVibrateAlert(Context context) {
        // TODO: Talk to UX about desired duration or pattern.
        if (mVibrator == null) {
            mVibrator = ((Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE));
        }
        mVibrator.vibrate(TRIGGER_VIBRATION_DURATION);
    }

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
