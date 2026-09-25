// Changelog dialog. The server copies CHANGELOG.md from GitHub next to this page (ftr-latest.py,
// every 15 minutes), so it is always current without visitors calling GitHub themselves.
// A deliberately small Markdown renderer for what the changelog actually uses: headings, nested
// bullet and numbered lists, paragraphs, **bold**, *emphasis*, `code`, [links](https://…), <https://…>.
// Every piece of text is escaped first; only http(s) links become anchors.
(function () {
  'use strict';

  function esc(s) {
    return s.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;');
  }

  function inline(raw) {
    var codes = [];
    // Code spans first, so nothing inside them is treated as formatting.
    var s = raw.replace(/`([^`]+)`/g, function (_, c) { codes.push(c); return '\u0000' + (codes.length - 1) + '\u0000'; });
    s = esc(s);
    s = s.replace(/\[([^\]]+)\]\((https?:\/\/[^)\s]+)\)/g, function (_, t, u) {
      return '<a href="' + u + '" target="_blank" rel="noopener noreferrer">' + t + '</a>';
    });
    s = s.replace(/&lt;(https?:\/\/[^\s&]+)&gt;/g, function (_, u) {
      return '<a href="' + u + '" target="_blank" rel="noopener noreferrer">' + u + '</a>';
    });
    s = s.replace(/\*\*([^*]+)\*\*/g, '<strong>$1</strong>');
    s = s.replace(/(^|[^*\w])\*([^*\s][^*]*?)\*(?!\w)/g, '$1<em>$2</em>');
    s = s.replace(/\u0000(\d+)\u0000/g, function (_, i) { return '<code>' + esc(codes[+i]) + '</code>'; });
    return s;
  }

  function render(md) {
    var lines = md.replace(/\r/g, '').split('\n');
    // Start at the first version section; drop reference-style link definitions at the end.
    var start = lines.findIndex(function (l) { return /^## \[/.test(l); });
    if (start < 0) start = 0;
    lines = lines.slice(start).filter(function (l) { return !/^\[[^\]]+\]:\s+https?:\/\//.test(l); });

    var out = [];
    var para = [];
    var stack = []; // open lists: {indent, tag}
    var item = null; // text of the list item being collected

    function flushPara() {
      if (para.length) { out.push('<p>' + inline(para.join(' ')) + '</p>'); para = []; }
    }
    function flushItem() {
      if (item !== null) { out.push(inline(item)); item = null; }
    }
    function closeLists(toIndent) {
      flushItem();
      while (stack.length && stack[stack.length - 1].indent >= toIndent) {
        out.push('</li></' + stack.pop().tag + '>');
      }
    }

    lines.forEach(function (line) {
      var h = /^(#{2,4}) (.*)$/.exec(line);
      var li = /^( *)([-*]|\d+\.) (.*)$/.exec(line);
      if (h) {
        flushPara(); closeLists(0);
        var level = h[1].length;
        var text = h[2];
        // "## [1.11.0] - 2026-09-25" reads better as "1.11.0 · 2026-09-25".
        var ver = /^\[([^\]]+)\](?:\s*-\s*(.*))?$/.exec(text);
        if (level === 2 && ver) {
          out.push('<h3 class="cl-version"><span>' + esc(ver[1]) + '</span>' +
            (ver[2] ? '<time>' + esc(ver[2]) + '</time>' : '') + '</h3>');
        } else {
          out.push('<h4>' + inline(text) + '</h4>');
        }
      } else if (li) {
        flushPara();
        var indent = li[1].length;
        var tag = /\d/.test(li[2]) ? 'ol' : 'ul';
        flushItem();
        var top = stack[stack.length - 1];
        if (!top || indent > top.indent) {
          out.push('<' + tag + '><li>');
          stack.push({ indent: indent, tag: tag });
        } else {
          closeLists(indent + 1);
          top = stack[stack.length - 1];
          if (top && top.indent === indent && top.tag === tag) {
            out.push('</li><li>');
          } else {
            if (top && top.indent === indent) out.push('</li></' + stack.pop().tag + '>');
            out.push('<' + tag + '><li>');
            stack.push({ indent: indent, tag: tag });
          }
        }
        item = li[3];
      } else if (/^\s*$/.test(line)) {
        flushPara();
        flushItem();
      } else if (stack.length && /^ +/.test(line)) {
        // Continuation of the current list item.
        if (item === null) { item = line.trim(); } else { item += ' ' + line.trim(); }
      } else {
        closeLists(0);
        para.push(line.trim());
      }
    });
    flushPara(); closeLists(0);
    // An empty "Unreleased" section is noise on a public page.
    return out.join('\n').replace(/<h3 class="cl-version"><span>Unreleased<\/span><\/h3>\s*(?=<h3)/, '');
  }

  var dialog = document.getElementById('changelog');
  var body = document.getElementById('changelog-body');
  var loaded = false;

  function load() {
    if (loaded) return;
    body.setAttribute('aria-busy', 'true');
    fetch('changelog.md', { cache: 'no-cache' })
      .then(function (r) { if (!r.ok) throw new Error(r.status); return r.text(); })
      .then(function (md) { body.innerHTML = render(md); loaded = true; })
      .catch(function () {
        body.innerHTML = '<p>' + esc(document.getElementById('changelog-error').textContent) +
          ' <a href="https://github.com/pepperonas/flipper-the-ripper/blob/main/CHANGELOG.md" target="_blank" rel="noopener noreferrer">GitHub</a></p>';
      })
      .then(function () { body.removeAttribute('aria-busy'); });
  }

  document.getElementById('changelog-open').addEventListener('click', function () {
    if (typeof dialog.showModal !== 'function') {
      window.open('https://github.com/pepperonas/flipper-the-ripper/blob/main/CHANGELOG.md', '_blank', 'noopener');
      return;
    }
    dialog.showModal();
    load();
  });
  document.getElementById('changelog-close').addEventListener('click', function () { dialog.close(); });
  dialog.addEventListener('click', function (e) {
    if (e.target !== dialog) return;
    var r = dialog.getBoundingClientRect();
    if (e.clientX < r.left || e.clientX > r.right || e.clientY < r.top || e.clientY > r.bottom) dialog.close();
  });

})();
