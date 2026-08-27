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

Wire coverage is `TurContentAiSearchEnginePluginTest`, which stands the Content AI
endpoint in miniature and asserts the request bodies against Adobe's published
OpenAPI examples — a mocked client would prove nothing about the translation,
which is the whole of the feature.

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
