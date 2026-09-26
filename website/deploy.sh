#!/usr/bin/env bash
# Deploy the product page to flipper-the-ripper.celox.io.
#   ./website/deploy.sh          static files only
#   ./website/deploy.sh server   also the latest-release timer + nginx vhost
set -euo pipefail
cd "$(dirname "$0")"
HOST=root@69.62.121.168
ROOT=/var/www/flipper-the-ripper.celox.io

# latest.json, changelog.md and ssi/ are written on the server by ftr-latest.py — never delete them from here.
rsync -avz --delete --exclude latest.json --exclude changelog.md --exclude ssi/ --exclude apk/ --exclude server/ --exclude deploy.sh --exclude README.md \
  --exclude .DS_Store ./ "$HOST:$ROOT/"
ssh "$HOST" "chown -R root:root $ROOT && chmod -R u=rwX,go=rX $ROOT"

if [[ "${1:-}" == server ]]; then
  if ssh "$HOST" 'pgrep -x certbot >/dev/null'; then echo "certbot is running — try again later" >&2; exit 1; fi
  scp server/ftr-latest.py "$HOST:/usr/local/sbin/ftr-latest.py"
  scp server/ftr-latest.service server/ftr-latest.timer "$HOST:/etc/systemd/system/"
  scp server/nginx/flipper-the-ripper.celox.io "$HOST:/etc/nginx/sites-available/flipper-the-ripper.celox.io"
  ssh "$HOST" 'chmod 755 /usr/local/sbin/ftr-latest.py &&
    ln -sf /etc/nginx/sites-available/flipper-the-ripper.celox.io /etc/nginx/sites-enabled/ &&
    nginx -t && systemctl reload nginx &&
    systemctl daemon-reload && systemctl enable --now ftr-latest.timer'
fi

ssh "$HOST" 'systemctl start ftr-latest.service; journalctl -u ftr-latest -n 1 --no-pager -o cat'
