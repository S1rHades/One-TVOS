"""Keeps the copies Pill Launcher reads from raw.githubusercontent.com when a TV can't reach
TheSportsDB (its Cloudflare protection turns some TVs away) or Al Jazeera itself.

Run by .github/workflows/fixtures.yml every 30 minutes inside a working copy of the
fixtures-data branch. It writes, next to what is already there:

  fixtures.json            Algeria's next and last match (the shape 2.4 and older read)
  teams/<id>.json          the same for every team in .github/mirror/teams.txt
  teams.json               those teams' names, crests, leagues and countries, for search
  tables/<league>.json     the top of the table for leagues those teams play in
  news/aljazeera.json      Al Jazeera English's latest headlines (title, link, summary)

TheSportsDB's free key answers one next and one last match per team and turns away
bursts, so calls are spaced out and each run refreshes a share of the teams (the ones
near a match every time). A failed call never replaces a good copy. RT is never copied.
"""

import datetime as dt
import email.utils
import html
import json
import os
import re
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
import xml.etree.ElementTree as ET

API = "https://www.thesportsdb.com/api/v1/json/123"
UA = "PillLauncher-mirror/2.5 (+https://github.com/S1rHades/One-TVOS)"
ALGERIA = "134516"
SPACING_S = 4.0
LOOKUPS_PER_RUN = 30
TEAMS_PER_RUN = 18
TABLES_PER_RUN = 4


class RateLimited(Exception):
    pass


_last_call = 0.0


def get(url, sportsdb=True):
    global _last_call
    if sportsdb:
        wait = SPACING_S - (time.time() - _last_call)
        if wait > 0:
            time.sleep(wait)
        _last_call = time.time()
    req = urllib.request.Request(url, headers={"User-Agent": UA})
    try:
        with urllib.request.urlopen(req, timeout=25) as r:
            body = r.read().decode("utf-8")
    except urllib.error.HTTPError as e:
        if e.code in (429, 403) and sportsdb:
            raise RateLimited(f"HTTP {e.code}")
        raise
    # Cloudflare's rate limit answers 200 with a bare "error code: 1015"
    if sportsdb and body.lstrip().startswith("error code"):
        raise RateLimited(body.strip())
    return body


def now_iso():
    return dt.datetime.now(dt.timezone.utc).replace(microsecond=0).isoformat().replace("+00:00", "Z")


def load(path, default):
    try:
        with open(path, encoding="utf-8") as f:
            return json.load(f)
    except (OSError, ValueError):
        return default


def save(path, data):
    os.makedirs(os.path.dirname(path) or ".", exist_ok=True)
    tmp = path + ".tmp"
    with open(tmp, "w", encoding="utf-8") as f:
        json.dump(data, f, ensure_ascii=False, separators=(",", ":"))
    os.replace(tmp, path)


def wanted_teams(list_path):
    names = []
    with open(list_path, encoding="utf-8") as f:
        for line in f:
            line = line.strip()
            if line and not line.startswith("#"):
                names.append(line)
    return names


def fixtures_for(team_id):
    """Next and last match as TheSportsDB answers them; None unless at least one has data."""
    nxt = json.loads(get(f"{API}/eventsnext.php?id={team_id}") or "{}")
    last = json.loads(get(f"{API}/eventslast.php?id={team_id}") or "{}")
    if not (nxt.get("events") or last.get("results")):
        return None
    return {"next": nxt, "last": last, "updated": now_iso()}


def lookup(name):
    """The football team TheSportsDB calls [name]: an exact name first, else the first hit."""
    found = json.loads(get(f"{API}/searchteams.php?t=" + urllib.parse.quote(name)) or "{}").get("teams") or []
    soccer = [t for t in found if t.get("strSport") == "Soccer"]
    exact = [t for t in soccer if t.get("strTeam", "").casefold() == name.casefold()]
    t = (exact or soccer or [None])[0]
    if t is None:
        return None
    league = t.get("strLeague") or ""
    return {
        "id": t["idTeam"],
        "name": t["strTeam"],
        "badge": t.get("strBadge") or "",
        "league": "" if league.startswith("_") else league,
        "country": t.get("strCountry") or "",
        "query": name,
    }


def near_match(copy, now):
    """Whether a saved copy has a match within three hours of now, or one being played."""
    for key, field in (("next", "events"), ("last", "results")):
        for e in (copy.get(key) or {}).get(field) or []:
            stamp = e.get("strTimestamp")
            if e.get("strStatus") in ("1H", "HT", "2H", "ET", "P", "LIVE"):
                return True
            if stamp:
                try:
                    at = dt.datetime.fromisoformat(stamp[:19]).replace(tzinfo=dt.timezone.utc)
                except ValueError:
                    continue
                if abs((at - now).total_seconds()) < 3 * 3600:
                    return True
    return False


