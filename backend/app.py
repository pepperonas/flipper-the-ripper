"""
Flipper the Ripper — extraction backend.

Runs the full yt-dlp toolchain (yt-dlp + ffmpeg + a JS runtime for YouTube's n-sig challenge +
curl_cffi impersonation for TikTok) on a server, so the Android app can download from platforms that
gate the media fetch behind anti-bot measures a stock-Android bundle cannot satisfy.

Flow: the app POSTs a URL, the server downloads it with yt-dlp, then streams the finished file back.
All endpoints require the `X-API-Key` header. Bound to loopback; nginx terminates TLS in front.
"""
from __future__ import annotations

import asyncio
import ipaddress
import json
import os
import re
import shutil
import socket
import time
import uuid
from dataclasses import dataclass, field
from pathlib import Path
from typing import Optional
from urllib.parse import urlparse

from fastapi import Depends, FastAPI, Header, HTTPException
from fastapi.responses import FileResponse, JSONResponse
from pydantic import BaseModel

# --- Configuration (env) ---
API_KEY = os.environ.get("FLIPPER_API_KEY", "")
DATA_DIR = Path(os.environ.get("FLIPPER_DATA_DIR", "/opt/flipper-backend/data"))
YTDLP = os.environ.get("FLIPPER_YTDLP", "yt-dlp")
JOB_TTL_SECONDS = int(os.environ.get("FLIPPER_JOB_TTL", "7200"))  # 2h
MAX_CONCURRENT = int(os.environ.get("FLIPPER_MAX_CONCURRENT", "3"))
# Impersonation target for sites that fingerprint TLS (TikTok). Empty disables it.
IMPERSONATE = os.environ.get("FLIPPER_IMPERSONATE", "chrome")

DATA_DIR.mkdir(parents=True, exist_ok=True)

SUPPORTED_HOSTS = ("youtube.com", "youtu.be", "instagram.com", "tiktok.com", "facebook.com", "fb.watch")
YOUTUBE_HOSTS = ("youtube.com", "youtu.be")
YOUTUBE_PLAYER_CLIENTS = "android_vr,web_safari,web_embedded"

app = FastAPI(title="Flipper Backend", docs_url=None, redoc_url=None, openapi_url=None)
_semaphore = asyncio.Semaphore(MAX_CONCURRENT)


# --- Auth ---
def require_key(x_api_key: str = Header(default="")) -> None:
    if not API_KEY or x_api_key != API_KEY:
        raise HTTPException(status_code=401, detail="unauthorized")


# --- Job state (in-memory) ---
@dataclass
class Job:
    id: str
    url: str
    mode: str
    status: str = "queued"  # queued | running | completed | failed
    progress: float = 0.0
    error: Optional[str] = None
    error_kind: Optional[str] = None
    file_path: Optional[str] = None
    file_name: Optional[str] = None
    size: int = 0
    created_at: float = field(default_factory=time.time)


JOBS: dict[str, Job] = {}


class ResolveBody(BaseModel):
    url: str


class JobBody(BaseModel):
    url: str
    mode: str = "video"  # video | audio


def _is_supported(url: str) -> bool:
    """
    True only when the URL's *hostname* (not any substring) is an allowlisted platform host, and it
    does not resolve to a private/loopback/link-local address. Substring matching would let
    `https://youtube.com.evil.tld/` or `https://evil.tld/?youtube.com` through and turn yt-dlp's
    generic extractor into an SSRF primitive, so we parse the host and match exactly / by subdomain,
    then reject non-public resolutions (defense-in-depth against DNS rebinding).
    """
    try:
        parsed = urlparse(url)
    except ValueError:
        return False
    if parsed.scheme not in ("http", "https") or not parsed.hostname:
        return False
    host = parsed.hostname.rstrip(".").lower()
    if not any(host == h or host.endswith("." + h) for h in SUPPORTED_HOSTS):
        return False
    try:
        for _, _, _, _, sockaddr in socket.getaddrinfo(host, None):
            ip = ipaddress.ip_address(sockaddr[0])
            if ip.is_private or ip.is_loopback or ip.is_link_local or ip.is_reserved or ip.is_multicast:
                return False
    except (socket.gaierror, ValueError):
        return False
    return True


