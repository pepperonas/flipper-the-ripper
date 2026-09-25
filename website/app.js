// Flipper the Ripper — product page.
// English is in the markup; German is swapped in for German browsers (like the app itself).
(function () {
  'use strict';

  var DE = {
    skip: 'Zum Inhalt springen',
    'nav.features': 'Funktionen',
    'nav.install': 'Installieren',
    'hero.kicker': 'Android · Open Source · Kostenlos',
    'hero.title': 'Link teilen.<br>Video behalten.',
    'hero.lead': 'Speichere öffentliche Videos von sechs Plattformen direkt in deine Galerie — <em>Teilen</em> antippen, Flipper the Ripper wählen, fertig. Kein Konto, keine Werbung, kein Tracking.',
    'hero.download': 'APK herunterladen',
    'hero.source': 'Quellcode',
    'hero.meta': 'Neueste Version · Android 7.0+ · 64-Bit-ARM',
    'pl.h': 'Unterstützte Plattformen',
    'f.h': 'Gebaut für möglichst wenige Handgriffe',
    'f1.t': 'Teilen, und es geht los',
    'f1.p': 'Wähle die App in einem beliebigen Teilen-Menü. Der Download startet sofort und steht oben in der Liste — oder kopiere einen Link, dann bietet die App ihn beim Start an.',
    'f2.t': 'Eine Warteschlange, die du steuerst',
    'f2.p': 'Ein Download nach dem anderen, in der angezeigten Reihenfolge. Pausieren und ab dem letzten Byte fortsetzen, Wartende umsortieren, Fertige wegwischen.',
    'f3.t': 'Qualität wählen',
    'f3.p': 'Beste, 1080p, 720p, 480p oder nur Audio. Stufen, die ein Video nicht erreicht, sind ausgegraut — und eine gemerkte Voreinstellung hält geteilte Links bei einem Tipp.',
    'f4.t': 'Läuft im Hintergrund weiter',
    'f4.p': 'Gesperrter Bildschirm, minimierte App, gedrehtes Handy — die Downloads laufen weiter, mit einer Benachrichtigung für den ganzen Stapel.',
    'f5.t': 'Direkt in die Galerie',
    'f5.p': 'Dateien landen in <em>Movies</em>, benannt nach dem Videotitel, und erscheinen sofort in Google Fotos und jedem Dateimanager.',
    'f6.t': 'Bleibt funktionsfähig',
    'f6.p': 'Das mitgelieferte yt-dlp aktualisiert sich selbst, denn Plattformen machen alte Extraktoren binnen Monaten kaputt. Die App meldet, wenn eine neue Version erscheint.',
    's.h': 'Material 3 Expressive, dunkel und hell',
    's.alt': 'Vier Bildschirme: das Qualitätsmenü auf der Startseite, die Download-Warteschlange, die Fortschrittsbenachrichtigung und die deutschen Einstellungen',
    'i.h': 'In drei Schritten installiert',
    'i1.t': 'APK herunterladen',
    'i1.p': 'Eine Datei für jedes Handy seit etwa 2016 — es gibt nichts auszuwählen.',
    'i2.t': 'Installation erlauben',
    'i2.p': 'Datei öffnen. Android fragt einmal, ob dein Browser Apps installieren darf — erlauben.',
    'i3.t': 'Link teilen',
    'i3.p': 'Updates installieren sich über die bestehende App; die Startseite zeigt an, wenn eines bereitsteht.',
    'v.h': 'Prüfe, was du geladen hast',
    'v.apk': 'APK SHA-256',
    'v.cert': 'SHA-256 des Signaturzertifikats',
    'v.p': 'Jede Version seit 1.0.0 ist mit demselben Schlüssel signiert. Vergleiche mit <code>apksigner verify --print-certs</code>.',
    'i.play': 'Bewusst nicht bei Google Play: Der Play Store erlaubt weder Video-Downloader noch sich selbst aktualisierende Engines. Verteilt wird über GitHub Releases, wie bei NewPipe und Seal.',
    'l.h': 'Speichere nur, was du behalten darfst',
    'l.p': 'Flipper the Ripper ist für öffentlich zugängliche Inhalte und den persönlichen, rechtmäßigen Gebrauch gedacht. Die App umgeht weder DRM noch Bezahlschranken oder Zugangssperren. Beachte die Nutzungsbedingungen der Plattformen und das Urheberrecht.',
    'ft.license': 'MIT-Lizenz',
    'ft.changelog': 'Änderungen',
    'ft.donate': 'Projekt unterstützen',
    'ft.imprint': 'Impressum',
    'ft.privacy': 'Datenschutz'
  };

  var EN = {}; // captured from the markup on first switch, so English lives in one place only
  var nodes = document.querySelectorAll('[data-i18n]');
  var altNodes = document.querySelectorAll('[data-i18n-alt]');
  var btn = document.getElementById('lang');
  var release = null;

  function store(k, v) { try { localStorage.setItem(k, v); } catch (e) { /* private mode */ } }
  function load(k) { try { return localStorage.getItem(k); } catch (e) { return null; } }

  function apply(lang) {
    var de = lang === 'de';
    nodes.forEach(function (n) {
      var k = n.getAttribute('data-i18n');
      if (!(k in EN)) EN[k] = n.innerHTML;
      n.innerHTML = de && DE[k] ? DE[k] : EN[k];
    });
    altNodes.forEach(function (n) {
      var k = n.getAttribute('data-i18n-alt');
      if (!(k in EN)) EN[k] = n.getAttribute('alt');
      n.setAttribute('alt', de && DE[k] ? DE[k] : EN[k]);
    });
    document.documentElement.lang = de ? 'de' : 'en';
    btn.textContent = de ? 'EN' : 'DE';
    btn.setAttribute('aria-label', de ? 'English' : 'Deutsch');
    renderMeta();
  }

  var current = load('ftr-lang') || ((navigator.language || '').toLowerCase().indexOf('de') === 0 ? 'de' : 'en');
  if (current === 'de') apply('de');
  btn.addEventListener('click', function () {
    current = current === 'de' ? 'en' : 'de';
    store('ftr-lang', current);
    apply(current);
  });

  // ---- latest release (written next to this page by a server-side timer; no third-party call)
  function mb(bytes) { return (bytes / 1048576).toFixed(1).replace('.', current === 'de' ? ',' : '.') + ' MB'; }
  function date(iso) {
    try { return new Date(iso).toLocaleDateString(current === 'de' ? 'de-DE' : 'en-GB', { day: 'numeric', month: 'short', year: 'numeric' }); }
    catch (e) { return ''; }
  }
  function renderMeta() {
    if (!release) return;
    var meta = document.getElementById('dl-meta');
    meta.textContent = [release.version, mb(release.size), date(release.published), 'Android 7.0+'].filter(Boolean).join(' · ');
  }

  fetch('latest.json', { cache: 'no-cache' })
    .then(function (r) { return r.ok ? r.json() : null; })
    .then(function (d) {
      if (!d || !/^https:\/\/github\.com\/pepperonas\/flipper-the-ripper\/releases\/download\//.test(d.url || '')) return;
      release = d;
      document.getElementById('dl').href = d.url;
      if (d.sha256) document.getElementById('sha').textContent = d.sha256;
      renderMeta();
    })
    .catch(function () { /* the link keeps pointing at the latest release page */ });

  // ---- chrome
  var bar = document.querySelector('.bar');
  function onScroll() { bar.classList.toggle('solid', window.scrollY > 24); }
  window.addEventListener('scroll', onScroll, { passive: true });
  onScroll();

  document.getElementById('year').textContent = new Date().getFullYear();

  if ('IntersectionObserver' in window && !matchMedia('(prefers-reduced-motion: reduce)').matches) {
    var io = new IntersectionObserver(function (entries) {
      entries.forEach(function (e) {
        if (!e.isIntersecting) return;
        var el = e.target;
        el.classList.add('in');
        io.unobserve(el);
        // Hand the element back to its own transitions (card hover) once it has arrived.
        setTimeout(function () { el.classList.remove('reveal', 'in'); el.style.transitionDelay = ''; }, 1000);
      });
    }, { rootMargin: '0px 0px -8% 0px' });
    document.querySelectorAll('.section .card, .section h2, .shots picture, .platforms li').forEach(function (el, i) {
      el.classList.add('reveal');
      el.style.transitionDelay = (i % 3) * 60 + 'ms';
      io.observe(el);
    });
  }
})();
