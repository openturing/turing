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

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import org.apache.solr.common.SolrDocument;
import org.apache.solr.common.SolrDocumentList;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import com.viglet.turing.commons.sn.field.TurSNFieldName;
import com.viglet.turing.persistence.model.sn.TurSNSite;
import com.viglet.turing.persistence.model.sn.merge.TurSNSiteMergeProviders;
import com.viglet.turing.persistence.model.sn.merge.TurSNSiteMergeProvidersField;
import com.viglet.turing.persistence.repository.sn.merge.TurSNSiteMergeProvidersRepository;
import com.viglet.turing.se.result.TurSEResult;
import com.viglet.turing.solr.TurSolr;
import com.viglet.turing.solr.TurSolrInstance;
import com.viglet.turing.solr.TurSolrInstanceProcess;
import com.viglet.turing.solr.TurSolrUtils;

@ExtendWith(MockitoExtension.class)
class TurSNMergeProvidersProcessTest {

        @Mock
        private TurSolrInstanceProcess turSolrInstanceProcess;

        @Mock
        private TurSolr turSolr;

        @Mock
        private TurSNSiteMergeProvidersRepository turSNSiteMergeProvidersRepository;

        @InjectMocks
        private TurSNMergeProvidersProcess process;

        @BeforeEach
        void setUp() {
                // No additional setup required; mocks are injected via @InjectMocks
        }

        @Test
        void testMergeDocuments_EmptyProviders() {
                TurSNSite site = new TurSNSite();
                Map<String, Object> attrs = new HashMap<>();

                when(turSNSiteMergeProvidersRepository.findByTurSNSite(site)).thenReturn(Collections.emptyList());

                Map<String, Object> result = process.mergeDocuments(site, attrs, Locale.US);
                assertEquals(attrs, result);
        }

        @Test
        void testMergeDocuments_ProviderFrom() {
                TurSNSite site = new TurSNSite();
                site.setName("site1");

                TurSNSiteMergeProviders mergeProviders = new TurSNSiteMergeProviders();
                mergeProviders.setTurSNSite(site);
                mergeProviders.setProviderFrom("providerA");
                mergeProviders.setProviderTo("providerB");
                mergeProviders.setRelationFrom("relFrom");
                mergeProviders.setRelationTo("relTo");
                mergeProviders.setLocale(Locale.US);

                TurSNSiteMergeProvidersField field = new TurSNSiteMergeProvidersField();
                field.setName("field1");
                mergeProviders.setOverwrittenFields(new HashSet<>(Collections.singletonList(field)));

                when(turSNSiteMergeProvidersRepository.findByTurSNSite(site))
                                .thenReturn(Collections.singletonList(mergeProviders));

                Map<String, Object> attrs = new HashMap<>();
                attrs.put(TurSNFieldName.SOURCE_APPS, "providerA");
                attrs.put("relFrom", "value1");

                TurSolrInstance solrInstance = mock(TurSolrInstance.class);
                when(turSolrInstanceProcess.initSolrInstance(eq("site1"), any(Locale.class)))
                                .thenReturn(Optional.of(solrInstance));

                SolrDocumentList emptyList = new SolrDocumentList();
                when(turSolr.solrResultAnd(eq(solrInstance), anyMap())).thenReturn(emptyList);

                Map<String, Object> result = process.mergeDocuments(site, attrs, Locale.US);
                assertEquals(attrs, result);
        }

