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
import android.app.Fragment;
import android.content.Context;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.widget.NestedScrollView;
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
import com.jakewharton.rxbinding2.widget.RxTextView;

import io.reactivex.Observable;
import io.reactivex.subjects.BehaviorSubject;

/**
 * Fragment controlling adding text notes in the observe pane.
 */
public class TextToolFragment extends PanesToolFragment {
    private static final String KEY_TEXT = "saved_text";

    /**
     * The height at which the control bar is replaced by an inline button, in (approximate)
     * lines of text (this includes toolbar and control bar)
     */
    private static final int COLLAPSE_THRESHHOLD_LINES_OF_TEXT = 10;

    private TextView mTextView;
    private TextLabelFragmentListener mListener;
    private BehaviorSubject<CharSequence> mWhenText = BehaviorSubject.create();
    private RxEvent mFocusLost = new RxEvent();
    private BehaviorSubject<Boolean> mShowingCollapsed = BehaviorSubject.create();
    private BehaviorSubject<Integer> mTextSize = BehaviorSubject.create();
    private boolean mUserMovingScroll = false;

    public View getViewToKeepVisible() {
        return mTextView;
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
    public View onCreatePanesView(LayoutInflater inflater, @Nullable ViewGroup container,
            Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.text_label_fragment, null);

        mTextView = (TextView) rootView.findViewById(R.id.text);

        NestedScrollView scroll = (NestedScrollView) rootView.findViewById(R.id.scroll);
        scroll.setOnTouchListener((v, event) -> {
            int action = event.getAction();
            if (action == MotionEvent.ACTION_MOVE) {
                mUserMovingScroll = true;
            } else if (action == MotionEvent.ACTION_UP) {
                mUserMovingScroll = false;
            }
            return false;
        });
        scroll.setOnScrollChangeListener(new NestedScrollView.OnScrollChangeListener() {
            @Override
            public void onScrollChange(NestedScrollView v, int scrollX, int scrollY, int oldScrollX,
                    int oldScrollY) {
                if (mUserMovingScroll) {
                    mTextView.clearFocus();
                }
            }
        });
        mTextSize.onNext((int)mTextView.getTextSize());

        RxTextView.afterTextChangeEvents(mTextView)
                  .subscribe(event -> mWhenText.onNext(event.view().getText()));

        if (savedInstanceState != null) {
            mTextView.setText(savedInstanceState.getString(KEY_TEXT));
        }

        ImageButton addButton = (ImageButton) rootView.findViewById(R.id.btn_add_inline);
        setupAddButton(addButton);

        mShowingCollapsed.subscribe(isCollapsed -> {
            addButton.setVisibility(isCollapsed ? View.VISIBLE : View.GONE);
            mTextView.setMaxLines(isCollapsed ? 3 : Integer.MAX_VALUE);
        });

        return rootView;
    }

    public void attachButtons(View controlBar) {
        ImageButton addButton = (ImageButton) controlBar.findViewById(R.id.btn_add);
        setupAddButton(addButton);

        mShowingCollapsed.subscribe(isCollapsed -> {
            controlBar.setVisibility(isCollapsed ? View.GONE : View.VISIBLE);
        });

        mFocusLost.happens().subscribe(o -> controlBar.setVisibility(View.VISIBLE));
    }

    public void setupAddButton(ImageButton addButton) {
        addButton.setOnClickListener(view -> {
            final long timestamp = getTimestamp(addButton.getContext());
            GoosciTextLabelValue.TextLabelValue labelValue =
                    new GoosciTextLabelValue.TextLabelValue();
            labelValue.text = mTextView.getText().toString();
            Label result = Label.newLabelWithValue(timestamp, GoosciLabel.Label.TEXT, labelValue,
                    null);
            getListener(addButton.getContext()).onTextLabelTaken(result);

            log(addButton.getContext(), result);
            // Clear the text
            mTextView.setText("");
        });
        // TODO: Need to update the content description if we are recording or not.
        // This will probably happen in the ControlBar rather than here.
        addButton.setEnabled(false);

        mWhenText.subscribe(text -> addButton.setEnabled(!TextUtils.isEmpty(text)));
    }

    private void log(Context context, Label result) {
        WhistlePunkApplication.getUsageTracker(context)
                              .trackEvent(TrackerConstants.CATEGORY_NOTES,
                                      TrackerConstants.ACTION_CREATE,
                                      TrackerConstants.LABEL_EXPERIMENT_DETAIL,
                                      TrackerConstants.getLabelValueType(result));
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        if (mTextView != null && mTextView.getText() != null) {
            outState.putString(KEY_TEXT, mTextView.getText().toString());
        }
        super.onSaveInstanceState(outState);
    }

    private long getTimestamp(Context context) {
        return getClock(context).getNow();
    }

    private Clock getClock(Context context) {
        return AppSingleton.getInstance(context)
                .getSensorEnvironment()
                .getDefaultClock();
    }

    private TextLabelFragmentListener getListener(Context context) {
        if (mListener == null) {
            if (context instanceof ListenerProvider) {
                mListener = ((ListenerProvider) context).getTextLabelFragmentListener();
            } else {
                Fragment parentFragment = getParentFragment();
                if (parentFragment instanceof ListenerProvider) {
                    mListener = ((ListenerProvider) parentFragment).getTextLabelFragmentListener();
                } else if (parentFragment == null) {
                    mListener = ((ListenerProvider) getActivity()).getTextLabelFragmentListener();
                }
            }
        }
        return mListener;
    }

    public void listenToAvailableHeight(Observable<Integer> height) {
        Observable.combineLatest(height, mTextSize, (h, s) -> h < collapseThreshold(s) && canTint())
                  .takeUntil(mFocusLost.happens())
                  .subscribe(collapsed -> mShowingCollapsed.onNext(collapsed));
    }

    private boolean canTint() {
        // if we can't tint, we can't currently show an inline send button (b/67312778)
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP;
    }

    public int collapseThreshold(int textSize) {
        return textSize * COLLAPSE_THRESHHOLD_LINES_OF_TEXT;
    }

    @Override
    public void onGainedFocus(Activity activity) {
        super.onGainedFocus(activity);
        // when losing focus, close keyboard
        mFocusLost.happensNext().subscribe(() -> KeyboardUtil.closeKeyboard(activity).subscribe());
    }

    public void onLosingFocus() {
        mFocusLost.onHappened();
        super.onLosingFocus();
    }
}
