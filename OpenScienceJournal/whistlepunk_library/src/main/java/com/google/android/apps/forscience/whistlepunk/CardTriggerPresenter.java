/*
 *  Copyright 2016 Google Inc. All Rights Reserved.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package com.google.android.apps.forscience.whistlepunk;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.os.Build;
import android.view.View;
import android.view.ViewAnimationUtils;

import com.google.android.apps.forscience.whistlepunk.metadata.SensorTrigger;

import java.util.Collections;
import java.util.List;

/**
 * A presenter for sensor triggers.
 */
public class CardTriggerPresenter {

    public interface OnCardTriggerClickedListener {
        void onCardTriggerIconClicked();
    }

    private final OnCardTriggerClickedListener mListener;
    private List<SensorTrigger> mSensorTriggers = Collections.emptyList();
    private CardViewHolder mCardViewHolder;

    public CardTriggerPresenter(OnCardTriggerClickedListener listener) {
        mListener = listener;
    }

    public void setViews(CardViewHolder cardViewHolder) {
        mCardViewHolder = cardViewHolder;
    }

    public void onViewRecycled() {
        mCardViewHolder.triggerIcon.setOnClickListener(null);
        mCardViewHolder = null;
    }

    public void setSensorTriggers(List<SensorTrigger> sensorTriggers) {
        mSensorTriggers = sensorTriggers;
    }

    public List<SensorTrigger> getSensorTriggers() {
        return mSensorTriggers;
    }

    public void updateSensorTriggerUi() {
        if (mSensorTriggers.size() == 0) {
            mCardViewHolder.triggerSection.setVisibility(View.GONE);
            return;
        }
        mCardViewHolder.triggerSection.setVisibility(View.VISIBLE);
        mCardViewHolder.triggerFiredBackground.setVisibility(View.GONE);
        mCardViewHolder.triggerIcon.setOnClickListener(new View.OnClickListener() {            @Override
            public void onClick(View v) {
                mListener.onCardTriggerIconClicked();
            }
        });
        mCardViewHolder.triggerText.setText(createCardTriggerText(mSensorTriggers.get(0)));
    }

    private String createCardTriggerText(SensorTrigger trigger) {
        return mCardViewHolder.getContext().getResources()
                .getStringArray(R.array.trigger_type_list)[trigger.getActionType()];
    }

    public void onSensorTriggerFired() {
        // Whenever a trigger fires, we update the header to show an animation. Per UX, it does not
        // matter, the type of trigger. All triggers show an animation, but visual alert triggers
        // do nothing else.
        if (mCardViewHolder == null ||
                mCardViewHolder.triggerSection.getVisibility() == View.GONE ||
                !mCardViewHolder.triggerSection.isAttachedToWindow()) {
            return;
        }
        // TODO: Do not animate a trigger until the animIn is completed. We can interrupt animOut but
        // should finish animIn even if it means missing a visual notification.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            final Animator animIn = ViewAnimationUtils.createCircularReveal(
                    mCardViewHolder.triggerFiredBackground, 20, 20, 0,
                    mCardViewHolder.triggerSection.getWidth());
            final Animator animOut = ViewAnimationUtils.createCircularReveal(
                    mCardViewHolder.triggerFiredBackground, 20, 20,
                    mCardViewHolder.triggerSection.getWidth(), 0);
            animIn.addListener(new AnimatorListenerAdapter() {
                public void onAnimationStart(Animator animation) {
                    mCardViewHolder.triggerFiredBackground.setVisibility(View.VISIBLE);
                }

                public void onAnimationEnd(Animator animation) {
                    animOut.start();
                    animIn.removeAllListeners();
                }
            });
            animOut.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    if (mCardViewHolder != null) {
                        mCardViewHolder.triggerFiredBackground.setVisibility(View.GONE);
                    }
                    animOut.removeAllListeners();
                }
            });
            animIn.setDuration(200);
            animOut.setDuration(200);
            animOut.setStartDelay(500);
            animIn.start();
        } else {
            // No animation, so just show and hide the background.
            mCardViewHolder.triggerFiredBackground.setVisibility(View.VISIBLE);
            mCardViewHolder.triggerFiredBackground.postDelayed(new Runnable() {
                @Override
                public void run() {
                    if (mCardViewHolder != null) {
                        mCardViewHolder.triggerFiredBackground.setVisibility(
                                View.GONE);
                    }
                }
            }, 500);
        }
    }
}
