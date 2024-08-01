package com.viglet.turing.connector.sprinklr;

import com.google.common.collect.Iterators;
import com.google.inject.Inject;
import com.viglet.turing.client.sn.TurSNServer;
import com.viglet.turing.client.sn.credentials.TurApiKeyCredentials;
import com.viglet.turing.client.sn.job.TurSNJobAction;
import com.viglet.turing.client.sn.job.TurSNJobItem;
import com.viglet.turing.client.sn.job.TurSNJobItems;
import com.viglet.turing.client.sn.job.TurSNJobUtils;
import com.viglet.turing.connector.sprinklr.bean.TurSprinklrSearch;
import com.viglet.turing.connector.sprinklr.bean.TurSprinklrSearchResult;
import com.viglet.turing.connector.sprinklr.kb.TurSprinklrKB;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.util.*;

@Slf4j
@Component
public class TurSprinklrProcess {
    public static final String ID_ATTR = "id";
    private final String turingUrl;
    private final String turingApiKey;
    private TurSNJobItems turSNJobItems = new TurSNJobItems();
    private final Collection<String> snSites;

    private final int jobSize;


    private final TurSprinklrKB turSprinklrKB;


    @Inject
    public TurSprinklrProcess(@Value("${turing.url}") String turingUrl,
                              @Value("${turing.apiKey}") String turingApiKey,
                              @Value("${turing.sprinklr.sn.site}") String snSite,
                              @Value("${turing.sprinklr.job.size}") int jobSize,
                              TurSprinklrKB turSprinklrKB) {
        this.turingUrl = turingUrl;
        this.turingApiKey = turingApiKey;
        this.snSites = List.of(snSite);
        this.jobSize = jobSize;
        this.turSprinklrKB = turSprinklrKB;
    }

    public void start() throws IOException {
        reset();
        TurSprinklrSearch turSprinklrSearch = turSprinklrKB.run();

        turSprinklrSearch.getData().getSearchResults().forEach(searchResult -> {
            getPage(searchResult);
            sendToTuringWhenMaxSize();
            getInfoQueue();
        });
        if (turSNJobItems.size() > 0) {
            sendToTuring();
            getInfoQueue();
        }
    }

    private void reset() {
        turSNJobItems = new TurSNJobItems();
    }

    private void getInfoQueue() {
        log.info("Total Job Item: {}", Iterators.size(turSNJobItems.iterator()));
    }

    public void getPage(TurSprinklrSearchResult searchResult) {
        log.info("{}: {}",searchResult.getId(), snSites);
        addTurSNJobItems(searchResult);

    }

    private void addTurSNJobItems(TurSprinklrSearchResult searchResult) {
        TurSNJobItem turSNJobItem = new TurSNJobItem(TurSNJobAction.CREATE, new ArrayList<>(snSites),
                searchResult.getLocale(),
                getJobItemAttributes(searchResult));
        turSNJobItems.add(turSNJobItem);
    }

    private void sendToTuringWhenMaxSize() {
        if (turSNJobItems.size() >= jobSize) {
            sendToTuring();
            turSNJobItems = new TurSNJobItems();
        }
    }

    public Map<String, Object> getJobItemAttributes(TurSprinklrSearchResult searchResult) {
        Map<String, Object> turSNJobItemAttributes = new HashMap<>();
        turSNJobItemAttributes.put("id", "sprinklr" + searchResult.getId());
        turSNJobItemAttributes.put("title",searchResult.getContent().getTitle());
        turSNJobItemAttributes.put("abstract","abstract");
        turSNJobItemAttributes.put("publication_date",searchResult.getPublishingDate());
        turSNJobItemAttributes.put("text",searchResult.getContent().getMarkUpText());
        turSNJobItemAttributes.put("url", "url");

        return turSNJobItemAttributes;
    }

    private void sendToTuring() {
        if (log.isDebugEnabled()) {
            for (TurSNJobItem turSNJobItem : turSNJobItems) {
                log.debug("TurSNJobItem Id: {}", turSNJobItem.getAttributes().get(ID_ATTR));
            }
        }
        try {
            TurSNJobUtils.importItems(turSNJobItems,
                    new TurSNServer(URI.create(turingUrl).toURL(), null,
                            new TurApiKeyCredentials(turingApiKey)),
                    false);
        } catch (MalformedURLException e) {
            log.error(e.getMessage(), e);
        }

    }
}
