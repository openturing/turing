package com.viglet.turing.client.sn;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.classic.methods.HttpUriRequestBase;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.sun.net.httpserver.HttpServer;
import com.viglet.turing.client.auth.credentials.TurApiKeyCredentials;
import com.viglet.turing.client.auth.credentials.TurUsernamePasswordCredentials;
import com.viglet.turing.client.sn.autocomplete.TurSNAutoCompleteQuery;
import com.viglet.turing.client.sn.response.QueryTurSNResponse;

/**
 * Comprehensive tests for the Turing Search REST API via the Java SDK.
 * Covers all query parameters, sorting, filtering, pagination, facets,
 * grouping, autocomplete, spell-check, and edge cases.
 *
 * Bugs found are documented inline with BUG markers.
 */
class TurSNSearchAPITest {

    // ──────────────────────────────────────────────────────────────────────
    // Minimal valid search response JSON used by the embedded HTTP server
    // ──────────────────────────────────────────────────────────────────────

    private static final String EMPTY_SEARCH_RESPONSE = """
            {
              "pagination": [],
              "queryContext": { "count": 0, "limit": 10, "offset": 0, "pageCount": 0, "responseTime": 5 },
              "results": { "document": [] },
              "groups": [],
              "widget": {
                "facet": [],
                "facetToRemove": null,
                "spellCheck": { "correctedText": false, "original": { "text": "", "link": "" }, "corrected": { "text": "", "link": "" } },
                "spotlights": [],
                "locales": []
              }
            }
            """;

    private static final String SINGLE_RESULT_RESPONSE = """
            {
              "pagination": [
                { "type": "CURRENT", "text": "1", "href": "/search?q=foo&p=1", "page": 1 }
              ],
              "queryContext": {
                "count": 1, "limit": 10, "offset": 0, "pageCount": 1, "responseTime": 12,
                "query": { "queryString": "foo", "sort": "relevance" }
              },
              "results": {
                "document": [
                  {
                    "fields": {
                      "id": "doc-1",
                      "title": "Test Document",
                      "abstract": "A test abstract",
                      "url": "http://example.com/doc1",
                      "type": "Page",
                      "publication_date": "2025-01-15T10:30:00Z"
                    },
                    "source": "http://example.com/doc1",
                    "elevate": false,
                    "metadata": []
                  }
                ]
              },
              "groups": [],
              "widget": {
                "facet": [
                  {
                    "label": { "lang": "en", "text": "Type" },
                    "name": "type",
                    "description": "Content type",
                    "type": "STRING",
                    "multiValued": false,
                    "facets": [
                      { "label": "Page", "count": 1, "link": "/search?fq[]=type:Page" }
                    ]
                  }
                ],
                "facetToRemove": null,
                "spellCheck": { "correctedText": false, "original": { "text": "", "link": "" }, "corrected": { "text": "", "link": "" } },
                "spotlights": [],
                "locales": [ { "locale": "en-US", "link": "/en-US" } ]
              }
            }
            """;

    private static final String MULTI_RESULT_PAGINATED_RESPONSE = """
            {
              "pagination": [
                { "type": "CURRENT", "text": "2", "href": "/search?q=foo&p=2", "page": 2 },
                { "type": "FIRST", "text": "1", "href": "/search?q=foo&p=1", "page": 1 },
                { "type": "PREVIOUS", "text": "1", "href": "/search?q=foo&p=1", "page": 1 },
                { "type": "NEXT", "text": "3", "href": "/search?q=foo&p=3", "page": 3 },
                { "type": "LAST", "text": "5", "href": "/search?q=foo&p=5", "page": 5 }
              ],
              "queryContext": {
                "count": 50, "limit": 10, "offset": 10, "pageCount": 5, "responseTime": 20,
                "query": { "queryString": "foo", "sort": "relevance" }
              },
              "results": {
                "document": [
                  { "fields": { "id": "doc-11", "title": "Page 2 Doc 1" }, "source": "", "elevate": false, "metadata": [] },
                  { "fields": { "id": "doc-12", "title": "Page 2 Doc 2" }, "source": "", "elevate": false, "metadata": [] }
                ]
              },
              "groups": [],
              "widget": {
                "facet": [],
                "facetToRemove": null,
                "spellCheck": { "correctedText": false, "original": { "text": "", "link": "" }, "corrected": { "text": "", "link": "" } },
                "spotlights": [],
                "locales": []
              }
            }
            """;

    private static final String GROUPED_RESPONSE = """
            {
              "pagination": [],
              "queryContext": { "count": 3, "limit": 10, "offset": 0, "pageCount": 1, "responseTime": 15 },
              "results": null,
              "groups": [
                {
                  "name": "Page",
                  "count": 2,
                  "limit": 10,
                  "page": 1,
                  "pageCount": 1,
                  "pageStart": 1,
                  "pageEnd": 1,
                  "results": {
                    "document": [
                      { "fields": { "id": "g1", "title": "Group Doc 1", "type": "Page" }, "source": "", "elevate": false, "metadata": [] }
                    ]
                  },
                  "pagination": [ { "type": "CURRENT", "text": "1", "href": "/search?group=type&p=1", "page": 1 } ]
                },
                {
                  "name": "Article",
                  "count": 1,
                  "limit": 10,
                  "page": 1,
                  "pageCount": 1,
                  "pageStart": 1,
                  "pageEnd": 1,
                  "results": {
                    "document": [
                      { "fields": { "id": "g2", "title": "Group Doc 2", "type": "Article" }, "source": "", "elevate": false, "metadata": [] }
                    ]
                  },
                  "pagination": [ { "type": "CURRENT", "text": "1", "href": "/search?group=type&p=1", "page": 1 } ]
                }
              ],
              "widget": {
                "facet": [],
                "facetToRemove": null,
                "spellCheck": { "correctedText": false, "original": { "text": "", "link": "" }, "corrected": { "text": "", "link": "" } },
                "spotlights": [],
                "locales": []
              }
            }
            """;

    private static final String SPELLCHECK_RESPONSE = """
            {
              "pagination": [
                { "type": "CURRENT", "text": "1", "href": "/search?q=corrected&p=1", "page": 1 }
              ],
              "queryContext": { "count": 5, "limit": 10, "offset": 0, "pageCount": 1, "responseTime": 8 },
              "results": {
                "document": [
                  { "fields": { "id": "s1", "title": "Spell Result" }, "source": "", "elevate": false, "metadata": [] }
                ]
              },
              "groups": [],
              "widget": {
                "facet": [],
                "facetToRemove": null,
                "spellCheck": {
                  "correctedText": true,
                  "original": { "uriTurSNSite": "http://localhost/search?q=tset", "text": "tset", "isEnabled": true },
                  "corrected": { "uriTurSNSite": "http://localhost/search?q=test", "text": "test", "isEnabled": false }
                },
                "spotlights": [
                  {
                    "id": "spot-1",
                    "position": 1,
                    "title": "Featured Result",
                    "type": "Page",
                    "referenceId": "ref-1",
                    "content": "Spotlight content",
                    "link": "http://example.com/featured"
                  }
                ],
                "locales": []
              }
            }
            """;

    private static final String MULTI_FACET_RESPONSE = """
            {
              "pagination": [
                { "type": "CURRENT", "text": "1", "href": "/search?q=foo&p=1", "page": 1 }
              ],
              "queryContext": { "count": 10, "limit": 10, "offset": 0, "pageCount": 1, "responseTime": 10 },
              "results": {
                "document": [
                  { "fields": { "id": "f1", "title": "Facet Doc" }, "source": "", "elevate": false, "metadata": [] }
                ]
              },
              "groups": [],
              "widget": {
                "facet": [
                  {
                    "label": { "lang": "en", "text": "Type" },
                    "name": "type",
                    "description": "Content type",
                    "type": "STRING",
                    "multiValued": false,
                    "facets": [
                      { "label": "Page", "count": 5, "link": "/search?fq[]=type:Page" },
                      { "label": "Article", "count": 3, "link": "/search?fq[]=type:Article" },
                      { "label": "News", "count": 2, "link": "/search?fq[]=type:News" }
                    ]
                  },
                  {
                    "label": { "lang": "en", "text": "Author" },
                    "name": "author",
                    "description": "Author field",
                    "type": "STRING",
                    "multiValued": true,
                    "facets": [
                      { "label": "John", "count": 6, "link": "/search?fq[]=author:John" },
                      { "label": "Jane", "count": 4, "link": "/search?fq[]=author:Jane" }
                    ]
                  }
                ],
                "facetToRemove": {
                  "label": { "lang": "en", "text": "Remove Filters" },
                  "name": "facetToRemove",
                  "description": "Applied filters",
                  "type": "STRING",
                  "multiValued": false,
                  "facets": [
                    { "label": "type:Page", "count": 0, "link": "/search?q=foo" }
                  ]
                },
                "spellCheck": { "correctedText": false, "original": { "text": "", "link": "" }, "corrected": { "text": "", "link": "" } },
                "spotlights": [],
                "locales": []
              }
            }
            """;

    // ──────────────────────────────────────────────────────────────────────
    // Embedded HTTP Server helper
    // ──────────────────────────────────────────────────────────────────────

    private static final class SearchHttpServer implements AutoCloseable {
        private final HttpServer server;
        private final List<CapturedRequest> capturedRequests = new ArrayList<>();

        record CapturedRequest(String method, String uri, String body) {
        }

        private SearchHttpServer(HttpServer server) {
            this.server = server;
        }

