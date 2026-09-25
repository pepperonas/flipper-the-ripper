// Flipper the Ripper — product page.
// English is in the markup; i18n.js carries DE/ES/IT/FR, picked from the browser like the app does.
(function () {
  'use strict';

  var I18N = window.FTR_I18N || {};
  var LANGS = ["en", "de", "es", "it", "fr"];

  var EN = {}; // captured from the markup on first switch, so English lives in one place only
  var nodes = document.querySelectorAll('[data-i18n]');
  var altNodes = document.querySelectorAll('[data-i18n-alt]');
  var picker = document.getElementById('lang');
  var release = null;

  function store(k, v) { try { localStorage.setItem(k, v); } catch (e) { /* private mode */ } }
  function load(k) { try { return localStorage.getItem(k); } catch (e) { return null; } }

  function apply(lang) {
    var dict = I18N[lang] || {};
    nodes.forEach(function (n) {
      var k = n.getAttribute('data-i18n');
      if (!(k in EN)) EN[k] = n.innerHTML;
      n.innerHTML = dict[k] || EN[k];
    });
    altNodes.forEach(function (n) {
      var k = n.getAttribute('data-i18n-alt');
      if (!(k in EN)) EN[k] = n.getAttribute('alt');
      n.setAttribute('alt', dict[k] || EN[k]);
    });
    document.documentElement.lang = lang;
    picker.value = lang;
    renderMeta();
  }

  function fromBrowser() {
    var list = navigator.languages && navigator.languages.length ? navigator.languages : [navigator.language || 'en'];
    for (var i = 0; i < list.length; i++) {
      var code = String(list[i]).toLowerCase().slice(0, 2);
      if (LANGS.indexOf(code) >= 0) return code;
    }
    return 'en';
  }

  var saved = load('ftr-lang');
  var current = LANGS.indexOf(saved) >= 0 ? saved : fromBrowser();
  if (current !== 'en') apply(current);
  picker.value = current;
  picker.addEventListener('change', function () {
    current = picker.value;
    store('ftr-lang', current);
    apply(current);
  });

  // ---- latest release (written next to this page by a server-side timer; no third-party call)
  function mb(bytes) {
    try { return (bytes / 1048576).toLocaleString(current, { minimumFractionDigits: 1, maximumFractionDigits: 1 }) + ' MB'; }
    catch (e) { return (bytes / 1048576).toFixed(1) + ' MB'; }
  }
  function date(iso) {
    try { return new Date(iso).toLocaleDateString(current === 'en' ? 'en-GB' : current, { day: 'numeric', month: 'short', year: 'numeric' }); }
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
