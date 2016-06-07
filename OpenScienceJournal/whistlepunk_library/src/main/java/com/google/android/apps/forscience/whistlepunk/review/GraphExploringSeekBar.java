package com.google.android.apps.forscience.whistlepunk.review;

import android.content.Context;
import android.content.res.Resources;
import android.support.v4.view.AccessibilityDelegateCompat;
import android.support.v4.view.ViewCompat;
import android.support.v4.view.accessibility.AccessibilityNodeInfoCompat;
import android.support.v7.widget.AppCompatSeekBar;
import android.util.AttributeSet;
import android.view.View;
import android.view.accessibility.AccessibilityEvent;

import com.google.android.apps.forscience.whistlepunk.R;

/**
 * A SeekBar which is used to explore a graph of data, and can announce the current
 * (time, value) pairs for accessibility.
 */
public class GraphExploringSeekBar extends AppCompatSeekBar {
    private String mTimeString = "";
    private String mValueString = "";
    private String mUnits = "";
    private String mFormat;

    public GraphExploringSeekBar(Context context) {
        super(context);
        init();
    }

    public GraphExploringSeekBar(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public GraphExploringSeekBar(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        // Use an AccessibilityDelegate to add custom text to Accessibility events.
        ViewCompat.setAccessibilityDelegate(this, new AccessibilityDelegateCompat() {
            @Override
            public void onPopulateAccessibilityEvent(View host, AccessibilityEvent event) {
                super.onPopulateAccessibilityEvent(host, event);
                // Removing the class name stops the A11y event from reading "Seek control. 25%"
                // at each small change. It still reads the seekBar a11y info when selected on some
                // phones, but not when changes are made. This is less noisy for the user.
                event.setClassName(GraphExploringSeekBar.class.toString());
                event.getText().clear();
                event.getText().add(generateEventText());
            }

            @Override
            public void onInitializeAccessibilityNodeInfo(View host,
                                                          AccessibilityNodeInfoCompat info) {
                super.onInitializeAccessibilityNodeInfo(host, info);
                // Removing the class name stops the A11y event from reading "Seek control. 25%"
                // at each small change. It still reads the seekBar a11y info when selected on some
                // phones, but not when changes are made. This is less noisy for the user.
                info.setClassName(GraphExploringSeekBar.class.toString());
                info.setText(generateEventText());
            }
        });

        Resources res = getContext().getResources();
        mFormat = res.getString(R.string.graph_exploring_seekbar_content_description);
    }

    public void updateValuesForAccessibility(String time, String value) {
        mTimeString = time;
        mValueString = value;
    }

    public void setUnits(String units) {
        mUnits = units;
    }

    private String generateEventText() {
        return String.format(mFormat, mTimeString, mValueString, mUnits);
    }
}
