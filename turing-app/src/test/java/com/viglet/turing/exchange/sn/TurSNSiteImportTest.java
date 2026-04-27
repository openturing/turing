package com.viglet.turing.exchange.sn;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;

import com.viglet.turing.exchange.TurExchange;
import com.viglet.turing.persistence.model.llm.TurLLMInstance;
import com.viglet.turing.persistence.model.llm.TurLLMVendor;
import com.viglet.turing.persistence.model.se.TurSEInstance;
import com.viglet.turing.persistence.model.se.TurSEVendor;
import com.viglet.turing.persistence.model.sn.TurSNSite;
import com.viglet.turing.persistence.model.sn.field.TurSNSiteField;
import com.viglet.turing.persistence.model.sn.field.TurSNSiteFieldExt;
import com.viglet.turing.persistence.model.sn.field.TurSNSiteFieldExtFacet;
import com.viglet.turing.persistence.model.sn.genai.TurSNSiteGenAi;
import com.viglet.turing.persistence.model.sn.locale.TurSNSiteLocale;
import com.viglet.turing.persistence.model.sn.merge.TurSNSiteMergeProviders;
import com.viglet.turing.persistence.model.sn.merge.TurSNSiteMergeProvidersField;
import com.viglet.turing.persistence.model.sn.ranking.TurSNRankingCondition;
import com.viglet.turing.persistence.model.sn.ranking.TurSNRankingExpression;
import com.viglet.turing.persistence.model.sn.spotlight.TurSNSiteSpotlight;
import com.viglet.turing.persistence.model.sn.spotlight.TurSNSiteSpotlightDocument;
import com.viglet.turing.persistence.model.sn.spotlight.TurSNSiteSpotlightTerm;
import com.viglet.turing.persistence.model.store.TurStoreInstance;
import com.viglet.turing.persistence.model.store.TurStoreVendor;
import com.viglet.turing.persistence.repository.llm.TurLLMInstanceRepository;
import com.viglet.turing.persistence.repository.llm.TurLLMVendorRepository;
import com.viglet.turing.persistence.repository.se.TurSEInstanceRepository;
import com.viglet.turing.persistence.repository.se.TurSEVendorRepository;
import com.viglet.turing.persistence.repository.sn.TurSNSiteRepository;
import com.viglet.turing.persistence.repository.sn.field.TurSNSiteFieldExtRepository;
import com.viglet.turing.persistence.repository.sn.field.TurSNSiteFieldRepository;
import com.viglet.turing.persistence.repository.sn.genai.TurSNSiteGenAiRepository;
import com.viglet.turing.persistence.repository.sn.locale.TurSNSiteLocaleRepository;
import com.viglet.turing.persistence.repository.sn.merge.TurSNSiteMergeProvidersRepository;
import com.viglet.turing.persistence.repository.sn.ranking.TurSNRankingConditionRepository;
import com.viglet.turing.persistence.repository.sn.ranking.TurSNRankingExpressionRepository;
import com.viglet.turing.persistence.repository.sn.spotlight.TurSNSiteSpotlightRepository;
import com.viglet.turing.persistence.repository.store.TurStoreInstanceRepository;
import com.viglet.turing.persistence.repository.store.TurStoreVendorRepository;

@ExtendWith(MockitoExtension.class)
class TurSNSiteImportTest {

