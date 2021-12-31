/*
 * Copyright (C) 2016-2019 the original author or authors.
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

package com.viglet.turing.api.sn.job;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.viglet.turing.persistence.model.sn.TurSNSite;
import com.viglet.turing.persistence.repository.sn.TurSNSiteRepository;
import com.viglet.turing.sn.TurSNConstants;
import com.viglet.turing.utils.TurUtils;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.apache.commons.io.FileUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tika.exception.TikaException;
import org.apache.tika.extractor.EmbeddedDocumentExtractor;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.parser.ocr.TesseractOCRConfig;
import org.apache.tika.parser.pdf.PDFParserConfig;
import org.apache.tika.sax.BodyContentHandler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jms.core.JmsMessagingTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/sn/{siteName}/import")
@Tag(name = "Semantic Navigation Import", description = "Semantic Navigation Import API")
public class TurSNImportAPI {
    private static final Logger logger = LogManager.getLogger(TurSNImportAPI.class);
    @Autowired
    private JmsMessagingTemplate jmsMessagingTemplate;
    @Autowired
    private TurSNSiteRepository turSNSiteRepository;

    @PostMapping
    public boolean turSNImportBroker(@PathVariable String siteName, @RequestBody TurSNJobItems turSNJobItems) {
        TurSNSite turSNSite = turSNSiteRepository.findByName(siteName);
        if (turSNSite != null) {
            TurSNJob turSNJob = new TurSNJob();
            turSNJob.setSiteId(turSNSite.getId());
            turSNJob.setTurSNJobItems(turSNJobItems);
            send(turSNJob);
            return true;
        } else {
            turSNJobItems.forEach(turSNJobItem -> {
                if (turSNJobItem != null) {
                    logger.warn("Object ID '{}' of '{}' SN Site ({}) was not processed. Because '{}' SN Site doesn't exist",
                            turSNJobItem.getAttributes() != null ?
                                    turSNJobItem.getAttributes().get(TurSNConstants.ID_ATTRIBUTE) : null,
                            siteName,
                            turSNJobItem.getLocale(),
                            siteName);
                } else {
                    logger.warn("No JobItem' of '{}' SN Site", siteName);
                }
            });
            return false;
        }
    }

    @PostMapping("zip")
    public boolean turSNImportZipFileBroker(@PathVariable String siteName, @RequestParam("file") MultipartFile multipartFile) {
        File extractFolder = TurUtils.extractZipFile(multipartFile);
        if (extractFolder != null) {
            TurSNJobItems turSNJobItems = null;
            try (FileInputStream fileInputStream = new FileInputStream(extractFolder.getAbsolutePath()
                    .concat(File.separator).concat(TurSNConstants.EXPORT_FILE))) {
                turSNJobItems = new ObjectMapper().readValue(fileInputStream, TurSNJobItems.class);
                turSNJobItems.forEach(turSNJobItem ->
                        turSNJobItem.getAttributes().entrySet().forEach(attribute ->
                                extractTextOfFileAttribute(extractFolder, attribute)));
            } catch (IOException e) {
                logger.error(e.getMessage(), e);
            }
            try {
                FileUtils.deleteDirectory(extractFolder);
            } catch (IOException e) {
                logger.error(e.getMessage(), e);
            }
            return turSNJobItems != null && turSNImportBroker(siteName, turSNJobItems);

        }
        return false;
    }

    private void extractTextOfFileAttribute(File extractFolder, Map.Entry<String, Object> attribute) {
        if (attribute.getValue().toString().startsWith(TurSNConstants.FILE_PROTOCOL)) {
            String fileName = attribute.getValue().toString().replace(TurSNConstants.FILE_PROTOCOL, "");
            try (FileInputStream fileInputStreamAttribute =
                         new FileInputStream(extractFolder.getAbsolutePath() + File.separator + fileName)) {
                StringBuilder contentFile = new StringBuilder();
                AutoDetectParser parser = new AutoDetectParser();
                // -1 = no limit of number of characters
                BodyContentHandler handler = new BodyContentHandler(-1);
                Metadata metadata = new Metadata();

                TesseractOCRConfig config = new TesseractOCRConfig();
                PDFParserConfig pdfConfig = new PDFParserConfig();
                pdfConfig.setExtractInlineImages(true);

                ParseContext parseContext = new ParseContext();
                parseContext.set(TesseractOCRConfig.class, config);
                parseContext.set(PDFParserConfig.class, pdfConfig);

                parseContext.set(Parser.class, parser);

                EmbeddedDocumentExtractor embeddedDocumentExtractor = new EmbeddedDocumentExtractor() {
                    @Override
                    public boolean shouldParseEmbedded(Metadata metadata) {
                        return true;
                    }

                    @Override
                    public void parseEmbedded(InputStream stream, ContentHandler handler, Metadata metadata,
                                              boolean outputHtml) throws IOException {

                        BodyContentHandler handlerInner = new BodyContentHandler(-1);
                        AutoDetectParser parserInner = new AutoDetectParser();

                        Metadata metadataInner = new Metadata();

                        TesseractOCRConfig tesseractOCRConfig = new TesseractOCRConfig();
                        PDFParserConfig pdfConfigInner = new PDFParserConfig();
                        pdfConfigInner.setExtractInlineImages(true);

                        ParseContext parseContextInner = new ParseContext();
                        parseContextInner.set(TesseractOCRConfig.class, tesseractOCRConfig);
                        parseContextInner.set(PDFParserConfig.class, pdfConfigInner);

                        parseContextInner.set(Parser.class, parserInner);

                        File tempFile = File.createTempFile(UUID.randomUUID().toString(), null,
                                TurUtils.addSubDirToStoreDir("tmp"));
                        Files.copy(stream, tempFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                        try (FileInputStream fileInputStreamInner = new FileInputStream(tempFile)) {
                            parserInner.parse(fileInputStreamInner, handlerInner, metadataInner, parseContextInner);
                            contentFile.append(TurUtils.cleanTextContent(handlerInner.toString()));

                        } catch (IOException | SAXException | TikaException e) {
                            logger.error(e);
                        }
                        FileUtils.delete(tempFile);
                    }
                };

                parseContext.set(EmbeddedDocumentExtractor.class, embeddedDocumentExtractor);

                parser.parse(fileInputStreamAttribute, handler, metadata, parseContext);

                contentFile.append(TurUtils.cleanTextContent(handler.toString()));

                attribute.setValue(contentFile.toString());

            } catch (IOException | SAXException | TikaException e) {
                logger.error(e);
            }
        }
    }

    public void send(TurSNJob turSNJob) {
        sentQueueInfo(turSNJob);
        if (logger.isDebugEnabled()) {
            logger.debug("Sent job - {}", TurSNConstants.INDEXING_QUEUE);
            logger.debug("turSNJob: {}", turSNJob.getTurSNJobItems());
        }
        this.jmsMessagingTemplate.convertAndSend(TurSNConstants.INDEXING_QUEUE, turSNJob);
    }

    private void sentQueueInfo(TurSNJob turSNJob) {
        TurSNSite turSNSite = turSNSiteRepository.findById(turSNJob.getSiteId()).orElse(null);
        turSNJob.getTurSNJobItems().forEach(turJobItem -> {
            if (turSNSite != null && turJobItem != null && turJobItem.getAttributes() != null
                    && turJobItem.getAttributes().containsKey(TurSNConstants.ID_ATTRIBUTE)) {
                String action = null;
                if (turJobItem.getTurSNJobAction().equals(TurSNJobAction.CREATE)) {
                    action = "index";
                } else if (turJobItem.getTurSNJobAction().equals(TurSNJobAction.DELETE)) {
                    action = "deindex";
                }
                logger.info("Sent to queue to {} the Object ID '{}' of '{}' SN Site ({}).", action,
                        turJobItem.getAttributes().get(TurSNConstants.ID_ATTRIBUTE),
                        turSNSite.getName(), turJobItem.getLocale());
            }
        });
    }
}
