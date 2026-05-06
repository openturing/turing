package com.viglet.turing.api.se;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.viglet.turing.commons.se.TurSEParameters;
import com.viglet.turing.persistence.mapper.se.TurSEInstanceMapper;
import com.viglet.turing.persistence.model.se.TurSEInstance;
import com.viglet.turing.se.result.TurSEResults;
import com.viglet.turing.plugins.se.TurSearchEnginePlugin;
import com.viglet.turing.plugins.se.TurSearchEnginePluginFactory;
import com.viglet.turing.solr.TurSolr;
import com.viglet.turing.solr.TurSolrInstance;
import com.viglet.turing.solr.TurSolrInstanceProcess;
import com.viglet.turing.solr.bean.TurSECoreInfo;
import com.viglet.turing.solr.source.TurSolrInstanceSource;

/**
 * Tests for TurSEInstanceAPI.
 *
 * @author Alexandre Oliveira
 * @since 2026.1.14
 */
@ExtendWith(MockitoExtension.class)
class TurSEInstanceAPITest {

    private MockMvc mockMvc;

    @Mock
    private TurSolrInstanceSource seInstanceSource;

    @Mock
    private TurSolrInstanceProcess turSolrInstanceProcess;

    @Mock
    private TurSolr turSolr;

    @Mock
    private com.viglet.turing.domain.sn.TurSNSiteLocaleRepositoryPort turSNSiteLocaleRepositoryPort;

    @Mock
    private TurSearchEnginePluginFactory pluginFactory;

    @Spy
    private TurSEInstanceMapper turSEInstanceMapper = Mappers.getMapper(TurSEInstanceMapper.class);

