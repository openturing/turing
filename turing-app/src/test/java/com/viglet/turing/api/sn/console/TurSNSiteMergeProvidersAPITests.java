package com.viglet.turing.api.sn.console;

import com.viglet.turing.persistence.model.sn.TurSNSite;
import com.viglet.turing.persistence.model.sn.merge.TurSNSiteMergeProviders;
import com.viglet.turing.persistence.model.sn.merge.TurSNSiteMergeProvidersField;
import com.viglet.turing.persistence.repository.sn.TurSNSiteRepository;
import com.viglet.turing.persistence.repository.sn.merge.TurSNSiteMergeProvidersRepository;
import com.viglet.turing.utils.TurUtilTests;
import com.viglet.turing.utils.TurUtils;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
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
import java.util.Locale;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Slf4j
@ExtendWith(SpringExtension.class)
@SpringBootTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class TurSNSiteMergeProvidersAPITests {
    @Autowired
    private WebApplicationContext webApplicationContext;
    @Autowired
    private TurSNSiteRepository turSNSiteRepository;
    @Autowired
    private TurSNSiteMergeProvidersRepository turSNSiteMergeProvidersRepository;

    private static final String SN_SITE_NAME = "Sample";
    private MockMvc mockMvc;
    private Principal mockPrincipal;
    private static final String SERVICE_URL = String.format("/api/sn/%s/merge", SN_SITE_NAME);

    @BeforeAll
    void setup() {
        log.debug("TurSNSiteMergeProvidersAPITests Setup");
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
        mockPrincipal = Mockito.mock(Principal.class);
        Mockito.when(mockPrincipal.getName()).thenReturn("admin");
    }

    @Test
    @Order(1)
    void turMergeProvidersList() throws Exception {
        mockMvc.perform(get(SERVICE_URL)).andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON));

    }

    @Test
    @Order(2)
    void turMergeProvidersModel() throws Exception {
        mockMvc.perform(get(SERVICE_URL.concat("/structure"))).andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON));

    }

    @Test
    @Order(3)
    void stage01MergeProvidersAdd() {
        turSNSiteRepository.findByName(SN_SITE_NAME).ifPresent(turSNSite -> {
            try {
                String mergeRequestBody = TurUtils.asJsonString(getTurSNSiteMergeProviders(turSNSite));
                RequestBuilder requestBuilder = MockMvcRequestBuilders.post(SERVICE_URL).principal(mockPrincipal)
                        .accept(MediaType.APPLICATION_JSON).content(mergeRequestBody).contentType(MediaType.APPLICATION_JSON);

                mockMvc.perform(requestBuilder).andExpect(status().isOk());
            } catch (Exception e) {
                log.error(e.getMessage(), e);
            }
        });

    }

    @NotNull
    private static TurSNSiteMergeProviders getTurSNSiteMergeProviders(TurSNSite turSNSite) {
        TurSNSiteMergeProviders turSNSiteMerge = new TurSNSiteMergeProviders();
        turSNSiteMerge.setTurSNSite(turSNSite);
        turSNSiteMerge.setLocale(Locale.US);
        turSNSiteMerge.setProviderFrom("Nutch");
        turSNSiteMerge.setProviderTo("WEM");
        turSNSiteMerge.setRelationFrom("id");
        turSNSiteMerge.setRelationTo("url");
        turSNSiteMerge.setDescription("Merge content from Nutch into existing WEM content.");
        TurSNSiteMergeProvidersField turSNSiteMergeField = new TurSNSiteMergeProvidersField();
        turSNSiteMergeField.setName("text");
        turSNSiteMerge.setOverwrittenFields(Collections.singleton(turSNSiteMergeField));
        return turSNSiteMerge;
    }

    @Test
    @Order(4)
    void stage02MergeProvidersGet() {
        turSNSiteMergeProvidersRepository.findAll().stream().findFirst().ifPresent(mergeProviders -> {
            try {
                mockMvc.perform(get(TurUtilTests
                                .getUrlTemplate(SERVICE_URL, mergeProviders.getId())))
                        .andExpect(content().contentType(MediaType.APPLICATION_JSON));
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

    @Test
    @Order(5)
    void stage03MergeProvidersUpdate() {
        turSNSiteMergeProvidersRepository.findAll().stream().findFirst().ifPresent(mergeProviders -> {
            try {
                mergeProviders.setDescription("Description Changed");
                String spotlightRequestBody = TurUtils.asJsonString(mergeProviders);

                RequestBuilder requestBuilder = MockMvcRequestBuilders.put(TurUtilTests
                                .getUrlTemplate(SERVICE_URL, mergeProviders.getId()))
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
    void stage04MergeProvidersDelete() {
        turSNSiteMergeProvidersRepository.findAll().stream().findFirst().ifPresent(mergeProviders -> {
            try {
                RequestBuilder requestBuilder = MockMvcRequestBuilders.delete(TurUtilTests
                                .getUrlTemplate(SERVICE_URL, mergeProviders.getId()))
                        .principal(mockPrincipal).accept(MediaType.APPLICATION_JSON).contentType(MediaType.APPLICATION_JSON);
                mockMvc.perform(requestBuilder).andExpect(status().isOk())
                        .andExpect(content().contentType(MediaType.APPLICATION_JSON));
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }
}
