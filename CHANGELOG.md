## 2026.3

#### NEW FEATURES
* Bento UI — redesigned admin console (frosted-glass layout, inline editing, scroll-linked save bar)
* AI Agent Skills — skill folders executed in a Docker sandbox, usable from chat and Semantic Navigation
* Agent workspace — per-conversation file store with signed URLs, SSE events and a Groovy helper
* Code Interpreter — sandboxed Python execution (native or Docker) with warm pool and structured results
* Governed LLM Gateway — OpenAI-compatible governed egress
* Unified model entity, visual model-type selection and multi-model LLM instances
* Public curated model catalog API with a multi-source regeneration pipeline
* Vectorless (structured-data) RAG plus batch/bulk structured-feed indexing
* Hybrid ranking for Semantic Navigation (BM25 + kNN fused with Reciprocal Rank Fusion)
* Pluggable RAG reranker (LLM, cross-encoder or Cohere)
* Thesaurus — microthesauri and index-time term expansion
* Native synonyms and query expansion
* Synthetic personas — persona match, persona dialogue and synthetic user research projects
* Evaluation platform with pluggable graders and datasets
* Field-manifest provisioning (schema-as-code), LLM-assisted derivation and field-coverage reporting
* Migration tooling from Algolia and Elasticsearch
* HuggingFace embedding provider and provider-aware model discovery
* Scalable, pluggable audio transcription with chunking
* Pluggable URL content fetcher with headless-browser fallback
* Multi-tenancy with tenant auto-provisioning
* Chat webhooks, multi-modal slots, slot-delta streaming and submission retention
* Memory compression and automatic offload of large tool results
* Turing CLI (`@viglet/turing-cli`) and the zero-dependency vanilla JS SDK (`@viglet/turing-sdk`)
* `@viglet/turing-react-ui` headless component package
* Live Preview — per-turn prompt diagnostics
* MCP server support with injected LLM instructions
* Client-side conversion analytics and Google Analytics bridge
* Reference showcase application ("Atlas Store")

#### IMPROVEMENTS
* Open-core source split — public snapshots published to `openviglet/turing-ce`
* Composable, cache-friendly prompt-assembly pipeline
* Bounded hexagonal domain layer with enforced conventions
* JPA caching strategy reworked — cache DTOs, never entities
* Security hardening following an external audit
* Documentation parity refresh across the docs site
* SDK parity between the vanilla and React clients
* GEO / LLM discoverability for published content

## 2026.2

#### NEW FEATURES
* Chat Flow Visual Builder (drag-and-drop conversational flows)
* Agent-centric chat with default agent
* AI Mode chat
* Query DSL API with Solr and Elasticsearch executors
* DSL aggregations, collapse, suggest, rescore and kNN
* ANN / vector search UI and API
* RAG reindexing with filters, parallel reindex and smoothed ETA
* Lucene vector store provider
* Lucene autocomplete, highlighter, sort and numeric doc values
* Marketplace (API + UI) with connector and sample apps
* Customizable dashboard with draggable widgets
* Git server (JGit HTTP backend) and Git repository browser
* Collection import/export (JSONL/ZIP)
* OAuth2 social login (Google, Microsoft, GitHub)
* User self-registration
* SN SPA templates served from storage (`searchTemplate`, SPA manifest, `includeTemplate` export)
* URL-synced search store with composable hooks and grouped search
* `TuringSearchField` and search history hook
* Site icons and result-card thumbnails
* Additional custom facet operators
* Redis logging support and configurable engine
* Property-based Solr mode (`turing.solr.enabled` / `turing.solr.endpoint`)
* React SDK Storybook and documentation
* Turing bootloader UI with animated molecule logo
* Koyeb deploy configuration and GitHub Pages workflow

#### IMPROVEMENTS
* Migrated frontend to pnpm + Turborepo monorepo
* Hazelcast-backed distributed cache (`hazelcast-spring`)
* Refactored LLM/Store provider forms and options
* Improved RAG GenAI flows, logging and AI UX
* Upgraded Viglet Design System
* Dependency upgrades (Vite, React Router, Jedis 7, ESLint)

#### FIXES
* CORS wildcard origin handling now uses `allowedOriginPatterns`
* OAuth2 `ClientRegistrationRepository` bean made optional
* Setup flow handles missing admin user on fresh deploy
* Upgraded Storybook and Vite to patch security vulnerabilities
* Fixed Koyeb build (multi-stage Dockerfile, `git-commit-id` plugin)

## 2026.1

#### BREAKING CHANGES
* GraphQL `siteSearch` now exposes `SearchDocument.fields` as `JSON` scalar, replacing the fixed `SearchDocumentFields` object.

#### IMPROVEMENTS
* GraphQL `siteSearch` dynamic fields are now normalized from persisted site fields (`TurSNSiteFieldExt`), including disabled fields with `null` when absent.

## 0.3.7

#### NEW FEATURES
* Maven
* Ranking Rules
* Unit Tests
* Integration Tests
* AEM Connector
* Keycloak Integration
* ApiKey
* SN: Facets using OR
* SN: fq.op parameter
* SN: Search using asterisk when return no results.
* Create solr core automatically
* Using Artemis

#### IMPROVEMENTS
* Spring Boot 3.2.0
* Angular 17
* Spotlight
* UI Flow
* UI: Order by

## 0.3.6

#### NEW FEATURES
* Store information about user accesses and searches performed.
* Reports - Generates access report, including targeting rules.
* Latest searches - Allows you to show the latest searches performed by the user

#### IMPROVEMENTS
* Java 17

## 0.3.5

#### NEW FEATURES
* Spotlight
* Multi language
* UI: Angular 13

#### IMPROVEMENTS
* Java 14
* Spring Boot 2.5.6

## 0.3.4

#### NEW FEATURES

## 0.3.3

#### NEW FEATURES
* Targeting Rules
* UUID for Primary Key

## 0.3.2

#### NEW FEATURES
* Unit Test
* SpaCy Plugin
* DockerFile
* SN Site: Import
* Export SNSite
* Default Fields into Search
* NLP and Thesaurus Activation
* Dynamic Fields: Text, Description and Date
* SN: MaxRows
* Deindexing by Type

#### IMPROVEMENTS
* Check Box fields on SN were fixed
* Spring Boot 2.1.2
* Using lib instead of modules
* Release Resources: HTTPClient and SolrServer
* SNSite using UUID
* Remove newline and trim to concatenated Text

## 0.3.1

#### NEW FEATURES
* Deindexing API

#### IMPROVEMENTS
* Spring Boot 2.0.5
* Gradle 4.10.1
* Using WebJars