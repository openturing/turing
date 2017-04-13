package com.viglet.turing.plugins.opennlp;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import opennlp.tools.namefind.NameFinderME;

public class OpenNLPModelManager {

	private static OpenNLPModelManager instance;
	private static Object monitor = new Object();
	private Map<String, NameFinderME> cache = Collections.synchronizedMap(new HashMap<String, NameFinderME>());

	private OpenNLPModelManager() {
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

	public static OpenNLPModelManager getInstance() {
		if (instance == null) {
			synchronized (monitor) {
				if (instance == null) {
					instance = new OpenNLPModelManager();
				}
			}
		}
		return instance;
	}

}
