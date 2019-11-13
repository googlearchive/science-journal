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

import com.google.android.apps.forscience.whistlepunk.metadata.GoosciLabelValue;
import com.google.android.apps.forscience.whistlepunk.metadata.GoosciLabelValue.LabelValue.ValueType;
import com.google.common.base.Preconditions;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/** POJO object for LabelValue proto. */
public class LabelValuePojo {
  private final Map<String, String> data = new HashMap<>();
  private ValueType type;

  public LabelValuePojo() {
  }

  public LabelValuePojo(GoosciLabelValue.LabelValue proto) {
    data.putAll(proto.getDataMap());
    type = proto.hasType() ? proto.getType() : null;
  }

  public GoosciLabelValue.LabelValue toProto() {
    GoosciLabelValue.LabelValue.Builder builder =
        GoosciLabelValue.LabelValue.newBuilder().putAllData(data);
    if (type != null) {
      builder.setType(type);
    }
    return builder.build();
  }

  public void putData(String key, String value) {
    data.put(key, value);
  }

  public String getDataOrThrow(String key) {
    Preconditions.checkArgument(data.containsKey(key));
    return data.get(key);
  }

  public String getDataOrDefault(String key, String defaultValue) {
    if (data.containsKey(key)) {
      return data.get(key);
    }
    return defaultValue;
  }

  public void setType(ValueType type) {
    this.type = type;
  }

  public ValueType getType() {
    return (type != null) ? type : ValueType.TEXT;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof LabelValuePojo)) {
      return false;
    }

    LabelValuePojo labelValuePojo = (LabelValuePojo) o;

    return Objects.equals(type, labelValuePojo.type) && Objects.equals(data, labelValuePojo.data);
  }

  @Override
  public int hashCode() {
    return Objects.hash(type, data);
  }
}
