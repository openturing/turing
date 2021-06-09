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
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

@Component
public class TurSEStopword {
	@Autowired
	private ResourceLoader resourceloader;
	
	@Cacheable("stopwords")
	public List<String> getStopWords(String locale) throws IOException {	
		List<String> stopWords = new ArrayList<String>();
		InputStreamReader isr = new InputStreamReader(
				resourceloader.getResource("classpath:/solr/conf/lang/stopwords_pt.txt").getInputStream());
		BufferedReader br = new BufferedReader(isr);
	
		String st;
		while ((st = br.readLine()) != null) {
			String line[] = st.split("\\|");
			if (line.length == 0) {
				stopWords.add(st.trim());
			} else {
				stopWords.add(line[0].trim());
			}
		}

		return stopWords;
	}
}
