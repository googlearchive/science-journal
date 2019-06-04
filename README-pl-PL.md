[Science Journal][play-store] pozwala na pozyskiwanie danych z otaczającego Cię świata. Używa czujników otoczenia jak np.: światła czy dźwięku abyś mógł stworzyć wykresy danych, zarejestrować eksperymenty i zorganizować pytania i pomysły. Jest laboratoryjnym notesem, który zawsze jest pod ręką.

Open Science Journal jest sercem aplikacji Science Journal z tym samym interfejsem i kodem czujników, może być skompilowany i uruchamiany samodzielnie.

## Możliwości

* Wizualizacja i tworzenie wykresów danych z czujników.
* Łączenie z zewnętrznymi czujnikami poprzez BLE ([firmware code][firmware-github]).
* Powiadomienia obrazkowe i tekstowe.

## Tworzenie aplikacji

Pobierz kod źródłowy, przejdź do katalogu OpenScienceJournal i uruchom:

    ./gradlew app:installDebug

Lub, importuj kod źródłowy OpenScienceJournal do Android Studio (File, Import Project).

Uwaga: Aby skompilować projekt będziesz potrzebować Android SDKw wersji 27, build tools 23.0.3, oraz Android Support Library.
Jeśli nie jesteś pewien użuj Android Studio i zaznacz odpowiednie opcje w Managerze SDK.

Plik [OpenScienceJournal README](https://github.com/google/science-journal/tree/master/OpenScienceJournal)
zawiera szczegółowe informacje na temat organizacji kodu źródłowego i zależności od tej publikacji [published app][play-store].

## Współpraca

Przeczytaj nasz [poradnik dla współpracowników][contributing].

## Licencja

Open Science Journal jest oparty na licencji [Apache 2][license].

## Więcej

Science Journal dostarcza [Making & Science][making-science], będący inicjatywą Google. Open Science Journal nie jest
oficjalnym  produktem Google.

[play-store]: https://play.google.com/store/apps/details?id=com.google.android.apps.forscience.whistlepunk
[firmware-github]:https://github.com/google/science-journal-arduino
[współpraca]: https://github.com/google/science-journal/blob/master/CONTRIBUTING.md
[licencja]: https://github.com/google/science-journal/blob/master/LICENSE
[making-science]: https://makingscience.withgoogle.com
