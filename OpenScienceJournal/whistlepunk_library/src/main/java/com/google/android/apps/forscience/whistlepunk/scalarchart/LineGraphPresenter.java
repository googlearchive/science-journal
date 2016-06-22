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

package com.google.android.apps.forscience.whistlepunk.scalarchart;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.drawable.ColorDrawable;
import android.support.annotation.NonNull;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.FrameLayout;
import android.widget.ProgressBar;

import com.google.android.apps.forscience.whistlepunk.DevOptionsFragment;
import com.google.android.apps.forscience.whistlepunk.ExternalAxisController;
import com.google.android.apps.forscience.whistlepunk.OutlinedScatterChart;
import com.google.android.apps.forscience.whistlepunk.R;
import com.google.android.apps.forscience.whistlepunk.metadata.Label;
import com.google.android.apps.forscience.whistlepunk.sensorapi.StreamStat;
import com.google.android.apps.forscience.whistlepunk.wireapi.RecordingMetadata;

import org.achartengine.GraphicalView;
import org.achartengine.chart.AbstractChart;
import org.achartengine.chart.CombinedXYChart;
import org.achartengine.chart.CubicLineChart;
import org.achartengine.chart.ScatterChart;
import org.achartengine.chart.XYChart;
import org.achartengine.model.XYMultipleSeriesDataset;
import org.achartengine.renderer.XYMultipleSeriesRenderer;
import org.achartengine.tools.PanListener;
import org.achartengine.tools.ZoomEvent;
import org.achartengine.tools.ZoomListener;

import java.util.List;
import java.util.SortedMap;

public class LineGraphPresenter {
    private static final String TAG = "LineGraphPresenter";

    private GraphData mGraphData;
    // TODO: Allow selection between multiple types of line charts. Currently achartengine has
    //       a CubicLineChart and a simple LineChart; we might want to extend these to get more
    //       types and allow the user to change them in an 'advanced settings' mode.
    private String mGraphDataType = CubicLineChart.TYPE;

    private ViewGroup mRootView;
    private GraphicalView mChartView;
    private SensorDataRenderer mRenderer;

    private ExternalAxisController.InteractionListener mInteractionListener;

    // Whether the chart is showing live data or pre-recorded data.
    private boolean mIsLive;

    private float mSmoothness;

    private final ScalarDisplayOptions mScalarDisplayOptions;
    private final ScalarDisplayOptions.ScalarDisplayOptionsListener mScalarDisplayOptionsListener;
    private PanListener mPanListener;
    private ZoomListener mZoomListener;

    private LineGraphOverlay mLineGraphOverlay;
    private long mRecordingStart = RecordingMetadata.NOT_RECORDING;
    private boolean mShowStatsOverlay = false;
    private FrameLayout mChartHolder;
    private ProgressBar mProgressBar;

    /**
     * @param currentTimeClock      current time clock, natch.
     * @param smoothYAxisAdjustment if true, then smoothly adjust the Y axis when a new datapoint
     *                              comes in outside of the current range; this only has a good
     *                              effect when one can be sure that new data is always coming,
     *                              so the graph always has time to find a happy state.  If
     *                              false, the Y axis instantly snaps to show all currently
     *                              on-screen data.
     * @param isLive                if true, then (1) algorithms can assume that new datapoints
     *                              are arriving all the time, with new datapoints always having
     *                              timestamps greater than all current timestamps.  (2) If the
     *                              most recent datapoint is currently on-screen, new datapoints
     *                              after that point should cause the graph to scroll right tos
     */
    public LineGraphPresenter(boolean smoothYAxisAdjustment, boolean isLive, int colorId,
            final ScalarDisplayOptions scalarDisplayOptions,
            final ExternalAxisController.InteractionListener interactionListener) {
        // By default, allow interaction when appropriate.
        this(smoothYAxisAdjustment, isLive, colorId, scalarDisplayOptions,
                interactionListener, true);
    }

