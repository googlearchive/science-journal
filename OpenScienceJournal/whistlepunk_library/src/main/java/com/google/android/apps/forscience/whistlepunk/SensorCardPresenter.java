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

import android.animation.ValueAnimator;
import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.content.res.Resources;
import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;
import androidx.fragment.app.Fragment;
import androidx.appcompat.widget.PopupMenu;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import com.google.android.apps.forscience.whistlepunk.audiogen.SonificationTypeAdapterFactory;
import com.google.android.apps.forscience.whistlepunk.devicemanager.ManageDevicesActivity;
import com.google.android.apps.forscience.whistlepunk.filemetadata.Experiment;
import com.google.android.apps.forscience.whistlepunk.filemetadata.Label;
import com.google.android.apps.forscience.whistlepunk.filemetadata.SensorLayoutPojo;
import com.google.android.apps.forscience.whistlepunk.filemetadata.SensorTrigger;
import com.google.android.apps.forscience.whistlepunk.metadata.TriggerListActivity;
import com.google.android.apps.forscience.whistlepunk.scalarchart.ScalarDisplayOptions;
import com.google.android.apps.forscience.whistlepunk.sensorapi.BlankReadableSensorOptions;
import com.google.android.apps.forscience.whistlepunk.sensorapi.DataViewOptions;
import com.google.android.apps.forscience.whistlepunk.sensorapi.NewOptionsStorage;
import com.google.android.apps.forscience.whistlepunk.sensorapi.OptionsListener;
import com.google.android.apps.forscience.whistlepunk.sensorapi.ReadableSensorOptions;
import com.google.android.apps.forscience.whistlepunk.sensorapi.SensorChoice;
import com.google.android.apps.forscience.whistlepunk.sensorapi.SensorObserver;
import com.google.android.apps.forscience.whistlepunk.sensorapi.SensorPresenter;
import com.google.android.apps.forscience.whistlepunk.sensorapi.SensorStatusListener;
import com.google.android.apps.forscience.whistlepunk.sensorapi.StreamStat;
import com.google.android.apps.forscience.whistlepunk.sensors.AccelerometerSensor;
import com.google.android.apps.forscience.whistlepunk.sensors.AmbientLightSensor;
import com.google.android.apps.forscience.whistlepunk.sensors.BarometerSensor;
import com.google.android.apps.forscience.whistlepunk.sensors.CompassSensor;
import com.google.android.apps.forscience.whistlepunk.sensors.DecibelSensor;
import com.google.android.apps.forscience.whistlepunk.sensors.LinearAccelerometerSensor;
import com.google.android.apps.forscience.whistlepunk.sensors.MagneticStrengthSensor;
import com.google.android.apps.forscience.whistlepunk.sensors.PitchSensor;
import com.google.android.apps.forscience.whistlepunk.wireapi.RecordingMetadata;
import com.google.android.material.tabs.TabLayout;
import com.google.common.collect.Lists;
import com.jakewharton.rxbinding2.view.RxView;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** Holds the data and objects necessary for a sensor view. */
public class SensorCardPresenter {
  private static final String TAG = "SensorCardPres";

  @VisibleForTesting
  public static class CardStatus {
    private int sourceStatus = SensorStatusListener.STATUS_CONNECTING;
    private boolean hasError = false;

    public void setStatus(int status) {
      sourceStatus = status;
    }

    public void setHasError(boolean hasError) {
      this.hasError = hasError;
    }

    public boolean shouldShowConnecting() {
      return sourceStatus == SensorStatusListener.STATUS_CONNECTING && !hasError;
    }

    public boolean isConnected() {
      return !hasError && sourceStatus == SensorStatusListener.STATUS_CONNECTED;
    }

    public boolean shouldShowRetry() {
      // if there's an error, or the sensor disconnected, we should allow an attempt to
      // reconnect.
      return hasError || sourceStatus == SensorStatusListener.STATUS_DISCONNECTED;
    }

    public boolean hasError() {
      return hasError;
    }

    @Override
    public String toString() {
      return "CardStatus{" + "sourceStatus=" + sourceStatus + ", hasError=" + hasError + '}';
    }
  }

  /** Object listening for when Sensor Selector tab items are clicked. */
  public interface OnSensorClickListener {
    /** Called when user is requesting to move to a sensor */
    void onSensorClicked(String sensorId);
  }

  private OnSensorClickListener onSensorClickListener;

  /** Object listening for when the close button is clicked. */
  public interface OnCloseClickedListener {
    /** Called when the close button is selected. */
    void onCloseClicked();
  }

  private OnCloseClickedListener closeListener;

  // The height of a sensor presenter when multiple cards are visible is 60% of maximum.
  private static final double MULTIPLE_CARD_HEIGHT_PERCENT = 0.6;

  // The sensor ID ordering.
  private static final String[] SENSOR_ID_ORDER = {
    AmbientLightSensor.ID,
    DecibelSensor.ID,
    PitchSensor.ID,
    LinearAccelerometerSensor.ID,
    AccelerometerSensor.Axis.X.getSensorId(),
    AccelerometerSensor.Axis.Y.getSensorId(),
    AccelerometerSensor.Axis.Z.getSensorId(),
    BarometerSensor.ID,
    CompassSensor.ID,
    MagneticStrengthSensor.ID
  };

  // Update the back data textview every .25 seconds maximum.
  private static final int MAX_TEXT_UPDATE_TIME_MS = 250;

  // Update the back data icon every .025 seconds maximum.
  private static final int MAX_ICON_UPDATE_TIME_MS = 25;

  public static final int ANIMATION_TIME_MS = 200;

  private long recordingStart = RecordingMetadata.NOT_RECORDING;
  private List<String> availableSensorIds;
  private String sensorDisplayName = "";
  private String units = "";
  private String sensorId;
  private final String experimentId;
  private SensorAnimationBehavior sensorAnimationBehavior;
  private SensorChoice currentSource = null;
  private SensorStatusListener sensorStatusListener = null;
  private CardViewHolder cardViewHolder;
  private TabLayout.OnTabSelectedListener onTabSelectedListener;
  private DataViewOptions dataViewOptions;
  private int singleCardPresenterHeight;
  private String initialSourceTagToSelect;
  private boolean isSingleCard = true;
  private View.OnClickListener retryClickListener;
  private boolean paused = false;
  private final Fragment parentFragment;
  private PopupMenu popupMenu;
  private boolean allowRetry = true;
  private CardTriggerPresenter cardTriggerPresenter;
  private ExternalAxisController.InteractionListener interactionListener;
  private final CardStatus cardStatus = new CardStatus();

