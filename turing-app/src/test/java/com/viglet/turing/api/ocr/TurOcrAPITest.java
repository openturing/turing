package com.viglet.turing.api.ocr;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.viglet.turing.commons.file.TurFileAttributes;
import com.viglet.turing.utils.TurFileUtils;

class TurOcrAPITest {

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private MockedStatic<TurFileUtils> mockedTurFileUtils;

    @BeforeEach
    void setUp() {
        TurOcrAPI api = new TurOcrAPI();
        mockMvc = MockMvcBuilders.standaloneSetup(api).build();
        mockedTurFileUtils = mockStatic(TurFileUtils.class);
    }

    @AfterEach
    void tearDown() {
        mockedTurFileUtils.close();
    }

    @Test
    void testFileToText() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "test.txt",
                "text/plain",
                "hello".getBytes());

        TurFileAttributes attributes = new TurFileAttributes();
        attributes.setContent("hello content");

        mockedTurFileUtils.when(() -> TurFileUtils.documentToText(any())).thenReturn(attributes);

        mockMvc.perform(multipart("/api/ocr/file").file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").value("hello content"));
    }

    @Test
    void testUrlToText_Allowed() throws Exception {
        TurOcrFromUrl ocrUrl = new TurOcrFromUrl();
        ocrUrl.setUrl("http://example.com/test.txt");

        TurFileAttributes attributes = new TurFileAttributes();
        attributes.setContent("hello url content");

        mockedTurFileUtils.when(() -> TurFileUtils.isAllowedRemoteUrlString(any())).thenReturn(true);
        mockedTurFileUtils.when(() -> TurFileUtils.urlContentToText(any())).thenReturn(attributes);

        mockMvc.perform(post("/api/ocr/url")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(ocrUrl)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").value("hello url content"));
    }

    @Test
    void testUrlToText_Blocked() throws Exception {
        TurOcrFromUrl ocrUrl = new TurOcrFromUrl();
        ocrUrl.setUrl("http://internal.network/test.txt");

        mockedTurFileUtils.when(() -> TurFileUtils.isAllowedRemoteUrlString(any())).thenReturn(false);

        mockMvc.perform(post("/api/ocr/url")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(ocrUrl)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").doesNotExist());
    }
}
