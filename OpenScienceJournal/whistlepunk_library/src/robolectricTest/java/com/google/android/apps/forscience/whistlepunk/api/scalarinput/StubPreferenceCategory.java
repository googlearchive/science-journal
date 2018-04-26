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
package com.google.android.apps.forscience.whistlepunk.api.scalarinput;

import android.content.Context;
import android.preference.Preference;
import android.preference.PreferenceCategory;
import java.util.ArrayList;
import java.util.List;

public class StubPreferenceCategory extends PreferenceCategory {
  public List<Preference> prefs = new ArrayList<>();

  public StubPreferenceCategory(Context context) {
    super(context);
  }

  @Override
  public boolean addPreference(Preference preference) {
    prefs.add(preference);
    return true;
  }

  @Override
  public Preference findPreference(CharSequence key) {
    for (Preference pref : prefs) {
      if (pref.getKey().equals(key)) {
        return pref;
      }
    }
    return null;
  }

  @Override
  public boolean removePreference(Preference preference) {
    return prefs.remove(preference);
  }

  @Override
  public void removeAll() {
    prefs.clear();
  }
}
