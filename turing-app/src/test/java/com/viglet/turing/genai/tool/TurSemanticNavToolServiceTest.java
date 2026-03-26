/**
 * Tests for TurSemanticNavToolService.
 *
 * @author Alexandre Oliveira
 * @since 2026.1.14
 */
package com.viglet.turing.genai.tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.when;

import org.mockito.MockedConstruction;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Sort;

import com.viglet.turing.client.sn.HttpTurSNServer;
import com.viglet.turing.client.sn.TurSNDocument;
import com.viglet.turing.client.sn.TurSNDocumentList;
import com.viglet.turing.client.sn.facet.TurSNFacetFieldList;
import com.viglet.turing.client.sn.response.QueryTurSNResponse;
import com.viglet.turing.commons.se.field.TurSEFieldType;
import com.viglet.turing.commons.sn.bean.TurSNSiteSearchBean;
import com.viglet.turing.commons.sn.bean.TurSNSiteSearchDocumentBean;
import com.viglet.turing.commons.sn.bean.TurSNSiteSearchFacetBean;
import com.viglet.turing.commons.sn.bean.TurSNSiteSearchFacetItemBean;
import com.viglet.turing.commons.sn.bean.TurSNSiteSearchFacetLabelBean;
import com.viglet.turing.commons.sn.bean.TurSNSiteSearchQueryContextBean;
import com.viglet.turing.commons.sn.bean.TurSNSiteSearchResultsBean;
import com.viglet.turing.commons.sn.bean.TurSNSiteSearchWidgetBean;
import com.viglet.turing.persistence.model.sn.TurSNSite;
import com.viglet.turing.persistence.model.sn.field.TurSNSiteFieldExt;
import com.viglet.turing.persistence.model.sn.locale.TurSNSiteLocale;
import com.viglet.turing.persistence.repository.sn.TurSNSiteRepository;
import com.viglet.turing.persistence.repository.sn.field.TurSNSiteFieldExtRepository;
import com.viglet.turing.persistence.repository.sn.locale.TurSNSiteLocaleRepository;

@ExtendWith(MockitoExtension.class)
class TurSemanticNavToolServiceTest {

    @Mock
    private TurSNSiteRepository turSNSiteRepository;
    @Mock
    private TurSNSiteLocaleRepository turSNSiteLocaleRepository;
    @Mock
    private TurSNSiteFieldExtRepository turSNSiteFieldExtRepository;

    private TurSemanticNavToolService service;

    @BeforeEach
    void setUp() throws Exception {
        service = new TurSemanticNavToolService(
                turSNSiteRepository, turSNSiteLocaleRepository, turSNSiteFieldExtRepository);
        // Set serverPort via reflection (default 0 will cause connection errors for HTTP-based methods)
        Field serverPortField = TurSemanticNavToolService.class.getDeclaredField("serverPort");
        serverPortField.setAccessible(true);
        serverPortField.setInt(service, 0);
    }

    // ======================== listSites ========================

    @Nested
    class ListSitesTests {

        @Test
        void shouldReturnNoSitesMessageWhenEmpty() {
            when(turSNSiteRepository.findAll(any(Sort.class))).thenReturn(Collections.emptyList());

            String result = service.listSites("");

            assertThat(result).isEqualTo("No Semantic Navigation sites found.");
        }

        @Test
        void shouldReturnNoSitesMessageWhenNullFilter() {
            when(turSNSiteRepository.findAll(any(Sort.class))).thenReturn(Collections.emptyList());

            String result = service.listSites(null);

            assertThat(result).isEqualTo("No Semantic Navigation sites found.");
        }

        @Test
        void shouldListAllSitesWithNullFilter() {
            TurSNSite site = createSite("Site1", "desc1");
            TurSNSiteLocale locale = createLocale(Locale.ENGLISH);

            when(turSNSiteRepository.findAll(any(Sort.class))).thenReturn(List.of(site));
            when(turSNSiteLocaleRepository.findByTurSNSite(site)).thenReturn(List.of(locale));

            String result = service.listSites(null);

            assertThat(result)
                    .contains("site_name;description;locales")
                    .contains("Site1;desc1;en");
        }

        @Test
        void shouldListAllSitesWithBlankFilter() {
            TurSNSite site = createSite("Site1", "desc1");

            when(turSNSiteRepository.findAll(any(Sort.class))).thenReturn(List.of(site));
            when(turSNSiteLocaleRepository.findByTurSNSite(site)).thenReturn(Collections.emptyList());

            String result = service.listSites("   ");

            assertThat(result).contains("Site1;desc1;");
        }

        @Test
        void shouldListAllSitesWithEmptyFilter() {
            TurSNSite site1 = createSite("Enterprise", "Enterprise site");
            TurSNSite site2 = createSite("Portal", null);
            TurSNSiteLocale localeEn = createLocale(Locale.ENGLISH);

            when(turSNSiteRepository.findAll(any(Sort.class))).thenReturn(List.of(site1, site2));
            when(turSNSiteLocaleRepository.findByTurSNSite(site1)).thenReturn(List.of(localeEn));
            when(turSNSiteLocaleRepository.findByTurSNSite(site2)).thenReturn(Collections.emptyList());

            String result = service.listSites("");

            assertThat(result)
                    .contains("site_name;description;locales")
                    .contains("Enterprise;Enterprise site;en")
                    .contains("Portal;;");
        }

        @Test
        void shouldFilterSitesByNameCaseInsensitive() {
            TurSNSite site1 = createSite("Enterprise", "desc");
            TurSNSite site2 = createSite("Portal", "desc");

            when(turSNSiteRepository.findAll(any(Sort.class))).thenReturn(List.of(site1, site2));
            when(turSNSiteLocaleRepository.findByTurSNSite(site1)).thenReturn(Collections.emptyList());

            String result = service.listSites("ENTERPRISE");

            assertThat(result).contains("Enterprise").doesNotContain("Portal");
        }

        @Test
        void shouldReturnNoSitesWhenFilterMatchesNone() {
            TurSNSite site = createSite("Enterprise", "desc");

            when(turSNSiteRepository.findAll(any(Sort.class))).thenReturn(List.of(site));

            String result = service.listSites("nonexistent");

            assertThat(result).isEqualTo("No Semantic Navigation sites found.");
        }

        @Test
        void shouldListSitesWithMultipleLocales() {
            TurSNSite site = createSite("MultiLang", "desc");
            TurSNSiteLocale localeEn = createLocale(Locale.ENGLISH);
            TurSNSiteLocale localePt = createLocale(Locale.of("pt"));
            TurSNSiteLocale localeEs = createLocale(Locale.of("es"));

            when(turSNSiteRepository.findAll(any(Sort.class))).thenReturn(List.of(site));
            when(turSNSiteLocaleRepository.findByTurSNSite(site)).thenReturn(List.of(localeEn, localePt, localeEs));

            String result = service.listSites("");

            assertThat(result).contains("en, pt, es");
        }

        @Test
        void shouldHandleNullDescription() {
            TurSNSite site = createSite("SiteNull", null);

            when(turSNSiteRepository.findAll(any(Sort.class))).thenReturn(List.of(site));
            when(turSNSiteLocaleRepository.findByTurSNSite(site)).thenReturn(Collections.emptyList());

            String result = service.listSites(null);

            assertThat(result).contains("SiteNull;;");
        }

        @Test
        void shouldPartialMatchFilter() {
            TurSNSite site = createSite("MyEnterpriseSite", "desc");

            when(turSNSiteRepository.findAll(any(Sort.class))).thenReturn(List.of(site));
            when(turSNSiteLocaleRepository.findByTurSNSite(site)).thenReturn(Collections.emptyList());

            String result = service.listSites("enterprise");

            assertThat(result).contains("MyEnterpriseSite");
        }
    }

    // ======================== getSiteFields ========================

    @Nested
    class GetSiteFieldsTests {

        @Test
        void shouldReturnSiteNotFound() {
            when(turSNSiteRepository.findByNameIgnoreCase("Missing")).thenReturn(Optional.empty());

            String result = service.getSiteFields("Missing");

            assertThat(result).isEqualTo("Site not found: Missing");
        }

        @Test
        void shouldReturnNoFieldsMessage() {
            TurSNSite site = createSite("MySite", "desc");

            when(turSNSiteRepository.findByNameIgnoreCase("MySite")).thenReturn(Optional.of(site));
            when(turSNSiteFieldExtRepository.findByTurSNSiteAndEnabled(site, 1))
                    .thenReturn(Collections.emptyList());

            String result = service.getSiteFields("MySite");

            assertThat(result).isEqualTo("No enabled fields found for site: MySite");
        }

        @Test
        void shouldReturnFieldsList() {
            TurSNSite site = createSite("MySite", "desc");

            TurSNSiteFieldExt field1 = createField("title", TurSEFieldType.TEXT, 0, null);
            TurSNSiteFieldExt field2 = createField("category", TurSEFieldType.STRING, 1, "Category field");

            when(turSNSiteRepository.findByNameIgnoreCase("MySite")).thenReturn(Optional.of(site));
            when(turSNSiteFieldExtRepository.findByTurSNSiteAndEnabled(site, 1))
                    .thenReturn(List.of(field1, field2));

            String result = service.getSiteFields("MySite");

            assertThat(result)
                    .contains("field_name;type;facet;description")
                    .contains("title;TEXT;no;")
                    .contains("category;STRING;yes;Category field");
        }

        @Test
        void shouldReturnFieldsWithNullDescription() {
            TurSNSite site = createSite("TestSite", "desc");
            TurSNSiteFieldExt field = createField("content", TurSEFieldType.TEXT, 0, null);

            when(turSNSiteRepository.findByNameIgnoreCase("TestSite")).thenReturn(Optional.of(site));
            when(turSNSiteFieldExtRepository.findByTurSNSiteAndEnabled(site, 1))
                    .thenReturn(List.of(field));

            String result = service.getSiteFields("TestSite");

            assertThat(result).contains("content;TEXT;no;\n");
        }

        @Test
        void shouldReturnMultipleFieldTypes() {
            TurSNSite site = createSite("MySite", "desc");

            TurSNSiteFieldExt f1 = createField("intField", TurSEFieldType.INT, 0, "int desc");
            TurSNSiteFieldExt f2 = createField("dateField", TurSEFieldType.DATE, 1, "date desc");
            TurSNSiteFieldExt f3 = createField("boolField", TurSEFieldType.BOOL, 0, null);

            when(turSNSiteRepository.findByNameIgnoreCase("MySite")).thenReturn(Optional.of(site));
            when(turSNSiteFieldExtRepository.findByTurSNSiteAndEnabled(site, 1))
                    .thenReturn(List.of(f1, f2, f3));

            String result = service.getSiteFields("MySite");

            assertThat(result)
                    .contains("intField;INT;no;int desc")
                    .contains("dateField;DATE;yes;date desc")
                    .contains("boolField;BOOL;no;");
        }
    }

    // ======================== searchSite ========================

    @Nested
    class SearchSiteTests {

        @Test
        void shouldReturnErrorWhenServerUnavailable() {
            String result = service.searchSite("MySite", "en", "test", 10, 1, null);

            assertThat(result).satisfiesAnyOf(
                    r -> assertThat(r).contains("Error executing search"),
                    r -> assertThat(r).contains("No results found"));
        }

        @Test
        void shouldReturnErrorWithNullQuery() {
            String result = service.searchSite("MySite", "en", null, 10, 1, null);

            assertThat(result).satisfiesAnyOf(
                    r -> assertThat(r).contains("Error executing search"),
                    r -> assertThat(r).contains("No results found"));
        }

        @Test
        void shouldReturnErrorWithBlankQuery() {
            String result = service.searchSite("MySite", "en", "  ", 5, 1, null);

            assertThat(result).satisfiesAnyOf(
                    r -> assertThat(r).contains("Error executing search"),
                    r -> assertThat(r).contains("No results found"));
        }

        @Test
        void shouldReturnErrorWithNullRows() {
            String result = service.searchSite("MySite", "en", "test", null, 1, null);

            assertThat(result).satisfiesAnyOf(
                    r -> assertThat(r).contains("Error executing search"),
                    r -> assertThat(r).contains("No results found"));
        }

        @Test
        void shouldReturnErrorWithZeroRows() {
            String result = service.searchSite("MySite", "en", "test", 0, 1, null);

            assertThat(result).satisfiesAnyOf(
                    r -> assertThat(r).contains("Error executing search"),
                    r -> assertThat(r).contains("No results found"));
        }

        @Test
        void shouldReturnErrorWithNegativeRows() {
            String result = service.searchSite("MySite", "en", "test", -5, 1, null);

            assertThat(result).satisfiesAnyOf(
                    r -> assertThat(r).contains("Error executing search"),
                    r -> assertThat(r).contains("No results found"));
        }

        @Test
        void shouldReturnErrorWithNullPage() {
            String result = service.searchSite("MySite", "en", "test", 10, null, null);

            assertThat(result).satisfiesAnyOf(
                    r -> assertThat(r).contains("Error executing search"),
                    r -> assertThat(r).contains("No results found"));
        }

        @Test
        void shouldReturnErrorWithZeroPage() {
            String result = service.searchSite("MySite", "en", "test", 10, 0, null);

            assertThat(result).satisfiesAnyOf(
                    r -> assertThat(r).contains("Error executing search"),
                    r -> assertThat(r).contains("No results found"));
        }

        @Test
        void shouldReturnErrorWithFilterQueries() {
            String result = service.searchSite("MySite", "en", "test", 10, 1, "type:Article|category:News");

            assertThat(result).satisfiesAnyOf(
                    r -> assertThat(r).contains("Error executing search"),
                    r -> assertThat(r).contains("No results found"));
        }

        @Test
        void shouldReturnErrorWithBlankFilterQueries() {
            String result = service.searchSite("MySite", "en", "test", 10, 1, "   ");

            assertThat(result).satisfiesAnyOf(
                    r -> assertThat(r).contains("Error executing search"),
                    r -> assertThat(r).contains("No results found"));
        }
    }

    // ======================== getDocumentDetails ========================

    @Nested
    class GetDocumentDetailsTests {

        @Test
        void shouldReturnErrorWhenServerUnavailable() {
            String result = service.getDocumentDetails("MySite", "en", "doc123");

            assertThat(result).satisfiesAnyOf(
                    r -> assertThat(r).contains("Error retrieving document details"),
                    r -> assertThat(r).contains("Document not found"));
        }

        @Test
        void shouldReturnErrorWithNullDocId() {
            String result = service.getDocumentDetails("MySite", "en", null);

            assertThat(result).satisfiesAnyOf(
                    r -> assertThat(r).contains("Error retrieving document details"),
                    r -> assertThat(r).contains("Document not found"));
        }
    }

    // ======================== getSearchSuggestions ========================

    @Nested
    class GetSearchSuggestionsTests {

        @Test
        void shouldReturnErrorWhenServerUnavailable() {
            String result = service.getSearchSuggestions("MySite", "en", "tset");

            assertThat(result).satisfiesAnyOf(
                    r -> assertThat(r).contains("Error retrieving suggestions"),
                    r -> assertThat(r).contains("No suggestions"),
                    r -> assertThat(r).contains("No autocomplete suggestions found"));
        }

        @Test
        void shouldReturnErrorWithNullTerm() {
            String result = service.getSearchSuggestions("MySite", "en", null);

            assertThat(result).satisfiesAnyOf(
                    r -> assertThat(r).contains("Error retrieving suggestions"),
                    r -> assertThat(r).contains("No suggestions"),
                    r -> assertThat(r).contains("No autocomplete suggestions found"));
        }
    }

    // ======================== findSimilarDocuments ========================

    @Nested
    class FindSimilarDocumentsTests {

        @Test
        void shouldReturnErrorWhenServerUnavailable() {
            String result = service.findSimilarDocuments("MySite", "en", "doc123");

            assertThat(result).satisfiesAnyOf(
                    r -> assertThat(r).contains("Error finding similar documents"),
                    r -> assertThat(r).contains("No similar documents"),
                    r -> assertThat(r).contains("No response from search API"));
        }

        @Test
        void shouldReturnErrorWithNullDocId() {
            String result = service.findSimilarDocuments("MySite", "en", null);

            assertThat(result).satisfiesAnyOf(
                    r -> assertThat(r).contains("Error finding similar documents"),
                    r -> assertThat(r).contains("No similar documents"));
        }
    }

    // ======================== getAggregatedStats ========================

    @Nested
    class GetAggregatedStatsTests {

        @Test
        void shouldReturnErrorWhenServerUnavailable() {
            String result = service.getAggregatedStats("MySite", "en", "test", "category", null);

            assertThat(result).satisfiesAnyOf(
                    r -> assertThat(r).contains("Error calculating aggregated stats"),
                    r -> assertThat(r).contains("No facet data available"));
        }

        @Test
        void shouldReturnErrorWithNullQuery() {
            String result = service.getAggregatedStats("MySite", "en", null, "type", null);

            assertThat(result).satisfiesAnyOf(
                    r -> assertThat(r).contains("Error calculating aggregated stats"),
                    r -> assertThat(r).contains("No facet data available"));
        }

        @Test
        void shouldReturnErrorWithFilterQueries() {
            String result = service.getAggregatedStats("MySite", "en", "test", "category", "type:Article");

            assertThat(result).satisfiesAnyOf(
                    r -> assertThat(r).contains("Error calculating aggregated stats"),
                    r -> assertThat(r).contains("No facet data available"));
        }

        @Test
        void shouldReturnErrorWithBlankQuery() {
            String result = service.getAggregatedStats("MySite", "en", "  ", "type", "  ");

            assertThat(result).satisfiesAnyOf(
                    r -> assertThat(r).contains("Error calculating aggregated stats"),
                    r -> assertThat(r).contains("No facet data available"));
        }
    }

    // ======================== getDocumentHighlights ========================

    @Nested
    class GetDocumentHighlightsTests {

        @Test
        void shouldReturnErrorWhenServerUnavailable() {
            String result = service.getDocumentHighlights("MySite", "en", "doc123", "search terms");

            assertThat(result).satisfiesAnyOf(
                    r -> assertThat(r).contains("Error extracting highlights"),
                    r -> assertThat(r).contains("No response from search API"),
                    r -> assertThat(r).contains("Document not found"));
        }

        @Test
        void shouldReturnErrorWithNullTerms() {
            String result = service.getDocumentHighlights("MySite", "en", "doc123", null);

            assertThat(result).satisfiesAnyOf(
                    r -> assertThat(r).contains("Error extracting highlights"),
                    r -> assertThat(r).contains("No response from search API"));
        }
    }

    // ======================== compareItems ========================

    @Nested
    class CompareItemsTests {

        @Test
        void shouldRejectFewerThanTwoDocumentIds() {
            String result = service.compareItems("MySite", "en", "doc1", "title,description");

            assertThat(result).isEqualTo("Please provide between 2 and 5 document IDs for comparison.");
        }

        @Test
        void shouldRejectMoreThanFiveDocumentIds() {
            String result = service.compareItems("MySite", "en",
                    "doc1,doc2,doc3,doc4,doc5,doc6", "title,description");

            assertThat(result).isEqualTo("Please provide between 2 and 5 document IDs for comparison.");
        }

        @Test
        void shouldReturnErrorWithTwoIdsWhenServerUnavailable() {
            String result = service.compareItems("MySite", "en", "doc1,doc2", "title,description");

            assertThat(result).satisfiesAnyOf(
                    r -> assertThat(r).contains("Error comparing documents"),
                    r -> assertThat(r).contains("Document Comparison"));
        }

        @Test
        void shouldReturnErrorWithFiveIds() {
            String result = service.compareItems("MySite", "en",
                    "doc1,doc2,doc3,doc4,doc5", "title");

            assertThat(result).satisfiesAnyOf(
                    r -> assertThat(r).contains("Error comparing documents"),
                    r -> assertThat(r).contains("Document Comparison"));
        }

