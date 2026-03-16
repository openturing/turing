package com.viglet.turing.sn.tr;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import org.junit.jupiter.api.Test;

class TurSNTargetingRulesTest {

    private final TurSNTargetingRules rules = new TurSNTargetingRules();

    @Test
    void andMethodShouldCreateCorrectQueryWithMultipleAttributes() {
        List<String> trs = List.of("group:admin", "group:user", "role:editor");
        String result = rules.andMethod(trs);
        assertNotNull(result);
        assertTrue(result.contains("AND"), "Expected AND between attribute groups: " + result);
        assertTrue(result.contains("group:admin"), "Expected group:admin in result: " + result);
        assertTrue(result.contains("group:user"), "Expected group:user in result: " + result);
        assertTrue(result.contains("role:editor"), "Expected role:editor in result: " + result);
        assertTrue(result.contains("NOT group:*"), "Expected NOT group:* in result: " + result);
        assertTrue(result.contains("NOT role:*"), "Expected NOT role:* in result: " + result);
    }

    @Test
    void andMethodShouldGroupSameAttribute() {
        List<String> trs = List.of("group:admin", "group:user");
        String result = rules.andMethod(trs);
        assertTrue(result.contains("OR"), "Expected OR between values of same attribute: " + result);
        assertTrue(result.contains("group:admin"), "Expected group:admin in result: " + result);
        assertTrue(result.contains("group:user"), "Expected group:user in result: " + result);
        // Single attribute group means no AND connector between groups
        assertFalse(result.contains(" AND "), "Expected no AND for single attribute group: " + result);
    }

    @Test
    void orMethodShouldCreateCorrectQuery() {
        List<String> trs = List.of("attr1:val1", "attr2:val2");
        String result = rules.orMethod(trs);
        assertTrue(result.contains("OR"), "Expected OR in result: " + result);
        assertTrue(result.contains("attr1:val1"), "Expected attr1:val1 in result: " + result);
        assertTrue(result.contains("attr2:val2"), "Expected attr2:val2 in result: " + result);
        assertTrue(result.contains("NOT attr1:*"), "Expected NOT attr1:* in result: " + result);
        assertTrue(result.contains("NOT attr2:*"), "Expected NOT attr2:* in result: " + result);
    }

    @Test
    void ruleExpressionShouldDelegateToAndMethod() {
        List<String> trs = List.of("group:admin");
        String andResult = rules.ruleExpression(TurSNTargetingRuleMethod.AND, trs);
        String directResult = rules.andMethod(trs);
        assertEquals(directResult, andResult);
    }

    @Test
    void ruleExpressionShouldDelegateToOrMethod() {
        List<String> trs = List.of("group:admin");
        String orResult = rules.ruleExpression(TurSNTargetingRuleMethod.OR, trs);
        String directResult = rules.orMethod(trs);
        assertEquals(directResult, orResult);
    }

    @Test
    void andMethodShouldReturnEmptyForEmptyList() {
        assertEquals("", rules.andMethod(List.of()));
    }

    @Test
    void orMethodShouldHandleEmptyList() {
        String result = rules.orMethod(List.of());
        // With no entries, produces " OR (*:* )" pattern
        assertNotNull(result);
    }

    @Test
    void andMethodShouldSkipEntriesWithoutColon() {
        List<String> trs = List.of("nocolon", "valid:entry");
        String result = rules.andMethod(trs);
        assertTrue(result.contains("valid:entry"), "Expected valid:entry in result: " + result);
        assertFalse(result.contains("nocolon"), "Expected nocolon to be skipped: " + result);
    }

    @Test
    void andMethodShouldHandleSingleEntry() {
        List<String> trs = List.of("group:admin");
        String result = rules.andMethod(trs);
        assertTrue(result.contains("group:admin"), "Expected group:admin in result: " + result);
        assertTrue(result.contains("NOT group:*"), "Expected NOT group:* in result: " + result);
        assertFalse(result.contains(" AND "), "Expected no AND for single entry: " + result);
    }
}
