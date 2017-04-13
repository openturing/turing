package com.viglet.turing.se.facet;

import java.util.LinkedHashMap;

public class VigSEFacetMaps {
	LinkedHashMap<String, VigSEFacetMap> facetMaps = new LinkedHashMap<String, VigSEFacetMap>();

	public LinkedHashMap<String, VigSEFacetMap> getFacetMaps() {
		return facetMaps;
	}

	public void setFieldMaps(LinkedHashMap<String, VigSEFacetMap> facetMaps) {
		this.facetMaps = facetMaps;
	}

	public VigSEFacetMaps() {
		facetMaps.put("turing_entity_PN", new VigSEFacetMap("turing_entity_PN", "People", "otsn:people",
				"http://semantic.opentext.com/otca/entity/people"));
		facetMaps.put("turing_entity_ON", new VigSEFacetMap("turing_entity_PN", "Organization", "otsn:organization",
				"http://semantic.opentext.com/otca/entity/organization"));
		facetMaps.put("turing_entity_GL", new VigSEFacetMap("turing_entity_GL", "Place", "otsn:place",
				"http://semantic.opentext.com/otca/entity/place"));
		facetMaps.put("turing_entity_IPTC", new VigSEFacetMap("turing_entity_IPTC", "IPTC Topic", "otsn:iptc",
				"http://semantic.opentext.com/otca/categorization/iptc"));
	//	facetMaps.put("otca_sentiment_Tone", new VigSEFacetMap("otca_sentiment_Tone", "Tonality", "otsn:tonality",
	//			"http://semantic.opentext.com/otca/sentiment-analysis/tonality"));
	//	facetMaps.put("otca_sentiment_Subj", new VigSEFacetMap("otca_sentiment_Subj", "Subjectivity",
	//			"otsn:subjectivity", "http://semantic.opentext.com/otca/sentiment-analysis/subjectivity"));
		facetMaps.put("type",
				new VigSEFacetMap("type", "Types", "otsn:type", "'http://semantic.opentext.com/otca/entity/type"));
		facetMaps.put("turing_entity_ComplexConcepts", new VigSEFacetMap("turing_entity_ComplexConcepts", "Concepts", "otsn:concept",
				"http://semantic.opentext.com/otca/concepts"));

		facetMaps.put("sebna_segmento", new VigSEFacetMap("sebna_segmento", "Segment", "sebna:segment",
				"http://semantic.opentext.com/sebna/segment"));
		facetMaps.put("sebna_momento", new VigSEFacetMap("sebna_momento", "Moment", "sebna:moment",
				"http://semantic.opentext.com/sebna/moment"));
		facetMaps.put("sebna_tipo_solucao", new VigSEFacetMap("sebna_tipo_solucao", "Type Solution",
				"sebna:typesolution", "http://semantic.opentext.com/sebna/typesolution"));
	//	facetMaps.put("sebna_momento_startup", new VigSEFacetMap("sebna_momento_startup", "MomentStartup",
	//			"sebna:momentstartup", "http://semantic.opentext.com/sebna/momentstartup"));
		facetMaps.put("sebna_tema",
				new VigSEFacetMap("sebna_tema", "Theme", "sebna:theme", "http://semantic.opentext.com/sebna/theme"));
		facetMaps.put("sebna_uf",
				new VigSEFacetMap("sebna_uf", "State", "sebna:state", "http://semantic.opentext.com/sebna/state"));
		facetMaps.put("sebna_tipo",
				new VigSEFacetMap("sebna_tipo", "Type", "sebna:type", "http://semantic.opentext.com/sebna/type"));

	}
}
