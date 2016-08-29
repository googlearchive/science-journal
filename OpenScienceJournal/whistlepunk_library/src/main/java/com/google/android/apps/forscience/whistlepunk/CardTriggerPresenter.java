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
import android.app.Activity;
import android.app.Fragment;
import android.os.Build;
import android.os.Handler;
import android.view.View;
import android.view.ViewAnimationUtils;

import com.google.android.apps.forscience.whistlepunk.metadata.SensorTrigger;
import com.google.android.apps.forscience.whistlepunk.metadata.TriggerHelper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A presenter for sensor triggers.
 */
public class CardTriggerPresenter {

    private static final long TRIGGER_TEXT_SWITCHER_DELAY_MS = 3000;

    public interface OnCardTriggerClickedListener {
        void onCardTriggerIconClicked();
    }

    private final OnCardTriggerClickedListener mListener;
    private List<SensorTrigger> mSensorTriggers = Collections.emptyList();
    private List<String> mTriggerText = new ArrayList<>();
    private int mDisplayedTriggerTextIndex = 0;
    private CardViewHolder mCardViewHolder;
    private Activity mActivity;
    private Handler mHandler;
    private Runnable mTriggerRunnable;

    public CardTriggerPresenter(OnCardTriggerClickedListener listener, Fragment fragment) {
        mListener = listener;
        // In tests, the fragment may be null.
        mActivity = fragment != null ? fragment.getActivity() : null;
        mHandler = new Handler();
        mTriggerRunnable = new Runnable() {
            @Override
            public void run() {
                if (mTriggerText.size() == 0 || mCardViewHolder == null) {
                    return;
                }
                mDisplayedTriggerTextIndex = (++mDisplayedTriggerTextIndex) % mTriggerText.size();
                mCardViewHolder.triggerTextSwitcher.setText(
                        mTriggerText.get(mDisplayedTriggerTextIndex));
                mHandler.postDelayed(mTriggerRunnable, TRIGGER_TEXT_SWITCHER_DELAY_MS);
            }
        };
    }

    public void setViews(CardViewHolder cardViewHolder) {
        mCardViewHolder = cardViewHolder;
        mCardViewHolder.triggerIcon.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mListener.onCardTriggerIconClicked();
            }
        });
        if (mSensorTriggers.size() > 0) {
            trySettingUpTextSwitcher();
        }
    }

    public void onViewRecycled() {
        mCardViewHolder.triggerIcon.setOnClickListener(null);
        mCardViewHolder = null;
        mHandler.removeCallbacks(mTriggerRunnable);
    }

    public void setSensorTriggers(List<SensorTrigger> sensorTriggers) {
        mSensorTriggers = sensorTriggers;
        if (mDisplayedTriggerTextIndex < mSensorTriggers.size()) {
            mDisplayedTriggerTextIndex = 0;
        }
        createTextForTriggers();
        if (mCardViewHolder != null) {
            trySettingUpTextSwitcher();
        }
    }

    private void trySettingUpTextSwitcher() {
        if (mTriggerText.size() == 0) {
            mCardViewHolder.triggerTextSwitcher.setCurrentText("");
        } else if (mTriggerText.size() == 1) {
            // No need for a switcher with one trigger
            mCardViewHolder.triggerTextSwitcher.setCurrentText(mTriggerText.get(0));
        } else {
            mCardViewHolder.triggerTextSwitcher.setCurrentText(mTriggerText.get(
                    mDisplayedTriggerTextIndex));
            mCardViewHolder.triggerTextSwitcher.setInAnimation(mCardViewHolder.getContext(),
                    android.R.anim.fade_in);
            mCardViewHolder.triggerTextSwitcher.setOutAnimation(mCardViewHolder.getContext(),
                    android.R.anim.fade_out);
            mTriggerRunnable.run();
        }
    }

    public List<SensorTrigger> getSensorTriggers() {
        return mSensorTriggers;
    }

    public void updateSensorTriggerUi() {
        if (mSensorTriggers.size() == 0) {
            mCardViewHolder.triggerSection.setVisibility(View.GONE);
        } else {
            mCardViewHolder.triggerSection.setVisibility(View.VISIBLE);
            mCardViewHolder.triggerFiredBackground.setVisibility(View.GONE);
        }
    }

    private void createTextForTriggers() {
        mTriggerText.clear();
        for (SensorTrigger trigger : mSensorTriggers) {
            mTriggerText.add(TriggerHelper.buildDescription(trigger, mActivity));
        }
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
                    prepareForAnimation();
                }

                public void onAnimationEnd(Animator animation) {
                    animOut.start();
                    animIn.removeAllListeners();
                }
            });
            animOut.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    completeAnimation();
                    animOut.removeAllListeners();
                }
            });
            animIn.setDuration(300);
            animOut.setDuration(300);
            animOut.setStartDelay(600);
            animIn.start();
        } else {
            // No animation, so just show and hide the background.
            prepareForAnimation();
            mCardViewHolder.triggerFiredBackground.postDelayed(new Runnable() {
                @Override
                public void run() {
                    completeAnimation();
                }
            }, 500);
        }
    }

    private void prepareForAnimation() {
        mCardViewHolder.triggerFiredBackground.setVisibility(View.VISIBLE);
        mCardViewHolder.triggerFiredText.setVisibility(View.VISIBLE);
        mCardViewHolder.triggerTextSwitcher.setVisibility(View.INVISIBLE);
    }

    private void completeAnimation() {
        if (mCardViewHolder != null) {
            mCardViewHolder.triggerFiredBackground.setVisibility(View.GONE);
            mCardViewHolder.triggerFiredText.setVisibility(View.GONE);
            mCardViewHolder.triggerTextSwitcher.setVisibility(View.VISIBLE);
        }
    }
}
