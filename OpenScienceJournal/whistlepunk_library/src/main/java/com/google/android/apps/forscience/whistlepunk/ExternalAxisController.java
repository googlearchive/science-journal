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
import android.content.Context;
import android.os.Handler;
import android.view.View;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import com.google.android.apps.forscience.whistlepunk.filemetadata.Label;
import com.google.android.apps.forscience.whistlepunk.scalarchart.ChartOptions;
import com.google.android.apps.forscience.whistlepunk.wireapi.RecordingMetadata;
import java.util.ArrayList;
import java.util.List;

/** Controller for the external X axis. */
public class ExternalAxisController {

  // A listener for classes who need to know when the external axis is updated.
  public interface AxisUpdateListener {
    /**
     * This interface should be extended by anything wishing to stay in sync with the ExternalAxis.
     * This will be called whenever any elements need to know that the min and max of the external
     * axis have been updated.
     *
     * @param xMin
     * @param xMax
     * @param isPinnedToNow Whether the external axis is "pinned" to the current time, i.e. is
     *     scrolling forward as data is added.
     */
    void onAxisUpdated(long xMin, long xMax, boolean isPinnedToNow);
  }

  // A listener that can notify this ExternalAxisController that an interaction has occured and
  // the axis should be updated.
  public interface InteractionListener {
    /**
     * onStartInteracting should be called from an OnTouchListener when the user has begun an
     * interaction with an element that interacts with the ExternalAxis.
     */
    void onStartInteracting();

    /**
     * onStopInteracting should be called from an OnTouchListener when the user has stopped
     * interacting, i.e. on MotionEvent.ACITON_UP.
     */
    void onStopInteracting();

    /**
     * Called when the user is doing an action which means the X axis should pan (left/right).
     *
     * @param xMin The requested xMin for the pan.
     * @param xMax The requested xMax for the pan.
     */
    void onPan(double xMin, double xMax);

    /**
     * Called when the user is doing an action that translates to a zoom in/out on the X axis.
     *
     * @param xMin The new xMin if the zoom were to be applied.
     * @param xMax The new xMax if the zoom were to be applied.
     */
    void onZoom(long xMin, long xMax);

    /** Called when the user does an action that should cause the pinned state to be reset. */
    void requestResetPinnedState();

    /** Called when the user does an action that should cause the zoom to be reset. */
    void requestResetZoom();
  }

  public interface RecordingTimeUpdateListener {
    void onRecordingTimeUpdated(long now);
  }

  private List<AxisUpdateListener> axisUpdateListeners = new ArrayList<>();
  private InteractionListener interactionListener;
  private RecordingTimeUpdateListener recordingTimeUpdateListener;

  public static final int MS_IN_SEC = 1000;
  private static final int DEFAULT_GRAPH_RANGE_IN_SECONDS = 20;
  public static final int DEFAULT_GRAPH_RANGE_IN_MILLIS =
      DEFAULT_GRAPH_RANGE_IN_SECONDS * MS_IN_SEC;
  private static final int UPDATE_TIME_MS = 20;

  // The minimum zoom-in range is 2 seconds.
  private static final long MINIMUM_ZOOM_RANGE_MS = MS_IN_SEC * 2;

  // The fraction of the x axis to use as a buffer for the leading edge point or endpoints.
  // This allows these points to not be truncated by the edge of the graph.
  public static final double EDGE_POINTS_BUFFER_FRACTION = .04;

  // The amount of buffer in the x axis when the live graph is showing so that these points are
  // not truncated by the edge of the graph.
  // This draws the points of the graph slightly back from the edge of the view.
  // This is ms and related to the DEFAULT_GRAPH_RANGE_IN_SECONDS.
  public static final double LEADING_EDGE_BUFFER_TIME =
      DEFAULT_GRAPH_RANGE_IN_SECONDS * EDGE_POINTS_BUFFER_FRACTION * MS_IN_SEC;

  long xMin = Long.MAX_VALUE;
  long xMax = Long.MIN_VALUE;

  private long recordingStart = RecordingMetadata.NOT_RECORDING;

  // The original start to the run. This may differ from recordingStart if the run has been
  // cropped, in which case this is always less than or equal to recordingStart.
  private long originalStart = RecordingMetadata.NOT_RECORDING;

  private boolean isInitialized = false;
  private Clock currentTimeClock;
  private View resetButton;

