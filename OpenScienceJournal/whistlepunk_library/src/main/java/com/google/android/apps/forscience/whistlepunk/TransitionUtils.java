package com.google.android.apps.forscience.whistlepunk;

import android.app.Activity;
import android.os.Build;
import android.support.v4.util.Pair;
import android.view.View;
import android.view.Window;

import java.util.ArrayList;

/**
 * Utilities for working with activity transitions.
 */
public class TransitionUtils {
    private TransitionUtils() {}

    public static Pair<View, String>[] getTransitionPairs(Activity activity, View v,
                                                          String transitionName) {
        ArrayList<Pair<View, String>> list = new ArrayList<>();
        list.add(Pair.create(v, transitionName));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            View statusBar = activity.findViewById(android.R.id.statusBarBackground);
            if (statusBar != null) {
                list.add(Pair.create(statusBar, Window.STATUS_BAR_BACKGROUND_TRANSITION_NAME));
            }
            View navigationBar = activity.findViewById(android.R.id.navigationBarBackground);
            if (navigationBar != null) {
                list.add(Pair.create(navigationBar,
                        Window.NAVIGATION_BAR_BACKGROUND_TRANSITION_NAME));
            }
        }
        return list.toArray(new Pair[list.size()]);
    }
}
