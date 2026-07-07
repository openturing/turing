---
name: turing-caching
description: Turing caching, scheduling & serialization rules — Jackson 3 imports (tools.jackson.*), Keycloak admin URL derivation, Spring Boot 4 Hazelcast-backed CacheManager, ShedLock for cluster-wide-once @Scheduled jobs, and the hard rule that repositories are NEVER cached (cache immutable DTOs/read-models in service-level caches, @Cacheable is proxy-based, pair each cache with an eviction, lowerCamelCase cache names). Use whenever adding @Cacheable/@CacheEvict, a @Scheduled job, configuring caches, or touching Jackson serialization.
---

# Cache, Scheduling & Serialization Notes

- This project uses Jackson 3 in `turing-app`: imports are `tools.jackson.*`,
  not `com.fasterxml.jackson.*`.
- For Keycloak admin REST URL derivation, preserve the `issuer-uri` base path
  (for example `/kc`); do not rebuild URLs from only scheme/host/realm.
- Spring Boot 4 clustered cache requires `com.hazelcast:hazelcast-spring` for a
  Hazelcast-backed `CacheManager`; without it Boot may silently fall back to
  `ConcurrentMapCacheManager`.
- Cluster-wide-once `@Scheduled` jobs should use ShedLock via
  `@SchedulerLock`; `TurSchedulerLockConfig` provides the Hazelcast-backed
  `LockProvider`.

## Caching policy: never on repositories (Block AC / T486)

**Repositories are uncached.** `@Cacheable`/`@CacheEvict`/`@Caching` must not
appear on any repository — `TurCacheAnnotationConventionsTest` (under
`turing-app/src/test/java/com/viglet/turing/db/`) fails the build if they do.
The reason: a repository finder caches a **JPA entity**, and entities carry
lazy `@ManyToOne` proxies. A cached entity outlives its session, so any hot-path
read that dereferences a lazy association outside a transaction throws
`LazyInitializationException` (the bug that surfaced when
`hibernate.enable_lazy_load_no_trans` was turned off). T486 removed the whole
class — including the per-finder cache-name constants. T488 then retired the
now-redundant `findByIdNoCache` band-aid: admin/CRUD callers use plain
`findById`, and load-mutate-map paths run `@Transactional` (with a
`LEFT JOIN FETCH` finder — e.g. `TurSNSiteRepository.findByIdWithGenAi` — for the
to-one graph the mapper/prompt-builder touches, never `@EntityGraph`).

When you do need to cache for the search/chat hot path:

1. **Cache an immutable read-model / DTO in a service-level cache, never a JPA
   entity.** The parsed `ChatFlowGraph`, an SN site + field-config DTO, router
   decisions, static prompts, rerank scores, prices — values with no lazy
   proxies and no session affinity, safe to read anywhere. Admin/CRUD stays
   uncached and uses `@Transactional` + `@Query ... LEFT JOIN FETCH` to load the
   graph it needs (see T488).
2. **`@Cacheable` is proxy-based — never call it via `this`.** A
   `this.cacheableMethod()` from the same class bypasses the Spring proxy and
   silently no-ops the cache (and its eviction). For intra-class calls, inject
   the bean into itself with `@Lazy` and route through that reference. See
   [`TurChatFlowStaticPromptCache`](turing-app/src/main/java/com/viglet/turing/genai/flow/TurChatFlowStaticPromptCache.java)
   for the canonical pattern.
3. **Pair every service cache name with an eviction.** Evict on the matching
   admin write (e.g. the reranker-config setters evict `turRagRerankScore`).
4. **Cache names follow lowerCamelCase** — `turXxx<Purpose>` for service-level
   caches (e.g. `turChatFlowRouterDecision`, `turPersonaStaticPrompt`).
5. **Hazelcast vs in-process is a deploy-time concern.** The cache topology is
   decided by whether `com.hazelcast:hazelcast-spring` is on the classpath; the
   `@Cacheable` site stays identical either way.
