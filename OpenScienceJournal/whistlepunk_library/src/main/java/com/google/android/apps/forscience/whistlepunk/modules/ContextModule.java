package com.google.android.apps.forscience.whistlepunk.modules;

import android.content.Context;

import com.google.android.apps.forscience.whistlepunk.WhistlePunkApplication;

import javax.inject.Singleton;

import dagger.Module;
import dagger.Provides;

/**
 * Provides a safe context for other modules.
 */
@Module
public class ContextModule {

    WhistlePunkApplication mApplication;

    public ContextModule(WhistlePunkApplication application) {
        mApplication = application;
    }

    @Provides
    Context providesContext() {
        return mApplication;
    }
}
