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

package com.google.android.apps.forscience.whistlepunk.project.experiment;

import android.app.Fragment;
import android.app.TaskStackBuilder;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.design.widget.Snackbar;
import android.support.v4.app.NavUtils;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.PopupMenu;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.google.android.apps.forscience.javalib.Success;
import com.google.android.apps.forscience.whistlepunk.AccessibilityUtils;
import com.google.android.apps.forscience.whistlepunk.AddNoteDialog;
import com.google.android.apps.forscience.whistlepunk.AppSingleton;
import com.google.android.apps.forscience.whistlepunk.Appearances;
import com.google.android.apps.forscience.whistlepunk.ColorUtils;
import com.google.android.apps.forscience.whistlepunk.DataController;
import com.google.android.apps.forscience.whistlepunk.LoggingConsumer;
import com.google.android.apps.forscience.whistlepunk.MainActivity;
import com.google.android.apps.forscience.whistlepunk.NoteViewHolder;
import com.google.android.apps.forscience.whistlepunk.PanesActivity;
import com.google.android.apps.forscience.whistlepunk.PictureUtils;
import com.google.android.apps.forscience.whistlepunk.R;
import com.google.android.apps.forscience.whistlepunk.RelativeTimeTextView;
import com.google.android.apps.forscience.whistlepunk.RxDataController;
import com.google.android.apps.forscience.whistlepunk.SensorAppearance;
import com.google.android.apps.forscience.whistlepunk.StatsAccumulator;
import com.google.android.apps.forscience.whistlepunk.StatsList;
import com.google.android.apps.forscience.whistlepunk.WhistlePunkApplication;
import com.google.android.apps.forscience.whistlepunk.analytics.TrackerConstants;
import com.google.android.apps.forscience.whistlepunk.data.GoosciSensorLayout;
import com.google.android.apps.forscience.whistlepunk.filemetadata.Experiment;
import com.google.android.apps.forscience.whistlepunk.filemetadata.Label;
import com.google.android.apps.forscience.whistlepunk.filemetadata.Trial;
import com.google.android.apps.forscience.whistlepunk.filemetadata.TrialStats;
import com.google.android.apps.forscience.whistlepunk.metadata.CropHelper;
import com.google.android.apps.forscience.whistlepunk.metadata.GoosciLabel;
import com.google.android.apps.forscience.whistlepunk.metadata.GoosciPictureLabelValue;
import com.google.android.apps.forscience.whistlepunk.metadata.GoosciTrial;
import com.google.android.apps.forscience.whistlepunk.review.DeleteMetadataItemDialog;
import com.google.android.apps.forscience.whistlepunk.review.PinnedNoteAdapter;
import com.google.android.apps.forscience.whistlepunk.review.RunReviewActivity;
import com.google.android.apps.forscience.whistlepunk.review.RunReviewFragment;
import com.google.android.apps.forscience.whistlepunk.review.labels.LabelDetailsActivity;
import com.google.android.apps.forscience.whistlepunk.scalarchart.ChartController;
import com.google.android.apps.forscience.whistlepunk.scalarchart.ChartView;
import com.google.android.apps.forscience.whistlepunk.scalarchart.GraphOptionsController;
import com.google.android.apps.forscience.whistlepunk.scalarchart.ScalarDisplayOptions;
import com.google.android.apps.forscience.whistlepunk.sensorapi.StreamStat;

import java.lang.ref.WeakReference;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import io.reactivex.Completable;
import io.reactivex.functions.Consumer;

/**
 * A fragment to handle displaying Experiment details, runs and labels.
 */
