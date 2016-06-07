package com.google.android.apps.forscience.whistlepunk.opensource.modules;

import com.google.android.apps.forscience.javalib.MaybeConsumer;
import com.google.android.apps.forscience.whistlepunk.feedback.FeedbackProvider;

import dagger.Module;
import dagger.Provides;

/**
 * Stub feedback module.
 */
@Module
public class FeedbackModule {

    @Provides
    public FeedbackProvider provideFeedbackProvider() {
        return new FeedbackProvider() {
            @Override
            public void sendFeedback(MaybeConsumer<Boolean> onSuccess) {
                // Do nothing.
                onSuccess.success(true);
            }
        };
    }
}
