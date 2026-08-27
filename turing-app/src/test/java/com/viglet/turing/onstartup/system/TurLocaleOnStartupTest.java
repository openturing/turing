package com.viglet.turing.onstartup.system;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Locale;

import org.apache.commons.lang3.LocaleUtils;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import com.viglet.turing.persistence.model.system.TurLocale;
import com.viglet.turing.persistence.repository.system.TurLocaleRepository;

/**
 * T1043 — the seeding used to be guarded by {@code findAll().isEmpty()}, and a
 * test here called {@code shouldNotCreateDefaultLocalesWhenRepositoryHasData}
 * asserted that guard: one row in the table means no locale is ever written
 * again. That is the defect, not the contract — a locale added in a later release
 * could not be assigned to an SN Site on any database that had ever started the
 * application.
 *
 * <p>The catalog is now diffed against what the table holds. Note the shape this
 * seeder uses and the two vendor seeders do not: the existing ids are read
 * <em>once</em> and compared in memory, because 48 {@code existsById} calls per
 * boot would be 48 queries to answer a question one query answers.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TurLocaleOnStartupTest {

    @Mock
    private TurLocaleRepository turLocaleRepository;

    @InjectMocks
    private TurLocaleOnStartup turLocaleOnStartup;

    @SuppressWarnings("unchecked")
    private List<TurLocale> capturedSaveAll() {
        ArgumentCaptor<Iterable<TurLocale>> captor = ArgumentCaptor.forClass(Iterable.class);
        verify(turLocaleRepository).saveAll(captor.capture());
        List<TurLocale> saved = new java.util.ArrayList<>();
        captor.getValue().forEach(saved::add);
        return saved;
    }

    @Test
    @DisplayName("An empty catalog gets the whole shipped locale set")
    void seedsEveryLocaleIntoAnEmptyCatalog() {
        when(turLocaleRepository.findAll()).thenReturn(List.of());

        turLocaleOnStartup.createDefaultRows();

        List<TurLocale> saved = capturedSaveAll();
        assertThat(saved).hasSize(48);
        assertThat(saved).extracting(TurLocale::getInitials)
                .contains(Locale.ENGLISH, Locale.US, Locale.UK, Locale.FRENCH,
                        LocaleUtils.toLocale("pt_BR"), LocaleUtils.toLocale("zh_TW"));
        // No id may exceed the column, which is VARCHAR(5).
        assertThat(saved).allSatisfy(locale -> assertThat(locale.getInitials().toString())
                .hasSizeLessThan(6));
        // Both display languages are filled in; a blank one renders as an empty
        // option in the SN Site locale picker.
        assertThat(saved).allSatisfy(locale -> {
            assertThat(locale.getEn()).isNotBlank();
            assertThat(locale.getPt()).isNotBlank();
        });
    }

    @Test
    @DisplayName("An existing install still gets a locale added in a later release")
    void addsOnlyTheMissingLocalesToAPopulatedCatalog() {
        // Everything the shipped catalog holds except pt_BR is already in the table.
        when(turLocaleRepository.findAll()).thenReturn(List.of(
                new TurLocale(Locale.ENGLISH, "English", "English"),
                new TurLocale(LocaleUtils.toLocale("es"), "Spanish", "Espanhol")));

        turLocaleOnStartup.createDefaultRows();

        List<TurLocale> saved = capturedSaveAll();
        assertThat(saved).extracting(TurLocale::getInitials)
                .contains(LocaleUtils.toLocale("pt_BR"))
                .doesNotContain(Locale.ENGLISH, LocaleUtils.toLocale("es"));
        assertThat(saved).hasSize(46);
    }

    @Test
    @DisplayName("Nothing is written when the catalog is already complete")
    void writesNothingWhenEveryLocaleIsPresent() {
        // Resolved before the stubbing starts: everyShippedLocale() stubs a probe
        // mock of its own, and Mockito rejects a nested when(...) inside one.
        List<TurLocale> complete = everyShippedLocale();
        when(turLocaleRepository.findAll()).thenReturn(complete);

        turLocaleOnStartup.createDefaultRows();

        verify(turLocaleRepository, never()).saveAll(org.mockito.ArgumentMatchers.any());
        verify(turLocaleRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    /**
     * The shipped catalog, read back out of the seeder itself by letting it write
     * into an empty table. Hardcoding 48 locales here would be a second copy to
     * keep in step with the first.
     */
    @SuppressWarnings("unchecked")
    private List<TurLocale> everyShippedLocale() {
        TurLocaleRepository probe = org.mockito.Mockito.mock(TurLocaleRepository.class);
        when(probe.findAll()).thenReturn(List.of());
        new TurLocaleOnStartup(probe).createDefaultRows();
        ArgumentCaptor<Iterable<TurLocale>> captor = ArgumentCaptor.forClass(Iterable.class);
        verify(probe).saveAll(captor.capture());
        List<TurLocale> all = new java.util.ArrayList<>();
        captor.getValue().forEach(all::add);
        return all;
    }
}
