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
package com.google.android.apps.forscience.whistlepunk.actionarea;

import com.google.android.apps.forscience.whistlepunk.R;

/**
 * ActionAreaItem is a simple class to hold an icon (R.drawable) and text (R.string) to be displayed
 * by {@link ActionAreaView}.
 *
 * <p>All items should be created at the top of this class to maximize code reuse and readability.
 */
public final class ActionAreaItem {
  public static final ActionAreaItem NOTE =
      new ActionAreaItem(R.string.text_note, R.drawable.ic_text_with_circle);
  public static final ActionAreaItem SENSOR =
      new ActionAreaItem(R.string.sensor_note, R.drawable.ic_sensor_with_circle);
  public static final ActionAreaItem CAMERA =
      new ActionAreaItem(R.string.camera_note, R.drawable.ic_camera);
  public static final ActionAreaItem GALLERY =
      new ActionAreaItem(R.string.gallery_note, R.drawable.ic_gallery_with_circle);
  public static final ActionAreaItem MORE =
      new ActionAreaItem(R.string.more, R.drawable.ic_more);
  public static final ActionAreaItem SNAPSHOT
      = new ActionAreaItem(R.string.snapshot, R.drawable.ic_snap_shot);
  public static final ActionAreaItem ADD_SENSOR =
      new ActionAreaItem(R.string.add_sensor_card, R.drawable.ic_add_sensor);

  private final int contentDescriptionId;
  private final int iconId;

  private ActionAreaItem(int contentDescriptionId, int iconId) {
    this.contentDescriptionId = contentDescriptionId;
    this.iconId = iconId;
  }

  /**
   * Should only be called by {@link ActionAreaView.ActionAreaView}.
   *
   * @return the id of the text to be used for the item
   */
  int getContentDescriptionId() {
    return contentDescriptionId;
  }

  /**
   * Should only be called by {@link ActionAreaView.ActionAreaItemView}.
   *
   * @return the id of the icon to be used for the item
   */
  int getIconId() {
    return iconId;
  }
}
