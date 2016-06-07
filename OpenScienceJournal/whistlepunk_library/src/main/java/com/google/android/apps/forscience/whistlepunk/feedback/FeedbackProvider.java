package com.google.android.apps.forscience.whistlepunk.feedback;

import com.google.android.apps.forscience.javalib.MaybeConsumer;

/**
 * An object which can build up feedback to be sent for collection.
 */
public interface FeedbackProvider {

    /**
     * Called when user is requesting a feedback report get created.
     *
     * @param onSuccess  {@code true} if feedback was successfully sent,
     */
    void sendFeedback(MaybeConsumer<Boolean> onSuccess);
}
