package com.viglet.turing.sn.spotlight;

import com.viglet.turing.domain.sn.TurSNSiteRepositoryPort;
import com.viglet.turing.persistence.model.sn.TurSNSite;
import com.viglet.turing.persistence.model.sn.spotlight.TurSNSiteSpotlight;
import com.viglet.turing.persistence.repository.sn.spotlight.TurSNSiteSpotlightRepository;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Locale;

@Service
public class TurSpotlightService {
    private final TurSNSiteRepositoryPort turSNSiteRepositoryPort;
    private final TurSNSiteSpotlightRepository turSNSiteSpotlightRepository;

    public TurSpotlightService(TurSNSiteRepositoryPort turSNSiteRepositoryPort,
                             TurSNSiteSpotlightRepository turSNSiteSpotlightRepository) {
        this.turSNSiteRepositoryPort = turSNSiteRepositoryPort;
        this.turSNSiteSpotlightRepository = turSNSiteSpotlightRepository;
    }

    public List<TurSNSiteSpotlight> findSpotlightBySNSiteAndLanguage(String snSite, Locale language) {
        // Site lookup via port; the spotlight repo doesn't have a port yet
        // (callers in TurSpotlightCache walk entity navigations on the
        // returned spotlights), so the spotlight call still takes a
        // JPA-managed reference — use a stub built from the domain id.
        return turSNSiteRepositoryPort.findByNameIgnoreCase(snSite)
                .map(site -> {
                    TurSNSite stub = new TurSNSite();
                    stub.setId(site.id());
                    return turSNSiteSpotlightRepository.findByTurSNSiteAndLanguage(stub, language);
                })
                .orElse(Collections.emptyList());
    }
}