  private OptionsListener commitListener =
      new OptionsListener() {
        @Override
        public void applyOptions(ReadableSensorOptions settings) {
          recorderController.applyOptions(
              sensorId, AbstractReadableSensorOptions.makeTransportable(settings));
        }
      };

  // The last timestamp when the back of the card data was update.
  // This works unless your phone thinks it is 1970 or earlier!
  private long lastUpdatedIconTimestamp = -1;
  private long lastUpdatedTextTimestamp = -1;
  private boolean textTimeHasElapsed = false;

  private interface ValueFormatter {
    String format(String valueString, String units);
  }

  private ValueFormatter dataFormat;
  private NumberFormat numberFormat;
  private LocalSensorOptionsStorage cardOptions = new LocalSensorOptionsStorage();

  /**
   * A SensorPresenter that can respond to further UI events and update the capture display, or null
   * if the sensor doesn't expect to respond to any events from outside the content view.
   */
  private SensorPresenter sensorPresenter = null;

  private String observerId;

  private SensorAppearanceProvider appearanceProvider;
  private SensorSettingsController sensorSettingsController;
  private final RecorderController recorderController;

  private SensorLayoutPojo layout;

  private boolean isActive = false;
  private boolean firstObserving = true;

  public SensorCardPresenter(
      DataViewOptions dataViewOptions,
      SensorSettingsController sensorSettingsController,
      RecorderController recorderController,
      SensorLayoutPojo layout,
      String experimentId,
      ExternalAxisController.InteractionListener interactionListener,
      Fragment fragment) {
    this.dataViewOptions = dataViewOptions;
    this.sensorSettingsController = sensorSettingsController;
    this.recorderController = recorderController;
    this.interactionListener = interactionListener;
    availableSensorIds = new ArrayList<>();
    this.layout = layout;
    cardOptions.putAllExtras(layout.getExtras());
    this.experimentId = experimentId;
    parentFragment = fragment; // TODO: Should this use a weak reference?
    cardTriggerPresenter =
        new CardTriggerPresenter(
            new CardTriggerPresenter.OnCardTriggerClickedListener() {
              @Override
              public void onCardTriggerIconClicked() {
                if (!isRecording() && cardStatus.isConnected()) {
                  startSetTriggersActivity();
                }
              }
            },
            parentFragment);
  }

  public void onNewData(long timestamp, SensorObserver.Data bundle) {
    if (sensorPresenter == null) {
      return;
    }
    sensorPresenter.onNewData(timestamp, bundle);
    boolean iconTimeHasElapsed = timestamp > lastUpdatedIconTimestamp + MAX_ICON_UPDATE_TIME_MS;
    textTimeHasElapsed = timestamp > lastUpdatedTextTimestamp + MAX_TEXT_UPDATE_TIME_MS;
    if (!textTimeHasElapsed && !iconTimeHasElapsed) {
      return;
    }
    if (sensorAnimationBehavior.updateIconAndTextTogether()) {
      // For some sensors (for example, the pitch sensor), it doesn't make sense to update
      // the icon without updating the text.
      textTimeHasElapsed = true;
      iconTimeHasElapsed = true;
    }

    if (textTimeHasElapsed) {
      lastUpdatedTextTimestamp = timestamp;
    }
    if (iconTimeHasElapsed) {
      lastUpdatedIconTimestamp = timestamp;
    }
    if (cardViewHolder == null) {
      return;
    }
    if (bundle.hasValidValue()) {
      double value = bundle.getValue();
      if (textTimeHasElapsed) {
        String valueString = numberFormat.format(value);
        cardViewHolder.meterLiveData.setText(dataFormat.format(valueString, units));
      }
      if (iconTimeHasElapsed && sensorPresenter != null) {
        sensorAnimationBehavior.updateIcon(
            cardViewHolder.meterSensorIconContainer,
            value,
            sensorPresenter.getMinY(),
            sensorPresenter.getMaxY(),
            cardViewHolder.screenOrientation);
      }
    } else {
      // TODO: Show an error state for no numerical value.
      cardViewHolder.meterLiveData.setText("");
      sensorAnimationBehavior.resetIcon(cardViewHolder.meterSensorIconContainer);
    }
  }

  public void onSensorTriggerFired() {
    cardTriggerPresenter.onSensorTriggerFired();
  }

  public void onSourceStatusUpdate(String id, int status) {
    if (!TextUtils.equals(id, sensorId)) {
      return;
    }
    cardStatus.setStatus(status);
    if (!paused) {
      updateStatusUi();
    }
  }

  public void onSourceError(boolean hasError) {
    cardStatus.setHasError(hasError);
    updateStatusUi();
  }

  private void updateAudio(boolean enabled, String sonificationType) {
    sensorPresenter.updateAudioSettings(enabled, sonificationType);
    if (cardViewHolder != null) {
      updateAudioEnabledUi(enabled);
    }
  }

  private void updateAudioEnabledUi(boolean isEnabled) {
    String display =
        isEnabled
            ? String.format(
                cardViewHolder.getContext().getString(R.string.audio_enabled_format),
                sensorDisplayName)
            : sensorDisplayName;
    cardViewHolder.headerText.setText(display);
  }

  private void updateStatusUi() {
    // Turn off the audio unless it is connected.
    if (sensorPresenter != null) {
      if (cardStatus.isConnected() && currentSource != null) {
        updateAudio(layout.isAudioEnabled(), getSonificationType(parentFragment.getActivity()));
      } else {
        updateAudio(false, SonificationTypeAdapterFactory.DEFAULT_SONIFICATION_TYPE);
      }
    }
    if (cardViewHolder == null) {
      return;
    }
    updateSensorTriggerUi();
    updateCardMenu();
    if (cardStatus.isConnected()) {
      // We are connected with no error! Set everything back to normal.
      cardViewHolder.statusViewGroup.setVisibility(View.GONE);
      cardViewHolder.graphViewGroup.setVisibility(View.VISIBLE);
      return;
    }
    cardViewHolder.statusViewGroup.bringToFront();
    cardViewHolder.statusViewGroup.setVisibility(View.VISIBLE);
    cardViewHolder.statusRetryButton.setVisibility(View.GONE);

    // Make the graph view group not explorable in TalkBack, so the user can't find
    // those views underneath the error state view.
    cardViewHolder.graphViewGroup.setVisibility(View.GONE);

    if (cardStatus.shouldShowConnecting()) {
      // Show a progress bar inside the card while connecting.
      cardViewHolder.statusMessage.setText(
          cardViewHolder.getContext().getText(R.string.sensor_card_loading_text));
      cardViewHolder.statusProgressBar.setVisibility(View.VISIBLE);
    } else {
      cardViewHolder.statusMessage.setText(
          cardViewHolder.getContext().getText(R.string.sensor_card_error_text));
      cardViewHolder.statusProgressBar.setVisibility(View.GONE);

      // An error
      if (cardStatus.shouldShowRetry() && retryClickListener != null && allowRetry) {
        cardViewHolder.statusRetryButton.setVisibility(View.VISIBLE);
        cardViewHolder.statusRetryButton.setOnClickListener(retryClickListener);
      }
    }
  }

