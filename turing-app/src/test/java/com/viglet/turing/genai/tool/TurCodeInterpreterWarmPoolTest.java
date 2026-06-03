package com.viglet.turing.genai.tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for the T82 pre-warmed interpreter pool. The queue / refill /
 * acquire logic is exercised with mocked {@link Process} workers and a
 * synchronous {@code refill()} call (the async executor is left uninitialized
 * so the tests stay deterministic — {@code triggerRefill()} no-ops when the
 * executor is null).
 */
class TurCodeInterpreterWarmPoolTest {

    private static void setField(Object target, String name, Object value) throws Exception {
        Field f = TurCodeInterpreterWarmPool.class.getDeclaredField(name);
        f.setAccessible(true);
        f.set(target, value);
    }

    private static void refill(TurCodeInterpreterWarmPool pool) throws Exception {
        Method m = TurCodeInterpreterWarmPool.class.getDeclaredMethod("refill");
        m.setAccessible(true);
        m.invoke(pool);
    }

    private static Process liveWorker() {
        Process p = mock(Process.class);
        when(p.isAlive()).thenReturn(true);
        return p;
    }

    @Test
    void targetSizeShouldBeAtLeastOne() throws Exception {
        TurCodeInterpreterWarmPool pool = new TurCodeInterpreterWarmPool();
        setField(pool, "size", 0);
        assertThat(pool.targetSize()).isEqualTo(1);

        setField(pool, "size", 5);
        assertThat(pool.targetSize()).isEqualTo(5);
    }

    @Test
    void isEnabledShouldReflectFlag() throws Exception {
        TurCodeInterpreterWarmPool pool = new TurCodeInterpreterWarmPool();
        assertThat(pool.isEnabled()).isFalse();
        setField(pool, "enabled", true);
        assertThat(pool.isEnabled()).isTrue();
    }

    @Test
    void acquireShouldReturnNullWhenDisabled() throws Exception {
        TurCodeInterpreterWarmPool pool = new TurCodeInterpreterWarmPool();
        // disabled by default, and no factory
        assertThat(pool.acquire()).isNull();
    }

    @Test
    void acquireShouldReturnNullWhenEnabledButNoFactory() throws Exception {
        TurCodeInterpreterWarmPool pool = new TurCodeInterpreterWarmPool();
        setField(pool, "enabled", true);
        // factory still null → not yet initialized
        assertThat(pool.acquire()).isNull();
    }

    @Test
    void refillShouldFillUpToTargetSize() throws Exception {
        TurCodeInterpreterWarmPool pool = new TurCodeInterpreterWarmPool();
        setField(pool, "enabled", true);
        setField(pool, "size", 3);
        AtomicInteger spawned = new AtomicInteger();
        Callable<Process> factory = () -> {
            spawned.incrementAndGet();
            return liveWorker();
        };
        setField(pool, "factory", factory);

        refill(pool);

        assertThat(pool.readyCount()).isEqualTo(3);
        assertThat(spawned.get()).isEqualTo(3);

        // Idempotent: already at target → no new spawns.
        refill(pool);
        assertThat(spawned.get()).isEqualTo(3);
    }

    @Test
    void acquireShouldHandOutLiveWorker() throws Exception {
        TurCodeInterpreterWarmPool pool = new TurCodeInterpreterWarmPool();
        setField(pool, "enabled", true);
        setField(pool, "size", 2);
        setField(pool, "factory", (Callable<Process>) TurCodeInterpreterWarmPoolTest::liveWorker);

        refill(pool);
        assertThat(pool.readyCount()).isEqualTo(2);

        Process worker = pool.acquire();
        assertThat(worker).isNotNull();
        // triggerRefill no-ops (executor uninitialized) so the count just drops.
        assertThat(pool.readyCount()).isEqualTo(1);
    }

    @Test
    void acquireShouldSkipAndDestroyDeadWorkers() throws Exception {
        TurCodeInterpreterWarmPool pool = new TurCodeInterpreterWarmPool();
        setField(pool, "enabled", true);
        setField(pool, "size", 2);
        Process dead1 = mock(Process.class);
        Process dead2 = mock(Process.class);
        when(dead1.isAlive()).thenReturn(false);
        when(dead2.isAlive()).thenReturn(false);
        java.util.Iterator<Process> it = java.util.List.of(dead1, dead2).iterator();
        setField(pool, "factory", (Callable<Process>) () -> it.next());

        refill(pool);
        assertThat(pool.readyCount()).isEqualTo(2);

        // Both are dead → acquire drains them, destroys each, returns null.
        assertThat(pool.acquire()).isNull();
        assertThat(pool.readyCount()).isZero();
        verify(dead1).destroyForcibly();
        verify(dead2).destroyForcibly();
    }

    @Test
    void shutdownShouldDestroyReadyWorkersAndBlockAcquire() throws Exception {
        TurCodeInterpreterWarmPool pool = new TurCodeInterpreterWarmPool();
        setField(pool, "enabled", true);
        setField(pool, "size", 2);
        Process w1 = liveWorker();
        Process w2 = liveWorker();
        java.util.Iterator<Process> it = java.util.List.of(w1, w2).iterator();
        setField(pool, "factory", (Callable<Process>) () -> it.next());

        refill(pool);
        assertThat(pool.readyCount()).isEqualTo(2);

        pool.shutdown();

        verify(w1).destroyForcibly();
        verify(w2).destroyForcibly();
        assertThat(pool.readyCount()).isZero();
        assertThat(pool.acquire()).isNull(); // shuttingDown gate
    }

    @Test
    void refillShouldStopGracefullyWhenFactoryThrows() throws Exception {
        TurCodeInterpreterWarmPool pool = new TurCodeInterpreterWarmPool();
        setField(pool, "enabled", true);
        setField(pool, "size", 3);
        setField(pool, "factory", (Callable<Process>) () -> {
            throw new IllegalStateException("Python not found");
        });

        // Must not propagate — pool just stays empty.
        refill(pool);
        assertThat(pool.readyCount()).isZero();
    }
}
