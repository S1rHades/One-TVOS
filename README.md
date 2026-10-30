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
- **Widgets you choose.** Up to four frosted-glass cards across the top: clock, weather (OK opens the forecast), sunrise & sunset, system info, headlines from Al Jazeera English and RT English by topic, and football for Algeria and the teams you follow, with a match-day countdown and a kick-off reminder.
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
    <td width="50%"><img src="docs/screenshots/menu.png" alt="The app menu for Maps over the dimmed home screen: Add to Favorites, App info and Uninstall"><br><sub><b>App menu</b>: hold OK on any app to pin it, move it, see its info or uninstall it.</sub></td>
    <td width="50%"><img src="docs/screenshots/first-launch.png" alt="First launch with no favourites: a hint explains how to pin apps, with the settings button focused"><br><sub><b>First launch</b>: a hint shows how to fill Favorites.</sub></td>
  </tr>
</table>

<table>
  <tr>
    <td width="50%"><img src="docs/screenshots/move.png" alt="Cinema being moved along the favourites row, with white arrows either side of it"><br><sub><b>Reorder favourites</b>: Move, then Left / Right, then OK.</sub></td>
    <td width="50%"><img src="docs/screenshots/pin.png" alt="Maps focused in the All apps grid with a message saying Added Maps to Favorites"><br><sub><b>Pinned</b>: a short confirmation, and the app joins Favorites.</sub></td>
  </tr>
</table>

<table>
  <tr>
    <td width="50%"><img src="docs/screenshots/widgets.png" alt="Four widget cards across the top: the clock, Algeria's next match with the last result, sunrise and sunset on an arc, and system info"><br><sub><b>Widgets</b>: up to four cards; with four on they switch to compact layouts.</sub></td>
    <td width="50%"><img src="docs/screenshots/widget-settings.png" alt="Settings with the Widgets group: four widgets on with their positions, System info dimmed, and a message saying Maximum of 4 widgets can be active at once"><br><sub><b>Widget settings</b>: switch widgets on and off and move them with Left / Right.</sub></td>
  </tr>
</table>

<table>
  <tr>
    <td width="50%"><img src="docs/screenshots/match-day.png" alt="Match day: the football card is green and counts down to Algeria v Togo's kick-off"><br><sub><b>Match day</b>: the football card counts down to kick-off.</sub></td>
    <td width="50%"><img src="docs/screenshots/kickoff-banner.png" alt="A banner at the bottom: Les Fennecs kick off in 8 minutes, Algeria v Togo, AFCON qualifier"><br><sub><b>Kick-off reminder</b>: ten minutes before, a banner that never gets in the way.</sub></td>
  </tr>
  <tr>
    <td width="50%"><img src="docs/screenshots/football-hub.png" alt="The football page: chips for Algeria and Arsenal, Arsenal's next match and latest result, and the top five of the Premier League"><br><sub><b>Football page</b>: OK on the card for the next match, the latest result and the table.</sub></td>
    <td width="50%"><img src="docs/screenshots/settings-football.png" alt="Settings, Football teams: Algeria and Arsenal followed, a search for alg listing MC Alger"><br><sub><b>Follow teams</b>: search for a club or country and add up to five beside Algeria.</sub></td>
  </tr>
  <tr>
    <td width="50%"><img src="docs/screenshots/forecast.png" alt="The forecast page: now, the next twelve hours with icons and chance of rain, and seven days with temperature bars"><br><sub><b>Forecast</b>: OK on the weather card for the next hours and days.</sub></td>
    <td width="50%"><img src="docs/screenshots/headlines.png" alt="Four widgets with a headlines card showing a Russia story from RT"><br><sub><b>Headlines</b>: one story at a time, picked by topic.</sub></td>
  </tr>
</table>

<p align="center"><img src="docs/screenshots/weather-icons.png" width="620" alt="The weather widget's icons: clear, partly cloudy, cloudy, fog, rain, snow, thunderstorm, clear night and partly cloudy night"></p>

