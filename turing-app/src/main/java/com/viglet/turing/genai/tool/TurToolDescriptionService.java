package com.viglet.turing.genai.tool;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * Loads tool descriptions from {@code classpath:prompts/tools/**&#47;*.md} files.
 * <p>
 * Files are organized in subdirectories per tool service
 * (e.g., {@code prompts/tools/finance/get_stock_quote.md}).
 * The filename (without .md) must match the {@code @Tool(name)} value.
 * Content replaces the {@code @Tool(description)} at runtime via
 * {@link TurToolDescriptionCallback}.
 *
 * @author Alexandre Oliveira
 * @since 2026.1.14
 */
@Service
public class TurToolDescriptionService {

    private static final Logger log = LoggerFactory.getLogger(TurToolDescriptionService.class);
    private final Map<String, String> descriptions = new HashMap<>();

    @PostConstruct
    void load() throws IOException {
        var resolver = new PathMatchingResourcePatternResolver();
        Resource[] resources = resolver.getResources("classpath:prompts/tools/**/*.md");
        for (Resource resource : resources) {
            String filename = resource.getFilename();
            if (filename == null) continue;
            String toolName = filename.replace(".md", "");
            String content = resource.getContentAsString(StandardCharsets.UTF_8).strip();
            descriptions.put(toolName, content);
            log.info("Loaded tool description for '{}' ({} chars)", toolName, content.length());
        }
        log.info("Loaded {} tool description overrides from classpath:prompts/tools/", descriptions.size());
    }

    /**
     * Returns the .md description for the given tool name, or {@code null} if no override exists.
     */
    public String getDescription(String toolName) {
        return descriptions.get(toolName);
    }

    public boolean hasDescription(String toolName) {
        return descriptions.containsKey(toolName);
    }
}
