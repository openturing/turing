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

package com.viglet.turing.se;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

@Component
public class TurSEStopword {
	private static final Logger logger = LogManager.getLogger(TurSEStopword.class);
	@Autowired
	private ResourceLoader resourceloader;

	public List<String> getStopWords() {
		List<String> stopWords = new ArrayList<>();
		try (InputStreamReader isr = new InputStreamReader(
				resourceloader.getResource("classpath:/solr/conf/lang/stopwords.txt").getInputStream());
				BufferedReader br = new BufferedReader(isr);) {
			String st;
			while ((st = br.readLine()) != null) {
				String[] line = st.split("\\|");
				if (line.length == 0) {
					stopWords.add(st.trim());
				} else {
					stopWords.add(line[0].trim());
				}
			}

			return stopWords;
		} catch (IOException e) {
			logger.error(e.getMessage(), e);
		}
		return Collections.emptyList();
	}
}