        @Test
        void shouldRejectEmptyDocumentIds() {
            // Single empty string splits to array of 1 element
            String result = service.compareItems("MySite", "en", "singleId", "title");

            assertThat(result).isEqualTo("Please provide between 2 and 5 document IDs for comparison.");
        }
    }

    // ======================== searchRecentUpdates ========================

    @Nested
    class SearchRecentUpdatesTests {

        @Test
        void shouldReturnErrorWhenServerUnavailable() {
            String result = service.searchRecentUpdates("MySite", "en", "test", 5, null);

            assertThat(result).satisfiesAnyOf(
                    r -> assertThat(r).contains("Error searching for recent content"),
                    r -> assertThat(r).contains("No recent content found"));
        }

        @Test
        void shouldReturnErrorWithNullQuery() {
            String result = service.searchRecentUpdates("MySite", "en", null, 5, null);

            assertThat(result).satisfiesAnyOf(
                    r -> assertThat(r).contains("Error searching for recent content"),
                    r -> assertThat(r).contains("No recent content found"));
        }

        @Test
        void shouldReturnErrorWithNullRows() {
            String result = service.searchRecentUpdates("MySite", "en", "test", null, null);

            assertThat(result).satisfiesAnyOf(
                    r -> assertThat(r).contains("Error searching for recent content"),
                    r -> assertThat(r).contains("No recent content found"));
        }

        @Test
        void shouldReturnErrorWithZeroRows() {
            String result = service.searchRecentUpdates("MySite", "en", "test", 0, null);

            assertThat(result).satisfiesAnyOf(
                    r -> assertThat(r).contains("Error searching for recent content"),
                    r -> assertThat(r).contains("No recent content found"));
        }

        @Test
        void shouldReturnErrorWithFilterQueries() {
            String result = service.searchRecentUpdates("MySite", "en", "test", 5, "type:Article");

            assertThat(result).satisfiesAnyOf(
                    r -> assertThat(r).contains("Error searching for recent content"),
                    r -> assertThat(r).contains("No recent content found"));
        }

        @Test
        void shouldReturnErrorWithBlankFilterQueries() {
            String result = service.searchRecentUpdates("MySite", "en", "test", 5, "   ");

            assertThat(result).satisfiesAnyOf(
                    r -> assertThat(r).contains("Error searching for recent content"),
                    r -> assertThat(r).contains("No recent content found"));
        }
    }

    // ======================== getFacetSummary ========================

    @Nested
    class GetFacetSummaryTests {

        @Test
        void shouldReturnErrorWhenServerUnavailable() {
            String result = service.getFacetSummary("MySite", "en", "test");

            assertThat(result).satisfiesAnyOf(
                    r -> assertThat(r).contains("Error retrieving facet summary"),
                    r -> assertThat(r).contains("No facet data available"));
        }

        @Test
        void shouldReturnErrorWithNullQuery() {
            String result = service.getFacetSummary("MySite", "en", null);

            assertThat(result).satisfiesAnyOf(
                    r -> assertThat(r).contains("Error retrieving facet summary"),
                    r -> assertThat(r).contains("No facet data available"));
        }

        @Test
        void shouldReturnErrorWithBlankQuery() {
            String result = service.getFacetSummary("MySite", "en", "   ");

            assertThat(result).satisfiesAnyOf(
                    r -> assertThat(r).contains("Error retrieving facet summary"),
                    r -> assertThat(r).contains("No facet data available"));
        }
    }

    // ======================== searchByDateRange ========================

    @Nested
    class SearchByDateRangeTests {

        @Test
        void shouldReturnErrorWhenServerUnavailable() {
            String result = service.searchByDateRange("MySite", "en", "test",
                    "modification_date", "2025-01-01", "2025-12-31", 10);

            assertThat(result).satisfiesAnyOf(
                    r -> assertThat(r).contains("Error executing date range search"),
                    r -> assertThat(r).contains("No results found"));
        }

        @Test
        void shouldReturnErrorWithNullQuery() {
            String result = service.searchByDateRange("MySite", "en", null,
                    "date", "2025-01-01", "2025-12-31", 10);

            assertThat(result).satisfiesAnyOf(
                    r -> assertThat(r).contains("Error executing date range search"),
                    r -> assertThat(r).contains("No results found"));
        }

        @Test
        void shouldReturnErrorWithNullRows() {
            String result = service.searchByDateRange("MySite", "en", "test",
                    "date", "2025-01-01", "2025-12-31", null);

            assertThat(result).satisfiesAnyOf(
                    r -> assertThat(r).contains("Error executing date range search"),
                    r -> assertThat(r).contains("No results found"));
        }

        @Test
        void shouldReturnErrorWithZeroRows() {
            String result = service.searchByDateRange("MySite", "en", "test",
                    "date", "2025-01-01", "2025-12-31", 0);

            assertThat(result).satisfiesAnyOf(
                    r -> assertThat(r).contains("Error executing date range search"),
                    r -> assertThat(r).contains("No results found"));
        }
    }

    // ======================== getValidFilterValues ========================

    @Nested
    class GetValidFilterValuesTests {

        @Test
        void shouldReturnErrorWhenServerUnavailable() {
            String result = service.getValidFilterValues("MySite", "en", "category");

            assertThat(result).satisfiesAnyOf(
                    r -> assertThat(r).contains("Error retrieving filter values"),
                    r -> assertThat(r).contains("No facet data available"));
        }

        @Test
        void shouldReturnErrorWithNullField() {
            String result = service.getValidFilterValues("MySite", "en", null);

            assertThat(result).satisfiesAnyOf(
                    r -> assertThat(r).contains("Error retrieving filter values"),
                    r -> assertThat(r).contains("No facet data available"));
        }
    }

    // ======================== lookupFacetValue ========================

    @Nested
    class LookupFacetValueTests {

        @Test
        void shouldReturnErrorWhenServerUnavailable() {
            String result = service.lookupFacetValue("MySite", "en", "technology");

            assertThat(result).satisfiesAnyOf(
                    r -> assertThat(r).contains("Error retrieving facet values"),
                    r -> assertThat(r).contains("No facets found"));
        }

        @Test
        void shouldReturnErrorWithNullTerm() {
            String result = service.lookupFacetValue("MySite", "en", null);

            assertThat(result).satisfiesAnyOf(
                    r -> assertThat(r).contains("Error retrieving facet values"),
                    r -> assertThat(r).contains("No facets found"));
        }
    }

    // ======================== discoverFacetValues ========================

    @Nested
    class DiscoverFacetValuesTests {

        @Test
        void shouldReturnErrorWhenServerUnavailable() {
            String result = service.discoverFacetValues("MySite", "en", "artificial intelligence");

            assertThat(result).satisfiesAnyOf(
                    r -> assertThat(r).contains("Error retrieving facet values"),
                    r -> assertThat(r).contains("No facets found"));
        }

        @Test
        void shouldReturnErrorWithNullText() {
            String result = service.discoverFacetValues("MySite", "en", null);

            assertThat(result).satisfiesAnyOf(
                    r -> assertThat(r).contains("Error retrieving facet values"),
                    r -> assertThat(r).contains("No facets found"));
        }
    }

    // ======================== Private method tests via reflection ========================

    @Nested
    class TruncateValueTests {

        @Test
        void shouldNotTruncateShortValue() throws Exception {
            Method method = TurSemanticNavToolService.class.getDeclaredMethod("truncateValue", String.class, int.class);
            method.setAccessible(true);

            String result = (String) method.invoke(service, "short text", 300);
            assertThat(result).isEqualTo("short text");
        }

        @Test
        void shouldTruncateLongValue() throws Exception {
            Method method = TurSemanticNavToolService.class.getDeclaredMethod("truncateValue", String.class, int.class);
            method.setAccessible(true);

            String longText = "x".repeat(400);
            String result = (String) method.invoke(service, longText, 300);
            assertThat(result).hasSize(303); // 300 + "..."
            assertThat(result).endsWith("...");
        }

        @Test
        void shouldNotTruncateExactLengthValue() throws Exception {
            Method method = TurSemanticNavToolService.class.getDeclaredMethod("truncateValue", String.class, int.class);
            method.setAccessible(true);

            String exactText = "x".repeat(300);
            String result = (String) method.invoke(service, exactText, 300);
            assertThat(result).hasSize(300);
            assertThat(result).doesNotEndWith("...");
        }
    }

    @Nested
    class IsEmptyResultsTests {

        @Test
        void shouldReturnTrueForNull() throws Exception {
            Method method = TurSemanticNavToolService.class.getDeclaredMethod("isEmptyResults",
                    com.viglet.turing.client.sn.TurSNDocumentList.class);
            method.setAccessible(true);

            boolean result = (boolean) method.invoke(service, (Object) null);
            assertThat(result).isTrue();
        }
    }

    @Nested
    class BuildNoResultsMessageTests {

        @Test
        void shouldBuildMessageWithoutFilters() throws Exception {
            Method method = TurSemanticNavToolService.class.getDeclaredMethod("buildNoResultsMessage",
                    String.class, String.class);
            method.setAccessible(true);

            String result = (String) method.invoke(service, "my query", null);

            assertThat(result)
                    .contains("No results found for query: my query")
                    .contains("get_search_suggestions")
                    .doesNotContain("get_valid_filter_values");
        }

        @Test
        void shouldBuildMessageWithFilters() throws Exception {
            Method method = TurSemanticNavToolService.class.getDeclaredMethod("buildNoResultsMessage",
                    String.class, String.class);
            method.setAccessible(true);

            String result = (String) method.invoke(service, "my query", "type:Article");

            assertThat(result)
                    .contains("No results found for query: my query")
                    .contains("get_search_suggestions")
                    .contains("get_valid_filter_values");
        }

        @Test
        void shouldBuildMessageWithBlankFilters() throws Exception {
            Method method = TurSemanticNavToolService.class.getDeclaredMethod("buildNoResultsMessage",
                    String.class, String.class);
            method.setAccessible(true);

            String result = (String) method.invoke(service, "my query", "   ");

            assertThat(result)
                    .contains("No results found for query: my query")
                    .doesNotContain("get_valid_filter_values");
        }

        @Test
        void shouldBuildMessageWithEmptyFilters() throws Exception {
            Method method = TurSemanticNavToolService.class.getDeclaredMethod("buildNoResultsMessage",
                    String.class, String.class);
            method.setAccessible(true);

            String result = (String) method.invoke(service, "test", "");

            assertThat(result)
                    .contains("No results found for query: test")
                    .doesNotContain("get_valid_filter_values");
        }
    }

    @Nested
    class ContainsHighlightTagsTests {

        @Test
        void shouldDetectEmTag() throws Exception {
            Method method = TurSemanticNavToolService.class.getDeclaredMethod("containsHighlightTags", String.class);
            method.setAccessible(true);

            assertThat((boolean) method.invoke(service, "This has <em>highlighted</em> text")).isTrue();
        }

        @Test
        void shouldDetectBTag() throws Exception {
            Method method = TurSemanticNavToolService.class.getDeclaredMethod("containsHighlightTags", String.class);
            method.setAccessible(true);

            assertThat((boolean) method.invoke(service, "This has <b>bold</b> text")).isTrue();
        }

        @Test
        void shouldDetectStrongTag() throws Exception {
            Method method = TurSemanticNavToolService.class.getDeclaredMethod("containsHighlightTags", String.class);
            method.setAccessible(true);

            assertThat((boolean) method.invoke(service, "This has <strong>strong</strong> text")).isTrue();
        }

        @Test
        void shouldDetectMarkTag() throws Exception {
            Method method = TurSemanticNavToolService.class.getDeclaredMethod("containsHighlightTags", String.class);
            method.setAccessible(true);

            assertThat((boolean) method.invoke(service, "This has <mark>marked</mark> text")).isTrue();
        }

        @Test
        void shouldReturnFalseForNoTags() throws Exception {
            Method method = TurSemanticNavToolService.class.getDeclaredMethod("containsHighlightTags", String.class);
            method.setAccessible(true);

            assertThat((boolean) method.invoke(service, "Plain text without tags")).isFalse();
        }

        @Test
        void shouldReturnFalseForEmptyString() throws Exception {
            Method method = TurSemanticNavToolService.class.getDeclaredMethod("containsHighlightTags", String.class);
            method.setAccessible(true);

            assertThat((boolean) method.invoke(service, "")).isFalse();
        }
    }

    @Nested
    class StemWordTests {

        @Test
        void shouldReturnNullForNull() throws Exception {
            Method method = TurSemanticNavToolService.class.getDeclaredMethod("stemWord", String.class);
            method.setAccessible(true);

            assertThat(method.invoke(service, (Object) null)).isNull();
        }

        @Test
        void shouldReturnShortWordsUnchanged() throws Exception {
            Method method = TurSemanticNavToolService.class.getDeclaredMethod("stemWord", String.class);
            method.setAccessible(true);

            assertThat(method.invoke(service, "ab")).isEqualTo("ab");
            assertThat(method.invoke(service, "abc")).isEqualTo("abc");
        }

        @Test
        void shouldStemPortuguesePluralOes() throws Exception {
            Method method = TurSemanticNavToolService.class.getDeclaredMethod("stemWord", String.class);
            method.setAccessible(true);

            // "ações" suffix with minLength 6
            String result = (String) method.invoke(service, "ações");
            // word length = 5, minLength for "ões" = 4, should match "ões"
            assertThat(result).doesNotEndWith("ões");
        }

        @Test
        void shouldStemPortugueseSuffix() throws Exception {
            Method method = TurSemanticNavToolService.class.getDeclaredMethod("stemWord", String.class);
            method.setAccessible(true);

            // "mente" suffix with minLength 6
            String result = (String) method.invoke(service, "rapidamente");
            assertThat(result).isEqualTo("rapida");
        }

        @Test
        void shouldStemPortugueseDade() throws Exception {
            Method method = TurSemanticNavToolService.class.getDeclaredMethod("stemWord", String.class);
            method.setAccessible(true);

            String result = (String) method.invoke(service, "cidade");
            assertThat(result).isEqualTo("ci");
        }

        @Test
        void shouldStemPortuguesePlurals() throws Exception {
            Method method = TurSemanticNavToolService.class.getDeclaredMethod("stemWord", String.class);
            method.setAccessible(true);

            // "s" suffix with minLength 4
            String result = (String) method.invoke(service, "gatos");
            assertThat(result).isEqualTo("gato");
        }

        @Test
        void shouldNotStemShortWordWithSuffix() throws Exception {
            Method method = TurSemanticNavToolService.class.getDeclaredMethod("stemWord", String.class);
            method.setAccessible(true);

            // "oso" requires minLength 4, "dos" is 3 chars
            String result = (String) method.invoke(service, "dos");
            // Should be returned as-is since length <= 3
            assertThat(result).isEqualTo("dos");
        }

        @Test
        void shouldStemAmento() throws Exception {
            Method method = TurSemanticNavToolService.class.getDeclaredMethod("stemWord", String.class);
            method.setAccessible(true);

            String result = (String) method.invoke(service, "casamento");
            assertThat(result).isEqualTo("cas");
        }

        @Test
        void shouldStemAdora() throws Exception {
            Method method = TurSemanticNavToolService.class.getDeclaredMethod("stemWord", String.class);
            method.setAccessible(true);

            String result = (String) method.invoke(service, "trabalhadora");
            assertThat(result).isEqualTo("trabalh");
        }

        @Test
        void shouldStemAdor() throws Exception {
            Method method = TurSemanticNavToolService.class.getDeclaredMethod("stemWord", String.class);
            method.setAccessible(true);

            String result = (String) method.invoke(service, "trabalhador");
            assertThat(result).isEqualTo("trabalh");
        }

        @Test
        void shouldStemAvel() throws Exception {
            Method method = TurSemanticNavToolService.class.getDeclaredMethod("stemWord", String.class);
            method.setAccessible(true);

            String result = (String) method.invoke(service, "amável");
            assertThat(result).isEqualTo("am");
        }

        @Test
        void shouldStemIvel() throws Exception {
            Method method = TurSemanticNavToolService.class.getDeclaredMethod("stemWord", String.class);
            method.setAccessible(true);

            String result = (String) method.invoke(service, "possível");
            assertThat(result).isEqualTo("poss");
        }

        @Test
        void shouldStemAcao() throws Exception {
            Method method = TurSemanticNavToolService.class.getDeclaredMethod("stemWord", String.class);
            method.setAccessible(true);

            String result = (String) method.invoke(service, "criação");
            assertThat(result).isEqualTo("cri");
        }

        @Test
        void shouldStemIsta() throws Exception {
            Method method = TurSemanticNavToolService.class.getDeclaredMethod("stemWord", String.class);
            method.setAccessible(true);

            String result = (String) method.invoke(service, "artista");
            assertThat(result).isEqualTo("art");
        }

        @Test
        void shouldStemIsmo() throws Exception {
            Method method = TurSemanticNavToolService.class.getDeclaredMethod("stemWord", String.class);
            method.setAccessible(true);

            String result = (String) method.invoke(service, "modernismo");
            assertThat(result).isEqualTo("modern");
        }

        @Test
        void shouldStemOso() throws Exception {
            Method method = TurSemanticNavToolService.class.getDeclaredMethod("stemWord", String.class);
            method.setAccessible(true);

            String result = (String) method.invoke(service, "famoso");
            assertThat(result).isEqualTo("fam");
        }

        @Test
        void shouldStemOsa() throws Exception {
            Method method = TurSemanticNavToolService.class.getDeclaredMethod("stemWord", String.class);
            method.setAccessible(true);

            String result = (String) method.invoke(service, "famosa");
            assertThat(result).isEqualTo("fam");
        }

        @Test
        void shouldStemIvo() throws Exception {
            Method method = TurSemanticNavToolService.class.getDeclaredMethod("stemWord", String.class);
            method.setAccessible(true);

            String result = (String) method.invoke(service, "ativo");
            assertThat(result).isEqualTo("at");
        }

        @Test
        void shouldStemIva() throws Exception {
            Method method = TurSemanticNavToolService.class.getDeclaredMethod("stemWord", String.class);
            method.setAccessible(true);

            String result = (String) method.invoke(service, "ativa");
            assertThat(result).isEqualTo("at");
        }

        @Test
        void shouldReturnWordUnchangedWhenNoRuleMatches() throws Exception {
            Method method = TurSemanticNavToolService.class.getDeclaredMethod("stemWord", String.class);
            method.setAccessible(true);

            String result = (String) method.invoke(service, "chat");
            assertThat(result).isEqualTo("chat");
        }

        @Test
        void shouldHandleUpperCase() throws Exception {
            Method method = TurSemanticNavToolService.class.getDeclaredMethod("stemWord", String.class);
            method.setAccessible(true);

            String result = (String) method.invoke(service, "GATOS");
            assertThat(result).isEqualTo("gato");
        }
    }

    @Nested
    class MatchesByStemTests {

        @Test
        void shouldMatchExactStemmedWord() throws Exception {
            Method method = TurSemanticNavToolService.class.getDeclaredMethod("matchesByStem",
                    String.class, String.class);
            method.setAccessible(true);

            assertThat((boolean) method.invoke(service, "Technology", "tech")).isTrue();
        }

        @Test
        void shouldMatchStemmedLabel() throws Exception {
            Method method = TurSemanticNavToolService.class.getDeclaredMethod("matchesByStem",
                    String.class, String.class);
            method.setAccessible(true);

            assertThat((boolean) method.invoke(service, "ciências", "ciênci")).isTrue();
        }

        @Test
        void shouldNotMatchUnrelatedWord() throws Exception {
            Method method = TurSemanticNavToolService.class.getDeclaredMethod("matchesByStem",
                    String.class, String.class);
            method.setAccessible(true);

            assertThat((boolean) method.invoke(service, "Finance", "tech")).isFalse();
        }