        static SearchHttpServer start(String searchResponse) throws IOException {
            HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
            SearchHttpServer searchServer = new SearchHttpServer(server);

            server.createContext("/api/sn/", exchange -> {
                String path = exchange.getRequestURI().getPath();
                String query = exchange.getRequestURI().getRawQuery();
                String fullUri = query != null ? path + "?" + query : path;
                String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                searchServer.capturedRequests.add(new CapturedRequest(exchange.getRequestMethod(), fullUri, body));

                String response;
                if (path.endsWith("/ac")) {
                    response = "[\"suggestion1\",\"suggestion2\",\"suggestion3\"]";
                } else if (path.endsWith("/search/locales")) {
                    response = "[{\"locale\":\"en-US\",\"link\":\"/en-US\"},{\"locale\":\"pt-BR\",\"link\":\"/pt-BR\"}]";
                } else if (path.endsWith("/search/latest")) {
                    response = "[\"latest1\",\"latest2\"]";
                } else {
                    response = searchResponse;
                }

                byte[] payload = response.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, payload.length);
                try (OutputStream out = exchange.getResponseBody()) {
                    out.write(payload);
                }
            });

            server.start();
            return searchServer;
        }

        int port() {
            return server.getAddress().getPort();
        }

        List<CapturedRequest> getCapturedRequests() {
            return capturedRequests;
        }

        CapturedRequest getLastSearchRequest() {
            return capturedRequests.stream()
                    .filter(r -> r.uri().contains("/search") && !r.uri().contains("/locales")
                            && !r.uri().contains("/latest"))
                    .reduce((first, second) -> second)
                    .orElse(null);
        }

