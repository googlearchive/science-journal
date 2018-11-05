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

import static org.junit.Assert.assertEquals;

import com.google.android.apps.forscience.whistlepunk.metadata.GoosciExperiment.Change.ChangeType;
import com.google.android.apps.forscience.whistlepunk.metadata.GoosciExperiment.ChangedElement.ElementType;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

/** Tests for the Change class */
@RunWith(RobolectricTestRunner.class)
public class ChangeTest {

  @Test
  public void testAddChange() {
    Change change = Change.newAddTypeChange(ElementType.NOTE, "id");
    assertEquals(ChangeType.ADD, change.getChangeType());
  }

  @Test
  public void testDeleteChange() {
    Change change = Change.newDeleteTypeChange(ElementType.NOTE, "id");
    assertEquals(ChangeType.DELETE, change.getChangeType());
  }

  @Test
  public void testModifyChange() {
    Change change = Change.newModifyTypeChange(ElementType.NOTE, "id");
    assertEquals(ChangeType.MODIFY, change.getChangeType());
  }

  @Test
  public void testChangedElementCorrect() {
    Change change = Change.newModifyTypeChange(ElementType.NOTE, "id");
    assertEquals("id", change.getChangedElementId());
    assertEquals(ElementType.NOTE, change.getChangedElementType());
  }
}
