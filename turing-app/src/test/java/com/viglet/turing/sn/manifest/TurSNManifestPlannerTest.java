package com.viglet.turing.sn.manifest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.apache.commons.lang3.LocaleUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Sort;

import com.viglet.core.manifest.VigletFieldManifest;
import com.viglet.core.manifest.VigletFieldMigration;
import com.viglet.core.manifest.VigletFieldSpec;
import com.viglet.core.manifest.VigletFieldType;
import com.viglet.core.manifest.VigletManifestDiff;
import com.viglet.core.manifest.VigletManifestFieldChange;
import com.viglet.core.manifest.VigletManifestMigrationAction;
import com.viglet.turing.commons.se.field.TurSEFieldType;
import com.viglet.turing.persistence.model.sn.TurSNSite;
import com.viglet.turing.persistence.model.sn.field.TurSNSiteFieldExt;
import com.viglet.turing.persistence.repository.sn.TurSNSiteRepository;
import com.viglet.turing.persistence.repository.sn.field.TurSNSiteFieldExtRepository;
import com.viglet.turing.persistence.repository.sn.locale.TurSNSiteLocaleRepository;

@ExtendWith(MockitoExtension.class)
class TurSNManifestPlannerTest {

    @Mock
    private TurSNSiteRepository turSNSiteRepository;
    @Mock
    private TurSNSiteFieldExtRepository turSNSiteFieldExtRepository;
    @Mock
    private TurSNSiteLocaleRepository turSNSiteLocaleRepository;
    @InjectMocks
    private TurSNManifestPlanner planner;

    private VigletFieldSpec spec(String name, VigletFieldType type, boolean multi) {
        return VigletFieldSpec.builder().name(name).type(type).multiValued(multi).build();
    }

    private TurSNSiteFieldExt liveField(String name, TurSEFieldType type, boolean multi) {
        return TurSNSiteFieldExt.builder().name(name).type(type).multiValued(multi ? 1 : 0).build();
    }

    @Test
    void newSiteMakesEverythingAdditive() {
        VigletFieldManifest manifest = new VigletFieldManifest(
                "courses", null, "se1", "1", List.of("pt_BR"),
                List.of(spec("mensalidade", VigletFieldType.STRING, false)), null);
        when(turSNSiteRepository.findByNameIgnoreCase("courses")).thenReturn(Optional.empty());

        VigletManifestDiff diff = planner.diff(manifest);

        assertFalse(diff.siteExists());
        assertEquals(List.of("mensalidade"), diff.fieldsToAdd());
        assertEquals(List.of("pt_BR"), diff.localesToAdd());
        assertFalse(diff.hasBreakingChanges());
        assertEquals("1", diff.schemaVersion());
    }

    @Test
    void newFieldIsAdditiveAndMatchingFieldIsUnchanged() {
        TurSNSite site = new TurSNSite();
        VigletFieldManifest manifest = new VigletFieldManifest(
                "courses", null, null, null,
                List.of(spec("mensalidade", VigletFieldType.STRING, false),
                        spec("modalidade", VigletFieldType.STRING, false)));

        when(turSNSiteFieldExtRepository.findByTurSNSite(any(Sort.class), eq(site)))
                .thenReturn(List.of(liveField("mensalidade", TurSEFieldType.STRING, false)));

        VigletManifestDiff diff = planner.diff(manifest, site);

        assertEquals(List.of("modalidade"), diff.fieldsToAdd());
        assertEquals(List.of("mensalidade"), diff.fieldsUnchanged());
        assertFalse(diff.hasBreakingChanges());
    }

    @Test
    void typeChangeIsBreakingAndUndeclaredWithoutMigration() {
        TurSNSite site = new TurSNSite();
        VigletFieldManifest manifest = new VigletFieldManifest(
                "courses", null, null, null,
                List.of(spec("mensalidade", VigletFieldType.INT, false)));

        when(turSNSiteFieldExtRepository.findByTurSNSite(any(Sort.class), eq(site)))
                .thenReturn(List.of(liveField("mensalidade", TurSEFieldType.STRING, false)));

        VigletManifestDiff diff = planner.diff(manifest, site);

        assertTrue(diff.hasBreakingChanges());
        assertEquals(1, diff.breakingChanges().size());
        VigletManifestFieldChange change = diff.breakingChanges().get(0);
        assertEquals("type", change.attribute());
        assertEquals("STRING", change.liveValue());
        assertEquals("INT", change.manifestValue());
        assertFalse(change.migrationDeclared());
        assertEquals(1, diff.undeclaredBreakingChanges().size());
    }

    @Test
    void multiValuedChangeIsBreaking() {
        TurSNSite site = new TurSNSite();
        VigletFieldManifest manifest = new VigletFieldManifest(
                "courses", null, null, null,
                List.of(spec("tags", VigletFieldType.STRING, true)));

        when(turSNSiteFieldExtRepository.findByTurSNSite(any(Sort.class), eq(site)))
                .thenReturn(List.of(liveField("tags", TurSEFieldType.STRING, false)));

        VigletManifestDiff diff = planner.diff(manifest, site);

        assertTrue(diff.hasBreakingChanges());
        assertEquals("multiValued", diff.breakingChanges().get(0).attribute());
    }

    @Test
    void declaredMigrationMarksBreakingChangeAsDeclared() {
        TurSNSite site = new TurSNSite();
        VigletFieldManifest manifest = new VigletFieldManifest(
                "courses", null, null, null, null,
                List.of(spec("mensalidade", VigletFieldType.INT, false)),
                List.of(new VigletFieldMigration("mensalidade", VigletManifestMigrationAction.RECREATE)));

        when(turSNSiteFieldExtRepository.findByTurSNSite(any(Sort.class), eq(site)))
                .thenReturn(List.of(liveField("mensalidade", TurSEFieldType.STRING, false)));

        VigletManifestDiff diff = planner.diff(manifest, site);

        assertTrue(diff.hasBreakingChanges());
        assertTrue(diff.breakingChanges().get(0).migrationDeclared());
        assertTrue(diff.undeclaredBreakingChanges().isEmpty());
    }

    @Test
    void localeAlreadyPresentIsNotAdded() {
        TurSNSite site = new TurSNSite();
        VigletFieldManifest manifest = new VigletFieldManifest(
                "courses", null, null, List.of("pt_BR", "en_US"), List.of());

        when(turSNSiteFieldExtRepository.findByTurSNSite(any(Sort.class), eq(site)))
                .thenReturn(List.of());
        when(turSNSiteLocaleRepository.existsByTurSNSiteAndLanguage(site, LocaleUtils.toLocale("pt_BR")))
                .thenReturn(true);
        when(turSNSiteLocaleRepository.existsByTurSNSiteAndLanguage(site, LocaleUtils.toLocale("en_US")))
                .thenReturn(false);

        VigletManifestDiff diff = planner.diff(manifest, site);

        assertEquals(List.of("en_US"), diff.localesToAdd());
    }
}
