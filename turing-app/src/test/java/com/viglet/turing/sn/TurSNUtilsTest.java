package com.viglet.turing.sn;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.InvocationTargetException;
import java.net.URI;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.viglet.turing.commons.se.TurSEParameters;
import com.viglet.turing.commons.se.result.spellcheck.TurSESpellCheckResult;
import com.viglet.turing.commons.sn.bean.TurSNSiteSearchDocumentBean;
import com.viglet.turing.commons.sn.search.TurSNSiteSearchContext;
import com.viglet.turing.persistence.dto.sn.field.TurSNSiteFieldExtDto;
import com.viglet.turing.persistence.model.sn.TurSNSite;
import com.viglet.turing.se.result.TurSEResult;

/**
 * Tests for TurSNUtils.
 *
 * @author Alexandre Oliveira
 * @since 2026.1.14
 */
class TurSNUtilsTest {

    // --- Constructor ---

    @Test
    void constructorShouldThrowIllegalStateException() throws NoSuchMethodException {
        var constructor = TurSNUtils.class.getDeclaredConstructor();
        constructor.setAccessible(true);

        assertThatThrownBy(constructor::newInstance)
                .isInstanceOf(InvocationTargetException.class)
                .cause()
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("SN Utility class");
    }

    // --- Constants ---

    @Test
    void constantsShouldHaveExpectedValues() {
        assertThat(TurSNUtils.TURING_ENTITY).isEqualTo("turing_entity");
        assertThat(TurSNUtils.DEFAULT_LANGUAGE).isEqualTo("en");
        assertThat(TurSNUtils.URL).isEqualTo("url");
    }

    // --- isTrue ---

    @Test
    void isTrueShouldReturnTrueForOne() {
        assertThat(TurSNUtils.isTrue(1)).isTrue();
    }

    @Test
    void isTrueShouldReturnFalseForZero() {
        assertThat(TurSNUtils.isTrue(0)).isFalse();
    }

    @Test
    void isTrueShouldReturnFalseForNull() {
        assertThat(TurSNUtils.isTrue(null)).isFalse();
    }

    @Test
    void isTrueShouldReturnFalseForNegative() {
        assertThat(TurSNUtils.isTrue(-1)).isFalse();
    }

    @Test
    void isTrueShouldReturnFalseForLargeNumber() {
        assertThat(TurSNUtils.isTrue(42)).isFalse();
    }

    @Test
    void isTrueShouldReturnFalseForTwo() {
        assertThat(TurSNUtils.isTrue(2)).isFalse();
    }

    // --- hasCorrectedText ---

    @Test
    void hasCorrectedTextShouldReturnTrueWhenCorrectedWithText() {
        TurSESpellCheckResult result = new TurSESpellCheckResult();
        result.setCorrected(true);
        result.setCorrectedText("corrected query");

        assertThat(TurSNUtils.hasCorrectedText(result)).isTrue();
    }

    @Test
    void hasCorrectedTextShouldReturnFalseWhenNotCorrected() {
        TurSESpellCheckResult result = new TurSESpellCheckResult();
        result.setCorrected(false);
        result.setCorrectedText("some text");

        assertThat(TurSNUtils.hasCorrectedText(result)).isFalse();
    }

    @Test
    void hasCorrectedTextShouldReturnFalseWhenCorrectedButEmptyText() {
        TurSESpellCheckResult result = new TurSESpellCheckResult();
        result.setCorrected(true);
        result.setCorrectedText("");

        assertThat(TurSNUtils.hasCorrectedText(result)).isFalse();
    }

    @Test
    void hasCorrectedTextShouldReturnFalseWhenCorrectedButNullText() {
        TurSESpellCheckResult result = new TurSESpellCheckResult();
        result.setCorrected(true);
        result.setCorrectedText(null);

        assertThat(TurSNUtils.hasCorrectedText(result)).isFalse();
    }

    @Test
    void hasCorrectedTextShouldReturnFalseWhenCorrectedButBlankText() {
        TurSESpellCheckResult result = new TurSESpellCheckResult();
        result.setCorrected(true);
        result.setCorrectedText("   ");

        assertThat(TurSNUtils.hasCorrectedText(result)).isFalse();
    }

    @Test
    void hasCorrectedTextDefaultConstructorShouldReturnFalse() {
        TurSESpellCheckResult result = new TurSESpellCheckResult();

        assertThat(TurSNUtils.hasCorrectedText(result)).isFalse();
    }

    // --- isAutoCorrectionEnabled ---

