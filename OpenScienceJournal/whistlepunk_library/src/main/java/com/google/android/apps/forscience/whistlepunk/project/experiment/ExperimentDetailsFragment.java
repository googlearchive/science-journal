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
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.design.widget.AppBarLayout;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v4.app.ActivityOptionsCompat;
import android.support.v4.app.NavUtils;
import android.support.v4.view.ViewCompat;
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

import com.google.android.apps.forscience.javalib.MaybeConsumer;
import com.google.android.apps.forscience.javalib.Success;
import com.google.android.apps.forscience.whistlepunk.AccessibilityUtils;
import com.google.android.apps.forscience.whistlepunk.AddNoteDialog;
import com.google.android.apps.forscience.whistlepunk.AppSingleton;
import com.google.android.apps.forscience.whistlepunk.Appearances;
import com.google.android.apps.forscience.whistlepunk.ColorUtils;
import com.google.android.apps.forscience.whistlepunk.DataController;
import com.google.android.apps.forscience.whistlepunk.ElapsedTimeFormatter;
import com.google.android.apps.forscience.whistlepunk.LoggingConsumer;
import com.google.android.apps.forscience.whistlepunk.MainActivity;
import com.google.android.apps.forscience.whistlepunk.NoteViewHolder;
import com.google.android.apps.forscience.whistlepunk.PictureUtils;
import com.google.android.apps.forscience.whistlepunk.R;
import com.google.android.apps.forscience.whistlepunk.RecorderController;
import com.google.android.apps.forscience.whistlepunk.RelativeTimeTextView;
import com.google.android.apps.forscience.whistlepunk.RxDataController;
import com.google.android.apps.forscience.whistlepunk.SensorAppearance;
import com.google.android.apps.forscience.whistlepunk.StatsAccumulator;
import com.google.android.apps.forscience.whistlepunk.StatsList;
import com.google.android.apps.forscience.whistlepunk.TransitionUtils;
import com.google.android.apps.forscience.whistlepunk.WhistlePunkApplication;
import com.google.android.apps.forscience.whistlepunk.analytics.TrackerConstants;
import com.google.android.apps.forscience.whistlepunk.data.GoosciSensorLayout;
import com.google.android.apps.forscience.whistlepunk.featurediscovery.FeatureDiscoveryProvider;
import com.google.android.apps.forscience.whistlepunk.filemetadata.Experiment;
import com.google.android.apps.forscience.whistlepunk.filemetadata.Label;
import com.google.android.apps.forscience.whistlepunk.filemetadata.Trial;
import com.google.android.apps.forscience.whistlepunk.filemetadata.TrialStats;
import com.google.android.apps.forscience.whistlepunk.metadata.CropHelper;
import com.google.android.apps.forscience.whistlepunk.metadata.GoosciLabel;
import com.google.android.apps.forscience.whistlepunk.metadata.GoosciTrial;
import com.google.android.apps.forscience.whistlepunk.review.DeleteMetadataItemDialog;
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

/**
 * A fragment to handle displaying Experiment details, runs and labels.
 */