  protected static final int NO_RESET = -1;
  private long resetAxisOnFirstDataPointAfter = NO_RESET;
  private ExternalAxisView axisView;
  private List<Label> labels;

  // Whether we are in the live / observe mode, or in a static mode.
  private boolean isLive;
  private Handler refreshHandler;
  private Runnable refreshRunnable;

  // Used to determine whether the run review data has been set.
  private static final long RUN_REVIEW_DATA_NOT_INITIALIZED = -1;

  // The min and max x values that can be shown in RunReview, so the user does not have
  // infinite scrolling
  private long reviewXMin = RUN_REVIEW_DATA_NOT_INITIALIZED;
  private long reviewXMax = RUN_REVIEW_DATA_NOT_INITIALIZED;

  // Whether the graph is pinned to "now", or we are looking back.
  private boolean isPinnedToNow;

  // Whether a user is currently interacting with a graph.
  private boolean userIsInteracting = false;

  private float resetButtonTranslationPx;

  /** Tracks if we are animating the reset button out or not. */
  private boolean resetAnimatingOut = false;

  public ExternalAxisController(
      ExternalAxisView axisView,
      AxisUpdateListener listener,
      boolean isLive,
      CurrentTimeClock currentTimeClock) {
    this(axisView, listener, isLive, currentTimeClock, null);
  }

  public ExternalAxisController(
      ExternalAxisView axisView,
      AxisUpdateListener listener,
      final boolean isLive,
      CurrentTimeClock currentTimeClock,
      View resetButton) {
    this.axisView = axisView;
    axisUpdateListeners.add(listener);
    this.isLive = isLive;
    // If we are creating a live mode sensor, it will probably start off pinned to now.
    isPinnedToNow = isLive;
    this.currentTimeClock = currentTimeClock;

    if (this.isLive) {
      this.axisView.setNumberFormat(
          new SecondsAgoFormat(currentTimeClock, this.axisView.getContext()));
    }
    resetButtonTranslationPx =
        axisView.getResources().getDimensionPixelSize(R.dimen.reset_btn_holder_width);

    this.resetButton = resetButton;
    if (this.resetButton != null) {
      this.resetButton.setOnClickListener(
          new View.OnClickListener() {
            @Override
            public void onClick(View view) {
              resetAxes();
              animateButtonOut();
            }
          });
      // Set this up the first time.
      setUpResetAnimation();
    }

    interactionListener =
        new InteractionListener() {
          @Override
          public void onStartInteracting() {
            userIsInteracting = true;
            // When the user is interacting, unpin to now.
            isPinnedToNow = false;
          }

          @Override
          public void onStopInteracting() {
            userIsInteracting = false;
            // When the user is done interacting, see if they've left the graph in a pinned
            // state, i.e. within 1% of now or with the X axis above now.
            long now = ExternalAxisController.this.currentTimeClock.getNow();
            isPinnedToNow =
                ExternalAxisController.this.xMax >= now - (0.01 * DEFAULT_GRAPH_RANGE_IN_MILLIS);
          }

          @Override
          public void onPan(double xMin, double xMax) {
            // When the user is interacting, unpin to now.
            isPinnedToNow = false;
            if (!ExternalAxisController.this.isLive) {
              if (xMin < reviewXMin || xMax > reviewXMax) {
                // Don't apply the change.
                updateAxis();
                return;
              }
            }
            ExternalAxisController.this.xMin = (long) xMin;
            ExternalAxisController.this.xMax = (long) xMax;
            updateAxis();
          }

          @Override
          public void onZoom(long xMin, long xMax) {
            if (!ExternalAxisController.this.isLive
                && reviewXMin != RUN_REVIEW_DATA_NOT_INITIALIZED) {
              // If we are zoomed into maximum, and trying to zoom smaller than our saved
              // xMin to xMax range...
              if (xMax - xMin < MINIMUM_ZOOM_RANGE_MS
                  && xMax - xMin
                      < ExternalAxisController.this.xMax - ExternalAxisController.this.xMin) {
                // Don't apply the change.
                updateAxis();
                return;
              }
            }
            zoomTo(xMin, xMax);
          }

          @Override
          public void requestResetPinnedState() {
            if (!isPinnedToNow) {
              ExternalAxisController.this.resetButton.callOnClick();
            }
          }

          @Override
          public void requestResetZoom() {
            // TODO: Animate?
            ExternalAxisController.this.xMin = reviewXMin;
            ExternalAxisController.this.xMax = reviewXMax;
            updateAxis();
          }
        };
  }

