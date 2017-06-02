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
package com.google.android.apps.forscience.whistlepunk;

import android.app.IntentService;
import android.content.Intent;
import android.content.Context;

import com.google.android.apps.forscience.whistlepunk.review.RunReviewExporter;

import io.reactivex.Single;

/**
 * An {@link IntentService} subclass for handling asynchronous task requests in
 * a service on a separate handler thread for exporting various data sets in various
 * formats.
 */
public class ExportService extends IntentService {
    private static final String ACTION_EXPORT_TRIAL =
            "com.google.android.apps.forscience.whistlepunk.action.EXPORT_TRIAL";

    private static final String EXTRA_TRIAL_ID =
            "com.google.android.apps.forscience.whistlepunk.extra.TRIAL_ID";
    private static final String EXTRA_RELATIVE_TIME =
            "com.google.android.apps.forscience.whistlepunk.extra.RELATIVE_TIME";

    public ExportService() {
        super("ExportService");
    }

    /**
     * Starts this service to perform action export trial with the given parameters. If
     * the service is already performing a task this action will be queued.
     *
     * @see IntentService
     */
    public static void exportTrial(Context context, String trialId, boolean relativeTime) {
        Intent intent = new Intent(context, ExportService.class);
        intent.setAction(ACTION_EXPORT_TRIAL);
        intent.putExtra(EXTRA_TRIAL_ID, trialId);
        intent.putExtra(EXTRA_RELATIVE_TIME, relativeTime);
        context.startService(intent);
    }


    @Override
    protected void onHandleIntent(Intent intent) {
        if (intent != null) {
            final String action = intent.getAction();
            if (ACTION_EXPORT_TRIAL.equals(action)) {
                final String trialId = intent.getStringExtra(EXTRA_TRIAL_ID);
                final boolean relativeTime = intent.getBooleanExtra(EXTRA_RELATIVE_TIME, false);
                handleActionExportTrial(trialId, relativeTime);
            }
        }
    }

    /**
     * Handle action export trial in the provided background thread with the provided
     * parameters.
     */
    private void handleActionExportTrial(String trialId, boolean relativeTime) {
        throw new UnsupportedOperationException("Not implemented yet.");
    }

    private Single<DataController> getDataController() {
        return DataService.bind(this).map(data -> data.getDataController());
    }
}