        @Mock
        private TurSNSiteRepository turSNSiteRepository;
        @Mock
        private TurSEInstanceRepository turSEInstanceRepository;
        @Mock
        private TurSEVendorRepository turSEVendorRepository;
        @Mock
        private TurSNSiteFieldRepository turSNSiteFieldRepository;
        @Mock
        private TurSNSiteFieldExtRepository turSNSiteFieldExtRepository;
        @Mock
        private TurSNSiteLocaleRepository turSNSiteLocaleRepository;
        @Mock
        private TurSNSiteSpotlightRepository turSNSiteSpotlightRepository;
        @Mock
        private TurSNRankingExpressionRepository turSNRankingExpressionRepository;
        @Mock
        private TurSNRankingConditionRepository turSNRankingConditionRepository;
        @Mock
        private TurSNSiteGenAiRepository turSNSiteGenAiRepository;
        @Mock
        private com.viglet.turing.persistence.repository.agent.TurAIAgentRepository turAIAgentRepository;
        @Mock
        private TurLLMInstanceRepository turLLMInstanceRepository;
        @Mock
        private TurLLMVendorRepository turLLMVendorRepository;
        @Mock
        private TurStoreInstanceRepository turStoreInstanceRepository;
        @Mock
        private TurStoreVendorRepository turStoreVendorRepository;
        @Mock
        private TurSNSiteMergeProvidersRepository turSNSiteMergeProvidersRepository;
        @Mock
        private CacheManager cacheManager;
        @Mock
        private Cache cache;

        @InjectMocks
        private TurSNSiteImport turSNSiteImport;

        @BeforeEach
        void setUp() {
                when(cacheManager.getCacheNames()).thenReturn(Collections.emptySet());
                lenient().when(turSNSiteRepository.findByNameIgnoreCase(any())).thenReturn(Optional.empty());
        }

        @Test
        void shouldCreateReferencedRootInstancesBeforeImportingSites() {
                TurSEVendor seVendor = new TurSEVendor();
                seVendor.setId("SOLR");

                TurSEInstance exportedSe = new TurSEInstance();
                exportedSe.setId("se-1");
                exportedSe.setTitle("SE 1");
                exportedSe.setDescription("SE Desc");
                exportedSe.setEnabled(1);
                exportedSe.setEndpointUrl("http://localhost:8983/solr");
                exportedSe.setTurSEVendor(seVendor);

                TurLLMVendor llmVendor = new TurLLMVendor();
                llmVendor.setId("OPENAI");

                TurLLMInstance exportedLlm = new TurLLMInstance();
                exportedLlm.setId("llm-1");
                exportedLlm.setTitle("LLM 1");
                exportedLlm.setDescription("LLM Desc");
                exportedLlm.setEnabled(1);
                exportedLlm.setUrl("http://localhost:11434");
                exportedLlm.setTurLLMVendor(llmVendor);

                TurStoreVendor storeVendor = new TurStoreVendor();
                storeVendor.setId("CHROMA");

                TurStoreInstance exportedStore = new TurStoreInstance();
                exportedStore.setId("store-1");
                exportedStore.setTitle("Store 1");
                exportedStore.setDescription("Store Desc");
                exportedStore.setEnabled(1);
                exportedStore.setUrl("http://localhost:8000");
                exportedStore.setTurStoreVendor(storeVendor);

                TurExchange exchange = new TurExchange();
                exchange.setSnSites(List.of());
                exchange.setSe(List.of(exportedSe));
                exchange.setLlm(List.of(exportedLlm));
                exchange.setStore(List.of(exportedStore));

                when(turSEInstanceRepository.findById("se-1")).thenReturn(Optional.empty());
                when(turSEVendorRepository.findById("SOLR")).thenReturn(Optional.of(seVendor));
                when(turSEInstanceRepository.save(any(TurSEInstance.class)))
                                .thenAnswer(invocation -> invocation.getArgument(0));

                when(turLLMInstanceRepository.findById("llm-1")).thenReturn(Optional.empty());
                when(turLLMVendorRepository.findById("OPENAI")).thenReturn(Optional.of(llmVendor));
                when(turLLMInstanceRepository.save(any(TurLLMInstance.class)))
                                .thenAnswer(invocation -> invocation.getArgument(0));

                when(turStoreInstanceRepository.findById("store-1")).thenReturn(Optional.empty());
                when(turStoreVendorRepository.findById("CHROMA")).thenReturn(Optional.of(storeVendor));
                when(turStoreInstanceRepository.save(any(TurStoreInstance.class)))
                                .thenAnswer(invocation -> invocation.getArgument(0));

                turSNSiteImport.importSNSite(exchange);

                verify(turSEInstanceRepository).save(any(TurSEInstance.class));
                verify(turLLMInstanceRepository).save(any(TurLLMInstance.class));
                verify(turStoreInstanceRepository).save(any(TurStoreInstance.class));
        }

