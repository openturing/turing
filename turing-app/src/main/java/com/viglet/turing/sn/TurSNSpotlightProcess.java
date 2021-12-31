/*
 * Copyright (C) 2016-2021 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package com.viglet.turing.sn;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.viglet.turing.api.sn.job.TurSNJobItem;
import com.viglet.turing.api.sn.queue.TurSpotlightContent;
import com.viglet.turing.persistence.model.sn.TurSNSite;
import com.viglet.turing.persistence.model.sn.spotlight.TurSNSiteSpotlight;
import com.viglet.turing.persistence.model.sn.spotlight.TurSNSiteSpotlightDocument;
import com.viglet.turing.persistence.model.sn.spotlight.TurSNSiteSpotlightTerm;
import com.viglet.turing.persistence.repository.sn.spotlight.TurSNSiteSpotlightDocumentRepository;
import com.viglet.turing.persistence.repository.sn.spotlight.TurSNSiteSpotlightRepository;
import com.viglet.turing.persistence.repository.sn.spotlight.TurSNSiteSpotlightTermRepository;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * @author Alexandre Oliveira
 * @since 0.3.5
 */
@Component
public class TurSNSpotlightProcess {
    private static final Logger logger = LogManager.getLogger(TurSNSpotlightProcess.class);
    private static final String NAME_ATTRIBUTE = "name";
    private static final String CONTENT_ATTRIBUTE = "content";
    private static final String TERMS_ATTRIBUTE = "terms";
    private static final String DOCUMENT_TYPE = "Page";
    private static final String TYPE_VALUE = "TUR_SPOTLIGHT";
    @Autowired
    private TurSNSiteSpotlightRepository turSNSiteSpotlightRepository;
    @Autowired
    private TurSNSiteSpotlightTermRepository turSNSiteSpotlightTermRepository;
    @Autowired
    private TurSNSiteSpotlightDocumentRepository turSNSiteSpotlightDocumentRepository;

    private void ifExistsDeleteSpotlightDependencies(TurSNSiteSpotlight turSNSiteSpotlight) {
        if (turSNSiteSpotlight != null) {
            Set<TurSNSiteSpotlightTerm> turSNSiteSpotlightTerms = turSNSiteSpotlightTermRepository
                    .findByTurSNSiteSpotlight(turSNSiteSpotlight);
            turSNSiteSpotlightTermRepository.deleteAllInBatch(turSNSiteSpotlightTerms);

            Set<TurSNSiteSpotlightDocument> turSNSiteSpotlightDocuments = turSNSiteSpotlightDocumentRepository
                    .findByTurSNSiteSpotlight(turSNSiteSpotlight);
            turSNSiteSpotlightDocumentRepository.deleteAllInBatch(turSNSiteSpotlightDocuments);
        }
    }

    public boolean isSpotlightJob(TurSNJobItem turSNJobItem) {
        return turSNJobItem != null && turSNJobItem.getAttributes() != null
                && turSNJobItem.getAttributes().containsKey(TurSNConstants.TYPE_ATTRIBUTE)
                && turSNJobItem.getAttributes().get(TurSNConstants.TYPE_ATTRIBUTE).equals(TYPE_VALUE);
    }

    public boolean deleteUnmanagedSpotlight(TurSNJobItem turSNJobItem, TurSNSite turSNSite) {
        if (turSNJobItem.getAttributes().containsKey(TurSNConstants.ID_ATTRIBUTE)) {
            Set<TurSNSiteSpotlight> turSNSiteSpotlights =
                    turSNSiteSpotlightRepository.findByUnmanagedIdAndTurSNSite((String) turSNJobItem.getAttributes()
                            .get(TurSNConstants.ID_ATTRIBUTE), turSNSite);
            turSNSiteSpotlightRepository.deleteAllInBatch(turSNSiteSpotlights);
        } else if (turSNJobItem.getAttributes().containsKey(TurSNConstants.PROVIDER_ATTRIBUTE)) {
            logger.info("Provider Value: {}",
                    turSNJobItem.getAttributes().get(TurSNConstants.PROVIDER_ATTRIBUTE));
            Set<TurSNSiteSpotlight> turSNSiteSpotlights = turSNSiteSpotlightRepository
                    .findByProvider((String) turSNJobItem.getAttributes().get(TurSNConstants.PROVIDER_ATTRIBUTE));
            turSNSiteSpotlightRepository.deleteAllInBatch(turSNSiteSpotlights);
        }
        return true;
    }

