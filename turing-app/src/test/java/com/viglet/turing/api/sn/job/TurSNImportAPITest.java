package com.viglet.turing.api.sn.job;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.File;
import java.io.FileOutputStream;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.jms.core.JmsMessagingTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.viglet.turing.client.sn.job.TurSNJobAction;
import com.viglet.turing.client.sn.job.TurSNJobItem;
import com.viglet.turing.client.sn.job.TurSNJobItems;
import com.viglet.turing.commons.sn.field.TurSNFieldName;
import com.viglet.turing.genai.TurGenAi;
import com.viglet.turing.persistence.model.sn.TurSNSite;
import com.viglet.turing.persistence.model.sn.genai.TurSNSiteGenAi;
import com.viglet.turing.persistence.repository.sn.TurSNSiteRepository;

@ExtendWith(MockitoExtension.class)
class TurSNImportAPITest {

    private MockMvc mockMvc;

    @Mock
    private JmsMessagingTemplate jmsMessagingTemplate;

    @Mock
    private TurSNSiteRepository turSNSiteRepository;

    @Mock
    private TurGenAi turGenAi;

    @InjectMocks
    private TurSNImportAPI api;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(api).build();
    }

    @Test
    void testTurSNImportBroker_Success() throws Exception {
        TurSNJobItems jobItems = new TurSNJobItems();
        TurSNJobItem item = new TurSNJobItem();
        item.setTurSNJobAction(TurSNJobAction.CREATE);
        item.setSiteNames(Collections.singletonList("site1"));
        item.setLocale(java.util.Locale.US);

        Map<String, Object> attributes = new HashMap<>();
        attributes.put(TurSNFieldName.ID, "1");
        item.setAttributes(attributes);
        jobItems.add(item);

        TurSNSite site = new TurSNSite();
        TurSNSiteGenAi genAi = new TurSNSiteGenAi();
        genAi.setEnabled(true);
        site.setTurSNSiteGenAi(genAi);

        when(turSNSiteRepository.findByName("site1")).thenReturn(Optional.of(site));

        mockMvc.perform(post("/api/sn/import")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(jobItems)))
                .andExpect(status().isOk())
                .andExpect(content().string("true"));

        verify(turGenAi, times(1)).addDocuments(any(TurSNJobItems.class));
        verify(jmsMessagingTemplate, times(1)).convertAndSend(anyString(), any(TurSNJobItems.class));
    }

    @Test
    void testTurSNImportBroker_SiteNotFound() throws Exception {
        TurSNJobItems jobItems = new TurSNJobItems();
        TurSNJobItem item = new TurSNJobItem();
        item.setTurSNJobAction(TurSNJobAction.CREATE);
        item.setSiteNames(Collections.singletonList("site1"));
        item.setLocale(java.util.Locale.US);

        Map<String, Object> attributes = new HashMap<>();
        attributes.put(TurSNFieldName.ID, "1");
        item.setAttributes(attributes);
        jobItems.add(item);

        when(turSNSiteRepository.findByName("site1")).thenReturn(Optional.empty());

        mockMvc.perform(post("/api/sn/import")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(jobItems)))
                .andExpect(status().isOk())
                .andExpect(content().string("true"));

        verify(turGenAi, never()).addDocuments(any(TurSNJobItems.class));
        verify(jmsMessagingTemplate, times(1)).convertAndSend(anyString(), any(TurSNJobItems.class));
    }

    @Test
    void testTurSNImportZipFileBroker() throws Exception {
        // Create a temporary zip file
        File tempZip = File.createTempFile("test", ".zip");
        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(tempZip))) {
            zos.putNextEntry(new ZipEntry("export.json"));

            TurSNJobItems jobItems = new TurSNJobItems();
            TurSNJobItem item = new TurSNJobItem();
            item.setTurSNJobAction(TurSNJobAction.CREATE);
            item.setSiteNames(Collections.singletonList("site1"));
            item.setLocale(java.util.Locale.US);
            Map<String, Object> attributes = new HashMap<>();
            attributes.put(TurSNFieldName.ID, "1");
            attributes.put("text_file", "file://sample.txt");
            item.setAttributes(attributes);
            jobItems.add(item);

            when(turSNSiteRepository.findByName("site1")).thenReturn(Optional.empty());

            zos.write(objectMapper.writeValueAsBytes(jobItems));
            zos.closeEntry();

            zos.putNextEntry(new ZipEntry("sample.txt"));
            zos.write("hello".getBytes());
            zos.closeEntry();
        }

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "test.zip",
                "application/zip",
                java.nio.file.Files.readAllBytes(tempZip.toPath()));

        mockMvc.perform(multipart("/api/sn/import/zip").file(file))
                .andExpect(status().isOk())
                .andExpect(content().string("true"));

        verify(jmsMessagingTemplate, times(1)).convertAndSend(anyString(), any(TurSNJobItems.class));

        tempZip.delete();
    }
}
