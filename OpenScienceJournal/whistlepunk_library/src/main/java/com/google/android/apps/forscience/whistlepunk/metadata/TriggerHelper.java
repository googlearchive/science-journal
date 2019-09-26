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

import android.app.Activity;
import android.content.Context;
import android.content.res.Resources;
import android.media.SoundPool;
import android.os.SystemClock;
import android.os.Vibrator;
import com.google.android.apps.forscience.whistlepunk.AppSingleton;
import com.google.android.apps.forscience.whistlepunk.R;
import com.google.android.apps.forscience.whistlepunk.SensorAppearance;
import com.google.android.apps.forscience.whistlepunk.accounts.AppAccount;
import com.google.android.apps.forscience.whistlepunk.filemetadata.SensorLayoutPojo;
import com.google.android.apps.forscience.whistlepunk.filemetadata.SensorTrigger;
import com.google.android.apps.forscience.whistlepunk.metadata.GoosciSensorTriggerInformation.TriggerInformation.TriggerActionType;
import com.google.android.apps.forscience.whistlepunk.metadata.GoosciSensorTriggerInformation.TriggerInformation.TriggerWhen;

/** Utils for triggers. */
public class TriggerHelper {

  private static final String TAG = "TriggerHelper";
  private static final long TRIGGER_VIBRATION_DURATION_MS = 200;
  private static final long THROTTLE_LIMIT_MS = 200;

  private static int soundId = -1;
  private static SoundPool soundPool;

  private Vibrator vibrator;
  private long lastVibrationMs;
  private long lastAudioMs;

  public TriggerHelper() {}

  public void doAudioAlert(Context context) {
    // Use a throttler to keep this from interrupting itself too much.
    if (SystemClock.elapsedRealtime() - lastAudioMs < THROTTLE_LIMIT_MS) {
      return;
    }
    SoundPool soundPool = getSoundPool(context);
    if (soundId != -1) {
      lastAudioMs = SystemClock.elapsedRealtime();
      soundPool.play(soundId, .8f, .8f, 0, 0, 1.0f);
    }
  }

  private SoundPool getSoundPool(Context context) {
    if (TriggerHelper.soundPool == null) {
      TriggerHelper.soundPool = new SoundPool.Builder().build();
      TriggerHelper.soundPool.load(context, R.raw.trigger_sound1, 0);
      TriggerHelper.soundPool.setOnLoadCompleteListener(
          new SoundPool.OnLoadCompleteListener() {
            @Override
            public void onLoadComplete(SoundPool soundPool, int sampleId, int status) {
              soundId = sampleId;
              // Always play once directly after load, because only trying to do an
              // audio alert triggers a play.
              TriggerHelper.soundPool.play(soundId, 1.0f, 1.0f, 0, 0, 1.0f);
            }
          });
    }
    return TriggerHelper.soundPool;
  }

  public void doVibrateAlert(Context context) {
    // Use a throttler to keep this from interrupting itself too much.
    if (System.currentTimeMillis() - lastVibrationMs < THROTTLE_LIMIT_MS) {
      return;
    }
    lastVibrationMs = System.currentTimeMillis();
    if (vibrator == null) {
      vibrator = ((Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE));
    }
    vibrator.vibrate(TRIGGER_VIBRATION_DURATION_MS);
  }

  public static boolean hasVibrator(Context context) {
    return ((Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE)).hasVibrator();
  }

  /** Adds the trigger ID to the layout's active triggers if it is not already in the list. */
  public static void addTriggerToLayoutActiveTriggers(SensorLayoutPojo layout, String triggerId) {
    layout.addActiveTriggerId(triggerId);
  }

  /** Removes the trigger ID from the layout's active triggers. */
  public static void removeTriggerFromLayoutActiveTriggers(
      SensorLayoutPojo layout, String triggerId) {
    layout.removeTrigger(triggerId);
  }

  public static String buildDescription(
      SensorTrigger trigger, Activity activity, AppAccount appAccount) {
    Resources res = activity.getResources();
    TriggerActionType actionType = trigger.getActionType();
    String action = "";
    if (actionType == TriggerActionType.TRIGGER_ACTION_START_RECORDING) {
      action = res.getString(R.string.trigger_type_start_recording);
    } else if (actionType == TriggerActionType.TRIGGER_ACTION_STOP_RECORDING) {
      action = res.getString(R.string.trigger_type_stop_recording);
    } else if (actionType == TriggerActionType.TRIGGER_ACTION_NOTE) {
      action = res.getString(R.string.trigger_type_note);
    } else if (actionType == TriggerActionType.TRIGGER_ACTION_ALERT) {
      action = res.getString(R.string.trigger_type_alert);
    }
    SensorAppearance appearance =
        AppSingleton.getInstance(activity)
            .getSensorAppearanceProvider(appAccount)
            .getAppearance(trigger.getSensorId());
    String units = appearance.getUnits(activity);
    String value = appearance.getNumberFormat().format(trigger.getValueToTrigger());
    String result = "";
    TriggerWhen when = trigger.getTriggerWhen();
    if (when == TriggerWhen.TRIGGER_WHEN_AT) {
      result = res.getString(R.string.trigger_when_at_description, action, value, units);
    } else if (when == TriggerWhen.TRIGGER_WHEN_RISES_ABOVE) {
      result = res.getString(R.string.trigger_when_rises_above_description, action, value, units);
    } else if (when == TriggerWhen.TRIGGER_WHEN_DROPS_BELOW) {
      result = res.getString(R.string.trigger_when_drops_below_description, action, value, units);
    } else if (when == TriggerWhen.TRIGGER_WHEN_ABOVE) {
      result = res.getString(R.string.trigger_when_above_description, action, value, units);
    } else if (when == TriggerWhen.TRIGGER_WHEN_BELOW) {
      result = res.getString(R.string.trigger_when_below_description, action, value, units);
    }
    return result;
  }
}
