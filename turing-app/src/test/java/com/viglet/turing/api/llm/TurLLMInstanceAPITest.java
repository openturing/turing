package com.viglet.turing.api.llm;

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

import java.util.Arrays;
import java.util.List;
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
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Sort;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.viglet.turing.persistence.mapper.llm.TurLLMInstanceMapper;
import com.viglet.turing.persistence.model.llm.TurLLMInstance;
import com.viglet.turing.persistence.repository.llm.TurLLMInstanceRepository;
import com.viglet.turing.system.security.TurSecretCryptoService;
import com.viglet.turing.tenant.TurInfraTenantScope;

@ExtendWith(MockitoExtension.class)
class TurLLMInstanceAPITest {

        private MockMvc mockMvc;

        @Mock
        private TurLLMInstanceRepository turLLMInstanceRepository;

        @Mock
        private TurSecretCryptoService turSecretCryptoService;

        @Mock
        private ApplicationEventPublisher eventPublisher;

        @Spy
        private TurLLMInstanceMapper turLLMInstanceMapper = Mappers.getMapper(TurLLMInstanceMapper.class);

        @Mock
        private TurInfraTenantScope tenantScope;

        @InjectMocks
        private TurLLMInstanceAPI turLLMInstanceAPI;

        private final ObjectMapper objectMapper = new ObjectMapper();

        @BeforeEach
        void setUp() {
                // Tenancy-off passthrough: list calls the unscoped supplier, create is a no-op.
                lenient().when(tenantScope.visibleList(any(), any()))
                                .thenAnswer(inv -> ((java.util.function.Supplier<?>) inv.getArgument(0)).get());
                lenient().when(tenantScope.stampOnCreate(any())).thenAnswer(inv -> inv.getArgument(0));
                lenient().when(tenantScope.isVisibleToTenant(any())).thenReturn(true);
                mockMvc = MockMvcBuilders.standaloneSetup(turLLMInstanceAPI).build();
        }