        @Test
        void shouldResolveGenAiLLMAndStoreByIdUsingRootCollections() {
                TurSEInstance existingSe = new TurSEInstance();
                existingSe.setId("se-1");

                TurLLMVendor llmVendor = new TurLLMVendor();
                llmVendor.setId("OPENAI");
                TurLLMInstance existingLlm = new TurLLMInstance();
                existingLlm.setId("llm-1");
                existingLlm.setTitle("LLM 1");
                existingLlm.setUrl("http://localhost:11434");
                existingLlm.setTurLLMVendor(llmVendor);

                TurStoreVendor storeVendor = new TurStoreVendor();
                storeVendor.setId("CHROMA");
                TurStoreInstance existingStore = new TurStoreInstance();
                existingStore.setId("store-1");
                existingStore.setTitle("Store 1");
                existingStore.setUrl("http://localhost:8000");
                existingStore.setTurStoreVendor(storeVendor);

                com.viglet.turing.persistence.model.agent.TurAIAgent existingAgent =
                                new com.viglet.turing.persistence.model.agent.TurAIAgent();
                existingAgent.setId("agent-1");

                TurSNSiteGenAiExchange genAiRef = new TurSNSiteGenAiExchange();
                genAiRef.setTurAIAgent("agent-1");

                TurSNSiteExchange siteExchange = new TurSNSiteExchange();
                siteExchange.setId("site-1");
                siteExchange.setName("Site 1");
                siteExchange.setTurSEInstance("se-1");
                siteExchange.setTurSNSiteGenAi(genAiRef);

                TurExchange exchange = new TurExchange();
                exchange.setSnSites(List.of(siteExchange));
                exchange.setLlm(List.of(existingLlm));
                exchange.setStore(List.of(existingStore));

                when(turSNSiteRepository.findById("site-1")).thenReturn(Optional.empty());
                when(turSEInstanceRepository.findById("se-1")).thenReturn(Optional.of(existingSe));
                when(turAIAgentRepository.findById("agent-1")).thenReturn(Optional.of(existingAgent));
                when(turSNSiteGenAiRepository.save(any(TurSNSiteGenAi.class)))
                                .thenAnswer(invocation -> invocation.getArgument(0));
                when(turSNSiteRepository.saveAndFlush(any(TurSNSite.class)))
                                .thenAnswer(invocation -> invocation.getArgument(0));

                turSNSiteImport.importSNSite(exchange);

                ArgumentCaptor<TurSNSiteGenAi> genAiCaptor = ArgumentCaptor.forClass(TurSNSiteGenAi.class);
                verify(turSNSiteGenAiRepository).save(genAiCaptor.capture());

                TurSNSiteGenAi savedGenAi = genAiCaptor.getValue();
                org.assertj.core.api.Assertions.assertThat(savedGenAi.getTurAIAgent()).isNotNull();
                org.assertj.core.api.Assertions.assertThat(savedGenAi.getTurAIAgent().getId())
                                .isEqualTo("agent-1");

                verify(turSNSiteRepository).saveAndFlush(any(TurSNSite.class));
                verify(turSEInstanceRepository).findById("se-1");
        }

        @Test
        void shouldAssignFirstAvailableSEInstanceWhenMissingInExchange() {
                TurSEInstance existingSe = new TurSEInstance();
                existingSe.setId("se-available");
                existingSe.setTitle("Default SE");

                TurSNSiteExchange siteExchange = new TurSNSiteExchange();
                siteExchange.setId("site-1");
                siteExchange.setName("Site 1");

                TurExchange exchange = new TurExchange();
                exchange.setSnSites(List.of(siteExchange));

                when(turSNSiteRepository.findById("site-1")).thenReturn(Optional.empty());
                when(turSEInstanceRepository.findAll()).thenReturn(List.of(existingSe));
                when(turSNSiteRepository.saveAndFlush(any(TurSNSite.class)))
                                .thenAnswer(invocation -> invocation.getArgument(0));

                turSNSiteImport.importSNSite(exchange);

                ArgumentCaptor<TurSNSite> siteCaptor = ArgumentCaptor.forClass(TurSNSite.class);
                verify(turSNSiteRepository).saveAndFlush(siteCaptor.capture());
                org.assertj.core.api.Assertions.assertThat(siteCaptor.getValue().getTurSEInstance())
                                .isNotNull();
                org.assertj.core.api.Assertions.assertThat(siteCaptor.getValue().getTurSEInstance().getId())
                                .isEqualTo("se-available");
        }

