package com.viglet.turing.sn.spotlight;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.viglet.turing.api.sn.job.TurSNJobItem;
import com.viglet.turing.api.sn.queue.TurSNMergeProvidersProcess;
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
import org.springframework.transaction.annotation.Transactional;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

@Component
public class TurSNSpotlightProcess {
    private static final Logger logger = LogManager.getLogger(TurSNSpotlightProcess.class);
    @Autowired
    private TurSNSiteSpotlightRepository turSNSiteSpotlightRepository;
    @Autowired
    private TurSNSiteSpotlightTermRepository turSNSiteSpotlightTermRepository;
    @Autowired
    private TurSNSiteSpotlightDocumentRepository turSNSiteSpotlightDocumentRepository;

    @Transactional
    private void ifExistsDeleteSpotlightDependencies(Optional<TurSNSiteSpotlight> turSNSiteSpotlight) {
        if (turSNSiteSpotlight.isPresent()) {
            Set<TurSNSiteSpotlightTerm> turSNSiteSpotlightTerms = turSNSiteSpotlightTermRepository
                    .findByTurSNSiteSpotlight(turSNSiteSpotlight.get());
            turSNSiteSpotlightTermRepository.deleteAllInBatch(turSNSiteSpotlightTerms);

            Set<TurSNSiteSpotlightDocument> turSNSiteSpotlightDocuments = turSNSiteSpotlightDocumentRepository
                    .findByTurSNSiteSpotlight(turSNSiteSpotlight.get());
            turSNSiteSpotlightDocumentRepository.deleteAllInBatch(turSNSiteSpotlightDocuments);
        }
    }

    public boolean isSpotlightJob(TurSNJobItem turSNJobItem) {
        return turSNJobItem != null && turSNJobItem.getAttributes() != null && turSNJobItem.getAttributes().containsKey("type")
                && turSNJobItem.getAttributes().get("type").equals("TUR_SPOTLIGHT");
    }

    public boolean deleteSpotlight(TurSNJobItem turSNJobItem) {
        turSNSiteSpotlightRepository.delete((String) turSNJobItem.getAttributes().get("id"));

        if (turSNJobItem.getAttributes().containsKey("id")) {
            turSNSiteSpotlightRepository.delete((String) turSNJobItem.getAttributes().get("id"));
        } else if (turSNJobItem.getAttributes().containsKey(TurSNMergeProvidersProcess.PROVIDER_ATTRIBUTE)) {
            logger.info("Provider Value: {}",
                    turSNJobItem.getAttributes().get(TurSNMergeProvidersProcess.PROVIDER_ATTRIBUTE));
            Set<TurSNSiteSpotlight> turSNSiteSpotlights = turSNSiteSpotlightRepository
                    .findByProvider((String) turSNJobItem.getAttributes().get(TurSNMergeProvidersProcess.PROVIDER_ATTRIBUTE));
            turSNSiteSpotlightRepository.deleteAllInBatch(turSNSiteSpotlights);
        }
        return true;
    }

    public boolean createSpotlight(TurSNJobItem turSNJobItem, TurSNSite turSNSite) {
        String jsonContent = (String) turSNJobItem.getAttributes().get("content");
        ObjectMapper mapper = new ObjectMapper();
        try {
            TurSNSiteSpotlight turSNSiteSpotlight = new TurSNSiteSpotlight();
            String id = (String) turSNJobItem.getAttributes().get("id");
            Optional<TurSNSiteSpotlight> turSNSiteSpotlightOptional = turSNSiteSpotlightRepository.findById(id);
            if (turSNSiteSpotlightOptional.isPresent()) {
                ifExistsDeleteSpotlightDependencies(turSNSiteSpotlightOptional);
                turSNSiteSpotlight = turSNSiteSpotlightOptional.get();
            }

            String name = (String) turSNJobItem.getAttributes().get("name");
            String provider = (String) turSNJobItem.getAttributes().get(TurSNMergeProvidersProcess.PROVIDER_ATTRIBUTE);
            List<String> terms = Arrays.asList(((String) turSNJobItem.getAttributes().get("terms")).split(","));
            SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
            simpleDateFormat.setTimeZone(TimeZone.getTimeZone("GMT"));
            Date date = simpleDateFormat.parse((String) turSNJobItem.getAttributes().get("modificationDate"));
            List<TurSpotlightContent> turSpotlightContents = mapper.readValue(jsonContent, new TypeReference<>() {
            });

            turSNSiteSpotlight.setId(id);
            turSNSiteSpotlight.setDescription(name);
            turSNSiteSpotlight.setName(name);
            turSNSiteSpotlight.setDate(date);
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
                turSNSiteSpotlightDocument.setType("Page");
                turSNSiteSpotlightDocumentRepository.save(turSNSiteSpotlightDocument);
            }

        } catch (ParseException | JsonProcessingException e) {
            logger.error(e.getMessage(), e);
        }
        return true;
    }
}
