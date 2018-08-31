package com.viglet.turing.se;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;

@Component
public class TurSEStopword {
	@Cacheable("stopwords")
	public List<String> getStopWords(String locale) throws IOException {	
		List<String> stopWords = new ArrayList<String>();
		File file = new File(getClass().getResource("/solr/conf/lang/stopwords_pt.txt").getFile());
		BufferedReader br = new BufferedReader(new FileReader(file));

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
