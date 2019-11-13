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
package com.google.android.apps.forscience.whistlepunk.review;

import static org.junit.Assert.assertEquals;

import com.google.android.apps.forscience.whistlepunk.TestConsumers;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

@RunWith(RobolectricTestRunner.class)
public class ExportStreamConsumerTest {
  @Test
  public void dontStartAtZero() throws IOException {
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    OutputStreamWriter writer = new OutputStreamWriter(baos);
    ExportStreamConsumer consumer =
        new ExportStreamConsumer(writer, false, TestConsumers.expectingSuccess());

    consumer.addData(1000, 10);
    consumer.addData(2000, 20);
    writer.close();
    assertEquals("1000,10.0\n2000,20.0\n", baos.toString());
  }

  @Test
  public void startAtZero() throws IOException {
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    OutputStreamWriter writer = new OutputStreamWriter(baos);
    ExportStreamConsumer consumer =
        new ExportStreamConsumer(writer, true, TestConsumers.expectingSuccess());
    consumer.addData(1000, 10);
    consumer.addData(2000, 20);
    writer.close();
    assertEquals("0,10.0\n1000,20.0\n", baos.toString());
  }
}
