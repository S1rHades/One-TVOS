"""Builds the online photo feed: a daily set of landscape photos from Wikimedia
Commons' Featured Pictures, resized to 1920x1080 and published with their credits.

The set is the same all day (seeded by the date) and changes every day. Output goes
to out/: the JPEGs and photos.json, which the launcher reads.
"""
import datetime
import hashlib
import html
import io
import json
import os
import random
import re
import sys
import time
import urllib.parse
import urllib.request

from PIL import Image

API = "https://commons.wikimedia.org/w/api.php"
UA = "PillLauncherPhotoFeed/1.0 (https://github.com/S1rHades/One-TVOS)"
CATEGORIES = [
    "Featured pictures of landscapes",
    "Featured pictures of mountains",
    "Featured pictures of lakes",
    "Featured pictures of forests",
    "Featured pictures of waterfalls",
    "Featured pictures of coasts",
]
COUNT = int(os.environ.get("PHOTO_COUNT", "24"))
OUT = "out"


def get(url, tries=3):
    for attempt in range(tries):
        try:
            req = urllib.request.Request(url, headers={"User-Agent": UA})
            with urllib.request.urlopen(req, timeout=60) as r:
                return r.read()
        except Exception as e:  # noqa: BLE001 - retry anything, then give up
            if attempt == tries - 1:
                raise
            print(f"retry {url[:80]}: {e}", file=sys.stderr)
            time.sleep(5 * (attempt + 1))


def api(**params):
    params.update(format="json", formatversion="2")
    return json.loads(get(API + "?" + urllib.parse.urlencode(params)))


def files_in(category):
    titles, cont = [], {}
    while True:
        data = api(action="query", list="categorymembers", cmtitle="Category:" + category,
                   cmtype="file", cmlimit="500", **cont)
        titles += [m["title"] for m in data.get("query", {}).get("categorymembers", [])]
        if "continue" not in data:
            return titles
        cont = {"cmcontinue": data["continue"]["cmcontinue"]}


def plain(text):
    return html.unescape(re.sub(r"<[^>]+>", "", text or "")).strip()


def main():
    pool = set()
    for category in CATEGORIES:
        found = files_in(category)
        print(f"{category}: {len(found)}")
        pool.update(t for t in found if t.lower().endswith((".jpg", ".jpeg")))
    pool = sorted(pool)
    if len(pool) < COUNT:
        sys.exit(f"only {len(pool)} photos found")
    rng = random.Random(datetime.date.today().isoformat())
    rng.shuffle(pool)

    os.makedirs(OUT, exist_ok=True)
    photos = []
    for start in range(0, len(pool), 20):
        if len(photos) >= COUNT:
            break
        batch = pool[start:start + 20]
        data = api(action="query", titles="|".join(batch), prop="imageinfo",
                   iiprop="url|size|extmetadata", iiurlwidth="2560")
        for page in data["query"]["pages"]:
            if len(photos) >= COUNT:
                break
            info = (page.get("imageinfo") or [{}])[0]
            w, h = info.get("width", 0), info.get("height", 0)
            # Wide enough to fill a TV without heavy cropping, and big enough to stay sharp
            if not h or w / h < 1.3 or w / h > 2.4 or w < 1920:
                continue
            meta = info.get("extmetadata", {})
            try:
                image = Image.open(io.BytesIO(get(info["thumburl"]))).convert("RGB")
            except Exception as e:  # noqa: BLE001
                print(f"skip {page['title']}: {e}", file=sys.stderr)
                continue
            # Cover 1920x1080, cropping evenly
            scale = max(1920 / image.width, 1080 / image.height)
            image = image.resize((round(image.width * scale), round(image.height * scale)), Image.LANCZOS)
            left, top = (image.width - 1920) // 2, (image.height - 1080) // 2
            image = image.crop((left, top, left + 1920, top + 1080))
            name = hashlib.sha1(page["title"].encode()).hexdigest()[:12] + ".jpg"
            image.save(os.path.join(OUT, name), "JPEG", quality=84, optimize=True, progressive=True)
            photos.append({
                "file": name,
                "title": plain(meta.get("ObjectName", {}).get("value")) or page["title"][5:].rsplit(".", 1)[0],
                "artist": plain(meta.get("Artist", {}).get("value"))[:80],
                "license": plain(meta.get("LicenseShortName", {}).get("value")),
                "source": info.get("descriptionurl", ""),
            })
            print(f"{name}  {photos[-1]['title'][:60]}  ({photos[-1]['artist'][:30]}, {photos[-1]['license']})")

    if len(photos) < COUNT // 2:
        sys.exit(f"only {len(photos)} usable photos")
    with open(os.path.join(OUT, "photos.json"), "w") as f:
        json.dump({"updated": datetime.datetime.now(datetime.timezone.utc).isoformat(timespec="seconds"),
                   "photos": photos}, f, ensure_ascii=False, indent=1)


if __name__ == "__main__":
    main()
