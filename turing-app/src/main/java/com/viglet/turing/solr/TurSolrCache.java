package com.viglet.turing.solr;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;

@Component
public class TurSolrCache {
    private static final Logger logger = LogManager.getLogger(TurSolrCache.class);

    @Cacheable(value = "solrCore", sync = true)
    public boolean isSolrCoreExists(String urlString) {
        int responseCode = 0;
        URL url;
        try {
            url = new URL(urlString.concat("/select"));
            HttpURLConnection huc = (HttpURLConnection) url.openConnection();
            responseCode = huc.getResponseCode();
        } catch (IOException e) {
            logger.error(e.getMessage(), e);
        }
        System.out.println("SolrCore");
        return responseCode == 200;
    }
}
