package com.google.android.apps.forscience.whistlepunk;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.widget.ImageView;
import android.widget.TextView;

public class SensorAppearance {
    /**
     * Human readable name for this source.
     */
    private final int mNameStringId;

    /**
     * The ID of the drawable to use as an icon for this sensor source.
     */
    private final int mDrawableId;

    /**
     * The ID of the string that represents the units for this source. Use 0 if no units are
     * needed.
     */
    private final int mUnitsStringId;

    /**
     * The ID of the string that has a short description of this sensor.
     */
    private final int mShortDescriptionStringId;

    /**
     * The SensorAnimationBehavior that controls the drawable used in the sensor animation.
     */
    private final SensorAnimationBehavior mSensorAnimationBehavior;

    /**
     * The IDs to the first and second paragraphs of text in the Learn More page.
     * The first paragraph comes before the image, and the second comes after.
     */
    private int mFirstParagraphStringId;
    private int mSecondParagraphStringId;
    private int mLearnMoreDrawableId;

    public SensorAppearance(int nameStringId, int drawableId) {
        mNameStringId = nameStringId;
        mDrawableId = drawableId;
        mUnitsStringId = 0;
        mShortDescriptionStringId = 0;
        mFirstParagraphStringId = 0;
        mSecondParagraphStringId = 0;
        mLearnMoreDrawableId = 0;
        mSensorAnimationBehavior = SensorAnimationBehavior.createDefault();
    }

    public SensorAppearance(int nameStringId, int drawableId, int shortDescriptionId,
                            SensorAnimationBehavior sensorAnimationBehavior) {
        mNameStringId = nameStringId;
        mDrawableId = drawableId;
        mUnitsStringId = 0;
        mShortDescriptionStringId = shortDescriptionId;
        mFirstParagraphStringId = 0;
        mSecondParagraphStringId = 0;
        mLearnMoreDrawableId = 0;
        mSensorAnimationBehavior = sensorAnimationBehavior;
    }

    public SensorAppearance(int nameStringId, int drawableId, int unitsStringId,
                            int shortDescriptionId, int firstParagraphStringId,
                            int secondParagraphStringId, int infoDrawableId,
                            SensorAnimationBehavior sensorAnimationBehavior) {
        mNameStringId = nameStringId;
        mDrawableId = drawableId;
        mUnitsStringId = unitsStringId;
        mShortDescriptionStringId = shortDescriptionId;
        mFirstParagraphStringId = firstParagraphStringId;
        mSecondParagraphStringId = secondParagraphStringId;
        mLearnMoreDrawableId = infoDrawableId;
        mSensorAnimationBehavior = sensorAnimationBehavior;
    }

    public int getNameResource() {
        return mNameStringId;
    }

    public String getName(Context context) {
        return context.getResources().getString(mNameStringId);
    }

    public String getUnits(Context context) {
        return getString(context, mUnitsStringId);
    }

    public int getDrawableId() {
        return mDrawableId;
    }

    public String getShortDescription(Context context) {
        return getString(context, mShortDescriptionStringId);
    }

    public String getFirstLearnMoreParagraph(Context context) {
        return getString(context, mFirstParagraphStringId);
    }

    public String getSecondLearnMoreParagraph(Context context) {
        return getString(context, mSecondParagraphStringId);
    }

    public Drawable getLearnMoreDrawable(Context context) {
        if (mLearnMoreDrawableId != 0) {
            return context.getResources().getDrawable(mLearnMoreDrawableId);
        }
        return null;
    }

    public SensorAnimationBehavior getSensorAnimationBehavior() {
        return mSensorAnimationBehavior;
    }

    public void applyDrawableToImageView(ImageView view, int color) {
        Context context = view.getContext();
        Resources res = context.getResources();
        Drawable drawable = res.getDrawable(getDrawableId()).mutate();
        drawable.setColorFilter(color, PorterDuff.Mode.MULTIPLY);
        view.setImageDrawable(drawable);
    }

    private String getString(Context context, int id) {
        if (id != 0) {
            return context.getResources().getString(id);
        }
        return "";
    }

    public String getSensorDisplayName(Context context) {
        String units = getUnits(context);
        return TextUtils.isEmpty(units) ?
                getName(context) : String.format(context.getResources().getString(
                R.string.header_name_and_units), getName(context), units);
    }
}
