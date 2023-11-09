package com.viglet.turing.api.sn.console;

import com.viglet.turing.persistence.model.sn.TurSNSite;
import com.viglet.turing.persistence.model.sn.spotlight.TurSNSiteSpotlight;
import com.viglet.turing.persistence.model.sn.spotlight.TurSNSiteSpotlightDocument;
import com.viglet.turing.persistence.model.sn.spotlight.TurSNSiteSpotlightTerm;
import com.viglet.turing.persistence.repository.sn.TurSNSiteRepository;
import com.viglet.turing.persistence.repository.sn.spotlight.TurSNSiteSpotlightRepository;
import com.viglet.turing.utils.TurUtilTests;
import com.viglet.turing.utils.TurUtils;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.RequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.security.Principal;
import java.util.Collections;
import java.util.Date;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Slf4j
@ExtendWith(SpringExtension.class)
@SpringBootTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class TurSNSiteSpotlightAPITests {
    @Autowired
    private WebApplicationContext webApplicationContext;
    @Autowired
    private TurSNSiteRepository turSNSiteRepository;
    @Autowired
    private TurSNSiteSpotlightRepository turSNSiteSpotlightRepository;
    private final static String SN_SITE_NAME = "Sample";
    private MockMvc mockMvc;
    private Principal mockPrincipal;
    private final static String SERVICE_URL = String.format("/api/sn/%s/spotlight", SN_SITE_NAME);

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
    void stage01SpotlightAdd() throws Exception {
        TurSNSiteSpotlightTerm turSNSiteSpotlightTerm = new TurSNSiteSpotlightTerm();
        turSNSiteSpotlightTerm.setName("search");
        TurSNSiteSpotlightDocument turSNSiteSpotlightDocument = new TurSNSiteSpotlightDocument();
        turSNSiteSpotlightDocument.setContent("Ad");
        turSNSiteSpotlightDocument.setLink("https://viglet.com");
        turSNSiteSpotlightDocument.setPosition(1);
        turSNSiteSpotlightDocument.setTitle("Ad");
        turSNSiteSpotlightDocument.setReferenceId("CMS");
        turSNSiteSpotlightDocument.setType("Page");

        TurSNSite turSNSite = turSNSiteRepository.findByName(SN_SITE_NAME);
        TurSNSiteSpotlight turSNSiteSpotlight = new TurSNSiteSpotlight();
        turSNSiteSpotlight.setDescription("Spotlight Sample Test");
        turSNSiteSpotlight.setName("Spotlight Sample Test");
        turSNSiteSpotlight.setModificationDate(new Date());
        turSNSiteSpotlight.setManaged(1);
        turSNSiteSpotlight.setProvider("TURING");
        turSNSiteSpotlight.setTurSNSite(turSNSite);
        turSNSite.getTurSNSiteLocales().stream().findFirst().ifPresent(locale ->
                turSNSiteSpotlight.setLanguage(locale.getLanguage())
        );
        turSNSiteSpotlight.setTurSNSiteSpotlightDocuments(Collections.singleton(turSNSiteSpotlightDocument));
        turSNSiteSpotlight.setTurSNSiteSpotlightTerms(Collections.singleton(turSNSiteSpotlightTerm));
        String spotlightRequestBody = TurUtils.asJsonString(turSNSiteSpotlight);
        RequestBuilder requestBuilder = MockMvcRequestBuilders.post(SERVICE_URL).principal(mockPrincipal)
                .accept(MediaType.APPLICATION_JSON)
                .content(spotlightRequestBody).contentType(MediaType.APPLICATION_JSON);
        mockMvc.perform(requestBuilder).andExpect(status().isOk());
    }
    @Test
    @Order(4)
    void stage02SpotlightGet() {
        turSNSiteSpotlightRepository.findAll().stream().findFirst().ifPresent(spotlight -> {
            try {
                mockMvc.perform(get(TurUtilTests
                                .getUrlTemplate(SERVICE_URL, spotlight.getId()))).andExpect(status().isOk())
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
                String spotlightRequestBody = TurUtils.asJsonString(spotlight);

                RequestBuilder requestBuilder = MockMvcRequestBuilders.put(TurUtilTests
                                .getUrlTemplate(SERVICE_URL, spotlight.getId()))
                        .principal(mockPrincipal).accept(MediaType.APPLICATION_JSON).content(spotlightRequestBody)
                        .contentType(MediaType.APPLICATION_JSON);

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
                RequestBuilder requestBuilder = MockMvcRequestBuilders.delete(TurUtilTests
                                .getUrlTemplate(SERVICE_URL, spotlight.getId()))
                        .principal(mockPrincipal).accept(MediaType.APPLICATION_JSON).contentType(MediaType.APPLICATION_JSON);

                mockMvc.perform(requestBuilder).andExpect(status().isOk())
                        .andExpect(content().contentType(MediaType.APPLICATION_JSON));
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

}
