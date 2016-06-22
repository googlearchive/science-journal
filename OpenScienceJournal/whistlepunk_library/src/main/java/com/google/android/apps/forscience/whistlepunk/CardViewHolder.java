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
import android.support.design.widget.TabLayout;
import android.support.v7.widget.CardView;
import android.support.v7.widget.RecyclerView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

/*
 * View Holder for sensor cards.
 */
public class CardViewHolder extends RecyclerView.ViewHolder {
    public FrameLayout graphViewContent;
    public ViewGroup sensorTabHolder;
    public View sensorSelectionArea;
    public ScrollListenerTabLayout sensorTabLayout;
    public StatsList graphStatsList;
    public StatsList meterStatsList;
    public SensorCardHeader header;
    public TextView headerText;
    public ImageButton toggleButton;
    public View toggleButtonSpacer;
    public TextView meterViewDescription;
    public ViewGroup graphViewGroup;
    public ViewGroup meterViewGroup;
    public ImageButton flipButton;
    public ImageButton menuButton;
    public Button infoButton;
    public ImageView meterSensorIcon;
    public TextView meterLiveData;
    public TextView meterLiveDataUnits;
    public LinearLayout infoSection;
    public ViewGroup statusViewGroup;
    public ProgressBar statusProgressBar;
    public TextView statusMessage;
    public final Button statusRetryButton;

    public CardViewHolder(CardView itemView) {
        super(itemView);
        graphViewContent = (FrameLayout) itemView.findViewById(R.id.graph_view_content);
        sensorSelectionArea = itemView.findViewById(R.id.sensor_selection_area);
        sensorTabLayout = (ScrollListenerTabLayout) itemView.findViewById(
                R.id.sensor_selector_tab_layout);
        sensorTabHolder = (ViewGroup) itemView.findViewById(R.id.sensor_selection_tab_holder);
        graphStatsList = (StatsList) itemView.findViewById(R.id.stats_drawer);
        meterStatsList = (StatsList) itemView.findViewById(R.id.stats_view_meter);
        header = (SensorCardHeader) itemView.findViewById(R.id.sensor_card_header);
        headerText = (TextView) itemView.findViewById(R.id.sensor_card_header_title);
        toggleButton = (ImageButton) itemView.findViewById(R.id.btn_sensor_card_toggle);
        toggleButtonSpacer = itemView.findViewById(R.id.sensor_card_toggle_spacer);
        meterViewDescription = (TextView) itemView.findViewById(R.id.back_view_description);
        flipButton = (ImageButton) itemView.findViewById(R.id.btn_sensor_card_flip);
        menuButton = (ImageButton) itemView.findViewById(R.id.btn_sensor_card_overflow_menu);
        graphViewGroup = (ViewGroup) itemView.findViewById(R.id.graph_view_content_group);
        meterViewGroup = (ViewGroup) itemView.findViewById(R.id.back_view_content_group);
        infoButton = (Button) itemView.findViewById(R.id.btn_sensor_learn_more);
        meterSensorIcon = (ImageView) itemView.findViewById(R.id.card_meter_sensor_icon);
        meterLiveData = (TextView) itemView.findViewById(R.id.live_sensor_value);
        meterLiveDataUnits = (TextView) itemView.findViewById(R.id.live_sensor_units);
        infoSection = (LinearLayout) itemView.findViewById(R.id.back_view_info_section);
        statusViewGroup = (ViewGroup) itemView.findViewById(R.id.status_view_content_group);
        statusProgressBar = (ProgressBar) itemView.findViewById(R.id.progress_bar);
        statusMessage = (TextView) itemView.findViewById(R.id.status_message);
        statusRetryButton = (Button) itemView.findViewById(R.id.status_retry_button);
    }

    public Context getContext() {
        return itemView.getContext();
    }
}