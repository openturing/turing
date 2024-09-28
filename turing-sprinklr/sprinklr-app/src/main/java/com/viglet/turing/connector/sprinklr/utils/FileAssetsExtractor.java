package com.viglet.turing.connector.sprinklr.utils;

import com.viglet.turing.client.auth.TurServer;
import com.viglet.turing.client.auth.credentials.TurApiKeyCredentials;
import com.viglet.turing.client.ocr.TurOcr;
import com.viglet.turing.commons.file.TurFileAttributes;
import com.viglet.turing.sprinklr.client.service.kb.response.TurSprinklrAsset;
import com.viglet.turing.sprinklr.client.service.kb.response.TurSprinklrSearchResult;
import lombok.extern.log4j.Log4j2;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.*;


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
    public static final String ID_PREFIX = "sprinklr";
    public static final String FILE_ATTACHMENT = "file-attachment";
    /**
     * Used for Turing OCR API
     */
    final String turingUrl;
    /**
     * Used for Turing OCR API
     */
    final String turingApiKey;

    final HashSet<String> alreadyProcessedIds = new HashSet<>();

    public FileAssetsExtractor(String turingUrl, String turingApiKey) {
        this.turingUrl = turingUrl;
        this.turingApiKey = turingApiKey;
    }

    /**
     * Extract all Files and its metadata from a Linked Asset key from searchResult.
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
        if (linkedAssets.removeIf(asset -> !asset.getAssetType().equals(FILE_ATTACHMENT))) {
            log.warn("Removed assets that are not file-attachment");
            log.warn("The assets on this iteration are: {}", linkedAssets.toString());
        }
        List<FileAsset> fileAssets = new ArrayList<>();

        // For each asset, uses turing OCR API to extract data.
        for (var asset : linkedAssets) {
            log.info("Processing asset - AssetId : {}", asset.getAssetId());
            try {
                // assetId it's the complete URI of the file.
                URI assetURI = new URI(asset.getAssetId()); // ex: google.com/files/text.pdf.
                String id = assetURI.getPath().substring(assetURI.getPath().lastIndexOf('/') + 1); // text.pdf
                URL url = assetURI.toURL();

                if (alreadyProcessedIds.contains(id)) {
                    log.info("Asset already processed, skipping: {}", id);
                } else {
                    fileAssets.add(getFileAsset(Objects.requireNonNull(getUrlOcr(url)), id, url));
                    alreadyProcessedIds.add(id);
                }
            } catch (URISyntaxException | MalformedURLException | IllegalArgumentException e) {
                assetLogError(searchResult, asset, e);
            }
        }

        return fileAssets;
    }

    private static void assetLogError(TurSprinklrSearchResult searchResult, TurSprinklrAsset asset, Exception e) {
        if (e instanceof IllegalArgumentException || e instanceof MalformedURLException) {
            log.error("Invalid URL, AssetId: {}, AssetType: {}, AssetCategory {}", asset.getAssetId(),
                    asset.getAssetType(), asset.getAssetCategory());
        }
        log.error(e);
        log.error("Asset coming from article: {}, {}", searchResult.getContent().getTitle(),
                searchResult.getMappingDetails().getFirst().getCommunityPermalink());
        log.info("Skipping asset");
    }

    @Nullable
    private TurFileAttributes getUrlOcr(URL url) {
        // Tries to use turing OCR API to extract content from the downloaded file.
        TurFileAttributes ocrResult = null;
        try {
            log.info("Sending File Asset to turing OCR API");
            TurServer turingServer = new TurServer(URI.create(turingUrl).toURL(),
                    new TurApiKeyCredentials(turingApiKey));
            TurOcr ocrProcessor = new TurOcr();
            ocrResult = ocrProcessor.processUrl(turingServer, url, false);
            log.debug("OCR result: {}", ocrResult);

        } catch (MalformedURLException e) {
            log.error(e);
        }
        return ocrResult;
    }

    @NotNull
    private static FileAsset getFileAsset(TurFileAttributes ocrResult, String id, URL url) {
        String extension = ocrResult.getExtension();
        String filename = ocrResult.getName();
        String content = ocrResult.getContent();
        Date indexingDate = new Date();
        Date modificationDate = ocrResult.getLastModified();
        float fileSize = ocrResult.getSize().getBytes();

        return new FileAsset(
                (ID_PREFIX + id),
                filename,
                content,
                indexingDate,
                modificationDate,
                url,
                fileSize,
                extension);
    }

}
