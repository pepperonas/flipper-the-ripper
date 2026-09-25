#!/usr/bin/env python3
"""Publish the newest Flipper the Ripper release next to the product page.

Runs on the VPS from ftr-latest.timer. Writes two files, both only when something changed:

  <webroot>/latest.json      read by the page (same origin: visitors never call GitHub's API)
  <webroot>/changelog.md     CHANGELOG.md from main, shown in the page's changelog dialog
  <webroot>/ssi/*            version, size, date, SHA-256 and the meta line, pulled into index.html and
                             index.md by nginx SSI — so the facts are in the HTML itself, for agents and
                             for anyone without JavaScript
  /etc/nginx/ftr-download.conf  `location = /download` -> 302 to the newest APK, so
                               https://flipper-the-ripper.celox.io/download is a stable link

A failed GitHub call changes nothing: the last good state stays in place.
"""
import datetime, html, json, os, re, subprocess, sys, tempfile, urllib.request

REPO = "pepperonas/flipper-the-ripper"
WEBROOT = os.environ.get("FTR_WEBROOT", "/var/www/flipper-the-ripper.celox.io")
NGINX_INC = os.environ.get("FTR_NGINX_INC", "/etc/nginx/ftr-download.conf")
ASSET = re.compile(r"^flipper-the-ripper-v[0-9][0-9A-Za-z.\-]*\.apk$")
URL = re.compile(r"^https://github\.com/pepperonas/flipper-the-ripper/releases/download/[^\s;\"'{}]+\.apk$")


def fetch():
    req = urllib.request.Request(
        f"https://api.github.com/repos/{REPO}/releases/latest",
        headers={"Accept": "application/vnd.github+json", "User-Agent": "ftr-latest"},
    )
    with urllib.request.urlopen(req, timeout=20) as r:
        rel = json.load(r)
    apks = [a for a in rel.get("assets", []) if ASSET.match(a.get("name", ""))]
    if len(apks) != 1:
        raise SystemExit(f"expected one APK asset, found {len(apks)}")
    a = apks[0]
    url = a["browser_download_url"]
    if not URL.match(url):
        raise SystemExit(f"unexpected asset URL: {url!r}")
    digest = a.get("digest") or ""
    return {
        "version": rel["tag_name"],
        "name": a["name"],
        "url": url,
        "size": a["size"],
        "sha256": digest.split(":", 1)[1] if digest.startswith("sha256:") else "",
        "published": rel.get("published_at", ""),
        "notes": rel.get("html_url", ""),
    }


CHANGELOG_URL = f"https://raw.githubusercontent.com/{REPO}/main/CHANGELOG.md"
CHANGELOG_MAX = 1_000_000


def fetch_changelog():
    req = urllib.request.Request(CHANGELOG_URL, headers={"User-Agent": "ftr-latest"})
    with urllib.request.urlopen(req, timeout=20) as r:
        body = r.read(CHANGELOG_MAX + 1)
    text = body.decode("utf-8")
    # Refuse anything that is not recognisably the changelog (an error page, a truncated body).
    if len(body) > CHANGELOG_MAX or not text.startswith("# Changelog") or "\n## [" not in text:
        raise ValueError("unexpected CHANGELOG.md content")
    return text


def ssi_fragments(d):
    """The release facts as tiny text files for nginx SSI (English; the page's JavaScript localises)."""
    size = f"{d['size'] / 1048576:.1f} MB"
    day = d["published"][:10]
    try:
        pretty = datetime.date.fromisoformat(day).strftime("%-d %b %Y")
    except ValueError:
        pretty = day
    meta = " · ".join(filter(None, [d["version"], size, pretty, "Android 7.0+", "64-bit ARM"]))
    return {
        "version.txt": d["version"].lstrip("v"),
        "size.txt": size,
        "date.txt": day,
        "sha.txt": d["sha256"],
        "meta.html": html.escape(meta),
    }


def same(path, text):
    try:
        with open(path) as f:
            return f.read() == text
    except FileNotFoundError:
        return False


def write_if_changed(path, text, mode=0o644):
    try:
        with open(path) as f:
            if f.read() == text:
                return False
    except FileNotFoundError:
        pass
    fd, tmp = tempfile.mkstemp(dir=os.path.dirname(path), prefix=".ftr-")
    with os.fdopen(fd, "w") as f:
        f.write(text)
    os.chmod(tmp, mode)
    os.replace(tmp, path)
    return True


def main():
    try:
        d = fetch()
    except Exception as e:  # network, rate limit, malformed release: keep the last good state
        print(f"ftr-latest: keeping previous state ({e})", file=sys.stderr)
        return 1
    changed_json = write_if_changed(os.path.join(WEBROOT, "latest.json"), json.dumps(d, indent=2) + "\n")
    ssi_dir = os.path.join(WEBROOT, "ssi")
    os.makedirs(ssi_dir, exist_ok=True)
    changed_ssi = False
    for name, text in ssi_fragments(d).items():
        changed_ssi |= write_if_changed(os.path.join(ssi_dir, name), text)
    try:
        changed_log = write_if_changed(os.path.join(WEBROOT, "changelog.md"), fetch_changelog())
    except Exception as e:  # the dialog keeps showing the last good copy
        print(f"ftr-latest: changelog not refreshed ({e})", file=sys.stderr)
        changed_log = False
    conf = (
        "# Written by ftr-latest.py - do not edit.\n"
        f"location = /download {{\n    add_header Cache-Control \"no-store\" always;\n    return 302 {d['url']};\n}}\n"
    )
    changed_conf = False
    if not same(NGINX_INC, conf):
        # Never touch nginx while certbot is working on it: leave the old include in place,
        # the next run sees the difference again and does it then.
        if subprocess.run(["pgrep", "-x", "certbot"], capture_output=True).returncode == 0:
            print("ftr-latest: certbot running, nginx update deferred", file=sys.stderr)
            return 0
        backup = open(NGINX_INC).read() if os.path.exists(NGINX_INC) else None
        write_if_changed(NGINX_INC, conf)
        t = subprocess.run(["nginx", "-t"], capture_output=True, text=True)
        if t.returncode != 0:
            print(t.stderr, file=sys.stderr)
            if backup is None:
                os.remove(NGINX_INC)
            else:
                write_if_changed(NGINX_INC, backup)
            return 1
        subprocess.run(["systemctl", "reload", "nginx"], check=True)
        changed_conf = True
    print(
        f"ftr-latest: {d['version']} json={'new' if changed_json else 'same'} "
        f"ssi={'new' if changed_ssi else 'same'} changelog={'new' if changed_log else 'same'} "
        f"nginx={'reloaded' if changed_conf else 'same'}"
    )
    return 0


if __name__ == "__main__":
    sys.exit(main())
