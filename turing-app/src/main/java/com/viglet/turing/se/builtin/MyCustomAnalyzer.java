package com.viglet.turing.se.builtin;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.LowerCaseFilter;
import org.apache.lucene.analysis.StopFilter;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.en.EnglishAnalyzer;
import org.apache.lucene.analysis.en.PorterStemFilter;
import org.apache.lucene.analysis.miscellaneous.CapitalizationFilter;
import org.apache.lucene.analysis.standard.StandardTokenizer;

public class MyCustomAnalyzer extends Analyzer {

	@Override
	protected TokenStreamComponents createComponents(String fieldName) {
		TokenStream result = new StandardTokenizer();
		result = new LowerCaseFilter(result);
		result = new StopFilter(result, EnglishAnalyzer.ENGLISH_STOP_WORDS_SET);
		result = new PorterStemFilter(result);
		result = new CapitalizationFilter(result);
		return new TokenStreamComponents(new StandardTokenizer(), result);
	}

}