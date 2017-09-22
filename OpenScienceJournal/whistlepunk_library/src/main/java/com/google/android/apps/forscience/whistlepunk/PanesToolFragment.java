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

import android.app.Fragment;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.google.common.base.Optional;

import io.reactivex.Maybe;
import io.reactivex.subjects.BehaviorSubject;

public abstract class PanesToolFragment extends Fragment {
    private BehaviorSubject<Optional<View>> mView = BehaviorSubject.create();

    @Nullable
    @Override
    public final View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container,
            Bundle savedInstanceState) {
        View view = onCreatePanesView(inflater, container, savedInstanceState);
        mView.onNext(Optional.of(view));
        return view;
    }

    protected abstract View onCreatePanesView(LayoutInflater inflater,
            @Nullable ViewGroup container, Bundle savedInstanceState);

    @Override
    public final void onDestroyView() {
        mView.onNext(Optional.absent());
        onDestroyPanesView();
        super.onDestroyView();
    }

    /**
     * Fragment-specific view destruction logic, if any.  Should _not_ call super.onDestroyView();
     */
    protected void onDestroyPanesView() {
        // do nothing, but subclasses can override.
    }

    public Maybe<View> whenNextView() {
        return mView.filter(Optional::isPresent).map(Optional::get).firstElement();
    }
}