        @Test
        void testTurLLMInstanceList() throws Exception {
                TurLLMInstance instance1 = new TurLLMInstance();
                instance1.setId("1");
                instance1.setTitle("Instance 1");

                TurLLMInstance instance2 = new TurLLMInstance();
                instance2.setId("2");
                instance2.setTitle("Instance 2");

                List<TurLLMInstance> instances = Arrays.asList(instance1, instance2);
                when(turLLMInstanceRepository.findAll(any(Sort.class))).thenReturn(instances);

                mockMvc.perform(get("/api/llm"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$[0].id").value("1"))
                                .andExpect(jsonPath("$[0].title").value("Instance 1"))
                                .andExpect(jsonPath("$[1].id").value("2"))
                                .andExpect(jsonPath("$[1].title").value("Instance 2"));

                verify(turLLMInstanceRepository, times(1)).findAll(any(Sort.class));
        }

        @Test
        void testTurLLMInstanceStructure() throws Exception {
                mockMvc.perform(get("/api/llm/structure"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.id").doesNotExist())
                                .andExpect(jsonPath("$.turLLMVendor").exists());
        }

        @Test
        void testTurLLMInstanceGet_Found() throws Exception {
                TurLLMInstance instance = new TurLLMInstance();
                instance.setId("1");
                instance.setTitle("Instance 1");
                instance.setApiKeyEncrypted("encrypted-value");
                instance.setApiKey("plain-value");

                when(turLLMInstanceRepository.findById("1")).thenReturn(Optional.of(instance));

                mockMvc.perform(get("/api/llm/1"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.id").value("1"))
                                .andExpect(jsonPath("$.title").value("Instance 1"))
                                .andExpect(jsonPath("$.apiKey").doesNotExist())
                                .andExpect(jsonPath("$.apiKeyEncrypted").doesNotExist());

                verify(turLLMInstanceRepository, times(1)).findById("1");
        }

        @Test
        void testTurLLMInstanceGet_NotFound() throws Exception {
                when(turLLMInstanceRepository.findById("1")).thenReturn(Optional.empty());

                mockMvc.perform(get("/api/llm/1"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.id").doesNotExist());

                verify(turLLMInstanceRepository, times(1)).findById("1");
        }

        @Test
        void testTurLLMInstanceUpdate_Found() throws Exception {
                TurLLMInstance existingInstance = new TurLLMInstance();
                existingInstance.setId("1");
                existingInstance.setTitle("Old Title");
                existingInstance.setApiKeyEncrypted("old-encrypted-key");

                String updatePayload = """
                                {
                                  "title": "New Title",
                                  "description": "New Desc",
                                  "url": "http://newurl",
                                  "enabled": 1,
                                  "modelName": "llama2",
                                  "apiKey": "new-plain-key",
                                  "turLLMVendor": { "id": "OLLAMA" }
                                }
                                """;

                when(turLLMInstanceRepository.findById("1")).thenReturn(Optional.of(existingInstance));
                when(turSecretCryptoService.encrypt("new-plain-key")).thenReturn("new-encrypted-key");

                mockMvc.perform(put("/api/llm/1")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(updatePayload))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.title").value("New Title"))
                                .andExpect(jsonPath("$.description").value("New Desc"))
                                .andExpect(jsonPath("$.url").value("http://newurl"))
                                .andExpect(jsonPath("$.enabled").value(1))
                                .andExpect(jsonPath("$.modelName").value("llama2"))
                                .andExpect(jsonPath("$.apiKey").doesNotExist())
                                .andExpect(jsonPath("$.apiKeyEncrypted").doesNotExist());

                verify(turLLMInstanceRepository, times(1)).findById("1");
                verify(turLLMInstanceRepository, times(1)).save(any(TurLLMInstance.class));
                verify(turSecretCryptoService, times(1)).encrypt("new-plain-key");

                ArgumentCaptor<TurLLMInstance> captor = ArgumentCaptor.forClass(TurLLMInstance.class);
                verify(turLLMInstanceRepository).save(captor.capture());
                TurLLMInstance saved = captor.getValue();
                org.assertj.core.api.Assertions.assertThat(saved.getApiKeyEncrypted()).isEqualTo("new-encrypted-key");
        }

        @Test
        void testTurLLMInstanceUpdate_persistsContextWindowAndEmbeddingDimensions() throws Exception {
                // T780 — catalog-auto-filled limits must survive the update path.
                TurLLMInstance existingInstance = new TurLLMInstance();
                existingInstance.setId("1");
                when(turLLMInstanceRepository.findById("1")).thenReturn(Optional.of(existingInstance));

                String payload = """
                                {
                                  "title": "T",
                                  "modelName": "gpt-4o",
                                  "contextWindow": 128000,
                                  "embeddingDimensions": 1536,
                                  "turLLMVendor": { "id": "OPENAI" }
                                }
                                """;
                mockMvc.perform(put("/api/llm/1")
                                .contentType(MediaType.APPLICATION_JSON).content(payload))
                                .andExpect(status().isOk());

                ArgumentCaptor<TurLLMInstance> captor = ArgumentCaptor.forClass(TurLLMInstance.class);
                verify(turLLMInstanceRepository).save(captor.capture());
                org.assertj.core.api.Assertions.assertThat(captor.getValue().getContextWindow()).isEqualTo(128000);
                org.assertj.core.api.Assertions.assertThat(captor.getValue().getEmbeddingDimensions()).isEqualTo(1536);
        }

        @Test
        void testTurLLMInstanceUpdate_preservesDetectedEmbeddingDimensionsWhenOmitted() throws Exception {
                // T780/T627 — a payload that omits embeddingDimensions must NOT wipe the
                // runtime-detected value on the existing row.
                TurLLMInstance existingInstance = new TurLLMInstance();
                existingInstance.setId("1");
                existingInstance.setEmbeddingDimensions(3072);
                when(turLLMInstanceRepository.findById("1")).thenReturn(Optional.of(existingInstance));

                String payload = """
                                { "title": "T", "modelName": "gpt-4o", "turLLMVendor": { "id": "OPENAI" } }
                                """;
                mockMvc.perform(put("/api/llm/1")
                                .contentType(MediaType.APPLICATION_JSON).content(payload))
                                .andExpect(status().isOk());

                ArgumentCaptor<TurLLMInstance> captor = ArgumentCaptor.forClass(TurLLMInstance.class);
                verify(turLLMInstanceRepository).save(captor.capture());
                org.assertj.core.api.Assertions.assertThat(captor.getValue().getEmbeddingDimensions()).isEqualTo(3072);
        }

        @Test
        void testTurLLMInstanceUpdate_NotFound() throws Exception {
                TurLLMInstance updatedInstance = new TurLLMInstance();
                updatedInstance.setTitle("New Title");

                when(turLLMInstanceRepository.findById("1")).thenReturn(Optional.empty());

                mockMvc.perform(put("/api/llm/1")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(updatedInstance)))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.id").doesNotExist());

                verify(turLLMInstanceRepository, times(1)).findById("1");
                verify(turLLMInstanceRepository, never()).save(any(TurLLMInstance.class));
        }

        @Test
        void testTurLLMInstanceDelete() throws Exception {
                // T365 — delete only runs for a visible instance, so the by-id load must resolve.
                TurLLMInstance instance = new TurLLMInstance();
                instance.setId("1");
                when(turLLMInstanceRepository.findById("1")).thenReturn(Optional.of(instance));

                mockMvc.perform(delete("/api/llm/1"))
                                .andExpect(status().isOk())
                                .andExpect(content().string("true"));

                verify(turLLMInstanceRepository, times(1)).delete("1");
        }

        @Test
        void testTurLLMInstanceAdd() throws Exception {
                String createPayload = """
                                {
                                  "title": "New Instance",
                                  "enabled": 1,
                                  "url": "http://localhost:11434",
                                  "apiKey": "plain-api-key",
                                  "turLLMVendor": { "id": "OPENAI" }
                                }
                                """;
                when(turSecretCryptoService.encrypt("plain-api-key")).thenReturn("encrypted-api-key");

                mockMvc.perform(post("/api/llm")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(createPayload))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.title").value("New Instance"))
                                .andExpect(jsonPath("$.apiKey").doesNotExist())
                                .andExpect(jsonPath("$.apiKeyEncrypted").doesNotExist());

                verify(turLLMInstanceRepository, times(1)).save(any(TurLLMInstance.class));
                verify(turSecretCryptoService, times(1)).encrypt("plain-api-key");

                ArgumentCaptor<TurLLMInstance> captor = ArgumentCaptor.forClass(TurLLMInstance.class);
                verify(turLLMInstanceRepository).save(captor.capture());
                org.assertj.core.api.Assertions.assertThat(captor.getValue().getApiKeyEncrypted())
                                .isEqualTo("encrypted-api-key");
        }
}
