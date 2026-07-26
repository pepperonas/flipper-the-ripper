# Flipper the Ripper — extraction backend

A small FastAPI service that runs the full yt-dlp toolchain (yt-dlp + ffmpeg + a `deno` JS runtime for
YouTube's n-sig challenge + `curl_cffi` impersonation for TikTok) on a server. The Android app is a
thin client: it POSTs a URL, the server downloads it, then streams the finished file back — which
sidesteps the platform anti-bot measures a stock-Android bundle cannot satisfy.

## API (all endpoints require the `X-API-Key` header)

| Method | Path | Purpose |
|--------|------|---------|
| GET | `/health` | Liveness. |
| POST | `/api/resolve` | `{url}` → `{id,title,uploader,thumbnail,durationSeconds}`. |
| POST | `/api/jobs` | `{url,mode}` → `{jobId}`. Starts a background download. |
| GET | `/api/jobs/{id}` | `{status,progress,fileName,size,errorKind,error}`. |
| GET | `/api/jobs/{id}/file` | Streams the finished file (`Content-Disposition`). |
| DELETE | `/api/jobs/{id}` | Delete the job + its file. |

`mode` is `video` (H.264+m4a → mp4) or `audio` (m4a).

## Deploy (VPS)

```bash
# from repo root
rsync -avz --exclude '.env' --exclude data backend/ root@<vps>:/opt/flipper-backend/
ssh root@<vps> '
  cd /opt/flipper-backend
  python3 -m venv venv && venv/bin/pip install -U pip -r requirements.txt
  # deno for the YouTube n-sig JS runtime
  curl -fsSL https://deno.land/install.sh | DENO_INSTALL=/opt/flipper-backend/.deno sh
  cp -n .env.example .env && chmod 600 .env   # then edit FLIPPER_API_KEY
  cp flipper-backend.service /etc/systemd/system/
  systemctl daemon-reload && systemctl enable --now flipper-backend
'
# nginx + TLS
ssh root@<vps> '
  cp /opt/flipper-backend/nginx-flipper.conf /etc/nginx/sites-available/flipper.celox.io
  ln -sf /etc/nginx/sites-available/flipper.celox.io /etc/nginx/sites-enabled/
  certbot certonly --nginx -d flipper.celox.io --non-interactive --agree-tos -m admin@celox.io
  nginx -t && systemctl reload nginx
'
```

Keep yt-dlp current with a nightly timer (`venv/bin/pip install -U yt-dlp` or `yt-dlp -U`).

## Security

- `FLIPPER_API_KEY` gates every endpoint; the service binds to loopback and nginx terminates TLS.
- Only Instagram/YouTube/TikTok/Facebook hosts are accepted (not an open proxy).
- Files live under `data/<jobId>/` and are pruned after `FLIPPER_JOB_TTL` (default 2 h).
