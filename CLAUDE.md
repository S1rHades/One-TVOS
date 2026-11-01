# Working on Pill Launcher

## Keep the README current

Every change that someone using the launcher would notice updates `README.md` in the same pull request:

- **Text:** describe the new or changed behaviour in the section about that feature, and remove anything that is no longer true.
- **Using it table:** add or change the row for any new key, page or setting path.
- **Pictures:** a new or changed screen gets a screenshot test in `app/src/test/java/app/pill/launcher/Screenshots.kt`, and the picture goes in the README section that talks about it (never in a separate gallery), with alt text and a caption that match what it shows. After the Screenshots workflow re-renders, look at the pictures the change affects and fix captions that no longer match.
- **Highlights:** update them for a big feature.
- **Versions:** on a version bump (`versionName` in `app/build.gradle.kts`), update the release examples under Install and add a `## <version>` section to `CHANGELOG.md`.
- **Architecture:** add new source files to the Architecture list.
- **Plans:** when a planned feature ships, remove it from `docs/PLANNED.md`.

`python3 .github/scripts/check_readme.py` checks the mechanical parts (pictures exist, every rendered screenshot is shown, every source file is listed, versions match). It runs in the Build APK workflow, so a stale README fails the build. Wording and captions still need reading.

## Project facts

- There is no local Android SDK here; CI (`build.yml`, `screenshots.yml`) is the compiler and test runner.
- Release: open a PR from the working branch and merge when CI is green; `build.yml` on `main` publishes `v<version>` and `latest`.
