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

package com.google.android.apps.forscience.whistlepunk.audio;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.media.AudioRecord;
import java.util.concurrent.CompletableFuture;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(shadows = {ShadowAudioRecord.class})
public class AudioSourceTest {
  // TODO(nmulcahey) make this Shadow work within the test methods
  private AudioSource audioSource;

  @Test
  public void testAudioRecordErrorBadValue() {
    ShadowAudioRecord.setMinBufferSize(AudioRecord.ERROR_BAD_VALUE);
    audioSource = new AudioSource();
    assertFalse(audioSource.registerAudioReceiver(buffer -> {}));
    assertEquals(audioSource.getRecievers().size(), 0);
    assertFalse(audioSource.isRunning());
  }

  @Test
  public void testRegisterAudioReceiverOnce() {
    ShadowAudioRecord.setMinBufferSize(2);
    audioSource = new AudioSource();
    AudioSource.AudioReceiver audioReceiver = buffer -> {};
    assertTrue(audioSource.registerAudioReceiver(audioReceiver));
    assertFalse(audioSource.registerAudioReceiver(audioReceiver));
    assertEquals(audioSource.getRecievers().size(), 1);
    assertTrue(audioSource.isRunning());
  }

  @Test
  public void testRegisterNullAudioReceiver() {
    ShadowAudioRecord.setMinBufferSize(2);
    audioSource = new AudioSource();
    assertFalse(audioSource.registerAudioReceiver(null));
    assertEquals(audioSource.getRecievers().size(), 0);
    assertFalse(audioSource.isRunning());
  }

  @Test
  public void testUnregisterOnlyAudioReceiver() {
    ShadowAudioRecord.setMinBufferSize(2);
    audioSource = new AudioSource();
    AudioSource.AudioReceiver audioReceiver = buffer -> {};
    assertTrue(audioSource.registerAudioReceiver(audioReceiver));
    assertEquals(audioSource.getRecievers().size(), 1);
    assertTrue(audioSource.isRunning());

    audioSource.unregisterAudioReceiver(audioReceiver);
    assertEquals(audioSource.getRecievers().size(), 0);
    assertFalse(audioSource.isRunning());
  }

  @Test
  public void testUnregisteringOneOfMultipleAudioReceivers() {
    ShadowAudioRecord.setMinBufferSize(2);
    audioSource = new AudioSource();
    AudioSource.AudioReceiver audioReceiver = buffer -> {};
    assertTrue(audioSource.registerAudioReceiver(audioReceiver));
    assertEquals(audioSource.getRecievers().size(), 1);
    assertTrue(audioSource.isRunning());

    AudioSource.AudioReceiver otherAudioReceiver = buffer -> {};
    assertTrue(audioSource.registerAudioReceiver(otherAudioReceiver));
    assertEquals(audioSource.getRecievers().size(), 2);
    assertTrue(audioSource.isRunning());

    audioSource.unregisterAudioReceiver(audioReceiver);
    assertEquals(audioSource.getRecievers().size(), 1);
    assertTrue(audioSource.isRunning());
  }

  @Test
  public void testRegisterAudioReceiverStartFailedUninitialized() {
    ShadowAudioRecord.setMinBufferSize(2);
    ShadowAudioRecord.setInitializationFailed();
    audioSource = new AudioSource();
    AudioSource.AudioReceiver audioReceiver = buffer -> {};
    assertFalse(audioSource.registerAudioReceiver(audioReceiver));
    assertEquals(audioSource.getRecievers().size(), 0);
    assertFalse(audioSource.isRunning());
  }

  @Test
  public void testRegisterAudioReceiverStartFailedNotRecording() {
    ShadowAudioRecord.setMinBufferSize(2);
    ShadowAudioRecord.setRecordingStartFailed();
    audioSource = new AudioSource();
    AudioSource.AudioReceiver audioReceiver = buffer -> {};
    assertFalse(audioSource.registerAudioReceiver(audioReceiver));
    assertEquals(audioSource.getRecievers().size(), 0);
    assertFalse(audioSource.isRunning());
  }

  @Test
  public void testOnReceiveAudioData() {
    CompletableFuture<short[]> future = new CompletableFuture<>();

    ShadowAudioRecord.setMinBufferSize(20);
    ShadowAudioRecord.setAudioData(new short[] {1, 2, 3, 4, 5, 6, 7, 8, 9, 0});
    audioSource = new AudioSource();

    AudioSource.AudioReceiver audioReceiver = future::complete;
    assertTrue(audioSource.registerAudioReceiver(audioReceiver));
    assertEquals(audioSource.getRecievers().size(), 1);
    assertTrue(audioSource.isRunning());

    assertArrayEquals(new short[] {1, 2, 3, 4, 5, 6, 7, 8, 9, 0}, future.join());
  }

  @Test
  public void testOnReceiveAudioDataIgnoreLeadingZeroes() {
    CompletableFuture<short[]> future = new CompletableFuture<>();

    ShadowAudioRecord.setMinBufferSize(20);
    ShadowAudioRecord.setAudioData(
        new short[] {0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 0});
    audioSource = new AudioSource();

    AudioSource.AudioReceiver audioReceiver = future::complete;
    assertTrue(audioSource.registerAudioReceiver(audioReceiver));
    assertEquals(audioSource.getRecievers().size(), 1);
    assertTrue(audioSource.isRunning());

    assertArrayEquals(new short[] {1, 2, 3, 4, 5, 6, 7, 8, 9, 0}, future.join());
  }

  @Test
  public void testOnReceiveAudioDataIgnorePartialLeadingZeroes() {
    CompletableFuture<short[]> future = new CompletableFuture<>();

    ShadowAudioRecord.setMinBufferSize(20);
    ShadowAudioRecord.setAudioData(
        new short[] {0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 0});
    audioSource = new AudioSource();

    AudioSource.AudioReceiver audioReceiver = future::complete;
    assertTrue(audioSource.registerAudioReceiver(audioReceiver));
    assertEquals(audioSource.getRecievers().size(), 1);
    assertTrue(audioSource.isRunning());

    assertArrayEquals(new short[] {1, 2, 3, 4, 5, 6, 7, 8, 9, 0}, future.join());
  }

  @Test
  public void testOnReceiveAudioDataMultipleReceivers() {
    CompletableFuture<short[]> future = new CompletableFuture<>();
    CompletableFuture<short[]> otherFuture = new CompletableFuture<>();

    ShadowAudioRecord.setMinBufferSize(20);
    audioSource = new AudioSource();

    AudioSource.AudioReceiver audioReceiver = future::complete;
    assertTrue(audioSource.registerAudioReceiver(audioReceiver));
    assertEquals(audioSource.getRecievers().size(), 1);
    assertTrue(audioSource.isRunning());

    AudioSource.AudioReceiver otherAudioReceiver = otherFuture::complete;
    assertTrue(audioSource.registerAudioReceiver(otherAudioReceiver));
    assertEquals(audioSource.getRecievers().size(), 2);
    assertTrue(audioSource.isRunning());

    ShadowAudioRecord.setAudioData(new short[] {1, 2, 3, 4, 5, 6, 7, 8, 9, 0});

    assertArrayEquals(new short[] {1, 2, 3, 4, 5, 6, 7, 8, 9, 0}, future.join());
    assertArrayEquals(new short[] {1, 2, 3, 4, 5, 6, 7, 8, 9, 0}, otherFuture.join());
  }

  @After
  public void cleanUp() {
    ShadowAudioRecord.resetState();
    if (audioSource != null) {
      audioSource.unregisterAllAudioReceivers();
      audioSource = null;
    }
  }
}
