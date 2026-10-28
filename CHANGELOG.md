# Changelog

Pill Launcher is built from `main` on every change; the newest build is always the
[`latest` release](https://github.com/S1rHades/One-TVOS/releases/tag/latest).

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
