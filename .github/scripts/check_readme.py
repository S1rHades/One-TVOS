"""Fails when the README has fallen behind the app.

Checks that:
- every image the README shows exists;
- every screenshot the screenshot tests render is shown in the README (or listed below);
- every source file is named in the README's Architecture list;
- the README and CHANGELOG mention the version in app/build.gradle.kts.

Run from the repository root: python3 .github/scripts/check_readme.py
"""
import pathlib
import re
import sys

ROOT = pathlib.Path(__file__).resolve().parents[2]
SRC = ROOT / "app/src/main/java/app/pill/launcher"
SHOTS_TEST = ROOT / "app/src/test/java/app/pill/launcher/Screenshots.kt"

# Rendered for a test's own check, not for the README
NOT_SHOWN = {"weather-icons-moving.png"}


def main() -> int:
    readme = (ROOT / "README.md").read_text(encoding="utf-8")
    changelog = (ROOT / "CHANGELOG.md").read_text(encoding="utf-8")
    problems = []

    for path in sorted(set(re.findall(r'(?:src="|\]\()((?:docs|app)/[^")\s]+\.(?:png|svg|gif|jpg))', readme))):
        if not (ROOT / path).is_file():
            problems.append(f"README shows {path}, which doesn't exist")

    rendered = set(re.findall(r'"([a-z0-9-]+\.png)"', SHOTS_TEST.read_text(encoding="utf-8")))
    for name in sorted(rendered - NOT_SHOWN):
        if f"docs/screenshots/{name}" not in readme:
            problems.append(f"Screenshot {name} isn't in the README: add it to the section about that feature")

    arch = re.search(r"<summary><b>Architecture</b></summary>(.*?)</details>", readme, re.S)
    arch_text = arch.group(1) if arch else ""
    for kt in sorted(SRC.rglob("*.kt")):
        if not re.search(rf"\b{re.escape(kt.name)}\b", arch_text):
            problems.append(f"{kt.relative_to(ROOT)} isn't in the README's Architecture list")

    version = re.search(r'versionName\s*=\s*"([^"]+)"', (ROOT / "app/build.gradle.kts").read_text()).group(1)
    if f"v{version}" not in readme or f"Pill Launcher {version}" not in readme:
        problems.append(f"README's release examples don't name version {version} (v{version}, Pill Launcher {version})")
    if not re.search(rf"^## {re.escape(version)}\b", changelog, re.M):
        problems.append(f"CHANGELOG.md has no '## {version}' section")

    for p in problems:
        print(f"::error file=README.md::{p}")
    if not problems:
        print("README is up to date")
    return 1 if problems else 0


if __name__ == "__main__":
    sys.exit(main())
