package com.google.android.apps.forscience.whistlepunk;

import android.content.Context;
import android.text.TextUtils;

import com.google.android.apps.forscience.javalib.Consumer;
import com.google.android.apps.forscience.javalib.FailureListener;
import com.google.android.apps.forscience.javalib.MaybeConsumer;
import com.google.android.apps.forscience.javalib.MaybeConsumers;
import com.google.android.apps.forscience.javalib.Success;
import com.google.android.apps.forscience.whistlepunk.metadata.Experiment;
import com.google.android.apps.forscience.whistlepunk.metadata.Project;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;

import java.util.ArrayList;
import java.util.List;

/**
 * Handles which project and experiment are currently loaded and selected.
 */
public class MetadataController {
    private static final String TAG = "MetadataController";
    private final DataController mDataController;
    private FailureListenerFactory mListenerFactory;
    private Experiment mSelectedExperiment = null;
    private Project mSelectedProject = null;
    private MetadataChangeListener mExperimentChangeListener;

    public MetadataController(DataController dataController,
            FailureListenerFactory listenerFactory) {
        mDataController = dataController;
        mListenerFactory = listenerFactory;
    }

    public String getExperimentName(Context context) {
        if (mSelectedExperiment != null) {
            return mSelectedExperiment.getDisplayTitle(context);
        } else {
            return "";
        }
    }

    public void changeSelectedExperiment(Experiment experiment) {
        if (!TextUtils.equals(Experiment.getExperimentId(experiment),
                Experiment.getExperimentId(mSelectedExperiment))) {
            internalSetSelectedExperiment(experiment);
            if (mSelectedProject != null) {
                // Re-order experiments
                loadExperiments(mSelectedProject);
            }
        }
    }

    /**
     * Update the internally stored selected experiment, and commit it to the database.  This does
     * _not_ call out to the listener, for that, use changeSelectedExperiment
     */
    void internalSetSelectedExperiment(Experiment experiment) {
        mSelectedExperiment = experiment;
        updateLastExperimentPreferences(mSelectedExperiment);
    }

    /**
     * This sets the listener which is called when the experiment or project are updated. In doing
     * so, it clears the selected experiment and then asks the database for the most up-to-date
     * selected experiment and project.
     */
    public void setExperimentChangeListener(MetadataChangeListener listener) {
        mExperimentChangeListener = listener;
        mSelectedExperiment = null;
        getDataController().getLastUsedProject(
                doOrReportFailure("Loading last used project", new Consumer<Project>() {
                    @Override
                    public void take(final Project project) {
                        if (project == null) {
                            createDefaultProjectAndExperiment();
                        } else {
                            // Now get the experiments.
                            loadExperiments(project);
                        }
                    }
                }));
    }

    void loadExperiments(final Project project) {
        Preconditions.checkNotNull(project);
        getDataController().getExperimentsForProject(project,
                false /* no archived */,
                doOrReportFailure("get last used experiment", new Consumer<List<Experiment>>() {
                    @Override
                    public void take(final List<Experiment> experiments) {
                        if (experiments.size() == 0) {
                            createExperimentInProject(project);
                        } else {
                            setMetadata(experiments, project);
                        }
                    }
                }));
    }

    void setMetadata(List<Experiment> experiments, Project project) {
        mSelectedProject = project;
        // We retrieve the experiments in last used order.
        internalSetSelectedExperiment(experiments.get(0));
        if (mExperimentChangeListener != null) {
            mExperimentChangeListener.onMetadataChanged(
                    project, new ArrayList<Experiment>(experiments));
        }
    }

    void createDefaultProjectAndExperiment() {
        getDataController().createProject(
                doOrReportFailure("Creating default project", new Consumer<Project>() {
                    @Override
                    public void take(Project project) {
                        createExperimentInProject(project);
                    }
                }));
    }

    void createExperimentInProject(final Project project) {
        getDataController().createExperiment(project,
                doOrReportFailure("Creating default experiment", new Consumer<Experiment>() {
                    @Override
                    public void take(Experiment value) {
                        internalSetSelectedExperiment(value);
                        List<Experiment> experiments = new ArrayList<Experiment>();
                        experiments.add(value);
                        setMetadata(experiments, project);
                    }
                }));
    }

    void updateLastExperimentPreferences(final Experiment experiment) {
        getDataController().updateLastUsedExperiment(experiment,
                LoggingConsumer.<Success>expectSuccess(TAG,
                        "Update last used experiment preference"));
    }

    DataController getDataController() {
        return mDataController;
    }

    public void clearExperimentChangeListener() {
        mExperimentChangeListener = null;
    }

    /**
     * Returns a MaybeConsumer that either passes a successful value to {@onSuccess}, or reports
     * a failre to {@code mListenerFactory}, tagged with the operation string {@code operation}.
     */
    <T> MaybeConsumer<T> doOrReportFailure(String operation, Consumer<T> onSuccess) {
        return MaybeConsumers.chainFailure(mListenerFactory.makeListenerForOperation(operation),
                onSuccess);
    }

    interface MetadataChangeListener {
        /**
         * @param newProject     The new selected project
         * @param newExperiments The experiments for this project.  The selected experiment is
         *                       the first in the list, which is guaranteed to be non-empty.
         */
        public void onMetadataChanged(Project newProject, List<Experiment> newExperiments);
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