        @Test
        void shouldDeleteExistingSiteBeforeReimport() {
                TurSEInstance seInstance = new TurSEInstance();
                seInstance.setId("se-1");

                TurSNSite existingSite = new TurSNSite();
                existingSite.setId("site-1");
                existingSite.setName("Old Site");

                TurSNSiteExchange siteExchange = new TurSNSiteExchange();
                siteExchange.setId("site-1");
                siteExchange.setName("Site 1");
                siteExchange.setTurSEInstance("se-1");

                TurExchange exchange = new TurExchange();
                exchange.setSnSites(List.of(siteExchange));

                when(turSNSiteRepository.findById("site-1")).thenReturn(Optional.of(existingSite));
                when(turSEInstanceRepository.findById("se-1")).thenReturn(Optional.of(seInstance));
                when(turSNSiteRepository.saveAndFlush(any(TurSNSite.class)))
                                .thenAnswer(invocation -> invocation.getArgument(0));

                turSNSiteImport.importSNSite(exchange);

                verify(turSNSiteRepository).delete(existingSite);
                verify(turSNSiteRepository).flush();
                verify(turSNSiteRepository).saveAndFlush(any(TurSNSite.class));
        }

        @Test
        void shouldClearOnlySemanticNavigationCaches() {
                TurExchange exchange = new TurExchange();
                exchange.setSnSites(List.of());

                Set<String> caches = new HashSet<>(Set.of("turSNCache", "spotlightCache", "otherCache"));
                when(cacheManager.getCacheNames()).thenReturn(caches);
                when(cacheManager.getCache("turSNCache")).thenReturn(cache);
                when(cacheManager.getCache("spotlightCache")).thenReturn(cache);

                turSNSiteImport.importSNSite(exchange);

                verify(cache, times(2)).clear();
        }

        @Test
        void shouldCreateDefaultSEInstanceWhenNoInstanceExists() {
                TurSEVendor vendor = new TurSEVendor();
                vendor.setId("SOLR");
                vendor.setTitle("Solr Vendor");

                TurSEInstance createdSe = new TurSEInstance();
                createdSe.setId("se-new");
                createdSe.setTitle("Created SE");

                TurSNSiteExchange siteExchange = new TurSNSiteExchange();
                siteExchange.setId("site-default-se");
                siteExchange.setName("Site Default");

                TurExchange exchange = new TurExchange();
                exchange.setSnSites(List.of(siteExchange));

                when(turSNSiteRepository.findById("site-default-se")).thenReturn(Optional.empty());
                when(turSEInstanceRepository.findAll()).thenReturn(List.of());
                when(turSEVendorRepository.findById("SOLR")).thenReturn(Optional.of(vendor));
                when(turSEInstanceRepository.save(any(TurSEInstance.class))).thenReturn(createdSe);
                when(turSNSiteRepository.saveAndFlush(any(TurSNSite.class)))
                                .thenAnswer(invocation -> invocation.getArgument(0));

                turSNSiteImport.importSNSite(exchange);

                verify(turSEInstanceRepository).save(any(TurSEInstance.class));
                ArgumentCaptor<TurSNSite> siteCaptor = ArgumentCaptor.forClass(TurSNSite.class);
                verify(turSNSiteRepository).saveAndFlush(siteCaptor.capture());
                org.assertj.core.api.Assertions.assertThat(siteCaptor.getValue().getTurSEInstance()).isNotNull();
                org.assertj.core.api.Assertions.assertThat(siteCaptor.getValue().getTurSEInstance().getId())
                                .isEqualTo("se-new");
        }