  public void onResumeLiveAxis() {
    if (!isLive) {
      return;
    }
    refreshHandler = new Handler();
    refreshRunnable =
        new Runnable() {
          @Override
          public void run() {
            long timestamp = currentTimeClock.getNow();
            if (!isInitialized) {
              xMax = timestamp;
              // The time range is the default time.
              xMin = timestamp - DEFAULT_GRAPH_RANGE_IN_MILLIS;
              isInitialized = true;
            }
            long now = currentTimeClock.getNow();
            scrollToNowIfPinned(now, timestamp);
            if (recordingStart != RecordingMetadata.NOT_RECORDING
                && recordingTimeUpdateListener != null) {
              recordingTimeUpdateListener.onRecordingTimeUpdated(now - recordingStart);
            }
            refreshHandler.postDelayed(refreshRunnable, UPDATE_TIME_MS);
          }
        };
    refreshRunnable.run();
  }

  public void onPauseLiveAxis() {
    if (refreshHandler != null) {
      refreshHandler.removeCallbacks(refreshRunnable);
      refreshRunnable = null;
      refreshHandler = null;
    }
  }

  public long getXMax() {
    return xMax;
  }

  public long getXMin() {
    return xMin;
  }

  public InteractionListener getInteractionListener() {
    return interactionListener;
  }

  public void setRecordingTimeUpdateListener(RecordingTimeUpdateListener listener) {
    recordingTimeUpdateListener = listener;
  }

  public void setReviewData(long originalStart, long runStart, long reviewMin, long reviewMax) {
    // The start and end of recording.
    recordingStart = runStart;
    this.originalStart = originalStart;

    axisView.setNumberFormat(new RelativeTimeFormat(recordingStart, axisView.getContext()));
    axisView.setRecordingStart(recordingStart);

    // The edges of the data which can be reviewed. Do not allow zoom or pan past these edges.
    // Slightly wider than the recording start and end, because of a zoom out to show endpoints.
    reviewXMin = reviewMin;
    reviewXMax = reviewMax;
  }

  public void destroy() {
    axisUpdateListeners.clear();
    interactionListener = null;
    recordingTimeUpdateListener = null;
  }

  public void updateAxis() {
    axisView.updateAxis(xMin, xMax);
    for (AxisUpdateListener listener : axisUpdateListeners) {
      listener.onAxisUpdated(xMin, xMax, isPinnedToNow);
    }
  }

  public void zoomTo(long xMin, long xMax) {
    if (!isLive && reviewXMin != RUN_REVIEW_DATA_NOT_INITIALIZED) {
      // Otherwise, make sure the change is within the review region
      if (xMin < reviewXMin) {
        xMin = reviewXMin;
      }
      if (xMax > reviewXMax) {
        xMax = reviewXMax;
      }
    }
    this.xMin = xMin;
    this.xMax = xMax;
    updateAxis();
  }

  public void addAxisUpdateListener(AxisUpdateListener listener) {
    axisUpdateListeners.add(listener);
  }

  // TODO: allow testing this logic without a real View
  /** @return The max timestamp after reset is completed. */
  public long resetAxes() {
    isPinnedToNow = isLive;
    if (isPinnedToNow) {
      long timestampMillis = currentTimeClock.getNow();
      resetAxisOnFirstDataPointAfter = timestampMillis;
      xMin = timestampMillis - DEFAULT_GRAPH_RANGE_IN_MILLIS;
      xMax = timestampMillis;
      return timestampMillis;
    } else {
      xMin = reviewXMin;
      xMax = reviewXMax;
      return reviewXMax;
    }
  }