        @Test
        void shouldMatchMultiWordLabel() throws Exception {
            Method method = TurSemanticNavToolService.class.getDeclaredMethod("matchesByStem",
                    String.class, String.class);
            method.setAccessible(true);

            // Label with multiple words separated by space
            assertThat((boolean) method.invoke(service, "Data Science", "scien")).isTrue();
        }

        @Test
        void shouldMatchLabelWithSpecialSeparators() throws Exception {
            Method method = TurSemanticNavToolService.class.getDeclaredMethod("matchesByStem",
                    String.class, String.class);
            method.setAccessible(true);

            // Label with comma separator
            assertThat((boolean) method.invoke(service, "AI,Machine Learning", "machin")).isTrue();
        }

        @Test
        void shouldHandleEmptyLabel() throws Exception {
            Method method = TurSemanticNavToolService.class.getDeclaredMethod("matchesByStem",
                    String.class, String.class);
            method.setAccessible(true);

            assertThat((boolean) method.invoke(service, "", "tech")).isFalse();
        }
    }

    @Nested
    class StemRuleRecordTests {

        @Test
        void shouldMatchWhenSuffixAndMinLengthSatisfied() throws Exception {
            // Test the StemRule inner record via stemWord behavior
            Method method = TurSemanticNavToolService.class.getDeclaredMethod("stemWord", String.class);
            method.setAccessible(true);

            // "imento" suffix, minLength 7 => "conhecimento" (12 chars) should match
            String result = (String) method.invoke(service, "conhecimento");
            assertThat(result).isEqualTo("conhec");
        }

        @Test
        void shouldNotMatchWhenWordTooShort() throws Exception {
            Method method = TurSemanticNavToolService.class.getDeclaredMethod("stemWord", String.class);
            method.setAccessible(true);

            // "ante" suffix requires minLength 5 => "ante" (4 chars) should NOT match
            String result = (String) method.invoke(service, "ante");
            assertThat(result).isEqualTo("ante");
        }
    }

    @Nested
    class AppendSummaryFieldsTests {

        @Test
        void shouldAppendOnlyExistingFields() throws Exception {
            Method method = TurSemanticNavToolService.class.getDeclaredMethod("appendSummaryFields",
                    StringBuilder.class, Map.class);
            method.setAccessible(true);

            Map<String, Object> fields = new LinkedHashMap<>();
            fields.put("id", "doc1");
            fields.put("title", "My Title");
            // description is null, should not appear

            StringBuilder sb = new StringBuilder();
            method.invoke(service, sb, fields);

            String result = sb.toString();
            assertThat(result).contains("id: doc1").contains("title: My Title");
            assertThat(result).doesNotContain("description:");
        }

        @Test
        void shouldTruncateLongValues() throws Exception {
            Method method = TurSemanticNavToolService.class.getDeclaredMethod("appendSummaryFields",
                    StringBuilder.class, Map.class);
            method.setAccessible(true);

            Map<String, Object> fields = new LinkedHashMap<>();
            fields.put("description", "x".repeat(400));

            StringBuilder sb = new StringBuilder();
            method.invoke(service, sb, fields);

            String result = sb.toString();
            assertThat(result).contains("...");
        }

        @Test
        void shouldHandleEmptyFieldsMap() throws Exception {
            Method method = TurSemanticNavToolService.class.getDeclaredMethod("appendSummaryFields",
                    StringBuilder.class, Map.class);
            method.setAccessible(true);

            StringBuilder sb = new StringBuilder();
            method.invoke(service, sb, new LinkedHashMap<>());

            assertThat(sb.toString()).isEmpty();
        }
    }

    @Nested
    class AppendAdditionalFieldsTests {

        @Test
        void shouldAppendNonSummaryFields() throws Exception {
            Method method = TurSemanticNavToolService.class.getDeclaredMethod("appendAdditionalFields",
                    StringBuilder.class, Map.class);
            method.setAccessible(true);

            Map<String, Object> fields = new LinkedHashMap<>();
            fields.put("title", "My Title"); // summary field, should skip
            fields.put("custom_field", "value");
            fields.put("id", "doc1"); // summary field, should skip

            StringBuilder sb = new StringBuilder();
            method.invoke(service, sb, fields);

            String result = sb.toString();
            assertThat(result).contains("custom_field: value");
            assertThat(result).doesNotContain("title:");
            assertThat(result).doesNotContain("id:");
        }

        @Test
        void shouldSkipBlankValues() throws Exception {
            Method method = TurSemanticNavToolService.class.getDeclaredMethod("appendAdditionalFields",
                    StringBuilder.class, Map.class);
            method.setAccessible(true);

            Map<String, Object> fields = new LinkedHashMap<>();
            fields.put("custom_field", "   ");

            StringBuilder sb = new StringBuilder();
            method.invoke(service, sb, fields);

            assertThat(sb.toString()).isEmpty();
        }

        @Test
        void shouldSkipLongValues() throws Exception {
            Method method = TurSemanticNavToolService.class.getDeclaredMethod("appendAdditionalFields",
                    StringBuilder.class, Map.class);
            method.setAccessible(true);

            Map<String, Object> fields = new LinkedHashMap<>();
            fields.put("custom_field", "x".repeat(200));

            StringBuilder sb = new StringBuilder();
            method.invoke(service, sb, fields);

            assertThat(sb.toString()).isEmpty();
        }

        @Test
        void shouldSkipNullValues() throws Exception {
            Method method = TurSemanticNavToolService.class.getDeclaredMethod("appendAdditionalFields",
                    StringBuilder.class, Map.class);
            method.setAccessible(true);

            Map<String, Object> fields = new LinkedHashMap<>();
            fields.put("custom_field", null);

            StringBuilder sb = new StringBuilder();
            method.invoke(service, sb, fields);

            assertThat(sb.toString()).isEmpty();
        }

        @Test
        void shouldIncludeFieldExactly150Chars() throws Exception {
            Method method = TurSemanticNavToolService.class.getDeclaredMethod("appendAdditionalFields",
                    StringBuilder.class, Map.class);
            method.setAccessible(true);

            Map<String, Object> fields = new LinkedHashMap<>();
            fields.put("custom_field", "x".repeat(150));

            StringBuilder sb = new StringBuilder();
            method.invoke(service, sb, fields);

            assertThat(sb.toString()).contains("custom_field:");
        }

        @Test
        void shouldSkipFieldExactly151Chars() throws Exception {
            Method method = TurSemanticNavToolService.class.getDeclaredMethod("appendAdditionalFields",
                    StringBuilder.class, Map.class);
            method.setAccessible(true);

            Map<String, Object> fields = new LinkedHashMap<>();
            fields.put("custom_field", "x".repeat(151));

            StringBuilder sb = new StringBuilder();
            method.invoke(service, sb, fields);

            assertThat(sb.toString()).isEmpty();
        }
    }

    @Nested
    class AppendHighlightedFieldsTests {

        @Test
        void shouldReturnTrueWhenHighlightFound() throws Exception {
            Method method = TurSemanticNavToolService.class.getDeclaredMethod("appendHighlightedFields",
                    StringBuilder.class, Map.class);
            method.setAccessible(true);

            Map<String, Object> fields = new LinkedHashMap<>();
            fields.put("text", "This has <em>highlighted</em> text");

            StringBuilder sb = new StringBuilder();
            boolean result = (boolean) method.invoke(service, sb, fields);

            assertThat(result).isTrue();
            assertThat(sb.toString()).contains("[text]:");
        }

        @Test
        void shouldReturnFalseWhenNoHighlight() throws Exception {
            Method method = TurSemanticNavToolService.class.getDeclaredMethod("appendHighlightedFields",
                    StringBuilder.class, Map.class);
            method.setAccessible(true);

            Map<String, Object> fields = new LinkedHashMap<>();
            fields.put("text", "Plain text");

            StringBuilder sb = new StringBuilder();
            boolean result = (boolean) method.invoke(service, sb, fields);

            assertThat(result).isFalse();
            assertThat(sb.toString()).isEmpty();
        }

        @Test
        void shouldSkipNullValues() throws Exception {
            Method method = TurSemanticNavToolService.class.getDeclaredMethod("appendHighlightedFields",
                    StringBuilder.class, Map.class);
            method.setAccessible(true);

            Map<String, Object> fields = new LinkedHashMap<>();
            fields.put("text", null);

            StringBuilder sb = new StringBuilder();
            boolean result = (boolean) method.invoke(service, sb, fields);

            assertThat(result).isFalse();
        }

        @Test
        void shouldTruncateVeryLongHighlightedFields() throws Exception {
            Method method = TurSemanticNavToolService.class.getDeclaredMethod("appendHighlightedFields",
                    StringBuilder.class, Map.class);
            method.setAccessible(true);

            Map<String, Object> fields = new LinkedHashMap<>();
            fields.put("text", "<em>highlight</em>" + "x".repeat(4000));

            StringBuilder sb = new StringBuilder();
            boolean result = (boolean) method.invoke(service, sb, fields);

            assertThat(result).isTrue();
            assertThat(sb.toString()).contains("[TRUNCATED]");
        }
    }

    @Nested
    class AppendFallbackFieldsTests {

        @Test
        void shouldAppendExistingFallbackFields() throws Exception {
            Method method = TurSemanticNavToolService.class.getDeclaredMethod("appendFallbackFields",
                    StringBuilder.class, Map.class);
            method.setAccessible(true);

            Map<String, Object> fields = new LinkedHashMap<>();
            fields.put("title", "My Title");
            fields.put("description", "My Description");
            fields.put("text", "Full text content");
            fields.put("abstract", "Abstract text");

            StringBuilder sb = new StringBuilder();
            method.invoke(service, sb, fields);

            String result = sb.toString();
            assertThat(result)
                    .contains("No highlighted passages found")
                    .contains("[title]: My Title")
                    .contains("[description]: My Description")
                    .contains("[text]: Full text content")
                    .contains("[abstract]: Abstract text");
        }

        @Test
        void shouldSkipMissingFallbackFields() throws Exception {
            Method method = TurSemanticNavToolService.class.getDeclaredMethod("appendFallbackFields",
                    StringBuilder.class, Map.class);
            method.setAccessible(true);

            Map<String, Object> fields = new LinkedHashMap<>();
            fields.put("title", "My Title");
            // no description, text, abstract

            StringBuilder sb = new StringBuilder();
            method.invoke(service, sb, fields);

            String result = sb.toString();
            assertThat(result).contains("[title]: My Title");
            assertThat(result).doesNotContain("[description]");
        }

        @Test
        void shouldTruncateLongFallbackValues() throws Exception {
            Method method = TurSemanticNavToolService.class.getDeclaredMethod("appendFallbackFields",
                    StringBuilder.class, Map.class);
            method.setAccessible(true);

            Map<String, Object> fields = new LinkedHashMap<>();
            fields.put("text", "x".repeat(3000));

            StringBuilder sb = new StringBuilder();
            method.invoke(service, sb, fields);

            assertThat(sb.toString()).contains("[TRUNCATED]");
        }

        @Test
        void shouldNotTruncateExactly2000Chars() throws Exception {
            Method method = TurSemanticNavToolService.class.getDeclaredMethod("appendFallbackFields",
                    StringBuilder.class, Map.class);
            method.setAccessible(true);

            Map<String, Object> fields = new LinkedHashMap<>();
            fields.put("text", "x".repeat(2000));

            StringBuilder sb = new StringBuilder();
            method.invoke(service, sb, fields);

            assertThat(sb.toString()).doesNotContain("[TRUNCATED]");
        }
    }

    @Nested
    class AppendFieldIfPresentTests {

        @Test
        void shouldAppendFieldWhenPresent() throws Exception {
            Method method = TurSemanticNavToolService.class.getDeclaredMethod("appendFieldIfPresent",
                    StringBuilder.class, Map.class, String.class);
            method.setAccessible(true);

            Map<String, Object> fields = Map.of("title", "My Title");
            StringBuilder sb = new StringBuilder();
            method.invoke(service, sb, fields, "title");

            assertThat(sb.toString()).isEqualTo("title: My Title\n");
        }

        @Test
        void shouldNotAppendFieldWhenNull() throws Exception {
            Method method = TurSemanticNavToolService.class.getDeclaredMethod("appendFieldIfPresent",
                    StringBuilder.class, Map.class, String.class);
            method.setAccessible(true);

            Map<String, Object> fields = new LinkedHashMap<>();
            fields.put("title", null);
            StringBuilder sb = new StringBuilder();
            method.invoke(service, sb, fields, "title");

            assertThat(sb.toString()).isEmpty();
        }

        @Test
        void shouldNotAppendFieldWhenMissing() throws Exception {
            Method method = TurSemanticNavToolService.class.getDeclaredMethod("appendFieldIfPresent",
                    StringBuilder.class, Map.class, String.class);
            method.setAccessible(true);

            Map<String, Object> fields = Map.of("title", "My Title");
            StringBuilder sb = new StringBuilder();
            method.invoke(service, sb, fields, "description");

            assertThat(sb.toString()).isEmpty();
        }

        @Test
        void shouldTruncateLongFieldValue() throws Exception {
            Method method = TurSemanticNavToolService.class.getDeclaredMethod("appendFieldIfPresent",
                    StringBuilder.class, Map.class, String.class);
            method.setAccessible(true);

            Map<String, Object> fields = Map.of("desc", "x".repeat(400));
            StringBuilder sb = new StringBuilder();
            method.invoke(service, sb, fields, "desc");

            assertThat(sb.toString()).contains("...");
        }

        @Test
        void shouldNotTruncateExactly300Chars() throws Exception {
            Method method = TurSemanticNavToolService.class.getDeclaredMethod("appendFieldIfPresent",
                    StringBuilder.class, Map.class, String.class);
            method.setAccessible(true);

            Map<String, Object> fields = Map.of("desc", "x".repeat(300));
            StringBuilder sb = new StringBuilder();
            method.invoke(service, sb, fields, "desc");

            assertThat(sb.toString()).doesNotContain("...");
        }
    }

    @Nested
    class ExtractFieldValuesTests {

        @Test
        void shouldExtractExistingFields() throws Exception {
            Method method = TurSemanticNavToolService.class.getDeclaredMethod("extractFieldValues",
                    Map.class, String[].class, Map.class);
            method.setAccessible(true);

            Map<String, Object> docFields = Map.of("title", "My Title", "type", "Article");
            String[] fieldNames = {"title", "type"};
            Map<String, String> result = new LinkedHashMap<>();

            method.invoke(service, docFields, fieldNames, result);

            assertThat(result).containsEntry("title", "My Title");
            assertThat(result).containsEntry("type", "Article");
        }

        @Test
        void shouldReturnNotAvailableForMissingFields() throws Exception {
            Method method = TurSemanticNavToolService.class.getDeclaredMethod("extractFieldValues",
                    Map.class, String[].class, Map.class);
            method.setAccessible(true);

            Map<String, Object> docFields = Map.of("title", "My Title");
            String[] fieldNames = {"title", "missing_field"};
            Map<String, String> result = new LinkedHashMap<>();

            method.invoke(service, docFields, fieldNames, result);

            assertThat(result).containsEntry("title", "My Title");
            assertThat(result).containsEntry("missing_field", "(not available)");
        }

        @Test
        void shouldTruncateValuesOver500() throws Exception {
            Method method = TurSemanticNavToolService.class.getDeclaredMethod("extractFieldValues",
                    Map.class, String[].class, Map.class);
            method.setAccessible(true);

            Map<String, Object> docFields = Map.of("text", "x".repeat(600));
            String[] fieldNames = {"text"};
            Map<String, String> result = new LinkedHashMap<>();

            method.invoke(service, docFields, fieldNames, result);

            assertThat(result.get("text")).endsWith("...");
            assertThat(result.get("text")).hasSize(503);
        }

        @Test
        void shouldTrimFieldNames() throws Exception {
            Method method = TurSemanticNavToolService.class.getDeclaredMethod("extractFieldValues",
                    Map.class, String[].class, Map.class);
            method.setAccessible(true);

            Map<String, Object> docFields = Map.of("title", "My Title");
            String[] fieldNames = {"  title  "};
            Map<String, String> result = new LinkedHashMap<>();

            method.invoke(service, docFields, fieldNames, result);

            assertThat(result).containsEntry("title", "My Title");
        }
    }

    @Nested
    class FillMissingFieldsTests {

        @Test
        void shouldFillAllFieldsAsNotFound() throws Exception {
            Method method = TurSemanticNavToolService.class.getDeclaredMethod("fillMissingFields",
                    String[].class, Map.class);
            method.setAccessible(true);

            String[] fieldNames = {" title ", "type "};
            Map<String, String> result = new LinkedHashMap<>();

            method.invoke(service, fieldNames, result);

            assertThat(result).containsEntry("title", "(document not found)");
            assertThat(result).containsEntry("type", "(document not found)");
        }
    }

    @Nested
    class BuildComparisonTableTests {

        @Test
        void shouldBuildTableWithHeader() throws Exception {
            Method method = TurSemanticNavToolService.class.getDeclaredMethod("buildComparisonTable",
                    Map.class, String[].class);
            method.setAccessible(true);

            Map<String, Map<String, String>> data = new LinkedHashMap<>();
            Map<String, String> doc1Fields = new LinkedHashMap<>();
            doc1Fields.put("title", "Doc A");
            doc1Fields.put("type", "Article");
            data.put("id1", doc1Fields);

            Map<String, String> doc2Fields = new LinkedHashMap<>();
            doc2Fields.put("title", "Doc B");
            doc2Fields.put("type", "Page");
            data.put("id2", doc2Fields);

            String[] fieldNames = {"title", "type"};

            String result = (String) method.invoke(service, data, fieldNames);

            assertThat(result)
                    .contains("=== Document Comparison ===")
                    .contains("| Field |")
                    .contains("Doc A")
                    .contains("Doc B")
                    .contains("**title**")
                    .contains("**type**")
                    .contains("Article")
                    .contains("Page");
        }

        @Test
        void shouldTruncateLongTitlesInHeader() throws Exception {
            Method method = TurSemanticNavToolService.class.getDeclaredMethod("buildComparisonTable",
                    Map.class, String[].class);
            method.setAccessible(true);

            Map<String, Map<String, String>> data = new LinkedHashMap<>();
            Map<String, String> doc1Fields = new LinkedHashMap<>();
            doc1Fields.put("title", "x".repeat(50));
            data.put("id1", doc1Fields);

            Map<String, String> doc2Fields = new LinkedHashMap<>();
            doc2Fields.put("title", "Short");
            data.put("id2", doc2Fields);

            String[] fieldNames = {"title"};

            String result = (String) method.invoke(service, data, fieldNames);

            assertThat(result).contains("...");
        }

        @Test
        void shouldUseFallbackTitleWhenMissing() throws Exception {
            Method method = TurSemanticNavToolService.class.getDeclaredMethod("buildComparisonTable",
                    Map.class, String[].class);
            method.setAccessible(true);

            Map<String, Map<String, String>> data = new LinkedHashMap<>();
            Map<String, String> doc1Fields = new LinkedHashMap<>();
            doc1Fields.put("type", "Article");
            data.put("id1", doc1Fields);

            Map<String, String> doc2Fields = new LinkedHashMap<>();
            doc2Fields.put("type", "Page");
            data.put("id2", doc2Fields);

            String[] fieldNames = {"type"};

            String result = (String) method.invoke(service, data, fieldNames);

            // When title is missing, uses "Doc N" fallback
            assertThat(result).contains("Doc 1").contains("Doc 2");
        }

