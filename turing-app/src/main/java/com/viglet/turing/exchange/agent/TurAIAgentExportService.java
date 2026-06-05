/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.exchange.agent;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.viglet.turing.commons.utils.TurCommonsUtils;
import com.viglet.turing.exchange.TurExchange;
import com.viglet.turing.persistence.model.agent.TurAIAgent;
import com.viglet.turing.persistence.model.agent.TurChatFlow;
import com.viglet.turing.persistence.model.customtool.TurCustomTool;
import com.viglet.turing.persistence.model.embedding.TurEmbeddingModel;
import com.viglet.turing.persistence.model.llm.TurLLMInstance;
import com.viglet.turing.persistence.model.mcp.TurMcpServer;
import com.viglet.turing.persistence.model.persona.TurPersona;
import com.viglet.turing.persistence.model.store.TurStoreInstance;
import com.viglet.turing.persistence.repository.agent.TurAIAgentRepository;
import com.viglet.turing.persistence.repository.agent.TurAIAgentSlotRepository;
import com.viglet.turing.persistence.repository.agent.TurAgentEvalSetRepository;
import com.viglet.turing.persistence.repository.agent.TurChatFlowRepository;
import com.viglet.turing.persistence.repository.intent.TurIntentRepository;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * Builds the exported ZIP bundle for one AI agent:
 * <ul>
 *   <li>{@code agent.json} — root {@link TurExchange} envelope with the agent
 *       inlined under {@code agents[0]}, owned children (slots/intents/
 *       chat-flow metadata) nested in it, and every referenced shared entity
 *       (personas, LLM instances, MCP servers, custom-tool metadata,
 *       embedding model, store) deduped at the root.</li>
 *   <li>{@code chat-flows/{slug}.chat-flow.json} — one file per chat-flow with
 *       the raw React-Flow graph extracted from {@code definitionJson}. Kept
 *       out of {@code agent.json} so diffs stay reviewable.</li>
 *   <li>{@code tools/{slug}.groovy} — one file per custom tool with the raw
 *       Groovy script extracted from {@code groovyScript}. Same diff-friendly
 *       rationale.</li>
 * </ul>
 *
 * <p>Secrets are not stripped explicitly: {@link TurLLMInstance#apiKey} is
 * {@code @Transient} + {@code @JsonProperty(WRITE_ONLY)} and
 * {@code apiKeyEncrypted} is {@code @JsonIgnore}, so neither leaves the
 * serializer. {@link TurMcpServer} carries no credential field.
 *
 * @author Alexandre Oliveira
 * @since 2026.2.8
 */
@Slf4j
@Service
public class TurAIAgentExportService {

    private static final String TMP_DIR = "store/tmp";
    /**
     * Same name SN site export uses, so a future combined export can carry both
     * an SN site and an agent in one envelope file. The agent block lives under
     * {@code agents[]} regardless of which feature triggered the export.
     */
    private static final String EXPORT_JSON = "export.json";
    private static final String CHAT_FLOWS_DIR = "chat-flows";
    private static final String TOOLS_DIR = "tools";
    private static final String ZIP_EXTENSION = ".zip";

    private final TurAIAgentRepository agentRepository;
    private final TurChatFlowRepository chatFlowRepository;
    private final TurAIAgentSlotRepository slotRepository;
    private final TurIntentRepository intentRepository;
    private final TurAgentEvalSetRepository evalSetRepository;

    public TurAIAgentExportService(TurAIAgentRepository agentRepository,
                                   TurChatFlowRepository chatFlowRepository,
                                   TurAIAgentSlotRepository slotRepository,
                                   TurIntentRepository intentRepository,
                                   TurAgentEvalSetRepository evalSetRepository) {
        this.agentRepository = agentRepository;
        this.chatFlowRepository = chatFlowRepository;
        this.slotRepository = slotRepository;
        this.intentRepository = intentRepository;
        this.evalSetRepository = evalSetRepository;
    }

    /**
     * Builds the export ZIP for the given agent. Returns the path to the
     * temp file; caller is responsible for streaming it to the HTTP response
     * and cleaning up afterwards (the temp folder containing the ZIP is named
     * {@code Agent_{millis}/} so it's easy to delete with one call).
     */
    public Path exportAgentToZip(String agentId) {
        TurAIAgent agent = agentRepository.findById(agentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "AI Agent not found: " + agentId));

        File exportDir = createExportDir();
        TurAIAgentExchange agentExchange = buildAgentExchange(agent, exportDir);
        ReferenceBag refs = collectReferences(agent);

        TurExchange envelope = buildEnvelope(agentExchange, refs, exportDir);
        File exportFile = writeAgentJson(exportDir, envelope);
        return zipExportDir(exportFile);
    }

    private File createExportDir() {
        File tmpDir = getOrCreateTmpDir();
        String folderName = "Agent_" + System.currentTimeMillis();
        File exportDir = Paths.get(tmpDir.getAbsolutePath(), folderName).toFile();
        if (!exportDir.exists() && !exportDir.mkdirs()) {
            throw new IllegalStateException("Failed to create export directory: " + exportDir.getAbsolutePath());
        }
        return exportDir;
    }

    private TurAIAgentExchange buildAgentExchange(TurAIAgent agent, File exportDir) {
        TurAIAgentExchange exchange = TurAIAgentExchange.fromEntity(agent);
        exchange.setSlots(slotRepository.findByTurAIAgent_IdOrderByNameAsc(agent.getId()));
        exchange.setIntents(intentRepository.findByTurAIAgent_IdOrderByTitleAsc(agent.getId()));
        List<TurChatFlow> flows = chatFlowRepository.findByTurAIAgent_IdOrderByNameAsc(agent.getId());
        exchange.setChatFlows(writeChatFlowFiles(exportDir, flows));
        // T285 — golden eval sets (cases inline) travel with the agent.
        exchange.setEvalSets(evalSetRepository.findByTurAIAgent_IdOrderByNameAsc(agent.getId()));
        return exchange;
    }

    /**
     * Walks the agent graph and dedupes every shared entity into ID-keyed
     * maps so the same persona / LLM / MCP server appears exactly once in
     * the envelope, even when reached through multiple paths (e.g. an
     * agent's persona AND its embedding model both point at the same LLM).
     */
    private ReferenceBag collectReferences(TurAIAgent agent) {
        ReferenceBag refs = new ReferenceBag();
        if (agent.getPersonas() != null) {
            agent.getPersonas().forEach(p -> refs.personas.put(p.getId(), p));
        }
        if (agent.getDefaultPersona() != null) {
            refs.personas.putIfAbsent(agent.getDefaultPersona().getId(), agent.getDefaultPersona());
        }
        if (agent.getLlmInstances() != null) {
            agent.getLlmInstances().forEach(l -> refs.llms.put(l.getId(), l));
        }
        if (agent.getMcpServers() != null) {
            agent.getMcpServers().forEach(m -> refs.mcpServers.put(m.getId(), m));
        }
        if (agent.getCustomTools() != null) {
            agent.getCustomTools().forEach(t -> refs.tools.put(t.getId(), t));
        }
        if (agent.getTurStoreInstance() != null) {
            refs.stores.put(agent.getTurStoreInstance().getId(), agent.getTurStoreInstance());
        }
        collectEmbeddingAndLinkedLlm(agent, refs);
        collectPersonaSubReferences(refs);
        return refs;
    }

    private static void collectEmbeddingAndLinkedLlm(TurAIAgent agent, ReferenceBag refs) {
        TurEmbeddingModel em = agent.getTurEmbeddingModelInstance();
        if (em == null) return;
        refs.embeddings.put(em.getId(), em);
        if (em.getTurLLMInstance() != null) {
            refs.llms.putIfAbsent(em.getTurLLMInstance().getId(), em.getTurLLMInstance());
        }
    }

    /** Personas may reference an MCP server (brand context) and a store (few-shot). */
    private static void collectPersonaSubReferences(ReferenceBag refs) {
        for (TurPersona p : refs.personas.values()) {
            if (p.getBrandContextMcpServer() != null) {
                refs.mcpServers.putIfAbsent(p.getBrandContextMcpServer().getId(), p.getBrandContextMcpServer());
            }
            if (p.getFewShotStore() != null) {
                refs.stores.putIfAbsent(p.getFewShotStore().getId(), p.getFewShotStore());
            }
        }
    }

    private TurExchange buildEnvelope(TurAIAgentExchange agentExchange, ReferenceBag refs, File exportDir) {
        List<TurCustomToolExchange> toolExchanges = writeCustomToolFiles(exportDir, refs.tools.values());
        TurExchange envelope = new TurExchange();
        envelope.setAgents(List.of(agentExchange));
        if (!refs.personas.isEmpty()) envelope.setPersonas(new ArrayList<>(refs.personas.values()));
        if (!refs.llms.isEmpty()) envelope.setLlm(new ArrayList<>(refs.llms.values()));
        if (!refs.mcpServers.isEmpty()) envelope.setMcpServers(new ArrayList<>(refs.mcpServers.values()));
        if (!refs.stores.isEmpty()) envelope.setStore(new ArrayList<>(refs.stores.values()));
        if (!refs.embeddings.isEmpty()) envelope.setEmbeddingModels(new ArrayList<>(refs.embeddings.values()));
        if (!toolExchanges.isEmpty()) envelope.setCustomTools(toolExchanges);
        return envelope;
    }

    private static final class ReferenceBag {
        final Map<String, TurPersona> personas = new LinkedHashMap<>();
        final Map<String, TurLLMInstance> llms = new LinkedHashMap<>();
        final Map<String, TurMcpServer> mcpServers = new LinkedHashMap<>();
        final Map<String, TurStoreInstance> stores = new LinkedHashMap<>();
        final Map<String, TurEmbeddingModel> embeddings = new LinkedHashMap<>();
        final Map<String, TurCustomTool> tools = new LinkedHashMap<>();
    }

    public File getOrCreateTmpDir() {
        File userDir = Paths.get(System.getProperty("user.dir")).toFile();
        File tmp = Paths.get(userDir.getAbsolutePath(), TMP_DIR).toFile();
        try {
            Files.createDirectories(tmp.toPath());
        } catch (IOException e) {
            throw new IllegalStateException("Temp directory creation failed", e);
        }
        return tmp;
    }

    private List<TurChatFlowExchange> writeChatFlowFiles(File exportDir, List<TurChatFlow> flows) {
        List<TurChatFlowExchange> exchanges = new ArrayList<>();
        if (flows.isEmpty()) return exchanges;
        File flowsDir = new File(exportDir, CHAT_FLOWS_DIR);
        if (!flowsDir.exists() && !flowsDir.mkdirs()) {
            throw new IllegalStateException("Failed to create chat-flows directory: " + flowsDir.getAbsolutePath());
        }
        for (TurChatFlow flow : flows) {
            String slug = slugify(flow.getName(), flow.getId());
            String filename = slug + ".chat-flow.json";
            File file = new File(flowsDir, filename);
            String definition = flow.getDefinitionJson() == null ? "{}" : flow.getDefinitionJson();
            try {
                Files.writeString(file.toPath(), definition, StandardCharsets.UTF_8);
            } catch (IOException e) {
                throw new IllegalStateException("Failed to write chat-flow file: " + filename, e);
            }
            exchanges.add(TurChatFlowExchange.fromEntity(flow, CHAT_FLOWS_DIR + "/" + filename));
        }
        return exchanges;
    }

    private List<TurCustomToolExchange> writeCustomToolFiles(File exportDir, Iterable<TurCustomTool> tools) {
        List<TurCustomToolExchange> exchanges = new ArrayList<>();
        File toolsDir = null;
        for (TurCustomTool tool : tools) {
            if (toolsDir == null) {
                toolsDir = new File(exportDir, TOOLS_DIR);
                if (!toolsDir.exists() && !toolsDir.mkdirs()) {
                    throw new IllegalStateException("Failed to create tools directory: " + toolsDir.getAbsolutePath());
                }
            }
            String slug = slugify(tool.getTitle(), tool.getId());
            String filename = slug + ".groovy";
            File file = new File(toolsDir, filename);
            String script = tool.getGroovyScript() == null ? "" : tool.getGroovyScript();
            try {
                Files.writeString(file.toPath(), script, StandardCharsets.UTF_8);
            } catch (IOException e) {
                throw new IllegalStateException("Failed to write custom-tool file: " + filename, e);
            }
            exchanges.add(TurCustomToolExchange.fromEntity(tool, TOOLS_DIR + "/" + filename));
        }
        return exchanges;
    }

    private File writeAgentJson(File exportDir, TurExchange envelope) {
        File exportFile = new File(exportDir, EXPORT_JSON);
        try {
            JsonMapper mapper = JsonMapper.builder()
                    .configure(SerializationFeature.INDENT_OUTPUT, true)
                    .build();
            Path tmp = Files.createTempFile(exportDir.toPath(), "agent-", ".json");
            mapper.writer().writeValue(tmp.toFile(), envelope);
            Files.move(tmp, exportFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new IllegalStateException("Export file creation failed", e);
        }
        return exportFile;
    }

    private Path zipExportDir(File exportFile) {
        File zipFile = new File(exportFile.getParent() + ZIP_EXTENSION);
        try {
            TurCommonsUtils.addFilesToZip(exportFile.getParentFile(), zipFile);
        } catch (Exception e) {
            throw new IllegalStateException("Zip file creation failed", e);
        }
        return zipFile.toPath();
    }

    public void cleanup(Path zipPath) {
        if (zipPath == null) return;
        try {
            File zipFile = zipPath.toFile();
            // The export folder is the zip filename minus the .zip extension
            String absolutePath = zipFile.getAbsolutePath();
            File exportDir = new File(absolutePath.substring(0, absolutePath.length() - ZIP_EXTENSION.length()));
            if (exportDir.exists()) {
                deleteRecursively(exportDir);
            }
            Files.deleteIfExists(zipPath);
        } catch (IOException e) {
            log.warn("Cleanup failed for {}: {}", zipPath, e.getMessage());
        }
    }

    private static void deleteRecursively(File f) throws IOException {
        if (f.isDirectory()) {
            File[] children = f.listFiles();
            if (children != null) {
                for (File c : children) deleteRecursively(c);
            }
        }
        Files.deleteIfExists(f.toPath());
    }

    /**
     * Produces a filesystem-safe slug from the entity name with the ID suffix
     * as a stable disambiguator. Two flows named {@code "Programa-Match"}
     * coexist as {@code programa-match-{id1}.chat-flow.json} and
     * {@code programa-match-{id2}.chat-flow.json}.
     */
    private static String slugify(String name, String id) {
        String base = (name == null ? "untitled" : name)
                .toLowerCase()
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-)|(-$)", "");
        if (base.isEmpty()) base = "untitled";
        String idSuffix = id == null ? "" : id.substring(0, Math.min(8, id.length()));
        return base + "-" + idSuffix;
    }
}
