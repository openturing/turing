package com.viglet.turing.api.logging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * Tests for TurLoggingAPI.
 *
 * @author Alexandre Oliveira
 * @since 2026.1.14
 */
class TurLoggingAPITest {

    private MockMvc mockMvc;
    private TurLoggingAPI turLoggingAPI;

    @BeforeEach
    void setUp() {
        // engine=none so it does not connect to Mongo
        turLoggingAPI = new TurLoggingAPI("none", false, "mongodb://localhost:27017", "turingLog", "server",
                "indexing", "aem");
        mockMvc = MockMvcBuilders.standaloneSetup(turLoggingAPI).build();
    }

    private static final String EMPTY_PAGE_RESPONSE =
            "{\"content\":[],\"page\":0,\"pageSize\":100,\"totalElements\":0,\"totalPages\":0}";

    @Test
    void testServerLogging() throws Exception {
        mockMvc.perform(get("/api/logging"))
                .andExpect(status().isOk())
                .andExpect(content().json(EMPTY_PAGE_RESPONSE));
    }

    @Test
    void testIndexingLogging() throws Exception {
        mockMvc.perform(get("/api/logging/indexing"))
                .andExpect(status().isOk())
                .andExpect(content().json(EMPTY_PAGE_RESPONSE));
    }

    @Test
    void testAemLogging() throws Exception {
        mockMvc.perform(get("/api/logging/aem"))
                .andExpect(status().isOk())
                .andExpect(content().json(EMPTY_PAGE_RESPONSE));
    }

    @Test
    void testServerLoggingWithParams() throws Exception {
        mockMvc.perform(get("/api/logging")
                .param("page", "1")
                .param("pageSize", "50")
                .param("level", "ERROR")
                .param("dateFrom", "2026-01-01")
                .param("dateTo", "2026-12-31")
                .param("search", "error message")
                .param("sort", "desc"))
                .andExpect(status().isOk())
                .andExpect(content().json("{\"content\":[],\"page\":1,\"pageSize\":50,\"totalElements\":0,\"totalPages\":0}"));
    }

    @Test
    void testIndexingLoggingWithParams() throws Exception {
        mockMvc.perform(get("/api/logging/indexing")
                .param("page", "0")
                .param("pageSize", "25")
                .param("dateFrom", "2026-03-01")
                .param("dateTo", "2026-03-31")
                .param("status", "SUCCESS")
                .param("contentId", "doc-123")
                .param("resultStatus", "INDEXED")
                .param("url", "http://example.com")
                .param("sort", "desc"))
                .andExpect(status().isOk())
                .andExpect(content().json("{\"content\":[],\"page\":0,\"pageSize\":25,\"totalElements\":0,\"totalPages\":0}"));
    }

    @Test
    void testAemLoggingWithParams() throws Exception {
        mockMvc.perform(get("/api/logging/aem")
                .param("page", "2")
                .param("pageSize", "10")
                .param("level", "WARN")
                .param("search", "AEM")
                .param("sort", "asc"))
                .andExpect(status().isOk())
                .andExpect(content().json("{\"content\":[],\"page\":2,\"pageSize\":10,\"totalElements\":0,\"totalPages\":0}"));
    }

    @Test
    void testServerLoggingDefaultParams() throws Exception {
        mockMvc.perform(get("/api/logging"))
                .andExpect(status().isOk())
                .andExpect(content().json("{\"page\":0,\"pageSize\":100}"));
    }

    @Test
    void testParseDateHelperWithValidDate() throws Exception {
        java.lang.reflect.Method method = TurLoggingAPI.class.getDeclaredMethod("parseDate", String.class, boolean.class);
        method.setAccessible(true);

        java.util.Date result = (java.util.Date) method.invoke(turLoggingAPI, "2026-03-15", false);
        assertThat(result).isNotNull();
    }