  public void startObserving(
      SensorChoice sensorChoice,
      SensorPresenter sensorPresenter,
      ReadableSensorOptions readOptions,
      Experiment experiment,
      SensorRegistry sensorRegistry) {
    final ReadableSensorOptions nonNullOptions =
        BlankReadableSensorOptions.blankIfNull(readOptions);
    currentSource = sensorChoice;
    this.sensorPresenter = sensorPresenter;
    List<SensorTrigger> triggers;
    if (layout.getActiveSensorTriggerIds().isEmpty()) {
      updateCardMenu();
      triggers = Collections.emptyList();
    } else {
      triggers = experiment.getActiveSensorTriggers(layout);
    }
    cardTriggerPresenter.setSensorTriggers(triggers, recorderController.getAppAccount());
    observerId =
        recorderController.startObserving(
            currentSource.getId(),
            triggers,
            new SensorObserver() {
              @Override
              public void onNewData(long timestamp, Data value) {
                SensorCardPresenter.this.onNewData(timestamp, value);
              }
            },
            getSensorStatusListener(),
            AbstractReadableSensorOptions.makeTransportable(nonNullOptions),
            sensorRegistry);
    if (cardStatus.isConnected() && parentFragment != null) {
      updateAudio(layout.isAudioEnabled(), getSonificationType(parentFragment.getActivity()));
    }
    sensorPresenter.setShowStatsOverlay(layout.isShowStatsOverlay());
    sensorPresenter.setTriggers(triggers);
    if (firstObserving) {
      // The first time we start observing on a sensor, we can load the minimum and maximum
      // y values from the layout. If the sensor is changed, we don't want to keep loading the
      // old min and max values.
      if (layout.getMinimumYAxisValue() < layout.getMaximumYAxisValue()) {
        sensorPresenter.setYAxisRange(layout.getMinimumYAxisValue(), layout.getMaximumYAxisValue());
      }
      firstObserving = false;
    }
    if (cardViewHolder != null) {
      sensorPresenter.startShowing(cardViewHolder.chartView, interactionListener);
      updateSensorTriggerUi();
      updateLearnMoreButton();
    }
    // It is possible we just resumed observing but we are currently recording, in which case
    // we need to refresh the recording UI.
    if (isRecording()) {
      sensorPresenter.onRecordingStateChange(isRecording(), recordingStart);
    }
  }

  @VisibleForTesting
  public void setUiForConnectingNewSensor(
      String sensorId, String sensorDisplayName, String sensorUnits, boolean hasError) {
    units = sensorUnits;
    this.sensorDisplayName = sensorDisplayName;
    // Set sensorId now; if we have to load SensorChoice from database, it may not be available
    // until later.
    this.sensorId = sensorId;
    SensorAppearance appearance = appearanceProvider.getAppearance(sensorId);
    numberFormat = appearance.getNumberFormat();
    sensorAnimationBehavior = appearance.getSensorAnimationBehavior();
    cardStatus.setHasError(hasError);
    cardStatus.setStatus(SensorStatusListener.STATUS_CONNECTING);
    if (cardViewHolder != null) {
      cardViewHolder.headerText.setText(sensorDisplayName);
      setMeterIcon();
      updateStatusUi();
    }
  }

  public void setViews(CardViewHolder cardViewHolder, OnCloseClickedListener closeListener) {
    this.cardViewHolder = cardViewHolder;
    this.closeListener = closeListener;
    cardTriggerPresenter.setViews(cardViewHolder);
    String formatString =
        cardViewHolder.getContext().getResources().getString(R.string.data_with_units);
    dataFormat = getDataFormatter(formatString);

    updateRecordingUi();

    cardViewHolder.headerText.setText(sensorDisplayName);

    if (sensorAnimationBehavior != null) {
      setMeterIcon();
    }

    int color = dataViewOptions.getGraphColor();
    int slightlyLighter = ColorUtils.getSlightlyLighterColor(color);
    cardViewHolder.header.setBackgroundColor(color);
    cardViewHolder.sensorSelectionArea.setBackgroundColor(slightlyLighter);
    cardViewHolder.sensorSettingsGear.setBackground(
        ColorUtils.colorDrawableWithActual(
            cardViewHolder.sensorSettingsGear.getBackground(), color));
    cardViewHolder.statusProgressBar.setIndeterminateTintList(ColorStateList.valueOf(color));

    if (sensorPresenter != null) {
      sensorPresenter.startShowing(cardViewHolder.chartView, interactionListener);
    }

    updateLearnMoreButton();

    cardViewHolder.graphStatsList.setTextBold(layout.isShowStatsOverlay());
    cardViewHolder.graphStatsList.setOnClickListener(
        v -> {
          if (sensorPresenter != null) {
            layout.setShowStatsOverlay(!layout.isShowStatsOverlay());
            sensorPresenter.setShowStatsOverlay(layout.isShowStatsOverlay());
            cardViewHolder.graphStatsList.setTextBold(layout.isShowStatsOverlay());
          }
        });
    updateStatusUi();
    updateAudioEnabledUi(layout.isAudioEnabled());

    // The first time a SensorCardPresenter is created, we cannot use the recycled view.
    // Exact reason unknown but this workaround fixes the bug described in b/24611618.
    // TODO: See if this bug can be resolved in a way that does not require view inflating.
    cardViewHolder.sensorTabHolder.removeAllViews();
    LayoutInflater.from(cardViewHolder.getContext())
        .inflate(R.layout.sensor_selector_tab_layout, cardViewHolder.sensorTabHolder, true);
    cardViewHolder.sensorTabLayout = (TabLayout) cardViewHolder.sensorTabHolder.getChildAt(0);
    cardViewHolder.sensorTabLayout.clearOnTabSelectedListeners();
    cardViewHolder.sensorTabLayout.addOnTabSelectedListener(onTabSelectedListener);
    if (!TextUtils.isEmpty(sensorId)) {
      initializeSensorTabs(sensorId);
    }
    refreshTabLayout();

    RxView.clicks(cardViewHolder.sensorSettingsGear)
        .subscribe(
            click -> {
              ManageDevicesActivity.launch(
                  cardViewHolder.getContext(), recorderController.getAppAccount(), experimentId);
            });

    // Force setActive whenever the views are reset, as previously used views might be already
    // View.GONE.
    // Note: this must be done after setting up the tablayout.
    setActive(isActive, /* force */ true);
  }

