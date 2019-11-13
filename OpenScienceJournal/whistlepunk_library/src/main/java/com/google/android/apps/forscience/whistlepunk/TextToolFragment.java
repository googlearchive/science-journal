/*
 *  Copyright 2017 Google Inc. All Rights Reserved.
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

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.core.widget.NestedScrollView;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;
import com.google.android.apps.forscience.whistlepunk.analytics.TrackerConstants;
import com.google.android.apps.forscience.whistlepunk.filemetadata.Label;
import com.google.android.apps.forscience.whistlepunk.metadata.GoosciLabel;
import com.google.android.apps.forscience.whistlepunk.metadata.GoosciTextLabelValue;
import com.google.android.apps.forscience.whistlepunk.metadata.GoosciTextLabelValue.TextLabelValue;
import com.jakewharton.rxbinding2.widget.RxTextView;
import io.reactivex.Observable;
import io.reactivex.subjects.BehaviorSubject;

/**
 * Fragment controlling adding text notes in the observe pane.
 *
 * @deprecated Moving to {@link TextNoteFragment}.
 */
@Deprecated
public class TextToolFragment extends PanesToolFragment {
  private static final String KEY_TEXT = "saved_text";

  /**
   * The height at which the control bar is replaced by an inline button, in (approximate) lines of
   * text (this includes toolbar and control bar)
   */
  private static final int COLLAPSE_THRESHHOLD_LINES_OF_TEXT = 10;

  private TextView textView;
  private TextLabelFragmentListener listener;
  private BehaviorSubject<CharSequence> whenText = BehaviorSubject.create();
  private RxEvent focusLost = new RxEvent();
  private BehaviorSubject<Boolean> showingCollapsed = BehaviorSubject.create();
  private BehaviorSubject<Integer> textSize = BehaviorSubject.create();
  private boolean userMovingScroll = false;

  public View getViewToKeepVisible() {
    return textView;
  }

  public interface TextLabelFragmentListener {
    void onTextLabelTaken(Label result);
  }

  public interface ListenerProvider {
    TextToolFragment.TextLabelFragmentListener getTextLabelFragmentListener();
  }

  public static Fragment newInstance() {
    TextToolFragment fragment = new TextToolFragment();
    Bundle args = new Bundle();
    fragment.setArguments(args);
    return fragment;
  }

  @SuppressLint("ClickableViewAccessibility")
  @Nullable
  @Override
  public View onCreatePanesView(
      LayoutInflater inflater, @Nullable ViewGroup container, Bundle savedInstanceState) {
    View rootView = inflater.inflate(R.layout.text_label_fragment, null);

    textView = (TextView) rootView.findViewById(R.id.text);

    NestedScrollView scroll = (NestedScrollView) rootView.findViewById(R.id.scroll);
    scroll.setOnTouchListener(
        (v, event) -> {
          int action = event.getAction();
          if (action == MotionEvent.ACTION_MOVE) {
            userMovingScroll = true;
          } else if (action == MotionEvent.ACTION_UP) {
            userMovingScroll = false;
          }
          return false;
        });
    scroll.setOnScrollChangeListener(
        new NestedScrollView.OnScrollChangeListener() {
          @Override
          public void onScrollChange(
              NestedScrollView v, int scrollX, int scrollY, int oldScrollX, int oldScrollY) {
            if (userMovingScroll) {
              textView.clearFocus();
            }
          }
        });
    textSize.onNext((int) textView.getTextSize());

    RxTextView.afterTextChangeEvents(textView)
        .subscribe(event -> whenText.onNext(event.view().getText()));

    if (savedInstanceState != null) {
      textView.setText(savedInstanceState.getString(KEY_TEXT));
    }

    ImageButton addButton = (ImageButton) rootView.findViewById(R.id.btn_add_inline);
    setupAddButton(addButton);

    showingCollapsed.subscribe(
        isCollapsed -> {
          addButton.setVisibility(isCollapsed ? View.VISIBLE : View.GONE);
          textView.setMaxLines(isCollapsed ? 3 : Integer.MAX_VALUE);
        });

    return rootView;
  }

  public void attachButtons(View controlBar) {
    ImageButton addButton = (ImageButton) controlBar.findViewById(R.id.btn_add);
    setupAddButton(addButton);

    showingCollapsed.subscribe(
        isCollapsed -> {
          controlBar.setVisibility(isCollapsed ? View.GONE : View.VISIBLE);
        });

    focusLost.happens().subscribe(o -> controlBar.setVisibility(View.VISIBLE));
  }

  public void setupAddButton(ImageButton addButton) {
    addButton.setOnClickListener(
        view -> {
          final long timestamp = getTimestamp(addButton.getContext());
          TextLabelValue labelValue =
              GoosciTextLabelValue.TextLabelValue.newBuilder()
                  .setText(textView.getText().toString())
                  .build();
          Label result =
              Label.newLabelWithValue(
                  timestamp, GoosciLabel.Label.ValueType.TEXT, labelValue, null);
          getListener(addButton.getContext()).onTextLabelTaken(result);

          log(addButton.getContext(), result);
          // Clear the text
          textView.setText("");
        });
    // TODO: Need to update the content description if we are recording or not.
    // This will probably happen in the ControlBar rather than here.
    addButton.setEnabled(false);

    whenText.subscribe(text -> addButton.setEnabled(!TextUtils.isEmpty(text)));
  }

  private void log(Context context, Label result) {
    WhistlePunkApplication.getUsageTracker(context)
        .trackEvent(
            TrackerConstants.CATEGORY_NOTES,
            TrackerConstants.ACTION_CREATE,
            TrackerConstants.LABEL_EXPERIMENT_DETAIL,
            TrackerConstants.getLabelValueType(result));
  }

  @Override
  public void onSaveInstanceState(Bundle outState) {
    if (textView != null && textView.getText() != null) {
      outState.putString(KEY_TEXT, textView.getText().toString());
    }
    super.onSaveInstanceState(outState);
  }

  private long getTimestamp(Context context) {
    return getClock(context).getNow();
  }

  private Clock getClock(Context context) {
    return AppSingleton.getInstance(context).getSensorEnvironment().getDefaultClock();
  }

  private TextLabelFragmentListener getListener(Context context) {
    if (listener == null) {
      if (context instanceof ListenerProvider) {
        listener = ((ListenerProvider) context).getTextLabelFragmentListener();
      } else {
        Fragment parentFragment = getParentFragment();
        if (parentFragment instanceof ListenerProvider) {
          listener = ((ListenerProvider) parentFragment).getTextLabelFragmentListener();
        } else if (parentFragment == null) {
          listener = ((ListenerProvider) getActivity()).getTextLabelFragmentListener();
        }
      }
    }
    return listener;
  }

  public void listenToAvailableHeight(Observable<Integer> height) {
    Observable.combineLatest(height, textSize, (h, s) -> h < collapseThreshold(s))
        .takeUntil(focusLost.happens())
        .subscribe(collapsed -> showingCollapsed.onNext(collapsed));
  }

  public int collapseThreshold(int textSize) {
    return textSize * COLLAPSE_THRESHHOLD_LINES_OF_TEXT;
  }

  @Override
  public void onGainedFocus(Activity activity) {
    super.onGainedFocus(activity);
    // when losing focus, close keyboard
    focusLost.happensNext().subscribe(() -> KeyboardUtil.closeKeyboard(activity).subscribe());
  }

  public void onLosingFocus() {
    focusLost.onHappened();
    super.onLosingFocus();
  }
}