    @Test
    void testParseDateHelperWithEndOfDay() throws Exception {
        java.lang.reflect.Method method = TurLoggingAPI.class.getDeclaredMethod("parseDate", String.class, boolean.class);
        method.setAccessible(true);

        java.util.Date startOfDay = (java.util.Date) method.invoke(turLoggingAPI, "2026-03-15", false);
        java.util.Date endOfDay = (java.util.Date) method.invoke(turLoggingAPI, "2026-03-15", true);

        assertThat(endOfDay).isAfter(startOfDay);
    }

    @Test
    void testParseDateHelperWithInvalidDate() throws Exception {
        java.lang.reflect.Method method = TurLoggingAPI.class.getDeclaredMethod("parseDate", String.class, boolean.class);
        method.setAccessible(true);

        java.util.Date result = (java.util.Date) method.invoke(turLoggingAPI, "invalid-date", false);
        assertThat(result).isNull();
    }

    @Test
    void testConstants() {
        assertThat(TurLoggingAPI.DATE).isEqualTo("date");
        assertThat(TurLoggingAPI.CONTENT).isEqualTo("content");
        assertThat(TurLoggingAPI.TOTAL_ELEMENTS).isEqualTo("totalElements");
        assertThat(TurLoggingAPI.TOTAL_PAGES).isEqualTo("totalPages");
        assertThat(TurLoggingAPI.PAGE).isEqualTo("page");
        assertThat(TurLoggingAPI.PAGE_SIZE).isEqualTo("pageSize");
    }

    // --- Additional coverage: private filter methods ---

    @Test
    void addLevelFilterShouldIgnoreNullAndBlank() throws Exception {
        java.lang.reflect.Method method = TurLoggingAPI.class.getDeclaredMethod(
                "addLevelFilter", java.util.List.class, String.class);
        method.setAccessible(true);

        java.util.List<org.bson.conversions.Bson> filters = new java.util.ArrayList<>();
        method.invoke(turLoggingAPI, filters, null);
        assertThat(filters).isEmpty();

        method.invoke(turLoggingAPI, filters, "");
        assertThat(filters).isEmpty();

        method.invoke(turLoggingAPI, filters, "   ");
        assertThat(filters).isEmpty();
    }

    @Test
    void addLevelFilterShouldAddFilterForValidLevel() throws Exception {
        java.lang.reflect.Method method = TurLoggingAPI.class.getDeclaredMethod(
                "addLevelFilter", java.util.List.class, String.class);
        method.setAccessible(true);

        java.util.List<org.bson.conversions.Bson> filters = new java.util.ArrayList<>();
        method.invoke(turLoggingAPI, filters, "error");
        assertThat(filters).hasSize(1);
    }

    @Test
    void addTextSearchFilterShouldIgnoreNullAndBlank() throws Exception {
        java.lang.reflect.Method method = TurLoggingAPI.class.getDeclaredMethod(
                "addTextSearchFilter", java.util.List.class, String.class, String[].class);
        method.setAccessible(true);

        java.util.List<org.bson.conversions.Bson> filters = new java.util.ArrayList<>();
        method.invoke(turLoggingAPI, filters, null, new String[]{"field1"});
        assertThat(filters).isEmpty();

        method.invoke(turLoggingAPI, filters, "  ", new String[]{"field1"});
        assertThat(filters).isEmpty();
    }

    @Test
    void addTextSearchFilterShouldAddFilterForValidSearch() throws Exception {
        java.lang.reflect.Method method = TurLoggingAPI.class.getDeclaredMethod(
                "addTextSearchFilter", java.util.List.class, String.class, String[].class);
        method.setAccessible(true);

        java.util.List<org.bson.conversions.Bson> filters = new java.util.ArrayList<>();
        method.invoke(turLoggingAPI, filters, "error msg", new String[]{"message", "stackTrace"});
        assertThat(filters).hasSize(1);
    }

