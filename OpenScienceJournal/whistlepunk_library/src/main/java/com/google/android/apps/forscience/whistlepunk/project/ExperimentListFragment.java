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

import android.content.Context;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.appcompat.widget.PopupMenu;
import androidx.recyclerview.widget.RecyclerView;
import android.text.TextUtils;
import android.text.format.DateFormat;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import com.google.android.apps.forscience.javalib.Success;
import com.google.android.apps.forscience.whistlepunk.AccessibilityUtils;
import com.google.android.apps.forscience.whistlepunk.AndroidVersionUtils;
import com.google.android.apps.forscience.whistlepunk.AppSingleton;
import com.google.android.apps.forscience.whistlepunk.Clock;
import com.google.android.apps.forscience.whistlepunk.DataController;
import com.google.android.apps.forscience.whistlepunk.ExportService;
import com.google.android.apps.forscience.whistlepunk.LoggingConsumer;
import com.google.android.apps.forscience.whistlepunk.PictureUtils;
import com.google.android.apps.forscience.whistlepunk.R;
import com.google.android.apps.forscience.whistlepunk.RxDataController;
import com.google.android.apps.forscience.whistlepunk.RxEvent;
import com.google.android.apps.forscience.whistlepunk.SnackbarManager;
import com.google.android.apps.forscience.whistlepunk.WhistlePunkApplication;
import com.google.android.apps.forscience.whistlepunk.analytics.TrackerConstants;
import com.google.android.apps.forscience.whistlepunk.filemetadata.Experiment;
import com.google.android.apps.forscience.whistlepunk.filemetadata.FileMetadataManager;
import com.google.android.apps.forscience.whistlepunk.filemetadata.Label;
import com.google.android.apps.forscience.whistlepunk.metadata.nano.GoosciCaption;
import com.google.android.apps.forscience.whistlepunk.metadata.nano.GoosciLabel;
import com.google.android.apps.forscience.whistlepunk.metadata.nano.GoosciPictureLabelValue;
import com.google.android.apps.forscience.whistlepunk.metadata.nano.GoosciTextLabelValue;
import com.google.android.apps.forscience.whistlepunk.metadata.nano.GoosciUserMetadata;
import com.google.android.apps.forscience.whistlepunk.performance.PerfTrackerProvider;
import com.google.android.apps.forscience.whistlepunk.review.DeleteMetadataItemDialog;