  private ValueFormatter getDataFormatter(String formatString) {
    if (formatString.equals("%1$s %2$s")) {
      // This is, I believe, the only format currently used.
      return new ValueFormatter() {
        StringBuffer buffer = new StringBuffer(20);

        @Override
        public String format(String valueString, String units) {
          buffer.setLength(0);
          buffer.append(valueString).append(" ").append(units);
          return buffer.toString();
        }
      };
    } else {
      // Just in case there are other formats, fall back to expensive String.format
      return (valueString, units) -> String.format(formatString, valueString, units);
    }
  }

  private void updateSensorTriggerUi() {
    if (cardViewHolder == null) {
      return;
    }
    if (!cardStatus.isConnected()) {
      cardViewHolder.triggerSection.setVisibility(View.GONE);
      return;
    }
    cardTriggerPresenter.updateSensorTriggerUi();
  }

  private void updateLearnMoreButton() {
    if (shouldShowInfoButton()) {
      cardViewHolder.infoButton.setOnClickListener(
          v -> {
            Context context = v.getContext();
            Intent intent = new Intent(context, SensorInfoActivity.class);
            intent.putExtra(
                SensorInfoActivity.EXTRA_ACCOUNT_KEY,
                recorderController.getAppAccount().getAccountKey());
            intent.putExtra(SensorInfoActivity.EXTRA_SENSOR_ID, sensorId);
            intent.putExtra(SensorInfoActivity.EXTRA_COLOR_ID, dataViewOptions.getGraphColor());
            context.startActivity(intent);
          });
      cardViewHolder.infoButton.setVisibility(View.VISIBLE);
    } else {
      cardViewHolder.infoButton.setOnClickListener(null);
      cardViewHolder.infoButton.setVisibility(View.INVISIBLE);
    }
  }

  private boolean shouldShowInfoButton() {
    return appearanceProvider.getAppearance(sensorId).hasLearnMore()
        || !TextUtils.isEmpty(
            appearanceProvider
                .getAppearance(sensorId)
                .getShortDescription(cardViewHolder.getContext()));
  }

  public void onViewRecycled() {
    if (cardViewHolder != null) {
      cardViewHolder.sensorTabLayout.clearOnTabSelectedListeners();
      cardViewHolder.menuButton.setOnClickListener(null);
      cardViewHolder.infoButton.setOnClickListener(null);
      cardViewHolder.graphStatsList.setOnClickListener(null);
      cardViewHolder.graphStatsList.clearStats();
      cardViewHolder.meterLiveData.setText("");
      cardViewHolder.meterLiveData.resetTextSize();
    }
    if (sensorPresenter != null) {
      sensorPresenter.onViewRecycled();
    }
    cardTriggerPresenter.onViewRecycled();
    closeListener = null;
    cardViewHolder = null;
  }

  private void setMeterIcon() {
    sensorAnimationBehavior.initializeLargeIcon(
        cardViewHolder.meterSensorIconContainer, null /* value */);
  }

  private void updateCardMenu() {
    if (cardViewHolder == null || cardViewHolder.menuButton == null) {
      return;
    }
    cardViewHolder.menuButton.setOnClickListener(v -> openCardMenu());
  }

