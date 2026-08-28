# Search Engine Plugin System

## Overview

The Turing Search Engine Plugin System allows you to switch between different search engine implementations (Solr, Elasticsearch, etc.) through configuration. This provides flexibility to choose the best search engine for your use case without changing application code.

## Architecture

The plugin system consists of:

1. **TurSearchEnginePlugin Interface** - Defines the contract for search engine implementations
2. **Plugin Implementations** - Concrete implementations for different search engines:
   - `TurSolrSearchEnginePlugin` - Apache Solr implementation (fully functional)
   - `TurElasticsearchSearchEnginePlugin` - Elasticsearch stub (for future implementation)
3. **TurSearchEnginePluginFactory** - Factory that manages and provides plugin instances based on configuration

## Configuration

Configure the search engine type in `application.yaml`:

```yaml
turing:
  search:
    engine:
      type: solr  # Options: solr, elasticsearch
```

### Default Configuration

If not specified, the system defaults to `solr`.

## Using Different Search Engines

### Apache Solr (Default)

```yaml
turing:
  search:
    engine:
      type: solr
  solr:
    timeout: 30000
    cloud: false
    commit:
      within: 10000
      enabled: false
```

### Elasticsearch

```yaml
turing:
  search:
    engine:
      type: elasticsearch
  elasticsearch:
    timeout: 30000
```

The Elasticsearch plugin is now implemented and functional. It supports:
- Basic search queries (match all, query string)
- Pagination
- Sorting
- Result mapping to TurSEResults format

**Configuration**: Elasticsearch instances are configured through the Turing SE Instance settings in the database, similar to Solr. The plugin connects to Elasticsearch using the host and port configured for each site locale.

## Plugin Interface

All search engine plugins must implement the `TurSearchEnginePlugin` interface:

```java
public interface TurSearchEnginePlugin {
    Optional<TurSEResults> retrieveSearchResults(TurSNSiteSearchContext context);
    Optional<TurSEResults> retrieveFacetResults(TurSNSiteSearchContext context, String facetName);
    String getPluginType();
}
```

## Creating a New Plugin

To add support for a new search engine:

1. Create a new package under `com.viglet.turing.plugins.se` (e.g., `mongodb`)

2. Implement the `TurSearchEnginePlugin` interface:

```java
@Slf4j
@Component
public class TurOpenSearchSearchEnginePlugin implements TurSearchEnginePlugin {
    
    @Override
    public Optional<TurSEResults> retrieveSearchResults(TurSNSiteSearchContext context) {
        // Implement search logic for MongoDB
    }

    @Override
    public Optional<TurSEResults> retrieveFacetResults(TurSNSiteSearchContext context, String facetName) {
        // Implement facet retrieval logic for MongoDB
    }

    @Override
    public String getPluginType() {
        return "mongodb";
    }
}
```

3. Register your plugin by annotating it with `@Component` (Spring will auto-discover it)

4. Update configuration to use your new plugin:

```yaml
turing:
  search:
    engine:
      type: mongodb
```

## Plugin Factory

The `TurSearchEnginePluginFactory` automatically discovers and registers all `TurSearchEnginePlugin` implementations via Spring's dependency injection.

### Usage in Code

```java
@Autowired
private TurSearchEnginePluginFactory pluginFactory;

public void performSearch() {
    // Get the configured default plugin
    TurSearchEnginePlugin plugin = pluginFactory.getDefaultPlugin();
    
    // Or get a specific plugin
    TurSearchEnginePlugin solrPlugin = pluginFactory.getPlugin("solr");
}
```

## Benefits

1. **Flexibility** - Switch search engines without code changes
2. **Extensibility** - Easy to add new search engine implementations
3. **Maintainability** - Clear separation of concerns between search engines
4. **Testability** - Mock plugins for testing
5. **Configuration-driven** - Control search engine selection through configuration

## Current Status

- ✅ **Solr Plugin** - Fully implemented and functional
- ✅ **Elasticsearch Plugin** - Implemented with basic functionality (search, pagination, sorting)
- ✅ **Content AI Plugin** - Read-only federation to an Adobe index (see below)

### Adobe AEM Content AI (`contentai`) — a federated, read-only engine

`TurContentAiSearchEnginePlugin` is the one plugin here that does **not** own its
index. A site whose SE vendor is `CONTENTAI` queries Adobe's own vector index
through the Content AI `POST /content-sources/search` API, so a customer already
paying the Content AI SKU stops embedding the same published AEM content a second
time. Turing sells the layer above it — multi-turn chat, agents, skills, facets,
its own analytics, and every non-AEM source Content AI does not reach.

