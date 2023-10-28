package com.viglet.turing.connector.cms.util;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.util.Map.Entry;

import com.viglet.turing.connector.cms.beans.TuringTag;
import com.viglet.turing.connector.cms.beans.TuringTagMap;

public class TuringUtils {
	private TuringUtils() {
		throw new IllegalStateException("TuringUtils");
	}
	
	public static Set<TuringTag> turingTagMapToSet(TuringTagMap turingTagMap) {
		Set<TuringTag> turingTags = new HashSet<>();
		for (Entry<String, ArrayList<TuringTag>> entryCtd : turingTagMap.entrySet()) {
			turingTags.addAll(entryCtd.getValue());
		}
		return turingTags;
	}


}
