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
import android.graphics.Rect;
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
import android.util.Log;
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

import com.bumptech.glide.Glide;
import com.google.android.apps.forscience.javalib.MaybeConsumer;
import com.google.android.apps.forscience.javalib.Success;
import com.google.android.apps.forscience.whistlepunk.AccessibilityUtils;
import com.google.android.apps.forscience.whistlepunk.AddNoteDialog;
import com.google.android.apps.forscience.whistlepunk.AppSingleton;
import com.google.android.apps.forscience.whistlepunk.Appearances;
import com.google.android.apps.forscience.whistlepunk.ColorUtils;
import com.google.android.apps.forscience.whistlepunk.DataController;
import com.google.android.apps.forscience.whistlepunk.EditNoteDialog;
import com.google.android.apps.forscience.whistlepunk.ElapsedTimeFormatter;
import com.google.android.apps.forscience.whistlepunk.LoggingConsumer;
import com.google.android.apps.forscience.whistlepunk.MainActivity;
import com.google.android.apps.forscience.whistlepunk.PictureUtils;
import com.google.android.apps.forscience.whistlepunk.R;
import com.google.android.apps.forscience.whistlepunk.RecorderController;
import com.google.android.apps.forscience.whistlepunk.RelativeTimeTextView;
import com.google.android.apps.forscience.whistlepunk.SensorAppearance;
import com.google.android.apps.forscience.whistlepunk.StatsAccumulator;
import com.google.android.apps.forscience.whistlepunk.StatsList;
import com.google.android.apps.forscience.whistlepunk.TransitionUtils;
import com.google.android.apps.forscience.whistlepunk.WhistlePunkApplication;
import com.google.android.apps.forscience.whistlepunk.analytics.TrackerConstants;
import com.google.android.apps.forscience.whistlepunk.data.GoosciSensorLayout;
import com.google.android.apps.forscience.whistlepunk.featurediscovery.FeatureDiscoveryProvider;
import com.google.android.apps.forscience.whistlepunk.filemetadata.Label;
import com.google.android.apps.forscience.whistlepunk.filemetadata.LabelValue;
import com.google.android.apps.forscience.whistlepunk.filemetadata.PictureLabelValue;
import com.google.android.apps.forscience.whistlepunk.filemetadata.SensorTriggerLabelValue;
import com.google.android.apps.forscience.whistlepunk.filemetadata.TextLabelValue;
import com.google.android.apps.forscience.whistlepunk.filemetadata.TrialStats;
import com.google.android.apps.forscience.whistlepunk.metadata.CropHelper;
import com.google.android.apps.forscience.whistlepunk.metadata.Experiment;
import com.google.android.apps.forscience.whistlepunk.metadata.ExperimentRun;
import com.google.android.apps.forscience.whistlepunk.metadata.GoosciLabelValue;
import com.google.android.apps.forscience.whistlepunk.metadata.GoosciTrial;
import com.google.android.apps.forscience.whistlepunk.metadata.TriggerHelper;
import com.google.android.apps.forscience.whistlepunk.review.DeleteMetadataItemDialog;
import com.google.android.apps.forscience.whistlepunk.review.RunReviewActivity;
import com.google.android.apps.forscience.whistlepunk.review.RunReviewFragment;
import com.google.android.apps.forscience.whistlepunk.scalarchart.ChartController;
import com.google.android.apps.forscience.whistlepunk.scalarchart.ChartOptions;
import com.google.android.apps.forscience.whistlepunk.scalarchart.ChartView;
import com.google.android.apps.forscience.whistlepunk.scalarchart.GraphOptionsController;
import com.google.android.apps.forscience.whistlepunk.scalarchart.ScalarDisplayOptions;
import com.google.android.apps.forscience.whistlepunk.sensorapi.StreamStat;

import java.lang.ref.WeakReference;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * A fragment to handle displaying Experiment details, runs and labels.
 */
