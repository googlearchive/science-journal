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
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.support.design.widget.FloatingActionButton;
import android.support.v4.graphics.drawable.RoundedBitmapDrawable;
import android.support.v4.graphics.drawable.RoundedBitmapDrawableFactory;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
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
import com.google.android.apps.forscience.whistlepunk.AppSingleton;
import com.google.android.apps.forscience.whistlepunk.DataController;
import com.google.android.apps.forscience.whistlepunk.LoggingConsumer;
import com.google.android.apps.forscience.whistlepunk.R;
import com.google.android.apps.forscience.whistlepunk.RelativeTimeTextView;
import com.google.android.apps.forscience.whistlepunk.WhistlePunkApplication;
import com.google.android.apps.forscience.whistlepunk.analytics.TrackerConstants;
import com.google.android.apps.forscience.whistlepunk.filemetadata.Label;
import com.google.android.apps.forscience.whistlepunk.filemetadata.PictureLabelValue;
import com.google.android.apps.forscience.whistlepunk.metadata.Experiment;
import com.google.android.apps.forscience.whistlepunk.metadata.ExperimentRun;
import com.google.android.apps.forscience.whistlepunk.metadata.GoosciLabelValue;
import com.google.android.apps.forscience.whistlepunk.project.experiment.ExperimentDetailsActivity;
import com.google.android.apps.forscience.whistlepunk.project.experiment.UpdateExperimentActivity;

import java.util.ArrayList;
import java.util.List;

/**
 * Experiment List Fragment lists all experiments.
 */
public class ExperimentListFragment extends Fragment {

    private static final String TAG = "ExperimentListFragment";

    /**
     * Boolen extra for savedInstanceState with the state of includeArchived experiments.
     */
    private static final String EXTRA_INCLUDE_ARCHIVED = "includeArchived";

    private ExperimentListAdapter mExperimentListAdapter;
    private boolean mIncludeArchived;

    public static ExperimentListFragment newInstance() {
        ExperimentListFragment fragment = new ExperimentListFragment();
        return fragment;
    }

    public ExperimentListFragment() {
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (savedInstanceState != null) {
            mIncludeArchived = savedInstanceState.getBoolean(EXTRA_INCLUDE_ARCHIVED, false);
            getActivity().invalidateOptionsMenu();
        }
        setHasOptionsMenu(true);
    }

    @Override
    public void onStart() {
        super.onStart();
        WhistlePunkApplication.getUsageTracker(getActivity()).trackScreenView(
                TrackerConstants.SCREEN_EXPERIMENT_LIST);
    }

    @Override
    public void onResume() {
        super.onResume();
        loadExperiments();
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean(EXTRA_INCLUDE_ARCHIVED, mIncludeArchived);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_experiment_list, container, false);
        final RecyclerView detailList = (RecyclerView) view.findViewById(R.id.details);

        mExperimentListAdapter = new ExperimentListAdapter(getActivity(), getDataController());
        detailList.setLayoutManager(new LinearLayoutManager(getActivity(),
                LinearLayoutManager.VERTICAL, false));
        detailList.setAdapter(mExperimentListAdapter);

