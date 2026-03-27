package com.viglet.turing.api.sn.console;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.security.Principal;
import java.time.LocalDateTime;
import java.util.Collections;

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
import com.viglet.turing.persistence.model.sn.spotlight.TurSNSiteSpotlight;
import com.viglet.turing.persistence.model.sn.spotlight.TurSNSiteSpotlightDocument;
import com.viglet.turing.persistence.model.sn.spotlight.TurSNSiteSpotlightTerm;
import com.viglet.turing.persistence.repository.sn.TurSNSiteRepository;
import com.viglet.turing.persistence.repository.sn.spotlight.TurSNSiteSpotlightRepository;
import com.viglet.turing.utils.TurUtils;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@SpringBootTest(properties = "spring.jmx.enabled=true")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class TurSNSiteSpotlightAPITests {
    @Autowired
    private WebApplicationContext webApplicationContext;
    @Autowired
    private TurSNSiteRepository turSNSiteRepository;
    @Autowired
    private TurSNSiteSpotlightRepository turSNSiteSpotlightRepository;
    private static final String SN_SITE_NAME = "Sample";
    private MockMvc mockMvc;
    private Principal mockPrincipal;
    private static final String SERVICE_URL = String.format("/api/sn/%s/spotlight", SN_SITE_NAME);

    @BeforeAll
    void setup() {
        log.debug("TurSNSiteSpotlightAPITests Setup");
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
        mockPrincipal = Mockito.mock(Principal.class);
        Mockito.when(mockPrincipal.getName()).thenReturn("admin");
    }

    @Test
    @Order(1)
    void turSpotlightList() throws Exception {
        mockMvc.perform(get(SERVICE_URL)).andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON));

    }

    @Test
    @Order(2)
    void turSpotlightModel() throws Exception {
        mockMvc.perform(get(SERVICE_URL.concat("/structure"))).andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON));

    }

    @Test
    @Order(3)
    void stage01SpotlightAdd() {
        TurSNSiteSpotlightTerm turSNSiteSpotlightTerm = new TurSNSiteSpotlightTerm();
        turSNSiteSpotlightTerm.setName("foobar");
        TurSNSiteSpotlightDocument turSNSiteSpotlightDocument = new TurSNSiteSpotlightDocument();
        turSNSiteSpotlightDocument.setContent("Ad");
        turSNSiteSpotlightDocument.setLink("https://viglet.org");
        turSNSiteSpotlightDocument.setPosition(1);
        turSNSiteSpotlightDocument.setTitle("Ad");
        turSNSiteSpotlightDocument.setReferenceId("CMS");
        turSNSiteSpotlightDocument.setType("Page");

        turSNSiteRepository.findByName(SN_SITE_NAME).ifPresent(turSNSite -> {
            TurSNSiteSpotlight turSNSiteSpotlight = new TurSNSiteSpotlight();
            turSNSiteSpotlight.setDescription("Spotlight Sample Test");
            turSNSiteSpotlight.setName("Spotlight Sample Test");
            turSNSiteSpotlight.setModificationDate(LocalDateTime.now());
            turSNSiteSpotlight.setManaged(1);
            turSNSiteSpotlight.setProvider("TURING");
            turSNSiteSpotlight.setTurSNSite(turSNSite);
            turSNSite.getTurSNSiteLocales().stream().findFirst()
                    .ifPresent(locale -> turSNSiteSpotlight.setLanguage(locale.getLanguage()));
            turSNSiteSpotlight.setTurSNSiteSpotlightDocuments(
                    Collections.singleton(turSNSiteSpotlightDocument));
            turSNSiteSpotlight
                    .setTurSNSiteSpotlightTerms(Collections.singleton(turSNSiteSpotlightTerm));
            try {
                String spotlightRequestBody = TurCommonsUtils.asJsonString(turSNSiteSpotlight);
                RequestBuilder requestBuilder = MockMvcRequestBuilders.post(SERVICE_URL)
                        .principal(mockPrincipal).accept(MediaType.APPLICATION_JSON)
                        .content(spotlightRequestBody).contentType(MediaType.APPLICATION_JSON);

                mockMvc.perform(requestBuilder).andExpect(status().isOk());
            } catch (Exception e) {
                log.error(e.getMessage(), e);
            }
        });

    }

    @Test
    @Order(4)
    void stage02SpotlightGet() {
        turSNSiteSpotlightRepository.findAll().stream().findFirst().ifPresent(spotlight -> {
            try {
                mockMvc.perform(get(TurUtils.getUrlTemplate(SERVICE_URL, spotlight.getId())))
                        .andExpect(status().isOk())
                        .andExpect(content().contentType(MediaType.APPLICATION_JSON));
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

    @Test
    @Order(5)
    void stage03SpotlightUpdate() {
        turSNSiteSpotlightRepository.findAll().stream().findFirst().ifPresent(spotlight -> {
            try {
                spotlight.setDescription("Description Changed");
                String spotlightRequestBody = TurCommonsUtils.asJsonString(spotlight);

                RequestBuilder requestBuilder = MockMvcRequestBuilders
                        .put(TurUtils.getUrlTemplate(SERVICE_URL, spotlight.getId()))
                        .principal(mockPrincipal).accept(MediaType.APPLICATION_JSON)
                        .content(spotlightRequestBody).contentType(MediaType.APPLICATION_JSON);

                mockMvc.perform(requestBuilder).andExpect(status().isOk());
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

    @Test
    @Order(6)
    void stage04SpotlightDelete() {
        turSNSiteSpotlightRepository.findAll().stream().findFirst().ifPresent(spotlight -> {
            try {
                RequestBuilder requestBuilder = MockMvcRequestBuilders
                        .delete(TurUtils.getUrlTemplate(SERVICE_URL, spotlight.getId()))
                        .principal(mockPrincipal).accept(MediaType.APPLICATION_JSON)
                        .contentType(MediaType.APPLICATION_JSON);

                mockMvc.perform(requestBuilder).andExpect(status().isOk())
                        .andExpect(content().contentType(MediaType.APPLICATION_JSON));
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

}
