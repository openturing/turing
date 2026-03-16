package com.viglet.turing.lucene;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.viglet.turing.solr.bean.TurSECoreInfo;

class TurLuceneUtilsTest {

    @TempDir
    Path tempDir;

    @Test
    void createCoreShouldCreateDirectory() {
        TurLuceneUtils.createCore(tempDir.toString(), "mycore");
        assertTrue(Files.isDirectory(tempDir.resolve("mycore")));
    }

    @Test
    void deleteCoreShouldRemoveDirectory() throws IOException {
        Path core = tempDir.resolve("todelete");
        Files.createDirectories(core);
        Files.writeString(core.resolve("file.txt"), "data");
        TurLuceneUtils.deleteCore(tempDir.toString(), "todelete");
        assertFalse(Files.exists(core));
    }

    @Test
    void deleteCoreShouldNotThrowForNonExistentPath() {
        assertDoesNotThrow(() -> TurLuceneUtils.deleteCore(tempDir.toString(), "nonexistent"));
    }

    @Test
    void coreExistsShouldReturnTrueForExistingDir() throws IOException {
        Files.createDirectories(tempDir.resolve("existing"));
        assertTrue(TurLuceneUtils.coreExists(tempDir.toString(), "existing"));
    }

    @Test
    void coreExistsShouldReturnFalseForMissingDir() {
        assertFalse(TurLuceneUtils.coreExists(tempDir.toString(), "missing"));
    }

    @Test
    void listCoresShouldReturnSubdirectories() throws IOException {
        Files.createDirectories(tempDir.resolve("core1"));
        Files.createDirectories(tempDir.resolve("core2"));
        Files.writeString(tempDir.resolve("notadir.txt"), "file");
        List<TurSECoreInfo> cores = TurLuceneUtils.listCores(tempDir.toString());
        assertEquals(2, cores.size());
        assertEquals("core1", cores.get(0).name());
        assertEquals("core2", cores.get(1).name());
    }

    @Test
    void listCoresShouldReturnEmptyListForNonExistingPath() {
        List<TurSECoreInfo> cores = TurLuceneUtils.listCores(tempDir.resolve("nope").toString());
        assertTrue(cores.isEmpty());
    }

    @Test
    void addOrUpdateFieldShouldNotThrow() {
        assertDoesNotThrow(() -> TurLuceneUtils.addOrUpdateField("core", "field"));
    }

    @Test
    void deleteFieldShouldNotThrow() {
        assertDoesNotThrow(() -> TurLuceneUtils.deleteField("core", "field"));
    }
}
