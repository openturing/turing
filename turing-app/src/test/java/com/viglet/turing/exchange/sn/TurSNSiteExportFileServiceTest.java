package com.viglet.turing.exchange.sn;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.viglet.turing.persistence.model.llm.TurLLMInstance;
import com.viglet.turing.persistence.model.llm.TurLLMVendor;
import com.viglet.turing.persistence.model.se.TurSEInstance;
import com.viglet.turing.persistence.model.se.TurSEVendor;
import com.viglet.turing.persistence.model.sn.TurSNSite;
import com.viglet.turing.persistence.model.sn.genai.TurSNSiteGenAi;
import com.viglet.turing.persistence.model.store.TurStoreInstance;
import com.viglet.turing.persistence.model.store.TurStoreVendor;

class TurSNSiteExportFileServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void shouldExportRootReferencesWithoutDuplicatingLLMStoreAndSEInsideSNSite() throws Exception {
        TurSNSiteExportFileService service = new TurSNSiteExportFileService(new TurSNSiteExportValidator(),
                org.mockito.Mockito.mock(com.viglet.turing.service.storage.TurStorageService.class));

        TurSEVendor seVendor = new TurSEVendor();
        seVendor.setId("SOLR");
        seVendor.setTitle("Solr");

        TurSEInstance seInstance = new TurSEInstance();
        seInstance.setId("se-1");
        seInstance.setTitle("Search Engine 1");
        seInstance.setDescription("SE Desc");
        seInstance.setEnabled(1);
        seInstance.setEndpointUrl("http://localhost:8983/solr");
        seInstance.setTurSEVendor(seVendor);

        TurLLMVendor llmVendor = new TurLLMVendor();
        llmVendor.setId("OPENAI");
        llmVendor.setTitle("OpenAI");

        TurLLMInstance llmInstance = new TurLLMInstance();
        llmInstance.setId("llm-1");
        llmInstance.setTitle("LLM 1");
        llmInstance.setDescription("LLM Desc");
        llmInstance.setEnabled(1);
        llmInstance.setUrl("http://localhost:11434");
        llmInstance.setTurLLMVendor(llmVendor);

        TurStoreVendor storeVendor = new TurStoreVendor();
        storeVendor.setId("CHROMA");
        storeVendor.setTitle("Chroma");

        TurStoreInstance storeInstance = new TurStoreInstance();
        storeInstance.setId("store-1");
        storeInstance.setTitle("Store 1");
        storeInstance.setDescription("Store Desc");
        storeInstance.setEnabled(1);
        storeInstance.setUrl("http://localhost:8000");
        storeInstance.setTurStoreVendor(storeVendor);

        com.viglet.turing.persistence.model.agent.TurAIAgent agent =
                new com.viglet.turing.persistence.model.agent.TurAIAgent();
        agent.setId("agent-1");
        agent.setEnabled(1);
        agent.setRagEnabled(true);
        agent.getLlmInstances().add(llmInstance);
        agent.setTurStoreInstance(storeInstance);

        TurSNSiteGenAi genAi = new TurSNSiteGenAi();
        genAi.setId("genai-1");
        genAi.setSitePrompt("This site is...");
        genAi.setTurAIAgent(agent);

        TurSNSite siteA = new TurSNSite();
        siteA.setId("site-a");
        siteA.setName("Site A");
        siteA.setFacet(0);
        siteA.setHl(0);
        siteA.setMlt(0);
        siteA.setThesaurus(0);
        siteA.setTurSEInstance(seInstance);
        siteA.setTurSNSiteGenAi(genAi);

        TurSNSite siteB = new TurSNSite();
        siteB.setId("site-b");
        siteB.setName("Site B");
        siteB.setFacet(0);
        siteB.setHl(0);
        siteB.setMlt(0);
        siteB.setThesaurus(0);
        siteB.setTurSEInstance(seInstance);
        siteB.setTurSNSiteGenAi(genAi);

        Path zipPath = service.exportSNSitesToZip(List.of(siteA, siteB), null, false);
        Path exportFolder = Path.of(zipPath.toString().replace(".zip", ""));

        try {
            JsonNode rootJson = readExportJsonFromZip(zipPath);

            assertThat(rootJson.path("snSites")).hasSize(2);
            assertThat(rootJson.path("llm")).hasSize(1);
            assertThat(rootJson.path("store")).hasSize(1);
            assertThat(rootJson.path("se")).hasSize(1);

            JsonNode firstSite = rootJson.path("snSites").get(0);
            assertThat(firstSite.path("turSEInstance").asText()).isEqualTo("se-1");
            // The site GenAI now references the AI agent only — LLM and store
            // come from the agent and are collected at the root of the export.
            assertThat(firstSite.path("turSNSiteGenAi").path("turAIAgent").asText())
                    .isEqualTo("agent-1");

            assertThat(rootJson.path("llm").get(0).path("id").asText()).isEqualTo("llm-1");
            assertThat(rootJson.path("llm").get(0).path("title").asText()).isEqualTo("LLM 1");
            assertThat(rootJson.path("store").get(0).path("id").asText()).isEqualTo("store-1");
            assertThat(rootJson.path("se").get(0).path("id").asText()).isEqualTo("se-1");
        } finally {
            Files.deleteIfExists(zipPath);
            if (Files.exists(exportFolder)) {
                FileUtils.deleteDirectory(exportFolder.toFile());
            }
        }
    }

    private JsonNode readExportJsonFromZip(Path zipPath) throws IOException {
        try (ZipFile zipFile = new ZipFile(zipPath.toFile())) {
            ZipEntry exportEntry = zipFile.getEntry("export.json");
            assertThat(exportEntry).isNotNull();
            return objectMapper.readTree(zipFile.getInputStream(exportEntry));
        }
    }
}
