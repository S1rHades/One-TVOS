package app.pill.launcher

import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import androidx.compose.ui.graphics.toArgb
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * The parsers and rules behind the football, weather and headlines widgets, checked
 * against real answers saved from each service (src/test/resources/data).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DataTest {

    private fun data(name: String): String = javaClass.classLoader!!.getResource("data/$name")!!.readText()

    private val utc = ZoneId.of("UTC")

    // ---- Football ----------------------------------------------------------------------

    @Test
    fun algeriaFixtures() {
        val f = parseFixtures(data("next-algeria.json"), data("last-algeria.json"), Instant.now())
        val next = f.next!!
        assertEquals("Algeria", next.ours.name)
        assertEquals("Togo", next.opponent.name)
        assertEquals("5520", next.leagueId)
        assertEquals("2027", next.season)
        assertEquals("Stade Nelson-Mandela", next.venue)
        assertEquals(Instant.parse("2026-11-11T17:00:00Z"), next.kickoff)
        val last = f.last!!
        assertEquals('W', last.outcome)
        assertEquals(2, last.ourScore)
        assertTrue(last.isFinished)
    }

    /** "Ours" follows the followed team, home or away. */
    @Test
    fun awayTeamPerspective() {
        val arsenal = Team("133604", "Arsenal")
        val f = parseFixtures(data("next-arsenal.json"), data("last-arsenal.json"), Instant.now(), arsenal)
        assertEquals("Arsenal", f.next!!.ours.name)
        assertEquals("Liverpool", f.next!!.opponent.name)
        assertEquals('W', f.last!!.outcome)
        assertNotNull("the crest comes from the feed", f.team.badge)
    }

    @Test
    fun teamSearchAndTable() {
        val hits = parseTeamSearch(data("search-mcalger.json"))
        assertEquals("MC Alger", hits.single().name)
        assertEquals("Algerian Ligue 1", hits.single().league)
        val table = parseTable(data("table-epl.json"))
        assertEquals(5, table.rows.size)
        assertEquals(1, table.rows.first().rank)
        assertEquals("4328", table.leagueId)
    }

    @Test
    fun localSearchIgnoresCaseAndAccents() {
        val teams = listOf(Team("1", "ES Sétif", league = "Algerian Ligue 1"), Team("2", "Arsenal", country = "England"), Team("3", "MC Alger"))
        assertEquals(listOf("ES Sétif"), searchTeams(teams, "setif").map { it.name })
        assertEquals(listOf("MC Alger", "ES Sétif"), searchTeams(teams, "alger").map { it.name })
        assertTrue(searchTeams(teams, "  ").isEmpty())
    }

    @Test
    fun followedTeamsKeepAlgeriaFirst() {
        assertEquals(listOf(Algeria), teamsFromJson(null))
        assertEquals(listOf(Algeria), teamsFromJson("not json"))
        val arsenal = Team("133604", "Arsenal", league = "English Premier League")
        val stored = teamsToJson(listOf(arsenal, Algeria))
        assertEquals(listOf(AlgeriaId, "133604"), teamsFromJson(stored).map { it.id })
        assertEquals("English Premier League", teamsFromJson(stored)[1].league)
        val many = (1..10).map { Team("$it", "Team $it") }
        assertEquals(MaxTeams, withAlgeriaFirst(many).size)
        assertTrue(withAlgeriaFirst(many).first().isAlgeria)
    }

    /** Saved layouts from before 2.5 name the football widget "Fennecs"; they still load. */
    @Test
    fun oldWidgetLayoutsLoad() {
        assertEquals(listOf(WidgetKind.Clock, WidgetKind.Weather, WidgetKind.Fennecs), parseWidgets("Clock,Weather,Fennecs"))
        assertEquals(listOf(WidgetKind.Headlines, WidgetKind.Clock), parseWidgets("Headlines,Gone,Clock"))
    }

    @Test
    fun settingsRoundTrip() {
        val store = SettingsStore(ApplicationProvider.getApplicationContext())
        val fresh = store.load()
        assertEquals(listOf(Algeria), fresh.teams)
        assertEquals(MatchDayMode.All, fresh.matchDay)
        assertEquals(NewsTopic.Defaults, fresh.newsTopics)
        val changed = fresh.copy(
            teams = listOf(Algeria, Team("139283", "MC Alger")),
            matchDay = MatchDayMode.AlgeriaOnly,
            newsTopics = setOf(NewsTopic.Russia, NewsTopic.Economy),
            widgets = listOf(WidgetKind.Clock, WidgetKind.Headlines),
        )
        store.save(changed)
        val loaded = store.load()
        assertEquals(changed.teams.map { it.id }, loaded.teams.map { it.id })
        assertEquals(changed.matchDay, loaded.matchDay)
        assertEquals(changed.newsTopics, loaded.newsTopics)
        assertEquals(changed.widgets, loaded.widgets)
    }

    // ---- Match day ---------------------------------------------------------------------

    private fun event(id: String, home: String, homeId: String, away: String, awayId: String, at: LocalDateTime, status: String = "NS", hs: Int? = null, aws: Int? = null) =
        """{"idEvent":"$id","strHomeTeam":"$home","idHomeTeam":"$homeId","strAwayTeam":"$away","idAwayTeam":"$awayId",""" +
            """"strLeague":"African Cup of Nations Qualifying","idLeague":"5520","strSeason":"2027","strTimestamp":"$at:00",""" +
            """"intHomeScore":${hs ?: "null"},"intAwayScore":${aws ?: "null"},"strStatus":"$status"}"""

    private fun algeriaAt(kickoff: LocalDateTime, status: String = "NS"): Fixtures =
        parseFixtures("""{"events":[${event("1", "Algeria", AlgeriaId, "Togo", "136484", kickoff, status)}]}""", """{"results":[]}""", Instant.now())

    @Test
    fun matchDayTimeline() {
        val kickoff = LocalDateTime.of(2026, 11, 11, 17, 0)
        val f = algeriaAt(kickoff)
        fun at(h: Int, m: Int = 0) = LocalDateTime.of(2026, 11, 11, h, m).toInstant(ZoneOffset.UTC)
        assertEquals(MatchPhase.None, matchPhase(f, LocalDateTime.of(2026, 11, 10, 20, 0).toInstant(ZoneOffset.UTC), utc))
        assertEquals(MatchPhase.Today, matchPhase(f, at(9), utc))
        assertEquals(MatchPhase.Today, matchPhase(f, at(16, 49), utc))
        assertEquals(MatchPhase.Soon, matchPhase(f, at(16, 50), utc))
        assertEquals(MatchPhase.Live, matchPhase(f, at(17, 30), utc))
        assertEquals(MatchPhase.None, matchPhase(algeriaAt(kickoff, "FT"), at(19), utc))
    }

    @Test
    fun kickoffBanner() {
        val kickoff = LocalDateTime.of(2026, 11, 11, 17, 0)
        val f = algeriaAt(kickoff)
        fun at(h: Int, m: Int) = LocalDateTime.of(2026, 11, 11, h, m).toInstant(ZoneOffset.UTC)
        assertNull(dueBanner(listOf(f), MatchDayMode.All, at(16, 49), emptySet()))
        val due = dueBanner(listOf(f), MatchDayMode.All, at(16, 51), emptySet())
        assertNotNull(due)
        assertEquals("Les Fennecs kick off in 9 minutes", bannerTitle(due!!.first, due.second, at(16, 51)))
        assertNotNull("a TV switched on just after kick-off still sees it", dueBanner(listOf(f), MatchDayMode.All, at(17, 3), emptySet()))
        assertNull(dueBanner(listOf(f), MatchDayMode.All, at(17, 6), emptySet()))
        assertNull("once dismissed it stays away", dueBanner(listOf(f), MatchDayMode.All, at(16, 55), setOf("1")))
        assertNull(dueBanner(listOf(f), MatchDayMode.Off, at(16, 55), emptySet()))
    }

    @Test
    fun algeriaOnlyMatchDay() {
        val kickoff = LocalDateTime.now(ZoneOffset.UTC).plusMinutes(5).withSecond(0).withNano(0)
        val club = Team("139283", "MC Alger")
        val clubDay = parseFixtures("""{"events":[${event("2", "MC Alger", club.id, "CRB", "1", kickoff)}]}""", "{}", Instant.now(), club)
        val quiet = algeriaAt(kickoff.plusDays(9))
        val now = Instant.now()
        assertNotNull(dueBanner(listOf(quiet, clubDay), MatchDayMode.All, now, emptySet()))
        assertNull(dueBanner(listOf(quiet, clubDay), MatchDayMode.AlgeriaOnly, now, emptySet()))
        // On a match day the card stays on the team that plays
        assertEquals(listOf(club.id), cardTeams(listOf(quiet, clubDay), MatchDayMode.All, now, utc).map { it.team.id })
        assertEquals(2, cardTeams(listOf(quiet, clubDay), MatchDayMode.AlgeriaOnly, now, utc).size)
        assertEquals("MC Alger kick off in 1 minute", bannerTitle(club, clubDay.next!!, kickoff.minusSeconds(40).toInstant(ZoneOffset.UTC)))
    }

    // ---- Weather -----------------------------------------------------------------------

    @Test
    fun openMeteoForecast() {
        val w = parseOpenMeteo(data("openmeteo.json"), "Lisbon", 38.7, -9.1, Instant.now())
        val f = w.forecast!!
        assertEquals(16.1, w.tempC, 0.01)
        assertEquals(12, f.hours.size)
        assertEquals(7, f.days.size)
        assertEquals(15.6, f.feelsLikeC!!, 0.01)
        assertEquals(68, f.humidity)
        assertEquals("N", compassPoint(f.windFromDeg!!))
        assertEquals(f.days.first().highC, w.highC, 0.01)
        assertTrue(f.days.all { it.highC >= it.lowC })
        assertEquals("Open-Meteo", f.source)
    }

    @Test
    fun metNorwayForecast() {
        val w = parseMetNorway(data("metno.json"), "Lisbon", 38.7, -9.1, Instant.now(), ZoneId.of("Europe/Lisbon"))
        val f = w.forecast!!
        assertEquals(18.6, w.tempC, 0.01)
        assertTrue(w.highC >= w.tempC && w.lowC <= w.tempC)
        assertEquals(24, f.hours.size)
        assertTrue(f.days.size in 5..7)
        assertTrue(f.days.all { it.highC >= it.lowC && it.rainMm != null })
        assertNull("MET Norway has no feels-like", f.feelsLikeC)
        assertEquals(10.4, f.windKmh!!, 0.1) // 2.9 m/s
        // Days follow each other without gaps
        f.days.zipWithNext { a, b -> assertEquals(a.date.plusDays(1), b.date) }
    }

    @Test
    fun compass() {
        assertEquals("N", compassPoint(350.0))
        assertEquals("NW", compassPoint(315.0))
        assertEquals("E", compassPoint(95.0))
        assertEquals("N", compassPoint(-10.0))
    }

    // ---- Headlines ---------------------------------------------------------------------

    @Test
    fun alJazeeraFeed() {
        val items = parseRss(data("aljazeera.xml"), NewsSource.AlJazeera)
        assertEquals(25, items.size)
        val first = items.first()
        assertTrue(first.title.startsWith("Patrick Stewart"))
        assertEquals("Stewart died in Los Angeles after living with Alzheimer's disease and Lewy body dementia.", first.summary)
        assertEquals("editorial", first.section)
        assertEquals(Instant.parse("2026-10-29T22:36:06Z"), first.published)
        assertTrue(items.any { it.section == "news" })
    }

    @Test
    fun rtFeed() {
        val items = parseRss(data("rt.xml"), NewsSource.RT)
        assertEquals(12, items.size)
        for (h in items) {
            assertFalse(h.summary, "<" in h.summary)
            assertFalse(h.summary, "Read Full Article" in h.summary)
            assertTrue(h.title.isNotBlank())
        }
        assertTrue(items.first().summary.startsWith("Binance is investigating"))
    }

    /** A stray character half-way through a feed keeps the stories before it. */
    @Test
    fun brokenFeedKeepsWhatItRead() {
        val xml = data("rt.xml")
        val cut = xml.indexOf("<item>", xml.indexOf("<item>", xml.indexOf("<item>") + 1) + 1)
        val broken = xml.substring(0, cut) + "<item><title>Broken & <here</title>"
        assertEquals(2, parseRss(broken, NewsSource.RT).size)
    }

    @Test
    fun topics() {
        val russia = parseRss(data("rt-russia.xml"), NewsSource.RT)
        assertTrue(russia.all { it.section == "russia" })
        val aj = parseRss(data("aljazeera.xml"), NewsSource.AlJazeera)
        val news = News(mapOf(NewsSource.RT to russia, NewsSource.AlJazeera to aj), emptyMap(), Instant.now())

        val picked = pickHeadlines(news, setOf(NewsTopic.Russia))
        assertTrue(picked.isNotEmpty())
        assertTrue(picked.all { it.topic == NewsTopic.Russia })

        // Newest first, across both sources
        val all = pickHeadlines(news, NewsTopic.entries.toSet(), limit = 50)
        val times = all.mapNotNull { it.headline.published }
        assertEquals(times.sortedDescending(), times)

        // Nothing matches: the newest stories, as top stories
        val none = pickHeadlines(News(mapOf(NewsSource.AlJazeera to aj.take(3)), emptyMap(), Instant.now()), setOf(NewsTopic.Algeria))
        assertTrue(none.isNotEmpty() && none.all { it.topic == null })

        val algeria = Headline("Algeria and Togo meet in AFCON qualifier", "https://www.aljazeera.com/sports/x", "", null, NewsSource.AlJazeera, "sports")
        assertEquals(NewsTopic.Algeria, pickHeadlines(News(mapOf(NewsSource.AlJazeera to listOf(algeria)), emptyMap(), Instant.now()), NewsTopic.entries.toSet()).single().topic)
        // Whole words only: "Unite" is not the UN, "Moscow" is Russia
        assertFalse(NewsTopic.World.matches(Headline("Unite for a cause", "", "", null, NewsSource.RT, null)))
        assertTrue(NewsTopic.Russia.matches(Headline("Talks in Moscow", "", "", null, NewsSource.AlJazeera, null)))
        assertTrue(NewsTopic.AfricaMiddleEast.matches(Headline("Moroccan fans", "", "", null, NewsSource.AlJazeera, null)))
    }

    @Test
    fun mirroredHeadlines() {
        val json = """{"items":[{"title":"A","link":"https://www.aljazeera.com/news/2026/1/1/a","summary":"S","published":"2026-10-29T22:36:06Z","section":""}]}"""
        val h = headlinesFromJson(json, NewsSource.AlJazeera).single()
        assertEquals("news", h.section)
        assertEquals(Instant.parse("2026-10-29T22:36:06Z"), h.published)
    }

    @Test
    fun ages() {
        val now = Instant.parse("2026-10-29T12:00:00Z")
        assertEquals("Just now", app.pill.launcher.ui.ageText(now.minusSeconds(20), now))
        assertEquals("12 min ago", app.pill.launcher.ui.ageText(now.minus(Duration.ofMinutes(12)), now))
        assertEquals("3 h ago", app.pill.launcher.ui.ageText(now.minus(Duration.ofHours(3)), now))
        assertEquals("Yesterday", app.pill.launcher.ui.ageText(now.minus(Duration.ofHours(30)), now))
    }

    @Test
    fun photoCaptionIsPlaceAndPhotographer() {
        assertEquals("Iceland, Gullfoss", placeOf("Iceland - 2017-02-22 - Gullfoss - 3684"))
        assertEquals("Lake Bled, Slovenia", placeOf("Lake_Bled, Slovenia (DSC 0412)"))
        val photo = parsePhotoFeed(
            """{"photos":[{"file":"a.jpg","title":"Iceland - 2017-02-22 - Gullfoss - 3684","artist":"Pierre-Selim Huard","license":"CC BY 4.0"}]}""",
        ).single()
        assertEquals("Iceland, Gullfoss · Pierre-Selim Huard", photo.caption)
        // The full credit, with the licence, stays for Settings
        assertEquals("Iceland - 2017-02-22 - Gullfoss - 3684 · Pierre-Selim Huard · CC BY 4.0 · Wikimedia Commons", photo.credit)
    }

    @Test
    fun clockPageOffsetsAndIslamicDate() {
        val here = java.time.ZonedDateTime.of(2026, 10, 30, 23, 30, 0, 0, ZoneId.of("Europe/Moscow"))
        fun at(zone: String) = here.withZoneSameInstant(ZoneId.of(zone))
        assertEquals("Same time", app.pill.launcher.ui.offsetText(here, at("Asia/Riyadh")))
        assertEquals("−3 h", app.pill.launcher.ui.offsetText(here, at("Europe/London")))
        assertEquals("+6 h, tomorrow", app.pill.launcher.ui.offsetText(here, at("Asia/Tokyo")))
        assertEquals("+2 h 30 min, tomorrow", app.pill.launcher.ui.offsetText(here, at("Asia/Kolkata")))
        // 1 Muharram 1448 fell in June 2026
        val hijri = app.pill.launcher.ui.hijriDate(java.time.LocalDate.of(2026, 10, 30), java.util.Locale.ENGLISH)
        assertNotNull(hijri)
        assertTrue(hijri!!, "1448" in hijri)
    }

    @Test
    fun wallpaperTintFollowsThePhotosColour() {
        fun px(vararg colors: Int) = IntArray(400) { colors[it % colors.size] }
        // A sunset: mostly orange, some dark ground. The tint is a soft orange
        val sunset = app.pill.launcher.ui.tintFromPixels(px(0xFFE07A20.toInt(), 0xFFE07A20.toInt(), 0xFF201810.toInt()))
        val hsv = FloatArray(3).also { android.graphics.Color.colorToHSV(sunset.toArgb(), it) }
        assertTrue("hue ${hsv[0]}", hsv[0] in 20f..40f)
        // Greys only (fog, snow): the neutral tint
        assertEquals(app.pill.launcher.ui.NeutralTint, app.pill.launcher.ui.tintFromPixels(px(0xFF808080.toInt(), 0xFFF0F0F0.toInt(), 0xFF101010.toInt())))
        // A little colour in a grey photo doesn't take over
        val mostlyGrey = IntArray(1000) { if (it < 5) 0xFF2060E0.toInt() else 0xFF7A7A7A.toInt() }
        assertEquals(app.pill.launcher.ui.NeutralTint, app.pill.launcher.ui.tintFromPixels(mostlyGrey))
    }

    // ---- 3.0: glass motion, the rail, All apps chips, the Internet page --------------------

    @Test
    fun glassMotionWavesInReadingOrder() {
        // Each step starts one step later, and every step is fully in by the end of the wave
        val at = 200f
        val steps = (0..9).map { app.pill.launcher.ui.waveProgress(at, it) }
        assertTrue("earlier steps lead: $steps", steps.zipWithNext().all { (a, b) -> a >= b })
        assertEquals(0f, app.pill.launcher.ui.waveProgress(0f, 3), 1e-3f)
        assertEquals(1f, app.pill.launcher.ui.waveProgress(app.pill.launcher.ui.Motion.RevealMs.toFloat(), 20), 1e-3f)
        // A long list stops waiting after the cap
        assertEquals(app.pill.launcher.ui.waveDelay(8), app.pill.launcher.ui.waveDelay(40))
        // One value drives a group: all in at 1, all out at 0, the first step leading
        for (step in 0..2) {
            assertEquals(1f, app.pill.launcher.ui.staggered(1f, step, 3), 0f)
            assertEquals(0f, app.pill.launcher.ui.staggered(0f, step, 3), 0f)
        }
        assertTrue(app.pill.launcher.ui.staggered(0.5f, 0, 3) > app.pill.launcher.ui.staggered(0.5f, 2, 3))
    }

    @Test
    fun appCategoriesForTheChips() {
        assertEquals(AppCategory.Games, categoryOf(-1, true, "Asphalt", "com.gameloft.asphalt"))
        assertEquals(AppCategory.Video, categoryOf(android.content.pm.ApplicationInfo.CATEGORY_VIDEO, false, "Anything", "a.b"))
        assertEquals(AppCategory.Music, categoryOf(android.content.pm.ApplicationInfo.CATEGORY_AUDIO, false, "Anything", "a.b"))
        assertEquals(AppCategory.Video, categoryOf(-1, false, "YouTube", "com.google.android.youtube.tv"))
        assertEquals(AppCategory.Video, categoryOf(-1, false, "Live TV", "app.example.livetv"))
        assertEquals(AppCategory.Music, categoryOf(-1, false, "Spotify", "com.spotify.tv.android"))
        assertEquals(AppCategory.Music, categoryOf(-1, false, "Radio", "app.example.radio"))
        // "tv" in a package alone doesn't make an app a video app
        assertEquals(AppCategory.Other, categoryOf(-1, false, "Settings", "com.android.tv.settings"))
        assertEquals(AppCategory.Other, categoryOf(-1, false, "Maps", "app.example.maps"))
    }

    @Test
    fun internetSpeedsInPlainWords() {
        // 10 MB in one second is 80 Mbps
        assertEquals(80.0, mbps(10_000_000, 1_000_000_000), 0.001)
        assertEquals(0.0, mbps(1000, 0), 0.0)
        assertEquals("Good for 4K streaming", speedVerdict(87.4))
        assertEquals("Good for HD streaming", speedVerdict(12.0))
        assertEquals("Fine for standard video", speedVerdict(5.0))
        assertEquals("Slow: video may stop to load", speedVerdict(1.5))
    }

    @Test
    fun greetingAndPlaces() {
        assertEquals("Good morning", app.pill.launcher.ui.greetingFor(8))
        assertEquals("Good afternoon", app.pill.launcher.ui.greetingFor(16))
        assertEquals("Good evening", app.pill.launcher.ui.greetingFor(20))
        assertEquals("Good night", app.pill.launcher.ui.greetingFor(2))
        assertEquals(listOf("1st", "2nd", "3rd", "4th", "11th", "12th", "13th", "21st", "22nd", "101st"), listOf(1, 2, 3, 4, 11, 12, 13, 21, 22, 101).map { app.pill.launcher.ui.ordinal(it) })
    }

    @Test
    fun widgetStackKeepsEachWidgetInOnePlace() {
        val base = LauncherSettings(widgets = listOf(WidgetKind.Clock, WidgetKind.Stack), stack = listOf(WidgetKind.Fennecs, WidgetKind.Headlines))
        assertEquals(setOf(WidgetKind.Clock, WidgetKind.Fennecs, WidgetKind.Headlines), base.activeWidgets())
        // Turning a stack widget on by itself takes it out of the stack
        val football = base.withRowToggled(WidgetKind.Fennecs)
        assertEquals(listOf(WidgetKind.Clock, WidgetKind.Stack, WidgetKind.Fennecs), football.widgets)
        assertEquals(listOf(WidgetKind.Headlines), football.stack)
        // Adding one to the stack takes it off the home screen
        val back = football.withStackToggled(WidgetKind.Fennecs)
        assertEquals(listOf(WidgetKind.Clock, WidgetKind.Stack), back.widgets)
        assertEquals(listOf(WidgetKind.Headlines, WidgetKind.Fennecs), back.stack)
        assertEquals(listOf(WidgetKind.Fennecs, WidgetKind.Headlines), back.withStackMoved(WidgetKind.Fennecs, -1).stack)
        assertEquals(back, back.withStackMoved(WidgetKind.Fennecs, 1))
        // A stack never holds itself, and the stack's widgets need data only while it is on
        assertEquals(back, back.withStackToggled(WidgetKind.Stack))
        assertEquals(listOf(WidgetKind.Clock, WidgetKind.Weather), parseStack("Clock,Stack,Clock,Weather,Nope"))
        assertEquals(setOf(WidgetKind.Clock), base.copy(widgets = listOf(WidgetKind.Clock)).activeWidgets())
    }

    @Test
    fun renamedAppsKeepTheirOwnName() {
        assertNull(cleanName("  ", "Cinema"))
        assertNull(cleanName("Cinema", "Cinema"))
        assertEquals("Films and series", cleanName("  Films   and series ", "Cinema"))
        assertEquals(MaxNameLength, cleanName("x".repeat(80), "Cinema")!!.length)
        val names = mapOf("app.films" to "Films")
        assertEquals(names, namesFromJson(namesToJson(names)))
        assertEquals(emptyMap<String, String>(), namesFromJson("not json"))
    }

    @Test
    fun letterStripDimsLettersThatLeadNowhere() {
        val labels = listOf("Music", "Maps", "Cinema")
        assertEquals(setOf('u', 'a'), nextLetters(labels, "m"))
        // Anywhere in a name, as search matches
        assertEquals(setOf('n'), nextLetters(labels, "ci"))
        assertEquals(emptySet<Char>(), nextLetters(labels, "zz"))
        assertTrue('c' in nextLetters(labels, ""))
    }

    @Test
    fun updatesFromGitHubReleases() {
        assertTrue(isNewer("3.1", "3.0"))
        assertTrue(isNewer("3.10", "3.9"))
        assertTrue(isNewer("4", "3.9"))
        assertFalse(isNewer("3.0", "3.0"))
        assertFalse(isNewer("2.9", "3.0"))
        val json = """{"name":"Pill Launcher 3.1 (latest)","body":"Install with Downloader\n\n## 3.1 (November 2026)\n\n- **Widget stack**: several widgets in one place.\n- Rename apps in the launcher.\n- Search with a strip of `letters`.\n- Fourth\n\n## 3.0\n- old",""" +
            """"assets":[{"name":"launcher.apk","browser_download_url":"https://x/launcher.apk","size":1},""" +
            """{"name":"pill-launcher-3.1.apk","browser_download_url":"https://x/pill-launcher-3.1.apk","size":9100000}]}"""
        val release = parseRelease(json)!!
        assertEquals("3.1", release.version)
        assertEquals("https://x/pill-launcher-3.1.apk", release.apkUrl)
        assertEquals(listOf("Widget stack: several widgets in one place.", "Rename apps in the launcher.", "Search with a strip of letters."), release.notes)
        assertNull(parseRelease("""{"assets":[{"name":"launcher.apk","browser_download_url":"u"}]}"""))
    }

    private fun liveMatch(us: Int?, them: Int?, status: String, kickoff: Instant, opponent: String = "Togo") = Match(
        id = "77", home = Side(AlgeriaId, "Algeria", null), away = Side("136484", opponent, null), homeScore = us, awayScore = them,
        competition = "African Cup of Nations Qualifying", leagueId = null, season = null, kickoff = kickoff, status = status, venue = null, teamId = AlgeriaId,
    )

    @Test
    fun goalsAndWinsBetweenFetches() {
        val now = Instant.parse("2026-11-01T18:10:00Z")
        val kickoff = Instant.parse("2026-11-01T17:00:00Z")
        fun f(m: Match, at: Instant = now) = listOf(Fixtures(Algeria, m.takeIf { !it.isFinished }, m.takeIf { it.isFinished }, at))
        val goal = footballMoments(f(liveMatch(0, 0, "2H", kickoff)), f(liveMatch(1, 0, "2H", kickoff)), now).single()
        assertEquals(MomentKind.Goal, goal.kind)
        assertEquals(56, goal.minute)
        assertEquals("Goal · Algeria 56'", momentText(goal, compact = false))
        assertEquals("Goal · 56'", momentText(goal, compact = true))
        // The other side scoring is no moment; nor is the first load
        assertTrue(footballMoments(f(liveMatch(1, 0, "2H", kickoff)), f(liveMatch(1, 1, "2H", kickoff)), now).isEmpty())
        assertTrue(footballMoments(f(liveMatch(0, 0, "2H", kickoff), Instant.EPOCH), f(liveMatch(1, 0, "2H", kickoff)), now).isEmpty())
        // Full time with Algeria ahead is a win, once
        val win = footballMoments(f(liveMatch(2, 1, "2H", kickoff)), f(liveMatch(2, 1, "FT", kickoff)), now).single()
        assertEquals(MomentKind.Win, win.kind)
        assertEquals("Algeria win 2–1", momentText(win, compact = false))
        assertTrue(footballMoments(f(liveMatch(2, 1, "FT", kickoff)), f(liveMatch(2, 1, "FT", kickoff)), now).isEmpty())
        // The minute from kick-off: first half, half-time, second half
        assertEquals(30, matchMinute(liveMatch(0, 0, "1H", kickoff), kickoff.plusSeconds(29 * 60)))
        assertEquals(45, matchMinute(liveMatch(0, 0, "HT", kickoff), kickoff.plusSeconds(52 * 60)))
        assertEquals(90, matchMinute(liveMatch(0, 0, "2H", kickoff), kickoff.plusSeconds(130 * 60)))
        assertNull(matchMinute(liveMatch(0, 0, "NS", kickoff), kickoff.minusSeconds(60)))
    }

    // Notifications

    @Test
    fun noticesNewestFirstWithoutOldOnesAndReplacingTheirOwn() {
        val now = Instant.parse("2026-11-02T12:00:00Z")
        var list = emptyList<Notice>()
        list = withNotice(list, Notice("a", NoticeKind.File, "a", "", now.minusSeconds(3600)), now)
        list = withNotice(list, Notice("b", NoticeKind.File, "b", "", now), now)
        list = withNotice(list, Notice("old", NoticeKind.File, "old", "", now.minus(Duration.ofDays(8))), now)
        list = withNotice(list, Notice("a", NoticeKind.File, "a again", "", now.minusSeconds(60)), now)
        assertEquals(listOf("b", "a"), list.map { it.id })
        assertEquals("a again", list[1].title)
        repeat(50) { list = withNotice(list, Notice("n$it", NoticeKind.File, "", "", now.minusSeconds(it.toLong())), now) }
        assertEquals(MaxNotices, list.size)
    }

    @Test
    fun noticesSurviveBeingSaved() {
        val n = Notice("file:x", NoticeKind.File, "x.jpg arrived", "3 MB", Instant.ofEpochMilli(1_700_000_000_000), target = "x.jpg")
        val app = Notice("app:k", NoticeKind.App, "Title", "Text", Instant.ofEpochMilli(1_700_000_100_000), pkg = "com.example")
        assertEquals(listOf(n, app), noticesFromJson(noticesToJson(listOf(n, app))))
        assertEquals(emptyList<Notice>(), noticesFromJson("not json"))
    }

    @Test
    fun noticeAgesReadNaturally() {
        val now = Instant.parse("2026-11-02T12:00:00Z")
        assertEquals("now", noticeAge(now.minusSeconds(20), now, utc))
        assertEquals("5 min", noticeAge(now.minusSeconds(300), now, utc))
        assertEquals("2 h", noticeAge(now.minusSeconds(7300), now, utc))
        assertEquals("Oct 20", noticeAge(Instant.parse("2026-10-20T08:00:00Z"), now, utc))
    }

    @Test
    fun anUpdateIsANoticeUntilPutOff() {
        val now = Instant.now()
        val release = Release("3.4", "https://example.com/pill-launcher-3.4.apk", 1, listOf("Something new"))
        val ready = updateNotice(UpdateState(UpdatePhase.Available, release), null, now)
        assertEquals("Pill Launcher 3.4 is ready", ready?.title)
        assertEquals("Something new", ready?.text)
        assertNull(updateNotice(UpdateState(UpdatePhase.Available, release), "3.4", now))
        assertEquals("Downloading Pill Launcher 3.4", updateNotice(UpdateState(UpdatePhase.Downloading, release, progress = 0.5f), "3.4", now)?.title)
        assertNull(updateNotice(UpdateState(UpdatePhase.UpToDate), null, now))
        assertEquals("Updated to 3.4", updatedNotice("3.4", listOf("One", "Two", "Three"), now).title)
        assertEquals("One · Two", updatedNotice("3.4", listOf("One", "Two", "Three"), now).text)
    }

    @Test
    fun theConnectionSaysHowLongItWasGone() {
        val since = Instant.parse("2026-11-02T12:00:00Z")
        assertEquals("The connection dropped for 4 min", backOnlineNotice(since, since.plusSeconds(4 * 60 + 10)).text)
        assertEquals("The connection dropped for 1 h 5 min", backOnlineNotice(since, since.plusSeconds(65 * 60)).text)
    }

    // Files

    @Test
    fun fileSizesAndKinds() {
        assertEquals("3.4 MB", fileSize(3_400_000))
        assertEquals("212 MB", fileSize(212_000_000))
        assertEquals("1.2 GB", fileSize(1_200_000_000))
        assertEquals("900 B", fileSize(900))
        assertEquals(FileKind.Photo, fileKind("IMG_001.JPG"))
        assertEquals(FileKind.Video, fileKind("match.mkv"))
        assertEquals(FileKind.App, fileKind("kinopub-2.4.apk"))
        assertEquals(FileKind.Document, fileKind("manual.pdf"))
        assertEquals(FileKind.Document, fileKind("README"))
    }

    @Test
    fun receivedNamesAreSafeAndNeverOverwrite() {
        assertEquals("passwd", safeFileName("../../etc/passwd"))
        assertEquals("My photo.jpg", safeFileName("C:\\Users\\me\\My photo?.jpg"))
        assertEquals("file", safeFileName(".."))
        assertEquals("file", safeFileName(null))
        val taken = setOf("a.jpg", "a (2).jpg", "notes")
        assertEquals("a (3).jpg", uniqueFileName("a.jpg") { it in taken })
        assertEquals("notes (2)", uniqueFileName("notes") { it in taken })
        assertEquals("b.jpg", uniqueFileName("b.jpg") { it in taken })
    }

    @Test
    fun sendersAndTheHomeNetwork() {
        assertEquals("an iPhone", senderOf("Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X)"))
        assertEquals("an Android phone", senderOf("Mozilla/5.0 (Linux; Android 14; Pixel 8) Mobile Safari/537.36"))
        assertEquals("a computer", senderOf("Mozilla/5.0 (Windows NT 10.0; Win64; x64)"))
        assertTrue(isHomeNetwork(java.net.InetAddress.getByName("192.168.1.20")))
        assertTrue(isHomeNetwork(java.net.InetAddress.getByName("10.0.0.7")))
        assertTrue(isHomeNetwork(java.net.InetAddress.getByName("fd12:3456::1")))
        assertFalse(isHomeNetwork(java.net.InetAddress.getByName("8.8.8.8")))
        assertTrue(FileShare.newCode().let { it.length == 6 && it.all(Char::isDigit) })
        assertEquals(32, FileShare.newKey().length)
        assertEquals("iPhone", deviceName("Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X)"))
        assertEquals("Computer", deviceName("Mozilla/5.0 (Windows NT 10.0; Win64; x64)"))
    }

    @Test
    fun twoCrashesInAMinuteMeanSafeMode() {
        val now = 1_000_000L
        assertFalse(shouldStartSafe(emptyList(), now))
        assertFalse(shouldStartSafe(listOf(now - 5_000), now))
        assertTrue(shouldStartSafe(listOf(now - 40_000, now - 5_000), now))
        assertFalse("crashes long ago don't count", shouldStartSafe(listOf(now - 600_000, now - 500_000), now))
    }

    @Test
    fun privateNotificationsCanHideTheirText() {
        val n = Notice("app:1", NoticeKind.App, "Sara", "See you at 8", Instant.EPOCH, pkg = "org.telegram", isPrivate = true)
        assertEquals("New notification", shownNotice(n, hide = true).title)
        assertEquals("", shownNotice(n, hide = true).text)
        assertEquals(n, shownNotice(n, hide = false))
        val open = n.copy(isPrivate = false)
        assertEquals(open, shownNotice(open, hide = true))
    }

    /** MAWAQIT's answer as an object of times, with iqama as offsets and as a clock time. */
    @Test
    fun prayerTimesFromMawaqit() {
        val at = Instant.parse("2026-11-03T08:00:00Z")
        val day = java.time.LocalDate.of(2026, 11, 3)
        val t = parsePrayerTimes(data("mawaqit.json"), at, day)
        assertEquals("Moscow Cathedral Mosque", t.mosque)
        assertEquals(listOf("05:15", "13:30", "16:45", "19:15", "20:30"), t.list.map { it.second.toString() })
        assertEquals("06:42", t.shuruq.toString())
        assertEquals("05:35", t.iqama.getValue(Prayer.Fajr).toString())
        assertEquals("20:45", t.iqama.getValue(Prayer.Isha).toString())
        assertFalse(t.fromCache)

        // The other shape the community API has used: a list of five, and the mosque under "name"
        val list = parsePrayerTimes("""{"name":"Al Noor","times":["5:15","13:30","16:45","19:15","20:30"],"shuruq":"06:42"}""", at, day)
        assertEquals("Al Noor", list.mosque)
        assertEquals("05:15", list.times.getValue(Prayer.Fajr).toString())
        assertEquals("06:42", list.shuruq.toString())
        // Six put sunrise second
        val six = parsePrayerTimes("""{"times":["05:15","06:42","13:30","16:45","19:15","20:30"]}""", at, day, fallbackName = "my-mosque")
        assertEquals("my-mosque", six.mosque)
        assertEquals("13:30", six.times.getValue(Prayer.Dhuhr).toString())
        assertEquals("06:42", six.shuruq.toString())

        // Missing or unreadable entries are left out; no prayers at all is an error
        val partial = parsePrayerTimes("""{"mosque":"X","times":{"fajr":"05:15","dhuhr":"noon","isha":null}}""", at, day)
        assertEquals(listOf(Prayer.Fajr), partial.list.map { it.first })
        assertTrue(partial.iqama.isEmpty())
        assertTrue(runCatching { parsePrayerTimes("""{"mosque":"X","times":{}}""", at, day) }.isFailure)
        assertTrue(runCatching { parsePrayerTimes("not json", at, day) }.isFailure)
        assertTrue(runCatching { parsePrayerTimes("", at, day) }.isFailure)
    }

    /** The next prayer follows the clock round the day, then on to tomorrow's Fajr. */
    @Test
    fun nextPrayerAndCountdown() {
        val day = java.time.LocalDate.of(2026, 11, 3)
        val t = parsePrayerTimes(data("mawaqit.json"), Instant.EPOCH, day)
        val zone = ZoneId.of("Europe/Moscow")
        fun at(h: Int, m: Int) = java.time.ZonedDateTime.of(day, java.time.LocalTime.of(h, m), zone)
        val morning = nextPrayer(t, at(8, 0))!!
        assertEquals(Prayer.Dhuhr, morning.prayer)
        assertFalse(morning.tomorrow)
        assertEquals("in 5 h 30 min", untilText(morning.at, at(8, 0)))
        val evening = nextPrayer(t, at(18, 33))!!
        assertEquals(Prayer.Maghrib, evening.prayer)
        assertEquals("in 42 min", untilText(evening.at, at(18, 33)))
        assertEquals("now", untilText(evening.at, at(19, 15)))
        // On the dot still counts as next
        assertEquals(Prayer.Isha, nextPrayer(t, at(20, 30))!!.prayer)
        val night = nextPrayer(t, at(23, 0))!!
        assertEquals(Prayer.Fajr, night.prayer)
        assertTrue(night.tomorrow)
        assertEquals(day.plusDays(1), night.at.toLocalDate())
        assertNull(nextPrayer(t.copy(times = emptyMap()), at(8, 0)))
    }

    /** What's typed for the mosque and the API is tidied; the saved copy is kept per mosque. */
    @Test
    fun prayerSettingsAndCache() {
        assertEquals("grande-mosquee-de-paris", cleanMosqueSlug("  https://mawaqit.net/fr/grande-mosquee-de-paris?x=1 "))
        assertEquals("al-noor_2", cleanMosqueSlug("Al-Noor_2"))
        assertEquals(DefaultPrayerApi, cleanPrayerApi("   "))
        assertEquals(DefaultPrayerApi, cleanPrayerApi("mawaqit"))
        assertEquals("https://example.org/api", cleanPrayerApi("https://example.org/api/"))

        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val zone = ZoneId.of("Europe/Moscow")
        val source = PrayerSource(context, zone)
        assertNull(source.cached("my-mosque"))
        // A fetch that can't reach anything fails, naming the reasons, and leaves no cache
        val failed = source.fetch("http://127.0.0.1:9", "my-mosque")
        assertTrue(failed.isFailure)
        assertTrue(failed.exceptionOrNull()!!.message!!.contains("prayer-times"))
        assertNull(source.cached("my-mosque"))
        assertTrue(source.fetch(DefaultPrayerApi, "").isFailure)
        // Saved answers come back marked, and only for their own mosque
        context.getSharedPreferences("prayers", android.content.Context.MODE_PRIVATE).edit()
            .putString("slug", "my-mosque").putString("body", data("mawaqit.json")).putLong("at", 1_700_000_000_000L).putString("date", "2026-11-02").apply()
        val saved = source.cached("my-mosque")!!
        assertTrue(saved.fromCache)
        assertEquals(java.time.LocalDate.of(2026, 11, 2), saved.forDate)
        assertNull(source.cached("other-mosque"))
        // After a good fetch the next one is just past midnight
        val wait = source.untilNextDay(java.time.ZonedDateTime.of(2026, 11, 3, 22, 0, 0, 0, zone))
        assertTrue(wait.toMinutes() in 120..121)
    }

    @Test
    fun prayerSettingsRoundTrip() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val store = SettingsStore(context)
        store.save(LauncherSettings(mosque = "my-mosque", prayerApi = "https://example.org/v1", widgets = listOf(WidgetKind.Clock, WidgetKind.Prayers)))
        val loaded = store.load()
        assertEquals("my-mosque", loaded.mosque)
        assertEquals("https://example.org/v1", loaded.prayerApi)
        assertEquals(listOf(WidgetKind.Clock, WidgetKind.Prayers), loaded.widgets)
    }
}
