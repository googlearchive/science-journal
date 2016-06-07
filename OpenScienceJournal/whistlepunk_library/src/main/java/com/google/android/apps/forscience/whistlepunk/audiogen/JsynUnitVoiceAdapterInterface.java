package com.google.android.apps.forscience.whistlepunk.audiogen;


import com.jsyn.unitgen.UnitVoice;
import com.softsynth.shared.time.TimeStamp;

/**
 *  Interface that adapts between the SimpleJsynAudioGenerator and custom unit voices.
 */
public interface JsynUnitVoiceAdapterInterface {
    /**
     * Turn on a note by transforming value (in range min-max).
     * <p>
     *     Play whatever you consider to be a note on this voice.
     * </p>
     * @param value the value to be transformed to a note
     * @param min the minimum for the value range
     * @param max the maximum for the value range
     * @param timeStamp the timestamp at which to play the note
     */
    public void noteOn(double value, double min, double max, TimeStamp timeStamp);

    /**
     * Return the UnitVoice.
     * @return UnitVoice
     */
    public UnitVoice getVoice();
}
