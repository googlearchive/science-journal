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

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.sqlite.SQLiteStatement;
import android.provider.BaseColumns;
import android.support.annotation.NonNull;
import android.support.v4.util.ArraySet;
import android.text.TextUtils;
import android.util.Log;

import com.google.android.apps.forscience.whistlepunk.Clock;
import com.google.android.apps.forscience.whistlepunk.CurrentTimeClock;
import com.google.android.apps.forscience.whistlepunk.ExternalSensorProvider;
import com.google.android.apps.forscience.whistlepunk.ProtoUtils;
import com.google.android.apps.forscience.whistlepunk.R;
import com.google.android.apps.forscience.whistlepunk.RecorderController;
import com.google.android.apps.forscience.whistlepunk.StatsAccumulator;
import com.google.android.apps.forscience.whistlepunk.api.scalarinput.InputDeviceSpec;
import com.google.android.apps.forscience.whistlepunk.data.GoosciSensorLayout;
import com.google.android.apps.forscience.whistlepunk.devicemanager.ConnectableSensor;
import com.google.android.apps.forscience.whistlepunk.filemetadata.Experiment;
import com.google.android.apps.forscience.whistlepunk.filemetadata.FileMetadataManager;
import com.google.android.apps.forscience.whistlepunk.filemetadata.Label;
import com.google.android.apps.forscience.whistlepunk.filemetadata.PictureLabelValue;
import com.google.android.apps.forscience.whistlepunk.filemetadata.SensorTrigger;
import com.google.android.apps.forscience.whistlepunk.filemetadata.SensorTriggerLabelValue;
import com.google.android.apps.forscience.whistlepunk.filemetadata.TextLabelValue;
import com.google.android.apps.forscience.whistlepunk.filemetadata.Trial;
import com.google.android.apps.forscience.whistlepunk.filemetadata.TrialStats;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Lists;
import com.google.protobuf.nano.InvalidProtocolBufferNanoException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * An implementation of the {@link MetaDataManager} which uses a simple database.
 */
public class SimpleMetaDataManager implements MetaDataManager {

    private static final int STABLE_EXPERIMENT_ID_LENGTH = 12;
    private static final int STABLE_PROJECT_ID_LENGTH = 6;
    private static final String TAG = "SimpleMetaDataManager";
    private static final String TEXT_LABEL_TAG = "text";
    private static final String PICTURE_LABEL_TAG = "picture";
    private static final String SENSOR_TRIGGER_LABEL_TAG = "sensorTriggerLabel";
    private static final String UNKNOWN_LABEL_TAG = "label";
    public static final String DEFAULT_PROJECT_ID = "defaultProjectId";

    private DatabaseHelper mDbHelper;
    private Context mContext;
    private Clock mClock;
    private Object mLock = new Object();
    private FileMetadataManager mFileMetadataManager;

    public void close() {
        mDbHelper.close();
        getFileMetadataManager().close();
    }

    /**
     * List of table names. NOTE: when adding a new table, make sure to delete the metadata in the
     * appropriate delete calls:
     * {@link #deleteExperiment(Experiment)}, {@link #deleteDatabaseLabel(SQLiteDatabase, Label)},
     * {@link #deleteDatabaseTrial(String)}, {@link #deleteDatabaseSensorTrigger(SQLiteDatabase,
     * SensorTrigger)}, etc.
     */
    interface Tables {
        String PROJECTS = "projects";
        String EXPERIMENTS = "experiments";
        String LABELS = "labels";
        String EXTERNAL_SENSORS = "sensors";
        String EXPERIMENT_SENSORS = "experiment_sensors";
        String RUN_STATS = "run_stats";
        String RUNS = "runs";
        String RUN_SENSORS = "run_sensors";
        String EXPERIMENT_SENSOR_LAYOUT = "experiment_sensor_layout";
        String SENSOR_TRIGGERS = "sensor_triggers";
        String MY_DEVICES = "my_devices";
    }

    public SimpleMetaDataManager(Context context) {
        this(context, null /* default filename */, new CurrentTimeClock());
    }

    @VisibleForTesting
    SimpleMetaDataManager(Context context, String filename, Clock clock) {
        mContext = context;
        mClock = clock;
        mFileMetadataManager = new FileMetadataManager(mContext, mClock);
        mDbHelper = new DatabaseHelper(context, filename,
                new DatabaseHelper.MetadataDatabaseUpgradeCallback() {
                    @Override
                    public void onMigrateProjectData(SQLiteDatabase db) {
                        migrateProjectData(db);
                    }

                    @Override
                    public void onMigrateExperimentsToFiles(SQLiteDatabase db) {
                        migrateExperimentsToFiles(db);
                    }
                });
    }

    private FileMetadataManager getFileMetadataManager() {
        synchronized (mLock) {
            // Force upgrade if needed
            final SQLiteDatabase db = mDbHelper.getWritableDatabase();
            return mFileMetadataManager;
        }
    }

    private void migrateExperimentsToFiles(SQLiteDatabase db) {
        // For each experiment, migrate it over and delete it from the database.
        List<String> experimentIds = getAllExperimentIds(db);

        // Clean up if a previous migration was not successful / complete.
        mFileMetadataManager.deleteAll(experimentIds);

        for (String experimentId : experimentIds) {
            Experiment experiment = getDatabaseExperimentById(db, experimentId, mContext, true);
            mFileMetadataManager.addExperimentImmediately(experiment);
            deleteDatabaseExperiment(db, experiment, mContext, /* don't delete assets */ false);
        }
        // TODO: Migrate assets!
    }