        @Test
        void shouldTruncateLongCellValues() throws Exception {
            Method method = TurSemanticNavToolService.class.getDeclaredMethod("buildComparisonTable",
                    Map.class, String[].class);
            method.setAccessible(true);

            Map<String, Map<String, String>> data = new LinkedHashMap<>();
            Map<String, String> doc1Fields = new LinkedHashMap<>();
            doc1Fields.put("title", "T1");
            doc1Fields.put("text", "x".repeat(300));
            data.put("id1", doc1Fields);

            Map<String, String> doc2Fields = new LinkedHashMap<>();
            doc2Fields.put("title", "T2");
            doc2Fields.put("text", "short");
            data.put("id2", doc2Fields);

            String[] fieldNames = {"text"};

            String result = (String) method.invoke(service, data, fieldNames);

            assertThat(result).contains("...");
        }

        @Test
        void shouldReplacePipesAndNewlines() throws Exception {
            Method method = TurSemanticNavToolService.class.getDeclaredMethod("buildComparisonTable",
                    Map.class, String[].class);
            method.setAccessible(true);

            Map<String, Map<String, String>> data = new LinkedHashMap<>();
            Map<String, String> doc1Fields = new LinkedHashMap<>();
            doc1Fields.put("title", "T1");
            doc1Fields.put("text", "pipe|here\nnewline");
            data.put("id1", doc1Fields);

            Map<String, String> doc2Fields = new LinkedHashMap<>();
            doc2Fields.put("title", "T2");
            doc2Fields.put("text", "ok");
            data.put("id2", doc2Fields);

            String[] fieldNames = {"text"};

            String result = (String) method.invoke(service, data, fieldNames);

            // Pipes should be replaced with /
            assertThat(result).contains("pipe/here newline");
        }
    }

    @Nested
    class AppendDateRangeFieldsTests {

        @Test
        void shouldAppendFieldsInOrder() throws Exception {
            Method method = TurSemanticNavToolService.class.getDeclaredMethod("appendDateRangeFields",
                    StringBuilder.class, Map.class, String.class);
            method.setAccessible(true);

            Map<String, Object> fields = new LinkedHashMap<>();
            fields.put("id", "doc1");
            fields.put("title", "My Title");
            fields.put("url", "http://example.com");
            fields.put("modification_date", "2025-06-01");
            fields.put("type", "Article");
            fields.put("description", "A description");

            StringBuilder sb = new StringBuilder();
            method.invoke(service, sb, fields, "modification_date");

            String result = sb.toString();
            assertThat(result)
                    .contains("id: doc1")
                    .contains("title: My Title")
                    .contains("url: http://example.com")
                    .contains("modification_date: 2025-06-01")
                    .contains("type: Article")
                    .contains("description: A description");
        }

        @Test
        void shouldTruncateLongValues() throws Exception {
            Method method = TurSemanticNavToolService.class.getDeclaredMethod("appendDateRangeFields",
                    StringBuilder.class, Map.class, String.class);
            method.setAccessible(true);

            Map<String, Object> fields = new LinkedHashMap<>();
            fields.put("description", "x".repeat(400));

            StringBuilder sb = new StringBuilder();
            method.invoke(service, sb, fields, "date");

            assertThat(sb.toString()).contains("...");
        }

        @Test
        void shouldNotTruncateExactly300() throws Exception {
            Method method = TurSemanticNavToolService.class.getDeclaredMethod("appendDateRangeFields",
                    StringBuilder.class, Map.class, String.class);
            method.setAccessible(true);

            Map<String, Object> fields = new LinkedHashMap<>();
            fields.put("description", "x".repeat(300));

            StringBuilder sb = new StringBuilder();
            method.invoke(service, sb, fields, "date");

            assertThat(sb.toString()).doesNotContain("...");
        }

        @Test
        void shouldSkipNullValues() throws Exception {
            Method method = TurSemanticNavToolService.class.getDeclaredMethod("appendDateRangeFields",
                    StringBuilder.class, Map.class, String.class);
            method.setAccessible(true);

            Map<String, Object> fields = new LinkedHashMap<>();
            fields.put("id", "doc1");
            fields.put("title", null);

            StringBuilder sb = new StringBuilder();
            method.invoke(service, sb, fields, "date");

            assertThat(sb.toString()).contains("id: doc1").doesNotContain("title:");
        }
    }

    @Nested
    class BuildAggregationStatsTests {

        @Test
        void shouldBuildFormattedAggregation() throws Exception {
            Method method = TurSemanticNavToolService.class.getDeclaredMethod("buildAggregationStats",
                    String.class, String.class, long.class, List.class);
            method.setAccessible(true);

            var value1 = createFacetFieldValue("Article", 50);
            var value2 = createFacetFieldValue("Page", 30);

            String result = (String) method.invoke(service, "test query", "type", 80L,
                    List.of(value1, value2));

            assertThat(result)
                    .contains("=== Aggregated Stats ===")
                    .contains("Query: 'test query'")
                    .contains("Total matching documents: 80")
                    .contains("Distribution by 'type':")
                    .contains("category;count")
                    .contains("Article;50")
                    .contains("Page;30");
        }
    }

    // ======================== formatSearchResults (private) ========================

    @Nested
    class FormatSearchResultsTests {

        @Test
        void shouldFormatResultsWithQueryContext() throws Exception {
            Method method = TurSemanticNavToolService.class.getDeclaredMethod("formatSearchResults",
                    TurSNDocumentList.class, String.class);
            method.setAccessible(true);

            TurSNDocumentList docList = createDocumentList(
                    List.of(createDocumentFields("doc1", "First Article", "http://example.com/1", "A description")),
                    42);

            String result = (String) method.invoke(service, docList, "test query");

            assertThat(result)
                    .contains("Found 42 results for query: test query")
                    .contains("--- Result 1 ---")
                    .contains("id: doc1")
                    .contains("title: First Article")
                    .contains("url: http://example.com/1")
                    .contains("description: A description")
                    .contains("TIP: Use get_document_details");
        }

        @Test
        void shouldFormatResultsWithoutQueryContext() throws Exception {
            Method method = TurSemanticNavToolService.class.getDeclaredMethod("formatSearchResults",
                    TurSNDocumentList.class, String.class);
            method.setAccessible(true);

            TurSNDocumentList docList = new TurSNDocumentList();
            docList.setTurSNDocuments(List.of(
                    createTurSNDocument(createDocumentFields("d1", "Title1", "http://a.com", "desc1")),
                    createTurSNDocument(createDocumentFields("d2", "Title2", "http://b.com", "desc2"))));
            // no queryContext set

            String result = (String) method.invoke(service, docList, "my search");

            assertThat(result)
                    .contains("Found 2 results for query: my search")
                    .contains("--- Result 1 ---")
                    .contains("--- Result 2 ---")
                    .contains("title: Title1")
                    .contains("title: Title2");
        }

        @Test
        void shouldFormatResultsWithAdditionalFields() throws Exception {
            Method method = TurSemanticNavToolService.class.getDeclaredMethod("formatSearchResults",
                    TurSNDocumentList.class, String.class);
            method.setAccessible(true);

            Map<String, Object> fields = new LinkedHashMap<>();
            fields.put("id", "doc1");
            fields.put("title", "My Title");
            fields.put("custom_short", "short value");
            fields.put("custom_long", "x".repeat(200)); // should be excluded (> 150)

            TurSNDocumentList docList = new TurSNDocumentList();
            docList.setTurSNDocuments(List.of(createTurSNDocument(fields)));

            String result = (String) method.invoke(service, docList, "q");

            assertThat(result)
                    .contains("custom_short: short value")
                    .doesNotContain("custom_long");
        }

        @Test
        void shouldSkipDocumentWithNullContent() throws Exception {
            Method method = TurSemanticNavToolService.class.getDeclaredMethod("formatSearchResults",
                    TurSNDocumentList.class, String.class);
            method.setAccessible(true);

            TurSNDocument docWithoutContent = new TurSNDocument();
            // no content set

            TurSNDocumentList docList = new TurSNDocumentList();
            docList.setTurSNDocuments(List.of(docWithoutContent));

            String result = (String) method.invoke(service, docList, "q");

            assertThat(result)
                    .contains("Found 1 results for query: q")
                    .contains("TIP: Use get_document_details")
                    .doesNotContain("--- Result 1 ---");
        }
    }

    // ======================== formatRecentUpdatesResponse (private) ========================

    @Nested
    class FormatRecentUpdatesResponseTests {

        @Test
        void shouldFormatRecentResultsWithQueryContext() throws Exception {
            Method method = TurSemanticNavToolService.class.getDeclaredMethod("formatRecentUpdatesResponse",
                    QueryTurSNResponse.class, String.class);
            method.setAccessible(true);

            TurSNDocumentList docList = createDocumentList(
                    List.of(createDocumentFields("r1", "Recent Article", "http://example.com/r1", "recent desc")),
                    100);

            QueryTurSNResponse response = new QueryTurSNResponse();
            response.setResults(docList);

            String result = (String) method.invoke(service, response, "latest");

            assertThat(result)
                    .contains("=== Latest Content ===")
                    .contains("Found 100 total results. Showing 1 most recent:")
                    .contains("id: r1")
                    .contains("title: Recent Article")
                    .contains("TIP: Use get_document_details");
        }

        @Test
        void shouldReturnNoRecentContentWhenEmpty() throws Exception {
            Method method = TurSemanticNavToolService.class.getDeclaredMethod("formatRecentUpdatesResponse",
                    QueryTurSNResponse.class, String.class);
            method.setAccessible(true);

            QueryTurSNResponse response = new QueryTurSNResponse();
            response.setResults(null);

            String result = (String) method.invoke(service, response, "nothing");

            assertThat(result).isEqualTo("No recent content found for query: nothing");
        }

        @Test
        void shouldReturnNoRecentContentWhenEmptyList() throws Exception {
            Method method = TurSemanticNavToolService.class.getDeclaredMethod("formatRecentUpdatesResponse",
                    QueryTurSNResponse.class, String.class);
            method.setAccessible(true);

            TurSNDocumentList docList = new TurSNDocumentList();
            docList.setTurSNDocuments(Collections.emptyList());
            QueryTurSNResponse response = new QueryTurSNResponse();
            response.setResults(docList);

            String result = (String) method.invoke(service, response, "empty");

            assertThat(result).isEqualTo("No recent content found for query: empty");
        }

        @Test
        void shouldFormatRecentWithoutQueryContext() throws Exception {
            Method method = TurSemanticNavToolService.class.getDeclaredMethod("formatRecentUpdatesResponse",
                    QueryTurSNResponse.class, String.class);
            method.setAccessible(true);

            TurSNDocumentList docList = new TurSNDocumentList();
            docList.setTurSNDocuments(List.of(
                    createTurSNDocument(createDocumentFields("d1", "T1", "http://a.com", "d1")),
                    createTurSNDocument(createDocumentFields("d2", "T2", "http://b.com", "d2"))));

            QueryTurSNResponse response = new QueryTurSNResponse();
            response.setResults(docList);

            String result = (String) method.invoke(service, response, "q");

            assertThat(result).contains("Found 2 total results. Showing 2 most recent:");
        }

        @Test
        void shouldSkipRecentDocWithNullContent() throws Exception {
            Method method = TurSemanticNavToolService.class.getDeclaredMethod("formatRecentUpdatesResponse",
                    QueryTurSNResponse.class, String.class);
            method.setAccessible(true);

            TurSNDocument docNoContent = new TurSNDocument();
            TurSNDocumentList docList = new TurSNDocumentList();
            docList.setTurSNDocuments(List.of(docNoContent));

            QueryTurSNResponse response = new QueryTurSNResponse();
            response.setResults(docList);

            String result = (String) method.invoke(service, response, "q");

            assertThat(result)
                    .contains("=== Latest Content ===")
                    .contains("--- 1 ---")
                    .doesNotContain("title:");
        }
    }

    // ======================== formatDateRangeResults (private) ========================

    @Nested
    class FormatDateRangeResultsTests {

        @Test
        void shouldFormatDateRangeWithQueryContext() throws Exception {
            Method method = TurSemanticNavToolService.class.getDeclaredMethod("formatDateRangeResults",
                    TurSNDocumentList.class, String.class, String.class, String.class, String.class);
            method.setAccessible(true);

            Map<String, Object> fields = new LinkedHashMap<>();
            fields.put("id", "dr1");
            fields.put("title", "Date Article");
            fields.put("url", "http://example.com/dr1");
            fields.put("modification_date", "2025-06-15");
            fields.put("type", "Article");
            fields.put("description", "A dated article");

            TurSNDocumentList docList = createDocumentList(List.of(fields), 25);

            String result = (String) method.invoke(service, docList, "test",
                    "2025-01-01", "2025-12-31", "modification_date");

            assertThat(result)
                    .contains("=== Date Range Search ===")
                    .contains("Query: 'test'")
                    .contains("Period: 2025-01-01 to 2025-12-31")
                    .contains("Found 25 results (showing 1):")
                    .contains("id: dr1")
                    .contains("title: Date Article")
                    .contains("modification_date: 2025-06-15")
                    .contains("TIP: Use get_document_details");
        }

        @Test
        void shouldFormatDateRangeWithoutQueryContext() throws Exception {
            Method method = TurSemanticNavToolService.class.getDeclaredMethod("formatDateRangeResults",
                    TurSNDocumentList.class, String.class, String.class, String.class, String.class);
            method.setAccessible(true);

            TurSNDocumentList docList = new TurSNDocumentList();
            docList.setTurSNDocuments(List.of(
                    createTurSNDocument(createDocumentFields("d1", "T1", "http://a.com", "desc"))));

            String result = (String) method.invoke(service, docList, "q",
                    "2025-01-01", "2025-06-30", "date");

            assertThat(result).contains("Found 1 results (showing 1):");
        }

        @Test
        void shouldSkipDateRangeDocWithNullContent() throws Exception {
            Method method = TurSemanticNavToolService.class.getDeclaredMethod("formatDateRangeResults",
                    TurSNDocumentList.class, String.class, String.class, String.class, String.class);
            method.setAccessible(true);

            TurSNDocument docNoContent = new TurSNDocument();
            TurSNDocumentList docList = new TurSNDocumentList();
            docList.setTurSNDocuments(List.of(docNoContent));

            String result = (String) method.invoke(service, docList, "q",
                    "2025-01-01", "2025-12-31", "date");

            assertThat(result)
                    .contains("=== Date Range Search ===")
                    .contains("--- 1 ---")
                    .doesNotContain("title:");
        }
    }

    // ======================== formatHighlightedDocument (private) ========================

    @Nested
    class FormatHighlightedDocumentTests {

        @Test
        void shouldFormatHighlightedFields() throws Exception {
            Method method = TurSemanticNavToolService.class.getDeclaredMethod("formatHighlightedDocument",
                    TurSNSiteSearchBean.class, String.class, String.class);
            method.setAccessible(true);

            Map<String, Object> fields = new LinkedHashMap<>();
            fields.put("title", "My <em>highlighted</em> Title");
            fields.put("text", "Some <b>bold</b> text content");

            TurSNSiteSearchBean searchBean = createSearchBeanWithDocument(fields);

            String result = (String) method.invoke(service, searchBean, "doc123", "highlighted");

            assertThat(result)
                    .contains("=== Highlighted Passages ===")
                    .contains("Document ID: doc123")
                    .contains("Search terms: 'highlighted'")
                    .contains("[title]: My <em>highlighted</em> Title")
                    .contains("[text]: Some <b>bold</b> text content");
        }

        @Test
        void shouldFallbackWhenNoHighlightTags() throws Exception {
            Method method = TurSemanticNavToolService.class.getDeclaredMethod("formatHighlightedDocument",
                    TurSNSiteSearchBean.class, String.class, String.class);
            method.setAccessible(true);

            Map<String, Object> fields = new LinkedHashMap<>();
            fields.put("title", "Plain Title");
            fields.put("description", "Plain Description");

            TurSNSiteSearchBean searchBean = createSearchBeanWithDocument(fields);

            String result = (String) method.invoke(service, searchBean, "doc456", "terms");

            assertThat(result)
                    .contains("=== Highlighted Passages ===")
                    .contains("No highlighted passages found")
                    .contains("[title]: Plain Title")
                    .contains("[description]: Plain Description");
        }

        @Test
        void shouldReturnNotFoundWhenNoResults() throws Exception {
            Method method = TurSemanticNavToolService.class.getDeclaredMethod("formatHighlightedDocument",
                    TurSNSiteSearchBean.class, String.class, String.class);
            method.setAccessible(true);

            TurSNSiteSearchBean searchBean = new TurSNSiteSearchBean();
            // no results set

            String result = (String) method.invoke(service, searchBean, "doc789", "search");

            assertThat(result).contains("Document not found with ID: doc789");
        }

        @Test
        void shouldReturnNotFoundWhenEmptyDocumentList() throws Exception {
            Method method = TurSemanticNavToolService.class.getDeclaredMethod("formatHighlightedDocument",
                    TurSNSiteSearchBean.class, String.class, String.class);
            method.setAccessible(true);

            TurSNSiteSearchResultsBean results = new TurSNSiteSearchResultsBean();
            results.setDocument(Collections.emptyList());
            TurSNSiteSearchBean searchBean = new TurSNSiteSearchBean();
            searchBean.setResults(results);

            String result = (String) method.invoke(service, searchBean, "doc000", "term");

            assertThat(result).contains("Document not found with ID: doc000");
        }

        @Test
        void shouldReturnNoContentWhenFieldsNull() throws Exception {
            Method method = TurSemanticNavToolService.class.getDeclaredMethod("formatHighlightedDocument",
                    TurSNSiteSearchBean.class, String.class, String.class);
            method.setAccessible(true);

            TurSNSiteSearchDocumentBean docBean = new TurSNSiteSearchDocumentBean();
            // fields is null
            TurSNSiteSearchResultsBean results = new TurSNSiteSearchResultsBean();
            results.setDocument(List.of(docBean));
            TurSNSiteSearchBean searchBean = new TurSNSiteSearchBean();
            searchBean.setResults(results);

            String result = (String) method.invoke(service, searchBean, "docX", "term");

            assertThat(result).contains("Document found but has no content fields.");
        }
    }

    // ======================== extractTotalCount (private) ========================

    @Nested
    class ExtractTotalCountTests {

        @Test
        void shouldExtractCountFromQueryContext() throws Exception {
            Method method = TurSemanticNavToolService.class.getDeclaredMethod("extractTotalCount",
                    QueryTurSNResponse.class);
            method.setAccessible(true);

            TurSNSiteSearchQueryContextBean ctx = new TurSNSiteSearchQueryContextBean();
            ctx.setCount(150);
            TurSNDocumentList docList = new TurSNDocumentList();
            docList.setQueryContext(ctx);
            QueryTurSNResponse response = new QueryTurSNResponse();
            response.setResults(docList);

            long result = (long) method.invoke(service, response);

            assertThat(result).isEqualTo(150);
        }

        @Test
        void shouldReturnZeroWhenNoResults() throws Exception {
            Method method = TurSemanticNavToolService.class.getDeclaredMethod("extractTotalCount",
                    QueryTurSNResponse.class);
            method.setAccessible(true);

            QueryTurSNResponse response = new QueryTurSNResponse();

            long result = (long) method.invoke(service, response);

            assertThat(result).isEqualTo(0);
        }

        @Test
        void shouldReturnZeroWhenNoQueryContext() throws Exception {
            Method method = TurSemanticNavToolService.class.getDeclaredMethod("extractTotalCount",
                    QueryTurSNResponse.class);
            method.setAccessible(true);

            TurSNDocumentList docList = new TurSNDocumentList();
            QueryTurSNResponse response = new QueryTurSNResponse();
            response.setResults(docList);

            long result = (long) method.invoke(service, response);

            assertThat(result).isEqualTo(0);
        }
    }

    // ======================== appendFacetFieldsSummary (private) ========================

    @Nested
    class AppendFacetFieldsSummaryTests {

