## Creating a third-party service to provide sensors to Science Journal.

When users open Science Journal, they see the sensors that are built-in on their
phone.  However, they can also choose to observe and record additional sensors
hosted by services on the phone.  Science Journal has one such service
built-in, which is designed to connect to the Arduino-based sensors that are
used in our [activities](https://makingscience.withgoogle.com/science-journal/activities/activity-connecting-external-sensors).  (Note that throughout this
doc, we will use "phone" to refer to the device that is running Science
Journal, although Science Journal may also run on a tablet or ChromeBook.)

If a user installs your app, and your app properly advertises a Science-Journal
compatible service, then your app can discover and connect to additional
sensors, and stream from them to Science Journal.  These sensors can be
external devices, connected by a protocol like Bluetooth, or sensors internal
to the phone, which Science Journal does not know how
to connect to itself.  The user can find a list of installed sensor services,
and connect to any devices they discover, by pressing the "add sensor" icon
from the Observe screen, which looks like this:

![Add sensor icon](https://raw.githubusercontent.com/google/science-journal/master/api/ScienceJournalApi/docs/add_sensor.png)

We have published an
[example app](https://github.com/google/science-journal/tree/master/ScalarApiSampleApp)
which is the second kind; it finds all of the hardware sensors on the phone,
and offers to expose data from those sensors.
To get started building your own sensor service, we recommend reading and
understanding this example app and the
[base service class](https://github.com/google/science-journal/blob/master/api/ScienceJournalApi/src/main/java/com/google/android/apps/forscience/whistlepunk/api/scalarinput/ScalarSensorService.java)
it implements.

Below are some points of interest to guide looking through the example code:

- The API client code your app will need is available in our android library on
jcenter.  To include this, you need to add the following lines to your
build.gradle file.  To double-check the currently-supported version, and the
correct format and placement, refer to the sample app's
[build file](https://github.com/google/science-journal/blob/master/ScalarApiSampleApp/app/build.gradle):

```
dependencies {
  compile 'com.google.android.apps.forscience:science-journal-api:0.1'
}
```

- In order for Science Journal to discover your service, it must advertise an
intent filter for the SCALAR_SENSOR action (see
[example app manifest](https://github.com/google/science-journal/blob/master/ScalarApiSampleApp/app/src/main/AndroidManifest.xml)):

```
<service android:name=".AllNativeSensorProvider"
         android:enabled="true"
         android:exported="true">
  <intent-filter>
    <action android:name="com.google.android.apps.forscience.whistlepunk.SCALAR_SENSOR"/>
  </intent-filter>
</service>
```

- We recommend that your service class inherit from
[ScalarSensorService](https://github.com/google/science-journal/blob/master/api/ScienceJournalApi/src/main/java/com/google/android/apps/forscience/whistlepunk/api/scalarinput/ScalarSensorService.java),
which helps to enforce the conventions of our inter-process communication.
Please review the javadoc of that class, which has guidance on how it expects
to be extended. The documentation below assumes this recommendation, but it is
not a requirement; by looking at the code, you can use the parts of
ScalarSensorService that you need.

- We recommend that you check the signature of client apps connecting to your
service.  Otherwise, it is possible that users will be surprised to discover
that unexpected apps are able to read the sensor data that your app is exposing.
  - By default, ScalarSensorService will not expose any sensors unless the
    connecting app is signed with either the production or development keys for
    Google Science Journal.
  - To change the set of accepted signatures, you can override allowedSignatures
  - To disable this check entirely, you can override
    [shouldCheckBinderSignature](https://github.com/google/science-journal/blob/master/ScalarApiSampleApp/app/src/main/AndroidManifest.xml):

- Your service is responsible for discovering _devices_ and _sensors_.  The
  terminology maps most easily to a situation in which a _device_ is an
  external piece of hardware wirelessly connecting to the user's phone, and a
  _sensor_ is a particular reading that can be made from that device.
  - We recommend that your service name _devices_ after hardware
    ("thermometer"), and _sensors_ after the quantity measured ("temperature")
  - If possible, if your service can connect to multiple devices and sensors
    of the same kind, try to give each a different name so they can be
    distinguished in the UI.
  - In many cases, there may be only one sensor per device.  (For example, a
    thermometer may only expose temperature).  Or there may be multiple
    sensors per device.  Ideally, a user should be able to connect to any
    combination of the sensors on a device, including streaming multiple
    sensors simultaneously.

- Be sure to check the javadoc on
  [findDevices](https://github.com/google/science-journal/blob/master/api/ScienceJournalApi/src/main/java/com/google/android/apps/forscience/whistlepunk/api/scalarinput/ScalarSensorService.java#L68) and
  [getDevices](https://github.com/google/science-journal/blob/master/api/ScienceJournalApi/src/main/java/com/google/android/apps/forscience/whistlepunk/api/scalarinput/ScalarSensorService.java#L81)

- Likewise,
  [AdvertisedSensor](https://github.com/google/science-journal/blob/master/api/ScienceJournalApi/src/main/java/com/google/android/apps/forscience/whistlepunk/api/scalarinput/AdvertisedSensor.java)
  has important advice about the expected ways your sensor may be connected and
  disconnected.

If you have any problems implementing this API, please raise an issue on
[github](https://github.com/google/science-journal/issues).  If you are
successful in implementing the API in a publicly-available app, we'd love to
hear about it on our
[forums](https://productforums.google.com/forum/#!forum/sciencejournal).

Good luck!