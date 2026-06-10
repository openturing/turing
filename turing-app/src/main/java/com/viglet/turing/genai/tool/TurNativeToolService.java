package com.viglet.turing.genai.tool;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.stereotype.Service;

import lombok.extern.slf4j.Slf4j;

/**
 * Service that discovers and lists all native tool callings
 * (methods annotated with {@code @Tool}) available in the application.
 *
 * @author Alexandre Oliveira
 * @since 2026.1.14
 */
@Slf4j
@Service
public class TurNativeToolService {

    private static final String TOOL_SERVICE_SUFFIX = "ToolService";

    private final List<Object> toolServiceInstances;
    private final Map<String, NativeToolGroup> toolGroups;

    public TurNativeToolService(
            TurDslToolService dslToolService,
            TurDateTimeToolService dateTimeToolService,
            TurFinanceToolService financeToolService,
            TurWeatherToolService weatherToolService,
            TurCodeInterpreterToolService codeInterpreterToolService,
            TurWebCrawlerToolService webCrawlerToolService,
            TurImageSearchToolService imageSearchToolService,
            TurLoggingToolService loggingToolService,
            TurIntegrationMonitoringToolService integrationMonitoringToolService,
            TurSystemInfoToolService systemInfoToolService,
            TurIconifyToolService iconifyToolService,
            TurScaffoldToolService scaffoldToolService) {

        this.toolServiceInstances = List.of(
                dslToolService,
                dateTimeToolService,
                financeToolService,
                weatherToolService,
                codeInterpreterToolService,
                webCrawlerToolService,
                imageSearchToolService,
                loggingToolService,
                integrationMonitoringToolService,
                systemInfoToolService,
                iconifyToolService,
                scaffoldToolService);

        this.toolGroups = discoverToolGroups();
        log.info("[NativeTool] Discovered {} tool groups with {} total tools",
                toolGroups.size(), toolGroups.values().stream()
                        .mapToInt(g -> g.tools().size()).sum());
    }

    /**
     * Returns all discovered native tool groups.
     */
    public Collection<NativeToolGroup> getToolGroups() {
        return toolGroups.values();
    }

    /**
     * Returns a flat list of all native tool descriptors.
     */
    public List<NativeToolDescriptor> getAllTools() {
        return toolGroups.values().stream()
                .flatMap(g -> g.tools().stream())
                .toList();
    }

    /**
     * Returns the tool group ID for a given service class.
     */
    public static String groupIdFromClass(Class<?> serviceClass) {
        String name = serviceClass.getSimpleName();
        if (name.startsWith("Tur") && name.endsWith(TOOL_SERVICE_SUFFIX)) {
            name = name.substring(3, name.length() - TOOL_SERVICE_SUFFIX.length());
        }
        return camelToKebab(name);
    }

    /**
     * Creates ToolCallback[] only for the specified tool names.
     */
    public ToolCallback[] getToolCallbacks(Set<String> toolNames) {
        if (toolNames == null || toolNames.isEmpty()) {
            return new ToolCallback[0];
        }
        List<ToolCallback> filtered = new ArrayList<>();
        for (Object svc : toolServiceInstances) {
            ToolCallback[] callbacks = MethodToolCallbackProvider.builder()
                    .toolObjects(svc)
                    .build()
                    .getToolCallbacks();
            for (ToolCallback cb : callbacks) {
                if (toolNames.contains(cb.getToolDefinition().name())) {
                    filtered.add(cb);
                }
            }
        }
        return filtered.toArray(new ToolCallback[0]);
    }

    /**
     * Creates ToolCallback[] for all native tools.
     */
    public ToolCallback[] getAllToolCallbacks() {
        return MethodToolCallbackProvider.builder()
                .toolObjects(toolServiceInstances.toArray())
                .build()
                .getToolCallbacks();
    }

    private Map<String, NativeToolGroup> discoverToolGroups() {
        Map<String, NativeToolGroup> groups = new LinkedHashMap<>();
        for (Object svc : toolServiceInstances) {
            Class<?> clazz = svc.getClass();
            String groupId = groupIdFromClass(clazz);
            String groupTitle = humanizeClassName(clazz);

            List<NativeToolDescriptor> tools = new ArrayList<>();
            for (Method method : clazz.getDeclaredMethods()) {
                Tool toolAnnotation = method.getAnnotation(Tool.class);
                if (toolAnnotation != null) {
                    String toolName = toolAnnotation.name().isEmpty()
                            ? method.getName() : toolAnnotation.name();
                    String description = extractFirstLine(toolAnnotation.description());
                    tools.add(new NativeToolDescriptor(toolName, description, groupId));
                }
            }
            tools.sort(Comparator.comparing(NativeToolDescriptor::name));

            if (!tools.isEmpty()) {
                groups.put(groupId, new NativeToolGroup(groupId, groupTitle, tools));
            }
        }
        return groups;
    }

    private static String humanizeClassName(Class<?> clazz) {
        String name = clazz.getSimpleName();
        if (name.startsWith("Tur") && name.endsWith(TOOL_SERVICE_SUFFIX)) {
            name = name.substring(3, name.length() - TOOL_SERVICE_SUFFIX.length());
        }
        return Arrays.stream(name.split("(?=[A-Z])"))
                .filter(s -> !s.isEmpty())
                .collect(Collectors.joining(" "));
    }

    private static String camelToKebab(String input) {
        return input.replaceAll("([a-z])([A-Z])", "$1-$2").toLowerCase();
    }

    private static String extractFirstLine(String description) {
        if (description == null || description.isBlank()) return "";
        String[] lines = description.strip().split("\\n");
        return lines[0].strip();
    }

    public record NativeToolDescriptor(String name, String description, String groupId) {
    }

    public record NativeToolGroup(String id, String title, List<NativeToolDescriptor> tools) {
    }
}
