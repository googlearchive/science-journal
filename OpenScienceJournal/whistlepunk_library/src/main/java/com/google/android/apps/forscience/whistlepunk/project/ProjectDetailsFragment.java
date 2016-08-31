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

package com.google.android.apps.forscience.whistlepunk.project;

import android.app.Fragment;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.support.design.widget.CollapsingToolbarLayout;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v4.app.ActivityOptionsCompat;
import android.support.v4.app.NavUtils;
import android.support.v4.graphics.drawable.RoundedBitmapDrawable;
import android.support.v4.graphics.drawable.RoundedBitmapDrawableFactory;
import android.support.v4.view.ViewCompat;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.text.TextUtils;
import android.text.format.DateUtils;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.target.BitmapImageViewTarget;
import com.google.android.apps.forscience.javalib.Success;
import com.google.android.apps.forscience.whistlepunk.AccessibilityUtils;
import com.google.android.apps.forscience.whistlepunk.AppSingleton;
import com.google.android.apps.forscience.whistlepunk.DataController;
import com.google.android.apps.forscience.whistlepunk.LoggingConsumer;
import com.google.android.apps.forscience.whistlepunk.MainActivity;
import com.google.android.apps.forscience.whistlepunk.R;
import com.google.android.apps.forscience.whistlepunk.WhistlePunkApplication;
import com.google.android.apps.forscience.whistlepunk.analytics.TrackerConstants;
import com.google.android.apps.forscience.whistlepunk.metadata.Experiment;
import com.google.android.apps.forscience.whistlepunk.metadata.ExperimentRun;
import com.google.android.apps.forscience.whistlepunk.metadata.Label;
import com.google.android.apps.forscience.whistlepunk.metadata.PictureLabel;
import com.google.android.apps.forscience.whistlepunk.metadata.Project;
import com.google.android.apps.forscience.whistlepunk.project.experiment.ExperimentDetailsActivity;
import com.google.android.apps.forscience.whistlepunk.project.experiment.UpdateExperimentActivity;
import com.google.android.apps.forscience.whistlepunk.review.DeleteMetadataItemDialog;

import java.util.ArrayList;
import java.util.List;

/**
 * Project detail fragment also contains project experiments list.
 */
