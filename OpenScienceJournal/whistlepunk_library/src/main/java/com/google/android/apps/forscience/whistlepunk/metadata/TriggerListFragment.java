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

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v4.app.Fragment;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.SwitchCompat;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.ImageButton;
import android.widget.PopupMenu;
import android.widget.TextView;

import com.google.android.apps.forscience.javalib.Success;
import com.google.android.apps.forscience.whistlepunk.AccessibilityUtils;
import com.google.android.apps.forscience.whistlepunk.AppSingleton;
import com.google.android.apps.forscience.whistlepunk.DataController;
import com.google.android.apps.forscience.whistlepunk.LoggingConsumer;
import com.google.android.apps.forscience.whistlepunk.ProtoUtils;
import com.google.android.apps.forscience.whistlepunk.R;
import com.google.android.apps.forscience.whistlepunk.WhistlePunkApplication;
import com.google.android.apps.forscience.whistlepunk.analytics.TrackerConstants;
import com.google.android.apps.forscience.whistlepunk.data.GoosciSensorLayout;
import com.google.android.apps.forscience.whistlepunk.filemetadata.Experiment;
import com.google.android.apps.forscience.whistlepunk.filemetadata.SensorTrigger;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * The fragment for displaying a list of triggers.
 */
public class TriggerListFragment extends Fragment {

    private static final String ARG_SENSOR_ID = "sensor_id";
    private static final String ARG_EXPERIMENT_ID = "experiment_id";
    private static final String ARG_LAYOUT_POSITION = "layout_position";
    public static final String ARG_TRIGGER_ORDER = "trigger_order";
    private static final String KEY_TRIGGER_ORDER = "trigger_order";
    private static final String TAG = "TriggerListFragment";

    private static String mSensorId;
    private String mExperimentId;
    private GoosciSensorLayout.SensorLayout mSensorLayout;
    private TriggerListAdapter mTriggerAdapter;
    private int mLayoutPosition;
    private ArrayList<String> mTriggerOrder;
    private Experiment mExperiment;
    private boolean mNeedsSave = false;

    public static TriggerListFragment newInstance(String sensorId, String experimentId,
            int position, ArrayList<String> triggerOrder) {
        TriggerListFragment fragment = new TriggerListFragment();
        Bundle args = new Bundle();
        args.putString(ARG_SENSOR_ID, sensorId);
        args.putString(ARG_EXPERIMENT_ID, experimentId);
        args.putInt(ARG_LAYOUT_POSITION, position);
        args.putStringArrayList(ARG_TRIGGER_ORDER, triggerOrder);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mSensorId = getArguments().getString(ARG_SENSOR_ID);
        mExperimentId = getArguments().getString(ARG_EXPERIMENT_ID);
        mLayoutPosition = getArguments().getInt(ARG_LAYOUT_POSITION);
        if (savedInstanceState != null) {
            mTriggerOrder = savedInstanceState.getStringArrayList(KEY_TRIGGER_ORDER);
        } else {
            mTriggerOrder = getArguments().getStringArrayList(ARG_TRIGGER_ORDER);
        }
        setHasOptionsMenu(true);
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        ActionBar actionBar = ((AppCompatActivity) getActivity()).getSupportActionBar();
        actionBar.setDisplayHomeAsUpEnabled(true);

        String sensorName = AppSingleton.getInstance(getActivity())
                .getSensorAppearanceProvider().getAppearance(mSensorId).getName(getActivity());
        actionBar.setTitle(getString(R.string.title_fragment_trigger_list, sensorName));

        super.onCreateOptionsMenu(menu, inflater);
    }

    @Override
    public void onStart() {
        super.onStart();
        WhistlePunkApplication.getUsageTracker(getActivity()).trackScreenView(
                TrackerConstants.SCREEN_TRIGGER_LIST);
    }

    @Override
    public void onResume() {
        super.onResume();
        loadExperiment();
    }

    @Override
    public void onPause() {
        if (mNeedsSave) {
            getDataController().updateExperiment(mExperimentId,
                    LoggingConsumer.<Success>expectSuccess(TAG, "updating sensor layout onPause"));
        }
        super.onPause();
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        saveTriggerOrder();
        outState.putStringArrayList(KEY_TRIGGER_ORDER, mTriggerOrder);
    }

