package com.viglet.turing.api.asset;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.viglet.turing.service.asset.TurAssetEvent;
import com.viglet.turing.service.asset.TurAssetTrainingService;
import com.viglet.turing.service.asset.TurAssetTrainingState;
import com.viglet.turing.service.asset.TurAssetTrainingStatus;
import com.viglet.turing.service.storage.TurStorageService;

/**
 * Tests for TurAssetAPI.
 *
 * @author Alexandre Oliveira
 * @since 2026.1.14
 */
@ExtendWith(MockitoExtension.class)
class TurAssetAPITest {

        private MockMvc mockMvc;

        @Mock
        private TurStorageService storageService;

        @Mock
        private TurAssetTrainingService turAssetTrainingService;

        @Mock
        private ApplicationEventPublisher eventPublisher;

        @InjectMocks
        private TurAssetAPI turAssetAPI;

        @BeforeEach
        void setUp() {
                mockMvc = MockMvcBuilders.standaloneSetup(turAssetAPI).build();
        }

        @Test
        void testList() throws Exception {
                List<TurAssetItem> items = List.of(
                                new TurAssetItem("folder/", 0, "", "", true),
                                new TurAssetItem("file.pdf", 1024, "application/pdf", "2026-01-01", false));
                when(storageService.listObjects("")).thenReturn(items);

                mockMvc.perform(get("/api/asset"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$[0].name").value("folder/"))
                                .andExpect(jsonPath("$[0].directory").value(true))
                                .andExpect(jsonPath("$[1].name").value("file.pdf"))
                                .andExpect(jsonPath("$[1].size").value(1024));
        }

        @Test
        void testListWithPrefix() throws Exception {
                List<TurAssetItem> items = List.of(
                                new TurAssetItem("docs/readme.md", 512, "text/markdown", "2026-01-01", false));
                when(storageService.listObjects("docs/")).thenReturn(items);

                mockMvc.perform(get("/api/asset").param("prefix", "docs/"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$[0].name").value("docs/readme.md"));
        }

        @Test
        void testCreateFolder() throws Exception {
                mockMvc.perform(post("/api/asset/folder").param("path", "new-folder/"))
                                .andExpect(status().isOk());

                verify(storageService, times(1)).createFolder("new-folder/");
        }

        @Test
        void testDeleteAsset() throws Exception {
                mockMvc.perform(delete("/api/asset").param("objectName", "test.pdf"))
                                .andExpect(status().isOk());

                verify(storageService, times(1)).deleteObject("test.pdf");
                verify(eventPublisher, times(1)).publishEvent(any(TurAssetEvent.class));
        }

        @Test
        void testStartTrainingRunning() throws Exception {
                TurAssetTrainingStatus runningStatus = new TurAssetTrainingStatus(
                                TurAssetTrainingState.RUNNING, 10, 0, 0, "2026-01-01", "", "");
                when(turAssetTrainingService.startTraining()).thenReturn(runningStatus);

                mockMvc.perform(post("/api/asset/train"))
                                .andExpect(status().isAccepted())
                                .andExpect(jsonPath("$.state").value("RUNNING"));
        }

        @Test
        void testStartTrainingConflict() throws Exception {
                TurAssetTrainingStatus failedStatus = new TurAssetTrainingStatus(
                                TurAssetTrainingState.FAILED, 0, 0, 0, "", "", "RAG not enabled");
                when(turAssetTrainingService.startTraining()).thenReturn(failedStatus);

                mockMvc.perform(post("/api/asset/train"))
                                .andExpect(status().isConflict())
                                .andExpect(jsonPath("$.state").value("FAILED"));
        }

        @Test
        void testGetTrainingStatus() throws Exception {
                TurAssetTrainingStatus idleStatus = TurAssetTrainingStatus.idle();
                when(turAssetTrainingService.getStatus()).thenReturn(idleStatus);

                mockMvc.perform(get("/api/asset/train/status"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.state").value("IDLE"));
        }

        @Test
        void testGetTrainingRecords() throws Exception {
                when(turAssetTrainingService.getTrainedAtMap(List.of("file1.pdf", "file2.pdf")))
                                .thenReturn(java.util.Map.of("file1.pdf", "2026-01-01T00:00:00Z"));

                mockMvc.perform(get("/api/asset/train/records")
                                .param("objectNames", "file1.pdf", "file2.pdf"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.['file1.pdf']").value("2026-01-01T00:00:00Z"));
        }

        @Test
        void testUpload() throws Exception {
                MockMultipartFile file = new MockMultipartFile(
                                "files", "test.txt", "text/plain", "content".getBytes());

                mockMvc.perform(multipart("/api/asset").file(file))
                                .andExpect(status().isOk());

                verify(storageService, times(1)).uploadObject(any(), eq(""));
                verify(eventPublisher, times(1)).publishEvent(any(TurAssetEvent.class));
        }
}
