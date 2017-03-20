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
import android.os.Bundle;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v4.app.ActivityOptionsCompat;
import android.support.v4.view.ViewCompat;
import android.support.v7.widget.CardView;
import android.support.v7.widget.DefaultItemAnimator;
import android.support.v7.widget.GridLayoutManager;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.PopupMenu;
import android.widget.TextView;

import com.bumptech.glide.Glide;
import com.google.android.apps.forscience.javalib.MaybeConsumer;
import com.google.android.apps.forscience.javalib.Success;
import com.google.android.apps.forscience.whistlepunk.AccessibilityUtils;
import com.google.android.apps.forscience.whistlepunk.AppSingleton;
import com.google.android.apps.forscience.whistlepunk.DataController;
import com.google.android.apps.forscience.whistlepunk.LoggingConsumer;
import com.google.android.apps.forscience.whistlepunk.R;
import com.google.android.apps.forscience.whistlepunk.WhistlePunkApplication;
import com.google.android.apps.forscience.whistlepunk.analytics.TrackerConstants;
import com.google.android.apps.forscience.whistlepunk.metadata.Experiment;
import com.google.android.apps.forscience.whistlepunk.metadata.Project;
import com.google.android.apps.forscience.whistlepunk.project.experiment.ExperimentDetailsActivity;
import com.google.android.apps.forscience.whistlepunk.project.experiment.UpdateExperimentActivity;
import com.google.android.apps.forscience.whistlepunk.review.DeleteMetadataItemDialog;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * A simple {@link Fragment} subclass.
 * Use the {@link ProjectTabsFragment#newInstance} factory method to
 * create an instance of this fragment.
 */
public class ProjectTabsFragment extends Fragment implements
        DeleteMetadataItemDialog.DeleteDialogListener {

    private static final String TAG = "ProjectFragment";

    /**
     * Boolean extra in instance state if we are including archived items.
     */
    private String EXTRA_INCLUDE_ARCHIVED = "includeArchived";

    private static final String ARG_DELETE_PROJECT_ID = "delete_project_id";
    private static final String ARG_DELETE_PROJECT_POSITION = "delete_project_position";

    private RecyclerView mRecyclerView;
    private TextView mEmptyView;
    private ProjectAdapter mAdapter;
    private boolean mIncludeArchived;
    private Snackbar mUndoSnackbar;

    public static Fragment newInstance() {
        return new ProjectTabsFragment();
    }

    public ProjectTabsFragment() {
        // Required empty public constructor
    }

    @Override
    public void onStart() {
        super.onStart();

        WhistlePunkApplication.getUsageTracker(getActivity()).trackScreenView(
                TrackerConstants.SCREEN_PROJECTS);
    }

    @Override
    public void onResume() {
        super.onResume();
        setHasOptionsMenu(true);
        loadProjects();
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean(EXTRA_INCLUDE_ARCHIVED, mIncludeArchived);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        Resources res = getResources();
        View view = inflater.inflate(R.layout.fragment_project_list, container, false);
        mEmptyView = (TextView) view.findViewById(R.id.empty);
        mEmptyView.setText(res.getString(R.string.empty_project_library));
        mEmptyView.setCompoundDrawablesRelativeWithIntrinsicBounds(null, null, null,
                res.getDrawable(R.drawable.empty_project));
        mRecyclerView = (RecyclerView) view.findViewById(R.id.projects_list);
        mRecyclerView.setLayoutManager(new GridLayoutManager(getActivity(),
                view.getContext().getResources().getInteger(R.integer.project_library_columns)));
        mRecyclerView.setItemAnimator(new DefaultItemAnimator() {

            private void setAlpha(RecyclerView.ViewHolder item) {
                // The default item animator messes with the alpha, so we need to reset the alpha.
                ProjectAdapter.CardViewHolder holder = (ProjectAdapter.CardViewHolder) item;
                holder.itemView.setAlpha(holder.itemView.getResources().getFraction(
                        holder.archivedIndicator.getVisibility() == View.VISIBLE ?
                                R.fraction.metadata_card_archived_alpha :
                                R.fraction.metadata_card_alpha, 1, 1));
            }

            @Override
            public void onAddFinished(RecyclerView.ViewHolder item) {
                setAlpha(item);
            }

            @Override
            public void onChangeFinished(RecyclerView.ViewHolder item, boolean oldItem) {
                setAlpha(item);
            }
        });
        mAdapter = new ProjectAdapter();
        mRecyclerView.setAdapter(mAdapter);
        if (savedInstanceState != null) {
            mIncludeArchived = savedInstanceState.getBoolean(EXTRA_INCLUDE_ARCHIVED, false);
            getActivity().invalidateOptionsMenu();
        }
        FloatingActionButton createProjectBtn = (FloatingActionButton) view.findViewById(
                R.id.create_project_button);
        createProjectBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                getDataController().createProject(
                        new LoggingConsumer<Project>(TAG, "Create project") {
                            @Override
                            public void success(Project project) {
                                UpdateProjectActivity.launch(getActivity(), project.getProjectId(),
                                        true /* new project */);
                            }
                        });
            }
        });

        return view;
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);
        inflater.inflate(R.menu.menu_project_list, menu);
    }

    @Override
    public void onPrepareOptionsMenu(Menu menu) {
        super.onPrepareOptionsMenu(menu);
        menu.findItem(R.id.action_include_archived).setVisible(!mIncludeArchived);
        menu.findItem(R.id.action_exclude_archived).setVisible(mIncludeArchived);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.action_include_archived) {
            mIncludeArchived = true;
            loadProjects();
            getActivity().invalidateOptionsMenu();
            return true;
        } else if (item.getItemId() == R.id.action_exclude_archived) {
            mIncludeArchived = false;
            loadProjects();
            getActivity().invalidateOptionsMenu();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private DataController getDataController() {
        return AppSingleton.getInstance(getActivity()).getDataController();
    }

    private void loadProjects() {
        // TODO: remove limit on projects.
        getDataController().getProjects(100, mIncludeArchived,
                new LoggingConsumer<List<Project>>(TAG, "retrieve projects") {
                    @Override
                    public void success(List<Project> projects) {
                        attachToProjects(projects);
                    }
                });
    }

    private void attachToProjects(final List<Project> projects) {
        setContentViewVisibility(projects.size() > 0);

        final View rootView = getView();
        if (rootView == null) {
            return;
        }
        getDataController().getLastUsedProject(new LoggingConsumer<Project>(TAG,
                "Loading last used") {
            @Override
            public void success(Project value) {
                mAdapter.setProjects(projects, value != null ? value.getProjectId() : null);
            }
        });
    }

    private void setContentViewVisibility(boolean visible) {
        if (visible) {
            mEmptyView.setVisibility(View.GONE);
            mRecyclerView.setVisibility(View.VISIBLE);
        } else {
            mEmptyView.setVisibility(View.VISIBLE);
            mRecyclerView.setVisibility(View.GONE);
        }
    }

    @Override
    public void requestDelete(Bundle extras) {
        String projectId = extras.getString(ARG_DELETE_PROJECT_ID, "");
        final int position = extras.getInt(ARG_DELETE_PROJECT_POSITION, -1);
        Project project = mAdapter.getItem(position);
        if (project.getProjectId().equals(projectId)) {
            getDataController().deleteProject(project,
                    new LoggingConsumer<Success>(TAG, "Delete") {
                        @Override
                        public void success(Success value) {
                            mAdapter.remove(position);
                        }
                    });
        } else {
            Log.e(TAG, "Could not delete project " + projectId + " since position doesn't match");
        }
    }

    private class ProjectAdapter extends RecyclerView.Adapter<ProjectAdapter.CardViewHolder> {

        private final List<Project> mProjects;
        private String mActiveProjectId;

        public ProjectAdapter() {
            mProjects = new ArrayList<>();
        }

        void setProjects(List<Project> projects, String activeProjectId) {
            if (Objects.equals(mActiveProjectId, activeProjectId)
                    && Arrays.deepEquals(mProjects.toArray(), projects.toArray())) {
                // Everything is equal. Don't destroy all the views.
                return;
            }

            mProjects.clear();
            mProjects.addAll(projects);
            mActiveProjectId = activeProjectId;
            notifyDataSetChanged();
        }

        Project getItem(int position) {
            return mProjects.get(position);
        }

        void insert(Project project, int position) {
            mProjects.add(position, project);
            notifyItemInserted(position);
            notifyItemRangeChanged(position, mAdapter.getItemCount());
            setContentViewVisibility(mAdapter.getItemCount() > 0);
        }

        void remove(int position) {
            mProjects.remove(position);
            notifyItemRemoved(position);
            notifyItemRangeChanged(position, mAdapter.getItemCount());
            setContentViewVisibility(mAdapter.getItemCount() > 0);
        }

        @Override
        public CardViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            CardView view = (CardView) LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.project_card, parent, false);
            return new CardViewHolder(view);
        }

        @Override
        public void onBindViewHolder(final CardViewHolder holder, int position) {
            final Project project = mProjects.get(position);
            Resources res = holder.itemView.getContext().getResources();
            String projectText = project.getDisplayTitle(getActivity());
            holder.projectTitle.setText(projectText);
            holder.projectExperimentsCount.setText("");
            holder.activeIndicator.setVisibility(project.getProjectId().equals(mActiveProjectId) ?
                    View.VISIBLE : View.GONE);
            holder.archivedIndicator.setVisibility(project.isArchived() ? View.VISIBLE : View.GONE);
            if (TextUtils.isEmpty(project.getCoverPhoto())) {
                holder.projectCover.setImageResource(R.drawable.placeholder_project);
            } else {
                Glide.with(holder.itemView.getContext())
                        .load(project.getCoverPhoto())
                        .crossFade()
                        .centerCrop()
                        .error(R.drawable.placeholder_project)
                        .into(holder.projectCover);
            }

            holder.itemView.setAlpha(res.getFraction(project.isArchived() ?
                    R.fraction.metadata_card_archived_alpha : R.fraction.metadata_card_alpha, 1,
                    1));
            if (project.isArchived()) {
                holder.projectTitle.setContentDescription(res.getString(
                        R.string.archived_content_description, projectText));
            } else {
                // Use default.
                holder.projectTitle.setContentDescription("");
            }

            holder.itemView.findViewById(R.id.card_menu).setOnClickListener(
                    new View.OnClickListener() {

                        @Override
                        public void onClick(View v) {
                            showPopup(v, project, holder.getAdapterPosition());
                        }
                    });
            getDataController().getExperimentsForProject(project, false /* no archived */,
                    new MaybeConsumer<List<Experiment>>() {
                        @Override
                        public void success(List<Experiment> value) {
                            if (getActivity() == null) {
                                return;
                            }

                            String experimentCountText = getActivity().getResources()
                                    .getQuantityString(R.plurals.project_experiments_count,
                                            value.size(), value.size());
                            holder.projectExperimentsCount.setText(experimentCountText);
                        }

                        @Override
                        public void fail(Exception e) {
                            holder.projectExperimentsCount.setText("");
                        }
                    });

            // Launch project details activity if the card is clicked.
            holder.cardView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    ActivityOptionsCompat options = ActivityOptionsCompat
                            .makeSceneTransitionAnimation(getActivity(), holder.projectCover,
                                    project.getProjectId());
                    ProjectDetailsActivity.launch(getActivity(), project.getProjectId(),
                            options.toBundle());
                }
            });
            ViewCompat.setTransitionName(holder.projectCover, project.getProjectId());
        }

        @Override
        public int getItemCount() {
            return mProjects.size();
        }

        private void showPopup(View view, final Project project, final int position) {
            final Context context = view.getContext();
            PopupMenu menu = new PopupMenu(context, view);
            MenuInflater inflater = menu.getMenuInflater();
            inflater.inflate(R.menu.menu_project_card, menu.getMenu());
            menu.getMenu().findItem(R.id.action_archive_project).setVisible(!project.isArchived());
            menu.getMenu().findItem(R.id.action_unarchive_project).setVisible(project.isArchived());
            menu.getMenu().findItem(R.id.action_delete_project).setEnabled(project.isArchived());

            menu.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {

                @Override
                public boolean onMenuItemClick(MenuItem item) {
                    int itemId = item.getItemId();
                    if (itemId == R.id.action_new_experiment) {
                        getDataController().createExperiment(project,
                                new LoggingConsumer<Experiment>(TAG, "create experiment") {
                                    @Override
                                    public void success(Experiment value) {
                                        context.startActivities(new Intent[] {
                                                ProjectDetailsActivity.getLaunchIntent(context,
                                                        value.getProjectId()),
                                                ExperimentDetailsActivity.getLaunchIntent(
                                                        context, value.getExperimentId(),
                                                        false),
                                                UpdateExperimentActivity.getLaunchIntent(
                                                        context, value.getExperimentId(),
                                                        true /* new */, null)
                                        });
                                    }
                                });
                    } else if (itemId == R.id.action_archive_project
                            || itemId == R.id.action_unarchive_project) {
                        setProjectArchived(project,
                                item.getItemId() == R.id.action_archive_project, position);
                    } else if (itemId == R.id.action_delete_project) {
                        confirmDelete(project, position);
                    }
                    return true;
                }
            });
            menu.show();
        }

        private void setProjectArchived(final Project project, final boolean archived,
                final int position) {
            project.setArchived(archived);
            getDataController().updateProject(project,
                    new LoggingConsumer<Success>(TAG, "Update project") {
                        @Override
                        public void success(Success value) {
                            if (mIncludeArchived) {
                                // Just update, it's cleaner.
                                notifyDataSetChanged();
                            } else {
                                if (archived) {
                                    mAdapter.remove(position);
                                } else {
                                    mAdapter.insert(project, position);
                                    mRecyclerView.scrollToPosition(position);
                                }
                            }
                            if (archived) {
                                showUndoSnackbar(project, position);
                            }
                        }
                    });
        }

        /**
         * Show the user a dialog confirming deletion.
         */
        private void confirmDelete(Project project, int position) {
            Bundle extras = new Bundle();
            extras.putString(ARG_DELETE_PROJECT_ID, project.getProjectId());
            extras.putInt(ARG_DELETE_PROJECT_POSITION, position);
            DeleteMetadataItemDialog dialog = DeleteMetadataItemDialog.newInstance(
                    R.string.delete_project_dialog_title,
                    R.string.delete_project_dialog_message, extras);
            dialog.show(getChildFragmentManager(), DeleteMetadataItemDialog.TAG);
            if (mUndoSnackbar != null) {
                mUndoSnackbar.dismiss();
            }
        }

        private void showUndoSnackbar(final Project project, final int position) {
            if (getActivity() == null) {
                return;
            }
            mUndoSnackbar = AccessibilityUtils.makeSnackbar(getView(),
                    getView().getResources().getString(R.string.archived_project_message),
                    Snackbar.LENGTH_LONG);
            mUndoSnackbar.setAction(R.string.action_undo, new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    setProjectArchived(project, false, position);
                }
            }).setCallback(new Snackbar.Callback() {
                @Override
                public void onDismissed(Snackbar snackbar, int event) {
                    mUndoSnackbar = null;
                }
            }).show();
        }

        public class CardViewHolder extends RecyclerView.ViewHolder {

            private final CardView cardView;
            public ImageView projectCover;
            public TextView projectTitle;
            public TextView projectExperimentsCount;
            public View activeIndicator;
            public View archivedIndicator;

            public CardViewHolder(CardView itemView) {
                super(itemView);
                cardView = itemView;
                projectTitle = (TextView) itemView.findViewById(R.id.project_title);
                projectCover = (ImageView) itemView.findViewById(R.id.project_cover);
                projectExperimentsCount = (TextView) itemView.findViewById(
                        R.id.project_experiments_count);
                activeIndicator = itemView.findViewById(R.id.active_indicator);
                archivedIndicator = itemView.findViewById(R.id.archived_indicator);
            }
        }
    }
}
