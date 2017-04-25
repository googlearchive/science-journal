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

import android.app.Activity;
import android.app.Fragment;
import android.os.Handler;
import android.view.View;

import com.google.android.apps.forscience.whistlepunk.filemetadata.SensorTrigger;
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
    }

    public void setViews(CardViewHolder cardViewHolder) {
        mCardViewHolder = cardViewHolder;
        View.OnClickListener listener = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mListener.onCardTriggerIconClicked();
            }
        };
        // Put the click listener on both the check box button and the trigger icon button.
        mCardViewHolder.triggerIcon.getChildAt(0).setOnClickListener(listener);
        mCardViewHolder.triggerIcon.getChildAt(1).setOnClickListener(listener);
        if (mSensorTriggers.size() > 0) {
            trySettingUpTextSwitcher();
        }
    }

    public void onViewRecycled() {
        if (mCardViewHolder != null) {
            mCardViewHolder.triggerIcon.getChildAt(0).setOnClickListener(null);
            mCardViewHolder.triggerIcon.getChildAt(1).setOnClickListener(null);
            mCardViewHolder.triggerFiredBackground.setAnimationListener(null);
            mCardViewHolder = null;
        }
        if (mHandler != null) {
            mHandler.removeCallbacks(mTriggerRunnable);
            mHandler = null;
        }
        mTriggerRunnable = null;
    }

    public void onDestroy() {
        if (mCardViewHolder != null) {
            onViewRecycled();
        }
        mActivity = null;
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
            return;
        }
        if (mHandler == null) {
            mHandler = new Handler();
            mTriggerRunnable = new Runnable() {
                @Override
                public void run() {
                    if (mTriggerText.size() == 0 || mCardViewHolder == null) {
                        return;
                    }
                    mDisplayedTriggerTextIndex =
                            (++mDisplayedTriggerTextIndex) % mTriggerText.size();
                    mCardViewHolder.triggerTextSwitcher.setText(
                            mTriggerText.get(mDisplayedTriggerTextIndex));
                    mHandler.postDelayed(mTriggerRunnable, TRIGGER_TEXT_SWITCHER_DELAY_MS);
                }
            };
        }
        mCardViewHolder.triggerFiredBackground.setAnimationListener(
                new TriggerBackgroundView.TriggerAnimationListener() {
                    @Override
                    public void onAnimationStart() {
                        mCardViewHolder.triggerFiredText.setVisibility(View.VISIBLE);
                        mCardViewHolder.triggerTextSwitcher.setVisibility(View.INVISIBLE);
                        mCardViewHolder.triggerIcon.showNext();
                    }

                    @Override
                    public void onAnimationEnd() {
                        if (mCardViewHolder != null) {
                            mCardViewHolder.triggerFiredText.setVisibility(View.GONE);
                            mCardViewHolder.triggerTextSwitcher.setVisibility(View.VISIBLE);
                            mCardViewHolder.triggerIcon.showPrevious();
                        }
                    }
                });
        if (mTriggerText.size() == 1) {
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
        mCardViewHolder.triggerLevelDrawableButton.setImageLevel(mTriggerText.size());
    }

    public List<SensorTrigger> getSensorTriggers() {
        return mSensorTriggers;
    }

    public void updateSensorTriggerUi() {
        if (mSensorTriggers.size() == 0) {
            mCardViewHolder.triggerSection.setVisibility(View.GONE);
        } else {
            mCardViewHolder.triggerSection.setVisibility(View.VISIBLE);
        }
    }

    private void createTextForTriggers() {
        mTriggerText.clear();
        if (mActivity == null) {
            return;
        }
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
        mCardViewHolder.triggerFiredBackground.onTriggerFired();
    }
}
