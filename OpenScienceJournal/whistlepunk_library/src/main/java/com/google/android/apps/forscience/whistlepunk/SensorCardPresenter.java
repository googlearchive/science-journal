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

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.app.Fragment;
import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.VisibleForTesting;
import android.support.design.widget.TabLayout;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.TtsSpan;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewAnimationUtils;
import android.view.ViewGroup;
import android.widget.PopupMenu;
import android.widget.TextView;

import com.google.android.apps.forscience.whistlepunk.data.GoosciSensorLayout;
import com.google.android.apps.forscience.whistlepunk.metadata.Experiment;
import com.google.android.apps.forscience.whistlepunk.metadata.Label;
import com.google.android.apps.forscience.whistlepunk.scalarchart.ScalarDisplayOptions;
import com.google.android.apps.forscience.whistlepunk.sensorapi.DataViewOptions;
import com.google.android.apps.forscience.whistlepunk.sensorapi.NewOptionsStorage;
import com.google.android.apps.forscience.whistlepunk.sensorapi.OptionsListener;
import com.google.android.apps.forscience.whistlepunk.sensorapi.ReadableSensorOptions;
import com.google.android.apps.forscience.whistlepunk.sensorapi.ScalarSensor;
import com.google.android.apps.forscience.whistlepunk.sensorapi.SensorChoice;
import com.google.android.apps.forscience.whistlepunk.sensorapi.SensorObserver;
import com.google.android.apps.forscience.whistlepunk.sensorapi.SensorPresenter;
import com.google.android.apps.forscience.whistlepunk.sensorapi.SensorStatusListener;
import com.google.android.apps.forscience.whistlepunk.sensorapi.StreamStat;
import com.google.android.apps.forscience.whistlepunk.sensors.AccelerometerSensor;
import com.google.android.apps.forscience.whistlepunk.sensors.AmbientLightSensor;
import com.google.android.apps.forscience.whistlepunk.sensors.DecibelSensor;
import com.google.android.apps.forscience.whistlepunk.sensors.MagneticRotationSensor;
import com.google.android.apps.forscience.whistlepunk.wireapi.RecordingMetadata;
import com.google.common.base.Strings;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * Holds the data and objects necessary for a sensor view.
 */
public class SensorCardPresenter {
    private static final String TAG = "SensorCardPres";

    /**
     * Object listening for when Sensor Selector tab items are clicked.
     */
    public interface OnSensorClickListener {
        /**
         * Called when user is requesting to move to a sensor
         */
        void onSensorClicked(String sensorId);
    }
    private OnSensorClickListener mOnSensorClickListener;

    /**
     * Object listening for when the close button is clicked.
     */
    public interface OnCloseClickedListener {
        /**
         * Called when the close button is selected.
         */
        void onCloseClicked();
    }
    private OnCloseClickedListener mCloseListener;

    // The height of a sensor presenter when multiple cards are visible is 60% of maximum.
    private static final double MULTIPLE_CARD_HEIGHT_PERCENT = 0.6;

    // The sensor ID ordering.
    private static final String[] SENSOR_ID_ORDER = {AmbientLightSensor.ID, DecibelSensor.ID,
            AccelerometerSensor.Axis.X.getSensorId(), AccelerometerSensor.Axis.Y.getSensorId(),
            AccelerometerSensor.Axis.Z.getSensorId(), MagneticRotationSensor.ID};

    // Update the back data textview every .25 seconds maximum.
    private static final int MAX_TEXT_UPDATE_TIME_MS = 250;

    // Update the back data imageview every .05 seconds maximum.
    private static final int MAX_ICON_UPDATE_TIME_MS = 25;

    private static final int ANIMATION_TIME_MS = 200;

    private long mRecordingStart = RecordingMetadata.NOT_RECORDING;
    private List<String> mAvailableSensorIds;
    private String mSensorDisplayName = "";
    private String mUnits = "";
    private String mSensorId;
    private SensorAnimationBehavior mSensorAnimationBehavior;
    private SensorChoice mCurrentSource = null;
    private SensorStatusListener mSensorStatusListener = null;
    private CardViewHolder mCardViewHolder;
    private TabLayout.OnTabSelectedListener mOnTabSelectedListener;
    private DataViewOptions mDataViewOptions;
    private int mSingleCardPresenterHeight;
    private String mInitialSourceTagToSelect;
    private boolean mIsSingleCard = true;
    private View.OnClickListener mRetryClickListener;
    private boolean mPaused = false;
    private String mTabSelectedFormat;
    private final Fragment mParentFragment;
    private PopupMenu mPopupMenu;
    private boolean mAllowRetry = true;

    private OptionsListener mCommitListener = new OptionsListener() {
        @Override
        public void applyOptions(ReadableSensorOptions settings) {
            mRecorderController.applyOptions(mSensorId,
                    AbstractReadableSensorOptions.makeTransportable(settings));
        }
    };

    // The last timestamp when the back of the card data was update.
    // This works unless your phone thinks it is 1970 or earlier!
    private long mLastUpdatedIconTimestamp = -1;
    private long mLastUpdatedTextTimestamp = -1;

    private AxisNumberFormat mNumberFormat;
    private LocalSensorOptionsStorage mCardOptions = new LocalSensorOptionsStorage();

    /**
     * A SensorPresenter that can respond to further UI events and update the capture
     * display, or null if the sensor doesn't expect to respond to any events from outside
     * the content view.
     */
    private SensorPresenter mSensorPresenter = null;
    private String mObserverId;

    private SensorAppearanceProvider mAppearanceProvider;
    private SensorSettingsController mSensorSettingsController;
    private final RecorderController mRecorderController;

    private GoosciSensorLayout.SensorLayout mLayout;

    private int mSourceStatus = SensorStatusListener.STATUS_CONNECTING;
    private boolean mHasError = false;
    private boolean mIsActive = false;
    private boolean mFirstObserving = true;

