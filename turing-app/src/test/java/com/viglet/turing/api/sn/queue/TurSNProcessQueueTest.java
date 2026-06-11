package com.viglet.turing.api.sn.queue;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.viglet.turing.client.sn.job.TurSNJobAction;
import com.viglet.turing.client.sn.job.TurSNJobAttributeSpec;
import com.viglet.turing.client.sn.job.TurSNJobItem;
import com.viglet.turing.client.sn.job.TurSNJobItems;
import com.viglet.turing.commons.se.field.TurSEFieldType;
import com.viglet.turing.commons.sn.field.TurSNFieldName;
import com.viglet.turing.persistence.model.se.TurSEInstance;
import com.viglet.turing.persistence.model.sn.TurSNSite;
import com.viglet.turing.persistence.model.sn.field.TurSNSiteField;
import com.viglet.turing.persistence.model.sn.field.TurSNSiteFieldExt;
import com.viglet.turing.persistence.model.sn.locale.TurSNSiteLocale;
import com.viglet.turing.persistence.repository.se.TurSEInstanceRepository;
import com.viglet.turing.persistence.repository.sn.TurSNSiteRepository;
import com.viglet.turing.persistence.repository.sn.field.TurSNSiteFieldExtFacetRepository;
import com.viglet.turing.persistence.repository.sn.field.TurSNSiteFieldExtRepository;
import com.viglet.turing.persistence.repository.sn.field.TurSNSiteFieldRepository;
import com.viglet.turing.persistence.repository.sn.locale.TurSNSiteLocaleRepository;
import com.viglet.turing.plugins.se.TurSearchEnginePlugin;
import com.viglet.turing.plugins.se.TurSearchEnginePluginFactory;
import com.viglet.turing.sn.spotlight.TurSNSpotlightProcess;

@ExtendWith(MockitoExtension.class)
class TurSNProcessQueueTest {

    @Mock
    private TurSearchEnginePluginFactory pluginFactory;
    @Mock
    private TurSNSiteRepository turSNSiteRepository;
    @Mock
    private TurSNSiteLocaleRepository turSNSiteLocaleRepository;
    @Mock
    private TurSNMergeProvidersProcess turSNMergeProvidersProcess;
    @Mock
    private TurSNSpotlightProcess turSNSpotlightProcess;
    @Mock
    private TurSNSiteFieldRepository turSNSiteFieldRepository;
    @Mock
    private TurSNSiteFieldExtRepository turSNSiteFieldExtRepository;
    @Mock
    private TurSNSiteFieldExtFacetRepository turSNSiteFieldExtFacetRepository;
    @Mock
    private TurSEInstanceRepository turSEInstanceRepository;

    @Mock
    private com.viglet.turing.tenant.TurJmsTenantPropagation turJmsTenantPropagation;
    @Mock
    private TurSNProcessQueue self;
    @InjectMocks
    private TurSNProcessQueue processQueue;

    @Test
    void testReceiveIndexingQueue_Create() {
        TurSNJobItems jobItems = new TurSNJobItems();
        TurSNJobItem item = new TurSNJobItem();
        item.setTurSNJobAction(TurSNJobAction.CREATE);
        item.setSiteNames(Collections.singletonList("site1"));
        item.setLocale(Locale.US);
        item.setSpecs(new ArrayList<>());

        Map<String, Object> attrs = new HashMap<>();
        attrs.put(TurSNFieldName.ID, "1");
        item.setAttributes(attrs);
        jobItems.add(item);

        TurSEInstance seInstance = new TurSEInstance();
        seInstance.setId("se1");

        TurSNSite site = new TurSNSite();
        site.setName("site1");
        site.setTurSEInstance(seInstance);

        TurSearchEnginePlugin plugin = mock(TurSearchEnginePlugin.class);

        when(turSNSiteRepository.findByNameIgnoreCase("site1")).thenReturn(Optional.of(site));
        when(turSNSpotlightProcess.isSpotlightJob(item)).thenReturn(false);
        when(turSNMergeProvidersProcess.mergeDocuments(eq(site), anyMap(), eq(Locale.US))).thenReturn(attrs);
        when(pluginFactory.getPluginForSite(site)).thenReturn(plugin);
        when(plugin.indexDocument(eq(site), eq(Locale.US), anyMap())).thenReturn(true);

        processQueue.processIndexingQueue(jobItems);

        verify(plugin, times(1)).indexDocument(eq(site), eq(Locale.US), anyMap());
    }

    @Test
    void testReceiveIndexingQueue_Delete() {
        TurSNJobItems jobItems = new TurSNJobItems();
        TurSNJobItem item = new TurSNJobItem();
        item.setTurSNJobAction(TurSNJobAction.DELETE);
        item.setSiteNames(Collections.singletonList("site1"));
        item.setLocale(Locale.US);

        Map<String, Object> attrs = new HashMap<>();
        attrs.put(TurSNFieldName.ID, "1");
        item.setAttributes(attrs);
        jobItems.add(item);

        TurSNSite site = new TurSNSite();
        site.setName("site1");

        TurSearchEnginePlugin plugin = mock(TurSearchEnginePlugin.class);

        when(turSNSiteRepository.findByNameIgnoreCase("site1")).thenReturn(Optional.of(site));
        when(turSNSpotlightProcess.isSpotlightJob(item)).thenReturn(false);
        when(pluginFactory.getPluginForSite(site)).thenReturn(plugin);
        when(plugin.deIndex(site, Locale.US, "1")).thenReturn(true);

        processQueue.processIndexingQueue(jobItems);

        verify(plugin, times(1)).deIndex(site, Locale.US, "1");
    }

