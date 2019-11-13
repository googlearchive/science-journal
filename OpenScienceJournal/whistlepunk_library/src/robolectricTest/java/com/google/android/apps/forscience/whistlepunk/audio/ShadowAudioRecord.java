package com.google.android.apps.forscience.whistlepunk.audio;

import android.media.AudioRecord;
import com.google.common.primitives.Shorts;
import java.util.ArrayList;
import java.util.List;
import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;

@SuppressWarnings("UnusedDeclaration")
@Implements(AudioRecord.class)
public class ShadowAudioRecord {

  private static int minBufferSize;
  private static int state = AudioRecord.STATE_INITIALIZED;
  private static int recordingState = AudioRecord.RECORDSTATE_RECORDING;

  private static List<Short> audioData = new ArrayList<>();

  private static final Object audioDataLock = new Object();

  public ShadowAudioRecord() {}

  @Implementation
  protected void __constructor__(
      int audioSource,
      int sampleRateInHz,
      int channelConfig,
      int audioFormat,
      int bufferSizeInBytes)
      throws IllegalArgumentException {}

  @Implementation
  protected static int getMinBufferSize(int sampleRateInHz, int channelConfig, int audioFormat) {
    return minBufferSize;
  }

  @Implementation
  protected void startRecording() throws IllegalStateException {}

  @Implementation
  protected int getState() {
    return state;
  }

  @Implementation
  protected int getRecordingState() {
    return recordingState;
  }

  @Implementation
  protected int read(short[] audioData, int offsetInShorts, int sizeInShorts) {
    synchronized (audioDataLock) {
      if (ShadowAudioRecord.audioData.size() > 0) {
        System.arraycopy(
            Shorts.toArray(
                ShadowAudioRecord.audioData.subList(
                    0,
                    sizeInShorts > ShadowAudioRecord.audioData.size()
                        ? ShadowAudioRecord.audioData.size()
                        : sizeInShorts)),
            0,
            audioData,
            offsetInShorts,
            sizeInShorts);
        if (ShadowAudioRecord.audioData.size() > 10) {
          ShadowAudioRecord.audioData =
              ShadowAudioRecord.audioData.subList(sizeInShorts, ShadowAudioRecord.audioData.size());
        } else {
          ShadowAudioRecord.audioData.clear();
        }
        return sizeInShorts;
      }
      return 0;
    }
  }

  @Implementation
  protected void stop() throws IllegalStateException {}

  @Implementation
  protected void release() {}

  public static void resetState() {
    minBufferSize = 0;
    state = AudioRecord.STATE_INITIALIZED;
    recordingState = AudioRecord.RECORDSTATE_RECORDING;
    synchronized (audioDataLock) {
      audioData.clear();
    }
  }

  public static void setMinBufferSize(int minBufferSize) {
    ShadowAudioRecord.minBufferSize = minBufferSize;
  }

  public static void setInitializationFailed() {
    state = AudioRecord.STATE_UNINITIALIZED;
  }

  public static void setRecordingStartFailed() {
    recordingState = AudioRecord.RECORDSTATE_STOPPED;
  }

  public static void setAudioData(short[] audioData) {
    synchronized (audioDataLock) {
      ShadowAudioRecord.audioData.addAll(Shorts.asList(audioData));
    }
  }
}