    @Test
    void addExactFilterShouldIgnoreNullAndBlank() throws Exception {
        java.lang.reflect.Method method = TurLoggingAPI.class.getDeclaredMethod(
                "addExactFilter", java.util.List.class, String.class, String.class);
        method.setAccessible(true);

        java.util.List<org.bson.conversions.Bson> filters = new java.util.ArrayList<>();
        method.invoke(turLoggingAPI, filters, "status", null);
        assertThat(filters).isEmpty();

        method.invoke(turLoggingAPI, filters, "status", "");
        assertThat(filters).isEmpty();

        method.invoke(turLoggingAPI, filters, "status", "   ");
        assertThat(filters).isEmpty();
    }

    @Test
    void addExactFilterShouldAddFilterForValidValue() throws Exception {
        java.lang.reflect.Method method = TurLoggingAPI.class.getDeclaredMethod(
                "addExactFilter", java.util.List.class, String.class, String.class);
        method.setAccessible(true);

        java.util.List<org.bson.conversions.Bson> filters = new java.util.ArrayList<>();
        method.invoke(turLoggingAPI, filters, "status", "SUCCESS");
        assertThat(filters).hasSize(1);
    }

    @Test
    void addRegexFilterShouldIgnoreNullAndBlank() throws Exception {
        java.lang.reflect.Method method = TurLoggingAPI.class.getDeclaredMethod(
                "addRegexFilter", java.util.List.class, String.class, String.class);
        method.setAccessible(true);

        java.util.List<org.bson.conversions.Bson> filters = new java.util.ArrayList<>();
        method.invoke(turLoggingAPI, filters, "contentId", null);
        assertThat(filters).isEmpty();

        method.invoke(turLoggingAPI, filters, "contentId", "");
        assertThat(filters).isEmpty();

        method.invoke(turLoggingAPI, filters, "contentId", "  ");
        assertThat(filters).isEmpty();
    }

    @Test
    void addRegexFilterShouldAddFilterForValidValue() throws Exception {
        java.lang.reflect.Method method = TurLoggingAPI.class.getDeclaredMethod(
                "addRegexFilter", java.util.List.class, String.class, String.class);
        method.setAccessible(true);

        java.util.List<org.bson.conversions.Bson> filters = new java.util.ArrayList<>();
        method.invoke(turLoggingAPI, filters, "contentId", "doc-.*");
        assertThat(filters).hasSize(1);
    }

    @Test
    void addDateRangeFilterShouldIgnoreNullAndBlank() throws Exception {
        java.lang.reflect.Method method = TurLoggingAPI.class.getDeclaredMethod(
                "addDateRangeFilter", java.util.List.class, String.class, String.class);
        method.setAccessible(true);

        java.util.List<org.bson.conversions.Bson> filters = new java.util.ArrayList<>();
        method.invoke(turLoggingAPI, filters, null, null);
        assertThat(filters).isEmpty();

        method.invoke(turLoggingAPI, filters, "", "");
        assertThat(filters).isEmpty();
    }

    @Test
    void addDateRangeFilterShouldAddFiltersForValidDates() throws Exception {
        java.lang.reflect.Method method = TurLoggingAPI.class.getDeclaredMethod(
                "addDateRangeFilter", java.util.List.class, String.class, String.class);
        method.setAccessible(true);

        java.util.List<org.bson.conversions.Bson> filters = new java.util.ArrayList<>();
        method.invoke(turLoggingAPI, filters, "2026-01-01", "2026-12-31");
        assertThat(filters).hasSize(2);
    }

    @Test
    void addDateRangeFilterShouldHandleInvalidDates() throws Exception {
        java.lang.reflect.Method method = TurLoggingAPI.class.getDeclaredMethod(
                "addDateRangeFilter", java.util.List.class, String.class, String.class);
        method.setAccessible(true);

        java.util.List<org.bson.conversions.Bson> filters = new java.util.ArrayList<>();
        method.invoke(turLoggingAPI, filters, "bad-date", "also-bad");
        assertThat(filters).isEmpty();
    }

    @Test
    void addDateRangeFilterShouldAddOnlyFromDate() throws Exception {
        java.lang.reflect.Method method = TurLoggingAPI.class.getDeclaredMethod(
                "addDateRangeFilter", java.util.List.class, String.class, String.class);
        method.setAccessible(true);

        java.util.List<org.bson.conversions.Bson> filters = new java.util.ArrayList<>();
        method.invoke(turLoggingAPI, filters, "2026-06-15", null);
        assertThat(filters).hasSize(1);
    }

