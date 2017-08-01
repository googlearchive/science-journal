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
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v7.widget.GridLayoutManager;
import android.support.v7.widget.PopupMenu;
import android.support.v7.widget.RecyclerView;
import android.text.TextUtils;
import android.text.format.DateFormat;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import com.bumptech.glide.Glide;
import com.google.android.apps.forscience.javalib.Success;
import com.google.android.apps.forscience.whistlepunk.AccessibilityUtils;
import com.google.android.apps.forscience.whistlepunk.AppSingleton;
import com.google.android.apps.forscience.whistlepunk.DataController;
import com.google.android.apps.forscience.whistlepunk.LoggingConsumer;
import com.google.android.apps.forscience.whistlepunk.PanesActivity;
import com.google.android.apps.forscience.whistlepunk.PictureUtils;
import com.google.android.apps.forscience.whistlepunk.R;
import com.google.android.apps.forscience.whistlepunk.RxDataController;
import com.google.android.apps.forscience.whistlepunk.WhistlePunkApplication;
import com.google.android.apps.forscience.whistlepunk.analytics.TrackerConstants;
import com.google.android.apps.forscience.whistlepunk.filemetadata.Experiment;
import com.google.android.apps.forscience.whistlepunk.metadata.GoosciUserMetadata;
import com.google.android.apps.forscience.whistlepunk.review.DeleteMetadataItemDialog;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.List;

/**
 * Experiment List Fragment lists all experiments.
 */
