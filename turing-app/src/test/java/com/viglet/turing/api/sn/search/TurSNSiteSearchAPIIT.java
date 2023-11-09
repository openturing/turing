package com.viglet.turing.api.sn.search;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.Locale;

import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(SpringExtension.class)
@SpringBootTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class TurSNSiteSearchAPIIT {
    @Autowired
    private WebApplicationContext webApplicationContext;

    private MockMvc mockMvc;

    @BeforeAll
    public void setup() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
    }

    @Test
    void showSpotLightInSearch() throws Exception {
        String spotlightTerm = "search";

        mockMvc.perform(get(String.format("/api/sn/Sample/search?q=%s&_setlocale=en", spotlightTerm)))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.widget.spotlights.length()", is(1)));
    }

    @Test
    void didYouMeanShowsCorrectTerm() throws Exception {
        String wrongTerm = "siarch";
        String expectedTerm = "search";
        mockMvc.perform(get(String.format("/api/sn/Sample/search?q=%s&_setlocale=en", wrongTerm)))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.widget.spellCheck.corrected.text").value(expectedTerm));
    }

    @Test
    void openMultiLanguagePortuguese() throws Exception {
        multiLanguageNotExistsTests(Locale.ITALY);
    }

    @Test
    void openMultiLanguageChinese() throws Exception {
        multiLanguageNotExistsTests(Locale.CHINESE);
    }

    @Test
    void openMultiLanguageEnglish() throws Exception {
        multiLanguageExistsTests(Locale.ENGLISH);
    }

    @Test
    @Timeout(100)
    void solrTimeout() throws Exception {
        mockMvc.perform(get("/api/se/select?q=*:*"))
                .andExpect(status().isOk()).andExpect(content().contentType(MediaType.APPLICATION_JSON));
    }

    private void multiLanguageExistsTests(Locale locale) throws Exception {
        mockMvc.perform(get(String.format("/api/sn/Sample/search?q=*&_setlocale=%s"
                , locale.getLanguage())))
                .andExpect(status().isOk()).andExpect(content().contentType(MediaType.APPLICATION_JSON));
    }

    private void multiLanguageNotExistsTests(Locale locale) throws Exception {
        mockMvc.perform(get(String.format("/api/sn/Sample/search?q=*&_setlocale=%s"
                        , locale.getLanguage())))
                .andExpect(status().is4xxClientError());
    }
}