    @Test
    void addDateRangeFilterShouldAddOnlyToDate() throws Exception {
        java.lang.reflect.Method method = TurLoggingAPI.class.getDeclaredMethod(
                "addDateRangeFilter", java.util.List.class, String.class, String.class);
        method.setAccessible(true);

        java.util.List<org.bson.conversions.Bson> filters = new java.util.ArrayList<>();
        method.invoke(turLoggingAPI, filters, null, "2026-06-15");
        assertThat(filters).hasSize(1);
    }

    // --- Enabled API with connection failure exercising full flow ---

    @ParameterizedTest(name = "{0} returns default on connection failure")
    @ValueSource(strings = {"/api/logging", "/api/logging/indexing", "/api/logging/aem"})
    void loggingEnabledShouldReturnDefaultOnConnectionFailure(String path) throws Exception {
        TurLoggingAPI enabledApi = new TurLoggingAPI("mongodb", true, "mongodb://invalid-host:99999",
                "turingLog", "server", "indexing", "aem");
        MockMvc enabledMockMvc = MockMvcBuilders.standaloneSetup(enabledApi).build();

        enabledMockMvc.perform(get(path))
                .andExpect(status().isOk())
                .andExpect(content().json("{\"page\":0,\"pageSize\":100,\"totalElements\":0,\"totalPages\":0}"));
    }

    @Test
    void serverLoggingEnabledWithDescSortShouldReturnDefault() throws Exception {
        TurLoggingAPI enabledApi = new TurLoggingAPI("mongodb", true, "mongodb://invalid-host:99999",
                "turingLog", "server", "indexing", "aem");
        MockMvc enabledMockMvc = MockMvcBuilders.standaloneSetup(enabledApi).build();

        enabledMockMvc.perform(get("/api/logging").param("sort", "desc"))
                .andExpect(status().isOk())
                .andExpect(content().json("{\"page\":0,\"pageSize\":100,\"totalElements\":0,\"totalPages\":0}"));
    }

    @Test
    void serverLoggingEnabledWithAllParamsShouldReturnDefault() throws Exception {
        TurLoggingAPI enabledApi = new TurLoggingAPI("mongodb", true, "mongodb://invalid-host:99999",
                "turingLog", "server", "indexing", "aem");
        MockMvc enabledMockMvc = MockMvcBuilders.standaloneSetup(enabledApi).build();

        enabledMockMvc.perform(get("/api/logging")
                        .param("page", "2")
                        .param("pageSize", "50")
                        .param("level", "ERROR")
                        .param("dateFrom", "2026-01-01")
                        .param("dateTo", "2026-12-31")
                        .param("search", "error")
                        .param("sort", "desc"))
                .andExpect(status().isOk())
                .andExpect(content().json("{\"page\":2,\"pageSize\":50,\"totalElements\":0,\"totalPages\":0}"));
    }

    @Test
    void indexingLoggingEnabledWithAllParamsShouldReturnDefault() throws Exception {
        TurLoggingAPI enabledApi = new TurLoggingAPI("mongodb", true, "mongodb://invalid-host:99999",
                "turingLog", "server", "indexing", "aem");
        MockMvc enabledMockMvc = MockMvcBuilders.standaloneSetup(enabledApi).build();

        enabledMockMvc.perform(get("/api/logging/indexing")
                        .param("dateFrom", "2026-03-01")
                        .param("dateTo", "2026-03-31")
                        .param("status", "COMPLETE")
                        .param("contentId", "doc-1")
                        .param("resultStatus", "SUCCESS")
                        .param("url", "http://example.com")
                        .param("sort", "desc"))
                .andExpect(status().isOk())
                .andExpect(content().json("{\"page\":0,\"pageSize\":100,\"totalElements\":0,\"totalPages\":0}"));
    }
}
