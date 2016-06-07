package com.google.android.apps.forscience.whistlepunk;

import android.annotation.TargetApi;
import android.content.Context;
import android.preference.PreferenceCategory;
import android.util.AttributeSet;
import android.view.View;

/**
 * A PreferenceCategory with a progress spinner.
 */
public class PreferenceProgressCategory extends PreferenceCategory {

    private boolean mProgress;

    public PreferenceProgressCategory(Context context) {
        this(context, null);
    }

    public PreferenceProgressCategory(Context context, AttributeSet attrs) {
        super(context, attrs, 0);
        setLayoutResource(R.layout.preference_progress_category);
    }

    @TargetApi(21)
    public PreferenceProgressCategory(Context context, AttributeSet attrs, int defStyleAttr,
                                      int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        setLayoutResource(R.layout.preference_progress_category);
    }

    @Override
    protected void onBindView(View view) {
        super.onBindView(view);
        final View progressBar = view.findViewById(R.id.scanning_progress);
        progressBar.setVisibility(mProgress ? View.VISIBLE : View.GONE);
    }

    public void setProgress(boolean progressOn) {
        mProgress = progressOn;
        notifyChanged();
    }
}
