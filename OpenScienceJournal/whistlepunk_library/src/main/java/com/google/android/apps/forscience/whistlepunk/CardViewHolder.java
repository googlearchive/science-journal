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

package com.google.android.apps.forscience.whistlepunk;

import android.content.Context;
import androidx.recyclerview.widget.RecyclerView;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.TextSwitcher;
import android.widget.TextView;
import android.widget.ViewSwitcher;
import androidx.cardview.widget.CardView;
import com.google.android.apps.forscience.whistlepunk.scalarchart.ChartView;
import com.google.android.material.tabs.TabLayout;

/*
 * View Holder for sensor cards.
 */
public class CardViewHolder extends RecyclerView.ViewHolder {
  public ChartView chartView;
  public ViewGroup sensorTabHolder;
  public View sensorSelectionArea;
  public TabLayout sensorTabLayout;
  public StatsList graphStatsList;
  public SensorCardHeader header;
  public TextView headerText;
  public ToggleArrow toggleButton;
  public View toggleButtonSpacer;
  public ViewGroup graphViewGroup;
  public ImageButton menuButton;
  public ImageButton infoButton;
  public RelativeLayout meterSensorIconContainer;
  public SingleLineResizableTextView meterLiveData;
  public ViewGroup statusViewGroup;
  public ProgressBar statusProgressBar;
  public TextView statusMessage;
  public Button statusRetryButton;
  public RelativeLayout triggerSection;
  public ViewSwitcher triggerIcon;
  public ImageButton triggerLevelDrawableButton;
  public TextSwitcher triggerTextSwitcher;
  public TriggerBackgroundView triggerFiredBackground;
  public TextView triggerFiredText;
  public ImageButton sensorSettingsGear;
  public int screenOrientation;

  public CardViewHolder(CardView itemView) {
    super(itemView);
    chartView = (ChartView) itemView.findViewById(R.id.chart_view);
    sensorSelectionArea = itemView.findViewById(R.id.sensor_selection_area);
    sensorTabLayout = (TabLayout) itemView.findViewById(R.id.sensor_selector_tab_layout);
    sensorSettingsGear = (ImageButton) itemView.findViewById(R.id.settings_gear);
    sensorTabHolder = (ViewGroup) itemView.findViewById(R.id.sensor_selection_tab_holder);
    graphStatsList = (StatsList) itemView.findViewById(R.id.stats_drawer);
    header = (SensorCardHeader) itemView.findViewById(R.id.sensor_card_header);
    headerText = (TextView) itemView.findViewById(R.id.sensor_card_header_title);
    toggleButton = (ToggleArrow) itemView.findViewById(R.id.btn_sensor_card_toggle);
    toggleButtonSpacer = itemView.findViewById(R.id.sensor_card_toggle_spacer);
    menuButton = (ImageButton) itemView.findViewById(R.id.btn_sensor_card_overflow_menu);
    graphViewGroup = (ViewGroup) itemView.findViewById(R.id.graph_view_content_group);
    meterSensorIconContainer =
        (RelativeLayout) itemView.findViewById(R.id.card_meter_sensor_icon_container);
    meterLiveData = (SingleLineResizableTextView) itemView.findViewById(R.id.live_sensor_value);
    statusViewGroup = (ViewGroup) itemView.findViewById(R.id.status_view_content_group);
    statusProgressBar = (ProgressBar) itemView.findViewById(R.id.progress_bar);
    statusMessage = (TextView) itemView.findViewById(R.id.status_message);
    statusRetryButton = (Button) itemView.findViewById(R.id.status_retry_button);
    triggerSection = (RelativeLayout) itemView.findViewById(R.id.sensor_card_trigger_section);
    triggerIcon = (ViewSwitcher) itemView.findViewById(R.id.trigger_icon_view_switcher);
    triggerLevelDrawableButton = (ImageButton) itemView.findViewById(R.id.sensor_trigger_icon);
    triggerTextSwitcher = (TextSwitcher) itemView.findViewById(R.id.trigger_text_switcher);
    triggerFiredBackground =
        (TriggerBackgroundView) itemView.findViewById(R.id.sensor_trigger_fired_background);
    triggerFiredText = (TextView) itemView.findViewById(R.id.trigger_fired_text);
    infoButton = (ImageButton) itemView.findViewById(R.id.btn_info);

    WindowManager windowManager =
        (WindowManager) itemView.getContext().getSystemService(Context.WINDOW_SERVICE);
    screenOrientation = windowManager.getDefaultDisplay().getRotation();
  }

  public Context getContext() {
    return itemView.getContext();
  }
}