    public boolean createUnmanagedSpotlight(TurSNJobItem turSNJobItem, TurSNSite turSNSite) {
        String id = (String) turSNJobItem.getAttributes().get(TurSNConstants.ID_ATTRIBUTE);
        Set<TurSNSiteSpotlight> turSNSiteSpotlights = turSNSiteSpotlightRepository
                .findByUnmanagedIdAndTurSNSite(id, turSNSite);
        TurSNSiteSpotlight turSNSiteSpotlight = new TurSNSiteSpotlight();
        if (!turSNSiteSpotlights.isEmpty()) {
            turSNSiteSpotlights.forEach(this::ifExistsDeleteSpotlightDependencies);
            if (turSNSiteSpotlights.size() > 1) {
                turSNSiteSpotlightRepository.deleteAllInBatch(turSNSiteSpotlights);
            } else {
                turSNSiteSpotlight = turSNSiteSpotlights.iterator().next();
            }
        }
        try {
            String jsonContent = (String) turSNJobItem.getAttributes().get(CONTENT_ATTRIBUTE);
            String name = (String) turSNJobItem.getAttributes().get(NAME_ATTRIBUTE);
            String provider = (String) turSNJobItem.getAttributes().get(TurSNConstants.PROVIDER_ATTRIBUTE);
            List<String> terms = Arrays.asList(((String) turSNJobItem.getAttributes().get(TERMS_ATTRIBUTE))
                    .split(","));
            SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
            simpleDateFormat.setTimeZone(TimeZone.getTimeZone("GMT"));
            Date date = simpleDateFormat.parse((String) turSNJobItem.getAttributes()
                    .get(TurSNConstants.MODIFICATION_DATE_ATTRIBUTE));
            List<TurSpotlightContent> turSpotlightContents = new ObjectMapper().readValue(jsonContent, new TypeReference<>() {
            });

            turSNSiteSpotlight.setUnmanagedId(id);
            turSNSiteSpotlight.setDescription(name);
            turSNSiteSpotlight.setName(name);
            turSNSiteSpotlight.setModificationDate(date);
            turSNSiteSpotlight.setTurSNSite(turSNSite);
            turSNSiteSpotlight.setManaged(0);
            turSNSiteSpotlight.setProvider(provider);
            turSNSiteSpotlightRepository.save(turSNSiteSpotlight);

            for (String term : terms) {
                TurSNSiteSpotlightTerm turSNSiteSpotlightTerm = new TurSNSiteSpotlightTerm();
                turSNSiteSpotlightTerm.setName(term.trim());
                turSNSiteSpotlightTerm.setTurSNSiteSpotlight(turSNSiteSpotlight);
                turSNSiteSpotlightTermRepository.save(turSNSiteSpotlightTerm);
            }

            for (TurSpotlightContent turSpotlightContent : turSpotlightContents) {
                TurSNSiteSpotlightDocument turSNSiteSpotlightDocument = new TurSNSiteSpotlightDocument();
                turSNSiteSpotlightDocument.setPosition(turSpotlightContent.getPosition());
                turSNSiteSpotlightDocument.setTitle(turSpotlightContent.getTitle());
                turSNSiteSpotlightDocument.setTurSNSiteSpotlight(turSNSiteSpotlight);
                turSNSiteSpotlightDocument.setContent(turSpotlightContent.getContent());
                turSNSiteSpotlightDocument.setLink(turSpotlightContent.getLink());
                turSNSiteSpotlightDocument.setType(DOCUMENT_TYPE);
                turSNSiteSpotlightDocumentRepository.save(turSNSiteSpotlightDocument);
            }
        } catch (ParseException | JsonProcessingException e) {
            logger.error(e.getMessage(), e);
        }
        return true;
    }
}
