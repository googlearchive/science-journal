package com.google.android.apps.forscience.whistlepunk;

import android.content.Context;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;

/**
 * Utility class for coloring icons.
 */
public class ColorUtils {

    // TODO: Find all setColorFilter methods and update them to use this utility function.
    public static Drawable colorDrawable(Context context, Drawable drawable, int colorId) {
        Drawable result = drawable.mutate();
        result.setColorFilter(context.getResources().getColor(colorId), PorterDuff.Mode.MULTIPLY);
        return result;
    }
}