def _is_youtube(url: str) -> bool:
    """
    Hostname-based YouTube check. A substring test over the whole URL would also match unrelated
    links that merely mention youtube.com (e.g. in a query string), applying the wrong extractor args.
    """
    try:
        host = (urlparse(url).hostname or "").rstrip(".").lower()
    except ValueError:
        return False
    return any(host == h or host.endswith("." + h) for h in YOUTUBE_HOSTS)


def _base_args(url: str, mode: str, out_template: str) -> list[str]:
    args = [YTDLP, "--no-playlist", "--no-mtime", "--no-warnings", "--newline"]
    if IMPERSONATE:
        args += ["--impersonate", IMPERSONATE]
    if mode == "audio":
        args += ["-x", "--audio-format", "m4a", "--audio-quality", "0"]
    else:
        args += ["-S", "vcodec:h264,res,acodec:m4a", "--merge-output-format", "mp4"]
    # Prefer YouTube player clients that need no GVS PO token and serve no DRM. `tv` is token-free but
    # DRM-protected without cookies, and ios/android/mweb/tv_simply all require a token — their formats
    # get skipped and the download fails. Mirrors the app's YtDlpArgsBuilder.YOUTUBE_PLAYER_CLIENTS.
    # Source: https://github.com/yt-dlp/yt-dlp/wiki/PO-Token-Guide
    if _is_youtube(url):
        args += ["--extractor-args", f"youtube:player_client={YOUTUBE_PLAYER_CLIENTS}"]
    args += ["--progress-template", "flip:%(progress._percent_str)s"]
    args += ["-o", out_template, "--", url]
    return args


# --- Error classification (mirrors the app's taxonomy) ---
_ERR_RULES = [
    ("LoginRequired", ("sign in to confirm", "login required", "requires authentication", "private and", "log in")),
    ("PrivateVideo", ("this video is private", "private video", "this post is private")),
    ("RegionBlocked", ("not available in your country", "not available in your region", "geo restrict")),
    ("Unavailable", ("video unavailable", "has been removed", "no longer available", "content isn't available")),
    ("RateLimitedOrStale", ("requested format is not available", "nsig", "rate-limit", "too many requests")),
    ("Network", ("unable to download webpage", "urlopen error", "timed out", "connection")),
]


def _classify(stderr: str) -> tuple[str, str]:
    s = stderr.lower()
    for kind, needles in _ERR_RULES:
        if any(n in s for n in needles):
            return kind, _last_line(stderr)
    return "Unknown", _last_line(stderr)


def _last_line(text: str) -> str:
    lines = [ln.strip() for ln in text.splitlines() if ln.strip()]
    return lines[-1] if lines else "download failed"


async def _run_download(job: Job) -> None:
    job.status = "running"
    work = DATA_DIR / job.id
    work.mkdir(parents=True, exist_ok=True)
    template = str(work / "%(title).100B [%(id)s].%(ext)s")
    args = _base_args(job.url, job.mode, template)
    stderr_buf: list[str] = []
    pct_re = re.compile(r"flip:\s*([0-9.]+)%")

    async with _semaphore:
        proc = await asyncio.create_subprocess_exec(
            *args, stdout=asyncio.subprocess.PIPE, stderr=asyncio.subprocess.PIPE
        )

        async def read_stdout() -> None:
            assert proc.stdout is not None
            async for raw in proc.stdout:
                m = pct_re.search(raw.decode(errors="ignore"))
                if m:
                    try:
                        job.progress = float(m.group(1))
                    except ValueError:
                        pass

        async def read_stderr() -> None:
            assert proc.stderr is not None
            async for raw in proc.stderr:
                stderr_buf.append(raw.decode(errors="ignore"))

        await asyncio.gather(read_stdout(), read_stderr())
        code = await proc.wait()

    produced = next((p for p in work.iterdir() if p.is_file() and p.stat().st_size > 0), None)
    if code == 0 and produced is not None:
        job.status = "completed"
        job.progress = 100.0
        job.file_path = str(produced)
        job.file_name = produced.name
        job.size = produced.stat().st_size
    else:
        kind, msg = _classify("".join(stderr_buf))
        job.status = "failed"
        job.error_kind = kind
        job.error = msg


