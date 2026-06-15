package com.viglet.turing.api.store;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
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
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Sort;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.viglet.turing.persistence.mapper.store.TurStoreInstanceMapper;
import com.viglet.turing.persistence.model.store.TurStoreInstance;
import com.viglet.turing.persistence.repository.store.TurStoreInstanceRepository;
import com.viglet.turing.system.security.TurSecretCryptoService;
import com.viglet.turing.tenant.TurInfraTenantScope;

@ExtendWith(MockitoExtension.class)
class TurStoreInstanceAPITest {

        private MockMvc mockMvc;

        @Mock
        private TurStoreInstanceRepository turStoreInstanceRepository;

        @Mock
        private TurSecretCryptoService turSecretCryptoService;

        @Spy
        private TurStoreInstanceMapper turStoreInstanceMapper = Mappers.getMapper(TurStoreInstanceMapper.class);

        @Mock
        private TurInfraTenantScope tenantScope;

        @InjectMocks
        private TurStoreInstanceAPI api;

        private final ObjectMapper objectMapper = new ObjectMapper();

        @BeforeEach
        void setUp() {
                // Tenancy-off passthrough: list calls the unscoped supplier, create is a no-op.
                lenient().when(tenantScope.visibleList(any(), any()))
                                .thenAnswer(inv -> ((java.util.function.Supplier<?>) inv.getArgument(0)).get());
                lenient().when(tenantScope.stampOnCreate(any())).thenAnswer(inv -> inv.getArgument(0));
                mockMvc = MockMvcBuilders.standaloneSetup(api).build();
        }

        @Test
        void testTurStoreInstanceList() throws Exception {
                TurStoreInstance instance = new TurStoreInstance();
                instance.setId("store1");
                instance.setTitle("Store 1");

                when(turStoreInstanceRepository.findAll(any(Sort.class)))
                                .thenReturn(Collections.singletonList(instance));

                mockMvc.perform(get("/api/store"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$[0].id").value("store1"))
                                .andExpect(jsonPath("$[0].title").value("Store 1"));
        }

        @Test
        void testTurStoreInstanceGet_Found() throws Exception {
                TurStoreInstance instance = new TurStoreInstance();
                instance.setId("store1");
                instance.setCredentialEncrypted("encrypted-credential");
                instance.setCredential("plain-credential");

                when(turStoreInstanceRepository.findById("store1")).thenReturn(Optional.of(instance));

                mockMvc.perform(get("/api/store/store1"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.id").value("store1"))
                                .andExpect(jsonPath("$.credential").doesNotExist())
                                .andExpect(jsonPath("$.credentialEncrypted").doesNotExist());
        }

        @Test
        void testTurStoreInstanceGet_NotFound() throws Exception {
                when(turStoreInstanceRepository.findById("store1")).thenReturn(Optional.empty());

                mockMvc.perform(get("/api/store/store1"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.id").doesNotExist());
        }

        @Test
        void testTurStoreInstanceUpdate_Found() throws Exception {
                TurStoreInstance instance = new TurStoreInstance();
                instance.setId("store1");
                instance.setCredentialEncrypted("old-encrypted-credential");
                String updatePayload = """
                                {
                                    "title": "New Title",
                                    "credential": "plain-credential"
                                }
                                """;

                when(turStoreInstanceRepository.findById("store1")).thenReturn(Optional.of(instance));
                when(turSecretCryptoService.encrypt("plain-credential")).thenReturn("new-encrypted-credential");

                mockMvc.perform(put("/api/store/store1")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(updatePayload))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.title").value("New Title"))
                                .andExpect(jsonPath("$.credential").doesNotExist())
                                .andExpect(jsonPath("$.credentialEncrypted").doesNotExist());

                verify(turStoreInstanceRepository, times(1)).save(instance);
                verify(turSecretCryptoService, times(1)).encrypt("plain-credential");
                org.assertj.core.api.Assertions.assertThat(instance.getCredentialEncrypted())
                                .isEqualTo("new-encrypted-credential");
        }

        @Test
        void testTurStoreInstanceUpdate_NotFound() throws Exception {
                TurStoreInstance updatedInstance = new TurStoreInstance();
                updatedInstance.setTitle("New Title");

                when(turStoreInstanceRepository.findById("store1")).thenReturn(Optional.empty());

                mockMvc.perform(put("/api/store/store1")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(updatedInstance)))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.id").doesNotExist());

                verify(turStoreInstanceRepository, never()).save(any());
        }

        @Test
        void testTurStoreInstanceDelete() throws Exception {
                mockMvc.perform(delete("/api/store/store1"))
                                .andExpect(status().isOk())
                                .andExpect(content().string("true"));

                verify(turStoreInstanceRepository, times(1)).delete("store1"); // Uses old delete(ID) in Spring Data?
                // Note: Check repository interface; if it uses deleteById, this might fail
                // compilation or execution.
        }

        @Test
        void testTurStoreInstanceAdd() throws Exception {
                String createPayload = """
                                {
                                  "title": "New Store",
                                  "credential": "plain-store-credential"
                                }
                                """;
                when(turSecretCryptoService.encrypt("plain-store-credential"))
                                .thenReturn("encrypted-store-credential");

                mockMvc.perform(post("/api/store")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(createPayload))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.title").value("New Store"))
                                .andExpect(jsonPath("$.credential").doesNotExist())
                                .andExpect(jsonPath("$.credentialEncrypted").doesNotExist());

                verify(turStoreInstanceRepository, times(1)).save(any(TurStoreInstance.class));
                verify(turSecretCryptoService, times(1)).encrypt("plain-store-credential");

                ArgumentCaptor<TurStoreInstance> captor = ArgumentCaptor.forClass(TurStoreInstance.class);
                verify(turStoreInstanceRepository).save(captor.capture());
                org.assertj.core.api.Assertions.assertThat(captor.getValue().getCredentialEncrypted())
                                .isEqualTo("encrypted-store-credential");
        }

        @Test
        void testTurEmbeddingStoreInstanceStructure() throws Exception {
                mockMvc.perform(get("/api/store/structure"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.turStoreVendor").exists());
        }
}