    @Test
    void isAutoCorrectionEnabledShouldReturnTrueWhenAllConditionsMet() {
        TurSEParameters params = createSEParameters(0, 1);
        TurSNSiteSearchContext context = createContext(params);
        TurSNSite site = createSNSite(1, 1);

        assertThat(TurSNUtils.isAutoCorrectionEnabled(context, site)).isTrue();
    }

    @Test
    void isAutoCorrectionEnabledShouldReturnFalseWhenAutoCorrectionDisabled() {
        TurSEParameters params = createSEParameters(1, 1);
        TurSNSiteSearchContext context = createContext(params);
        TurSNSite site = createSNSite(1, 1);

        assertThat(TurSNUtils.isAutoCorrectionEnabled(context, site)).isFalse();
    }

    @Test
    void isAutoCorrectionEnabledShouldReturnFalseWhenNotFirstPage() {
        TurSEParameters params = createSEParameters(0, 2);
        TurSNSiteSearchContext context = createContext(params);
        TurSNSite site = createSNSite(1, 1);

        assertThat(TurSNUtils.isAutoCorrectionEnabled(context, site)).isFalse();
    }

    @Test
    void isAutoCorrectionEnabledShouldReturnFalseWhenSpellCheckDisabled() {
        TurSEParameters params = createSEParameters(0, 1);
        TurSNSiteSearchContext context = createContext(params);
        TurSNSite site = createSNSite(0, 1);

        assertThat(TurSNUtils.isAutoCorrectionEnabled(context, site)).isFalse();
    }

    @Test
    void isAutoCorrectionEnabledShouldReturnFalseWhenSpellCheckFixesDisabled() {
        TurSEParameters params = createSEParameters(0, 1);
        TurSNSiteSearchContext context = createContext(params);
        TurSNSite site = createSNSite(1, 0);

        assertThat(TurSNUtils.isAutoCorrectionEnabled(context, site)).isFalse();
    }

    // --- addFilterQuery ---

    @Test
    void addFilterQueryShouldAddFqParam() {
        URI uri = URI.create("http://localhost/search?q=test");
        URI result = TurSNUtils.addFilterQuery(uri, "category:books");
        String resultStr = result.toString();

        assertThat(resultStr).containsAnyOf("fq%5B%5D=category", "fq[]=category");
        assertThat(resultStr).contains("category");
        assertThat(resultStr).contains("books");
    }

    @Test
    void addFilterQueryShouldNotDuplicateExistingFq() {
        URI uri = URI.create("http://localhost/search?q=test&fq%5B%5D=category%3Abooks");
        URI result = TurSNUtils.addFilterQuery(uri, "category:books");
        String resultStr = result.toString();

        int count = resultStr.split("category", -1).length - 1;
        assertThat(count).isEqualTo(1);
    }

    @Test
    void addFilterQueryShouldResetPaginationToPageOne() {
        URI uri = URI.create("http://localhost/search?q=test&p=5");
        URI result = TurSNUtils.addFilterQuery(uri, "category:books");
        String resultStr = result.toString();

        assertThat(resultStr).contains("p=1");
        assertThat(resultStr).doesNotContain("p=5");
    }

    @Test
    void addFilterQueryShouldPreserveOtherParams() {
        URI uri = URI.create("http://localhost/search?q=test&sort=date");
        URI result = TurSNUtils.addFilterQuery(uri, "type:pdf");
        String resultStr = result.toString();

        assertThat(resultStr).contains("q=test");
        assertThat(resultStr).contains("sort=date");
    }

    @Test
    void addFilterQueryShouldAddMultipleDifferentFilters() {
        URI uri = URI.create("http://localhost/search?q=test");
        URI result1 = TurSNUtils.addFilterQuery(uri, "category:books");
        URI result2 = TurSNUtils.addFilterQuery(result1, "type:pdf");
        String resultStr = result2.toString();

        assertThat(resultStr).contains("category");
        assertThat(resultStr).contains("pdf");
    }

    // --- removeFilterQuery ---

    @Test
    void removeFilterQueryShouldRemoveFqParam() {
        URI uri = URI.create("http://localhost/search?q=test&fq%5B%5D=category%3Abooks");
        URI result = TurSNUtils.removeFilterQuery(uri, "category:books");
        String resultStr = result.toString();

        assertThat(resultStr).doesNotContain("category");
    }

