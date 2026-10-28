<p align="center">
  <img src="docs/images/icon.svg" width="112" height="112" alt="Pill Launcher app icon">
</p>

<h1 align="center">Pill Launcher</h1>

<p align="center">
  <b>A calm, photo-first home screen for Android TV.</b><br>
  Your favourite apps, the time and weather, and a new nature photo every few seconds. No ads, no accounts, no rows of recommendations.
</p>

<p align="center">
  <a href="https://github.com/S1rHades/One-TVOS/releases/download/latest/launcher.apk"><img src="docs/images/download.svg" height="56" alt="Download Pill Launcher: latest APK for Android TV"></a>
  &nbsp;
  <a href="CHANGELOG.md"><img src="docs/images/whats-new.svg" height="56" alt="What's new"></a>
</p>

<p align="center">
  <img src="docs/screenshots/home.png" width="100%" alt="The home screen: the clock and date with the weather beside it over a photo of a lake, a round settings button top right, five favourite apps along the bottom with the first one focused, and an All apps hint below them">
</p>

## Highlights

- **Just your apps.** The home screen shows only your favourites; everything else waits one press below, in an alphabetical grid that turns over a page at a time.
- **Time and weather.** A big clock with the current conditions, today's high and low beside it.
- **Built for the remote.** Focused apps lift and get a crisp white ring, every move animates smoothly, and every action is one or two presses of the D-pad.
- **A fresh view.** The background cross-fades between full-screen photos, and the next one is loaded before it appears, so it never flashes.
- **Light on the TV.** One small app, written in Jetpack Compose, with no background services and no tracking.

## Screenshots

<table>
  <tr>
    <td width="50%"><img src="docs/screenshots/all-apps.png" alt="All apps: the grid fills the screen under its title, the photo dimmed behind it, with Games focused"><br><sub><b>All apps</b>: press down and the grid slides up a page at a time over a dimmed photo.</sub></td>
    <td width="50%"><img src="docs/screenshots/settings.png" alt="The settings card on the right: Photo slideshow switch, Change photo every 20 s, Weather in °C, and Android TV settings"><br><sub><b>Settings</b>: a floating card for the slideshow, the weather and the TV's own settings.</sub></td>
  </tr>
  <tr>
    <td width="50%"><img src="docs/screenshots/pin.png" alt="Maps focused in the All apps grid after holding OK on it, with a message saying Added Maps to Favorites"><br><sub><b>Pin with one press</b>: hold OK on any app and it joins Favorites, with a short confirmation.</sub></td>
    <td width="50%"><img src="docs/screenshots/first-launch.png" alt="First launch with no favourites: a hint explains how to pin apps, with the settings button focused"><br><sub><b>First launch</b>: a hint shows how to fill Favorites.</sub></td>
  </tr>
</table>

<p align="center"><img src="docs/screenshots/weather-icons.png" width="620" alt="The weather widget's icons: clear, partly cloudy, cloudy, fog, rain, snow, thunderstorm, clear night and partly cloudy night"></p>

<sub>Screenshots are rendered at 1920×1080 from the app's own interface code by <code>.github/workflows/screenshots.yml</code>. The app names and icons in them are stand-ins; on your TV you'll see your installed apps. Photos: <a href="https://picsum.photos">Lorem Picsum</a> (Unsplash licence).</sub>

## Install

1. On the TV, install **Downloader** (by AFTVnews) from the Play Store.
2. Open it and enter `https://github.com/S1rHades/One-TVOS/releases/download/latest/launcher.apk`.
3. Allow Downloader to install unknown apps when Android asks, then choose **Install**.

To make it your home screen, press **Home** on the remote. If the TV asks which app to use, choose **Pill Launcher** and **Always**. Some TVs, including most Google TV devices, never ask; on those, open Pill Launcher from the apps list instead.

The `latest` release is rebuilt automatically every time `main` changes, so installing it again always gets you the newest version.

## Using it

| To | Press |
| --- | --- |
| Open an app | **OK** |
| See all apps | **Down** from Favorites |
| Pin or unpin an app | Hold **OK** for half a second, or press **Menu** (☰) |
| Open settings | Go up to the round gear button (top right), then **OK** |
| Change how often the photo changes, or the weather unit | In settings, **Left** / **Right** on that row |
| Close settings | **Back** |

Settings and favourites are saved on the TV and survive restarts and updates.

### Weather

The widget finds the TV's approximate location from its internet address with [GeoJS](https://www.geojs.io/) and gets the forecast from [Open-Meteo](https://open-meteo.com/); neither needs an account or key. It refreshes every 30 minutes. Choose °C or °F in settings, or turn it **Off** and the launcher contacts neither service.

### Your own photos

The backgrounds come from the list in `BackgroundSources` (`app/src/main/java/app/pill/launcher/ui/BackgroundSlideshow.kt`). Replace those URLs with any direct links to 1920×1080 images and rebuild.

## Development

You need JDK 17 and the Android SDK (API 34). Android Studio works, or from the command line:

```
gradle assembleDebug                                   # app/build/outputs/apk/debug/app-debug.apk
gradle :app:testDebugUnitTest --tests '*Screenshots*'  # re-render docs/screenshots
```

**Builds are automated.** `.github/workflows/build.yml` builds the APK for every pull request and push; builds of `main` replace the `latest` release. `.github/workflows/screenshots.yml` re-renders the README screenshots at 1920×1080 and commits them; run it from the Actions tab after changing the interface.

<details>
<summary><b>Architecture</b></summary>

```
app/src/main/java/app/pill/launcher/
  MainActivity.kt        launcher activity: loads apps, opens them, keeps pins and settings
  AppModel.kt            lists launchable apps (TV apps first) with their icons
  LauncherSettings.kt    settings and pinned apps in SharedPreferences
  Weather.kt             location and forecast lookup, condition names and units
  ui/
    LauncherScreen.kt    the home screen: a first page with the clock and favourites, All apps below, page scrolling
    AppTile.kt           a focusable squircle app tile
    BackgroundSlideshow.kt  photo cross-fade and the readability scrim
    SettingsPanel.kt     the floating settings card (its own dialog window, so focus stays inside)
    SettingsPill.kt      the round gear button and the gear icon
    TimeWidget.kt        clock and date
    WeatherWidget.kt     weather beside the clock, with drawn condition icons
    Theme.kt             shared colours and spacing
    Squircle.kt          the superellipse tile shape
app/src/test/.../Screenshots.kt   Robolectric + Roborazzi renders for this README
```

The screen is laid out in dp for a 960×540 dp canvas (a 1080p TV at xhdpi), with 48 dp and 27 dp margins to stay inside the overscan-safe area.

</details>
