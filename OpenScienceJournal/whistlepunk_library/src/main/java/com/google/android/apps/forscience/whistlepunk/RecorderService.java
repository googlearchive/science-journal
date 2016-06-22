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

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.support.annotation.Nullable;

/**
 * Foreground service that keeps our application alive while recorders are recording.
 *
 * For now, this service doesn't really hold any data, they are still in AppSingleton.
 */
public class RecorderService extends Service {
    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return new Binder();
    }

    public class Binder extends android.os.Binder {
        public RecorderService getService() {
            return RecorderService.this;
        }
    }

    public void beginServiceRecording(String experimentName, Intent launchIntent) {
        final PendingIntent pi = PendingIntent.getActivity(this, 1, launchIntent, 0);
        startForeground(NotificationIds.RECORDER_SERVICE,
                new Notification.Builder(this)
                        .setContentTitle(getString(R.string.service_notification_content_title))
                        .setContentText(getString(R.string.service_notification_content_text))
                        .setSubText(experimentName)
                        .setSmallIcon(R.drawable.ic_notification_24dp)
                        .setContentIntent(pi)
                        .build());
    }

    public void endServiceRecording() {
        stopForeground(true);
        stopSelf();
    }
}