Because Turing does not own the index, the plugin is read-only **by design**:
`createIndex`, `indexDocument`, `deIndex`, `addOrUpdateField` and the rest throw
`UnsupportedOperationException` with a sentence saying why. A no-op would be
worse than a failure — it would let the indexer report a successful publish into
a store that never received the document.

What the mapping looks like:

| Turing | Content AI |
| ------ | ---------- |
| `q` | `composite` query over `OR` of a `vector` arm and a `fulltext` arm |
| `fq=field:value` | `FilterGroup`: values of one field union (`InFilter`) only where the site declares the facet multi-select, fields intersect (`AND`) |
| facet field | `termBuckets` aggregate whose `id` is the field name |
| `sort=field asc` | `queryOptions.sort[].orderBy` (passed through verbatim; `relevance` sends none, since Adobe already sorts by score) |
| `rows` | `pagination.limit`, **clamped to 50** — the API's own maximum |
| `p` (page) | an opaque cursor walked one request per page, bounded by `max-page-walk` |
| `TurSEResult.passages` | `chunks[]` (`chunkData.text` + score), sorted best-first, when `include-chunks` is on |

#### Passages: the one thing only a federated engine can tell you (T1044)

Every other plugin returns whole documents, because Turing chunked the content
itself and a caller wanting a passage can re-chunk the body. Content AI cannot work
that way: Adobe embedded the content with its own chunker, so the passage it names
is the only sub-document grounding that exists.

`turing.content-ai.include-chunks: true` asks for them, and they arrive as
`TurSEResult.passages` — a list of `TurSEPassage(id, score, text)` kept *beside* the
field map, never merged into it, for the same reason `rankingExplanation` is (a
passage is not a field the source declared). `supportsPassages()` on the plugin
interface says whether asking is worth it; it defaults to `false`.

A passage is **not** a highlight. Turing's Solr path merges a highlight back over the
field's own value, which is why highlighting needs no type of its own. A passage is a
separately-embedded fragment with its own score, and the document's fields say nothing
about which fragment matched.

The consumer is `TurCustomToolSearchHelper.toSnHitList`: the best passage becomes the
hit's `content`, which is the key the ANN surface already fills with chunk text. So a
Groovy tool reading `hit.content` gets a passage from `sn()` and from `ann()` alike,
instead of one meaning on one surface and nothing on the other.

Two properties of Adobe's API drive the awkward parts and are worth knowing before
reading the code: pagination has **no offset**, only a cursor, so page N costs N
round trips; and the endpoint is versioned *and dated*
(`/adobe/experimental/aemcontentai-expires-20261231/contentAI`), so the SE
instance's endpoint URL is used verbatim rather than rebuilt from parts, and a
`Sunset` response header is logged at WARN the moment it appears.

Configuration is `turing.content-ai.*` in `application.yaml` (credential, source
mapping, hybrid on/off, boosts, facet size, page-walk ceiling). The **endpoint**
is not there — it comes from the SE instance, so a per-tenant BYO-infra bucket
keeps working. The plugin is inert until a credential is set: with none, it
registers, reports `status: DOWN` from `getSystemInfo` and refuses to call Adobe
rather than sending an unauthenticated request that returns a 401 reading like an
outage.

#### The configuration cannot be checked against the index, so ask (T1045)

Adobe answers a query naming a source it does not have with `totalResults: 0` — the
same answer as a source that exists and matched nothing. So a typo in
`turing.content-ai.sources.<site>` was indistinguishable from an empty index, and
three sibling settings fail the same silent way: a `source-type` that does not match
the source resolves against the wrong index, a `vector-space`/`lexical-space` the
IndexConfig does not declare makes Adobe fall back to its default, and a facet field
the source will not filter on returns an empty bucket list.

`TurContentAiSourceCatalog` reads `GET /content-sources` and checks all four. Note it
is the **list** endpoint, not a per-name lookup: the spec states that
`GET /content-sources/acquisition/{name}` addresses one source only when it *is* an
acquisition source, and that `AEM_AUTHOR`, `AEM_PUBLISH` and `CUSTOM` sources are
"accessible through this list endpoint only" — so a per-name lookup would 404 on three
of the four types, reading exactly like the misconfiguration it was meant to diagnose.

Where the read happens, and what it costs:

| Trigger | Behaviour |
| ------- | --------- |
| A search returns **zero results** | Diagnose once per endpoint+source and log WARN naming the available sources. A misconfiguration is permanent until somebody changes it, so repeating would bury it. A search that finds something never pays. |
| `indexExists` | Resolves the name against the listing. An **unreadable** listing reports the source *present* — "Adobe could not be reached" is not "no such source". |
| `listIndexes` | The real source names, with `numDocs = -1`; a `0` would read as "this source is empty". |
| `getSystemInfo` | The tenant's sources beside the configured mapping, each marked `(NOT FOUND)` when it does not resolve. |

