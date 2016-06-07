package com.google.android.apps.forscience.whistlepunk.opensource;

import com.google.android.apps.forscience.whistlepunk.WhistlePunkApplication;
import com.google.android.apps.forscience.whistlepunk.opensource.components
        .DaggerOpenSourceComponent;

/**
 * Subclass of WhistlePunkApplication which installs stub component.
 */
public class OpenScienceJournalApplication extends WhistlePunkApplication {

    @Override
    protected void onCreateInjector() {
        DaggerOpenSourceComponent.create().inject(this);
    }
}
