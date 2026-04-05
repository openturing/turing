package com.viglet.turing.lucene;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.when;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.viglet.turing.persistence.model.se.TurSEInstance;
import com.viglet.turing.persistence.model.sn.TurSNSite;
import com.viglet.turing.persistence.model.sn.locale.TurSNSiteLocale;
import com.viglet.turing.persistence.repository.sn.TurSNSiteRepository;
import com.viglet.turing.persistence.repository.sn.locale.TurSNSiteLocaleRepository;

/**
 * Tests for TurLuceneInstanceProcess.
 *
 * @author Alexandre Oliveira
 * @since 2026.1.14
 */
@ExtendWith(MockitoExtension.class)
class TurLuceneInstanceProcessTest {

    @Mock
    private TurSNSiteRepository turSNSiteRepository;

    @Mock
    private TurSNSiteLocaleRepository turSNSiteLocaleRepository;

    @TempDir
    Path tempDir;

    private TurLuceneInstanceProcess process;

    @BeforeEach
    void setUp() {
        process = new TurLuceneInstanceProcess(turSNSiteRepository, turSNSiteLocaleRepository, null);
    }

    @AfterEach
    void tearDown() {
        process.shutdown();
    }

    // --- initLuceneInstance(TurSEInstance, String) Tests ---

    @Test
    void initLuceneInstanceShouldCreateInstanceForValidPath() {
        TurSEInstance seInstance = new TurSEInstance();
        seInstance.setEndpointUrl(tempDir.toString());

        Optional<TurLuceneInstance> result = process.initLuceneInstance(seInstance, "test-core");

        assertThat(result).isPresent();
        assertThat(Files.exists(tempDir.resolve("test-core"))).isTrue();
    }

    @Test
    void initLuceneInstanceShouldReturnCachedInstance() {
        TurSEInstance seInstance = new TurSEInstance();
        seInstance.setEndpointUrl(tempDir.toString());

        Optional<TurLuceneInstance> first = process.initLuceneInstance(seInstance, "test-core");
        Optional<TurLuceneInstance> second = process.initLuceneInstance(seInstance, "test-core");

        assertThat(first).isPresent();
        assertThat(second).isPresent();
        assertThat(first).containsSame(second.get());
    }

    @Test
    void initLuceneInstanceShouldReturnDifferentInstancesForDifferentCores() {
        TurSEInstance seInstance = new TurSEInstance();
        seInstance.setEndpointUrl(tempDir.toString());

        Optional<TurLuceneInstance> core1 = process.initLuceneInstance(seInstance, "core1");
        Optional<TurLuceneInstance> core2 = process.initLuceneInstance(seInstance, "core2");

        assertThat(core1).isPresent();
        assertThat(core2).isPresent();
        assertThat(core1.get()).isNotSameAs(core2.get());
    }

    // --- initLuceneInstance(String siteName, Locale) Tests ---

    @Test
    void initLuceneInstanceByNameShouldReturnEmptyWhenSiteNotFound() {
        when(turSNSiteRepository.findByNameIgnoreCase("missing")).thenReturn(Optional.empty());

        Optional<TurLuceneInstance> result = process.initLuceneInstance("missing", Locale.ENGLISH);

        assertThat(result).isEmpty();
    }

    @Test
    void initLuceneInstanceByNameShouldReturnEmptyWhenLocaleNotFound() {
        TurSNSite site = new TurSNSite();
        site.setName("MySite");
        when(turSNSiteRepository.findByNameIgnoreCase("MySite")).thenReturn(Optional.of(site));
        when(turSNSiteLocaleRepository.findByTurSNSiteAndLanguage(site, Locale.ENGLISH)).thenReturn(null);

        Optional<TurLuceneInstance> result = process.initLuceneInstance("MySite", Locale.ENGLISH);

        assertThat(result).isEmpty();
    }

    @Test
    void initLuceneInstanceByNameShouldReturnInstanceWhenValid() {
        TurSEInstance seInstance = new TurSEInstance();
        seInstance.setEndpointUrl(tempDir.toString());

        TurSNSite site = new TurSNSite();
        site.setName("MySite");
        site.setTurSEInstance(seInstance);

        TurSNSiteLocale siteLocale = new TurSNSiteLocale();
        siteLocale.setCore("en-core");
        siteLocale.setTurSNSite(site);

        when(turSNSiteRepository.findByNameIgnoreCase("MySite")).thenReturn(Optional.of(site));
        when(turSNSiteLocaleRepository.findByTurSNSiteAndLanguage(site, Locale.ENGLISH)).thenReturn(siteLocale);

        Optional<TurLuceneInstance> result = process.initLuceneInstance("MySite", Locale.ENGLISH);

        assertThat(result).isPresent();
    }

    // --- initLuceneInstance(TurSNSiteLocale) Tests ---

    @Test
    void initLuceneInstanceByLocaleEntityShouldReturnInstance() {
        TurSEInstance seInstance = new TurSEInstance();
        seInstance.setEndpointUrl(tempDir.toString());

        TurSNSite site = new TurSNSite();
        site.setTurSEInstance(seInstance);

        TurSNSiteLocale siteLocale = new TurSNSiteLocale();
        siteLocale.setCore("locale-core");
        siteLocale.setTurSNSite(site);

        Optional<TurLuceneInstance> result = process.initLuceneInstance(siteLocale);

        assertThat(result).isPresent();
    }

    // --- evict Tests ---

    @Test
    void evictShouldRemoveCachedInstance() {
        TurSEInstance seInstance = new TurSEInstance();
        seInstance.setEndpointUrl(tempDir.toString());

        Optional<TurLuceneInstance> instance = process.initLuceneInstance(seInstance, "evict-core");
        assertThat(instance).isPresent();

        process.evict(tempDir.toString(), "evict-core");

        // After evict, a new call should create a different instance
        Optional<TurLuceneInstance> newInstance = process.initLuceneInstance(seInstance, "evict-core");
        assertThat(newInstance).isPresent();
        assertThat(newInstance.get()).isNotSameAs(instance.get());
    }

    @Test
    void evictShouldDoNothingForNonExistentCache() {
        // Should not throw
        assertDoesNotThrow(() -> process.evict(tempDir.toString(), "non-existent-core"));
    }

    // --- resetInstance Tests ---

    @Test
    void resetInstanceShouldDeleteIndexDirectory() {
        TurSEInstance seInstance = new TurSEInstance();
        seInstance.setEndpointUrl(tempDir.toString());

        process.initLuceneInstance(seInstance, "reset-core");
        Path indexPath = tempDir.resolve("reset-core");
        assertThat(Files.exists(indexPath)).isTrue();

        process.resetInstance(indexPath);

        assertThat(Files.exists(indexPath)).isFalse();
    }

    @Test
    void resetInstanceShouldHandleNonExistentPath() {
        Path nonExistent = tempDir.resolve("does-not-exist");

        // Should not throw
        assertDoesNotThrow(() -> process.resetInstance(nonExistent));
    }

    // --- shutdown Tests ---

    @Test
    void shutdownShouldCloseAllInstances() {
        TurSEInstance seInstance = new TurSEInstance();
        seInstance.setEndpointUrl(tempDir.toString());

        process.initLuceneInstance(seInstance, "shutdown-core1");
        process.initLuceneInstance(seInstance, "shutdown-core2");

        // Should not throw
        assertDoesNotThrow(() -> process.shutdown());
    }

    @Test
    void shutdownShouldBeIdempotent() {
        process.shutdown();
        assertDoesNotThrow(() -> process.shutdown()); // Should not throw
    }
}