  private void openCardMenu() {
    if (popupMenu != null) {
      return;
    }
    final Context context = cardViewHolder.getContext();
    Resources res = context.getResources();
    boolean showDevTools = DevOptionsFragment.isDevToolsEnabled(context);
    popupMenu =
        new PopupMenu(
            context, cardViewHolder.menuButton, Gravity.END, R.attr.actionOverflowMenuStyle, 0);
    popupMenu.getMenuInflater().inflate(R.menu.menu_sensor_card, popupMenu.getMenu());
    final Menu menu = popupMenu.getMenu();
    menu.findItem(R.id.btn_sensor_card_close).setVisible(!isSingleCard && !isRecording());

    // Adjusting sensor options through the UI is only a developer option.
    menu.findItem(R.id.btn_sensor_card_settings).setVisible(showDevTools && !isRecording());

    // Don't show audio options if there is an error or bad status.
    boolean sensorConnected = cardStatus.isConnected();
    menu.findItem(R.id.btn_sensor_card_audio_toggle).setEnabled(sensorConnected);
    menu.findItem(R.id.btn_sensor_card_audio_settings).setEnabled(sensorConnected);

    menu.findItem(R.id.btn_sensor_card_audio_toggle)
        .setTitle(
            res.getString(
                layout.isAudioEnabled()
                    ? R.string.graph_options_audio_feedback_disable
                    : R.string.graph_options_audio_feedback_enable));

    menu.findItem(R.id.btn_sensor_card_audio_settings).setVisible(!isRecording());

    // Disable trigger settings during recording.
    menu.findItem(R.id.btn_sensor_card_set_triggers)
        .setEnabled(sensorConnected && !isRecording() && layout != null);
    menu.findItem(R.id.btn_sensor_card_set_triggers)
        .setTitle(
            res.getString(
                cardTriggerPresenter.getSensorTriggers().size() == 0
                    ? R.string.menu_item_set_triggers
                    : R.string.menu_item_edit_triggers));

    // Show the option to disable all triggers only during recording and if triggers exist
    // on the card.
    menu.findItem(R.id.btn_disable_sensor_card_triggers)
        .setVisible(
            isRecording() && layout != null && cardTriggerPresenter.getSensorTriggers().size() > 0);

    popupMenu.setOnMenuItemClickListener(
        new PopupMenu.OnMenuItemClickListener() {
          public boolean onMenuItemClick(MenuItem item) {
            int itemId = item.getItemId();
            if (itemId == R.id.btn_sensor_card_close) {
              if (closeListener != null) {
                closeListener.onCloseClicked();
              }
              return true;
            } else if (itemId == R.id.btn_sensor_card_settings) {
              sensorSettingsController.launchOptionsDialog(
                  currentSource,
                  sensorPresenter,
                  getCardOptions(currentSource, context),
                  commitListener,
                  new NewOptionsStorage.SnackbarFailureListener(cardViewHolder.menuButton));
              return true;
            } else if (itemId == R.id.btn_sensor_card_audio_toggle) {
              layout.setAudioEnabled(!layout.isAudioEnabled());
              updateAudio(layout.isAudioEnabled(), getSonificationType(context));
              return true;
            } else if (itemId == R.id.btn_sensor_card_audio_settings) {
              String currentSonificationType =
                  getCardOptions(currentSource, context)
                      .load(LoggingConsumer.expectSuccess(TAG, "loading card options"))
                      .getReadOnly()
                      .getString(
                          ScalarDisplayOptions.PREFS_KEY_SONIFICATION_TYPE,
                          SonificationTypeAdapterFactory.DEFAULT_SONIFICATION_TYPE);
              AudioSettingsDialog dialog =
                  AudioSettingsDialog.newInstance(
                      recorderController.getAppAccount(),
                      new String[] {currentSonificationType},
                      new String[] {sensorId},
                      0);
              dialog.show(parentFragment.getChildFragmentManager(), AudioSettingsDialog.TAG);
              return true;
            } else if (itemId == R.id.btn_sensor_card_set_triggers) {
              if (parentFragment == null) {
                return false;
              }
              Intent intent = new Intent(parentFragment.getActivity(), TriggerListActivity.class);
              intent.putExtra(
                  TriggerListActivity.EXTRA_ACCOUNT_KEY,
                  recorderController.getAppAccount().getAccountKey());
              intent.putExtra(TriggerListActivity.EXTRA_SENSOR_ID, sensorId);
              intent.putExtra(TriggerListActivity.EXTRA_EXPERIMENT_ID, experimentId);
              if (parentFragment instanceof SensorFragment) {
                intent.putExtra(
                    TriggerListActivity.EXTRA_LAYOUT_POSITION,
                    ((SensorFragment) parentFragment).getPositionOfLayout(layout));
              } else if (parentFragment instanceof RecordFragment) {
                // TODO(b/134590927): Delete this logic when deleting RecordFragment
                intent.putExtra(
                    TriggerListActivity.EXTRA_LAYOUT_POSITION,
                    ((RecordFragment) parentFragment).getPositionOfLayout(layout));
              }
              parentFragment.getActivity().startActivity(intent);
              return true;
            } else if (itemId == R.id.btn_sensor_card_set_triggers) {
              return startSetTriggersActivity();
            } else if (itemId == R.id.btn_disable_sensor_card_triggers) {
              return disableTriggers();
            }
            return false;
          }
        });
    popupMenu.setOnDismissListener(
        new PopupMenu.OnDismissListener() {
          @Override
          public void onDismiss(PopupMenu menu) {
            popupMenu = null;
          }
        });

    popupMenu.show();
  }

  private boolean disableTriggers() {
    // Disable all triggers on this card.
    if (parentFragment == null) {
      return false;
    }
    layout.clearActiveTriggerIds();
    if (parentFragment instanceof SensorFragment) {
      ((SensorFragment) parentFragment).disableAllTriggers(layout, this);
    } else if (parentFragment instanceof RecordFragment) {
      // TODO(b/134590927): Delete this logic when deleting RecordFragment
      ((RecordFragment) parentFragment).disableAllTriggers(layout, this);
    }
    return true;
  }

  public void onSensorTriggersCleared() {
    cardTriggerPresenter.setSensorTriggers(
        Collections.<SensorTrigger>emptyList(), recorderController.getAppAccount());
    sensorPresenter.setTriggers(Collections.<SensorTrigger>emptyList());
    updateSensorTriggerUi();
  }

  private boolean startSetTriggersActivity() {
    if (parentFragment == null) {
      return false;
    }
    Intent intent = new Intent(parentFragment.getActivity(), TriggerListActivity.class);
    intent.putExtra(
        TriggerListActivity.EXTRA_ACCOUNT_KEY, recorderController.getAppAccount().getAccountKey());
    intent.putExtra(TriggerListActivity.EXTRA_SENSOR_ID, sensorId);
    intent.putExtra(TriggerListActivity.EXTRA_EXPERIMENT_ID, experimentId);
    if (parentFragment instanceof SensorFragment) {
      intent.putExtra(
          TriggerListActivity.EXTRA_LAYOUT_POSITION,
          ((SensorFragment) parentFragment).getPositionOfLayout(layout));
    } else if (parentFragment instanceof RecordFragment) {
      // TODO(b/134590927): Delete this logic when deleting RecordFragment
      intent.putExtra(
          TriggerListActivity.EXTRA_LAYOUT_POSITION,
          ((RecordFragment) parentFragment).getPositionOfLayout(layout));
    }
    parentFragment.getActivity().startActivity(intent);
    return true;
  }

  private String getSonificationType(Context context) {
    if (context == null) {
      // Probably tearing down anyway, but return something safe
      return SonificationTypeAdapterFactory.DEFAULT_SONIFICATION_TYPE;
    }
    return getCardOptions(currentSource, context)
        .load(LoggingConsumer.expectSuccess(TAG, "loading card options"))
        .getReadOnly()
        .getString(
            ScalarDisplayOptions.PREFS_KEY_SONIFICATION_TYPE,
            SonificationTypeAdapterFactory.DEFAULT_SONIFICATION_TYPE);
  }

  public void onAudioSettingsPreview(String previewSonificationType) {
    // Must save audio settings in the layout so that if a rotation or backgrounding occurs
    // while the dialog is active, it is saved on resume.
    updateSonificationType(previewSonificationType);
  }

  public void onAudioSettingsApplied(String newSonificationType) {
    updateSonificationType(newSonificationType);
  }

  public void onAudioSettingsCanceled(String originalSonificationType) {
    updateSonificationType(originalSonificationType);
  }

  private void updateSonificationType(String sonificationType) {
    updateAudio(layout.isAudioEnabled(), sonificationType);
    getCardOptions(currentSource, parentFragment.getActivity())
        .load(LoggingConsumer.expectSuccess(TAG, "loading card options"))
        .put(ScalarDisplayOptions.PREFS_KEY_SONIFICATION_TYPE, sonificationType);
  }

