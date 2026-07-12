# Local audio transcription backend (self-hosted, OpenAI-compatible)

Turing's transcription seam (Block AS) can run **fully locally** with no cloud
STT provider. The recommended local path is the `OPENAI_COMPATIBLE` strategy
pointed at a self-hosted server that speaks the OpenAI
`/audio/transcriptions` contract — e.g. [`faster-whisper-server`], `whisper.cpp`
server, or vLLM-whisper. It reuses the exact same REST code path as the cloud
provider; only the URL changes. It scales horizontally and is GPU-capable.

> For a true air-gapped, no-sidecar install, use the in-process `LOCAL_ONNX`
> strategy instead (Block AS / T691) — at the cost of JVM footprint and
> single-node scaling.

## 1. Run the sidecar

A minimal `faster-whisper-server` sidecar (CPU; add a GPU runtime for speed):

```yaml
# docker-compose fragment — add alongside the Turing service
services:
  faster-whisper:
    image: fedirz/faster-whisper-server:latest-cpu
    # image: fedirz/faster-whisper-server:latest-cuda   # GPU
    environment:
      # Preloaded model; "small"/"medium"/"large-v3" trade accuracy for RAM/latency.
      WHISPER__MODEL: Systran/faster-whisper-small
      WHISPER__INFERENCE_DEVICE: cpu
    volumes:
      - faster-whisper-cache:/root/.cache/huggingface   # persist the model download
    ports:
      - "8000:8000"
    restart: unless-stopped

volumes:
  faster-whisper-cache:
```

The server exposes `POST /v1/audio/transcriptions` (OpenAI-compatible). No API
key is required by default.

## 2. Point Turing at it

**Option A — Global Settings UI** (`/bento/settings` → *Generative AI* →
*Transcription*): set **Backend** = `OpenAI-compatible (dedicated endpoint)`,
**Endpoint URL** = `http://faster-whisper:8000/v1`, **Model** =
`Systran/faster-whisper-small`. Leave the API key blank (no auth). Optionally
raise **Max upload (bytes)** — a local server has no 25 MiB cap; anything above
the limit is chunked automatically when `ffmpeg` is present.

**Option B — environment / properties** (headless / Viglet Cloud), which win
over the UI value so a container can pin the backend:

```properties
turing.transcription.type=OPENAI_COMPATIBLE
turing.transcription.endpoint=http://faster-whisper:8000/v1
turing.transcription.model=Systran/faster-whisper-small
# turing.transcription.api-key=            # usually none for a local server
turing.transcription.max-upload-bytes=524288000
```

## 3. Large recordings

Audio above `max-upload-bytes` is split into overlapping ≤-limit chunks
(`ffmpeg` required — use **Check ffmpeg** in the Transcription section to verify
the host), transcribed bounded-parallel, and stitched back together. Small clips
are sent directly and need no ffmpeg.

Because chunks are re-encoded to a compact 16 kHz mono MP3 (~4000 B/s), a long
recording often fits a **single** chunk even when the source file is large (e.g.
a 35-min clip re-encodes to ~8 MB, under the 25 MiB cap → one provider call). To
make a long transcription both **faster** and **show progress**, cap the chunk
*duration* so it splits into several shorter chunks that transcribe in parallel:

```properties
# Split into chunks no longer than 5 min each (0 = off; byte-limit only).
turing.transcription.max-chunk-seconds=300
# How many chunks transcribe at once (bounds backend load / cloud spend).
turing.transcription.chunk-concurrency=3
```

With this set, the async persona-from-audio job (and any chunk-aware caller)
reports live per-chunk progress (`3/7 …`); left at `0` (default), a long clip
that re-encodes under the limit stays one chunk (legacy behaviour).

## 4. Configuring the ffmpeg toolchain

The chunker (T688) shells out to `ffmpeg` (re-encode/split) and `ffprobe`
(duration probe). There is **no Global Settings UI field** for these — the
executable paths are configured only via properties / environment, so the path
stays at the trusted container level (an executable path is not something an
admin should be able to point anywhere from a web form). By default Turing calls
`ffmpeg`/`ffprobe` as found on the `PATH`; override when they live elsewhere or
you want to pin a specific build:

```properties
# Absolute paths (or bare names to resolve on PATH — the defaults)
turing.transcription.ffmpeg-path=/usr/bin/ffmpeg
turing.transcription.ffprobe-path=/usr/bin/ffprobe
# Per-invocation timeout for a single ffmpeg/ffprobe call (seconds; default 120)
turing.transcription.ffmpeg-timeout-seconds=120
```

Equivalent environment variables (Spring relaxed binding):

```bash
TURING_TRANSCRIPTION_FFMPEG_PATH=/usr/bin/ffmpeg
TURING_TRANSCRIPTION_FFPROBE_PATH=/usr/bin/ffprobe
TURING_TRANSCRIPTION_FFMPEG_TIMEOUT_SECONDS=120
```

**Installing ffmpeg** (needed only for recordings above `max-upload-bytes`):

- **Debian/Ubuntu**: `apt-get install -y ffmpeg` (provides both `ffmpeg` and `ffprobe`)
- **Alpine** (common in containers): `apk add --no-cache ffmpeg`
- **macOS**: `brew install ffmpeg`
- **Windows**: `choco install ffmpeg` (or `winget install Gyan.FFmpeg`), then
  ensure the `bin` directory is on `PATH` — otherwise set the absolute
  `ffmpeg-path`/`ffprobe-path` above (e.g. `C:\ffmpeg\bin\ffmpeg.exe`).

After adjusting the paths, restart Turing and use **Check ffmpeg** in *Global
Settings → Generative AI → Transcription* to confirm the toolchain is reachable
(it reports the resolved ffmpeg version banner, or the error if not runnable).

> **In most cases you set nothing.** If `ffmpeg`/`ffprobe` are on the system
> `PATH` — the normal outcome of `apt install ffmpeg`, `brew install ffmpeg`, or
> `winget install "FFmpeg (Essentials Build)"` — the defaults already find them;
> `ffmpeg-path`/`ffprobe-path` are only for non-PATH installs.

> **Troubleshooting — "ffmpeg works in my terminal but Check ffmpeg says
> unavailable"**: a running process keeps the `PATH` it started with. If you
> installed ffmpeg *after* Turing (or the IDE that launched it) was already
> running, the JVM still has the old `PATH` and can't see the new binary — this
> is especially common on Windows after `winget`/`choco`. **Restart Turing (and
> the IDE if it started the backend)**, then run Check ffmpeg again. No path
> configuration is needed once the process is restarted with the updated `PATH`.

[`faster-whisper-server`]: https://github.com/fedirz/faster-whisper-server