    @Test
    void removeFilterQueryShouldPreserveOtherFqs() {
        URI uri = URI.create(
                "http://localhost/search?q=test&fq%5B%5D=category%3Abooks&fq%5B%5D=type%3Apdf");
        URI result = TurSNUtils.removeFilterQuery(uri, "category:books");
        String resultStr = result.toString();

        assertThat(resultStr).doesNotContain("category");
        assertThat(resultStr).contains("type");
    }

    @Test
    void removeFilterQueryShouldResetPagination() {
        URI uri = URI.create(
                "http://localhost/search?q=test&p=3&fq%5B%5D=category%3Abooks");
        URI result = TurSNUtils.removeFilterQuery(uri, "category:books");
        String resultStr = result.toString();

        assertThat(resultStr).contains("p=1");
    }

    // --- removeQueryStringParameter ---

    @Test
    void removeQueryStringParameterShouldRemoveField() {
        URI uri = URI.create("http://localhost/search?q=test&p=2");
        URI result = TurSNUtils.removeQueryStringParameter(uri, "p");
        String resultStr = result.toString();

        assertThat(resultStr).doesNotContain("p=2");
        assertThat(resultStr).contains("q=test");
    }

    @Test
    void removeQueryStringParameterShouldHandleNonExistentParam() {
        URI uri = URI.create("http://localhost/search?q=test");
        URI result = TurSNUtils.removeQueryStringParameter(uri, "nonexistent");
        String resultStr = result.toString();

        assertThat(resultStr).contains("q=test");
    }

    @Test
    void removeQueryStringParameterShouldRemoveAllOccurrences() {
        URI uri = URI.create("http://localhost/search?q=test&sort=date");
        URI result = TurSNUtils.removeQueryStringParameter(uri, "sort");
        String resultStr = result.toString();

        assertThat(resultStr).doesNotContain("sort");
        assertThat(resultStr).contains("q=test");
    }

    // --- removeFilterQueryByFieldName / removeFilterQueryByFieldNames ---

    @Test
    void removeFilterQueryByFieldNameShouldRemoveMatchingField() {
        URI uri = URI.create(
                "http://localhost/search?q=test&fq%5B%5D=category%3Abooks");
        URI result = TurSNUtils.removeFilterQueryByFieldName(uri, "category");
        String resultStr = result.toString();

        assertThat(resultStr).doesNotContain("category");
        assertThat(resultStr).contains("q=test");
    }

    @Test
    void removeFilterQueryByFieldNamesShouldRemoveMultipleFields() {
        URI uri = URI.create(
                "http://localhost/search?q=test&fq%5B%5D=category%3Abooks&fq%5B%5D=type%3Apdf&fq%5B%5D=author%3AJohn");
        URI result = TurSNUtils.removeFilterQueryByFieldNames(uri, Arrays.asList("category", "type"));
        String resultStr = result.toString();

        assertThat(resultStr).doesNotContain("category");
        assertThat(resultStr).doesNotContain("type");
        assertThat(resultStr).contains("author");
    }

    @Test
    void removeFilterQueryByFieldNamesShouldPreserveNonFqParams() {
        URI uri = URI.create(
                "http://localhost/search?q=test&sort=date&fq%5B%5D=category%3Abooks");
        URI result = TurSNUtils.removeFilterQueryByFieldNames(uri, Collections.singletonList("category"));
        String resultStr = result.toString();

        assertThat(resultStr).contains("q=test");
        assertThat(resultStr).contains("sort=date");
    }

    // --- filterQueryByFieldName / filterQueryByFieldNames ---

    @Test
    void filterQueryByFieldNameShouldReturnMatchingValues() {
        URI uri = URI.create(
                "http://localhost/search?q=test&fq%5B%5D=category%3Abooks");
        List<String> result = TurSNUtils.filterQueryByFieldName(uri, "category");

        assertThat(result).isNotEmpty();
    }

    @Test
    void filterQueryByFieldNameShouldReturnEmptyForNoMatch() {
        URI uri = URI.create("http://localhost/search?q=test");
        List<String> result = TurSNUtils.filterQueryByFieldName(uri, "category");

        assertThat(result).isEmpty();
    }

    @Test
    void filterQueryByFieldNamesShouldReturnMultipleMatches() {
        URI uri = URI.create(
                "http://localhost/search?q=test&fq%5B%5D=category%3Abooks&fq%5B%5D=category%3Amusic&fq%5B%5D=type%3Apdf");
        List<String> result = TurSNUtils.filterQueryByFieldNames(uri, Collections.singletonList("category"));

        assertThat(result).hasSize(2);
    }