public class ExperimentDetailsFragment extends Fragment
        implements DeleteMetadataItemDialog.DeleteDialogListener,
        NameExperimentDialog.OnExperimentTitleChangeListener {

    public static final String ARG_EXPERIMENT_ID = "experiment_id";
    public static final String ARG_DELETED_LABEL = "deleted_label";
    public static final String ARG_CREATE_TASK = "create_task";
    private static final String TAG = "ExperimentDetails";

    /**
     * Boolen extra for savedInstanceState with the state of includeArchived experiments.
     */
    private static final String EXTRA_INCLUDE_ARCHIVED = "includeArchived";

    private RecyclerView mDetails;
    private DetailsAdapter mAdapter;

    private String mExperimentId;
    private Experiment mExperiment;
    private ScalarDisplayOptions mScalarDisplayOptions;
    private boolean mIncludeArchived;
    private BroadcastReceiver mBroadcastReceiver;
    private Label mDeletedLabel;
    private String mActiveTrialId;
    private TextView mEmptyView;

    /**
     * Creates a new instance of this fragment.
     *
     * @param experimentId      Experiment ID to display
     * @param createTaskStack   If {@code true}, then navigating home requires building a task stack
     *                          up to the experiment list. If {@code false}, use the default
     *                          navigation.
     */
    public static ExperimentDetailsFragment newInstance(String experimentId,
            boolean createTaskStack, Label deletedLabel) {
        ExperimentDetailsFragment fragment = new ExperimentDetailsFragment();
        Bundle args = new Bundle();
        args.putString(ARG_EXPERIMENT_ID, experimentId);
        args.putBoolean(ARG_CREATE_TASK, createTaskStack);
        args.putParcelable(ARG_DELETED_LABEL, deletedLabel);
        fragment.setArguments(args);
        return fragment;
    }

    public ExperimentDetailsFragment() {
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mExperimentId = getArguments().getString(ARG_EXPERIMENT_ID);
        if (savedInstanceState == null) {
            // Only try to restore a deleted label the first time.
            mDeletedLabel = getArguments().getParcelable(ARG_DELETED_LABEL);
        }
        setHasOptionsMenu(true);
    }

    public void setExperimentId(String experimentId) {
        if (!Objects.equals(experimentId, mExperiment)) {
            mExperimentId = experimentId;
            if (isResumed()) {
                // If not resumed, wait to load until next resume!
                reloadWithoutScroll();
            }
        }
    }

    @Override
    public void onStart() {
        super.onStart();
        WhistlePunkApplication.getUsageTracker(getActivity()).trackScreenView(
                TrackerConstants.SCREEN_EXPERIMENT_DETAIL);
    }

    @Override
    public void onResume() {
        super.onResume();
        reloadWithoutScroll();
        // Create a BroadcastReceiver for when the stats get updated.
        mBroadcastReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String statsRunId = intent.getStringExtra(CropHelper.EXTRA_TRIAL_ID);
                mAdapter.onStatsBroadcastReceived(statsRunId, getDataController());
            }
        };
        CropHelper.registerStatsBroadcastReceiver(getActivity().getApplicationContext(),
                mBroadcastReceiver);
    }

    public void reloadWithoutScroll() {
        loadExperimentIfInitialized().subscribe(() -> {}, onReloadError());
    }

    @NonNull
    public Consumer<? super Throwable> onReloadError() {
        return LoggingConsumer.complain(TAG, "reload");
    }

    public Completable loadExperimentIfInitialized() {
        if (mExperimentId == null) {
            // We haven't initialized yet. Just wait for this to get called later during
            // initialization.
            return Completable.complete();
        }
        return RxDataController.getExperimentById(getDataController(), mExperimentId)
                               .doOnSuccess(experiment -> {
                                   if (experiment == null) {
                                       // This was deleted on us. Finish and return so we don't
                                       // try to load.
                                       getActivity().finish();
                                       return;
                                   }
                                   attachExperimentDetails(experiment);
                                   loadExperimentData(experiment);
                               })
                               .toCompletable();
    }

    @Override
    public void onPause() {
        if (mBroadcastReceiver != null) {
            CropHelper.unregisterBroadcastReceiver(getActivity().getApplicationContext(),
                    mBroadcastReceiver);
            mBroadcastReceiver = null;
        }
        super.onPause();
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean(EXTRA_INCLUDE_ARCHIVED, mIncludeArchived);
        mAdapter.onSaveInstanceState(outState);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_panes_experiment_details, container, false);

        AppCompatActivity activity = (AppCompatActivity) getActivity();
        ActionBar actionBar = activity.getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
            actionBar.setHomeButtonEnabled(true);
        }

        mDetails = (RecyclerView) view.findViewById(R.id.details_list);
        mDetails.setLayoutManager(new LinearLayoutManager(view.getContext(),
                LinearLayoutManager.VERTICAL, /* don't reverse layout */ false));
        mAdapter = new DetailsAdapter(this, savedInstanceState);
        mDetails.setAdapter(mAdapter);

        mEmptyView = (TextView) view.findViewById(R.id.empty_list);
        mEmptyView.setText(R.string.empty_experiment);
        mEmptyView.setCompoundDrawablesRelativeWithIntrinsicBounds(null, null, null,
                view.getResources().getDrawable(R.drawable.empty_run));

        // TODO: Because mScalarDisplayOptions are static, if the options are changed during the
        // time we are on this page it probably won't have an effect. Since graph options are
        // hidden from non-userdebug users, and not shown in the ExperimentDetails menu even when
        // enabled, this is OK for now.
        mScalarDisplayOptions = new ScalarDisplayOptions();
        GraphOptionsController graphOptionsController = new GraphOptionsController(getActivity());
        graphOptionsController.loadIntoScalarDisplayOptions(mScalarDisplayOptions, view);

        if (savedInstanceState != null) {
            mIncludeArchived = savedInstanceState.getBoolean(EXTRA_INCLUDE_ARCHIVED, false);
            getActivity().invalidateOptionsMenu();
        }

        return view;
    }

    public void loadExperimentData(final Experiment experiment) {
        boolean includeInvalidRuns = false;
        mAdapter.setScalarDisplayOptions(mScalarDisplayOptions);
        mAdapter.setData(experiment, experiment.getTrials(mIncludeArchived, includeInvalidRuns));
        if (mActiveTrialId != null) {
            mAdapter.addActiveRecording(experiment.getTrial(mActiveTrialId));
        }
    }

    public void onStartRecording(String trialId) {
        mActiveTrialId = trialId;
        if (mAdapter != null) {
            RxDataController.getTrial(getDataController(), mExperimentId, trialId)
                    .subscribe(t -> mAdapter.addActiveRecording(t));
        }
        if (getActivity() != null) {
            getActivity().invalidateOptionsMenu();
        }
        setHomeButtonState(true);

        // TODO: there has to be a cheaper way to make the feed scroll to the bottom
        reloadAndScrollToBottom();
    }

    public void reloadAndScrollToBottom() {
        loadExperimentIfInitialized().subscribe(() -> scrollToBottom(), onReloadError());
    }

    public void scrollToBottom() {
        if (mDetails != null) {
            mDetails.smoothScrollToPosition(mAdapter.getItemCount() - 1);
        }
    }

    public void onRecordingTrialUpdated(String trialId) {
        RxDataController.getTrial(getDataController(), mExperimentId, trialId).subscribe(t -> {
            mAdapter.updateActiveRecording(t);
            scrollToBottom();
        });
    }

    public void onStopRecording() {
        if (mActiveTrialId != null) {
            RxDataController.getTrial(getDataController(), mExperimentId, mActiveTrialId)
                    .subscribe(t -> mAdapter.onRecordingEnded(t));
            mActiveTrialId = null;
        }
        getActivity().invalidateOptionsMenu();
        setHomeButtonState(false);
    }

    // Sets the actionBar home button to opaque to indicate it is disabled.
    // Behavior is intercepted in onOptionsItemSelected when a recording is in progress
    private void setHomeButtonState(boolean disabled) {
        AppCompatActivity activity = (AppCompatActivity) getActivity();
        if (activity != null) {
            ActionBar actionBar = activity.getSupportActionBar();
            if (actionBar != null) {
                final Drawable upArrow = ContextCompat.getDrawable(activity,
                        R.drawable.ic_arrow_back_white_24dp);
                if (disabled) {
                    upArrow.setAlpha(
                            getResources().getInteger(R.integer.home_disabled_drawable_alpha));
                } else {
                    upArrow.setAlpha(
                            getResources().getInteger(R.integer.home_enabled_drawable_alpha));
                }
                actionBar.setHomeAsUpIndicator(upArrow);
            }
        }
    }

    public String getActiveRecordingId() {
        return mActiveTrialId;
    }

    private void attachExperimentDetails(Experiment experiment) {
        mExperiment = experiment;
        final View rootView = getView();
        if (rootView == null) {
            return;
        }

        setExperimentItemsOrder(experiment);

        if (mDeletedLabel != null) {
            onLabelDelete(mDeletedLabel);
            mDeletedLabel = null;
        }

        getActivity().setTitle(experiment.getDisplayTitle(getActivity()));

        Toolbar toolbar = (Toolbar) rootView.findViewById(R.id.toolbar);
        if (toolbar != null) {
            toolbar.setTitle(experiment.getDisplayTitle(getActivity()));
        }
    }

    private DataController getDataController() {
        return AppSingleton.getInstance(getActivity()).getDataController();
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);
        inflater.inflate(R.menu.menu_experiment_details, menu);
    }

    @Override
    public void onPrepareOptionsMenu(Menu menu) {
        menu.findItem(R.id.action_archive_experiment).setVisible(mExperiment != null &&
                !mExperiment.isArchived());
        menu.findItem(R.id.action_unarchive_experiment).setVisible(mExperiment != null &&
                mExperiment.isArchived());
        // Disable archive option when recording.
        menu.findItem(R.id.action_archive_experiment).setEnabled(!isRecording());
        menu.findItem(R.id.action_delete_experiment).setEnabled(mExperiment != null
                && mExperiment.isArchived());
        menu.findItem(R.id.action_include_archived).setVisible(!mIncludeArchived);
        menu.findItem(R.id.action_exclude_archived).setVisible(mIncludeArchived);
        menu.findItem(R.id.action_edit_experiment).setVisible(mExperiment != null &&
                !mExperiment.isArchived());
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int itemId = item.getItemId();
        if (itemId == android.R.id.home) {
            // Disable the home button functionality when recording is active
            // Set with appearance in setHomeButtonState
            if (isRecording()) {
                return true;
            }
            displayNamePromptOrGoUp();
            return true;
        } else if (itemId == R.id.action_edit_experiment) {
            UpdateExperimentActivity.launch(getActivity(), mExperimentId);
            return true;
        } else if (itemId == R.id.action_archive_experiment
                || itemId == R.id.action_unarchive_experiment) {
            setExperimentArchived(item.getItemId() == R.id.action_archive_experiment);
            return true;
        } else if (itemId == R.id.action_include_archived) {
            mIncludeArchived = true;
            loadExperimentData(mExperiment);
            getActivity().invalidateOptionsMenu();
            return true;
        } else if (itemId == R.id.action_exclude_archived) {
            mIncludeArchived = false;
            loadExperimentData(mExperiment);
            getActivity().invalidateOptionsMenu();
            return true;
        } else if (itemId == R.id.action_delete_experiment) {
            confirmDeleteExperiment();
        }
        return super.onOptionsItemSelected(item);
    }

    // Prompt the user to name the experiment if they haven't yet.
    private void displayNamePromptOrGoUp() {
        if (!TextUtils.isEmpty(mExperiment.getTitle()) || mExperiment.isArchived()) {
            goToExperimentList();
            return;
        }
        displayNamePrompt();
    }

    private void displayNamePrompt() {
        // The experiment needs a title still.
        NameExperimentDialog dialog = NameExperimentDialog.newInstance(mExperimentId);
        dialog.show(getChildFragmentManager(), NameExperimentDialog.TAG);
    }

    @Override
    public void onTitleChangedFromDialog() {
        // If it was saved successfully, we can just go up to the parent.
        WhistlePunkApplication.getUsageTracker(getActivity())
                .trackEvent(TrackerConstants.CATEGORY_EXPERIMENTS,
                        TrackerConstants.ACTION_EDITED,
                        TrackerConstants.LABEL_EXPERIMENT_DETAIL, 0);
        goToExperimentList();
    }

    public boolean handleOnBackPressed() {
        if (isRecording()) {
            Intent intent = new Intent(Intent.ACTION_MAIN);
            intent.addCategory(Intent.CATEGORY_HOME);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
            return true;
        }
        if (TextUtils.isEmpty(mExperiment.getTitle()) && !mExperiment.isArchived()) {
            displayNamePrompt();
            // We are handling this.
            return true;
        }
        if (getActivity().isTaskRoot()) {
            goToExperimentList();
            return true;
        }
        // The activity can handle it normally.
        return false;
    }

    private boolean isRecording() {
        return mActiveTrialId != null;
    }

    private void goToExperimentList() {
        Intent upIntent = NavUtils.getParentActivityIntent(getActivity());
        if (upIntent == null) {
            // This is cheating a bit.  Currently, upIntent has only been observed to be null
            // when we're using panes mode, so here we just assume usePanes==true.
            Intent intent =
                    MainActivity.launchIntent(getActivity(), R.id.navigation_item_experiments,
                            true);
            getActivity().startActivity(intent);
            getActivity().finish();
            return;
        }
        if (NavUtils.shouldUpRecreateTask(getActivity(), upIntent) || getArguments().getBoolean(
                ARG_CREATE_TASK, false)) {
            upIntent.putExtra(MainActivity.ARG_SELECTED_NAV_ITEM_ID,
                    R.id.navigation_item_experiments);
            upIntent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            // TODO: Transition animation
            TaskStackBuilder.create(getActivity()).addNextIntentWithParentStack(upIntent)
                    .startActivities();
        } else {
            NavUtils.navigateUpTo(getActivity(), upIntent);
        }
    }

    private void setExperimentArchived(final boolean archived) {
        mExperiment.setArchived(archived);
        getDataController().updateExperiment(mExperimentId, new LoggingConsumer<Success>(
                TAG, "Editing experiment") {
            @Override
            public void success(Success value) {
                setExperimentItemsOrder(mExperiment);
                ((PanesActivity) getActivity()).onArchivedStateChanged(mExperiment);
                // Reload the data to refresh experiment item and insert it if necessary.
                loadExperimentData(mExperiment);
                WhistlePunkApplication.getUsageTracker(getActivity())
                        .trackEvent(TrackerConstants.CATEGORY_EXPERIMENTS,
                                archived ? TrackerConstants.ACTION_ARCHIVE :
                                        TrackerConstants.ACTION_UNARCHIVE,
                                TrackerConstants.LABEL_EXPERIMENT_DETAIL, 0);

                Snackbar bar = AccessibilityUtils.makeSnackbar(getView(),
                        getActivity().getResources().getString(
                                archived ? R.string.archived_experiment_message : R.string
                                        .unarchived_experiment_message),
                        Snackbar.LENGTH_LONG);

                if (archived) {
                    bar.setAction(R.string.action_undo, v -> setExperimentArchived(false));
                }
                bar.show();
                getActivity().invalidateOptionsMenu();
            }
        });
    }

    private void setExperimentItemsOrder(Experiment experiment) {
        mAdapter.setReverseLayout(!experiment.isArchived());
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == PictureUtils.REQUEST_TAKE_PHOTO) {
            Fragment dialog = getChildFragmentManager().findFragmentByTag(AddNoteDialog.TAG);
            if (dialog != null) {
                dialog.onActivityResult(requestCode, resultCode, data);
            }
        }
    }

    void deleteLabel(Label label) {
        mExperiment.deleteLabel(label, getActivity());
        RxDataController.updateExperiment(getDataController(), mExperiment)
                .subscribe(() -> onLabelDelete(label));
    }

    private void onLabelDelete(final Label item) {
        final DataController dc = getDataController();
        Snackbar bar = AccessibilityUtils.makeSnackbar(getView(), getActivity().getResources()
                .getString(R.string.snackbar_note_deleted),
                Snackbar.LENGTH_LONG);

        // On undo, re-add the item to the database and the pinned note list.
        bar.setAction(R.string.snackbar_undo, new View.OnClickListener() {
            boolean mUndone = false;
            @Override
            public void onClick(View v) {
                if (mUndone) {
                    return;
                }
                mUndone = true;
                final Label label = Label.copyOf(item);
                mExperiment.addLabel(label);
                dc.updateExperiment(mExperimentId, new LoggingConsumer<Success>(TAG,
                        "re-add deleted label") {
                    @Override
                    public void success(Success value) {
                        // TODO: Somehow re-add the deleted picture here.
                        mAdapter.insertNote(label);
                        WhistlePunkApplication.getUsageTracker(getActivity())
                                .trackEvent(TrackerConstants.CATEGORY_NOTES,
                                        TrackerConstants.ACTION_DELETE_UNDO,
                                        TrackerConstants.LABEL_EXPERIMENT_DETAIL,
                                        TrackerConstants.getLabelValueType(item));
                    }
                });
            }
        });
        bar.show();

        mAdapter.deleteNote(item);

        WhistlePunkApplication.getUsageTracker(getActivity())
                .trackEvent(TrackerConstants.CATEGORY_NOTES,
                        TrackerConstants.ACTION_DELETED,
                        TrackerConstants.LABEL_EXPERIMENT_DETAIL,
                        TrackerConstants.getLabelValueType(item));
    }

    private void setTrialArchived(Trial trial, boolean toArchive) {
        trial.setArchived(toArchive);
        mExperiment.updateTrial(trial);
        RxDataController.updateExperiment(getDataController(), mExperiment).subscribe(() -> {
            mAdapter.onTrialArchivedStateChanged(trial, mIncludeArchived);
            WhistlePunkApplication.getUsageTracker(getActivity())
                    .trackEvent(TrackerConstants.CATEGORY_RUNS,
                            toArchive ? TrackerConstants.ACTION_ARCHIVE :
                                    TrackerConstants.ACTION_UNARCHIVE,
                            TrackerConstants.LABEL_EXPERIMENT_DETAIL, 0);
        });
    }

    private void deleteTrial(Trial trial) {
        DeleteMetadataItemDialog dialog = DeleteMetadataItemDialog.newInstance(
                R.string.delete_run_dialog_title, R.string.run_review_delete_confirm,
                trial.getTrialId());
        dialog.show(getChildFragmentManager(), DeleteMetadataItemDialog.TAG);
    }

    private void confirmDeleteExperiment() {
        DeleteMetadataItemDialog dialog = DeleteMetadataItemDialog.newInstance(
                R.string.delete_experiment_dialog_title, R.string.delete_experiment_dialog_message);
        dialog.show(getChildFragmentManager(), DeleteMetadataItemDialog.TAG);
    }

    @Override
    public void requestDelete(Bundle extras) {
        String trialId = extras.getString(DeleteMetadataItemDialog.KEY_ITEM_ID);
        if (!TextUtils.isEmpty(trialId)) {
            // Then we were trying to delete a trial.
            mExperiment.deleteTrial(mExperiment.getTrial(trialId), getActivity());
            RxDataController.updateExperiment(getDataController(), mExperiment).subscribe(() ->
                    mAdapter.onTrialDeleted(trialId));
        } else {
            getDataController().deleteExperiment(mExperiment,
                    new LoggingConsumer<Success>(TAG, "Delete experiment") {
                @Override
                public void success(Success value) {
                    WhistlePunkApplication.getUsageTracker(getActivity())
                            .trackEvent(TrackerConstants.CATEGORY_EXPERIMENTS,
                                    TrackerConstants.ACTION_DELETED,
                                    TrackerConstants.LABEL_EXPERIMENT_DETAIL,
                                    0);
                    getActivity().finish();
                }
            });
        }
    }

    public String getExperimentId() {
        return mExperimentId;
    }

    public static class DetailsAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

        private static final String KEY_SAVED_SENSOR_INDICES = "savedSensorIndices";

        private static final int VIEW_TYPE_EXPERIMENT_ARCHIVED = 0;
        static final int VIEW_TYPE_EXPERIMENT_TEXT_LABEL = 1;
        static final int VIEW_TYPE_EXPERIMENT_PICTURE_LABEL = 2;
        static final int VIEW_TYPE_RUN_CARD = 3;
        static final int VIEW_TYPE_EXPERIMENT_TRIGGER_LABEL = 4;
        static final int VIEW_TYPE_SNAPSHOT_LABEL = 5;
        static final int VIEW_TYPE_UNKNOWN_LABEL = 6;
        static final int VIEW_TYPE_RECORDING = 7;

        private final WeakReference<ExperimentDetailsFragment> mParentReference;
        private Experiment mExperiment;
        private List<ExperimentDetailItem> mItems;
        private List<Integer> mSensorIndices = null;
        private boolean mHasRunsOrLabels;
        private ScalarDisplayOptions mScalarDisplayOptions;
        private boolean mReverseOrder = true;

        DetailsAdapter(ExperimentDetailsFragment parent, Bundle savedInstanceState) {
            mItems = new ArrayList<>();
            mParentReference = new WeakReference<ExperimentDetailsFragment>(parent);
            if (savedInstanceState != null &&
                    savedInstanceState.containsKey(KEY_SAVED_SENSOR_INDICES)) {
                mSensorIndices = savedInstanceState.getIntegerArrayList(KEY_SAVED_SENSOR_INDICES);
            }
        }

        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view;
            LayoutInflater inflater =  LayoutInflater.from(parent.getContext());
            if (viewType == VIEW_TYPE_EXPERIMENT_TEXT_LABEL ||
                    viewType == VIEW_TYPE_EXPERIMENT_PICTURE_LABEL ||
                    viewType == VIEW_TYPE_EXPERIMENT_TRIGGER_LABEL ||
                    viewType == VIEW_TYPE_SNAPSHOT_LABEL) {
                view = inflater.inflate(R.layout.exp_card_pinned_note, parent, false);
                return new NoteViewHolder(view);
            } else if (viewType == VIEW_TYPE_RECORDING) {
                view = inflater.inflate(R.layout.exp_card_recording, parent, false);
                return new RecordingViewHolder(view);
            } else if (viewType == VIEW_TYPE_EXPERIMENT_ARCHIVED) {
                view = inflater.inflate(R.layout.metadata_archived, parent, false);
            } else {
                view = inflater.inflate(R.layout.exp_card_run, parent, false);
            }
            return new DetailsViewHolder(view, viewType);
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
            ExperimentDetailItem item = mItems.get(position);
            int type = item.getViewType();
            if (type == VIEW_TYPE_RUN_CARD) {
                setupTrialHeader((DetailsViewHolder) holder, item);
                setupCaption((DetailsViewHolder) holder, item.getTrial().getCaptionText());
                bindRun((DetailsViewHolder) holder, item);
                return;
            } else if (type == VIEW_TYPE_RECORDING) {
                bindRecording((RecordingViewHolder) holder, item);
                return;
            }
            boolean isPictureLabel = type == VIEW_TYPE_EXPERIMENT_PICTURE_LABEL;
            boolean isTextLabel = type == VIEW_TYPE_EXPERIMENT_TEXT_LABEL;
            boolean isTriggerLabel = type == VIEW_TYPE_EXPERIMENT_TRIGGER_LABEL;
            boolean isSnapshotLabel = type == VIEW_TYPE_SNAPSHOT_LABEL;
            if (isPictureLabel || isTextLabel || isTriggerLabel || isSnapshotLabel) {
                final Label label = mItems.get(position).getLabel();

                NoteViewHolder noteViewHolder = (NoteViewHolder) holder;
                noteViewHolder.setNote(label, mExperiment.getExperimentId());

                // Work specific to ExperimentDetails
                noteViewHolder.relativeTimeView.setTime(label.getTimeStamp());
                noteViewHolder.durationText.setVisibility(View.GONE);

                noteViewHolder.menuButton.setOnClickListener(view -> {
                    Context context = noteViewHolder.menuButton.getContext();
                    PopupMenu popup = new PopupMenu(context, noteViewHolder.menuButton);
                    setupNoteMenu(item, popup);
                    popup.show();
                });
                holder.itemView.setOnClickListener(view -> {
                    if (!isRecording()) {
                        // Can't click into details pages when recording.
                        LabelDetailsActivity.launchFromExpDetails(holder.itemView.getContext(),
                                mExperiment.getExperimentId(), label);
                    }
                });
            }
            if (type == VIEW_TYPE_EXPERIMENT_ARCHIVED) {
                View archivedIndicator = holder.itemView.findViewById(R.id.archived_indicator);
                archivedIndicator.setVisibility(
                        mExperiment.isArchived() ? View.VISIBLE : View.GONE);
            }
        }

        private boolean isRecording() {
            return mParentReference.get() != null &&
                    mParentReference.get().getActiveRecordingId() != null;
        }

        private void setupTrialHeader(DetailsViewHolder holder, final ExperimentDetailItem item) {
            holder.timeHeader.setTime(item.getViewType() == VIEW_TYPE_RUN_CARD ?
                    item.getTrial().getFirstTimestamp() : item.getLabel().getTimeStamp());

            ColorUtils.colorDrawable(holder.captionIcon.getContext(),
                    holder.captionIcon.getDrawable(), R.color.text_color_light_grey);
            ColorUtils.colorDrawable(holder.menuButton.getContext(),
                    holder.menuButton.getDrawable(), R.color.text_color_light_grey);

            holder.menuButton.setOnClickListener(view -> {
                Context context = holder.menuButton.getContext();
                PopupMenu popup = new PopupMenu(context, holder.menuButton);
                setupTrialMenu(item, popup);
                popup.show();
            });
        }

        private void setupCaption(DetailsViewHolder holder, String caption) {
            // This is temporarily removed per b/65063919 but planned to be re-enabled later.
            holder.captionView.setVisibility(View.GONE);
            holder.captionIcon.setVisibility(View.GONE);

            /*
            if (!TextUtils.isEmpty(caption)) {
                holder.captionView.setVisibility(View.VISIBLE);
                holder.captionTextView.setText(caption);
                holder.captionIcon.setVisibility(View.GONE);
            } else {
                holder.captionView.setVisibility(View.GONE);
                holder.captionIcon.setVisibility(View.VISIBLE);
            }*/
        }

        private void setupTrialMenu(ExperimentDetailItem item, PopupMenu popup) {
            popup.getMenuInflater().inflate(R.menu.menu_experiment_trial, popup.getMenu());
            boolean archived = item.getTrial().isArchived();
            popup.getMenu().findItem(R.id.menu_item_archive).setVisible(!archived);
            popup.getMenu().findItem(R.id.menu_item_unarchive).setVisible(archived);
            popup.getMenu().findItem(R.id.menu_item_delete).setEnabled(archived);
            popup.setOnMenuItemClickListener(menuItem -> {
                if (menuItem.getItemId() == R.id.menu_item_archive) {
                    mParentReference.get().setTrialArchived(item.getTrial(), true);
                    return true;
                } else if (menuItem.getItemId() == R.id.menu_item_unarchive) {
                    mParentReference.get().setTrialArchived(item.getTrial(), false);
                    return true;
                } else if (menuItem.getItemId() == R.id.menu_item_delete) {
                    if (mParentReference.get() != null) {
                        mParentReference.get().deleteTrial(item.getTrial());
                    }
                    return true;
                }
                return false;
            });
        }

        private void setupNoteMenu(ExperimentDetailItem item, PopupMenu popup) {
            popup.getMenuInflater().inflate(R.menu.menu_experiment_note, popup.getMenu());
            popup.setOnMenuItemClickListener(menuItem -> {
                if (menuItem.getItemId() == R.id.btn_delete_note) {
                    if (mParentReference.get() != null) {
                        mParentReference.get().deleteLabel(item.getLabel());
                    }
                    return true;
                }
                return false;
            });
        }

        public void deleteNote(Label label) {
            int position = findLabelIndex(label);
            if (position == -1) {
                return;
            }
            mItems.remove(position);
            updateEmptyView();
            notifyItemRemoved(position);
        }

        private int findLabelIndex(Label label) {
            int expectedViewType = label.getType() == GoosciLabel.Label.TEXT ?
                    VIEW_TYPE_EXPERIMENT_TEXT_LABEL : label.getType() == GoosciLabel.Label.PICTURE ?
                    VIEW_TYPE_EXPERIMENT_PICTURE_LABEL :
                    label.getType() == GoosciLabel.Label.SNAPSHOT ? VIEW_TYPE_SNAPSHOT_LABEL :
                            VIEW_TYPE_EXPERIMENT_TRIGGER_LABEL;
            int position = -1;
            int size = mItems.size();
            for (int i = 0; i < size; i++) {
                ExperimentDetailItem item = mItems.get(i);
                if (item.getViewType() == expectedViewType) {
                    if (TextUtils.equals(label.getLabelId(), item.getLabel().getLabelId())) {
                        position = i;
                        break;
                    }
                }
            }
            return position;
        }

        public void onTrialArchivedStateChanged(Trial trial, boolean includeArchived) {
            int position = findTrialIndex(trial.getTrialId());
            if (position != -1) {
                if (includeArchived) {
                    notifyItemChanged(position);
                } else {
                    // It shouldn't be in the list any more.
                    mItems.remove(position);
                    notifyItemRemoved(position);
                    updateEmptyView();
                }
            }
        }

        public void onTrialDeleted(String trialId) {
            int position = findTrialIndex(trialId);
            if (position != -1) {
                mItems.remove(position);
                notifyItemRemoved(position);
            }
        }

        private int findTrialIndex(String trialId) {
            for (int i = 0; i < mItems.size(); i++) {
                ExperimentDetailItem item = mItems.get(i);
                if (item.getViewType() == VIEW_TYPE_RUN_CARD ||
                        item.getViewType() == VIEW_TYPE_RECORDING) {
                    if (TextUtils.equals(item.getTrial().getTrialId(), trialId)) {
                        return i;
                    }
                }
            }
            return -1;
        }

        public void insertNote(Label label) {
            int size = mItems.size();
            long timestamp = label.getTimeStamp();
            boolean inserted = false;
            mHasRunsOrLabels = true;
            for (int i = 0; i < size; i++) {
                ExperimentDetailItem item = mItems.get(i);
                if (item.getViewType() == VIEW_TYPE_EXPERIMENT_ARCHIVED) {
                    continue;
                }
                if (timestamp > item.getTimestamp()) {
                    mItems.add(i, new ExperimentDetailItem(label));
                    notifyItemInserted(i);
                    inserted = true;
                    break;
                }
            }
            if (!inserted) {
                mItems.add(size, new ExperimentDetailItem(label));
                notifyItemInserted(size);
            }
            mParentReference.get().mEmptyView.setVisibility(View.GONE);
        }

        @Override
        public int getItemCount() {
            return mItems.size();
        }

        @Override
        public int getItemViewType(int position) {
            return mItems.get(position).getViewType();
        }

        public void setScalarDisplayOptions(ScalarDisplayOptions scalarDisplayOptions) {
            mScalarDisplayOptions = scalarDisplayOptions;
        }

        public void setData(Experiment experiment, List<Trial> trials) {
            mHasRunsOrLabels = false;
            mExperiment = experiment;
            // TODO: compare data and see if anything has changed. If so, don't reload at all.
            mItems.clear();
            // As a safety check, if mSensorIndices is not the same size as the run list,
            // just ignore it.
            if (mSensorIndices != null && mSensorIndices.size() != trials.size()) {
                mSensorIndices = null;
            }
            int i = 0;
            String activeTrialId = mParentReference.get().getActiveRecordingId();
            for (Trial trial : trials) {
                ExperimentDetailItem item = new ExperimentDetailItem(trial, mScalarDisplayOptions,
                        TextUtils.equals(activeTrialId, trial.getTrialId()));
                item.setSensorTagIndex(mSensorIndices != null ? mSensorIndices.get(i++) : 0);
                mItems.add(item);
                mHasRunsOrLabels = true;
            }
            for (Label label : experiment.getLabels()) {
                mItems.add(new ExperimentDetailItem(label));
                mHasRunsOrLabels = true;
            }
            sortItems();

            if (experiment.isArchived()) {
                mItems.add(0, new ExperimentDetailItem(VIEW_TYPE_EXPERIMENT_ARCHIVED));
            }

            mParentReference.get().mEmptyView.setVisibility(mHasRunsOrLabels ?
                    View.GONE : View.VISIBLE);

            notifyDataSetChanged();
        }

        /**
         * Checks to see if we have any labels or runs. If so, hides the empty view. Otherwise,
         * add the empty view at the right location.
         */
        private void updateEmptyView() {
            boolean hasRunsOrLabels = false;

            final int count = mItems.size();
            for (int index = 0; index < count; ++index) {
                int viewType = mItems.get(index).getViewType();
                switch (viewType) {
                    // Most view types count as runs or labels.
                    // However, the archived view type does not.
                    case VIEW_TYPE_RUN_CARD:
                    case VIEW_TYPE_EXPERIMENT_PICTURE_LABEL:
                    case VIEW_TYPE_EXPERIMENT_TEXT_LABEL:
                    case VIEW_TYPE_SNAPSHOT_LABEL:
                    case VIEW_TYPE_RECORDING:
                    case VIEW_TYPE_EXPERIMENT_TRIGGER_LABEL:
                        hasRunsOrLabels = true;
                        break;
                }

                if (hasRunsOrLabels) {
                    // Don't need to look anymore.
                    break;
                }
            }

            mHasRunsOrLabels = hasRunsOrLabels;
            mParentReference.get().mEmptyView.setVisibility(hasRunsOrLabels ? View.GONE :
                    View.VISIBLE);

        }

        void bindRun(final DetailsViewHolder holder, final ExperimentDetailItem item) {
            final Trial trial = item.getTrial();
            final Context applicationContext = holder.itemView.getContext().getApplicationContext();
            holder.setRunId(trial.getTrialId());
            String title = trial.getTitleWithDuration(applicationContext);
            holder.runTitle.setText(title);
            holder.cardView.setTag(R.id.run_title_text, trial.getTrialId());

            holder.itemView.findViewById(R.id.content).setAlpha(
                    applicationContext.getResources().getFraction(trial.isArchived() ?
                        R.fraction.metadata_card_archived_alpha :
                        R.fraction.metadata_card_alpha, 1, 1));
            View archivedIndicator = holder.itemView.findViewById(R.id.archived_indicator);
            archivedIndicator.setVisibility(trial.isArchived() ? View.VISIBLE :
                    View.GONE);
            if (trial.isArchived()) {
                holder.runTitle.setContentDescription(applicationContext.getResources().getString(
                        R.string.archived_content_description, title));
            }

            holder.noteHolder.removeAllViews();
            if (trial.getLabelCount() > 0) {
                // Load the first two labels
                holder.noteHolder.setVisibility(View.VISIBLE);
                loadLabelIntoHolder(trial.getLabels().get(0), trial.getFirstTimestamp(),
                        holder.noteHolder);
                if (trial.getLabelCount() > 1) {
                    loadLabelIntoHolder(trial.getLabels().get(1), trial.getFirstTimestamp(),
                            holder.noteHolder);
                }
                if (trial.getLabelCount() > 2) {
                    // Show the "load more" link
                    loadLearnMoreIntoHolder(holder.noteHolder, item.getTrial().getTrialId());
                }
            }
            if (!trial.isValid()) {
                removeSensorData(holder);
            } else if (trial.getSensorIds().size() > 0) {
                holder.cardView.setOnClickListener(createRunClickListener(
                        item.getSensorTagIndex()));
                loadSensorData(applicationContext, holder, item);
                holder.sensorNext.setOnClickListener(v -> {
                    //Sometimes we tap the button before it can disable so return if the button
                    //should be disabled.
                    if (item.getSensorTagIndex() >= item.getTrial().getSensorIds().size() - 1)
                        return;
                    item.setSensorTagIndex(item.getSensorTagIndex() + 1);
                    loadSensorData(applicationContext, holder, item);
                    GoosciSensorLayout.SensorLayout layout = item.getSelectedSensorLayout();
                    holder.cardView.setOnClickListener(createRunClickListener(
                            item.getSensorTagIndex()));
                    holder.setSensorId(layout.sensorId);
                });
                holder.sensorPrev.setOnClickListener(v -> {
                    // TODO: reduce duplication with next listener above?
                    //Sometimes we tap the button before it can disable so return if the button
                    //should be disabled.
                    if (item.getSensorTagIndex() == 0)
                        return;
                    item.setSensorTagIndex(item.getSensorTagIndex() - 1);
                    loadSensorData(applicationContext, holder, item);
                    GoosciSensorLayout.SensorLayout layout = item.getSelectedSensorLayout();
                    holder.cardView.setOnClickListener(createRunClickListener(
                            item.getSensorTagIndex()));
                    holder.setSensorId(layout.sensorId);
                });
            } else {
                removeSensorData(holder);
            }
        }

        private void bindRecording(RecordingViewHolder holder, ExperimentDetailItem item) {
            Context context = holder.cardView.getContext();
            holder.title.setText(context.getResources().getString(R.string.card_recording_title,
                    item.getTrial().getTitle(context)));
            holder.noteHolder.removeAllViews();
            if (item.getTrial().getLabelCount() > 0) {
                for (int i = 0; i < item.getTrial().getLabelCount(); i++) {
                    loadLabelIntoHolder(item.getTrial().getLabels().get(i),
                            item.getTrial().getFirstTimestamp(), holder.noteHolder);
                }
            }
        }

        private void loadLabelIntoHolder(Label label, long trialStartTime, ViewGroup noteHolder) {
            // Add labels
            // TODO: Combine with code in NoteViewHolder?
            // most of this is not duplicated since NoteViewHolder also deals with menu / caption
            // as well as click listeners.
            ViewGroup noteView = (ViewGroup) LayoutInflater.from(noteHolder.getContext()).inflate(
                    R.layout.exp_card_pinned_note_content, null, false);

            // TODO: Is it more efficient for Android to have a second XML file so I don't have to
            // show & hide all this stuff?
            noteView.findViewById(R.id.relative_run_time_text).setVisibility(View.VISIBLE);
            noteView.findViewById(R.id.top_divider).setVisibility(View.VISIBLE);
            noteView.findViewById(R.id.heading_section).setVisibility(View.GONE);
            ((TextView) noteView.findViewById(R.id.relative_run_time_text)).setText(
                    PinnedNoteAdapter.getNoteTimeText(label.getTimeStamp(), trialStartTime));
            String captionText = label.getCaptionText();
            if (!TextUtils.isEmpty(captionText)) {
                noteView.findViewById(R.id.caption_section).setVisibility(View.VISIBLE);
                ((TextView) noteView.findViewById(R.id.caption)).setText(captionText);
            } else {
                noteView.findViewById(R.id.caption_section).setVisibility(View.GONE);
            }

            if (label.getType() == GoosciLabel.Label.TEXT) {
                ((TextView) noteView.findViewById(R.id.note_text)).setText(
                        label.getTextLabelValue().text);
            } else {
                noteView.findViewById(R.id.note_text).setVisibility(View.GONE);
            }

            if (label.getType() == GoosciLabel.Label.PICTURE) {
                GoosciPictureLabelValue.PictureLabelValue labelValue = label.getPictureLabelValue();
                noteView.findViewById(R.id.note_image).setVisibility(View.VISIBLE);
                PictureUtils.loadExperimentImage(noteView.getContext(),
                        (ImageView) noteView.findViewById(R.id.note_image),
                        mExperiment.getExperimentId(), labelValue.filePath);
            }

            if (label.getType() != GoosciLabel.Label.SENSOR_TRIGGER &&
                    label.getType() != GoosciLabel.Label.SNAPSHOT) {
                noteView.findViewById(R.id.snapshot_values_list).setVisibility(View.GONE);
            } else {
                if (label.getType() == GoosciLabel.Label.SENSOR_TRIGGER) {
                    NoteViewHolder.loadTriggerIntoList((ViewGroup)
                            noteView.findViewById(R.id.snapshot_values_list), label);
                }

                if (label.getType() == GoosciLabel.Label.SNAPSHOT) {
                    NoteViewHolder.loadSnapshotsIntoList((ViewGroup)
                            noteView.findViewById(R.id.snapshot_values_list), label);
                }
            }

            noteHolder.addView(noteView);
        }

        private void loadLearnMoreIntoHolder(ViewGroup noteHolder, String runId) {
            LayoutInflater.from(noteHolder.getContext()).inflate(R.layout.load_more_notes_button,
                    noteHolder);
            // TODO: Jump straight to the notes section.
            noteHolder.findViewById(R.id.load_more_btn).setOnClickListener(
                    view -> RunReviewActivity.launch(noteHolder.getContext(), runId,
                            mExperiment.getExperimentId(), 0 /* sensor index deprecated */,
                            false /* from record */, false /* create task */, null)
            );
        }

        private void removeSensorData(DetailsViewHolder holder) {
            holder.sensorName.setText("");
            holder.setSensorId(null);
            setIndeterminateSensorData(holder);
        }

        private View.OnClickListener createRunClickListener(final int selectedSensorIndex) {
            return v -> {
                if (!isRecording()) {
                    // Can't click into details pages when recording.
                    String runId = (String) v.getTag(R.id.run_title_text);
                    RunReviewActivity.launch(v.getContext(), runId, mExperiment.getExperimentId(),
                            selectedSensorIndex, false /* from record */, false /* create task */,
                            null);
                }
            };
        }

        private void setIndeterminateSensorData(DetailsViewHolder holder) {
            holder.statsLoadStatus = DetailsViewHolder.STATS_LOAD_STATUS_LOADING;
            holder.statsList.clearStats();
        }

        private void loadSensorData(Context appContext, final DetailsViewHolder holder,
                                    final ExperimentDetailItem item) {
            final Trial trial = item.getTrial();
            final String sensorId = trial.getSensorIds().get(item.getSensorTagIndex());

            final SensorAppearance appearance = AppSingleton.getInstance(appContext)
                    .getSensorAppearanceProvider()
                    .getAppearance(sensorId);
            final NumberFormat numberFormat = appearance.getNumberFormat();
            holder.sensorName.setText(Appearances.getSensorDisplayName(appearance, appContext));
            final GoosciSensorLayout.SensorLayout sensorLayout = item.getSelectedSensorLayout();
            int color = appContext.getResources().getIntArray(
                    R.array.graph_colors_array)[sensorLayout.colorIndex];
            Appearances.applyDrawableToImageView(appearance.getIconDrawable(appContext),
                    holder.sensorImage, color);

            boolean hasNextButton = item.getSensorTagIndex() < trial.getSensorIds().size() - 1;
            boolean hasPrevButton = item.getSensorTagIndex() > 0;
            holder.sensorPrev.setVisibility(hasPrevButton ? View.VISIBLE : View.INVISIBLE);
            holder.sensorNext.setVisibility(hasNextButton ? View.VISIBLE : View.INVISIBLE);
            if (hasNextButton) {
                RunReviewFragment.updateContentDescription(holder.sensorNext,
                        R.string.run_review_switch_sensor_btn_next, item.getNextSensorId(),
                        appContext);
            }
            if (hasPrevButton) {
                RunReviewFragment.updateContentDescription(holder.sensorPrev,
                        R.string.run_review_switch_sensor_btn_prev, item.getPrevSensorId(),
                        appContext);
            }

            final TrialStats stats = trial.getStatsForSensor(sensorId);
            if (stats == null) {
                throw new IllegalArgumentException("Invalid trial: " + trial);
            }
            holder.statsList.updateColor(color);
            if (!stats.statsAreValid()) {
                holder.statsList.clearStats();
            } else {
                List<StreamStat> streamStats = new StatsAccumulator.StatsDisplay(numberFormat)
                        .updateStreamStats(stats);
                holder.statsList.updateStats(streamStats);
            }

            // Load sensor readings into a chart.
            final ChartController chartController = item.getChartController();
            chartController.setChartView(holder.chartView);
            chartController.setProgressView(holder.progressView);
            holder.setSensorId(sensorLayout.sensorId);
            DataController dc = AppSingleton.getInstance(appContext).getDataController();
            chartController.loadRunData(trial, sensorLayout, dc, holder, stats,
                    new ChartController.ChartDataLoadedCallback() {
                        @Override
                        public void onChartDataLoaded(long firstTimestamp,
                                long lastTimestamp) {
                            // Display the graph.
                            chartController.setLabels(trial.getLabels());
                            chartController.setXAxis(firstTimestamp, lastTimestamp);
                            chartController.setReviewYAxis(
                                    stats.getStatValue(GoosciTrial.SensorStat.MINIMUM, 0),
                                    stats.getStatValue(GoosciTrial.SensorStat.MAXIMUM, 0), true);
                        }

                        @Override
                        public void onLoadAttemptStarted(boolean unused) {

                        }
                    }, holder.itemView.getContext());
        }

        void sortItems() {
            if (mReverseOrder) {
                Collections.sort(mItems,
                        (lhs, rhs) -> Long.compare(lhs.getTimestamp(), rhs.getTimestamp()));
            } else {
                Collections.sort(mItems,
                        (lhs, rhs) -> Long.compare(rhs.getTimestamp(), lhs.getTimestamp()));
            }
        }

        public void onSaveInstanceState(Bundle outState) {
            ArrayList<Integer> selectedIndices = new ArrayList<>();
            int size = getItemCount();
            for (int i = 0; i < size; i++) {
                if (getItemViewType(i) == VIEW_TYPE_RUN_CARD) {
                    selectedIndices.add(mItems.get(i).getSensorTagIndex());
                }
            }
            outState.putIntegerArrayList(KEY_SAVED_SENSOR_INDICES, selectedIndices);
        }

        public void onStatsBroadcastReceived(String statsRunId, DataController dc) {
            // Update the stats when this is received.
            // TODO: Optimize: only update the full view if the sensor ID that changed was visible?
            for (int i = 0; i < mItems.size(); i++) {
                Trial trial = mItems.get(i).getTrial();
                if (trial == null) {
                    continue;
                }
                final String trialId = trial.getTrialId();
                if (TextUtils.equals(statsRunId, trialId)) {
                    // Reload the experiment run since the stats have changed.
                    // TODO: Do we need a full experiment reload if the same objects are being used
                    // everywhere?
                    final int trialIndex = i;
                    dc.getExperimentById(mExperiment.getExperimentId(),
                            new LoggingConsumer<Experiment>(TAG, "load experiment") {
                                @Override
                                public void success(final Experiment experiment) {
                                    // Rebind the View Holder to reload the stats and graphs.
                                    mExperiment = experiment;
                                    mItems.get(trialIndex).setTrial(experiment.getTrial(trialId));
                                }
                            });
                    return;
                }
            }
        }

        public void addActiveRecording(Trial trial) {
            if (findTrialIndex(trial.getTrialId()) == -1) {
                mItems.add(0, new ExperimentDetailItem(trial, mScalarDisplayOptions, true));
                notifyItemInserted(0);
                updateEmptyView();
            }
        }

        public void updateActiveRecording(Trial trial) {
            int position = findTrialIndex(trial.getTrialId());
            if (position == -1) {
                addActiveRecording(trial);
            } else {
                mItems.get(position).setTrial(trial);
                notifyItemChanged(position);
            }
        }

        public void onRecordingEnded(Trial trial) {
            int position = findTrialIndex(trial.getTrialId());
            if (position == -1) {
                return;
            }
            if (!trial.isValid()) {
                // Remove it if it is invalid
                // TODO: Ask the parent fragment if we are including invalid runs.
                mItems.remove(position);
                notifyItemRemoved(position);
            } else {
                mItems.set(position, new ExperimentDetailItem(trial, mScalarDisplayOptions, false));
                notifyItemChanged(position);
            }
        }

        public void setReverseLayout(boolean reverseLayout) {
            if (mReverseOrder != reverseLayout) {
                mReverseOrder = reverseLayout;
                sortItems();
                notifyDataSetChanged();
            }
        }

        public static class DetailsViewHolder extends RecyclerView.ViewHolder implements
                ChartController.ChartLoadingStatus {

            static final int STATS_LOAD_STATUS_IDLE = 0;
            static final int STATS_LOAD_STATUS_LOADING = 1;

            private int mGraphLoadStatus;

            // Keep track of the loading state and what should currently be displayed:
            // Loads are done on a background thread, so as cards are scrolled or sensors are
            // updated we need to track what needs to be reloaded.
            private String mRunId;
            private String mSensorId;

            final int mViewType;

            int statsLoadStatus = STATS_LOAD_STATUS_IDLE;

            View cardView;

            // Run members.
            TextView runTitle;
            ChartView chartView;

            // Stats members.
            StatsList statsList;

            TextView sensorName;
            ImageView sensorImage;
            ImageButton sensorPrev;
            ImageButton sensorNext;
            ProgressBar progressView;

            ImageView captionIcon;
            View captionView;
            RelativeTimeTextView timeHeader;
            ImageButton menuButton;
            TextView captionTextView;
            ViewGroup noteHolder;

            public DetailsViewHolder(View itemView, int viewType) {
                super(itemView);
                mViewType = viewType;
                if (mViewType == VIEW_TYPE_RUN_CARD) {
                    cardView = itemView.findViewById(R.id.card_view);
                    runTitle = (TextView) itemView.findViewById(R.id.run_title_text);
                    statsList = (StatsList) itemView.findViewById(R.id.stats_view);
                    sensorName = (TextView) itemView.findViewById(R.id.run_review_sensor_name);
                    sensorPrev = (ImageButton) itemView.findViewById(
                            R.id.run_review_switch_sensor_btn_prev);
                    sensorNext = (ImageButton) itemView.findViewById(
                            R.id.run_review_switch_sensor_btn_next);
                    chartView = (ChartView) itemView.findViewById(R.id.chart_view);
                    sensorImage = (ImageView) itemView.findViewById(R.id.sensor_icon);
                    progressView = (ProgressBar) itemView.findViewById(R.id.chart_progress);
                    captionIcon = (ImageView) itemView.findViewById(R.id.edit_icon);
                    captionView = itemView.findViewById(R.id.caption_section);
                    timeHeader = (RelativeTimeTextView) itemView.findViewById(
                            R.id.relative_time_text);
                    menuButton = (ImageButton) itemView.findViewById(R.id.note_menu_button);
                    captionTextView = (TextView) itemView.findViewById(R.id.caption);
                    noteHolder = (ViewGroup) itemView.findViewById(R.id.notes_holder);

                    // Only used in RunReview
                    itemView.findViewById(R.id.time_text).setVisibility(View.GONE);
                }
            }

            public void setRunId(String runId) {
                this.mRunId = runId;
            }

            public void setSensorId(String sensorId) {
                this.mSensorId = sensorId;
            }

            @Override
            public int getGraphLoadStatus() {
                return mGraphLoadStatus;
            }

            @Override
            public void setGraphLoadStatus(int graphLoadStatus) {
                this.mGraphLoadStatus = graphLoadStatus;
            }

            @Override
            public String getRunId() {
                return mRunId;
            }

            @Override
            public String getSensorId() {
                return mSensorId;
            }
        }

        public static class RecordingViewHolder extends RecyclerView.ViewHolder {
            View cardView;
            ViewGroup noteHolder;
            TextView title;

            public RecordingViewHolder(View itemView) {
                super(itemView);
                cardView = itemView.findViewById(R.id.card_view);
                noteHolder = (ViewGroup) itemView.findViewById(R.id.notes_holder);
                title = (TextView) itemView.findViewById(R.id.title);
            }
        }

    }
}
