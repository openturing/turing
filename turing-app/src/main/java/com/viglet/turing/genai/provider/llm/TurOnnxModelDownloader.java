/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package com.viglet.turing.genai.provider.llm;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Locale;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import lombok.extern.slf4j.Slf4j;

/**
 * Resilient acquisition of remote ONNX embedding-model artifacts (T656).
 *
 * <p>
 * The in-process ONNX runtime shared by the {@code TRANSFORMERS_LOCAL} (T622)
 * and {@code HUGGINGFACE} (T623/T629) embedding providers handed its
 * {@code https:} model/tokenizer URLs straight to Spring AI's
 * {@code TransformersEmbeddingModel}, which fetches them through
 * {@code ResourceCacheService} into an <b>ephemeral</b> tmpdir with <b>no</b>
 * retry, integrity check, or actionable error. That was fragile for the large
 * ONNX artifact: {@code resolve/main/onnx/model.onnx} (~90&nbsp;MB) is a
 * Git-LFS file that 302-redirects to a separate CDN host (e.g.
 * {@code us.aws.cdn.hf.co} / {@code cdn-lfs.huggingface.co}), so on a cold cache
 * a network blip, an egress policy that reaches {@code huggingface.co} but not
 * the CDN host, or a wiped tmpfs on every boot left RAG silently without its
 * embedding model — while the small same-host {@code tokenizer.json} kept
 * working, making the failure look mysterious.
 *
 * <p>
 * <b>Note:</b> redirect-following itself was <i>not</i> the gap —
 * {@code UrlResource} (hence {@code ResourceCacheService}) already follows the
 * same-scheme LFS 302. What was missing is the operational hardening below.
 *
 * <p>
 * This component makes acquisition a robust <b>product capability</b> rather than
 * a per-deployment workaround: given a remote ({@code http:}/{@code https:})
 * resource it downloads it here — with bounded retry/backoff, into a configurable
 * <b>persistent</b> cache directory — verifies the result (non-empty, size
 * matches {@code Content-Length}, not a stray Git-LFS pointer stub), surfaces an
 * actionable diagnostic on failure, and returns a {@code file:} URI. The
 * {@link TurTransformersEmbeddingModelBuilder} then feeds that local file to the
 * ONNX runtime, which no longer performs any remote fetch of its own.
 * {@code classpath:}/{@code file:} resources are returned unchanged — only the
 * remote case is owned here.
 *
 * <p>
 * <b>Persistence.</b> When {@code turing.genai.embedding.local.cache-dir} points
 * at a mounted volume, a warm cache survives a wiped/ephemeral environment (the
 * public demo's reset) so the ~90&nbsp;MB model is not re-downloaded on every
 * boot. Left blank it falls back to {@code store/turing-onnx-cache} — the
 * project-wide {@code store/} directory that already holds the H2 db, asset
 * storage, the Artemis queue, and logs, so the model cache is persisted next to
 * them (in Docker, under the {@code /app/store} volume) instead of a volatile OS
 * temp dir. A pre-seeded cache volume remains an optional {@code viglet/cloud}
 * belt — not the fix.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Slf4j
@Component
public class TurOnnxModelDownloader {

    /** Sub-directory (under the configured cache dir) that holds downloaded artifacts. */
    static final String CACHE_SUBDIR = "turing-onnx-cache";

    /**
     * Default base directory when {@code turing.genai.embedding.local.cache-dir}
     * is blank. {@code store} is the project-wide convention shared by the H2 db,
     * asset storage, the Artemis queue, and logs (all relative to the working
     * directory), so the ONNX cache lands next to them under {@code store/} rather
     * than a volatile OS temp dir.
     */
    static final String DEFAULT_STORE_DIR = "store";

    /**
     * A {@code .onnx} artifact below this many bytes is almost certainly not a
     * real model — most often the tiny Git-LFS <i>pointer stub</i> that is served
     * when an LFS redirect is not followed. Treated as a failed download.
     */
    private static final long MIN_ONNX_BYTES = 4096L;

    private final String cacheDirectory;
    private final int maxAttempts;
    private final Duration requestTimeout;
    private final long initialBackoffMillis;
    private final HttpClient httpClient;

    @Autowired
    public TurOnnxModelDownloader(
            @Value("${turing.genai.embedding.local.cache-dir:}") String cacheDirectory,
            @Value("${turing.genai.embedding.download.max-attempts:4}") int maxAttempts,
            @Value("${turing.genai.embedding.download.timeout-seconds:180}") long timeoutSeconds,
            @Value("${turing.genai.embedding.download.initial-backoff-millis:750}") long initialBackoffMillis) {
        this.cacheDirectory = cacheDirectory == null ? "" : cacheDirectory.trim();
        this.maxAttempts = Math.max(1, maxAttempts);
        this.requestTimeout = Duration.ofSeconds(Math.max(1, timeoutSeconds));
        this.initialBackoffMillis = Math.max(0, initialBackoffMillis);
        // NORMAL follows all redirects except an HTTPS→HTTP downgrade — enough
        // for the HuggingFace LFS 302 to the CDN host (HTTPS→HTTPS). (The stock
        // UrlResource path follows it too; the win here is retry + integrity +
        // diagnostics + a guaranteed persistent cache, not the redirect.)
        this.httpClient = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .connectTimeout(Duration.ofSeconds(30))
                .build();
    }

    /** Test/convenience constructor — production values for retry/backoff/timeout. */
    public TurOnnxModelDownloader(String cacheDirectory) {
        this(cacheDirectory, 4, 180, 750);
    }

    /**
     * The resolved persistent cache directory (the configured value, or the
     * {@code java.io.tmpdir} fallback). Exposed so the builder can also point
     * Spring AI's own cache (used for {@code classpath:} copies) at the same
     * place.
     */
    public String cacheDirectory() {
        return baseCacheDir().toString();
    }

    /**
     * Ensures {@code resourcePath} is available as a local file and returns a URI
     * the ONNX runtime can load directly. A remote ({@code http:}/{@code https:})
     * URL is downloaded (resiliently, once) into the persistent cache and a
     * {@code file:} URI is returned; a {@code classpath:}/{@code file:}/other
     * resource is returned unchanged.
     *
     * @throws IllegalStateException with an actionable message when a remote
     *                               artifact cannot be acquired after retries
     */
    public String ensureLocal(String resourcePath) {
        if (!isRemote(resourcePath)) {
            return resourcePath;
        }
        Path target = cacheFileFor(resourcePath);
        if (isUsableCacheHit(target, resourcePath)) {
            log.debug("ONNX artifact cache hit for '{}' → {}", resourcePath, target);
            return target.toUri().toString();
        }
        Path downloaded = downloadWithRetry(resourcePath, target);
        return downloaded.toUri().toString();
    }

    private static boolean isRemote(String path) {
        if (!StringUtils.hasText(path)) {
            return false;
        }
        String lower = path.toLowerCase(Locale.ROOT).trim();
        return lower.startsWith("http://") || lower.startsWith("https://");
    }

    private boolean isUsableCacheHit(Path target, String url) {
        try {
            if (!Files.isRegularFile(target)) {
                return false;
            }
            long size = Files.size(target);
            if (size <= 0) {
                return false;
            }
            // A cached file that is suspiciously small for an .onnx is treated as
            // a poisoned cache (e.g. an LFS pointer stub from a prior bad run) and
            // re-downloaded rather than loaded into a broken model.
            return !(isOnnx(url) && size < MIN_ONNX_BYTES);
        } catch (IOException e) {
            return false;
        }
    }

    private Path downloadWithRetry(String url, Path target) {
        Exception last = null;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                return download(url, target);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(
                        "Interrupted while downloading ONNX artifact '%s'.".formatted(url), e);
            } catch (java.nio.file.FileSystemException e) {
                // A cache-dir permission/filesystem problem won't fix itself by
                // retrying — fail fast with an actionable (non-network) message.
                throw new IllegalStateException(diagnostic(url, target, e), e);
            } catch (Exception e) {
                last = e;
                log.warn("ONNX artifact download attempt {}/{} for '{}' failed: {}",
                        attempt, maxAttempts, url, describe(e));
                if (attempt < maxAttempts) {
                    sleepBackoff(attempt);
                }
            }
        }
        throw new IllegalStateException(diagnostic(url, target, last), last);
    }

    /**
     * Exception class + message. The class matters: several {@code java.nio.file}
     * exceptions (e.g. {@link java.nio.file.AccessDeniedException}) use the bare
     * path as their message, which reads like a network error without the type.
     */
    private static String describe(Throwable e) {
        return e == null ? "unknown" : e.getClass().getSimpleName() + ": " + e.getMessage();
    }

    private Path download(String url, Path target) throws IOException, InterruptedException {
        Files.createDirectories(target.getParent());
        Path part = target.resolveSibling(target.getFileName() + ".part");
        Files.deleteIfExists(part);

        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(requestTimeout)
                .header("User-Agent", "viglet-turing-onnx-downloader")
                .GET()
                .build();
        HttpResponse<InputStream> response =
                httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());

        int status = response.statusCode();
        if (status != 200) {
            response.body().close();
            throw new IOException("HTTP %d for %s".formatted(status, response.uri()));
        }

        long written;
        try (InputStream in = response.body()) {
            written = Files.copy(in, part, StandardCopyOption.REPLACE_EXISTING);
        }

        verify(url, part, written, response);
        move(part, target);
        log.info("Downloaded ONNX artifact '{}' ({} bytes) → {}", url, written, target);
        return target;
    }

    /**
     * Integrity gate: reject an empty download, a {@code Content-Length}
     * mismatch (partial/truncated transfer — retryable), and a {@code .onnx}
     * that is either too small to be a model or is a Git-LFS pointer stub
     * (redirect not followed — surfaces clearly rather than corrupting the model).
     */
    private void verify(String url, Path part, long written, HttpResponse<InputStream> response)
            throws IOException {
        if (written <= 0) {
            throw new IOException("empty response body");
        }
        long declared = response.headers().firstValueAsLong("content-length").orElse(-1L);
        if (declared >= 0 && declared != written) {
            throw new IOException(
                    "size mismatch: Content-Length=%d but downloaded %d bytes".formatted(declared, written));
        }
        if (isOnnx(url)) {
            if (looksLikeLfsPointer(part)) {
                throw new IOException(
                        "server returned a Git-LFS pointer stub instead of the model binary "
                                + "(LFS redirect was not resolved)");
            }
            if (written < MIN_ONNX_BYTES) {
                throw new IOException(
                        "downloaded .onnx is only %d bytes — not a valid model".formatted(written));
            }
        }
    }

    private void move(Path part, Path target) throws IOException {
        try {
            Files.move(part, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(part, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    /** A Git-LFS pointer file is small UTF-8 text beginning {@code version https://git-lfs…}. */
    private static boolean looksLikeLfsPointer(Path part) {
        try {
            if (Files.size(part) > 1024) {
                return false;
            }
            String head = Files.readString(part, StandardCharsets.UTF_8).stripLeading();
            return head.startsWith("version https://git-lfs");
        } catch (IOException e) {
            return false;
        }
    }

    private static boolean isOnnx(String url) {
        return url.toLowerCase(Locale.ROOT).contains(".onnx");
    }

    private void sleepBackoff(int attempt) {
        long delay = initialBackoffMillis * (1L << (attempt - 1));
        if (delay <= 0) {
            return;
        }
        try {
            Thread.sleep(delay);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private Path baseCacheDir() {
        String base = StringUtils.hasText(cacheDirectory)
                ? cacheDirectory
                : DEFAULT_STORE_DIR;
        return Paths.get(base, CACHE_SUBDIR);
    }

    /**
     * A stable per-URL cache path: {@code <cacheDir>/<sha256(url)[:16]>/<filename>}.
     * The hash prefix avoids collisions between different repos exposing the same
     * {@code model.onnx}/{@code tokenizer.json} filename; the readable filename
     * keeps the cache inspectable.
     */
    private Path cacheFileFor(String url) {
        return baseCacheDir().resolve(hash(url)).resolve(fileName(url));
    }

    private static String hash(String url) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(url.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(bytes).substring(0, 16);
        } catch (Exception e) {
            // SHA-256 is always available; fall back to a filesystem-safe token.
            return Integer.toHexString(url.hashCode());
        }
    }

    private static String fileName(String url) {
        String path = URI.create(url).getPath();
        int slash = path.lastIndexOf('/');
        String name = slash >= 0 ? path.substring(slash + 1) : path;
        name = name.replaceAll("[^A-Za-z0-9._-]", "_");
        return StringUtils.hasText(name) ? name : "artifact";
    }

    private String diagnostic(String url, Path target, Exception cause) {
        // Distinguish the two real failure modes so the message doesn't send the
        // reader down the wrong path: a filesystem/permission error on the cache
        // dir (e.g. a root-owned mounted volume the app user can't write) vs a
        // network error reaching HuggingFace / its CDN.
        boolean fsError = cause instanceof java.nio.file.FileSystemException;
        String hint = fsError
                ? "The cache directory is not writable — check the ownership/permissions of '%s' "
                        .formatted(target.getParent())
                        + "(a mounted volume must be writable by the app user)."
                : "Check network egress to huggingface.co and its download CDN (*.hf.co), "
                        + "or pre-seed the cache volume; the cache dir is set via "
                        + "'turing.genai.embedding.local.cache-dir'.";
        return ("Failed to acquire ONNX embedding artifact '%s' after %d attempt(s). "
                + "Cache dir: '%s'. Last error: %s. %s")
                .formatted(url, maxAttempts, target.getParent(), describe(cause), hint);
    }
}
