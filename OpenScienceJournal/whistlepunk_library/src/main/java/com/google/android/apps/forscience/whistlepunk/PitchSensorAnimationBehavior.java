/*
 *  Copyright 2016 Google Inc. All Rights Reserved.
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

import static com.google.android.apps.forscience.whistlepunk.audio.SoundUtils.HALF_STEP_FREQUENCY_RATIO;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.appcompat.widget.AppCompatImageView;
import android.util.DisplayMetrics;
import android.widget.RelativeLayout;
import android.widget.RelativeLayout.LayoutParams;
import com.google.android.apps.forscience.whistlepunk.audio.SoundUtils;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/** Implements sensor animation behavior for the pitch sensor. */
class PitchSensorAnimationBehavior implements SensorAnimationBehavior {
  private static final double ANGLE_TOP = Math.PI / 2;

  private static final float ELLIPSE_RADIUS_RATIO = 0.44f;
  private static final float SHADE_RADIUS_RATIO = 0.38f;
  private static final float DOT_RADIUS_RATIO = 0.05f;
  private static final float NOTE_TEXT_SIZE_RATIO = 0.48f;
  private static final float NOTE_X_RATIO = 0.42f;
  private static final float NOTE_Y_RATIO = 0.62f;
  private static final float SIGN_TEXT_SIZE_RATIO = 0.25f;
  private static final float SIGN_X_RATIO = 0.68f;
  private static final float SIGN_Y_RATIO = 0.43f;
  private static final float OCTAVE_TEXT_SIZE_RATIO = 0.2f;
  private static final float OCTAVE_X_RATIO = 0.71f;
  private static final float OCTAVE_Y_RATIO = 0.71f;
  private static final float HALF_PIXEL = 0.5f;

  private static final int SHADE_LOW_RED = 0x71;
  private static final int SHADE_LOW_GREEN = 0xCA;
  private static final int SHADE_LOW_BLUE = 0xF8;
  private static final int SHADE_LOW = Color.rgb(SHADE_LOW_RED, SHADE_LOW_GREEN, SHADE_LOW_BLUE);
  private static final int SHADE_HIGH_RED = 0x0D;
  private static final int SHADE_HIGH_GREEN = 0x61;
  private static final int SHADE_HIGH_BLUE = 0xAF;
  private static final int SHADE_HIGH =
      Color.rgb(SHADE_HIGH_RED, SHADE_HIGH_GREEN, SHADE_HIGH_BLUE);
  private static final int COLOR_NOTE_LEFT = 0xFFEEEEEE;
  private static final int COLOR_NOTE_RIGHT = 0xFFDCDCDC;
  private static final int COLOR_SIGN = 0xFFDCDCDC;
  private static final int COLOR_OCTAVE = 0xFFDCDCDC;

  // These shadow values are from Konina.
  private static final int SHADOW_RADIUS = 4;
  private static final int SHADOW_DX = 1;
  private static final int SHADOW_DY = 2;
  private static final int SHADOW_COLOR = 0x3D000000;

  private static final String NATURAL = "";
  private static final String FLAT = "\u266D";
  private static final String SHARP = "\u266F";

  private static class MusicalNote {
    final String letter;
    final String sign;
    final String octave;

    MusicalNote(String letter, String sign, String octave) {
      this.letter = letter;
      this.sign = sign;
      this.octave = octave;
    }

    boolean isNotePlacedAtCenter() {
      // "-" and "+" are placed at the center.
      return letter.equals("-") || letter.equals("+");
    }
  }

  /**
   * noteFrequencies contains the frequencies of notes of a piano at indices 1-88. The value at
   * index 0 is a half step less than the value at index 1. The value at index 89 is a half step
   * more than the value at index 88.
   */
  @VisibleForTesting static final List<Double> noteFrequencies = new ArrayList<>();

  private static final List<MusicalNote> musicalNotes = new ArrayList<>();

  private final Paint paintForDot;
  private final Paint paintForShade;
  private final Paint paintForNoteLeft;
  private final Paint paintForNoteRight;
  private final Paint paintForSign;
  private final Paint paintForOctave;