  private void initializeSensorTabs(final String sensorIdToSelect) {
    cardViewHolder.sensorTabLayout.removeAllTabs();
    Context context = cardViewHolder.getContext();
    int size = availableSensorIds.size();
    for (int i = 0; i < size; i++) {
      String sensorId = availableSensorIds.get(i);
      addSensorTab(sensorId, i, context);
    }
    // By selecting the tab in a runnable, we also cause the SensorTabLayout to scroll
    // to the correct position.
    cardViewHolder.sensorTabLayout.post(
        new Runnable() {
          @Override
          public void run() {
            if (cardViewHolder != null) {
              int i = availableSensorIds.indexOf(sensorIdToSelect);
              if (i < 0) {
                i = 0;
              }
              TabLayout.Tab tab = cardViewHolder.sensorTabLayout.getTabAt(i);
              if (tab != null) {
                tab.select();
              }
            }
          }
        });
  }

  private void addSensorTab(String sensorId, int index, Context context) {
    final SensorAppearance appearance = appearanceProvider.getAppearance(sensorId);
    TabLayout.Tab tab = cardViewHolder.sensorTabLayout.newTab();
    tab.setContentDescription(appearance.getName(context));
    tab.setIcon(appearance.getIconDrawable(context));
    tab.setTag(sensorId);
    cardViewHolder.sensorTabLayout.addTab(tab, index, false);
    // HACK: we need to retrieve the view using View#findViewByTag to avoid adding lots of
    // callbacks and plumbing, just for a one time use case (feature discovery).
    // We also need to set the content description on the TabView so that FeatureDiscovery can
    // retrieve it properly. This does not seem to cause a double content description in
    // TalkBack, probably because the TabView's content description is otherwise unused.
    // Finding the TabView is dependent on the current implementation of TabLayout, but since
    // it comes from the support library, not worried about it changing on different devices.
    if (cardViewHolder.sensorTabLayout.getChildCount() > 0) {
      View tabView = ((ViewGroup) cardViewHolder.sensorTabLayout.getChildAt(0)).getChildAt(index);
      tabView.setTag(sensorId);
      tabView.setContentDescription(appearance.getName(context));
    }
  }

  public void setOnSensorSelectedListener(final OnSensorClickListener listener) {
    onSensorClickListener = listener;
    onTabSelectedListener =
        new TabLayout.OnTabSelectedListener() {

          @Override
          public void onTabSelected(TabLayout.Tab tab) {
            if (cardViewHolder != null) {
              String newSensorId = (String) tab.getTag();
              trySelectingNewSensor(newSensorId, sensorId);
            }
          }

          @Override
          public void onTabUnselected(TabLayout.Tab tab) {}

          @Override
          public void onTabReselected(TabLayout.Tab tab) {
            if (cardViewHolder != null) {
              String newSensorId = (String) tab.getTag();
              if (TextUtils.equals(sensorId, newSensorId) && sensorPresenter != null) {
                sensorPresenter.resetView();
                // Also need to pin the graph to now again.
                interactionListener.requestResetPinnedState();
              } else {
                trySelectingNewSensor(sensorId, newSensorId);
              }
            }
          }
        };
    if (cardViewHolder != null && isActive) {
      cardViewHolder.sensorTabLayout.clearOnTabSelectedListeners();
      cardViewHolder.sensorTabLayout.addOnTabSelectedListener(onTabSelectedListener);
    }
  }

  private String getSensorName(String sensorId) {
    return appearanceProvider.getAppearance(sensorId).getName(cardViewHolder.getContext());
  }

  // Selects the new sensor if it is different from the old sensor or if no sensor is currently
  // selected.
  private void trySelectingNewSensor(String newSensorId, String oldSensorId) {
    if ((currentSource == null && !cardStatus.hasError())
        || !TextUtils.equals(newSensorId, oldSensorId)) {
      // Clear the active sensor triggers when changing sensors.
      if (!TextUtils.equals(layout.getSensorId(), newSensorId)) {
        layout.clearActiveTriggerIds();
        cardTriggerPresenter.setSensorTriggers(
            Collections.<SensorTrigger>emptyList(), recorderController.getAppAccount());
        updateSensorTriggerUi();
      }
      onSensorClickListener.onSensorClicked(newSensorId);
    }
  }

  public void setAppearanceProvider(SensorAppearanceProvider appearanceProvider) {
    this.appearanceProvider = appearanceProvider;
  }

  /**
   * @param availableSensorIds a _sorted_ list of availableSensorIds, in the order they should be
   *     laid out in sensor tabs.
   * @param allSensorIds all of the sensors, including the currently-used ones, in order.
   */
  public void updateAvailableSensors(List<String> availableSensorIds, List<String> allSensorIds) {
    // We should never be updating the selected sensor.
    List<String> newAvailableSensorIds = new ArrayList(availableSensorIds);
    if (!TextUtils.isEmpty(sensorId) && !availableSensorIds.contains(sensorId)) {
      newAvailableSensorIds.add(sensorId);
    }
    List<String> sorted = customSortSensorIds(newAvailableSensorIds, allSensorIds);
    if (!sorted.equals(this.availableSensorIds)) {
      this.availableSensorIds = sorted;
      if (cardViewHolder != null && !TextUtils.isEmpty(sensorId)) {
        initializeSensorTabs(sensorId);
      }
    }
    refreshTabLayout();
  }

  // TODO: find a way to test without exposing this.
  @VisibleForTesting
  public ArrayList<String> getAvailableSensorIds() {
    return Lists.newArrayList(availableSensorIds);
  }

  // The following is a workaround to a bug described in
  // https://code.google.com/p/android/issues/detail?id=180462.
  private void refreshTabLayout() {
    if (cardViewHolder == null) {
      return;
    }
    cardViewHolder.sensorTabLayout.post(
        new Runnable() {
          public void run() {
            if (cardViewHolder != null) {
              cardViewHolder.sensorTabLayout.requestLayout();
            }
          }
        });
  }

  @VisibleForTesting
  public static List<String> customSortSensorIds(
      List<String> sensorIds, List<String> allSensorIds) {
    List<String> result = new ArrayList(Arrays.asList(SENSOR_ID_ORDER));
    // Keep only the elements in result that are in the available SensorIds list.
    for (String id : SENSOR_ID_ORDER) {
      if (!sensorIds.contains(id)) {
        result.remove(id);
      } else {
        sensorIds.remove(id);
      }
    }

    for (String id : allSensorIds) {
      if (sensorIds.contains(id)) {
        result.add(id);
      }
    }

    return result;
  }

