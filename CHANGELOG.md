# Changelog

Pill Launcher is built from `main` on every change; the newest build is always the
[`latest` release](https://github.com/S1rHades/One-TVOS/releases/tag/latest).

## 2.6 (October 2026)

- **Uninstall Pill Launcher** from **Settings → TV & system**. Press OK twice (so a stray press does nothing), then the TV asks to confirm. Afterwards the TV's own home screen takes over. Each release's notes say how to uninstall, right under the install link.
- **Releases named by version**: each version now keeps its own release (**Pill Launcher 2.6**, tag `v2.6`, with `pill-launcher-2.6.apk`), so older versions stay downloadable and are easy to tell apart. `latest` still holds the newest, now titled **Pill Launcher 2.6 (latest)**, at the same download link. 2.5 gets its own release (`v2.5`) when 2.6 is published.

## 2.5 (October 2026)

- **Football for any team**: the Algeria widget is now the football widget. Algeria stays first; add up to five more clubs or national teams under **Settings → Widgets → Football teams**, by searching. The card takes turns between them every 20 seconds. The fixtures mirror now keeps copies for about 120 teams (`.github/mirror/teams.txt`), so they load on TVs that can't reach TheSportsDB; search says which ones need a direct connection.
- **Match day**: on the day a followed team plays, the card turns green and counts down to kick-off, and ten minutes before, a banner says who's about to kick off. It's information only: it never takes focus, **Back** hides it, and it goes by itself after 30 seconds. All teams, Algeria only, or off.
- **Football page**: OK on the football card opens the next match (with venue and countdown), the latest result and the top five of the league table, for each followed team.
- **Forecast**: OK on the weather card opens the forecast: now (feels like, humidity, wind, UV, sunrise and sunset), the next twelve hours and seven days. The last reading is saved, so the weather shows at once after a restart.
- **Headlines widget**: Al Jazeera English and RT English, one story at a time, picked by topic (Algeria, Russia and International politics by default; Africa & Middle East, Economy and Sport too). OK opens the summary. Al Jazeera falls back to a GitHub copy; RT is read directly only and never copied, and where it's blocked the widget carries on with Al Jazeera.
- Widgets with a page take focus like apps (a white edge and a slight lift) when you go up to them.
- The build now also checks the football, weather and headline parsers against saved real answers from each service.

## 2.4 (October 2026)

- **Wakes up awake**: turning the TV on now lands on the home screen, never the idle screen. The idle timer used to keep counting while the TV was off; it now restarts whenever the screen comes on or you come back from an app.
- **Settings in pages**: settings open on a short list (Background, Widgets, Favorites row, Idle screen, Home & startup, TV & system), each with a one-line summary of its current setting. OK opens a category's own page; Back returns to the list.
- **TV & system**: Free up memory (closes apps running in the background and says how much came free), plus shortcuts to the TV's Network, Display, Sound, Storage, System update, Date & time and About screens.
- **Widgets fill the row**: cards share the full width in proportion, so two widgets stretch across it and four tighten into compact layouts, resizing smoothly when you add or remove one. Their contents are centred. The weather card always names the city.
- **System widget**: free storage and memory, each with a bar (amber when running low), the connection with VPN, and the Android version.
- **Idle screen**: the widgets glide to the middle of the screen and grow, then hold perfectly still. The constant slow wander (which looked like jitter) is gone. To spare plasma and OLED screens, they step a few dp to a new spot every 3 minutes instead.

## 2.3 (October 2026)

- **Online nature photos**: the slideshow now shows a new set of 24 landscape photos every day: mountains, waterfalls, coasts and forests from Wikimedia Commons' Featured Pictures. A daily workflow resizes them for TV and publishes them on GitHub, which TVs that refused other photo sites can reach. Offline, the last set (kept on the TV) and the 12 built-in photos fill in. The photographer and licence show on the idle screen and in Settings. Turn it off under **Settings → Online nature photos** to use only the built-in set.

## 2.2.1 (October 2026)

- **Home screen reminder**: until Pill Launcher is the TV's home screen (or the Home button helper is on), closing an app goes to the TV's own launcher. A reminder on the home screen now says so, and **Set up** makes Pill Launcher the home screen where the TV allows it, or opens the helper's switch where it doesn't. The README covers an ADB route for TVs that allow neither.
- The weather card's text no longer wraps: cards share out spare room, allow for a larger TV text size, and the compact card shows the high and low as "11° / 4°".

## 2.2 (October 2026)

- **Status bar**: the capsule at the top right now starts with the connection: Wi-Fi with its signal strength, Ethernet, or crossed out (in red) when offline. Press OK on it to open the TV's Wi-Fi settings. A green key appears beside it while a VPN is on. The white highlight slides across all the buttons.
- The System info widget shows the same connection and says when a VPN is on.
- **App menu**: holding OK on an app (or pressing Menu) now opens a menu instead of pinning straight away: Add to / Remove from Favorites, Move, App info and Uninstall (Uninstall only for apps you installed). The release of the held OK never picks anything by itself.
- **Reorder favourites**: choose Move, then press Left / Right to slide the app along the favourites row, and OK or Back when it's in place.

- **Algeria widget loads on more TVs**: when a TV can't reach TheSportsDB directly, the widget now reads a copy of the fixtures that GitHub refreshes every 30 minutes. If neither works, Settings → Widgets shows why under the Algeria row, and the card says it can't reach the data rather than suggesting the TV is offline.
- The widget cards and the search/settings capsule no longer cast a shadow; their glass edge sets them apart.

## 2.1 (October 2026)

- **Widgets**: the clock and weather are now frosted-glass cards in one band across the top (the divider between them is gone), each with a soft wash of colour that follows what it shows. Cards rise in when turned on, fade out when turned off and glide when moved.
- **Les Fennecs**: a new widget for Algeria's national team, showing the next match (competition, crests, kick-off in your time zone) and the latest result with W / D / L. During a match it shows LIVE and the score. It keeps the last copy for when the TV is offline.
- **Sunrise & sunset** and **System info** (connection and free storage) widgets.
- **Settings → Widgets**: turn each widget on or off, up to four at once. Turning on a fifth explains the limit. Press Left / Right on a widget that's on to move it. The weather's on/off switch moved here; the °C / °F choice stays under Home screen.

## 2.0 (October 2026)

- **Icons with depth**: when you focus an app whose icon has layers, the artwork lifts a little off its background, like on Apple TV.
- **Softer All apps edge**: rows of apps fade gently into the bottom of the screen until you reach them, so it's clear there's more below.
- **Sliding highlight**: in the search and settings capsule, one white highlight glides between the two buttons instead of each lighting up on its own.

## 1.9 (October 2026)

- **Animated weather**: the sun's rays turn, clouds drift, rain and snow fall and lightning flashes, in short bursts when the weather updates and every 40 seconds, so the screen can rest in between. The icons stay still on the idle screen and when the TV's "remove animations" setting is on.
- **Easier to read on bright photos**: the top of the screen is a little darker behind the clock and weather, the faintest text is a little brighter, and frosted panels are slightly deeper. All text now meets the WCAG AA contrast standard on every built-in photo, checked automatically on every build.
- Focus can no longer get lost or end up off screen, however fast you press the remote's arrows.

## 1.8 (October 2026)

**A new look**
- **App artwork**: favourites now show each app's wide TV banner; apps without one get a card made from their icon and colours. Prefer square icons? **Settings → Favorites row → Tile style**.
- **Focus that glows**: the selected app springs up, glows in its own colour, keeps a crisp white outline and shows its name in bold.
- **Ambient light**: the photo picks up a soft wash of the selected app's colour (**Settings → Home screen → Ambient light** to turn it off).
- **Frosted glass** for search, settings, messages and the new combined search-and-settings button.
- **New typeface**: Roboto Flex throughout, with a light, elegant clock.
- All apps sits on a blurred version of the photo, with a cleaner section header; pinned apps carry a small star.

## 1.7.1 (October 2026)

- The favourites row now fades softly into the screen edges: apps beyond the edge recede (fainter and slightly smaller) instead of being cut off, and a slim bar under the row shows where you are in it. This replaces the round arrow markers.

## 1.7 (October 2026)

**New**
- **Search**: a magnifier button next to Settings finds your apps by name, and links to the TV's own search when it has one.
- **Idle screen**: after a while without a key press, the apps fade away and only the clock and weather stay, with a slow photo zoom and a gentle drift that spares plasma and OLED screens. The first key press just wakes it. Set the time (or Off) and the animation in **Settings → Idle screen**.
- **Favorites row settings**: choose **Scroll** (one row that slides and fades into the screen edges) or **Wrap** (as many as fit across, the rest on rows below), and turn the row's animations on or off. Left and Right stop at the ends of a row instead of jumping elsewhere.
- **Open at startup**: show Pill Launcher when the TV turns on (**Settings → Home & startup**). Settings tells you if Android needs one more permission for it.

**Cleaner first page**
- No "Favorites" heading, no pin hints and no "All apps" label: just the clock, the weather and a slightly smaller row of your favourite apps.

## 1.6 (October 2026)

- **Settings → Default home screen** makes Pill Launcher the TV's home screen, so Home and closing or killing an app always bring you back here.
- **Settings → Home button helper**, for TVs that don't allow another home screen (most Google TV devices): once turned on in Accessibility settings, it brings Pill Launcher back whenever the TV's own home screen opens. It only checks which app came to the front, never what's on screen.

## 1.5 (October 2026)

- **Background photos are built into the app**, so they always appear, even on networks that block photo sites. Twelve nature photos: mountains, lakes, coasts, forests and a waterfall.
- **Weather has a second source.** If Open-Meteo can't be reached, the forecast comes from MET Norway instead. Settings names both services if neither answers.

## 1.4 (October 2026)

- **Holding OK now pins and unpins apps** on every remote. Hold for half a second; a quick press still opens the app. Some remotes (especially TV remotes passed through HDMI) never sent the signal the old long press needed.
- The remote's **Menu** (☰) button also pins or unpins the focused app.

## 1.3 (October 2026)

**Photos and weather on more TVs**
- Background photos and the weather now keep retrying until the internet is ready, instead of giving up when the TV starts before Wi-Fi connects.
- Works on older Android TV boxes whose built-in security certificates are out of date.
- If photos or the weather still can't load, **Settings** says why under their rows.
- New hand-picked nature photos: mountains, lakes, coasts, forests and waterfalls.

**Calmer and quicker**
- The clock shows hours and minutes only.
- Scrolling to All apps is quick and smooth instead of a slow crawl.

## 1.2 (October 2026)

**Smoother everyday use**
- **Back** in All apps and **Home** while already home take you back to the first page (Home also closes settings).
- The pin/unpin message now appears at the bottom, so it no longer covers the weather.
- The focus ring on the first favourite is no longer cut off at the left edge.
- The weather fades in when it arrives instead of popping in.
- Focus reliably starts on your first favourite.
- The "All apps" arrow bobs a few times and then rests, so the TV is not redrawing the screen constantly.
- All apps shows "Hold OK to pin", and small grey text is easier to read over bright photos.
- Updates now install over the previous version: builds are signed with one permanent key. (Uninstall once more before installing this version.)

## 1.1 (October 2026)

**A calmer home screen**
- The home screen now shows only the clock, the weather and your favourites. **All apps** waits one press below, and the grid slides up a page at a time with a smooth animation while the photo dims behind it.
- New weather widget beside the clock: current conditions, today's high and low, and your area. Choose °C, °F or Off in settings.
- App icons fill their tile with no see-through backing. Focus (or a pointer) adds a crisp white ring with a small gap, a lift shadow and a gentle bounce.
- Favourites slide into place when you pin or unpin one.
- A sharper, slimmer gear in a round button that turns when focused.
- Settings is now a floating card with grouped options, an accent switch and the weather unit.

## 1.0 (October 2026)

**README and screenshots**
- A README with install steps, remote controls and screenshots rendered from the app itself.

**Home screen**
- The whole home screen now scrolls as one page. Going down into **All apps** moves the clock and favourites out of the way, so the grid uses the full screen instead of a clipped strip at the bottom.
- Focus starts on your first favourite (or first app) instead of the settings button.
- Tiles lift, glow and get a white ring when focused, and draw above their neighbours.
- Pinning or unpinning shows a short confirmation, and focus stays in Favorites after you unpin one.
- A loading message while apps are read, and a hint on how to pin when Favorites is empty.
- Sharper app icons on 1080p TVs, and the date follows your language's own format.

**Settings**
- The panel slides out when closed, with grouped options and descriptions.
- A real on/off switch for the slideshow, and **Left** / **Right** to change how often the photo changes.

## Earlier

- Photo slideshow background, clock and date, settings pill and side panel.
- First version: favourites row and All apps grid for Android TV.