  PitchSensorAnimationBehavior() {
    paintForDot = new Paint();
    paintForDot.setColor(Color.RED);

    paintForShade = new Paint();
    paintForShade.setColor(makeShadeColor(0));

    Typeface sansSerifBold = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD);

    paintForNoteLeft = new Paint(Paint.LINEAR_TEXT_FLAG | Paint.ANTI_ALIAS_FLAG);
    paintForNoteLeft.setTextAlign(Paint.Align.CENTER);
    paintForNoteLeft.setColor(COLOR_NOTE_LEFT);
    paintForNoteLeft.setTypeface(sansSerifBold);

    paintForNoteRight = new Paint(Paint.LINEAR_TEXT_FLAG | Paint.ANTI_ALIAS_FLAG);
    paintForNoteRight.setTextAlign(Paint.Align.CENTER);
    paintForNoteRight.setColor(COLOR_NOTE_RIGHT);
    paintForNoteRight.setTypeface(sansSerifBold);

    paintForSign = new Paint(Paint.LINEAR_TEXT_FLAG | Paint.ANTI_ALIAS_FLAG);
    paintForSign.setTextAlign(Paint.Align.CENTER);
    paintForSign.setColor(COLOR_SIGN);
    paintForSign.setTypeface(sansSerifBold);

