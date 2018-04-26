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

package com.google.android.apps.forscience.whistlepunk.audio;

import static org.junit.Assert.*;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

@RunWith(RobolectricTestRunner.class)
public class AudioAnalyzerTest {
  private static final int SAMPLE_RATE_IN_HZ = 44100;

  private final ClassLoader classLoader = getClass().getClassLoader();
  private final AudioAnalyzer audioAnalyzer = new AudioAnalyzer(SAMPLE_RATE_IN_HZ);

  private short[] readSamples(String filename) throws Exception {
    List<Short> list = new ArrayList<>();
    boolean foundNonZero = false;
    InputStream inputStream = classLoader.getResourceAsStream(filename);
    try (BufferedReader br = new BufferedReader(new InputStreamReader(inputStream))) {
      while (true) {
        try {
          String s = br.readLine();
          if (!s.isEmpty()) {
            short n = Short.parseShort(s);
            if (n != 0) {
              foundNonZero = true;
            }
            if (foundNonZero) {
              list.add(n);
            }
          }
        } catch (Exception e) {
          break;
        }
      }
    }
    short[] samples = new short[list.size()];
    for (int i = 0; i < list.size(); i++) {
      samples[i] = list.get(i);
    }
    return samples;
  }

  private void testDetectFundamentalFrequency(String sampleFilename, double expectedFrequency)
      throws Exception {
    short[] samples = readSamples(sampleFilename);
    Double actualFrequency = audioAnalyzer.detectFundamentalFrequency(samples);
    assertEquals(expectedFrequency, actualFrequency, expectedFrequency * 0.01);
  }

  @Test
  public void cokeBottle() throws Exception {
    testDetectFundamentalFrequency("coke_bottle_325.samples", 325);
  }

  @Test
  public void guitar() throws Exception {
    testDetectFundamentalFrequency("guitar_E_82_4069.samples", 82.4069);
    testDetectFundamentalFrequency("guitar_A_110_000.samples", 110.000);
    testDetectFundamentalFrequency("guitar_D_146_832.samples", 146.832);
    testDetectFundamentalFrequency("guitar_G_195_998.samples", 195.998);
    testDetectFundamentalFrequency("guitar_B_246_942.samples", 246.942);
    testDetectFundamentalFrequency("guitar_E_329_628.samples", 329.628);
  }

  @Test
  public void melodica() throws Exception {
    testDetectFundamentalFrequency("melodica_f3_174_614.samples", 174.614);
    testDetectFundamentalFrequency("melodica_g3_195_998.samples", 195.998);
    testDetectFundamentalFrequency("melodica_a3_220_000.samples", 220.000);
    testDetectFundamentalFrequency("melodica_b3_246_942.samples", 246.942);
    testDetectFundamentalFrequency("melodica_c4_261_626.samples", 261.626);
    testDetectFundamentalFrequency("melodica_d4_293_665.samples", 293.665);
    testDetectFundamentalFrequency("melodica_e4_329_628.samples", 329.628);
    testDetectFundamentalFrequency("melodica_f4_349_228.samples", 349.228);
    testDetectFundamentalFrequency("melodica_g4_391_995.samples", 391.995);
    testDetectFundamentalFrequency("melodica_a4_440_000.samples", 440.000);
    testDetectFundamentalFrequency("melodica_b4_493_883.samples", 493.883);
    testDetectFundamentalFrequency("melodica_c5_523_251.samples", 523.251);
    testDetectFundamentalFrequency("melodica_d5_587_330.samples", 587.330);
    testDetectFundamentalFrequency("melodica_e5_659_255.samples", 659.255);
    testDetectFundamentalFrequency("melodica_f5_698_456.samples", 698.456);
    testDetectFundamentalFrequency("melodica_g5_783_991.samples", 783.991);
    testDetectFundamentalFrequency("melodica_a5_880_000.samples", 880.000);
    testDetectFundamentalFrequency("melodica_b5_987_767.samples", 987.767);
    testDetectFundamentalFrequency("melodica_c6_1046_50.samples", 1046.50);
  }

  @Test
  public void glass() throws Exception {
    testDetectFundamentalFrequency("pint_glass_1797.samples", 1797);
  }

  @Test
  public void synthClarinet() throws Exception {
    testDetectFundamentalFrequency("synth_clarinet_b2_123_471.samples", 123.471);
    testDetectFundamentalFrequency("synth_clarinet_b3_246_942.samples", 246.942);
    testDetectFundamentalFrequency("synth_clarinet_b4_493_883.samples", 493.883);
    testDetectFundamentalFrequency("synth_clarinet_b5_987_767.samples", 987.767);
  }

  @Test
  public void synthGuitar() throws Exception {
    testDetectFundamentalFrequency("synth_guitar_b2_123_471.samples", 123.471);
    testDetectFundamentalFrequency("synth_guitar_b3_246_942.samples", 246.942);
    testDetectFundamentalFrequency("synth_guitar_b4_493_883.samples", 493.883);
  }

  @Test
  public void synthPiano() throws Exception {
    testDetectFundamentalFrequency("synth_piano_b2_123_471.samples", 123.471);
    testDetectFundamentalFrequency("synth_piano_b3_246_942.samples", 246.942);
    testDetectFundamentalFrequency("synth_piano_b4_493_883.samples", 493.883);
    testDetectFundamentalFrequency("synth_piano_b5_987_767.samples", 987.767);
  }

  @Test
  public void tone() throws Exception {
    testDetectFundamentalFrequency("tone_b3_246_942.samples", 246.942);
    testDetectFundamentalFrequency("tone_b4_493_883.samples", 493.883);
    testDetectFundamentalFrequency("tone_b5_987_767.samples", 987.767);
  }

  @Test
  public void xylophone() throws Exception {
    testDetectFundamentalFrequency("xylophone_979.samples", 979);
    testDetectFundamentalFrequency("xylophone_1081.samples", 1081);
    testDetectFundamentalFrequency("xylophone_1250.samples", 1250);
    testDetectFundamentalFrequency("xylophone_1295.samples", 1295);
    testDetectFundamentalFrequency("xylophone_1466.samples", 1466);
    testDetectFundamentalFrequency("xylophone_1594.samples", 1594);
    testDetectFundamentalFrequency("xylophone_1802.samples", 1802);
    testDetectFundamentalFrequency("xylophone_1950.samples", 1950);
  }
}
