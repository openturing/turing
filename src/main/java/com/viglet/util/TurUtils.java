package com.viglet.util;

import java.text.Normalizer;

public class TurUtils {
	public static String stripAccents(String s) {
		s = Normalizer.normalize(s, Normalizer.Form.NFD);
		s = s.replaceAll("[\\p{InCombiningDiacriticalMarks}]", "");
		return s;
	}
	
	public static String removeDuplicateWhiteSpaces(String s) {
		return s.replaceAll("\\s+", " ").trim();
	}
	
	
}