import java.io.File;
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
    private static final String KEY_DEFAULT_EXPERIMENT_CREATED = "key_default_experiment_created";

    private ExperimentListAdapter mExperimentListAdapter;
    private boolean mIncludeArchived;
    private boolean mProgressBarVisible = false;
    private RxEvent mDestroyed = new RxEvent();

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
        AppSingleton.getInstance(getContext()).whenExportBusyChanges()
                .takeUntil(mDestroyed.happens()).subscribe(busy -> {
            setProgressBarVisible(busy);
        });
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
        setProgressBarVisible(mProgressBarVisible);
        loadExperiments();
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean(EXTRA_INCLUDE_ARCHIVED, mIncludeArchived);
    }

    @Override
    public void onDestroy() {
        // TODO: Use RxEvent here
        mExperimentListAdapter.onDestroy();
        mDestroyed.onHappened();
        super.onDestroy();
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
                        WhistlePunkApplication.getUsageTracker(getActivity())
                                .trackEvent(TrackerConstants.CATEGORY_EXPERIMENTS,
                                        TrackerConstants.ACTION_CREATE,
                                        TrackerConstants.LABEL_EXPERIMENT_LIST,
                                        0);
                        launchPanesActivity(v.getContext(), experiment.getExperimentId());
                    }
                }));

        return view;
    }

    public static void launchPanesActivity(Context context, String experimentId) {
        context.startActivity(
                WhistlePunkApplication.getLaunchIntentForPanesActivity(context, experimentId));
    }

    private void loadExperiments() {
        PerfTrackerProvider perfTracker = WhistlePunkApplication
                .getPerfTrackerProvider(getActivity());
        PerfTrackerProvider.TimerToken loadExperimentTimer = perfTracker.startTimer();
        getDataController().getExperimentOverviews(mIncludeArchived,
                new LoggingConsumer<List<GoosciUserMetadata.ExperimentOverview>>(TAG,
                        "Retrieve experiments") {
                    @Override
                    public void success(List<GoosciUserMetadata.ExperimentOverview> experiments) {
                        if (experiments.size() == 0 &&
                                !PreferenceManager.getDefaultSharedPreferences(getActivity())
                                        .getBoolean(KEY_DEFAULT_EXPERIMENT_CREATED, false)) {
                            // If there are no experiments and we've never made a default one,
                            // create the default experiment and set the boolean to true.
                            createDefaultExperiment();
                            perfTracker.stopTimer(loadExperimentTimer,
                                    TrackerConstants.PRIMES_DEFAULT_EXPERIMENT_CREATED);
                        } else {
                            attachToExperiments(experiments);
                            perfTracker.stopTimer(loadExperimentTimer,
                                    TrackerConstants.PRIMES_EXPERIMENT_LIST_LOADED);
                        }
                        perfTracker.onAppInteractive();
                    }
                });
    }

    private void createDefaultExperiment() {
        RxDataController.createExperiment(getDataController()).subscribe(e -> {
            Resources res = getActivity().getResources();
            e.setTitle(res.getString(R.string.first_experiment_title));
            Clock clock = AppSingleton.getInstance(getActivity())
                    .getSensorEnvironment()
                    .getDefaultClock();

            // Create a text label 1 second ago with default text.
            GoosciTextLabelValue.TextLabelValue goosciTextLabel1 = new GoosciTextLabelValue
                    .TextLabelValue();
            goosciTextLabel1.text = res.getString(R.string.first_experiment_second_text_note);
            Label textLabel1 = Label.newLabelWithValue(clock.getNow() - 1000,
                    GoosciLabel.Label.ValueType.TEXT, goosciTextLabel1, null);
            e.addLabel(textLabel1);

            // Create a text label 2 seconds ago with default text.
            GoosciTextLabelValue.TextLabelValue goosciTextLabel2 = new GoosciTextLabelValue
                    .TextLabelValue();
            goosciTextLabel2.text = res.getString(R.string.first_experiment_text_note);
            Label textLabel2 = Label.newLabelWithValue(clock.getNow() - 2000,
                    GoosciLabel.Label.ValueType.TEXT, goosciTextLabel2, null);
            e.addLabel(textLabel2);

            // Create a picture label 4 second ago with a default drawable and caption.
            GoosciCaption.Caption caption = new GoosciCaption.Caption();
            caption.text = res.getString(R.string.first_experiment_picture_note_caption);
            caption.lastEditedTimestamp = clock.getNow() - 4000;
            Label pictureLabel = Label.newLabel(caption.lastEditedTimestamp,
                    GoosciLabel.Label.ValueType.PICTURE);
            File pictureFile = PictureUtils.createImageFile(getActivity(), e.getExperimentId(),
                    pictureLabel.getLabelId());
            PictureUtils.writeDrawableToFile(getActivity(), pictureFile, R.drawable.first_note);
            GoosciPictureLabelValue.PictureLabelValue goosciPictureLabel =
                    new GoosciPictureLabelValue.PictureLabelValue();
            goosciPictureLabel.filePath = FileMetadataManager.getRelativePathInExperiment(
                    e.getExperimentId(), pictureFile);
            pictureLabel.setLabelProtoData(goosciPictureLabel);
            pictureLabel.setCaption(caption);
            e.addLabel(pictureLabel);

            // TODO: Add a recording item if required by b/64844798.

            RxDataController.updateExperiment(getDataController(), e).subscribe(() -> {
                PreferenceManager.getDefaultSharedPreferences(getActivity()).edit().putBoolean(
                        KEY_DEFAULT_EXPERIMENT_CREATED, true).apply();
                loadExperiments();
            });

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

    public void setProgressBarVisible(boolean visible) {
        mProgressBarVisible = visible;
        if (getView() != null) {
            if (visible) {
                getView().findViewById(R.id.indeterminateBar).setVisibility(View.VISIBLE);
            } else {
                getView().findViewById(R.id.indeterminateBar).setVisibility(View.GONE);
            }
        }
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
        if (mProgressBarVisible) {
            return true;
        }
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
                                    WhistlePunkApplication.getUsageTracker(getActivity())
                                            .trackEvent(TrackerConstants.CATEGORY_EXPERIMENTS,
                                                    TrackerConstants.ACTION_DELETED,
                                                    TrackerConstants.LABEL_EXPERIMENT_LIST,
                                                    0);
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
        private SnackbarManager mSnackbarManager = new SnackbarManager();
        private PopupMenu mPopupMenu = null;

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
                ((TextView) holder.itemView).setText(mItems.get(position).dateString);
            }
        }

        @Override
        public int getItemCount() {
            return mItems.size() == 0 ? 1 : mItems.size();
        }

        @Override
        public int getItemViewType(int position) {
            if (mItems.size() == 0) {
                return VIEW_TYPE_EMPTY;
            } else {
                return mItems.get(position).viewType;
            }
        }

        private void bindExperiment(final ViewHolder holder, final ExperimentListItem item) {
            Resources res = holder.itemView.getResources();
            // First on the UI thread, set what experiment we're trying to load.
            GoosciUserMetadata.ExperimentOverview overview = item.experimentOverview;
            holder.experimentId = overview.experimentId;

            // Set the data we know about.
            String experimentText = Experiment.getDisplayTitle(holder.itemView.getContext(),
                    overview.title);
            holder.experimentTitle.setText(experimentText);
            holder.archivedIndicator.setVisibility(overview.isArchived ? View.VISIBLE : View.GONE);

            if (overview.isArchived) {
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

            holder.itemView.setTag(R.id.experiment_title, overview.experimentId);

            holder.cardView.setOnClickListener(v -> {
                if (!mParentReference.get().mProgressBarVisible) {
                    launchPanesActivity(v.getContext(), overview.experimentId);
                }
            });

            Context context = holder.menuButton.getContext();
            boolean isShareIntentValid = FileMetadataManager.validateShareIntent(context,
                    overview.experimentId);

            holder.menuButton.setOnClickListener(v -> {
                int position = mItems.indexOf(item);

                mPopupMenu = new PopupMenu(context, holder.menuButton, Gravity.NO_GRAVITY,
                        R.attr.actionOverflowMenuStyle, 0);
                mPopupMenu.getMenuInflater().inflate(R.menu.menu_experiment_overview,
                        mPopupMenu.getMenu());
                mPopupMenu.getMenu().findItem(R.id.menu_item_archive).setVisible(
                        !overview.isArchived);
                mPopupMenu.getMenu().findItem(R.id.menu_item_unarchive).setVisible(
                        overview.isArchived);
                mPopupMenu.getMenu().findItem(R.id.menu_item_export_experiment).setVisible(
                        isShareIntentValid && !overview.isArchived);

                mPopupMenu.setOnMenuItemClickListener(menuItem -> {
                    if (mParentReference.get().mProgressBarVisible) {
                        return true;
                    }
                    if (menuItem.getItemId() == R.id.menu_item_archive) {
                        setExperimentArchived(overview, position, true);
                        return true;
                    } else if (menuItem.getItemId() == R.id.menu_item_unarchive) {
                        setExperimentArchived(overview, position, false);
                        return true;
                    } else if (menuItem.getItemId() == R.id.menu_item_delete) {
                        mSnackbarManager.hideVisibleSnackbar();
                        mParentReference.get().confirmDelete(overview.experimentId);
                        return true;
                    } else if (menuItem.getItemId() == R.id.menu_item_export_experiment) {
                        WhistlePunkApplication.getUsageTracker(
                                mParentReference.get().getActivity())
                                .trackEvent(TrackerConstants.CATEGORY_EXPERIMENTS,
                                        TrackerConstants.ACTION_SHARED,
                                        TrackerConstants.LABEL_EXPERIMENT_LIST, 0);
                        mParentReference.get().setProgressBarVisible(true);
                        ExportService.handleExperimentExportClick(context, overview.experimentId);
                        return true;
                    }
                    return false;
                });
                mPopupMenu.setOnDismissListener(menu -> mPopupMenu = null);
                mPopupMenu.show();
            });

            if (!TextUtils.isEmpty(overview.imagePath)) {
                PictureUtils.loadExperimentOverviewImage(holder.experimentImage,
                        overview.imagePath);
            } else {
                // Make sure the scale type is correct for the placeholder
                holder.experimentImage.setScaleType(ImageView.ScaleType.FIT_CENTER);
                holder.experimentImage.setImageDrawable(mPlaceHolderImage);
                int[] intArray = holder.experimentImage.getContext()
                        .getResources()
                        .getIntArray(
                                R.array.experiment_colors_array);
                holder.experimentImage.setBackgroundColor(intArray[overview.colorIndex]);
            }
        }

        private void setExperimentArchived(GoosciUserMetadata.ExperimentOverview overview,
                final int position, boolean archived) {
            overview.isArchived = archived;
            RxDataController.getExperimentById(mDataController, overview.experimentId)
                    .subscribe(fullExperiment -> {
                        fullExperiment.setArchived(archived);
                        mDataController.updateExperiment(overview.experimentId,
                                new LoggingConsumer<Success>(TAG, "set archived bit") {
                                    @Override
                                    public void success(Success value) {
                                        updateArchivedState(position, archived);
                                        WhistlePunkApplication.getUsageTracker(
                                                mParentReference.get().getActivity())
                                                .trackEvent(TrackerConstants.CATEGORY_EXPERIMENTS,
                                                        archived ? TrackerConstants.ACTION_ARCHIVE :
                                                                TrackerConstants.ACTION_UNARCHIVE,
                                                        TrackerConstants.LABEL_EXPERIMENT_LIST, 0);
                                        showArchivedSnackbar(overview, position, archived);
                                    }
                                });
                    });
        }

        private void updateArchivedState(int position, boolean archived) {
            if (mIncludeArchived) {
                notifyItemChanged(position);
            } else if (archived) {
                // Remove archived experiment immediately.
                int i = position;
                removeExperiment(i);
            } else {
                // It could be added back anywhere.
                if (mParentReference.get() != null) {
                    mParentReference.get().loadExperiments();
                }
            }
        }

        private void showArchivedSnackbar(GoosciUserMetadata.ExperimentOverview overview,
                int position, boolean archived) {
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
                bar.setAction(R.string.action_undo,
                        view -> setExperimentArchived(overview, position, !archived));
            }
            mSnackbarManager.showSnackbar(bar);
        }

        private void setCardColor(ViewHolder holder, int color) {
            if (AndroidVersionUtils.isApiLevelAtLeastLollipop()) {
                //holder.cardView.setBackgroundColor(color);
            } else {
                // Setting the color of the CardView in KitKat has a side-effect of making the
                // drop shadow disappear around the card. Instead, we set the background color
                // of the content of the card, which looks almost as good. And has UX approval.
                // See b/70328251
                holder.cardView.findViewById(R.id.content).setBackgroundColor(color);
            }
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
                removeExperiment(index);
            }
        }

        private void removeExperiment(int index) {
            mItems.remove(index);
            if (mItems.size() > 1) {
                notifyItemRemoved(index);
                // Make sure the item before is not a date that has no children.
                // If it is, remove it too.
                // Index cannot be 0 because the first item is a date.
                if (mItems.get(index - 1).viewType == VIEW_TYPE_DATE &&
                        ((mItems.size() > index && mItems.get(index).viewType == VIEW_TYPE_DATE) ||
                                (mItems.size() == index))) {
                    mItems.remove(index - 1);
                    notifyItemRemoved(index - 1);
                }
            } else {
                // The last experiment was just removed.
                // All that's left is one date! Remove it.
                mItems.remove(0);
                notifyDataSetChanged();
            }
        }

        public void onDestroy() {
            mSnackbarManager.onDestroy();
            if (mPopupMenu != null) {
                mPopupMenu.dismiss();
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
