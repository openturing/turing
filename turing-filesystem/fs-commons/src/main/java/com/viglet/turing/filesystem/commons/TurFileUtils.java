package com.viglet.turing.filesystem.commons;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.invoke.MethodHandles;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.tika.exception.TikaException;
import org.apache.tika.extractor.EmbeddedDocumentExtractor;
import org.apache.tika.metadata.DublinCore;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.parser.ocr.TesseractOCRConfig;
import org.apache.tika.parser.pdf.PDFParserConfig;
import org.apache.tika.sax.BodyContentHandler;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;
import org.springframework.web.multipart.MultipartFile;
import com.viglet.turing.commons.utils.TurCommonsUtils;

public class TurFileUtils {
	private static final Logger logger = LogManager.getLogger(MethodHandles.lookup().lookupClass());
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
			logger.info("File not exists: {}", file.getAbsolutePath());
			return null;
		}
	}

	public static TurTikaFileAttributes parseFile(File file) {
		try (InputStream inputStream = Files.newInputStream(file.toPath())) {
			return getTurTikaFileAttributes(file, inputStream);
		} catch (IOException e) {
			logger.error(e.getMessage(), e);
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
			logger.error(e.getMessage(), e);
		}
		contentFile.append(handler);
		return new TurTikaFileAttributes(file, contentFile.toString(), metadata);
	}

	public static TurTikaFileAttributes parseFile(MultipartFile multipartFile) {
		try (InputStream inputStream = multipartFile.getInputStream()) {
			return getTurTikaFileAttributes(null, inputStream);
		} catch (IOException e) {
			logger.error(e.getMessage(), e);
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
		return Optional.ofNullable(parseFile(multipartFile)).map(tikaFileAttributes ->
						getTurFileAttributes(parseFile(multipartFile),
								multipartFile.getOriginalFilename(),
								FilenameUtils.getExtension(multipartFile.getOriginalFilename()),
								multipartFile.getSize(),
								getTikaLastModified(tikaFileAttributes)
										.orElseGet(Date::new)))
				.orElseGet(TurFileAttributes::new);
	}

	private static Optional<Date> getTikaLastModified(TurTikaFileAttributes tikaFileAttributes) {
		return Optional.ofNullable(tikaFileAttributes)
				.flatMap(t -> Optional.ofNullable(t.getMetadata())
						.map(m -> m.getDate(DublinCore.MODIFIED)));
	}

	private static void ocrDocumentLog(String documentName) {
		logger.info("Processing {} document to text", documentName);
	}

	public static TurFileAttributes urlContentToText(URL url) {
		ocrDocumentLog(url.toString());
		return Optional.ofNullable(getFile(url)).map(f -> {
					f.deleteOnExit();
					return Optional.ofNullable(parseFile(f)).map(tikaFileAttributes ->
									getTurFileAttributes(tikaFileAttributes,
											FilenameUtils.getName(url.getPath()),
											FilenameUtils.getExtension(url.getPath()),
											f.length(),
											getLastModified(tikaFileAttributes, url)))
							.orElseGet(TurFileAttributes::new);
				})
				.orElseGet(TurFileAttributes::new);
	}

	private static Date getLastModified(TurTikaFileAttributes tikaFileAttributes, URL url) {
		return getTikaLastModified(tikaFileAttributes)
				.orElseGet(() -> getLastModifiedFromUrl(url));
	}

	private static Date getLastModifiedFromUrl(URL url) {
		Date date = new Date();
		if (TurCommonsUtils.isValidUrl(url)) {
			try {
				HttpURLConnection httpUrlConnection = (HttpURLConnection) url.openConnection();
				httpUrlConnection.setRequestMethod(HEAD);
				date = new Date(httpUrlConnection.getLastModified());
				httpUrlConnection.disconnect();
			} catch (IOException e) {
				logger.error(e.getMessage(), e);
			}
		}
		return date;
	}

	private static File getFile(URL url) {
		File tempFile = null;
		if (TurCommonsUtils.isValidUrl(url)) {
			try {
				tempFile = createTempFile();
				FileUtils.copyURLToFile(
						url,
						tempFile,
						CONNECTION_TIMEOUT_MILLIS,
						CONNECTION_TIMEOUT_MILLIS);
			} catch (IOException e) {
				logger.error(e.getMessage(), e);
			}
		}
		return tempFile;
	}

	private static TurFileAttributes getTurFileAttributes(TurTikaFileAttributes tikaFileAttributes,
														  String fileName,
														  String fileExtension,
														  long fileSize,
														  Date lastModified) {
		return Optional.ofNullable(tikaFileAttributes).map(attributes ->
						TurFileAttributes.builder()
								.content(attributes.getContent())
								.name(fileName)
								.extension(fileExtension)
								.size(new TurFileSize(fileSize))
								.title(getTitle(tikaFileAttributes, fileName))
								.lastModified(lastModified)
								.metadata(getMetadataMap(tikaFileAttributes))
								.build())
				.orElseGet(TurFileAttributes::new);
	}

	private static String getTitle(TurTikaFileAttributes tikaFileAttributes, String fileName) {
		return Optional.ofNullable(tikaFileAttributes
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
			return Optional.ofNullable(handlerInner.toString());

		} catch (IOException | SAXException | TikaException e) {
			logger.error(e.getMessage(), e);
		}
		tempFile.deleteOnExit();
		return Optional.empty();
	}

	private static File createTempFile() throws IOException {
		return File.createTempFile(UUID.randomUUID().toString(), null,
				TurCommonsUtils.addSubDirToStoreDir(TMP));
	}


}
