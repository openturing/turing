package com.viglet.turing.api.sn.search;

import com.viglet.turing.persistence.model.sn.TurSNSite;
import com.viglet.turing.persistence.model.sn.spotlight.TurSNSiteSpotlight;
import com.viglet.turing.persistence.model.sn.spotlight.TurSNSiteSpotlightDocument;
import com.viglet.turing.persistence.model.sn.spotlight.TurSNSiteSpotlightTerm;
import com.viglet.turing.persistence.repository.sn.TurSNSiteRepository;
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
import java.util.Locale;

import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@Slf4j
@ExtendWith(SpringExtension.class)
@SpringBootTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class TurSNSiteSearchAPIIT {
    @Autowired
    private WebApplicationContext webApplicationContext;
    @Autowired
    private TurSNSiteRepository turSNSiteRepository;
    private MockMvc mockMvc;
    private final static String SN_SITE_NAME = "Sample";
    private Principal mockPrincipal;
    private final static String SPOTLIGHT_SERVICE_URL = String.format("/api/sn/%s/spotlight", SN_SITE_NAME);
    private final static String SEARCH_SERVICE_URL = "/api/sn/%s/search?q=%s&_setlocale=%s";
    private final static String SEARCH_INSTANCE_SERVICE_URL = "/api/se/select?q=*:*";
    private final static String WRONG_SEARCH_TERM = "siarch";
    private final static String SEARCH_TERM = "search";
    private final static String SEARCH_ALL_TERM = "*";
    @BeforeAll
    public void setup() {
        log.debug("TurSNSiteSearchAPIIT Setup");
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
        mockPrincipal = Mockito.mock(Principal.class);
        Mockito.when(mockPrincipal.getName()).thenReturn("admin");
    }

    @Test
    @Order(1)
    void spotlightAdd() throws Exception {
        TurSNSiteSpotlightTerm turSNSiteSpotlightTerm = new TurSNSiteSpotlightTerm();
        turSNSiteSpotlightTerm.setName(SEARCH_TERM);
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
        RequestBuilder requestBuilder = MockMvcRequestBuilders.post(SPOTLIGHT_SERVICE_URL).principal(mockPrincipal)
                .accept(MediaType.APPLICATION_JSON)
                .content(spotlightRequestBody).contentType(MediaType.APPLICATION_JSON);
        mockMvc.perform(requestBuilder).andExpect(status().isOk());
    }
    @Test
    @Order(2)
    void showSpotLightInSearch() throws Exception {
        mockMvc.perform(get(String.format(SEARCH_SERVICE_URL, SN_SITE_NAME, SEARCH_TERM,
                        Locale.ENGLISH.getLanguage())))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.widget.spotlights.length()", is(1)));
    }

    @Test
    @Order(3)
    void didYouMeanShowsCorrectTerm() throws Exception {
        mockMvc.perform(get(String.format(SEARCH_SERVICE_URL, SN_SITE_NAME, WRONG_SEARCH_TERM,
                        Locale.ENGLISH.getLanguage())))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.widget.spellCheck.corrected.text").value(SEARCH_TERM));
    }

    @Test
    @Order(4)
    void openMultiLanguagePortuguese() throws Exception {
        multiLanguageNotExistsTests(Locale.ITALY);
    }

    @Test
    @Order(5)
    void openMultiLanguageChinese() throws Exception {
        multiLanguageNotExistsTests(Locale.CHINESE);
    }

    @Test
    @Order(6)
    void openMultiLanguageEnglish() throws Exception {
        multiLanguageExistsTests(Locale.ENGLISH);
    }

    @Test
    @Order(7)
    @Timeout(100)
    void solrTimeout() throws Exception {
        mockMvc.perform(get(SEARCH_INSTANCE_SERVICE_URL))
                .andExpect(status().isOk()).andExpect(content().contentType(MediaType.APPLICATION_JSON));
    }

    private void multiLanguageExistsTests(Locale locale) throws Exception {
        mockMvc.perform(get(String.format(SEARCH_SERVICE_URL, SN_SITE_NAME, SEARCH_ALL_TERM
                , locale.getLanguage())))
                .andExpect(status().isOk()).andExpect(content().contentType(MediaType.APPLICATION_JSON));
    }

    private void multiLanguageNotExistsTests(Locale locale) throws Exception {
        mockMvc.perform(get(String.format(SEARCH_SERVICE_URL, SN_SITE_NAME, SEARCH_ALL_TERM
                        , locale.getLanguage())))
                .andExpect(status().is4xxClientError());
    }
}
