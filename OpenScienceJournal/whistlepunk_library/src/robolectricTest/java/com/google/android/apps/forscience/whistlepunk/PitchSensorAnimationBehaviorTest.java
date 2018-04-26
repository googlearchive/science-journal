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

package com.google.android.apps.forscience.whistlepunk;

import static com.google.android.apps.forscience.whistlepunk.PitchSensorAnimationBehavior.calculateDifference;
import static com.google.android.apps.forscience.whistlepunk.PitchSensorAnimationBehavior.fillNoteLists;
import static com.google.android.apps.forscience.whistlepunk.PitchSensorAnimationBehavior.makeContentDescription;
import static com.google.android.apps.forscience.whistlepunk.PitchSensorAnimationBehavior.noteFrequencies;
import static com.google.android.apps.forscience.whistlepunk.PitchSensorAnimationBehavior.pitchToLevel;
import static org.junit.Assert.assertEquals;

import android.content.Context;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

@RunWith(RobolectricTestRunner.class)
public class PitchSensorAnimationBehaviorTest {
  private String getContentDescriptionForPitch(double detectedPitch) {
    int level = pitchToLevel(detectedPitch);
    double difference = calculateDifference(detectedPitch, level);
    Context context = RuntimeEnvironment.application.getApplicationContext();
    return makeContentDescription(context, level, difference);
  }

