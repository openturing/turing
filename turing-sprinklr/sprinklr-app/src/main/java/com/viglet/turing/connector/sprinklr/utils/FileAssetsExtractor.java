package com.viglet.turing.connector.sprinklr.utils;

import com.viglet.turing.client.auth.TurServer;
import com.viglet.turing.client.auth.credentials.TurApiKeyCredentials;
import com.viglet.turing.client.ocr.TurOcr;
import com.viglet.turing.commons.file.TurFileAttributes;
import com.viglet.turing.sprinklr.client.service.kb.response.TurSprinklrAsset;
import com.viglet.turing.sprinklr.client.service.kb.response.TurSprinklrSearchResult;
import lombok.extern.log4j.Log4j2;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;


// Estou nomeando de File extractor ao invés de asset extractor pois talvez linked Assets pode ser mais do que da categoria "file-attachment"
// https://www.sprinklr.com/help/articles/manage-assets/add-an-asset/641a8fdda1367f1be7db8255

/**
 * Extracts File Assets from Sprinklr Knowledge Base search result. Has turing URL and turing API key just to use the
 * OCR API.
 *
 * @author Gabriel F. Gomazako
 * @see FileAsset
 * @since 0.3.9
 */
@Log4j2
public class FileAssetsExtractor {
    /**
     * Used for Turing OCR API
     */
    final String turingUrl;
    /**
     * Used for Turing OCR API
     */
    final String turingApiKey;

    public FileAssetsExtractor(String turingUrl, String turingApiKey) {
        this.turingUrl = turingUrl;
        this.turingApiKey = turingApiKey;
    }


    /**
     * Extract all Files and its metadata from Linked Asset key from searchResult.
     *
     * @param searchResult Knowledge Base search API result.
     */
    public List<FileAsset> extractFromLinkedAssets(TurSprinklrSearchResult searchResult) {
        // Extracting LinkedAssets from the search API result of Sprinkler Knowledge Base.
        List<TurSprinklrAsset> linkedAssets = searchResult.getLinkedAssets();

        // If there are no linked assets, return an empty list.
        if (linkedAssets == null || linkedAssets.isEmpty()) {
            return Collections.emptyList();
        }
        List<FileAsset> fileAssets = new ArrayList<>();

        // For each asset, uses turing OCR API to extract data.
        for (var asset : linkedAssets) {
            String id = null;
            URL url = null;
            try {
                // assetId it's the complete URI of the file.
                URI assetURI = new URI(asset.getAssetId()); // ex: google.com/files/text.pdf.
                id = assetURI.getPath();// /files/text.pdf
                id = id.substring(id.lastIndexOf('/') + 1); // text.pdf
                url = assetURI.toURL();
            } catch (URISyntaxException | MalformedURLException e) {
                log.error(e);
            }

            // Tries to use turing OCR API to extract content from the downloaded file.
            TurFileAttributes ocrResult = null;
            try {
                log.info("Sending File Asset to turing OCR API, document url is {}", url);
                TurServer turingServer = new TurServer(URI.create(turingUrl).toURL(), new TurApiKeyCredentials(turingApiKey));
                TurOcr ocrProcessor = new TurOcr();
                ocrResult = ocrProcessor.processUrl(turingServer, url, false);
                log.debug("OCR result: {}", ocrResult);

            } catch (MalformedURLException e) {
                log.error(e);
            }
            String extension = ocrResult.getExtension();
            String filename = ocrResult.getName();
            String content = ocrResult.getContent();
            Date indexingDate = new Date();
            Date modificationDate = ocrResult.getLastModified();
            float fileSize = ocrResult.getSize().getBytes();

            var fileAsset = new FileAsset(
                    ("sprinklr" + id),
                    filename,
                    content,
                    indexingDate,
                    modificationDate,
                    url,
                    fileSize,
                    extension);

            fileAssets.add(fileAsset);
        }
        return fileAssets;
    }
}