    @InjectMocks
    private TurSEInstanceAPI turSEInstanceAPI;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(turSEInstanceAPI).build();
    }

    @Test
    void testTurSEInstanceList() throws Exception {
        TurSEInstance instance1 = new TurSEInstance();
        instance1.setId("1");
        instance1.setTitle("Instance 1");

        TurSEInstance instance2 = new TurSEInstance();
        instance2.setId("2");
        instance2.setTitle("Instance 2");

        List<TurSEInstance> instances = Arrays.asList(instance1, instance2);
        when(seInstanceSource.findAll()).thenReturn(instances);

        mockMvc.perform(get("/api/se"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("1"))
                .andExpect(jsonPath("$[0].title").value("Instance 1"))
                .andExpect(jsonPath("$[1].id").value("2"))
                .andExpect(jsonPath("$[1].title").value("Instance 2"));

        verify(seInstanceSource, times(1)).findAll();
    }

    @Test
    void testTurSearchEngineStructure() throws Exception {
        mockMvc.perform(get("/api/se/structure"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").doesNotExist())
                .andExpect(jsonPath("$.turSEVendor").exists());
    }

    @Test
    void testTurSEInstanceGet_Found() throws Exception {
        TurSEInstance instance = new TurSEInstance();
        instance.setId("1");
        instance.setTitle("Instance 1");

        when(seInstanceSource.findById("1")).thenReturn(Optional.of(instance));

        mockMvc.perform(get("/api/se/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("1"))
                .andExpect(jsonPath("$.title").value("Instance 1"));

        verify(seInstanceSource, times(1)).findById("1");
    }

    @Test
    void testTurSEInstanceGet_NotFound() throws Exception {
        when(seInstanceSource.findById("1")).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/se/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").doesNotExist());

        verify(seInstanceSource, times(1)).findById("1");
    }

    @Test
    void testTurSEInstanceUpdate() throws Exception {
        TurSEInstance existingInstance = new TurSEInstance();
        existingInstance.setId("1");
        existingInstance.setTitle("Old Title");

        TurSEInstance updatedInstance = new TurSEInstance();
        updatedInstance.setTitle("New Title");
        updatedInstance.setEndpointUrl("http://localhost:8983/solr");
        updatedInstance.setEnabled(1);

        when(seInstanceSource.findById("1")).thenReturn(Optional.of(existingInstance));

        mockMvc.perform(put("/api/se/1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(updatedInstance)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("New Title"))
                .andExpect(jsonPath("$.endpointUrl").value("http://localhost:8983/solr"))
                .andExpect(jsonPath("$.enabled").value(1));

        verify(seInstanceSource, times(1)).findById("1");
        verify(seInstanceSource, times(1)).save(any(TurSEInstance.class));
    }

    @Test
    void testTurSEInstanceUpdate_NotFound() throws Exception {
        TurSEInstance updatedInstance = new TurSEInstance();
        updatedInstance.setTitle("New Title");

        when(seInstanceSource.findById("1")).thenReturn(Optional.empty());

        mockMvc.perform(put("/api/se/1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(updatedInstance)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").doesNotExist());

        verify(seInstanceSource, times(1)).findById("1");
        verify(seInstanceSource, never()).save(any());
    }

    @Test
    void testTurSEInstanceDelete() throws Exception {
        mockMvc.perform(delete("/api/se/1"))
                .andExpect(status().isOk())
                .andExpect(content().string("true"));

        verify(seInstanceSource, times(1)).delete("1");
    }

    @Test
    void testTurSEInstanceAdd() throws Exception {
        TurSEInstance newInstance = new TurSEInstance();
        newInstance.setTitle("New Instance");

        mockMvc.perform(post("/api/se")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(newInstance)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("New Instance"));

        verify(seInstanceSource, times(1)).save(any(TurSEInstance.class));
    }

    @Test
    void testTurSEInstanceSelect_Found() throws Exception {
        TurSEInstance instance = new TurSEInstance();
        instance.setId("1");

        TurSolrInstance solrInstance = mock(TurSolrInstance.class);
        TurSEResults results = mock(TurSEResults.class);

        when(seInstanceSource.findById("1")).thenReturn(Optional.of(instance));
        when(turSolrInstanceProcess.initSolrInstance(instance, "core1")).thenReturn(Optional.of(solrInstance));
        when(turSolr.retrieveSolr(eq(solrInstance), any(TurSEParameters.class), eq("text"))).thenReturn(results);

        mockMvc.perform(get("/api/se/1/core1/select")
                .param("q", "test")
                .param("p", "2")
                .param("rows", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").exists());

        verify(seInstanceSource, times(1)).findById("1");
        verify(turSolrInstanceProcess, times(1)).initSolrInstance(instance, "core1");
        verify(turSolr, times(1)).retrieveSolr(eq(solrInstance), any(TurSEParameters.class), eq("text"));
    }

    @Test
    void testTurSEInstanceSelect_SolrInstanceNotFound() throws Exception {
        TurSEInstance instance = new TurSEInstance();
        instance.setId("1");

        when(seInstanceSource.findById("1")).thenReturn(Optional.of(instance));
        when(turSolrInstanceProcess.initSolrInstance(instance, "core1")).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/se/1/core1/select"))
                .andExpect(status().isNotFound());

        verify(seInstanceSource, times(1)).findById("1");
        verify(turSolrInstanceProcess, times(1)).initSolrInstance(instance, "core1");
        verify(turSolr, never()).retrieveSolr(any(), any(), any());
    }

    @Test
    void testTurSEInstanceSelect_InstanceNotFound() throws Exception {
        when(seInstanceSource.findById("1")).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/se/1/core1/select"))
                .andExpect(status().isNotFound());

        verify(seInstanceSource, times(1)).findById("1");
        verify(turSolrInstanceProcess, never()).initSolrInstance(any(TurSEInstance.class), anyString());
    }

    // --- Cores endpoint tests ---

    @Test
    void testTurSEInstanceCores_Found() throws Exception {
        TurSEInstance instance = new TurSEInstance();
        instance.setId("1");

        TurSearchEnginePlugin plugin = mock(TurSearchEnginePlugin.class);
        when(seInstanceSource.findById("1")).thenReturn(Optional.of(instance));
        when(pluginFactory.getPluginForInstance(instance)).thenReturn(plugin);
        when(plugin.listIndexes(instance)).thenReturn(
                List.of(new TurSECoreInfo("core1", 100, List.of())));
        when(turSNSiteLocaleRepositoryPort.findCoreUsage("core1")).thenReturn(List.of());

        mockMvc.perform(get("/api/se/1/cores"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("core1"))
                .andExpect(jsonPath("$[0].numDocs").value(100));
    }

    @Test
    void testTurSEInstanceCores_NotFound() throws Exception {
        when(seInstanceSource.findById("missing")).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/se/missing/cores"))
                .andExpect(status().isNotFound());
    }

    @Test
    void testTurSEInstanceCores_PopulatesSiteUsageFromPort() throws Exception {
        TurSEInstance instance = new TurSEInstance();
        instance.setId("1");

        TurSearchEnginePlugin plugin = mock(TurSearchEnginePlugin.class);
        when(seInstanceSource.findById("1")).thenReturn(Optional.of(instance));
        when(pluginFactory.getPluginForInstance(instance)).thenReturn(plugin);
        when(plugin.listIndexes(instance)).thenReturn(
                List.of(new TurSECoreInfo("core1", 7, List.of())));
        when(turSNSiteLocaleRepositoryPort.findCoreUsage("core1")).thenReturn(List.of(
                new com.viglet.turing.domain.sn.TurSNSiteCoreUsageDomain(
                        "locale-1", java.util.Locale.US, "site-1", "MarketingSite"),
                new com.viglet.turing.domain.sn.TurSNSiteCoreUsageDomain(
                        "locale-2", java.util.Locale.of("pt", "BR"), "site-2", "BlogPT")));

        mockMvc.perform(get("/api/se/1/cores"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("core1"))
                .andExpect(jsonPath("$[0].numDocs").value(7))
                .andExpect(jsonPath("$[0].usedBySites[0].siteId").value("site-1"))
                .andExpect(jsonPath("$[0].usedBySites[0].siteName").value("MarketingSite"))
                .andExpect(jsonPath("$[0].usedBySites[0].localeId").value("locale-1"))
                .andExpect(jsonPath("$[0].usedBySites[0].language").value("en_US"))
                .andExpect(jsonPath("$[0].usedBySites[1].siteId").value("site-2"))
                .andExpect(jsonPath("$[0].usedBySites[1].siteName").value("BlogPT"));
    }

    // --- Create core tests ---

    @Test
    void testCreateCore_Success() throws Exception {
        TurSEInstance instance = new TurSEInstance();
        instance.setId("1");

        TurSearchEnginePlugin plugin = mock(TurSearchEnginePlugin.class);
        when(seInstanceSource.findById("1")).thenReturn(Optional.of(instance));
        when(pluginFactory.getPluginForInstance(instance)).thenReturn(plugin);

        mockMvc.perform(post("/api/se/1/cores")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"new-core\",\"locale\":\"en\"}"))
                .andExpect(status().isCreated());
    }

    @Test
    void testCreateCore_BlankName() throws Exception {
        mockMvc.perform(post("/api/se/1/cores")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"\",\"locale\":\"en\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void testCreateCore_NullLocale() throws Exception {
        mockMvc.perform(post("/api/se/1/cores")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"core1\",\"locale\":\"\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void testCreateCore_InstanceNotFound() throws Exception {
        when(seInstanceSource.findById("missing")).thenReturn(Optional.empty());

        mockMvc.perform(post("/api/se/missing/cores")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"core1\",\"locale\":\"en\"}"))
                .andExpect(status().isNotFound());
    }

    // --- Delete core tests ---

    @Test
    void testDeleteCore_Success() throws Exception {
        TurSEInstance instance = new TurSEInstance();
        instance.setId("1");

        TurSearchEnginePlugin plugin = mock(TurSearchEnginePlugin.class);
        when(seInstanceSource.findById("1")).thenReturn(Optional.of(instance));
        when(pluginFactory.getPluginForInstance(instance)).thenReturn(plugin);
        when(turSNSiteLocaleRepositoryPort.existsByCore("core1")).thenReturn(false);

        mockMvc.perform(delete("/api/se/1/cores/core1"))
                .andExpect(status().isNoContent());
    }

    @Test
    void testDeleteCore_InUseByLocale() throws Exception {
        TurSEInstance instance = new TurSEInstance();
        instance.setId("1");

        when(seInstanceSource.findById("1")).thenReturn(Optional.of(instance));
        when(turSNSiteLocaleRepositoryPort.existsByCore("core1")).thenReturn(true);

        mockMvc.perform(delete("/api/se/1/cores/core1"))
                .andExpect(status().isConflict());
    }

    @Test
    void testDeleteCore_InstanceNotFound() throws Exception {
        when(seInstanceSource.findById("missing")).thenReturn(Optional.empty());

        mockMvc.perform(delete("/api/se/missing/cores/core1"))
                .andExpect(status().isNotFound());
    }

    // --- Clear core tests ---

    @Test
    void testClearCore_Success() throws Exception {
        TurSEInstance instance = new TurSEInstance();
        instance.setId("1");

        TurSearchEnginePlugin plugin = mock(TurSearchEnginePlugin.class);
        when(seInstanceSource.findById("1")).thenReturn(Optional.of(instance));
        when(pluginFactory.getPluginForInstance(instance)).thenReturn(plugin);

        mockMvc.perform(delete("/api/se/1/cores/core1/documents"))
                .andExpect(status().isNoContent());
    }

    @Test
    void testClearCore_InstanceNotFound() throws Exception {
        when(seInstanceSource.findById("missing")).thenReturn(Optional.empty());

        mockMvc.perform(delete("/api/se/missing/cores/core1/documents"))
                .andExpect(status().isNotFound());
    }

    // --- System info tests ---

    @Test
    void testSystemInfo_Success() throws Exception {
        TurSEInstance instance = new TurSEInstance();
        instance.setId("1");

        TurSearchEnginePlugin plugin = mock(TurSearchEnginePlugin.class);
        when(seInstanceSource.findById("1")).thenReturn(Optional.of(instance));
        when(pluginFactory.getPluginForInstance(instance)).thenReturn(plugin);
        when(plugin.getSystemInfo(instance)).thenReturn(java.util.Map.of("version", "9.0"));

        mockMvc.perform(get("/api/se/1/system-info"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value("9.0"));
    }

    @Test
    void testSystemInfo_InstanceNotFound() throws Exception {
        when(seInstanceSource.findById("missing")).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/se/missing/system-info"))
                .andExpect(status().isNotFound());
    }

    // --- Select with default params ---

    @Test
    void testTurSEInstanceSelect_DefaultPaginationParams() throws Exception {
        TurSEInstance instance = new TurSEInstance();
        instance.setId("1");

        TurSolrInstance solrInstance = mock(TurSolrInstance.class);
        TurSEResults results = mock(TurSEResults.class);

        when(seInstanceSource.findById("1")).thenReturn(Optional.of(instance));
        when(turSolrInstanceProcess.initSolrInstance(instance, "core1")).thenReturn(Optional.of(solrInstance));
        when(turSolr.retrieveSolr(eq(solrInstance), any(TurSEParameters.class), eq("text"))).thenReturn(results);

        mockMvc.perform(get("/api/se/1/core1/select"))
                .andExpect(status().isOk());
    }
}