public class ProjectDetailsFragment extends Fragment implements
        DeleteMetadataItemDialog.DeleteDialogListener {

    private static final String TAG = "ProjectDetailsFragment";
    public static final String ARG_PROJECT_ID = "project_id";

    /**
     * Boolen extra for savedInstanceState with the state of includeArchived experiments.
     */
    private static final String EXTRA_INCLUDE_ARCHIVED = "includeArchived";

    private String mProjectId;
    private Project mProject;

    private ProjectDetailAdapter mProjectDetailAdapter;
    private boolean mIncludeArchived;
    private ImageView mProjectCover;

    public static ProjectDetailsFragment newInstance(String projectId) {
        ProjectDetailsFragment fragment = new ProjectDetailsFragment();
        Bundle args = new Bundle();
        args.putString(ARG_PROJECT_ID, projectId);
        fragment.setArguments(args);
        return fragment;
    }

    public ProjectDetailsFragment() {
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mIncludeArchived = (savedInstanceState != null) ? savedInstanceState.getBoolean(
                EXTRA_INCLUDE_ARCHIVED, false) : false;
        setHasOptionsMenu(true);
    }

    @Override
    public void onResume() {
        super.onResume();
        WhistlePunkApplication.getUsageTracker(getActivity()).trackScreenView(
                TrackerConstants.SCREEN_PROJECT_DETAIL);
        mProjectId = getArguments().getString(ARG_PROJECT_ID);
        getDataController().getProjectById(mProjectId,
                new LoggingConsumer<Project>(TAG, "Retrieve project") {
                    @Override
                    public void success(Project project) {
                        attachProjectDetails(project);
                        loadExperiments();
                    }
                });
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_project_details, container, false);

        AppCompatActivity activity = (AppCompatActivity) getActivity();
        Toolbar toolbar = (Toolbar) view.findViewById(R.id.toolbar);
        activity.setSupportActionBar(toolbar);

        ActionBar actionBar = activity.getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
            actionBar.setHomeButtonEnabled(true);
        }

        mProjectCover = (ImageView) view.findViewById(R.id.project_cover);
        ViewCompat.setTransitionName(mProjectCover, getArguments().getString(ARG_PROJECT_ID));

        RecyclerView detailList = (RecyclerView) view.findViewById(R.id.details);
        mProjectDetailAdapter = new ProjectDetailAdapter(getActivity());
        detailList.setLayoutManager(new LinearLayoutManager(getActivity(),
                LinearLayoutManager.VERTICAL, false));
        detailList.setAdapter(mProjectDetailAdapter);

        FloatingActionButton newExperimentButton = (FloatingActionButton) view.findViewById(
                R.id.new_experiment);
        newExperimentButton.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                getDataController().createExperiment(mProject,
                        new LoggingConsumer<Experiment>(TAG, "Create a new experiment") {
                            @Override
                            public void success(final Experiment experiment) {
                                UpdateExperimentActivity.launch(getActivity(),
                                        experiment.getExperimentId(), true /* is new */);
                            }
                        });
            }
        });

        return view;
    }

    private void attachProjectDetails(final Project project) {
        mProject = project;
        final View rootView = getView();
        if (rootView == null) {
            return;
        }

        String title = project.getDisplayTitle(getActivity());
        getActivity().setTitle(title);
        CollapsingToolbarLayout collapsingToolbar = (CollapsingToolbarLayout) rootView.findViewById(
                R.id.collapsing_toolbar);
        collapsingToolbar.setTitle(title);
        collapsingToolbar.setContentDescription(title);

        if (!TextUtils.isEmpty(project.getCoverPhoto())) {
            Glide.with(getActivity())
                    .load(project.getCoverPhoto())
                    .placeholder(R.drawable.placeholder_project)
                    .centerCrop()
                    .into(mProjectCover);
        } else {
            mProjectCover.setImageResource(R.drawable.placeholder_project);
        }
    }

    private void loadExperiments() {
        getDataController().getExperimentsForProject(mProject, mIncludeArchived,
                new LoggingConsumer<List<Experiment>>(TAG,
                        "Retrieve project experiments") {
                    @Override
                    public void success(List<Experiment> experiments) {
                        attachToExperiments(experiments);
                    }
                });
    }

    private void attachToExperiments(final List<Experiment> experiments) {
        final View rootView = getView();
        if (rootView == null) {
            return;
        }
        mProjectDetailAdapter.setData(mProject, experiments);
    }

    private DataController getDataController() {
        return AppSingleton.getInstance(getActivity()).getDataController();
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);
        inflater.inflate(R.menu.menu_project_details, menu);
    }

    @Override
    public void onPrepareOptionsMenu(Menu menu) {
        MenuItem archiveButton = menu.findItem(R.id.action_archive_project);
        MenuItem unarchiveButton = menu.findItem(R.id.action_unarchive_project);
        MenuItem deleteButton = menu.findItem(R.id.action_delete_project);

        // If the project hasn't loaded yet hide both options.
        if (mProject == null) {
            archiveButton.setVisible(false);
            unarchiveButton.setVisible(false);
            deleteButton.setVisible(false);
        } else {
            // Show the archive button if the project is not already archived.
            archiveButton.setVisible(!mProject.isArchived());
            // Show the unarchive button if it's already archived.
            unarchiveButton.setVisible(mProject.isArchived());
            deleteButton.setVisible(true);
            deleteButton.setEnabled(mProject.isArchived());
        }
        MenuItem includeArchived = menu.findItem(R.id.action_include_archived);
        includeArchived.setChecked(mIncludeArchived);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == android.R.id.home) {
            Intent upIntent = NavUtils.getParentActivityIntent(getActivity());
            upIntent.putExtra(MainActivity.ARG_SELECTED_NAV_ITEM_ID, R.id.navigation_item_projects);
            upIntent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            ActivityOptionsCompat options = ActivityOptionsCompat.makeSceneTransitionAnimation(
                    getActivity(), mProjectCover, mProjectId);
            getActivity().startActivity(upIntent, options.toBundle());
            return true;
        } else if (id == R.id.action_edit_project) {
            UpdateProjectActivity.launch(getActivity(), mProjectId, false /* not new */);
            return true;
        } else if (id == R.id.action_archive_project || id == R.id.action_unarchive_project) {
            setProjectArchived(id == R.id.action_archive_project);
            return true;

        } else if (id == R.id.action_delete_project) {
            confirmDelete();
            return true;
        } else if (id == R.id.action_include_archived) {
            item.setChecked(!item.isChecked());
            mIncludeArchived = item.isChecked();
            loadExperiments();
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    private void setProjectArchived(final boolean archived) {
        mProject.setArchived(archived);
        getDataController().updateProject(mProject,
                new LoggingConsumer<Success>(TAG, "archive/unarchive project") {
                    @Override
                    public void success(Success value) {
                        Snackbar bar = AccessibilityUtils.makeSnackbar(
                                getView(), getResources().getString(archived ?
                                        R.string.archived_project_message :
                                        R.string.unarchived_project_message),
                                Snackbar.LENGTH_LONG);

                        if (archived) {
                            bar.setAction(R.string.action_undo, new View.OnClickListener() {
                                @Override
                                public void onClick(View v) {
                                    setProjectArchived(false);
                                }
                            });
                        }
                        bar.show();
                    }
                });
        WhistlePunkApplication.getUsageTracker(getActivity())
                .trackEvent(TrackerConstants.CATEGORY_PROJECTS,
                        archived ? TrackerConstants.ACTION_ARCHIVE :
                                TrackerConstants.ACTION_UNARCHIVE,
                        null, 0);
    }

    private void confirmDelete() {
        DeleteMetadataItemDialog dialog = DeleteMetadataItemDialog.newInstance(
                R.string.delete_project_dialog_title, R.string.delete_project_dialog_message);
        dialog.show(getChildFragmentManager(), DeleteMetadataItemDialog.TAG);
    }

    @Override
    public void requestDelete(Bundle extras) {
        getDataController().deleteProject(mProject, new LoggingConsumer<Success>(TAG,
                "Delete project") {
            @Override
            public void success(Success value) {
                getActivity().finish();
            }
        });
    }

    public static class ProjectDetailAdapter extends RecyclerView.Adapter<ViewHolder> {
        static final int VIEW_TYPE_EXPERIMENT = 0;
        static final int VIEW_TYPE_DESCRIPTION = 1;
        static final int VIEW_TYPE_EMPTY = 2;
        private final Drawable mPlaceHolderImage;

        private Project mProject;
        private List<Experiment> mExperiments;
        private int mExperimentStartPosition;

        public ProjectDetailAdapter(Context context) {
            mExperiments = new ArrayList<>();
            mPlaceHolderImage = context.getResources().getDrawable(
                    R.drawable.placeholder_experiment);
        }

        void setData(Project project, List<Experiment> experiments) {
            mProject = project;
            mExperiments.clear();
            mExperiments.addAll(experiments);
            if (TextUtils.isEmpty(mProject.getDescription()) && !mProject.isArchived()) {
                // Don't need to show the metadata card in this case.
                mExperimentStartPosition = 0;
            } else {
                mExperimentStartPosition = 1;
            }
            notifyDataSetChanged();
        }

        @Override
        public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            LayoutInflater inflater = LayoutInflater.from(parent.getContext());
            View view = null;
            if (viewType == VIEW_TYPE_DESCRIPTION) {
                view = inflater.inflate(R.layout.metadata_description, parent, false);
            } else if (viewType == VIEW_TYPE_EMPTY) {
                view = inflater.inflate(R.layout.empty_list, parent, false);
            } else {
                view = inflater.inflate(R.layout.project_experiment_overview, parent, false);
            }
            return new ViewHolder(view, viewType);
        }

        @Override
        public void onBindViewHolder(ViewHolder holder, int position) {
            if (holder.viewType == VIEW_TYPE_DESCRIPTION) {
                TextView description = ((TextView) holder.itemView.findViewById(
                        R.id.metadata_description));
                description.setBackgroundColor(holder.itemView.getContext().getResources()
                        .getColor(R.color.color_primary_dark));
                description.setText(mProject.getDescription());
                description.setVisibility(TextUtils.isEmpty(mProject.getDescription()) ? View.GONE :
                        View.VISIBLE);
                View archivedIndicator = holder.itemView.findViewById(R.id.archived_indicator);
                archivedIndicator.setVisibility(mProject.isArchived() ? View.VISIBLE : View.GONE);
            } else if (holder.viewType == VIEW_TYPE_EXPERIMENT) {
                bindExperiment(holder, mExperiments.get(position - mExperimentStartPosition));
            }
        }

        @Override
        public int getItemCount() {
            if (mProject != null) {
                // There is always an "experiment" due to the empty view.
                return Math.max(mExperiments.size(), 1) + mExperimentStartPosition;
            } else {
                return 0;
            }
        }

        @Override
        public int getItemViewType(int position) {
            if (position == mExperimentStartPosition - 1) {
                return VIEW_TYPE_DESCRIPTION;
            } else if (mExperiments.size() > 0){
                return VIEW_TYPE_EXPERIMENT;
            } else {
                return VIEW_TYPE_EMPTY;
            }
        }

        private void bindExperiment(final ViewHolder holder, final Experiment experiment) {
            Resources res = holder.itemView.getResources();
            // First on the UI thread, set what experiment we're trying to load.
            holder.overviewLoadStatus = ViewHolder.LOAD_STATUS_IN_PROGRESS;
            holder.experimentId = experiment.getExperimentId();

            // Set the data we know about.
            String experimentText = experiment.getDisplayTitle(holder.itemView.getContext());
            holder.experimentTitle.setText(experimentText);
            holder.experimentImage.setImageDrawable(mPlaceHolderImage);
            // Set indeterminate states on the things we don't know.
            holder.experimentLastRun.setText("");
            holder.experimentRunTotals.setText("");
            holder.archivedIndicator.setVisibility(experiment.isArchived() ? View.VISIBLE :
                    View.GONE);
            holder.itemView.setAlpha(res.getFraction(experiment.isArchived() ?
                    R.fraction.metadata_card_archived_alpha :
                    R.fraction.metadata_card_alpha, 1, 1));
            if (experiment.isArchived()) {
                holder.experimentTitle.setContentDescription(res.getString(
                        R.string.archived_content_description, experimentText));
            } else {
                // Use default.
                holder.experimentTitle.setContentDescription("");
            }

            holder.itemView.setTag(R.id.experiment_title, experiment.getExperimentId());

            final DataController dc = AppSingleton.getInstance(
                    holder.itemView.getContext()).getDataController();

            holder.itemView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    ExperimentDetailsActivity.launch(v.getContext(), experiment.getExperimentId());
                }
            });

            // Start loading data for runs.
            dc.getExperimentRuns(
                    experiment.getExperimentId(), /* don't include archived runs */ false,
                    new LoggingConsumer<List<ExperimentRun>>(TAG, "loading runs") {

                        @Override
                        public void success(List<ExperimentRun> runs) {
                            if (!holder.experimentId.equals(experiment.getExperimentId())) {
                                // Only load the data if we are have retrieved data for the same
                                // item. Otherwise, exit.
                                return;
                            }
                            holder.overviewLoadStatus = ViewHolder.LOAD_STATUS_IDLE;
                            loadRunData(holder, experiment, runs);
                        }
                    });
        }

        private void loadRunData(final ViewHolder holder, final Experiment experiment,
                                 List<ExperimentRun> runs) {
            Context context = holder.itemView.getContext();
            holder.experimentRunTotals.setText(context.getResources()
                    .getQuantityString(R.plurals.experiment_run_count, runs.size(), runs.size()));
            final DataController dc = AppSingleton.getInstance(
                    holder.itemView.getContext()).getDataController();
            final String experimentId = holder.experimentId;
            if (runs.size() > 0) {
                // Take the first run, which should be the last created run.
                holder.experimentLastRun.setText(
                        DateUtils.getRelativeDateTimeString(context,
                                runs.get(0).getFirstTimestamp(),
                                DateUtils.MINUTE_IN_MILLIS,
                                DateUtils.MINUTE_IN_MILLIS,
                                DateUtils.FORMAT_ABBREV_RELATIVE));

                PictureLabel expPhoto = null;
                for (ExperimentRun run : runs) {
                    if (run.getCoverPictureLabel() != null) {
                        expPhoto = run.getCoverPictureLabel();
                        break;
                    }
                }
                if (expPhoto != null) {
                    loadPhoto(holder, expPhoto);
                } else {
                    loadPhotoFromExperimentLabels(holder, experiment, dc, experimentId);
                }
            } else {
                holder.experimentLastRun.setText("");
                loadPhotoFromExperimentLabels(holder, experiment, dc, experimentId);
            }
        }

        private void loadPhotoFromExperimentLabels(final ViewHolder holder, Experiment experiment,
                                                   DataController dc, final String experimentId) {
            holder.labelLoadStatus = ViewHolder.LOAD_STATUS_IN_PROGRESS;
            dc.getLabelsForExperiment(experiment, new LoggingConsumer<List<Label>>(TAG,
                    "Loading labels") {
                @Override
                public void success(List<Label> labels) {
                    if (!experimentId.equals(holder.experimentId) ||
                            holder.labelLoadStatus != ViewHolder.LOAD_STATUS_IN_PROGRESS) {
                        // Don't load data if the experiment ID of the holder has changed or
                        // if we didn't think we were loading.
                        return;
                    }
                    holder.labelLoadStatus = ViewHolder.LOAD_STATUS_IDLE;
                    for (Label label : labels) {
                        if (label instanceof PictureLabel) {
                            loadPhoto(holder, (PictureLabel) label);
                            break;
                        }
                    }
                }
            });
        }

        private void loadPhoto(final ViewHolder holder, PictureLabel expPhoto) {
            Glide.with(holder.experimentImage.getContext())
                    .load(expPhoto.getFilePath())
                    .asBitmap()
                    .centerCrop()
                    .into(new BitmapImageViewTarget(holder.experimentImage) {
                        @Override
                        protected void setResource(Bitmap resource) {
                            RoundedBitmapDrawable experimentDrawable =
                                    RoundedBitmapDrawableFactory.create(
                                            holder.itemView.getContext().getResources(),
                                            resource);
                            experimentDrawable.setCircular(true);
                            holder.experimentImage.setImageDrawable(experimentDrawable);
                        }
                    });
        }
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {

        static final int LOAD_STATUS_IN_PROGRESS = 1;
        static final int LOAD_STATUS_IDLE = 2;

        // Accessing via fields for faster access.

        /**
         * Experiment ID that is being loaded or has been loaded.
         */
        String experimentId;

        /**
         * Load status for the experiment overview.
         */
        int overviewLoadStatus;

        /**
         * Load status for labels.
         */
        int labelLoadStatus;

        TextView experimentTitle;
        TextView experimentLastRun;
        TextView experimentRunTotals;
        ImageView experimentImage;
        View archivedIndicator;

        int viewType;

        public ViewHolder(View itemView, int viewType) {
            super(itemView);
            this.viewType = viewType;
            if (viewType == ProjectDetailAdapter.VIEW_TYPE_EXPERIMENT) {
                experimentImage = (ImageView) itemView.findViewById(R.id.experiment_image);
                experimentTitle = (TextView) itemView.findViewById(R.id.experiment_title);
                experimentLastRun = (TextView) itemView.findViewById(R.id.experiment_last_run);
                experimentRunTotals = (TextView) itemView.findViewById(R.id.experiment_total_runs);
                archivedIndicator = (View) itemView.findViewById(R.id.archived_indicator);
            }
        }
    }
}
