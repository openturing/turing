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

[`faster-whisper-server`]: https://github.com/fedirz/faster-whisper-server