<table>
  <tr>
    <td width="50%"><img src="docs/screenshots/idle.png" alt="The idle screen: only the clock and weather over a slowly zooming mountain photo"><br><sub><b>Idle screen</b>: after a while without a key press, only the widgets stay.</sub></td>
    <td width="50%"><img src="docs/screenshots/search.png" alt="The search panel with m typed, showing Maps, Music, Cinema and Games"><br><sub><b>Search</b>: find any app by name with the TV keyboard.</sub></td>
  </tr>
</table>

<sub>Screenshots are rendered at 1920×1080 from the app's own interface code by <code>.github/workflows/screenshots.yml</code>. The app names and icons in them are stand-ins; on your TV you'll see your installed apps. Photos: <a href="https://picsum.photos">Lorem Picsum</a> (Unsplash licence).</sub>

## Install

1. On the TV, install **Downloader** (by AFTVnews) from the Play Store.
2. Open it and enter `https://github.com/S1rHades/One-TVOS/releases/download/latest/launcher.apk`.
3. Allow Downloader to install unknown apps when Android asks, then choose **Install**.

To make it your home screen, open **Settings** (the gear) → **Home & startup** → **Default home screen**. Android asks you to confirm; after that, Home and closing or killing any app bring you back to Pill Launcher.

Many TVs, most Google TV devices among them, don't let any app replace the home screen. On those, use **Settings → Home & startup → Home button helper**: it opens Accessibility settings, where you turn on **Pill Launcher Home button**. Whenever the TV's own home screen opens (Home pressed, an app closed or killed), the helper brings Pill Launcher straight back. It only checks which app came to the front, never what is on screen. On Android 13 and later, a sideloaded app's accessibility switch may be greyed out at first: open **Settings → Apps → Pill Launcher**, choose **⋮ → Allow restricted settings**, then turn it on.

Until one of these is set up, the home screen shows a reminder with a **Set up** button, which tries the first way and then the second. **Not now** hides it.