The listing is cached per endpoint for `source-cache-seconds` (default 300) in a plain
map rather than a Spring cache: the value is a small immutable snapshot with no
eviction event to hook, and a cache wants a paired eviction to be worth declaring. A
transport failure is **not** memoized — it says nothing about the source, so the next
empty search retries.

One subtlety in reading the listing: a field is filterable only when
`filter.enabled` is `true`. Adobe's own example carries a `description` field with
`filter: {enabled: false}`, so presence in a lexical space's `fields` array is not the
answer.

#### The DSL executor, and the vectorless copilot (T1047)

`TurDslContentAiExecutor` (in `sn/dsl/`, not here) is the fourth
`TurDslSearchEngineExecutor`. It exists so the **vectorless copilot** reaches a
federated site: that surface needs no embeddings — prose becomes a grounded DSL query
over the declared field schema, the engine runs it, the LLM answers from the cited
rows — and it was the one answer surface a Content AI site could not use, because
everything the copilot does goes through the DSL layer.

It translates what the copilot generates (`match_all`, `match`, `term`, `terms`,
`range`, `bool`, plus `terms` aggregations) and **refuses the rest by name**. Two
structural mismatches drive the translation:

- **Scoring and filtering are separate query types.** An Elasticsearch `bool` mixes
  them; Content AI has a non-scoring `filter` query and scoring `fulltext`/`vector`
  queries joined by `composite`. So a `bool` is split — text clauses become the
  composite's arms, the rest becomes one `FilterGroup` hung off them as a pre-filter.
  A `bool` of pure filters collapses to a single `filter` query.
- **There is no offset.** A non-zero `from` is refused rather than answered with page
  one, which the caller could not detect.

`minimum_should_match > 1` and a negated text match are refused too: a `FilterGroup`
has no threshold, and there is no filter form of a text query. Approximating either
changes the result set.

**`match_all` is `NOT exists("__turing_match_all__")`.** The obvious shape — an empty
`FilterGroup` — is invalid: `filters` carries `minItems: 1`. T1031 shipped that empty
group, and nothing caught it because its only callers were `getDocumentTotal` and a
blank query, and the test stub accepts any body. Negating a field no source can declare
is one filter, legal, and universally true.

Wire coverage is `TurContentAiSearchEnginePluginTest`, which stands the Content AI
endpoint in miniature and asserts the request bodies against Adobe's published
OpenAPI examples — a mocked client would prove nothing about the translation,
which is the whole of the feature.

#### Every request body is checked against Adobe's schema (T1048)

A stub that replies `200` to anything makes a test catch only what its author thought
to assert, and that is not hypothetical: T1031's `match_all` was an empty
`FilterGroup`, which `minItems: 1` forbids, and it survived T1044, T1045 and T1046
because the test asserting it asserted the *invalid* shape by name.

So both stubs now call `TurContentAiSpecValidator.assertValid(...)` on **every**
recorded body. The spec is vendored and pinned at
`src/test/resources/contentai/openapi.yaml`; refreshing it is a deliberate act, since
the API is experimental and dated and a diff against a newer bundle is a thing to
read. No new dependency — `com.networknt:json-schema-validator` was already on the
classpath.

One thing makes it work, and it was measured rather than assumed. Adobe's schemas use
OpenAPI `discriminator` + `allOf` inheritance: `FilterComponent` declares only `type`
plus a `mapping`, and each concrete filter references it. There is no `oneOf` for the
discriminator to select from, so a validator checks `type` is a string and descends no
further — **before the transform, the empty group produced zero violations**. The
validator therefore rewrites each discriminated parent into a `oneOf` over its mapped
children, derived from the spec's own `discriminator.mapping` and never from a
hand-typed list; each child absorbs the parent's constraints in place of the
back-reference (breaking the cycle) and gains an `enum` pinning its discriminator
value. The empty group then produces 114 violations.

It found a bug on its first run: `DateFilter.value` is `format: date-time`, so T1047's
pass-through of an Elasticsearch-style `"gte": "2026-01-01"` would have been refused.
Date-only bounds are now widened to midnight UTC.

#### The spec's array bounds are enforced, and which way (T1049)

The check above only ever sees bodies a test happens to build, so the bounds still had
to be enforced where the body is built. Four are, and the split between refusing and
clamping is the point:

| Bound | Where | What happens |
| ----- | ----- | ------------ |
| `pagination.limit` ≤ 50 | both paths | clamped |
| `FilterGroup.filters` ≤ 25 | DSL executor | refused |
| `composite.queries` ≤ 10 | DSL executor | refused |
| `aggregates` ≤ 50 | DSL executor | refused |
| `aggregates` ≤ 50 | plugin | clamped (surplus facet fields dropped) |
| `termBuckets.size` ≤ 2000 | both paths | clamped |