    @Test
    void testReceiveIndexingQueue_Commit() {
        TurSNJobItems jobItems = new TurSNJobItems();
        TurSNJobItem item = new TurSNJobItem();
        item.setTurSNJobAction(TurSNJobAction.COMMIT);
        item.setSiteNames(Collections.singletonList("site1"));
        item.setLocale(Locale.US);

        Map<String, Object> attrs = new HashMap<>();
        item.setAttributes(attrs);
        jobItems.add(item);

        TurSNSite site = new TurSNSite();
        site.setName("site1");

        TurSearchEnginePlugin plugin = mock(TurSearchEnginePlugin.class);

        when(turSNSiteRepository.findByNameIgnoreCase("site1")).thenReturn(Optional.of(site));
        when(pluginFactory.getPluginForSite(site)).thenReturn(plugin);
        when(plugin.commit(site, Locale.US)).thenReturn(true);

        processQueue.processIndexingQueue(jobItems);

        verify(plugin, times(1)).commit(site, Locale.US);
    }

    @Test
    void testRemoveDuplicateTerms() {
        Map<String, Object> attrs = new HashMap<>();
        List<String> list = new ArrayList<>();
        list.add("term1");
        list.add("term1");
        list.add("term2");
        attrs.put("field1", list);

        Map<String, Object> result = processQueue.removeDuplicateTerms(attrs);
        List<?> resultList = (List<?>) result.get("field1");

        assertTrue(resultList.contains("term1"));
        assertTrue(resultList.contains("term2"));
        assertEquals(2, resultList.size());
    }

    @Test
    void testReceiveIndexingQueue_CreateSpotlight() {
        TurSNJobItems jobItems = new TurSNJobItems();
        TurSNJobItem item = new TurSNJobItem();
        item.setTurSNJobAction(TurSNJobAction.CREATE);
        item.setSiteNames(Collections.singletonList("site1"));
        item.setLocale(Locale.US);
        item.setAttributes(Map.of(TurSNFieldName.ID, "1"));
        item.setSpecs(new ArrayList<>());
        jobItems.add(item);

        TurSNSite site = new TurSNSite();
        site.setName("site1");
        when(turSNSiteRepository.findByNameIgnoreCase("site1")).thenReturn(Optional.of(site));
        when(turSNSpotlightProcess.isSpotlightJob(item)).thenReturn(true);
        when(turSNSpotlightProcess.createUnmanagedSpotlight(item, site)).thenReturn(true);

        processQueue.processIndexingQueue(jobItems);

        verify(turSNSpotlightProcess).createUnmanagedSpotlight(item, site);
        verify(pluginFactory, never()).getPluginForSite(any());
    }

    @Test
    void testReceiveIndexingQueue_DeleteSpotlight() {
        TurSNJobItems jobItems = new TurSNJobItems();
        TurSNJobItem item = new TurSNJobItem();
        item.setTurSNJobAction(TurSNJobAction.DELETE);
        item.setSiteNames(Collections.singletonList("site1"));
        item.setLocale(Locale.US);
        item.setAttributes(Map.of(TurSNFieldName.ID, "1"));
        jobItems.add(item);

        TurSNSite site = new TurSNSite();
        site.setName("site1");
        when(turSNSiteRepository.findByNameIgnoreCase("site1")).thenReturn(Optional.of(site));
        when(turSNSpotlightProcess.isSpotlightJob(item)).thenReturn(true);
        when(turSNSpotlightProcess.deleteUnmanagedSpotlight(item, site)).thenReturn(true);

        processQueue.processIndexingQueue(jobItems);

        verify(turSNSpotlightProcess).deleteUnmanagedSpotlight(item, site);
        verify(pluginFactory, never()).getPluginForSite(any());
    }

    @Test
    void testReceiveIndexingQueue_DeleteByType() {
        TurSNJobItems jobItems = new TurSNJobItems();
        TurSNJobItem item = new TurSNJobItem();
        item.setTurSNJobAction(TurSNJobAction.DELETE);
        item.setSiteNames(Collections.singletonList("site1"));
        item.setLocale(Locale.US);
        item.setAttributes(Map.of(TurSNFieldName.TYPE, "news"));
        jobItems.add(item);

        TurSNSite site = new TurSNSite();
        site.setName("site1");

        TurSearchEnginePlugin plugin = mock(TurSearchEnginePlugin.class);

        when(turSNSiteRepository.findByNameIgnoreCase("site1")).thenReturn(Optional.of(site));
        when(turSNSpotlightProcess.isSpotlightJob(item)).thenReturn(false);
        when(pluginFactory.getPluginForSite(site)).thenReturn(plugin);
        when(plugin.deIndexByType(site, Locale.US, "news")).thenReturn(true);

        processQueue.processIndexingQueue(jobItems);

        verify(plugin).deIndexByType(site, Locale.US, "news");
        verify(plugin, never()).deIndex(any(), any(), any());
    }

