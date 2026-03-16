package com.viglet.turing.sn;

import static org.junit.jupiter.api.Assertions.*;

import java.net.URI;

import org.junit.jupiter.api.Test;

class TurSNUtilsTest {

    @Test
    void isTrueShouldReturnTrueForOne() {
        assertTrue(TurSNUtils.isTrue(1));
    }

    @Test
    void isTrueShouldReturnFalseForZero() {
        assertFalse(TurSNUtils.isTrue(0));
    }

    @Test
    void isTrueShouldReturnFalseForNull() {
        assertFalse(TurSNUtils.isTrue(null));
    }

    @Test
    void addFilterQueryShouldAddFqParam() {
        URI uri = URI.create("http://localhost/search?q=test");
        URI result = TurSNUtils.addFilterQuery(uri, "category:books");
        String resultStr = result.toString();
        // The filter query param name is "fq[]" which gets URL-encoded as "fq%5B%5D"
        assertTrue(resultStr.contains("fq%5B%5D=category") || resultStr.contains("fq[]=category"),
                "Expected fq[] parameter in URI: " + resultStr);
        assertTrue(resultStr.contains("category") && resultStr.contains("books"),
                "Expected category:books in URI: " + resultStr);
    }

    @Test
    void removeFilterQueryShouldRemoveFqParam() {
        // Use URL-encoded "fq[]" = "fq%5B%5D" and ":" = "%3A"
        URI uri = URI.create("http://localhost/search?q=test&fq%5B%5D=category%3Abooks");
        URI result = TurSNUtils.removeFilterQuery(uri, "category:books");
        String resultStr = result.toString();
        assertFalse(resultStr.contains("category"), "Expected category to be removed from URI: " + resultStr);
    }

    @Test
    void removeQueryStringParameterShouldRemoveField() {
        URI uri = URI.create("http://localhost/search?q=test&p=2");
        URI result = TurSNUtils.removeQueryStringParameter(uri, "p");
        String resultStr = result.toString();
        assertFalse(resultStr.contains("p=2"), "Expected p=2 to be removed from URI: " + resultStr);
        assertTrue(resultStr.contains("q=test"), "Expected q=test to remain in URI: " + resultStr);
    }

    @Test
    void addFilterQueryShouldNotDuplicateExistingFq() {
        URI uri = URI.create("http://localhost/search?q=test&fq%5B%5D=category%3Abooks");
        URI result = TurSNUtils.addFilterQuery(uri, "category:books");
        String resultStr = result.toString();
        // Count occurrences of "category" - should appear only once
        int count = resultStr.split("category", -1).length - 1;
        assertEquals(1, count, "Expected exactly one fq for category:books in URI: " + resultStr);
    }

    @Test
    void addFilterQueryShouldResetPaginationToPageOne() {
        URI uri = URI.create("http://localhost/search?q=test&p=5");
        URI result = TurSNUtils.addFilterQuery(uri, "category:books");
        String resultStr = result.toString();
        assertTrue(resultStr.contains("p=1"), "Expected pagination reset to p=1 in URI: " + resultStr);
        assertFalse(resultStr.contains("p=5"), "Expected p=5 to be replaced in URI: " + resultStr);
    }
}