  public String getSelectedSensorId() {
    if (!TextUtils.isEmpty(sensorId)) {
      return sensorId;
    } else if (cardViewHolder != null) {
      // If we are switching sensors, the TabLayout tab may already be selected even
      // though the currentSource is null (after stopObserving but before starting again).
      int position = cardViewHolder.sensorTabLayout.getSelectedTabPosition();
      if (position >= 0) {
        return (String) cardViewHolder.sensorTabLayout.getTabAt(position).getTag();
      }
    }
    return "";
  }

  public DataViewOptions getDataViewOptions() {
    return dataViewOptions;
  }

  private SensorStatusListener getSensorStatusListener() {
    return sensorStatusListener;
  }

  public void setSensorStatusListener(SensorStatusListener sensorStatusListener) {
    this.sensorStatusListener = sensorStatusListener;
  }

  public void setOnRetryClickListener(View.OnClickListener retryClickListener) {
    this.retryClickListener = retryClickListener;
  }

  public SensorPresenter getSensorPresenter() {
    return sensorPresenter;
  }

  // When the stats drawer is recycled, this can return the old drawer for a different
  // sensor, so check whether the view is recycled (unavailable) before updating.
  public void updateStats(List<StreamStat> stats) {
    if (!isRecording()) {
      return;
    }
    if (cardViewHolder != null && sensorPresenter != null && textTimeHasElapsed) {
      cardViewHolder.graphStatsList.updateStats(stats);
      sensorPresenter.updateStats(stats);
    }
  }

  /**
   * Updates the UI of the SensorCard to be "active" (show all buttons) or "inactive" (only show
   * header). If recording is in progress, always deactivates.
   *
   * @param isActive Whether this SensorCardPresenter should be active
   * @param force If true, forces UI updates even if isActive is not changed from the previous
   *     state. This is useful when a card is created for the first time or when views are recycled
   *     from other SensorCards and we want to make sure that they have the correct visibility.
   */
  public void setActive(boolean isActive, boolean force) {
    if (isRecording()) {
      isActive = false;
    }
    int expandedHeight =
        cardViewHolder != null
            ? cardViewHolder
                .getContext()
                .getResources()
                .getDimensionPixelSize(R.dimen.sensor_tablayout_height)
            : 0;
    // Add animation only if "force" is false -- in other words, if this was user initiated!
    if (this.isActive != isActive && !force) {
      this.isActive = isActive;
      if (cardViewHolder != null) {
        int startHeight = isActive ? 0 : expandedHeight;
        int endHeight = isActive ? expandedHeight : 0;
        cardViewHolder.sensorTabLayout.clearOnTabSelectedListeners();
        if (isActive) {
          cardViewHolder.sensorTabLayout.addOnTabSelectedListener(onTabSelectedListener);
        }
        final ValueAnimator animator =
            ValueAnimator.ofInt(startHeight, endHeight).setDuration(ANIMATION_TIME_MS);
        animator.setTarget(cardViewHolder.sensorSelectionArea);
        animator.addUpdateListener(
            new ValueAnimator.AnimatorUpdateListener() {
              @Override
              public void onAnimationUpdate(ValueAnimator animation) {
                if (cardViewHolder == null) {
                  return;
                }
                Integer value = (Integer) animation.getAnimatedValue();
                cardViewHolder.sensorSelectionArea.getLayoutParams().height = value.intValue();
                cardViewHolder.sensorSelectionArea.requestLayout();
              }
            });
        animator.start();
      }
    } else if (force) {
      this.isActive = isActive;
      if (cardViewHolder != null) {
        cardViewHolder.sensorSelectionArea.getLayoutParams().height = isActive ? expandedHeight : 0;
        cardViewHolder.sensorSelectionArea.requestLayout();
      }
    }
    updateButtonsVisibility(!force);
    refreshTabLayout();
  }

  public boolean isActive() {
    return isActive && !isRecording();
  }

  public void setIsSingleCard(boolean isSingleCard) {
    if (cardViewHolder == null) {
      return;
    }
    int height;
    boolean alwaysUseMultiCardHeight =
        cardViewHolder.getContext().getResources().getBoolean(R.bool.always_use_multi_card_height);
    if (alwaysUseMultiCardHeight) {
      // For extra large views, the cards are always shown at the multiple card height.
      height =
          Math.max(
              (int) (MULTIPLE_CARD_HEIGHT_PERCENT * singleCardPresenterHeight),
              cardViewHolder
                  .getContext()
                  .getResources()
                  .getDimensionPixelSize(R.dimen.sensor_card_content_height_min));
    } else {
      height =
          isSingleCard
              ? singleCardPresenterHeight
              : Math.max(
                  (int) (MULTIPLE_CARD_HEIGHT_PERCENT * singleCardPresenterHeight),
                  cardViewHolder
                      .getContext()
                      .getResources()
                      .getDimensionPixelSize(R.dimen.sensor_card_content_height_min));
    }
    ViewGroup.LayoutParams params = cardViewHolder.graphViewGroup.getLayoutParams();
    params.height = height;
    cardViewHolder.graphViewGroup.setLayoutParams(params);

    if (this.isSingleCard != isSingleCard) {
      this.isSingleCard = isSingleCard;
      updateCardMenu();
    }
  }

  public void setSingleCardPresenterHeight(int singleCardPresenterHeight) {
    this.singleCardPresenterHeight = singleCardPresenterHeight;
  }

  public void scrollToSensor(String sensorId) {
    int index = availableSensorIds.indexOf(sensorId);
    if (index != -1) {
      cardViewHolder.sensorTabLayout.setScrollPosition(index, 0, false);
    }
  }

  private void updateButtonsVisibility(boolean animate) {
    if (cardViewHolder == null) {
      return;
    }
    cardViewHolder.toggleButton.setActive(isActive, animate);
  }

  public void destroy() {
    if (!TextUtils.isEmpty(sensorId)) {
      recorderController.stopObserving(sensorId, observerId);
    }
    cardTriggerPresenter.onDestroy();
    if (cardViewHolder != null) {
      cardViewHolder.header.setOnHeaderTouchListener(null);
    }
    onViewRecycled();

    // Close the menu, because it will reference obsolete views and
    // presenters after resume.
    if (popupMenu != null) {
      popupMenu.dismiss();
    }

    // Any other destroy code can go here.

    // TODO: Find a way to clear the ChartController from ScalarSensor when the card
    // is destroyed but still keep graph data in memory. If we called
    // lineGraphPresenter.onDestroy() here it would clear the data, which is not what we
    // want in the case of a rotation. However, destroying the data may stop the blinking bug
    // at b/28666990. Need to find a way to keep the graph most of the
    // time but not have the blinking bug happen by not trying to load too much old data.
  }

