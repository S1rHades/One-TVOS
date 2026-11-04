# Changelog

Pill Launcher is built from `main` on every change; the newest build is always the
[`latest` release](https://github.com/S1rHades/One-TVOS/releases/tag/latest).

## 3.4.3 (November 2026)

- **Prayer times by mosque ID**: type the number at the bottom left of the mosque's MAWAQIT screen (or "ID 1234" as it shows it) under **Settings → Widgets → Prayer times**. A mawaqit.net address still works too.
- **Times straight from MAWAQIT**: the times are now read from the mosque's own MAWAQIT page, where its screen gets them, and the community API is only a fallback, so the widget no longer depends on that third-party server. When nothing works, the message names what each source answered.

## 3.4.2 (November 2026)

- **Files: scanning the code** still ended in Chrome's ERR_TOO_MANY_RETRIES for some phones. The TV now always answers: when something goes wrong while it answers a phone, the phone gets a page saying what, instead of a connection closed with no answer (which is what makes Chrome retry until it gives up). The Files page also shows the last phone that reached the TV, from which address, and how that went, so a scan that never reaches the TV (a phone on mobile data or a VPN, say) can be told apart from one that does.
- **Prayer times**: a mosque address MAWAQIT doesn't know now says so ("MAWAQIT has no mosque at …"), instead of "Can't reach MAWAQIT: HTTP 404". Every failed check shows when it was made, so **Refresh** visibly tries again. The settings page explains how to find a mosque's address (MAWAQIT doesn't allow other apps to search its mosques).

## 3.4.1 (November 2026)

- **Weather for your own town**: the weather was for the place the TV's internet address points to, often the nearest city. **Settings → Widgets → Weather location** now lets you choose a town by name, using Open-Meteo's place search (it covers small towns and villages), or type a latitude and longitude. The forecast, sunrise and sunset are then for that exact place. **Automatic** keeps the old behaviour.

## 3.4 (November 2026)

- **Prayer times widget**: today's five prayers from your mosque on [MAWAQIT](https://mawaqit.net), through the community API. The card names the mosque, shows the next prayer with its time and a countdown that ticks every minute, and the five along the bottom with the next one marked; compact, it shows the one after instead. **OK** opens the prayer page: the next prayer large, the day as a line from Fajr to Isha, every prayer with its adhan and iqama times, when the times last came, and a **Refresh** button. Times are saved on the TV, so the card has them at once after a restart or offline (marked as saved), and they refresh on their own just after midnight; failures retry and keep the saved copy. Choose the mosque under **Settings → Widgets → Prayer times** (the name from its MAWAQIT address; the whole address pastes too), with **Refresh now** and, for anyone running their own copy of the API, its address.

## 3.3.4 (November 2026)

- **Notification bar focus**: the bell and Files no longer show a bump when focused (their white glow was cut off by the bar's rounded edge; the white disc alone marks focus now).
- **Down from any widget** lands at the start of the notification bar (the newest notification, or "All caught up") instead of skipping the bar or jumping to the bell; "All caught up" also takes focus across the whole bar.
- **Down from the notification bar** always goes to the first app in Favorites, at the left, wherever on the bar you were.

## 3.3.3 (November 2026)

- **Fix**: scanning the Files code could end in Chrome's "This site can't be reached" (ERR_TOO_MANY_RETRIES). Pairing now answers with the page straight away instead of sending the phone to a second address, and connections the browser opens ahead of time and never uses are closed quietly instead of being answered with an error the browser later trips over.

## 3.3.2 (November 2026)

- **Files asks before deleting an APK**: once the app inside an APK is installed, the notification bar asks instead of deleting it. **OK** offers **Keep it** (focused first) or **Delete**; holding **OK** keeps it, and each APK is asked about once. The setting is now **Offer to delete installed APKs** (Settings → Files). Nothing in Files is deleted without you, apart from **Remove files after 30 days** when you turn it on.

## 3.3.1 (November 2026)

- **Icons only**: favourite banners are gone, along with the Tile style setting. Favorites are app icons, and the launcher no longer keeps every app's banner in memory.
- **Lighter behind other apps**: the slideshow waits while another app is in front, instead of downloading and blurring photos nobody sees. The app list is read again only when an app is installed, removed or updated (and after an hour away), so Home comes back faster. One network watcher serves the whole launcher. Widget data still refreshes, so it's current the moment you return.
- **Files pairing**: scanning the QR code pairs a phone at once (it carries a long random key); typing the new six-digit code asks **Allow** or **Block** on the TV. Paired phones stay trusted for 90 days, listed in the new **Settings → Files**, where one can be removed or every phone signed out. Sharing listens only on the TV's home-network address, answers only requests made to that address and, for changes, from its own page, and handles at most 4 connections and 2 uploads at once.
- **Files runs while the launcher is on screen**, so a phone can send without the Files page open; **Keep receiving** now means 15 more minutes after another app comes to the front.
- **Files tidies itself**: an APK goes once its app is installed (on by default), and optionally files go after 30 days, with a notice the day before.
- **Hide private messages** (Settings → Notifications): another app's private notification shows as just "New notification".
- **Safe mode**: two crashes within a minute start the launcher with only the photo and Favorites, saying what failed, with **Try normally**.

## 3.3 (November 2026)

- **Notification bar**: a new glass bar under the widgets, as wide as them. The newest notification is on the left (**OK** does what it says, holding **OK** dismisses it); the bell and **Files** are on the right. **OK** on the bell opens the bar downwards into every notification, with **Clear all**; a red dot marks anything new, and the bell swings when something arrives.
- **What it shows**: Pill Launcher's own notices (an update, "Updated to …" with what was new, why an update failed, kick-off reminders, goals and wins, the connection dropping and coming back, files received) and, once the TV allows Notification access, other apps' notifications. **Settings → Notifications** sets which, and mutes single apps.
- **Updates** and the **kick-off reminder** moved into the notification bar: the update card above Favorites and the banner at the bottom are gone.
- **Files**: a page of its own, from the notification bar. Scan its code with a phone and send photos, videos, apps or documents from the phone's browser, with nothing to install, and save the TV's files back to the phone. Files show as cards with chips by kind; **OK** opens one (photos in the launcher's own viewer, an APK with Android's installer), and holding **OK** offers **Send to phone** and **Delete**. Sharing runs while the page is open, or 15 more minutes with **Keep receiving**, and only for the home network and the code shown on the TV.

## 3.2.1 (November 2026)

- **Updates**: downloading tries a second link (the version's own release), waits longer on slow connections, and says what went wrong, separately for the download and the install. If Android's background installer refuses, its own install screen opens instead.
- **Settings → Updates**: every line of what's new can be reached with **Down**, so the page scrolls to the end.
- **Idle screen**: the update card and the home-screen reminder no longer show over the widgets.

## 3.2 (November 2026)

- **Football card**: a thin line along its bottom fills up until the next team takes over. With the card focused it waits, and **Left** / **Right** switch between the teams by hand (also inside a widget stack).
- **Football page**: the league table column is gone (the free data has nothing worth showing there); the next match and the latest result fill the page.
- **All apps**: going down into it is now a page turn. The first page fades and settles back, All apps rises into place and its apps wave in, and the scroll eases in more slowly. The line beside the All apps title, which ran under the bar, is gone.
- **Chips**: the focused chip lifts and glows, instead of the dark ring.
- **Internet page**: it opens with focus on **Network settings**; **Down** goes to **Start test**.

## 3.1.2 (November 2026)

- **Internet page**: **Network settings** moved to the top left, level with the bar and above the Internet title. **Start test** stays in the dial's card.
- **Settings from the bar** opens over the page you're on (the Internet page, a widget's page, Search or All apps) instead of going back to the home screen; Back returns to that page.
- **Bar**: the grey disc stays on the page you're on while the white focus disc moves along the bar.

## 3.1.1 (November 2026)

- **Fix**: Down from the bar at the top right now always moves: to the widgets when one takes focus, else the first favourite, and to the top of All apps when that is on screen (or there is nothing else). Before, focus stayed stuck on the bar.
- **Fix**: Home in the bar while Settings is open now goes to the first favourite, rather than back to the gear.
- **Bar**: the name no longer pops up under the focused icon; the white disc shows where you are.
- **Bar**: the Internet button is always a plain Wi-Fi symbol, also on a wired connection (red and crossed out when offline).
- **All apps**: going down into the chips lands on the selected one.
- **Idle screen**: no more text on the photo. The place and photographer moved off the screen; the full credit and licence are still under **Settings → Background**.

## 3.1 (November 2026)

The five planned features, a navigation bar at the top right, and a quieter greeting.

- **Navigation bar**: Home, Search, then a divider, Wi-Fi and Settings, in a glass bar at the top right that stays put on every page and panel. Pages, Settings and Search now start below it, so nothing sits under it. Up from the widgets reaches it. All apps has no button any more: it is where it always was, below Favorites.
- **Greeting**: just "Good evening" (or morning, afternoon, night), level with the bar. The date and place were already on the clock and weather cards.
- **Rename apps**: the app menu has **Rename…**. The new name shows everywhere in the launcher (Favorites, All apps, search); the app itself keeps its own, and **Use its own name** brings it back. **App info** stays in the menu.
- **Widget stack**: a new widget that holds several others and slides up to the next every 8 seconds, with dots down its side. With it focused, **Up** / **Down** flips through by hand and OK opens the page of the one showing. Choose what goes in it under **Settings → Widgets → Widget stack**; hold OK on one, then **Left** / **Right**, to move it, the same way as moving a favourite. While Settings → Widgets is open, the home screen steps aside to the left as a live preview, undimmed.
- **Update from the launcher**: when a newer release is on GitHub, a card above Favorites says so. **Update** downloads it with a progress bar, then Android asks to confirm; settings and favourites stay. **Settings → Updates** checks by hand, shows what's new, and turns the automatic check off.
- **Letter strip search**: Search opens on a strip of letters. **Left** / **Right** and OK spell a name without the keyboard, the results narrow as you go, and letters no app's name continues with are dimmed. The TV keyboard is still on the search field.
- **Goals and wins**: when a team you follow scores, its football card flashes green, the score flips to the new one and the card says "Goal · Algeria 78'". A win ends at full time in a short burst of confetti. A widget stack holding the football card brings it forward. Everything stays inside the card: long team names give way with an ellipsis before the score does, and nothing moves on the idle screen.

## 3.0 (October 2026)

A new look, built from the 3.0 mockup video.

- **Side rail**: Home, All apps, Search, Internet and Settings now sit in a slim rail down the left, instead of the buttons at the top right. A disc marks where you are; go Left onto the rail and it turns white and glides with you, and the item's name appears beside it.
- **Greeting**: "Good evening" (or morning, afternoon, night) above the widgets, with the date and your city. It goes with the rail and favourites when the screen rests.
- **Glass motion**: everything now moves the same way. Pages and panels arrive frosted and come into focus, their parts in a wave from top left; the photo goes soft behind them; markers glide from item to item instead of jumping. Going idle and waking up use the same dissolve. On TVs older than Android 12 the frost is left out and the rest stays.
- **All apps**: chips above the grid (All, Video, Music, Games, Other) filter it, with a white pill gliding between them; apps show as frosted cards in each app's own colour. **Apps** on the rail jumps straight there.
- **Weather page**: OK on the weather card opens a dashboard: feels like (with the next day's trend), humidity, wind and UV across the top; the week as a chart of each day's range, which follows **Left** / **Right** with the day's details; and a Today list (the next sunrise or sunset, when rain is likely, the warmest or coolest hour to come, and tomorrow).
- **Football page**: three columns. Your teams down the left (**Up** / **Down** picks one), the match centre with a countdown in days, hours and minutes and the latest result, and the team with where it stands and the top of its table.
- **News page**: OK on the headlines card lists every headline, with the story beside it; **Up** / **Down** moves through them.
- **Internet page** (new, on the rail): **Start test** measures ping, download (on a dial, with a graph as it runs) and upload against Cloudflare's speed test, then says what the speed is good for. **Test again** runs it again; **Network settings** opens the TV's own. The last result is kept.

## 2.8 (October 2026)

- **Wallpaper tinting**: the launcher takes a little of each photo's own colour (amber at sunset, sea blue on the coast, leaf green in a forest). It shows in the glass of the widgets, panels and pages, their edges, the clock and weather cards, and the glow under the app you're on. It drifts to the next photo's colour over three seconds, after the photos have changed. Focus outlines and highlighted rows stay white, and the match-day green and headline pink keep their meaning. The build checks that text stays readable on the tinted glass over every bundled photo.
- **Smoother wake-up**: leaving the idle screen no longer lurches. The widgets glide back over about a second, starting and ending gently. The apps and buttons fade in just after they set off. The photo eases out of its slow zoom, and the small anti-burn-in offset glides back instead of jumping.
- **Livelier weather icons**: they now move all the time, gently, instead of in short bursts. The sun's glow breathes and its rays turn and shimmer, cloud puffs swell and drift (two layers at their own pace when it's cloudy), rain and snow fall steadily, fog rolls, lightning flickers, and stars twinkle by the moon. They pause on the idle screen and with Android's "remove animations".

## 2.7 (October 2026)

- **Clock page**: the clock card now takes focus like the other widgets, and OK opens a page with the time to the second, the full date and the Islamic (Umm al-Qura) date, this month's calendar, and the time in Algiers, London, Mecca, Moscow and New York.
- **APK files named by version**: every release's APK is now `pill-launcher-2.7.apk` (and so on), the latest release included, so downloaded files can be told apart. `latest/launcher.apk` stays as the link that always gets the newest.
- **Focused widgets aren't cut off**: the card at either end of the row used to lose its edge when it grew on focus.
- **Ambient light removed**: the photo is no longer tinted with the focused app's colour, and the setting is gone.
- **Quieter idle screen**: the line at the bottom now says only where the photo was taken and who took it. The full credit and licence stay in Settings → Background.

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
