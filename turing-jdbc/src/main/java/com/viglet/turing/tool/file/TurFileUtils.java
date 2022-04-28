/*
 * Copyright (C) 2016-2022 the original author or authors. 
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
package com.viglet.turing.tool.file;

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

import com.viglet.turing.utils.TurUtils;

/**
 *
 * @author Alexandre Oliveira
 * 
 * @since 0.3.5
 *
 **/
public class TurFileUtils {
	private static final Logger logger = LogManager.getLogger(MethodHandles.lookup().lookupClass());

	private TurFileUtils() {
		throw new IllegalStateException("Turing File Utilities class");
	}

	public static TurFileAttributes readFile(String filePath) {
		File file = new File(filePath);
		if (file.exists()) {
			return parseFile(file);
		} else {
			logger.info("File not exists: {}", filePath);
			return null;
		}
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
							TurUtils.addSubDirToStoreDir("tmp"));
					Files.copy(stream, tempFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
					try (FileInputStream fileInputStreamInner = new FileInputStream(tempFile)) {
						parserInner.parse(fileInputStreamInner, handlerInner, metadataInner, parseContextInner);
						contentFile.append(TurUtils.cleanTextContent(handlerInner.toString()));

					} catch (TikaException e) {
						logger.error(e);
					}
					FileUtils.delete(tempFile);
				}
			};

			parseContext.set(EmbeddedDocumentExtractor.class, embeddedDocumentExtractor);

			parser.parse(fileInputStreamAttribute, handler, metadata, parseContext);

			contentFile.append(TurUtils.cleanTextContent(handler.toString()));

			return new TurFileAttributes(file, cleanTextContent(contentFile.toString()), metadata);

		} catch (IOException | SAXException | TikaException e) {
			logger.error(e.getMessage(), e);
		}

		return null;
	}

	private static String cleanTextContent(String text) {
		text = text.replaceAll("[\r\n\t]", " ");
		text = text.replaceAll("[^\\p{L}&&[^0-9A-Za-z]&&[^\\p{javaSpaceChar}]&&[^\\p{Punct}]]", "").replaceAll("_{2,}",
				"");
		// Remove 2 or more spaces
		text = text.trim().replaceAll(" +", " ");
		return text.trim();
	}
}
