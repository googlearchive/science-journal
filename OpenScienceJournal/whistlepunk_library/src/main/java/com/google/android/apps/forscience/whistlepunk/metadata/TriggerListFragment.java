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
import android.util.Log;
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
import com.google.android.apps.forscience.whistlepunk.RecordFragment;
import com.google.android.apps.forscience.whistlepunk.WhistlePunkApplication;
import com.google.android.apps.forscience.whistlepunk.analytics.TrackerConstants;
import com.google.android.apps.forscience.whistlepunk.data.GoosciSensorLayout;
import com.google.protobuf.nano.InvalidProtocolBufferNanoException;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

/**
 * The fragment for displaying a list of triggers.
 */
public class TriggerListFragment extends Fragment {

    private static final String ARG_SENSOR_ID = "sensor_id";
    private static final String ARG_EXPERIMENT_ID = "experiment_id";
    private static final String ARG_SENSOR_LAYOUT = "sensor_layout";
    private static final String ARG_LAYOUT_POSITION = "layout_position";
    private static final String TAG = "TriggerListFragment";

    private static String mSensorId;
    private String mExperimentId;
    private GoosciSensorLayout.SensorLayout mSensorLayout;
    private TriggerListAdapter mTriggerAdapter;
    private int mLayoutPosition;

    public static TriggerListFragment newInstance(String sensorId, String experimentId,
            byte[] sensorLayoutBlob, int position) {
        TriggerListFragment fragment = new TriggerListFragment();
        Bundle args = new Bundle();
        args.putString(ARG_SENSOR_ID, sensorId);
        args.putString(ARG_EXPERIMENT_ID, experimentId);
        args.putByteArray(ARG_SENSOR_LAYOUT, sensorLayoutBlob);
        args.putInt(ARG_LAYOUT_POSITION, position);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mSensorId = getArguments().getString(ARG_SENSOR_ID);
        mExperimentId = getArguments().getString(ARG_EXPERIMENT_ID);
        mLayoutPosition = getArguments().getInt(ARG_LAYOUT_POSITION);
        if (savedInstanceState == null) {
            try {
                mSensorLayout = GoosciSensorLayout.SensorLayout.parseFrom(
                        getArguments().getByteArray(ARG_SENSOR_LAYOUT));
            } catch (InvalidProtocolBufferNanoException e) {
                if (Log.isLoggable(TAG, Log.ERROR)) {
                    Log.e(TAG, "Error parsing the SensorLayout", e);
                }
                mSensorLayout = RecordFragment.defaultLayout();
            }
        } else {
            try {
                mSensorLayout = GoosciSensorLayout.SensorLayout.parseFrom(
                        savedInstanceState.getByteArray(ARG_SENSOR_LAYOUT));
            } catch (InvalidProtocolBufferNanoException e) {
                if (Log.isLoggable(TAG, Log.ERROR)) {
                    Log.e(TAG, "Error parsing the SensorLayout", e);
                }
                mSensorLayout = RecordFragment.defaultLayout();
            }
        }
        setHasOptionsMenu(true);
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        ActionBar actionBar = ((AppCompatActivity) getActivity()).getSupportActionBar();
        actionBar.setDisplayHomeAsUpEnabled(true);

        String sensorName = AppSingleton.getInstance(getActivity())
                .getSensorAppearanceProvider().getAppearance(mSensorId).getName(getActivity());
        actionBar.setTitle(getString(R.string.title_activity_trigger_list, sensorName));

        super.onCreateOptionsMenu(menu, inflater);
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putByteArray(ARG_SENSOR_LAYOUT, ProtoUtils.makeBlob(mSensorLayout));
    }

