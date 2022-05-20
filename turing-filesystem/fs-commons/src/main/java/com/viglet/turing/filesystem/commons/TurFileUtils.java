package com.viglet.turing.filesystem.commons;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.invoke.MethodHandles;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

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
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import com.viglet.turing.commons.utils.TurCommonsUtils;

public class TurFileUtils {

	private static final Logger logger = LogManager.getLogger(MethodHandles.lookup().lookupClass());

	private TurFileUtils() {
		throw new IllegalStateException("Turing File Utilities class");
	}

	public static TurFileAttributes readFile(String filePath) {
		return readFile(new File(filePath));
	}

	public static TurFileAttributes readFileAndClean(File file) {
		if (file.exists()) {
			TurFileAttributes turFileAttributes = parseFile(file);
			turFileAttributes.setContent(TurCommonsUtils.cleanTextContent(turFileAttributes.getContent()));
			return turFileAttributes;
		} else {
			logger.info("File not exists: {}", file.getAbsolutePath());
			return null;
		}
	}

	public static TurFileAttributes readFile(File file) {
		if (file.exists()) {
			return parseFile(file);
		} else {
			logger.info("File not exists: {}", file.getAbsolutePath());
			return null;
		}
	}

	public static void main(String[] args) {
		TurFileAttributes turFileAttributes = readFile(args[0]);
		System.out.println(turFileAttributes.getContent());
	}

	public static TurFileAttributes parseFile(File file) {

		try (InputStream fileInputStreamAttribute = new FileInputStream(file)) {
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
						boolean outputHtml) throws SAXException, IOException {

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
							TurCommonsUtils.addSubDirToStoreDir("tmp"));
					Files.copy(stream, tempFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
					try (FileInputStream fileInputStreamInner = new FileInputStream(tempFile)) {
						parserInner.parse(fileInputStreamInner, handlerInner, metadataInner, parseContextInner);
						contentFile.append(handlerInner.toString());

					} catch (TikaException e) {
						logger.error(e);
					}
					FileUtils.delete(tempFile);
				}
			};

			parseContext.set(EmbeddedDocumentExtractor.class, embeddedDocumentExtractor);

			parser.parse(fileInputStreamAttribute, handler, metadata, parseContext);

			contentFile.append(handler.toString());
			return new TurFileAttributes(file, contentFile.toString(), metadata);

		} catch (IOException | SAXException | TikaException e) {
			logger.error(e.getMessage(), e);
		}

		return null;
	}
}