public class ExperimentDetailsFragment extends Fragment
        implements AddNoteDialog.ListenerProvider, Handler.Callback,
        DeleteMetadataItemDialog.DeleteDialogListener {

    public static final String ARG_EXPERIMENT_ID = "experiment_id";
    public static final String ARG_OLDEST_AT_TOP = "oldest_at_top";
    public static final String ARG_DISAPPEARING_ACTION_BAR = "disappearing_action_bar";
    public static final String ARG_DELETED_LABEL = "deleted_label";
    public static final String ARG_CREATE_TASK = "create_task";
    private static final String TAG = "ExperimentDetails";
    private static final int MSG_SHOW_FEATURE_DISCOVERY = 111;

    /**
     * Boolen extra for savedInstanceState with the state of includeArchived experiments.
     */
    private static final String EXTRA_INCLUDE_ARCHIVED = "includeArchived";
    private boolean mOldestAtTop = false;

    private RecyclerView mDetails;
    private DetailsAdapter mAdapter;
    FloatingActionButton mObserveButton;

    private Handler mHandler;

    private String mExperimentId;
    private Experiment mExperiment;
    private ScalarDisplayOptions mScalarDisplayOptions;
    private boolean mIncludeArchived;
    private Toolbar mControlledToolbar;
    private BroadcastReceiver mBroadcastReceiver;
    private boolean mDisappearingActionBar;
    private Label mDeletedLabel;

    /**
     * Creates a new instance of this fragment.
     *
     * @param experimentId      Experiment ID to display
     * @param createTaskStack   If {@code true}, then navigating home requires building a task stack
     *                          up to the experiment list. If {@code false}, use the default
     *                          navigation.
     * @param oldestAtTop       If {@code true}, then the oldest cards should be at the _top_ of
     *                          the list, otherwise the newest are at the top.
     * @param disappearingActionBar If {@code true}, then this fragment contains and controls the
     *                              action bar, which should disappear when the items scroll up.
     */
    public static ExperimentDetailsFragment newInstance(String experimentId,
            boolean createTaskStack, boolean oldestAtTop, boolean disappearingActionBar,
            Label deletedLabel) {
        ExperimentDetailsFragment fragment = new ExperimentDetailsFragment();
        Bundle args = new Bundle();
        args.putString(ARG_EXPERIMENT_ID, experimentId);
        args.putBoolean(ARG_CREATE_TASK, createTaskStack);
        args.putBoolean(ARG_OLDEST_AT_TOP, oldestAtTop);
        args.putBoolean(ARG_DISAPPEARING_ACTION_BAR, disappearingActionBar);
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
        mOldestAtTop = getArguments().getBoolean(ARG_OLDEST_AT_TOP, false);
        mDisappearingActionBar = getArguments().getBoolean(ARG_DISAPPEARING_ACTION_BAR, true);
        if (savedInstanceState == null) {
            // Only try to restore a deleted label the first time.
            mDeletedLabel = getArguments().getParcelable(ARG_DELETED_LABEL);
        }
        mHandler = new Handler(this);
        setHasOptionsMenu(true);
    }

    public void setExperimentId(String experimentId) {
        if (!Objects.equals(experimentId, mExperiment)) {
            mExperimentId = experimentId;
            if (isResumed()) {
                // If not resumed, wait to load until next resume!
                loadExperiment();
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
        loadExperiment();
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

    public void loadExperiment() {
        getDataController().getExperimentById(mExperimentId,
                new LoggingConsumer<Experiment>(TAG, "retrieve experiment") {
                    @Override
                    public void success(final Experiment experiment) {
                        if (experiment == null) {
                            // This was deleted on us. Finish and return so we don't try to load.
                            getActivity().finish();
                            return;
                        }
                        attachExperimentDetails(experiment);
                        loadExperimentData(experiment);
                    }
                });
    }

    @Override
    public void onPause() {
        if (mBroadcastReceiver != null) {
            CropHelper.unregisterBroadcastReceiver(getActivity().getApplicationContext(),
                    mBroadcastReceiver);
            mBroadcastReceiver = null;
        }
        clearDiscovery();
        super.onPause();
    }

    @Override
    public void onDestroy() {
        mHandler = null;
        super.onDestroy();
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
        View view = inflater.inflate(computeFragmentLayoutId(), container, false);

        AppCompatActivity activity = (AppCompatActivity) getActivity();

        Toolbar toolbar = (Toolbar) view.findViewById(R.id.toolbar);
        if (toolbar != null) {
            if (mDisappearingActionBar) {
                mControlledToolbar = toolbar;
                activity.setSupportActionBar(mControlledToolbar);
            } else {
                toolbar.setVisibility(View.GONE);
            }
        }

        ActionBar actionBar = activity.getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
            actionBar.setHomeButtonEnabled(true);
        }

        mDetails = (RecyclerView) view.findViewById(R.id.details_list);
        mDetails.setLayoutManager(new LinearLayoutManager(view.getContext(),
                LinearLayoutManager.VERTICAL, mOldestAtTop));
        mAdapter = new DetailsAdapter(this, savedInstanceState);
        mDetails.setAdapter(mAdapter);

        mObserveButton = (FloatingActionButton) view.findViewById(R.id.observe);

        // TODO: Because mScalarDisplayOptions are static, if the options are changed during the
        // time we are on this page it probably won't have an effect. Since graph options are
        // hidden from non-userdebug users, and not shown in the ExperimentDetails menu even when
        // enabled, this is OK for now.
        mScalarDisplayOptions = new ScalarDisplayOptions();
        GraphOptionsController graphOptionsController = new GraphOptionsController(getActivity());
        graphOptionsController.loadIntoScalarDisplayOptions(mScalarDisplayOptions, view);

        FeatureDiscoveryProvider provider = WhistlePunkApplication.getFeatureDiscoveryProvider(
                getActivity());
        if (provider.isEnabled(getActivity(), FeatureDiscoveryProvider.FEATURE_OBSERVE_FAB)) {
            scheduleDiscovery();
        }

        if (savedInstanceState != null) {
            mIncludeArchived = savedInstanceState.getBoolean(EXTRA_INCLUDE_ARCHIVED, false);
            getActivity().invalidateOptionsMenu();
        }

        return view;
    }

    private int computeFragmentLayoutId() {
        if (mDisappearingActionBar) {
            return R.layout.fragment_experiment_details;
        } else {
            return R.layout.fragment_panes_experiment_details;
        }
    }

    public void loadExperimentData(final Experiment experiment) {
        adjustObserveButton(experiment);

        boolean includeInvalidRuns = false;
        mAdapter.setData(experiment, experiment.getTrials(mIncludeArchived, includeInvalidRuns),
                mScalarDisplayOptions);
    }

    private void adjustObserveButton(Experiment experiment) {
        if (mObserveButton == null) {
            return;
        }

        // If we don't have the disappearing action bar, we're in V2 UI, and shouldn't have a FAB,
        // either.
        if (experiment.isArchived() || !mDisappearingActionBar) {
            mObserveButton.setVisibility(View.GONE);
            mObserveButton.setOnClickListener(null);
        } else {
            mObserveButton.setVisibility(View.VISIBLE);
            mObserveButton.setOnClickListener(v -> launchObserve());
        }
    }

    private void attachExperimentDetails(Experiment experiment) {
        mExperiment = experiment;
        final View rootView = getView();
        if (rootView == null) {
            return;
        }

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
        menu.findItem(R.id.action_experiment_add_note).setVisible(mDisappearingActionBar);
        menu.findItem(R.id.action_archive_experiment).setVisible(mExperiment != null &&
                !mExperiment.isArchived());
        menu.findItem(R.id.action_unarchive_experiment).setVisible(mExperiment != null &&
                mExperiment.isArchived());
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
            goToExperimentList();
            return true;
        } else if (itemId == R.id.action_edit_experiment) {
            UpdateExperimentActivity.launch(getActivity(), mExperimentId, false /* not new */);
            return true;
        } else if (itemId == R.id.action_archive_experiment
                || itemId == R.id.action_unarchive_experiment) {
            setExperimentArchived(item.getItemId() == R.id.action_archive_experiment);
            return true;
        } else if (itemId == R.id.action_experiment_add_note) {
            launchLabelAdd();
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
        // Reload the data to refresh experiment item and insert it if necessary.
        loadExperimentData(mExperiment);
        WhistlePunkApplication.getUsageTracker(getActivity())
                .trackEvent(TrackerConstants.CATEGORY_EXPERIMENTS,
                        archived ? TrackerConstants.ACTION_ARCHIVE :
                                TrackerConstants.ACTION_UNARCHIVE,
                        null, 0);
    }

    /**
     * Sets this as the active experiment and launches observe when done.
     */
    private void launchObserve() {
        if (mExperiment == null) {
            return;
        }
        Context context = getActivity();
        boolean usePanes = false;
        context.startActivity(MainActivity.launchIntent(context, R.id.navigation_item_observe,
                usePanes));
    }

    private void launchLabelAdd() {
        long now = AppSingleton.getInstance(getActivity()).getSensorEnvironment()
                .getDefaultClock().getNow();
        AddNoteDialog dialog =
                AddNoteDialog.createWithSavedTimestamp(now, RecorderController.NOT_RECORDING_RUN_ID,
                        mExperimentId, R.string.add_experiment_note_placeholder_text);
        dialog.show(getChildFragmentManager(), AddNoteDialog.TAG);
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

    @Override
    public AddNoteDialog.AddNoteDialogListener getAddNoteDialogListener() {
        return new AddNoteDialog.AddNoteDialogListener() {
            @Override
            public MaybeConsumer<Label> onLabelAdd() {
                return  new LoggingConsumer<Label>(TAG, "add label") {
                    @Override
                    public void success(Label value) {
                        mAdapter.insertNote(value);
                        WhistlePunkApplication.getUsageTracker(getActivity())
                                              .trackEvent(TrackerConstants.CATEGORY_NOTES,
                                                      TrackerConstants.ACTION_CREATE,
                                                      TrackerConstants.LABEL_EXPERIMENT_DETAIL,
                                                      TrackerConstants.getLabelValueType(value));
                    }
                };
            }

            @Override
            public void onAddNoteTimestampClicked(Label label, long selectedTimestamp) {
                // No timestamp editing available in Experiments.
            }
        };
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
                Snackbar.LENGTH_SHORT);

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
                            null, 0);
        });
    }

    private void deleteTrial(Trial trial) {
        DeleteMetadataItemDialog dialog = DeleteMetadataItemDialog.newInstance(
                R.string.delete_run_dialog_title, R.string.run_review_delete_confirm,
                trial.getTrialId());
        dialog.show(getChildFragmentManager(), DeleteMetadataItemDialog.TAG);
    }

    @Override
    public boolean handleMessage(Message msg) {
        if (msg.what == MSG_SHOW_FEATURE_DISCOVERY) {
            showFeatureDiscovery();
        }
        return false;
    }

    private void scheduleDiscovery() {
        mHandler.sendEmptyMessageDelayed(MSG_SHOW_FEATURE_DISCOVERY,
                FeatureDiscoveryProvider.FEATURE_DISCOVERY_SHOW_DELAY_MS);
    }

    private void clearDiscovery() {
        mHandler.removeMessages(MSG_SHOW_FEATURE_DISCOVERY);
    }

    private void showFeatureDiscovery() {
        if (getActivity() == null) {
            return;
        }
        FeatureDiscoveryProvider provider = WhistlePunkApplication.getFeatureDiscoveryProvider(
                getActivity());
        if (mObserveButton != null) {
            mObserveButton.setTag(TAG);
        }
        provider.show(((AppCompatActivity) getActivity()),
                FeatureDiscoveryProvider.FEATURE_OBSERVE_FAB, TAG);
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
                    getActivity().finish();
                }
            });
        }
    }

    private void setToolbarScrollFlags(boolean emptyView) {
        if (mControlledToolbar == null) {
            return;
        }
        AppBarLayout.LayoutParams params =
                (AppBarLayout.LayoutParams) mControlledToolbar.getLayoutParams();
        if (emptyView) {
            // Don't scroll the toolbar if empty view is showing.
            params.setScrollFlags(0);
        } else {
            params.setScrollFlags(AppBarLayout.LayoutParams.SCROLL_FLAG_SCROLL
                    | AppBarLayout.LayoutParams.SCROLL_FLAG_ENTER_ALWAYS);
        }
        mControlledToolbar.setLayoutParams(params);
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
        private static final int VIEW_TYPE_EMPTY = 4;
        static final int VIEW_TYPE_EXPERIMENT_TRIGGER_LABEL = 5;
        static final int VIEW_TYPE_UNKNOWN_LABEL = 6;

        private final WeakReference<ExperimentDetailsFragment> mParentReference;
        private Experiment mExperiment;
        private List<ExperimentDetailItem> mItems;
        private List<Integer> mSensorIndices = null;
        private boolean mHasRunsOrLabels;

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
                    viewType == VIEW_TYPE_EXPERIMENT_TRIGGER_LABEL) {
                view = inflater.inflate(R.layout.exp_card_pinned_note, parent, false);
                return new NoteViewHolder(view);
            } else if (viewType == VIEW_TYPE_EXPERIMENT_ARCHIVED) {
                view = inflater.inflate(R.layout.metadata_archived, parent, false);
            } else if (viewType == VIEW_TYPE_EMPTY) {
                view = inflater.inflate(R.layout.empty_list, parent, false);
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
            }
            boolean isPictureLabel = type == VIEW_TYPE_EXPERIMENT_PICTURE_LABEL;
            boolean isTextLabel = type == VIEW_TYPE_EXPERIMENT_TEXT_LABEL;
            boolean isTriggerLabel = type == VIEW_TYPE_EXPERIMENT_TRIGGER_LABEL;
            if (isPictureLabel || isTextLabel || isTriggerLabel) {
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
                    if (mParentReference.get() != null) {
                        LabelDetailsActivity.launchFromExpDetails(holder.itemView.getContext(),
                                mExperiment.getExperimentId(), label);
                    }
                });
            }
            if (type == VIEW_TYPE_EXPERIMENT_ARCHIVED) {
                View archivedIndicator = holder.itemView.findViewById(R.id.archived_indicator);
                archivedIndicator.setVisibility(
                        mExperiment.isArchived() ? View.VISIBLE : View.GONE);
            } else if (type == VIEW_TYPE_EMPTY) {
                TextView view = (TextView) holder.itemView;
                view.setText(R.string.empty_experiment);
                view.setCompoundDrawablesRelativeWithIntrinsicBounds(null, null, null,
                        view.getResources().getDrawable(R.drawable.empty_run));

            }
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
            if (!TextUtils.isEmpty(caption)) {
                holder.captionView.setVisibility(View.VISIBLE);
                holder.captionTextView.setText(caption);
                holder.captionIcon.setVisibility(View.GONE);
            } else {
                holder.captionView.setVisibility(View.GONE);
                holder.captionIcon.setVisibility(View.VISIBLE);
            }
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
                    VIEW_TYPE_EXPERIMENT_PICTURE_LABEL : VIEW_TYPE_EXPERIMENT_TRIGGER_LABEL;
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
                if (item.getViewType() == VIEW_TYPE_RUN_CARD) {
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
            int emptyIndex = -1;
            mHasRunsOrLabels = true;
            for (int i = 0; i < size; i++) {
                ExperimentDetailItem item = mItems.get(i);
                if (item.getViewType() == VIEW_TYPE_EXPERIMENT_ARCHIVED) {
                    continue;
                } else if (item.getViewType() == VIEW_TYPE_EMPTY) {
                    emptyIndex = i;
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
            // Remove the empty type if necessary.
            if (emptyIndex > -1) {
                removeEmptyView(emptyIndex, true);
            }
        }

        @Override
        public int getItemCount() {
            return mItems.size();
        }

        @Override
        public int getItemViewType(int position) {
            return mItems.get(position).getViewType();
        }

        public void setData(Experiment experiment, List<Trial> trials,
                ScalarDisplayOptions scalarDisplayOptions) {
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
            for (Trial trial : trials) {
                ExperimentDetailItem item = new ExperimentDetailItem(trial, scalarDisplayOptions);
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

            if (!mHasRunsOrLabels) {
                addEmptyView(false /* don't notify */);
            }

            if (mParentReference.get() != null) {
                mParentReference.get().setToolbarScrollFlags(!mHasRunsOrLabels);
            }

            notifyDataSetChanged();
        }

        /**
         * Checks to see if we have any labels or runs. If so, hides the empty view. Otherwise,
         * add the empty view at the right location.
         */
        private void updateEmptyView() {
            boolean hasRunsOrLabels = false;
            int emptyIndex = -1;

            final int count = mItems.size();
            for (int index = 0; index < count; ++index) {
                int viewType = mItems.get(index).getViewType();
                switch (viewType) {
                    case VIEW_TYPE_EMPTY:
                        emptyIndex = index;
                        break;
                    case VIEW_TYPE_RUN_CARD:
                    case VIEW_TYPE_EXPERIMENT_PICTURE_LABEL:
                    case VIEW_TYPE_EXPERIMENT_TEXT_LABEL:
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
            if (hasRunsOrLabels && emptyIndex != -1) {
                // We have runs but there is an empty item. Remove it.
                removeEmptyView(emptyIndex, true);
            } else if (!hasRunsOrLabels && emptyIndex == -1) {
                // We have no runs or labels and no empty item. Add it to the end.
                addEmptyView(true);
            }
        }

        void addEmptyView(boolean notifyAdd) {
            mItems.add(new ExperimentDetailItem(VIEW_TYPE_EMPTY));
            if (notifyAdd) {
                notifyItemInserted(mItems.size() - 1);
                if (mExperiment.isArchived()) {
                    notifyItemChanged(0);
                }
            }
            if (mParentReference.get() != null) {
                mParentReference.get().setToolbarScrollFlags(true /* has an empty view*/);
            }
        }

        void removeEmptyView(int location, boolean notifyRemove) {
            mItems.remove(location);
            if (notifyRemove) {
                notifyItemRemoved(location);
                if (mExperiment.isArchived()) {
                    notifyItemChanged(0);
                }
            }
            if (mParentReference.get() != null) {
                mParentReference.get().setToolbarScrollFlags(false /* has an empty view*/);
            }
        }

        boolean hasEmptyView() {
            return !mHasRunsOrLabels;
        }

        void bindRun(final DetailsViewHolder holder, final ExperimentDetailItem item) {
            final Trial trial = item.getTrial();
            final Context applicationContext = holder.itemView.getContext().getApplicationContext();
            holder.setRunId(trial.getTrialId());
            String title = trial.getTitle(applicationContext);
            holder.runTitle.setText(title);
            holder.date.setTime(trial.getFirstTimestamp());
            ElapsedTimeFormatter formatter = ElapsedTimeFormatter.getInstance(applicationContext);
            holder.duration.setText(formatter.format(trial.elapsedSeconds()));
            holder.duration.setContentDescription(
                    formatter.formatForAccessibility(trial.elapsedSeconds()));
            if (trial.getLabelCount() > 0) {
                holder.noteCount.setVisibility(View.VISIBLE);
                holder.noteCount.setText(applicationContext.getResources().getQuantityString(
                        R.plurals.notes_count, trial.getLabelCount(), trial.getLabelCount()));
            } else {
                holder.noteCount.setVisibility(View.GONE);
            }
            holder.cardView.setOnClickListener(createRunClickListener(item.getSensorTagIndex()));
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

            ViewCompat.setTransitionName(holder.itemView, trial.getTrialId());
            if (!trial.isValid()) {
                removeSensorData(holder);

            } else if (trial.getSensorIds().size() > 0) {
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

        private void removeSensorData(DetailsViewHolder holder) {
            holder.sensorName.setText("");
            holder.setSensorId(null);
            setIndeterminateSensorData(holder);
        }

        private View.OnClickListener createRunClickListener(final int selectedSensorIndex) {
            return v -> {
                AppCompatActivity activity = (AppCompatActivity) v.getContext();
                String runId = (String) v.getTag(R.id.run_title_text);

                ActivityOptionsCompat options = ActivityOptionsCompat
                        .makeSceneTransitionAnimation(activity, TransitionUtils
                                .getTransitionPairs(activity, v, runId));
                RunReviewActivity.launch(v.getContext(), runId, mExperiment.getExperimentId(),
                        selectedSensorIndex, false /* from record */, false /* create task */,
                        options.toBundle());
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
            Appearances.applyDrawableToImageView(appearance.getIconDrawable(appContext),
                    holder.sensorImage, appContext.getResources().getIntArray(
                            R.array.graph_colors_array)[sensorLayout.colorIndex]);

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
                            chartController.setXAxisWithBuffer(firstTimestamp, lastTimestamp);
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
            Collections.sort(mItems,
                    (lhs, rhs) -> Long.compare(rhs.getTimestamp(), lhs.getTimestamp()));
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
            RelativeTimeTextView date;
            TextView duration;
            TextView noteCount;
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

            public DetailsViewHolder(View itemView, int viewType) {
                super(itemView);
                mViewType = viewType;
                if (mViewType == VIEW_TYPE_RUN_CARD) {
                    cardView = itemView.findViewById(R.id.card_view);
                    runTitle = (TextView) itemView.findViewById(R.id.run_title_text);
                    date = (RelativeTimeTextView) itemView.findViewById(R.id.run_details_text);
                    noteCount = (TextView) itemView.findViewById(R.id.notes_count);
                    // Set color programatically because this is a compound drawable and
                    // android:drawableTint starts in API 23.
                    ColorUtils.colorDrawable(noteCount.getContext(),
                            noteCount.getCompoundDrawablesRelative()[0],
                            R.color.text_color_light_grey);
                    duration = (TextView) itemView.findViewById(R.id.run_review_duration);
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

    }
}
