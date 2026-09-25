// WebMCP tools: lets an AI agent in the browser ask this page for facts and actions directly instead of
// scraping it (https://developer.chrome.com/docs/ai/webmcp). Only registered where the browser offers
// `modelContext`; everywhere else this file does nothing. All data comes from the same places the page
// uses — latest.json and changelog.md on this origin — so tools and page can never disagree.
(function () {
  'use strict';

  var mc = (typeof document !== 'undefined' && document.modelContext) ||
    (typeof navigator !== 'undefined' && navigator.modelContext);
  if (!mc || typeof mc.registerTool !== 'function') return;

  var SITE = 'https://flipper-the-ripper.celox.io';
  var CERT = '1fc3904fd80eb8135b25ee15fe62ec3b74545eba360298c152f712646fd910cb';
  var LANGS = ['en', 'de', 'es', 'it', 'fr'];

  function latest(signal) {
    return fetch('/latest.json', { cache: 'no-cache', signal: signal }).then(function (r) {
      if (!r.ok) throw new Error('latest.json: HTTP ' + r.status);
      return r.json();
    });
  }
  function json(v) { return JSON.stringify(v, null, 2); }
  var READ = { readOnlyHint: true };

  var tools = [
    {
      name: 'get_app_facts',
      description: 'What Flipper the Ripper is: a one-paragraph summary, requirements, supported platforms, ' +
        'features, limits and the important links (download, source, changelog, licence).',
      inputSchema: { type: 'object', properties: {}, additionalProperties: false },
      annotations: READ,
      execute: function () {
        return Promise.resolve(json({
          name: 'Flipper the Ripper',
          summary: 'Free, open-source (MIT) Android app that saves publicly accessible videos from YouTube, ' +
            'Instagram, TikTok, Facebook, X and Dailymotion to the phone’s gallery. Share a link to the app ' +
            'and the download starts. No account, no ads, no tracking. Not on Google Play by design.',
          requirements: 'Android 7.0 or later, 64-bit ARM',
          platforms: ['YouTube', 'Instagram', 'TikTok', 'Facebook', 'X', 'Dailymotion'],
          features: [
            'Share-sheet integration and clipboard detection; one-tap download',
            'Quality: Best, 1080p, 720p, 480p, audio only',
            'Queue: one at a time, pause/resume, drag to reorder, swipe away with undo',
            'Background downloads with one progress notification',
            'Saves to Movies, named after the video title',
            'Self-updating yt-dlp extractor',
            'English, German, Spanish, Italian, French'],
          limits: ['Publicly accessible content and lawful personal use only; no DRM/paywall/login bypass',
            'No playlists, no subtitles'],
          links: {
            download: SITE + '/download', latest: SITE + '/latest.json', markdown: SITE + '/index.md',
            changelog: SITE + '/changelog.md', source: 'https://github.com/pepperonas/flipper-the-ripper',
            licence: 'https://github.com/pepperonas/flipper-the-ripper/blob/main/LICENSE'
          }
        }));
      }
    },
    {
      name: 'get_latest_release',
      description: 'The newest release of Flipper the Ripper: version, publication date, release notes URL ' +
        'and every file with its download URL, size in bytes and SHA-256.',
      inputSchema: { type: 'object', properties: {}, additionalProperties: false },
      annotations: READ,
      execute: function (_input, opts) {
        return latest(opts && opts.signal).then(function (d) {
          return json({ version: d.version, published: d.published, notes: d.notes, assets: d.assets ||
            [{ target: 'android', name: d.name, url: d.url, size: d.size, sha256: d.sha256 }] });
        });
      }
    },
    {
      name: 'get_download_url',
      description: 'The direct download URL of the newest Flipper the Ripper APK (Android, 64-bit ARM), plus ' +
        'the stable link that always redirects to the newest file.',
      inputSchema: {
        type: 'object',
        properties: { platform: { type: 'string', enum: ['android'], description: 'Target platform.' } },
        additionalProperties: false
      },
      annotations: READ,
      execute: function (input, opts) {
        return latest(opts && opts.signal).then(function (d) {
          var assets = d.assets || [{ target: 'android', name: d.name, url: d.url, size: d.size }];
          var want = (input && input.platform) || assets[0].target;
          var a = assets.filter(function (x) { return x.target === want; })[0] || assets[0];
          return json({ version: d.version, file: a.name, url: a.url, size: a.size,
            stable_link: SITE + '/download' });
        });
      }
    },
    {
      name: 'get_checksums',
      description: 'How to verify a downloaded Flipper the Ripper APK: the SHA-256 of the newest file and the ' +
        'signing-certificate SHA-256 that every release since 1.0.0 carries.',
      inputSchema: { type: 'object', properties: {}, additionalProperties: false },
      annotations: READ,
      execute: function (_input, opts) {
        return latest(opts && opts.signal).then(function (d) {
          var assets = d.assets || [{ name: d.name, sha256: d.sha256 }];
          return json({ version: d.version,
            files: assets.map(function (a) { return { name: a.name, sha256: a.sha256 }; }),
            signing_certificate_sha256: CERT,
            check_with: 'apksigner verify --print-certs <file>.apk | grep SHA-256' });
        });
      }
    },
    {
      name: 'get_changelog',
      description: 'The most recent entries of the Flipper the Ripper changelog as Markdown.',
      inputSchema: {
        type: 'object',
        properties: { versions: { type: 'integer', minimum: 1, maximum: 10, description: 'How many versions (default 3).' } },
        additionalProperties: false
      },
      annotations: READ,
      execute: function (input, opts) {
        var n = Math.min(10, Math.max(1, (input && input.versions) || 3));
        return fetch('/changelog.md', { cache: 'no-cache', signal: opts && opts.signal }).then(function (r) {
          if (!r.ok) throw new Error('changelog.md: HTTP ' + r.status);
          return r.text();
        }).then(function (md) {
          var parts = md.split(/\n(?=## \[)/).slice(1).filter(function (s) { return !/^## \[Unreleased\]\s*$/.test(s.trim()); });
          return parts.slice(0, n).join('\n').trim();
        });
      }
    },
    {
      name: 'set_page_language',
      description: 'Show this page in another language (English, German, Spanish, Italian or French).',
      inputSchema: {
        type: 'object',
        properties: { language: { type: 'string', enum: LANGS, description: 'ISO 639-1 code.' } },
        required: ['language'],
        additionalProperties: false
      },
      annotations: { readOnlyHint: false },
      execute: function (input) {
        if (LANGS.indexOf(input.language) < 0) throw new Error('unsupported language: ' + input.language);
        if (typeof window.SITE_setLanguage === 'function') window.SITE_setLanguage(input.language);
        return Promise.resolve('Page language set to ' + input.language + '.');
      }
    }
  ];

  tools.forEach(function (t) {
    try { mc.registerTool(t); } catch (e) { /* a browser with an older WebMCP shape: skip that tool */ }
  });
})();