def indexed(known, names):
    """The list in teams.txt order, Algeria first."""
    teams = [known[n] for n in names if n in known]
    if not any(t["id"] == ALGERIA for t in teams):
        teams.insert(0, {"id": ALGERIA, "name": "Algeria", "badge": "", "league": "FIFA World Cup", "country": "Algeria", "query": "Algeria"})
    return {"teams": teams, "updated": now_iso()}


def football(out, list_path):
    state = load(os.path.join(out, "state.json"), {"teams": {}, "tables": {}})
    index = load(os.path.join(out, "teams.json"), {"teams": []})
    names = wanted_teams(list_path)
    known = {t["query"]: t for t in index["teams"] if t.get("query") in names}
    try:
        # Algeria every run, also under the old name that 2.4 reads
        alg = fixtures_for(ALGERIA)
        if alg:
            save(os.path.join(out, "fixtures.json"), alg)
            save(os.path.join(out, "teams", f"{ALGERIA}.json"), alg)
            state["teams"][ALGERIA] = alg["updated"]

        # Look up teams new to the list (a share per run); drop ones no longer listed
        # A name TheSportsDB didn't know is tried again a day later
        missing = state.setdefault("missing", {})
        today = now_iso()[:10]
        for name in [n for n in names if n not in known and missing.get(n) != today][:LOOKUPS_PER_RUN]:
            team = lookup(name)
            if team:
                known[name] = team
                missing.pop(name, None)
            else:
                missing[name] = today
                print(f"not found: {name}", file=sys.stderr)
        index = indexed(known, names)

        # Refresh: teams near a match every run, then the longest-waiting ones
        now = dt.datetime.now(dt.timezone.utc)
        ids = [t["id"] for t in index["teams"] if t["id"] != ALGERIA]
        copies = {i: load(os.path.join(out, "teams", f"{i}.json"), {}) for i in ids}
        hot = [i for i in ids if near_match(copies[i], now)]
        rest = sorted((i for i in ids if i not in hot), key=lambda i: state["teams"].get(i, ""))
        for team_id in (hot + rest)[: max(TEAMS_PER_RUN, len(hot))]:
            copy = fixtures_for(team_id)
            if copy:
                save(os.path.join(out, "teams", f"{team_id}.json"), copy)
                copies[team_id] = copy
            state["teams"][team_id] = now_iso()

        # Tables for the leagues those matches are in (the free key gives the top five)
        leagues = {}
        for copy in list(copies.values()) + [alg or {}]:
            for key, field in (("next", "events"), ("last", "results")):
                for e in (copy.get(key) or {}).get(field) or []:
                    if e.get("idLeague") and e.get("strSeason"):
                        leagues[e["idLeague"]] = e["strSeason"]
        for league in sorted(leagues, key=lambda l: state["tables"].get(l, ""))[:TABLES_PER_RUN]:
            body = json.loads(get(f"{API}/lookuptable.php?l={league}&s=" + urllib.parse.quote(leagues[league])) or "{}")
            if body.get("table"):
                save(os.path.join(out, "tables", f"{league}.json"), body)
            state["tables"][league] = now_iso()
    except RateLimited as e:
        # Keep what this run got; the rest waits for the next run
        print(f"TheSportsDB rate limit, stopping for this run: {e}", file=sys.stderr)
    finally:
        # Whatever was looked up before a rate limit is kept
        index = indexed(known, names)
        save(os.path.join(out, "teams.json"), index)
        save(os.path.join(out, "state.json"), state)
    print(f"teams in list: {len(index['teams'])}, refreshed: {sum(1 for v in state['teams'].values() if v)}")


def plain(text):
    text = html.unescape(html.unescape(text or ""))
    text = re.sub(r"<[^>]+>", " ", text)
    return re.sub(r"\s+", " ", text).strip()


def news(out):
    """Al Jazeera English's feed, reduced to what the headlines card shows."""
    try:
        root = ET.fromstring(get("https://www.aljazeera.com/xml/rss/all.xml", sportsdb=False))
    except Exception as e:  # keep the last good copy
        print(f"Al Jazeera: {e}", file=sys.stderr)
        return
    items = []
    for item in root.iter("item"):
        title = plain(item.findtext("title"))
        link = (item.findtext("link") or "").strip()
        if not title or not link:
            continue
        published = ""
        try:
            published = email.utils.parsedate_to_datetime(item.findtext("pubDate")).astimezone(dt.timezone.utc).isoformat().replace("+00:00", "Z")
        except (TypeError, ValueError):
            pass
        m = re.match(r"https?://[^/]+/([a-z-]+)/", link)
        items.append({
            "title": title,
            "link": link,
            "summary": plain(item.findtext("description"))[:400],
            "published": published,
            "section": m.group(1) if m else "",
        })
    if items:
        save(os.path.join(out, "news", "aljazeera.json"), {"items": items, "updated": now_iso()})
    print(f"Al Jazeera: {len(items)} headlines")


if __name__ == "__main__":
    out, list_path = sys.argv[1], sys.argv[2]
    news(out)
    football(out, list_path)
