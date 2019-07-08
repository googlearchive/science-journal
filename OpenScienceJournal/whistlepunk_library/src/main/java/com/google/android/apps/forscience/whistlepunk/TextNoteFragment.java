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

import android.content.Context;
import android.os.Bundle;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import android.text.TextUtils;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import com.google.android.apps.forscience.whistlepunk.analytics.TrackerConstants;
import com.google.android.apps.forscience.whistlepunk.filemetadata.Label;
import com.google.android.apps.forscience.whistlepunk.metadata.nano.GoosciLabel;
import com.google.android.apps.forscience.whistlepunk.metadata.nano.GoosciTextLabelValue;
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

    FloatingActionButton addButton = rootView.findViewById(R.id.btn_add_inline);
    setupAddButton(addButton);

    return rootView;
  }

  public void setupAddButton(FloatingActionButton addButton) {
    addButton.setOnClickListener(
        view -> {
          final long timestamp = getTimestamp(addButton.getContext());
          GoosciTextLabelValue.TextLabelValue labelValue =
              new GoosciTextLabelValue.TextLabelValue();
          labelValue.text = textView.getText().toString();
          Label result =
              Label.newLabelWithValue(
                  timestamp, GoosciLabel.Label.ValueType.TEXT, labelValue, null);
          getListener(addButton.getContext()).onTextLabelTaken(result);

          log(addButton.getContext(), result);
          // Clear the text
          textView.setText("");
        });
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
}