If a TV allows neither, a computer with [ADB](https://developer.android.com/tools/adb) connected to the TV can make Pill Launcher the home screen:

```
adb shell cmd package set-home-activity app.pill.launcher/.MainActivity
```

On TVs that ignore that command, turning off the stock launcher works too (turn it back on with `pm enable`). On Google TV, run `adb shell pm disable-user --user 0 com.google.android.apps.tv.launcherx`; on Android TV, run `adb shell pm disable-user --user 0 com.google.android.tvlauncher`.

The `latest` release is rebuilt automatically every time `main` changes, so installing it again always gets you the newest version.

Every version also keeps its own release, named after it (**Pill Launcher 2.6**, tag `v2.6`, file `pill-launcher-2.6.apk`), so an older version stays downloadable: `https://github.com/S1rHades/One-TVOS/releases/download/v2.6/pill-launcher-2.6.apk`. The newest is titled **Pill Launcher 2.6 (latest)**. Releases from 2.5 on are kept; older ones weren't saved.

### Uninstalling

From the launcher: **Settings → TV & system → Uninstall Pill Launcher**, then OK twice and confirm.

If the launcher won't open, install the uninstaller with Downloader instead: `https://github.com/S1rHades/One-TVOS/releases/download/latest/uninstall-pill-launcher.apk`. Open **Uninstall Pill Launcher** from the TV's apps and press OK to remove the launcher; afterwards the same button removes the uninstaller too. It's a separate app (`uninstaller/`) and works on any version of the launcher.

## Using it

| To | Press |
| --- | --- |
| Open an app | **OK** |
| See all apps | **Down** from Favorites |
| Find an app | The magnifier (top right), then type |
| Open Wi-Fi settings | The connection icon (top right, left of the magnifier), then **OK** |
| See if a VPN is on | A green key shows beside the connection icon |
| Change how favourites are laid out | **Settings → Favorites row → Layout**: Scroll or Wrap |
| Banners or icons for favourites | **Settings → Favorites row → Tile style** |
| Wake the idle screen | Any key |
| App menu: pin / unpin, move, app info, uninstall | Hold **OK** for half a second, or press **Menu** (☰) |
| Reorder favourites | App menu → **Move**, then **Left** / **Right**, and **OK** when done |
| Open settings | Go up to the round gear button (top right), then **OK** |
| Change how often the photo changes, or the weather unit | In settings, **Left** / **Right** on that row |
| Turn widgets on or off | **Settings → Widgets**, **OK** on a widget (up to four at once) |
| Reorder widgets | **Settings → Widgets**, **Left** / **Right** on a widget that's on |
| See the forecast, the football page or a headline's summary | Go up to that widget, then **OK** (**Back** closes it) |
| Follow more teams, or change match day | **Settings → Widgets → Football teams** |
| Choose headline topics | **Settings → Widgets → Headlines** |
| Hide the kick-off reminder | **Back** (it also goes by itself after 30 seconds) |
| Free up memory, or open the TV's network, display, sound or update screens | **Settings → TV & system** |
| Uninstall Pill Launcher (OK twice, then confirm) | **Settings → TV & system → Uninstall Pill Launcher** |
| Close settings | **Back** |

Settings and favourites are saved on the TV and survive restarts and updates.

### Weather and forecast

The widget finds the TV's approximate location from its internet address with [GeoJS](https://www.geojs.io/) and gets the forecast from [Open-Meteo](https://open-meteo.com/), or from [MET Norway](https://api.met.no/) if Open-Meteo can't be reached. None of them needs an account or key. It refreshes every 30 minutes. Choose °C or °F in settings. Turn off both the Weather and the Sunrise & sunset widgets, and the launcher contacts none of these services. If the weather can't load, Settings shows why under the Weather widget's row. Sunrise and sunset are worked out on the TV from the same location.

OK on the weather card opens the forecast: now (with "feels like", humidity, wind, UV, sunrise and sunset), the next twelve hours with the chance of rain, and seven days. MET Norway gives no "feels like", UV or chance of rain, so with it the page shows the expected rain in mm instead. The last reading is saved on the TV, so the card and the forecast show at once after a restart and while offline.

### Football

The football widget follows Algeria's men's team (always first) and up to five more teams you add under **Settings → Widgets → Football teams**: search for a club or a country. The card takes turns between them every 20 seconds, each with its next fixture (competition, crests and kick-off in the TV's time zone) and latest result, marked W, D or L. During a match it shows **LIVE** and the score. OK on the card opens the football page: the next match with its venue, the latest result, and the top of the league table where there is one.

**Match day**: on the day a followed team plays, the card stays on that team and counts down to kick-off, and ten minutes before, a banner says so (it never takes focus: **Back** hides it, and it goes by itself after 30 seconds). Choose all teams, Algeria only, or off under **Football teams → Match day**.

The data comes from [TheSportsDB](https://www.thesportsdb.com/)'s free API, with no account needed. The free data has one next match and one latest result per team, and only the top five of a table. Many TVs can't reach TheSportsDB (its Cloudflare protection turns some away), so the **Fixtures mirror** workflow (`.github/workflows/fixtures.yml`, `.github/scripts/mirror.py`) keeps copies on the `fixtures-data` branch for Algeria (every 30 minutes) and for about 120 clubs and national teams listed in `.github/mirror/teams.txt` (each every few hours, and every 30 minutes around its matches). Search shows which teams work on any network; others need the TV to reach TheSportsDB directly. To add a team for every network, add its name to `teams.txt`. The widget refreshes every 30 minutes, and every 3 minutes around a match. Each team's last good copy is saved on the TV.

### Headlines

The headlines widget shows one story at a time from [Al Jazeera English](https://www.aljazeera.com/) and [RT English](https://www.rt.com/), newest first, a new one every 20 seconds. OK opens its summary; **Left** / **Right** there reads the others. Neither site has feeds by topic, so the launcher picks stories itself, by section and by words in the headline and summary, for the topics on under **Settings → Widgets → Headlines**: Algeria, Russia and International politics by default, plus Africa & Middle East, Economy and Sport. If nothing matches, it shows the newest stories as top stories.

Al Jazeera is read from its RSS feed, or from a copy the Fixtures mirror keeps on `fixtures-data` (headline, link, date and short summary only). RT is only ever read directly by the TV: it is banned from distribution in the EU and blocked in some other countries, so it is never copied to GitHub. Where it is blocked, Settings says "Not available on this network" and the widget carries on with Al Jazeera.

### Online photos

By default the slideshow shows a new set of 24 landscape photos each day from [Wikimedia Commons' Featured Pictures](https://commons.wikimedia.org/wiki/Commons:Featured_pictures) (mountains, lakes, forests, waterfalls and coasts). The **Photo feed** workflow (`.github/workflows/photo-feed.yml`, `.github/scripts/photo_feed.py`) picks them each morning, resizes them to 1920×1080 and publishes them with their credits on the `photos-data` branch. The TV reads them from `raw.githubusercontent.com`. The photos are freely licensed (mostly CC BY-SA): the author and licence of the one on screen show on the idle screen and in Settings. Offline, the TV uses the last set it saw and the built-in photos.

### Your own photos

The twelve built-in nature photos (used offline, or with **Online nature photos** off) are part of the app (`app/src/main/assets/photos`), so they show even without internet. To change them, edit the Lorem Picsum ids in `BackgroundSources` (`app/src/main/java/app/pill/launcher/ui/BackgroundSlideshow.kt`) and in `.github/workflows/photos.yml`, then run the **Bundle photos** workflow. `BackgroundSources` also accepts direct `https://` image links.

## Development

You need JDK 17 and the Android SDK (API 34). Android Studio works, or from the command line:

```
gradle assembleDebug                                   # app/build/outputs/apk/debug/app-debug.apk
gradle :app:testDebugUnitTest --tests '*Screenshots*'  # re-render docs/screenshots
```

**Builds are automated.** `.github/workflows/build.yml` builds the APK for every pull request and push; builds of `main` publish a release for the version in `app/build.gradle.kts` (`v2.6`, with the launcher and the uninstaller) and replace the `latest` release. `.github/workflows/screenshots.yml` re-renders the README screenshots at 1920×1080 and commits them; run it from the Actions tab after changing the interface.

<details>
<summary><b>Architecture</b></summary>

```
app/src/main/java/app/pill/launcher/
  MainActivity.kt        launcher activity: loads apps, opens them, keeps pins and settings
  AppModel.kt            lists launchable apps (TV apps first) with their icons
  LauncherSettings.kt    settings and pinned apps in SharedPreferences
  Weather.kt             location and forecast lookup, condition names and units
  Widgets.kt             the widget kinds, the four-widget limit, toggling and reordering
  Fixtures.kt            Algeria's fixtures from TheSportsDB, with an offline copy
  Daylight.kt            sunrise and sunset from latitude and longitude
  ui/
    LauncherScreen.kt    the home screen: a first page with the clock and favourites, All apps below, page scrolling
    AppTile.kt           a focusable squircle app tile
    BackgroundSlideshow.kt  photo cross-fade and the readability scrim
    SettingsPanel.kt     the floating settings card (its own dialog window, so focus stays inside)
    SettingsPill.kt      the round gear button and the gear icon
    WidgetCards.kt       the header's glass widget cards and their row
    WeatherWidget.kt     the weather card's content, with drawn condition icons
    Theme.kt             shared colours and spacing
    Squircle.kt          the superellipse tile shape
app/src/test/.../Screenshots.kt   Robolectric + Roborazzi renders for this README
```

The screen is laid out in dp for a 960×540 dp canvas (a 1080p TV at xhdpi), with 48 dp and 27 dp margins to stay inside the overscan-safe area.

</details>
