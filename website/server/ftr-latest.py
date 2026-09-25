#!/usr/bin/env python3
"""Publish the newest Flipper the Ripper release next to the product page.

Runs on the VPS from ftr-latest.timer. Writes two files, both only when something changed:

  <webroot>/latest.json      read by the page (same origin: visitors never call GitHub's API)
  /etc/nginx/ftr-download.conf  `location = /download` -> 302 to the newest APK, so
                               https://flipper-the-ripper.celox.io/download is a stable link

A failed GitHub call changes nothing: the last good state stays in place.
"""
import json, os, re, subprocess, sys, tempfile, urllib.request

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
    print(f"ftr-latest: {d['version']} json={'new' if changed_json else 'same'} nginx={'reloaded' if changed_conf else 'same'}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