    public LineGraphPresenter(boolean smoothYAxisAdjustment, boolean isLive, int colorId,
                              final ScalarDisplayOptions scalarDisplayOptions,
                              final ExternalAxisController.InteractionListener interactionListener,
                              boolean allowInteraction) {
        mScalarDisplayOptions = scalarDisplayOptions;
        mInteractionListener = interactionListener;
        mIsLive = isLive;

        mSmoothness = scalarDisplayOptions.getSmoothness();

        mGraphData = createGraphData(scalarDisplayOptions);
        mGraphData.setIsLive(isLive);

        mRenderer = new SensorDataRenderer(smoothYAxisAdjustment, mGraphData.getLineDataIndex(),
                mGraphData.getLabelDataIndex(), mGraphData.getLeadingEdgeIndex(),
                mGraphData.getEndpointsIndex(), isLive, colorId, allowInteraction);

        mScalarDisplayOptionsListener = new ScalarDisplayOptions.ScalarDisplayOptionsListener() {
            @Override
            public void onLineOptionsChanged(float smoothness, int window,
                                      @ScalarDisplayOptions.BlurType int blurType, float sigma) {
                updateSettings(smoothness, window, blurType, sigma, true);
            }

            @Override
            public void onAudioPreferencesChanged(String sonificationType) {
                // Global changes no longer override settings on already-created cards.
            }
        };

        mScalarDisplayOptions.weaklyRegisterListener(mScalarDisplayOptionsListener);
    }

    // This should be called after an onDestroy() removed the interaction listener, in order to
    // recreate it.
    public void onResume(int graphColor, ScalarDisplayOptions options,
            ExternalAxisController.InteractionListener interactionListener) {
        updateColor(graphColor);
        updateSettings(options.getSmoothness(), options.getWindow(), options.getBlurType(),
                options.getGaussianSigma(), false);
        mInteractionListener = interactionListener;
    }

    public void updateColor(int newColorId) {
        mRenderer.updateMainColor(newColorId);
    }

    @NonNull
    protected GraphData createGraphData(ScalarDisplayOptions scalarDisplayOptions) {
        return new GraphData(scalarDisplayOptions.getWindow(),
                scalarDisplayOptions.getBlurType(), scalarDisplayOptions.getGaussianSigma());
    }

    public void clearData(boolean resetYAxis) {
        mGraphData.clear();
        if (resetYAxis) {
            mRenderer.resetYAxis();
        }
    }

    public void refreshLabels() {
        mGraphData.refreshLabels();
    }

    public void redraw() {
        if (mRootView != null) {
            mRootView.removeAllViews();
            populateEmpty(mRootView);
        }
    }

    public void populateContentView(ViewGroup rootView) {
        mRenderer.setAllowZoomInOnY(DevOptionsFragment.isEnableZoomInOnY(rootView.getContext()));
        rootView.removeAllViews();
        populateEmpty(rootView);
    }

    public void setShowProgress(boolean showProgress) {
        if (mChartHolder == null || mLineGraphOverlay == null || mProgressBar == null) {
            return;
        }
        int progressVisibility = showProgress ? View.VISIBLE : View.GONE;
        int nonProgressVisibility = !showProgress ? View.VISIBLE : View.GONE;
        mProgressBar.setVisibility(progressVisibility);
        mChartHolder.setVisibility(nonProgressVisibility);
        mLineGraphOverlay.setVisibility(nonProgressVisibility);
    }

