/*
 *  Copyright 2018 Google Inc. All Rights Reserved.
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

package com.google.android.apps.forscience.whistlepunk.filemetadata;

import com.google.android.apps.forscience.whistlepunk.metadata.GoosciExperiment.ChangedElement.ElementType;
import com.google.android.apps.forscience.whistlepunk.metadata.nano.GoosciExperiment;
import java.util.UUID;

/** A wrapper class for a Change proto. */
public class Change {
  private static final String TAG = "Change";

  private GoosciExperiment.Change changeProto;

  // Short cut to create a Add type Change.
  public static Change newAddTypeChange(ElementType elementType, String elementId) {
    return new Change(GoosciExperiment.Change.ChangeType.ADD, elementType, elementId);
  }

  // Short cut to create a Delete type Change.
  public static Change newDeleteTypeChange(ElementType elementType, String elementId) {
    return new Change(GoosciExperiment.Change.ChangeType.DELETE, elementType, elementId);
  }

  // Short cut to create a Modify type Change.
  public static Change newModifyTypeChange(ElementType elementType, String elementId) {
    return new Change(GoosciExperiment.Change.ChangeType.MODIFY, elementType, elementId);
  }

  public static Change fromProto(GoosciExperiment.Change proto) {
    return new Change(proto);
  }

  public Change(int changeType, ElementType elementType, String elementId) {
    this(changeType, new GoosciExperiment.ChangedElement(), UUID.randomUUID().toString());
    changeProto.changedData.type = elementType;
    changeProto.changedData.id = elementId;
  }

  public Change(int changeType, GoosciExperiment.ChangedElement element) {
    this(changeType, element, UUID.randomUUID().toString());
  }

  private Change(GoosciExperiment.Change proto) {
    changeProto = proto;
  }

  private Change(int changeType, GoosciExperiment.ChangedElement element, String changeId) {
    changeProto = new GoosciExperiment.Change();
    changeProto.changedData = element;
    changeProto.type = changeType;
    changeProto.changeId = changeId;
  }

  public Change() {
    changeProto = new GoosciExperiment.Change();
  }

  public GoosciExperiment.Change getChangeProto() {
    return changeProto;
  }

  public String getChangedElementId() {
    return changeProto.changedData.id;
  }

  public ElementType getChangedElementType() {
    return changeProto.changedData.type;
  }

  public String getChangeId() {
    return changeProto.changeId;
  }

  public int getChangeType() {
    return changeProto.type;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null) {
      return false;
    }
    if (getClass() != o.getClass()) {
      return false;
    }
    Change c = (Change) o;
    return getChangeId().equals(c.getChangeId());
  }

  @Override
  public int hashCode() {
    return getChangeId().hashCode();
  }
}
