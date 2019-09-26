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

import android.graphics.Rect;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import androidx.cardview.widget.CardView;
import com.google.android.apps.forscience.whistlepunk.filemetadata.SensorLayoutPojo;
import java.util.ArrayList;
import java.util.List;

/** Adapter for a SensorCard Recycler View. */
public class SensorCardAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
  public interface SensorCardHeaderToggleListener {
    void onToggleSensorHeader(SensorCardPresenter sensorCardPresenter);
  }

  // Called after the card is removed and before the SensorCardPresenter is destroyed.
  public interface CardRemovedListener {
    void onCardRemoved(SensorCardPresenter sensorCardPresenter);
  }

  private static final int TYPE_SENSOR_CARD = 0;
  private static final int TYPE_SENSOR_ADD = 1;

  // The maximum number of sensor cards allowed at one time.
  private static final int MAX_SENSOR_COUNT = 10;

  // Enables WIP functionality to show multiple sensor cards.
  private static final boolean ENABLE_MULTIPLE_SENSOR_CARDS = true;

  private List<SensorCardPresenter> sensorCardPresenters;
  private int availableSensorCount;
  private View.OnClickListener onAddButtonClickListener;
  private SensorCardHeaderToggleListener sensorCardHeaderToggleListener;
  private CardRemovedListener cardRemovedListener;
  private int singleCardPresenterHeight;
  private View addView;
  private Rect addRect = new Rect();
  private boolean uiIsLocked;

  public static class AddCardViewHolder extends RecyclerView.ViewHolder {
    public ImageButton button;

    public AddCardViewHolder(View itemView) {
      super(itemView);
      // Because the same Drawable is used in other button backgrounds but with different
      // color tins, we need to update the color dynamically.
      button = (ImageButton) itemView.findViewById(R.id.btn_add_sensor_card);
    }
  }

  public SensorCardAdapter(
      List<SensorCardPresenter> sensorCardPresenters,
      View.OnClickListener onAddButtonClickListener,
      CardRemovedListener cardRemovedListener,
      SensorCardHeaderToggleListener sensorCardHeaderToggleListener) {
    this.sensorCardPresenters = sensorCardPresenters;
    // TODO(b/134590927): Delete onAddButtonClickListener as part of killing RecordFragment
    this.onAddButtonClickListener = onAddButtonClickListener;
    this.sensorCardHeaderToggleListener = sensorCardHeaderToggleListener;
    this.cardRemovedListener = cardRemovedListener;
  }

  public List<SensorCardPresenter> getSensorCardPresenters() {
    return sensorCardPresenters;
  }

  public void addSensorCardPresenter(SensorCardPresenter sensorCardPresenter) {
    int oldSize = sensorCardPresenters.size();
    // If the size is bigger than 1, then we need to re-enable the close button on the first
    // card.
    if (oldSize == 1) {
      sensorCardPresenters.get(0).setIsSingleCard(false);
    }
    sensorCardPresenters.add(sensorCardPresenter);
    if (oldSize == getMaxSensorCount() - 1) {
      notifyItemChanged(oldSize);
    } else {
      notifyItemInserted(oldSize);
    }
  }

  public void setSingleCardPresenterHeight(int singlePresenterHeight) {
    singleCardPresenterHeight = singlePresenterHeight;
    int size = sensorCardPresenters.size();
    for (SensorCardPresenter sensorCardPresenter : sensorCardPresenters) {
      sensorCardPresenter.setSingleCardPresenterHeight(singlePresenterHeight);
      if (size == 1) {
        sensorCardPresenter.setIsSingleCard(true);
      } else {
        sensorCardPresenter.setIsSingleCard(false);
      }
    }
  }

  public void setRecording(boolean isRecording, long startRecordingTimestamp) {
    setUiLockedForRecording(isRecording);
    for (SensorCardPresenter sensorCardPresenter : sensorCardPresenters) {
      sensorCardPresenter.setRecording(startRecordingTimestamp);
    }
  }

  public void setUiLockedForRecording(boolean uiIsLocked) {
    if (this.uiIsLocked != uiIsLocked) {
      this.uiIsLocked = uiIsLocked;
      if (this.uiIsLocked && getMaxSensorCount() != sensorCardPresenters.size()) {
        // Hide the add button and tell the presenters to lock their UIs for recording.
        notifyItemRemoved(sensorCardPresenters.size());
      } else {
        notifyItemInserted(sensorCardPresenters.size());
      }
    }
    if (uiIsLocked) {
      for (SensorCardPresenter presenter : sensorCardPresenters) {
        presenter.lockUiForRecording();
      }
    }
  }

  public void setAvailableSensorCount(int sensorCount) {
    if (availableSensorCount == sensorCardPresenters.size() && sensorCount > getMaxSensorCount()) {
      // Notify if we've added the "add sensor card" button back in.
      notifyItemInserted(getMaxSensorCount());
    }
    availableSensorCount = sensorCount;
  }

  public void onPause() {
    for (SensorCardPresenter sensorCardPresenter : sensorCardPresenters) {
      sensorCardPresenter.onPause();
    }
  }

  public void onResume(long resetTime) {
    for (SensorCardPresenter sensorCardPresenter : sensorCardPresenters) {
      sensorCardPresenter.onResume(resetTime);
    }
  }

  public void onDestroy() {
    for (SensorCardPresenter sensorCardPresenter : sensorCardPresenters) {
      sensorCardPresenter.destroy();
    }
  }

  // Create new views (invoked by the layout manager).
  @Override
  public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
    if (viewType == TYPE_SENSOR_CARD) {
      CardView itemView =
          (CardView)
              LayoutInflater.from(parent.getContext()).inflate(R.layout.sensor_card, parent, false);
      final CardViewHolder cardViewHolder = new CardViewHolder(itemView);
      // Fix the touch size of the toggle button here, as it does not need to be updated
      // every time data is bound.
      cardViewHolder.header.post(
          new Runnable() {
            // Post in the parent's message queue to make sure the parent
            // lays out its children before you call getHitRect()
            @Override
            public void run() {
              cardViewHolder.header.setTouchDelegate(
                  cardViewHolder.toggleButton.makeTouchDelegate());
            }
          });
      cardViewHolder.toggleButton.setActionStrings(
          R.string.btn_sensor_card_expand, R.string.btn_sensor_card_contract);
      return cardViewHolder;
    } else if (viewType == TYPE_SENSOR_ADD) {
      addView =
          LayoutInflater.from(parent.getContext())
              .inflate(R.layout.add_sensor_card_button, parent, false);
      return new SensorCardAdapter.AddCardViewHolder(addView);
    } else {
      return null;
    }
  }

  // Replace the contents of a view (invoked by the layout manager).
  @Override
  public void onBindViewHolder(RecyclerView.ViewHolder viewHolder, int position) {
    // - get element from your dataset at this position.
    // - replace the contents of the view with that element.
    if (getItemViewType(position) == TYPE_SENSOR_CARD) {
      final CardViewHolder cardViewHolder = (CardViewHolder) viewHolder;
      final SensorCardPresenter sensorCardPresenter = sensorCardPresenters.get(position);
      sensorCardPresenter.setSingleCardPresenterHeight(singleCardPresenterHeight);
      sensorCardPresenter.setViews(
          cardViewHolder,
          new SensorCardPresenter.OnCloseClickedListener() {
            @Override
            public void onCloseClicked() {
              if (sensorCardPresenters.size() < 2) {
                return;
              }
              int index = sensorCardPresenters.indexOf(sensorCardPresenter);
              sensorCardPresenters.remove(sensorCardPresenter);
              cardRemovedListener.onCardRemoved(sensorCardPresenter);
              sensorCardPresenter.destroy();
              // If only one card is left, disable the 'close' button.
              if (sensorCardPresenters.size() == 1) {
                sensorCardPresenters.get(0).setIsSingleCard(true);
              }
              notifyItemRemoved(index);
            }
          });
      if (!ENABLE_MULTIPLE_SENSOR_CARDS || sensorCardPresenters.size() == 1) {
        sensorCardPresenter.setIsSingleCard(true);
      } else {
        sensorCardPresenter.setIsSingleCard(false);
      }
      View.OnClickListener onToggleClickListener =
          new View.OnClickListener() {
            @Override
            public void onClick(View v) {
              sensorCardHeaderToggleListener.onToggleSensorHeader(sensorCardPresenter);
            }
          };
      cardViewHolder.header.setOnClickListener(onToggleClickListener);
      cardViewHolder.toggleButton.setOnClickListener(onToggleClickListener);
    } else if (getItemViewType(position) == TYPE_SENSOR_ADD) {
      AddCardViewHolder addCardViewHolder = (AddCardViewHolder) viewHolder;
      if (onAddButtonClickListener != null) {
        addCardViewHolder.button.setOnClickListener(onAddButtonClickListener);
      }
      addView = addCardViewHolder.itemView;
    }
  }

  @Override
  public void onViewRecycled(RecyclerView.ViewHolder viewHolder) {
    int position = viewHolder.getAdapterPosition();
    if (viewHolder instanceof CardViewHolder && position != RecyclerView.NO_POSITION) {
      sensorCardPresenters.get(position).onViewRecycled();
      CardViewHolder cardViewHolder = (CardViewHolder) viewHolder;
      cardViewHolder.header.setOnClickListener(null);
    } else if (getItemViewType(position) == TYPE_SENSOR_ADD) {
      addView = null;
    }
    super.onViewRecycled(viewHolder);
  }

  @Override
  public int getItemCount() {
    int size = sensorCardPresenters.size();
    if (shouldShowAddMoreSensorCardsButton()) {
      return size + 1;
    } else {
      return size;
    }
  }

  private boolean shouldShowAddMoreSensorCardsButton() {
    int size = sensorCardPresenters.size();
    return ENABLE_MULTIPLE_SENSOR_CARDS
        && !uiIsLocked
        && getMaxSensorCount() > size
        && !Flags.showActionBar();
  }

  @Override
  public int getItemViewType(int position) {
    // Return the add button view type for the final element.
    if (position == sensorCardPresenters.size()) {
      return TYPE_SENSOR_ADD;
    }
    return TYPE_SENSOR_CARD;
  }

  // Don't allow more than MAX_SENSOR_COUNT sensors to be added even if more are available.
  private int getMaxSensorCount() {
    return Math.min(MAX_SENSOR_COUNT, availableSensorCount);
  }

  public boolean canAddMoreCards() {
    return getSensorCardPresenters().size() < getMaxSensorCount() && !uiIsLocked;
  }

  /**
   * Adjusts the alpha of the add button based on the bottom panel (which houses the axis). The
   * button is completely hidden when it's below the panel's bottom, but fades in as it intersects
   * and moves above the panel.
   */
  public void adjustAddViewAlpha(Rect panelRect) {
    if (addView != null) {
      addView.getHitRect(addRect);
      int difference = panelRect.top - addRect.top;
      boolean setAlpha = false;
      if (difference < 0) {
        addView.setAlpha(0.0f);
      } else {
        addView.setAlpha(Math.min(((float) difference) / addRect.height(), 1.0f));
      }
    }
  }

  /** Gets the array of color indexes from the sensor card presenters, in order. */
  public int[] getUsedColors() {
    int[] colors = new int[sensorCardPresenters.size()];
    for (int index = 0, size = sensorCardPresenters.size(); index < size; index++) {
      colors[index] = sensorCardPresenters.get(index).getColorIndex();
    }
    return colors;
  }

  public List<String> getSelectedSensorIds() {
    List<String> sensorIds = new ArrayList<>();
    for (SensorCardPresenter presenter : sensorCardPresenters) {
      sensorIds.add(presenter.getSelectedSensorId());
    }
    return sensorIds;
  }

  /** Gets the sensor card presenter sensor IDs. */
  public String[] getSensorTags() {
    String[] tags = new String[sensorCardPresenters.size()];
    for (int index = 0, size = sensorCardPresenters.size(); index < size; index++) {
      tags[index] = sensorCardPresenters.get(index).getSelectedSensorId();
    }
    return tags;
  }

  @NonNull
  public List<SensorLayoutPojo> buildLayouts() {
    List<SensorCardPresenter> presenters = getSensorCardPresenters();
    int size = presenters.size();
    List<SensorLayoutPojo> layouts = new ArrayList<>();
    for (int i = 0; i < size; i++) {
      layouts.add(presenters.get(i).buildLayout());
    }
    return layouts;
  }
}
