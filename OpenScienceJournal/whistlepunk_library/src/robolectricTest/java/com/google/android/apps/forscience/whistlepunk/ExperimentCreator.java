/*
 *  Copyright 2018 Google Inc. All Rights Reserved.
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

import android.content.Context;
import com.google.android.apps.forscience.whistlepunk.accounts.AppAccount;
import com.google.android.apps.forscience.whistlepunk.accounts.NonSignedInAccount;
import com.google.android.apps.forscience.whistlepunk.data.GoosciExperimentLibrary.ExperimentLibrary;
import com.google.android.apps.forscience.whistlepunk.data.GoosciLocalSyncStatus.LocalSyncStatus;
import com.google.android.apps.forscience.whistlepunk.filemetadata.Experiment;
import com.google.android.apps.forscience.whistlepunk.filemetadata.ExperimentLibraryManager;
import com.google.android.apps.forscience.whistlepunk.filemetadata.ExperimentOverviewPojo;
import com.google.android.apps.forscience.whistlepunk.filemetadata.LocalSyncManager;
import com.google.android.apps.forscience.whistlepunk.metadata.GoosciExperiment;
import com.google.android.apps.forscience.whistlepunk.metadata.GoosciUserMetadata;
import org.robolectric.RuntimeEnvironment;

/**
 * Utility class for creating experiments in tests, and registering those experiments with the
 * LocalSyncManager and ExperimentLibraryManager.
 */
public class ExperimentCreator {

  /** Gets a new experiment that can be used for testing. */
  public static Experiment newExperimentForTesting(
      Context context, long creationTime, String experimentId, int colorIndex) {
    Experiment experiment = Experiment.newExperiment(creationTime, experimentId, colorIndex);
    addToManagers(context, experiment);
    return experiment;
  }

  /** Gets a new experiment that can be used for testing. */
  public static Experiment newExperimentForTesting(
      long creationTime, String experimentId, int colorIndex) {
    return newExperimentForTesting(
        RuntimeEnvironment.application.getApplicationContext(),
        creationTime,
        experimentId,
        colorIndex);
  }

  /** Gets a new experiment that can be used for testing. */
  public static Experiment newExperimentForTesting(
      Context context,
      GoosciExperiment.Experiment proto,
      GoosciUserMetadata.ExperimentOverview overview) {
    Experiment experiment =
        Experiment.fromExperiment(proto, ExperimentOverviewPojo.fromProto(overview));
    addToManagers(context, experiment);
    return experiment;
  }

  private static void addToManagers(Context context, Experiment experiment) {
    AppAccount appAccount = NonSignedInAccount.getInstance(context);
    ExperimentLibraryManager elm =
        AppSingleton.getInstance(context).getExperimentLibraryManager(appAccount);
    elm.setLibrary(ExperimentLibrary.getDefaultInstance());
    elm.addExperiment(experiment.getExperimentId());
    LocalSyncManager lsm = AppSingleton.getInstance(context).getLocalSyncManager(appAccount);
    lsm.setLocalSyncStatus(LocalSyncStatus.getDefaultInstance());
    lsm.addExperiment(experiment.getExperimentId());
  }
}
