package com.viglet.turing.filesystem.commons;

import com.viglet.turing.commons.file.TurFileAttributes;
import com.viglet.turing.commons.file.TurFileSize;
import com.viglet.turing.commons.utils.TurCommonsUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.validator.routines.UrlValidator;
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
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.*;

@Slf4j
public class TurFileUtils {

    public static final String PDF_DOC_INFO_TITLE = "pdf:docinfo:title";
    public static final int CONNECTION_TIMEOUT_MILLIS = 5000;
    public static final String TMP = "tmp";
    public static final String HEAD = "HEAD";

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
        try (InputStream inputStream = new FileInputStream(file)) {
            return getTurTikaFileAttributes(file, inputStream);
        } catch (IOException e) {
            log.error(e.getMessage(), e);
        }
        return null;
    }

    private static TurTikaFileAttributes getTurTikaFileAttributes(File file, InputStream inputStream) {
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
        ParseContext parseContext = getParseContext(parser);
        parseContext.set(EmbeddedDocumentExtractor.class, embeddedDocumentExtractor);
        try {
            parser.parse(inputStream, handler, metadata, parseContext);
        } catch (IOException | SAXException | TikaException e) {
            log.error(e.getMessage(), e);
        }
        contentFile.append(handler);
        return new TurTikaFileAttributes(file, contentFile.toString(), metadata);
    }

    public static TurTikaFileAttributes parseFile(MultipartFile multipartFile) {
        try (InputStream inputStream = multipartFile.getInputStream()) {
            return getTurTikaFileAttributes(null, inputStream);
        } catch (IOException e) {
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
        return getTurFileAttributes(parseFile(multipartFile),
                multipartFile.getOriginalFilename(),
                FilenameUtils.getExtension(multipartFile.getOriginalFilename()),
                multipartFile.getSize(),
                new Date());
    }

    public static TurFileAttributes urlContentToText(URL url) {
        File file = getFile(url);
        file.deleteOnExit();
        return getTurFileAttributes(parseFile(file),
                FilenameUtils.getName(url.getPath()),
                FilenameUtils.getExtension(url.getPath()),
                file.length(),
                getLastModified(url));
    }

    private static Date getLastModified(URL url) {
        Date date = new Date();

        if (isValidUrl(url)) {
            try {
                HttpURLConnection httpUrlConnection = (HttpURLConnection) url.openConnection();
                httpUrlConnection.setRequestMethod(HEAD);
                date = new Date(httpUrlConnection.getLastModified());
                httpUrlConnection.disconnect();
            } catch (IOException e) {
                log.error(e.getMessage(), e);
            }
        }
        return date;
    }

    private static boolean isValidUrl(URL url) {
        UrlValidator urlValidator = new UrlValidator();
        return urlValidator.isValid(url.getPath());
    }

    private static File getFile(URL url) {
        File tempFile = null;
        if (isValidUrl(url)) {
            try {
                tempFile = createTempFile();
                FileUtils.copyURLToFile(
                        url,
                        tempFile,
                        CONNECTION_TIMEOUT_MILLIS,
                        CONNECTION_TIMEOUT_MILLIS);
            } catch (IOException e) {
                log.error(e.getMessage(), e);
            }
        }
        return tempFile;
    }

    private static TurFileAttributes getTurFileAttributes(TurTikaFileAttributes file,
                                                          String fileName,
                                                          String fileExtension,
                                                          long fileSize,
                                                          Date lastModified) {
        return Optional.ofNullable(file).map(attributes ->
                        TurFileAttributes.builder()
                                .content(attributes.getContent())
                                .name(fileName)
                                .extension(fileExtension)
                                .size(new TurFileSize(fileSize))
                                .title(getTitle(file, fileName))
                                .lastModified(lastModified)
                                .metadata(getMetadataMap(file))
                                .build())
                .orElseGet(TurFileAttributes::new);
    }

    private static String getTitle(TurTikaFileAttributes file, String fileName) {
        return Optional.ofNullable(file
                        .getMetadata()
                        .get(PDF_DOC_INFO_TITLE))
                .orElse(fileName);
    }

    private static Map<String, String> getMetadataMap(TurTikaFileAttributes file) {
        Map<String, String> metadataMap = new HashMap<>();
        Arrays.stream(file.getMetadata().names()).forEach(name ->
                metadataMap.put(name, file.getMetadata().get(name)));
        return metadataMap;
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
        File tempFile = createTempFile();
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

    private static File createTempFile() throws IOException {
        return File.createTempFile(UUID.randomUUID().toString(), null,
                TurCommonsUtils.addSubDirToStoreDir(TMP));
    }


}