public class ExperimentListFragment extends Fragment implements
        DeleteMetadataItemDialog.DeleteDialogListener {

    private static final String TAG = "ExperimentListFragment";

    /**
     * Boolen extra for savedInstanceState with the state of includeArchived experiments.
     */
    private static final String EXTRA_INCLUDE_ARCHIVED = "includeArchived";
    private static final String ARG_USE_PANES = "usePanes";

    private ExperimentListAdapter mExperimentListAdapter;
    private boolean mIncludeArchived;

    public static ExperimentListFragment newInstance(boolean usePanes) {
        ExperimentListFragment fragment = new ExperimentListFragment();
        Bundle args = new Bundle();
        args.putBoolean(ARG_USE_PANES, usePanes);
        fragment.setArguments(args);
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

        mExperimentListAdapter = new ExperimentListAdapter(this, getDataController());
        // TODO: Adjust the column count based on breakpoint specs when available.
        int column_count = 2;
        GridLayoutManager manager = new GridLayoutManager(getActivity(), column_count);
        manager.setSpanSizeLookup(new GridLayoutManager.SpanSizeLookup() {
            @Override
            public int getSpanSize(int position) {
                return mExperimentListAdapter.getItemViewType(position) ==
                        ExperimentListAdapter.VIEW_TYPE_EXPERIMENT ? 1 : column_count;
            }
        });
        detailList.setLayoutManager(manager);
        detailList.setAdapter(mExperimentListAdapter);

        FloatingActionButton newExperimentButton = (FloatingActionButton) view.findViewById(
                R.id.new_experiment);
        newExperimentButton.setOnClickListener(v -> getDataController().createExperiment(
                new LoggingConsumer<Experiment>(TAG, "Create a new experiment") {
                    @Override
                    public void success(final Experiment experiment) {
                        PanesActivity.launch(v.getContext(), experiment.getExperimentId());
                    }
                }));

        return view;
    }

    private boolean shouldUsePanes() {
        return getArguments().getBoolean(ARG_USE_PANES, true);
    }

    private void loadExperiments() {
        getDataController().getExperimentOverviews(mIncludeArchived,
                new LoggingConsumer<List<GoosciUserMetadata.ExperimentOverview>>(TAG,
                        "Retrieve experiments") {
                    @Override
                    public void success(List<GoosciUserMetadata.ExperimentOverview> experiments) {
                        attachToExperiments(experiments);
                    }
                });
    }

    private void attachToExperiments(List<GoosciUserMetadata.ExperimentOverview> experiments) {
        final View rootView = getView();
        if (rootView == null) {
            return;
        }
        mExperimentListAdapter.setData(experiments, mIncludeArchived);
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

    private void confirmDelete(String experimentId) {
        DeleteMetadataItemDialog dialog = DeleteMetadataItemDialog.newInstance(
                R.string.delete_experiment_dialog_title, R.string.delete_experiment_dialog_message,
                experimentId);
        dialog.show(getChildFragmentManager(), DeleteMetadataItemDialog.TAG);
    }

    @Override
    public void requestDelete(Bundle extras) {
        String experimentId = extras.getString(DeleteMetadataItemDialog.KEY_ITEM_ID);
        RxDataController.getExperimentById(getDataController(), experimentId)
                .subscribe(fullExperiment -> {
                    getDataController().deleteExperiment(fullExperiment,
                            new LoggingConsumer<Success>(TAG, "delete experiment") {
                                @Override
                                public void success(Success value) {
                                    mExperimentListAdapter.onExperimentDeleted(experimentId);
                                }
                            });
                });
    }

    static class ExperimentListItem {
        public final int viewType;
        public final GoosciUserMetadata.ExperimentOverview experimentOverview;
        public final String dateString;

        ExperimentListItem(GoosciUserMetadata.ExperimentOverview experimentOverview) {
            viewType = ExperimentListAdapter.VIEW_TYPE_EXPERIMENT;
            this.experimentOverview = experimentOverview;
            dateString = null;
        }

        ExperimentListItem(String date) {
            viewType = ExperimentListAdapter.VIEW_TYPE_DATE;
            dateString = date;
            experimentOverview = null;
        }
    }

    public static class ExperimentListAdapter extends RecyclerView.Adapter<ViewHolder> {
        static final int VIEW_TYPE_EXPERIMENT = 0;
        static final int VIEW_TYPE_EMPTY = 1;
        static final int VIEW_TYPE_DATE = 2;
        private final Drawable mPlaceHolderImage;
        private DataController mDataController;

        private List<ExperimentListItem> mItems;
        private boolean mIncludeArchived;
        private final Calendar mCalendar;
        private final int mCurrentYear;
        private final String mMonthYearFormat;

        private final WeakReference<ExperimentListFragment> mParentReference;

        public ExperimentListAdapter(ExperimentListFragment parent, DataController dc) {
            mItems = new ArrayList<>();
            mPlaceHolderImage = parent.getActivity().getResources().getDrawable(
                    R.drawable.experiment_card_placeholder);
            mDataController = dc;
            mCalendar = Calendar.getInstance(
                    parent.getActivity().getResources().getConfiguration().locale);
            mCurrentYear = mCalendar.get(Calendar.YEAR);
            mMonthYearFormat = parent.getActivity().getResources().getString(
                    R.string.month_year_format);
            mParentReference = new WeakReference<>(parent);
        }

        void setData(List<GoosciUserMetadata.ExperimentOverview> experimentOverviews,
                boolean includeArchived) {
            mIncludeArchived = includeArchived;
            mItems.clear();
            if (experimentOverviews.size() == 0) {
                notifyDataSetChanged();
                return;
            }
            // Sort most recent first
            Collections.sort(experimentOverviews,
                    (eo1, eo2) -> Long.compare(eo2.lastUsedTimeMs, eo1.lastUsedTimeMs));
            String date = "";
            for (GoosciUserMetadata.ExperimentOverview overview : experimentOverviews) {
                // Only show the year if it is not this year.
                mCalendar.setTime(new Date(overview.lastUsedTimeMs));
                String nextDate = DateFormat.format(mCalendar.get(Calendar.YEAR) == mCurrentYear ?
                        "MMMM" : mMonthYearFormat, mCalendar).toString();
                if (!TextUtils.equals(date, nextDate)) {
                    date = nextDate;
                    mItems.add(new ExperimentListItem(date));
                }
                mItems.add(new ExperimentListItem(overview));
            }
            notifyDataSetChanged();
        }

        @Override
        public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            LayoutInflater inflater = LayoutInflater.from(parent.getContext());
            View view;
            if (viewType == VIEW_TYPE_EMPTY) {
                view = inflater.inflate(R.layout.empty_list, parent, false);
            } else if (viewType == VIEW_TYPE_DATE) {
                view = inflater.inflate(R.layout.experiment_date, parent, false);
            } else {
                view = inflater.inflate(R.layout.project_experiment_overview, parent, false);
            }
            return new ViewHolder(view, viewType);
        }

        @Override
        public void onBindViewHolder(ViewHolder holder, int position) {
            if (mItems.size() == 0) {
                // Empty view holder doesn't need any binding logic.
                return;
            }
            if (mItems.get(position).viewType == VIEW_TYPE_EXPERIMENT) {
                bindExperiment(holder, mItems.get(position));
            } else if (mItems.get(position).viewType == VIEW_TYPE_DATE) {
                ((TextView)holder.itemView).setText(mItems.get(position).dateString);
            }
        }

        @Override
        public int getItemCount() {
            return mItems.size() == 0 ? 1 : mItems.size();
        }

        @Override
        public int getItemViewType(int position) {
            if (mItems.size() == 0){
                return VIEW_TYPE_EMPTY;
            } else {
                return mItems.get(position).viewType;
            }
        }

        private void bindExperiment(final ViewHolder holder, final ExperimentListItem item) {
            Resources res = holder.itemView.getResources();
            // First on the UI thread, set what experiment we're trying to load.
            holder.experimentId = item.experimentOverview.experimentId;

            // Set the data we know about.
            String experimentText = Experiment.getDisplayTitle(holder.itemView.getContext(),
                    item.experimentOverview.title);
            holder.experimentTitle.setText(experimentText);
            holder.archivedIndicator.setVisibility(item.experimentOverview.isArchived ?
                    View.VISIBLE : View.GONE);

            if (item.experimentOverview.isArchived) {
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

            holder.itemView.setTag(R.id.experiment_title, item.experimentOverview.experimentId);

            holder.cardView.setOnClickListener(v -> {
                PanesActivity.launch(v.getContext(), item.experimentOverview.experimentId);
            });

            holder.menuButton.setOnClickListener(v -> {
                Context context = holder.menuButton.getContext();
                PopupMenu popup = new PopupMenu(context, holder.menuButton);
                popup.getMenuInflater().inflate(R.menu.menu_experiment_overview, popup.getMenu());
                popup.getMenu().findItem(R.id.menu_item_archive).setVisible(
                        !item.experimentOverview.isArchived);
                popup.getMenu().findItem(R.id.menu_item_unarchive).setVisible(
                        item.experimentOverview.isArchived);
                popup.getMenu().findItem(R.id.menu_item_delete).setEnabled(
                        item.experimentOverview.isArchived);
                popup.setOnMenuItemClickListener(menuItem -> {
                    if (menuItem.getItemId() == R.id.menu_item_archive) {
                        setExperimentArchived(item, true);
                        return true;
                    } else if (menuItem.getItemId() == R.id.menu_item_unarchive) {
                        setExperimentArchived(item, false);
                        return true;
                    } else if (menuItem.getItemId() == R.id.menu_item_delete) {
                        mParentReference.get().confirmDelete(item.experimentOverview.experimentId);
                        return true;
                    }
                    return false;
                });
                popup.show();
            });

            if (!TextUtils.isEmpty(item.experimentOverview.imagePath)) {
                loadPhoto(holder, item.experimentOverview.imagePath);
            } else {
                // Make sure the scale type is correct for the placeholder
                holder.experimentImage.setScaleType(ImageView.ScaleType.FIT_CENTER);
                holder.experimentImage.setImageDrawable(mPlaceHolderImage);
                holder.experimentImage.setBackgroundColor(holder.experimentImage.getContext()
                        .getResources().getIntArray(R.array.experiment_colors_array)[
                                item.experimentOverview.colorIndex]);
            }
        }

        private void setExperimentArchived(ExperimentListItem item, boolean archived) {
            item.experimentOverview.isArchived = archived;
            RxDataController.getExperimentById(mDataController, item.experimentOverview.experimentId)
                    .subscribe(fullExperiment -> {
                        fullExperiment.setArchived(archived);
                        mDataController.updateExperiment(item.experimentOverview.experimentId,
                                new LoggingConsumer<Success>(TAG, "set archived bit") {
                                    @Override
                                    public void success(Success value) {
                                        updateArchivedState(item, archived);
                                        showArchivedSnackbar(archived, item);
                                    }
                                });
                    });
        }

        private void updateArchivedState(ExperimentListItem item, boolean archived) {
            if (mIncludeArchived) {
                notifyItemChanged(mItems.indexOf(item));
            } else if (archived) {
                // Remove archived experiment immediately.
                int i = mItems.indexOf(item);
                removeItem(i);
            } else {
                // It could be added back anywhere.
                if (mParentReference.get() != null) {
                    mParentReference.get().loadExperiments();
                }
            }
        }

        private void showArchivedSnackbar(boolean archived, ExperimentListItem item) {
            if (mParentReference.get() == null) {
                return;
            }
            Snackbar bar = AccessibilityUtils.makeSnackbar(mParentReference.get().getView(),
                    mParentReference.get().getResources().getString(archived ?
                            R.string.archived_experiment_message :
                            R.string.unarchived_experiment_message),
                    Snackbar.LENGTH_LONG);
            if (archived) {
                // We only seem to show "undo" for archiving items, not unarchiving them.
                bar.setAction(R.string.action_undo, view -> setExperimentArchived(item, !archived));
            }
            bar.show();
        }

        private void setCardColor(ViewHolder holder, int color) {
            if (Build.VERSION.SDK_INT > Build.VERSION_CODES.KITKAT) {
                //holder.cardView.setBackgroundColor(color);
            } else {
                // Setting the color of the CardView in KitKat has a side-effect of making the
                // drop shadow disappear around the card. Instead, we set the background color
                // of the content of the card, which looks almost as good. And has UX approval.
                holder.cardView.findViewById(R.id.content).setBackgroundColor(color);
            }
        }

        private void loadPhoto(final ViewHolder holder, String experimentOverviewFilePath) {
            holder.experimentImage.setScaleType(ImageView.ScaleType.CENTER_CROP);
            Glide.with(holder.experimentImage.getContext())
                    .load(PictureUtils.getExperimentOverviewFullImagePath(
                            holder.experimentImage.getContext(),
                            experimentOverviewFilePath)).into(holder.experimentImage);
        }

        public void onExperimentDeleted(String experimentId) {
            int index = -1;
            for (int i = 0; i < mItems.size(); i++) {
                ExperimentListItem item = mItems.get(i);
                if (item.viewType == VIEW_TYPE_EXPERIMENT &&
                        TextUtils.equals(item.experimentOverview.experimentId, experimentId)) {
                    index = i;
                    break;
                }
            }
            if (index > 0) {
                removeItem(index);
            }
        }

        private void removeItem(int index) {
            mItems.remove(index);
            if (mItems.size() > 1) {
                notifyItemRemoved(index);
            } else {
                // The last experiment was just removed.
                // All that's left is one date! Remove it.
                mItems.remove(0);
                notifyDataSetChanged();
            }
        }
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {

        // Accessing via fields for faster access.

        /**
         * Experiment ID that is being loaded or has been loaded.
         */
        public String experimentId;

        public TextView experimentTitle;
        public ImageView experimentImage;
        public View archivedIndicator;
        public View cardView;
        public ImageButton menuButton;

        int viewType;

        public ViewHolder(View itemView, int viewType) {
            super(itemView);
            this.viewType = viewType;
            if (viewType == ExperimentListAdapter.VIEW_TYPE_EXPERIMENT) {
                cardView = itemView.findViewById(R.id.card_view);
                experimentImage = (ImageView) itemView.findViewById(R.id.experiment_image);
                experimentTitle = (TextView) itemView.findViewById(R.id.experiment_title);
                archivedIndicator = itemView.findViewById(R.id.archived_indicator);
                menuButton = (ImageButton) itemView.findViewById(R.id.menu_button);
            }
        }
    }
}
