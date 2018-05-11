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
import android.os.Handler;
import androidx.fragment.app.Fragment;
import android.view.View;
import com.google.android.apps.forscience.whistlepunk.accounts.AppAccount;
import com.google.android.apps.forscience.whistlepunk.filemetadata.SensorTrigger;
import com.google.android.apps.forscience.whistlepunk.metadata.TriggerHelper;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** A presenter for sensor triggers. */
public class CardTriggerPresenter {

  private static final long TRIGGER_TEXT_SWITCHER_DELAY_MS = 3000;

  public interface OnCardTriggerClickedListener {
    void onCardTriggerIconClicked();
  }

  private final OnCardTriggerClickedListener listener;
  private List<SensorTrigger> sensorTriggers = Collections.emptyList();
  private List<String> triggerText = new ArrayList<>();
  private int displayedTriggerTextIndex = 0;
  private CardViewHolder cardViewHolder;
  private Activity activity;
  private Handler handler;
  private Runnable triggerRunnable;

  public CardTriggerPresenter(OnCardTriggerClickedListener listener, Fragment fragment) {
    this.listener = listener;
    // In tests, the fragment may be null.
    activity = fragment != null ? fragment.getActivity() : null;
  }

  public void setViews(CardViewHolder cardViewHolder) {
    this.cardViewHolder = cardViewHolder;
    View.OnClickListener listener =
        new View.OnClickListener() {
          @Override
          public void onClick(View v) {
            CardTriggerPresenter.this.listener.onCardTriggerIconClicked();
          }
        };
    // Put the click listener on both the check box button and the trigger icon button.
    this.cardViewHolder.triggerIcon.getChildAt(0).setOnClickListener(listener);
    this.cardViewHolder.triggerIcon.getChildAt(1).setOnClickListener(listener);
    if (sensorTriggers.size() > 0) {
      trySettingUpTextSwitcher();
    }
  }

  public void onViewRecycled() {
    if (cardViewHolder != null) {
      cardViewHolder.triggerIcon.getChildAt(0).setOnClickListener(null);
      cardViewHolder.triggerIcon.getChildAt(1).setOnClickListener(null);
      cardViewHolder.triggerFiredBackground.setAnimationListener(null);
      cardViewHolder = null;
    }
    if (handler != null) {
      handler.removeCallbacks(triggerRunnable);
      handler = null;
    }
    triggerRunnable = null;
  }

  public void onDestroy() {
    if (cardViewHolder != null) {
      onViewRecycled();
    }
    activity = null;
  }

  public void setSensorTriggers(List<SensorTrigger> sensorTriggers, AppAccount appAccount) {
    this.sensorTriggers = sensorTriggers;
    if (displayedTriggerTextIndex < this.sensorTriggers.size()) {
      displayedTriggerTextIndex = 0;
    }
    createTextForTriggers(appAccount);
    if (cardViewHolder != null) {
      trySettingUpTextSwitcher();
    }
  }

  private void trySettingUpTextSwitcher() {
    if (triggerText.size() == 0) {
      cardViewHolder.triggerTextSwitcher.setCurrentText("");
      return;
    }
    if (handler == null) {
      handler = new Handler();
      triggerRunnable =
          new Runnable() {
            @Override
            public void run() {
              if (triggerText.size() == 0 || cardViewHolder == null) {
                return;
              }
              displayedTriggerTextIndex = (++displayedTriggerTextIndex) % triggerText.size();
              cardViewHolder.triggerTextSwitcher.setText(
                  triggerText.get(displayedTriggerTextIndex));
              handler.postDelayed(triggerRunnable, TRIGGER_TEXT_SWITCHER_DELAY_MS);
            }
          };
    }
    cardViewHolder.triggerFiredBackground.setAnimationListener(
        new TriggerBackgroundView.TriggerAnimationListener() {
          @Override
          public void onAnimationStart() {
            cardViewHolder.triggerFiredText.setVisibility(View.VISIBLE);
            cardViewHolder.triggerTextSwitcher.setVisibility(View.INVISIBLE);
            cardViewHolder.triggerIcon.showNext();
          }

          @Override
          public void onAnimationEnd() {
            if (cardViewHolder != null) {
              cardViewHolder.triggerFiredText.setVisibility(View.GONE);
              cardViewHolder.triggerTextSwitcher.setVisibility(View.VISIBLE);
              cardViewHolder.triggerIcon.showPrevious();
            }
          }
        });
    if (triggerText.size() == 1) {
      // No need for a switcher with one trigger
      cardViewHolder.triggerTextSwitcher.setCurrentText(triggerText.get(0));
    } else {
      cardViewHolder.triggerTextSwitcher.setCurrentText(triggerText.get(displayedTriggerTextIndex));
      cardViewHolder.triggerTextSwitcher.setInAnimation(
          cardViewHolder.getContext(), android.R.anim.fade_in);
      cardViewHolder.triggerTextSwitcher.setOutAnimation(
          cardViewHolder.getContext(), android.R.anim.fade_out);
      triggerRunnable.run();
    }
    cardViewHolder.triggerLevelDrawableButton.setImageLevel(triggerText.size());
  }

  public List<SensorTrigger> getSensorTriggers() {
    return sensorTriggers;
  }

  public void updateSensorTriggerUi() {
    if (sensorTriggers.size() == 0) {
      cardViewHolder.triggerSection.setVisibility(View.GONE);
    } else {
      cardViewHolder.triggerSection.setVisibility(View.VISIBLE);
    }
  }

  private void createTextForTriggers(AppAccount appAccount) {
    triggerText.clear();
    if (activity == null) {
      return;
    }
    for (SensorTrigger trigger : sensorTriggers) {
      triggerText.add(TriggerHelper.buildDescription(trigger, activity, appAccount));
    }
  }

  public void onSensorTriggerFired() {
    // Whenever a trigger fires, we update the header to show an animation. Per UX, it does not
    // matter, the type of trigger. All triggers show an animation, but visual alert triggers
    // do nothing else.
    if (cardViewHolder == null
        || cardViewHolder.triggerSection.getVisibility() == View.GONE
        || !cardViewHolder.triggerSection.isAttachedToWindow()) {
      return;
    }
    cardViewHolder.triggerFiredBackground.onTriggerFired();
  }
}
