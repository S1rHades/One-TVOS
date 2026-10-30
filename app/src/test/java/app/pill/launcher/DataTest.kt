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
}