    public SensorCardPresenter(DataViewOptions dataViewOptions,
            SensorSettingsController sensorSettingsController,
            RecorderController recorderController, GoosciSensorLayout.SensorLayout layout,
            Fragment fragment) {
        mDataViewOptions = dataViewOptions;
        mSensorSettingsController = sensorSettingsController;
        mRecorderController = recorderController;
        mAvailableSensorIds = new ArrayList<>();
        mNumberFormat = new AxisNumberFormat();
        mLayout = layout;
        mCardOptions.putAllExtras(layout.extras);
        mParentFragment = fragment;
    }

    public void onNewData(long timestamp, Bundle bundle) {
        boolean iconTimeHasElapsed = timestamp > mLastUpdatedIconTimestamp + MAX_ICON_UPDATE_TIME_MS;
        boolean textTimeHasElapsed = timestamp > mLastUpdatedTextTimestamp + MAX_TEXT_UPDATE_TIME_MS;
        if (!textTimeHasElapsed || !iconTimeHasElapsed) {
            return;
        }

        if (textTimeHasElapsed) {
            mLastUpdatedTextTimestamp = timestamp;
        }
        if (iconTimeHasElapsed) {
            mLastUpdatedIconTimestamp = timestamp;
        }
        if (mCardViewHolder == null) {
            return;
        }
        if (ScalarSensor.hasValue(bundle)) {
            double value = ScalarSensor.getValue(bundle);
            if (textTimeHasElapsed) {
                String valueString = mNumberFormat.format(value);
                SpannableString spannable = new SpannableString(valueString);
                // Use a TtsSpan to tell Screen Readers how to interpret the number.
                // Note that this does fine if mUnits is empty -- it just reads the value.
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    TtsSpan span = new TtsSpan.TextBuilder(valueString + " " + mUnits).build();
                    spannable.setSpan(span, 0, valueString.length(), Spanned.SPAN_INCLUSIVE_INCLUSIVE);
                }
                mCardViewHolder.meterLiveData.setText(spannable, TextView.BufferType.SPANNABLE);
            }
            if (iconTimeHasElapsed) {
                mSensorAnimationBehavior.updateImageView(mCardViewHolder.meterSensorIcon,
                        value, mSensorPresenter.getMinY(), mSensorPresenter.getMaxY());
            }
        } else {
            // TODO: Show an error state for no numerical value.
            mCardViewHolder.meterLiveData.setText("");
            mCardViewHolder.meterSensorIcon.setImageLevel(0);
        }
    }

    public void onSourceStatusUpdate(String id, int status) {
        if (!TextUtils.equals(id, mSensorId)) {
            return;
        }
        mSourceStatus = status;
        if (!mPaused) {
            updateStatusUi();
        }
    }

    public void onSourceError(boolean hasError) {
        mHasError = hasError;
        updateStatusUi();
    }

    private void updateAudio(boolean enabled, String sonificationType) {
        mSensorPresenter.updateAudioSettings(enabled, sonificationType);
        if (mCardViewHolder != null) {
            updateAudioEnabledUi(enabled);
        }
    }

    private void updateAudioEnabledUi(boolean isEnabled) {
        String display = isEnabled ? String.format(
                mCardViewHolder.getContext().getString(R.string.audio_enabled_format),
                mSensorDisplayName) : mSensorDisplayName;
        mCardViewHolder.headerText.setText(display);
    }

    private void updateStatusUi() {
        // Turn off the audio unless it is connected.
        if (mSensorPresenter != null) {
            if (!mHasError && mSourceStatus == SensorStatusListener.STATUS_CONNECTED
                    && mCurrentSource != null) {
                updateAudio(mLayout.audioEnabled, getSonificationType(
                        mParentFragment.getActivity()));
            } else {
                updateAudio(false, ScalarDisplayOptions.DEFAULT_SONIFICATION_TYPE);
            }
        }
        if (mCardViewHolder == null) {
            return;
        }
        updateCardMenu();
        if (!mHasError && mSourceStatus == SensorStatusListener.STATUS_CONNECTED) {
            mCardViewHolder.flipButton.setVisibility(View.VISIBLE);
            mCardViewHolder.statusViewGroup.setVisibility(View.GONE);
            return;
        }
        mCardViewHolder.flipButton.setVisibility(View.GONE);
        mCardViewHolder.statusViewGroup.setVisibility(View.VISIBLE);
        mCardViewHolder.statusRetryButton.setVisibility(View.GONE);
        if (mHasError) {
            // An error
            if (mRetryClickListener != null && mAllowRetry) {
                mCardViewHolder.statusRetryButton.setVisibility(View.VISIBLE);
                mCardViewHolder.statusRetryButton.setOnClickListener(mRetryClickListener);
            }
            mCardViewHolder.statusMessage.setText(
                    mCardViewHolder.getContext().getText(R.string.sensor_card_error_text));
            mCardViewHolder.statusProgressBar.setVisibility(View.GONE);
        } else if (mSourceStatus != SensorStatusListener.STATUS_CONNECTING) {
            // Unknown status.
            mCardViewHolder.statusMessage.setText(
                    mCardViewHolder.getContext().getText(R.string.sensor_card_error_text));
            mCardViewHolder.statusProgressBar.setVisibility(View.GONE);
        } else {
            // Show a progress bar inside the card while connecting.
            mCardViewHolder.statusMessage.setText(
                    mCardViewHolder.getContext().getText(R.string.sensor_card_loading_text));
            mCardViewHolder.statusProgressBar.setVisibility(View.VISIBLE);
        }
    }

    public void startObserving(SensorChoice sensorChoice, SensorPresenter sensorPresenter,
            ReadableSensorOptions readOptions, final SensorObserver observer) {
        mObserverId = mRecorderController.startObserving(sensorChoice.getId(),
                new SensorObserver() {
                    @Override
                    public void onNewData(long timestamp, Bundle value) {
                        SensorCardPresenter.this.onNewData(timestamp, value);
                        observer.onNewData(timestamp, value);
                    }
                }, getSensorStatusListener(),
                AbstractReadableSensorOptions.makeTransportable(readOptions));

        mCurrentSource = sensorChoice;
        mSensorPresenter = sensorPresenter;
        if (mSourceStatus == SensorStatusListener.STATUS_CONNECTED && mParentFragment != null) {
            updateAudio(mLayout.audioEnabled, getSonificationType(mParentFragment.getActivity()));
        }
        mSensorPresenter.setShowStatsOverlay(mLayout.showStatsOverlay);
        if (mFirstObserving) {
            // The first time we start observing on a sensor, we can load the minimum and maximum
            // y values from the layout. If the sensor is changed, we don't want to keep loading the
            // old min and max values.
            if (mLayout.minimumYAxisValue < mLayout.maximumYAxisValue){
                mSensorPresenter.setYAxisRange(mLayout.minimumYAxisValue,
                        mLayout.maximumYAxisValue);
            }
            mFirstObserving = false;
        }
        if (mCardViewHolder != null) {
            mSensorPresenter.startShowing(mCardViewHolder.chartView);
        }
        // It is possible we just resumed observing but we are currently recording, in which case
        // we need to refresh the recording UI.
        if (isRecording()) {
            mSensorPresenter.onRecordingStateChange(isRecording(), mRecordingStart);
        }
    }

    @VisibleForTesting
    public void setUiForConnectingNewSensor(String sensorId, String sensorDisplayName,
            String sensorUnits, boolean hasError) {
        mUnits = sensorUnits;
        mSensorDisplayName = sensorDisplayName;
        // Set sensorId now; if we have to load SensorChoice from database, it may not be available
        // until later.
        mSensorId = sensorId;
        mSensorAnimationBehavior =
                mAppearanceProvider.getAppearance(mSensorId).getSensorAnimationBehavior();
        mHasError = hasError;
        mSourceStatus = SensorStatusListener.STATUS_CONNECTING;
        if (mCardViewHolder != null) {
            mCardViewHolder.headerText.setText(mSensorDisplayName);
            mCardViewHolder.meterLiveDataUnits.setText(mUnits);
            setMeterIcon();
            mCardViewHolder.meterViewDescription.setText(
                    mAppearanceProvider.getAppearance(mSensorId).getShortDescription(
                            mCardViewHolder.getContext()));
            updateStatusUi();
        }
    }

    public void setViews(CardViewHolder cardViewHolder, OnCloseClickedListener closeListener) {
        mCardViewHolder = cardViewHolder;
        mCloseListener = closeListener;

        updateRecordingUi();

        mCardViewHolder.headerText.setText(mSensorDisplayName);
        mCardViewHolder.meterLiveDataUnits.setText(mUnits);
        if (!TextUtils.isEmpty(mSensorId)) {
            mCardViewHolder.meterViewDescription.setText(
                    mAppearanceProvider.getAppearance(mSensorId).getShortDescription(
                            mCardViewHolder.getContext()));
        }

        if (mSensorAnimationBehavior != null) {
            setMeterIcon();
        }

        int color = mDataViewOptions.getGraphColor();
        mCardViewHolder.header.setBackgroundColor(color);
        mCardViewHolder.sensorSelectionArea.setBackgroundColor(color);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            mCardViewHolder.statusProgressBar.setIndeterminateTintList(ColorStateList.valueOf(color));
        }

        if (mSensorPresenter != null) {
            mSensorPresenter.startShowing(mCardViewHolder.chartView);
        }

        updateContentView(false);
        mCardViewHolder.flipButton.setVisibility(View.VISIBLE);
        mCardViewHolder.flipButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mLayout.cardView = mLayout.cardView == GoosciSensorLayout.SensorLayout.GRAPH ?
                                GoosciSensorLayout.SensorLayout.METER :
                                GoosciSensorLayout.SensorLayout.GRAPH;
                updateContentView(true);
            }
        });
        mCardViewHolder.flipButton.setEnabled(true);

        mCardViewHolder.infoButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Context context = v.getContext();
                Intent intent = new Intent(context, SensorInfoActivity.class);
                intent.putExtra(SensorInfoActivity.EXTRA_SENSOR_ID, mSensorId);
                intent.putExtra(SensorInfoActivity.EXTRA_COLOR_ID,
                        mDataViewOptions.getGraphColor());
                context.startActivity(intent);
            }
        });

        mCardViewHolder.graphStatsList.setTextBold(mLayout.showStatsOverlay);
        mCardViewHolder.graphStatsList.setTextDarkerColor(mLayout.showStatsOverlay);
        mCardViewHolder.graphStatsList.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mLayout.showStatsOverlay = !mLayout.showStatsOverlay;
                mSensorPresenter.setShowStatsOverlay(mLayout.showStatsOverlay);
                mCardViewHolder.graphStatsList.setTextBold(mLayout.showStatsOverlay);
                mCardViewHolder.graphStatsList.setTextDarkerColor(mLayout.showStatsOverlay);
            }
        });
        updateStatusUi();
        updateAudioEnabledUi(mLayout.audioEnabled);

        if (mTabSelectedFormat == null) {
            mTabSelectedFormat =
                    mCardViewHolder.getContext().getString(R.string.sensor_tab_selected_format);
        }

        // The first time a SensorCardPresenter is created, we cannot use the recycled view.
        // Exact reason unknown but this workaround fixes the bug described in b/24611618.
        // TODO: See if this bug can be resolved in a way that does not require view inflating.
        mCardViewHolder.sensorTabHolder.removeAllViews();
        LayoutInflater.from(mCardViewHolder.getContext()).inflate(
                R.layout.sensor_selector_tab_layout, mCardViewHolder.sensorTabHolder, true);
        mCardViewHolder.sensorTabLayout =
                (ScrollListenerTabLayout) mCardViewHolder.sensorTabHolder.getChildAt(0);
        mCardViewHolder.sensorTabLayout.setScrollListener(
                new ScrollListenerTabLayout.TabLayoutScrollListener() {
                    @Override
                    public void onScrollChanged(int l, int t, int oldl, int oldt) {
                        resetTabTouchDelegates();
                    }
        });

        mCardViewHolder.sensorTabLayout.setOnTabSelectedListener(mOnTabSelectedListener);
        if (!TextUtils.isEmpty(mSensorId)) {
            initializeSensorTabs(mSensorId);
        }
        refreshTabLayout();

        // Force setActive whenever the views are reset, as previously used views might be already
        // View.GONE.
        // Note: this must be done after setting up the tablayout.
        setActive(mIsActive, /* force */ true);
    }

    private void updateContentView(boolean animate) {
        boolean graphIsVisible = mLayout.cardView == GoosciSensorLayout.SensorLayout.GRAPH;
        if (!animate) {
            mCardViewHolder.graphViewGroup.setVisibility(
                    graphIsVisible ? View.VISIBLE : View.INVISIBLE);
            mCardViewHolder.meterViewGroup.setVisibility(
                    graphIsVisible ? View.INVISIBLE : View.VISIBLE);
        } else {
            updateContentViewAnimated(graphIsVisible);
        }
        Resources res = mCardViewHolder.getContext().getResources();
        mCardViewHolder.flipButton.setContentDescription(res.getString(graphIsVisible ?
                R.string.btn_sensor_flip_info_content_description :
                R.string.btn_sensor_flip_graph_content_description));
        Drawable drawable = res.getDrawable(graphIsVisible ?
                R.drawable.sensorcard_toggle_info_24 : R.drawable.sensorcard_toggle_graph_24);
        mCardViewHolder.flipButton.setImageDrawable(drawable);
    }

    private void updateContentViewAnimated(boolean graphIsVisible) {
        final View viewToShow;
        final View viewToHide;
        int centerX = (int) (mCardViewHolder.meterViewGroup.getWidth() * .8);
        int centerY = 0;
        int startRadius;
        int endRadius;
        if (graphIsVisible) {
            viewToShow = mCardViewHolder.graphViewGroup;
            viewToHide = mCardViewHolder.meterViewGroup;
            startRadius = viewToHide.getWidth();
            endRadius = 0;
        } else {
            viewToShow = mCardViewHolder.meterViewGroup;
            viewToHide = mCardViewHolder.graphViewGroup;
            startRadius = 0;
            endRadius = viewToHide.getWidth();
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            mCardViewHolder.meterViewGroup.bringToFront();
            final Animator anim = ViewAnimationUtils.createCircularReveal(
                    mCardViewHolder.meterViewGroup, centerX, centerY, startRadius, endRadius);
            anim.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationStart(Animator animation) {
                    viewToShow.setVisibility(View.VISIBLE);
                    mCardViewHolder.flipButton.setEnabled(false);
                }

                @Override
                public void onAnimationEnd(Animator animation) {
                    viewToHide.setVisibility(View.INVISIBLE);
                    anim.removeAllListeners();
                    // The card can get recycled mid-animation this NPEs
                    if (mCardViewHolder != null) {
                        mCardViewHolder.flipButton.setEnabled(true);
                    }
                }
            });
            anim.start();
        } else {
            viewToShow.animate()
                    .alpha(1.0f)
                    .setListener(new AnimatorListenerAdapter() {
                        @Override
                        public void onAnimationStart(Animator animation) {
                            viewToShow.setAlpha(0.0f);
                            viewToShow.setVisibility(View.VISIBLE);
                            mCardViewHolder.flipButton.setEnabled(false);
                        }

                        @Override
                        public void onAnimationEnd(Animator animation) {
                            viewToHide.setVisibility(View.INVISIBLE);
                            mCardViewHolder.flipButton.setEnabled(true);
                        }
                    })
                    .start();
            viewToHide.animate()
                    .alpha(0.0f)
                    .setListener(new AnimatorListenerAdapter() {
                        @Override
                        public void onAnimationStart(Animator animation) {
                            viewToHide.setVisibility(View.VISIBLE);
                            viewToHide.bringToFront();
                        }

                        @Override
                        public void onAnimationEnd(Animator animation) {
                            viewToHide.setVisibility(View.INVISIBLE);
                            viewToHide.setAlpha(1.0f);
                        }
                    })
                    .start();
        }
    }

    public void onViewRecycled() {
        mCardViewHolder.sensorTabLayout.setOnTabSelectedListener(null);
        mCardViewHolder.sensorTabLayout.setScrollListener(null);
        mCardViewHolder.flipButton.setOnClickListener(null);
        mCardViewHolder.menuButton.setOnClickListener(null);
        mCardViewHolder.infoButton.setOnClickListener(null);
        mCardViewHolder.graphStatsList.setOnClickListener(null);
        if (mSensorPresenter != null) {
            mSensorPresenter.onViewRecycled();
        }
        mCloseListener = null;
        mCardViewHolder = null;
    }

    private void setMeterIcon() {
        mCardViewHolder.meterSensorIcon.setImageDrawable(
                mSensorAnimationBehavior.getLevelDrawable(mCardViewHolder.getContext()));
    }

    private void updateCardMenu() {
        if (mCardViewHolder == null || mCardViewHolder.menuButton == null ||
                mSensorPresenter == null) {
            return;
        }
        mCardViewHolder.menuButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openCardMenu();
            }
        });
    }

    private void openCardMenu() {
        final Context context = mCardViewHolder.getContext();
        mPopupMenu = new PopupMenu(context, mCardViewHolder.menuButton);
        mPopupMenu.getMenuInflater().inflate(R.menu.menu_sensor_card, mPopupMenu.getMenu());
        final Menu menu = mPopupMenu.getMenu();
        menu.findItem(R.id.btn_sensor_card_close).setVisible(
                !mIsSingleCard && !isRecording());

        // Adjusting sensor options through the UI is only a developer option.
        menu.findItem(R.id.btn_sensor_card_settings).setVisible(
                DevOptionsFragment.isDevToolsEnabled(context) && !isRecording());

        // Don't show audio options if there is an error or bad status.
        boolean sensorConnected = !mHasError &&
                mSourceStatus == SensorStatusListener.STATUS_CONNECTED;
        menu.findItem(R.id.btn_sensor_card_audio_toggle).setEnabled(sensorConnected);
        menu.findItem(R.id.btn_sensor_card_audio_settings).setEnabled(sensorConnected);

        menu.findItem(R.id.btn_sensor_card_audio_toggle).setTitle(
                context.getResources().getString(mLayout.audioEnabled ?
                        R.string.graph_options_audio_feedback_disable :
                        R.string.graph_options_audio_feedback_enable));

        menu.findItem(R.id.btn_sensor_card_audio_settings).setVisible(!isRecording());

        mPopupMenu.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
            public boolean onMenuItemClick(MenuItem item) {
                int itemId = item.getItemId();
                if (itemId == R.id.btn_sensor_card_close) {
                    if (mCloseListener != null) {
                        mCloseListener.onCloseClicked();
                    }
                    return true;
                } else if (itemId == R.id.btn_sensor_card_settings) {
                    mSensorSettingsController.launchOptionsDialog(mCurrentSource,
                            mSensorPresenter,
                            getCardOptions(mCurrentSource, context),
                            mCommitListener, new NewOptionsStorage.SnackbarFailureListener(
                                    mCardViewHolder.menuButton));
                    return true;
                } else if (itemId == R.id.btn_sensor_card_audio_toggle) {
                    mLayout.audioEnabled = !mLayout.audioEnabled;
                    updateAudio(mLayout.audioEnabled, getSonificationType(context));
                    return true;
                } else if (itemId == R.id.btn_sensor_card_audio_settings) {
                    String currentSonificationType = getCardOptions(mCurrentSource, context).load(
                            LoggingConsumer.expectSuccess(TAG, "loading card options")
                    ).getReadOnly().getString(
                            ScalarDisplayOptions.PREFS_KEY_SONIFICATION_TYPE,
                            ScalarDisplayOptions.DEFAULT_SONIFICATION_TYPE);
                    AudioSettingsDialog dialog =
                            AudioSettingsDialog.newInstance(new String[] {currentSonificationType},
                                    new String[] {mSensorId}, 0);
                    dialog.show(mParentFragment.getChildFragmentManager(), AudioSettingsDialog.TAG);
                    return true;
                }
                return false;
            }
        });
        mPopupMenu.show();
    }

    private String getSonificationType(Context context) {
        return getCardOptions(mCurrentSource, context).load(
                LoggingConsumer.expectSuccess(TAG, "loading card options")
        ).getReadOnly().getString(ScalarDisplayOptions.PREFS_KEY_SONIFICATION_TYPE,
                ScalarDisplayOptions.DEFAULT_SONIFICATION_TYPE);
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
        updateAudio(mLayout.audioEnabled, sonificationType);
        getCardOptions(mCurrentSource, mParentFragment.getActivity()).load(
                        LoggingConsumer.expectSuccess(TAG, "loading card options")).put(
                ScalarDisplayOptions.PREFS_KEY_SONIFICATION_TYPE,
                sonificationType);
    }

    private void initializeSensorTabs(final String sensorIdToSelect) {
        mCardViewHolder.sensorTabLayout.removeAllTabs();
        Context context = mCardViewHolder.getContext();
        int size = mAvailableSensorIds.size();
        for (int i = 0; i < size; i++) {
            String sensorId = mAvailableSensorIds.get(i);
            addSensorTab(sensorId, i, context);
        }
        // By selecting the tab in a runnable, we also cause the SensorTabLayout to scroll
        // to the correct position.
        mCardViewHolder.sensorTabLayout.post(new Runnable() {
            @Override
            public void run() {
                if (mCardViewHolder != null) {
                    mCardViewHolder.sensorTabLayout.getTabAt(
                            mAvailableSensorIds.indexOf(sensorIdToSelect)).select();
                }
            }
        });
    }

    private void addSensorTab(String sensorId, int index, Context context) {
        final SensorAppearance appearance = mAppearanceProvider.getAppearance(sensorId);
        TabLayout.Tab tab = mCardViewHolder.sensorTabLayout.newTab();
        tab.setContentDescription(appearance.getName(context));
        tab.setIcon(context.getResources().getDrawable(appearance.getDrawableId()));
        tab.setTag(sensorId);
        mCardViewHolder.sensorTabLayout.addTab(tab, index, false);
        // HACK: we need to retrieve the view using View#findViewByTag to avoid adding lots of
        // callbacks and plumbing, just for a one time use case (feature discovery).
        // This is dependent on the current implementation of TabLayout, but since it comes from the
        // support library, not worried about it changing on different devices.
        if (mCardViewHolder.sensorTabLayout.getChildCount() > 0) {
            ((ViewGroup) mCardViewHolder.sensorTabLayout.getChildAt(0)).getChildAt(index).setTag(
                    sensorId);
        }
    }

    public void setOnSensorSelectedListener(final OnSensorClickListener listener) {
        mOnSensorClickListener = listener;
        mOnTabSelectedListener = new TabLayout.OnTabSelectedListener() {

            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                if (mCardViewHolder != null) {
                    String newSensorId = (String) tab.getTag();
                    trySelectingNewSensor(newSensorId, mSensorId);
                    tab.setContentDescription(String.format(mTabSelectedFormat,
                            getSensorName(newSensorId)));
                }
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {
                if (mCardViewHolder != null) {
                    tab.setContentDescription(getSensorName((String) tab.getTag()));
                }
            }

            @Override
            public void onTabReselected(TabLayout.Tab tab) {
                if (mCardViewHolder != null) {
                    String newSensorId = (String) tab.getTag();
                    if (TextUtils.equals(mSensorId, newSensorId) && mSensorPresenter != null) {
                        mSensorPresenter.resetView();
                    } else {
                        trySelectingNewSensor(mSensorId, newSensorId);
                    }
                }
            }
        };
        if (mCardViewHolder != null && mIsActive) {
            mCardViewHolder.sensorTabLayout.setOnTabSelectedListener(mOnTabSelectedListener);
        }
    }

    private String getSensorName(String sensorId) {
        return mAppearanceProvider.getAppearance(sensorId).getName(mCardViewHolder.getContext());
    }

    // Selects the new sensor if it is different from the old sensor or if no sensor is currently
    // selected.
    private void trySelectingNewSensor(String newSensorId, String oldSensorId) {
        if ((mCurrentSource == null && !mHasError) || !TextUtils.equals(newSensorId, oldSensorId)) {
            mOnSensorClickListener.onSensorClicked(newSensorId);
        }
    }

    public void setAppearanceProvider(SensorAppearanceProvider appearanceProvider) {
        mAppearanceProvider = appearanceProvider;
    }

    public void updateAvailableSensors(Set<String> sensorIds) {
        // We should never be updating the selected sensor.
        List<String> newAvailableSensorIds = new ArrayList(sensorIds);
        if (!TextUtils.isEmpty(mSensorId) && !sensorIds.contains(mSensorId)) {
            newAvailableSensorIds.add(mSensorId);
        }
        List<String> sorted = customSortSensorIds(newAvailableSensorIds);
        if (!sorted.equals(mAvailableSensorIds)) {
            mAvailableSensorIds = sorted;
            if (mCardViewHolder != null && !TextUtils.isEmpty(mSensorId)) {
                initializeSensorTabs(mSensorId);
            }
        }
        refreshTabLayout();
    }

    // The following is a workaround to a bug described in
    // https://code.google.com/p/android/issues/detail?id=180462.
    private void refreshTabLayout() {
        if (mCardViewHolder == null) {
            return;
        }
        mCardViewHolder.sensorTabLayout.post(new Runnable() {
            public void run() {
                if (mCardViewHolder != null) {
                    mCardViewHolder.sensorTabLayout.requestLayout();
                    resetTabTouchDelegates();
                }
            }
        });
    }

    private List<String> customSortSensorIds(List<String> sensorIds) {
        List<String> result = new ArrayList(Arrays.asList(SENSOR_ID_ORDER));
        // Keep only the elements in result that are in the available SensorIds list.
        for (String id : SENSOR_ID_ORDER) {
            if (!sensorIds.contains(id)) {
                result.remove(id);
            } else {
                sensorIds.remove(id);
            }
        }
        // Add any other sensors from SensorIds alphabetically.
        Collections.sort(sensorIds);
        for (String id : sensorIds) {
            result.add(id);
        }
        return result;
    }

    public String getSelectedSensorId() {
        if (!TextUtils.isEmpty(mSensorId)) {
            return mSensorId;
        } else if (mCardViewHolder != null) {
            // If we are switching sensors, the TabLayout tab may already be selected even
            // though the currentSource is null (after stopObserving but before starting again).
            int position = mCardViewHolder.sensorTabLayout.getSelectedTabPosition();
            if (position >= 0) {
                return (String) mCardViewHolder.sensorTabLayout.getTabAt(position).getTag();
            }
        }
        return "";
    }

    public DataViewOptions getDataViewOptions() {
        return mDataViewOptions;
    }

    public SensorStatusListener getSensorStatusListener() {
        return mSensorStatusListener;
    }

    public void setSensorStatusListener(SensorStatusListener sensorStatusListener) {
        mSensorStatusListener = sensorStatusListener;
    }

    public void setOnRetryClickListener(View.OnClickListener retryClickListener) {
        mRetryClickListener = retryClickListener;
    }

    public SensorPresenter getSensorPresenter() {
        return mSensorPresenter;
    }

    // When the stats drawer is recycled, this can return the old drawer for a different
    // sensor, so check whether the view is recycled (unavailable) before updating.
    public void updateStats(List<StreamStat> stats) {
        if (mCardViewHolder != null) {
            mCardViewHolder.graphStatsList.updateStats(stats);
            mCardViewHolder.meterStatsList.updateStats(stats);
            mSensorPresenter.updateStats(stats);
        }
    }

    /**
     * Updates the UI of the SensorCard to be "active" (show all buttons) or "inactive" (only show
     * header). If recording is in progress, always deactivates.
     *
     * @param isActive Whether this SensorCardPresenter should be active
     * @param force    If true, forces UI updates even if isActive is not changed from the previous
     *                 state. This is useful when a card is created for the first time or when views
     *                 are recycled from other SensorCards and we want to make sure that they
     *                 have the
     *                 correct visibility.
     */
    public void setActive(boolean isActive, boolean force) {
        if (isRecording()) {
            isActive = false;
        }
        // Add animation only if "force" is false -- in other words, if this was user initiated!
        if (mIsActive != isActive && !force) {
            mIsActive = isActive;
            if (mCardViewHolder != null) {
                if (isActive) {
                    mCardViewHolder.sensorTabLayout.setOnTabSelectedListener(mOnTabSelectedListener);
                    mCardViewHolder.sensorSelectionArea.animate().translationY(0).setListener(
                            new Animator.AnimatorListener() {
                                @Override
                                public void onAnimationStart(Animator animation) {
                                    mCardViewHolder.sensorSelectionArea.setVisibility(View.VISIBLE);
                                    mCardViewHolder.sensorSelectionArea.setTranslationY(-1 *
                                            mCardViewHolder.getContext().getResources()
                                                    .getDimensionPixelSize(
                                                            R.dimen.sensor_tablayout_height));
                                }

                                @Override
                                public void onAnimationEnd(Animator animation) {
                                    mCardViewHolder.sensorSelectionArea.animate().setListener(null);
                                    resetTabTouchDelegates();
                                }

                                @Override
                                public void onAnimationCancel(Animator animation) {
                                }

                                @Override
                                public void onAnimationRepeat(Animator animation) {
                                }
                            }).setDuration(ANIMATION_TIME_MS).start();
                } else {
                    mCardViewHolder.sensorTabLayout.setOnTabSelectedListener(null);
                    mCardViewHolder.sensorSelectionArea.animate().translationY(-1 *
                            mCardViewHolder.getContext().getResources().getDimensionPixelSize(
                                    R.dimen.sensor_tablayout_height)).setListener(
                            new Animator.AnimatorListener() {
                                @Override
                                public void onAnimationStart(Animator animation) {
                                    mCardViewHolder.sensorSelectionArea.setTranslationY(0);
                                }

                                @Override
                                public void onAnimationEnd(Animator animation) {
                                    mCardViewHolder.sensorSelectionArea.setVisibility(View.GONE);
                                    mCardViewHolder.sensorSelectionArea.animate().setListener(null);
                                }

                                @Override
                                public void onAnimationCancel(Animator animation) {
                                }

                                @Override
                                public void onAnimationRepeat(Animator animation) {
                                }
                            }).setDuration(ANIMATION_TIME_MS).start();
                }
            }
        } else if (force) {
            mIsActive = isActive;
            if (mCardViewHolder != null) {
                if (isActive) {
                    mCardViewHolder.sensorSelectionArea.setVisibility(View.VISIBLE);
                    mCardViewHolder.sensorSelectionArea.setTranslationY(0);
                    resetTabTouchDelegates();
                } else {
                    mCardViewHolder.sensorSelectionArea.setVisibility(View.GONE);
                    mCardViewHolder.sensorSelectionArea.setTranslationY(
                            -1 * mCardViewHolder.getContext().getResources().getDimensionPixelSize(
                                    R.dimen.sensor_tablayout_height));
                }
                mCardViewHolder.sensorSelectionArea.invalidate();
            }
        }
        updateButtonsVisibility();
        refreshTabLayout();
    }

    private void resetTabTouchDelegates() {
        // TODO This doesn't need to be called every single time we make the card active, instead
        // we could call it the first time we activate the card with a given set of views.
        int size = mAvailableSensorIds.size();
        View[] children = new View[size];
        for (int i = 0; i < size; i++) {
            // The tab itself is two views into the tab layout.
            View child = ((ViewGroup) mCardViewHolder.sensorTabLayout.getChildAt(0)).getChildAt(i);
            children[i] = child;
        }
        AccessibilityUtils.setTouchDelegateForSensorTabs(children, mCardViewHolder.itemView);
    }

    public boolean isActive() {
        return mIsActive && !isRecording();
    }

    public boolean isAudioEnabled() {
        return mLayout.audioEnabled;
    }

    public boolean isShowStatsOverlay() {
        return mLayout.showStatsOverlay;
    }

    public void setIsSingleCard(boolean isSingleCard) {
        if (mCardViewHolder == null) {
            return;
        }
        int height = isSingleCard ? mSingleCardPresenterHeight :
                Math.max((int) (MULTIPLE_CARD_HEIGHT_PERCENT * mSingleCardPresenterHeight),
                        mCardViewHolder.getContext().getResources().getDimensionPixelSize(
                                R.dimen.sensor_card_content_height_min));
        ViewGroup.LayoutParams params = mCardViewHolder.graphViewGroup.getLayoutParams();
        params.height = height;
        mCardViewHolder.graphViewGroup.setLayoutParams(params);

        params = mCardViewHolder.meterViewGroup.getLayoutParams();
        params.height = height;
        mCardViewHolder.meterViewGroup.setLayoutParams(params);

        params = mCardViewHolder.statusViewGroup.getLayoutParams();
        params.height = height;
        mCardViewHolder.meterViewGroup.setLayoutParams(params);

        if (mIsSingleCard != isSingleCard) {
            mIsSingleCard = isSingleCard;
            updateCardMenu();
        }
    }

    public void setSingleCardPresenterHeight(int singleCardPresenterHeight) {
        mSingleCardPresenterHeight = singleCardPresenterHeight;
    }

    private void updateButtonsVisibility() {
        if (mCardViewHolder == null) {
            return;
        }
        Resources resources = mCardViewHolder.toggleButton.getResources();
        if (mIsActive) {
            mCardViewHolder.toggleButton.setContentDescription(resources.getString(
                    R.string.btn_sensor_card_contract));
            mCardViewHolder.toggleButton.setImageDrawable(resources.getDrawable(
                    R.drawable.ic_expand_less_white_24dp));
        } else {
            mCardViewHolder.toggleButton.setContentDescription(resources.getString(
                    R.string.btn_sensor_card_expand));
            mCardViewHolder.toggleButton.setImageDrawable(resources.getDrawable(
                    R.drawable.ic_expand_more_white_24dp));
        }
    }

    public void destroy() {
        mRecorderController.stopObserving(mSensorId, mObserverId);
        if (mCardViewHolder != null) {
            mCardViewHolder.header.setOnHeaderTouchListener(null);
        }
        // Close the menu, because it will reference obsolete views and
        // presenters after resume.
        if (mPopupMenu != null ) {
            mPopupMenu.dismiss();
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
        mPaused = true;
        if (mSensorPresenter != null) {
            mSensorPresenter.onPause();
        }
        mRecorderController.stopObserving(mSensorId, mObserverId);
    }

    public void onResume(long resetTime) {
        mPaused = false;
        updateStatusUi();
        if (mSensorPresenter != null) {
            mSensorPresenter.onResume(resetTime);
        }
    }

    public void stopObserving() {
        if (mSensorPresenter != null) {
            mSensorPresenter.onStopObserving();
        }
        mSensorPresenter = null;
        mSensorAnimationBehavior = null;
        mRecorderController.stopObserving(mSensorId, mObserverId);
        clearSensorStreamData();
    }

    private void clearSensorStreamData() {
        mSensorDisplayName = "";
        mUnits = "";
        mCurrentSource = null;
        mSensorId = "";
        if (mCardViewHolder != null) {
            mCardViewHolder.meterLiveData.setText("");
        }
        mLastUpdatedIconTimestamp = -1;
        mLastUpdatedTextTimestamp = -1;
    }

    public void setInitialSourceTagToSelect(String sourceTag) {
        mInitialSourceTagToSelect = sourceTag;
    }

    // Selects the initial sensor source if possible, otherwise tries to select the
    // next sensor in the available list. Should be used to initialize the sensor
    // selection in this card.
    public void initializeSensorSelection() {
        if (!TextUtils.isEmpty(mInitialSourceTagToSelect)) {
            // Don't select the initial source if it isn't actually available or if it is
            // already selected.
            if (!TextUtils.equals(mSensorId, mInitialSourceTagToSelect) &&
                    mAvailableSensorIds.contains(mInitialSourceTagToSelect)) {
                trySelectingNewSensor(mInitialSourceTagToSelect, mSensorId);
            } else {
                mInitialSourceTagToSelect = null;
            }
        } else {
            trySelectingNewSensor(mAvailableSensorIds.get(0), mSensorId);
        }
    }

    public void setRecording(long recordingStart) {
        mRecordingStart = recordingStart;
        if (mSensorPresenter != null) {
            mSensorPresenter.onRecordingStateChange(isRecording(), mRecordingStart);
        }
        updateCardMenu();
        updateRecordingUi();
    }

    public void lockUiForRecording() {
        setActive(false, false);
    }

    private boolean isRecording() {
        return mRecordingStart != RecordingMetadata.NOT_RECORDING;
    }

    /**
     * Whether data was recorded.
     * @return true if at least one data point was recorded.
     */
    public boolean hasRecordedData() {
        return mRecordingStart != RecordingMetadata.NOT_RECORDING &&
                mLastUpdatedIconTimestamp > mRecordingStart;
    }

    public boolean isConnected() {
        return !mHasError && mSourceStatus == SensorStatusListener.STATUS_CONNECTED;
    }

    private void updateRecordingUi() {
        // Show the stats drawer, hide toggle button when recording.
        if (mCardViewHolder != null) {
            int toggleButtonSpacerWidth = 0;
            if (isRecording()) {
                mCardViewHolder.graphStatsList.setVisibility(View.VISIBLE);
                mCardViewHolder.meterStatsList.setVisibility(View.VISIBLE);
                // TODO: Animate this change.
                mCardViewHolder.toggleButton.setVisibility(View.GONE);
                mCardViewHolder.infoSection.setVisibility(View.GONE);
                toggleButtonSpacerWidth = mCardViewHolder.getContext().getResources()
                        .getDimensionPixelSize(R.dimen.sensor_card_header_padding);
            } else {
                mCardViewHolder.graphStatsList.setVisibility(View.GONE);
                mCardViewHolder.meterStatsList.setVisibility(View.GONE);
                mCardViewHolder.toggleButton.setVisibility(View.VISIBLE);
                mCardViewHolder.infoSection.setVisibility(View.VISIBLE);
            }
            ViewGroup.LayoutParams params = mCardViewHolder.toggleButtonSpacer.getLayoutParams();
            params.width = toggleButtonSpacerWidth;
            mCardViewHolder.toggleButtonSpacer.setLayoutParams(params);
            updateButtonsVisibility();
        }
        // Collapse the header during recording.
        if (isRecording()) {
            setActive(false, false);
        }
    }

    @NonNull
    GoosciSensorLayout.SensorLayout buildLayout() {
        // Get an updated min and max, and return mLayout.
        mLayout.sensorId = getSelectedSensorId();
        mLayout.color = getDataViewOptions().getGraphColor();
        if (mSensorPresenter != null) {
            mLayout.minimumYAxisValue = mSensorPresenter.getMinY();
            mLayout.maximumYAxisValue = mSensorPresenter.getMaxY();
        }
        mLayout.extras = mCardOptions.exportAsLayoutExtras();
        return mLayout;
    }

    NewOptionsStorage getCardOptions(SensorChoice sensorChoice, Context context) {
        // Use card options if set, otherwise sensor defaults.
        return new OverlayOptionsStorage(mCardOptions,
                sensorChoice.getStorageForSensorDefaultOptions(context));
    }

    public void tryRefreshingLabels(DataController dc, final ExternalAxisController externalAxis,
            Experiment experiment) {
        if (experiment == null) {
            return;
        }
        if (mSensorPresenter == null && externalAxis == null) {
            return;
        }
        dc.getLabelsForExperiment(experiment,
                new LoggingConsumer<List<Label>>(TAG, "retrieving labels") {
                    @Override
                    public void success(List<Label> labels) {
                        if (mSensorPresenter != null) {
                            mSensorPresenter.onLabelsChanged(labels);
                        }
                        if (externalAxis != null) {
                            externalAxis.onLabelsChanged(labels);
                        }

                    }
                });
    }

    void setConnectingUI(String sensorId, boolean hasError, Context context, boolean allowRetry) {
        mAllowRetry = allowRetry;
        SensorAppearance appearance = mAppearanceProvider.getAppearance(sensorId);
        setUiForConnectingNewSensor(sensorId,
                appearance.getSensorDisplayName(context), appearance.getUnits(context), hasError);
    }
}
