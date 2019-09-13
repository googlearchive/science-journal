/*
 *  Copyright 2019 Google Inc. All Rights Reserved.
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
import android.content.Context;
import android.os.Bundle;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import android.text.TextUtils;
import android.transition.Slide;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.ImageView;
import android.widget.TextView;
import com.google.android.apps.forscience.whistlepunk.analytics.TrackerConstants;
import com.google.android.apps.forscience.whistlepunk.filemetadata.Label;
import com.google.android.apps.forscience.whistlepunk.metadata.GoosciLabel;
import com.google.android.apps.forscience.whistlepunk.metadata.GoosciTextLabelValue;
import com.google.android.apps.forscience.whistlepunk.metadata.GoosciTextLabelValue.TextLabelValue;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.jakewharton.rxbinding2.widget.RxTextView;
import io.reactivex.subjects.BehaviorSubject;

/** Fragment controlling adding text notes in the ExperimentActivity. */
public class TextNoteFragment extends Fragment {
  private static final String KEY_TEXT = "saved_text";

  private TextView textView;
  private TextLabelFragmentListener listener;
  private BehaviorSubject<CharSequence> whenText = BehaviorSubject.create();
  private BehaviorSubject<Integer> textSize = BehaviorSubject.create();

  public interface TextLabelFragmentListener {
    void onTextLabelTaken(Label result);
  }

  public interface ListenerProvider {
    TextNoteFragment.TextLabelFragmentListener getTextLabelFragmentListener();
  }

  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setEnterTransition(new Slide());
    setExitTransition(new Slide());
  }

  @Override
  public final View onCreateView(
      LayoutInflater inflater, @Nullable ViewGroup container, Bundle savedInstanceState) {
    // The send button coloring depends on whether or not were recording so we have to set the theme
    // here. The theme will be updated by the activity if we're currently recording.
    Context contextThemeWrapper =
        new ContextThemeWrapper(getActivity(), R.style.DefaultActionAreaIcon);
    LayoutInflater localInflater = inflater.cloneInContext(contextThemeWrapper);
    View rootView = localInflater.inflate(R.layout.text_note_fragment, null);

    textView = rootView.findViewById(R.id.text);
    textSize.onNext((int) textView.getTextSize());

    RxTextView.afterTextChangeEvents(textView)
        .subscribe(event -> whenText.onNext(event.view().getText()));

    if (savedInstanceState != null) {
      textView.setText(savedInstanceState.getString(KEY_TEXT));
    }

    textView.setOnFocusChangeListener(
        (v, hasFocus) -> {
          InputMethodManager imm =
              (InputMethodManager) getActivity().getSystemService(Context.INPUT_METHOD_SERVICE);
          if (hasFocus) {
            imm.showSoftInputFromInputMethod(v.getWindowToken(), 0);
          } else {
            imm.hideSoftInputFromWindow(v.getWindowToken(), 0);
          }
        });
    textView.requestFocus();

    FloatingActionButton addButton = rootView.findViewById(R.id.btn_add_inline);
    setupAddButton(addButton);
    setUpTitle(rootView.findViewById(R.id.tool_pane_title_bar));
    return rootView;
  }

  public void setupAddButton(FloatingActionButton addButton) {
    addButton.setOnClickListener(
        view -> {
          Activity activity = getActivity();
          if (activity == null) {
            return;
          }
          final long timestamp =
              ((NoteTakingActivity) activity).getTimestamp(addButton.getContext());
          TextLabelValue labelValue =
              GoosciTextLabelValue.TextLabelValue.newBuilder()
                  .setText(textView.getText().toString())
                  .build();
          Label result =
              Label.newLabelWithValue(
                  timestamp, GoosciLabel.Label.ValueType.TEXT, labelValue, null);
          if (listener != null) {
            listener.onTextLabelTaken(result);
          }

          log(addButton.getContext(), result);
          // Clear the text
          textView.setText("");
        });
    addButton.setEnabled(false);

    whenText.subscribe(text -> addButton.setEnabled(!TextUtils.isEmpty(text)));
  }

  private void setUpTitle(View titleBarView) {
    NoteTakingActivity activity = (NoteTakingActivity) getActivity();
    if (activity != null) {
      if (activity.isTwoPane()) {
        ((TextView) titleBarView.findViewById(R.id.title_bar_text))
            .setText(R.string.action_bar_text_note);
        ((ImageView) titleBarView.findViewById(R.id.title_bar_icon))
            .setImageDrawable(
                getResources().getDrawable(R.drawable.ic_text, activity.getActivityTheme()));
        titleBarView
            .findViewById(R.id.title_bar_close)
            .setOnClickListener(v -> activity.closeToolFragment());
      } else {
        titleBarView.setVisibility(View.GONE);
      }
    }
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

  @Override
  public void onAttach(Context context) {
    super.onAttach(context);
    listener = ((ListenerProvider) context).getTextLabelFragmentListener();
  }

  @Override
  public void onDetach() {
    super.onDetach();
    listener = null;
  }

  @Override
  public void onResume() {
    super.onResume();
    if (isVisible()) {
      updateTitle();
    }
  }

  @Override
  public void onHiddenChanged(boolean hidden) {
    super.onHiddenChanged(hidden);
    if (!hidden) {
      updateTitle();
    }
  }

  private void updateTitle() {
    NoteTakingActivity activity = (NoteTakingActivity) getActivity();
    if (activity != null) {
      String title = getString(R.string.action_bar_text_note);
      if (activity.isTwoPane()) {
        ((TextView) getView().findViewById(R.id.title_bar_text)).setText(title);
      } else {
        activity.updateTitleByToolFragment(title);
      }
    }
  }
}