        @Test
        void shouldFormatFacetFieldsWithValues() throws Exception {
            Method method = TurSemanticNavToolService.class.getDeclaredMethod("appendFacetFieldsSummary",
                    StringBuilder.class, TurSNFacetFieldList.class);
            method.setAccessible(true);

            TurSNFacetFieldList facetFieldList = createFacetFieldList(
                    List.of(createFacetBean("type", "Content Type",
                            List.of(createFacetItem("Article", 50), createFacetItem("Page", 30)))));

            StringBuilder sb = new StringBuilder();
            method.invoke(service, sb, facetFieldList);

            assertThat(sb.toString())
                    .contains(">> type (Content Type):")
                    .contains("   - Article (50)")
                    .contains("   - Page (30)");
        }

        @Test
        void shouldFormatFacetFieldWithoutLabel() throws Exception {
            Method method = TurSemanticNavToolService.class.getDeclaredMethod("appendFacetFieldsSummary",
                    StringBuilder.class, TurSNFacetFieldList.class);
            method.setAccessible(true);

            TurSNFacetFieldList facetFieldList = createFacetFieldList(
                    List.of(createFacetBean("category", null,
                            List.of(createFacetItem("Tech", 20)))));

            StringBuilder sb = new StringBuilder();
            method.invoke(service, sb, facetFieldList);

            assertThat(sb.toString())
                    .contains(">> category:")
                    .doesNotContain("(null)")
                    .contains("   - Tech (20)");
        }

        @Test
        void shouldFormatFacetFieldWithEmptyValues() throws Exception {
            Method method = TurSemanticNavToolService.class.getDeclaredMethod("appendFacetFieldsSummary",
                    StringBuilder.class, TurSNFacetFieldList.class);
            method.setAccessible(true);

            TurSNFacetFieldList facetFieldList = createFacetFieldList(
                    List.of(createFacetBean("empty_facet", "Empty", Collections.emptyList())));

            StringBuilder sb = new StringBuilder();
            method.invoke(service, sb, facetFieldList);

            assertThat(sb.toString())
                    .contains(">> empty_facet (Empty):")
                    .contains("(no values)");
        }
    }

    // ======================== appendFacetValues (private) ========================

    @Nested
    class AppendFacetValuesTests {

        @Test
        void shouldFormatNonEmptyValues() throws Exception {
            Method method = TurSemanticNavToolService.class.getDeclaredMethod("appendFacetValues",
                    StringBuilder.class, List.class);
            method.setAccessible(true);

            var v1 = createFacetFieldValue("News", 100);
            var v2 = createFacetFieldValue("Blog", 45);

            StringBuilder sb = new StringBuilder();
            method.invoke(service, sb, List.of(v1, v2));

            assertThat(sb.toString())
                    .contains("   - News (100)")
                    .contains("   - Blog (45)");
        }

        @Test
        void shouldFormatNullValues() throws Exception {
            Method method = TurSemanticNavToolService.class.getDeclaredMethod("appendFacetValues",
                    StringBuilder.class, List.class);
            method.setAccessible(true);

            StringBuilder sb = new StringBuilder();
            method.invoke(service, sb, (List<?>) null);

            assertThat(sb.toString()).contains("(no values)");
        }

        @Test
        void shouldFormatEmptyList() throws Exception {
            Method method = TurSemanticNavToolService.class.getDeclaredMethod("appendFacetValues",
                    StringBuilder.class, List.class);
            method.setAccessible(true);

            StringBuilder sb = new StringBuilder();
            method.invoke(service, sb, Collections.emptyList());

            assertThat(sb.toString()).contains("(no values)");
        }
    }

    // ======================== matchTermAgainstFacets (private) ========================

    @Nested
    class MatchTermAgainstFacetsTests {

        @Test
        void shouldMatchTermToFacetValue() throws Exception {
            Method method = TurSemanticNavToolService.class.getDeclaredMethod("matchTermAgainstFacets",
                    String.class, TurSNFacetFieldList.class);
            method.setAccessible(true);

            TurSNFacetFieldList facetFieldList = createFacetFieldList(
                    List.of(createFacetBean("category", "Category",
                            List.of(createFacetItem("Technology", 50), createFacetItem("Science", 30)))));

            String result = (String) method.invoke(service, "tech", facetFieldList);

            assertThat(result)
                    .contains("Facet values matching 'tech'")
                    .contains("Field: 'category'")
                    .contains("'Technology' (50 items)")
                    .contains("INSTRUCTION: Use the Field name");
        }

        @Test
        void shouldReturnNoMatchMessage() throws Exception {
            Method method = TurSemanticNavToolService.class.getDeclaredMethod("matchTermAgainstFacets",
                    String.class, TurSNFacetFieldList.class);
            method.setAccessible(true);

            TurSNFacetFieldList facetFieldList = createFacetFieldList(
                    List.of(createFacetBean("category", "Category",
                            List.of(createFacetItem("Technology", 50)))));

            String result = (String) method.invoke(service, "finance", facetFieldList);

            assertThat(result)
                    .startsWith("No facet values found matching 'finance'")
                    .contains("Do NOT use this term as a filter");
        }

        @Test
        void shouldSkipFacetsWithNullValues() throws Exception {
            Method method = TurSemanticNavToolService.class.getDeclaredMethod("matchTermAgainstFacets",
                    String.class, TurSNFacetFieldList.class);
            method.setAccessible(true);

            TurSNFacetFieldList facetFieldList = createFacetFieldList(
                    List.of(createFacetBean("empty", "Empty", null)));

            String result = (String) method.invoke(service, "test", facetFieldList);

            assertThat(result).startsWith("No facet values found matching 'test'");
        }

        @Test
        void shouldSkipFacetsWithEmptyValues() throws Exception {
            Method method = TurSemanticNavToolService.class.getDeclaredMethod("matchTermAgainstFacets",
                    String.class, TurSNFacetFieldList.class);
            method.setAccessible(true);

            TurSNFacetFieldList facetFieldList = createFacetFieldList(
                    List.of(createFacetBean("empty", "Empty", Collections.emptyList())));

            String result = (String) method.invoke(service, "test", facetFieldList);

            assertThat(result).startsWith("No facet values found matching 'test'");
        }

        @Test
        void shouldMatchAcrossMultipleFacetFields() throws Exception {
            Method method = TurSemanticNavToolService.class.getDeclaredMethod("matchTermAgainstFacets",
                    String.class, TurSNFacetFieldList.class);
            method.setAccessible(true);

            TurSNFacetFieldList facetFieldList = createFacetFieldList(List.of(
                    createFacetBean("category", "Category",
                            List.of(createFacetItem("Article", 10))),
                    createFacetBean("type", "Type",
                            List.of(createFacetItem("Article Page", 5)))));

            String result = (String) method.invoke(service, "article", facetFieldList);

            assertThat(result)
                    .contains("Field: 'category'")
                    .contains("'Article' (10 items)")
                    .contains("Field: 'type'")
                    .contains("'Article Page' (5 items)");
        }
    }

    // ======================== findAndFormatFacetAggregation (private) ========================

    @Nested
    class FindAndFormatFacetAggregationTests {

        @Test
        void shouldReturnAggregationForMatchingFacet() throws Exception {
            Method method = TurSemanticNavToolService.class.getDeclaredMethod("findAndFormatFacetAggregation",
                    TurSNFacetFieldList.class, String.class, String.class, long.class);
            method.setAccessible(true);

            TurSNFacetFieldList facetFieldList = createFacetFieldList(
                    List.of(createFacetBean("type", "Type",
                            List.of(createFacetItem("Article", 50), createFacetItem("Page", 30)))));

            String result = (String) method.invoke(service, facetFieldList, "type", "test", 80L);

            assertThat(result)
                    .contains("=== Aggregated Stats ===")
                    .contains("Query: 'test'")
                    .contains("Total matching documents: 80")
                    .contains("Distribution by 'type':")
                    .contains("Article;50")
                    .contains("Page;30");
        }

        @Test
        void shouldReturnNoAggregationForEmptyValues() throws Exception {
            Method method = TurSemanticNavToolService.class.getDeclaredMethod("findAndFormatFacetAggregation",
                    TurSNFacetFieldList.class, String.class, String.class, long.class);
            method.setAccessible(true);

            TurSNFacetFieldList facetFieldList = createFacetFieldList(
                    List.of(createFacetBean("type", "Type", Collections.emptyList())));

            String result = (String) method.invoke(service, facetFieldList, "type", "q", 10L);

            assertThat(result)
                    .contains("No aggregation data for field 'type'")
                    .contains("Total documents matching query 'q': 10");
        }

        @Test
        void shouldReturnNullForNonMatchingFacet() throws Exception {
            Method method = TurSemanticNavToolService.class.getDeclaredMethod("findAndFormatFacetAggregation",
                    TurSNFacetFieldList.class, String.class, String.class, long.class);
            method.setAccessible(true);

            TurSNFacetFieldList facetFieldList = createFacetFieldList(
                    List.of(createFacetBean("type", "Type",
                            List.of(createFacetItem("Article", 50)))));

            Object result = method.invoke(service, facetFieldList, "nonexistent", "q", 10L);

            assertThat(result).isNull();
        }

        @Test
        void shouldMatchFacetNameCaseInsensitive() throws Exception {
            Method method = TurSemanticNavToolService.class.getDeclaredMethod("findAndFormatFacetAggregation",
                    TurSNFacetFieldList.class, String.class, String.class, long.class);
            method.setAccessible(true);

            TurSNFacetFieldList facetFieldList = createFacetFieldList(
                    List.of(createFacetBean("Type", "Type",
                            List.of(createFacetItem("Article", 50)))));

            String result = (String) method.invoke(service, facetFieldList, "TYPE", "q", 10L);

            assertThat(result).contains("=== Aggregated Stats ===");
        }
    }

    // ======================== buildAvailableFacetsMessage (private) ========================

    @Nested
    class BuildAvailableFacetsMessageTests {

        @Test
        void shouldListAvailableFacets() throws Exception {
            Method method = TurSemanticNavToolService.class.getDeclaredMethod("buildAvailableFacetsMessage",
                    TurSNFacetFieldList.class, String.class, String.class, long.class);
            method.setAccessible(true);

            TurSNFacetFieldList facetFieldList = createFacetFieldList(List.of(
                    createFacetBean("type", "Type", List.of(createFacetItem("A", 1))),
                    createFacetBean("category", "Cat", List.of(createFacetItem("B", 2)))));

            String result = (String) method.invoke(service, facetFieldList, "invalid_field", "q", 55L);

            assertThat(result)
                    .contains("Field 'invalid_field' is not a valid facet")
                    .contains("Available facet fields for aggregation:")
                    .contains("type")
                    .contains("category")
                    .contains("Total documents matching query 'q': 55");
        }
    }

    // ======================== formatDocumentResult (private) ========================

    @Nested
    class FormatDocumentResultTests {

        @Test
        void shouldFormatDocumentWithFields() throws Exception {
            Method method = TurSemanticNavToolService.class.getDeclaredMethod("formatDocumentResult",
                    StringBuilder.class, Object.class, int.class);
            method.setAccessible(true);

            TurSNDocument doc = createTurSNDocument(
                    createDocumentFields("id1", "Test Title", "http://test.com", "Test desc"));

            StringBuilder sb = new StringBuilder();
            method.invoke(service, sb, doc, 3);

            assertThat(sb.toString())
                    .contains("--- Result 3 ---")
                    .contains("id: id1")
                    .contains("title: Test Title");
        }

        @Test
        void shouldHandleNonTurSNDocumentObject() throws Exception {
            Method method = TurSemanticNavToolService.class.getDeclaredMethod("formatDocumentResult",
                    StringBuilder.class, Object.class, int.class);
            method.setAccessible(true);

            StringBuilder sb = new StringBuilder();
            method.invoke(service, sb, "not a document", 1);

            assertThat(sb.toString())
                    .contains("--- Result 1 ---")
                    .doesNotContain("id:");
        }

        @Test
        void shouldHandleDocumentWithNullContent() throws Exception {
            Method method = TurSemanticNavToolService.class.getDeclaredMethod("formatDocumentResult",
                    StringBuilder.class, Object.class, int.class);
            method.setAccessible(true);

            TurSNDocument doc = new TurSNDocument();

            StringBuilder sb = new StringBuilder();
            method.invoke(service, sb, doc, 1);

            assertThat(sb.toString())
                    .contains("--- Result 1 ---")
                    .doesNotContain("id:");
        }
    }

    // ======================== formatRecentDocument (private) ========================

    @Nested
    class FormatRecentDocumentTests {

        @Test
        void shouldFormatRecentDocumentWithFields() throws Exception {
            Method method = TurSemanticNavToolService.class.getDeclaredMethod("formatRecentDocument",
                    StringBuilder.class, Object.class, int.class);
            method.setAccessible(true);

            Map<String, Object> fields = new LinkedHashMap<>();
            fields.put("id", "r1");
            fields.put("title", "Recent Title");
            fields.put("url", "http://recent.com");
            fields.put("date", "2025-06-01");
            fields.put("modification_date", "2025-06-15");
            fields.put("publication_date", "2025-06-01");
            fields.put("type", "Article");
            fields.put("description", "A recent article");

            TurSNDocument doc = createTurSNDocument(fields);

            StringBuilder sb = new StringBuilder();
            method.invoke(service, sb, doc, 2);

            assertThat(sb.toString())
                    .contains("--- 2 ---")
                    .contains("id: r1")
                    .contains("title: Recent Title")
                    .contains("url: http://recent.com")
                    .contains("date: 2025-06-01")
                    .contains("modification_date: 2025-06-15")
                    .contains("publication_date: 2025-06-01")
                    .contains("type: Article")
                    .contains("description: A recent article");
        }

        @Test
        void shouldHandleNonDocumentObject() throws Exception {
            Method method = TurSemanticNavToolService.class.getDeclaredMethod("formatRecentDocument",
                    StringBuilder.class, Object.class, int.class);
            method.setAccessible(true);

            StringBuilder sb = new StringBuilder();
            method.invoke(service, sb, "not a document", 1);

            assertThat(sb.toString())
                    .contains("--- 1 ---")
                    .doesNotContain("id:");
        }
    }

    // ======================== appendDateRangeDocument (private) ========================

    @Nested
    class AppendDateRangeDocumentTests {

        @Test
        void shouldFormatDateRangeDocument() throws Exception {
            Method method = TurSemanticNavToolService.class.getDeclaredMethod("appendDateRangeDocument",
                    StringBuilder.class, Object.class, int.class, String.class);
            method.setAccessible(true);

            Map<String, Object> fields = new LinkedHashMap<>();
            fields.put("id", "drd1");
            fields.put("title", "Date Doc");
            fields.put("custom_date", "2025-03-15");

            TurSNDocument doc = createTurSNDocument(fields);

            StringBuilder sb = new StringBuilder();
            method.invoke(service, sb, doc, 5, "custom_date");

            assertThat(sb.toString())
                    .contains("--- 5 ---")
                    .contains("id: drd1")
                    .contains("title: Date Doc")
                    .contains("custom_date: 2025-03-15");
        }

        @Test
        void shouldHandleNonDocumentForDateRange() throws Exception {
            Method method = TurSemanticNavToolService.class.getDeclaredMethod("appendDateRangeDocument",
                    StringBuilder.class, Object.class, int.class, String.class);
            method.setAccessible(true);

            StringBuilder sb = new StringBuilder();
            method.invoke(service, sb, "not a doc", 1, "date");

            assertThat(sb.toString())
                    .contains("--- 1 ---")
                    .doesNotContain("id:");
        }
    }

    // ======================== isEmptyResults extended ========================

    @Nested
    class IsEmptyResultsExtendedTests {

        @Test
        void shouldReturnTrueForNullDocList() throws Exception {
            Method method = TurSemanticNavToolService.class.getDeclaredMethod("isEmptyResults",
                    TurSNDocumentList.class);
            method.setAccessible(true);

            TurSNDocumentList docList = new TurSNDocumentList();
            docList.setTurSNDocuments(null);

            assertThat((boolean) method.invoke(service, docList)).isTrue();
        }

        @Test
        void shouldReturnTrueForEmptyDocList() throws Exception {
            Method method = TurSemanticNavToolService.class.getDeclaredMethod("isEmptyResults",
                    TurSNDocumentList.class);
            method.setAccessible(true);

            TurSNDocumentList docList = new TurSNDocumentList();
            docList.setTurSNDocuments(Collections.emptyList());

            assertThat((boolean) method.invoke(service, docList)).isTrue();
        }

        @Test
        void shouldReturnFalseForNonEmptyDocList() throws Exception {
            Method method = TurSemanticNavToolService.class.getDeclaredMethod("isEmptyResults",
                    TurSNDocumentList.class);
            method.setAccessible(true);

            TurSNDocumentList docList = new TurSNDocumentList();
            docList.setTurSNDocuments(List.of(new TurSNDocument()));

            assertThat((boolean) method.invoke(service, docList)).isFalse();
        }
    }

    // ======================== fetchDocumentsForComparison (private) ========================

    @Nested
    class FetchDocumentsForComparisonTests {

        @Test
        void shouldBuildDocumentsMapWithMissingFields() throws Exception {
            // fetchDocumentsForComparison calls fetchDocumentFields which makes HTTP calls
            // that will fail, so all docs get fillMissingFields
            Method method = TurSemanticNavToolService.class.getDeclaredMethod("fillMissingFields",
                    String[].class, Map.class);
            method.setAccessible(true);

            String[] fieldNames = {"title", "description", "type"};
            Map<String, String> result = new LinkedHashMap<>();
            method.invoke(service, fieldNames, result);

            assertThat(result).hasSize(3);
            assertThat(result.get("title")).isEqualTo("(document not found)");
            assertThat(result.get("description")).isEqualTo("(document not found)");
            assertThat(result.get("type")).isEqualTo("(document not found)");
        }
    }

    // ======================== Multiple documents in formatSearchResults ========================

    @Nested
    class FormatSearchResultsMultiDocTests {

        @Test
        void shouldFormatMultipleDocumentsWithMixedContent() throws Exception {
            Method method = TurSemanticNavToolService.class.getDeclaredMethod("formatSearchResults",
                    TurSNDocumentList.class, String.class);
            method.setAccessible(true);

            Map<String, Object> fields1 = new LinkedHashMap<>();
            fields1.put("id", "doc1");
            fields1.put("title", "First Doc");
            fields1.put("url", "http://a.com");
            fields1.put("description", "First description");
            fields1.put("date", "2025-01-01");
            fields1.put("type", "Article");
            fields1.put("modification_date", "2025-02-01");
            fields1.put("publication_date", "2025-01-01");
            fields1.put("author", "John");
            fields1.put("source_apps", "CMS");
            fields1.put("extra_short", "val");

            Map<String, Object> fields2 = new LinkedHashMap<>();
            fields2.put("id", "doc2");
            fields2.put("title", "Second Doc");
            fields2.put("description", "x".repeat(400)); // will be truncated in summary

            TurSNDocumentList docList = createDocumentList(List.of(fields1, fields2), 200);

            String result = (String) method.invoke(service, docList, "multi test");

            assertThat(result)
                    .contains("Found 200 results for query: multi test")
                    .contains("--- Result 1 ---")
                    .contains("--- Result 2 ---")
                    .contains("id: doc1")
                    .contains("title: First Doc")
                    .contains("author: John")
                    .contains("source_apps: CMS")
                    .contains("extra_short: val")
                    .contains("id: doc2")
                    .contains("title: Second Doc")
                    .contains("...");
        }
    }

    // ======================== formatHighlightedDocument with long content ========================

    @Nested
    class FormatHighlightedDocumentExtendedTests {

        @Test
        void shouldTruncateLongHighlightedContent() throws Exception {
            Method method = TurSemanticNavToolService.class.getDeclaredMethod("formatHighlightedDocument",
                    TurSNSiteSearchBean.class, String.class, String.class);
            method.setAccessible(true);

            Map<String, Object> fields = new LinkedHashMap<>();
            fields.put("text", "<em>found</em>" + "x".repeat(4000));

            TurSNSiteSearchBean searchBean = createSearchBeanWithDocument(fields);

            String result = (String) method.invoke(service, searchBean, "doc1", "found");

            assertThat(result)
                    .contains("=== Highlighted Passages ===")
                    .contains("[TRUNCATED]");
        }

