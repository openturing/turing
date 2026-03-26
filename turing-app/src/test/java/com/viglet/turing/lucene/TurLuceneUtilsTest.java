package com.viglet.turing.lucene;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.viglet.turing.solr.bean.TurSECoreInfo;

/**
 * Tests for TurLuceneUtils.
 *
 * @author Alexandre Oliveira
 * @since 2026.1.14
 */
class TurLuceneUtilsTest {

    @TempDir
    Path tempDir;

    // ---- Constructor ----

    @Test
    void constructorShouldThrowIllegalStateException() {
        var constructor = TurLuceneUtils.class.getDeclaredConstructors()[0];
        constructor.setAccessible(true);
        var ex = assertThrows(java.lang.reflect.InvocationTargetException.class,
                constructor::newInstance);
        assertInstanceOf(IllegalStateException.class, ex.getCause());
        assertEquals("Lucene Utility class", ex.getCause().getMessage());
    }

    // ---- createCore ----

    @Test
    void createCoreShouldCreateDirectory() {
        TurLuceneUtils.createCore(tempDir.toString(), "mycore");
        assertTrue(Files.isDirectory(tempDir.resolve("mycore")));
    }

    @Test
    void createCoreShouldBeIdempotent() {
        TurLuceneUtils.createCore(tempDir.toString(), "idempotent");
        TurLuceneUtils.createCore(tempDir.toString(), "idempotent");
        assertTrue(Files.isDirectory(tempDir.resolve("idempotent")));
    }

    @Test
    void createCoreShouldCreateNestedDirectories() {
        TurLuceneUtils.createCore(tempDir.toString(), "nested");
        assertTrue(Files.isDirectory(tempDir.resolve("nested")));
    }

    // ---- deleteCore ----

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
    void deleteCoreShouldRemoveNestedFiles() throws IOException {
        Path core = tempDir.resolve("deepcore");
        Path subDir = core.resolve("subdir");
        Files.createDirectories(subDir);
        Files.writeString(subDir.resolve("data.bin"), "binary");
        Files.writeString(core.resolve("index.dat"), "index");

        TurLuceneUtils.deleteCore(tempDir.toString(), "deepcore");
        assertFalse(Files.exists(core));
    }

    @Test
    void deleteCoreShouldRemoveEmptyDirectory() throws IOException {
        Path core = tempDir.resolve("emptycore");
        Files.createDirectories(core);
        TurLuceneUtils.deleteCore(tempDir.toString(), "emptycore");
        assertFalse(Files.exists(core));
    }

    // ---- coreExists ----

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
    void coreExistsShouldReturnFalseForFile() throws IOException {
        Files.writeString(tempDir.resolve("notadir"), "content");
        assertFalse(TurLuceneUtils.coreExists(tempDir.toString(), "notadir"));
    }

    @Test
    void coreExistsShouldReturnFalseForNonExistentBasePath() {
        assertFalse(TurLuceneUtils.coreExists(tempDir.resolve("nope").toString(), "core"));
    }

    // ---- listCores ----

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
    void listCoresShouldReturnSortedResults() throws IOException {
        Files.createDirectories(tempDir.resolve("zebra"));
        Files.createDirectories(tempDir.resolve("alpha"));
        Files.createDirectories(tempDir.resolve("middle"));
        List<TurSECoreInfo> cores = TurLuceneUtils.listCores(tempDir.toString());
        assertEquals(3, cores.size());
        assertEquals("alpha", cores.get(0).name());
        assertEquals("middle", cores.get(1).name());
        assertEquals("zebra", cores.get(2).name());
    }

    @Test
    void listCoresShouldReturnEmptyListForEmptyDirectory() {
        List<TurSECoreInfo> cores = TurLuceneUtils.listCores(tempDir.toString());
        assertTrue(cores.isEmpty());
    }

    @Test
    void listCoresShouldReturnZeroDocCount() throws IOException {
        Files.createDirectories(tempDir.resolve("core1"));
        List<TurSECoreInfo> cores = TurLuceneUtils.listCores(tempDir.toString());
        assertEquals(1, cores.size());
        assertEquals(0L, cores.get(0).numDocs());
    }

    @Test
    void listCoresShouldReturnEmptyFieldList() throws IOException {
        Files.createDirectories(tempDir.resolve("core1"));
        List<TurSECoreInfo> cores = TurLuceneUtils.listCores(tempDir.toString());
        assertTrue(cores.get(0).usedBySites().isEmpty());
    }

    // ---- addOrUpdateField (no-op) ----

    @Test
    void addOrUpdateFieldShouldNotThrow() {
        assertDoesNotThrow(() -> TurLuceneUtils.addOrUpdateField("core", "field"));
    }

    @Test
    void addOrUpdateFieldShouldNotThrowWithNullArgs() {
        assertDoesNotThrow(() -> TurLuceneUtils.addOrUpdateField(null, null));
    }

    // ---- deleteField (no-op) ----

    @Test
    void deleteFieldShouldNotThrow() {
        assertDoesNotThrow(() -> TurLuceneUtils.deleteField("core", "field"));
    }

    @Test
    void deleteFieldShouldNotThrowWithNullArgs() {
        assertDoesNotThrow(() -> TurLuceneUtils.deleteField(null, null));
    }

    // ---- Integration: create then list then delete ----

    @Test
    void fullLifecycleCreateListDeleteShouldWork() {
        TurLuceneUtils.createCore(tempDir.toString(), "lifecycle");
        assertTrue(TurLuceneUtils.coreExists(tempDir.toString(), "lifecycle"));

        List<TurSECoreInfo> cores = TurLuceneUtils.listCores(tempDir.toString());
        assertEquals(1, cores.size());
        assertEquals("lifecycle", cores.get(0).name());

        TurLuceneUtils.deleteCore(tempDir.toString(), "lifecycle");
        assertFalse(TurLuceneUtils.coreExists(tempDir.toString(), "lifecycle"));

        cores = TurLuceneUtils.listCores(tempDir.toString());
        assertTrue(cores.isEmpty());
    }
}