@app.get("/health")
async def health() -> dict:
    return {"ok": True, "jobs": len(JOBS)}


@app.post("/api/resolve", dependencies=[Depends(require_key)])
async def resolve(body: ResolveBody) -> JSONResponse:
    if not _is_supported(body.url):
        raise HTTPException(status_code=400, detail="unsupported url")
    args = [YTDLP, "-J", "--no-warnings", "--no-playlist"]
    if IMPERSONATE:
        args += ["--impersonate", IMPERSONATE]
    # Resolve with the same player clients the download will use, otherwise metadata can come back from
    # a client whose formats the download step then refuses to fetch.
    if _is_youtube(body.url):
        args += ["--extractor-args", f"youtube:player_client={YOUTUBE_PLAYER_CLIENTS}"]
    args += ["--", body.url]
    proc = await asyncio.create_subprocess_exec(
        *args, stdout=asyncio.subprocess.PIPE, stderr=asyncio.subprocess.PIPE
    )
    out, err = await proc.communicate()
    if proc.returncode != 0:
        kind, msg = _classify(err.decode(errors="ignore"))
        return JSONResponse(status_code=422, content={"errorKind": kind, "error": msg})
    info = json.loads(out.decode(errors="ignore"))
    return JSONResponse(
        {
            "id": info.get("id"),
            "title": info.get("title") or "Untitled",
            "uploader": info.get("uploader"),
            "thumbnail": info.get("thumbnail"),
            "durationSeconds": int(info["duration"]) if info.get("duration") else None,
        }
    )


@app.post("/api/jobs", dependencies=[Depends(require_key)])
async def create_job(body: JobBody) -> dict:
    if not _is_supported(body.url):
        raise HTTPException(status_code=400, detail="unsupported url")
    _cleanup_expired()
    job = Job(id=uuid.uuid4().hex, url=body.url, mode=("audio" if body.mode == "audio" else "video"))
    JOBS[job.id] = job
    asyncio.create_task(_run_download(job))
    return {"jobId": job.id}


@app.get("/api/jobs/{job_id}", dependencies=[Depends(require_key)])
async def job_status(job_id: str) -> dict:
    job = JOBS.get(job_id)
    if job is None:
        raise HTTPException(status_code=404, detail="unknown job")
    return {
        "status": job.status,
        "progress": job.progress,
        "fileName": job.file_name,
        "size": job.size,
        "errorKind": job.error_kind,
        "error": job.error,
    }


@app.get("/api/jobs/{job_id}/file", dependencies=[Depends(require_key)])
async def job_file(job_id: str) -> FileResponse:
    job = JOBS.get(job_id)
    if job is None or job.status != "completed" or not job.file_path:
        raise HTTPException(status_code=404, detail="file not ready")
    media_type = "audio/mp4" if job.mode == "audio" else "video/mp4"
    return FileResponse(job.file_path, media_type=media_type, filename=job.file_name)


@app.delete("/api/jobs/{job_id}", dependencies=[Depends(require_key)])
async def delete_job(job_id: str) -> dict:
    _delete_job(job_id)
    return {"ok": True}


def _delete_job(job_id: str) -> None:
    JOBS.pop(job_id, None)
    shutil.rmtree(DATA_DIR / job_id, ignore_errors=True)


def _cleanup_expired() -> None:
    now = time.time()
    for jid, job in list(JOBS.items()):
        if now - job.created_at > JOB_TTL_SECONDS:
            _delete_job(jid)