    @Override
    public void onResume() {
        super.onResume();
        getDataController().getSensorTriggersForSensor(mSensorId,
                new LoggingConsumer<List<SensorTrigger>>(TAG, "get triggers for sensor") {
                    @Override
                    public void success(List<SensorTrigger> triggers) {
                        mTriggerAdapter.setSensorTriggers(triggers);
                    }
                });
        WhistlePunkApplication.getUsageTracker(getActivity()).trackScreenView(
                TrackerConstants.SCREEN_TRIGGER_LIST);
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

    private void launchEditTriggerActivity(SensorTrigger trigger) {
        Intent intent = new Intent(getActivity(), EditTriggerActivity.class);
        if (trigger != null) {
            intent.putExtra(EditTriggerActivity.EXTRA_TRIGGER_ID, trigger.getTriggerId());
            intent.putExtra(EditTriggerActivity.EXTRA_TRIGGER_INFO_BLOB,
                    ProtoUtils.makeBlob(trigger.getTriggerInformation()));
        }
        intent.putExtra(EditTriggerActivity.EXTRA_EXPERIMENT_ID, mExperimentId);
        intent.putExtra(EditTriggerActivity.EXTRA_SENSOR_ID, mSensorId);

        // Also send the Sensor Layout and the position so that this fragment can be recreated on
        // completion.
        intent.putExtra(TriggerListActivity.EXTRA_SENSOR_LAYOUT_BLOB,
                ProtoUtils.makeBlob(mSensorLayout));
        intent.putExtra(TriggerListActivity.EXTRA_LAYOUT_POSITION, mLayoutPosition);

        getActivity().startActivity(intent);
    }

    private void deleteTrigger(final SensorTrigger trigger) {
        final boolean isActive = isTriggerActive(trigger);
        final DataController dc = getDataController();

        // Set up the undo snackbar.
        final Snackbar bar = AccessibilityUtils.makeSnackbar(getView(),
                getActivity().getResources().getString(R.string.sensor_trigger_deleted),
                Snackbar.LENGTH_SHORT);
        bar.setAction(R.string.snackbar_undo, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dc.addSensorTrigger(trigger, mExperimentId,
                        new LoggingConsumer<Success>(TAG, "re-add deleted trigger") {
                            @Override
                            public void success(Success value) {
                                if (isActive) {
                                    // If it was active, re-add it to the Layout.
                                    TriggerHelper.addTriggerToLayoutActiveTriggers(
                                            mSensorLayout, trigger.getTriggerId());
                                    dc.updateSensorLayout(mExperimentId, mLayoutPosition,
                                            mSensorLayout, new LoggingConsumer<Success>(
                                                    TAG, "add trigger to layout") {
                                                @Override
                                                public void success(Success value) {
                                                    mTriggerAdapter.addTrigger(trigger);
                                                }
                                            });
                                } else {
                                    mTriggerAdapter.addTrigger(trigger);
                                }
                            }
                        });
            }
        });

        // Do the deletion, first by removing it from the layout and next by removing it
        // from the trigger database.
        TriggerHelper.removeTriggerFromLayoutActiveTriggers(mSensorLayout,
                trigger.getTriggerId());
        dc.updateSensorLayout(mExperimentId, mLayoutPosition, mSensorLayout,
                new LoggingConsumer<Success>(TAG, "remove trigger from layout") {
                    @Override
                    public void success(Success value) {
                        dc.deleteSensorTrigger(trigger, new LoggingConsumer<Success>(TAG,
                                "delete trigger") {
                            @Override
                            public void success(Success value) {
                                bar.show();
                                mTriggerAdapter.removeTrigger(trigger);
                            }
                        });
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
        if (isActive) {
            TriggerHelper.addTriggerToLayoutActiveTriggers(mSensorLayout,
                    trigger.getTriggerId());
        } else {
            TriggerHelper.removeTriggerFromLayoutActiveTriggers(mSensorLayout,
                    trigger.getTriggerId());
        }
        getDataController().updateSensorLayout(mExperimentId, mLayoutPosition, mSensorLayout,
                new LoggingConsumer<Success>(TAG, "updating trigger active state") {
                    @Override
                    public void success(Success value) {
                        mTriggerAdapter.sortSensorTriggers();
                    }
                });
        // TODO: Should we also update the lastUsed time on this trigger when it is toggled? Ask UX!
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
        public void onBindViewHolder(final ViewHolder holder, int position) {
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
                                    mParentReference.get().deleteTrigger(trigger);
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
            sortSensorTriggers();
            notifyDataSetChanged();
        }

        private void sortSensorTriggers() {
            // TODO: Sort based on activation? Based on last used? Talk with UX!
            // If this needs to sort based on activation, give it a reference to the sensorLayout
            // or activeTriggers list.
            notifyDataSetChanged();
        }

        public void removeTrigger(SensorTrigger trigger) {
            if (mSensorTriggers.contains(trigger)) {
                mSensorTriggers.remove(trigger);
            }
            notifyDataSetChanged();
        }

        public void addTrigger(SensorTrigger trigger) {
            mSensorTriggers.add(trigger);
            sortSensorTriggers();
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
