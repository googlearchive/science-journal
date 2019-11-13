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
import androidx.fragment.app.Fragment;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.appcompat.widget.PopupMenu;
import androidx.recyclerview.widget.RecyclerView;
import androidx.appcompat.widget.SwitchCompat;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.ImageButton;
import android.widget.TextView;
import com.google.android.apps.forscience.javalib.Success;
import com.google.android.apps.forscience.whistlepunk.AccessibilityUtils;
import com.google.android.apps.forscience.whistlepunk.AppSingleton;
import com.google.android.apps.forscience.whistlepunk.DataController;
import com.google.android.apps.forscience.whistlepunk.LoggingConsumer;
import com.google.android.apps.forscience.whistlepunk.R;
import com.google.android.apps.forscience.whistlepunk.WhistlePunkApplication;
import com.google.android.apps.forscience.whistlepunk.accounts.AppAccount;
import com.google.android.apps.forscience.whistlepunk.analytics.TrackerConstants;
import com.google.android.apps.forscience.whistlepunk.filemetadata.Experiment;
import com.google.android.apps.forscience.whistlepunk.filemetadata.SensorLayoutPojo;
import com.google.android.apps.forscience.whistlepunk.filemetadata.SensorTrigger;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.snackbar.Snackbar;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/** The fragment for displaying a list of triggers. */
public class TriggerListFragment extends Fragment {

  private static final String ARG_ACCOUNT_KEY = "account_key";
  private static final String ARG_SENSOR_ID = "sensor_id";
  private static final String ARG_EXPERIMENT_ID = "experiment_id";
  private static final String ARG_LAYOUT_POSITION = "layout_position";
  public static final String ARG_TRIGGER_ORDER = "trigger_order";
  private static final String KEY_TRIGGER_ORDER = "trigger_order";
  private static final String TAG = "TriggerListFragment";

  private static String sensorId;
  private AppAccount appAccount;
  private String experimentId;
  private SensorLayoutPojo sensorLayout;
  private TriggerListAdapter triggerAdapter;
  private int layoutPosition;
  private ArrayList<String> triggerOrder;
  private Experiment experiment;
  private boolean needsSave = false;

  public static TriggerListFragment newInstance(
      AppAccount appAccount,
      String sensorId,
      String experimentId,
      int position,
      ArrayList<String> triggerOrder) {
    TriggerListFragment fragment = new TriggerListFragment();
    Bundle args = new Bundle();
    args.putString(ARG_ACCOUNT_KEY, appAccount.getAccountKey());
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
    appAccount = WhistlePunkApplication.getAccount(getContext(), getArguments(), ARG_ACCOUNT_KEY);
    sensorId = getArguments().getString(ARG_SENSOR_ID);
    experimentId = getArguments().getString(ARG_EXPERIMENT_ID);
    layoutPosition = getArguments().getInt(ARG_LAYOUT_POSITION);
    if (savedInstanceState != null) {
      triggerOrder = savedInstanceState.getStringArrayList(KEY_TRIGGER_ORDER);
    } else {
      triggerOrder = getArguments().getStringArrayList(ARG_TRIGGER_ORDER);
    }
    setHasOptionsMenu(true);
  }

  @Override
  public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
    ActionBar actionBar = ((AppCompatActivity) getActivity()).getSupportActionBar();
    actionBar.setDisplayHomeAsUpEnabled(true);

    String sensorName =
        AppSingleton.getInstance(getActivity())
            .getSensorAppearanceProvider(appAccount)
            .getAppearance(sensorId)
            .getName(getActivity());
    actionBar.setTitle(getString(R.string.title_fragment_trigger_list, sensorName));

