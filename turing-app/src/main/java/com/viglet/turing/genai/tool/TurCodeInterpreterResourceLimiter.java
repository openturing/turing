package com.viglet.turing.genai.tool;

import java.util.Locale;

/**
 * How the NATIVE Code Interpreter subprocess is wrapped to enforce
 * per-execution CPU / RAM caps (T81).
 *
 * <p>The DOCKER mode (T80) already gets hard resource limits from the
 * container runtime ({@code --memory} / {@code --cpus} / {@code --pids-limit}).
 * The NATIVE path runs Python as a plain host subprocess, so without one of
 * these wrappers a single runaway script (e.g.
 * {@code [x*x for x in range(10**9)]}) can exhaust host RAM and trash the JVM
 * well before the 30s wall-clock timeout has a chance to fire.
 *
 * <ul>
 *   <li>{@link #PRLIMIT} — wrap with {@code prlimit(1)} (util-linux). Uses
 *       POSIX {@code setrlimit} ({@code RLIMIT_AS} for address space,
 *       {@code RLIMIT_CPU} for CPU seconds). No privileges, no systemd, and
 *       it {@code exec}s into Python so the limited process keeps the same
 *       PID (a timeout {@code destroyForcibly} cleanly kills it). The most
 *       portable backend — preferred by {@link #AUTO}.</li>
 *   <li>{@link #SYSTEMD_RUN} — wrap with {@code systemd-run --scope}. Uses a
 *       transient <b>cgroup</b> ({@code MemoryMax} / {@code MemorySwapMax} /
 *       {@code CPUQuota}); memory is RSS-accounted by the kernel and a
 *       memory-bomb is OOM-killed inside the cgroup. Requires systemd with
 *       user cgroup delegation. {@code cpu-seconds} does not map to a cgroup
 *       primitive, so CPU is bounded by {@code CPUQuota} + the wall-clock
 *       timeout instead.</li>
 *   <li>{@link #NONE} — no wrapper (legacy behavior; the wall-clock timeout is
 *       the only guard). Equivalent to leaving limits disabled.</li>
 *   <li>{@link #AUTO} — pick {@link #PRLIMIT} when its binary is present,
 *       otherwise {@link #SYSTEMD_RUN}, otherwise {@link #NONE} (logged).
 *       The default.</li>
 * </ul>
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
public enum TurCodeInterpreterResourceLimiter {
    AUTO,
    PRLIMIT,
    SYSTEMD_RUN,
    NONE;

    /** Default when the setting is missing or unparseable. */
    public static final TurCodeInterpreterResourceLimiter DEFAULT = AUTO;

    /**
     * Lenient parse: case-insensitive, accepts the {@code systemd-run}
     * spelling (hyphen) as an alias for {@link #SYSTEMD_RUN}, and maps
     * null / blank / unknown to {@link #DEFAULT} so a typo in config never
     * breaks the sandbox (it just auto-selects a limiter).
     */
    public static TurCodeInterpreterResourceLimiter fromString(String value) {
        if (value == null || value.isBlank()) {
            return DEFAULT;
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT).replace('-', '_');
        try {
            return valueOf(normalized);
        } catch (IllegalArgumentException e) {
            return DEFAULT;
        }
    }
}
