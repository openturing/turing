---
name: turing-search-lucene
description: Turing search-engine plugin abstraction and Lucene 10 gotchas — the TurSearchEnginePlugin lifecycle interface, resolving plugins via TurSearchEnginePluginFactory, Lucene 10 API removals (no FacetsCollector.search(), no TopScoreDocCollector.create(int,int)), the primitive int flags on TurSNSiteFieldExt (compare == 1, not null), and that the embedded Lucene vector store (TurLuceneStoreProvider) is separate from the Lucene search-engine plugin. Use whenever working with search-engine plugins (Solr/Elasticsearch/Lucene), the lucene/ package, faceting, or the Lucene vector store.
---

# Search Engine & Lucene Notes

- `TurSearchEnginePlugin` is the search-engine plugin lifecycle interface;
  resolve plugins through `TurSearchEnginePluginFactory` (`getPluginForSite`,
  `getPluginForInstance`, `getPlugin(String type)`). Plugin type strings come
  from `getPluginType()` and are lowercased.
- The Lucene search-engine plugin lives under
  `turing-app/src/main/java/com/viglet/turing/lucene/` and uses Lucene 10.
  Lucene 10 removed `FacetsCollector.search()`; do not use it.
- `TopScoreDocCollector.create(int, int)` does not exist in Lucene 10. Use
  separate `searcher.search()` calls where needed.
- `TurSNSiteFieldExt.getFacet()`, `getEnabled()`, and `getMultiValued()` are
  primitive `int` flags; compare with `== 1`, not null checks. Only
  `getFacetPosition()` is nullable.
- The embedded Lucene vector store is separate from the search-engine Lucene
  plugin. It is exposed through `TurLuceneStoreProvider` with plugin type
  `lucene`.
- In SN field-check flows, `TurSolrFieldBean.getType()` may be `null`; prefer
  `Objects.equals(actual, expected)` over calling `.equals()` on nullable values.