    super.onCreateOptionsMenu(menu, inflater);
  }

  @Override
  public void onStart() {
    super.onStart();
    WhistlePunkApplication.getUsageTracker(getActivity())
        .trackScreenView(TrackerConstants.SCREEN_TRIGGER_LIST);
  }

  @Override
  public void onResume() {
    super.onResume();
    loadExperiment();
  }

  @Override
  public void onPause() {
    if (needsSave) {
      getDataController()
          .updateExperiment(
              experimentId,
              LoggingConsumer.<Success>expectSuccess(TAG, "updating sensor layout onPause"));
    }
    super.onPause();
  }

  @Override
  public void onSaveInstanceState(Bundle outState) {
    super.onSaveInstanceState(outState);
    saveTriggerOrder();
    outState.putStringArrayList(KEY_TRIGGER_ORDER, triggerOrder);
  }

  private DataController getDataController() {
    return AppSingleton.getInstance(getActivity()).getDataController(appAccount);
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
    triggerList.setLayoutManager(
        new LinearLayoutManager(view.getContext(), LinearLayoutManager.VERTICAL, false));
    triggerAdapter = new TriggerListAdapter(this);
    triggerList.setAdapter(triggerAdapter);

    FloatingActionButton addButton =
        (FloatingActionButton) view.findViewById(R.id.add_trigger_button);
    addButton.setOnClickListener(
        new View.OnClickListener() {
          @Override
          public void onClick(View v) {
            launchEditTriggerActivity(null);
          }
        });
    return view;
  }

  private void loadExperiment() {
    getDataController()
        .getExperimentById(
            experimentId,
            new LoggingConsumer<Experiment>(TAG, "get experiment") {
              @Override
              public void success(Experiment experiment) {
                TriggerListFragment.this.experiment = experiment;
                for (SensorLayoutPojo layout : experiment.getSensorLayouts()) {
                  if (TextUtils.equals(layout.getSensorId(), sensorId)) {
                    sensorLayout = layout;
                  }
                }
                Comparator<SensorTrigger> cp;
                if (triggerOrder != null) {
                  // If this is not the first load, use the saved order to define a new
                  // order, but insert new triggers at the top.
                  cp =
                      new Comparator<SensorTrigger>() {
                        @Override
                        public int compare(SensorTrigger lhs, SensorTrigger rhs) {
                          int lhsIndex = triggerOrder.indexOf(lhs.getTriggerId());
                          int rhsIndex = triggerOrder.indexOf(rhs.getTriggerId());
                          if (lhsIndex == rhsIndex && lhsIndex == -1) {
                            // If they are both not found, they are both new.
                            return Long.compare(rhs.getLastUsed(), lhs.getLastUsed());
                          }
                          return Integer.compare(lhsIndex, rhsIndex);
                        }
                      };
                } else {
                  // Only do this sort on the first load.
                  cp =
                      new Comparator<SensorTrigger>() {
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
                List<SensorTrigger> triggers = experiment.getSensorTriggersForSensor(sensorId);
                Collections.sort(triggers, cp);
                triggerAdapter.setSensorTriggers(triggers);
              }
            });
  }

  private void launchEditTriggerActivity(SensorTrigger trigger) {
    Intent intent = new Intent(getActivity(), EditTriggerActivity.class);
    if (trigger != null) {
      intent.putExtra(EditTriggerActivity.EXTRA_TRIGGER_ID, trigger.getTriggerId());
    }
    intent.putExtra(EditTriggerActivity.EXTRA_ACCOUNT_KEY, appAccount.getAccountKey());
    intent.putExtra(EditTriggerActivity.EXTRA_EXPERIMENT_ID, experimentId);
    intent.putExtra(EditTriggerActivity.EXTRA_SENSOR_ID, sensorId);

    // Also send the Sensor Layout and the position so that this fragment can be recreated on
    // completion, and the order in which the triggers are shown so that the order does not
    // change when the user gets back.
    intent.putExtra(
        EditTriggerActivity.EXTRA_SENSOR_LAYOUT_BLOB, sensorLayout.toProto().toByteArray());
    intent.putExtra(TriggerListActivity.EXTRA_LAYOUT_POSITION, layoutPosition);
    saveTriggerOrder();
    intent.putExtra(TriggerListActivity.EXTRA_TRIGGER_ORDER, triggerOrder);

    getActivity().startActivity(intent);
  }

  private void saveTriggerOrder() {
    triggerOrder = new ArrayList<>();
    for (SensorTrigger trigger : triggerAdapter.sensorTriggers) {
      triggerOrder.add(trigger.getTriggerId());
    }
  }

  private void deleteTrigger(final SensorTrigger trigger, final int index) {
    final boolean isActive = isTriggerActive(trigger);
    final DataController dc = getDataController();

    // Set up the undo snackbar.
    final Snackbar bar =
        AccessibilityUtils.makeSnackbar(
            getView(),
            getActivity().getResources().getString(R.string.sensor_trigger_deleted),
            Snackbar.LENGTH_SHORT);
    bar.setAction(
        R.string.snackbar_undo,
        new View.OnClickListener() {
          boolean undone = false;

          @Override
          public void onClick(View v) {
            if (undone) {
              return;
            }
            undone = true;
            experiment.addSensorTrigger(trigger);
            if (isActive) {
              TriggerHelper.addTriggerToLayoutActiveTriggers(sensorLayout, trigger.getTriggerId());
              experiment.updateSensorLayout(layoutPosition, sensorLayout);
            }
            dc.updateExperiment(
                experimentId,
                new LoggingConsumer<Success>(TAG, "update exp: re-add deleted trigger") {
                  @Override
                  public void success(Success value) {
                    if (isActive) {
                      // If it was active, re-add it to the Layout.
                      triggerAdapter.addTriggerAtIndex(trigger, index);
                    } else {
                      triggerAdapter.addTriggerAtIndex(trigger, index);
                    }
                  }
                });
          }
        });

    // Do the deletion, first by removing it from the layout and next by removing it
    // from the trigger database.
    TriggerHelper.removeTriggerFromLayoutActiveTriggers(sensorLayout, trigger.getTriggerId());
    experiment.removeSensorTrigger(trigger);
    experiment.updateSensorLayout(layoutPosition, sensorLayout);
    dc.updateExperiment(
        experimentId,
        new LoggingConsumer<Success>(TAG, "delete trigger") {
          @Override
          public void success(Success value) {
            bar.show();
            triggerAdapter.removeTrigger(trigger);
          }
        });
  }

  private boolean isTriggerActive(SensorTrigger trigger) {
    return sensorLayout.getActiveSensorTriggerIds().contains(trigger.getTriggerId());
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
    needsSave = true;
    if (isActive) {
      TriggerHelper.addTriggerToLayoutActiveTriggers(sensorLayout, trigger.getTriggerId());
    } else {
      TriggerHelper.removeTriggerFromLayoutActiveTriggers(sensorLayout, trigger.getTriggerId());
    }
    experiment.updateSensorLayout(layoutPosition, sensorLayout);
    // Note: Last used time is not updated when the trigger is activated / deactivated, and the
    // list should not be resorted at this time.
  }

  private static class TriggerListAdapter
      extends RecyclerView.Adapter<TriggerListAdapter.ViewHolder> {

    private static final int VIEW_TYPE_TRIGGER = 0;
    private static final int VIEW_TYPE_EMPTY = 1;

    List<SensorTrigger> sensorTriggers = new ArrayList<>();
    private final WeakReference<TriggerListFragment> parentReference;

    public TriggerListAdapter(TriggerListFragment parent) {
      parentReference = new WeakReference<TriggerListFragment>(parent);
    }

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
      LayoutInflater inflater = LayoutInflater.from(parent.getContext());
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
      final SensorTrigger trigger = sensorTriggers.get(position);
      if (parentReference.get() == null) {
        return;
      }
      holder.description.setText(
          TriggerHelper.buildDescription(
              trigger, parentReference.get().getActivity(), parentReference.get().appAccount));

      holder.menuButton.setOnClickListener(
          new View.OnClickListener() {
            @Override
            public void onClick(View v) {
              Context context = holder.menuButton.getContext();
              PopupMenu popup =
                  new PopupMenu(
                      context,
                      holder.menuButton,
                      Gravity.NO_GRAVITY,
                      R.attr.actionOverflowMenuStyle,
                      0);
              popup.getMenuInflater().inflate(R.menu.menu_sensor_trigger, popup.getMenu());
              popup.setOnMenuItemClickListener(
                  new PopupMenu.OnMenuItemClickListener() {
                    public boolean onMenuItemClick(MenuItem item) {
                      if (item.getItemId() == R.id.edit_trigger) {
                        if (parentReference.get() != null) {
                          parentReference.get().launchEditTriggerActivity(trigger);
                        }
                        return true;
                      } else if (item.getItemId() == R.id.delete_trigger) {
                        if (parentReference.get() != null) {
                          parentReference
                              .get()
                              .deleteTrigger(trigger, sensorTriggers.indexOf(trigger));
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
      holder.activationSwitch.setChecked(parentReference.get().isTriggerActive(trigger));
      holder.activationSwitch.setOnCheckedChangeListener(
          new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
              if (parentReference.get() != null) {
                parentReference.get().setSensorTriggerActive(trigger, isChecked);
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
      return sensorTriggers.size() == 0 ? 1 : sensorTriggers.size();
    }

    @Override
    public int getItemViewType(int position) {
      if (position == 0 && sensorTriggers.size() == 0) {
        return VIEW_TYPE_EMPTY;
      }
      return VIEW_TYPE_TRIGGER;
    }

    public void setSensorTriggers(List<SensorTrigger> sensorTriggers) {
      this.sensorTriggers = sensorTriggers;
      notifyDataSetChanged();
    }

    public void removeTrigger(SensorTrigger trigger) {
      if (sensorTriggers.contains(trigger)) {
        sensorTriggers.remove(trigger);
      }
      notifyDataSetChanged();
    }

    public void addTriggerAtIndex(SensorTrigger trigger, int index) {
      sensorTriggers.add(index, trigger);
      notifyDataSetChanged();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
      int viewType;
      TextView description;
      ImageButton menuButton;
      SwitchCompat activationSwitch;

      public ViewHolder(View itemView, int viewType) {
        super(itemView);
        this.viewType = viewType;
        if (viewType == VIEW_TYPE_TRIGGER) {
          description = (TextView) itemView.findViewById(R.id.trigger_description);
          menuButton = (ImageButton) itemView.findViewById(R.id.btn_trigger_menu);
          activationSwitch = (SwitchCompat) itemView.findViewById(R.id.trigger_activation_switch);
        }
      }
    }
  }
}
