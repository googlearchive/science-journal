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

import com.google.android.apps.forscience.whistlepunk.metadata.GoosciExperiment;
import com.google.android.apps.forscience.whistlepunk.metadata.GoosciExperiment.Change.ChangeType;
import com.google.android.apps.forscience.whistlepunk.metadata.GoosciExperiment.ChangedElement;
import com.google.android.apps.forscience.whistlepunk.metadata.GoosciExperiment.ChangedElement.ElementType;
import java.util.UUID;

/** A wrapper class for a Change proto. */
public class Change {
  private static final String TAG = "Change";

  private GoosciExperiment.Change changeProto;

  // Short cut to create a Add type Change.
  public static Change newAddTypeChange(ElementType elementType, String elementId) {
    return new Change(ChangeType.ADD, elementType, elementId);
  }

  // Short cut to create a Delete type Change.
  public static Change newDeleteTypeChange(ElementType elementType, String elementId) {
    return new Change(ChangeType.DELETE, elementType, elementId);
  }

  // Short cut to create a Modify type Change.
  public static Change newModifyTypeChange(ElementType elementType, String elementId) {
    return new Change(ChangeType.MODIFY, elementType, elementId);
  }

  public static Change fromProto(GoosciExperiment.Change proto) {
    return new Change(proto);
  }

  public Change(ChangeType changeType, ElementType elementType, String elementId) {
    this(changeType, ChangedElement.getDefaultInstance(), UUID.randomUUID().toString());
    ChangedElement newChangedData =
        changeProto.getChangedData().toBuilder().setType(elementType).setId(elementId).build();
    changeProto = changeProto.toBuilder()
        .setChangedData(newChangedData)
        .build();
  }

  public Change(ChangeType changeType, ChangedElement element) {
    this(changeType, element, UUID.randomUUID().toString());
  }

  private Change(GoosciExperiment.Change proto) {
    changeProto = proto;
  }

  private Change(ChangeType changeType, ChangedElement element, String changeId) {
    changeProto =
        GoosciExperiment.Change.newBuilder()
            .setChangedData(element)
            .setType(changeType)
            .setChangeId(changeId)
            .build();
  }

  public Change() {
    changeProto = GoosciExperiment.Change.getDefaultInstance();
  }

  public GoosciExperiment.Change getChangeProto() {
    return changeProto;
  }

  public String getChangedElementId() {
    return changeProto.getChangedData().getId();
  }

  public ElementType getChangedElementType() {
    return changeProto.getChangedData().getType();
  }

  public String getChangeId() {
    return changeProto.getChangeId();
  }

  public ChangeType getChangeType() {
    return changeProto.getType();
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