  public void onPause() {
    paused = true;
    if (sensorPresenter != null) {
      sensorPresenter.onPause();
    }
    recorderController.stopObserving(sensorId, observerId);
  }

  public void onResume(long resetTime) {
    paused = false;
    updateStatusUi();
    if (sensorPresenter != null) {
      sensorPresenter.onResume(resetTime);
    }
  }

  public void stopObserving() {
    if (sensorPresenter != null) {
      sensorPresenter.onStopObserving();
    }
    sensorPresenter = null;
    currentSource = null;
    sensorAnimationBehavior = null;
    recorderController.stopObserving(sensorId, observerId);
    if (!cardStatus.hasError()) {
      // Only clear the data if the disconnect didn't come from an error.
      clearSensorStreamData();
    }
  }

  public void retryConnection(Context context) {
    setConnectingUI(getSelectedSensorId(), false, context, true);
    recorderController.reboot(sensorId);
  }

  private void clearSensorStreamData() {
    sensorDisplayName = "";
    units = "";
    sensorId = "";
    if (cardViewHolder != null) {
      cardViewHolder.meterLiveData.setText("");
      cardViewHolder.meterLiveData.resetTextSize();
    }
    lastUpdatedIconTimestamp = -1;
    lastUpdatedTextTimestamp = -1;
  }

  public void setInitialSourceTagToSelect(String sourceTag) {
    initialSourceTagToSelect = sourceTag;
  }

  // Selects the initial sensor source if possible, otherwise tries to select the
  // next sensor in the available list. Should be used to initialize the sensor
  // selection in this card.
  public void initializeSensorSelection() {
    if (!TextUtils.isEmpty(initialSourceTagToSelect)) {
      // Don't select the initial source if it isn't actually available or if it is
      // already selected.
      if (!TextUtils.equals(sensorId, initialSourceTagToSelect)
          && availableSensorIds.contains(initialSourceTagToSelect)) {
        trySelectingNewSensor(initialSourceTagToSelect, sensorId);
      } else {
        initialSourceTagToSelect = null;
      }
    } else {
      trySelectingNewSensor(availableSensorIds.get(0), sensorId);
    }
  }

  public void setRecording(long recordingStart) {
    this.recordingStart = recordingStart;
    if (sensorPresenter != null) {
      sensorPresenter.onRecordingStateChange(isRecording(), recordingStart);
    }
    updateCardMenu();
    updateRecordingUi();
  }

  public void lockUiForRecording() {
    setActive(false, false);
  }

  private boolean isRecording() {
    return recordingStart != RecordingMetadata.NOT_RECORDING;
  }

  private void updateRecordingUi() {
    // Show the stats drawer, hide toggle button when recording.
    if (cardViewHolder != null) {
      int toggleButtonSpacerWidth = 0;
      if (isRecording()) {
        cardViewHolder.graphStatsList.setVisibility(View.VISIBLE);
        // TODO: Animate this change.
        cardViewHolder.toggleButton.setVisibility(View.GONE);
        toggleButtonSpacerWidth =
            cardViewHolder
                .getContext()
                .getResources()
                .getDimensionPixelSize(R.dimen.sensor_card_header_padding);
      } else {
        cardViewHolder.graphStatsList.setVisibility(View.GONE);
        cardViewHolder.toggleButton.setVisibility(View.VISIBLE);
      }
      ViewGroup.LayoutParams params = cardViewHolder.toggleButtonSpacer.getLayoutParams();
      params.width = toggleButtonSpacerWidth;
      cardViewHolder.toggleButtonSpacer.setLayoutParams(params);
      updateButtonsVisibility(true /* animate */);
      // Close the menu, because options change when recording starts.
      if (popupMenu != null) {
        popupMenu.dismiss();
      }
    }
    // Collapse the header during recording.
    if (isRecording()) {
      setActive(false, false);
    }
  }

  @NonNull
  SensorLayoutPojo buildLayout() {
    // Get an updated min and max, and return layout.
    layout.setSensorId(getSelectedSensorId());
    if (sensorPresenter != null) {
      layout.setMinimumYAxisValue(sensorPresenter.getMinY());
      layout.setMaximumYAxisValue(sensorPresenter.getMaxY());
    }
    layout.putAllExtras(cardOptions.exportAsLayoutExtras());

    // Copy layout so that future modifications don't do bad things.
    return new SensorLayoutPojo(layout);
  }

  int getColorIndex() {
    return layout.getColorIndex();
  }

  NewOptionsStorage getCardOptions(SensorChoice sensorChoice, Context context) {
    if (sensorChoice == null) {
      return cardOptions;
    }
    // Use card options if set, otherwise sensor defaults.
    return new OverlayOptionsStorage(
        cardOptions, sensorChoice.getStorageForSensorDefaultOptions(context));
  }

  public void refreshLabels(List<Label> labels) {
    if (sensorPresenter != null) {
      sensorPresenter.onLabelsChanged(labels);
    }
  }

  void setConnectingUI(String sensorId, boolean hasError, Context context, boolean allowRetry) {
    this.allowRetry = allowRetry;
    SensorAppearance appearance = appearanceProvider.getAppearance(sensorId);
    setUiForConnectingNewSensor(
        sensorId,
        Appearances.getSensorDisplayName(appearance, context),
        appearance.getUnits(context),
        hasError);
  }

  public boolean isTriggerBarOnScreen() {
    if (cardViewHolder == null || parentFragment == null || parentFragment.getActivity() == null) {
      return false;
    }
    Resources res = cardViewHolder.getContext().getResources();
    int[] location = new int[2];
    cardViewHolder.triggerSection.getLocationInWindow(location);
    if (location[1] <= res.getDimensionPixelSize(R.dimen.accessibility_touch_target_min_size)) {
      return false;
    }
    DisplayMetrics metrics = new DisplayMetrics();
    parentFragment.getActivity().getWindowManager().getDefaultDisplay().getMetrics(metrics);
    if (metrics.heightPixels < location[1]) {
      return false;
    }
    return true;
  }

  public boolean hasError() {
    return cardStatus.hasError();
  }
}
