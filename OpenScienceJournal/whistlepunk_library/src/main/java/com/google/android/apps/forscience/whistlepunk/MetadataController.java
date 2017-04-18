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

import android.content.Context;
import android.text.TextUtils;
import android.util.ArrayMap;

import com.google.android.apps.forscience.javalib.Consumer;
import com.google.android.apps.forscience.javalib.FailureListener;
import com.google.android.apps.forscience.javalib.MaybeConsumer;
import com.google.android.apps.forscience.javalib.MaybeConsumers;
import com.google.android.apps.forscience.javalib.Success;
import com.google.android.apps.forscience.whistlepunk.metadata.Experiment;
import com.google.common.annotations.VisibleForTesting;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Handles which experiment is currently loaded and selected.
 */
public class MetadataController {
    private static final String TAG = "MetadataController";
    private final DataController mDataController;
    private FailureListenerFactory mListenerFactory;
    private Experiment mSelectedExperiment = null;
    private Map<String, MetadataChangeListener> mExperimentChangeListeners = new ArrayMap<>();

    public MetadataController(DataController dataController,
            FailureListenerFactory listenerFactory) {
        mDataController = dataController;
        mListenerFactory = listenerFactory;
    }

    public String getExperimentName(Context context) {
        if (mSelectedExperiment != null) {
            return mSelectedExperiment.getExperiment().getDisplayTitle(context);
        } else {
            return "";
        }
    }

    public void changeSelectedExperiment(Experiment experiment) {
        if (!TextUtils.equals(Experiment.getExperimentId(experiment),
                Experiment.getExperimentId(mSelectedExperiment))) {
            internalSetSelectedExperiment(experiment);
            // Re-order experiments
            loadExperiments();
        }
    }

    /**
     * Update the internally stored selected experiment, and commit it to the database.  This does
     * _not_ call out to the listener, for that, use changeSelectedExperiment
     */
    private void internalSetSelectedExperiment(Experiment experiment) {
        mSelectedExperiment = experiment;
        updateLastExperimentPreferences(mSelectedExperiment);
    }

    /**
     * This adds a listener which is called when the experiment or project are updated. In doing
     * so, it clears the selected experiment and then asks the database for the most up-to-date
     * selected experiment and project.
     *
     * @param listenerKey if a previous listener has been added with the same key, it will be
     *                    overridden by this new value.  This must also be the key passed to
     *                    {@link #removeExperimentChangeListener(String)}.
     */
    public void addExperimentChangeListener(String listenerKey, MetadataChangeListener listener) {
        mExperimentChangeListeners.put(listenerKey, listener);
        mSelectedExperiment = null;
        loadExperiments();
    }

    private void loadExperiments() {
        getDataController().getExperiments(false /* no archived */,
                doOrReportFailure("get last used experiment", new Consumer<List<Experiment>>() {
                    @Override
                    public void take(final List<Experiment> experiments) {
                        if (experiments.size() == 0) {
                            createExperiment();
                        } else {
                            setMetadata(experiments);
                        }
                    }
                }));
    }

    private void setMetadata(List<Experiment> experiments) {
        // We retrieve the experiments in last used order.
        internalSetSelectedExperiment(experiments.get(0));
        ArrayList<Experiment> experimentsCopy = new ArrayList<>(experiments);
        for (MetadataChangeListener listener : mExperimentChangeListeners.values()) {
            listener.onMetadataChanged(experimentsCopy);
        }
    }

    private void createExperiment() {
        getDataController().createExperiment(doOrReportFailure(
                "Creating default experiment", new Consumer<Experiment>() {
                    @Override
                    public void take(Experiment value) {
                        internalSetSelectedExperiment(value);
                        List<Experiment> experiments = new ArrayList<Experiment>();
                        experiments.add(value);
                        setMetadata(experiments);
                    }
                }));
    }

    private void updateLastExperimentPreferences(final Experiment experiment) {
        getDataController().updateLastUsedExperiment(experiment,
                LoggingConsumer.<Success>expectSuccess(TAG,
                        "Update last used experiment preference"));
    }

    DataController getDataController() {
        return mDataController;
    }

    /**
     * @param listenerKey must be the same value used in
     * {@link #addExperimentChangeListener(String, MetadataChangeListener)}
     */
    public void removeExperimentChangeListener(String listenerKey) {
        mExperimentChangeListeners.remove(listenerKey);
    }

    /**
     * Returns a MaybeConsumer that either passes a successful value to {@onSuccess}, or reports
     * a failre to {@code mListenerFactory}, tagged with the operation string {@code operation}.
     */
    private <T> MaybeConsumer<T> doOrReportFailure(String operation, Consumer<T> onSuccess) {
        return MaybeConsumers.chainFailure(mListenerFactory.makeListenerForOperation(operation),
                onSuccess);
    }

    interface MetadataChangeListener {
        /**
         * @param newExperiments The experiments for this project.  The selected experiment is
         *                       the first in the list, which is guaranteed to be non-empty.
         */
        public void onMetadataChanged(List<Experiment> newExperiments);
    }

    /**
     * Most users don't need to know about this class, but by supplying a new value during testing,
     * we can trigger desired behaviors when operations fail.
     */
    @VisibleForTesting
    public interface FailureListenerFactory {
        FailureListener makeListenerForOperation(String operation);
    }
}