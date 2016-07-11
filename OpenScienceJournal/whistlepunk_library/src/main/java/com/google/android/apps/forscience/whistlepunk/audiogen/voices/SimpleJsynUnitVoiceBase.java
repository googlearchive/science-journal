package com.google.android.apps.forscience.whistlepunk.audiogen.voices;

import com.jsyn.ports.UnitOutputPort;
import com.jsyn.unitgen.Circuit;
import com.jsyn.unitgen.UnitVoice;
import com.softsynth.shared.time.TimeStamp;

public abstract class SimpleJsynUnitVoiceBase extends Circuit implements UnitVoice {

    public abstract void noteOn(double frequency, double amplitude, TimeStamp timeStamp);

    public abstract void noteOff(TimeStamp timeStamp);

    public abstract UnitOutputPort getOutput();
}