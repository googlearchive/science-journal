## Source layout

The repository consists of two parts:

* *whistlepunk_library*: This contains the lion's share of the code, including all UI elements,
data collection service, sensor code, etc.  This code is used verbatim in the app builds we publish
to the [Play Store](https://play.google.com/store/apps/details?id=com.google.android.apps.forscience.whistlepunk&hl=en).

* *app*: This contains a thin wrapper that bundles whistlepunk_library to make a working Android
  app.  To make our Play Store app, we have an internal, closed-source replacement for this
  directory, which includes:
  * Our official Science Journal icon
  * Implementations of features that use Google proprietary services or code:
    * Usage reporting
    * Crash reporting and feedback
    * A few in-app feature discovery tips
  * App signing logic

