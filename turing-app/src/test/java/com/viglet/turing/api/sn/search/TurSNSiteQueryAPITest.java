package com.viglet.turing.api.sn.search;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.viglet.turing.solr.TurSolr;
import com.viglet.turing.solr.TurSolrInstance;
import com.viglet.turing.solr.TurSolrInstanceProcess;

@ExtendWith(MockitoExtension.class)
class TurSNSiteQueryAPITest {

    private MockMvc mockMvc;

    @Mock
    private TurSolr turSolr;

    @Mock
    private TurSolrInstanceProcess turSolrInstanceProcess;

    @InjectMocks
    private TurSNSiteQueryAPI api;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(api).build();
    }

    @Test
    void testTurSNSiteSearchSelectGet_Found() throws Exception {
        TurSolrInstance solrInstance = mock(TurSolrInstance.class);

        when(turSolrInstanceProcess.initSolrInstance(eq("site1"), any())).thenReturn(Optional.of(solrInstance));
        when(turSolr.dslQuery(solrInstance, "{\"query\":\"test\"}")).thenReturn("{\"result\":\"ok\"}");

        mockMvc.perform(post("/api/sn/site1/query")
                .param("_setlocale", "en_US")
                .content("{\"query\":\"test\"}")
                .contentType("application/json"))
                .andExpect(status().isOk())
                .andExpect(content().json("{\"result\":\"ok\"}"));

        verify(turSolr, times(1)).dslQuery(solrInstance, "{\"query\":\"test\"}");
    }

    @Test
    void testTurSNSiteSearchSelectGet_NotFound() throws Exception {
        when(turSolrInstanceProcess.initSolrInstance(eq("site1"), any())).thenReturn(Optional.empty());

        mockMvc.perform(post("/api/sn/site1/query")
                .param("_setlocale", "en_US")
                .content("{\"query\":\"test\"}")
                .contentType("application/json"))
                .andExpect(status().isOk())
                .andExpect(content().json("{}"));

        verify(turSolr, never()).dslQuery(any(), any());
    }
}