        @Test
        void shouldShowFallbackFieldsWhenNoHighlightAndHasText() throws Exception {
            Method method = TurSemanticNavToolService.class.getDeclaredMethod("formatHighlightedDocument",
                    TurSNSiteSearchBean.class, String.class, String.class);
            method.setAccessible(true);

            Map<String, Object> fields = new LinkedHashMap<>();
            fields.put("title", "No Highlights Title");
            fields.put("description", "Plain description text");
            fields.put("text", "Full text body");
            fields.put("abstract", "Abstract section");

            TurSNSiteSearchBean searchBean = createSearchBeanWithDocument(fields);

            String result = (String) method.invoke(service, searchBean, "doc1", "terms");

            assertThat(result)
                    .contains("No highlighted passages found")
                    .contains("[title]: No Highlights Title")
                    .contains("[description]: Plain description text")
                    .contains("[text]: Full text body")
                    .contains("[abstract]: Abstract section");
        }

        @Test
        void shouldTruncateLongFallbackContent() throws Exception {
            Method method = TurSemanticNavToolService.class.getDeclaredMethod("formatHighlightedDocument",
                    TurSNSiteSearchBean.class, String.class, String.class);
            method.setAccessible(true);

            Map<String, Object> fields = new LinkedHashMap<>();
            fields.put("text", "x".repeat(3000)); // > 2000, should be truncated

            TurSNSiteSearchBean searchBean = createSearchBeanWithDocument(fields);

            String result = (String) method.invoke(service, searchBean, "doc1", "terms");

            assertThat(result)
                    .contains("No highlighted passages found")
                    .contains("[TRUNCATED]");
        }
    }

    // ======================== matchTermAgainstFacets extended ========================

    @Nested
    class MatchTermAgainstFacetsExtendedTests {

        @Test
        void shouldMatchStemmedTermAgainstFacetValue() throws Exception {
            Method method = TurSemanticNavToolService.class.getDeclaredMethod("matchTermAgainstFacets",
                    String.class, TurSNFacetFieldList.class);
            method.setAccessible(true);

            // "artigos" stems to "artig" which should match "Artigo" (stemmed to "artig")
            TurSNFacetFieldList facetFieldList = createFacetFieldList(
                    List.of(createFacetBean("type", "Type",
                            List.of(createFacetItem("Artigo", 25)))));

            String result = (String) method.invoke(service, "artigos", facetFieldList);

            assertThat(result)
                    .contains("Field: 'type'")
                    .contains("'Artigo' (25 items)");
        }

        @Test
        void shouldHandleNullLabelInFacetValues() throws Exception {
            Method method = TurSemanticNavToolService.class.getDeclaredMethod("matchTermAgainstFacets",
                    String.class, TurSNFacetFieldList.class);
            method.setAccessible(true);

            TurSNSiteSearchFacetItemBean itemWithNullLabel = new TurSNSiteSearchFacetItemBean();
            itemWithNullLabel.setLabel(null);
            itemWithNullLabel.setCount(10);

            TurSNFacetFieldList facetFieldList = createFacetFieldList(
                    List.of(createFacetBean("type", "Type",
                            List.of(itemWithNullLabel))));

            String result = (String) method.invoke(service, "test", facetFieldList);

            assertThat(result).startsWith("No facet values found matching 'test'");
        }
    }

    // ======================== findAndFormatFacetAggregation with null values ========================

    @Nested
    class FindAndFormatFacetAggregationExtendedTests {

        @Test
        void shouldReturnNoAggregationForNullValues() throws Exception {
            Method method = TurSemanticNavToolService.class.getDeclaredMethod("findAndFormatFacetAggregation",
                    TurSNFacetFieldList.class, String.class, String.class, long.class);
            method.setAccessible(true);

            TurSNFacetFieldList facetFieldList = createFacetFieldList(
                    List.of(createFacetBean("type", "Type", null)));

            String result = (String) method.invoke(service, facetFieldList, "type", "q", 10L);

            assertThat(result)
                    .contains("No aggregation data for field 'type'")
                    .contains("Total documents matching query 'q': 10");
        }

        @Test
        void shouldSkipFacetsWithNullName() throws Exception {
            Method method = TurSemanticNavToolService.class.getDeclaredMethod("findAndFormatFacetAggregation",
                    TurSNFacetFieldList.class, String.class, String.class, long.class);
            method.setAccessible(true);

            TurSNFacetFieldList facetFieldList = createFacetFieldList(
                    List.of(createFacetBean(null, "NoName", List.of(createFacetItem("A", 1)))));

            Object result = method.invoke(service, facetFieldList, "type", "q", 5L);

            assertThat(result).isNull();
        }
    }

    // ======================== formatDateRangeResults with long description ========================

    @Nested
    class FormatDateRangeResultsExtendedTests {

        @Test
        void shouldTruncateLongDescriptionInDateRange() throws Exception {
            Method method = TurSemanticNavToolService.class.getDeclaredMethod("formatDateRangeResults",
                    TurSNDocumentList.class, String.class, String.class, String.class, String.class);
            method.setAccessible(true);

            Map<String, Object> fields = new LinkedHashMap<>();
            fields.put("id", "dr1");
            fields.put("title", "Title");
            fields.put("description", "x".repeat(400)); // > 300 should be truncated

            TurSNDocumentList docList = createDocumentList(List.of(fields), 10);

            String result = (String) method.invoke(service, docList, "q",
                    "2025-01-01", "2025-12-31", "date");

            assertThat(result).contains("...");
        }

        @Test
        void shouldFormatMultipleDateRangeDocs() throws Exception {
            Method method = TurSemanticNavToolService.class.getDeclaredMethod("formatDateRangeResults",
                    TurSNDocumentList.class, String.class, String.class, String.class, String.class);
            method.setAccessible(true);

            TurSNDocumentList docList = createDocumentList(
                    List.of(
                            createDocumentFields("d1", "First", "http://a.com", "desc1"),
                            createDocumentFields("d2", "Second", "http://b.com", "desc2")),
                    50);

            String result = (String) method.invoke(service, docList, "q",
                    "2025-01-01", "2025-12-31", "date");

            assertThat(result)
                    .contains("--- 1 ---")
                    .contains("--- 2 ---")
                    .contains("Found 50 results (showing 2):");
        }
    }

    // ======================== formatRecentUpdatesResponse with all field types ========================

    @Nested
    class FormatRecentUpdatesResponseExtendedTests {

        @Test
        void shouldFormatRecentDocWithAllFields() throws Exception {
            Method method = TurSemanticNavToolService.class.getDeclaredMethod("formatRecentUpdatesResponse",
                    QueryTurSNResponse.class, String.class);
            method.setAccessible(true);

            Map<String, Object> fields = new LinkedHashMap<>();
            fields.put("id", "r1");
            fields.put("title", "Recent Title");
            fields.put("url", "http://recent.com/r1");
            fields.put("date", "2025-06-01");
            fields.put("modification_date", "2025-06-15");
            fields.put("publication_date", "2025-06-01");
            fields.put("type", "Article");
            fields.put("description", "Full description here");

            TurSNDocumentList docList = createDocumentList(List.of(fields), 99);
            QueryTurSNResponse response = new QueryTurSNResponse();
            response.setResults(docList);

            String result = (String) method.invoke(service, response, "recent search");

            assertThat(result)
                    .contains("=== Latest Content ===")
                    .contains("Found 99 total results. Showing 1 most recent:")
                    .contains("id: r1")
                    .contains("title: Recent Title")
                    .contains("url: http://recent.com/r1")
                    .contains("date: 2025-06-01")
                    .contains("modification_date: 2025-06-15")
                    .contains("publication_date: 2025-06-01")
                    .contains("type: Article")
                    .contains("description: Full description here");
        }

        @Test
        void shouldTruncateLongRecentDescription() throws Exception {
            Method method = TurSemanticNavToolService.class.getDeclaredMethod("formatRecentUpdatesResponse",
                    QueryTurSNResponse.class, String.class);
            method.setAccessible(true);

            Map<String, Object> fields = new LinkedHashMap<>();
            fields.put("description", "x".repeat(400));

            TurSNDocumentList docList = createDocumentList(List.of(fields), 1);
            QueryTurSNResponse response = new QueryTurSNResponse();
            response.setResults(docList);

            String result = (String) method.invoke(service, response, "q");

            assertThat(result).contains("...");
        }
    }

    // ======================== appendFacetFieldsSummary extended ========================

    @Nested
    class AppendFacetFieldsSummaryExtendedTests {

        @Test
        void shouldFormatMultipleFacetFields() throws Exception {
            Method method = TurSemanticNavToolService.class.getDeclaredMethod("appendFacetFieldsSummary",
                    StringBuilder.class, TurSNFacetFieldList.class);
            method.setAccessible(true);

            TurSNFacetFieldList facetFieldList = createFacetFieldList(List.of(
                    createFacetBean("type", "Content Type",
                            List.of(createFacetItem("Article", 50), createFacetItem("Page", 30))),
                    createFacetBean("source", null,
                            List.of(createFacetItem("CMS", 80)))));

            StringBuilder sb = new StringBuilder();
            method.invoke(service, sb, facetFieldList);

            assertThat(sb.toString())
                    .contains(">> type (Content Type):")
                    .contains("   - Article (50)")
                    .contains("   - Page (30)")
                    .contains(">> source:")
                    .contains("   - CMS (80)");
        }

        @Test
        void shouldFormatFacetWithNullValues() throws Exception {
            Method method = TurSemanticNavToolService.class.getDeclaredMethod("appendFacetFieldsSummary",
                    StringBuilder.class, TurSNFacetFieldList.class);
            method.setAccessible(true);

            TurSNFacetFieldList facetFieldList = createFacetFieldList(
                    List.of(createFacetBean("empty", "Empty", null)));

            StringBuilder sb = new StringBuilder();
            method.invoke(service, sb, facetFieldList);

            assertThat(sb.toString())
                    .contains(">> empty (Empty):")
                    .contains("(no values)");
        }
    }

    // ======================== executeSearchQuery (private, with mocked server) ========================

    @Nested
    class ExecuteSearchQueryTests {

        @Test
        void shouldExecuteWithDefaultsWhenNullParams() throws Exception {
            Method method = TurSemanticNavToolService.class.getDeclaredMethod("executeSearchQuery",
                    HttpTurSNServer.class, String.class, Integer.class, Integer.class, String.class);
            method.setAccessible(true);

            HttpTurSNServer mockServer = mock(HttpTurSNServer.class);
            QueryTurSNResponse expectedResponse = new QueryTurSNResponse();
            when(mockServer.query(any())).thenReturn(expectedResponse);

            QueryTurSNResponse result = (QueryTurSNResponse) method.invoke(service,
                    mockServer, null, null, null, null);

            assertThat(result).isSameAs(expectedResponse);
        }

        @Test
        void shouldExecuteWithBlankQuery() throws Exception {
            Method method = TurSemanticNavToolService.class.getDeclaredMethod("executeSearchQuery",
                    HttpTurSNServer.class, String.class, Integer.class, Integer.class, String.class);
            method.setAccessible(true);

            HttpTurSNServer mockServer = mock(HttpTurSNServer.class);
            QueryTurSNResponse expectedResponse = new QueryTurSNResponse();
            when(mockServer.query(any())).thenReturn(expectedResponse);

            QueryTurSNResponse result = (QueryTurSNResponse) method.invoke(service,
                    mockServer, "  ", Integer.valueOf(5), Integer.valueOf(2), null);

            assertThat(result).isSameAs(expectedResponse);
        }

        @Test
        void shouldExecuteWithFilterQueries() throws Exception {
            Method method = TurSemanticNavToolService.class.getDeclaredMethod("executeSearchQuery",
                    HttpTurSNServer.class, String.class, Integer.class, Integer.class, String.class);
            method.setAccessible(true);

            HttpTurSNServer mockServer = mock(HttpTurSNServer.class);
            QueryTurSNResponse expectedResponse = new QueryTurSNResponse();
            when(mockServer.query(any())).thenReturn(expectedResponse);

            QueryTurSNResponse result = (QueryTurSNResponse) method.invoke(service,
                    mockServer, "search term", Integer.valueOf(10), Integer.valueOf(1), "type:Article|source:CMS");

            assertThat(result).isSameAs(expectedResponse);
        }

        @Test
        void shouldExecuteWithBlankFilterQueries() throws Exception {
            Method method = TurSemanticNavToolService.class.getDeclaredMethod("executeSearchQuery",
                    HttpTurSNServer.class, String.class, Integer.class, Integer.class, String.class);
            method.setAccessible(true);

            HttpTurSNServer mockServer = mock(HttpTurSNServer.class);
            QueryTurSNResponse expectedResponse = new QueryTurSNResponse();
            when(mockServer.query(any())).thenReturn(expectedResponse);

            QueryTurSNResponse result = (QueryTurSNResponse) method.invoke(service,
                    mockServer, "test", Integer.valueOf(10), Integer.valueOf(1), "   ");

            assertThat(result).isSameAs(expectedResponse);
        }

        @Test
        void shouldExecuteWithZeroRowsAndPage() throws Exception {
            Method method = TurSemanticNavToolService.class.getDeclaredMethod("executeSearchQuery",
                    HttpTurSNServer.class, String.class, Integer.class, Integer.class, String.class);
            method.setAccessible(true);

            HttpTurSNServer mockServer = mock(HttpTurSNServer.class);
            QueryTurSNResponse expectedResponse = new QueryTurSNResponse();
            when(mockServer.query(any())).thenReturn(expectedResponse);

            QueryTurSNResponse result = (QueryTurSNResponse) method.invoke(service,
                    mockServer, "test", Integer.valueOf(0), Integer.valueOf(0), null);

            assertThat(result).isSameAs(expectedResponse);
        }

        @Test
        void shouldExecuteWithNegativeRowsAndPage() throws Exception {
            Method method = TurSemanticNavToolService.class.getDeclaredMethod("executeSearchQuery",
                    HttpTurSNServer.class, String.class, Integer.class, Integer.class, String.class);
            method.setAccessible(true);

            HttpTurSNServer mockServer = mock(HttpTurSNServer.class);
            QueryTurSNResponse expectedResponse = new QueryTurSNResponse();
            when(mockServer.query(any())).thenReturn(expectedResponse);

            QueryTurSNResponse result = (QueryTurSNResponse) method.invoke(service,
                    mockServer, "test", Integer.valueOf(-5), Integer.valueOf(-1), null);

            assertThat(result).isSameAs(expectedResponse);
        }
    }

    // ======================== executeAggregationQuery (private, with mocked server) ========================

    @Nested
    class ExecuteAggregationQueryTests {

        @Test
        void shouldExecuteWithNullQueryAndFilters() throws Exception {
            Method method = TurSemanticNavToolService.class.getDeclaredMethod("executeAggregationQuery",
                    HttpTurSNServer.class, String.class, String.class);
            method.setAccessible(true);

            HttpTurSNServer mockServer = mock(HttpTurSNServer.class);
            QueryTurSNResponse expectedResponse = new QueryTurSNResponse();
            when(mockServer.query(any())).thenReturn(expectedResponse);

            QueryTurSNResponse result = (QueryTurSNResponse) method.invoke(service,
                    mockServer, null, null);

            assertThat(result).isSameAs(expectedResponse);
        }

        @Test
        void shouldExecuteWithBlankQuery() throws Exception {
            Method method = TurSemanticNavToolService.class.getDeclaredMethod("executeAggregationQuery",
                    HttpTurSNServer.class, String.class, String.class);
            method.setAccessible(true);

            HttpTurSNServer mockServer = mock(HttpTurSNServer.class);
            QueryTurSNResponse expectedResponse = new QueryTurSNResponse();
            when(mockServer.query(any())).thenReturn(expectedResponse);

            QueryTurSNResponse result = (QueryTurSNResponse) method.invoke(service,
                    mockServer, "  ", "  ");

            assertThat(result).isSameAs(expectedResponse);
        }

        @Test
        void shouldExecuteWithFilterQueries() throws Exception {
            Method method = TurSemanticNavToolService.class.getDeclaredMethod("executeAggregationQuery",
                    HttpTurSNServer.class, String.class, String.class);
            method.setAccessible(true);

            HttpTurSNServer mockServer = mock(HttpTurSNServer.class);
            QueryTurSNResponse expectedResponse = new QueryTurSNResponse();
            when(mockServer.query(any())).thenReturn(expectedResponse);

            QueryTurSNResponse result = (QueryTurSNResponse) method.invoke(service,
                    mockServer, "my query", "type:Article|source:CMS");

            assertThat(result).isSameAs(expectedResponse);
        }
    }

    // ======================== executeDateRangeQuery (private, with mocked server) ========================

    @Nested
    class ExecuteDateRangeQueryTests {

        @Test
        void shouldExecuteWithAllParams() throws Exception {
            Method method = TurSemanticNavToolService.class.getDeclaredMethod("executeDateRangeQuery",
                    HttpTurSNServer.class, String.class, Integer.class, String.class, String.class, String.class);
            method.setAccessible(true);

            HttpTurSNServer mockServer = mock(HttpTurSNServer.class);
            QueryTurSNResponse expectedResponse = new QueryTurSNResponse();
            when(mockServer.query(any())).thenReturn(expectedResponse);

            QueryTurSNResponse result = (QueryTurSNResponse) method.invoke(service,
                    mockServer, "search", Integer.valueOf(10), "modification_date",
                    "2025-01-01", "2025-12-31");

            assertThat(result).isSameAs(expectedResponse);
        }

        @Test
        void shouldExecuteWithNullQueryAndRows() throws Exception {
            Method method = TurSemanticNavToolService.class.getDeclaredMethod("executeDateRangeQuery",
                    HttpTurSNServer.class, String.class, Integer.class, String.class, String.class, String.class);
            method.setAccessible(true);

            HttpTurSNServer mockServer = mock(HttpTurSNServer.class);
            QueryTurSNResponse expectedResponse = new QueryTurSNResponse();
            when(mockServer.query(any())).thenReturn(expectedResponse);

            QueryTurSNResponse result = (QueryTurSNResponse) method.invoke(service,
                    mockServer, null, null, "date", "2025-01-01", "2025-06-30");

            assertThat(result).isSameAs(expectedResponse);
        }

        @Test
        void shouldExecuteWithBlankQueryAndZeroRows() throws Exception {
            Method method = TurSemanticNavToolService.class.getDeclaredMethod("executeDateRangeQuery",
                    HttpTurSNServer.class, String.class, Integer.class, String.class, String.class, String.class);
            method.setAccessible(true);

            HttpTurSNServer mockServer = mock(HttpTurSNServer.class);
            QueryTurSNResponse expectedResponse = new QueryTurSNResponse();
            when(mockServer.query(any())).thenReturn(expectedResponse);

            QueryTurSNResponse result = (QueryTurSNResponse) method.invoke(service,
                    mockServer, "  ", Integer.valueOf(0), "date", "2025-01-01", "2025-12-31");

            assertThat(result).isSameAs(expectedResponse);
        }
    }

    // ======================== fetchDocumentFields (private, with mocked server) ========================

    @Nested
    class FetchDocumentFieldsTests {