        @Test
        void shouldSkipSiteWhenNoSEInstanceOrVendorAvailable() {
                TurSNSiteExchange siteExchange = new TurSNSiteExchange();
                siteExchange.setId("site-no-se");
                siteExchange.setName("Site No SE");

                TurExchange exchange = new TurExchange();
                exchange.setSnSites(List.of(siteExchange));

                when(turSNSiteRepository.findById("site-no-se")).thenReturn(Optional.empty());
                when(turSEInstanceRepository.findAll()).thenReturn(List.of());
                when(turSEVendorRepository.findById("SOLR")).thenReturn(Optional.empty());
                when(turSEVendorRepository.findAll()).thenReturn(List.of());

                turSNSiteImport.importSNSite(exchange);

                verify(turSNSiteRepository, never()).saveAndFlush(any(TurSNSite.class));
        }

        @Test
        void shouldPersistNestedSiteEntitiesDuringImport() {
                TurSEInstance seInstance = new TurSEInstance();
                seInstance.setId("se-1");

                TurSNSiteField field = new TurSNSiteField();
                field.setName("title");

                TurSNSiteFieldExtFacet facetLocale = new TurSNSiteFieldExtFacet();
                facetLocale.setLabel("Category");
                TurSNSiteFieldExt fieldExt = TurSNSiteFieldExt.builder().name("category").build();
                fieldExt.setFacetLocales(Set.of(facetLocale));

                TurSNSiteLocale locale = new TurSNSiteLocale();
                locale.setLanguage(java.util.Locale.US);

                TurSNSiteSpotlightTerm spotlightTerm = new TurSNSiteSpotlightTerm();
                spotlightTerm.setName("java");
                TurSNSiteSpotlightDocument spotlightDoc = new TurSNSiteSpotlightDocument();
                spotlightDoc.setTitle("Doc");
                TurSNSiteSpotlight spotlight = new TurSNSiteSpotlight();
                spotlight.setTurSNSiteSpotlightTerms(Set.of(spotlightTerm));
                spotlight.setTurSNSiteSpotlightDocuments(Set.of(spotlightDoc));

                TurSNRankingCondition rankingCondition = new TurSNRankingCondition();
                rankingCondition.setAttribute("type");
                rankingCondition.setValue("article");
                TurSNRankingExpression rankingExpression = new TurSNRankingExpression();
                rankingExpression.setName("Boost");
                rankingExpression.setTurSNRankingConditions(Set.of(rankingCondition));

                TurSNSiteMergeProvidersField overwrittenField = new TurSNSiteMergeProvidersField();
                overwrittenField.setName("title");
                TurSNSiteMergeProviders mergeProvider = new TurSNSiteMergeProviders();
                mergeProvider.setOverwrittenFields(Set.of(overwrittenField));

                TurSNSiteExchange siteExchange = new TurSNSiteExchange();
                siteExchange.setId("site-nested");
                siteExchange.setName("Site Nested");
                siteExchange.setTurSEInstance("se-1");
                siteExchange.setTurSNSiteFields(Set.of(field));
                siteExchange.setTurSNSiteFieldExts(Set.of(fieldExt));
                siteExchange.setTurSNSiteLocales(Set.of(locale));
                siteExchange.setTurSNSiteSpotlights(Set.of(spotlight));
                siteExchange.setTurSNRankingExpressions(Set.of(rankingExpression));
                siteExchange.setTurSNSiteMergeProviders(Set.of(mergeProvider));

                TurExchange exchange = new TurExchange();
                exchange.setSnSites(List.of(siteExchange));

                when(turSNSiteRepository.findById("site-nested")).thenReturn(Optional.empty());
                when(turSEInstanceRepository.findById("se-1")).thenReturn(Optional.of(seInstance));
                when(turSNSiteRepository.saveAndFlush(any(TurSNSite.class)))
                                .thenAnswer(invocation -> invocation.getArgument(0));

                turSNSiteImport.importSNSite(exchange);

                verify(turSNSiteFieldRepository).save(any(TurSNSiteField.class));
                verify(turSNSiteFieldExtRepository).save(any(TurSNSiteFieldExt.class));
                verify(turSNSiteLocaleRepository).save(any(TurSNSiteLocale.class));
                verify(turSNSiteSpotlightRepository).save(any(TurSNSiteSpotlight.class));
                verify(turSNRankingExpressionRepository).save(any(TurSNRankingExpression.class));
                verify(turSNRankingConditionRepository).save(any(TurSNRankingCondition.class));
                verify(turSNSiteMergeProvidersRepository).save(any(TurSNSiteMergeProviders.class));
        }