    public void populateEmpty(ViewGroup rootView) {
        mRootView = rootView;
        Context context = rootView.getContext();
        Resources res = rootView.getResources();
        int color = res.getColor(R.color.chart_margins_color);
        if (rootView.getBackground() instanceof  ColorDrawable) {
            color = ((ColorDrawable) rootView.getBackground()).getColor();
        }
        mRenderer.setupRendererStyle(res, color);
        LayoutInflater.from(context).inflate(R.layout.line_graph_content, rootView,
                true);
        mChartHolder = (FrameLayout) rootView.findViewById(R.id.chart_holder);
        mLineGraphOverlay = (LineGraphOverlay) rootView.findViewById(R.id.chart_overlay);
        mProgressBar = (ProgressBar) rootView.findViewById(R.id.chart_progress);
        updateLineGraphOverlay();

        mChartHolder.removeAllViews();
        mChartHolder.setFocusable(true);
        mChartHolder.setContentDescription(res.getString(
                mIsLive ? R.string.live_graph_content_description :
                        R.string.static_graph_content_description));

        final CombinedXYChart.XYCombinedChartDef[] types = {
                new CombinedXYChart.XYCombinedChartDef(mGraphDataType,
                        mGraphData.getLineDataIndex()),
                new CombinedXYChart.XYCombinedChartDef(OutlinedScatterChart.TYPE,
                        mGraphData.getLabelDataIndex()),
                new CombinedXYChart.XYCombinedChartDef(ScatterChart.TYPE,
                        mGraphData.getLeadingEdgeIndex()),
                new CombinedXYChart.XYCombinedChartDef(OutlinedScatterChart.TYPE,
                        mGraphData.getEndpointsIndex())};

        XYMultipleSeriesDataset dataset = mGraphData.getDataset();
        int size = dataset.getSeriesCount();
        // Manually create the charts so that parameters can be passed to the constructor.
        // If the CombinedXYChart is created without a list of charts, it makes
        // default charts with no additional parameters.
        XYChart[] charts = new XYChart[size];
        for (int i = 0; i < size; i++) {
            // Create a new dataset for each chart from the full dataset in mGraphData.
            XYMultipleSeriesDataset newDataset = new XYMultipleSeriesDataset();
            XYMultipleSeriesRenderer newRenderer = new XYMultipleSeriesRenderer();
            newDataset.addSeries(dataset.getSeriesAt(i));
            newRenderer.addSeriesRenderer(mRenderer.getSeriesRendererAt(i));
            if (i == mGraphData.getLineDataIndex()) {
                charts[i] = new CubicLineChart(newDataset, newRenderer, mSmoothness);
            } else if (i == mGraphData.getLabelDataIndex()) {
                charts[i] = new OutlinedScatterChart(newDataset, newRenderer,
                        mRenderer.getLabelFillColor(), mRenderer.getLabelFillSize());
            } else if (i == mGraphData.getLeadingEdgeIndex()) {
                charts[i] = new ScatterChart(newDataset, newRenderer);
            } else if (i == mGraphData.getEndpointsIndex()) {
                charts[i] = new OutlinedScatterChart(newDataset, newRenderer,
                        mRenderer.getEndpointsFillColor(), mRenderer.getEndpointsFillSize());
            }
        }
        CombinedXYChart chart = new CombinedXYChart(dataset, mRenderer, types, charts);

        mChartView = new GraphicalView(mChartHolder.getContext(), chart);
        if (mInteractionListener != null) {
            setInteractionListeners();
        }

        mChartHolder.addView(mChartView, ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT);
    }

