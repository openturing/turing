package com.viglet.turing.plugins.opennlp;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import opennlp.tools.namefind.NameFinderME;

public class TurOpenNLPModelManager {

	private static TurOpenNLPModelManager instance;
	private static Object monitor = new Object();
	private Map<String, NameFinderME> cache = Collections.synchronizedMap(new HashMap<String, NameFinderME>());

	private TurOpenNLPModelManager() {
	}

	public void put(String modelPath, NameFinderME value) {
		cache.put(modelPath, value);
	}

	public NameFinderME get(String modelPath) {
		return cache.get(modelPath);
	}

	public void clear(String modelPath) {
		cache.put(modelPath, null);
	}

	public void clear() {
		cache.clear();
	}

	public boolean exists(String modelPath) {
		return cache.get(modelPath) != null;

	}

	public static TurOpenNLPModelManager getInstance() {
		if (instance == null) {
			synchronized (monitor) {
				if (instance == null) {
					instance = new TurOpenNLPModelManager();
				}
			}
		}
		return instance;
	}

}
