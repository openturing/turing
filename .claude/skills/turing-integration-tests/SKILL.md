---
name: turing-integration-tests
description: Requirements for writing Turing backend integration tests (*IT.java) that boot the full Spring context with @SpringBootTest — extend AbstractTuringSpringIT (do NOT redeclare @SpringBootTest), why JMX must be passed via properties=, the per-JVM Artemis data directory / persistence=false requirement, and where @TestConfiguration @Primary mocks must live. Use whenever creating or debugging a *IT.java, a @SpringBootTest, or a context-startup hang.
---

# Integration test (`*IT.java`) `@SpringBootTest` requirements

Every new IT that boots the full Spring context **must** either:

1. **Extend `AbstractTuringSpringIT`** ([turing-app/src/test/java/com/viglet/turing/testutil/AbstractTuringSpringIT.java](turing-app/src/test/java/com/viglet/turing/testutil/AbstractTuringSpringIT.java)) — gives you an ephemeral H2 datasource, snapshot seeding, JMX enabled, **per-JVM Artemis data directory**, and proper cleanup. **Preferred**. The subclass **must NOT redeclare `@SpringBootTest`** — that would override (not merge with) the parent's annotation and silently drop `spring.jmx.enabled=true`:

   ```java
   // ✅ correct — inherits @SpringBootTest from AbstractTuringSpringIT
   class TurFooIT extends AbstractTuringSpringIT {
       @Test
       void something() { ... }
   }

   // ❌ wrong — own @SpringBootTest overrides parent's annotation,
   //    JMX property is lost, context fails on TurQueueBrowserService
   @SpringBootTest
   class TurFooIT extends AbstractTuringSpringIT { ... }
   ```

2. **Or, when subclassing isn't possible** (rare — e.g. needs a different base or test slice), explicitly enable JMX on the annotation:

   ```java
   @SpringBootTest(properties = "spring.jmx.enabled=true")
   class TurFooIT { ... }
   ```

   When going this route, also override `spring.artemis.embedded.data-directory` via `@DynamicPropertySource` to a unique per-JVM path (see `AbstractTuringSpringIT.overrideArtemisDataDirectoryWithEphemeralPath`), otherwise concurrent ITs and a locally-running app will collide on the same Artemis file lock and hang for several minutes on context startup.

**Why JMX must be passed via `properties = ...`**: `TurQueueBrowserService` injects `javax.management.MBeanServer` in its constructor (mandatory dep, not optional). In Spring Boot 4, the `JmxAutoConfiguration` that supplies `MBeanServer` is **not** activated in `@SpringBootTest` from `application.yaml` alone — the property has to be passed via `properties = ...` on the annotation. Without it the context fails with `No qualifying bean of type 'javax.management.MBeanServer' available` and Failsafe burns ~30s per attempt before giving up.

**Why per-JVM Artemis data dir matters**: the production default is `spring.artemis.embedded.data-directory=store/queue` — a CWD-relative path. With embedded Artemis + `persistent: true`, two JVMs sharing that path (e.g. a running app + a forked Surefire IT, or two parallel ITs) compete for `server.lock` and serialize through `FileLockNodeManager`'s sleep-retry loop. Symptom: the IT hangs on Spring context startup with no error logged, until you `Stop-Process` the other JVM. `AbstractTuringSpringIT` redirects this to a unique per-JVM temp dir, eliminating the class of bug entirely.

`AbstractTuringSpringIT` also sets
`spring.artemis.embedded.persistent=false`. Keep it that way: multiple cached
Spring contexts in the same Maven fork can otherwise share the per-JVM Artemis
directory and block for minutes on `FileLockNodeManager`.

`@TestConfiguration` classes with `@Primary` mocks must live outside the main
component scan (`com.viglet.turing.*`). Put reusable test config under
`com.viglet.testsupport.*`; otherwise a mock can leak into every
`@SpringBootTest`.

## Other testing rules

- Always check `application.yaml` defaults before writing test-specific properties — most ITs should inherit project conventions.
- Backend: JUnit 5 + Mockito with `@ExtendWith(MockitoExtension.class)`. Mockito strict stubbing: stub all methods that will be called (use `any()` for broad matching).
- Keep controllers lean to avoid bean issues in full-context ITs.
- When executing the multi-phase test plan in `docs/IMPROVEMENTS.md`, do not mark a task done for skeleton-only tests that still contain `fail()` or only `@Disabled`. Implement real assertions first.
- In PowerShell, quote Maven test filters like `"-Dtest=A,B"` to avoid comma parsing issues.
- In JUnit `@CsvSource`, quote regex values containing commas (for example `'1[.,]5B'`) and use `nullValues = "null"` when a true `null` is required.
- For write-only secret fields modeled as JPA `@Transient` (`apiKey`, `credential`), MockMvc/Jackson binding may skip values; extract secrets from raw request payload map in controller tests and runtime paths.
