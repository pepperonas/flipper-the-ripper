// Flipper the Ripper — product page.
// English is in the markup; i18n.js carries DE/ES/IT/FR, picked from the browser like the app does.
(function () {
  'use strict';

  var I18N = window.FTR_I18N || {};
  var LANGS = ["en", "de", "es", "it", "fr"];

  var EN = {}; // captured from the markup on first switch, so English lives in one place only
  var nodes = document.querySelectorAll('[data-i18n]');
  var altNodes = document.querySelectorAll('[data-i18n-alt]');
  var ariaNodes = document.querySelectorAll('[data-i18n-aria]');
  var langBtn = document.getElementById('lang-btn');
  var langMenu = document.getElementById('lang-menu');
  var langItems = Array.prototype.slice.call(langMenu.querySelectorAll('[data-lang]'));
  var LANG_NAMES = { en: 'English', de: 'Deutsch', es: 'Español', it: 'Italiano', fr: 'Français' };
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
    ariaNodes.forEach(function (n) {
      var k = n.getAttribute('data-i18n-aria');
      if (!(k in EN)) EN[k] = n.getAttribute('aria-label');
      n.setAttribute('aria-label', dict[k] || EN[k]);
    });
    document.documentElement.lang = lang;
    showLang(lang);
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

  // ---- language menu: a button and a listbox (flags + native names), keyboard like a select
  function showLang(lang) {
    document.getElementById('lang-flag').className = 'flag flag-' + lang;
    document.getElementById('lang-code').textContent = lang.toUpperCase();
    langBtn.setAttribute('aria-label', 'Language: ' + LANG_NAMES[lang]);
    langItems.forEach(function (li) { li.setAttribute('aria-selected', String(li.getAttribute('data-lang') === lang)); });
  }
  var activeIndex = 0;
  function setActive(i) {
    activeIndex = (i + langItems.length) % langItems.length;
    langItems.forEach(function (li, n) { li.classList.toggle('active', n === activeIndex); });
    langMenu.setAttribute('aria-activedescendant', langItems[activeIndex].id);
  }
  function openMenu() {
    langMenu.hidden = false;
    langBtn.setAttribute('aria-expanded', 'true');
    setActive(LANGS.indexOf(current));
    langMenu.focus();
  }
  function closeMenu(refocus) {
    if (langMenu.hidden) return;
    langMenu.hidden = true;
    langBtn.setAttribute('aria-expanded', 'false');
    if (refocus) langBtn.focus();
  }
  function choose(lang) {
    closeMenu(true);
    if (lang === current) return;
    current = lang;
    store('ftr-lang', current);
    apply(current);
  }
  langBtn.addEventListener('click', function () { if (langMenu.hidden) openMenu(); else closeMenu(true); });
  langBtn.addEventListener('keydown', function (e) {
    if (e.key === 'ArrowDown' || e.key === 'ArrowUp') { e.preventDefault(); openMenu(); }
  });
  langMenu.addEventListener('keydown', function (e) {
    if (e.key === 'ArrowDown') { e.preventDefault(); setActive(activeIndex + 1); }
    else if (e.key === 'ArrowUp') { e.preventDefault(); setActive(activeIndex - 1); }
    else if (e.key === 'Home') { e.preventDefault(); setActive(0); }
    else if (e.key === 'End') { e.preventDefault(); setActive(langItems.length - 1); }
    else if (e.key === 'Enter' || e.key === ' ') { e.preventDefault(); choose(langItems[activeIndex].getAttribute('data-lang')); }
    else if (e.key === 'Escape' || e.key === 'Tab') { closeMenu(e.key === 'Escape'); }
  });
  langItems.forEach(function (li, n) {
    li.addEventListener('click', function () { choose(li.getAttribute('data-lang')); });
    li.addEventListener('mousemove', function () { if (n !== activeIndex) setActive(n); });
  });
  document.addEventListener('click', function (e) {
    if (!langMenu.hidden && !document.getElementById('lang').contains(e.target)) closeMenu(false);
  });

  var saved = load('ftr-lang');
  var current = LANGS.indexOf(saved) >= 0 ? saved : fromBrowser();
  if (current !== 'en') apply(current);
  else showLang('en');

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

  // ---- licence: the full MIT text in a dialog instead of a trip to GitHub
  var licence = document.getElementById('license');
  document.getElementById('license-open').addEventListener('click', function () {
    if (typeof licence.showModal === 'function') licence.showModal();
    else window.open('https://github.com/pepperonas/flipper-the-ripper/blob/main/LICENSE', '_blank', 'noopener');
  });
  document.getElementById('license-close').addEventListener('click', function () { licence.close(); });
  // A click on the backdrop lands on the dialog element itself, outside its content box.
  licence.addEventListener('click', function (e) {
    if (e.target !== licence) return;
    var r = licence.getBoundingClientRect();
    if (e.clientX < r.left || e.clientX > r.right || e.clientY < r.top || e.clientY > r.bottom) licence.close();
  });

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
