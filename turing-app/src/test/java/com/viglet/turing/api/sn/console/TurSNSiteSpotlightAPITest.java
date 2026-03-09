package com.viglet.turing.api.sn.console;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Collections;
import java.util.HashSet;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Sort;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.viglet.turing.persistence.mapper.sn.spotlight.TurSNSiteSpotlightMapper;
import com.viglet.turing.persistence.model.sn.TurSNSite;
import com.viglet.turing.persistence.model.sn.spotlight.TurSNSiteSpotlight;
import com.viglet.turing.persistence.model.sn.spotlight.TurSNSiteSpotlightDocument;
import com.viglet.turing.persistence.model.sn.spotlight.TurSNSiteSpotlightTerm;
import com.viglet.turing.persistence.repository.sn.TurSNSiteRepository;
import com.viglet.turing.persistence.repository.sn.spotlight.TurSNSiteSpotlightDocumentRepository;
import com.viglet.turing.persistence.repository.sn.spotlight.TurSNSiteSpotlightRepository;
import com.viglet.turing.persistence.repository.sn.spotlight.TurSNSiteSpotlightTermRepository;

@ExtendWith(MockitoExtension.class)
class TurSNSiteSpotlightAPITest {

        private MockMvc mockMvc;

        @Mock
        private TurSNSiteRepository turSNSiteRepository;

        @Mock
        private TurSNSiteSpotlightRepository turSNSiteSpotlightRepository;

        @Mock
        private TurSNSiteSpotlightDocumentRepository turSNSiteSpotlightDocumentRepository;

        @Mock
        private TurSNSiteSpotlightTermRepository turSNSiteSpotlightTermRepository;

        @Spy
        private TurSNSiteSpotlightMapper turSNSiteSpotlightMapper = Mappers.getMapper(TurSNSiteSpotlightMapper.class);

        @InjectMocks
        private TurSNSiteSpotlightAPI api;

        private final ObjectMapper objectMapper = new ObjectMapper();

        @BeforeEach
        void setUp() {
                mockMvc = MockMvcBuilders.standaloneSetup(api).build();
        }

