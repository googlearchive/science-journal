package com.google.android.apps.forscience.whistlepunk;


import android.graphics.Canvas;
import android.graphics.Paint;

import org.achartengine.chart.ScatterChart;
import org.achartengine.model.XYMultipleSeriesDataset;
import org.achartengine.renderer.XYMultipleSeriesRenderer;
import org.achartengine.renderer.XYSeriesRenderer;

import java.util.List;

/**
 * A scatter chart that shows an outlined point instead of a solid point.
 */
public class OutlinedScatterChart extends ScatterChart {
    private Paint mFillPaint;
    private float mRadius;

    /**
     * Builds a new OutlinedScatterChart.
     * @param dataset
     * @param renderer The data renderer. This should already have a pointStrokeWidth set that
     *                 corresponds to the outer radius of the point.
     * @param fillColor The color of the inner dot.
     * @param innserSize The size of the inner dot. Assumes this is smaller than the
     *                   pointStrokeWidth of the renderer.
     */
    public OutlinedScatterChart(XYMultipleSeriesDataset dataset, XYMultipleSeriesRenderer renderer,
                            int fillColor, float innserSize) {
        super(dataset, renderer);
        mFillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mFillPaint.setStyle(Paint.Style.FILL);
        mFillPaint.setAntiAlias(true);
        mFillPaint.setColor(fillColor);
        mRadius = innserSize / 2.0f;
    }

    @Override
    public void drawSeries(Canvas canvas, Paint paint, List<Float> points,
                           XYSeriesRenderer renderer, float yAxisValue, int seriesIndex,
                           int startIndex) {
        // Draws the normal points, then the paint.
        super.drawSeries(canvas, paint, points, renderer, yAxisValue, seriesIndex, startIndex);
        int length = points.size();
        for (int i = 0; i < length; i += 2) {
            canvas.drawCircle(points.get(i), points.get(i + 1), mRadius, mFillPaint);
        }
    }


}