  @Test
  public void makeContentDescriptionNotes() throws Exception {
    if (noteFrequencies.isEmpty()) {
      fillNoteLists();
    }
    assertEquals("low pitch", getContentDescriptionForPitch(noteFrequencies.get(0)));
    assertEquals("A, octave 0", getContentDescriptionForPitch(noteFrequencies.get(1)));
    assertEquals("B flat, octave 0", getContentDescriptionForPitch(noteFrequencies.get(2)));
    assertEquals("B, octave 0", getContentDescriptionForPitch(noteFrequencies.get(3)));
    assertEquals("C, octave 1", getContentDescriptionForPitch(noteFrequencies.get(4)));
    assertEquals("C sharp, octave 1", getContentDescriptionForPitch(noteFrequencies.get(5)));
    assertEquals("D, octave 1", getContentDescriptionForPitch(noteFrequencies.get(6)));
    assertEquals("E flat, octave 1", getContentDescriptionForPitch(noteFrequencies.get(7)));
    assertEquals("E, octave 1", getContentDescriptionForPitch(noteFrequencies.get(8)));
    assertEquals("F, octave 1", getContentDescriptionForPitch(noteFrequencies.get(9)));
    assertEquals("F sharp, octave 1", getContentDescriptionForPitch(noteFrequencies.get(10)));
    assertEquals("G, octave 1", getContentDescriptionForPitch(noteFrequencies.get(11)));
    assertEquals("A flat, octave 1", getContentDescriptionForPitch(noteFrequencies.get(12)));
    assertEquals("A, octave 1", getContentDescriptionForPitch(noteFrequencies.get(13)));
    assertEquals("B flat, octave 1", getContentDescriptionForPitch(noteFrequencies.get(14)));
    assertEquals("B, octave 1", getContentDescriptionForPitch(noteFrequencies.get(15)));
    assertEquals("C, octave 2", getContentDescriptionForPitch(noteFrequencies.get(16)));
    assertEquals("C sharp, octave 2", getContentDescriptionForPitch(noteFrequencies.get(17)));
    assertEquals("D, octave 2", getContentDescriptionForPitch(noteFrequencies.get(18)));
    assertEquals("E flat, octave 2", getContentDescriptionForPitch(noteFrequencies.get(19)));
    assertEquals("E, octave 2", getContentDescriptionForPitch(noteFrequencies.get(20)));
    assertEquals("F, octave 2", getContentDescriptionForPitch(noteFrequencies.get(21)));
    assertEquals("F sharp, octave 2", getContentDescriptionForPitch(noteFrequencies.get(22)));
    assertEquals("G, octave 2", getContentDescriptionForPitch(noteFrequencies.get(23)));
    assertEquals("A flat, octave 2", getContentDescriptionForPitch(noteFrequencies.get(24)));
    assertEquals("A, octave 2", getContentDescriptionForPitch(noteFrequencies.get(25)));
    assertEquals("B flat, octave 2", getContentDescriptionForPitch(noteFrequencies.get(26)));
    assertEquals("B, octave 2", getContentDescriptionForPitch(noteFrequencies.get(27)));
    assertEquals("C, octave 3", getContentDescriptionForPitch(noteFrequencies.get(28)));
    assertEquals("C sharp, octave 3", getContentDescriptionForPitch(noteFrequencies.get(29)));
    assertEquals("D, octave 3", getContentDescriptionForPitch(noteFrequencies.get(30)));
    assertEquals("E flat, octave 3", getContentDescriptionForPitch(noteFrequencies.get(31)));
    assertEquals("E, octave 3", getContentDescriptionForPitch(noteFrequencies.get(32)));
    assertEquals("F, octave 3", getContentDescriptionForPitch(noteFrequencies.get(33)));
    assertEquals("F sharp, octave 3", getContentDescriptionForPitch(noteFrequencies.get(34)));
    assertEquals("G, octave 3", getContentDescriptionForPitch(noteFrequencies.get(35)));
    assertEquals("A flat, octave 3", getContentDescriptionForPitch(noteFrequencies.get(36)));
    assertEquals("A, octave 3", getContentDescriptionForPitch(noteFrequencies.get(37)));
    assertEquals("B flat, octave 3", getContentDescriptionForPitch(noteFrequencies.get(38)));
    assertEquals("B, octave 3", getContentDescriptionForPitch(noteFrequencies.get(39)));
    assertEquals("C, octave 4", getContentDescriptionForPitch(noteFrequencies.get(40)));
    assertEquals("C sharp, octave 4", getContentDescriptionForPitch(noteFrequencies.get(41)));
    assertEquals("D, octave 4", getContentDescriptionForPitch(noteFrequencies.get(42)));
    assertEquals("E flat, octave 4", getContentDescriptionForPitch(noteFrequencies.get(43)));
    assertEquals("E, octave 4", getContentDescriptionForPitch(noteFrequencies.get(44)));
    assertEquals("F, octave 4", getContentDescriptionForPitch(noteFrequencies.get(45)));
    assertEquals("F sharp, octave 4", getContentDescriptionForPitch(noteFrequencies.get(46)));
    assertEquals("G, octave 4", getContentDescriptionForPitch(noteFrequencies.get(47)));
    assertEquals("A flat, octave 4", getContentDescriptionForPitch(noteFrequencies.get(48)));
    assertEquals("A, octave 4", getContentDescriptionForPitch(noteFrequencies.get(49)));
    assertEquals("B flat, octave 4", getContentDescriptionForPitch(noteFrequencies.get(50)));
    assertEquals("B, octave 4", getContentDescriptionForPitch(noteFrequencies.get(51)));
    assertEquals("C, octave 5", getContentDescriptionForPitch(noteFrequencies.get(52)));
    assertEquals("C sharp, octave 5", getContentDescriptionForPitch(noteFrequencies.get(53)));
    assertEquals("D, octave 5", getContentDescriptionForPitch(noteFrequencies.get(54)));
    assertEquals("E flat, octave 5", getContentDescriptionForPitch(noteFrequencies.get(55)));
    assertEquals("E, octave 5", getContentDescriptionForPitch(noteFrequencies.get(56)));
    assertEquals("F, octave 5", getContentDescriptionForPitch(noteFrequencies.get(57)));
    assertEquals("F sharp, octave 5", getContentDescriptionForPitch(noteFrequencies.get(58)));
    assertEquals("G, octave 5", getContentDescriptionForPitch(noteFrequencies.get(59)));
    assertEquals("A flat, octave 5", getContentDescriptionForPitch(noteFrequencies.get(60)));
    assertEquals("A, octave 5", getContentDescriptionForPitch(noteFrequencies.get(61)));
    assertEquals("B flat, octave 5", getContentDescriptionForPitch(noteFrequencies.get(62)));
    assertEquals("B, octave 5", getContentDescriptionForPitch(noteFrequencies.get(63)));
    assertEquals("C, octave 6", getContentDescriptionForPitch(noteFrequencies.get(64)));
    assertEquals("C sharp, octave 6", getContentDescriptionForPitch(noteFrequencies.get(65)));
    assertEquals("D, octave 6", getContentDescriptionForPitch(noteFrequencies.get(66)));
    assertEquals("E flat, octave 6", getContentDescriptionForPitch(noteFrequencies.get(67)));
    assertEquals("E, octave 6", getContentDescriptionForPitch(noteFrequencies.get(68)));
    assertEquals("F, octave 6", getContentDescriptionForPitch(noteFrequencies.get(69)));
    assertEquals("F sharp, octave 6", getContentDescriptionForPitch(noteFrequencies.get(70)));
    assertEquals("G, octave 6", getContentDescriptionForPitch(noteFrequencies.get(71)));
    assertEquals("A flat, octave 6", getContentDescriptionForPitch(noteFrequencies.get(72)));
    assertEquals("A, octave 6", getContentDescriptionForPitch(noteFrequencies.get(73)));
    assertEquals("B flat, octave 6", getContentDescriptionForPitch(noteFrequencies.get(74)));
    assertEquals("B, octave 6", getContentDescriptionForPitch(noteFrequencies.get(75)));
    assertEquals("C, octave 7", getContentDescriptionForPitch(noteFrequencies.get(76)));
    assertEquals("C sharp, octave 7", getContentDescriptionForPitch(noteFrequencies.get(77)));
    assertEquals("D, octave 7", getContentDescriptionForPitch(noteFrequencies.get(78)));
    assertEquals("E flat, octave 7", getContentDescriptionForPitch(noteFrequencies.get(79)));
    assertEquals("E, octave 7", getContentDescriptionForPitch(noteFrequencies.get(80)));
    assertEquals("F, octave 7", getContentDescriptionForPitch(noteFrequencies.get(81)));
    assertEquals("F sharp, octave 7", getContentDescriptionForPitch(noteFrequencies.get(82)));
    assertEquals("G, octave 7", getContentDescriptionForPitch(noteFrequencies.get(83)));
    assertEquals("A flat, octave 7", getContentDescriptionForPitch(noteFrequencies.get(84)));
    assertEquals("A, octave 7", getContentDescriptionForPitch(noteFrequencies.get(85)));
    assertEquals("B flat, octave 7", getContentDescriptionForPitch(noteFrequencies.get(86)));
    assertEquals("B, octave 7", getContentDescriptionForPitch(noteFrequencies.get(87)));
    assertEquals("C, octave 8", getContentDescriptionForPitch(noteFrequencies.get(88)));
    assertEquals("high pitch", getContentDescriptionForPitch(noteFrequencies.get(89)));
  }

