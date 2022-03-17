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

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.BodyContentHandler;
import org.xml.sax.SAXException;

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
		try (InputStream inputStream = new FileInputStream(file)) {
			AutoDetectParser parser = new AutoDetectParser();
			// -1 = no limit of number of characters
			BodyContentHandler handler = new BodyContentHandler(-1);
			Metadata metadata = new Metadata();
			parser.parse(inputStream, handler, metadata, new ParseContext());
			return new TurFileAttributes(file, cleanTextContent(handler.toString()), metadata);
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
