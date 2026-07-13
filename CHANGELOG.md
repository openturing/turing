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