    @Test
    void filterQueryByFieldNamesShouldFilterByMultipleFieldNames() {
        URI uri = URI.create(
                "http://localhost/search?q=test&fq%5B%5D=category%3Abooks&fq%5B%5D=type%3Apdf&fq%5B%5D=author%3AJohn");
        List<String> result = TurSNUtils.filterQueryByFieldNames(uri, Arrays.asList("category", "type"));

        assertThat(result).hasSize(2);
    }

    // --- addSNDocument / addSNDocumentWithPosition ---

    @Test
    void addSNDocumentShouldAppendDocumentToList() {
        URI uri = URI.create("http://localhost/search?q=test");
        Map<String, TurSNSiteFieldExtDto> fieldExtMap = new java.util.HashMap<>();
        TurSNSiteFieldExtDto titleDto = new TurSNSiteFieldExtDto();
        titleDto.setName("title");
        fieldExtMap.put("title", titleDto);

        Map<String, TurSNSiteFieldExtDto> facetMap = new java.util.HashMap<>();

        TurSEResult result = TurSEResult.builder()
                .fields(Map.of("title", "My Document", "url", "http://example.com/doc"))
                .build();

        List<TurSNSiteSearchDocumentBean> docs = new java.util.ArrayList<>();
        TurSNUtils.addSNDocument(uri, fieldExtMap, facetMap, docs, result, false);

        assertThat(docs).hasSize(1);
        assertThat(docs.get(0).getFields()).containsEntry("title", "My Document");
        assertThat(docs.get(0).getSource()).isEqualTo("http://example.com/doc");
        assertThat(docs.get(0).isElevate()).isFalse();
    }

    @Test
    void addSNDocumentShouldSetElevateTrue() {
        URI uri = URI.create("http://localhost/search?q=test");
        TurSEResult result = TurSEResult.builder()
                .fields(Map.of("title", "Promoted"))
                .build();

        List<TurSNSiteSearchDocumentBean> docs = new java.util.ArrayList<>();
        TurSNUtils.addSNDocument(uri, new java.util.HashMap<>(), new java.util.HashMap<>(), docs, result, true);

        assertThat(docs).hasSize(1);
        assertThat(docs.get(0).isElevate()).isTrue();
    }

    @Test
    void addSNDocumentWithPositionShouldInsertAtSpecificPosition() {
        URI uri = URI.create("http://localhost/search?q=test");

        TurSEResult result1 = TurSEResult.builder().fields(Map.of("title", "First")).build();
        TurSEResult result2 = TurSEResult.builder().fields(Map.of("title", "Second")).build();
        TurSEResult result3 = TurSEResult.builder().fields(Map.of("title", "Inserted")).build();

        List<TurSNSiteSearchDocumentBean> docs = new java.util.ArrayList<>();
        TurSNUtils.addSNDocument(uri, new java.util.HashMap<>(), new java.util.HashMap<>(), docs, result1, false);
        TurSNUtils.addSNDocument(uri, new java.util.HashMap<>(), new java.util.HashMap<>(), docs, result2, false);
        TurSNUtils.addSNDocumentWithPosition(uri, new java.util.HashMap<>(), new java.util.HashMap<>(), docs, result3, true, 1);

        assertThat(docs).hasSize(3);
        assertThat(docs.get(1).getFields()).containsEntry("title", "Inserted");
        assertThat(docs.get(1).isElevate()).isTrue();
    }

    @Test
    void addSNDocumentShouldHandleNullFields() {
        URI uri = URI.create("http://localhost/search?q=test");
        TurSEResult result = TurSEResult.builder().fields(null).build();

        List<TurSNSiteSearchDocumentBean> docs = new java.util.ArrayList<>();
        TurSNUtils.addSNDocument(uri, new java.util.HashMap<>(), new java.util.HashMap<>(), docs, result, false);

        assertThat(docs).hasSize(1);
        // should return empty bean
        assertThat(docs.get(0).getFields()).isNull();
    }

    @Test
    void addSNDocumentShouldFilterTuringEntityFields() {
        URI uri = URI.create("http://localhost/search?q=test");
        Map<String, Object> fields = new java.util.HashMap<>();
        fields.put("title", "Doc");
        fields.put("turing_entity_person", "John");
        TurSEResult result = TurSEResult.builder().fields(fields).build();

        List<TurSNSiteSearchDocumentBean> docs = new java.util.ArrayList<>();
        TurSNUtils.addSNDocument(uri, new java.util.HashMap<>(), new java.util.HashMap<>(), docs, result, false);

        assertThat(docs.get(0).getFields()).containsKey("title");
        assertThat(docs.get(0).getFields()).doesNotContainKey("turing_entity_person");
    }

