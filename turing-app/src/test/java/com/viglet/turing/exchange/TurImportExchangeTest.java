package com.viglet.turing.exchange;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import com.viglet.turing.exchange.sn.TurSNSiteExchange;
import com.viglet.turing.exchange.sn.TurSNSiteImport;

import tools.jackson.databind.json.JsonMapper;

class TurImportExchangeTest {

    @Test
    void shouldReturnEmptyExchangeWhenFileDoesNotExist() {
        TurSNSiteImport turSNSiteImport = org.mockito.Mockito.mock(TurSNSiteImport.class);
        TurImportExchange turImportExchange = new TurImportExchange(turSNSiteImport);

        TurExchange result = turImportExchange.importFromFile(new File("D:/this/path/does/not/exist.zip"));

        assertNotNull(result);
        verifyNoInteractions(turSNSiteImport);
    }

    @Test
    void shouldReturnEmptyExchangeWhenZipExtractionFails() {
        TurSNSiteImport turSNSiteImport = org.mockito.Mockito.mock(TurSNSiteImport.class);
        TurImportExchange turImportExchange = org.mockito.Mockito.spy(new TurImportExchange(turSNSiteImport));
        MultipartFile multipartFile = new MockMultipartFile("file", "any.zip", "application/zip", new byte[] { 1, 2 });

        org.mockito.Mockito.doReturn(null).when(turImportExchange).extractZipFile(multipartFile);

        TurExchange result = turImportExchange.importFromMultipartFile(multipartFile);

        assertNotNull(result);
        verifyNoInteractions(turSNSiteImport);
    }

    @Test
    void shouldReadExportFromSingleSubDirectoryWhenRootHasNoExport(@TempDir Path tempDir) throws Exception {
        TurSNSiteImport turSNSiteImport = org.mockito.Mockito.mock(TurSNSiteImport.class);
        TurImportExchange turImportExchange = org.mockito.Mockito.spy(new TurImportExchange(turSNSiteImport));

        File rootExtract = tempDir.resolve("rootExtract").toFile();
        File subDir = new File(rootExtract, "nested");
        Files.createDirectories(subDir.toPath());
        Files.writeString(new File(subDir, "export.json").toPath(), "{\"snSites\":[]}", StandardCharsets.UTF_8);

        MultipartFile multipartFile = new MockMultipartFile("file", "any.zip", "application/zip", new byte[] { 1 });
        org.mockito.Mockito.doReturn(rootExtract).when(turImportExchange).extractZipFile(multipartFile);

        TurExchange result = turImportExchange.importFromMultipartFile(multipartFile);

        assertNotNull(result);
        verifyNoInteractions(turSNSiteImport);
    }

    @Test
    void shouldImportSnSitesWhenExportContainsSnSite(@TempDir Path tempDir) throws Exception {
        TurSNSiteImport turSNSiteImport = org.mockito.Mockito.mock(TurSNSiteImport.class);
        TurImportExchange turImportExchange = org.mockito.Mockito.spy(new TurImportExchange(turSNSiteImport));

        File extract = tempDir.resolve("extract").toFile();
        Files.createDirectories(extract.toPath());

        TurSNSiteExchange site = new TurSNSiteExchange();
        site.setName("site-1");
        TurExchange exchange = new TurExchange();
        exchange.setSnSites(List.of(site));

        String json = JsonMapper.builder().build().writeValueAsString(exchange);
        Files.writeString(new File(extract, "export.json").toPath(), json, StandardCharsets.UTF_8);

        MultipartFile multipartFile = new MockMultipartFile("file", "any.zip", "application/zip", new byte[] { 1 });
        org.mockito.Mockito.doReturn(extract).when(turImportExchange).extractZipFile(multipartFile);

        TurExchange result = turImportExchange.importFromMultipartFile(multipartFile);

        assertNotNull(result);
        verify(turSNSiteImport, times(1)).importSNSite(org.mockito.ArgumentMatchers.any(TurExchange.class));
    }
}