        @Test
        void shouldRenameSiteWhenNameAlreadyExistsWithDifferentId() {
                TurSEInstance seInstance = new TurSEInstance();
                seInstance.setId("se-1");

                TurSNSite existingSite = new TurSNSite();
                existingSite.setId("other-id");
                existingSite.setName("MySite");

                TurSNSiteExchange siteExchange = new TurSNSiteExchange();
                siteExchange.setId("site-new");
                siteExchange.setName("MySite");
                siteExchange.setTurSEInstance("se-1");

                TurExchange exchange = new TurExchange();
                exchange.setSnSites(List.of(siteExchange));

                when(turSNSiteRepository.findById("site-new")).thenReturn(Optional.empty());
                when(turSEInstanceRepository.findById("se-1")).thenReturn(Optional.of(seInstance));
                when(turSNSiteRepository.findByNameIgnoreCase("MySite")).thenReturn(Optional.of(existingSite));
                when(turSNSiteRepository.findByNameIgnoreCase("MySite (1)")).thenReturn(Optional.empty());
                when(turSNSiteRepository.saveAndFlush(any(TurSNSite.class)))
                                .thenAnswer(invocation -> invocation.getArgument(0));

                turSNSiteImport.importSNSite(exchange);

                ArgumentCaptor<TurSNSite> siteCaptor = ArgumentCaptor.forClass(TurSNSite.class);
                verify(turSNSiteRepository).saveAndFlush(siteCaptor.capture());
                org.assertj.core.api.Assertions.assertThat(siteCaptor.getValue().getName())
                                .isEqualTo("MySite (1)");
        }

        @Test
        void shouldIncrementSuffixWhenMultipleDuplicatesExist() {
                TurSEInstance seInstance = new TurSEInstance();
                seInstance.setId("se-1");

                TurSNSite existingSite1 = new TurSNSite();
                existingSite1.setId("other-id-1");
                existingSite1.setName("MySite");

                TurSNSite existingSite2 = new TurSNSite();
                existingSite2.setId("other-id-2");
                existingSite2.setName("MySite (1)");

                TurSNSiteExchange siteExchange = new TurSNSiteExchange();
                siteExchange.setId("site-new");
                siteExchange.setName("MySite");
                siteExchange.setTurSEInstance("se-1");

                TurExchange exchange = new TurExchange();
                exchange.setSnSites(List.of(siteExchange));

                when(turSNSiteRepository.findById("site-new")).thenReturn(Optional.empty());
                when(turSEInstanceRepository.findById("se-1")).thenReturn(Optional.of(seInstance));
                when(turSNSiteRepository.findByNameIgnoreCase("MySite")).thenReturn(Optional.of(existingSite1));
                when(turSNSiteRepository.findByNameIgnoreCase("MySite (1)")).thenReturn(Optional.of(existingSite2));
                when(turSNSiteRepository.findByNameIgnoreCase("MySite (2)")).thenReturn(Optional.empty());
                when(turSNSiteRepository.saveAndFlush(any(TurSNSite.class)))
                                .thenAnswer(invocation -> invocation.getArgument(0));

                turSNSiteImport.importSNSite(exchange);

                ArgumentCaptor<TurSNSite> siteCaptor = ArgumentCaptor.forClass(TurSNSite.class);
                verify(turSNSiteRepository).saveAndFlush(siteCaptor.capture());
                org.assertj.core.api.Assertions.assertThat(siteCaptor.getValue().getName())
                                .isEqualTo("MySite (2)");
        }
}
