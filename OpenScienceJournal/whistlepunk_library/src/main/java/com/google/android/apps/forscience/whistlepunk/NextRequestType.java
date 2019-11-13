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

import com.google.common.collect.Range;

// TODO(saff): convert to non-enum?
enum NextRequestType {
  NONE,
  FIRST,
  NEXT_LOWER,
  NEXT_HIGHER;

  static NextRequestType compute(Range<Long> alreadyRequested, long minWanted, long maxWanted) {
    if (maxWanted < minWanted) {
      return NONE;
    } else if (alreadyRequested == null) {
      return FIRST;
    } else if (alreadyRequested.hasLowerBound() && minWanted < alreadyRequested.lowerEndpoint()) {
      return NEXT_LOWER;
    } else if (alreadyRequested.hasUpperBound() && maxWanted > alreadyRequested.upperEndpoint()) {
      return NEXT_HIGHER;
    } else {
      return NONE;
    }
  }
}
