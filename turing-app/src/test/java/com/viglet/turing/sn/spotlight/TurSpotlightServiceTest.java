package com.viglet.turing.sn.spotlight;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
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

import com.viglet.turing.domain.sn.TurSNSiteDomain;
import com.viglet.turing.domain.sn.TurSNSiteRepositoryPort;
import com.viglet.turing.persistence.model.sn.spotlight.TurSNSiteSpotlight;
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
    private TurSNSiteRepositoryPort turSNSiteRepositoryPort;

    @Mock
    private TurSNSiteSpotlightRepository turSNSiteSpotlightRepository;

    private TurSpotlightService service;

    @BeforeEach
    void setUp() {
        service = new TurSpotlightService(turSNSiteRepositoryPort, turSNSiteSpotlightRepository);
    }

    private static TurSNSiteDomain siteDomain(String id, String name) {
        return new TurSNSiteDomain(id, name, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null, null, null, null);
    }

    @Test
    void findSpotlightShouldReturnListWhenSiteExists() {
        when(turSNSiteRepositoryPort.findByNameIgnoreCase("site"))
                .thenReturn(Optional.of(siteDomain("site-id", "site")));
        List<TurSNSiteSpotlight> spotlights = List.of(new TurSNSiteSpotlight());
        when(turSNSiteSpotlightRepository.findByTurSNSiteAndLanguage(
                argThat(stub -> "site-id".equals(stub.getId())), eq(Locale.US)))
                .thenReturn(spotlights);

        assertThat(service.findSpotlightBySNSiteAndLanguage("site", Locale.US))
                .isEqualTo(spotlights);
    }

    @Test
    void findSpotlightShouldReturnEmptyWhenSiteMissing() {
        when(turSNSiteRepositoryPort.findByNameIgnoreCase("site")).thenReturn(Optional.empty());

        assertThat(service.findSpotlightBySNSiteAndLanguage("site", Locale.US))
                .isEqualTo(Collections.emptyList());
    }

    @Test
    void findSpotlightShouldReturnEmptyWhenNoSpotlightsForLocale() {
        when(turSNSiteRepositoryPort.findByNameIgnoreCase("mySite"))
                .thenReturn(Optional.of(siteDomain("mysite-id", "mySite")));
        when(turSNSiteSpotlightRepository.findByTurSNSiteAndLanguage(any(), eq(Locale.FRENCH)))
                .thenReturn(Collections.emptyList());

        assertThat(service.findSpotlightBySNSiteAndLanguage("mySite", Locale.FRENCH))
                .isEmpty();
    }

    @Test
    void findSpotlightShouldReturnMultipleSpotlights() {
        when(turSNSiteRepositoryPort.findByNameIgnoreCase("site"))
                .thenReturn(Optional.of(siteDomain("site-id", "site")));

        TurSNSiteSpotlight sp1 = new TurSNSiteSpotlight();
        TurSNSiteSpotlight sp2 = new TurSNSiteSpotlight();
        TurSNSiteSpotlight sp3 = new TurSNSiteSpotlight();
        when(turSNSiteSpotlightRepository.findByTurSNSiteAndLanguage(any(), eq(Locale.US)))
                .thenReturn(List.of(sp1, sp2, sp3));

        assertThat(service.findSpotlightBySNSiteAndLanguage("site", Locale.US))
                .hasSize(3)
                .containsExactly(sp1, sp2, sp3);
    }

    @Test
    void findSpotlightShouldBeCaseInsensitiveOnSiteName() {
        when(turSNSiteRepositoryPort.findByNameIgnoreCase("MySite"))
                .thenReturn(Optional.of(siteDomain("mysite-id", "MySite")));
        when(turSNSiteSpotlightRepository.findByTurSNSiteAndLanguage(any(), eq(Locale.US)))
                .thenReturn(List.of(new TurSNSiteSpotlight()));

        assertThat(service.findSpotlightBySNSiteAndLanguage("MySite", Locale.US))
                .hasSize(1);
    }

    @Test
    void findSpotlightShouldNotQuerySpotlightRepoWhenSiteNotFound() {
        when(turSNSiteRepositoryPort.findByNameIgnoreCase("unknown")).thenReturn(Optional.empty());

        service.findSpotlightBySNSiteAndLanguage("unknown", Locale.US);

        verifyNoInteractions(turSNSiteSpotlightRepository);
    }
}