  @Test
  public void makeContentDescriptionFlatter() throws Exception {
    if (noteFrequencies.isEmpty()) {
      fillNoteLists();
    }
    assertEquals("low pitch", getContentDescriptionForPitch(noteFrequencies.get(0) - 1));
    assertEquals(
        "0.25 half steps flatter than A, octave 0",
        getContentDescriptionForPitch((3 * noteFrequencies.get(1) + noteFrequencies.get(0)) / 4));
    assertEquals(
        "0.25 half steps flatter than B flat, octave 0",
        getContentDescriptionForPitch((3 * noteFrequencies.get(2) + noteFrequencies.get(1)) / 4));
    assertEquals(
        "0.25 half steps flatter than B, octave 0",
        getContentDescriptionForPitch((3 * noteFrequencies.get(3) + noteFrequencies.get(2)) / 4));
    assertEquals(
        "0.25 half steps flatter than C, octave 1",
        getContentDescriptionForPitch((3 * noteFrequencies.get(4) + noteFrequencies.get(3)) / 4));
    assertEquals(
        "0.25 half steps flatter than C sharp, octave 1",
        getContentDescriptionForPitch((3 * noteFrequencies.get(5) + noteFrequencies.get(4)) / 4));
    assertEquals(
        "0.25 half steps flatter than D, octave 1",
        getContentDescriptionForPitch((3 * noteFrequencies.get(6) + noteFrequencies.get(5)) / 4));
    assertEquals(
        "0.25 half steps flatter than E flat, octave 1",
        getContentDescriptionForPitch((3 * noteFrequencies.get(7) + noteFrequencies.get(6)) / 4));
    assertEquals(
        "0.25 half steps flatter than E, octave 1",
        getContentDescriptionForPitch((3 * noteFrequencies.get(8) + noteFrequencies.get(7)) / 4));
    assertEquals(
        "0.25 half steps flatter than F, octave 1",
        getContentDescriptionForPitch((3 * noteFrequencies.get(9) + noteFrequencies.get(8)) / 4));
    assertEquals(
        "0.25 half steps flatter than F sharp, octave 1",
        getContentDescriptionForPitch((3 * noteFrequencies.get(10) + noteFrequencies.get(9)) / 4));
    assertEquals(
        "0.25 half steps flatter than G, octave 1",
        getContentDescriptionForPitch((3 * noteFrequencies.get(11) + noteFrequencies.get(10)) / 4));
    assertEquals(
        "0.25 half steps flatter than A flat, octave 1",
        getContentDescriptionForPitch((3 * noteFrequencies.get(12) + noteFrequencies.get(11)) / 4));
    assertEquals(
        "0.25 half steps flatter than A, octave 1",
        getContentDescriptionForPitch((3 * noteFrequencies.get(13) + noteFrequencies.get(12)) / 4));
    assertEquals(
        "0.25 half steps flatter than B flat, octave 1",
        getContentDescriptionForPitch((3 * noteFrequencies.get(14) + noteFrequencies.get(13)) / 4));
    assertEquals(
        "0.25 half steps flatter than B, octave 1",
        getContentDescriptionForPitch((3 * noteFrequencies.get(15) + noteFrequencies.get(14)) / 4));
    assertEquals(
        "0.25 half steps flatter than C, octave 2",
        getContentDescriptionForPitch((3 * noteFrequencies.get(16) + noteFrequencies.get(15)) / 4));
    assertEquals(
        "0.25 half steps flatter than C sharp, octave 2",
        getContentDescriptionForPitch((3 * noteFrequencies.get(17) + noteFrequencies.get(16)) / 4));
    assertEquals(
        "0.25 half steps flatter than D, octave 2",
        getContentDescriptionForPitch((3 * noteFrequencies.get(18) + noteFrequencies.get(17)) / 4));
    assertEquals(
        "0.25 half steps flatter than E flat, octave 2",
        getContentDescriptionForPitch((3 * noteFrequencies.get(19) + noteFrequencies.get(18)) / 4));
    assertEquals(
        "0.25 half steps flatter than E, octave 2",
        getContentDescriptionForPitch((3 * noteFrequencies.get(20) + noteFrequencies.get(19)) / 4));
    assertEquals(
        "0.25 half steps flatter than F, octave 2",
        getContentDescriptionForPitch((3 * noteFrequencies.get(21) + noteFrequencies.get(20)) / 4));
    assertEquals(
        "0.25 half steps flatter than F sharp, octave 2",
        getContentDescriptionForPitch((3 * noteFrequencies.get(22) + noteFrequencies.get(21)) / 4));
    assertEquals(
        "0.25 half steps flatter than G, octave 2",
        getContentDescriptionForPitch((3 * noteFrequencies.get(23) + noteFrequencies.get(22)) / 4));
    assertEquals(
        "0.25 half steps flatter than A flat, octave 2",
        getContentDescriptionForPitch((3 * noteFrequencies.get(24) + noteFrequencies.get(23)) / 4));
    assertEquals(
        "0.25 half steps flatter than A, octave 2",
        getContentDescriptionForPitch((3 * noteFrequencies.get(25) + noteFrequencies.get(24)) / 4));
    assertEquals(
        "0.25 half steps flatter than B flat, octave 2",
        getContentDescriptionForPitch((3 * noteFrequencies.get(26) + noteFrequencies.get(25)) / 4));
    assertEquals(
        "0.25 half steps flatter than B, octave 2",
        getContentDescriptionForPitch((3 * noteFrequencies.get(27) + noteFrequencies.get(26)) / 4));
    assertEquals(
        "0.25 half steps flatter than C, octave 3",
        getContentDescriptionForPitch((3 * noteFrequencies.get(28) + noteFrequencies.get(27)) / 4));
    assertEquals(
        "0.25 half steps flatter than C sharp, octave 3",
        getContentDescriptionForPitch((3 * noteFrequencies.get(29) + noteFrequencies.get(28)) / 4));
    assertEquals(
        "0.25 half steps flatter than D, octave 3",
        getContentDescriptionForPitch((3 * noteFrequencies.get(30) + noteFrequencies.get(29)) / 4));
    assertEquals(
        "0.25 half steps flatter than E flat, octave 3",
        getContentDescriptionForPitch((3 * noteFrequencies.get(31) + noteFrequencies.get(30)) / 4));
    assertEquals(
        "0.25 half steps flatter than E, octave 3",
        getContentDescriptionForPitch((3 * noteFrequencies.get(32) + noteFrequencies.get(31)) / 4));
    assertEquals(
        "0.25 half steps flatter than F, octave 3",
        getContentDescriptionForPitch((3 * noteFrequencies.get(33) + noteFrequencies.get(32)) / 4));
    assertEquals(
        "0.25 half steps flatter than F sharp, octave 3",
        getContentDescriptionForPitch((3 * noteFrequencies.get(34) + noteFrequencies.get(33)) / 4));
    assertEquals(
        "0.25 half steps flatter than G, octave 3",
        getContentDescriptionForPitch((3 * noteFrequencies.get(35) + noteFrequencies.get(34)) / 4));
    assertEquals(
        "0.25 half steps flatter than A flat, octave 3",
        getContentDescriptionForPitch((3 * noteFrequencies.get(36) + noteFrequencies.get(35)) / 4));
    assertEquals(
        "0.25 half steps flatter than A, octave 3",
        getContentDescriptionForPitch((3 * noteFrequencies.get(37) + noteFrequencies.get(36)) / 4));
    assertEquals(
        "0.25 half steps flatter than B flat, octave 3",
        getContentDescriptionForPitch((3 * noteFrequencies.get(38) + noteFrequencies.get(37)) / 4));
    assertEquals(
        "0.25 half steps flatter than B, octave 3",
        getContentDescriptionForPitch((3 * noteFrequencies.get(39) + noteFrequencies.get(38)) / 4));
    assertEquals(
        "0.25 half steps flatter than C, octave 4",
        getContentDescriptionForPitch((3 * noteFrequencies.get(40) + noteFrequencies.get(39)) / 4));
    assertEquals(
        "0.25 half steps flatter than C sharp, octave 4",
        getContentDescriptionForPitch((3 * noteFrequencies.get(41) + noteFrequencies.get(40)) / 4));
    assertEquals(
        "0.25 half steps flatter than D, octave 4",
        getContentDescriptionForPitch((3 * noteFrequencies.get(42) + noteFrequencies.get(41)) / 4));
    assertEquals(
        "0.25 half steps flatter than E flat, octave 4",
        getContentDescriptionForPitch((3 * noteFrequencies.get(43) + noteFrequencies.get(42)) / 4));
    assertEquals(
        "0.25 half steps flatter than E, octave 4",
        getContentDescriptionForPitch((3 * noteFrequencies.get(44) + noteFrequencies.get(43)) / 4));
    assertEquals(
        "0.25 half steps flatter than F, octave 4",
        getContentDescriptionForPitch((3 * noteFrequencies.get(45) + noteFrequencies.get(44)) / 4));
    assertEquals(
        "0.25 half steps flatter than F sharp, octave 4",
        getContentDescriptionForPitch((3 * noteFrequencies.get(46) + noteFrequencies.get(45)) / 4));
    assertEquals(
        "0.25 half steps flatter than G, octave 4",
        getContentDescriptionForPitch((3 * noteFrequencies.get(47) + noteFrequencies.get(46)) / 4));
    assertEquals(
        "0.25 half steps flatter than A flat, octave 4",
        getContentDescriptionForPitch((3 * noteFrequencies.get(48) + noteFrequencies.get(47)) / 4));
    assertEquals(
        "0.25 half steps flatter than A, octave 4",
        getContentDescriptionForPitch((3 * noteFrequencies.get(49) + noteFrequencies.get(48)) / 4));
    assertEquals(
        "0.25 half steps flatter than B flat, octave 4",
        getContentDescriptionForPitch((3 * noteFrequencies.get(50) + noteFrequencies.get(49)) / 4));
    assertEquals(
        "0.25 half steps flatter than B, octave 4",
        getContentDescriptionForPitch((3 * noteFrequencies.get(51) + noteFrequencies.get(50)) / 4));
    assertEquals(
        "0.25 half steps flatter than C, octave 5",
        getContentDescriptionForPitch((3 * noteFrequencies.get(52) + noteFrequencies.get(51)) / 4));
    assertEquals(
        "0.25 half steps flatter than C sharp, octave 5",
        getContentDescriptionForPitch((3 * noteFrequencies.get(53) + noteFrequencies.get(52)) / 4));
    assertEquals(
        "0.25 half steps flatter than D, octave 5",
        getContentDescriptionForPitch((3 * noteFrequencies.get(54) + noteFrequencies.get(53)) / 4));
    assertEquals(
        "0.25 half steps flatter than E flat, octave 5",
        getContentDescriptionForPitch((3 * noteFrequencies.get(55) + noteFrequencies.get(54)) / 4));
    assertEquals(
        "0.25 half steps flatter than E, octave 5",
        getContentDescriptionForPitch((3 * noteFrequencies.get(56) + noteFrequencies.get(55)) / 4));
    assertEquals(
        "0.25 half steps flatter than F, octave 5",
        getContentDescriptionForPitch((3 * noteFrequencies.get(57) + noteFrequencies.get(56)) / 4));
    assertEquals(
        "0.25 half steps flatter than F sharp, octave 5",
        getContentDescriptionForPitch((3 * noteFrequencies.get(58) + noteFrequencies.get(57)) / 4));
    assertEquals(
        "0.25 half steps flatter than G, octave 5",
        getContentDescriptionForPitch((3 * noteFrequencies.get(59) + noteFrequencies.get(58)) / 4));
    assertEquals(
        "0.25 half steps flatter than A flat, octave 5",
        getContentDescriptionForPitch((3 * noteFrequencies.get(60) + noteFrequencies.get(59)) / 4));
    assertEquals(
        "0.25 half steps flatter than A, octave 5",
        getContentDescriptionForPitch((3 * noteFrequencies.get(61) + noteFrequencies.get(60)) / 4));
    assertEquals(
        "0.25 half steps flatter than B flat, octave 5",
        getContentDescriptionForPitch((3 * noteFrequencies.get(62) + noteFrequencies.get(61)) / 4));
    assertEquals(
        "0.25 half steps flatter than B, octave 5",
        getContentDescriptionForPitch((3 * noteFrequencies.get(63) + noteFrequencies.get(62)) / 4));
    assertEquals(
        "0.25 half steps flatter than C, octave 6",
        getContentDescriptionForPitch((3 * noteFrequencies.get(64) + noteFrequencies.get(63)) / 4));
    assertEquals(
        "0.25 half steps flatter than C sharp, octave 6",
        getContentDescriptionForPitch((3 * noteFrequencies.get(65) + noteFrequencies.get(64)) / 4));
    assertEquals(
        "0.25 half steps flatter than D, octave 6",
        getContentDescriptionForPitch((3 * noteFrequencies.get(66) + noteFrequencies.get(65)) / 4));
    assertEquals(
        "0.25 half steps flatter than E flat, octave 6",
        getContentDescriptionForPitch((3 * noteFrequencies.get(67) + noteFrequencies.get(66)) / 4));
    assertEquals(
        "0.25 half steps flatter than E, octave 6",
        getContentDescriptionForPitch((3 * noteFrequencies.get(68) + noteFrequencies.get(67)) / 4));
    assertEquals(
        "0.25 half steps flatter than F, octave 6",
        getContentDescriptionForPitch((3 * noteFrequencies.get(69) + noteFrequencies.get(68)) / 4));
    assertEquals(
        "0.25 half steps flatter than F sharp, octave 6",
        getContentDescriptionForPitch((3 * noteFrequencies.get(70) + noteFrequencies.get(69)) / 4));
    assertEquals(
        "0.25 half steps flatter than G, octave 6",
        getContentDescriptionForPitch((3 * noteFrequencies.get(71) + noteFrequencies.get(70)) / 4));
    assertEquals(
        "0.25 half steps flatter than A flat, octave 6",
        getContentDescriptionForPitch((3 * noteFrequencies.get(72) + noteFrequencies.get(71)) / 4));
    assertEquals(
        "0.25 half steps flatter than A, octave 6",
        getContentDescriptionForPitch((3 * noteFrequencies.get(73) + noteFrequencies.get(72)) / 4));
    assertEquals(
        "0.25 half steps flatter than B flat, octave 6",
        getContentDescriptionForPitch((3 * noteFrequencies.get(74) + noteFrequencies.get(73)) / 4));
    assertEquals(
        "0.25 half steps flatter than B, octave 6",
        getContentDescriptionForPitch((3 * noteFrequencies.get(75) + noteFrequencies.get(74)) / 4));
    assertEquals(
        "0.25 half steps flatter than C, octave 7",
        getContentDescriptionForPitch((3 * noteFrequencies.get(76) + noteFrequencies.get(75)) / 4));
    assertEquals(
        "0.25 half steps flatter than C sharp, octave 7",
        getContentDescriptionForPitch((3 * noteFrequencies.get(77) + noteFrequencies.get(76)) / 4));
    assertEquals(
        "0.25 half steps flatter than D, octave 7",
        getContentDescriptionForPitch((3 * noteFrequencies.get(78) + noteFrequencies.get(77)) / 4));
    assertEquals(
        "0.25 half steps flatter than E flat, octave 7",
        getContentDescriptionForPitch((3 * noteFrequencies.get(79) + noteFrequencies.get(78)) / 4));
    assertEquals(
        "0.25 half steps flatter than E, octave 7",
        getContentDescriptionForPitch((3 * noteFrequencies.get(80) + noteFrequencies.get(79)) / 4));
    assertEquals(
        "0.25 half steps flatter than F, octave 7",
        getContentDescriptionForPitch((3 * noteFrequencies.get(81) + noteFrequencies.get(80)) / 4));
    assertEquals(
        "0.25 half steps flatter than F sharp, octave 7",
        getContentDescriptionForPitch((3 * noteFrequencies.get(82) + noteFrequencies.get(81)) / 4));
    assertEquals(
        "0.25 half steps flatter than G, octave 7",
        getContentDescriptionForPitch((3 * noteFrequencies.get(83) + noteFrequencies.get(82)) / 4));
    assertEquals(
        "0.25 half steps flatter than A flat, octave 7",
        getContentDescriptionForPitch((3 * noteFrequencies.get(84) + noteFrequencies.get(83)) / 4));
    assertEquals(
        "0.25 half steps flatter than A, octave 7",
        getContentDescriptionForPitch((3 * noteFrequencies.get(85) + noteFrequencies.get(84)) / 4));
    assertEquals(
        "0.25 half steps flatter than B flat, octave 7",
        getContentDescriptionForPitch((3 * noteFrequencies.get(86) + noteFrequencies.get(85)) / 4));
    assertEquals(
        "0.25 half steps flatter than B, octave 7",
        getContentDescriptionForPitch((3 * noteFrequencies.get(87) + noteFrequencies.get(86)) / 4));
    assertEquals(
        "0.25 half steps flatter than C, octave 8",
        getContentDescriptionForPitch((3 * noteFrequencies.get(88) + noteFrequencies.get(87)) / 4));
    assertEquals(
        "high pitch",
        getContentDescriptionForPitch((3 * noteFrequencies.get(89) + noteFrequencies.get(88)) / 4));
  }

