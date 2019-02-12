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