A count of things the *query asked for* is refused: dropping the eleventh text clause
or the fifty-first named aggregation returns a plausible answer to a different
question, and the caller cannot tell. A count of things the *site is configured with*
is clamped, because a site declaring 51 facet fields should lose one facet rather than
every search it runs — the fields arrive in facet-position order, so what survives is
what the site put first. A `size` is clamped either way: it is a ceiling on what comes
back, not a request for something specific.

A fifth bound is not in the table because no schema states it (T1051). The
`aggregates` description says in prose that the **sum** of every term bucket `size`
must stay below 10,000, and that a request breaching it fails — so the T1048 check can
never reach it, JSON Schema having no keyword for a sum across siblings. It is
reachable precisely because `size` now clamps to 2000: six aggregations at that
ceiling is a body every schema rule accepts and 12,000 buckets against this one. On
the plugin path `fitBucketBudget` scales every facet down to fit (`facet-size` 500
across 50 fields becomes 200 each); on the DSL path it is refused with the total
named. Both follow the same split as the rest — and the trade is worth restating,
because Adobe fails the **request**, not the aggregate: a site that crossed the line
stops returning results at all.

#### The reply is checked too, and the bundle contradicts itself once (T1050)

A stub whose *response* is whatever its author typed proves the mapper agrees with
itself. That failure is quieter than a rejected request — a field read under the wrong
name is zero hits, an empty facet or a cursor that never advances, with no exception
and no log line — so both stubs now validate each canned response against
`ContentSourceSearchResponse` before sending it.

It found two fixtures short of what Adobe sends (a `termBuckets` aggregate with no
`field`, which `AggregatesResultData` requires) and one defect in the bundle itself:
**`TermBucketResultData` declares `required: [values]` while the only bucket property
it defines is `buckets`**, and Adobe publishes no example aggregate response to settle
it. `TurContentAiResponseMapper` already read either key for exactly that reason.

So the validator makes that requirement "one of the two" rather than dropping it — a
response carrying neither is still refused. It is the only hand-authored departure from
the bundle, deliberately one field of one schema and pinned by its own test, so if
Adobe reconciles the two the exception becomes visible instead of silently redundant.

#### The source listing was reading a key Adobe does not send (T1052)

`GET /content-sources` is the second endpoint the suite stands in miniature — T1045
added it so a source name that resolves to nothing is a statement rather than an
inference. Its fixture was hand-written and unchecked, and pointing
`ContentSourceListPage` at it found a live bug rather than a fixture typo.

`TurContentAiSourceCatalog` read the capability block as **`search`**. The bundle
declares it as **`config`** (a `SourceConfig` carrying `vectorSpaces` and
`lexicalSpaces`). Validation alone does not catch that — `ContentSource` permits extra
properties, so a `search`-keyed page is schema-valid — which is exactly why it went
unnoticed: the fixture and the reader agreed with each other and neither agreed with
Adobe. **The fixture had to say what Adobe sends before the bug became a failing
test**; correcting it broke seven.

The failure it was producing does not throw. Every source parsed with no vector
spaces, no lexical spaces and no filterable fields, so facets quietly stop working on
a federated site and `indexExists` answers from a listing it cannot read. `config` is
now preferred and `search` kept as a fallback — one `get()` against a live third-party
endpoint this project cannot query to prove what the deployed version sends, and
reading both beats being wrong in either direction.

It also exposed a hole in the T1048 transform: `PublicFieldConfig`'s discriminator
maps `BOOLEAN`, `DOUBLE`, `LONG` and `DATE` **back at itself**, so rewriting the
parent into a `oneOf` of `$ref`s produced a branch containing itself and a
`StackOverflowError`. Those values are now inlined as the parent's own constraints
plus the discriminator pin.

### Elasticsearch Implementation Notes

The Elasticsearch plugin includes:
- Connection management via `TurElasticsearchInstanceProcess`
- Query building with query_string and match_all support
- Result mapping to common `TurSEResults` format
- Pagination and sorting support
- Basic facet support (can be enhanced)

Advanced features like MLT (More Like This), spell checking, and grouping can be added incrementally.

## Migration from Direct Solr Usage

The plugin system maintains backward compatibility. Existing Solr-based installations will continue to work without any configuration changes, as Solr is the default search engine.

## Logging

The plugin system provides informative logs:
- Plugin registration and initialization
- Default engine selection
- Warnings for unimplemented plugins

Example logs:
```
INFO  TurSearchEnginePluginFactory - Initialized TurSearchEnginePluginFactory with 2 plugins. Default engine: solr
INFO  TurSearchEnginePluginFactory - Registered plugin: solr
INFO  TurSearchEnginePluginFactory - Registered plugin: elasticsearch
```