    private DataController getDataController() {
        return AppSingleton.getInstance(getActivity()).getDataController();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup parent, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_trigger_list, parent, false);

        ActionBar actionBar = ((AppCompatActivity) getActivity()).getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
            actionBar.setHomeButtonEnabled(true);
        }

        RecyclerView triggerList = (RecyclerView) view.findViewById(R.id.trigger_recycler_view);
        triggerList.setLayoutManager(new LinearLayoutManager(view.getContext(),
                LinearLayoutManager.VERTICAL, false));
        mTriggerAdapter = new TriggerListAdapter(this);
        triggerList.setAdapter(mTriggerAdapter);

        FloatingActionButton addButton =
                (FloatingActionButton) view.findViewById(R.id.add_trigger_button);
        addButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                launchEditTriggerActivity(null);
            }
        });
        return view;
    }

    private void loadExperiment() {
        getDataController().getExperimentById(mExperimentId,
                new LoggingConsumer<Experiment>(TAG, "get experiment") {
                    @Override
                    public void success(Experiment experiment) {
                        mExperiment = experiment;
                        for (GoosciSensorLayout.SensorLayout layout :
                                experiment.getSensorLayouts()) {
                            if (TextUtils.equals(layout.sensorId, mSensorId)) {
                                mSensorLayout = layout;
                            }
                        }
                        Comparator<SensorTrigger> cp;
                        if (mTriggerOrder != null) {
                            // If this is not the first load, use the saved order to define a new
                            // order, but insert new triggers at the top.
                            cp = new Comparator<SensorTrigger>() {
                                @Override
                                public int compare(SensorTrigger lhs, SensorTrigger rhs) {
                                    int lhsIndex = mTriggerOrder.indexOf(lhs.getTriggerId());
                                    int rhsIndex = mTriggerOrder.indexOf(rhs.getTriggerId());
                                    if (lhsIndex == rhsIndex && lhsIndex == -1) {
                                        // If they are both not found, they are both new.
                                        return Long.compare(rhs.getLastUsed(), lhs.getLastUsed());
                                    }
                                    return Integer.compare(lhsIndex, rhsIndex);
                                }
                            };
                        } else {
                            // Only do this sort on the first load.
                            cp = new Comparator<SensorTrigger>() {
                                @Override
                                public int compare(SensorTrigger lhs, SensorTrigger rhs) {
                                    boolean lhsIsActive = isTriggerActive(lhs);
                                    boolean rhsIsActive = isTriggerActive(rhs);
                                    if (lhsIsActive && !rhsIsActive) {
                                        return -1;
                                    }
                                    if (!lhsIsActive && rhsIsActive) {
                                        return 1;
                                    }
                                    return Long.compare(rhs.getLastUsed(), lhs.getLastUsed());

                                }
                            };
                        }
                        // Sort sensor triggers
                        List<SensorTrigger> triggers = experiment.getSensorTriggersForSensor(
                                mSensorId);
                        Collections.sort(triggers, cp);
                        mTriggerAdapter.setSensorTriggers(triggers);
                    }
                });
    }

    private void launchEditTriggerActivity(SensorTrigger trigger) {
        Intent intent = new Intent(getActivity(), EditTriggerActivity.class);
        if (trigger != null) {
            intent.putExtra(EditTriggerActivity.EXTRA_TRIGGER_ID, trigger.getTriggerId());
        }
        intent.putExtra(EditTriggerActivity.EXTRA_EXPERIMENT_ID, mExperimentId);
        intent.putExtra(EditTriggerActivity.EXTRA_SENSOR_ID, mSensorId);

        // Also send the Sensor Layout and the position so that this fragment can be recreated on
        // completion, and the order in which the triggers are shown so that the order does not
        // change when the user gets back.
        intent.putExtra(EditTriggerActivity.EXTRA_SENSOR_LAYOUT_BLOB,
                ProtoUtils.makeBlob(mSensorLayout));
        intent.putExtra(TriggerListActivity.EXTRA_LAYOUT_POSITION, mLayoutPosition);
        saveTriggerOrder();
        intent.putExtra(TriggerListActivity.EXTRA_TRIGGER_ORDER, mTriggerOrder);

        getActivity().startActivity(intent);
    }

    private void saveTriggerOrder() {
        mTriggerOrder = new ArrayList<>();
        for (SensorTrigger trigger : mTriggerAdapter.mSensorTriggers) {
            mTriggerOrder.add(trigger.getTriggerId());
        }
    }

    private void deleteTrigger(final SensorTrigger trigger, final int index) {
        final boolean isActive = isTriggerActive(trigger);
        final DataController dc = getDataController();

        // Set up the undo snackbar.
        final Snackbar bar = AccessibilityUtils.makeSnackbar(getView(),
                getActivity().getResources().getString(R.string.sensor_trigger_deleted),
                Snackbar.LENGTH_SHORT);
        bar.setAction(R.string.snackbar_undo, new View.OnClickListener() {
            boolean mUndone = false;
            @Override
            public void onClick(View v) {
                if (mUndone) {
                    return;
                }
                mUndone = true;
                mExperiment.addSensorTrigger(trigger);
                if (isActive) {
                    TriggerHelper.addTriggerToLayoutActiveTriggers(
                            mSensorLayout, trigger.getTriggerId());
                    mExperiment.updateSensorLayout(mLayoutPosition, mSensorLayout);
                }
                dc.updateExperiment(mExperimentId,
                        new LoggingConsumer<Success>(TAG, "update exp: re-add deleted trigger") {
                            @Override
                            public void success(Success value) {
                                if (isActive) {
                                    // If it was active, re-add it to the Layout.
                                    mTriggerAdapter.addTriggerAtIndex(trigger,
                                            index);
                                } else {
                                    mTriggerAdapter.addTriggerAtIndex(trigger, index);
                                }
                            }
                        });
            }
        });

        // Do the deletion, first by removing it from the layout and next by removing it
        // from the trigger database.
        TriggerHelper.removeTriggerFromLayoutActiveTriggers(mSensorLayout,
                trigger.getTriggerId());
        mExperiment.removeSensorTrigger(trigger);
        mExperiment.updateSensorLayout(mLayoutPosition, mSensorLayout);
        dc.updateExperiment(mExperimentId, new LoggingConsumer<Success>(TAG, "delete trigger") {
            @Override
            public void success(Success value) {
                bar.show();
                mTriggerAdapter.removeTrigger(trigger);
            }
        });
    }

    private boolean isTriggerActive(SensorTrigger trigger) {
        for (int i = 0; i < mSensorLayout.activeSensorTriggerIds.length; i++) {
            if (TextUtils.equals(trigger.getTriggerId(), mSensorLayout.activeSensorTriggerIds[i])) {
                return true;
            }
        }
        return false;
    }

    private void setSensorTriggerActive(SensorTrigger trigger, boolean isActive) {
        // Don't do the work if the state is already correct.
        if (isTriggerActive(trigger) == isActive) {
            return;
        }
        // Make a note that a trigger was changed and needs saving, but don't bother updating the
        // experiment triggers at this time.
        // Unlike delete (which is updated immediately), and edit (where saving is handled in
        // another fragment), toggling the active state doesn't need to be written to the database
        // until we exit the fragment.
        mNeedsSave = true;
        if (isActive) {
            TriggerHelper.addTriggerToLayoutActiveTriggers(mSensorLayout,
                    trigger.getTriggerId());
        } else {
            TriggerHelper.removeTriggerFromLayoutActiveTriggers(mSensorLayout,
                    trigger.getTriggerId());
        }
        mExperiment.updateSensorLayout(mLayoutPosition, mSensorLayout);
        // Note: Last used time is not updated when the trigger is activated / deactivated, and the
        // list should not be resorted at this time.
    }

    private static class TriggerListAdapter extends
            RecyclerView.Adapter<TriggerListAdapter.ViewHolder> {

        private static final int VIEW_TYPE_TRIGGER = 0;
        private static final int VIEW_TYPE_EMPTY = 1;

        List<SensorTrigger> mSensorTriggers = new ArrayList<>();
        private final WeakReference<TriggerListFragment> mParentReference;

        public TriggerListAdapter(TriggerListFragment parent) {
            mParentReference = new WeakReference<TriggerListFragment>(parent);
        }

        @Override
        public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            LayoutInflater inflater =  LayoutInflater.from(parent.getContext());
            int viewId;
            if (viewType == VIEW_TYPE_EMPTY) {
                viewId = R.layout.empty_trigger_list;
            } else {
                viewId = R.layout.trigger_list_item;
            }
            return new ViewHolder(inflater.inflate(viewId, parent, false), viewType);
        }

        @Override
        public void onBindViewHolder(final ViewHolder holder, final int position) {
            if (getItemViewType(position) == VIEW_TYPE_EMPTY) {
                return;
            }
            final SensorTrigger trigger = mSensorTriggers.get(position);
            if (mParentReference.get() == null) {
                return;
            }
            holder.description.setText(TriggerHelper.buildDescription(trigger,
                    mParentReference.get().getActivity()));

            holder.menuButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Context context = holder.menuButton.getContext();
                    PopupMenu popup = new PopupMenu(context, holder.menuButton);
                    popup.getMenuInflater().inflate(R.menu.menu_sensor_trigger, popup.getMenu());
                    popup.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
                        public boolean onMenuItemClick(MenuItem item) {
                            if (item.getItemId() == R.id.edit_trigger) {
                                if (mParentReference.get() != null) {
                                    mParentReference.get().launchEditTriggerActivity(trigger);
                                }
                                return true;
                            } else if (item.getItemId() == R.id.delete_trigger) {
                                if (mParentReference.get() != null) {
                                    mParentReference.get().deleteTrigger(trigger,
                                            mSensorTriggers.indexOf(trigger));
                                }
                                return true;
                            }
                            return false;
                        }
                    });
                    popup.show();
                }
            });

            holder.activationSwitch.setOnCheckedChangeListener(null);
            holder.activationSwitch.setChecked(mParentReference.get().isTriggerActive(trigger));
            holder.activationSwitch.setOnCheckedChangeListener(
                    new CompoundButton.OnCheckedChangeListener() {
                        @Override
                        public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                            if (mParentReference.get() != null) {
                                mParentReference.get().setSensorTriggerActive(trigger, isChecked);
                            }
                        }
                    });
        }

        @Override
        public void onViewRecycled(TriggerListAdapter.ViewHolder holder) {
            int position = holder.getAdapterPosition();
            if (position == RecyclerView.NO_POSITION) {
                return;
            }
            holder.activationSwitch.setOnCheckedChangeListener(null);
            holder.menuButton.setOnClickListener(null);
        }

        @Override
        public int getItemCount() {
            return mSensorTriggers.size() == 0 ? 1 : mSensorTriggers.size();
        }

        @Override
        public int getItemViewType(int position) {
            if (position == 0 && mSensorTriggers.size() == 0) {
                return VIEW_TYPE_EMPTY;
            }
            return VIEW_TYPE_TRIGGER;
        }

        public void setSensorTriggers(List<SensorTrigger> sensorTriggers) {
            mSensorTriggers = sensorTriggers;
            notifyDataSetChanged();
        }

        public void removeTrigger(SensorTrigger trigger) {
            if (mSensorTriggers.contains(trigger)) {
                mSensorTriggers.remove(trigger);
            }
            notifyDataSetChanged();
        }

        public void addTriggerAtIndex(SensorTrigger trigger, int index) {
            mSensorTriggers.add(index, trigger);
            notifyDataSetChanged();
        }

        public static class ViewHolder extends RecyclerView.ViewHolder {
            int mViewType;
            TextView description;
            ImageButton menuButton;
            SwitchCompat activationSwitch;

            public ViewHolder(View itemView, int viewType) {
                super(itemView);
                mViewType = viewType;
                if (mViewType == VIEW_TYPE_TRIGGER) {
                    description = (TextView) itemView.findViewById(R.id.trigger_description);
                    menuButton = (ImageButton) itemView.findViewById(R.id.btn_trigger_menu);
                    activationSwitch =
                            (SwitchCompat) itemView.findViewById(R.id.trigger_activation_switch);
                }
            }
        }
    }
}
