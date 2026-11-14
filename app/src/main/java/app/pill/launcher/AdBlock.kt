package app.pill.launcher

import java.util.Locale

/**
 * Ad, pop-under and tracking networks the web viewer never loads anything from: the big ad
 * exchanges, the pop-under and push networks free streaming sites live on, content-ad
 * widgets and trackers. A host is blocked when it is one of these or under one.
 */
private val AdDomains = setOf(
    // Google's ads and measuring
    "doubleclick.net", "googlesyndication.com", "googleadservices.com", "adservice.google.com",
    "googletagservices.com", "google-analytics.com", "pagead2.googlesyndication.com", "imasdk.googleapis.com",
    // Ad exchanges and bidders
    "adnxs.com", "criteo.com", "criteo.net", "pubmatic.com", "rubiconproject.com", "openx.net",
    "casalemedia.com", "smartadserver.com", "adform.net", "bidswitch.net", "33across.com",
    "sharethrough.com", "teads.tv", "yieldmo.com", "amazon-adsystem.com", "media.net", "adsrvr.org",
    "advertising.com", "contextweb.com", "lijit.com", "sovrn.com", "indexww.com", "spotxchange.com",
    "springserve.com", "adcolony.com", "applovin.com", "inmobi.com", "smaato.net", "unityads.unity3d.com",
    "yieldlove.com", "adition.com", "adtelligent.com", "betweendigital.com", "adhigh.net",
    "zedo.com", "serving-sys.com", "flashtalking.com", "innovid.com", "tremorhub.com", "undertone.com",
    // Pop-unders, redirects and push ads (the networks free streaming sites use)
    "popads.net", "popcash.net", "propellerads.com", "propellerclick.com", "onclickads.net",
    "onclkds.com", "onclickalgo.com", "onclickperformance.com", "adsterra.com", "adsterratools.com",
    "highperformanceformat.com", "highcpmrevenuegate.com", "highcpmgate.com", "profitablecpmgate.com",
    "profitablegatecpm.com", "effectivegatecpm.com", "effectiveratecpm.com", "exoclick.com",
    "exosrv.com", "exdynsrv.com", "realsrv.com", "juicyads.com", "trafficjunky.net",
    "trafficfactory.biz", "trafficstars.com", "tsyndicate.com", "hilltopads.net", "hilltopads.com",
    "clickadu.com", "adcash.com", "ad-maven.com", "admaven.com", "monetag.com", "galaksion.com",
    "a-ads.com", "adskeeper.com", "adskeeper.co.uk", "richads.com", "pushame.com", "evadav.com",
    "rollerads.com", "clickaine.com", "popmyads.com", "adspyglass.com", "bidvertiser.com",
    "admitad.com", "adf.ly", "shorte.st", "ouo.io", "linkvertise.com", "acint.net", "dolohen.com",
    "zeroredirect1.com", "zeroredirect2.com", "pemsrv.com", "pushwhy.com", "pushnest.com",
    "notifpush.com", "push-sdk.com", "realpush.digital", "adxadserv.com", "adxpremium.services",
    "aclib.net", "ad-delivery.net", "adbetclickin.pink", "a.realsrv.com", "syndication.realsrv.com",
    // Content-ad widgets ("around the web")
    "mgid.com", "taboola.com", "outbrain.com", "revcontent.com", "zergnet.com", "content.ad",
    "adblade.com", "nativo.com", "plista.com", "ligatus.com",
    // Trackers
    "scorecardresearch.com", "quantserve.com", "moatads.com", "doubleverify.com",
    "adsafeprotected.com", "hotjar.com", "mouseflow.com", "crazyegg.com", "clarity.ms",
    "connect.facebook.net", "analytics.tiktok.com", "ads.tiktok.com", "bat.bing.com",
    "ads.linkedin.com", "ads-twitter.com", "static.ads-twitter.com", "branch.io", "app-measurement.com",
    "mc.yandex.ru", "an.yandex.ru", "top-fwz1.mail.ru", "counter.yadro.ru",
)

/** Whether [host] belongs to an ad, pop-under or tracking network. */
fun isAdHost(host: String?): Boolean {
    var h = host?.lowercase(Locale.ROOT)?.trimEnd('.') ?: return false
    while (true) {
        if (h in AdDomains) return true
        val dot = h.indexOf('.')
        if (dot < 0 || h.indexOf('.', dot + 1) < 0) return false
        h = h.substring(dot + 1)
    }
}

/** Second-level names that are part of a country's suffix ("co.uk", "com.dz"): the site is the name before them. */
private val SuffixSeconds = setOf("co", "com", "net", "org", "gov", "edu", "ac", "or", "ne", "go")

/** The site [host] belongs to ("www.example.co.uk" → "example.co.uk"), for telling one site from another. */
fun siteOf(host: String?): String {
    val parts = host?.lowercase(Locale.ROOT)?.trimEnd('.')?.split('.')?.filter { it.isNotEmpty() } ?: return ""
    if (parts.size <= 2) return parts.joinToString(".")
    val keep = if (parts.last().length == 2 && parts[parts.size - 2] in SuffixSeconds) 3 else 2
    return parts.takeLast(keep).joinToString(".")
}

/** Two addresses on the same site. */
fun sameSite(a: String?, b: String?): Boolean {
    val x = siteOf(runCatching { java.net.URI(a).host }.getOrNull())
    return x.isNotEmpty() && x == siteOf(runCatching { java.net.URI(b).host }.getOrNull())
}
