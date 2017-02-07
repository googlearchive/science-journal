package com.google.android.apps.forscience.whistlepunk;

import android.content.Context;
import android.os.Build;

/**
 * Utility class for figuring out if the app support multiwindow.
 */
public class MultiWindowUtils {

    public static boolean isMultiWindowEnabled(Context context) {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.N ||
                (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && isARC(context));
    }

    private static boolean isARC(Context context) {
        if (context == null) {
            return false;
        }
        return context.getPackageManager().hasSystemFeature("org.chromium.arc.device_management");
    }
}