    @Test
    void testReceiveIndexingQueue_CommitPluginReturnsFalse() {
        TurSNJobItems jobItems = new TurSNJobItems();
        TurSNJobItem item = new TurSNJobItem();
        item.setTurSNJobAction(TurSNJobAction.COMMIT);
        item.setSiteNames(Collections.singletonList("site1"));
        item.setLocale(Locale.US);
        item.setAttributes(new HashMap<>());
        jobItems.add(item);

        TurSNSite site = new TurSNSite();
        site.setName("site1");

        TurSearchEnginePlugin plugin = mock(TurSearchEnginePlugin.class);

        when(turSNSiteRepository.findByNameIgnoreCase("site1")).thenReturn(Optional.of(site));
        when(pluginFactory.getPluginForSite(site)).thenReturn(plugin);
        when(plugin.commit(site, Locale.US)).thenReturn(false);

        processQueue.processIndexingQueue(jobItems);

        verify(plugin).commit(site, Locale.US);
    }

    @Test
    void testRemoveDuplicateTermsHandlesNullAndSimpleValues() {
        Map<String, Object> attrs = new HashMap<>();
        attrs.put("nullField", null);
        attrs.put("single", "value");

        Map<String, Object> result = processQueue.removeDuplicateTerms(attrs);

        assertTrue(result.containsKey("single"));
        assertTrue(!result.containsKey("nullField"));
        assertTrue(processQueue.removeDuplicateTerms(null).isEmpty());
    }

    @Test
    void testReceiveIndexingQueue_CreateCreatesMissingFieldAndSearchEngineField() {
        TurSNJobItems jobItems = new TurSNJobItems();
        TurSNJobItem item = new TurSNJobItem();
        item.setTurSNJobAction(TurSNJobAction.CREATE);
        item.setSiteNames(Collections.singletonList("site1"));
        item.setLocale(Locale.US);
        item.setAttributes(Map.of(TurSNFieldName.ID, "1"));

        TurSNJobAttributeSpec spec = TurSNJobAttributeSpec.builder()
                .name("customField")
                .type(TurSEFieldType.STRING)
                .description("Custom")
                .multiValued(false)
                .facet(true)
                .facetName(Map.of("default", "Custom", "pt_BR", "Personalizado"))
                .build();
        item.setSpecs(List.of(spec));
        jobItems.add(item);

        TurSEInstance siteSeInstance = new TurSEInstance();
        siteSeInstance.setId("se1");
        TurSNSite site = new TurSNSite();
        site.setName("site1");
        site.setTurSEInstance(siteSeInstance);

        TurSEInstance seInstance = new TurSEInstance();
        seInstance.setId("se1");

        TurSNSiteLocale locale = new TurSNSiteLocale();
        locale.setCore("core_en");
        locale.setTurSNSite(site);

        TurSearchEnginePlugin plugin = mock(TurSearchEnginePlugin.class);

        when(turSNSiteRepository.findByNameIgnoreCase("site1")).thenReturn(Optional.of(site));
        when(turSNSpotlightProcess.isSpotlightJob(item)).thenReturn(false);
        when(turSNMergeProvidersProcess.mergeDocuments(eq(site), anyMap(), eq(Locale.US)))
                .thenReturn(new HashMap<>(item.getAttributes()));
        when(turSNSiteFieldExtRepository.existsByTurSNSiteAndName(site, "customField")).thenReturn(false);
        when(turSNSiteLocaleRepository.findByTurSNSite(site)).thenReturn(List.of(locale));
        when(turSEInstanceRepository.findById("se1")).thenReturn(Optional.of(seInstance));
        when(turSNSiteFieldRepository.save(any(TurSNSiteField.class))).thenAnswer(invocation -> {
            TurSNSiteField field = invocation.getArgument(0);
            field.setId("f1");
            return field;
        });
        when(turSNSiteFieldExtRepository.save(any(TurSNSiteFieldExt.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(pluginFactory.getPluginForSite(site)).thenReturn(plugin);
        when(plugin.fieldExists(seInstance, "core_en", "customField")).thenReturn(false);
        when(plugin.indexDocument(eq(site), eq(Locale.US), anyMap())).thenReturn(true);

        processQueue.processIndexingQueue(jobItems);

        verify(turSNSiteFieldRepository).save(any(TurSNSiteField.class));
        verify(turSNSiteFieldExtRepository).save(any(TurSNSiteFieldExt.class));
        verify(turSNSiteFieldExtFacetRepository).saveAll(any());
        verify(plugin).addOrUpdateField(seInstance, "core_en",
                "customField", TurSEFieldType.STRING, true, false, true);
        verify(plugin).indexDocument(eq(site), eq(Locale.US), anyMap());
    }
}
