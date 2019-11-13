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

import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import com.google.common.base.Optional;
import io.reactivex.Maybe;
import io.reactivex.Observable;
import io.reactivex.subjects.BehaviorSubject;

public abstract class PanesToolFragment extends Fragment {
  private RxEvent visibilityGained = new RxEvent();
  private RxEvent visibilityLost = new RxEvent();
  private BehaviorSubject<Boolean> focused = BehaviorSubject.create();
  private BehaviorSubject<Boolean> uiStarted = BehaviorSubject.create();
  protected BehaviorSubject<Integer> drawerState = BehaviorSubject.create();
  private BehaviorSubject<Optional<View>> view = BehaviorSubject.create();
  private RxEvent viewDestroyed = new RxEvent();

  public static interface Env {
    Observable<Integer> watchDrawerState();
  }

  public static interface EnvProvider {
    Env getPanesToolEnv();
  }

  protected PanesToolFragment() {
    // Only treat as visible (and therefore connect the camera) when we are both focused and
    // resumed.
    Observable.combineLatest(
            this.focused,
            uiStarted,
            this.drawerState,
            (focused, resumed, drawerState) ->
                focused && resumed && drawerState != PanesBottomSheetBehavior.STATE_COLLAPSED)
        .distinctUntilChanged()
        .subscribe(
            hasBecomeVisible -> {
              if (hasBecomeVisible) {
                visibilityGained.onHappened();
              } else {
                visibilityLost.onHappened();
              }
            });
  }

  @Nullable
  @Override
  public final View onCreateView(
      LayoutInflater inflater, @Nullable ViewGroup container, Bundle savedInstanceState) {
    EnvProvider provider = (EnvProvider) container.getContext();
    provider
        .getPanesToolEnv()
        .watchDrawerState()
        .takeUntil(viewDestroyed.happens())
        .subscribe(drawerState::onNext);

    View view = onCreatePanesView(inflater, container, savedInstanceState);
    this.view.onNext(Optional.of(view));
    return view;
  }

  protected abstract View onCreatePanesView(
      LayoutInflater inflater, @Nullable ViewGroup container, Bundle savedInstanceState);

  @Override
  public final void onDestroyView() {
    view.onNext(Optional.absent());
    viewDestroyed.onHappened();
    onDestroyPanesView();
    super.onDestroyView();
  }

  /** Fragment-specific view destruction logic, if any. Should _not_ call super.onDestroyView(); */
  protected void onDestroyPanesView() {
    // do nothing, but subclasses can override.
  }

  public Maybe<View> whenNextView() {
    return view.filter(Optional::isPresent).map(Optional::get).firstElement();
  }

  // TODO: extract this pattern of fragment listeners
  @Override
  public void onAttach(Context context) {
    super.onAttach(context);
    panesOnAttach(context);
  }

  @Override
  public void onAttach(Activity activity) {
    super.onAttach(activity);
    panesOnAttach(activity);
  }

  protected void panesOnAttach(Context context) {}

  protected Observable<Object> whenVisibilityGained() {
    return visibilityGained.happens();
  }

  protected Observable<Object> whenVisibilityLost() {
    return visibilityLost.happens();
  }

  protected Observable<Integer> watchDrawerState() {
    return drawerState;
  }

  public void onGainedFocus(Activity activity) {
    focused.onNext(true);
  }

  public void onLosingFocus() {
    focused.onNext(false);
  }

  @Override
  public void onStart() {
    super.onStart();

    if (isMultiWindowEnabled()) {
      uiStarted.onNext(true);
    }
  }

  @Override
  public void onResume() {
    super.onResume();

    if (!isMultiWindowEnabled()) {
      uiStarted.onNext(true);
    }
  }

  @Override
  public void onPause() {
    // TODO: can we safely use onStop to shut down observing on pre-Nougat?
    //       See discussion at b/34368790
    if (!isMultiWindowEnabled()) {
      uiStarted.onNext(false);
    }
    super.onPause();
  }

  @Override
  public void onStop() {
    if (isMultiWindowEnabled()) {
      uiStarted.onNext(false);
    }
    super.onStop();
  }

  private boolean isMultiWindowEnabled() {
    return MultiWindowUtils.isMultiWindowEnabled(getActivity());
  }
}