        @Test
        void testTurSNSiteSpotlightList() throws Exception {
                TurSNSite site = new TurSNSite();
                site.setId("site1");

                TurSNSiteSpotlight spotlight = new TurSNSiteSpotlight();
                spotlight.setId("spot1");
                spotlight.setName("Spotlight 1");

                when(turSNSiteRepository.findById("site1")).thenReturn(Optional.of(site));
                when(turSNSiteSpotlightRepository.findByTurSNSite(any(Sort.class), eq(site)))
                                .thenReturn(Collections.singletonList(spotlight));

                mockMvc.perform(get("/api/sn/site1/spotlight"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$[0].id").value("spot1"))
                                .andExpect(jsonPath("$[0].name").value("Spotlight 1"));
        }

        @Test
        void testTurSNSiteSpotlightList_SiteNotFound() throws Exception {
                when(turSNSiteRepository.findById("site1")).thenReturn(Optional.empty());

                mockMvc.perform(get("/api/sn/site1/spotlight"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$").isEmpty());
        }

        @Test
        void testTurSNSiteSpotlightGet() throws Exception {
                TurSNSiteSpotlight spotlight = new TurSNSiteSpotlight();
                spotlight.setId("spot1");

                TurSNSiteSpotlightDocument doc = new TurSNSiteSpotlightDocument();
                doc.setId("doc1");

                TurSNSiteSpotlightTerm term = new TurSNSiteSpotlightTerm();
                term.setId("term1");

                when(turSNSiteSpotlightRepository.findById("spot1")).thenReturn(Optional.of(spotlight));
                when(turSNSiteSpotlightDocumentRepository.findByTurSNSiteSpotlight(spotlight))
                                .thenReturn(Collections.singleton(doc));
                when(turSNSiteSpotlightTermRepository.findByTurSNSiteSpotlight(spotlight))
                                .thenReturn(Collections.singleton(term));

                mockMvc.perform(get("/api/sn/site1/spotlight/spot1"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.id").value("spot1"))
                                .andExpect(jsonPath("$.turSNSiteSpotlightDocuments[0].id").value("doc1"))
                                .andExpect(jsonPath("$.turSNSiteSpotlightTerms[0].id").value("term1"));
        }

        @Test
        void testTurSNSiteSpotlightGet_NotFound() throws Exception {
                when(turSNSiteSpotlightRepository.findById("spot1")).thenReturn(Optional.empty());

                mockMvc.perform(get("/api/sn/site1/spotlight/spot1"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.id").doesNotExist());
        }

        @Test
        void testTurSNSiteSpotlightUpdate_Found() throws Exception {
                TurSNSiteSpotlight existingSpot = new TurSNSiteSpotlight();
                existingSpot.setId("spot1");

                TurSNSiteSpotlight updatedSpot = new TurSNSiteSpotlight();
                updatedSpot.setName("New Name");

                TurSNSiteSpotlightDocument doc = new TurSNSiteSpotlightDocument();
                updatedSpot.setTurSNSiteSpotlightDocuments(new HashSet<>(Collections.singletonList(doc)));

                TurSNSiteSpotlightTerm term = new TurSNSiteSpotlightTerm();
                updatedSpot.setTurSNSiteSpotlightTerms(new HashSet<>(Collections.singletonList(term)));

                when(turSNSiteSpotlightRepository.findById("spot1")).thenReturn(Optional.of(existingSpot));

                mockMvc.perform(put("/api/sn/site1/spotlight/spot1")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(updatedSpot)))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.name").value("New Name"));

                verify(turSNSiteSpotlightRepository, times(1)).save(existingSpot);
        }

        @Test
        void testTurSNSiteSpotlightUpdate_NotFound() throws Exception {
                TurSNSiteSpotlight updatedSpot = new TurSNSiteSpotlight();
                updatedSpot.setName("New Name");

                when(turSNSiteSpotlightRepository.findById("spot1")).thenReturn(Optional.empty());

                mockMvc.perform(put("/api/sn/site1/spotlight/spot1")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(updatedSpot)))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.id").doesNotExist());

                verify(turSNSiteSpotlightRepository, never()).save(any());
        }

        @Test
        void testTurSNSiteSpotlightDelete() throws Exception {
                mockMvc.perform(delete("/api/sn/site1/spotlight/spot1"))
                                .andExpect(status().isOk())
                                .andExpect(content().string("true"));

                verify(turSNSiteSpotlightRepository, times(1)).deleteById("spot1");
        }

        @Test
        void testTurSNSiteSpotlightAdd() throws Exception {
                TurSNSiteSpotlight newSpotlight = new TurSNSiteSpotlight();
                newSpotlight.setName("New Spot");
                newSpotlight.setTurSNSiteSpotlightDocuments(new HashSet<>());
                newSpotlight.setTurSNSiteSpotlightTerms(new HashSet<>());

                mockMvc.perform(post("/api/sn/site1/spotlight")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(newSpotlight)))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.name").value("New Spot"));

                verify(turSNSiteSpotlightRepository, times(1)).save(any(TurSNSiteSpotlight.class));
        }

        @Test
        void testTurSNSiteSpotlightStructure() throws Exception {
                TurSNSite site = new TurSNSite();
                site.setId("site1");

                when(turSNSiteRepository.findById("site1")).thenReturn(Optional.of(site));

                mockMvc.perform(get("/api/sn/site1/spotlight/structure"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.turSNSite.id").value("site1"));
        }

        @Test
        void testTurSNSiteSpotlightStructure_SiteNotFound() throws Exception {
                when(turSNSiteRepository.findById("site1")).thenReturn(Optional.empty());

                mockMvc.perform(get("/api/sn/site1/spotlight/structure"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.id").doesNotExist());
        }
}
