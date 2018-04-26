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

  private static int mMinBufferSize;
  private static int mState = AudioRecord.STATE_INITIALIZED;
  private static int mRecordingState = AudioRecord.RECORDSTATE_RECORDING;

  private static List<Short> mAudioData = new ArrayList<>();

  private static final Object mAudioDataLock = new Object();

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
    return mMinBufferSize;
  }

  @Implementation
  protected void startRecording() throws IllegalStateException {}

  @Implementation
  protected int getState() {
    return mState;
  }

  @Implementation
  protected int getRecordingState() {
    return mRecordingState;
  }

  @Implementation
  protected int read(short[] audioData, int offsetInShorts, int sizeInShorts) {
    synchronized (mAudioDataLock) {
      if (mAudioData.size() > 0) {
        System.arraycopy(
            Shorts.toArray(
                mAudioData.subList(
                    0, sizeInShorts > mAudioData.size() ? mAudioData.size() : sizeInShorts)),
            0,
            audioData,
            offsetInShorts,
            sizeInShorts);
        if (mAudioData.size() > 10) {
          mAudioData = mAudioData.subList(sizeInShorts, mAudioData.size());
        } else {
          mAudioData.clear();
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
    mMinBufferSize = 0;
    mState = AudioRecord.STATE_INITIALIZED;
    mRecordingState = AudioRecord.RECORDSTATE_RECORDING;
    synchronized (mAudioDataLock) {
      mAudioData.clear();
    }
  }

  public static void setMinBufferSize(int minBufferSize) {
    mMinBufferSize = minBufferSize;
  }

  public static void setInitializationFailed() {
    mState = AudioRecord.STATE_UNINITIALIZED;
  }

  public static void setRecordingStartFailed() {
    mRecordingState = AudioRecord.RECORDSTATE_STOPPED;
  }

  public static void setAudioData(short[] audioData) {
    synchronized (mAudioDataLock) {
      mAudioData.addAll(Shorts.asList(audioData));
    }
  }
}
