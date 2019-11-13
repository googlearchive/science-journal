[Science Journal][play-store] allows you to gather data from the world around you. It uses sensors to measure your environment, like light and sound, so you can graph your data, record your experiments, and organize your questions and ideas. It's the lab notebook you always have with you.

Open Science Journal is the core of the Science Journal app with the same UI and sensor code and can be compiled and run on its own.

## Features

* Visualize and graph data from sensors.
* Connect to external sensors over BLE ([firmware code][firmware-github]).
* Annotate with pictures and notes.

## Building the app

Download the source, go into the OpenScienceJournal directory and run:

    ./gradlew app:installDebug

Alternatively, import the source code in OpenScienceJournal into Android Studio (File, Import Project).

Note: You'll need Android SDK version 27, build tools 23.0.3, and the Android Support Library to
compile the project. If you're unsure about this, use Android Studio and tick the appropriate boxes
in the SDK Manager.

The [OpenScienceJournal README](https://github.com/google/science-journal/tree/master/OpenScienceJournal)
contains details about the organization of the source code, and the relationship of this published source
to the [published app][play-store].

## Release names

We have fun choosing names for our releases.  Read the [stories][releasenames].

## Contributing

Please read our [guidelines for contributors][contributing].

## License

Open Science Journal is licensed under the [Apache 2 license][license].

## More

Science Journal is brought to you by [Making & Science][making-science], an initiative by Google. Open Science Journal is not an official Google product.

[play-store]: https://play.google.com/store/apps/details?id=com.google.android.apps.forscience.whistlepunk
[firmware-github]:https://github.com/google/science-journal-arduino
[contributing]: https://github.com/google/science-journal/blob/master/CONTRIBUTING.md
[releasenames]: https://github.com/google/science-journal/blob/master/RELEASES.md
[license]: https://github.com/google/science-journal/blob/master/LICENSE
[making-science]: https://makingscience.withgoogle.com
