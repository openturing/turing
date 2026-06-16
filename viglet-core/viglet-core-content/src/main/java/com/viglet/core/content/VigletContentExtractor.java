/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.viglet.core.content;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Optional;

import org.apache.tika.exception.TikaException;
import org.apache.tika.extractor.EmbeddedDocumentExtractor;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.parser.ocr.TesseractOCRConfig;
import org.apache.tika.parser.pdf.PDFParserConfig;
import org.apache.tika.sax.BodyContentHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

/**
 * Shared Apache Tika extraction pipeline: an {@link AutoDetectParser} with PDF
 * inline-image extraction, Tesseract OCR config, and recursive embedded-document
 * extraction, producing the plain text plus the raw Tika {@link Metadata}.
 *
 * <p>This is the document-extraction core that Turing ({@code TurFileUtils}),
 * Shio and Dumont ({@code DumCommonsUtils}) each re-implemented; products keep
 * their own URL-fetch / multipart / DTO-mapping conventions on top of it.</p>
 *
 * <p>Stateless and thread-safe — a fresh parser/handler/context is built per
 * call. Temporary files used while extracting embedded documents are created in
 * the JVM default temp directory and removed on exit.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
public final class VigletContentExtractor {

    private static final Logger log = LoggerFactory.getLogger(VigletContentExtractor.class);

    private VigletContentExtractor() {
        throw new IllegalStateException("Viglet Content Extractor utility class");
    }

    /**
     * Extracts the given file, returning {@code null} when the file is missing.
     */
    public static VigletExtractedContent extract(File file) {
        if (file == null || !file.exists()) {
            log.info("File not exists: {}", file != null ? file.getAbsolutePath() : "null");
            return null;
        }
        try (InputStream inputStream = new FileInputStream(file)) {
            return extract(file, inputStream);
        } catch (IOException e) {
            log.error("Error parsing file: {}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * Extracts from a stream with no backing {@link File}.
     */
    public static VigletExtractedContent extract(InputStream inputStream) {
        return extract(null, inputStream);
    }

    /**
     * Core extraction: parse {@code inputStream}, recursively append any embedded
     * documents, and return the text + metadata bound to {@code file}.
     */
    public static VigletExtractedContent extract(File file, InputStream inputStream) {
        StringBuilder contentFile = new StringBuilder();
        AutoDetectParser parser = new AutoDetectParser();
        BodyContentHandler handler = new BodyContentHandler(-1);
        Metadata metadata = new Metadata();

        ParseContext parseContext = createParseContext(parser);
        parseContext.set(EmbeddedDocumentExtractor.class, createEmbeddedDocumentExtractor(contentFile));

        try {
            parser.parse(inputStream, handler, metadata, parseContext);
        } catch (IOException | SAXException | TikaException e) {
            log.error("Error during Tika parsing: {}", e.getMessage(), e);
        }

        contentFile.append(handler);
        return new VigletExtractedContent(file, contentFile.toString(), metadata);
    }

    /**
     * Parses a single stream into plain text, returning {@link Optional#empty()}
     * on failure. Used for embedded documents and standalone text extraction.
     */
    public static Optional<String> parseDocument(InputStream stream) throws IOException {
        AutoDetectParser parser = new AutoDetectParser();
        BodyContentHandler handler = new BodyContentHandler(-1);
        Metadata metadata = new Metadata();
        ParseContext parseContext = createParseContext(parser);

        File tempFile = createTempFile();
        try {
            Files.copy(stream, tempFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            try (FileInputStream fileInputStream = new FileInputStream(tempFile)) {
                parser.parse(fileInputStream, handler, metadata, parseContext);
                return Optional.of(handler.toString());
            }
        } catch (IOException | SAXException | TikaException e) {
            log.error("Error parsing document: {}", e.getMessage(), e);
            return Optional.empty();
        } finally {
            tempFile.deleteOnExit();
        }
    }

    private static EmbeddedDocumentExtractor createEmbeddedDocumentExtractor(StringBuilder contentFile) {
        return new EmbeddedDocumentExtractor() {
            @Override
            public boolean shouldParseEmbedded(Metadata metadata) {
                return true;
            }

            @Override
            public void parseEmbedded(InputStream stream, ContentHandler handler, Metadata metadata,
                    boolean outputHtml) throws IOException {
                parseDocument(stream).ifPresent(contentFile::append);
            }
        };
    }

    private static ParseContext createParseContext(AutoDetectParser parser) {
        TesseractOCRConfig config = new TesseractOCRConfig();
        PDFParserConfig pdfConfig = new PDFParserConfig();
        pdfConfig.setExtractInlineImages(true);

        ParseContext parseContext = new ParseContext();
        parseContext.set(TesseractOCRConfig.class, config);
        parseContext.set(PDFParserConfig.class, pdfConfig);
        parseContext.set(Parser.class, parser);
        return parseContext;
    }

    private static File createTempFile() throws IOException {
        return Files.createTempFile("viglet-content-", ".tmp").toFile();
    }
}