        @Test
        void testMergeDocuments_ProviderTo() {
                TurSNSite site = new TurSNSite();
                site.setName("site1");

                TurSNSiteMergeProviders mergeProviders = new TurSNSiteMergeProviders();
                mergeProviders.setTurSNSite(site);
                mergeProviders.setProviderFrom("providerA");
                mergeProviders.setProviderTo("providerB");
                mergeProviders.setRelationFrom("relFrom");
                mergeProviders.setRelationTo("relTo");
                mergeProviders.setLocale(Locale.US);

                when(turSNSiteMergeProvidersRepository.findByTurSNSite(site))
                                .thenReturn(Collections.singletonList(mergeProviders));

                Map<String, Object> attrs = new HashMap<>();
                attrs.put(TurSNFieldName.SOURCE_APPS, "providerB");
                attrs.put("relTo", "value1");
                attrs.put(TurSNFieldName.ID, "1");

                TurSolrInstance solrInstance = mock(TurSolrInstance.class);
                when(turSolrInstanceProcess.initSolrInstance(eq("site1"), any(Locale.class)))
                                .thenReturn(Optional.of(solrInstance));

                SolrDocumentList emptyList = new SolrDocumentList();
                when(turSolr.solrResultAnd(eq(solrInstance), anyMap())).thenReturn(emptyList);

                Map<String, Object> result = process.mergeDocuments(site, attrs, Locale.US);
                assertEquals(attrs, result);
        }

        @Test
        void testMergeDocuments_ProviderFromMergesFromAndToResultAndOverwritesFields() {
                TurSNSite site = new TurSNSite();
                site.setName("site1");

                TurSNSiteMergeProviders mergeProviders = new TurSNSiteMergeProviders();
                mergeProviders.setTurSNSite(site);
                mergeProviders.setProviderFrom("providerA");
                mergeProviders.setProviderTo("providerB");
                mergeProviders.setRelationFrom("relFrom");
                mergeProviders.setRelationTo("relTo");
                mergeProviders.setLocale(Locale.US);

                TurSNSiteMergeProvidersField overwrittenField = new TurSNSiteMergeProvidersField();
                overwrittenField.setName("title");
                mergeProviders.setOverwrittenFields(new HashSet<>(Collections.singletonList(overwrittenField)));

                when(turSNSiteMergeProvidersRepository.findByTurSNSite(site))
                                .thenReturn(Collections.singletonList(mergeProviders));

                Map<String, Object> attrs = new HashMap<>();
                attrs.put(TurSNFieldName.SOURCE_APPS, List.of("providerA", "providerX"));
                attrs.put("relFrom", "value1");
                attrs.put("title", "queue-title");

                TurSolrInstance solrInstance = mock(TurSolrInstance.class);
                when(turSolrInstanceProcess.initSolrInstance("site1", Locale.US))
                                .thenReturn(Optional.of(solrInstance));

                SolrDocumentList empty1 = new SolrDocumentList();
                SolrDocumentList empty2 = new SolrDocumentList();
                SolrDocumentList fromAndTo = new SolrDocumentList();
                SolrDocument mergedDoc = new SolrDocument();
                mergedDoc.setField(TurSNFieldName.ID, "doc-1");
                fromAndTo.add(mergedDoc);

                when(turSolr.solrResultAnd(eq(solrInstance), anyMap())).thenReturn(empty1, empty2, fromAndTo);

                Map<String, Object> fromAndToFields = new HashMap<>();
                fromAndToFields.put(TurSNFieldName.SOURCE_APPS, List.of("providerB"));
                fromAndToFields.put("title", "search-title");

                try (MockedStatic<TurSolrUtils> utils = Mockito.mockStatic(TurSolrUtils.class)) {
                        utils.when(() -> TurSolrUtils.createTurSEResultFromDocument(mergedDoc))
                                        .thenReturn(TurSEResult.builder().fields(fromAndToFields).build());

                        Map<String, Object> result = process.mergeDocuments(site, attrs, Locale.US);

                        assertEquals("queue-title", result.get("title"));
                        assertTrue(result.containsKey(TurSNFieldName.SOURCE_APPS));
                        @SuppressWarnings("unchecked")
                        List<String> providers = (List<String>) result.get(TurSNFieldName.SOURCE_APPS);
                        assertTrue(providers.contains("providerA"));
                        assertTrue(providers.contains("providerB"));
                        verify(turSolr, never()).deIndexing(any(), any());
                }
        }