    private List<String> getAllExperimentIds(SQLiteDatabase db) {
        List<String> experimentIds = new ArrayList<>();
        Cursor cursor = null;
        try {
            cursor = db.query(Tables.EXPERIMENTS, new String[] {ExperimentColumns.EXPERIMENT_ID},
                    null, null, null, null, null);
            while (cursor.moveToNext()) {
                experimentIds.add(cursor.getString(0));
            }
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        return experimentIds;
    }

    @VisibleForTesting
    void migrateProjectData() {
        synchronized (mLock) {
            final SQLiteDatabase db = mDbHelper.getWritableDatabase();
            migrateProjectData(db);
        }
    }

    private void migrateProjectData(SQLiteDatabase db) {
        // Get every project and migrate its data to its experiments.
        List<Project> projects = getDatabaseProjects(db, true);
        for (Project project : projects) {
            List<Experiment> experiments = getAllDatabaseExperimentsForProject(db, project);
            for (Experiment experiment : experiments) {
                if (!TextUtils.isEmpty(project.getDescription())) {
                    // Create a label with the description at the start of the experiment.
                    // Because projects do not track their creation time, use the experiment
                    // creation time instead.
                    addDatabaseLabel(db, experiment.getExperimentId(),
                            RecorderController.NOT_RECORDING_RUN_ID,
                            Label.newLabelWithValue(
                                    experiment.getCreationTimeMs() - 2000,
                                    TextLabelValue.fromText(project.getDescription())));
                }
                if (!TextUtils.isEmpty(project.getCoverPhoto())) {
                    // Create a label with the picture at the start of the experiment.
                    addDatabaseLabel(db, experiment.getExperimentId(),
                            RecorderController.NOT_RECORDING_RUN_ID,
                            Label.newLabelWithValue(
                                    experiment.getCreationTimeMs() - 1000,
                                    PictureLabelValue.fromPicture(project.getCoverPhoto(), "")));
                }
                boolean needsWrite = false;
                if (project.isArchived()) {
                    // If the project is archived, the experiment should be archived.
                    experiment.setArchived(true);
                    needsWrite = true;
                }
                if (!TextUtils.isEmpty(project.getTitle())) {
                    // Experiment title prefixed with Project title, unless project title is not set
                    experiment.setTitle(String.format(mContext.getResources()
                                    .getString(R.string.project_experiment_title),
                            project.getTitle(), experiment.getDisplayTitle(
                                    mContext)));
                    needsWrite = true;
                }
                if (needsWrite) {
                    updateDatabaseExperiment(db, experiment);
                }
            }
            deleteProjectFromDb(db, project);
        }
    }

    /**
     * This function is only used as part of the database upgrade which deletes projects, so these
     * experiments returned do not contain their trials, labels, triggers, sensors, etc.
     */
    private static List<Experiment> getAllDatabaseExperimentsForProject(SQLiteDatabase db,
            Project project) {
        List<Experiment> experiments = new ArrayList<>();
        String selection = ExperimentColumns.PROJECT_ID + "=?";
        String[] selectionArgs = new String[]{project.getProjectId()};
        Cursor cursor = null;
        try {
            cursor = db.query(Tables.EXPERIMENTS, ExperimentColumns.GET_COLUMNS, selection,
                    selectionArgs, null, null,
                    ExperimentColumns.LAST_USED_TIME + " DESC, " + BaseColumns._ID + " DESC");
            while (cursor.moveToNext()) {
                experiments.add(createExperimentFromCursor(cursor));
            }
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        return experiments;
    }

    // Deletes a project object without touching its experiments.
    private static void deleteProjectFromDb(SQLiteDatabase db, Project project) {
        db.delete(Tables.PROJECTS, ProjectColumns.PROJECT_ID + "=?",
                new String[]{project.getProjectId()});
    }

    private static Project createProjectFromCursor(Cursor cursor) {
        Project project = new Project(cursor.getLong(0));
        project.setProjectId(cursor.getString(1));
        project.setTitle(cursor.getString(2));
        project.setCoverPhoto(cursor.getString(3));
        project.setArchived(cursor.getInt(4) == 1);
        project.setDescription(cursor.getString(5));
        project.setLastUsedTime(cursor.getLong(6));
        return project;
    }

    @VisibleForTesting
    @Deprecated
    List<Project> getDatabaseProjects(boolean includeArchived) {
        synchronized (mLock) {
            final SQLiteDatabase db = mDbHelper.getWritableDatabase();
            return getDatabaseProjects(db, includeArchived);
        }
    }

    private static List<Project> getDatabaseProjects(SQLiteDatabase db, boolean includeArchived) {
        List<Project> projects = new ArrayList<>();
        String selection = ProjectColumns.ARCHIVED + "=?";
        String[] selectionArgs = new String[]{"0"};
        if (includeArchived) {
            selection = null;
            selectionArgs = null;
        }

        Cursor cursor = null;
        try {
            cursor = db.query(
                    Tables.PROJECTS, ProjectColumns.GET_COLUMNS, selection, selectionArgs,
                    null, null,
                    ProjectColumns.LAST_USED_TIME + " DESC, " + BaseColumns._ID + " DESC");
            while (cursor.moveToNext()) {
                projects.add(createProjectFromCursor(cursor));
            }
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        return projects;
    }

    @VisibleForTesting
    @Deprecated
    Project newProject() {
        String projectId = newStableId(STABLE_PROJECT_ID_LENGTH);
        ContentValues values = new ContentValues();
        values.put(ProjectColumns.PROJECT_ID, projectId);
        synchronized (mLock) {
            final SQLiteDatabase db = mDbHelper.getWritableDatabase();
            long id = db.insert(Tables.PROJECTS, null, values);
            if (id != -1) {
                Project project = new Project(id);
                project.setProjectId(projectId);
                return project;
            }
        }
        return null;
    }

    @VisibleForTesting
    @Deprecated
    void updateProject(Project project) {
        synchronized (mLock) {
            final SQLiteDatabase db = mDbHelper.getWritableDatabase();
            final ContentValues values = new ContentValues();
            values.put(ProjectColumns.TITLE, project.getTitle());
            values.put(ProjectColumns.DESCRIPTION, project.getDescription());
            values.put(ProjectColumns.COVER_PHOTO, project.getCoverPhoto());
            values.put(ProjectColumns.ARCHIVED, project.isArchived());
            values.put(ProjectColumns.LAST_USED_TIME, project.getLastUsedTime());
            db.update(Tables.PROJECTS, values, ProjectColumns.PROJECT_ID + "=?",
                    new String[]{project.getProjectId()});
        }
    }

    @Override
    public Experiment getExperimentById(String experimentId) {
        return getFileMetadataManager().getExperimentById(experimentId);
    }

    @VisibleForTesting
    Experiment getDatabaseExperimentById(String experimentId) {
        Experiment experiment = null;
        synchronized (mLock) {
            final SQLiteDatabase db = mDbHelper.getReadableDatabase();
            experiment = getDatabaseExperimentById(db, experimentId, mContext, false);
        }
        return experiment;
    }

    // Gets the experiment from the database.
    private static Experiment getDatabaseExperimentById(SQLiteDatabase db, String experimentId,
            Context context, boolean includeTrials) {
        Experiment experiment;
        final String selection = ExperimentColumns.EXPERIMENT_ID + "=?";
        final String[] selectionArgs = new String[]{experimentId};
        Cursor cursor = null;
        try {
            cursor = db.query(
                    Tables.EXPERIMENTS, ExperimentColumns.GET_COLUMNS, selection, selectionArgs,
                    null, null, null, "1");
            if (cursor == null || !cursor.moveToFirst()) {
                return null;
            }
            experiment = createExperimentFromCursor(cursor);
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        populateDatabaseExperiment(db, experiment, context, includeTrials);
        return experiment;
    }

    @VisibleForTesting
    Experiment newDatabaseExperiment(String projectId) {
        String experimentId = newStableId(STABLE_EXPERIMENT_ID_LENGTH);
        long timestamp = getCurrentTime();
        Experiment result = Experiment.newExperiment(timestamp, experimentId);
        ContentValues values = new ContentValues();
        values.put(ExperimentColumns.EXPERIMENT_ID, experimentId);
        values.put(ExperimentColumns.PROJECT_ID, projectId);
        values.put(ExperimentColumns.TIMESTAMP, timestamp);
        synchronized (mLock) {
            final SQLiteDatabase db = mDbHelper.getWritableDatabase();
            long id = db.insert(Tables.EXPERIMENTS, null, values);
            if (id != -1) {
                return result;
            }
        }
        return null;
    }

    @Override
    public Experiment newExperiment() {
        return getFileMetadataManager().newExperiment();
    }

    @VisibleForTesting
    Experiment newDatabaseExperiment() {
        return newDatabaseExperiment(DEFAULT_PROJECT_ID);
    }

    @Override
    public void deleteExperiment(Experiment experiment) {
        getFileMetadataManager().deleteExperiment(experiment);
    }

    @VisibleForTesting
    void deleteDatabaseExperiment(Experiment experiment) {
        synchronized (mLock) {
            final SQLiteDatabase db = mDbHelper.getWritableDatabase();
            deleteDatabaseExperiment(db, experiment, mContext, /* delete assets */ true);
        }
    }

    private static void deleteDatabaseExperiment(SQLiteDatabase db, Experiment experiment,
            Context context, boolean deleteAssets) {
        List<String> runIds = getDatabaseExperimentRunIds(db, experiment.getExperimentId(),
                /* include archived runs */ true);
        for (String runId : runIds) {
            deleteDatabaseTrial(db, runId, context, deleteAssets);
        }
        deleteDatabaseObjectsInExperiment(db, experiment, deleteAssets, context);
        String[] experimentArgs = new String[]{experiment.getExperimentId()};
        db.delete(Tables.EXPERIMENTS, ExperimentColumns.EXPERIMENT_ID + "=?", experimentArgs);
        db.delete(Tables.EXPERIMENT_SENSORS, ExperimentSensorColumns.EXPERIMENT_ID + "=?",
                experimentArgs);
        db.delete(Tables.EXPERIMENT_SENSOR_LAYOUT, ExperimentSensorLayoutColumns.EXPERIMENT_ID
                + "=?", experimentArgs);
    }

    private long getCurrentTime() {
        return mClock.getNow();
    }

    @Override
    public void updateExperiment(Experiment experiment) {
        getFileMetadataManager().updateExperiment(experiment);
    }

    @VisibleForTesting
    void updateDatabaseExperiment(Experiment experiment) {
        // Delete and re-add all the objects, as if this was a file we were re-writing from scratch.
        // This is not super efficient, but it is temporary in the file system migration process.
        synchronized (mLock) {
            final SQLiteDatabase db = mDbHelper.getWritableDatabase();
            deleteDatabaseObjectsInExperiment(db, experiment, /* don't delete assets */ false,
                    mContext);
            updateDatabaseExperiment(db, experiment);
            for (Label label : experiment.getLabels()) {
                // how does this do on conflict? poorly.
                addDatabaseLabel(experiment.getExperimentId(),
                        RecorderController.NOT_RECORDING_RUN_ID, label);
            }
            for (SensorTrigger trigger : experiment.getSensorTriggers()) {
                addSensorTrigger(trigger, experiment.getExperimentId());
            }
            setExperimentSensorLayouts(experiment.getExperimentId(),
                    experiment.getSensorLayouts());
        }
    }

    private static void updateDatabaseExperiment(SQLiteDatabase db, Experiment experiment) {
        final ContentValues values = new ContentValues();
        values.put(ExperimentColumns.TITLE, experiment.getTitle());
        values.put(ExperimentColumns.DESCRIPTION, experiment.getDescription());
        values.put(ExperimentColumns.ARCHIVED, experiment.isArchived());
        values.put(ExperimentColumns.LAST_USED_TIME, experiment.getLastUsedTime());
        db.update(Tables.EXPERIMENTS, values, ExperimentColumns.EXPERIMENT_ID + "=?",
                new String[]{experiment.getExperimentId()});
    }

    @Override
    public List<GoosciUserMetadata.ExperimentOverview> getExperimentOverviews(
            boolean includeArchived) {
        return getFileMetadataManager().getExperimentOverviews(includeArchived);
    }

    @VisibleForTesting
    List<GoosciUserMetadata.ExperimentOverview> getDatabaseExperimentOverviews(
            boolean includeArchived) {
        List<GoosciUserMetadata.ExperimentOverview> experiments = new ArrayList<>();
        synchronized (mLock) {
            final SQLiteDatabase db = mDbHelper.getReadableDatabase();

            String selection = "";
            if (!includeArchived) {
                selection = ExperimentColumns.ARCHIVED + "=0";
            }
            Cursor cursor = null;
            try {
                cursor = db.query(Tables.EXPERIMENTS, ExperimentColumns.GET_COLUMNS, selection,
                        null, null, null,
                        ExperimentColumns.LAST_USED_TIME + " DESC, " + BaseColumns._ID + " DESC");
                while (cursor.moveToNext()) {
                    experiments.add(createExperimentOverviewFromCursor(cursor));
                }
            } finally {
                if (cursor != null) {
                    cursor.close();
                }
            }
        }
        return experiments;
    }

    private static Experiment createExperimentFromCursor(Cursor cursor) {
        GoosciExperiment.Experiment expProto = new GoosciExperiment.Experiment();
        expProto.creationTimeMs = cursor.getLong(2);
        expProto.description = cursor.getString(4);
        expProto.title = cursor.getString(3);

        if (expProto.description == null) {
            expProto.description = "";
        }

        // Version 1 for starters.
        // TODO: Remove this if we default the proto to 1.
        expProto.version = 1;

        return Experiment.fromExperiment(expProto, createExperimentOverviewFromCursor(cursor));
    }

    private static GoosciUserMetadata.ExperimentOverview createExperimentOverviewFromCursor(
            Cursor cursor) {
        GoosciUserMetadata.ExperimentOverview overviewProto =
                new GoosciUserMetadata.ExperimentOverview();
        overviewProto.lastUsedTimeMs = cursor.getLong(7);
        overviewProto.title = cursor.getString(3);
        overviewProto.isArchived = cursor.getInt(6) != 0;
        overviewProto.experimentId = cursor.getString(1);

        if (overviewProto.title == null) {
            overviewProto.title = "";
        }
        return overviewProto;
    }

    @Override
    public Experiment getLastUsedUnarchivedExperiment() {
        return getFileMetadataManager().getLastUsedUnarchivedExperiment();
    }

    @VisibleForTesting
    Experiment getLastUsedUnarchivedDatabaseExperiment() {
        Experiment experiment = null;
        synchronized (mLock) {
            final SQLiteDatabase db = mDbHelper.getReadableDatabase();
            String selection = ExperimentColumns.ARCHIVED + "=0";
            Cursor cursor = null;
            try {
                cursor = db.query(Tables.EXPERIMENTS, ExperimentColumns.GET_COLUMNS, selection,
                        null, null, null,
                        ExperimentColumns.LAST_USED_TIME + " DESC, " + BaseColumns._ID + " DESC",
                        "1");
                if (cursor.moveToNext()) {
                    experiment = createExperimentFromCursor(cursor);
                }
            } finally {
                if (cursor != null) {
                    cursor.close();
                }
            }
            populateDatabaseExperiment(db, experiment, mContext, false);
        }
        return experiment;
    }

    private static void deleteDatabaseObjectsInExperiment(SQLiteDatabase db, Experiment experiment,
            boolean deleteAssets, Context context) {
        List<Label> labels = getDatabaseLabelsForExperiment(db, experiment);
        for (Label label : labels) {
            if (deleteAssets) {
                label.deleteAssets(context);
            }
            deleteDatabaseLabel(db, label);
        }
        List<SensorTrigger> triggers = getDatabaseSensorTriggers(db, experiment.getExperimentId());
        for (SensorTrigger trigger : triggers) {
            deleteDatabaseSensorTrigger(db, trigger);
        }
    }

    private static void populateDatabaseExperiment(SQLiteDatabase db, Experiment experiment,
            Context context, boolean includeTrials) {
        if (experiment == null) {
            return;
        }
        List<Label> labels = getDatabaseLabelsForExperiment(db, experiment);
        experiment.populateLabels(labels);
        List<SensorTrigger> triggers = getDatabaseSensorTriggers(db, experiment.getExperimentId());
        experiment.setSensorTriggers(triggers);
        experiment.setSensorLayouts(getDatabaseExperimentSensorLayouts(db,
                experiment.getExperimentId()));
        if (includeTrials) {
            List<String> trialIds = getDatabaseExperimentRunIds(db, experiment.getExperimentId(),
                    true);
            for (String trialId : trialIds) {
                List<ApplicationLabel> applicationLabels =
                        getDatabaseApplicationLabelsWithStartId(db, trialId);
                experiment.addTrial(getDatabaseTrial(db, trialId, applicationLabels, context));
            }
        }
    }

    @Override
    public void setLastUsedExperiment(Experiment experiment) {
        getFileMetadataManager().setLastUsedExperiment(experiment);
    }

    void setLastUsedDatabaseExperiment(Experiment experiment) {
        long time = getCurrentTime();
        experiment.setLastUsedTime(time);
        synchronized (mLock) {
            final SQLiteDatabase db = mDbHelper.getWritableDatabase();
            final ContentValues values = new ContentValues();
            values.put(ExperimentColumns.LAST_USED_TIME, time);
            db.update(Tables.EXPERIMENTS, values, ExperimentColumns.EXPERIMENT_ID + "=?",
                    new String[]{experiment.getExperimentId()});
        }
    }

    @VisibleForTesting
    Trial newTrial(Experiment experiment, String trialId, long startTimestamp,
            List<GoosciSensorLayout.SensorLayout> sensorLayouts) {
        // How many runs already exist?
        List<String> runIds = getDatabaseExperimentRunIds(experiment.getExperimentId(),
                /* include archived runs for indexing */ true);
        int runIndex = runIds.size();
        synchronized (mLock) {
            insertTrial(trialId, runIndex);
            insertRunSensors(trialId, sensorLayouts);
        }

        // Can't use Trial.newTrial because runId must be the start label ID for this trial.
        // That method can be used after DB upgrade, however.
        GoosciTrial.Trial trialProto = new GoosciTrial.Trial();
        trialProto.creationTimeMs = mClock.getNow();
        trialProto.sensorLayouts = sensorLayouts.toArray(
                new GoosciSensorLayout.SensorLayout[sensorLayouts.size()]);
        trialProto.trialId = trialId;
        trialProto.archived = false;
        trialProto.autoZoomEnabled = true;
        trialProto.recordingRange = new GoosciTrial.Range();
        trialProto.recordingRange.startMs = startTimestamp;
        return Trial.fromTrial(trialProto);
    }

    private void insertTrial(String trialId, int runIndex) {
        synchronized (mLock) {
            final SQLiteDatabase db = mDbHelper.getWritableDatabase();
            final ContentValues values = new ContentValues();
            values.put(RunsColumns.RUN_ID, trialId);
            values.put(RunsColumns.RUN_INDEX, runIndex);
            values.put(RunsColumns.TIMESTAMP, getCurrentTime());
            values.put(RunsColumns.ARCHIVED, false);
            values.put(RunsColumns.TITLE, "");
            values.put(RunsColumns.AUTO_ZOOM_ENABLED, true);
            db.insert(Tables.RUNS, null, values);
        }
    }

    private void insertRunSensors(String runId,
            List<GoosciSensorLayout.SensorLayout> sensorLayouts) {
        synchronized (mLock) {
            final SQLiteDatabase db = mDbHelper.getWritableDatabase();
            final ContentValues values = new ContentValues();
            values.put(RunSensorsColumns.RUN_ID, runId);
            for (int i = 0; i < sensorLayouts.size(); i++) {
                GoosciSensorLayout.SensorLayout layout = sensorLayouts.get(i);
                fillLayoutValues(values, layout);
                values.put(RunSensorsColumns.POSITION, i);
                db.insert(Tables.RUN_SENSORS, null, values);
            }
        }
    }

    private void fillLayoutValues(ContentValues values, GoosciSensorLayout.SensorLayout layout) {
        values.put(RunSensorsColumns.SENSOR_ID, layout.sensorId);
        values.put(RunSensorsColumns.LAYOUT, ProtoUtils.makeBlob(layout));
    }

    private void updateTrialSensors(String runId,
            List<GoosciSensorLayout.SensorLayout> sensorLayouts) {
        synchronized (mLock) {
            final SQLiteDatabase db = mDbHelper.getWritableDatabase();
            final ContentValues values = new ContentValues();
            for (int i = 0; i < sensorLayouts.size(); i++) {
                GoosciSensorLayout.SensorLayout layout = sensorLayouts.get(i);
                values.put(RunSensorsColumns.LAYOUT, ProtoUtils.makeBlob(layout));
                db.update(Tables.RUN_SENSORS, values, RunSensorsColumns.RUN_ID + "=? AND " +
                        RunSensorsColumns.SENSOR_ID + "=?", new String[]{runId, layout.sensorId});
            }
        }
    }

    @VisibleForTesting
    void updateTrial(Trial trial) {
        // Only the labels, layout, title, archived state, and autozoom selection can be edited.
        // TODO: Make this transactional?
        synchronized (mLock) {
            final SQLiteDatabase db = mDbHelper.getWritableDatabase();
            for (Label label : getDatabaseLabelsForTrial(db, trial.getTrialId())) {
                // Don't delete the labels assets. If the label was deleted, that is done elsewhere.
                deleteDatabaseLabel(db, label);
            }
            final ContentValues values = new ContentValues();
            values.put(RunsColumns.TITLE, trial.getRawTitle());
            values.put(RunsColumns.ARCHIVED, trial.isArchived());
            values.put(RunsColumns.AUTO_ZOOM_ENABLED, trial.getAutoZoomEnabled());
            db.update(Tables.RUNS, values, RunsColumns.RUN_ID + "=?",
                    new String[]{trial.getTrialId()});
        }
        for (Label label : trial.getLabels()) {
            // TODO: Do we need to keep the experiment ID in the label? Can we ditch it?
            // It is never used on trial labels.
            // If we need to keep it, we can get it before doing the deletes above.
            addDatabaseLabel("UNUSED", trial.getTrialId(), label);
        }
        for (TrialStats stats : trial.getStats()) {
            setStats(trial.getTrialId(), stats.getSensorId(), stats);
        }
        updateTrialSensors(trial.getTrialId(), trial.getSensorLayouts());
    }

    @VisibleForTesting
    Trial getDatabaseTrial(String trialId, List<ApplicationLabel> applicationLabels) {
        Trial trial;
        synchronized (mLock) {
            final SQLiteDatabase db = mDbHelper.getReadableDatabase();
            trial = getDatabaseTrial(db, trialId, applicationLabels, mContext);
        }
        return trial;
    }

    private static Trial getDatabaseTrial(SQLiteDatabase db, String trialId,
            List<ApplicationLabel> applicationLabels, Context context) {
        List<Label> labels = getDatabaseLabelsForTrial(db, trialId);

        List<GoosciSensorLayout.SensorLayout> sensorLayouts = new ArrayList<>();
        int runIndex = -1;
        boolean archived = false;
        String title = "";
        boolean autoZoomEnabled = true;
        long creationTimeMs = -1;

        final String selection = RunSensorsColumns.RUN_ID + "=?";
        final String[] selectionArgs = new String[]{trialId};
        Cursor cursor = null;
        try {
            cursor = db.query(Tables.RUNS, new String[]{RunsColumns.RUN_INDEX, RunsColumns.TITLE,
                            RunsColumns.ARCHIVED, RunsColumns.AUTO_ZOOM_ENABLED,
                            RunsColumns.TIMESTAMP},
                    selection, selectionArgs, null, null, null);
            if (cursor != null & cursor.moveToFirst()) {
                runIndex = cursor.getInt(0);
                title = cursor.getString(1);
                archived = cursor.getInt(2) != 0;
                autoZoomEnabled = cursor.getInt(3) != 0;
                creationTimeMs = cursor.getLong(4);
            }
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }

        // Now get sensor layouts.
        GoosciSensorLayout.SensorLayout layout;
        int defaultColor = context.getResources().getColor(R.color.graph_line_color_blue);
        try {
            cursor = db.query(Tables.RUN_SENSORS, new String[]{RunSensorsColumns.LAYOUT,
                            RunSensorsColumns.SENSOR_ID}, selection, selectionArgs, null, null,
                    RunSensorsColumns.POSITION + " ASC");
            while (cursor.moveToNext()) {
                try {
                    byte[] blob = cursor.getBlob(0);
                    if (blob != null) {
                        layout = GoosciSensorLayout.SensorLayout.parseFrom(blob);
                    } else {
                        // In this case, create a fake sensorLayout since none exists.
                        layout = new GoosciSensorLayout.SensorLayout();
                        layout.sensorId = cursor.getString(1);
                        layout.color = defaultColor;
                    }
                    sensorLayouts.add(layout);
                } catch (InvalidProtocolBufferNanoException e) {
                    Log.d(TAG, "Couldn't parse layout", e);
                }
            }
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }

        if (runIndex != -1) {
            GoosciTrial.Trial trialProto = new GoosciTrial.Trial();
            trialProto.trialId = trialId;
            trialProto.sensorLayouts = sensorLayouts.toArray(
                    new GoosciSensorLayout.SensorLayout[sensorLayouts.size()]);
            trialProto.title = title;
            trialProto.autoZoomEnabled = autoZoomEnabled;
            trialProto.archived = archived;
            trialProto.creationTimeMs = creationTimeMs;

            populateTrialProtoFromLabels(trialProto, applicationLabels, labels);

            Trial result = Trial.fromTrial(trialProto);
            for (String sensorId : result.getSensorIds()) {
                result.setStats(getDatabaseStats(db, trialId, sensorId));
            }
            return result;
        } else {
            return null;
        }
    }

    @VisibleForTesting
    public static void populateTrialProtoFromLabels(GoosciTrial.Trial trialProto,
            List<ApplicationLabel> applicationLabels, List<Label> labels) {
        // Populate the recording and crop ranges from labels.
        trialProto.recordingRange = new GoosciTrial.Range();
        for (ApplicationLabel label : applicationLabels) {
            if (label.getType() == ApplicationLabel.TYPE_RECORDING_START) {
                trialProto.recordingRange.startMs = label.getTimeStamp();
            } else if (label.getType() == ApplicationLabel.TYPE_RECORDING_STOP) {
                trialProto.recordingRange.endMs = label.getTimeStamp();
            } else if (label.getType() == ApplicationLabel.TYPE_CROP_START) {
                if (trialProto.cropRange == null) {
                    trialProto.cropRange = new GoosciTrial.Range();
                }
                trialProto.cropRange.startMs = label.getTimeStamp();
            } else if (label.getType() == ApplicationLabel.TYPE_CROP_END) {
                if (trialProto.cropRange == null) {
                    trialProto.cropRange = new GoosciTrial.Range();
                }
                trialProto.cropRange.endMs = label.getTimeStamp();
            }
        }
        trialProto.labels = new GoosciLabel.Label[labels.size()];
        for (int i = 0; i < labels.size(); i++) {
            trialProto.labels[i] = labels.get(i).getLabelProto();
        }
    }

    /**
     * Set the sensor selection and layout for an experiment.
     */
    @VisibleForTesting
    void setExperimentSensorLayouts(String experimentId,
            List<GoosciSensorLayout.SensorLayout> sensorLayouts) {
        synchronized (mLock) {
            final SQLiteDatabase db = mDbHelper.getWritableDatabase();
            for (int i = 0; i < sensorLayouts.size(); i++) {
                ContentValues values = new ContentValues();
                values.put(ExperimentSensorLayoutColumns.EXPERIMENT_ID, experimentId);
                values.put(ExperimentSensorLayoutColumns.POSITION, i);
                values.put(ExperimentSensorLayoutColumns.LAYOUT,
                        ProtoUtils.makeBlob(sensorLayouts.get(i)));
                db.insertWithOnConflict(Tables.EXPERIMENT_SENSOR_LAYOUT, null, values,
                        SQLiteDatabase.CONFLICT_REPLACE);
            }

            db.delete(Tables.EXPERIMENT_SENSOR_LAYOUT,
                    ExperimentSensorLayoutColumns.EXPERIMENT_ID + "=? AND "
                            + ExperimentSensorLayoutColumns.POSITION + " >= "
                            + sensorLayouts.size(), new String[]{experimentId});
        }
    }

    /**
     * Retrieve the sensor selection and layout for an experiment.
     */
    @VisibleForTesting
    List<GoosciSensorLayout.SensorLayout> getDatabaseExperimentSensorLayouts(String experimentId) {
        List<GoosciSensorLayout.SensorLayout> layouts = new ArrayList<>();
        synchronized (mLock) {
            final SQLiteDatabase db = mDbHelper.getReadableDatabase();
            layouts = getDatabaseExperimentSensorLayouts(db, experimentId);
        }
        return layouts;
    }

    private static List<GoosciSensorLayout.SensorLayout> getDatabaseExperimentSensorLayouts(
            SQLiteDatabase db, String experimentId) {
        List<GoosciSensorLayout.SensorLayout> layouts = new ArrayList<>();
        Cursor cursor = null;
        try {
            cursor = db.query(Tables.EXPERIMENT_SENSOR_LAYOUT,
                    new String[]{ExperimentSensorLayoutColumns.LAYOUT},
                    ExperimentSensorLayoutColumns.EXPERIMENT_ID + "=?",
                    new String[]{experimentId}, null, null,
                    ExperimentSensorLayoutColumns.POSITION + " ASC");
            Set<String> sensorIdsAdded = new HashSet<>();
            while (cursor.moveToNext()) {
                try {
                    GoosciSensorLayout.SensorLayout layout =
                            GoosciSensorLayout.SensorLayout.parseFrom(cursor.getBlob(0));
                    if (!sensorIdsAdded.contains(layout.sensorId)) {
                        layouts.add(layout);
                    }
                    sensorIdsAdded.add(layout.sensorId);
                } catch (InvalidProtocolBufferNanoException e) {
                    Log.e(TAG, "Couldn't parse layout", e);
                }
            }
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        return layouts;
    }

    @VisibleForTesting
    void deleteDatabaseTrial(String runId) {
        synchronized (mLock) {
            final SQLiteDatabase db = mDbHelper.getWritableDatabase();
            deleteDatabaseTrial(db, runId, mContext, /*delete assets*/ true);
        }
    }

    private static void deleteDatabaseTrial(SQLiteDatabase db, String runId, Context context,
            boolean deleteAssets) {
        for (Label label : getDatabaseLabelsForTrial(db, runId)) {
            if (deleteAssets) {
                label.deleteAssets(context);
            }
            deleteDatabaseLabel(db, label);
        }
        for (ApplicationLabel label : getDatabaseApplicationLabelsWithStartId(db, runId)) {
            deleteDatabaseApplicationLabel(db, label);
        }
        String selectionRunId = RunsColumns.RUN_ID + "=?";
        String[] runIdArgs = new String[]{runId};
        db.delete(Tables.RUN_SENSORS, selectionRunId, runIdArgs);
        db.delete(Tables.RUN_STATS, RunStatsColumns.START_LABEL_ID + "=?", runIdArgs);
        db.delete(Tables.RUNS, selectionRunId, runIdArgs);
    }

    @Override
    public String addOrGetExternalSensor(ExternalSensorSpec sensor,
            Map<String, ExternalSensorProvider> providerMap) {
        synchronized (mLock) {
            final SQLiteDatabase db = mDbHelper.getReadableDatabase();
            String sql = "SELECT IFNULL(MIN(" + SensorColumns.SENSOR_ID + "), '') FROM " + Tables
                    .EXTERNAL_SENSORS + " WHERE " + SensorColumns.CONFIG + "=? AND " +
                    SensorColumns.TYPE + "=?";
            SQLiteStatement statement = db.compileStatement(sql);
            statement.bindBlob(1, sensor.getConfig());
            statement.bindString(2, sensor.getType());
            String sensorId = statement.simpleQueryForString();
            if (!sensorId.isEmpty()) {
                return sensorId;
            }
        }

        int suffix = 0;
        while (getExternalSensorById(ExternalSensorSpec.getSensorId(sensor, suffix), providerMap)
                != null) {
            suffix++;
        }

        String sensorId = ExternalSensorSpec.getSensorId(sensor, suffix);
        synchronized (mLock) {
            final SQLiteDatabase db = mDbHelper.getWritableDatabase();
            ContentValues values = getContentValuesFromSensor(sensor);
            values.put(SensorColumns.SENSOR_ID, sensorId);
            db.insert(Tables.EXTERNAL_SENSORS, null, values);
        }
        return sensorId;
    }

    private void addDatabaseLabel(String experimentId, String trialId, Label label) {
        synchronized (mLock) {
            final SQLiteDatabase db = mDbHelper.getWritableDatabase();
            addDatabaseLabel(db, experimentId, trialId, label);
        }
    }

    private static void addDatabaseLabel(SQLiteDatabase db, String experimentId, String trialId,
            Label label) {
        ContentValues values = new ContentValues();
        values.put(LabelColumns.EXPERIMENT_ID, experimentId);
        values.put(LabelColumns.TYPE, getLabelTag(label));
        values.put(LabelColumns.TIMESTAMP, label.getTimeStamp());
        values.put(LabelColumns.LABEL_ID, label.getLabelId());
        values.put(LabelColumns.START_LABEL_ID, trialId);
        // The database will only ever have one label value per label, so this is OK here.
        values.put(LabelColumns.VALUE, ProtoUtils.makeBlob(label.getLabelProto().values[0]));
        db.insert(Tables.LABELS, null, values);
    }

    private static String getLabelTag(Label label) {
        if (label.hasValueType(GoosciLabelValue.LabelValue.TEXT)) {
            return TEXT_LABEL_TAG;
        } else if (label.hasValueType(GoosciLabelValue.LabelValue.PICTURE)) {
            return PICTURE_LABEL_TAG;
        } else if (label.hasValueType(GoosciLabelValue.LabelValue.SENSOR_TRIGGER)) {
            return SENSOR_TRIGGER_LABEL_TAG;
        }
        return UNKNOWN_LABEL_TAG;
    }

    @VisibleForTesting
    void addDatabaseApplicationLabel(String experimentId, ApplicationLabel label) {
        ContentValues values = new ContentValues();
        values.put(LabelColumns.EXPERIMENT_ID, experimentId);
        values.put(LabelColumns.TYPE, label.getTag());
        values.put(LabelColumns.TIMESTAMP, label.getTimeStamp());
        values.put(LabelColumns.LABEL_ID, label.getLabelId());
        values.put(LabelColumns.START_LABEL_ID, label.getTrialId());
        values.put(LabelColumns.VALUE, ProtoUtils.makeBlob(label.getValue()));
        synchronized (mLock) {
            final SQLiteDatabase db = mDbHelper.getWritableDatabase();
            db.insert(Tables.LABELS, null, values);
        }
    }

    private static class LabelQuery {
        public static String[] PROJECTION = new String[]{
                LabelColumns.TYPE,
                LabelColumns.TIMESTAMP,
                LabelColumns.DATA,  // Deprecated for newer versions.
                LabelColumns.LABEL_ID,
                LabelColumns.START_LABEL_ID,
                LabelColumns.EXPERIMENT_ID,
                LabelColumns.VALUE,
        };

        public static int TYPE_INDEX = 0;
        public static int TIMESTAMP_INDEX = 1;
        public static int DATA_INDEX = 2;
        public static int LABEL_ID_INDEX = 3;
        public static int START_LABEL_ID_INDEX = 4;
        public static int EXPERIMENT_ID_INDEX = 5;
        public static int VALUE_INDEX = 6;
    }

    /**
     * Gets the labels for a given experiment. This function is still used privately to populate
     * an experiment with labels.
     */
    private static List<Label> getDatabaseLabelsForExperiment(SQLiteDatabase db,
            Experiment experiment) {
        final String selection = LabelColumns.EXPERIMENT_ID + "=? AND " +
                LabelColumns.START_LABEL_ID + "=? and not " + LabelColumns.TYPE + "=?";;
        final String[] selectionArgs = new String[]{experiment.getExperimentId(),
                RecorderController.NOT_RECORDING_RUN_ID, ApplicationLabel.TAG};
        return getDatabaseLabels(db, selection, selectionArgs);
    }

    private static List<Label> getDatabaseLabels(SQLiteDatabase db, String selection,
            String[] selectionArgs) {
        List<Label> labels = new ArrayList<>();
        Cursor cursor = null;
        try {
            cursor = db.query(Tables.LABELS, LabelQuery.PROJECTION, selection, selectionArgs,
                    null, null, null);
            while (cursor.moveToNext()) {
                String type = cursor.getString(LabelQuery.TYPE_INDEX);
                if (ApplicationLabel.isTag(type)) {
                    continue;
                }
                // TODO: fix code smell: perhaps make a factory?
                final String labelId = cursor.getString(LabelQuery.LABEL_ID_INDEX);
                long timestamp = cursor.getLong(LabelQuery.TIMESTAMP_INDEX);
                GoosciLabelValue.LabelValue value = null;
                try {
                    byte[] blob = cursor.getBlob(LabelQuery.VALUE_INDEX);
                    if (blob != null) {
                        value = GoosciLabelValue.LabelValue.parseFrom(blob);
                    }
                } catch (InvalidProtocolBufferNanoException ex) {
                    Log.d(TAG, "Unable to parse label value");
                }
                GoosciLabel.Label goosciLabel = new GoosciLabel.Label();
                goosciLabel.labelId = labelId;
                goosciLabel.timestampMs = timestamp;
                goosciLabel.creationTimeMs = timestamp;
                if (value != null) {
                    // Add new types of labels to this list, upgrading to Captions where appropriate
                    if (TextUtils.equals(type, PICTURE_LABEL_TAG) ||
                            TextUtils.equals(type, SENSOR_TRIGGER_LABEL_TAG)) {
                        GoosciCaption.Caption caption = new GoosciCaption.Caption();
                        caption.lastEditedTimestamp = timestamp;
                        if (TextUtils.equals(type, PICTURE_LABEL_TAG)) {
                            caption.text = PictureLabelValue.getCaption(value);
                            // No need to save this in the label value now that it is a caption.
                            PictureLabelValue.clearCaption(value);
                            value.type = GoosciLabelValue.LabelValue.PICTURE;
                        } else if (TextUtils.equals(type, SENSOR_TRIGGER_LABEL_TAG)) {
                            caption.text = SensorTriggerLabelValue.getCustomText(value);
                            // No need to save this in the label value now that it is a caption.
                            SensorTriggerLabelValue.clearCustomText(value);
                            value.type = GoosciLabelValue.LabelValue.SENSOR_TRIGGER;
                        }
                        goosciLabel.caption = caption;
                    }
                    goosciLabel.values = new GoosciLabelValue.LabelValue[1];
                    goosciLabel.values[0] = value;
                } else {
                    // Old text, picture and application labels were added when label data
                    // was stored as a string. New types of labels should not be added to this
                    // list.
                    final String data = cursor.getString(LabelQuery.DATA_INDEX);
                    if (TextUtils.equals(type, TEXT_LABEL_TAG)) {
                        value = TextLabelValue.fromText(data).getValue();
                    } else if (TextUtils.equals(type, PICTURE_LABEL_TAG)) {
                        // Early picture labels had no captions.
                        value = PictureLabelValue.fromPicture(data, "").getValue();
                    } else {
                        throw new IllegalStateException("Unknown label type: " + type);
                    }
                    goosciLabel.values = new GoosciLabelValue.LabelValue[1];
                    goosciLabel.values[0] = value;
                }
                labels.add(Label.fromLabel(goosciLabel));
            }
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        return labels;
    }

    private static List<Label> getDatabaseLabelsForTrial(SQLiteDatabase db, String trialId) {
        final String selection = LabelColumns.START_LABEL_ID + "=? and not " +
                LabelColumns.TYPE + "=?";
        final String[] selectionArgs = new String[]{trialId, ApplicationLabel.TAG};
        return getDatabaseLabels(db, selection, selectionArgs);
    }

    @VisibleForTesting
    List<ApplicationLabel> getDatabaseApplicationLabelsWithStartId(String trialId) {
        List<ApplicationLabel> result;
        synchronized (mLock) {
            final SQLiteDatabase db = mDbHelper.getReadableDatabase();
            result = getDatabaseApplicationLabelsWithStartId(db, trialId);
        }
        return result;
    }

    private static List<ApplicationLabel> getDatabaseApplicationLabelsWithStartId(SQLiteDatabase db,
            String trialId) {
        final String selection = LabelColumns.START_LABEL_ID + "=? and " + LabelColumns.TYPE + "=?";
        final String[] selectionArgs = new String[]{trialId, ApplicationLabel.TAG};
        return getDatabaseApplicationLabels(db, selection, selectionArgs);
    }

    private static List<ApplicationLabel> getDatabaseApplicationLabels(SQLiteDatabase db,
            String selection, String[] selectionArgs) {
        List<ApplicationLabel> labels = new ArrayList<>();
        Cursor cursor = null;
        try {
            cursor = db.query(Tables.LABELS, LabelQuery.PROJECTION, selection, selectionArgs,
                    null, null, null);
            while (cursor.moveToNext()) {
                String type = cursor.getString(LabelQuery.TYPE_INDEX);
                ApplicationLabel label;
                // TODO: fix code smell: perhaps make a factory?
                final String labelId = cursor.getString(LabelQuery.LABEL_ID_INDEX);
                final String trialId = cursor.getString(LabelQuery.START_LABEL_ID_INDEX);
                long timestamp = cursor.getLong(LabelQuery.TIMESTAMP_INDEX);
                GoosciLabelValue.LabelValue value = null;
                try {
                    byte[] blob = cursor.getBlob(LabelQuery.VALUE_INDEX);
                    if (blob != null) {
                        value = GoosciLabelValue.LabelValue.parseFrom(blob);
                    }
                } catch (InvalidProtocolBufferNanoException ex) {
                    Log.d(TAG, "Unable to parse label value");
                }
                if (value == null) {
                    // Old text, picture and application labels were added when label data
                    // was stored as a string. New types of labels should not be added to this
                    // list.
                    final String data = cursor.getString(LabelQuery.DATA_INDEX);
                    label = new ApplicationLabel(data, labelId, trialId, timestamp);
                } else {
                    label = new ApplicationLabel(labelId, trialId, timestamp, value);
                }
                labels.add(label);
            }
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        return labels;
    }

    private void setStats(String trialId, String sensorId, TrialStats stats) {
        ContentValues values = new ContentValues();
        values.put(RunStatsColumns.START_LABEL_ID, trialId);
        values.put(RunStatsColumns.SENSOR_TAG, sensorId);
        RunStats runStats = RunStats.fromTrialStats(stats);
        for (String key : runStats.getKeys()) {
            values.put(RunStatsColumns.STAT_NAME, key);
            values.put(RunStatsColumns.STAT_VALUE, runStats.getStat(key));
            synchronized (mLock) {
                final SQLiteDatabase db = mDbHelper.getWritableDatabase();
                db.insert(Tables.RUN_STATS, null, values);
            }
        }
        // Add the status too
        values.put(RunStatsColumns.STAT_NAME, StatsAccumulator.KEY_STATUS);
        values.put(RunStatsColumns.STAT_VALUE, runStats.getStatus());
        synchronized (mLock) {
            final SQLiteDatabase db = mDbHelper.getWritableDatabase();
            db.insert(Tables.RUN_STATS, null, values);
        }
    }

    private static TrialStats getDatabaseStats(SQLiteDatabase db, String trialId, String sensorId) {
        RunStats runStats = new RunStats(sensorId);
        Cursor cursor = null;
        try {
            cursor = db.query(Tables.RUN_STATS,
                    new String[]{RunStatsColumns.STAT_NAME, RunStatsColumns.STAT_VALUE},
                    RunStatsColumns.START_LABEL_ID + " =? AND " + RunStatsColumns.SENSOR_TAG
                            + " =?",
                    new String[]{trialId, sensorId}, null, null, null);
            while (cursor.moveToNext()) {
                final String statName = cursor.getString(0);
                final double statValue = cursor.getDouble(1);
                if (TextUtils.equals(statName, StatsAccumulator.KEY_STATUS)) {
                    runStats.setStatus((int) statValue);
                } else {
                    runStats.putStat(statName, statValue);
                }
            }
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        return runStats.getTrialStats();
    }

    @VisibleForTesting
    List<String> getDatabaseExperimentRunIds(String experimentId, boolean includeArchived) {
        // TODO: use start index as offset.
        List<String> ids;
        synchronized (mLock) {
            final SQLiteDatabase db = mDbHelper.getReadableDatabase();
            ids = getDatabaseExperimentRunIds(db, experimentId, includeArchived);
        }
        return ids;
    }

    private static List<String> getDatabaseExperimentRunIds(SQLiteDatabase db, String experimentId,
            boolean includeArchived) {
        List<String> ids = new ArrayList<>();
        Cursor cursor = null;
        try {
            String selection = LabelColumns.LABEL_ID + "=" + LabelColumns.START_LABEL_ID +
                    " AND " + LabelColumns.EXPERIMENT_ID + "=?";
            if (!includeArchived) {
                selection += " AND (" + RunsColumns.ARCHIVED + "=0 OR " +
                        RunsColumns.ARCHIVED + " IS NULL)";
            }
            cursor = db.query(
                    Tables.RUNS + " AS r JOIN " + Tables.LABELS + " AS l ON "
                            + RunsColumns.RUN_ID + "=" + LabelColumns.START_LABEL_ID,
                    new String[]{RunsColumns.RUN_ID}, selection, new String[]{experimentId},
                    null, null, "r." + RunsColumns.TIMESTAMP + " DESC", null);
            while (cursor.moveToNext()) {
                ids.add(cursor.getString(0));
            }
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        return ids;
    }

    @VisibleForTesting
    void editApplicationLabel(ApplicationLabel updatedLabel) {
        synchronized (mLock) {
            final SQLiteDatabase db = mDbHelper.getWritableDatabase();
            final ContentValues values = new ContentValues();
            values.put(LabelColumns.VALUE, ProtoUtils.makeBlob(updatedLabel.getValue()));
            values.put(LabelColumns.TIMESTAMP, updatedLabel.getTimeStamp());
            db.update(Tables.LABELS, values, LabelColumns.LABEL_ID + "=?",
                    new String[]{updatedLabel.getLabelId()});
        }
    }

    // Deletes a label from the database, but does not touch its assets.
    private static void deleteDatabaseLabel(SQLiteDatabase db, Label label) {
        String selection = LabelColumns.LABEL_ID + "=?";
        db.delete(Tables.LABELS, selection, new String[]{label.getLabelId()});
    }

    private static void deleteDatabaseApplicationLabel(SQLiteDatabase db, ApplicationLabel label) {
        String selection = LabelColumns.LABEL_ID + "=?";
        db.delete(Tables.LABELS, selection, new String[]{label.getLabelId()});
    }

    @NonNull
    private ContentValues getContentValuesFromSensor(ExternalSensorSpec sensor) {
        ContentValues values = new ContentValues();
        values.put(SensorColumns.TYPE, sensor.getType());
        values.put(SensorColumns.NAME, sensor.getName());
        values.put(SensorColumns.CONFIG, sensor.getConfig());
        return values;
    }

    @Override
    public void removeExternalSensor(String databaseTag) {
        synchronized (mLock) {
            final SQLiteDatabase db = mDbHelper.getWritableDatabase();
            db.delete(Tables.EXTERNAL_SENSORS, SensorColumns.SENSOR_ID + "=?",
                    new String[]{databaseTag});
            db.delete(Tables.EXPERIMENT_SENSORS, ExperimentSensorColumns.SENSOR_TAG + "=?",
                    new String[]{databaseTag});
        }
    }

    private static class SensorQuery {
        public static String[] PROJECTION = new String[]{
                SensorColumns.SENSOR_ID,
                SensorColumns.TYPE,
                SensorColumns.NAME,
                SensorColumns.CONFIG
        };

        static int DATABASE_TAG_INDEX = 0;
        static int TYPE_INDEX = 1;
        static int NAME_INDEX = 2;
        static int CONFIG_INDEX = 3;
    }

    @Override
    public Map<String, ExternalSensorSpec> getExternalSensors(
            Map<String, ExternalSensorProvider> providerMap) {
        Map<String, ExternalSensorSpec> sensors = new HashMap<>();

        synchronized (mLock) {
            final SQLiteDatabase db = mDbHelper.getReadableDatabase();
            Cursor c = null;
            try {
                c = db.query(Tables.EXTERNAL_SENSORS, SensorQuery.PROJECTION, null, null, null,
                        null, null);
                while (c.moveToNext()) {
                    ExternalSensorSpec value = loadSensorFromDatabase(c, providerMap);
                    if (value != null) {
                        sensors.put(c.getString(SensorQuery.DATABASE_TAG_INDEX), value);
                    }
                }
            } finally {
                if (c != null) {
                    c.close();
                }
            }
        }

        return sensors;
    }

    @Override
    public ExternalSensorSpec getExternalSensorById(String id,
            Map<String, ExternalSensorProvider> providerMap) {
        ExternalSensorSpec sensor = null;
        synchronized (mLock) {
            final SQLiteDatabase db = mDbHelper.getReadableDatabase();
            Cursor c = null;
            try {
                c = db.query(Tables.EXTERNAL_SENSORS, SensorQuery.PROJECTION,
                        SensorColumns.SENSOR_ID + "=?", new String[]{id}, null, null, null);
                if (c.moveToNext()) {
                    sensor = loadSensorFromDatabase(c, providerMap);
                }
            } finally {
                if (c != null) {
                    c.close();
                }
            }
        }
        return sensor;
    }

    private ExternalSensorSpec loadSensorFromDatabase(Cursor c,
            Map<String, ExternalSensorProvider> providerMap) {
        String type = c.getString(SensorQuery.TYPE_INDEX);
        ExternalSensorProvider externalSensorProvider = providerMap.get(type);
        if (externalSensorProvider == null) {
            throw new IllegalArgumentException("No provider for sensor type: " + type);
        }
        return externalSensorProvider.buildSensorSpec(c.getString(SensorQuery.NAME_INDEX),
                c.getBlob(SensorQuery.CONFIG_INDEX));
    }

    @Override
    public void addSensorToExperiment(String databaseTag, String experimentId) {
        setSensorExperimentInclusion(databaseTag, experimentId, true);
    }

    @Override
    public void removeSensorFromExperiment(String databaseTag, String experimentId) {
        setSensorExperimentInclusion(databaseTag, experimentId, false);
    }

    private void setSensorExperimentInclusion(String databaseTag, String experimentId,
            boolean included) {
        synchronized (mLock) {
            final SQLiteDatabase db = mDbHelper.getWritableDatabase();

            // Because of legacy oddities, a databaseTag may be "included" multiple times.
            // Delete them all (and don't do that again).
            removeSensorExperimentInclusion(databaseTag, experimentId);
            addSensorExperimentInclusion(db, databaseTag, experimentId, included);
        }
    }

    private void removeSensorExperimentInclusion(String databaseTag, String experimentId) {
        String selection = ExperimentSensorColumns.SENSOR_TAG + " =? AND " +
                ExperimentSensorColumns.EXPERIMENT_ID + "=?";
        String[] selectionArgs = new String[]{databaseTag, experimentId};
        final SQLiteDatabase db = mDbHelper.getWritableDatabase();
        db.delete(Tables.EXPERIMENT_SENSORS, selection, selectionArgs);
    }

    private static void addSensorExperimentInclusion(SQLiteDatabase db, String databaseTag,
            String experimentId, boolean included) {
        ContentValues values = new ContentValues();
        values.put(ExperimentSensorColumns.SENSOR_TAG, databaseTag);
        values.put(ExperimentSensorColumns.EXPERIMENT_ID, experimentId);
        values.put(ExperimentSensorColumns.INCLUDED, included ? 1 : 0);
        db.insert(Tables.EXPERIMENT_SENSORS, ExperimentSensorColumns.SENSOR_TAG, values);
    }

    @Override
    public ExperimentSensors getExperimentExternalSensors(String experimentId,
            Map<String, ExternalSensorProvider> providerMap,
            ConnectableSensor.Connector connector) {
        List<ConnectableSensor> includedSensors = new ArrayList<>();
        Set<String> excludedTags = new ArraySet<>();

        synchronized (mLock) {
            final SQLiteDatabase db = mDbHelper.getReadableDatabase();
            Cursor c = null;
            List<String> tags = new ArrayList<>();
            try {
                // Explicitly order by ascending rowid, to preserve insertion order
                c = db.query(Tables.EXPERIMENT_SENSORS,
                        new String[]{ExperimentSensorColumns.SENSOR_TAG,
                                ExperimentSensorColumns.INCLUDED},
                        ExperimentSensorColumns.EXPERIMENT_ID + "=?",
                        new String[]{experimentId}, null, null, BaseColumns._ID + " ASC");
                while (c.moveToNext()) {
                    String tag = c.getString(0);
                    boolean included = c.getInt(1) > 0;

                    if (included) {
                        // We don't expect to get duplicates, but we can deal with them gracefully.
                        if (!tags.contains(tag)) {
                            tags.add(tag);
                        }
                    } else {
                        excludedTags.add(tag);
                    }
                }
            } finally {
                if (c != null) {
                    c.close();
                }
            }

            // This is somewhat inefficient to do nested queries, but in most cases there will
            // only be one or two, so we are trading off code complexity of doing a db join.
            for (String tag : tags) {
                includedSensors.add(
                        connector.connected(getExternalSensorById(tag, providerMap), tag));
            }
        }

        return new ExperimentSensors(includedSensors, excludedTags);
    }

    @Override
    public void addMyDevice(InputDeviceSpec deviceSpec) {
        String deviceId = addOrGetExternalSensor(deviceSpec, InputDeviceSpec.PROVIDER_MAP);
        ContentValues values = new ContentValues();
        values.put(MyDevicesColumns.DEVICE_ID, deviceId);
        synchronized (mLock) {
            final SQLiteDatabase db = mDbHelper.getWritableDatabase();
            db.insert(Tables.MY_DEVICES, null, values);
        }
    }

    @Override
    public void removeMyDevice(InputDeviceSpec deviceSpec) {
        String deviceId = addOrGetExternalSensor(deviceSpec, InputDeviceSpec.PROVIDER_MAP);
        synchronized (mLock) {
            final SQLiteDatabase db = mDbHelper.getWritableDatabase();
            db.delete(Tables.MY_DEVICES, MyDevicesColumns.DEVICE_ID + "=?", new String[]{deviceId});
        }
    }

    @Override
    public List<InputDeviceSpec> getMyDevices() {
        ArrayList<InputDeviceSpec> myDevices = Lists.newArrayList();

        synchronized (mLock) {
            final SQLiteDatabase db = mDbHelper.getReadableDatabase();
            Cursor c = null;
            try {
                c = db.query(Tables.MY_DEVICES, new String[]{MyDevicesColumns.DEVICE_ID},
                        null, null, null, null, BaseColumns._ID + " ASC");
                while (c.moveToNext()) {
                    InputDeviceSpec spec = (InputDeviceSpec) getExternalSensorById(c.getString(0),
                            InputDeviceSpec.PROVIDER_MAP);

                    // I _think_ this data state is only possible when debugging puts the data in
                    // weird states, but just to be safe...
                    if (spec != null) {
                        myDevices.add(spec);
                    }
                }
            } finally {
                if (c != null) {
                    c.close();
                }
            }
        }
        return myDevices;
    }

    /**
     * Adds a new trigger.
     * @param trigger
     * @param experimentId The experiment active when the trigger was first added.
     */
    private void addSensorTrigger(SensorTrigger trigger, String experimentId) {
        ContentValues values = new ContentValues();
        values.put(SensorTriggerColumns.EXPERIMENT_ID, experimentId);
        values.put(SensorTriggerColumns.TRIGGER_ID, trigger.getTriggerId());
        values.put(SensorTriggerColumns.LAST_USED_TIMESTAMP_MS, trigger.getLastUsed());
        values.put(SensorTriggerColumns.SENSOR_ID, trigger.getSensorId());
        values.put(SensorTriggerColumns.TRIGGER_INFORMATION,
                ProtoUtils.makeBlob(trigger.getTriggerProto().triggerInformation));
        synchronized (mLock) {
            final SQLiteDatabase db = mDbHelper.getWritableDatabase();
            db.insert(Tables.SENSOR_TRIGGERS, null, values);
        }
    }

    /**
     * Updates an existing SensorTrigger. note that only the last used timestamp and
     * TriggerInformation can be mutated.
     * @param trigger
     */
    private void updateSensorTrigger(SensorTrigger trigger) {
        // Only the LastUsedTimestamp and TriggerInformation can be updated.
        ContentValues values = new ContentValues();
        values.put(SensorTriggerColumns.LAST_USED_TIMESTAMP_MS, trigger.getLastUsed());
        values.put(SensorTriggerColumns.TRIGGER_INFORMATION,
                ProtoUtils.makeBlob(trigger.getTriggerProto().triggerInformation));
        synchronized (mLock) {
            final SQLiteDatabase db = mDbHelper.getWritableDatabase();
            db.update(Tables.SENSOR_TRIGGERS, values, SensorTriggerColumns.TRIGGER_ID + "=?",
                    new String[]{trigger.getTriggerId()});
        }
    }

    /**
     * Gets a list of SensorTrigger by their experiment ID.
     */
    private static List<SensorTrigger> getDatabaseSensorTriggers(SQLiteDatabase db,
            String experimentId) {
        List<SensorTrigger> triggers = new ArrayList<>();
        if (TextUtils.isEmpty(experimentId)) {
            return triggers;
        }

        final String selection = SensorTriggerColumns.EXPERIMENT_ID + "=?";
        String[] selectionArgs = new String[]{experimentId};
        Cursor c = null;
        try {
            c = db.query(Tables.SENSOR_TRIGGERS, new String[]{SensorTriggerColumns.TRIGGER_ID,
                            SensorTriggerColumns.SENSOR_ID,
                            SensorTriggerColumns.LAST_USED_TIMESTAMP_MS,
                            SensorTriggerColumns.TRIGGER_INFORMATION},
                    selection, selectionArgs, null, null,
                    SensorTriggerColumns.LAST_USED_TIMESTAMP_MS + " DESC");
            if (c == null || !c.moveToFirst()) {
                return triggers;
            }
            while (!c.isAfterLast()) {
                triggers.add(SensorTrigger.fromTrigger(c.getString(0), c.getString(1), c.getLong(2),
                        GoosciSensorTriggerInformation.TriggerInformation.parseFrom(c.getBlob(3))));
                c.moveToNext();
            }
        } catch (InvalidProtocolBufferNanoException e) {
            e.printStackTrace();
        } finally {
            if (c != null) {
                c.close();
            }
        }
        return triggers;
    }

    /**
     * Deletes the SensorTrigger from the database.
     */
    private static void deleteDatabaseSensorTrigger(SQLiteDatabase db, SensorTrigger trigger) {
        db.delete(Tables.SENSOR_TRIGGERS, SensorTriggerColumns.TRIGGER_ID + "=?",
                new String[]{trigger.getTriggerId()});
    }

    public interface ProjectColumns {

        /**
         * Stable project ID.
         */
        String PROJECT_ID = "project_id";

        /**
         * Project Title.
         */
        String TITLE = "title";

        /**
         * Project description.
         */
        String DESCRIPTION = "description";

        /**
         * Project cover photo. This is a local file URL.
         */
        String COVER_PHOTO = "cover_photo";


        /**
         * Whether the project is archived or not.
         */
        String ARCHIVED = "archived";

        /**
         * Timestamp in UTC based on phone system clock of the last time the project was used.
         */
        String LAST_USED_TIME = "last_used_time";

        /**
         * Selection args for getting a project data.
         */
        String[] GET_COLUMNS = new String[]{
                BaseColumns._ID,
                ProjectColumns.PROJECT_ID,
                ProjectColumns.TITLE,
                ProjectColumns.COVER_PHOTO,
                ProjectColumns.ARCHIVED,
                ProjectColumns.DESCRIPTION,
                ProjectColumns.LAST_USED_TIME
        };
    }

    public interface ExperimentColumns {

        /**
         * Project this experiment belongs to, corresponding to {@link ProjectColumns#PROJECT_ID}.
         */
        String PROJECT_ID = "project_id";

        /**
         * Stable experiment ID.
         */
        String EXPERIMENT_ID = "experiment_id";

        /**
         * Timestamp of this experiment when it was created.
         */
        String TIMESTAMP = "timestamp";

        /**
         * Experiment title.
         */
        String TITLE = "title";

        /**
         * Experiment description.
         */
        String DESCRIPTION = "description";

        /**
         * Whether the experiment is archived or not.
         */
        String ARCHIVED = "archived";

        /**
         * Timestamp of when this experiment was last used.
         */
        String LAST_USED_TIME = "last_used_time";

        String[] GET_COLUMNS = new String[]{
                BaseColumns._ID,
                ExperimentColumns.EXPERIMENT_ID,
                ExperimentColumns.TIMESTAMP,
                ExperimentColumns.TITLE,
                ExperimentColumns.DESCRIPTION,
                ExperimentColumns.PROJECT_ID,
                ExperimentColumns.ARCHIVED,
                ExperimentColumns.LAST_USED_TIME
        };
    }

    public interface LabelColumns {

        /**
         * Experiment this label belongs to, corresponding to
         * {@link ExperimentColumns#EXPERIMENT_ID}.
         */
        String EXPERIMENT_ID = "experiment_id";

        /**
         * Time when this label was created.
         */
        String TIMESTAMP = "timestamp";

        /**
         * Type of label, "text", "picture", "application", or "sensorTriggerLabel".
         */
        String TYPE = "type";

        /**
         * Data for the label: in the case of a text label, this is the text. In the case of other
         * types, a Uri pointing at the media.
         * This field is deprecated at database version 15, but is still read for old labels.
         */
        String DATA = "data";

        /**
         * Unique id for the label.
         */
        String LABEL_ID = "label_id";

        /**
         * ID for the run that this label is associated with.
         */
        String START_LABEL_ID = "start_label_id";

        /**
         * The GoosciLabelStorage stored as a blob for the value of the data.
         */
        String VALUE = "value";
    }

    public interface SensorColumns {

        /**
         * ID of the sensor. Should be unique.
         */
        String SENSOR_ID = "sensor_id";

        /**
         * Type of external sensor.
         */
        String TYPE = "type";

        /**
         * Human readable name of this sensor.
         */
        String NAME = "name";

        /**
         * Configuration data for this sensor.
         */
        String CONFIG = "config";
    }

    public interface ExperimentSensorColumns {

        /**
         * Database tag of a sensor that belongs (or doesn't) to a particular experiment.
         */
        String SENSOR_TAG = "sensor_tag";

        /**
         * Experiment ID.
         */
        String EXPERIMENT_ID = "experiment_id";

        /**
         * boolean, 1 = included, 0 = excluded.
         */
        String INCLUDED = "included";
    }

    public interface RunStatsColumns {
        /**
         * ID for the run that this stat is associated with.
         */
        String START_LABEL_ID = "start_label_id";

        /**
         * Database tag of the sensor that this stat is associated with.
         */
        String SENSOR_TAG = "sensor_tag";

        /**
         * Name of the stat being stored.
         */
        String STAT_NAME = "stat_name";

        /**
         * Value of the stat
         */
        String STAT_VALUE = "stat_value";
    }

    public interface RunsColumns {
        /**
         * ID for the run that this row is associated with.  (Matches "start_label_id" in some
         * other tables)
         */
        String RUN_ID = "run_id";

        /**
         * Index of this run in the total experiment list. Storing this because we retrieve runs
         * one at a time, so can't derive this at query time.
         */
        String RUN_INDEX = "run_index";

        /**
         * Time when this run was _completed_
         */
        String TIMESTAMP = "timestamp";

        /**
         * User chosen name for this run. This may be empty.
         */
        String TITLE = "run_title";

        /**
         * Whether the run is archived.
         */
        String ARCHIVED = "run_archived";

        /**
         * Whether auto zoom is enabled (i.e. RunReview should zoom in on the Y axis by default)
         */
        String AUTO_ZOOM_ENABLED = "auto_zoom_enabled";
    }

    public interface RunSensorsColumns {
        /**
         * ID for the run that this sensor is associated with.  (Matches "start_label_id" in some
         * other tables)
         */
        String RUN_ID = "run_id";

        /**
         * ID of the sensor
         */
        String SENSOR_ID = "sensor_id";

        /**
         * Position in the list of sensors on screen
         */
        String POSITION = "position";

        /**
         * ID for the sensor layout this sensor is associated with.
         */
        String LAYOUT = "layout";
    }

    public interface ExperimentSensorLayoutColumns {
        /**
         * Experiment ID.
         */
        String EXPERIMENT_ID = "experiment_id";

        /**
         * Position in the list of sensors on screen
         */
        String POSITION = "position";

        /**
         * Layout of this sensor (including sensorId)
         */
        String LAYOUT = "layout";
    }

    public interface SensorTriggerColumns {
        /**
         * Trigger ID. THis is unique.
         */
        String TRIGGER_ID = "trigger_id";

        /**
         * Sensor ID for this trigger.
         */
        String SENSOR_ID = "sensor_id";

        /**
         * The timestamp when this trigger was last used.
         */
        String LAST_USED_TIMESTAMP_MS = "last_used_timestamp";

        /**
         * The experiment ID that this trigger is associated with.
         */
        String EXPERIMENT_ID = "experiment_id";

        /**
         * The TriggerInformation proto containing the configuration of this trigger.
         */
        String TRIGGER_INFORMATION = "trigger_information";
    }

    public interface MyDevicesColumns {
        /**
         * The id of a device that has been memorized to "My Devices"
         * This should be a key to a row in EXTERNAL_SENSORS
         */
        String DEVICE_ID = "device_id";
    }

    /**
     * Manages the SQLite database backing the data for the entire app.
     */
    private static class DatabaseHelper extends SQLiteOpenHelper {
        private static final int DB_VERSION = 22;
        private static final String DB_NAME = "main.db";

        // Callbacks for database upgrades.
        interface MetadataDatabaseUpgradeCallback {
            // Called when project data needs to be migrated.
            void onMigrateProjectData(SQLiteDatabase db);

            // Called when experiment data needs to be migrated.
            void onMigrateExperimentsToFiles(SQLiteDatabase db);
        }
        private MetadataDatabaseUpgradeCallback mUpgradeCallback;

        DatabaseHelper(Context context, String filename,
                MetadataDatabaseUpgradeCallback upgradeCallback) {
            super(context, filename != null ? filename : DB_NAME, null, DB_VERSION);
            mUpgradeCallback = upgradeCallback;
        }

        @Override
        public void onCreate(SQLiteDatabase db) {
            createProjectsTable(db);
            createExperimentsTable(db);
            createLabelsTable(db);
            createSensorsTable(db);
            createExperimentSensorsTable(db);
            createRunStatsTable(db);
            createRunsTable(db);
            createRunSensorsTable(db);
            createExperimentSensorLayoutTable(db);
            createSensorTriggersTable(db);
            createMyDevicesTable(db);
        }

        private void createExperimentsTable(SQLiteDatabase db) {
            db.execSQL("CREATE TABLE " + Tables.EXPERIMENTS + " ("
                    + BaseColumns._ID + " INTEGER PRIMARY KEY AUTOINCREMENT,"
                    + ExperimentColumns.EXPERIMENT_ID + " TEXT NOT NULL, "
                    + ExperimentColumns.PROJECT_ID + " TEXT NOT NULL, "
                    + ExperimentColumns.TIMESTAMP + " INTEGER NOT NULL, "
                    + ExperimentColumns.TITLE + " TEXT, "
                    + ExperimentColumns.DESCRIPTION + " TEXT, "
                    + ExperimentColumns.ARCHIVED + " BOOLEAN NOT NULL DEFAULT 0, "
                    + ExperimentColumns.LAST_USED_TIME + " INTEGER, "
                    + "UNIQUE (" + ExperimentColumns.EXPERIMENT_ID + ") ON CONFLICT REPLACE)");
        }

        @Override
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
            int version = oldVersion;

            if ((version == 1 || version == 2) && version < newVersion) {
                // 1 -> 2: Recreate labels table with label_id field.
                // 2 -> 3: Recreate labels table with start_label_id field.

                db.execSQL("DROP TABLE " + Tables.LABELS);
                createLabelsTable(db);
                //noinspection UnusedAssignment
                version = 3;
            }
            if (version == 3 && version < newVersion) {
                // 3 -> 4: Add sensors table and mapping table for experiment sensors.
                createSensorsTable(db);
                createExperimentSensorsTable(db);
                version = 4;
            }
            if (version == 4 && version < newVersion) {
                // 4 -> 5: Add new columns to projects table (title, cover and archived).
                // Also add title, archived and description to experiments table.
                db.execSQL("ALTER TABLE " + Tables.PROJECTS + " ADD COLUMN " +
                        ProjectColumns.TITLE + " TEXT");
                db.execSQL("ALTER TABLE " + Tables.PROJECTS + " ADD COLUMN " +
                        ProjectColumns.ARCHIVED + " BOOLEAN");
                db.execSQL("ALTER TABLE " + Tables.PROJECTS + " ADD COLUMN " +
                        ProjectColumns.COVER_PHOTO + " TEXT");

                db.execSQL("ALTER TABLE " + Tables.EXPERIMENTS + " ADD COLUMN " +
                        ExperimentColumns.TITLE + " TEXT");
                db.execSQL("ALTER TABLE " + Tables.EXPERIMENTS + " ADD COLUMN " +
                        ExperimentColumns.ARCHIVED + " BOOLEAN");
                db.execSQL("ALTER TABLE " + Tables.EXPERIMENTS + " ADD COLUMN " +
                        ExperimentColumns.DESCRIPTION + " TEXT");
                version = 5;
            }
            if (version == 5 && version < newVersion) {
                // 5 -> 6: Drop tables and recreate them to set a default FALSE value to archived
                // bit in projects and experiments tables. SQLite does not support ALTER TABLE
                // foo ALTER COLUMN or DROP COLUMN. SQLite implements a small subset of SQL.
                // Hence the need to drop the tables and re-create them.
                // See https://www.sqlite.org/lang_altertable.html for allowed syntax.
                db.execSQL("DROP TABLE " + Tables.PROJECTS);
                db.execSQL("DROP TABLE " + Tables.EXPERIMENTS);
                db.execSQL("DROP TABLE " + Tables.EXPERIMENT_SENSORS);
                db.execSQL("DROP TABLE " + Tables.LABELS);
                createProjectsTable(db);
                createExperimentsTable(db);
                createLabelsTable(db);
                createExperimentSensorsTable(db);
                version = 6;
            }
            if (version == 6 && version < newVersion) {
                createRunStatsTable(db);
                version = 7;
            }
            if (version == 7 && version < newVersion) {
                // 7 -> 8: Add description column to projects.
                db.execSQL("ALTER TABLE " + Tables.PROJECTS + " ADD COLUMN " +
                        ProjectColumns.DESCRIPTION + " TEXT");
            }

            if (version == 8 && version < newVersion) {
                // We could try to rebuild the runs table from the information in the labels
                // table, but it's likely not worth it pre-release.
                db.execSQL("DROP TABLE " + Tables.LABELS);
                createLabelsTable(db);
                createRunsTable(db);
                createRunSensorsTable(db);
                version = 9;
            }

            if (version == 9 && version < newVersion) {
                // Add last used columns.
                db.execSQL("ALTER TABLE " + Tables.PROJECTS + " ADD COLUMN " +
                        ProjectColumns.LAST_USED_TIME + " INTEGER");
                db.execSQL("ALTER TABLE " + Tables.EXPERIMENTS + " ADD COLUMN " +
                        ExperimentColumns.LAST_USED_TIME + " INTEGER");
                version = 10;
            }

            if (version == 10 && version < newVersion) {
                // Add experiment sensor layout table
                createExperimentSensorLayoutTable(db);
                version = 11;
            }

            if (version == 11 && version < newVersion) {
                // Add run index to runs table.
                db.execSQL("ALTER TABLE " + Tables.RUNS + " ADD COLUMN " +
                        RunsColumns.RUN_INDEX + " INTEGER");
                // Insert sentinel value for older runs.
                db.execSQL("UPDATE " + Tables.RUNS + " SET " + RunsColumns.RUN_INDEX + " = -1");
                version = 12;
            }

            if (version == 12 && version < newVersion) {
                // Add run archived state and title to the runs table.
                db.execSQL("ALTER TABLE " + Tables.RUNS + " ADD COLUMN " +
                        RunsColumns.TITLE + " TEXT");
                db.execSQL("ALTER TABLE " + Tables.RUNS + " ADD COLUMN " +
                        RunsColumns.ARCHIVED + " BOOLEAN");
                version = 13;
            }

            if (version == 13 && version < newVersion) {
                // Add SensorLayouts to the Runs Sensors table.
                db.execSQL("ALTER TABLE " + Tables.RUN_SENSORS + " ADD COLUMN " +
                        RunSensorsColumns.LAYOUT + " BLOB");
                db.execSQL("ALTER TABLE " + Tables.RUN_SENSORS + " ADD COLUMN " +
                        RunSensorsColumns.POSITION + " INTEGER");
                db.execSQL("UPDATE " + Tables.RUN_SENSORS + " SET " +
                        RunSensorsColumns.POSITION + " = -1");
                version = 14;
            }

            if (version == 14 && version < newVersion) {
                db.execSQL("ALTER TABLE " + Tables.LABELS + " ADD COLUMN " +
                        LabelColumns.VALUE + " BLOB");
                version = 15;
            }

            if (version == 15 && version < newVersion) {
                db.execSQL("ALTER TABLE " + Tables.RUNS + " ADD COLUMN " +
                        RunsColumns.AUTO_ZOOM_ENABLED + " BOOLEAN");
                version = 16;
            }

            if (version == 16 && version < newVersion) {
                db.execSQL("UPDATE " + Tables.RUNS + " SET " + RunsColumns.AUTO_ZOOM_ENABLED +
                        " = 1 WHERE " + RunsColumns.AUTO_ZOOM_ENABLED + " IS NULL");
                version = 17;
            }

            if (version == 17 && version < newVersion) {
                createSensorTriggersTable(db);
                version = 18;
            }

            if (version == 18 && version < newVersion) {
                createMyDevicesTable(db);
                version = 19;
            }

            if (version == 19 && version < newVersion) {
                db.execSQL("ALTER TABLE " + Tables.EXPERIMENT_SENSORS + " ADD COLUMN " +
                        ExperimentSensorColumns.INCLUDED + " INTEGER DEFAULT 1");
                version = 20;
            }

            if (version == 20 && version < newVersion) {
                // Projects are no longer used; need to tell the metadata manager to integrate that
                // data into the experiment.
                mUpgradeCallback.onMigrateProjectData(db);
                version = 21;
            }

            if (version == 21 && version < newVersion) {
                // Migrate experiment data into file-based system.
                mUpgradeCallback.onMigrateExperimentsToFiles(db);
                version = 22;
            }
        }

        private void createProjectsTable(SQLiteDatabase db) {
            db.execSQL("CREATE TABLE " + Tables.PROJECTS + " ("
                    + BaseColumns._ID + " INTEGER PRIMARY KEY AUTOINCREMENT,"
                    + ProjectColumns.PROJECT_ID + " TEXT NOT NULL, "
                    + ProjectColumns.TITLE + " TEXT, "
                    + ProjectColumns.DESCRIPTION + " TEXT, "
                    + ProjectColumns.COVER_PHOTO + " TEXT, "
                    + ProjectColumns.ARCHIVED + " BOOLEAN NOT NULL DEFAULT 0, "
                    + ProjectColumns.LAST_USED_TIME + " INTEGER, "
                    + "UNIQUE (" + ProjectColumns.PROJECT_ID + ") ON CONFLICT REPLACE)");
        }

        private void createLabelsTable(SQLiteDatabase db) {
            db.execSQL("CREATE TABLE " + Tables.LABELS + " ("
                    + BaseColumns._ID + " INTEGER PRIMARY KEY AUTOINCREMENT,"
                    + LabelColumns.TIMESTAMP + " INTEGER NOT NULL, "
                    + LabelColumns.EXPERIMENT_ID + " TEXT NOT NULL, "
                    + LabelColumns.TYPE + " TEXT NOT NULL, "
                    + LabelColumns.DATA + " TEXT, "
                    + LabelColumns.LABEL_ID + " TEXT NOT NULL, "
                    + LabelColumns.START_LABEL_ID + " TEXT,"
                    + LabelColumns.VALUE + " BLOB)");
        }

        private void createSensorsTable(SQLiteDatabase db) {
            db.execSQL("CREATE TABLE " + Tables.EXTERNAL_SENSORS + " ("
                    + BaseColumns._ID + " INTEGER PRIMARY KEY AUTOINCREMENT,"
                    + SensorColumns.TYPE + " TEXT NOT NULL,"
                    + SensorColumns.SENSOR_ID + " TEXT UNIQUE,"
                    + SensorColumns.NAME + " TEXT NOT NULL,"
                    + SensorColumns.CONFIG + " BLOB)");
        }

        private void createExperimentSensorsTable(SQLiteDatabase db) {
            db.execSQL("CREATE TABLE " + Tables.EXPERIMENT_SENSORS + " ("
                    + BaseColumns._ID + " INTEGER PRIMARY KEY AUTOINCREMENT,"
                    + ExperimentSensorColumns.SENSOR_TAG + " TEXT,"
                    + ExperimentSensorColumns.EXPERIMENT_ID + " TEXT,"
                    + ExperimentSensorColumns.INCLUDED + " INTEGER DEFAULT 1)");
        }

        private void createRunStatsTable(SQLiteDatabase db) {
            db.execSQL("CREATE TABLE " + Tables.RUN_STATS + " ("
                    + BaseColumns._ID + " INTEGER PRIMARY KEY AUTOINCREMENT,"
                    + RunStatsColumns.START_LABEL_ID + " TEXT,"
                    + RunStatsColumns.SENSOR_TAG + " TEXT,"
                    + RunStatsColumns.STAT_NAME + " TEXT,"
                    + RunStatsColumns.STAT_VALUE + " REAL, "
                    + "UNIQUE(" + RunStatsColumns.START_LABEL_ID + ","
                    + RunStatsColumns.SENSOR_TAG + "," + RunStatsColumns
                    .STAT_NAME + ") ON CONFLICT REPLACE)");
        }

        private void createRunsTable(SQLiteDatabase db) {
            db.execSQL("CREATE TABLE " + Tables.RUNS + " ("
                    + BaseColumns._ID + " INTEGER PRIMARY KEY AUTOINCREMENT,"
                    + RunsColumns.RUN_ID + " TEXT UNIQUE,"
                    + RunsColumns.RUN_INDEX + " INTEGER,"
                    + RunsColumns.TIMESTAMP + " INTEGER NOT NULL,"
                    + RunsColumns.TITLE + " TEXT,"
                    + RunsColumns.ARCHIVED + " BOOLEAN,"
                    + RunsColumns.AUTO_ZOOM_ENABLED + " BOOLEAN NOT NULL DEFAULT 1)");
        }

        private void createRunSensorsTable(SQLiteDatabase db) {
            db.execSQL("CREATE TABLE " + Tables.RUN_SENSORS + " ("
                    + BaseColumns._ID + " INTEGER PRIMARY KEY AUTOINCREMENT,"
                    + RunSensorsColumns.RUN_ID + " TEXT,"
                    + RunSensorsColumns.SENSOR_ID + " TEXT,"
                    + RunSensorsColumns.LAYOUT + " BLOB,"
                    + RunSensorsColumns.POSITION + " INTEGER,"
                    + "UNIQUE(" + RunSensorsColumns.RUN_ID + ","
                    + RunSensorsColumns.SENSOR_ID + "))");
        }

        private void createExperimentSensorLayoutTable(SQLiteDatabase db) {
            db.execSQL("CREATE TABLE " + Tables.EXPERIMENT_SENSOR_LAYOUT + " ("
                    + BaseColumns._ID + " INTEGER PRIMARY KEY AUTOINCREMENT,"
                    + ExperimentSensorLayoutColumns.EXPERIMENT_ID + " TEXT,"
                    + ExperimentSensorLayoutColumns.POSITION + " INTEGER,"
                    + ExperimentSensorLayoutColumns.LAYOUT + " BLOB,"
                    + "UNIQUE(" + ExperimentSensorLayoutColumns.EXPERIMENT_ID + ","
                    + ExperimentSensorLayoutColumns.POSITION + "))");
        }

        private void createSensorTriggersTable(SQLiteDatabase db) {
            db.execSQL("CREATE TABLE " + Tables.SENSOR_TRIGGERS + " ("
                    + BaseColumns._ID + " INTEGER PRIMARY KEY AUTOINCREMENT,"
                    + SensorTriggerColumns.TRIGGER_ID + " TEXT UNIQUE,"
                    + SensorTriggerColumns.EXPERIMENT_ID + " TEXT,"
                    + SensorTriggerColumns.LAST_USED_TIMESTAMP_MS + " INTEGER,"
                    + SensorTriggerColumns.SENSOR_ID + " TEXT,"
                    + SensorTriggerColumns.TRIGGER_INFORMATION + " BLOB)"
            );
        }

        private void createMyDevicesTable(SQLiteDatabase db) {
            db.execSQL("CREATE TABLE " + Tables.MY_DEVICES + " ("
                    + BaseColumns._ID + " INTEGER PRIMARY KEY AUTOINCREMENT,"
                    + MyDevicesColumns.DEVICE_ID + " TEXT NOT NULL, "
                    + "UNIQUE (" + MyDevicesColumns.DEVICE_ID + ") ON CONFLICT REPLACE)");
        }
    }

    private static final String STABLE_ID_CHARS = "abcdefghijklmnopqrstuvwxyz" +
            "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ";

    /**
     * Creates a stable ID of alphanumeric characters. This is more useful than autoincremented
     * row IDs from the database because those can get moved around due to account data
     * synchronization or sync adapter munging.
     *
     * @return a stable ID which is a random String of alphanumeric characters at the desired
     * length.
     */
    private String newStableId(int length) {
        Random random = new Random();
        StringBuilder builder = new StringBuilder(length);
        for (int index = 0; index < length; index++) {
            builder.append(STABLE_ID_CHARS.charAt(random.nextInt(STABLE_ID_CHARS.length())));
        }
        return builder.toString();
    }
}