    private void setInteractionListeners() {
        mChartView.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View view, MotionEvent motionEvent) {
                int action = motionEvent.getActionMasked();
                if (action == MotionEvent.ACTION_DOWN) {
                    mInteractionListener.onStartInteracting();
                } else if (action == MotionEvent.ACTION_UP) {
                    mInteractionListener.onStopInteracting();
                } else if (action == MotionEvent.ACTION_CANCEL) {
                    mInteractionListener.onStopInteracting();
                }
                return mChartView.onTouchEvent(motionEvent);
            };
        });

        mPanListener = new PanListener() {
            @Override
            public void panApplied() {
                mInteractionListener.onPan(mRenderer.getXAxisMin(), mRenderer.getXAxisMax());
            }
        };
        mChartView.addPanListener(mPanListener);

        mZoomListener = new ZoomListener() {
            @Override
            public void zoomApplied(ZoomEvent e) {
                mInteractionListener.onZoom(mRenderer.getXAxisMin(), mRenderer.getXAxisMax());
            }

            @Override
            public void zoomReset() {
                mInteractionListener.onZoom(mRenderer.getXAxisMin(), mRenderer.getXAxisMax());
            }
        };
        mChartView.addZoomListener(mZoomListener, false, true);
    }

    public void setLabels(List<? extends Label> labels) {
        mGraphData.setLabels(labels);
        if (mLineGraphOverlay != null) {
            mLineGraphOverlay.setLabels(labels);
            mLineGraphOverlay.invalidate();
        }
        if (mChartView != null) {
            mChartView.invalidate();
        }
    }

    public void setXAxis(long min, long max) {
        mRenderer.adjustXAxis(min, max);
        if (mLineGraphOverlay != null) {
            mLineGraphOverlay.updateAxis(min, max, mRenderer.getYAxisMin(),
                    mRenderer.getYAxisMax());
        }
        if (mChartView != null) {
            mChartView.invalidate();
        }
    }

    public void updateIsPinned(boolean isPinnedToNow) {
        if (isPinnedToNow && mRenderer.isYAxisInitialized()) {
            mRenderer.adjustYAxisStep();
        }
    }

    public void setRecordingUi(long recordingStart) {
        mRecordingStart = recordingStart;
        updateLineGraphOverlay();
    }

    private void updateLineGraphOverlay() {
        if (mLineGraphOverlay == null) {
            return;
        }
        mLineGraphOverlay.setRecordingStart(mRecordingStart);
        mLineGraphOverlay.setLabels(mGraphData.getLabels());
        mLineGraphOverlay.setShowStats(mShowStatsOverlay);
    }

    public void setRecordingState(long recordingStart) {
        mGraphData.setRecording(recordingStart);
        setRecordingUi(recordingStart);
    }

    // TODO: find a way to test all of this
    public void addToGraph(long timestampMillis, double value) {
        mGraphData.addDataPoint(timestampMillis, value);

        if (!mIsLive) {
            return;
        }

        SortedMap<Double, Double> range = mGraphData.getDataRange(mRenderer.getXAxisMin(),
                mRenderer.getXAxisMax());

        if (!range.isEmpty()) {
            // Update stats for the shown range.
            // These are used in the UI when recording and for the renderer min/max.
            double yMin = Double.MAX_VALUE;
            double yMax = -Double.MAX_VALUE;

            // TODO: this is not an efficient way to calculate the min, max and average.
            // A better way is tracking the previous values and change them if necessary.
            for (final double shownValue : range.values()) {
                yMin = Math.min(yMin, shownValue);
                yMax = Math.max(yMax, shownValue);
            }
            mRenderer.updatePinnedYAxisLimits(yMin, yMax);
        }
    }

    public void zoomToFit(long firstTimestamp, long lastTimestamp) {
        zoomToFitX(firstTimestamp, lastTimestamp);

        SortedMap<Double, Double> range = mGraphData.getDataRange(mRenderer.getXAxisMin(),
                mRenderer.getXAxisMax());
        if (!range.isEmpty()) {
            // Calculate stats for the shown range.
            double yMin = Double.MAX_VALUE;
            double yMax = -Double.MAX_VALUE;
            for (final double shownValue : range.values()) {
                yMin = Math.min(yMin, shownValue);
                yMax = Math.max(yMax, shownValue);
            }
            // TODO: Grab this from stats, not re-calculate ourselves.
            mRenderer.updatePinnedYAxisLimits(yMin, yMax);
        }
        mRenderer.adjustYAxisImmediately();
    }

    public void zoomToFitX(long firstTimestamp, long lastTimestamp) {
        updateEndpoints();

        // Buffer the endpoints by a fraction of the total X axis length so that the
        // points are not truncated by the edges of the chart.
        long bufferSize = getBufferSize(firstTimestamp, lastTimestamp);
        long xMin = firstTimestamp - bufferSize;
        long xMax = lastTimestamp + bufferSize;
        setXAxis(xMin, xMax);
        if (mInteractionListener != null) {
            mInteractionListener.onZoom(xMin, xMax);
        }
    }

    public void updateEndpoints() {
        mGraphData.updateEndpoints();
    }

    public static long getBufferSize(long firstTimestamp, long lastTimestamp) {
        return (long) (ExternalAxisController.EDGE_POINTS_BUFFER_FRACTION *
                (lastTimestamp - firstTimestamp));
    }

    public long getRenderedXMax() {
        return (long) mRenderer.getXAxisMax();
    }

    public long getRenderedXMin() {
        return (long) mRenderer.getXAxisMin();
    }

    public double getRenderedYMin() {
        return mRenderer.getYAxisMin();
    }

    public double getRenderedYMax() {
        return mRenderer.getYAxisMax();
    }

    public long getXMax() {
        return (long) mGraphData.getMaxX();
    }

    public long getXMin() {
        return (long) mGraphData.getMinX();
    }

    public GraphData.ReadonlyDataPoint getClosestDataPointToTimestamp(long timestamp) {
        return mGraphData.getClosestDataPointToTimestamp(timestamp);
    }

    public int getIndexForTimestamp(long timestamp) {
        return mGraphData.getIndexForTimestamp(timestamp);
    }

    public ViewTreeObserver getChartViewTreeObserver() {
        return mChartView != null ? mChartView.getViewTreeObserver() : null;
    }

    public boolean hasDrawnChart() {
        return mChartView != null && mChartView.isChartDrawn();
    }

    public double[] getScreenPoint(double[] point) {
        AbstractChart chart = mChartView.getChart();
        if (chart == null) {
            return null;
        }
        return ((CombinedXYChart) chart).toScreenPoint(point, 0);
    }

    public void updateSettings(float smoothness, int window, int blurType, float gaussianSigma,
            boolean doRedraw) {
        mGraphData.setWindowAndBlur(window, blurType, gaussianSigma);
        mSmoothness = smoothness;
        if (doRedraw) {
            redraw();
        }
    }

    public void setShowStatsOverlay(boolean showStatsOverlay) {
        mShowStatsOverlay = showStatsOverlay;
        if (mLineGraphOverlay != null) {
            mLineGraphOverlay.setShowStats(mShowStatsOverlay);
        }
    }

    public void updateStats(List<StreamStat> stats) {
        mLineGraphOverlay.updateStats(stats);
    }

    public void onDestroy() {
        if (mChartView != null) {
            // Call to stop interacting in case an interaction is happening. Clean up all listeners.
            mChartView.removePanListener(mPanListener);
            mChartView.removeZoomListener(mZoomListener);
            mChartView.setOnTouchListener(null);
        }
        mChartHolder = null;
        if (mInteractionListener != null) {
            mInteractionListener.onStopInteracting();
        }
        clearData(true);
    }

    public List<GraphData.ReadonlyDataPoint> getRawData() {
        return mGraphData.getRawData();
    }

    public void throwAwayBefore(long throwawayThreshhold) {
        mGraphData.throwAwayBefore(throwawayThreshhold);
    }

    public void throwAwayBetween(long throwawayAfter, long throwawayBefore) {
        mGraphData.throwAwayBetween(throwawayAfter, throwawayBefore);
    }


    public void setYAxisRange(double minimumYAxisValue, double maximumYAxisValue) {
        mRenderer.adjustYAxis(minimumYAxisValue, maximumYAxisValue);
        if (mChartView != null) {
            mChartView.invalidate();
        }
    }

    public void resetView() {
        mGraphData.clear();
        mRenderer.resetYAxis();
    }

}