    paintForOctave = new Paint(Paint.LINEAR_TEXT_FLAG | Paint.ANTI_ALIAS_FLAG);
    paintForOctave.setTextAlign(Paint.Align.CENTER);
    paintForOctave.setColor(COLOR_OCTAVE);
    paintForOctave.setTypeface(sansSerifBold);
  }

  private ImageViewCanvas getImageViewCanvas(RelativeLayout layout) {
    return (ImageViewCanvas) layout.getChildAt(0);
  }

  private class ImageViewCanvas extends AppCompatImageView {
    private float iconCenterX;
    private float iconCenterY;
    private float dotRadius;
    private float ellipseRadius;
    private float shadeRadius;
    private float noteX;
    private float noteY;
    private float signX;
    private float signY;
    private float octaveX;
    private float octaveY;
    private Rect noteBounds = new Rect();

    /**
     * The angle of the dot indicating how close the detected pitch is to the nearest musical note.
     * A value of 0 positions the dot at the far right. A value of PI/2 positions the dot at the
     * top. A value of PI positions the dot at the far left. Initialize so that the dot is displayed
     * at the top.
     */
    private double angleOfDot = ANGLE_TOP;

    private int level = 0;

    ImageViewCanvas(Context context) {
      super(context);
      // Without setting the layr type to software, the octave numbers 6 and 3 are cut off on
      // the right edge.
      setLayerType(LAYER_TYPE_SOFTWARE, null);
    }

    private void setPitch(double detectedPitch) {
      int level = pitchToLevel(detectedPitch);
      if (level != this.level) {
        this.level = level;
        paintForShade.setColor(makeShadeColor(level));
      }
      double difference = calculateDifference(detectedPitch, level);
      angleOfDot = (1 - 2 * difference) * (Math.PI / 2);
      setContentDescription(makeContentDescription(getContext(), level, difference));
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldW, int oldH) {
      iconCenterX = w / 2.0f;
      iconCenterY = h / 2.0f;
      ellipseRadius = w * ELLIPSE_RADIUS_RATIO;
      dotRadius = w * DOT_RADIUS_RATIO;
      shadeRadius = w * SHADE_RADIUS_RATIO;

      noteX = w * NOTE_X_RATIO;
      noteY = h * NOTE_Y_RATIO;
      signX = w * SIGN_X_RATIO;
      signY = h * SIGN_Y_RATIO;
      octaveX = w * OCTAVE_X_RATIO;
      octaveY = h * OCTAVE_Y_RATIO;

      paintForNoteLeft.setTextSize(h * NOTE_TEXT_SIZE_RATIO);
      paintForNoteRight.setTextSize(h * NOTE_TEXT_SIZE_RATIO);
      paintForSign.setTextSize(h * SIGN_TEXT_SIZE_RATIO);
      paintForOctave.setTextSize(h * OCTAVE_TEXT_SIZE_RATIO);
      DisplayMetrics metrics = getContext().getResources().getDisplayMetrics();
      int shadowRadius = SHADOW_RADIUS;
      int shadowDx = SHADOW_DX;
      int shadowDy = SHADOW_DY;
      if (metrics.densityDpi > DisplayMetrics.DENSITY_HIGH) {
        shadowRadius *= 2;
        shadowDx *= 2;
        shadowDy *= 2;
      }
      paintForNoteLeft.setShadowLayer(shadowRadius, shadowDx, shadowDy, SHADOW_COLOR);
      paintForNoteRight.setShadowLayer(shadowRadius, shadowDx, shadowDy, SHADOW_COLOR);
      paintForOctave.setShadowLayer(shadowRadius, shadowDx, shadowDy, SHADOW_COLOR);
      paintForSign.setShadowLayer(shadowRadius, shadowDx, shadowDy, SHADOW_COLOR);

      if (musicalNotes.isEmpty()) {
        fillNoteLists();
      }
    }

    @Override
    protected void onDraw(Canvas canvas) {
      super.onDraw(canvas);

      if (musicalNotes.isEmpty()) {
        // This shouldn't happen because onSize should have been called before onDraw, but
        // just in case it does happen, we'll just show the base icon, which is a - sign.
        return;
      }

      canvas.drawCircle(iconCenterX, iconCenterY, shadeRadius, paintForShade);

      MusicalNote musicalNote = musicalNotes.get(level);
      float noteX = this.noteX;
      float noteY = this.noteY;
      if (musicalNote.isNotePlacedAtCenter()) {
        // "-" and "+" are placed at the center.
        noteX = iconCenterX;
        paintForNoteLeft.getTextBounds(
            musicalNote.letter, 0, musicalNote.letter.length(), noteBounds);
        noteY = iconCenterY - (noteBounds.top + noteBounds.bottom) / 2;
      }
      canvas.save();
      canvas.clipRect(0, 0, noteX, getHeight());
      canvas.drawText(musicalNote.letter, noteX, noteY, paintForNoteLeft);
      canvas.restore();
      canvas.save();
      canvas.clipRect(noteX, 0, getWidth(), getHeight());
      canvas.drawText(musicalNote.letter, noteX, noteY, paintForNoteRight);
      canvas.restore();
      if (musicalNote.sign != null && !musicalNote.sign.isEmpty()) {
        // We need to draw the sharp or flat sign.
        canvas.drawText(musicalNote.sign, signX, signY, paintForSign);
      }
      if (musicalNote.octave != null && !musicalNote.octave.isEmpty()) {
        canvas.drawText(musicalNote.octave, octaveX, octaveY, paintForOctave);
      }

      // Calculate the location of the red dot. The red dot will be a point on the invisible
      // ellipse.
      float xDot = (float) (iconCenterX + ellipseRadius * Math.cos(angleOfDot));
      float yDot = (float) (iconCenterY - ellipseRadius * Math.sin(angleOfDot));
      if (angleOfDot == ANGLE_TOP) {
        // Because the drawable is an even number of pixels wide, the top circle is offset
        // half a pixel to the right. This fixes the overlap issues.
        xDot += HALF_PIXEL;
      }
      canvas.drawCircle(xDot, yDot, dotRadius, paintForDot);
    }
  }

  @Override
  public void resetIcon(RelativeLayout layout) {
    ImageViewCanvas view = getImageViewCanvas(layout);
    view.setPitch(0);
    view.postInvalidateOnAnimation();
  }

  @Override
  public boolean updateIconAndTextTogether() {
    return false;
  }

  @Override
  public void updateIcon(
      RelativeLayout layout,
      double detectedPitch,
      double yMin,
      double yMax,
      int screenOrientation) {
    ImageViewCanvas view = getImageViewCanvas(layout);
    view.setPitch(detectedPitch);
    view.postInvalidateOnAnimation();
  }

  @Override
  public void initializeLargeIcon(RelativeLayout layout, @Nullable Double value) {
    // Remove previous views.
    if (layout.getChildCount() > 0) {
      layout.removeAllViews();
    }
    Context context = layout.getContext();
    ImageViewCanvas largeIcon = new ImageViewCanvas(context);
    layout.addView(
        largeIcon, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
    largeIcon.setImageDrawable(
        context.getResources().getDrawable(R.drawable.sound_frequency_drawable));
    largeIcon.setPitch((value != null) ? value : 0);
  }

  @VisibleForTesting
  static void fillNoteLists() {
    noteFrequencies.addAll(SoundUtils.getPianoNoteFrequencies());
    // Add first and last items to make lookup easier. Use the approximate half-step ratio to
    // determine the first and last items.
    noteFrequencies.add(0, noteFrequencies.get(0) / HALF_STEP_FREQUENCY_RATIO);
    noteFrequencies.add(
        noteFrequencies.get(noteFrequencies.size() - 1) * HALF_STEP_FREQUENCY_RATIO);

    musicalNotes.add(new MusicalNote("-", "" /* sign */, "" /* octave */));
    for (int i = 1; i <= 88; i++) {
      String letter;
      String sign;
      switch ((i + 8) % 12) {
        case 0:
          letter = "C";
          sign = NATURAL;
          break;
        case 1:
          letter = "C";
          sign = SHARP;
          break;
        case 2:
          letter = "D";
          sign = NATURAL;
          break;
        case 3:
          letter = "E";
          sign = FLAT;
          break;
        case 4:
          letter = "E";
          sign = NATURAL;
          break;
        case 5:
          letter = "F";
          sign = NATURAL;
          break;
        case 6:
          letter = "F";
          sign = SHARP;
          break;
        case 7:
          letter = "G";
          sign = NATURAL;
          break;
        case 8:
          letter = "A";
          sign = FLAT;
          break;
        case 9:
          letter = "A";
          sign = NATURAL;
          break;
        case 10:
          letter = "B";
          sign = FLAT;
          break;
        case 11:
          letter = "B";
          sign = NATURAL;
          break;
        default:
          throw new RuntimeException("This is not possible.");
      }
      int octave = (i + 8) / 12;
      musicalNotes.add(new MusicalNote(letter, sign, "" + octave));
    }
    musicalNotes.add(new MusicalNote("+", "" /* sign */, "" /* octave */));
  }

  /**
   * Returns the index corresponding to the given sound frequency, where indices 1-88 represent the
   * notes of keys on a piano.
   */
  @VisibleForTesting
  static int pitchToLevel(double frequency) {
    if (noteFrequencies.isEmpty() || musicalNotes.isEmpty()) {
      fillNoteLists();
    }
    int i = Collections.binarySearch(noteFrequencies, frequency);
    // If there is an exact match, i will be a non-negative number, which is the index of the
    // matching value.
    if (i >= 0) {
      return i;
    }
    // If there is no exact match, i will provide the insertion point, where the observed
    // frequency would belong in the list, as (-(insertion point) - 1).
    // This is the usual case, since in most cases the observed frequency will not match a
    // specific note exactly.
    // Calculate the insertion point.
    i = -i - 1;
    if (i == 0) {
      // frequency is significantly lower than the lowest note.
      return 0;
    }
    if (i == noteFrequencies.size()) {
      // frequency is significantly higher than the highest note.
      return noteFrequencies.size() - 1;
    }
    // frequency is between two notes.
    double midpoint = (noteFrequencies.get(i - 1) + noteFrequencies.get(i)) / 2;
    return (frequency < midpoint) ? (i - 1) : i;
  }

  /**
   * Returns the difference, in half steps, between the detected pitch and the note associated with
   * the given level.
   */
  @VisibleForTesting
  static double calculateDifference(double detectedPitch, int level) {
    if (level == 0 || level == noteFrequencies.size() - 1) {
      // If the nearest musical note is more than one half step lower than the lowest musical
      // note or more than one half step higher than the highest musical note, we don't
      // calculate the difference.
      return 0;
    }
    // If the detected pitch equals a musical note the dot is at the top, which is 90
    // degrees or Math.PI / 2 radians.
    // If the detected pitch is half way between the nearest musical note and the next
    // lower musical note, the dot is at the far left, which is 180 degrees, or Math.PI
    // radians.
    // If the detected pitch is half way between the nearest musical note and the next
    // higher musical note, the dot is at the far right, which is 0 degrees, or 0 radians.
    double nearestNote = noteFrequencies.get(level);
    double difference = detectedPitch - nearestNote;
    if (difference < 0) {
      // The detected pitch is lower than the nearest musical note.
      // Adjust the difference to the range of -1 to 0, where -1 is the next lower note.
      double lowerNote = noteFrequencies.get(level - 1);
      difference /= (nearestNote - lowerNote);
      // The difference should never actually be less than -0.5, since that would
      // indicate that the detected pitch was actually closer to the lowerNote.
    } else {
      // The detected pitch is higher than the nearest musical note.
      // Adjust the difference to the range of 0 to +1, where +1 is the next higher note.
      double higherNote = noteFrequencies.get(level + 1);
      difference /= (higherNote - nearestNote);
      // The difference should never actually be greater than 0.5, since that would
      // indicate that the detected pitch was actually closer to the higherNote.
    }
    return difference;
  }

  @VisibleForTesting
  static String makeContentDescription(Context context, int noteNumber, double difference) {
    if (noteNumber < 1) {
      return context.getResources().getString(R.string.pitch_low_content_description);
    }
    if (noteNumber > 88) {
      return context.getResources().getString(R.string.pitch_high_content_description);
    }
    Locale locale = context.getResources().getConfiguration().locale;
    String differenceFormatted = String.format(locale, "%1.2f", Math.abs(difference));
    int signum = (int) Math.signum(difference);
    // differenceFormatted is in the range 0.00 to 0.50, but the decimal point may be "." or
    // ",", depending on locale. The following comparison tells us whether difference is close
    // enough to zero, that we should consider the detetected pitch as not flatter or sharper
    // than the musical note.
    if (differenceFormatted.endsWith("00")) {
      signum = 0;
    }
    MusicalNote musicalNote = musicalNotes.get(noteNumber);
    int format;
    if (musicalNote.sign.equals(FLAT)) {
      format = flatNoteDescription(signum);
    } else if (musicalNote.sign.equals(SHARP)) {
      format = sharpNoteDescription(signum);
    } else {
      format = naturalNoteDescription(signum);
    }
    return context
        .getResources()
        .getString(format, musicalNote.letter, musicalNote.octave, differenceFormatted);
  }

  private static int naturalNoteDescription(int signum) {
    if (signum < 0) {
      return R.string.pitch_flatter_than_natural_note_content_description;
    }
    if (signum > 0) {
      return R.string.pitch_sharper_than_natural_note_content_description;
    }
    return R.string.pitch_natural_note_content_description;
  }

  private static int flatNoteDescription(int signum) {
    if (signum < 0) {
      return R.string.pitch_flatter_than_flat_note_content_description;
    }
    if (signum > 0) {
      return R.string.pitch_sharper_than_flat_note_content_description;
    }
    return R.string.pitch_flat_note_content_description;
  }

  private static int sharpNoteDescription(int signum) {
    if (signum < 0) {
      return R.string.pitch_flatter_than_sharp_note_content_description;
    }
    if (signum > 0) {
      return R.string.pitch_sharper_than_sharp_note_content_description;
    }
    return R.string.pitch_sharp_note_content_description;
  }

  private static int makeShadeColor(int level) {
    if (level == 0) {
      return SHADE_LOW;
    }
    if (level == noteFrequencies.size() - 1) {
      return SHADE_HIGH;
    }
    int r = (int) Math.round(SHADE_LOW_RED + (SHADE_HIGH_RED - SHADE_LOW_RED) * level / 89.0);
    int g = (int) Math.round(SHADE_LOW_GREEN + (SHADE_HIGH_GREEN - SHADE_LOW_GREEN) * level / 89.0);
    int b = (int) Math.round(SHADE_LOW_BLUE + (SHADE_HIGH_BLUE - SHADE_LOW_BLUE) * level / 89.0);
    return Color.rgb(r, g, b);
  }
}
