package com.viglet.turing.genai.tool;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;

/**
 * Pre-warmed pool of host Python interpreters for the Code Interpreter
 * NATIVE path (T82).
 *
 * <p>A cold {@code execute_python} call pays the full interpreter boot
 * (~200&nbsp;ms on a typical box, more on Windows) on top of the script's own
 * work. For frequently-invoked tools that boot dominates wall-clock latency.
 * This pool keeps a handful of interpreters <b>already booted and blocked on
 * stdin</b>, so when a request arrives the only remaining cost is handing it
 * the per-call runner — the boot is already paid.
 *
 * <h2>Single-use workers (no state leak)</h2>
 * Each warm worker handles <b>exactly one</b> execution and then exits, just
 * like a cold spawn — the only difference is <i>when</i> the interpreter
 * started. There is no process reuse across executions, so there is no
 * cross-tenant global / {@code sys.modules} / monkey-patch leakage. After a
 * worker is consumed the pool asynchronously spawns a replacement to refill
 * back to {@link #targetSize()}.
 *
 * <h2>Scope &amp; graceful degradation</h2>
 * <ul>
 *   <li><b>NATIVE only.</b> DOCKER mode starts a container per call (container
 *       start dominates and pooling containers is a different design), so the
 *       caller never consults the pool there.</li>
 *   <li><b>Mutually exclusive with T81 native resource limits.</b> A limiter
 *       ({@code prlimit} / {@code systemd-run}) must wrap the interpreter at
 *       {@code exec} time; a worker that is already running cannot be wrapped
 *       retroactively. When limits are enabled the caller bypasses the pool
 *       and cold-spawns under the limiter prefix.</li>
 *   <li><b>Opt-in, default-off.</b> {@code turing.code-interpreter.warm-pool.enabled=false}
 *       keeps the exact legacy behavior (cold spawn per call).</li>
 *   <li><b>Never fatal.</b> {@link #acquire()} is non-blocking and returns
 *       {@code null} whenever a warm worker is unavailable (pool empty, worker
 *       died, python misconfigured, shutting down) — the caller transparently
 *       falls back to a cold spawn. A failing factory just leaves the pool
 *       under target; it is retried on the next acquire.</li>
 * </ul>
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
@Slf4j
@Component
public class TurCodeInterpreterWarmPool {

    /** Opt-in flag. {@code false} = legacy cold-spawn-per-call behavior. */
    @Value("${turing.code-interpreter.warm-pool.enabled:false}")
    private boolean enabled;

    /** How many interpreters to keep booted and ready. */
    @Value("${turing.code-interpreter.warm-pool.size:2}")
    private int size;

    /**
     * Factory that spawns one booted-and-blocked worker. Supplied by
     * {@link TurCodeInterpreterToolService} via {@link #initialize} (the
     * service owns python-executable resolution + the locked-down env). Null
     * until initialized — {@link #acquire()} returns {@code null} while null.
     */
    private volatile Callable<Process> factory;
    private volatile boolean shuttingDown = false;

    private final BlockingQueue<Process> ready = new LinkedBlockingQueue<>();
    /** Coalesces redundant refill submissions onto the single refill thread. */
    private final AtomicBoolean refillPending = new AtomicBoolean(false);

    private ExecutorService refillExecutor;

    /** True when the operator turned the pool on (independent of readiness). */
    public boolean isEnabled() {
        return enabled;
    }

    /** Effective target size — always at least one when enabled. */
    int targetSize() {
        return Math.max(1, size);
    }

    /** Number of workers currently booted and waiting (test/diagnostics seam). */
    int readyCount() {
        return ready.size();
    }

    /**
     * Wires the worker factory and pre-fills the pool. No-op when disabled.
     * Called once from the service's {@code @PostConstruct}.
     */
    public synchronized void initialize(Callable<Process> workerFactory) {
        if (!enabled) {
            log.debug("[CodeInterpreter] warm pool disabled");
            return;
        }
        if (this.factory != null) {
            return; // already initialized
        }
        this.factory = workerFactory;
        this.refillExecutor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "turing-ci-warmpool");
            t.setDaemon(true);
            return t;
        });
        log.info("[CodeInterpreter] warm pool enabled (size={})", targetSize());
        triggerRefill();
    }

    /**
     * Hands out one booted worker, or {@code null} if none is available right
     * now (caller cold-spawns). Always non-blocking. Dead workers found at the
     * head of the queue are discarded. Schedules an async refill on the way out
     * so the pool tops back up toward {@link #targetSize()}.
     */
    public Process acquire() {
        if (!enabled || factory == null || shuttingDown) {
            return null;
        }
        Process worker;
        while ((worker = ready.poll()) != null) {
            if (worker.isAlive()) {
                triggerRefill();
                return worker;
            }
            destroyQuietly(worker); // crashed while idle — drop it
        }
        triggerRefill(); // empty: refill for next time, degrade to cold now
        return null;
    }

    private void triggerRefill() {
        if (shuttingDown || refillExecutor == null) {
            return;
        }
        if (refillPending.compareAndSet(false, true)) {
            refillExecutor.execute(() -> {
                refillPending.set(false);
                refill();
            });
        }
    }

    /**
     * Tops the pool up to {@link #targetSize()}. Synchronous — runs on the
     * single refill thread (or directly in tests). A factory failure (e.g.
     * python not configured) is logged and stops this pass; the pool stays
     * under target and is retried on the next {@link #acquire()}.
     */
    void refill() {
        try {
            while (!shuttingDown && ready.size() < targetSize()) {
                Process worker = factory.call();
                if (worker == null) {
                    break;
                }
                if (shuttingDown) {
                    destroyQuietly(worker);
                    break;
                }
                ready.offer(worker);
                log.debug("[CodeInterpreter] warm worker spawned (ready={}/{})",
                        ready.size(), targetSize());
            }
        } catch (Exception e) {
            log.warn("[CodeInterpreter] warm worker spawn failed ({}); pool stays under target",
                    e.getMessage());
        }
    }

    @PreDestroy
    public void shutdown() {
        shuttingDown = true;
        if (refillExecutor != null) {
            refillExecutor.shutdownNow();
        }
        Process worker;
        while ((worker = ready.poll()) != null) {
            destroyQuietly(worker);
        }
    }

    private static void destroyQuietly(Process worker) {
        try {
            worker.destroyForcibly();
        } catch (RuntimeException e) {
            // best-effort cleanup
        }
    }
}