public class ExperimentDetailsFragment extends Fragment
        implements AddNoteDialog.ListenerProvider, EditNoteDialog.EditNoteDialogListener,
        Handler.Callback, DeleteMetadataItemDialog.DeleteDialogListener {

    public static final String ARG_EXPERIMENT_ID = "experiment_id";
    public static final String ARG_CREATE_TASK = "create_task";
    private static final String TAG = "ExperimentDetails";
    private static final int MSG_SHOW_FEATURE_DISCOVERY = 111;

    /**
     * Boolen extra for savedInstanceState with the state of includeArchived experiments.
     */
    private static final String EXTRA_INCLUDE_ARCHIVED = "includeArchived";

    private RecyclerView mDetails;
    private DetailsAdapter mAdapter;
    FloatingActionButton mObserveButton;

    private Handler mHandler;

    private String mExperimentId;
    private Experiment mExperiment;
    private ScalarDisplayOptions mScalarDisplayOptions;
    private GraphOptionsController mGraphOptionsController;
    private boolean mIncludeArchived;
    private Toolbar mToolbar;
    private BroadcastReceiver mBroadcastReceiver;

    /**
     * Creates a new instance of this fragment.
     *
     * @param experimentId      Experiment ID to display
     * @param createTaskStack   If {@code true}, then navigating home requires building a task stack
     *                          up to the experiment list. If {@code false}, use the default
     *                          navigation.
     */
    public static ExperimentDetailsFragment newInstance(String experimentId,
                                                        boolean createTaskStack) {
        ExperimentDetailsFragment fragment = new ExperimentDetailsFragment();
        Bundle args = new Bundle();
        args.putString(ARG_EXPERIMENT_ID, experimentId);
        args.putBoolean(ARG_CREATE_TASK, createTaskStack);
        fragment.setArguments(args);
        return fragment;
    }

    public ExperimentDetailsFragment() {
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mExperimentId = getArguments().getString(ARG_EXPERIMENT_ID);
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
                String statsRunId = intent.getStringExtra(CropHelper.EXTRA_RUN_ID);
                mAdapter.onStatsBroadcastReceived(statsRunId, getDataController());
            }
        };
        CropHelper.registerStatsBroadcastReceiver(getActivity().getApplicationContext(),
                mBroadcastReceiver);
    }

    private void loadExperiment() {
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
        View view = inflater.inflate(R.layout.fragment_experiment_details, container, false);

        AppCompatActivity activity = (AppCompatActivity) getActivity();
        mToolbar = (Toolbar) view.findViewById(R.id.toolbar);
        activity.setSupportActionBar(mToolbar);

        ActionBar actionBar = activity.getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
            actionBar.setHomeButtonEnabled(true);
        }

        mDetails = (RecyclerView) view.findViewById(R.id.details_list);
        final int experimentDescriptionPadding = mDetails.getContext().getResources()
                .getDimensionPixelSize(R.dimen.metadata_description_overlap_bottom);
        mDetails.addItemDecoration(new RecyclerView.ItemDecoration() {
            @Override
            public void getItemOffsets(Rect outRect, View view, RecyclerView parent,
                    RecyclerView.State state) {
                if (isDescriptionView(view)) {
                    // Then it is the VIEW_TYPE_EXPERIMENT_DESCRIPTION, so we need to decrease
                    // its bottom padding to let the next card overlap it to meet the UX spec.
                    // Need to also check if this is an empty experiment before
                    // deciding to set this.
                    if (mAdapter.hasEmptyView()) {
                        // Empty experiment view doesn't show as a card, so no overlap needed.
                        super.getItemOffsets(outRect, view, parent, state);
                    } else {
                        outRect.set(0, 0, 0, experimentDescriptionPadding);
                    }
                } else {
                    super.getItemOffsets(outRect, view, parent, state);
                }
            }

            private boolean isDescriptionView(View view) {
                if (mDetails.getChildAdapterPosition(view) != 0) {
                    return false;
                }
                if (mExperiment != null && (mExperiment.getExperiment().isArchived() ||
                        !TextUtils.isEmpty(mExperiment.getExperiment().getDescription()))) {
                    // Then it is the VIEW_TYPE_EXPERIMENT_DESCRIPTION
                    return true;
                }
                return false;
            }
        });
        mDetails.setLayoutManager(new LinearLayoutManager(view.getContext(),
                LinearLayoutManager.VERTICAL, false));
        mAdapter = new DetailsAdapter(this, savedInstanceState);
        mDetails.setAdapter(mAdapter);

        mObserveButton = (FloatingActionButton) view.findViewById(R.id.observe);

        // TODO: Because mScalarDisplayOptions are static, if the options are changed during the
        // time we are on this page it probably won't have an effect. Since graph options are
        // hidden from non-userdebug users, and not shown in the ExperimentDetails menu even when
        // enabled, this is OK for now.
        mScalarDisplayOptions = new ScalarDisplayOptions();
        mGraphOptionsController = new GraphOptionsController(getActivity());
        mGraphOptionsController.loadIntoScalarDisplayOptions(mScalarDisplayOptions, view);

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

    private void loadExperimentData(final Experiment experiment) {
        if (experiment.getExperiment().isArchived()) {
            mObserveButton.setVisibility(View.GONE);
            mObserveButton.setOnClickListener(null);
        } else {
            mObserveButton.setVisibility(View.VISIBLE);
            mObserveButton.setOnClickListener(new View.OnClickListener() {

                @Override
                public void onClick(View v) {
                    launchObserve();
                }
            });
        }
        final DataController dc = getDataController();
        boolean includeInvalidRuns = false;
        dc.getExperimentRuns(experiment.getExperimentId(), mIncludeArchived, includeInvalidRuns,
                new LoggingConsumer<List<ExperimentRun>>(TAG, "loading runs") {
                    @Override
                    public void success(final List<ExperimentRun> runs) {
                        mAdapter.setData(experiment, runs, mScalarDisplayOptions);
                    }
                });
    }

    private void attachExperimentDetails(Experiment experiment) {
        mExperiment = experiment;
        final View rootView = getView();
        if (rootView == null) {
            return;
        }

        getActivity().setTitle(experiment.getExperiment().getDisplayTitle(getActivity()));

        Toolbar toolbar = (Toolbar) rootView.findViewById(R.id.toolbar);
        toolbar.setTitle(experiment.getExperiment().getDisplayTitle(getActivity()));
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
                !mExperiment.getExperiment().isArchived());
        menu.findItem(R.id.action_unarchive_experiment).setVisible(mExperiment != null &&
                mExperiment.getExperiment().isArchived());
        menu.findItem(R.id.action_delete_experiment).setEnabled(mExperiment != null
                && mExperiment.getExperiment().isArchived());
        menu.findItem(R.id.action_include_archived).setVisible(!mIncludeArchived);
        menu.findItem(R.id.action_exclude_archived).setVisible(mIncludeArchived);
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
            confirmDelete();
        }
        return super.onOptionsItemSelected(item);
    }

    private void goToExperimentList() {
        Intent upIntent = NavUtils.getParentActivityIntent(getActivity());
        if (NavUtils.shouldUpRecreateTask(getActivity(), upIntent)
                || getArguments().getBoolean(ARG_CREATE_TASK, false)) {
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
        mExperiment.getExperiment().setArchived(archived);
        getDataController().updateExperiment(mExperiment, new LoggingConsumer<Success>(
                TAG, "Editing experiment") {
            @Override
            public void success(Success value) {
                Snackbar bar = AccessibilityUtils.makeSnackbar(getView(),
                        getActivity().getResources().getString(
                                archived ? R.string.archived_experiment_message : R.string
                                        .unarchived_experiment_message),
                        Snackbar.LENGTH_LONG);

                if (archived) {
                    bar.setAction(R.string.action_undo, new View.OnClickListener() {

                        @Override
                        public void onClick(View v) {
                            setExperimentArchived(false);
                        }
                    });
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
        getDataController().updateLastUsedExperiment(mExperiment,
                new LoggingConsumer<Success>(TAG, "updating active experiment") {
                    @Override
                    public void success(Success value) {
                        MainActivity.launch(getActivity(), R.id.navigation_item_observe);
                    }
                });
    }

    private void launchPicturePreview(Label label) {
        EditNoteDialog dialog = EditNoteDialog.newInstance(label,
                label.getLabelValue(GoosciLabelValue.LabelValue.PICTURE).getValue(),
                label.getTimeStamp(), mExperimentId, RecorderController.NOT_RECORDING_RUN_ID);
        dialog.show(getChildFragmentManager(), EditNoteDialog.TAG);
    }

    private void launchLabelEdit(final Label label) {
        // Assuming one labelValue per label at the moment.
        GoosciLabelValue.LabelValue value = label.getLabelValues().get(0).getValue();
        EditNoteDialog dialog = EditNoteDialog.newInstance(label, value,
                label.getTimeStamp(), mExperimentId, RecorderController.NOT_RECORDING_RUN_ID);
        dialog.show(getChildFragmentManager(), EditNoteDialog.TAG);
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
            public MaybeConsumer<Label> onLabelAdd(Label label) {
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
            public void onAddNoteTimestampClicked(GoosciLabelValue.LabelValue selectedValue,
                    int labelType, long selectedTimestamp) {
                // No timestamp editing available in Experiments.
            }
        };
    }

    @Override
    public MaybeConsumer<Success> onLabelEdit(final Label label) {
        return new LoggingConsumer<Success>(TAG, "edit label text") {
            @Override
            public void success(Success value) {
                mAdapter.updateLabel(label);
                WhistlePunkApplication.getUsageTracker(getActivity())
                        .trackEvent(TrackerConstants.CATEGORY_NOTES,
                                TrackerConstants.ACTION_EDITED,
                                TrackerConstants.LABEL_EXPERIMENT_DETAIL,
                                TrackerConstants.getLabelValueType(label));
            }
        };
    }

    @Override
    public void onEditNoteTimestampClicked(Label label, GoosciLabelValue.LabelValue selectedValue,
            long labelTimestamp) {
        // No timestamp editing available in Experiments.
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
                mExperiment.getExperiment().addLabel(label);
                dc.updateExperiment(mExperiment, new LoggingConsumer<Success>(TAG,
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

        // Delete the item immediately, and remove it from the pinned note list.
        // TODO: Deleting the assets makes undo not work on photo labels...
        mExperiment.getExperiment().deleteLabel(item, getActivity());
        dc.updateExperiment(mExperiment, new LoggingConsumer<Success>(TAG, "delete label") {
            @Override
            public void success(Success value) {
                mAdapter.deleteNote(item);
            }
        });
        bar.show();

        WhistlePunkApplication.getUsageTracker(getActivity())
                .trackEvent(TrackerConstants.CATEGORY_NOTES,
                        TrackerConstants.ACTION_DELETED,
                        TrackerConstants.LABEL_EXPERIMENT_DETAIL,
                        TrackerConstants.getLabelValueType(item));
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
        mObserveButton.setTag(TAG);
        provider.show(((AppCompatActivity) getActivity()),
                FeatureDiscoveryProvider.FEATURE_OBSERVE_FAB, TAG);
    }

    private void confirmDelete() {
        DeleteMetadataItemDialog dialog = DeleteMetadataItemDialog.newInstance(
                R.string.delete_experiment_dialog_title, R.string.delete_experiment_dialog_message);
        dialog.show(getChildFragmentManager(), DeleteMetadataItemDialog.TAG);
    }

    @Override
    public void requestDelete(Bundle extras) {
        getDataController().deleteExperiment(mExperiment, new LoggingConsumer<Success>(TAG,
                "Delete experiment") {
            @Override
            public void success(Success value) {
                getActivity().finish();
            }
        });
    }

    private void setToolbarScrollFlags(boolean emptyView) {
        AppBarLayout.LayoutParams params = (AppBarLayout.LayoutParams) mToolbar.getLayoutParams();
        if (emptyView) {
            // Don't scroll the toolbar if empty view is showing.
            params.setScrollFlags(0);
        } else {
            params.setScrollFlags(AppBarLayout.LayoutParams.SCROLL_FLAG_SCROLL
                    | AppBarLayout.LayoutParams.SCROLL_FLAG_ENTER_ALWAYS);
        }
        mToolbar.setLayoutParams(params);
    }

    public String getExperimentId() {
        return mExperimentId;
    }

    public static class DetailsAdapter extends RecyclerView.Adapter<DetailsAdapter.ViewHolder> {

        private static final String KEY_SAVED_SENSOR_INDICES = "savedSensorIndices";

        private static final int VIEW_TYPE_EXPERIMENT_DESCRIPTION = 0;
        private static final int VIEW_TYPE_EXPERIMENT_TEXT_LABEL = 1;
        private static final int VIEW_TYPE_EXPERIMENT_PICTURE_LABEL = 2;
        private static final int VIEW_TYPE_RUN_CARD = 3;
        private static final int VIEW_TYPE_EMPTY = 4;
        private static final int VIEW_TYPE_EXPERIMENT_TRIGGER_LABEL = 5;

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
        public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view;
            LayoutInflater inflater =  LayoutInflater.from(parent.getContext());
            if (viewType == VIEW_TYPE_EXPERIMENT_TEXT_LABEL ||
                    viewType == VIEW_TYPE_EXPERIMENT_PICTURE_LABEL ||
                    viewType == VIEW_TYPE_EXPERIMENT_TRIGGER_LABEL) {
                view = inflater.inflate(R.layout.exp_card_pinned_note, parent, false);
            } else if (viewType == VIEW_TYPE_EXPERIMENT_DESCRIPTION) {
                view = inflater.inflate(R.layout.metadata_description, parent, false);
            } else if (viewType == VIEW_TYPE_EMPTY) {
                view = inflater.inflate(R.layout.empty_list, parent, false);
            } else {
                view = inflater.inflate(R.layout.exp_card_run, parent, false);
            }
            return new ViewHolder(view, viewType);
        }

        @Override
        public void onBindViewHolder(ViewHolder holder, int position) {
            ExperimentDetailItem item = mItems.get(position);
            int type = item.getViewType();
            if (type == VIEW_TYPE_RUN_CARD) {
                bindRun(holder, item);
            }
            boolean isPictureLabel = type == VIEW_TYPE_EXPERIMENT_PICTURE_LABEL;
            boolean isTextLabel = type == VIEW_TYPE_EXPERIMENT_TEXT_LABEL;
            boolean isTriggerLabel = type == VIEW_TYPE_EXPERIMENT_TRIGGER_LABEL;
            if (isPictureLabel || isTextLabel || isTriggerLabel) {
                // TODO: Can this code be reused from PinnedNoteAdapter?
                TextView textView = (TextView) holder.itemView.findViewById(R.id.note_text);
                TextView autoTextView = (TextView) holder.itemView.findViewById(
                        R.id.auto_note_text);
                final Label label = mItems.get(position).mLabel;
                final LabelValue labelValue = mItems.get(position).mLabelValue;
                String text = isPictureLabel ? ((PictureLabelValue) labelValue).getCaption() :
                        isTextLabel ? ((TextLabelValue) labelValue).getText() :
                        ((SensorTriggerLabelValue) labelValue).getCustomText();
                if (!TextUtils.isEmpty(text)) {
                    textView.setText(text);
                    textView.setTextColor(textView.getResources().getColor(
                            R.color.text_color_black));
                } else {
                    textView.setText(textView.getResources().getString(isPictureLabel ?
                            R.string.picture_note_caption_hint :
                            R.string.pinned_note_placeholder_text));
                    textView.setTextColor(
                            textView.getResources().getColor(R.color.text_color_light_grey));
                }
                ((RelativeTimeTextView) holder.itemView.findViewById(R.id.duration_text)).setTime(
                        label.getTimeStamp());
                setupNoteMenu(label, holder.itemView.findViewById(R.id.note_menu_button),
                        label.getTimeStamp());
                ImageView imageView = (ImageView) holder.itemView.findViewById(R.id.note_image);
                if (isPictureLabel) {
                    imageView.setVisibility(View.VISIBLE);
                    Glide.with(imageView.getContext())
                            .load(((PictureLabelValue) labelValue).getFilePath())
                            .into(imageView);
                    View.OnClickListener clickListener = new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            if (mParentReference.get() != null) {
                                mParentReference.get().launchPicturePreview(label);
                            }
                        }
                    };
                    holder.cardView.setOnClickListener(clickListener);
                    autoTextView.setVisibility(View.GONE);
                } else {
                    holder.cardView.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            if (mParentReference.get() != null) {
                                mParentReference.get().launchLabelEdit(label);
                            }
                        }
                    });
                    imageView.setVisibility(View.GONE);
                    if (isTriggerLabel) {
                        autoTextView.setVisibility(View.VISIBLE);
                        String autoText = ((SensorTriggerLabelValue) labelValue).getAutogenText();
                        TriggerHelper.populateAutoTextViews(autoTextView, autoText,
                                R.drawable.ic_label_black_18dp, autoTextView.getResources());

                    }
                }
            }
            if (type == VIEW_TYPE_EXPERIMENT_DESCRIPTION) {
                TextView description = (TextView) holder.itemView.findViewById(
                        R.id.metadata_description);
                holder.itemView.findViewById(R.id.metadata_description_holder)
                        .setBackgroundColor(holder.itemView.getContext().getResources()
                                .getColor(R.color.color_accent_dark));
                holder.itemView.findViewById(R.id.description_overlap_spacer).setVisibility(
                        hasEmptyView() ? View.GONE : View.VISIBLE);
                description.setText(mExperiment.getExperiment().getDescription());
                description.setVisibility(TextUtils.isEmpty(
                        mExperiment.getExperiment().getDescription()) ? View.GONE : View.VISIBLE);
                View archivedIndicator = holder.itemView.findViewById(R.id.archived_indicator);
                archivedIndicator.setVisibility(
                        mExperiment.getExperiment().isArchived() ? View.VISIBLE : View.GONE);
            } else if (type == VIEW_TYPE_EMPTY) {
                TextView view = (TextView) holder.itemView;
                view.setText(R.string.empty_experiment);
                view.setCompoundDrawablesRelativeWithIntrinsicBounds(null, null, null,
                        view.getResources().getDrawable(R.drawable.empty_run));

            }
        }

        private void setupNoteMenu(final Label label, final View menu, final long timestamp) {
            menu.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Context context = menu.getContext();
                    PopupMenu popup = new PopupMenu(context, menu);
                    popup.getMenuInflater().inflate(R.menu.menu_note, popup.getMenu());
                    popup.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
                        public boolean onMenuItemClick(MenuItem item) {
                            if (item.getItemId() == R.id.btn_edit_note) {
                                if (mParentReference.get() != null) {
                                    mParentReference.get().launchLabelEdit(label);
                                }
                                return true;
                            } else if (item.getItemId() == R.id.btn_delete_note) {
                                if (mParentReference.get() != null) {
                                    mParentReference.get().onLabelDelete(label);
                                }
                                return true;
                            }
                            return false;
                        }
                    });
                    popup.show();
                }
            });
        }

        public void updateLabel(Label label) {
            if (!(label.hasValueType(GoosciLabelValue.LabelValue.TEXT) ||
                    label.hasValueType(GoosciLabelValue.LabelValue.PICTURE) ||
                    label.hasValueType(GoosciLabelValue.LabelValue.SENSOR_TRIGGER))) {
                if (Log.isLoggable(TAG, Log.WARN)) {
                    Log.w(TAG, "How did we try to replace text on a non-text label?");
                }
                return;
            }
            int position = findLabelIndex(label);
            if (position == -1) {
                return;
            }
            mItems.get(position).mLabel = label;
            List<LabelValue> values = label.getLabelValues();
            mItems.get(position).mLabelValue = values.size() > 0 ? values.get(0) : null;
            notifyItemChanged(position);
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
            int expectedViewType = label.hasValueType(GoosciLabelValue.LabelValue.TEXT) ?
                    VIEW_TYPE_EXPERIMENT_TEXT_LABEL :
                    label.hasValueType(GoosciLabelValue.LabelValue.PICTURE) ?
                    VIEW_TYPE_EXPERIMENT_PICTURE_LABEL : VIEW_TYPE_EXPERIMENT_TRIGGER_LABEL;
            int position = -1;
            int size = mItems.size();
            for (int i = 0; i < size; i++) {
                ExperimentDetailItem item = mItems.get(i);
                if (item.getViewType() == expectedViewType) {
                    if (TextUtils.equals(label.getLabelId(), item.mLabel.getLabelId())) {
                        position = i;
                        break;
                    }
                }
            }
            return position;
        }

        public void insertNote(Label label) {
            int size = mItems.size();
            long timestamp = label.getTimeStamp();
            boolean inserted = false;
            int emptyIndex = -1;
            mHasRunsOrLabels = true;
            for (int i = 0; i < size; i++) {
                ExperimentDetailItem item = mItems.get(i);
                if (item.getViewType() == VIEW_TYPE_EXPERIMENT_DESCRIPTION) {
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

        public void setData(Experiment experiment, List<ExperimentRun> runs,
                ScalarDisplayOptions scalarDisplayOptions) {
            mHasRunsOrLabels = false;
            mExperiment = experiment;
            // TODO: compare data and see if anything has changed. If so, don't reload at all.
            mItems.clear();
            // As a safety check, if mSensorIndices is not the same size as the run list,
            // just ignore it.
            if (mSensorIndices != null && mSensorIndices.size() != runs.size()) {
                mSensorIndices = null;
            }
            int i = 0;
            for (ExperimentRun run : runs) {
                ExperimentDetailItem item = new ExperimentDetailItem(run, scalarDisplayOptions);
                item.setSensorTagIndex(mSensorIndices != null ? mSensorIndices.get(i++) : 0);
                mItems.add(item);
                mHasRunsOrLabels = true;
            }
            for (Label label : experiment.getExperiment().getLabels()) {
                mItems.add(new ExperimentDetailItem(label));
                mHasRunsOrLabels = true;
            }
            sortItems();

            if (!TextUtils.isEmpty(experiment.getExperiment().getDescription()) ||
                    experiment.getExperiment().isArchived()) {
                mItems.add(0, new ExperimentDetailItem(VIEW_TYPE_EXPERIMENT_DESCRIPTION));
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
                if (mExperiment.getExperiment().isArchived() ||
                        !TextUtils.isEmpty(mExperiment.getExperiment().getDescription())) {
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
                if (mExperiment.getExperiment().isArchived() ||
                        !TextUtils.isEmpty(mExperiment.getExperiment().getDescription())) {
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

        void bindRun(final ViewHolder holder, final ExperimentDetailItem item) {
            final ExperimentRun run = item.getRun();
            final Context applicationContext = holder.itemView.getContext().getApplicationContext();
            holder.setRunId(run.getTrialId());
            String title = run.getRunTitle(applicationContext);
            holder.runTitle.setText(title);
            holder.date.setTime(run.getFirstTimestamp());
            ElapsedTimeFormatter formatter = ElapsedTimeFormatter.getInstance(applicationContext);
            holder.duration.setText(formatter.format(run.elapsedSeconds()));
            holder.duration.setContentDescription(
                    formatter.formatForAccessibility(run.elapsedSeconds()));
            if (run.getNoteCount() > 0) {
                holder.noteCount.setVisibility(View.VISIBLE);
                holder.noteCount.setText(applicationContext.getResources().getQuantityString(
                        R.plurals.notes_count, run.getNoteCount(), run.getNoteCount()));
            } else {
                holder.noteCount.setVisibility(View.GONE);
            }
            holder.cardView.setOnClickListener(createRunClickListener(item.getSensorTagIndex()));
            holder.cardView.setTag(R.id.run_title_text, run.getTrialId());

            holder.itemView.findViewById(R.id.content).setAlpha(
                    applicationContext.getResources().getFraction(run.isArchived() ?
                        R.fraction.metadata_card_archived_alpha :
                        R.fraction.metadata_card_alpha, 1, 1));
            View archivedIndicator = holder.itemView.findViewById(R.id.archived_indicator);
            archivedIndicator.setVisibility(run.isArchived() ? View.VISIBLE :
                    View.GONE);
            if (run.isArchived()) {
                holder.runTitle.setContentDescription(applicationContext.getResources().getString(
                        R.string.archived_content_description, title));
            }

            ViewCompat.setTransitionName(holder.itemView, run.getTrialId());
            if (!run.isValidRun()) {
                removeSensorData(holder);

            } else if (run.getSensorIds().size() > 0) {
                loadSensorData(applicationContext, holder, item);
                holder.sensorNext.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        //Sometimes we tap the button before it can disable so return if the button
                        //should be disabled.
                        if (item.getSensorTagIndex() >= item.getRun().getSensorIds().size() - 1)
                            return;
                        item.setSensorTagIndex(item.getSensorTagIndex() + 1);
                        loadSensorData(applicationContext, holder, item);
                        GoosciSensorLayout.SensorLayout layout = item.getSelectedSensorLayout();
                        holder.cardView.setOnClickListener(createRunClickListener(
                                item.getSensorTagIndex()));
                        holder.setSensorId(layout.sensorId);
                    }
                });
                holder.sensorPrev.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
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
                    }
                });

            } else {
                removeSensorData(holder);
            }
        }

        private void removeSensorData(ViewHolder holder) {
            holder.sensorName.setText("");
            holder.setSensorId(null);
            setIndeterminateSensorData(holder);
        }

        private View.OnClickListener createRunClickListener(final int selectedSensorIndex) {
            return new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    AppCompatActivity activity = (AppCompatActivity) v.getContext();
                    String runId = (String) v.getTag(R.id.run_title_text);

                    ActivityOptionsCompat options = ActivityOptionsCompat
                            .makeSceneTransitionAnimation(activity, TransitionUtils
                                    .getTransitionPairs(activity, v, runId));
                    RunReviewActivity.launch(v.getContext(), runId, mExperiment.getExperimentId(),
                            selectedSensorIndex, false /* from record */, false /* create task */,
                            options.toBundle());
                }
            };
        }

        private void setIndeterminateSensorData(ViewHolder holder) {
            holder.statsLoadStatus = ViewHolder.STATS_LOAD_STATUS_LOADING;
            holder.statsList.clearStats();
        }

        private void loadSensorData(Context appContext, final ViewHolder holder,
                                    final ExperimentDetailItem item) {
            final ExperimentRun run = item.getRun();
            final String sensorId = run.getSensorIds().get(item.getSensorTagIndex());

            final SensorAppearance appearance = AppSingleton.getInstance(appContext)
                    .getSensorAppearanceProvider()
                    .getAppearance(sensorId);
            final NumberFormat numberFormat = appearance.getNumberFormat();
            holder.sensorName.setText(Appearances.getSensorDisplayName(appearance, appContext));
            final GoosciSensorLayout.SensorLayout sensorLayout = item.getSelectedSensorLayout();
            Appearances.applyDrawableToImageView(appearance.getIconDrawable(appContext),
                    holder.sensorImage, sensorLayout.color);

            boolean hasNextButton = item.getSensorTagIndex() < run.getSensorIds().size() - 1;
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

            final TrialStats stats = run.getTrial().getStatsForSensor(sensorId);
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
            chartController.loadRunData(run, sensorLayout, dc, holder, stats,
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
                    });
        }

        void sortItems() {
            Collections.sort(mItems, new Comparator<ExperimentDetailItem>() {
                @Override
                public int compare(ExperimentDetailItem lhs, ExperimentDetailItem rhs) {
                    return Long.compare(rhs.getTimestamp(), lhs.getTimestamp());
                }
            });
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
                ExperimentRun run = mItems.get(i).getRun();
                if (run == null) {
                    continue;
                }
                if (TextUtils.equals(statsRunId, run.getTrialId())) {
                    // Reload the experiment run since the stats have changed.
                    final int trialIndex = i;
                    dc.getExperimentRun(run.getExperimentId(), run.getTrialId(),
                            new LoggingConsumer<ExperimentRun>(TAG, "load experiment run") {
                                @Override
                                public void success(final ExperimentRun run) {
                                    // Rebind the View Holder to reload the stats and graphs.
                                    mItems.get(trialIndex).mRun = run;
                                    notifyItemChanged(trialIndex);
                                }
                            });
                    return;
                }
            }
        }

        public static class ViewHolder extends RecyclerView.ViewHolder implements
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

            int statsLoadStatus;

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
            public ProgressBar progressView;
            public TrialStats currentSensorStats;

            public ViewHolder(View itemView, int viewType) {
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
                } else if (mViewType == VIEW_TYPE_EXPERIMENT_PICTURE_LABEL ||
                        mViewType == VIEW_TYPE_EXPERIMENT_TEXT_LABEL ||
                        mViewType == VIEW_TYPE_EXPERIMENT_TRIGGER_LABEL) {
                    cardView = itemView.findViewById(R.id.card_view);
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

        /**
         * Represents a detail item: either a run or an experiment level label or a special card.
         * <p>
         * TODO: might be able to rework this when Run objects exist.
         */
        public static class ExperimentDetailItem {
            private final int mViewType;
            private ExperimentRun mRun;
            private int mSensorTagIndex = -1;
            private Label mLabel;
            private LabelValue mLabelValue;
            private long mTimestamp;
            private ChartController mChartController;

            ExperimentDetailItem(ExperimentRun run, ScalarDisplayOptions scalarDisplayOptions) {
                mRun = run;
                mTimestamp = mRun.getFirstTimestamp();
                mViewType = VIEW_TYPE_RUN_CARD;
                mSensorTagIndex = run.getSensorIds().size() > 0 ? 0 : -1;
                mChartController = new ChartController(
                        ChartOptions.ChartPlacementType.TYPE_PREVIEW_REVIEW,
                        scalarDisplayOptions);
            }

            ExperimentDetailItem(Label label) {
                mLabel = label;
                if (label.hasValueType(GoosciLabelValue.LabelValue.TEXT)) {
                    mLabelValue = label.getLabelValue(GoosciLabelValue.LabelValue.TEXT);
                    mViewType = VIEW_TYPE_EXPERIMENT_TEXT_LABEL;
                } else if (label.hasValueType(GoosciLabelValue.LabelValue.PICTURE)) {
                    mLabelValue = label.getLabelValue(GoosciLabelValue.LabelValue.PICTURE);
                    mViewType = VIEW_TYPE_EXPERIMENT_PICTURE_LABEL;
                } else {
                    mLabelValue = label.getLabelValue(GoosciLabelValue.LabelValue.SENSOR_TRIGGER);
                    mViewType = VIEW_TYPE_EXPERIMENT_TRIGGER_LABEL;
                }
                mTimestamp = label.getTimeStamp();
            }

            ExperimentDetailItem(int viewType) {
                mViewType = viewType;
            }

            int getViewType() {
                return mViewType;
            }

            long getTimestamp() {
                return mTimestamp;
            }

            ExperimentRun getRun() {
                return mRun;
            }

            int getSensorTagIndex() {
                return mSensorTagIndex;
            }

            GoosciSensorLayout.SensorLayout getSelectedSensorLayout() {
                return mRun.getSensorLayouts().get(mSensorTagIndex);
            }

            String getNextSensorId() {
                return mRun.getSensorIds().get(mSensorTagIndex + 1);
            }

            String getPrevSensorId() {
                return mRun.getSensorIds().get(mSensorTagIndex - 1);
            }

            void setSensorTagIndex(int index) {
                mSensorTagIndex = index;
            }

            ChartController getChartController() {
                return mChartController;
            }
        }
    }
}
