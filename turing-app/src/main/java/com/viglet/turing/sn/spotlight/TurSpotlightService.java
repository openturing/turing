package com.viglet.turing.sn.spotlight;

import com.viglet.turing.persistence.model.sn.spotlight.TurSNSiteSpotlight;
import com.viglet.turing.persistence.repository.sn.TurSNSiteRepository;
import com.viglet.turing.persistence.repository.sn.spotlight.TurSNSiteSpotlightRepository;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Locale;

@Service
public class TurSpotlightService {
    private final TurSNSiteRepository turSNSiteRepository;
    private final TurSNSiteSpotlightRepository turSNSiteSpotlightRepository;

    public TurSpotlightService(TurSNSiteRepository turSNSiteRepository,
                             TurSNSiteSpotlightRepository turSNSiteSpotlightRepository) {
        this.turSNSiteRepository = turSNSiteRepository;
        this.turSNSiteSpotlightRepository = turSNSiteSpotlightRepository;
    }
    public List<TurSNSiteSpotlight> findSpotlightBySNSiteAndLanguage(String snSite, Locale language) {
        return turSNSiteRepository.findByNameIgnoreCase(snSite).map(turSNSite -> turSNSiteSpotlightRepository
                .findByTurSNSiteAndLanguage(turSNSite, language)).orElse(Collections.emptyList());

    }
}