        @Test
        void shouldFetchFieldsFromDocument() throws Exception {
            Method method = TurSemanticNavToolService.class.getDeclaredMethod("fetchDocumentFields",
                    HttpTurSNServer.class, String.class, String[].class);
            method.setAccessible(true);

            HttpTurSNServer mockServer = mock(HttpTurSNServer.class);
            TurSNDocumentList docList = createDocumentList(
                    List.of(createDocumentFields("doc1", "My Title", "http://test.com", "A description")), 1);
            QueryTurSNResponse response = new QueryTurSNResponse();
            response.setResults(docList);
            when(mockServer.query(any())).thenReturn(response);

            String[] fieldNames = {"title", "description"};
            @SuppressWarnings("unchecked")
            Map<String, String> result = (Map<String, String>) method.invoke(service,
                    mockServer, "doc1", fieldNames);

            assertThat(result)
                    .containsEntry("title", "My Title")
                    .containsEntry("description", "A description");
        }

        @Test
        void shouldReturnNotFoundWhenNoResults() throws Exception {
            Method method = TurSemanticNavToolService.class.getDeclaredMethod("fetchDocumentFields",
                    HttpTurSNServer.class, String.class, String[].class);
            method.setAccessible(true);

            HttpTurSNServer mockServer = mock(HttpTurSNServer.class);
            TurSNDocumentList docList = new TurSNDocumentList();
            docList.setTurSNDocuments(Collections.emptyList());
            QueryTurSNResponse response = new QueryTurSNResponse();
            response.setResults(docList);
            when(mockServer.query(any())).thenReturn(response);

            String[] fieldNames = {"title", "type"};
            @SuppressWarnings("unchecked")
            Map<String, String> result = (Map<String, String>) method.invoke(service,
                    mockServer, "missing_doc", fieldNames);

            assertThat(result)
                    .containsEntry("title", "(document not found)")
                    .containsEntry("type", "(document not found)");
        }

        @Test
        void shouldReturnNotFoundWhenNullResults() throws Exception {
            Method method = TurSemanticNavToolService.class.getDeclaredMethod("fetchDocumentFields",
                    HttpTurSNServer.class, String.class, String[].class);
            method.setAccessible(true);

            HttpTurSNServer mockServer = mock(HttpTurSNServer.class);
            QueryTurSNResponse response = new QueryTurSNResponse();
            when(mockServer.query(any())).thenReturn(response);

            String[] fieldNames = {"title"};
            @SuppressWarnings("unchecked")
            Map<String, String> result = (Map<String, String>) method.invoke(service,
                    mockServer, "missing_doc", fieldNames);

            assertThat(result).containsEntry("title", "(document not found)");
        }

        @Test
        void shouldHandleDocWithNullContent() throws Exception {
            Method method = TurSemanticNavToolService.class.getDeclaredMethod("fetchDocumentFields",
                    HttpTurSNServer.class, String.class, String[].class);
            method.setAccessible(true);

            HttpTurSNServer mockServer = mock(HttpTurSNServer.class);
            TurSNDocument docNoContent = new TurSNDocument();
            TurSNDocumentList docList = new TurSNDocumentList();
            docList.setTurSNDocuments(List.of(docNoContent));
            QueryTurSNResponse response = new QueryTurSNResponse();
            response.setResults(docList);
            when(mockServer.query(any())).thenReturn(response);

            String[] fieldNames = {"title"};
            @SuppressWarnings("unchecked")
            Map<String, String> result = (Map<String, String>) method.invoke(service,
                    mockServer, "doc1", fieldNames);

            // Content is null so fieldValues should be empty (no extraction, no fillMissing)
            assertThat(result).isEmpty();
        }

        @Test
        void shouldTruncateLongFieldValues() throws Exception {
            Method method = TurSemanticNavToolService.class.getDeclaredMethod("fetchDocumentFields",
                    HttpTurSNServer.class, String.class, String[].class);
            method.setAccessible(true);

            HttpTurSNServer mockServer = mock(HttpTurSNServer.class);
            Map<String, Object> fields = new LinkedHashMap<>();
            fields.put("text", "x".repeat(600));
            TurSNDocumentList docList = createDocumentList(List.of(fields), 1);
            QueryTurSNResponse response = new QueryTurSNResponse();
            response.setResults(docList);
            when(mockServer.query(any())).thenReturn(response);

            String[] fieldNames = {"text"};
            @SuppressWarnings("unchecked")
            Map<String, String> result = (Map<String, String>) method.invoke(service,
                    mockServer, "doc1", fieldNames);

            assertThat(result.get("text")).endsWith("...");
            assertThat(result.get("text")).hasSize(503);
        }
    }

    // ======================== fetchDocumentsForComparison (private, with mocked server) ========================

    @Nested
    class FetchDocumentsForComparisonWithMockTests {

        @Test
        void shouldFetchMultipleDocuments() throws Exception {
            Method method = TurSemanticNavToolService.class.getDeclaredMethod("fetchDocumentsForComparison",
                    HttpTurSNServer.class, String[].class, String[].class);
            method.setAccessible(true);

            HttpTurSNServer mockServer = mock(HttpTurSNServer.class);

            Map<String, Object> fields1 = new LinkedHashMap<>();
            fields1.put("title", "Doc A");
            fields1.put("type", "Article");
            TurSNDocumentList docList1 = createDocumentList(List.of(fields1), 1);

            Map<String, Object> fields2 = new LinkedHashMap<>();
            fields2.put("title", "Doc B");
            fields2.put("type", "Page");
            TurSNDocumentList docList2 = createDocumentList(List.of(fields2), 1);

            QueryTurSNResponse response1 = new QueryTurSNResponse();
            response1.setResults(docList1);
            QueryTurSNResponse response2 = new QueryTurSNResponse();
            response2.setResults(docList2);

            when(mockServer.query(any())).thenReturn(response1, response2);

            String[] ids = {"id1", " id2 "};
            String[] fieldNames = {"title", "type"};

            @SuppressWarnings("unchecked")
            Map<String, Map<String, String>> result = (Map<String, Map<String, String>>) method.invoke(service,
                    mockServer, ids, fieldNames);

            assertThat(result).hasSize(2);
            assertThat(result.get("id1")).containsEntry("title", "Doc A");
            assertThat(result.get("id2")).containsEntry("title", "Doc B");
        }
    }

    // ======================== Public @Tool methods with MockedConstruction ========================

    @Nested
    class SearchSiteWithMockedServerTests {

        @Test
        void shouldReturnFormattedResultsOnSuccess() {
            TurSNDocumentList docList = createDocumentList(
                    List.of(createDocumentFields("doc1", "Found Article", "http://a.com", "desc")), 42);
            QueryTurSNResponse response = new QueryTurSNResponse();
            response.setResults(docList);

            try (MockedConstruction<HttpTurSNServer> mocked = mockConstruction(HttpTurSNServer.class,
                    (mock, context) -> when(mock.query(any())).thenReturn(response))) {
                String result = service.searchSite("MySite", "en", "search", 10, 1, null);

                assertThat(result)
                        .contains("Found 42 results for query: search")
                        .contains("id: doc1")
                        .contains("title: Found Article")
                        .contains("TIP: Use get_document_details");
            }
        }

        @Test
        void shouldReturnNoResultsMessageWithFilters() {
            TurSNDocumentList docList = new TurSNDocumentList();
            docList.setTurSNDocuments(Collections.emptyList());
            QueryTurSNResponse response = new QueryTurSNResponse();
            response.setResults(docList);

            try (MockedConstruction<HttpTurSNServer> mocked = mockConstruction(HttpTurSNServer.class,
                    (mock, context) -> when(mock.query(any())).thenReturn(response))) {
                String result = service.searchSite("MySite", "en", "nope", 10, 1, "type:Article");

                assertThat(result)
                        .contains("No results found for query: nope")
                        .contains("get_search_suggestions")
                        .contains("get_valid_filter_values");
            }
        }

        @Test
        void shouldReturnNoResultsMessageWithoutFilters() {
            TurSNDocumentList docList = new TurSNDocumentList();
            docList.setTurSNDocuments(Collections.emptyList());
            QueryTurSNResponse response = new QueryTurSNResponse();
            response.setResults(docList);

            try (MockedConstruction<HttpTurSNServer> mocked = mockConstruction(HttpTurSNServer.class,
                    (mock, context) -> when(mock.query(any())).thenReturn(response))) {
                String result = service.searchSite("MySite", "en", "nope", 10, 1, null);

                assertThat(result)
                        .contains("No results found for query: nope")
                        .doesNotContain("get_valid_filter_values");
            }
        }

        @Test
        void shouldReturnFormattedResultsWithNullRowsAndPage() {
            TurSNDocumentList docList = createDocumentList(
                    List.of(createDocumentFields("d1", "Title1", "http://a.com", "desc1")), 5);
            QueryTurSNResponse response = new QueryTurSNResponse();
            response.setResults(docList);

            try (MockedConstruction<HttpTurSNServer> mocked = mockConstruction(HttpTurSNServer.class,
                    (mock, context) -> when(mock.query(any())).thenReturn(response))) {
                String result = service.searchSite("MySite", "en", "test", null, null, null);

                assertThat(result).contains("Found 5 results for query: test");
            }
        }
    }

    @Nested
    class GetDocumentDetailsWithMockedServerTests {

        @Test
        void shouldReturnFullDocumentDetails() {
            Map<String, Object> fields = new LinkedHashMap<>();
            fields.put("id", "doc123");
            fields.put("title", "Full Document");
            fields.put("text", "Complete text content");
            fields.put("author", "John");

            TurSNDocumentList docList = createDocumentList(List.of(fields), 1);
            QueryTurSNResponse response = new QueryTurSNResponse();
            response.setResults(docList);

            try (MockedConstruction<HttpTurSNServer> mocked = mockConstruction(HttpTurSNServer.class,
                    (mock, context) -> when(mock.query(any())).thenReturn(response))) {
                String result = service.getDocumentDetails("MySite", "en", "doc123");

                assertThat(result)
                        .contains("=== Full Document Details ===")
                        .contains("id: doc123")
                        .contains("title: Full Document")
                        .contains("text: Complete text content")
                        .contains("author: John");
            }
        }

        @Test
        void shouldTruncateLongDocumentContent() {
            Map<String, Object> fields = new LinkedHashMap<>();
            fields.put("text", "x".repeat(6000));

            TurSNDocumentList docList = createDocumentList(List.of(fields), 1);
            QueryTurSNResponse response = new QueryTurSNResponse();
            response.setResults(docList);

            try (MockedConstruction<HttpTurSNServer> mocked = mockConstruction(HttpTurSNServer.class,
                    (mock, context) -> when(mock.query(any())).thenReturn(response))) {
                String result = service.getDocumentDetails("MySite", "en", "doc1");

                assertThat(result).contains("[TRUNCATED - content too long]");
            }
        }

        @Test
        void shouldReturnNotFoundWhenNoResults() {
            TurSNDocumentList docList = new TurSNDocumentList();
            docList.setTurSNDocuments(Collections.emptyList());
            QueryTurSNResponse response = new QueryTurSNResponse();
            response.setResults(docList);

            try (MockedConstruction<HttpTurSNServer> mocked = mockConstruction(HttpTurSNServer.class,
                    (mock, context) -> when(mock.query(any())).thenReturn(response))) {
                String result = service.getDocumentDetails("MySite", "en", "missing");

                assertThat(result).isEqualTo("Document not found with ID: missing");
            }
        }

        @Test
        void shouldReturnNoContentWhenFieldsNull() {
            TurSNDocument doc = new TurSNDocument();
            // content is null
            TurSNDocumentList docList = new TurSNDocumentList();
            docList.setTurSNDocuments(List.of(doc));
            QueryTurSNResponse response = new QueryTurSNResponse();
            response.setResults(docList);

            try (MockedConstruction<HttpTurSNServer> mocked = mockConstruction(HttpTurSNServer.class,
                    (mock, context) -> when(mock.query(any())).thenReturn(response))) {
                String result = service.getDocumentDetails("MySite", "en", "doc1");

                assertThat(result).contains("Document found but has no content");
            }
        }

        @Test
        void shouldHandleNullValueInFields() {
            Map<String, Object> fields = new LinkedHashMap<>();
            fields.put("title", "Title");
            fields.put("nullField", null);

            TurSNDocumentList docList = createDocumentList(List.of(fields), 1);
            QueryTurSNResponse response = new QueryTurSNResponse();
            response.setResults(docList);

            try (MockedConstruction<HttpTurSNServer> mocked = mockConstruction(HttpTurSNServer.class,
                    (mock, context) -> when(mock.query(any())).thenReturn(response))) {
                String result = service.getDocumentDetails("MySite", "en", "doc1");

                assertThat(result)
                        .contains("title: Title")
                        .contains("nullField: ");
            }
        }
    }

    @Nested
    class SearchRecentUpdatesWithMockedServerTests {

        @Test
        void shouldReturnFormattedRecentResults() {
            Map<String, Object> fields = new LinkedHashMap<>();
            fields.put("id", "r1");
            fields.put("title", "Recent Article");
            fields.put("date", "2025-06-01");

            TurSNDocumentList docList = createDocumentList(List.of(fields), 50);
            QueryTurSNResponse response = new QueryTurSNResponse();
            response.setResults(docList);

            try (MockedConstruction<HttpTurSNServer> mocked = mockConstruction(HttpTurSNServer.class,
                    (mock, context) -> when(mock.query(any())).thenReturn(response))) {
                String result = service.searchRecentUpdates("MySite", "en", "test", 5, null);

                assertThat(result)
                        .contains("=== Latest Content ===")
                        .contains("id: r1")
                        .contains("title: Recent Article");
            }
        }

        @Test
        void shouldReturnNoRecentContent() {
            TurSNDocumentList docList = new TurSNDocumentList();
            docList.setTurSNDocuments(Collections.emptyList());
            QueryTurSNResponse response = new QueryTurSNResponse();
            response.setResults(docList);

            try (MockedConstruction<HttpTurSNServer> mocked = mockConstruction(HttpTurSNServer.class,
                    (mock, context) -> when(mock.query(any())).thenReturn(response))) {
                String result = service.searchRecentUpdates("MySite", "en", "test", 5, "type:Article");

                assertThat(result).contains("No recent content found for query: test");
            }
        }

        @Test
        void shouldHandleNullQueryAndNullRows() {
            TurSNDocumentList docList = createDocumentList(
                    List.of(createDocumentFields("r1", "T1", "http://a.com", "d1")), 1);
            QueryTurSNResponse response = new QueryTurSNResponse();
            response.setResults(docList);

            try (MockedConstruction<HttpTurSNServer> mocked = mockConstruction(HttpTurSNServer.class,
                    (mock, context) -> when(mock.query(any())).thenReturn(response))) {
                String result = service.searchRecentUpdates("MySite", "en", null, null, null);

                assertThat(result).contains("=== Latest Content ===");
            }
        }

        @Test
        void shouldHandleWithFilterQueries() {
            TurSNDocumentList docList = createDocumentList(
                    List.of(createDocumentFields("r1", "T1", "http://a.com", "d1")), 1);
            QueryTurSNResponse response = new QueryTurSNResponse();
            response.setResults(docList);

            try (MockedConstruction<HttpTurSNServer> mocked = mockConstruction(HttpTurSNServer.class,
                    (mock, context) -> when(mock.query(any())).thenReturn(response))) {
                String result = service.searchRecentUpdates("MySite", "en", "test", 5, "type:Article|source:CMS");

                assertThat(result).contains("=== Latest Content ===");
            }
        }
    }

    @Nested
    class SearchByDateRangeWithMockedServerTests {

        @Test
        void shouldReturnFormattedDateRangeResults() {
            Map<String, Object> fields = new LinkedHashMap<>();
            fields.put("id", "dr1");
            fields.put("title", "Dated Article");
            fields.put("modification_date", "2025-06-15");

            TurSNDocumentList docList = createDocumentList(List.of(fields), 25);
            QueryTurSNResponse response = new QueryTurSNResponse();
            response.setResults(docList);

            try (MockedConstruction<HttpTurSNServer> mocked = mockConstruction(HttpTurSNServer.class,
                    (mock, context) -> when(mock.query(any())).thenReturn(response))) {
                String result = service.searchByDateRange("MySite", "en", "test",
                        "modification_date", "2025-01-01", "2025-12-31", 10);

                assertThat(result)
                        .contains("=== Date Range Search ===")
                        .contains("Period: 2025-01-01 to 2025-12-31")
                        .contains("id: dr1")
                        .contains("title: Dated Article");
            }
        }

        @Test
        void shouldReturnNoResultsForDateRange() {
            TurSNDocumentList docList = new TurSNDocumentList();
            docList.setTurSNDocuments(Collections.emptyList());
            QueryTurSNResponse response = new QueryTurSNResponse();
            response.setResults(docList);

            try (MockedConstruction<HttpTurSNServer> mocked = mockConstruction(HttpTurSNServer.class,
                    (mock, context) -> when(mock.query(any())).thenReturn(response))) {
                String result = service.searchByDateRange("MySite", "en", "test",
                        "date", "2025-01-01", "2025-12-31", 10);

                assertThat(result).contains("No results found for query 'test' between 2025-01-01 and 2025-12-31");
            }
        }

        @Test
        void shouldHandleNullQueryAndRows() {
            TurSNDocumentList docList = createDocumentList(
                    List.of(createDocumentFields("dr1", "T1", "http://a.com", "d1")), 1);
            QueryTurSNResponse response = new QueryTurSNResponse();
            response.setResults(docList);

            try (MockedConstruction<HttpTurSNServer> mocked = mockConstruction(HttpTurSNServer.class,
                    (mock, context) -> when(mock.query(any())).thenReturn(response))) {
                String result = service.searchByDateRange("MySite", "en", null,
                        "date", "2025-01-01", "2025-12-31", null);

                assertThat(result).contains("=== Date Range Search ===");
            }
        }
    }

    @Nested
    class GetAggregatedStatsWithMockedServerTests {

        @Test
        void shouldReturnAggregatedStatsForMatchingFacet() {
            TurSNFacetFieldList facetFieldList = createFacetFieldList(
                    List.of(createFacetBean("type", "Type",
                            List.of(createFacetItem("Article", 50), createFacetItem("Page", 30)))));

            TurSNSiteSearchQueryContextBean ctx = new TurSNSiteSearchQueryContextBean();
            ctx.setCount(80);
            TurSNDocumentList docList = new TurSNDocumentList();
            docList.setQueryContext(ctx);
            QueryTurSNResponse response = new QueryTurSNResponse();
            response.setResults(docList);
            response.setFacetFields(facetFieldList);

            try (MockedConstruction<HttpTurSNServer> mocked = mockConstruction(HttpTurSNServer.class,
                    (mock, context) -> when(mock.query(any())).thenReturn(response))) {
                String result = service.getAggregatedStats("MySite", "en", "test", "type", null);

                assertThat(result)
                        .contains("=== Aggregated Stats ===")
                        .contains("Total matching documents: 80")
                        .contains("Article;50")
                        .contains("Page;30");
            }
        }

        @Test
        void shouldReturnNoFacetDataMessage() {
            QueryTurSNResponse response = new QueryTurSNResponse();

            try (MockedConstruction<HttpTurSNServer> mocked = mockConstruction(HttpTurSNServer.class,
                    (mock, context) -> when(mock.query(any())).thenReturn(response))) {
                String result = service.getAggregatedStats("MySite", "en", "test", "type", null);

                assertThat(result).contains("No facet data available");
            }
        }

        @Test
        void shouldReturnAvailableFacetsWhenFieldNotFound() {
            TurSNFacetFieldList facetFieldList = createFacetFieldList(
                    List.of(createFacetBean("category", "Category",
                            List.of(createFacetItem("Tech", 20)))));

            TurSNSiteSearchQueryContextBean ctx = new TurSNSiteSearchQueryContextBean();
            ctx.setCount(20);
            TurSNDocumentList docList = new TurSNDocumentList();
            docList.setQueryContext(ctx);
            QueryTurSNResponse response = new QueryTurSNResponse();
            response.setResults(docList);
            response.setFacetFields(facetFieldList);

            try (MockedConstruction<HttpTurSNServer> mocked = mockConstruction(HttpTurSNServer.class,
                    (mock, context) -> when(mock.query(any())).thenReturn(response))) {
                String result = service.getAggregatedStats("MySite", "en", "test", "wrong_field", null);

                assertThat(result)
                        .contains("Field 'wrong_field' is not a valid facet")
                        .contains("category");
            }
        }