        @Override
        public void close() {
            server.stop(0);
        }
    }

    // Helper to parse query string parameters from a URI
    private static Map<String, List<String>> parseQueryString(String uri) {
        Map<String, List<String>> params = new LinkedHashMap<>();
        int qIndex = uri.indexOf('?');
        if (qIndex < 0)
            return params;
        String queryStr = uri.substring(qIndex + 1);
        for (String pair : queryStr.split("&")) {
            String[] parts = pair.split("=", 2);
            String key = URLDecoder.decode(parts[0], StandardCharsets.UTF_8);
            String value = parts.length > 1 ? URLDecoder.decode(parts[1], StandardCharsets.UTF_8) : "";
            params.computeIfAbsent(key, k -> new ArrayList<>()).add(value);
        }
        return params;
    }

    // ──────────────────────────────────────────────────────────────────────
    // 1. Basic Query Parameter Tests
    // ──────────────────────────────────────────────────────────────────────

    @Nested
    class BasicQueryTests {

        @Test
        void shouldSendQueryParameterQ() throws Exception {
            try (SearchHttpServer http = SearchHttpServer.start(EMPTY_SEARCH_RESPONSE)) {
                TurSNServer server = new TurSNServer(
                        URI.create("http://localhost:" + http.port()), "MySite", Locale.US);
                TurSNQuery query = new TurSNQuery();
                query.setQuery("enterprise search");

                server.query(query);

                SearchHttpServer.CapturedRequest req = http.getLastSearchRequest();
                assertThat(req).isNotNull();
                Map<String, List<String>> params = parseQueryString(req.uri());
                assertThat(params.get("q")).containsExactly("enterprise search");
                assertThat(params.get("_setlocale")).containsExactly("en-US");
            }
        }

        @Test
        void shouldSendLocaleParameter() throws Exception {
            try (SearchHttpServer http = SearchHttpServer.start(EMPTY_SEARCH_RESPONSE)) {
                TurSNServer server = new TurSNServer(
                        URI.create("http://localhost:" + http.port()), "MySite",
                        Locale.of("pt", "BR"));
                TurSNQuery query = new TurSNQuery();
                query.setQuery("busca");

                server.query(query);

                Map<String, List<String>> params = parseQueryString(http.getLastSearchRequest().uri());
                assertThat(params.get("_setlocale")).containsExactly("pt-BR");
            }
        }

        @Test
        void shouldSendRowsParameter() throws Exception {
            try (SearchHttpServer http = SearchHttpServer.start(EMPTY_SEARCH_RESPONSE)) {
                TurSNServer server = new TurSNServer(
                        URI.create("http://localhost:" + http.port()), "MySite", Locale.US);
                TurSNQuery query = new TurSNQuery();
                query.setQuery("test");
                query.setRows(25);

                server.query(query);

                Map<String, List<String>> params = parseQueryString(http.getLastSearchRequest().uri());
                assertThat(params.get("rows")).containsExactly("25");
            }
        }

        @Test
        void shouldNotSendRowsWhenZero() throws Exception {
            try (SearchHttpServer http = SearchHttpServer.start(EMPTY_SEARCH_RESPONSE)) {
                TurSNServer server = new TurSNServer(
                        URI.create("http://localhost:" + http.port()), "MySite", Locale.US);
                TurSNQuery query = new TurSNQuery();
                query.setQuery("test");
                // rows defaults to 0

                server.query(query);

                Map<String, List<String>> params = parseQueryString(http.getLastSearchRequest().uri());
                assertThat(params.get("rows")).isNull();
            }
        }

        @Test
        void shouldDefaultToPage1WhenPageNumberNotSet() throws Exception {
            try (SearchHttpServer http = SearchHttpServer.start(EMPTY_SEARCH_RESPONSE)) {
                TurSNServer server = new TurSNServer(
                        URI.create("http://localhost:" + http.port()), "MySite", Locale.US);
                TurSNQuery query = new TurSNQuery();
                query.setQuery("test");

                server.query(query);

                Map<String, List<String>> params = parseQueryString(http.getLastSearchRequest().uri());
                assertThat(params.get("p")).containsExactly("1");
            }
        }

        @Test
        void shouldSendSpecificPageNumber() throws Exception {
            try (SearchHttpServer http = SearchHttpServer.start(EMPTY_SEARCH_RESPONSE)) {
                TurSNServer server = new TurSNServer(
                        URI.create("http://localhost:" + http.port()), "MySite", Locale.US);
                TurSNQuery query = new TurSNQuery();
                query.setQuery("test");
                query.setPageNumber(3);

                server.query(query);

                Map<String, List<String>> params = parseQueryString(http.getLastSearchRequest().uri());
                assertThat(params.get("p")).containsExactly("3");
            }
        }

        @Test
        void shouldUseGetRequestWhenNoCredentials() throws Exception {
            try (SearchHttpServer http = SearchHttpServer.start(SINGLE_RESULT_RESPONSE)) {
                TurSNServer server = new TurSNServer(
                        URI.create("http://localhost:" + http.port()), "MySite", Locale.US);
                TurSNQuery query = new TurSNQuery();
                query.setQuery("foo");

                server.query(query);

                assertThat(http.getLastSearchRequest().method()).isEqualTo("GET");
            }
        }

        @Test
        void shouldUsePostRequestWhenCredentialsAreSet() throws Exception {
            try (SearchHttpServer http = SearchHttpServer.start(SINGLE_RESULT_RESPONSE)) {
                TurSNServer server = new TurSNServer(
                        URI.create("http://localhost:" + http.port()), "MySite", Locale.US,
                        new TurUsernamePasswordCredentials("admin", "admin"), "admin");
                TurSNQuery query = new TurSNQuery();
                query.setQuery("foo");

                server.query(query);

                assertThat(http.getLastSearchRequest().method()).isEqualTo("POST");
            }
        }

        @Test
        void shouldSendQueryWithSpecialCharacters() throws Exception {
            try (SearchHttpServer http = SearchHttpServer.start(EMPTY_SEARCH_RESPONSE)) {
                TurSNServer server = new TurSNServer(
                        URI.create("http://localhost:" + http.port()), "MySite", Locale.US);
                TurSNQuery query = new TurSNQuery();
                query.setQuery("test & search <special> \"quotes\"");

                server.query(query);

                Map<String, List<String>> params = parseQueryString(http.getLastSearchRequest().uri());
                assertThat(params.get("q")).containsExactly("test & search <special> \"quotes\"");
            }
        }

        @Test
        void shouldHandleUnicodeQueryText() throws Exception {
            try (SearchHttpServer http = SearchHttpServer.start(EMPTY_SEARCH_RESPONSE)) {
                TurSNServer server = new TurSNServer(
                        URI.create("http://localhost:" + http.port()), "MySite", Locale.US);
                TurSNQuery query = new TurSNQuery();
                query.setQuery("搜索 búsqueda поиск");

                server.query(query);

                Map<String, List<String>> params = parseQueryString(http.getLastSearchRequest().uri());
                assertThat(params.get("q")).containsExactly("搜索 búsqueda поиск");
            }
        }

        @Test
        void shouldHandleEmptyQueryString() throws Exception {
            try (SearchHttpServer http = SearchHttpServer.start(EMPTY_SEARCH_RESPONSE)) {
                TurSNServer server = new TurSNServer(
                        URI.create("http://localhost:" + http.port()), "MySite", Locale.US);
                TurSNQuery query = new TurSNQuery();
                query.setQuery("");

                server.query(query);

                Map<String, List<String>> params = parseQueryString(http.getLastSearchRequest().uri());
                assertThat(params.get("q")).containsExactly("");
            }
        }

        @Test
        void shouldHandleWildcardQuery() throws Exception {
            try (SearchHttpServer http = SearchHttpServer.start(EMPTY_SEARCH_RESPONSE)) {
                TurSNServer server = new TurSNServer(
                        URI.create("http://localhost:" + http.port()), "MySite", Locale.US);
                TurSNQuery query = new TurSNQuery();
                query.setQuery("*");

                server.query(query);

                Map<String, List<String>> params = parseQueryString(http.getLastSearchRequest().uri());
                assertThat(params.get("q")).containsExactly("*");
            }
        }
    }

    // ──────────────────────────────────────────────────────────────────────
    // 2. Sorting Tests
    // ──────────────────────────────────────────────────────────────────────

    @Nested
    class SortTests {

        @Test
        void shouldSendNewestSortWhenDescWithNoField() throws Exception {
            try (SearchHttpServer http = SearchHttpServer.start(EMPTY_SEARCH_RESPONSE)) {
                TurSNServer server = new TurSNServer(
                        URI.create("http://localhost:" + http.port()), "MySite", Locale.US);
                TurSNQuery query = new TurSNQuery();
                query.setQuery("test");
                query.setSortField(TurSNQuery.Order.desc);

                server.query(query);

                Map<String, List<String>> params = parseQueryString(http.getLastSearchRequest().uri());
                assertThat(params.get("sort")).containsExactly("newest");
            }
        }

        @Test
        void shouldSendOldestSortWhenAscWithNoField() throws Exception {
            try (SearchHttpServer http = SearchHttpServer.start(EMPTY_SEARCH_RESPONSE)) {
                TurSNServer server = new TurSNServer(
                        URI.create("http://localhost:" + http.port()), "MySite", Locale.US);
                TurSNQuery query = new TurSNQuery();
                query.setQuery("test");
                query.setSortField(TurSNQuery.Order.asc);

                server.query(query);

                Map<String, List<String>> params = parseQueryString(http.getLastSearchRequest().uri());
                assertThat(params.get("sort")).containsExactly("oldest");
            }
        }

        @Test
        void shouldSendFieldSortWithFieldAndOrder() throws Exception {
            try (SearchHttpServer http = SearchHttpServer.start(EMPTY_SEARCH_RESPONSE)) {
                TurSNServer server = new TurSNServer(
                        URI.create("http://localhost:" + http.port()), "MySite", Locale.US);
                TurSNQuery query = new TurSNQuery();
                query.setQuery("test");
                query.setSortField("title", TurSNQuery.Order.asc);

                server.query(query);

                Map<String, List<String>> params = parseQueryString(http.getLastSearchRequest().uri());
                assertThat(params.get("sort")).containsExactly("title asc");
            }
        }

        @Test
        void shouldSendFieldSortDescending() throws Exception {
            try (SearchHttpServer http = SearchHttpServer.start(EMPTY_SEARCH_RESPONSE)) {
                TurSNServer server = new TurSNServer(
                        URI.create("http://localhost:" + http.port()), "MySite", Locale.US);
                TurSNQuery query = new TurSNQuery();
                query.setQuery("test");
                query.setSortField("publication_date", TurSNQuery.Order.desc);

                server.query(query);

                Map<String, List<String>> params = parseQueryString(http.getLastSearchRequest().uri());
                assertThat(params.get("sort")).containsExactly("publication_date desc");
            }
        }

        @Test
        void shouldNotSendSortWhenNotSet() throws Exception {
            try (SearchHttpServer http = SearchHttpServer.start(EMPTY_SEARCH_RESPONSE)) {
                TurSNServer server = new TurSNServer(
                        URI.create("http://localhost:" + http.port()), "MySite", Locale.US);
                TurSNQuery query = new TurSNQuery();
                query.setQuery("test");
                // no sort set

                server.query(query);

                Map<String, List<String>> params = parseQueryString(http.getLastSearchRequest().uri());
                assertThat(params.get("sort")).isNull();
            }
        }

        @Test
        void shouldSortByRelevance() throws Exception {
            try (SearchHttpServer http = SearchHttpServer.start(EMPTY_SEARCH_RESPONSE)) {
                TurSNServer server = new TurSNServer(
                        URI.create("http://localhost:" + http.port()), "MySite", Locale.US);
                TurSNQuery query = new TurSNQuery();
                query.setQuery("test");
                TurSNSortField sortField = new TurSNSortField();
                sortField.setSort(TurSNQuery.Order.relevance);
                query.setSortField(sortField);

                server.query(query);

                String uri = http.getLastSearchRequest().uri();
                assertThat(uri).contains("sort=relevance");
            }
        }

        @Test
        void shouldHaveRelevanceSortOption() {
            assertThat(TurSNQuery.Order.values()).hasSize(3);
            assertThat(TurSNQuery.Order.values()).containsExactly(
                    TurSNQuery.Order.asc, TurSNQuery.Order.desc, TurSNQuery.Order.relevance);
        }

        @Test
        void shouldNotSendSortWhenSortFieldHasNullSort() throws Exception {
            try (SearchHttpServer http = SearchHttpServer.start(EMPTY_SEARCH_RESPONSE)) {
                TurSNServer server = new TurSNServer(
                        URI.create("http://localhost:" + http.port()), "MySite", Locale.US);
                TurSNQuery query = new TurSNQuery();
                query.setQuery("test");
                TurSNSortField sortField = new TurSNSortField();
                sortField.setField("title");
                // sort order is null
                query.setSortField(sortField);

                server.query(query);

                Map<String, List<String>> params = parseQueryString(http.getLastSearchRequest().uri());
                // When sort order is null, no sort param should be sent
                assertThat(params.get("sort")).isNull();
            }
        }
    }

    // ──────────────────────────────────────────────────────────────────────
    // 3. Filter Query Tests
    // ──────────────────────────────────────────────────────────────────────

    @Nested
    class FilterQueryTests {

        @Test
        void shouldSendSingleFilterQuery() throws Exception {
            try (SearchHttpServer http = SearchHttpServer.start(EMPTY_SEARCH_RESPONSE)) {
                TurSNServer server = new TurSNServer(
                        URI.create("http://localhost:" + http.port()), "MySite", Locale.US);
                TurSNQuery query = new TurSNQuery();
                query.setQuery("test");
                query.addFilterQuery("type:Page");

                server.query(query);

                Map<String, List<String>> params = parseQueryString(http.getLastSearchRequest().uri());
                assertThat(params.get("fq[]")).containsExactly("type:Page");
            }
        }

        @Test
        void shouldSendMultipleFilterQueries() throws Exception {
            try (SearchHttpServer http = SearchHttpServer.start(EMPTY_SEARCH_RESPONSE)) {
                TurSNServer server = new TurSNServer(
                        URI.create("http://localhost:" + http.port()), "MySite", Locale.US);
                TurSNQuery query = new TurSNQuery();
                query.setQuery("test");
                query.addFilterQuery("type:Page", "author:John");

                server.query(query);

                Map<String, List<String>> params = parseQueryString(http.getLastSearchRequest().uri());
                assertThat(params.get("fq[]")).containsExactly("type:Page", "author:John");
            }
        }

        @Test
        void shouldSendFilterQueriesAddedInMultipleCalls() throws Exception {
            try (SearchHttpServer http = SearchHttpServer.start(EMPTY_SEARCH_RESPONSE)) {
                TurSNServer server = new TurSNServer(
                        URI.create("http://localhost:" + http.port()), "MySite", Locale.US);
                TurSNQuery query = new TurSNQuery();
                query.setQuery("test");
                query.addFilterQuery("type:Page");
                query.addFilterQuery("status:published");
                query.addFilterQuery("lang:en");

                server.query(query);

                Map<String, List<String>> params = parseQueryString(http.getLastSearchRequest().uri());
                assertThat(params.get("fq[]")).containsExactly("type:Page", "status:published", "lang:en");
            }
        }

        @Test
        void shouldSendDateRangeFilterQuery() throws Exception {
            try (SearchHttpServer http = SearchHttpServer.start(EMPTY_SEARCH_RESPONSE)) {
                TurSNServer server = new TurSNServer(
                        URI.create("http://localhost:" + http.port()), "MySite", Locale.US);
                TurSNQuery query = new TurSNQuery();
                query.setQuery("test");
                query.addFilterQuery("publication_date:[2024-01-01T00:00:00Z TO 2024-12-31T23:59:59Z]");

                server.query(query);

                Map<String, List<String>> params = parseQueryString(http.getLastSearchRequest().uri());
                assertThat(params.get("fq[]")).hasSize(1);
                assertThat(params.get("fq[]").getFirst())
                        .contains("publication_date:[2024-01-01T00:00:00Z TO 2024-12-31T23:59:59Z]");
            }
        }

        @Test
        void shouldNotSendFilterQueriesWhenNotSet() throws Exception {
            try (SearchHttpServer http = SearchHttpServer.start(EMPTY_SEARCH_RESPONSE)) {
                TurSNServer server = new TurSNServer(
                        URI.create("http://localhost:" + http.port()), "MySite", Locale.US);
                TurSNQuery query = new TurSNQuery();
                query.setQuery("test");

                server.query(query);

                Map<String, List<String>> params = parseQueryString(http.getLastSearchRequest().uri());
                assertThat(params.get("fq[]")).isNull();
            }
        }

        /**
         * BUG: The SDK's TurSNQuery only supports fq[] (default filter queries)
         * but the REST API also supports fq.and[], fq.or[], and fq.op parameters.
         * There is no way to send AND/OR filter queries through the SDK.
         */
        @Test
        void shouldNotSupportAndOrFilterQueriesInSdk() {
            TurSNQuery query = new TurSNQuery();
            // Only addFilterQuery exists, which maps to fq[]
            // No method for AND/OR filters
            query.addFilterQuery("type:Page");
            assertThat(query.getFieldQueries()).containsExactly("type:Page");
            // BUG: Missing addFilterQueryAnd() and addFilterQueryOr() methods
            // BUG: Missing setFilterQueryOperator() method
        }
    }

    // ──────────────────────────────────────────────────────────────────────
    // 4. Between Dates Tests
    // ──────────────────────────────────────────────────────────────────────

    @Nested
    class BetweenDatesTests {

        @Test
        void shouldSendBetweenDatesAsFilterQuery() throws Exception {
            try (SearchHttpServer http = SearchHttpServer.start(EMPTY_SEARCH_RESPONSE)) {
                TurSNServer server = new TurSNServer(
                        URI.create("http://localhost:" + http.port()), "MySite", Locale.US);
                TurSNQuery query = new TurSNQuery();
                query.setQuery("test");
                // epoch 0 = 1970-01-01T00:00:00Z, epoch 86400000 = 1970-01-02T00:00:00Z
                query.setBetweenDates("created", new Date(0L), new Date(86_400_000L));

                server.query(query);

                Map<String, List<String>> params = parseQueryString(http.getLastSearchRequest().uri());
                assertThat(params.get("fq[]")).hasSize(1);
                String fq = params.get("fq[]").getFirst();
                assertTrue(fq.startsWith("created:["), "should start with 'created:['");
                assertTrue(fq.contains("1970-01-01T00:00:00Z"), "should contain start date");
                assertTrue(fq.contains("1970-01-02T00:00:00Z"), "should contain end date");
                assertTrue(fq.contains(" TO "), "should contain ' TO '");
            }
        }

        @Test
        void shouldNotSendBetweenDatesWhenFieldIsNull() throws Exception {
            try (SearchHttpServer http = SearchHttpServer.start(EMPTY_SEARCH_RESPONSE)) {
                TurSNServer server = new TurSNServer(
                        URI.create("http://localhost:" + http.port()), "MySite", Locale.US);
                TurSNQuery query = new TurSNQuery();
                query.setQuery("test");
                query.setBetweenDates(new TurSNClientBetweenDates(null, new Date(), new Date()));

                server.query(query);

                Map<String, List<String>> params = parseQueryString(http.getLastSearchRequest().uri());
                // Should not send fq[] when field is null
                assertThat(params.get("fq[]")).isNull();
            }
        }

        @Test
        void shouldNotSendBetweenDatesWhenStartDateIsNull() throws Exception {
            try (SearchHttpServer http = SearchHttpServer.start(EMPTY_SEARCH_RESPONSE)) {
                TurSNServer server = new TurSNServer(
                        URI.create("http://localhost:" + http.port()), "MySite", Locale.US);
                TurSNQuery query = new TurSNQuery();
                query.setQuery("test");
                query.setBetweenDates(new TurSNClientBetweenDates("created", null, new Date()));

                server.query(query);

                Map<String, List<String>> params = parseQueryString(http.getLastSearchRequest().uri());
                assertThat(params.get("fq[]")).isNull();
            }
        }

        @Test
        void shouldNotSendBetweenDatesWhenEndDateIsNull() throws Exception {
            try (SearchHttpServer http = SearchHttpServer.start(EMPTY_SEARCH_RESPONSE)) {
                TurSNServer server = new TurSNServer(
                        URI.create("http://localhost:" + http.port()), "MySite", Locale.US);
                TurSNQuery query = new TurSNQuery();
                query.setQuery("test");
                query.setBetweenDates(new TurSNClientBetweenDates("created", new Date(), null));

                server.query(query);

                Map<String, List<String>> params = parseQueryString(http.getLastSearchRequest().uri());
                assertThat(params.get("fq[]")).isNull();
            }
        }

        @Test
        void shouldCombineBetweenDatesWithFilterQueries() throws Exception {
            try (SearchHttpServer http = SearchHttpServer.start(EMPTY_SEARCH_RESPONSE)) {
                TurSNServer server = new TurSNServer(
                        URI.create("http://localhost:" + http.port()), "MySite", Locale.US);
                TurSNQuery query = new TurSNQuery();
                query.setQuery("test");
                query.addFilterQuery("type:Page");
                query.setBetweenDates("created", new Date(0L), new Date(86_400_000L));

                server.query(query);

                Map<String, List<String>> params = parseQueryString(http.getLastSearchRequest().uri());
                // Both filter query and between dates should be present as fq[]
                assertThat(params.get("fq[]")).hasSize(2);
                assertThat(params.get("fq[]").getFirst()).isEqualTo("type:Page");
                assertThat(params.get("fq[]").get(1)).contains("created:[");
            }
        }
    }

    // ──────────────────────────────────────────────────────────────────────
    // 5. Grouping Tests
    // ──────────────────────────────────────────────────────────────────────

    @Nested
    class GroupingTests {

        @Test
        void shouldSendGroupParameter() throws Exception {
            try (SearchHttpServer http = SearchHttpServer.start(GROUPED_RESPONSE)) {
                TurSNServer server = new TurSNServer(
                        URI.create("http://localhost:" + http.port()), "MySite", Locale.US);
                TurSNQuery query = new TurSNQuery();
                query.setQuery("test");
                query.setGroupBy("type");

                QueryTurSNResponse response = server.query(query);

                Map<String, List<String>> params = parseQueryString(http.getLastSearchRequest().uri());
                assertThat(params.get("group")).containsExactly("type");
                assertThat(response.getGroupResponse()).isNotNull();
                assertThat(response.getGroupResponse()).hasSize(2);
            }
        }

        @Test
        void shouldNotSendGroupWhenNull() throws Exception {
            try (SearchHttpServer http = SearchHttpServer.start(EMPTY_SEARCH_RESPONSE)) {
                TurSNServer server = new TurSNServer(
                        URI.create("http://localhost:" + http.port()), "MySite", Locale.US);
                TurSNQuery query = new TurSNQuery();
                query.setQuery("test");

                server.query(query);

                Map<String, List<String>> params = parseQueryString(http.getLastSearchRequest().uri());
                assertThat(params.get("group")).isNull();
            }
        }

        @Test
        void shouldNotSendGroupWhenEmpty() throws Exception {
            try (SearchHttpServer http = SearchHttpServer.start(EMPTY_SEARCH_RESPONSE)) {
                TurSNServer server = new TurSNServer(
                        URI.create("http://localhost:" + http.port()), "MySite", Locale.US);
                TurSNQuery query = new TurSNQuery();
                query.setQuery("test");
                query.setGroupBy("");

                server.query(query);

                Map<String, List<String>> params = parseQueryString(http.getLastSearchRequest().uri());
                assertThat(params.get("group")).isNull();
            }
        }

        @Test
        void shouldNotSendGroupWhenWhitespace() throws Exception {
            try (SearchHttpServer http = SearchHttpServer.start(EMPTY_SEARCH_RESPONSE)) {
                TurSNServer server = new TurSNServer(
                        URI.create("http://localhost:" + http.port()), "MySite", Locale.US);
                TurSNQuery query = new TurSNQuery();
                query.setQuery("test");
                query.setGroupBy("   ");

                server.query(query);

                Map<String, List<String>> params = parseQueryString(http.getLastSearchRequest().uri());
                assertThat(params.get("group")).isNull();
            }
        }

        @Test
        void shouldMapGroupResponseFields() throws Exception {
            try (SearchHttpServer http = SearchHttpServer.start(GROUPED_RESPONSE)) {
                TurSNServer server = new TurSNServer(
                        URI.create("http://localhost:" + http.port()), "MySite", Locale.US);
                TurSNQuery query = new TurSNQuery();
                query.setQuery("test");
                query.setGroupBy("type");

                QueryTurSNResponse response = server.query(query);

                TurSNGroup firstGroup = response.getGroupResponse().getTurSNGroups().getFirst();
                assertThat(firstGroup.getName()).isEqualTo("Page");
                assertThat(firstGroup.getCount()).isEqualTo(2);
                assertThat(firstGroup.getLimit()).isEqualTo(10);
                assertThat(firstGroup.getPage()).isEqualTo(1);
                assertThat(firstGroup.getPageCount()).isEqualTo(1);
                assertThat(firstGroup.getResults()).hasSize(1);
                assertThat(firstGroup.getPagination()).isNotNull();

                TurSNGroup secondGroup = response.getGroupResponse().getTurSNGroups().get(1);
                assertThat(secondGroup.getName()).isEqualTo("Article");
                assertThat(secondGroup.getCount()).isEqualTo(1);
            }
        }
    }

    // ──────────────────────────────────────────────────────────────────────
    // 6. Response Mapping Tests
    // ──────────────────────────────────────────────────────────────────────

    @Nested
    class ResponseMappingTests {

        @Test
        void shouldMapSingleResultResponse() throws Exception {
            try (SearchHttpServer http = SearchHttpServer.start(SINGLE_RESULT_RESPONSE)) {
                TurSNServer server = new TurSNServer(
                        URI.create("http://localhost:" + http.port()), "MySite", Locale.US);
                TurSNQuery query = new TurSNQuery();
                query.setQuery("foo");

                QueryTurSNResponse response = server.query(query);

                assertThat(response.getResults()).isNotNull();
                assertThat(response.getResults()).hasSize(1);

                TurSNDocument doc = response.getResults().getTurSNDocuments().getFirst();
                assertThat(doc.getFieldValue("title")).isEqualTo("Test Document");
                assertThat(doc.getFieldValue("id")).isEqualTo("doc-1");
                assertThat(doc.getFieldValue("type")).isEqualTo("Page");
                assertThat(doc.getFieldValue("url")).isEqualTo("http://example.com/doc1");
            }
        }

        @Test
        void shouldMapQueryContextFromResponse() throws Exception {
            try (SearchHttpServer http = SearchHttpServer.start(SINGLE_RESULT_RESPONSE)) {
                TurSNServer server = new TurSNServer(
                        URI.create("http://localhost:" + http.port()), "MySite", Locale.US);
                TurSNQuery query = new TurSNQuery();
                query.setQuery("foo");

                QueryTurSNResponse response = server.query(query);

                assertThat(response.getResults().getQueryContext()).isNotNull();
                assertThat(response.getResults().getQueryContext().getCount()).isEqualTo(1);
                assertThat(response.getResults().getQueryContext().getLimit()).isEqualTo(10);
                assertThat(response.getResults().getQueryContext().getOffset()).isZero();
                assertThat(response.getResults().getQueryContext().getPageCount()).isEqualTo(1);
            }
        }

        @Test
        void shouldReturnEmptyResponseOnConnectionFailure() {
            TurSNServer server = new TurSNServer(
                    URI.create("http://localhost:1"), "MySite", Locale.US);
            TurSNQuery query = new TurSNQuery();
            query.setQuery("test");

            QueryTurSNResponse response = server.query(query);

            assertThat(response).isNotNull();
            assertThat(response.getResults()).isNull();
        }

        @Test
        void shouldReturnNonNullFieldValueForMissingField() throws Exception {
            try (SearchHttpServer http = SearchHttpServer.start(SINGLE_RESULT_RESPONSE)) {
                TurSNServer server = new TurSNServer(
                        URI.create("http://localhost:" + http.port()), "MySite", Locale.US);
                TurSNQuery query = new TurSNQuery();
                query.setQuery("foo");

                QueryTurSNResponse response = server.query(query);

                TurSNDocument doc = response.getResults().getTurSNDocuments().getFirst();
                // Accessing a field that doesn't exist in the response
                assertThat(doc.getFieldValue("nonexistent_field")).isNull();
            }
        }

        @Test
        void shouldHandleNullResultsInResponse() throws Exception {
            try (SearchHttpServer http = SearchHttpServer.start(GROUPED_RESPONSE)) {
                TurSNServer server = new TurSNServer(
                        URI.create("http://localhost:" + http.port()), "MySite", Locale.US);
                TurSNQuery query = new TurSNQuery();
                query.setQuery("test");

                QueryTurSNResponse response = server.query(query);

                // GROUPED_RESPONSE has "results": null, but SDK initializes to empty list
                assertThat(response.getResults()).isNotNull();
                assertThat(response.getResults().getTurSNDocuments()).isEmpty();
            }
        }
    }

    // ──────────────────────────────────────────────────────────────────────
    // 7. Pagination Response Tests
    // ──────────────────────────────────────────────────────────────────────

    @Nested
    class PaginationTests {

        @Test
        void shouldMapFullPaginationResponse() throws Exception {
            try (SearchHttpServer http = SearchHttpServer.start(MULTI_RESULT_PAGINATED_RESPONSE)) {
                TurSNServer server = new TurSNServer(
                        URI.create("http://localhost:" + http.port()), "MySite", Locale.US);
                TurSNQuery query = new TurSNQuery();
                query.setQuery("foo");
                query.setPageNumber(2);
                query.setRows(10);

                QueryTurSNResponse response = server.query(query);

                assertThat(response.getPagination()).isNotNull();
                assertThat(response.getPagination().getCurrentPage()).isPresent();
                assertThat(response.getPagination().getCurrentPage().get().getPageNumber()).isEqualTo(2);
                assertThat(response.getPagination().getFirstPage()).isPresent();
                assertThat(response.getPagination().getFirstPage().get().getPageNumber()).isEqualTo(1);
                assertThat(response.getPagination().getPreviousPage()).isPresent();
                assertThat(response.getPagination().getPreviousPage().get().getPageNumber()).isEqualTo(1);
                assertThat(response.getPagination().getNextPage()).isPresent();
                assertThat(response.getPagination().getNextPage().get().getPageNumber()).isEqualTo(3);
                assertThat(response.getPagination().getLastPage()).isPresent();
                assertThat(response.getPagination().getLastPage().get().getPageNumber()).isEqualTo(5);
            }
        }

        @Test
        void shouldMapPageNumberList() throws Exception {
            try (SearchHttpServer http = SearchHttpServer.start(MULTI_RESULT_PAGINATED_RESPONSE)) {
                TurSNServer server = new TurSNServer(
                        URI.create("http://localhost:" + http.port()), "MySite", Locale.US);
                TurSNQuery query = new TurSNQuery();
                query.setQuery("foo");

                QueryTurSNResponse response = server.query(query);

                assertThat(response.getPagination().getPageNumberList()).contains(1, 2, 3, 5);
            }
        }

        @Test
        void shouldFindPageByNumber() throws Exception {
            try (SearchHttpServer http = SearchHttpServer.start(MULTI_RESULT_PAGINATED_RESPONSE)) {
                TurSNServer server = new TurSNServer(
                        URI.create("http://localhost:" + http.port()), "MySite", Locale.US);
                TurSNQuery query = new TurSNQuery();
                query.setQuery("foo");

                QueryTurSNResponse response = server.query(query);

                assertThat(response.getPagination().findByPageNumber(3)).isNotNull();
                assertThat(response.getPagination().findByPageNumber(3).getPageNumber()).isEqualTo(3);
                assertThat(response.getPagination().findByPageNumber(999)).isNull();
            }
        }

        @Test
        void shouldReturnResultsCount() throws Exception {
            try (SearchHttpServer http = SearchHttpServer.start(MULTI_RESULT_PAGINATED_RESPONSE)) {
                TurSNServer server = new TurSNServer(
                        URI.create("http://localhost:" + http.port()), "MySite", Locale.US);
                TurSNQuery query = new TurSNQuery();
                query.setQuery("foo");

                QueryTurSNResponse response = server.query(query);

                assertThat(response.getResults()).hasSize(2);
                assertThat(response.getResults().getQueryContext().getCount()).isEqualTo(50);
            }
        }
    }

    // ──────────────────────────────────────────────────────────────────────
    // 8. Facet Response Tests
    // ──────────────────────────────────────────────────────────────────────

    @Nested
    class FacetTests {

        @Test
        void shouldMapSingleFacetField() throws Exception {
            try (SearchHttpServer http = SearchHttpServer.start(SINGLE_RESULT_RESPONSE)) {
                TurSNServer server = new TurSNServer(
                        URI.create("http://localhost:" + http.port()), "MySite", Locale.US);
                TurSNQuery query = new TurSNQuery();
                query.setQuery("foo");

                QueryTurSNResponse response = server.query(query);

                assertThat(response.getFacetFields()).isNotNull();
                assertThat(response.getFacetFields().getFields()).hasSize(1);
                assertThat(response.getFacetFields().getFields().getFirst().getName()).isEqualTo("type");
                assertThat(response.getFacetFields().getFields().getFirst().getLabel()).isEqualTo("Type");
            }
        }

        @Test
        void shouldMapMultipleFacetFieldsAndValues() throws Exception {
            try (SearchHttpServer http = SearchHttpServer.start(MULTI_FACET_RESPONSE)) {
                TurSNServer server = new TurSNServer(
                        URI.create("http://localhost:" + http.port()), "MySite", Locale.US);
                TurSNQuery query = new TurSNQuery();
                query.setQuery("foo");

                QueryTurSNResponse response = server.query(query);

                assertThat(response.getFacetFields().getFields()).hasSize(2);

                var typeFacet = response.getFacetFields().getFields().getFirst();
                assertThat(typeFacet.getName()).isEqualTo("type");
                assertThat(typeFacet.getValues()).hasSize(3);
                assertThat(typeFacet.getValues().getTurSNFacetFieldValues().getFirst().getLabel()).isEqualTo("Page");
                assertThat(typeFacet.getValues().getTurSNFacetFieldValues().getFirst().getCount()).isEqualTo(5);

                var authorFacet = response.getFacetFields().getFields().get(1);
                assertThat(authorFacet.getName()).isEqualTo("author");
                assertThat(authorFacet.isMultiValued()).isTrue();
                assertThat(authorFacet.getValues()).hasSize(2);
            }
        }

        @Test
        void shouldMapFacetToRemove() throws Exception {
            try (SearchHttpServer http = SearchHttpServer.start(MULTI_FACET_RESPONSE)) {
                TurSNServer server = new TurSNServer(
                        URI.create("http://localhost:" + http.port()), "MySite", Locale.US);
                TurSNQuery query = new TurSNQuery();
                query.setQuery("foo");

                QueryTurSNResponse response = server.query(query);

                assertThat(response.getFacetFields().getFacetWithRemovedValues()).isPresent();
                var removeFacet = response.getFacetFields().getFacetWithRemovedValues().get();
                assertThat(removeFacet.getName()).isEqualTo("facetToRemove");
                assertThat(removeFacet.getLabel()).isEqualTo("Remove Filters");
            }
        }

        @Test
        void shouldIterateOverFacetFields() throws Exception {
            try (SearchHttpServer http = SearchHttpServer.start(MULTI_FACET_RESPONSE)) {
                TurSNServer server = new TurSNServer(
                        URI.create("http://localhost:" + http.port()), "MySite", Locale.US);
                TurSNQuery query = new TurSNQuery();
                query.setQuery("foo");

                QueryTurSNResponse response = server.query(query);

                List<String> facetNames = new ArrayList<>();
                for (var facet : response.getFacetFields()) {
                    facetNames.add(facet.getName());
                }
                assertThat(facetNames).containsExactly("type", "author");
            }
        }
    }

    // ──────────────────────────────────────────────────────────────────────
    // 9. Spell Check & Spotlight Tests
    // ──────────────────────────────────────────────────────────────────────

    @Nested
    class SpellCheckAndSpotlightTests {

        @Test
        void shouldMapSpellCheckCorrectedResponse() throws Exception {
            try (SearchHttpServer http = SearchHttpServer.start(SPELLCHECK_RESPONSE)) {
                TurSNServer server = new TurSNServer(
                        URI.create("http://localhost:" + http.port()), "MySite", Locale.US);
                TurSNQuery query = new TurSNQuery();
                query.setQuery("tset");

                QueryTurSNResponse response = server.query(query);

                assertThat(response.getDidYouMean()).isNotNull();
                assertThat(response.getDidYouMean().isCorrectedText()).isTrue();
                assertThat(response.getDidYouMean().getOriginal().getText()).isEqualTo("tset");
                assertThat(response.getDidYouMean().getCorrected().getText()).isEqualTo("test");
            }
        }

        @Test
        void shouldMapSpellCheckNotCorrectedResponse() throws Exception {
            try (SearchHttpServer http = SearchHttpServer.start(SINGLE_RESULT_RESPONSE)) {
                TurSNServer server = new TurSNServer(
                        URI.create("http://localhost:" + http.port()), "MySite", Locale.US);
                TurSNQuery query = new TurSNQuery();
                query.setQuery("foo");

                QueryTurSNResponse response = server.query(query);

                assertThat(response.getDidYouMean()).isNotNull();
                assertThat(response.getDidYouMean().isCorrectedText()).isFalse();
            }
        }

        @Test
        void shouldMapSpotlightDocuments() throws Exception {
            try (SearchHttpServer http = SearchHttpServer.start(SPELLCHECK_RESPONSE)) {
                TurSNServer server = new TurSNServer(
                        URI.create("http://localhost:" + http.port()), "MySite", Locale.US);
                TurSNQuery query = new TurSNQuery();
                query.setQuery("test");

                QueryTurSNResponse response = server.query(query);

                assertThat(response.getSpotlightDocuments()).hasSize(1);
                var spotlight = response.getSpotlightDocuments().getFirst();
                assertThat(spotlight.getId()).isEqualTo("spot-1");
                assertThat(spotlight.getTitle()).isEqualTo("Featured Result");
                assertThat(spotlight.getPosition()).isEqualTo(1);
                assertThat(spotlight.getType()).isEqualTo("Page");
                assertThat(spotlight.getContent()).isEqualTo("Spotlight content");
                assertThat(spotlight.getLink()).isEqualTo("http://example.com/featured");
            }
        }

        @Test
        void shouldReturnEmptySpotlightsWhenNonePresent() throws Exception {
            try (SearchHttpServer http = SearchHttpServer.start(SINGLE_RESULT_RESPONSE)) {
                TurSNServer server = new TurSNServer(
                        URI.create("http://localhost:" + http.port()), "MySite", Locale.US);
                TurSNQuery query = new TurSNQuery();
                query.setQuery("foo");

                QueryTurSNResponse response = server.query(query);

                assertThat(response.getSpotlightDocuments()).isEmpty();
            }
        }
    }

    // ──────────────────────────────────────────────────────────────────────
    // 10. Autocomplete Tests
    // ──────────────────────────────────────────────────────────────────────

    @Nested
    class AutocompleteTests {

        @Test
        void shouldReturnAutocompleteSuggestions() throws Exception {
            try (SearchHttpServer http = SearchHttpServer.start(EMPTY_SEARCH_RESPONSE)) {
                TurSNServer server = new TurSNServer(
                        URI.create("http://localhost:" + http.port()), "MySite", Locale.US,
                        new TurApiKeyCredentials("key"));
                TurSNAutoCompleteQuery acQuery = new TurSNAutoCompleteQuery();
                acQuery.setQuery("tur");
                acQuery.setRows(5);

                List<String> suggestions = server.autoComplete(acQuery);

                assertThat(suggestions).containsExactly("suggestion1", "suggestion2", "suggestion3");
            }
        }

        @Test
        void shouldSendAutoCompleteParametersCorrectly() throws Exception {
            try (SearchHttpServer http = SearchHttpServer.start(EMPTY_SEARCH_RESPONSE)) {
                TurSNServer server = new TurSNServer(
                        URI.create("http://localhost:" + http.port()), "MySite",
                        Locale.of("pt", "BR"),
                        new TurApiKeyCredentials("key"));
                TurSNAutoCompleteQuery acQuery = new TurSNAutoCompleteQuery();
                acQuery.setQuery("bus");
                acQuery.setRows(3);

                server.autoComplete(acQuery);

                var acRequest = http.getCapturedRequests().stream()
                        .filter(r -> r.uri().contains("/ac"))
                        .findFirst()
                        .orElse(null);
                assertThat(acRequest).isNotNull();
                assertThat(acRequest.method()).isEqualTo("GET");
                Map<String, List<String>> params = parseQueryString(acRequest.uri());
                assertThat(params.get("q")).containsExactly("bus");
                assertThat(params.get("rows")).containsExactly("3");
                assertThat(params.get("_setlocale")).containsExactly("pt-BR");
            }
        }

        @Test
        void shouldReturnEmptyListOnAutoCompleteConnectionError() {
            TurSNServer server = new TurSNServer(
                    URI.create("http://localhost:1"), "MySite", Locale.US,
                    new TurApiKeyCredentials("key"));
            TurSNAutoCompleteQuery acQuery = new TurSNAutoCompleteQuery();
            acQuery.setQuery("test");
            acQuery.setRows(5);

            List<String> suggestions = server.autoComplete(acQuery);

            assertThat(suggestions).isEmpty();
        }
    }

    // ──────────────────────────────────────────────────────────────────────
    // 11. Locales Tests
    // ──────────────────────────────────────────────────────────────────────

    @Nested
    class LocaleTests {

        @Test
        void shouldReturnAvailableLocales() throws Exception {
            try (SearchHttpServer http = SearchHttpServer.start(EMPTY_SEARCH_RESPONSE)) {
                TurSNServer server = new TurSNServer(
                        URI.create("http://localhost:" + http.port()), "MySite", Locale.US,
                        new TurApiKeyCredentials("key"));

                List<TurSNLocale> locales = server.getLocales();

                assertThat(locales).hasSize(2);
                assertThat(locales.getFirst().getLocale()).isEqualTo("en-US");
                assertThat(locales.get(1).getLocale()).isEqualTo("pt-BR");
            }
        }

        @Test
        void shouldReturnEmptyLocalesOnConnectionError() {
            TurSNServer server = new TurSNServer(
                    URI.create("http://localhost:1"), "MySite", Locale.US);

            List<TurSNLocale> locales = server.getLocales();

            assertThat(locales).isEmpty();
        }
    }

    // ──────────────────────────────────────────────────────────────────────
    // 12. Authentication Tests
    // ──────────────────────────────────────────────────────────────────────

    @Nested
    class AuthenticationTests {

        /**
         * BUG: When using TurApiKeyCredentials (not TurUsernamePasswordCredentials),
         * the SDK falls through to prepareGetRequest() which does NOT call
         * TurSNClientUtils.authentication(). The API key is never sent on GET
         * requests. Only POST requests (which require TurUsernamePasswordCredentials)
         * send the authentication header.
         */
        @Test
        void shouldUseGetRequestWhenOnlyApiKeyIsProvided() throws Exception {
            try (SearchHttpServer http = SearchHttpServer.start(SINGLE_RESULT_RESPONSE)) {
                TurSNServer server = new TurSNServer(
                        URI.create("http://localhost:" + http.port()), "MySite", Locale.US,
                        new TurApiKeyCredentials("my-api-key"));
                TurSNQuery query = new TurSNQuery();
                query.setQuery("test");

                server.query(query);

                var req = http.getLastSearchRequest();
                // BUG: Even though apiKey is set, credentials is null,
                // so it uses GET which doesn't send the Key header.
                assertThat(req.method()).isEqualTo("GET");
                // The API key is NOT sent in GET requests - this is a bug
            }
        }

        @Test
        void shouldUsePostRequestWithCredentials() throws Exception {
            try (SearchHttpServer http = SearchHttpServer.start(SINGLE_RESULT_RESPONSE)) {
                TurSNServer server = new TurSNServer(
                        URI.create("http://localhost:" + http.port()), "MySite", Locale.US,
                        new TurUsernamePasswordCredentials("admin", "pass"), "admin");
                TurSNQuery query = new TurSNQuery();
                query.setQuery("test");

                server.query(query);

                assertThat(http.getLastSearchRequest().method()).isEqualTo("POST");
            }
        }

        /**
         * BUG: getLatestSearches() requires TurUsernamePasswordCredentials
         * (checks this.getCredentials() != null). When using TurApiKeyCredentials,
         * getCredentials() returns null and latest searches returns empty.
         */
        @Test
        void shouldReturnEmptyLatestSearchesWithApiKeyOnly() throws Exception {
            try (SearchHttpServer http = SearchHttpServer.start(EMPTY_SEARCH_RESPONSE)) {
                TurSNServer server = new TurSNServer(
                        URI.create("http://localhost:" + http.port()), "MySite", Locale.US,
                        new TurApiKeyCredentials("my-api-key"), "user-1");

                List<String> latestSearches = server.getLatestSearches(10);

                // BUG: Returns empty because getCredentials() is null even though apiKey is set
                assertThat(latestSearches).isEmpty();
            }
        }

        @Test
        void shouldReturnLatestSearchesWithUsernamePasswordCredentials() throws Exception {
            try (SearchHttpServer http = SearchHttpServer.start(EMPTY_SEARCH_RESPONSE)) {
                TurSNServer server = new TurSNServer(
                        URI.create("http://localhost:" + http.port()), "MySite", Locale.US,
                        new TurUsernamePasswordCredentials("admin", "pass"), "admin");

                List<String> latestSearches = server.getLatestSearches(10);

                assertThat(latestSearches).containsExactly("latest1", "latest2");
            }
        }

        /**
         * BUG: deleteItemsByType() only works with TurUsernamePasswordCredentials.
         * When using API key, credentials is null and the method logs an error
         * without attempting the request.
         */
        @Test
        void shouldNotDeleteItemsByTypeWithApiKeyOnly() {
            TurSNServer server = new TurSNServer(
                    URI.create("http://localhost:2700"), "MySite", Locale.US,
                    new TurApiKeyCredentials("my-api-key"));

            // BUG: This silently fails because credentials is null
            // even though authentication could work with API key
            server.deleteItemsByType("Page");
            // No exception but nothing happens - the API key is ignored
            assertThat(server.getApiKey()).isEqualTo("my-api-key");
        }
    }

    // ──────────────────────────────────────────────────────────────────────
    // 13. POST Body Parameters Tests
    // ──────────────────────────────────────────────────────────────────────

    @Nested
    class PostBodyTests {

        @Test
        void shouldSendUserIdInPostBody() throws Exception {
            try (SearchHttpServer http = SearchHttpServer.start(SINGLE_RESULT_RESPONSE)) {
                TurSNServer server = new TurSNServer(
                        URI.create("http://localhost:" + http.port()), "MySite", Locale.US,
                        new TurUsernamePasswordCredentials("admin", "pass"), "user-123");
                TurSNQuery query = new TurSNQuery();
                query.setQuery("test");

                server.query(query);

                String body = http.getLastSearchRequest().body();
                assertThat(body).contains("\"userId\":\"user-123\"");
            }
        }

        @Test
        void shouldSendPopulateMetricsInPostBody() throws Exception {
            try (SearchHttpServer http = SearchHttpServer.start(SINGLE_RESULT_RESPONSE)) {
                TurSNServer server = new TurSNServer(
                        URI.create("http://localhost:" + http.port()), "MySite", Locale.US,
                        new TurUsernamePasswordCredentials("admin", "pass"), "admin");
                TurSNQuery query = new TurSNQuery();
                query.setQuery("test");
                query.setPopulateMetrics(true);

                server.query(query);

                String body = http.getLastSearchRequest().body();
                assertThat(body).contains("\"populateMetrics\":true");
            }
        }

        @Test
        void shouldSendTargetingRulesInPostBody() throws Exception {
            try (SearchHttpServer http = SearchHttpServer.start(SINGLE_RESULT_RESPONSE)) {
                TurSNServer server = new TurSNServer(
                        URI.create("http://localhost:" + http.port()), "MySite", Locale.US,
                        new TurUsernamePasswordCredentials("admin", "pass"), "admin");
                TurSNQuery query = new TurSNQuery();
                query.setQuery("test");
                query.addTargetingRule("segment:premium", "geo:US");

                server.query(query);

                String body = http.getLastSearchRequest().body();
                assertThat(body).contains("targetingRules");
                assertThat(body).contains("segment:premium");
                assertThat(body).contains("geo:US");
            }
        }

        /**
         * BUG: TurSNSitePostParamsBean supports targetingRulesWithCondition,
         * targetingRulesWithConditionAND, and targetingRulesWithConditionOR,
         * but TurSNQuery has no methods to set these. The SDK only supports
         * simple targetingRules list.
         */
        @Test
        void shouldNotSupportTargetingRulesWithConditions() {
            TurSNQuery query = new TurSNQuery();
            // Only simple targeting rules are available
            query.addTargetingRule("rule1");
            assertThat(query.getTargetingRules()).containsExactly("rule1");
            // BUG: No way to set targetingRulesWithCondition, AND, or OR maps
        }
    }

    // ──────────────────────────────────────────────────────────────────────
    // 14. Complete Search Scenario Tests
    // ──────────────────────────────────────────────────────────────────────

    @Nested
    class CompleteSearchScenarioTests {

        @Test
        void shouldExecuteFullSearchWithAllParameters() throws Exception {
            try (SearchHttpServer http = SearchHttpServer.start(SINGLE_RESULT_RESPONSE)) {
                TurSNServer server = new TurSNServer(
                        URI.create("http://localhost:" + http.port()), "MySite", Locale.US);
                TurSNQuery query = new TurSNQuery();
                query.setQuery("enterprise search platform");
                query.setRows(20);
                query.setPageNumber(1);
                query.setSortField("title", TurSNQuery.Order.asc);
                query.addFilterQuery("type:Page", "status:published");
                query.setBetweenDates("created", new Date(0L), new Date(86_400_000L));

                server.query(query);

                Map<String, List<String>> params = parseQueryString(http.getLastSearchRequest().uri());
                assertThat(params.get("q")).containsExactly("enterprise search platform");
                assertThat(params.get("rows")).containsExactly("20");
                assertThat(params.get("p")).containsExactly("1");
                assertThat(params.get("sort")).containsExactly("title asc");
                assertThat(params.get("fq[]")).hasSize(3); // 2 filter queries + 1 between dates
                assertThat(params.get("_setlocale")).containsExactly("en-US");
            }
        }

        @Test
        void shouldExecuteSearchWithGroupAndFacetResponse() throws Exception {
            try (SearchHttpServer http = SearchHttpServer.start(MULTI_FACET_RESPONSE)) {
                TurSNServer server = new TurSNServer(
                        URI.create("http://localhost:" + http.port()), "MySite", Locale.US);
                TurSNQuery query = new TurSNQuery();
                query.setQuery("foo");
                query.addFilterQuery("type:Page");

                QueryTurSNResponse response = server.query(query);

                // Verify full response mapping
                assertThat(response.getResults()).isNotNull();
                assertThat(response.getResults()).hasSize(1);
                assertThat(response.getFacetFields().getFields()).hasSize(2);
                assertThat(response.getPagination()).isNotNull();
                assertThat(response.getDidYouMean().isCorrectedText()).isFalse();
            }
        }

        @Test
        void shouldIterateResultDocuments() throws Exception {
            try (SearchHttpServer http = SearchHttpServer.start(MULTI_RESULT_PAGINATED_RESPONSE)) {
                TurSNServer server = new TurSNServer(
                        URI.create("http://localhost:" + http.port()), "MySite", Locale.US);
                TurSNQuery query = new TurSNQuery();
                query.setQuery("foo");

                QueryTurSNResponse response = server.query(query);

                List<String> ids = new ArrayList<>();
                for (TurSNDocument doc : response.getResults()) {
                    ids.add((String) doc.getFieldValue("id"));
                }
                assertThat(ids).containsExactly("doc-11", "doc-12");
            }
        }

        @Test
        void shouldHandleSearchWithSpellCheckAndSpotlight() throws Exception {
            try (SearchHttpServer http = SearchHttpServer.start(SPELLCHECK_RESPONSE)) {
                TurSNServer server = new TurSNServer(
                        URI.create("http://localhost:" + http.port()), "MySite", Locale.US);
                TurSNQuery query = new TurSNQuery();
                query.setQuery("tset");

                QueryTurSNResponse response = server.query(query);

                // Verify spell check
                assertThat(response.getDidYouMean().isCorrectedText()).isTrue();
                assertThat(response.getDidYouMean().getOriginal().getText()).isEqualTo("tset");
                assertThat(response.getDidYouMean().getCorrected().getText()).isEqualTo("test");

                // Verify spotlight
                assertThat(response.getSpotlightDocuments()).hasSize(1);
                assertThat(response.getSpotlightDocuments().getFirst().getTitle()).isEqualTo("Featured Result");

                // Verify regular results still present
                assertThat(response.getResults()).hasSize(1);
            }
        }
    }

    // ──────────────────────────────────────────────────────────────────────
    // 15. Internal Request Building Tests (via reflection)
    // ──────────────────────────────────────────────────────────────────────

    @Nested
    class InternalRequestBuildingTests {

        @Test
        void shouldBuildCorrectUrlForSiteName() {
            TurSNServer server = new TurSNServer(
                    URI.create("http://myhost:2700"), "Enterprise-Portal", Locale.US);

            assertThat(server.getSnServer()).isEqualTo("http://myhost:2700/api/sn/Enterprise-Portal");
        }

        @Test
        void shouldBuildCorrectUrlWithSiteNameContainingSpaces() {
            TurSNServer server = new TurSNServer(
                    URI.create("http://myhost:2700"), "My Site", Locale.US);

            assertThat(server.getSnServer()).isEqualTo("http://myhost:2700/api/sn/My Site");
        }

        @Test
        void shouldUseDefaultProviderName() {
            TurSNServer server = new TurSNServer(
                    URI.create("http://localhost:2700"), "Site", Locale.US);

            assertThat(server.getProviderName()).isEqualTo("turing-java-sdk");
        }

        @Test
        void shouldAllowCustomProviderName() {
            TurSNServer server = new TurSNServer(
                    URI.create("http://localhost:2700"), "Site", Locale.US);
            server.setProviderName("my-custom-app");

            assertThat(server.getProviderName()).isEqualTo("my-custom-app");
        }

        @Test
        void shouldPreparePostRequestWithAuthHeaders() throws Exception {
            TurSNServer server = new TurSNServer(
                    URI.create("http://localhost:2700"), "MySite", Locale.US,
                    new TurUsernamePasswordCredentials("user", "pass"), "user");
            TurSNQuery query = new TurSNQuery();
            query.setQuery("test");
            server.setTurSNQuery(query);

            HttpUriRequestBase request = invokePrepareQueryRequest(server);

            assertThat(request).isInstanceOf(HttpPost.class);
            assertThat(request.getHeader("Accept").getValue()).isEqualTo("application/json");
            assertThat(request.getHeader("Content-Type").getValue()).isEqualTo("application/json");
            assertThat(request.getHeader("Authorization")).isNotNull();
            assertThat(request.getHeader("Authorization").getValue()).startsWith("Basic ");
        }

        @Test
        void shouldPrepareGetRequestWithoutAuthHeaders() throws Exception {
            TurSNServer server = new TurSNServer(
                    URI.create("http://localhost:2700"), "MySite", Locale.US);
            TurSNQuery query = new TurSNQuery();
            query.setQuery("test");
            server.setTurSNQuery(query);

            HttpUriRequestBase request = invokePrepareQueryRequest(server);

            assertThat(request).isInstanceOf(HttpGet.class);
            assertThat(request.getHeader("Authorization")).isNull();
        }

        @Test
        void shouldBuildUrlWithAllQueryParameters() throws Exception {
            TurSNServer server = new TurSNServer(
                    URI.create("http://localhost:2700"), "MySite", Locale.US);
            TurSNQuery query = new TurSNQuery();
            query.setQuery("hello world");
            query.setRows(15);
            query.setGroupBy("category");
            query.setSortField("date", TurSNQuery.Order.desc);
            query.addFilterQuery("status:active");
            query.setPageNumber(2);
            server.setTurSNQuery(query);

            HttpUriRequestBase request = invokePrepareQueryRequest(server);
            assertThat(request.getUri()).isNotNull();
            String uri = Objects.requireNonNull(request.getUri()).toString();

            assertTrue(uri.contains("q=hello+world") || uri.contains("q=hello%20world"),
                    "query parameter 'q' should contain encoded 'hello world'");
            assertTrue(uri.contains("rows=15"), "should contain rows=15");
            assertTrue(uri.contains("group=category"), "should contain group=category");
            assertTrue(uri.contains("sort=date+desc") || uri.contains("sort=date%20desc"),
                    "sort parameter should contain 'date desc' encoded");
            assertTrue(uri.contains("p=2"), "should contain p=2");
        }

        private HttpUriRequestBase invokePrepareQueryRequest(TurSNServer server) throws Exception {
            Method method = TurSNServer.class.getDeclaredMethod("prepareQueryRequest");
            method.setAccessible(true);
            return (HttpUriRequestBase) method.invoke(server);
        }
    }

    // ──────────────────────────────────────────────────────────────────────
    // 16. Edge Cases and Error Handling
    // ──────────────────────────────────────────────────────────────────────

    @Nested
    class EdgeCaseTests {

        @Test
        void shouldHandleServerReturning500() throws Exception {
            HttpServer errorServer = HttpServer.create(new InetSocketAddress(0), 0);
            errorServer.createContext("/api/sn/", exchange -> {
                exchange.sendResponseHeaders(500, 0);
                exchange.getResponseBody().close();
            });
            errorServer.start();

            try {
                TurSNServer server = new TurSNServer(
                        URI.create("http://localhost:" + errorServer.getAddress().getPort()),
                        "MySite", Locale.US);
                TurSNQuery query = new TurSNQuery();
                query.setQuery("test");

                QueryTurSNResponse response = server.query(query);

                // Should return empty response, not throw
                assertThat(response).isNotNull();
                assertThat(response.getResults()).isNull();
            } finally {
                errorServer.stop(0);
            }
        }

        @Test
        void shouldHandleServerReturning404() throws Exception {
            HttpServer notFoundServer = HttpServer.create(new InetSocketAddress(0), 0);
            notFoundServer.createContext("/api/sn/", exchange -> {
                exchange.sendResponseHeaders(404, 0);
                exchange.getResponseBody().close();
            });
            notFoundServer.start();

            try {
                TurSNServer server = new TurSNServer(
                        URI.create("http://localhost:" + notFoundServer.getAddress().getPort()),
                        "MySite", Locale.US);
                TurSNQuery query = new TurSNQuery();
                query.setQuery("test");

                QueryTurSNResponse response = server.query(query);

                assertThat(response).isNotNull();
                assertThat(response.getResults()).isNull();
            } finally {
                notFoundServer.stop(0);
            }
        }

        @Test
        void shouldHandleNullQueryInTurSNQuery() {
            TurSNQuery query = new TurSNQuery();
            query.setQuery(null);

            assertThat(query.getQuery()).isNull();
        }

        @Test
        void shouldHandleNegativeRows() throws Exception {
            try (SearchHttpServer http = SearchHttpServer.start(EMPTY_SEARCH_RESPONSE)) {
                TurSNServer server = new TurSNServer(
                        URI.create("http://localhost:" + http.port()), "MySite", Locale.US);
                TurSNQuery query = new TurSNQuery();
                query.setQuery("test");
                query.setRows(-1);

                server.query(query);

                Map<String, List<String>> params = parseQueryString(http.getLastSearchRequest().uri());
                // Negative rows should not be sent (same as 0)
                assertThat(params.get("rows")).isNull();
            }
        }

        @Test
        void shouldHandleNegativePageNumber() throws Exception {
            try (SearchHttpServer http = SearchHttpServer.start(EMPTY_SEARCH_RESPONSE)) {
                TurSNServer server = new TurSNServer(
                        URI.create("http://localhost:" + http.port()), "MySite", Locale.US);
                TurSNQuery query = new TurSNQuery();
                query.setQuery("test");
                query.setPageNumber(-5);

                server.query(query);

                Map<String, List<String>> params = parseQueryString(http.getLastSearchRequest().uri());
                // Negative page should default to 1
                assertThat(params.get("p")).containsExactly("1");
            }
        }

        @Test
        void shouldHandleVeryLargePageNumber() throws Exception {
            try (SearchHttpServer http = SearchHttpServer.start(EMPTY_SEARCH_RESPONSE)) {
                TurSNServer server = new TurSNServer(
                        URI.create("http://localhost:" + http.port()), "MySite", Locale.US);
                TurSNQuery query = new TurSNQuery();
                query.setQuery("test");
                query.setPageNumber(Integer.MAX_VALUE);

                server.query(query);

                Map<String, List<String>> params = parseQueryString(http.getLastSearchRequest().uri());
                assertThat(params.get("p")).containsExactly(String.valueOf(Integer.MAX_VALUE));
            }
        }

        @Test
        void shouldHandleVeryLargeRowsValue() throws Exception {
            try (SearchHttpServer http = SearchHttpServer.start(EMPTY_SEARCH_RESPONSE)) {
                TurSNServer server = new TurSNServer(
                        URI.create("http://localhost:" + http.port()), "MySite", Locale.US);
                TurSNQuery query = new TurSNQuery();
                query.setQuery("test");
                query.setRows(10000);

                server.query(query);

                Map<String, List<String>> params = parseQueryString(http.getLastSearchRequest().uri());
                assertThat(params.get("rows")).containsExactly("10000");
            }
        }

        /**
         * BUG: The REST API supports the 'nfpr' parameter to disable
         * auto-correction, but TurSNQuery has no disableAutoComplete
         * or nfpr field, so there's no way to disable spell-check
         * correction from the SDK.
         */
        @Test
        void shouldNotSupportDisableAutoCorrection() {
            TurSNQuery query = new TurSNQuery();
            // No method to set disableAutoComplete or nfpr
            // BUG: SDK is missing nfpr parameter support
            assertThat(query.getQuery()).isNull(); // just to verify object creation
        }

        @Test
        void shouldHandleFilterQueryWithSpecialCharacters() throws Exception {
            try (SearchHttpServer http = SearchHttpServer.start(EMPTY_SEARCH_RESPONSE)) {
                TurSNServer server = new TurSNServer(
                        URI.create("http://localhost:" + http.port()), "MySite", Locale.US);
                TurSNQuery query = new TurSNQuery();
                query.setQuery("test");
                query.addFilterQuery("title:hello world & goodbye");

                server.query(query);

                Map<String, List<String>> params = parseQueryString(http.getLastSearchRequest().uri());
                assertThat(params.get("fq[]")).containsExactly("title:hello world & goodbye");
            }
        }

        @Test
        void shouldHandleFilterQueryWithSolrRangeSyntax() throws Exception {
            try (SearchHttpServer http = SearchHttpServer.start(EMPTY_SEARCH_RESPONSE)) {
                TurSNServer server = new TurSNServer(
                        URI.create("http://localhost:" + http.port()), "MySite", Locale.US);
                TurSNQuery query = new TurSNQuery();
                query.setQuery("test");
                query.addFilterQuery("price:[10 TO 100]");
                query.addFilterQuery("date:[* TO 2025-12-31T23:59:59Z]");
                query.addFilterQuery("date:[2024-01-01T00:00:00Z TO *]");

                server.query(query);

                Map<String, List<String>> params = parseQueryString(http.getLastSearchRequest().uri());
                assertThat(params.get("fq[]")).hasSize(3);
                assertThat(params.get("fq[]")).containsExactly(
                        "price:[10 TO 100]",
                        "date:[* TO 2025-12-31T23:59:59Z]",
                        "date:[2024-01-01T00:00:00Z TO *]");
            }
        }
    }

    // ──────────────────────────────────────────────────────────────────────
    // 17. Constructor Variants Tests
    // ──────────────────────────────────────────────────────────────────────

    @Nested
    class ConstructorTests {

        @Test
        void shouldCreateWithUriAndSiteName() {
            TurSNServer server = new TurSNServer(URI.create("http://localhost:2700"), "Portal");
            assertThat(server.getSiteName()).isEqualTo("Portal");
            assertThat(server.getLocale()).isEqualTo(Locale.US);
            assertThat(server.getCredentials()).isNull();
            assertThat(server.getApiKey()).isNull();
        }

        @Test
        void shouldCreateWithUriSiteNameAndLocale() {
            TurSNServer server = new TurSNServer(
                    URI.create("http://localhost:2700"), "Portal", Locale.FRANCE);
            assertThat(server.getLocale()).isEqualTo(Locale.FRANCE);
        }

        @Test
        void shouldCreateWithApiKeyAndDefaultSite() {
            TurSNServer server = new TurSNServer(
                    URI.create("http://localhost:2700"), new TurApiKeyCredentials("key123"));
            assertThat(server.getSiteName()).isEqualTo("Sample");
            assertThat(server.getApiKey()).isEqualTo("key123");
        }

        @Test
        void shouldCreateWithSiteAndApiKey() {
            TurSNServer server = new TurSNServer(
                    URI.create("http://localhost:2700"), "Portal",
                    new TurApiKeyCredentials("key456"));
            assertThat(server.getSiteName()).isEqualTo("Portal");
            assertThat(server.getApiKey()).isEqualTo("key456");
            assertThat(server.getLocale()).isEqualTo(Locale.US);
        }

        @Test
        void shouldCreateWithUsernamePasswordAndSetUserId() {
            TurUsernamePasswordCredentials creds = new TurUsernamePasswordCredentials("admin", "pass");
            TurSNServer server = new TurSNServer(
                    URI.create("http://localhost:2700"), "Portal", Locale.US, creds);
            assertThat(server.getCredentials()).isSameAs(creds);
            // userId should be the username from credentials
            assertThat(server.getTurSNSitePostParams().getUserId()).isEqualTo("admin");
        }

        @Test
        void shouldCreateWithNullCredentials() {
            TurSNServer server = new TurSNServer(
                    URI.create("http://localhost:2700"), "Portal", Locale.US,
                    (TurUsernamePasswordCredentials) null);
            assertThat(server.getCredentials()).isNull();
            assertThat(server.getTurSNSitePostParams().getUserId()).isNull();
        }
    }
}