        @Test
        void testMergeDocuments_ProviderToMergesFromResultAndDeindexesPreviousDocument() {
                TurSNSite site = new TurSNSite();
                site.setName("site1");

                TurSNSiteMergeProviders mergeProviders = new TurSNSiteMergeProviders();
                mergeProviders.setTurSNSite(site);
                mergeProviders.setProviderFrom("providerA");
                mergeProviders.setProviderTo("providerB");
                mergeProviders.setRelationFrom("relFrom");
                mergeProviders.setRelationTo("relTo");
                mergeProviders.setLocale(Locale.US);

                TurSNSiteMergeProvidersField overwrittenField = new TurSNSiteMergeProvidersField();
                overwrittenField.setName("headline");
                mergeProviders.setOverwrittenFields(new HashSet<>(Collections.singletonList(overwrittenField)));

                when(turSNSiteMergeProvidersRepository.findByTurSNSite(site))
                                .thenReturn(Collections.singletonList(mergeProviders));

                Map<String, Object> attrs = new HashMap<>();
                attrs.put(TurSNFieldName.SOURCE_APPS, "providerB");
                attrs.put("relTo", "group-1");
                attrs.put(TurSNFieldName.ID, "id-200");

                TurSolrInstance solrInstance = mock(TurSolrInstance.class);
                when(turSolrInstanceProcess.initSolrInstance("site1", Locale.US))
                                .thenReturn(Optional.of(solrInstance));

                SolrDocumentList fromResults = new SolrDocumentList();
                SolrDocument fromDoc = new SolrDocument();
                fromDoc.setField(TurSNFieldName.ID, "old-id");
                fromResults.add(fromDoc);
                SolrDocumentList fromAndToResults = new SolrDocumentList();

                when(turSolr.solrResultAnd(eq(solrInstance), anyMap())).thenReturn(fromResults, fromAndToResults);

                Map<String, Object> fromFields = new HashMap<>();
                fromFields.put("headline", "merged headline");
                fromFields.put(TurSNFieldName.SOURCE_APPS, "providerA");

                try (MockedStatic<TurSolrUtils> utils = Mockito.mockStatic(TurSolrUtils.class)) {
                        utils.when(() -> TurSolrUtils.createTurSEResultFromDocument(fromDoc))
                                        .thenReturn(TurSEResult.builder().fields(fromFields).build());

                        Map<String, Object> result = process.mergeDocuments(site, attrs, Locale.US);

                        assertEquals("merged headline", result.get("headline"));
                        @SuppressWarnings("unchecked")
                        List<String> providers = (List<String>) result.get(TurSNFieldName.SOURCE_APPS);
                        assertTrue(providers.contains("providerA"));
                        assertTrue(providers.contains("providerB"));
                        verify(turSolr).deIndexing(solrInstance, "old-id");
                }
        }

        @Test
        void testMergeDocuments_UsesMergeLocaleWhenLocaleParameterIsNull() {
                TurSNSite site = new TurSNSite();
                site.setName("site1");

                TurSNSiteMergeProviders mergeProviders = new TurSNSiteMergeProviders();
                mergeProviders.setTurSNSite(site);
                mergeProviders.setProviderFrom("providerA");
                mergeProviders.setProviderTo("providerB");
                mergeProviders.setRelationFrom("relFrom");
                mergeProviders.setRelationTo("relTo");
                mergeProviders.setLocale(Locale.CANADA_FRENCH);

                when(turSNSiteMergeProvidersRepository.findByTurSNSite(site))
                                .thenReturn(Collections.singletonList(mergeProviders));

                Map<String, Object> attrs = new HashMap<>();
                attrs.put(TurSNFieldName.SOURCE_APPS, "providerA");
                attrs.put("relFrom", "value1");

                TurSolrInstance solrInstance = mock(TurSolrInstance.class);
                when(turSolrInstanceProcess.initSolrInstance("site1", Locale.CANADA_FRENCH))
                                .thenReturn(Optional.of(solrInstance));
                when(turSolr.solrResultAnd(eq(solrInstance), anyMap()))
                                .thenReturn(new SolrDocumentList(), new SolrDocumentList(), new SolrDocumentList());

                Map<String, Object> result = process.mergeDocuments(site, attrs, null);

                assertEquals(attrs, result);
                verify(turSolrInstanceProcess, times(3)).initSolrInstance("site1", Locale.CANADA_FRENCH);
        }
}
