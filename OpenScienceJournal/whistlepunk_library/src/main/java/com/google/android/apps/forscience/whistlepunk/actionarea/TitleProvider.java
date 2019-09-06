package com.google.android.apps.forscience.whistlepunk.actionarea;

/**
 * Interface for fragments who provide a getTitle method to be used by {@link
 * com.google.android.apps.forscience.whistlepunk.NoteTakingActivity}
 */
public interface TitleProvider {
  String getTitle();
}