        FloatingActionButton newExperimentButton = (FloatingActionButton) view.findViewById(
                R.id.new_experiment);
        newExperimentButton.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                getDataController().createExperiment(
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

    private void loadExperiments() {
        getDataController().getExperiments(mIncludeArchived,
                new LoggingConsumer<List<Experiment>>(TAG, "Retrieve experiments") {
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
        mExperimentListAdapter.setData(experiments);
    }

    private DataController getDataController() {
        return AppSingleton.getInstance(getActivity()).getDataController();
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);
        inflater.inflate(R.menu.menu_experiment_list, menu);
    }

    @Override
    public void onPrepareOptionsMenu(Menu menu) {
        menu.findItem(R.id.action_include_archived).setVisible(!mIncludeArchived);
        menu.findItem(R.id.action_exclude_archived).setVisible(mIncludeArchived);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_include_archived) {
            mIncludeArchived = true;
            loadExperiments();
            getActivity().invalidateOptionsMenu();
            return true;
        } else if (id == R.id.action_exclude_archived) {
            mIncludeArchived = false;
            loadExperiments();
            getActivity().invalidateOptionsMenu();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    public static class ExperimentListAdapter extends RecyclerView.Adapter<ViewHolder> {
        static final int VIEW_TYPE_EXPERIMENT = 0;
        static final int VIEW_TYPE_EMPTY = 1;
        private final Drawable mPlaceHolderImage;
        private DataController mDataController;

        private List<Experiment> mExperiments;

        public ExperimentListAdapter(Context context, DataController dc) {
            mExperiments = new ArrayList<>();
            mPlaceHolderImage = context.getResources().getDrawable(
                    R.drawable.placeholder_experiment);
            mDataController = dc;
        }

        void setData(List<Experiment> experiments) {
            mExperiments.clear();
            mExperiments.addAll(experiments);
            notifyDataSetChanged();
        }

        @Override
        public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            LayoutInflater inflater = LayoutInflater.from(parent.getContext());
            View view;
            if (viewType == VIEW_TYPE_EMPTY) {
                view = inflater.inflate(R.layout.empty_list, parent, false);
            } else {
                view = inflater.inflate(R.layout.project_experiment_overview, parent, false);
            }
            return new ViewHolder(view, viewType);
        }

        @Override
        public void onBindViewHolder(ViewHolder holder, int position) {
            if (mExperiments.size() != 0) {
                bindExperiment(holder, mExperiments.get(position));
            }
        }

        @Override
        public int getItemCount() {
            return mExperiments.size() == 0 ? 1 : mExperiments.size();
        }

        @Override
        public int getItemViewType(int position) {
            if (mExperiments.size() > 0){
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
            String experimentText = experiment.getExperiment().getDisplayTitle(
                    holder.itemView.getContext());
            holder.experimentTitle.setText(experimentText);
            holder.experimentImage.setImageDrawable(mPlaceHolderImage);
            // Set indeterminate states on the things we don't know.
            holder.experimentLastRun.setText("");
            holder.experimentRunTotals.setText("");
            holder.archivedIndicator.setVisibility(experiment.getExperiment().isArchived() ?
                    View.VISIBLE : View.GONE);

            if (experiment.getExperiment().isArchived()) {
                holder.experimentTitle.setContentDescription(res.getString(
                        R.string.archived_content_description, experimentText));
                holder.itemView.findViewById(R.id.content).setAlpha(res.getFraction(
                        R.fraction.metadata_card_archived_alpha, 1, 1));
                setCardColor(holder, res.getColor(R.color.archived_background_color));
            } else {
                // Use default.
                holder.experimentTitle.setContentDescription("");
                holder.itemView.findViewById(R.id.content).setAlpha(res.getFraction(
                        R.fraction.metadata_card_alpha, 1, 1));
                setCardColor(holder, res.getColor(R.color.text_color_white));
            }

            holder.itemView.setTag(R.id.experiment_title, experiment.getExperimentId());

            holder.cardView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    ExperimentDetailsActivity.launch(v.getContext(), experiment.getExperimentId());
                }
            });

            // Start loading data for runs.
            boolean includeInvalidRuns = false;
            mDataController.getExperimentRuns(experiment.getExperimentId(),
                    /* don't include archived runs */ false, includeInvalidRuns,
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

        private void setCardColor(ViewHolder holder, int color) {
            if (Build.VERSION.SDK_INT > Build.VERSION_CODES.KITKAT) {
                holder.cardView.setBackgroundColor(color);
            } else {
                // Setting the color of the CardView in KitKat has a side-effect of making the
                // drop shadow disappear around the card. Instead, we set the background color
                // of the content of the card, which looks almost as good. And has UX approval.
                holder.cardView.findViewById(R.id.content).setBackgroundColor(color);
            }
        }

        private void loadRunData(final ViewHolder holder, final Experiment experiment,
                                 List<ExperimentRun> runs) {
            Context context = holder.itemView.getContext();
            holder.experimentRunTotals.setText(context.getResources()
                    .getQuantityString(R.plurals.experiment_run_count, runs.size(), runs.size()));
            final String experimentId = holder.experimentId;
            if (runs.size() > 0) {
                // Take the first run, which should be the last created run.
                holder.experimentLastRun.setTime(runs.get(0).getFirstTimestamp());

                PictureLabelValue expPhoto = null;
                for (ExperimentRun run : runs) {
                    if (run.getCoverPictureLabelValue() != null) {
                        expPhoto = run.getCoverPictureLabelValue();
                        break;
                    }
                }
                if (expPhoto != null) {
                    loadPhoto(holder, expPhoto);
                } else {
                    loadPhotoFromExperimentLabels(holder, experiment, mDataController,
                            experimentId);
                }
            } else {
                holder.experimentLastRun.setText("");
                loadPhotoFromExperimentLabels(holder, experiment, mDataController, experimentId);
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
                        if (label.hasValueType(GoosciLabelValue.LabelValue.PICTURE)) {
                            loadPhoto(holder, (PictureLabelValue) label.getLabelValue(
                                    GoosciLabelValue.LabelValue.PICTURE));
                            break;
                        }
                    }
                }
            });
        }

        private void loadPhoto(final ViewHolder holder, PictureLabelValue expPhoto) {
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

        public boolean hasEmptyView() {
            return mExperiments.size() == 0;
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
        RelativeTimeTextView experimentLastRun;
        TextView experimentRunTotals;
        ImageView experimentImage;
        View archivedIndicator;
        View cardView;

        int viewType;

        public ViewHolder(View itemView, int viewType) {
            super(itemView);
            this.viewType = viewType;
            if (viewType == ExperimentListAdapter.VIEW_TYPE_EXPERIMENT) {
                cardView = itemView.findViewById(R.id.card_view);
                experimentImage = (ImageView) itemView.findViewById(R.id.experiment_image);
                experimentTitle = (TextView) itemView.findViewById(R.id.experiment_title);
                experimentLastRun = (RelativeTimeTextView) itemView.findViewById(
                        R.id.experiment_last_run);
                experimentRunTotals = (TextView) itemView.findViewById(R.id.experiment_total_runs);
                archivedIndicator = (View) itemView.findViewById(R.id.archived_indicator);
            }
        }
    }
}
