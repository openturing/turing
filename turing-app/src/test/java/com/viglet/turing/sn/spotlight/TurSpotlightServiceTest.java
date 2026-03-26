package com.viglet.turing.sn.spotlight;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verifyNoInteractions;

import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.viglet.turing.persistence.model.sn.TurSNSite;
import com.viglet.turing.persistence.model.sn.spotlight.TurSNSiteSpotlight;
import com.viglet.turing.persistence.repository.sn.TurSNSiteRepository;
import com.viglet.turing.persistence.repository.sn.spotlight.TurSNSiteSpotlightRepository;

/**
 * Tests for TurSpotlightService.
 *
 * @author Alexandre Oliveira
 * @since 2026.1.14
 */
@ExtendWith(MockitoExtension.class)
class TurSpotlightServiceTest {

    @Mock
    private TurSNSiteRepository turSNSiteRepository;

    @Mock
    private TurSNSiteSpotlightRepository turSNSiteSpotlightRepository;

    private TurSpotlightService service;

    @BeforeEach
    void setUp() {
        service = new TurSpotlightService(turSNSiteRepository, turSNSiteSpotlightRepository);
    }

    @Test
    void findSpotlightShouldReturnListWhenSiteExists() {
        TurSNSite site = new TurSNSite();
        when(turSNSiteRepository.findByNameIgnoreCase("site")).thenReturn(Optional.of(site));
        List<TurSNSiteSpotlight> spotlights = List.of(new TurSNSiteSpotlight());
        when(turSNSiteSpotlightRepository.findByTurSNSiteAndLanguage(site, Locale.US))
                .thenReturn(spotlights);

        assertThat(service.findSpotlightBySNSiteAndLanguage("site", Locale.US))
                .isEqualTo(spotlights);
    }

    @Test
    void findSpotlightShouldReturnEmptyWhenSiteMissing() {
        when(turSNSiteRepository.findByNameIgnoreCase("site")).thenReturn(Optional.empty());

        assertThat(service.findSpotlightBySNSiteAndLanguage("site", Locale.US))
                .isEqualTo(Collections.emptyList());
    }

    @Test
    void findSpotlightShouldReturnEmptyWhenNoSpotlightsForLocale() {
        TurSNSite site = new TurSNSite();
        when(turSNSiteRepository.findByNameIgnoreCase("mySite")).thenReturn(Optional.of(site));
        when(turSNSiteSpotlightRepository.findByTurSNSiteAndLanguage(site, Locale.FRENCH))
                .thenReturn(Collections.emptyList());

        assertThat(service.findSpotlightBySNSiteAndLanguage("mySite", Locale.FRENCH))
                .isEmpty();
    }

    @Test
    void findSpotlightShouldReturnMultipleSpotlights() {
        TurSNSite site = new TurSNSite();
        when(turSNSiteRepository.findByNameIgnoreCase("site")).thenReturn(Optional.of(site));

        TurSNSiteSpotlight sp1 = new TurSNSiteSpotlight();
        TurSNSiteSpotlight sp2 = new TurSNSiteSpotlight();
        TurSNSiteSpotlight sp3 = new TurSNSiteSpotlight();
        when(turSNSiteSpotlightRepository.findByTurSNSiteAndLanguage(site, Locale.US))
                .thenReturn(List.of(sp1, sp2, sp3));

        assertThat(service.findSpotlightBySNSiteAndLanguage("site", Locale.US))
                .hasSize(3)
                .containsExactly(sp1, sp2, sp3);
    }

    @Test
    void findSpotlightShouldBeCaseInsensitiveOnSiteName() {
        TurSNSite site = new TurSNSite();
        when(turSNSiteRepository.findByNameIgnoreCase("MySite")).thenReturn(Optional.of(site));
        when(turSNSiteSpotlightRepository.findByTurSNSiteAndLanguage(site, Locale.US))
                .thenReturn(List.of(new TurSNSiteSpotlight()));

        assertThat(service.findSpotlightBySNSiteAndLanguage("MySite", Locale.US))
                .hasSize(1);
    }

    @Test
    void findSpotlightShouldNotQuerySpotlightRepoWhenSiteNotFound() {
        when(turSNSiteRepository.findByNameIgnoreCase("unknown")).thenReturn(Optional.empty());

        service.findSpotlightBySNSiteAndLanguage("unknown", Locale.US);

        verifyNoInteractions(turSNSiteSpotlightRepository);
    }
}
