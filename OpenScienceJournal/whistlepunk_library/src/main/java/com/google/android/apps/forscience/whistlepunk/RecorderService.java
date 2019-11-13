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

import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.IBinder;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import com.google.android.apps.forscience.whistlepunk.accounts.AppAccount;
import com.google.android.apps.forscience.whistlepunk.review.RunReviewActivity;
import com.google.android.apps.forscience.whistlepunk.review.RunReviewDeprecatedActivity;

/**
 * Foreground service that keeps our application alive while recorders are recording.
 *
 * <p>For now, this service doesn't really hold any data, they are still in AppSingleton.
 */
public class RecorderService extends Service implements IRecorderService {
  @Nullable
  @Override
  public IBinder onBind(Intent intent) {
    return new Binder();
  }

  public class Binder extends android.os.Binder {
    public IRecorderService getService() {
      return RecorderService.this;
    }
  }

  @Override
  public void beginServiceRecording(String experimentName, Intent launchIntent) {
    clearRecordingCompletedNotification(getApplicationContext());
    final PendingIntent pi = PendingIntent.getActivity(this, 1, launchIntent, 0);
    startForeground(
        NotificationIds.RECORDER_SERVICE,
        new NotificationCompat.Builder(this, NotificationChannels.NOTIFICATION_CHANNEL)
            .setContentTitle(getString(R.string.service_notification_content_title))
            .setContentText(getString(R.string.service_notification_content_text))
            .setSubText(experimentName)
            .setSmallIcon(R.drawable.ic_notification_24dp)
            .setShowWhen(false)
            .setContentIntent(pi)
            .build());
    WhistlePunkApplication.getPerfTrackerProvider(getApplicationContext())
        .recordBatterySnapshotOnForegroundServiceStart();
  }

  /**
   * Stop the recording. Create a notification for the user if notifyRecordingEnded is true.
   *
   * @param notifyRecordingEnded Whether to show a notification to the user that the run is done.
   * @param runId If notifiyRecordingEnded is false, can be empty.
   * @param experimentTitle If notifyRecordingEnded is false, can be empty.
   */
  @Override
  public void endServiceRecording(
      AppAccount appAccount,
      boolean notifyRecordingEnded,
      String runId,
      String experimentId,
      String experimentTitle) {
    // Remove the recording notification before notifying that recording has stopped, so that
    // Science Journal only has one notification at a time.
    clearNotification(getApplicationContext(), NotificationIds.RECORDER_SERVICE);
    if (notifyRecordingEnded) {
      notifyRecordingEnded(appAccount, runId, experimentId, experimentTitle);
    }
    stopForeground(true);
    WhistlePunkApplication.getPerfTrackerProvider(getApplicationContext())
        .recordBatterySnapshotOnForegroundServiceStop();
    stopSelf();
  }

  private void notifyRecordingEnded(
      AppAccount appAccount, String runId, String experimentId, String experimentTitle) {
    Intent intent;
    if (Flags.showActionBar()) {
      intent =
          RunReviewActivity.createLaunchIntent(
              getApplicationContext(), appAccount, runId, experimentId, 0, false, false, false);
    } else {
      intent =
          RunReviewDeprecatedActivity.createLaunchIntent(
              getApplicationContext(), appAccount, runId, experimentId, 0, false, false, false);
    }
    intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);

    PendingIntent notificationIntent =
        PendingIntent.getActivity(
            getApplicationContext(),
            NotificationIds.RECORDING_COMPLETED,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT);

    ((NotificationManager) getApplicationContext().getSystemService(Context.NOTIFICATION_SERVICE))
        .notify(
            NotificationIds.RECORDING_COMPLETED,
            new NotificationCompat.Builder(
                    getApplicationContext(), NotificationChannels.NOTIFICATION_CHANNEL)
                .setContentTitle(
                    getApplicationContext().getString(R.string.service_notification_content_title))
                .setContentText(
                    getApplicationContext().getString(R.string.recording_stopped_notification_text))
                .setSubText(experimentTitle)
                .setSmallIcon(R.drawable.ic_notification_24dp)
                .setContentIntent(notificationIntent)
                .setAutoCancel(true)
                .build());
  }

  public static void clearRecordingCompletedNotification(Context context) {
    clearNotification(context, NotificationIds.RECORDING_COMPLETED);
  }

  private static void clearNotification(Context context, int notificationId) {
    ((NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE))
        .cancel(notificationId);
  }
}
