package com.viglet.turing.genai.tool;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * Tests for TurNativeToolService.
 *
 * @author Alexandre Oliveira
 * @since 2026.1.14
 */
class TurNativeToolServiceTest {

    @ParameterizedTest
    @CsvSource({
            "TurDslToolService, dsl",
            "TurDateTimeToolService, date-time",
            "TurFinanceToolService, finance",
            "TurCodeInterpreterToolService, code-interpreter",
            "TurLoggingToolService, logging",
            "TurSystemInfoToolService, system-info",
            "TurIntegrationMonitoringToolService, integration-monitoring",
            "TurWeatherToolService, weather",
            "TurWebCrawlerToolService, web-crawler",
            "TurImageSearchToolService, image-search"
    })
    void groupIdFromClassShouldConvertClassNameToKebabCase(String className, String expectedId) {
        // Simulate what groupIdFromClass does with class simple names
        String name = className;
        if (name.startsWith("Tur") && name.endsWith("ToolService")) {
            name = name.substring(3, name.length() - "ToolService".length());
        }
        String result = name.replaceAll("([a-z])([A-Z])", "$1-$2").toLowerCase();
        assertThat(result).isEqualTo(expectedId);
    }

    @Test
    void groupIdFromClassShouldHandleActualClass() {
        String result = TurNativeToolService.groupIdFromClass(TurDateTimeToolService.class);
        assertThat(result).isEqualTo("date-time");
    }

    @Test
    void groupIdFromClassShouldHandleNonToolServiceClass() {
        // Class that doesn't match Tur*ToolService pattern
        String result = TurNativeToolService.groupIdFromClass(String.class);
        assertThat(result).isEqualTo("string");
    }

    @Test
    void nativeToolDescriptorRecordShouldHoldValues() {
        var descriptor = new TurNativeToolService.NativeToolDescriptor("test_tool", "A test tool", "test-group");
        assertThat(descriptor.name()).isEqualTo("test_tool");
        assertThat(descriptor.description()).isEqualTo("A test tool");
        assertThat(descriptor.groupId()).isEqualTo("test-group");
    }

    @Test
    void nativeToolGroupRecordShouldHoldValues() {
        var tool = new TurNativeToolService.NativeToolDescriptor("tool1", "desc", "grp");
        var group = new TurNativeToolService.NativeToolGroup("grp", "Group Title", java.util.List.of(tool));
        assertThat(group.id()).isEqualTo("grp");
        assertThat(group.title()).isEqualTo("Group Title");
        assertThat(group.tools()).hasSize(1);
        assertThat(group.tools().getFirst().name()).isEqualTo("tool1");
    }

    @Test
    void nativeToolDescriptorEqualityShouldWork() {
        var d1 = new TurNativeToolService.NativeToolDescriptor("tool", "desc", "grp");
        var d2 = new TurNativeToolService.NativeToolDescriptor("tool", "desc", "grp");
        var d3 = new TurNativeToolService.NativeToolDescriptor("other", "desc", "grp");
        assertThat(d1)
                .isEqualTo(d2)
                .isNotEqualTo(d3);
    }

    @Test
    void nativeToolGroupEqualityShouldWork() {
        var tools = java.util.List.of(new TurNativeToolService.NativeToolDescriptor("t", "d", "g"));
        var g1 = new TurNativeToolService.NativeToolGroup("g", "title", tools);
        var g2 = new TurNativeToolService.NativeToolGroup("g", "title", tools);
        assertThat(g1).isEqualTo(g2);
    }
}
