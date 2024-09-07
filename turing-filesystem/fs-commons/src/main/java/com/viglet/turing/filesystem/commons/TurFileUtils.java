package com.viglet.turing.filesystem.commons;

import com.viglet.turing.commons.file.TurFileAttributes;
import com.viglet.turing.commons.file.TurFileSize;
import com.viglet.turing.commons.utils.TurCommonsUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FilenameUtils;
import org.apache.tika.exception.TikaException;
import org.apache.tika.extractor.EmbeddedDocumentExtractor;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.parser.ocr.TesseractOCRConfig;
import org.apache.tika.parser.pdf.PDFParserConfig;
import org.apache.tika.sax.BodyContentHandler;
import org.springframework.web.multipart.MultipartFile;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Optional;
import java.util.UUID;

@Slf4j
public class TurFileUtils {

    private TurFileUtils() {
        throw new IllegalStateException("Turing File Utilities class");
    }

    public static TurTikaFileAttributes readFile(String filePath) {
        return readFile(new File(filePath));
    }

    public static TurTikaFileAttributes readFile(File file) {
        if (file.exists()) {
            return parseFile(file);
        } else {
            log.info("File not exists: {}", file.getAbsolutePath());
            return null;
        }
    }

    public static TurTikaFileAttributes parseFile(File file) {
        try (InputStream fileInputStreamAttribute = new FileInputStream(file)) {
            return parseFile(fileInputStreamAttribute, file);

        } catch (IOException e) {
            log.error(e.getMessage(), e);
        }
        return null;
    }

    public static TurTikaFileAttributes parseFile(InputStream inputStream, File file) {
        try (inputStream) {
            StringBuilder contentFile = new StringBuilder();
            AutoDetectParser parser = new AutoDetectParser();
            // -1 = no limit of number of characters
            BodyContentHandler handler = new BodyContentHandler(-1);
            Metadata metadata = new Metadata();
            EmbeddedDocumentExtractor embeddedDocumentExtractor = new EmbeddedDocumentExtractor() {
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
            final ParseContext parseContext = getParseContext(parser);
            parseContext.set(EmbeddedDocumentExtractor.class, embeddedDocumentExtractor);
            parser.parse(inputStream, handler, metadata, parseContext);
            contentFile.append(handler);
            return new TurTikaFileAttributes(file, contentFile.toString(), metadata);
        } catch (IOException | SAXException | TikaException e) {
            log.error(e.getMessage(), e);
        }
        return null;
    }

    private static ParseContext getParseContext(AutoDetectParser parser) {
        TesseractOCRConfig config = new TesseractOCRConfig();
        PDFParserConfig pdfConfig = new PDFParserConfig();
        pdfConfig.setExtractInlineImages(true);
        ParseContext parseContext = new ParseContext();
        parseContext.set(TesseractOCRConfig.class, config);
        parseContext.set(PDFParserConfig.class, pdfConfig);
        parseContext.set(Parser.class, parser);
        return parseContext;
    }

    public static TurFileAttributes documentToText(MultipartFile multipartFile) {

        try (InputStream inputStream = multipartFile.getInputStream()) {
            TurTikaFileAttributes turTikaFileAttributes = parseFile(inputStream, null);
            TurFileAttributes turFileAttributes = new TurFileAttributes();
            Optional.ofNullable(turTikaFileAttributes).ifPresent(attributes -> {
                turFileAttributes.setContent(attributes.getContent());
                turFileAttributes.setName(multipartFile.getOriginalFilename());
                turFileAttributes.setExtension(FilenameUtils.getExtension(multipartFile.getOriginalFilename()));
                turFileAttributes.setSize(new TurFileSize(multipartFile.getSize()));
            });
            return turFileAttributes;
        } catch (IOException e) {
            log.error(e.getMessage(), e);
        }
        return null;
    }

    public static Optional<String> parseDocument(InputStream stream) throws IOException {
        BodyContentHandler handlerInner = new BodyContentHandler(-1);
        AutoDetectParser parserInner = new AutoDetectParser();
        Metadata metadataInner = new Metadata();
        final ParseContext parseContextInner = getParseContext(parserInner);
        return getFileContent(stream, handlerInner, parserInner, metadataInner, parseContextInner);
    }

    public static Optional<String> getFileContent(InputStream stream, BodyContentHandler handlerInner,
                                                  AutoDetectParser parserInner, Metadata metadataInner,
                                                  ParseContext parseContextInner) throws IOException {
        File tempFile = File.createTempFile(UUID.randomUUID().toString(), null,
                TurCommonsUtils.addSubDirToStoreDir("tmp"));
        Files.copy(stream, tempFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
        try (FileInputStream fileInputStreamInner = new FileInputStream(tempFile)) {
            parserInner.parse(fileInputStreamInner, handlerInner, metadataInner, parseContextInner);
            return handlerInner.toString().describeConstable();

        } catch (IOException | SAXException | TikaException e) {
            log.error(e.getMessage(), e);
        }
        tempFile.deleteOnExit();
        return Optional.empty();
    }

}