  public void scrollToNowIfPinned(long nowTimestamp, long lastAddedTimestamp) {
    // Ignore user interaction if it happens between data resets.
    if (userIsInteracting && resetAxisOnFirstDataPointAfter == NO_RESET) {
      return;
    }
    nowTimestamp += LEADING_EDGE_BUFFER_TIME;

    // Only auto-scroll if we're already pinned to now (within 1%), or the user has indicated
    // that a reset is necessary.
    if (resetAxisOnFirstDataPointAfter != NO_RESET
        && lastAddedTimestamp > resetAxisOnFirstDataPointAfter) {
      isPinnedToNow = true;
      resetAxisOnFirstDataPointAfter = NO_RESET;
    }
    if (isPinnedToNow) {
      // Push the min by as much as the max
      xMax = nowTimestamp;
      xMin = nowTimestamp - DEFAULT_GRAPH_RANGE_IN_MILLIS;
    }
    if (resetButton != null) {
      boolean resetButtonMatchesPinned =
          resetButton.getVisibility() == (isPinnedToNow ? View.INVISIBLE : View.VISIBLE);
      if (!resetButtonMatchesPinned) {
        // If we have any differences between old and new values.
        if (!isPinnedToNow) {
          animateButtonIn();
        } else {
          animateButtonOut();
        }
      }
    }
    updateAxis();
  }

  public void onLabelsChanged(List<Label> labels) {
    this.labels = labels;
    List<Long> timestamps = new ArrayList<>();
    for (Label label : this.labels) {
      if (ChartOptions.isDisplayable(
          label, recordingStart, ChartOptions.ChartPlacementType.TYPE_OBSERVE)) {
        timestamps.add(label.getTimeStamp());
      }
    }
    axisView.setLabels(timestamps);
  }

  public void onStartRecording(long timestamp) {
    recordingStart = timestamp;
    axisView.setNumberFormat(new RelativeTimeFormat(recordingStart, axisView.getContext()));
    axisView.setRecordingStart(recordingStart);
    if (Flags.showActionBar()) {
      axisView.setVisibility(View.VISIBLE);
    }
  }

  public long getRecordingStartTime() {
    return recordingStart;
  }

  public void onStopRecording() {
    recordingStart = RecordingMetadata.NOT_RECORDING;
    axisView.setNumberFormat(new SecondsAgoFormat(currentTimeClock, axisView.getContext()));
    axisView.setRecordingStart(recordingStart);
    if (Flags.showActionBar()) {
      axisView.setVisibility(View.GONE);
    }
  }

  // Returns an elapsed time for RunReview data. Will return the empty string otherwise.
  public String formatElapsedTimeForAccessibility(long timestamp, Context context) {
    if (reviewXMin == RUN_REVIEW_DATA_NOT_INITIALIZED) {
      return "";
    }
    return ElapsedTimeFormatter.getInstance(context)
        .formatForAccessibility((timestamp - recordingStart) / MS_IN_SEC);
  }

  private void animateButtonIn() {
    resetButton
        .animate()
        .translationX(0)
        .setInterpolator(new DecelerateInterpolator())
        .rotation(0)
        .setListener(
            new AnimatorListenerAdapter() {
              @Override
              public void onAnimationStart(Animator animation) {
                super.onAnimationStart(animation);
                resetButton.setVisibility(View.VISIBLE);
              }
            })
        .start();
  }

  /** Sets up the button. Basically set all the end points that are done in animateButtonOut. */
  private void setUpResetAnimation() {
    resetButton.setTranslationX(resetButtonTranslationPx);
    resetButton.setRotation(180);
  }

  private void animateButtonOut() {
    if (resetAnimatingOut) {
      // Don't animate if we are already doing it.
      return;
    }
    resetButton
        .animate()
        .translationX(resetButtonTranslationPx)
        .rotation(180)
        .setInterpolator(new AccelerateInterpolator())
        .setListener(
            new AnimatorListenerAdapter() {
              @Override
              public void onAnimationStart(Animator animation) {
                resetAnimatingOut = true;
              }

              @Override
              public void onAnimationEnd(Animator animation) {
                resetButton.setVisibility(View.INVISIBLE);
                resetAnimatingOut = false;
              }
            })
        .start();
  }

  public boolean containsTimestamp(long timestamp) {
    return xMin <= timestamp && timestamp <= xMax;
  }

  public boolean isIntialized() {
    return xMax != Long.MIN_VALUE;
  }

  public static long getReviewBuffer(long firstTimestamp, long lastTimestamp) {
    return (long) (EDGE_POINTS_BUFFER_FRACTION * (lastTimestamp - firstTimestamp));
  }

  public long timestampAtAxisFraction(double fraction) {
    return (long) ((xMax - xMin) * fraction + xMin);
  }
}
