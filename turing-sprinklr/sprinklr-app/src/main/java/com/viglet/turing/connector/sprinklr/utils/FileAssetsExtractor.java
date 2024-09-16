package com.viglet.turing.connector.sprinklr.utils;

import com.viglet.turing.client.auth.TurServer;
import com.viglet.turing.client.auth.credentials.TurApiKeyCredentials;
import com.viglet.turing.client.ocr.TurOcr;
import com.viglet.turing.sprinklr.client.service.kb.response.TurSprinklrAsset;
import com.viglet.turing.sprinklr.client.service.kb.response.TurSprinklrSearchResult;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.io.FileUtils;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.UUID;


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
     * @param searchResult Knowledge Base search API result.
     */
    public List<FileAsset> extractFromLinkedAssets(TurSprinklrSearchResult searchResult) {
        // Extraindo LinkedAssets do resultado da chamada de API de search do Knowledge Base do Sprinkler
        List<TurSprinklrAsset> linkedAssets = searchResult.getLinkedAssets();

        // Se não houver resultados.
        if (linkedAssets == null || linkedAssets.isEmpty()) {
            return Collections.emptyList();
        }

        List<FileAsset> fileAssets = new ArrayList<>();

        // Vamos extrair informações a partir de cada asset, vamos baixar o documento para conseguir seu tamanho e informações sobre a data
        // Também vamos usar OCR para extrair o conteúdo do arquivo.
        for (var asset : linkedAssets) {
            String id = null;
            String filename = null;
            String extension = null;
            URL url = null;
            try {
                // assetId em arquivos é a completa do URL do arquivo
                URI assetURI = new URI(asset.getAssetId()); // ex: google.com/files/text.pdf.
                id = assetURI.getPath();// /files/text.pdf
                id = id.substring(id.lastIndexOf('/') + 1); // text.pdf

                url = assetURI.toURL();

                int lastDotIndex = id.lastIndexOf('.');
                filename = id.substring(0, lastDotIndex);
                extension = id.substring(lastDotIndex + 1);

            } catch (URISyntaxException | MalformedURLException e) {
                log.error(e);
            }

            File downloadedFile = downloadFile(url);
            String contentFromDownloadedFile = null;
            // Usa OCR para converter o arquivo para string.
            try {
                log.info("Sending documento to OCR api in: " + URI.create(turingUrl).toURL());
                log.info("file type=" + asset.getAssetType());
                contentFromDownloadedFile = TurOcr.processFile(new TurServer(URI.create(turingUrl).toURL(), new TurApiKeyCredentials(turingApiKey)), downloadedFile, false);
            } catch (MalformedURLException e) {
                log.error(e);
            }

            Date indexingDate = new Date();
            Date modificationDate = null;
            long fileSize = -1;
            try {
                long dateFromFile = Files.getLastModifiedTime(downloadedFile.toPath()).toMillis();
                modificationDate = new Date(dateFromFile);
                fileSize = Files.size(downloadedFile.toPath());
            } catch (IOException e) {
                log.error(e);
            }

            var assetType = asset.getAssetType();
            var assetCategory = asset.getAssetCategory();

            var fileAsset = new FileAsset(
                    ("sprinklr" + id),
                    filename,
                    contentFromDownloadedFile,
                    indexingDate,
                    modificationDate,
                    url,
                    fileSize,
                    extension,
                    assetType,
                    assetCategory);

            fileAssets.add(fileAsset);
        }
        return fileAssets;
    }

    private File downloadFile(URL url) {
        try {
            File file = new File("/store/tmp/" + UUID.randomUUID() + ".pdf");
            FileUtils.copyURLToFile(url, file, 5000, 5000);

            return file;

        } catch (IOException e) {
            log.error(e);
        }
        return null;
    }
}