    @Test
    void addSNDocumentShouldMapFieldNamesUsingFieldExtMap() {
        URI uri = URI.create("http://localhost/search?q=test");
        Map<String, TurSNSiteFieldExtDto> fieldExtMap = new java.util.HashMap<>();
        TurSNSiteFieldExtDto dto = new TurSNSiteFieldExtDto();
        dto.setName("displayTitle");
        fieldExtMap.put("title", dto);

        TurSEResult result = TurSEResult.builder().fields(Map.of("title", "Mapped")).build();

        List<TurSNSiteSearchDocumentBean> docs = new java.util.ArrayList<>();
        TurSNUtils.addSNDocument(uri, fieldExtMap, new java.util.HashMap<>(), docs, result, false);

        assertThat(docs.get(0).getFields()).containsEntry("displayTitle", "Mapped");
        assertThat(docs.get(0).getFields()).doesNotContainKey("title");
    }

    @Test
    void addSNDocumentShouldReturnNullSourceWhenNoUrl() {
        URI uri = URI.create("http://localhost/search?q=test");
        TurSEResult result = TurSEResult.builder().fields(Map.of("title", "No URL")).build();

        List<TurSNSiteSearchDocumentBean> docs = new java.util.ArrayList<>();
        TurSNUtils.addSNDocument(uri, new java.util.HashMap<>(), new java.util.HashMap<>(), docs, result, false);

        assertThat(docs.get(0).getSource()).isNull();
    }

    @Test
    void addSNDocumentShouldBuildMetadataFromFacetMap() {
        URI uri = URI.create("http://localhost/search?q=test");
        Map<String, TurSNSiteFieldExtDto> facetMap = new java.util.HashMap<>();
        TurSNSiteFieldExtDto categoryDto = new TurSNSiteFieldExtDto();
        categoryDto.setName("category");
        facetMap.put("category", categoryDto);

        TurSEResult result = TurSEResult.builder()
                .fields(Map.of("category", "books", "title", "Test"))
                .build();

        List<TurSNSiteSearchDocumentBean> docs = new java.util.ArrayList<>();
        TurSNUtils.addSNDocument(uri, new java.util.HashMap<>(), facetMap, docs, result, false);

        assertThat(docs.get(0).getMetadata()).isNotEmpty();
        assertThat(docs.get(0).getMetadata().get(0).getText()).isEqualTo("books");
    }

    @Test
    void addSNDocumentShouldBuildMetadataFromArrayListFacetValues() {
        URI uri = URI.create("http://localhost/search?q=test");
        Map<String, TurSNSiteFieldExtDto> facetMap = new java.util.HashMap<>();
        TurSNSiteFieldExtDto tagDto = new TurSNSiteFieldExtDto();
        tagDto.setName("tags");
        facetMap.put("tags", tagDto);

        java.util.ArrayList<String> tagValues = new java.util.ArrayList<>(List.of("java", "spring"));
        Map<String, Object> fields = new java.util.HashMap<>();
        fields.put("tags", tagValues);
        fields.put("title", "Test");
        TurSEResult result = TurSEResult.builder().fields(fields).build();

        List<TurSNSiteSearchDocumentBean> docs = new java.util.ArrayList<>();
        TurSNUtils.addSNDocument(uri, new java.util.HashMap<>(), facetMap, docs, result, false);

        assertThat(docs.get(0).getMetadata()).hasSize(2);
    }

    // --- Helpers ---

    private TurSEParameters createSEParameters(int autoCorrectionDisabled, int currentPage) {
        TurSEParameters params = new TurSEParameters(
                new com.viglet.turing.commons.sn.bean.TurSNSearchParams());
        params.setAutoCorrectionDisabled(autoCorrectionDisabled);
        params.setCurrentPage(currentPage);
        return params;
    }

    private TurSNSiteSearchContext createContext(TurSEParameters params) {
        return new TurSNSiteSearchContext(
                "testSite", null, params, null,
                URI.create("http://localhost/search"), null);
    }

    private TurSNSite createSNSite(int spellCheck, int spellCheckFixes) {
        TurSNSite site = new TurSNSite();
        site.setSpellCheck(spellCheck);
        site.setSpellCheckFixes(spellCheckFixes);
        return site;
    }
}