        @Test
        void shouldHandleFilterQueries() {
            TurSNFacetFieldList facetFieldList = createFacetFieldList(
                    List.of(createFacetBean("type", "Type",
                            List.of(createFacetItem("Article", 10)))));

            TurSNSiteSearchQueryContextBean ctx = new TurSNSiteSearchQueryContextBean();
            ctx.setCount(10);
            TurSNDocumentList docList = new TurSNDocumentList();
            docList.setQueryContext(ctx);
            QueryTurSNResponse response = new QueryTurSNResponse();
            response.setResults(docList);
            response.setFacetFields(facetFieldList);

            try (MockedConstruction<HttpTurSNServer> mocked = mockConstruction(HttpTurSNServer.class,
                    (mock, context) -> when(mock.query(any())).thenReturn(response))) {
                String result = service.getAggregatedStats("MySite", "en", "test", "type", "source:CMS");

                assertThat(result).contains("=== Aggregated Stats ===");
            }
        }
    }

    @Nested
    class GetValidFilterValuesWithMockedServerTests {

        @Test
        void shouldReturnFilterValuesForMatchingFacet() {
            TurSNFacetFieldList facetFieldList = createFacetFieldList(
                    List.of(createFacetBean("category", "Category",
                            List.of(createFacetItem("Tech", 50), createFacetItem("Science", 30)))));

            QueryTurSNResponse response = new QueryTurSNResponse();
            response.setFacetFields(facetFieldList);

            try (MockedConstruction<HttpTurSNServer> mocked = mockConstruction(HttpTurSNServer.class,
                    (mock, context) -> when(mock.query(any())).thenReturn(response))) {
                String result = service.getValidFilterValues("MySite", "en", "category");

                assertThat(result)
                        .contains("Valid values for 'category':")
                        .contains("value;count")
                        .contains("Tech;50")
                        .contains("Science;30");
            }
        }

        @Test
        void shouldReturnNoValuesForEmptyFacet() {
            TurSNFacetFieldList facetFieldList = createFacetFieldList(
                    List.of(createFacetBean("category", "Category", Collections.emptyList())));

            QueryTurSNResponse response = new QueryTurSNResponse();
            response.setFacetFields(facetFieldList);

            try (MockedConstruction<HttpTurSNServer> mocked = mockConstruction(HttpTurSNServer.class,
                    (mock, context) -> when(mock.query(any())).thenReturn(response))) {
                String result = service.getValidFilterValues("MySite", "en", "category");

                assertThat(result).isEqualTo("No values found for facet field: category");
            }
        }

        @Test
        void shouldReturnAvailableFacetsWhenFieldNotFound() {
            TurSNFacetFieldList facetFieldList = createFacetFieldList(
                    List.of(createFacetBean("type", "Type",
                            List.of(createFacetItem("Article", 10)))));

            QueryTurSNResponse response = new QueryTurSNResponse();
            response.setFacetFields(facetFieldList);

            try (MockedConstruction<HttpTurSNServer> mocked = mockConstruction(HttpTurSNServer.class,
                    (mock, context) -> when(mock.query(any())).thenReturn(response))) {
                String result = service.getValidFilterValues("MySite", "en", "wrong_field");

                assertThat(result)
                        .contains("Field 'wrong_field' is not a valid facet")
                        .contains("type");
            }
        }

        @Test
        void shouldReturnNoFacetDataWhenNull() {
            QueryTurSNResponse response = new QueryTurSNResponse();

            try (MockedConstruction<HttpTurSNServer> mocked = mockConstruction(HttpTurSNServer.class,
                    (mock, context) -> when(mock.query(any())).thenReturn(response))) {
                String result = service.getValidFilterValues("MySite", "en", "field");

                assertThat(result).isEqualTo("No facet data available for site: MySite");
            }
        }
    }

    @Nested
    class GetFacetSummaryWithMockedServerTests {

        @Test
        void shouldReturnFacetSummary() {
            TurSNFacetFieldList facetFieldList = createFacetFieldList(
                    List.of(createFacetBean("type", "Content Type",
                            List.of(createFacetItem("Article", 50), createFacetItem("Page", 30)))));

            TurSNSiteSearchQueryContextBean ctx = new TurSNSiteSearchQueryContextBean();
            ctx.setCount(80);
            TurSNDocumentList docList = new TurSNDocumentList();
            docList.setQueryContext(ctx);
            QueryTurSNResponse response = new QueryTurSNResponse();
            response.setResults(docList);
            response.setFacetFields(facetFieldList);

            try (MockedConstruction<HttpTurSNServer> mocked = mockConstruction(HttpTurSNServer.class,
                    (mock, context) -> when(mock.query(any())).thenReturn(response))) {
                String result = service.getFacetSummary("MySite", "en", "test");

                assertThat(result)
                        .contains("=== Site Content Summary ===")
                        .contains("Total matching documents: 80")
                        .contains(">> type (Content Type):")
                        .contains("   - Article (50)")
                        .contains("   - Page (30)")
                        .contains("TIP: Use get_valid_filter_values");
            }
        }

        @Test
        void shouldReturnNoFacetData() {
            QueryTurSNResponse response = new QueryTurSNResponse();

            try (MockedConstruction<HttpTurSNServer> mocked = mockConstruction(HttpTurSNServer.class,
                    (mock, context) -> when(mock.query(any())).thenReturn(response))) {
                String result = service.getFacetSummary("MySite", "en", "test");

                assertThat(result).contains("No facet data available for site: MySite");
            }
        }

        @Test
        void shouldHandleNullQuery() {
            TurSNFacetFieldList facetFieldList = createFacetFieldList(
                    List.of(createFacetBean("type", "Type",
                            List.of(createFacetItem("A", 1)))));

            QueryTurSNResponse response = new QueryTurSNResponse();
            response.setFacetFields(facetFieldList);

            try (MockedConstruction<HttpTurSNServer> mocked = mockConstruction(HttpTurSNServer.class,
                    (mock, context) -> when(mock.query(any())).thenReturn(response))) {
                String result = service.getFacetSummary("MySite", "en", null);

                assertThat(result).contains("=== Site Content Summary ===");
            }
        }
    }

    @Nested
    class CompareItemsWithMockedServerTests {

        @Test
        void shouldReturnComparisonTable() {
            Map<String, Object> fields1 = new LinkedHashMap<>();
            fields1.put("title", "Doc A");
            fields1.put("type", "Article");

            Map<String, Object> fields2 = new LinkedHashMap<>();
            fields2.put("title", "Doc B");
            fields2.put("type", "Page");

            TurSNDocumentList docList1 = createDocumentList(List.of(fields1), 1);
            TurSNDocumentList docList2 = createDocumentList(List.of(fields2), 1);

            QueryTurSNResponse response1 = new QueryTurSNResponse();
            response1.setResults(docList1);
            QueryTurSNResponse response2 = new QueryTurSNResponse();
            response2.setResults(docList2);

            try (MockedConstruction<HttpTurSNServer> mocked = mockConstruction(HttpTurSNServer.class,
                    (mock, context) -> when(mock.query(any())).thenReturn(response1, response2))) {
                String result = service.compareItems("MySite", "en", "id1,id2", "title,type");

                assertThat(result)
                        .contains("=== Document Comparison ===")
                        .contains("Doc A")
                        .contains("Doc B")
                        .contains("Article")
                        .contains("Page");
            }
        }

        @Test
        void shouldHandleMissingDocumentsInComparison() {
            TurSNDocumentList emptyList = new TurSNDocumentList();
            emptyList.setTurSNDocuments(Collections.emptyList());
            QueryTurSNResponse emptyResponse = new QueryTurSNResponse();
            emptyResponse.setResults(emptyList);

            try (MockedConstruction<HttpTurSNServer> mocked = mockConstruction(HttpTurSNServer.class,
                    (mock, context) -> when(mock.query(any())).thenReturn(emptyResponse))) {
                String result = service.compareItems("MySite", "en", "id1,id2", "title");

                assertThat(result)
                        .contains("=== Document Comparison ===")
                        .contains("(document not found)");
            }
        }
    }

    @Nested
    class LookupFacetValueWithMockedServerTests {

        @Test
        void shouldMatchTermToFacetValue() {
            TurSNFacetFieldList facetFieldList = createFacetFieldList(
                    List.of(createFacetBean("category", "Category",
                            List.of(createFacetItem("Technology", 50)))));

            QueryTurSNResponse response = new QueryTurSNResponse();
            response.setFacetFields(facetFieldList);

            try (MockedConstruction<HttpTurSNServer> mocked = mockConstruction(HttpTurSNServer.class,
                    (mock, context) -> when(mock.query(any())).thenReturn(response))) {
                String result = service.lookupFacetValue("MySite", "en", "tech");

                assertThat(result)
                        .contains("Facet values matching 'tech'")
                        .contains("'Technology' (50 items)");
            }
        }

        @Test
        void shouldReturnNoFacetsFound() {
            QueryTurSNResponse response = new QueryTurSNResponse();
            // no facetFields set

            try (MockedConstruction<HttpTurSNServer> mocked = mockConstruction(HttpTurSNServer.class,
                    (mock, context) -> when(mock.query(any())).thenReturn(response))) {
                String result = service.lookupFacetValue("MySite", "en", "test");

                assertThat(result).isEqualTo("No facets found for site: MySite");
            }
        }

        @Test
        void shouldReturnNoMatchMessage() {
            TurSNFacetFieldList facetFieldList = createFacetFieldList(
                    List.of(createFacetBean("category", "Category",
                            List.of(createFacetItem("Technology", 50)))));

            QueryTurSNResponse response = new QueryTurSNResponse();
            response.setFacetFields(facetFieldList);

            try (MockedConstruction<HttpTurSNServer> mocked = mockConstruction(HttpTurSNServer.class,
                    (mock, context) -> when(mock.query(any())).thenReturn(response))) {
                String result = service.lookupFacetValue("MySite", "en", "finance");

                assertThat(result).contains("No facet values found matching 'finance'");
            }
        }
    }

    @Nested
    class DiscoverFacetValuesWithMockedServerTests {

        @Test
        void shouldDiscoverMatchingFacetValues() {
            TurSNFacetFieldList facetFieldList = createFacetFieldList(
                    List.of(createFacetBean("category", "Category",
                            List.of(createFacetItem("Technology", 50), createFacetItem("Science", 30)))));

            QueryTurSNResponse response = new QueryTurSNResponse();
            response.setFacetFields(facetFieldList);

            try (MockedConstruction<HttpTurSNServer> mocked = mockConstruction(HttpTurSNServer.class,
                    (mock, context) -> when(mock.query(any())).thenReturn(response))) {
                String result = service.discoverFacetValues("MySite", "en", "technology articles science");

                assertThat(result)
                        .contains("Facet discovery for phrase:")
                        .contains("=== SEARCH STRATEGY ===")
                        .contains("Words matched to facets");
            }
        }

        @Test
        void shouldReturnNoFacetsFound() {
            QueryTurSNResponse response = new QueryTurSNResponse();

            try (MockedConstruction<HttpTurSNServer> mocked = mockConstruction(HttpTurSNServer.class,
                    (mock, context) -> when(mock.query(any())).thenReturn(response))) {
                String result = service.discoverFacetValues("MySite", "en", "some text");

                assertThat(result).isEqualTo("No facets found for site: MySite");
            }
        }

        @Test
        void shouldReturnNoMeaningfulWords() {
            TurSNFacetFieldList facetFieldList = createFacetFieldList(
                    List.of(createFacetBean("type", "Type",
                            List.of(createFacetItem("A", 1)))));

            QueryTurSNResponse response = new QueryTurSNResponse();
            response.setFacetFields(facetFieldList);

            try (MockedConstruction<HttpTurSNServer> mocked = mockConstruction(HttpTurSNServer.class,
                    (mock, context) -> when(mock.query(any())).thenReturn(response))) {
                // All words are stop words or too short
                String result = service.discoverFacetValues("MySite", "en", "the of in an");

                assertThat(result).contains("No meaningful words found in:");
            }
        }

        @Test
        void shouldReturnNoMatchForAnyWord() {
            TurSNFacetFieldList facetFieldList = createFacetFieldList(
                    List.of(createFacetBean("category", "Category",
                            List.of(createFacetItem("Technology", 50)))));

            QueryTurSNResponse response = new QueryTurSNResponse();
            response.setFacetFields(facetFieldList);

            try (MockedConstruction<HttpTurSNServer> mocked = mockConstruction(HttpTurSNServer.class,
                    (mock, context) -> when(mock.query(any())).thenReturn(response))) {
                String result = service.discoverFacetValues("MySite", "en", "finance banking investments");

                assertThat(result)
                        .contains("No facet values found for any word")
                        .contains("Do NOT use these terms as filters");
            }
        }

        @Test
        void shouldReportUnmatchedWords() {
            TurSNFacetFieldList facetFieldList = createFacetFieldList(
                    List.of(createFacetBean("category", "Category",
                            List.of(createFacetItem("Technology", 50)))));

            QueryTurSNResponse response = new QueryTurSNResponse();
            response.setFacetFields(facetFieldList);

            try (MockedConstruction<HttpTurSNServer> mocked = mockConstruction(HttpTurSNServer.class,
                    (mock, context) -> when(mock.query(any())).thenReturn(response))) {
                String result = service.discoverFacetValues("MySite", "en", "technology finance");

                assertThat(result)
                        .contains("=== SEARCH STRATEGY ===")
                        .contains("Words matched to facets")
                        .contains("Words with no facet match");
            }
        }

        @Test
        void shouldReportAllWordsMatchedToFacets() {
            TurSNFacetFieldList facetFieldList = createFacetFieldList(
                    List.of(createFacetBean("category", "Category",
                            List.of(createFacetItem("Technology", 50), createFacetItem("Science", 30)))));

            QueryTurSNResponse response = new QueryTurSNResponse();
            response.setFacetFields(facetFieldList);

            try (MockedConstruction<HttpTurSNServer> mocked = mockConstruction(HttpTurSNServer.class,
                    (mock, context) -> when(mock.query(any())).thenReturn(response))) {
                String result = service.discoverFacetValues("MySite", "en", "technology science");

                assertThat(result)
                        .contains("=== SEARCH STRATEGY ===")
                        .contains("All words matched facets");
            }
        }
    }

    @Nested
    class GetSearchSuggestionsWithMockedServerTests {

        @Test
        void shouldReturnAutocompleteSuggestions() {
            QueryTurSNResponse response = new QueryTurSNResponse();

            try (MockedConstruction<HttpTurSNServer> mocked = mockConstruction(HttpTurSNServer.class,
                    (mock, context) -> {
                        when(mock.autoComplete(any())).thenReturn(List.of("suggestion1", "suggestion2"));
                        when(mock.query(any())).thenReturn(response);
                    })) {
                String result = service.getSearchSuggestions("MySite", "en", "test");

                assertThat(result)
                        .contains("Autocomplete suggestions for 'test':")
                        .contains("  - suggestion1")
                        .contains("  - suggestion2");
            }
        }

        @Test
        void shouldReturnNoSuggestionsMessage() {
            QueryTurSNResponse response = new QueryTurSNResponse();

            try (MockedConstruction<HttpTurSNServer> mocked = mockConstruction(HttpTurSNServer.class,
                    (mock, context) -> {
                        when(mock.autoComplete(any())).thenReturn(Collections.emptyList());
                        when(mock.query(any())).thenReturn(response);
                    })) {
                String result = service.getSearchSuggestions("MySite", "en", "test");

                assertThat(result).contains("No autocomplete suggestions found for 'test'");
            }
        }

        @Test
        void shouldReturnNullSuggestionsMessage() {
            QueryTurSNResponse response = new QueryTurSNResponse();

            try (MockedConstruction<HttpTurSNServer> mocked = mockConstruction(HttpTurSNServer.class,
                    (mock, context) -> {
                        when(mock.autoComplete(any())).thenReturn(null);
                        when(mock.query(any())).thenReturn(response);
                    })) {
                String result = service.getSearchSuggestions("MySite", "en", "test");

                assertThat(result).contains("No autocomplete suggestions found for 'test'");
            }
        }
    }

    // ======================== Helpers ========================

    private TurSNDocument createTurSNDocument(Map<String, Object> fields) {
        TurSNSiteSearchDocumentBean docBean = TurSNSiteSearchDocumentBean.builder()
                .fields(fields)
                .build();
        TurSNDocument doc = new TurSNDocument();
        doc.setContent(docBean);
        return doc;
    }

    private Map<String, Object> createDocumentFields(String id, String title, String url, String description) {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("id", id);
        fields.put("title", title);
        fields.put("url", url);
        fields.put("description", description);
        return fields;
    }

    private TurSNDocumentList createDocumentList(List<Map<String, Object>> docFieldsList, int totalCount) {
        TurSNDocumentList docList = new TurSNDocumentList();
        List<TurSNDocument> docs = new ArrayList<>();
        for (Map<String, Object> fields : docFieldsList) {
            docs.add(createTurSNDocument(fields));
        }
        docList.setTurSNDocuments(docs);
        TurSNSiteSearchQueryContextBean ctx = new TurSNSiteSearchQueryContextBean();
        ctx.setCount(totalCount);
        docList.setQueryContext(ctx);
        return docList;
    }

    private TurSNSiteSearchBean createSearchBeanWithDocument(Map<String, Object> fields) {
        TurSNSiteSearchDocumentBean docBean = TurSNSiteSearchDocumentBean.builder()
                .fields(fields)
                .build();
        TurSNSiteSearchResultsBean results = new TurSNSiteSearchResultsBean();
        results.setDocument(List.of(docBean));
        TurSNSiteSearchBean searchBean = new TurSNSiteSearchBean();
        searchBean.setResults(results);
        return searchBean;
    }

    private TurSNSiteSearchFacetItemBean createFacetItem(String label, int count) {
        TurSNSiteSearchFacetItemBean item = new TurSNSiteSearchFacetItemBean();
        item.setLabel(label);
        item.setCount(count);
        return item;
    }

    private TurSNSiteSearchFacetBean createFacetBean(String name, String labelText,
            List<TurSNSiteSearchFacetItemBean> items) {
        TurSNSiteSearchFacetBean bean = new TurSNSiteSearchFacetBean();
        bean.setName(name);
        if (labelText != null) {
            TurSNSiteSearchFacetLabelBean label = new TurSNSiteSearchFacetLabelBean();
            label.setText(labelText);
            bean.setLabel(label);
        }
        bean.setFacets(items);
        return bean;
    }

    private TurSNFacetFieldList createFacetFieldList(List<TurSNSiteSearchFacetBean> facetBeans) {
        return new TurSNFacetFieldList(facetBeans, null);
    }

    private TurSNSite createSite(String name, String description) {
        TurSNSite site = new TurSNSite();
        site.setName(name);
        site.setDescription(description);
        return site;
    }

    private TurSNSiteLocale createLocale(Locale language) {
        TurSNSiteLocale locale = new TurSNSiteLocale();
        locale.setLanguage(language);
        return locale;
    }

    private TurSNSiteFieldExt createField(String name, TurSEFieldType type, int facet, String description) {
        TurSNSiteFieldExt field = new TurSNSiteFieldExt();
        field.setName(name);
        field.setType(type);
        field.setFacet(facet);
        field.setDescription(description);
        return field;
    }

    private com.viglet.turing.client.sn.facet.TurSNFacetFieldValue createFacetFieldValue(
            String label, int count) {
        var value = new com.viglet.turing.client.sn.facet.TurSNFacetFieldValue();
        value.setLabel(label);
        value.setCount(count);
        return value;
    }
}
