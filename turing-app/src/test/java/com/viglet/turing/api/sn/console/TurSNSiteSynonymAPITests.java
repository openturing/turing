package com.viglet.turing.api.sn.console;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.security.Principal;
import java.util.List;
import java.util.Locale;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.RequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import com.viglet.turing.commons.utils.TurCommonsUtils;
import com.viglet.turing.persistence.dto.sn.synonym.TurSNSynonymDto;
import com.viglet.turing.persistence.model.se.TurSEInstance;
import com.viglet.turing.persistence.model.sn.TurSNSite;
import com.viglet.turing.persistence.model.sn.locale.TurSNSiteLocale;
import com.viglet.turing.persistence.model.sn.synonym.TurSNSynonymType;
import com.viglet.turing.persistence.repository.se.TurSEInstanceRepository;
import com.viglet.turing.persistence.repository.se.TurSEVendorRepository;
import com.viglet.turing.persistence.repository.sn.TurSNSiteRepository;
import com.viglet.turing.persistence.repository.sn.locale.TurSNSiteLocaleRepository;
import com.viglet.turing.persistence.repository.sn.synonym.TurSNSynonymRepository;

import lombok.extern.slf4j.Slf4j;

/**
 * Boots the full context against H2 (validating the T662 Liquibase changelog +
 * JPA mapping) and exercises the synonym CRUD/batch API end-to-end over the
 * seeded {@code Sample} site.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Slf4j
@SpringBootTest(properties = "spring.jmx.enabled=true")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class TurSNSiteSynonymAPITests {
    @Autowired
    private WebApplicationContext webApplicationContext;
    @Autowired
    private TurSNSiteRepository turSNSiteRepository;
    @Autowired
    private TurSNSynonymRepository turSNSynonymRepository;
    @Autowired
    private TurSEInstanceRepository turSEInstanceRepository;
    @Autowired
    private TurSEVendorRepository turSEVendorRepository;
    @Autowired
    private TurSNSiteLocaleRepository turSNSiteLocaleRepository;

    private MockMvc mockMvc;
    private Principal mockPrincipal;
    private String serviceUrl;

    @BeforeAll
    void setup() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
        mockPrincipal = Mockito.mock(Principal.class);
        Mockito.when(mockPrincipal.getName()).thenReturn("admin");
        serviceUrl = String.format("/api/sn/%s/synonym", ensureSite().getId());
    }

    /**
     * No SN site is seeded on startup, so create a minimal one over a Lucene SE
     * instance. Idempotent — the H2 test DB is file-based and reused across runs,
     * so both the site and its locale are ensured independently.
     */
    private TurSNSite ensureSite() {
        TurSNSite site = turSNSiteRepository.findByName("SynonymTest").orElseGet(() -> {
            TurSEInstance instance = new TurSEInstance();
            instance.setTitle("Synonym Test Lucene");
            instance.setDescription("Synonym Test Lucene");
            instance.setEnabled(1);
            instance.setEndpointUrl("http://localhost:8983");
            instance.setTurSEVendor(turSEVendorRepository.findById("LUCENE").orElseThrow());
            TurSEInstance savedInstance = turSEInstanceRepository.save(instance);

            TurSNSite newSite = new TurSNSite();
            newSite.setName("SynonymTest");
            newSite.setDescription("Synonym Test Site");
            newSite.setTurSEInstance(savedInstance);
            return turSNSiteRepository.save(newSite);
        });
        if (turSNSiteLocaleRepository.findByTurSNSite(site).isEmpty()) {
            TurSNSiteLocale locale = new TurSNSiteLocale();
            locale.setLanguage(Locale.ENGLISH);
            locale.setCore("synonymtest_en");
            locale.setPosition(0);
            locale.setTurSNSite(site);
            turSNSiteLocaleRepository.save(locale);
        }
        return site;
    }

    @Test
    @Order(1)
    void listIsOk() throws Exception {
        mockMvc.perform(get(serviceUrl).principal(mockPrincipal)).andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON));
    }

    @Test
    @Order(2)
    void createRegularSynonym() throws Exception {
        TurSNSynonymDto dto = new TurSNSynonymDto(null, "tv set", TurSNSynonymType.REGULAR,
                Locale.ENGLISH, null, List.of("tv", "television", "TV"), true);
        RequestBuilder rb = MockMvcRequestBuilders.post(serviceUrl).principal(mockPrincipal)
                .accept(MediaType.APPLICATION_JSON).content(TurCommonsUtils.asJsonString(dto))
                .contentType(MediaType.APPLICATION_JSON);
        // "TV" is a case-insensitive dup of "tv" -> normalized to two terms.
        mockMvc.perform(rb).andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("REGULAR"))
                .andExpect(jsonPath("$.terms.length()").value(2));
    }

    @Test
    @Order(3)
    void createOneWaySynonym() throws Exception {
        TurSNSynonymDto dto = new TurSNSynonymDto(null, null, TurSNSynonymType.ONE_WAY,
                Locale.ENGLISH, "tablet", List.of("ipad", "galaxy tab"), true);
        RequestBuilder rb = MockMvcRequestBuilders.post(serviceUrl).principal(mockPrincipal)
                .accept(MediaType.APPLICATION_JSON).content(TurCommonsUtils.asJsonString(dto))
                .contentType(MediaType.APPLICATION_JSON);
        mockMvc.perform(rb).andExpect(status().isOk())
                .andExpect(jsonPath("$.input").value("tablet"));
    }

    @Test
    @Order(4)
    void regularWithOneTermIsRejected() throws Exception {
        TurSNSynonymDto dto = new TurSNSynonymDto(null, null, TurSNSynonymType.REGULAR,
                Locale.ENGLISH, null, List.of("only"), true);
        RequestBuilder rb = MockMvcRequestBuilders.post(serviceUrl).principal(mockPrincipal)
                .accept(MediaType.APPLICATION_JSON).content(TurCommonsUtils.asJsonString(dto))
                .contentType(MediaType.APPLICATION_JSON);
        mockMvc.perform(rb).andExpect(status().isBadRequest());
    }

    @Test
    @Order(5)
    void getAndUpdate() throws Exception {
        var synonym = turSNSynonymRepository.findAll().stream()
                .filter(s -> s.getType() == TurSNSynonymType.REGULAR).findFirst().orElseThrow();
        String id = synonym.getId();
        mockMvc.perform(get(serviceUrl + "/" + id).principal(mockPrincipal))
                .andExpect(status().isOk());

        TurSNSynonymDto update = new TurSNSynonymDto(id, "renamed", TurSNSynonymType.REGULAR,
                Locale.ENGLISH, null, List.of("tv", "television", "telly"), false);
        RequestBuilder rb = MockMvcRequestBuilders.put(serviceUrl + "/" + id).principal(mockPrincipal)
                .accept(MediaType.APPLICATION_JSON).content(TurCommonsUtils.asJsonString(update))
                .contentType(MediaType.APPLICATION_JSON);
        mockMvc.perform(rb).andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(false))
                .andExpect(jsonPath("$.terms.length()").value(3));
    }

    @Test
    @Order(6)
    void batchUpsert() throws Exception {
        List<TurSNSynonymDto> batch = List.of(
                new TurSNSynonymDto(null, null, TurSNSynonymType.ALTERNATIVE_CORRECTION_1,
                        Locale.ENGLISH, "tshirt", List.of("t-shirt"), true),
                new TurSNSynonymDto(null, null, TurSNSynonymType.ONE_WAY,
                        Locale.forLanguageTag("pt-BR"), "notebook", List.of("laptop"), true));
        RequestBuilder rb = MockMvcRequestBuilders.post(serviceUrl + "/batch").principal(mockPrincipal)
                .accept(MediaType.APPLICATION_JSON).content(TurCommonsUtils.asJsonString(batch))
                .contentType(MediaType.APPLICATION_JSON);
        mockMvc.perform(rb).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    @Order(7)
    void deleteRemoves() throws Exception {
        String id = turSNSynonymRepository.findAll().stream().findFirst().orElseThrow().getId();
        RequestBuilder rb = MockMvcRequestBuilders.delete(serviceUrl + "/" + id)
                .principal(mockPrincipal).accept(MediaType.APPLICATION_JSON)
                .contentType(MediaType.APPLICATION_JSON);
        mockMvc.perform(rb).andExpect(status().isOk());
    }

    @Test
    @Order(8)
    void supportReportsEngineCapability() throws Exception {
        // The test site runs on the Lucene plugin, which supports query-time
        // synonyms via an in-JVM SynonymMap (T665).
        mockMvc.perform(get(serviceUrl + "/support").principal(mockPrincipal))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.engine").value("lucene"))
                .andExpect(jsonPath("$.supported").value(true));
    }

    @Test
    @Order(9)
    void applyReturnsPerLocaleResult() throws Exception {
        RequestBuilder rb = MockMvcRequestBuilders.post(serviceUrl + "/apply").principal(mockPrincipal)
                .accept(MediaType.APPLICATION_JSON).contentType(MediaType.APPLICATION_JSON);
        // One locale (en) on the site; Lucene registers the query-time map and
        // reports it applied.
        mockMvc.perform(rb).andExpect(status().isOk())
                .andExpect(jsonPath("$.en.supported").value(true));
    }
}
