package com.google.android.apps.forscience.whistlepunk.audiogen;


import com.google.android.apps.forscience.whistlepunk.audiogen.voices.SimpleJsynUnitVoiceBase;
import com.jsyn.unitgen.UnitVoice;
import com.softsynth.shared.time.TimeStamp;

public class JsynUnitVoiceAdapter implements JsynUnitVoiceAdapterInterface {
    protected SimpleJsynUnitVoiceBase mVoice = null;
    // Range of generated frequencies is [FREQ_MIN,FREQ_MAX).  Lower than 200Hz is very quiet on
    // small speakers, while higher than 800Hz tends to sound like a metal detector.
    protected static final double FREQ_MIN = 220.;
    protected static final double FREQ_MAX = 783.991;
    protected static final double AMP_VALUE = 1.0; // default value for amplitude

    public void noteOn(double value, double min, double max, TimeStamp timeStamp) {
        double freq = (value - min) / (max - min) * (FREQ_MAX - FREQ_MIN) + FREQ_MIN;
        mVoice.noteOn(freq, AMP_VALUE, timeStamp);
    }

    public void noteOff(TimeStamp timeStamp) {
        mVoice.noteOff(timeStamp);
    }

    public UnitVoice getVoice() {
        return mVoice;
    }

}