  @Test
  public void makeContentDescriptionSharper() throws Exception {
    if (noteFrequencies.isEmpty()) {
      fillNoteLists();
    }
    assertEquals(
        "low pitch",
        getContentDescriptionForPitch((3 * noteFrequencies.get(0) + noteFrequencies.get(1)) / 4));
    assertEquals(
        "0.25 half steps sharper than A, octave 0",
        getContentDescriptionForPitch((3 * noteFrequencies.get(1) + noteFrequencies.get(2)) / 4));
    assertEquals(
        "0.25 half steps sharper than B flat, octave 0",
        getContentDescriptionForPitch((3 * noteFrequencies.get(2) + noteFrequencies.get(3)) / 4));
    assertEquals(
        "0.25 half steps sharper than B, octave 0",
        getContentDescriptionForPitch((3 * noteFrequencies.get(3) + noteFrequencies.get(4)) / 4));
    assertEquals(
        "0.25 half steps sharper than C, octave 1",
        getContentDescriptionForPitch((3 * noteFrequencies.get(4) + noteFrequencies.get(5)) / 4));
    assertEquals(
        "0.25 half steps sharper than C sharp, octave 1",
        getContentDescriptionForPitch((3 * noteFrequencies.get(5) + noteFrequencies.get(6)) / 4));
    assertEquals(
        "0.25 half steps sharper than D, octave 1",
        getContentDescriptionForPitch((3 * noteFrequencies.get(6) + noteFrequencies.get(7)) / 4));
    assertEquals(
        "0.25 half steps sharper than E flat, octave 1",
        getContentDescriptionForPitch((3 * noteFrequencies.get(7) + noteFrequencies.get(8)) / 4));
    assertEquals(
        "0.25 half steps sharper than E, octave 1",
        getContentDescriptionForPitch((3 * noteFrequencies.get(8) + noteFrequencies.get(9)) / 4));
    assertEquals(
        "0.25 half steps sharper than F, octave 1",
        getContentDescriptionForPitch((3 * noteFrequencies.get(9) + noteFrequencies.get(10)) / 4));
    assertEquals(
        "0.25 half steps sharper than F sharp, octave 1",
        getContentDescriptionForPitch((3 * noteFrequencies.get(10) + noteFrequencies.get(11)) / 4));
    assertEquals(
        "0.25 half steps sharper than G, octave 1",
        getContentDescriptionForPitch((3 * noteFrequencies.get(11) + noteFrequencies.get(12)) / 4));
    assertEquals(
        "0.25 half steps sharper than A flat, octave 1",
        getContentDescriptionForPitch((3 * noteFrequencies.get(12) + noteFrequencies.get(13)) / 4));
    assertEquals(
        "0.25 half steps sharper than A, octave 1",
        getContentDescriptionForPitch((3 * noteFrequencies.get(13) + noteFrequencies.get(14)) / 4));
    assertEquals(
        "0.25 half steps sharper than B flat, octave 1",
        getContentDescriptionForPitch((3 * noteFrequencies.get(14) + noteFrequencies.get(15)) / 4));
    assertEquals(
        "0.25 half steps sharper than B, octave 1",
        getContentDescriptionForPitch((3 * noteFrequencies.get(15) + noteFrequencies.get(16)) / 4));
    assertEquals(
        "0.25 half steps sharper than C, octave 2",
        getContentDescriptionForPitch((3 * noteFrequencies.get(16) + noteFrequencies.get(17)) / 4));
    assertEquals(
        "0.25 half steps sharper than C sharp, octave 2",
        getContentDescriptionForPitch((3 * noteFrequencies.get(17) + noteFrequencies.get(18)) / 4));
    assertEquals(
        "0.25 half steps sharper than D, octave 2",
        getContentDescriptionForPitch((3 * noteFrequencies.get(18) + noteFrequencies.get(19)) / 4));
    assertEquals(
        "0.25 half steps sharper than E flat, octave 2",
        getContentDescriptionForPitch((3 * noteFrequencies.get(19) + noteFrequencies.get(20)) / 4));
    assertEquals(
        "0.25 half steps sharper than E, octave 2",
        getContentDescriptionForPitch((3 * noteFrequencies.get(20) + noteFrequencies.get(21)) / 4));
    assertEquals(
        "0.25 half steps sharper than F, octave 2",
        getContentDescriptionForPitch((3 * noteFrequencies.get(21) + noteFrequencies.get(22)) / 4));
    assertEquals(
        "0.25 half steps sharper than F sharp, octave 2",
        getContentDescriptionForPitch((3 * noteFrequencies.get(22) + noteFrequencies.get(23)) / 4));
    assertEquals(
        "0.25 half steps sharper than G, octave 2",
        getContentDescriptionForPitch((3 * noteFrequencies.get(23) + noteFrequencies.get(24)) / 4));
    assertEquals(
        "0.25 half steps sharper than A flat, octave 2",
        getContentDescriptionForPitch((3 * noteFrequencies.get(24) + noteFrequencies.get(25)) / 4));
    assertEquals(
        "0.25 half steps sharper than A, octave 2",
        getContentDescriptionForPitch((3 * noteFrequencies.get(25) + noteFrequencies.get(26)) / 4));
    assertEquals(
        "0.25 half steps sharper than B flat, octave 2",
        getContentDescriptionForPitch((3 * noteFrequencies.get(26) + noteFrequencies.get(27)) / 4));
    assertEquals(
        "0.25 half steps sharper than B, octave 2",
        getContentDescriptionForPitch((3 * noteFrequencies.get(27) + noteFrequencies.get(28)) / 4));
    assertEquals(
        "0.25 half steps sharper than C, octave 3",
        getContentDescriptionForPitch((3 * noteFrequencies.get(28) + noteFrequencies.get(29)) / 4));
    assertEquals(
        "0.25 half steps sharper than C sharp, octave 3",
        getContentDescriptionForPitch((3 * noteFrequencies.get(29) + noteFrequencies.get(30)) / 4));
    assertEquals(
        "0.25 half steps sharper than D, octave 3",
        getContentDescriptionForPitch((3 * noteFrequencies.get(30) + noteFrequencies.get(31)) / 4));
    assertEquals(
        "0.25 half steps sharper than E flat, octave 3",
        getContentDescriptionForPitch((3 * noteFrequencies.get(31) + noteFrequencies.get(32)) / 4));
    assertEquals(
        "0.25 half steps sharper than E, octave 3",
        getContentDescriptionForPitch((3 * noteFrequencies.get(32) + noteFrequencies.get(33)) / 4));
    assertEquals(
        "0.25 half steps sharper than F, octave 3",
        getContentDescriptionForPitch((3 * noteFrequencies.get(33) + noteFrequencies.get(34)) / 4));
    assertEquals(
        "0.25 half steps sharper than F sharp, octave 3",
        getContentDescriptionForPitch((3 * noteFrequencies.get(34) + noteFrequencies.get(35)) / 4));
    assertEquals(
        "0.25 half steps sharper than G, octave 3",
        getContentDescriptionForPitch((3 * noteFrequencies.get(35) + noteFrequencies.get(36)) / 4));
    assertEquals(
        "0.25 half steps sharper than A flat, octave 3",
        getContentDescriptionForPitch((3 * noteFrequencies.get(36) + noteFrequencies.get(37)) / 4));
    assertEquals(
        "0.25 half steps sharper than A, octave 3",
        getContentDescriptionForPitch((3 * noteFrequencies.get(37) + noteFrequencies.get(38)) / 4));
    assertEquals(
        "0.25 half steps sharper than B flat, octave 3",
        getContentDescriptionForPitch((3 * noteFrequencies.get(38) + noteFrequencies.get(39)) / 4));
    assertEquals(
        "0.25 half steps sharper than B, octave 3",
        getContentDescriptionForPitch((3 * noteFrequencies.get(39) + noteFrequencies.get(40)) / 4));
    assertEquals(
        "0.25 half steps sharper than C, octave 4",
        getContentDescriptionForPitch((3 * noteFrequencies.get(40) + noteFrequencies.get(41)) / 4));
    assertEquals(
        "0.25 half steps sharper than C sharp, octave 4",
        getContentDescriptionForPitch((3 * noteFrequencies.get(41) + noteFrequencies.get(42)) / 4));
    assertEquals(
        "0.25 half steps sharper than D, octave 4",
        getContentDescriptionForPitch((3 * noteFrequencies.get(42) + noteFrequencies.get(43)) / 4));
    assertEquals(
        "0.25 half steps sharper than E flat, octave 4",
        getContentDescriptionForPitch((3 * noteFrequencies.get(43) + noteFrequencies.get(44)) / 4));
    assertEquals(
        "0.25 half steps sharper than E, octave 4",
        getContentDescriptionForPitch((3 * noteFrequencies.get(44) + noteFrequencies.get(45)) / 4));
    assertEquals(
        "0.25 half steps sharper than F, octave 4",
        getContentDescriptionForPitch((3 * noteFrequencies.get(45) + noteFrequencies.get(46)) / 4));
    assertEquals(
        "0.25 half steps sharper than F sharp, octave 4",
        getContentDescriptionForPitch((3 * noteFrequencies.get(46) + noteFrequencies.get(47)) / 4));
    assertEquals(
        "0.25 half steps sharper than G, octave 4",
        getContentDescriptionForPitch((3 * noteFrequencies.get(47) + noteFrequencies.get(48)) / 4));
    assertEquals(
        "0.25 half steps sharper than A flat, octave 4",
        getContentDescriptionForPitch((3 * noteFrequencies.get(48) + noteFrequencies.get(49)) / 4));
    assertEquals(
        "0.25 half steps sharper than A, octave 4",
        getContentDescriptionForPitch((3 * noteFrequencies.get(49) + noteFrequencies.get(50)) / 4));
    assertEquals(
        "0.25 half steps sharper than B flat, octave 4",
        getContentDescriptionForPitch((3 * noteFrequencies.get(50) + noteFrequencies.get(51)) / 4));
    assertEquals(
        "0.25 half steps sharper than B, octave 4",
        getContentDescriptionForPitch((3 * noteFrequencies.get(51) + noteFrequencies.get(52)) / 4));
    assertEquals(
        "0.25 half steps sharper than C, octave 5",
        getContentDescriptionForPitch((3 * noteFrequencies.get(52) + noteFrequencies.get(53)) / 4));
    assertEquals(
        "0.25 half steps sharper than C sharp, octave 5",
        getContentDescriptionForPitch((3 * noteFrequencies.get(53) + noteFrequencies.get(54)) / 4));
    assertEquals(
        "0.25 half steps sharper than D, octave 5",
        getContentDescriptionForPitch((3 * noteFrequencies.get(54) + noteFrequencies.get(55)) / 4));
    assertEquals(
        "0.25 half steps sharper than E flat, octave 5",
        getContentDescriptionForPitch((3 * noteFrequencies.get(55) + noteFrequencies.get(56)) / 4));
    assertEquals(
        "0.25 half steps sharper than E, octave 5",
        getContentDescriptionForPitch((3 * noteFrequencies.get(56) + noteFrequencies.get(57)) / 4));
    assertEquals(
        "0.25 half steps sharper than F, octave 5",
        getContentDescriptionForPitch((3 * noteFrequencies.get(57) + noteFrequencies.get(58)) / 4));
    assertEquals(
        "0.25 half steps sharper than F sharp, octave 5",
        getContentDescriptionForPitch((3 * noteFrequencies.get(58) + noteFrequencies.get(59)) / 4));
    assertEquals(
        "0.25 half steps sharper than G, octave 5",
        getContentDescriptionForPitch((3 * noteFrequencies.get(59) + noteFrequencies.get(60)) / 4));
    assertEquals(
        "0.25 half steps sharper than A flat, octave 5",
        getContentDescriptionForPitch((3 * noteFrequencies.get(60) + noteFrequencies.get(61)) / 4));
    assertEquals(
        "0.25 half steps sharper than A, octave 5",
        getContentDescriptionForPitch((3 * noteFrequencies.get(61) + noteFrequencies.get(62)) / 4));
    assertEquals(
        "0.25 half steps sharper than B flat, octave 5",
        getContentDescriptionForPitch((3 * noteFrequencies.get(62) + noteFrequencies.get(63)) / 4));
    assertEquals(
        "0.25 half steps sharper than B, octave 5",
        getContentDescriptionForPitch((3 * noteFrequencies.get(63) + noteFrequencies.get(64)) / 4));
    assertEquals(
        "0.25 half steps sharper than C, octave 6",
        getContentDescriptionForPitch((3 * noteFrequencies.get(64) + noteFrequencies.get(65)) / 4));
    assertEquals(
        "0.25 half steps sharper than C sharp, octave 6",
        getContentDescriptionForPitch((3 * noteFrequencies.get(65) + noteFrequencies.get(66)) / 4));
    assertEquals(
        "0.25 half steps sharper than D, octave 6",
        getContentDescriptionForPitch((3 * noteFrequencies.get(66) + noteFrequencies.get(67)) / 4));
    assertEquals(
        "0.25 half steps sharper than E flat, octave 6",
        getContentDescriptionForPitch((3 * noteFrequencies.get(67) + noteFrequencies.get(68)) / 4));
    assertEquals(
        "0.25 half steps sharper than E, octave 6",
        getContentDescriptionForPitch((3 * noteFrequencies.get(68) + noteFrequencies.get(69)) / 4));
    assertEquals(
        "0.25 half steps sharper than F, octave 6",
        getContentDescriptionForPitch((3 * noteFrequencies.get(69) + noteFrequencies.get(70)) / 4));
    assertEquals(
        "0.25 half steps sharper than F sharp, octave 6",
        getContentDescriptionForPitch((3 * noteFrequencies.get(70) + noteFrequencies.get(71)) / 4));
    assertEquals(
        "0.25 half steps sharper than G, octave 6",
        getContentDescriptionForPitch((3 * noteFrequencies.get(71) + noteFrequencies.get(72)) / 4));
    assertEquals(
        "0.25 half steps sharper than A flat, octave 6",
        getContentDescriptionForPitch((3 * noteFrequencies.get(72) + noteFrequencies.get(73)) / 4));
    assertEquals(
        "0.25 half steps sharper than A, octave 6",
        getContentDescriptionForPitch((3 * noteFrequencies.get(73) + noteFrequencies.get(74)) / 4));
    assertEquals(
        "0.25 half steps sharper than B flat, octave 6",
        getContentDescriptionForPitch((3 * noteFrequencies.get(74) + noteFrequencies.get(75)) / 4));
    assertEquals(
        "0.25 half steps sharper than B, octave 6",
        getContentDescriptionForPitch((3 * noteFrequencies.get(75) + noteFrequencies.get(76)) / 4));
    assertEquals(
        "0.25 half steps sharper than C, octave 7",
        getContentDescriptionForPitch((3 * noteFrequencies.get(76) + noteFrequencies.get(77)) / 4));
    assertEquals(
        "0.25 half steps sharper than C sharp, octave 7",
        getContentDescriptionForPitch((3 * noteFrequencies.get(77) + noteFrequencies.get(78)) / 4));
    assertEquals(
        "0.25 half steps sharper than D, octave 7",
        getContentDescriptionForPitch((3 * noteFrequencies.get(78) + noteFrequencies.get(79)) / 4));
    assertEquals(
        "0.25 half steps sharper than E flat, octave 7",
        getContentDescriptionForPitch((3 * noteFrequencies.get(79) + noteFrequencies.get(80)) / 4));
    assertEquals(
        "0.25 half steps sharper than E, octave 7",
        getContentDescriptionForPitch((3 * noteFrequencies.get(80) + noteFrequencies.get(81)) / 4));
    assertEquals(
        "0.25 half steps sharper than F, octave 7",
        getContentDescriptionForPitch((3 * noteFrequencies.get(81) + noteFrequencies.get(82)) / 4));
    assertEquals(
        "0.25 half steps sharper than F sharp, octave 7",
        getContentDescriptionForPitch((3 * noteFrequencies.get(82) + noteFrequencies.get(83)) / 4));
    assertEquals(
        "0.25 half steps sharper than G, octave 7",
        getContentDescriptionForPitch((3 * noteFrequencies.get(83) + noteFrequencies.get(84)) / 4));
    assertEquals(
        "0.25 half steps sharper than A flat, octave 7",
        getContentDescriptionForPitch((3 * noteFrequencies.get(84) + noteFrequencies.get(85)) / 4));
    assertEquals(
        "0.25 half steps sharper than A, octave 7",
        getContentDescriptionForPitch((3 * noteFrequencies.get(85) + noteFrequencies.get(86)) / 4));
    assertEquals(
        "0.25 half steps sharper than B flat, octave 7",
        getContentDescriptionForPitch((3 * noteFrequencies.get(86) + noteFrequencies.get(87)) / 4));
    assertEquals(
        "0.25 half steps sharper than B, octave 7",
        getContentDescriptionForPitch((3 * noteFrequencies.get(87) + noteFrequencies.get(88)) / 4));
    assertEquals("high pitch", getContentDescriptionForPitch(noteFrequencies.get(89) + 1));
  }
}
