// Pill Launcher's site scanner: reads a finished page and returns what the TV app view
// draws (menu, banner, rows of cards, the item's details, a play button), as JSON.
// It only reads: nothing on the page is changed, so the site keeps working as it is.
(function () {
  var W = innerWidth || 1280, H = innerHeight || 720, here = location.href;
  function abs(u) { try { return u ? new URL(u, here).href : null } catch (e) { return null } }
  function clean(s) { return (s || '').replace(/\s+/g, ' ').trim() }
  function text(e) { return clean(e && (e.innerText || e.textContent)) }
  function rect(e) { try { return e.getBoundingClientRect() } catch (x) { return { width: 0, height: 0, top: 0, left: 0 } } }
  function shown(e) {
    var r = rect(e);
    if (r.width < 24 || r.height < 16) return false;
    var s = getComputedStyle(e);
    return s.visibility !== 'hidden' && s.display !== 'none' && +s.opacity !== 0;
  }
  function sameSite(u) {
    try {
      var a = new URL(u).hostname.split('.').slice(-2).join('.'), b = location.hostname.split('.').slice(-2).join('.');
      return a === b;
    } catch (e) { return false }
  }
  // Where an element is in the page, to find it again for a click
  function path(e) {
    var parts = [];
    while (e && e.nodeType === 1 && e !== document.body && parts.length < 40) {
      var p = e.parentElement; if (!p) break;
      var i = Array.prototype.indexOf.call(p.children, e) + 1;
      parts.unshift(e.tagName.toLowerCase() + ':nth-child(' + i + ')');
      e = p;
    }
    return 'body>' + parts.join('>');
  }
  function bestOfSrcset(s) {
    if (!s) return null;
    var best = null, bw = -1;
    s.split(',').forEach(function (c) {
      var bits = clean(c).split(' '), w = parseFloat(bits[1]) || 1;
      if (bits[0] && w > bw) { bw = w; best = bits[0] }
    });
    return best;
  }
  function imgSrc(i) {
    var lazy = i.getAttribute('data-src') || i.getAttribute('data-lazy-src') || i.getAttribute('data-original') || i.getAttribute('data-lazy') || bestOfSrcset(i.getAttribute('data-srcset'));
    var src = i.currentSrc || i.src || '';
    if (!src || /^data:/.test(src) || /blank|spacer|placeholder|pixel\.gif/i.test(src)) src = lazy || bestOfSrcset(i.getAttribute('srcset')) || src;
    if (/^data:/.test(src)) return null;
    return abs(src);
  }
  function bg(e) {
    var b = getComputedStyle(e).backgroundImage;
    var m = b && b !== 'none' && /url\(["']?([^"')]+)["']?\)/.exec(b);
    return m && !/^data:/.test(m[1]) ? abs(m[1]) : null;
  }
  // The picture of a card or banner: its own picture, else a background
  function picture(e) {
    var imgs = e.tagName === 'IMG' ? [e] : e.querySelectorAll('img');
    var best = null, ba = 0;
    for (var k = 0; k < imgs.length && k < 8; k++) {
      var im = imgs[k], r = rect(im), s = imgSrc(im);
      // A slide that isn't showing has no size on the page: its picture's own size then
      var w = r.width || im.naturalWidth || +im.getAttribute('width') || 0, h = r.height || im.naturalHeight || +im.getAttribute('height') || 0;
      var a = w * h;
      if (s && a >= ba) { ba = a; best = { src: s, w: w, h: h } }
    }
    if (best) return best;
    var all = [e].concat(Array.prototype.slice.call(e.querySelectorAll('div,span,figure,picture,i'), 0, 12));
    for (var j = 0; j < all.length; j++) {
      var u = bg(all[j]);
      if (u) { var q = rect(all[j]); return { src: u, w: q.width, h: q.height } }
    }
    var src = e.querySelector('source[srcset],source[data-srcset]');
    if (src) { var t = rect(e); return { src: abs(bestOfSrcset(src.getAttribute('srcset') || src.getAttribute('data-srcset'))), w: t.width, h: t.height } }
    return null;
  }
  function nameOf(e) {
    var h = e.querySelector('h1,h2,h3,h4,h5,h6,[class*="title" i],[class*="name" i]');
    var n = clean(e.getAttribute('aria-label')) || clean(e.getAttribute('title')) || (h && text(h));
    if (!n) { var i = e.querySelector('img[alt]'); n = i && clean(i.getAttribute('alt')) }
    if (!n) n = text(e).split(/ ?[\n|•·] ?/)[0];
    return (n || '').slice(0, 90);
  }
  function subOf(e, name) {
    var t = text(e);
    if (name && t.indexOf(name) === 0) t = clean(t.slice(name.length));
    return t.length > 0 && t.length < 70 ? t : '';
  }
  function isButton(e) {
    return e.tagName === 'BUTTON' || e.getAttribute('role') === 'button' || e.getAttribute('role') === 'link' || e.hasAttribute('onclick') || e.hasAttribute('tabindex');
  }
  function card(e) {
    var p = picture(e);
    if (!p || !p.src) return null;
    var href = e.tagName === 'A' ? e.href : (e.querySelector('a[href]') || {}).href;
    if (href && !/^https?:/.test(href)) href = null;
    var n = nameOf(e);
    var box = rect(e), ratio = box.width > 20 && box.height > 20 ? box.width / box.height : (p.h > 0 ? p.w / p.h : 0);
    return { title: n, sub: subOf(e, n), image: p.src, ratio: +ratio.toFixed(2), url: href || null, path: path(e), w: box.width, top: box.top + scrollY };
  }

  // The banner: a slider near the top (the common slider libraries, or anything named hero,
  // banner, slider or carousel), every slide of it, showing or not
  var heroes = [], heroRoot = null;
  (function () {
    var sel = '.swiper-slide,.slick-slide,.splide__slide,.glide__slide,.owl-item,.carousel-item,.keen-slider__slide,.flickity-slider>*,.embla__slide,' +
      '[class*="hero" i] [class*="slide" i],[class*="banner" i] [class*="slide" i],[class*="slider" i] [class*="slide" i],[class*="carousel" i] [class*="slide" i],[class*="slider" i] [class*="item" i],[class*="carousel" i] [class*="item" i],[class*="hero" i] li,[class*="banner" i] li';
    var lists = new Map();
    document.querySelectorAll(sel).forEach(function (sl) {
      var p = sl.parentElement;
      if (!p) return;
      var g = lists.get(p);
      if (!g) { g = []; lists.set(p, g) }
      if (g.indexOf(sl) < 0) g.push(sl);
    });
    var best = null, bestScore = 0;
    lists.forEach(function (slides, list) {
      // The slider's window: the first box up from the list that is laid out
      var win = list, r = rect(win);
      for (var up = 0; up < 4 && win.parentElement && (r.width < 10 || r.height < 10); up++) { win = win.parentElement; r = rect(win) }
      var top = r.top + scrollY;
      if (r.width < W * 0.55 || r.height < H * 0.22 || top > H * 0.9) return;
      var pics = slides.filter(function (sl) { return picture(sl) });
      if (!pics.length) return;
      var score = r.width * r.height / (1 + top / H);
      if (score > bestScore) { bestScore = score; best = { win: win, slides: pics } }
    });
    if (!best) return;
    heroRoot = best.win;
    var seen = {};
    best.slides.forEach(function (sl) {
      if (/clone/i.test(sl.className) || heroes.length >= 8) return;
      var p = picture(sl), link = sl.tagName === 'A' ? sl : sl.querySelector('a[href]') || sl.closest('a[href]');
      var url = link && /^https?:/.test(link.href) ? link.href : null;
      var key = (url || '') + '|' + p.src;
      if (seen[key]) return;
      seen[key] = 1;
      var h = sl.querySelector('h1,h2,h3,h4,[class*="title" i],[class*="name" i]');
      var logo = sl.querySelector('img[class*="logo" i],img[class*="title" i],[class*="logo" i] img');
      var title = clean(h && text(h)) || clean(logo && logo.getAttribute('alt')) || clean(sl.getAttribute('aria-label')) || nameOf(sl);
      var d = sl.querySelector('p,[class*="desc" i],[class*="overview" i],[class*="synopsis" i],[class*="summary" i]');
      var btn = null;
      sl.querySelectorAll('a[href],button,[role="button"]').forEach(function (b) {
        if (!btn && /play|watch|stream|regarder|شاهد|مشاهدة|تشغيل/i.test(clean(b.getAttribute('aria-label')) + ' ' + text(b))) btn = b;
      });
      heroes.push({ title: title.slice(0, 90), sub: '', desc: d ? text(d).slice(0, 260) : '', image: p.src, ratio: p.h > 0 ? +(p.w / p.h).toFixed(2) : 0, url: url, path: path(btn || link || sl) });
    });
  })();

  // Candidate cards: links (or clickable boxes) holding a picture
  var seenEl = [], cands = [];
  var nodes = document.querySelectorAll('a[href],[role="button"],[role="link"],[onclick],button,[tabindex="0"]');
  for (var n = 0; n < nodes.length && cands.length < 600; n++) {
    var e = nodes[n];
    if (e.closest('nav,header,footer') && !e.closest('main')) continue;
    if (e.tagName !== 'A' && !isButton(e)) continue;
    if (e.parentElement && e.parentElement.closest('a[href]')) continue;
    if (heroRoot && heroRoot.contains(e)) continue;
    var r = rect(e);
    if (r.width < 60 || r.height < 40 || r.width > W * 1.05) continue;
    if (!e.querySelector('img,picture,[style*="background"]') && !bg(e)) continue;
    cands.push(e);
  }

  // Each card's list: climb past wrappers that hold only it; the list is the parent of that item
  function itemOf(e) {
    var it = e;
    while (it.parentElement && it.parentElement !== document.body && it.parentElement.children.length === 1) it = it.parentElement;
    return it;
  }
  var groups = [], byList = new Map();
  cands.forEach(function (e) {
    var it = itemOf(e), list = it.parentElement;
    if (!list) return;
    var g = byList.get(list);
    if (!g) { g = { list: list, cards: [], seen: {} }; byList.set(list, g); groups.push(g) }
    var c = card(e);
    if (!c) return;
    var key = c.url || c.image;
    if (g.seen[key]) return; // a carousel's copies of its own cards
    g.seen[key] = 1;
    g.cards.push(c);
  });

  // A row's heading: the nearest heading before its list, a few levels up
  function headingFor(list) {
    var el = list;
    for (var up = 0; up < 5 && el && el !== document.body; up++) {
      var al = clean(el.getAttribute && el.getAttribute('aria-label'));
      if (al && al.length < 60 && up > 0) return al;
      var sib = el.previousElementSibling;
      for (var s = 0; s < 3 && sib; s++, sib = sib.previousElementSibling) {
        var h = /^H[1-6]$/.test(sib.tagName) || sib.getAttribute('role') === 'heading' ? sib : sib.querySelector('h1,h2,h3,h4,h5,h6,[role="heading"],[class*="title" i],[class*="heading" i]');
        var t = h && text(h);
        if (t && t.length < 60) return t;
      }
      el = el.parentElement;
    }
    return '';
  }

  // A row's tabs (Films | Series, Today | This week): a set of short labels near its heading
  // that pick what the row shows
  var lists = groups.map(function (g) { return g.list });
  function tabsFor(list) {
    var el = list;
    for (var up = 0; up < 5 && el && el.parentElement && el !== document.body; up++) {
      var scope = el.parentElement;
      // Not past a box that holds another row
      if (lists.some(function (l) { return l !== list && scope.contains(l) && !list.contains(l) })) break;
      var found = Array.prototype.filter.call(scope.querySelectorAll('[role="tab"]'), function (t) { return !list.contains(t) });
      if (found.length < 2) {
        var sets = scope.querySelectorAll('[role="tablist"],[class*="tab" i],[class*="filter" i],[class*="toggle" i],[class*="switch" i],[class*="segment" i],[class*="pills" i],[class*="chips" i],ul,nav');
        for (var k = 0; k < sets.length && found.length < 2; k++) {
          var c = sets[k];
          if (c.contains(list) || list.contains(c) || c.querySelector('img,picture')) continue;
          var items = Array.prototype.filter.call(c.children, function (x) { var t = text(x); return t && t.length <= 24 && shown(x) });
          if (items.length >= 2 && items.length <= 8 && items.length === c.children.length) {
            found = items.map(function (x) { return x.querySelector('a,button,[role="tab"],[role="button"]') || x });
          }
        }
      }
      if (found.length >= 2 && found.length <= 8) {
        return found.map(function (t) {
          var cls = (t.className && t.className.baseVal === undefined ? t.className : '') + ' ' + (t.parentElement && typeof t.parentElement.className === 'string' ? t.parentElement.className : '');
          return { title: text(t).slice(0, 24), path: path(t), on: t.getAttribute('aria-selected') === 'true' || t.getAttribute('aria-current') !== null && t.getAttribute('aria-current') !== 'false' || /(^|\s|-)(active|selected|current|is-on|on)(\s|$)/i.test(cls) };
        });
      }
      el = scope;
    }
    return [];
  }

  var rows = [];
  groups.forEach(function (g) {
    // Two cards make a row only under a heading (a short list of search results, say)
    if (g.cards.length < 2 || (g.cards.length < 3 && !headingFor(g.list))) return;
    var avgW = g.cards.reduce(function (a, c) { return a + c.w }, 0) / g.cards.length;
    var top = g.cards[0].top;
    // A slider of near-full-width pictures at the top of the page is the banner
    if (avgW > W * 0.6 && top < H * 1.2 && !heroes.length && !heroRoot) {
      g.cards.slice(0, 6).forEach(function (c) { heroes.push(c) });
      return;
    }
    var ratios = g.cards.map(function (c) { return c.ratio }).filter(function (x) { return x > 0 }).sort(function (a, b) { return a - b });
    var mid = ratios.length ? ratios[Math.floor(ratios.length / 2)] : 1.7;
    rows.push({ title: headingFor(g.list).slice(0, 60), tall: mid < 0.9, ratio: mid, tabs: tabsFor(g.list), top: top, cards: g.cards.slice(0, 40).map(function (c) { delete c.w; delete c.top; delete c.ratio; return c }) });
  });
  rows.sort(function (a, b) { return a.top - b.top });

  // No slider: the biggest linked picture near the top is the banner
  if (!heroes.length && !heroRoot) {
    var big = null, ba = 0;
    cands.forEach(function (e) {
      var r = rect(e);
      if (r.top + scrollY < H && r.width > W * 0.5 && r.width * r.height > ba) { ba = r.width * r.height; big = e }
    });
    var hc = big && card(big);
    if (hc) heroes.push(hc);
  }
  heroes.forEach(function (h) {
    if (h.desc === undefined) { var el = document.querySelector(h.path), p = el && el.querySelector('p'); h.desc = p ? text(p).slice(0, 260) : '' }
    delete h.w; delete h.top;
  });

  // The menu along the top
  var nav = [], navSeen = {};
  var bars = document.querySelectorAll('nav,header,[role="navigation"]');
  for (var b = 0; b < bars.length && nav.length < 9; b++) {
    var links = bars[b].querySelectorAll('a[href]');
    for (var l = 0; l < links.length && nav.length < 9; l++) {
      var a = links[l], t = text(a);
      if (!t || t.length > 24 || !shown(a) || !sameSite(a.href) || navSeen[a.href]) continue;
      if (/log ?in|sign ?(in|up)|register|account|profile|cart|basket|connexion|تسجيل/i.test(t)) continue;
      if (a.href.split('#')[0] === here.split('#')[0] && nav.length) continue;
      navSeen[a.href] = 1;
      nav.push({ title: t, url: a.href });
    }
  }

  // The item this page is about: page facts (schema.org, OpenGraph), the main heading
  function meta(n) { var m = document.querySelector('meta[property="' + n + '"],meta[name="' + n + '"]'); return m ? clean(m.getAttribute('content')) : '' }
  var ld = null;
  document.querySelectorAll('script[type="application/ld+json"]').forEach(function (s) {
    if (ld) return;
    try {
      var j = JSON.parse(s.textContent), list = [].concat(j['@graph'] || j);
      list.forEach(function (x) {
        var t = [].concat(x && x['@type'] || []).join(' ');
        if (!ld && /Movie|TVSeries|TVSeason|Episode|VideoObject|CreativeWork|Product|Article|NewsArticle|MusicRecording|Recipe/.test(t)) ld = x;
      });
    } catch (e) {}
  });
  var facts = [];
  function iso(d) { var m = /P(?:T)?(?:(\d+)H)?(?:(\d+)M)?/.exec(d || ''); return m && (m[1] || m[2]) ? (m[1] ? m[1] + 'h ' : '') + (m[2] ? m[2] + 'm' : '') : '' }
  var ldType = ld ? [].concat(ld['@type']).join(' ') : '';
  if (ld) {
    var yr = /\d{4}/.exec(ld.datePublished || ld.dateCreated || ld.uploadDate || '');
    if (yr) facts.push(yr[0]);
    var du = iso(ld.duration || ld.timeRequired);
    if (du) facts.push(du.trim());
    [].concat(ld.genre || []).slice(0, 2).forEach(function (g) { if (typeof g === 'string') facts.push(g) });
    if (ld.contentRating && typeof ld.contentRating === 'string') facts.push(ld.contentRating);
    if (ld.numberOfSeasons) facts.push(ld.numberOfSeasons + ' seasons');
  }
  function ldImage(x) { var i = x && x.image; if (Array.isArray(i)) i = i[0]; if (i && typeof i === 'object') i = i.url; return typeof i === 'string' ? abs(i) : null }
  var h1 = document.querySelector('main h1, h1');
  var ogType = meta('og:type');
  var vids = Array.prototype.filter.call(document.querySelectorAll('video'), function (v) { var r = rect(v); return r.width > W * 0.25 });
  var players = Array.prototype.filter.call(document.querySelectorAll('iframe'), function (f) { var r = rect(f); return r.width > W * 0.4 && r.height > H * 0.3 && /player|embed|video|stream/i.test(f.src + f.className + f.id) });

  // A play button: the words on it (several languages), its size and how high up it is
  var play = null, pscore = 0;
  document.querySelectorAll('button,a[href],[role="button"],[class*="play" i]').forEach(function (e) {
    if (!shown(e)) return;
    var t = (clean(e.getAttribute('aria-label')) + ' ' + text(e) + ' ' + (e.className && e.className.baseVal === undefined ? e.className : '')).toLowerCase();
    if (!/(^|\W)(play|watch|stream|start|resume|regarder|lecture|reprendre|ver|reproducir|ansehen|شاهد|مشاهدة|تشغيل|ابدأ)(\W|$)|btn-play|play-button|playbutton/.test(t)) return;
    if (/trailer|bande|إعلان|playlist|play store|google play|display/.test(t)) return;
    var r = rect(e), s = r.width * r.height / (1 + Math.max(0, r.top + scrollY) / H);
    if (s > pscore) { pscore = s; play = e }
  });
  var detail = {
    title: clean((ld && ld.name) || meta('og:title') || (h1 && text(h1)) || document.title).slice(0, 120),
    desc: clean((ld && typeof ld.description === 'string' && ld.description) || meta('og:description') || meta('description')).slice(0, 400),
    image: ldImage(ld) || abs(meta('og:image')) || null,
    facts: facts.slice(0, 5),
    media: /Movie|Episode|VideoObject|TVSeries|TVSeason/.test(ldType) || /^video/.test(ogType),
    video: vids.length > 0 || players.length > 0,
    play: play ? path(play) : null
  };

  // Search: a form sent by address, with its query box
  var search = null;
  var boxes = document.querySelectorAll('input[type="search"],input[name="q"],input[name="s"],input[name="query"],input[name="search"],input[name="keyword"],input[name="k"]');
  for (var q = 0; q < boxes.length && !search; q++) {
    var f = boxes[q].form, nm = boxes[q].name;
    if (f && nm && (f.method || 'get').toLowerCase() === 'get') {
      var act = abs(f.getAttribute('action') || here.split('?')[0]);
      search = act + (act.indexOf('?') < 0 ? '?' : '&') + encodeURIComponent(nm) + '={q}';
    }
  }

  // Otherwise the search box itself (to type into), or the button or page that brings it
  var searchInput = null, searchButton = null, searchPage = null;
  var anyBox = document.querySelector('input[type="search"]') || Array.prototype.filter.call(document.querySelectorAll('input:not([type]),input[type="text"]'), function (i) {
    return /search|recherche|chercher|buscar|suche|بحث|ابحث/i.test((i.name || '') + ' ' + (i.placeholder || '') + ' ' + (i.getAttribute('aria-label') || '') + ' ' + (i.id || '') + ' ' + i.className);
  })[0];
  if (anyBox) searchInput = path(anyBox);
  document.querySelectorAll('a[href],button,[role="button"]').forEach(function (b) {
    if (searchButton || searchPage) return;
    var t = (clean(b.getAttribute('aria-label')) + ' ' + clean(b.getAttribute('title')) + ' ' + text(b) + ' ' + (typeof b.className === 'string' ? b.className : '') + ' ' + (b.id || '')).toLowerCase();
    if (!/search|recherche|chercher|بحث|ابحث/.test(t) || t.length > 120) return;
    if (b.tagName === 'A' && /^https?:/.test(b.href) && sameSite(b.href) && b.href.split('#')[0] !== here.split('#')[0]) searchPage = b.href;
    else searchButton = path(b);
  });

  // Signing in: a sign-in link or button, and whether someone is signed in already
  var login = null, signedIn = false;
  document.querySelectorAll('a[href],button,[role="button"]').forEach(function (b) {
    var t = clean(clean(b.getAttribute('aria-label')) || text(b) || clean(b.getAttribute('title'))).toLowerCase();
    if (!t || t.length > 30) return;
    if (/^(sign ?out|log ?out|logout|se déconnecter|déconnexion|تسجيل الخروج|خروج)$/.test(t)) signedIn = true;
    if (!login && /^(sign ?in|log ?in|login|sign in \/ sign up|sign up|register|connexion|se connecter|s'identifier|تسجيل الدخول|دخول)$/.test(t) && (shown(b) || b.closest('header,nav'))) {
      login = { title: clean(text(b)) || t, url: b.tagName === 'A' && /^https?:/.test(b.href) ? b.href : null, path: path(b) };
    }
  });
  if (document.querySelector('[class*="avatar" i] img,img[class*="avatar" i],[class*="profile" i] img,[aria-label*="account" i] img')) signedIn = true;

  return JSON.stringify({
    url: here,
    site: clean(meta('og:site_name') || meta('application-name') || document.title).slice(0, 60),
    nav: nav, heroes: heroes.slice(0, 6),
    rows: rows.slice(0, 24).map(function (r) { delete r.top; return r }),
    detail: detail, search: search, searchInput: searchInput, searchButton: searchButton, searchPage: searchPage,
    login: signedIn ? null : login, signedIn: signedIn
  });
})()
