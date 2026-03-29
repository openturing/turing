package com.viglet.turing.api.sn.graphql;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

class TurSNSiteGraphQLNameUtilsTest {

    @Test
    void testBuildEnumToSiteNameMap_nullOrEmpty() {
        assertTrue(TurSNSiteGraphQLNameUtils.buildEnumToSiteNameMap(null).isEmpty());
        assertTrue(TurSNSiteGraphQLNameUtils.buildEnumToSiteNameMap(Collections.emptyList()).isEmpty());
    }

    @Test
    void testBuildEnumToSiteNameMap_validNames() {
        List<String> siteNames = Arrays.asList("Sample Site", "Sample Site", "unknown", "123 Site");
        Map<String, String> map = TurSNSiteGraphQLNameUtils.buildEnumToSiteNameMap(siteNames);

        assertEquals(4, map.size());
        assertEquals("Sample Site", map.get("SAMPLE_SITE"));
        assertEquals("Sample Site", map.get("SAMPLE_SITE_2"));
        assertEquals("unknown", map.get("UNKNOWN_2"));
        assertEquals("123 Site", map.get("SITE_123_SITE"));
    }

    @Test
    void testResolveGraphQLSiteArgument() {
        HashMap<String, String> map = new LinkedHashMap<>();
        map.put("SAMPLE_SITE", "Sample Site");

        assertEquals("argument", TurSNSiteGraphQLNameUtils.resolveGraphQLSiteArgument("argument", null));
        assertEquals("argument",
                TurSNSiteGraphQLNameUtils.resolveGraphQLSiteArgument("argument", new LinkedHashMap<>()));
        assertEquals("Sample Site", TurSNSiteGraphQLNameUtils.resolveGraphQLSiteArgument("SAMPLE_SITE", map));
        assertEquals("Sample Site", TurSNSiteGraphQLNameUtils.resolveGraphQLSiteArgument("sample_site", map));
        assertEquals("not_found", TurSNSiteGraphQLNameUtils.resolveGraphQLSiteArgument("not_found", map));
    }

    @Test
    void testToGraphQLFieldName() {
        assertNull(TurSNSiteGraphQLNameUtils.toGraphQLFieldName(null));
        assertEquals("simple", TurSNSiteGraphQLNameUtils.toGraphQLFieldName("simple"));
        assertEquals("my__field", TurSNSiteGraphQLNameUtils.toGraphQLFieldName("my-field"));
        assertEquals("a__b__c", TurSNSiteGraphQLNameUtils.toGraphQLFieldName("a-b-c"));
        assertEquals("no_hyphens", TurSNSiteGraphQLNameUtils.toGraphQLFieldName("no_hyphens"));
    }

    @Test
    void testToGraphQLEnumValue() {
        assertEquals("SITE", TurSNSiteGraphQLNameUtils.toGraphQLEnumValue(""));
        assertEquals("SITE", TurSNSiteGraphQLNameUtils.toGraphQLEnumValue("   "));
        assertEquals("SAMPLE_SITE", TurSNSiteGraphQLNameUtils.toGraphQLEnumValue("Sample Site!"));
        assertEquals("SITE_123", TurSNSiteGraphQLNameUtils.toGraphQLEnumValue("123"));
        assertEquals("TEST", TurSNSiteGraphQLNameUtils.toGraphQLEnumValue("__Test"));
    }
}
